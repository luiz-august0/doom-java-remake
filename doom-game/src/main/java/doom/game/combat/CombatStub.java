package doom.game.combat;

import doom.game.actor.Actor;
import doom.game.data.StateAction;
import doom.game.player.PSprite;
import doom.game.player.Player;
import doom.game.world.World;

/**
 * Placeholder until W3-B implements damage, projectiles and weapon fire: every call records a stub hit
 * ({@link doom.game.world.Unimplemented}) and does nothing else ({@link #spawnMissile} returns null).
 */
public final class CombatStub implements Combat {

    private final World world;

    public CombatStub(World world) {
        this.world = world;
    }

    @Override
    public void damageMobj(Actor target, Actor inflictor, Actor source, int damage) {
        world.unimplemented.hit("combat.damageMobj");
    }

    @Override
    public void killMobj(Actor source, Actor target) {
        world.unimplemented.hit("combat.killMobj");
    }

    @Override
    public void explodeMissile(Actor missile) {
        world.unimplemented.hit("combat.explodeMissile");
    }

    @Override
    public void spawnPuff(int x, int y, int z) {
        world.unimplemented.hit("combat.spawnPuff");
    }

    @Override
    public void spawnBlood(int x, int y, int z, int damage) {
        world.unimplemented.hit("combat.spawnBlood");
    }

    @Override
    public Actor spawnMissile(Actor source, Actor dest, int type) {
        world.unimplemented.hit("combat.spawnMissile");
        return null;
    }

    @Override
    public void spawnPlayerMissile(Actor source, int type) {
        world.unimplemented.hit("combat.spawnPlayerMissile");
    }

    @Override
    public void checkMissileSpawn(Actor missile) {
        world.unimplemented.hit("combat.checkMissileSpawn");
    }

    @Override
    public void weaponAction(StateAction action, Player player, PSprite psp) {
        world.unimplemented.hit("combat." + action.name());
    }

    @Override
    public void bfgSpray(Actor mo) {
        world.unimplemented.hit("combat.A_BFGSpray");
    }
}
