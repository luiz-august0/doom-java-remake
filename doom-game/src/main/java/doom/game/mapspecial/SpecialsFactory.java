package doom.game.mapspecial;

import doom.game.world.World;

/** Creates this area's implementation for a session. Owned by the area's agent; returns the W3-C implementation. */
public final class SpecialsFactory {

    private SpecialsFactory() {
    }

    public static MapSpecials create(World world) {
        return new MapSpecialsImpl(world);
    }
}
