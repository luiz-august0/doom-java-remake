package doom.game.mapspecial;

import doom.game.view.LineFlags;
import doom.game.world.Line;
import doom.game.world.Sector;
import doom.game.world.Side;
import doom.game.world.World;
import doom.math.DoomFatalException;
import doom.math.Fixed;
import doom.math.annotation.VanillaRef;

/** The sector utilities of {@code p_spec.c} and {@code T_MovePlane} of {@code p_floor.c}. */
final class Sectors {

    /** {@code MAX_ADJOINING_SECTORS}. */
    private static final int MAX_ADJOINING_SECTORS = 20;

    private final World world;
    /** {@code heightlist[MAX_ADJOINING_SECTORS + 2]}. */
    private final int[] heightList = new int[MAX_ADJOINING_SECTORS + 2];

    Sectors(World world) {
        this.world = world;
    }

    /** {@code getSide}. */
    @VanillaRef("p_spec.c:getSide")
    Side getSide(int currentSector, int line, int side) {
        return world.level.sides[world.level.sectors[currentSector].lines[line].sideNum[side]];
    }

    /** {@code getSector}. */
    @VanillaRef("p_spec.c:getSector")
    Sector getSector(int currentSector, int line, int side) {
        return world.level.sides[world.level.sectors[currentSector].lines[line].sideNum[side]].sector;
    }

    /** {@code twoSided}. */
    @VanillaRef("p_spec.c:twoSided")
    boolean twoSided(int sector, int line) {
        return (world.level.sectors[sector].lines[line].flags & LineFlags.TWOSIDED) != 0;
    }

    /** {@code getNextSector}: the sector on the other side of a two-sided line, else null. */
    @VanillaRef("p_spec.c:getNextSector")
    static Sector getNextSector(Line line, Sector sec) {
        if ((line.flags & LineFlags.TWOSIDED) == 0) {
            return null;
        }
        if (line.frontSector == sec) {
            return line.backSector;
        }
        return line.frontSector;
    }

    @VanillaRef("p_spec.c:P_FindLowestFloorSurrounding")
    static int findLowestFloorSurrounding(Sector sec) {
        int floor = sec.floorHeight;
        for (int i = 0; i < sec.lineCount; i++) {
            Sector other = getNextSector(sec.lines[i], sec);
            if (other == null) {
                continue;
            }
            if (other.floorHeight < floor) {
                floor = other.floorHeight;
            }
        }
        return floor;
    }

    @VanillaRef("p_spec.c:P_FindHighestFloorSurrounding")
    static int findHighestFloorSurrounding(Sector sec) {
        int floor = -500 * Fixed.FRACUNIT;
        for (int i = 0; i < sec.lineCount; i++) {
            Sector other = getNextSector(sec.lines[i], sec);
            if (other == null) {
                continue;
            }
            if (other.floorHeight > floor) {
                floor = other.floorHeight;
            }
        }
        return floor;
    }

    /** {@code P_FindNextHighestFloor}, with Chocolate Doom's emulation of the vanilla stack overflow. */
    @VanillaRef("p_spec.c:P_FindNextHighestFloor")
    int findNextHighestFloor(Sector sec, int currentHeight) {
        int h = 0;
        int height = currentHeight;
        for (int i = 0; i < sec.lineCount; i++) {
            Sector other = getNextSector(sec.lines[i], sec);
            if (other == null) {
                continue;
            }
            if (other.floorHeight > height) {
                // Emulation of memory (stack) overflow
                if (h == MAX_ADJOINING_SECTORS + 1) {
                    // VANILLA-BUG: the 21st entry overwrites the local variable "height"
                    height = other.floorHeight;
                } else if (h == MAX_ADJOINING_SECTORS + 2) {
                    // Fatal overflow: game crashes at 22 sectors
                    throw new DoomFatalException(
                            "Sector with more than 22 adjoining sectors. Vanilla will crash here");
                }
                heightList[h++] = other.floorHeight;
            }
        }
        // Find lowest height in list
        if (h == 0) {
            return currentHeight;
        }
        int min = heightList[0];
        for (int i = 1; i < h; i++) {
            if (heightList[i] < min) {
                min = heightList[i];
            }
        }
        return min;
    }

    @VanillaRef("p_spec.c:P_FindLowestCeilingSurrounding")
    static int findLowestCeilingSurrounding(Sector sec) {
        int height = Integer.MAX_VALUE;
        for (int i = 0; i < sec.lineCount; i++) {
            Sector other = getNextSector(sec.lines[i], sec);
            if (other == null) {
                continue;
            }
            if (other.ceilingHeight < height) {
                height = other.ceilingHeight;
            }
        }
        return height;
    }

    @VanillaRef("p_spec.c:P_FindHighestCeilingSurrounding")
    static int findHighestCeilingSurrounding(Sector sec) {
        int height = 0;
        for (int i = 0; i < sec.lineCount; i++) {
            Sector other = getNextSector(sec.lines[i], sec);
            if (other == null) {
                continue;
            }
            if (other.ceilingHeight > height) {
                height = other.ceilingHeight;
            }
        }
        return height;
    }

    /** {@code P_FindSectorFromLineTag}: next sector index after {@code start} with the line's tag, or -1. */
    @VanillaRef("p_spec.c:P_FindSectorFromLineTag")
    int findSectorFromLineTag(Line line, int start) {
        Sector[] sectors = world.level.sectors;
        for (int i = start + 1; i < sectors.length; i++) {
            if (sectors[i].tag == line.tag) {
                return i;
            }
        }
        return -1;
    }

    @VanillaRef("p_spec.c:P_FindMinSurroundingLight")
    static int findMinSurroundingLight(Sector sector, int max) {
        int min = max;
        for (int i = 0; i < sector.lineCount; i++) {
            Sector check = getNextSector(sector.lines[i], sector);
            if (check == null) {
                continue;
            }
            if (check.lightLevel < min) {
                min = check.lightLevel;
            }
        }
        return min;
    }

    /**
     * {@code T_MovePlane}: moves a floor ({@code floorOrCeiling} 0) or ceiling (1) and checks for crushing.
     * {@code crush} is an {@code int} like the original's {@code boolean}: any non-zero value makes
     * {@code P_ChangeSector} crush, only the value 1 is {@code == true}.
     */
    @VanillaRef("p_floor.c:T_MovePlane")
    int movePlane(Sector sector, int speed, int dest, int crush, int floorOrCeiling, int direction) {
        boolean crunch = crush != 0;
        boolean flag;
        int lastpos;
        switch (floorOrCeiling) {
            case 0 -> {
                // FLOOR
                switch (direction) {
                    case -1 -> {
                        // DOWN
                        if (sector.floorHeight - speed < dest) {
                            lastpos = sector.floorHeight;
                            sector.floorHeight = dest;
                            flag = world.physics.changeSector(sector, crunch);
                            if (flag) {
                                sector.floorHeight = lastpos;
                                world.physics.changeSector(sector, crunch);
                                // return crushed;
                            }
                            return SpecialTypes.Result.PASTDEST;
                        } else {
                            lastpos = sector.floorHeight;
                            sector.floorHeight -= speed;
                            flag = world.physics.changeSector(sector, crunch);
                            if (flag) {
                                sector.floorHeight = lastpos;
                                world.physics.changeSector(sector, crunch);
                                return SpecialTypes.Result.CRUSHED;
                            }
                        }
                    }
                    case 1 -> {
                        // UP
                        if (sector.floorHeight + speed > dest) {
                            lastpos = sector.floorHeight;
                            sector.floorHeight = dest;
                            flag = world.physics.changeSector(sector, crunch);
                            if (flag) {
                                sector.floorHeight = lastpos;
                                world.physics.changeSector(sector, crunch);
                                // return crushed;
                            }
                            return SpecialTypes.Result.PASTDEST;
                        } else {
                            // COULD GET CRUSHED
                            lastpos = sector.floorHeight;
                            sector.floorHeight += speed;
                            flag = world.physics.changeSector(sector, crunch);
                            if (flag) {
                                if (crush == 1) {
                                    return SpecialTypes.Result.CRUSHED;
                                }
                                sector.floorHeight = lastpos;
                                world.physics.changeSector(sector, crunch);
                                return SpecialTypes.Result.CRUSHED;
                            }
                        }
                    }
                    default -> {
                    }
                }
            }
            case 1 -> {
                // CEILING
                switch (direction) {
                    case -1 -> {
                        // DOWN
                        if (sector.ceilingHeight - speed < dest) {
                            lastpos = sector.ceilingHeight;
                            sector.ceilingHeight = dest;
                            flag = world.physics.changeSector(sector, crunch);
                            if (flag) {
                                sector.ceilingHeight = lastpos;
                                world.physics.changeSector(sector, crunch);
                                // return crushed;
                            }
                            return SpecialTypes.Result.PASTDEST;
                        } else {
                            // COULD GET CRUSHED
                            lastpos = sector.ceilingHeight;
                            sector.ceilingHeight -= speed;
                            flag = world.physics.changeSector(sector, crunch);
                            if (flag) {
                                if (crush == 1) {
                                    return SpecialTypes.Result.CRUSHED;
                                }
                                sector.ceilingHeight = lastpos;
                                world.physics.changeSector(sector, crunch);
                                return SpecialTypes.Result.CRUSHED;
                            }
                        }
                    }
                    case 1 -> {
                        // UP
                        if (sector.ceilingHeight + speed > dest) {
                            lastpos = sector.ceilingHeight;
                            sector.ceilingHeight = dest;
                            flag = world.physics.changeSector(sector, crunch);
                            if (flag) {
                                sector.ceilingHeight = lastpos;
                                world.physics.changeSector(sector, crunch);
                                // return crushed;
                            }
                            return SpecialTypes.Result.PASTDEST;
                        } else {
                            lastpos = sector.ceilingHeight;
                            sector.ceilingHeight += speed;
                            world.physics.changeSector(sector, crunch);
                            // The original has an "UNUSED" #if 0 block here that would revert the move.
                        }
                    }
                    default -> {
                    }
                }
            }
            default -> {
            }
        }
        return SpecialTypes.Result.OK;
    }
}
