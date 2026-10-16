package doom.game.mapspecial;

import doom.game.actor.SpecialThinker;
import doom.game.actor.TraceSink;
import doom.game.world.Line;
import doom.game.world.Sector;
import doom.game.world.World;
import doom.math.annotation.VanillaRef;

/** {@code p_lights.c}: flickering, flashing, strobing and glowing lights. */
final class Lights {

    /** {@code GLOWSPEED}. */
    static final int GLOWSPEED = 8;
    /** {@code STROBEBRIGHT}. */
    static final int STROBEBRIGHT = 5;
    /** {@code FASTDARK}. */
    static final int FASTDARK = 15;
    /** {@code SLOWDARK}. */
    static final int SLOWDARK = 35;

    private final World world;
    private final Sectors sectors;

    Lights(World world, Sectors sectors) {
        this.world = world;
        this.sectors = sectors;
    }

    /** {@code fireflicker_t} with {@code T_FireFlicker} as its function. */
    final class FireFlicker extends SpecialThinker {
        Sector sector;
        int count;
        int maxLight;
        int minLight;

        @Override
        @VanillaRef("p_lights.c:T_FireFlicker")
        protected void think() {
            if (--count != 0) {
                return;
            }

            int amount = (world.rng.game() & 3) * 16;

            if (sector.lightLevel - amount < minLight) {
                sector.lightLevel = minLight;
            } else {
                sector.lightLevel = maxLight - amount;
            }

            count = 4;
        }

        @Override
        public int traceCode() {
            return 8;
        }

        @Override
        public void traceFields(TraceSink sink) {
            sink.put(sector.index);
            sink.put(count);
            sink.put(maxLight);
            sink.put(minLight);
        }
    }

    /** {@code P_SpawnFireFlicker}. */
    @VanillaRef("p_lights.c:P_SpawnFireFlicker")
    void spawnFireFlicker(Sector sector) {
        // Note that we are resetting sector attributes. Nothing special about it during gameplay.
        sector.special = 0;

        FireFlicker flick = new FireFlicker();
        world.thinkers.add(flick);
        flick.sector = sector;
        flick.maxLight = sector.lightLevel;
        flick.minLight = Sectors.findMinSurroundingLight(sector, sector.lightLevel) + 16;
        flick.count = 4;
    }

    /** {@code lightflash_t} with {@code T_LightFlash} as its function. */
    final class LightFlash extends SpecialThinker {
        Sector sector;
        int count;
        int maxLight;
        int minLight;
        int maxTime;
        int minTime;

        @Override
        @VanillaRef("p_lights.c:T_LightFlash")
        protected void think() {
            if (--count != 0) {
                return;
            }

            if (sector.lightLevel == maxLight) {
                sector.lightLevel = minLight;
                count = (world.rng.game() & minTime) + 1;
            } else {
                sector.lightLevel = maxLight;
                count = (world.rng.game() & maxTime) + 1;
            }
        }

        @Override
        public int traceCode() {
            return 5;
        }

        @Override
        public void traceFields(TraceSink sink) {
            sink.put(sector.index);
            sink.put(count);
            sink.put(maxLight);
            sink.put(minLight);
            sink.put(maxTime);
            sink.put(minTime);
        }
    }

    /** {@code P_SpawnLightFlash}: broken light flashing. */
    @VanillaRef("p_lights.c:P_SpawnLightFlash")
    void spawnLightFlash(Sector sector) {
        // nothing special about it during gameplay
        sector.special = 0;

        LightFlash flash = new LightFlash();
        world.thinkers.add(flash);
        flash.sector = sector;
        flash.maxLight = sector.lightLevel;
        flash.minLight = Sectors.findMinSurroundingLight(sector, sector.lightLevel);
        flash.maxTime = 64;
        flash.minTime = 7;
        flash.count = (world.rng.game() & flash.maxTime) + 1;
    }

    /** {@code strobe_t} with {@code T_StrobeFlash} as its function. */
    final class StrobeFlash extends SpecialThinker {
        Sector sector;
        int count;
        int minLight;
        int maxLight;
        int darkTime;
        int brightTime;

        @Override
        @VanillaRef("p_lights.c:T_StrobeFlash")
        protected void think() {
            if (--count != 0) {
                return;
            }

            if (sector.lightLevel == minLight) {
                sector.lightLevel = maxLight;
                count = brightTime;
            } else {
                sector.lightLevel = minLight;
                count = darkTime;
            }
        }

        @Override
        public int traceCode() {
            return 6;
        }

        @Override
        public void traceFields(TraceSink sink) {
            sink.put(sector.index);
            sink.put(count);
            sink.put(minLight);
            sink.put(maxLight);
            sink.put(darkTime);
            sink.put(brightTime);
        }
    }

    /** {@code P_SpawnStrobeFlash}. */
    @VanillaRef("p_lights.c:P_SpawnStrobeFlash")
    void spawnStrobeFlash(Sector sector, int fastOrSlow, int inSync) {
        StrobeFlash flash = new StrobeFlash();
        world.thinkers.add(flash);
        flash.sector = sector;
        flash.darkTime = fastOrSlow;
        flash.brightTime = STROBEBRIGHT;
        flash.maxLight = sector.lightLevel;
        flash.minLight = Sectors.findMinSurroundingLight(sector, sector.lightLevel);

        if (flash.minLight == flash.maxLight) {
            flash.minLight = 0;
        }

        // nothing special about it during gameplay
        sector.special = 0;

        if (inSync == 0) {
            flash.count = (world.rng.game() & 7) + 1;
        } else {
            flash.count = 1;
        }
    }

    /** {@code EV_StartLightStrobing}: start strobing lights (usually from a trigger). */
    @VanillaRef("p_lights.c:EV_StartLightStrobing")
    void startLightStrobing(Line line) {
        int secnum = -1;
        while ((secnum = sectors.findSectorFromLineTag(line, secnum)) >= 0) {
            Sector sec = world.level.sectors[secnum];
            if (sec.specialData != null) {
                continue;
            }

            spawnStrobeFlash(sec, SLOWDARK, 0);
        }
    }

    /** {@code EV_TurnTagLightsOff}: turn the lights of the line's tag off. */
    @VanillaRef("p_lights.c:EV_TurnTagLightsOff")
    void turnTagLightsOff(Line line) {
        for (Sector sector : world.level.sectors) {
            if (sector.tag == line.tag) {
                int min = sector.lightLevel;
                for (int i = 0; i < sector.lineCount; i++) {
                    Sector tsec = Sectors.getNextSector(sector.lines[i], sector);
                    if (tsec == null) {
                        continue;
                    }
                    if (tsec.lightLevel < min) {
                        min = tsec.lightLevel;
                    }
                }
                sector.lightLevel = min;
            }
        }
    }

    /** {@code EV_LightTurnOn}: turn the lights of the line's tag on ({@code bright} 0 = brightest neighbour). */
    @VanillaRef("p_lights.c:EV_LightTurnOn")
    void lightTurnOn(Line line, int bright) {
        for (Sector sector : world.level.sectors) {
            if (sector.tag == line.tag) {
                // bright = 0 means to search for highest light level surrounding sector
                if (bright == 0) {
                    for (int j = 0; j < sector.lineCount; j++) {
                        Sector temp = Sectors.getNextSector(sector.lines[j], sector);
                        if (temp == null) {
                            continue;
                        }
                        if (temp.lightLevel > bright) {
                            bright = temp.lightLevel;
                        }
                    }
                }
                sector.lightLevel = bright;
            }
        }
    }

    /** {@code glow_t} with {@code T_Glow} as its function. */
    final class Glow extends SpecialThinker {
        Sector sector;
        int minLight;
        int maxLight;
        int direction;

        @Override
        @VanillaRef("p_lights.c:T_Glow")
        protected void think() {
            switch (direction) {
                case -1 -> {
                    // DOWN
                    sector.lightLevel -= GLOWSPEED;
                    if (sector.lightLevel <= minLight) {
                        sector.lightLevel += GLOWSPEED;
                        direction = 1;
                    }
                }
                case 1 -> {
                    // UP
                    sector.lightLevel += GLOWSPEED;
                    if (sector.lightLevel >= maxLight) {
                        sector.lightLevel -= GLOWSPEED;
                        direction = -1;
                    }
                }
                default -> {
                }
            }
        }

        @Override
        public int traceCode() {
            return 7;
        }

        @Override
        public void traceFields(TraceSink sink) {
            sink.put(sector.index);
            sink.put(minLight);
            sink.put(maxLight);
            sink.put(direction);
        }
    }

    /** {@code P_SpawnGlowingLight}. */
    @VanillaRef("p_lights.c:P_SpawnGlowingLight")
    void spawnGlowingLight(Sector sector) {
        Glow g = new Glow();
        world.thinkers.add(g);
        g.sector = sector;
        g.minLight = Sectors.findMinSurroundingLight(sector, sector.lightLevel);
        g.maxLight = sector.lightLevel;
        g.direction = -1;
        sector.special = 0;
    }
}
