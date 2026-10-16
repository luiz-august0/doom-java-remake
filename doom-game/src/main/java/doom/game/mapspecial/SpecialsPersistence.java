package doom.game.mapspecial;

import doom.game.actor.SpecialThinker;
import doom.game.world.Sector;
import doom.game.world.World;

/**
 * Save game support of the map-special area (W4-P): turns the sector-effect thinkers into plain {@code int} arrays and
 * back. Unlike the trace description, every field is stored exactly as held (for example the {@code crush} field of
 * stairs, which is 10, and the NULL function flag of ceilings).
 */
final class SpecialsPersistence {

    static final int DOOR = 1;
    static final int CEILING = 2;
    static final int FLOOR = 3;
    static final int PLAT = 4;
    static final int LIGHT_FLASH = 5;
    static final int STROBE_FLASH = 6;
    static final int GLOW = 7;
    static final int FIRE_FLICKER = 8;

    private final World world;
    private final Doors doors;
    private final Floors floors;
    private final Ceilings ceilings;
    private final Plats plats;
    private final Lights lights;

    SpecialsPersistence(World world, Doors doors, Floors floors, Ceilings ceilings, Plats plats, Lights lights) {
        this.world = world;
        this.doors = doors;
        this.floors = floors;
        this.ceilings = ceilings;
        this.plats = plats;
        this.lights = lights;
    }

    int[] save(SpecialThinker t) {
        if (t instanceof Doors.Door d) {
            return new int[] {DOOR, d.type, d.sector.index, d.topHeight, d.speed, d.direction, d.topWait,
                d.topCountdown};
        }
        if (t instanceof Ceilings.CeilingMove c) {
            return new int[] {CEILING, c.type, c.sector.index, c.bottomHeight, c.topHeight, c.speed, c.crush ? 1 : 0,
                c.direction, c.tag, c.oldDirection, c.nullFunction ? 1 : 0};
        }
        if (t instanceof Floors.FloorMove f) {
            return new int[] {FLOOR, f.type, f.crush, f.sector.index, f.direction, f.newSpecial, f.texture,
                f.floorDestHeight, f.speed};
        }
        if (t instanceof Plats.PlatRaise p) {
            return new int[] {PLAT, p.sector.index, p.speed, p.low, p.high, p.wait, p.count, p.status, p.oldStatus,
                p.crush ? 1 : 0, p.tag, p.type};
        }
        if (t instanceof Lights.LightFlash l) {
            return new int[] {LIGHT_FLASH, l.sector.index, l.count, l.maxLight, l.minLight, l.maxTime, l.minTime};
        }
        if (t instanceof Lights.StrobeFlash s) {
            return new int[] {STROBE_FLASH, s.sector.index, s.count, s.minLight, s.maxLight, s.darkTime,
                s.brightTime};
        }
        if (t instanceof Lights.Glow g) {
            return new int[] {GLOW, g.sector.index, g.minLight, g.maxLight, g.direction};
        }
        if (t instanceof Lights.FireFlicker f) {
            return new int[] {FIRE_FLICKER, f.sector.index, f.count, f.maxLight, f.minLight};
        }
        throw new IllegalArgumentException("not a sector effect of the map-special area: " + t);
    }

    SpecialThinker restore(int[] s) {
        if (s.length == 0) {
            throw new IllegalArgumentException("empty thinker description");
        }
        switch (s[0]) {
            case DOOR -> {
                need(s, 8);
                Doors.Door d = doors.new Door();
                d.type = s[1];
                d.sector = sector(s[2]);
                d.topHeight = s[3];
                d.speed = s[4];
                d.direction = s[5];
                d.topWait = s[6];
                d.topCountdown = s[7];
                return d;
            }
            case CEILING -> {
                need(s, 11);
                Ceilings.CeilingMove c = ceilings.new CeilingMove();
                c.type = s[1];
                c.sector = sector(s[2]);
                c.bottomHeight = s[3];
                c.topHeight = s[4];
                c.speed = s[5];
                c.crush = s[6] != 0;
                c.direction = s[7];
                c.tag = s[8];
                c.oldDirection = s[9];
                c.nullFunction = s[10] != 0;
                return c;
            }
            case FLOOR -> {
                need(s, 9);
                Floors.FloorMove f = floors.new FloorMove();
                f.type = s[1];
                f.crush = s[2];
                f.sector = sector(s[3]);
                f.direction = s[4];
                f.newSpecial = s[5];
                f.texture = s[6];
                f.floorDestHeight = s[7];
                f.speed = s[8];
                return f;
            }
            case PLAT -> {
                need(s, 12);
                Plats.PlatRaise p = plats.new PlatRaise();
                p.sector = sector(s[1]);
                p.speed = s[2];
                p.low = s[3];
                p.high = s[4];
                p.wait = s[5];
                p.count = s[6];
                p.status = s[7];
                p.oldStatus = s[8];
                p.crush = s[9] != 0;
                p.tag = s[10];
                p.type = s[11];
                return p;
            }
            case LIGHT_FLASH -> {
                need(s, 7);
                Lights.LightFlash l = lights.new LightFlash();
                l.sector = sector(s[1]);
                l.count = s[2];
                l.maxLight = s[3];
                l.minLight = s[4];
                l.maxTime = s[5];
                l.minTime = s[6];
                return l;
            }
            case STROBE_FLASH -> {
                need(s, 7);
                Lights.StrobeFlash l = lights.new StrobeFlash();
                l.sector = sector(s[1]);
                l.count = s[2];
                l.minLight = s[3];
                l.maxLight = s[4];
                l.darkTime = s[5];
                l.brightTime = s[6];
                return l;
            }
            case GLOW -> {
                need(s, 5);
                Lights.Glow g = lights.new Glow();
                g.sector = sector(s[1]);
                g.minLight = s[2];
                g.maxLight = s[3];
                g.direction = s[4];
                return g;
            }
            case FIRE_FLICKER -> {
                need(s, 5);
                Lights.FireFlicker f = lights.new FireFlicker();
                f.sector = sector(s[1]);
                f.count = s[2];
                f.maxLight = s[3];
                f.minLight = s[4];
                return f;
            }
            default -> throw new IllegalArgumentException("unknown sector effect kind " + s[0]);
        }
    }

    private static void need(int[] s, int length) {
        if (s.length != length) {
            throw new IllegalArgumentException("sector effect kind " + s[0] + " needs " + length + " values, has "
                    + s.length);
        }
    }

    private Sector sector(int index) {
        Sector[] sectors = world.level.sectors;
        if (index < 0 || index >= sectors.length) {
            throw new IllegalArgumentException("sector " + index + " out of range");
        }
        return sectors[index];
    }
}
