package doom.game.ai;

import doom.game.actor.Actor;
import doom.game.data.StateAction;
import doom.game.world.World;

/**
 * Placeholder until W3-A implements {@code p_enemy.c}: every call records a stub hit
 * ({@link doom.game.world.Unimplemented}) and does nothing else.
 */
public final class MonsterAiStub implements MonsterAi {

    private final World world;

    public MonsterAiStub(World world) {
        this.world = world;
    }

    @Override
    public void action(StateAction action, Actor actor) {
        world.unimplemented.hit("ai." + action.name());
    }

    @Override
    public void nightmareRespawn(Actor actor) {
        world.unimplemented.hit("ai.nightmareRespawn");
    }

    @Override
    public void noiseAlert(Actor target, Actor emitter) {
        world.unimplemented.hit("ai.noiseAlert");
    }
}
