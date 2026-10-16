package doom.game.combat;

import doom.game.world.World;

/** Creates this area's implementation for a session. Owned by the area's agent. */
public final class CombatFactory {

    private CombatFactory() {
    }

    public static Combat create(World world) {
        return new CombatImpl(world);
    }
}
