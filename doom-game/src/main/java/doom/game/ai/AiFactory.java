package doom.game.ai;

import doom.game.world.World;

/** Creates this area's implementation for a session. */
public final class AiFactory {

    private AiFactory() {
    }

    public static MonsterAi create(World world) {
        return new MonsterAiImpl(world);
    }
}
