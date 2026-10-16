package doom.game.mapspecial;

import doom.game.actor.SpecialThinker;
import doom.game.actor.TraceSink;
import doom.game.view.SoundId;
import doom.game.world.Line;
import doom.game.world.Sector;
import doom.game.world.World;
import doom.math.DoomFatalException;
import doom.math.Fixed;
import doom.math.annotation.VanillaRef;
import java.util.Arrays;

/** {@code p_plats.c}: platforms (lifts). */
final class Plats {

    /** {@code PLATWAIT}. */
    static final int PLATWAIT = 3;
    /** {@code PLATSPEED}. */
    static final int PLATSPEED = Fixed.FRACUNIT;
    /** {@code MAXPLATS}: more is a fatal error. */
    static final int MAXPLATS = 30;

    private final World world;
    private final Sectors sectors;
    /** {@code activeplats[MAXPLATS]}. */
    private final PlatRaise[] activePlats = new PlatRaise[MAXPLATS];

    Plats(World world, Sectors sectors) {
        this.world = world;
        this.sectors = sectors;
    }

    /** The slots of {@code activeplats[]} (save games). */
    PlatRaise[] slots() {
        return activePlats;
    }

    /** {@code P_SpawnSpecials}: {@code activeplats[i] = NULL}. */
    void reset() {
        Arrays.fill(activePlats, null);
    }

    /** {@code plat_t} with {@code T_PlatRaise} as its function. */
    final class PlatRaise extends SpecialThinker {
        Sector sector;
        int speed;
        int low;
        int high;
        int wait;
        int count;
        int status;
        int oldStatus;
        /** {@code boolean} of the original. */
        boolean crush;
        int tag;
        int type;

        /** A platform in stasis has a NULL function. */
        @Override
        public boolean hasNullFunction() {
            return status == SpecialTypes.PlatStatus.IN_STASIS;
        }

        @Override
        @VanillaRef("p_plats.c:T_PlatRaise")
        protected void think() {
            int res;
            switch (status) {
                case SpecialTypes.PlatStatus.UP -> {
                    res = sectors.movePlane(sector, speed, high, crush ? 1 : 0, 0, 1);

                    if (type == SpecialTypes.Plat.RAISE_AND_CHANGE
                            || type == SpecialTypes.Plat.RAISE_TO_NEAREST_AND_CHANGE) {
                        if ((world.levelTime & 7) == 0) {
                            world.startSectorSound(sector, SoundId.STNMOV);
                        }
                    }

                    if (res == SpecialTypes.Result.CRUSHED && !crush) {
                        count = wait;
                        status = SpecialTypes.PlatStatus.DOWN;
                        world.startSectorSound(sector, SoundId.PSTART);
                    } else {
                        if (res == SpecialTypes.Result.PASTDEST) {
                            count = wait;
                            status = SpecialTypes.PlatStatus.WAITING;
                            world.startSectorSound(sector, SoundId.PSTOP);

                            switch (type) {
                                case SpecialTypes.Plat.BLAZE_DWUS, SpecialTypes.Plat.DOWN_WAIT_UP_STAY,
                                        SpecialTypes.Plat.RAISE_AND_CHANGE,
                                        SpecialTypes.Plat.RAISE_TO_NEAREST_AND_CHANGE -> removeActivePlat(this);
                                default -> {
                                }
                            }
                        }
                    }
                }
                case SpecialTypes.PlatStatus.DOWN -> {
                    res = sectors.movePlane(sector, speed, low, 0, 0, -1);
                    if (res == SpecialTypes.Result.PASTDEST) {
                        count = wait;
                        status = SpecialTypes.PlatStatus.WAITING;
                        world.startSectorSound(sector, SoundId.PSTOP);
                    }
                }
                case SpecialTypes.PlatStatus.WAITING -> {
                    if (--count == 0) {
                        if (sector.floorHeight == low) {
                            status = SpecialTypes.PlatStatus.UP;
                        } else {
                            status = SpecialTypes.PlatStatus.DOWN;
                        }
                        world.startSectorSound(sector, SoundId.PSTART);
                    }
                }
                default -> {
                    // in_stasis
                }
            }
        }

        @Override
        public int traceCode() {
            return hasNullFunction() ? 104 : 4;
        }

        @Override
        public void traceFields(TraceSink sink) {
            sink.put(type);
            sink.put(sector.index);
            sink.put(speed);
            sink.put(low);
            sink.put(high);
            sink.put(wait);
            sink.put(count);
            sink.put(status);
            sink.put(oldStatus);
            sink.put(crush ? 1 : 0);
            sink.put(tag);
        }
    }

    /** {@code EV_DoPlat}: do platforms; {@code amount} is only used for some of them. */
    @VanillaRef("p_plats.c:EV_DoPlat")
    boolean doPlat(Line line, int type, int amount) {
        int secnum = -1;
        boolean rtn = false;

        // Activate all <type> plats that are in_stasis
        if (type == SpecialTypes.Plat.PERPETUAL_RAISE) {
            activateInStasis(line.tag);
        }

        while ((secnum = sectors.findSectorFromLineTag(line, secnum)) >= 0) {
            Sector sec = world.level.sectors[secnum];

            if (sec.specialData != null) {
                continue;
            }

            // Find lowest & highest floors around sector
            rtn = true;
            PlatRaise plat = new PlatRaise();
            world.thinkers.add(plat);

            plat.type = type;
            plat.sector = sec;
            plat.sector.specialData = plat;
            plat.crush = false;
            plat.tag = line.tag;

            switch (type) {
                case SpecialTypes.Plat.RAISE_TO_NEAREST_AND_CHANGE -> {
                    plat.speed = PLATSPEED / 2;
                    sec.floorPic = world.level.sides[line.sideNum[0]].sector.floorPic;
                    plat.high = sectors.findNextHighestFloor(sec, sec.floorHeight);
                    plat.wait = 0;
                    plat.status = SpecialTypes.PlatStatus.UP;
                    // NO MORE DAMAGE, IF APPLICABLE
                    sec.special = 0;

                    world.startSectorSound(sec, SoundId.STNMOV);
                }
                case SpecialTypes.Plat.RAISE_AND_CHANGE -> {
                    plat.speed = PLATSPEED / 2;
                    sec.floorPic = world.level.sides[line.sideNum[0]].sector.floorPic;
                    plat.high = sec.floorHeight + amount * Fixed.FRACUNIT;
                    plat.wait = 0;
                    plat.status = SpecialTypes.PlatStatus.UP;

                    world.startSectorSound(sec, SoundId.STNMOV);
                }
                case SpecialTypes.Plat.DOWN_WAIT_UP_STAY -> {
                    plat.speed = PLATSPEED * 4;
                    plat.low = Sectors.findLowestFloorSurrounding(sec);

                    if (plat.low > sec.floorHeight) {
                        plat.low = sec.floorHeight;
                    }

                    plat.high = sec.floorHeight;
                    plat.wait = World.TICRATE * PLATWAIT;
                    plat.status = SpecialTypes.PlatStatus.DOWN;
                    world.startSectorSound(sec, SoundId.PSTART);
                }
                case SpecialTypes.Plat.BLAZE_DWUS -> {
                    plat.speed = PLATSPEED * 8;
                    plat.low = Sectors.findLowestFloorSurrounding(sec);

                    if (plat.low > sec.floorHeight) {
                        plat.low = sec.floorHeight;
                    }

                    plat.high = sec.floorHeight;
                    plat.wait = World.TICRATE * PLATWAIT;
                    plat.status = SpecialTypes.PlatStatus.DOWN;
                    world.startSectorSound(sec, SoundId.PSTART);
                }
                case SpecialTypes.Plat.PERPETUAL_RAISE -> {
                    plat.speed = PLATSPEED;
                    plat.low = Sectors.findLowestFloorSurrounding(sec);

                    if (plat.low > sec.floorHeight) {
                        plat.low = sec.floorHeight;
                    }

                    plat.high = Sectors.findHighestFloorSurrounding(sec);

                    if (plat.high < sec.floorHeight) {
                        plat.high = sec.floorHeight;
                    }

                    plat.wait = World.TICRATE * PLATWAIT;
                    plat.status = world.rng.game() & 1;

                    world.startSectorSound(sec, SoundId.PSTART);
                }
                default -> {
                }
            }
            addActivePlat(plat);
        }
        return rtn;
    }

    /** {@code P_ActivateInStasis}. */
    @VanillaRef("p_plats.c:P_ActivateInStasis")
    void activateInStasis(int tag) {
        for (int i = 0; i < MAXPLATS; i++) {
            if (activePlats[i] != null && activePlats[i].tag == tag
                    && activePlats[i].status == SpecialTypes.PlatStatus.IN_STASIS) {
                activePlats[i].status = activePlats[i].oldStatus;
            }
        }
    }

    /** {@code EV_StopPlat}. */
    @VanillaRef("p_plats.c:EV_StopPlat")
    void stopPlat(Line line) {
        for (int j = 0; j < MAXPLATS; j++) {
            if (activePlats[j] != null && activePlats[j].status != SpecialTypes.PlatStatus.IN_STASIS
                    && activePlats[j].tag == line.tag) {
                activePlats[j].oldStatus = activePlats[j].status;
                activePlats[j].status = SpecialTypes.PlatStatus.IN_STASIS;
            }
        }
    }

    /** {@code P_AddActivePlat}. */
    @VanillaRef("p_plats.c:P_AddActivePlat")
    void addActivePlat(PlatRaise plat) {
        for (int i = 0; i < MAXPLATS; i++) {
            if (activePlats[i] == null) {
                activePlats[i] = plat;
                return;
            }
        }
        throw new DoomFatalException("P_AddActivePlat: no more plats!");
    }

    /** {@code P_RemoveActivePlat}. */
    @VanillaRef("p_plats.c:P_RemoveActivePlat")
    void removeActivePlat(PlatRaise plat) {
        for (int i = 0; i < MAXPLATS; i++) {
            if (plat == activePlats[i]) {
                activePlats[i].sector.specialData = null;
                world.thinkers.remove(activePlats[i]);
                activePlats[i] = null;
                return;
            }
        }
        throw new DoomFatalException("P_RemoveActivePlat: can't find plat!");
    }
}
