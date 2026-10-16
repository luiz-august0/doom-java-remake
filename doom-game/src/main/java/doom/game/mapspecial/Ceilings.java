package doom.game.mapspecial;

import doom.game.actor.SpecialThinker;
import doom.game.actor.TraceSink;
import doom.game.view.SoundId;
import doom.game.world.Line;
import doom.game.world.Sector;
import doom.game.world.World;
import doom.math.Fixed;
import doom.math.annotation.VanillaRef;
import java.util.Arrays;

/** {@code p_ceilng.c}: moving ceilings and crushers. */
final class Ceilings {

    /** {@code CEILSPEED}. */
    static final int CEILSPEED = Fixed.FRACUNIT;
    /** {@code CEILWAIT}. */
    static final int CEILWAIT = 150;
    /** {@code MAXCEILINGS}: ceilings beyond it are silently left untracked (they still move). */
    static final int MAXCEILINGS = 30;

    private final World world;
    private final Sectors sectors;
    /** {@code activeceilings[MAXCEILINGS]}. */
    private final CeilingMove[] activeCeilings = new CeilingMove[MAXCEILINGS];

    Ceilings(World world, Sectors sectors) {
        this.world = world;
        this.sectors = sectors;
    }

    /** The slots of {@code activeceilings[]} (save games). */
    CeilingMove[] slots() {
        return activeCeilings;
    }

    /** {@code P_SpawnSpecials}: {@code activeceilings[i] = NULL}. */
    void reset() {
        Arrays.fill(activeCeilings, null);
    }

    /** {@code ceiling_t} with {@code T_MoveCeiling} as its function. */
    final class CeilingMove extends SpecialThinker {
        int type;
        Sector sector;
        int bottomHeight;
        int topHeight;
        int speed;
        /** {@code boolean} of the original. */
        boolean crush;
        /** 1 = up, 0 = waiting (in stasis), -1 = down. */
        int direction;
        int tag;
        int oldDirection;
        /** The thinker function is NULL (in stasis). */
        boolean nullFunction;

        @Override
        public boolean hasNullFunction() {
            return nullFunction;
        }

        @Override
        @VanillaRef("p_ceilng.c:T_MoveCeiling")
        protected void think() {
            int res;
            switch (direction) {
                case 0 -> {
                    // IN STASIS
                }
                case 1 -> {
                    // UP
                    res = sectors.movePlane(sector, speed, topHeight, 0, 1, direction);

                    if ((world.levelTime & 7) == 0) {
                        if (type != SpecialTypes.Ceiling.SILENT_CRUSH_AND_RAISE) {
                            world.startSectorSound(sector, SoundId.STNMOV);
                        }
                    }

                    if (res == SpecialTypes.Result.PASTDEST) {
                        switch (type) {
                            case SpecialTypes.Ceiling.RAISE_TO_HIGHEST -> removeActiveCeiling(this);
                            case SpecialTypes.Ceiling.SILENT_CRUSH_AND_RAISE -> {
                                world.startSectorSound(sector, SoundId.PSTOP);
                                direction = -1;
                            }
                            case SpecialTypes.Ceiling.FAST_CRUSH_AND_RAISE,
                                    SpecialTypes.Ceiling.CRUSH_AND_RAISE -> direction = -1;
                            default -> {
                            }
                        }
                    }
                }
                case -1 -> {
                    // DOWN
                    res = sectors.movePlane(sector, speed, bottomHeight, crush ? 1 : 0, 1, direction);

                    if ((world.levelTime & 7) == 0) {
                        if (type != SpecialTypes.Ceiling.SILENT_CRUSH_AND_RAISE) {
                            world.startSectorSound(sector, SoundId.STNMOV);
                        }
                    }

                    if (res == SpecialTypes.Result.PASTDEST) {
                        switch (type) {
                            case SpecialTypes.Ceiling.SILENT_CRUSH_AND_RAISE -> {
                                world.startSectorSound(sector, SoundId.PSTOP);
                                speed = CEILSPEED;
                                direction = 1;
                            }
                            case SpecialTypes.Ceiling.CRUSH_AND_RAISE -> {
                                speed = CEILSPEED;
                                direction = 1;
                            }
                            case SpecialTypes.Ceiling.FAST_CRUSH_AND_RAISE -> direction = 1;
                            case SpecialTypes.Ceiling.LOWER_AND_CRUSH, SpecialTypes.Ceiling.LOWER_TO_FLOOR ->
                                    removeActiveCeiling(this);
                            default -> {
                            }
                        }
                    } else { // ( res != pastdest )
                        if (res == SpecialTypes.Result.CRUSHED) {
                            switch (type) {
                                case SpecialTypes.Ceiling.SILENT_CRUSH_AND_RAISE,
                                        SpecialTypes.Ceiling.CRUSH_AND_RAISE,
                                        SpecialTypes.Ceiling.LOWER_AND_CRUSH -> speed = CEILSPEED / 8;
                                default -> {
                                }
                            }
                        }
                    }
                }
                default -> {
                }
            }
        }

        @Override
        public int traceCode() {
            return nullFunction ? 102 : 2;
        }

        @Override
        public void traceFields(TraceSink sink) {
            sink.put(type);
            sink.put(sector.index);
            sink.put(bottomHeight);
            sink.put(topHeight);
            sink.put(speed);
            sink.put(crush ? 1 : 0);
            sink.put(direction);
            sink.put(tag);
            sink.put(oldDirection);
        }
    }

    /** {@code EV_DoCeiling}: move a ceiling up/down and all around. */
    @VanillaRef("p_ceilng.c:EV_DoCeiling")
    boolean doCeiling(Line line, int type) {
        int secnum = -1;
        boolean rtn = false;

        // Reactivate in-stasis ceilings...for certain types.
        switch (type) {
            case SpecialTypes.Ceiling.FAST_CRUSH_AND_RAISE, SpecialTypes.Ceiling.SILENT_CRUSH_AND_RAISE,
                    SpecialTypes.Ceiling.CRUSH_AND_RAISE -> activateInStasisCeiling(line);
            default -> {
            }
        }

        while ((secnum = sectors.findSectorFromLineTag(line, secnum)) >= 0) {
            Sector sec = world.level.sectors[secnum];
            if (sec.specialData != null) {
                continue;
            }

            // new door thinker
            rtn = true;
            CeilingMove ceiling = new CeilingMove();
            world.thinkers.add(ceiling);
            sec.specialData = ceiling;
            ceiling.sector = sec;
            ceiling.crush = false;

            switch (type) {
                case SpecialTypes.Ceiling.FAST_CRUSH_AND_RAISE -> {
                    ceiling.crush = true;
                    ceiling.topHeight = sec.ceilingHeight;
                    ceiling.bottomHeight = sec.floorHeight + (8 * Fixed.FRACUNIT);
                    ceiling.direction = -1;
                    ceiling.speed = CEILSPEED * 2;
                }
                case SpecialTypes.Ceiling.SILENT_CRUSH_AND_RAISE, SpecialTypes.Ceiling.CRUSH_AND_RAISE,
                        SpecialTypes.Ceiling.LOWER_AND_CRUSH, SpecialTypes.Ceiling.LOWER_TO_FLOOR -> {
                    if (type == SpecialTypes.Ceiling.SILENT_CRUSH_AND_RAISE
                            || type == SpecialTypes.Ceiling.CRUSH_AND_RAISE) {
                        ceiling.crush = true;
                        ceiling.topHeight = sec.ceilingHeight;
                    }
                    ceiling.bottomHeight = sec.floorHeight;
                    if (type != SpecialTypes.Ceiling.LOWER_TO_FLOOR) {
                        ceiling.bottomHeight += 8 * Fixed.FRACUNIT;
                    }
                    ceiling.direction = -1;
                    ceiling.speed = CEILSPEED;
                }
                case SpecialTypes.Ceiling.RAISE_TO_HIGHEST -> {
                    ceiling.topHeight = Sectors.findHighestCeilingSurrounding(sec);
                    ceiling.direction = 1;
                    ceiling.speed = CEILSPEED;
                }
                default -> {
                }
            }

            ceiling.tag = sec.tag;
            ceiling.type = type;
            addActiveCeiling(ceiling);
        }
        return rtn;
    }

    /** {@code P_AddActiveCeiling}: when all slots are taken the ceiling is not tracked (no error). */
    @VanillaRef("p_ceilng.c:P_AddActiveCeiling")
    void addActiveCeiling(CeilingMove c) {
        for (int i = 0; i < MAXCEILINGS; i++) {
            if (activeCeilings[i] == null) {
                activeCeilings[i] = c;
                return;
            }
        }
    }

    /** {@code P_RemoveActiveCeiling}. */
    @VanillaRef("p_ceilng.c:P_RemoveActiveCeiling")
    void removeActiveCeiling(CeilingMove c) {
        for (int i = 0; i < MAXCEILINGS; i++) {
            if (activeCeilings[i] == c) {
                activeCeilings[i].sector.specialData = null;
                world.thinkers.remove(activeCeilings[i]);
                activeCeilings[i] = null;
                break;
            }
        }
    }

    /** {@code P_ActivateInStasisCeiling}: restart a ceiling that is in stasis. */
    @VanillaRef("p_ceilng.c:P_ActivateInStasisCeiling")
    void activateInStasisCeiling(Line line) {
        for (int i = 0; i < MAXCEILINGS; i++) {
            if (activeCeilings[i] != null && activeCeilings[i].tag == line.tag && activeCeilings[i].direction == 0) {
                activeCeilings[i].direction = activeCeilings[i].oldDirection;
                activeCeilings[i].nullFunction = false;
            }
        }
    }

    /** {@code EV_CeilingCrushStop}: stop a ceiling from crushing. */
    @VanillaRef("p_ceilng.c:EV_CeilingCrushStop")
    boolean ceilingCrushStop(Line line) {
        boolean rtn = false;
        for (int i = 0; i < MAXCEILINGS; i++) {
            if (activeCeilings[i] != null && activeCeilings[i].tag == line.tag && activeCeilings[i].direction != 0) {
                activeCeilings[i].oldDirection = activeCeilings[i].direction;
                activeCeilings[i].nullFunction = true;
                activeCeilings[i].direction = 0; // in-stasis
                rtn = true;
            }
        }
        return rtn;
    }
}
