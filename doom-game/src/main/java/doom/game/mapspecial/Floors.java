package doom.game.mapspecial;

import doom.game.actor.SpecialThinker;
import doom.game.actor.TraceSink;
import doom.game.view.LineFlags;
import doom.game.view.SoundId;
import doom.game.world.Line;
import doom.game.world.Sector;
import doom.game.world.Side;
import doom.game.world.World;
import doom.math.Fixed;
import doom.math.annotation.VanillaRef;

/** {@code p_floor.c} (moving floors, stairs) and the donut of {@code p_spec.c}. */
final class Floors {

    /** {@code FLOORSPEED}. */
    static final int FLOORSPEED = Fixed.FRACUNIT;
    /** {@code STAIRS_UNINITIALIZED_CRUSH_FIELD_VALUE} (e6y). */
    private static final int STAIRS_UNINITIALIZED_CRUSH_FIELD_VALUE = 10;
    /** {@code DONUT_FLOORHEIGHT_DEFAULT}: the value read from 0000:0000 under Windows 98. */
    private static final int DONUT_FLOORHEIGHT_DEFAULT = 0x00000000;
    /** {@code DONUT_FLOORPIC_DEFAULT}. */
    private static final int DONUT_FLOORPIC_DEFAULT = 0x16;

    private final World world;
    private final Sectors sectors;

    Floors(World world, Sectors sectors) {
        this.world = world;
        this.sectors = sectors;
    }

    /** {@code floormove_t} with {@code T_MoveFloor} as its function. */
    final class FloorMove extends SpecialThinker {
        int type;
        /** {@code boolean} of the original: an int, because stairs leave 10 in it. */
        int crush;
        Sector sector;
        int direction;
        int newSpecial;
        /** {@code short texture}. */
        int texture;
        int floorDestHeight;
        int speed;

        @Override
        @VanillaRef("p_floor.c:T_MoveFloor")
        protected void think() {
            int res = sectors.movePlane(sector, speed, floorDestHeight, crush, 0, direction);

            if ((world.levelTime & 7) == 0) {
                world.startSectorSound(sector, SoundId.STNMOV);
            }

            if (res == SpecialTypes.Result.PASTDEST) {
                sector.specialData = null;

                if (direction == 1) {
                    if (type == SpecialTypes.Floor.DONUT_RAISE) {
                        sector.special = newSpecial;
                        sector.floorPic = texture;
                    }
                } else if (direction == -1) {
                    if (type == SpecialTypes.Floor.LOWER_AND_CHANGE) {
                        sector.special = newSpecial;
                        sector.floorPic = texture;
                    }
                }
                world.thinkers.remove(this);
                world.startSectorSound(sector, SoundId.PSTOP);
            }
        }

        @Override
        public int traceCode() {
            return 3;
        }

        @Override
        public void traceFields(TraceSink sink) {
            sink.put(type);
            sink.put(crush != 0 ? 1 : 0);
            sink.put(sector.index);
            sink.put(direction);
            sink.put(newSpecial);
            sink.put((short) texture);
            sink.put(floorDestHeight);
            sink.put(speed);
        }
    }

    /** {@code EV_DoFloor}: handle floor types. */
    @VanillaRef("p_floor.c:EV_DoFloor")
    boolean doFloor(Line line, int floorType) {
        int secnum = -1;
        boolean rtn = false;

        while ((secnum = sectors.findSectorFromLineTag(line, secnum)) >= 0) {
            Sector sec = world.level.sectors[secnum];

            // ALREADY MOVING?  IF SO, KEEP GOING...
            if (sec.specialData != null) {
                continue;
            }

            // new floor thinker
            rtn = true;
            FloorMove floor = new FloorMove();
            world.thinkers.add(floor);
            sec.specialData = floor;
            floor.type = floorType;
            floor.crush = 0;
            switch (floorType) {
                case SpecialTypes.Floor.LOWER_FLOOR -> {
                    floor.direction = -1;
                    floor.sector = sec;
                    floor.speed = FLOORSPEED;
                    floor.floorDestHeight = Sectors.findHighestFloorSurrounding(sec);
                }
                case SpecialTypes.Floor.LOWER_FLOOR_TO_LOWEST -> {
                    floor.direction = -1;
                    floor.sector = sec;
                    floor.speed = FLOORSPEED;
                    floor.floorDestHeight = Sectors.findLowestFloorSurrounding(sec);
                }
                case SpecialTypes.Floor.TURBO_LOWER -> {
                    floor.direction = -1;
                    floor.sector = sec;
                    floor.speed = FLOORSPEED * 4;
                    floor.floorDestHeight = Sectors.findHighestFloorSurrounding(sec);
                    // gameversion <= exe_doom_1_2 is never true here (every supported version is 1.9 or later)
                    if (floor.floorDestHeight != sec.floorHeight) {
                        floor.floorDestHeight += 8 * Fixed.FRACUNIT;
                    }
                }
                case SpecialTypes.Floor.RAISE_FLOOR_CRUSH, SpecialTypes.Floor.RAISE_FLOOR -> {
                    if (floorType == SpecialTypes.Floor.RAISE_FLOOR_CRUSH) {
                        floor.crush = 1;
                    }
                    floor.direction = 1;
                    floor.sector = sec;
                    floor.speed = FLOORSPEED;
                    floor.floorDestHeight = Sectors.findLowestCeilingSurrounding(sec);
                    if (floor.floorDestHeight > sec.ceilingHeight) {
                        floor.floorDestHeight = sec.ceilingHeight;
                    }
                    floor.floorDestHeight -= (8 * Fixed.FRACUNIT)
                            * (floorType == SpecialTypes.Floor.RAISE_FLOOR_CRUSH ? 1 : 0);
                }
                case SpecialTypes.Floor.RAISE_FLOOR_TURBO -> {
                    floor.direction = 1;
                    floor.sector = sec;
                    floor.speed = FLOORSPEED * 4;
                    floor.floorDestHeight = sectors.findNextHighestFloor(sec, sec.floorHeight);
                }
                case SpecialTypes.Floor.RAISE_FLOOR_TO_NEAREST -> {
                    floor.direction = 1;
                    floor.sector = sec;
                    floor.speed = FLOORSPEED;
                    floor.floorDestHeight = sectors.findNextHighestFloor(sec, sec.floorHeight);
                }
                case SpecialTypes.Floor.RAISE_FLOOR_24 -> {
                    floor.direction = 1;
                    floor.sector = sec;
                    floor.speed = FLOORSPEED;
                    floor.floorDestHeight = floor.sector.floorHeight + 24 * Fixed.FRACUNIT;
                }
                case SpecialTypes.Floor.RAISE_FLOOR_512 -> {
                    floor.direction = 1;
                    floor.sector = sec;
                    floor.speed = FLOORSPEED;
                    floor.floorDestHeight = floor.sector.floorHeight + 512 * Fixed.FRACUNIT;
                }
                case SpecialTypes.Floor.RAISE_FLOOR_24_AND_CHANGE -> {
                    floor.direction = 1;
                    floor.sector = sec;
                    floor.speed = FLOORSPEED;
                    floor.floorDestHeight = floor.sector.floorHeight + 24 * Fixed.FRACUNIT;
                    sec.floorPic = line.frontSector.floorPic;
                    sec.special = line.frontSector.special;
                }
                case SpecialTypes.Floor.RAISE_TO_TEXTURE -> {
                    int minsize = Integer.MAX_VALUE;

                    floor.direction = 1;
                    floor.sector = sec;
                    floor.speed = FLOORSPEED;
                    for (int i = 0; i < sec.lineCount; i++) {
                        if (sectors.twoSided(secnum, i)) {
                            Side side = sectors.getSide(secnum, i, 0);
                            if (side.bottomTexture >= 0) {
                                if (world.textures.height(side.bottomTexture) < minsize) {
                                    minsize = world.textures.height(side.bottomTexture);
                                }
                            }
                            side = sectors.getSide(secnum, i, 1);
                            if (side.bottomTexture >= 0) {
                                if (world.textures.height(side.bottomTexture) < minsize) {
                                    minsize = world.textures.height(side.bottomTexture);
                                }
                            }
                        }
                    }
                    floor.floorDestHeight = floor.sector.floorHeight + minsize;
                }
                case SpecialTypes.Floor.LOWER_AND_CHANGE -> {
                    floor.direction = -1;
                    floor.sector = sec;
                    floor.speed = FLOORSPEED;
                    floor.floorDestHeight = Sectors.findLowestFloorSurrounding(sec);
                    floor.texture = sec.floorPic;

                    // NOTE: "sec" is reassigned inside the loop, also when nothing matches, and then bounds the loop
                    for (int i = 0; i < sec.lineCount; i++) {
                        if (sectors.twoSided(secnum, i)) {
                            if (sectors.getSide(secnum, i, 0).sector.index == secnum) {
                                sec = sectors.getSector(secnum, i, 1);
                                if (sec.floorHeight == floor.floorDestHeight) {
                                    floor.texture = sec.floorPic;
                                    floor.newSpecial = sec.special;
                                    break;
                                }
                            } else {
                                sec = sectors.getSector(secnum, i, 0);
                                if (sec.floorHeight == floor.floorDestHeight) {
                                    floor.texture = sec.floorPic;
                                    floor.newSpecial = sec.special;
                                    break;
                                }
                            }
                        }
                    }
                }
                default -> {
                }
            }
        }
        return rtn;
    }

    /** {@code EV_BuildStairs}: build a staircase. */
    @VanillaRef("p_floor.c:EV_BuildStairs")
    boolean buildStairs(Line line, int type) {
        int secnum = -1;
        boolean rtn = false;
        int stairsize = 0;
        int speed = 0;

        while ((secnum = sectors.findSectorFromLineTag(line, secnum)) >= 0) {
            Sector sec = world.level.sectors[secnum];

            // ALREADY MOVING?  IF SO, KEEP GOING...
            if (sec.specialData != null) {
                continue;
            }

            // new floor thinker
            rtn = true;
            FloorMove floor = new FloorMove();
            world.thinkers.add(floor);
            sec.specialData = floor;
            floor.direction = 1;
            floor.sector = sec;
            switch (type) {
                case SpecialTypes.Stair.BUILD_8 -> {
                    speed = FLOORSPEED / 4;
                    stairsize = 8 * Fixed.FRACUNIT;
                }
                case SpecialTypes.Stair.TURBO_16 -> {
                    speed = FLOORSPEED * 4;
                    stairsize = 16 * Fixed.FRACUNIT;
                }
                default -> {
                }
            }
            floor.speed = speed;
            int height = sec.floorHeight + stairsize;
            floor.floorDestHeight = height;
            // Initialize
            floor.type = SpecialTypes.Floor.LOWER_FLOOR;
            // e6y: the uninitialized crush field is any value other than 0 or 1
            floor.crush = STAIRS_UNINITIALIZED_CRUSH_FIELD_VALUE;

            int texture = sec.floorPic;

            // Find next sector to raise
            // 1. Find 2-sided line with same sector side[0]
            // 2. Other side is the next sector to raise
            boolean ok;
            do {
                ok = false;
                for (int i = 0; i < sec.lineCount; i++) {
                    if ((sec.lines[i].flags & LineFlags.TWOSIDED) == 0) {
                        continue;
                    }

                    Sector tsec = sec.lines[i].frontSector;
                    int newsecnum = tsec.index;

                    if (secnum != newsecnum) {
                        continue;
                    }

                    tsec = sec.lines[i].backSector;
                    newsecnum = tsec.index;

                    if (tsec.floorPic != texture) {
                        continue;
                    }

                    height += stairsize;

                    if (tsec.specialData != null) {
                        continue;
                    }

                    sec = tsec;
                    secnum = newsecnum;
                    floor = new FloorMove();

                    world.thinkers.add(floor);

                    sec.specialData = floor;
                    floor.direction = 1;
                    floor.sector = sec;
                    floor.speed = speed;
                    floor.floorDestHeight = height;
                    // Initialize
                    floor.type = SpecialTypes.Floor.LOWER_FLOOR;
                    floor.crush = STAIRS_UNINITIALIZED_CRUSH_FIELD_VALUE;
                    ok = true;
                    break;
                }
            } while (ok);
        }
        return rtn;
    }

    /** {@code EV_DoDonut}: the donut special (switch 9). */
    @VanillaRef("p_spec.c:EV_DoDonut")
    boolean doDonut(Line line) {
        int secnum = -1;
        boolean rtn = false;

        while ((secnum = sectors.findSectorFromLineTag(line, secnum)) >= 0) {
            Sector s1 = world.level.sectors[secnum];

            // ALREADY MOVING?  IF SO, KEEP GOING...
            if (s1.specialData != null) {
                continue;
            }

            rtn = true;
            Sector s2 = Sectors.getNextSector(s1.lines[0], s1);

            // Vanilla Doom does not check if the linedef is one sided: it reads invalid memory. Chocolate Doom
            // only prints a warning and returns.
            if (s2 == null) {
                break;
            }

            for (int i = 0; i < s2.lineCount; i++) {
                Sector s3 = s2.lines[i].backSector;

                if (s3 == s1) {
                    continue;
                }

                int s3FloorHeight;
                int s3FloorPic;
                if (s3 == null) {
                    // e6y: s3 is NULL, so s3->floorheight is an int at 0000:0000 and s3->floorpic a short at
                    // 0000:0008. DonutOverrun emulates the Windows 98 values.
                    s3FloorHeight = DONUT_FLOORHEIGHT_DEFAULT;
                    s3FloorPic = DONUT_FLOORPIC_DEFAULT;
                } else {
                    s3FloorHeight = s3.floorHeight;
                    s3FloorPic = s3.floorPic;
                }

                // Spawn rising slime
                FloorMove floor = new FloorMove();
                world.thinkers.add(floor);
                s2.specialData = floor;
                floor.type = SpecialTypes.Floor.DONUT_RAISE;
                floor.crush = 0;
                floor.direction = 1;
                floor.sector = s2;
                floor.speed = FLOORSPEED / 2;
                floor.texture = s3FloorPic;
                floor.newSpecial = 0;
                floor.floorDestHeight = s3FloorHeight;

                // Spawn lowering donut-hole
                floor = new FloorMove();
                world.thinkers.add(floor);
                s1.specialData = floor;
                floor.type = SpecialTypes.Floor.LOWER_FLOOR;
                floor.crush = 0;
                floor.direction = -1;
                floor.sector = s1;
                floor.speed = FLOORSPEED / 2;
                floor.floorDestHeight = s3FloorHeight;
                break;
            }
        }
        return rtn;
    }
}
