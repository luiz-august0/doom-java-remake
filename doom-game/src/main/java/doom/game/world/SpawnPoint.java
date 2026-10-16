package doom.game.world;

import doom.wad.map.MapThing;

/**
 * A mutable {@code mapthing_t} (five {@code short} fields). Mutable because the original changes copies of map things
 * in place (player starts get a fake {@code type} in {@code G_DoReborn}, the intercepts overrun writes into
 * {@code playerstarts}).
 */
public final class SpawnPoint {

    public int x;
    public int y;
    public int angle;
    public int type;
    public int options;

    public SpawnPoint() {
    }

    public SpawnPoint set(MapThing t) {
        x = t.x();
        y = t.y();
        angle = t.angle();
        type = t.type();
        options = t.options();
        return this;
    }

    public SpawnPoint set(SpawnPoint o) {
        x = o.x;
        y = o.y;
        angle = o.angle;
        type = o.type;
        options = o.options;
        return this;
    }

    /** Field {@code i} (0 x, 1 y, 2 angle, 3 type, 4 options) as a {@code short}, for memory-overrun emulation. */
    public int shortField(int i) {
        return switch (i) {
            case 0 -> x;
            case 1 -> y;
            case 2 -> angle;
            case 3 -> type;
            default -> options;
        };
    }

    /** Writes field {@code i} as a {@code short} (sign-extended), for memory-overrun emulation. */
    public void setShortField(int i, int value) {
        short v = (short) value;
        switch (i) {
            case 0 -> x = v;
            case 1 -> y = v;
            case 2 -> angle = v;
            case 3 -> type = v;
            default -> options = v;
        }
    }

    @Override
    public String toString() {
        return "SpawnPoint[x=" + x + ", y=" + y + ", angle=" + angle + ", type=" + type + ", options=" + options + "]";
    }
}
