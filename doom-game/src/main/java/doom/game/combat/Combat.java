package doom.game.combat;

import doom.game.actor.Actor;
import doom.game.data.StateAction;
import doom.game.player.PSprite;
import doom.game.player.Player;

/**
 * Damage, death, projectiles and weapon fire ({@code p_inter.c} damage part, {@code p_mobj.c} spawn functions,
 * {@code p_pspr.c} offensive actions), implemented by W3-B. Traversals ({@code P_AimLineAttack},
 * {@code P_LineAttack}, {@code P_RadiusAttack}) live in {@link doom.game.physics.Physics} and call back into this
 * interface for puffs, blood and damage.
 */
public interface Combat {

    /** {@code P_DamageMobj(target, inflictor, source, damage)}; inflictor and source may be null. */
    void damageMobj(Actor target, Actor inflictor, Actor source, int damage);

    /** {@code P_KillMobj(source, target)}. */
    void killMobj(Actor source, Actor target);

    /** {@code P_ExplodeMissile}. */
    void explodeMissile(Actor missile);

    /** {@code P_SpawnPuff} (reads {@code attackrange} from the physics state). */
    void spawnPuff(int x, int y, int z);

    /** {@code P_SpawnBlood}. */
    void spawnBlood(int x, int y, int z, int damage);

    /** {@code P_SpawnMissile}: returns the missile. */
    Actor spawnMissile(Actor source, Actor dest, int type);

    /** {@code P_SpawnPlayerMissile}. */
    void spawnPlayerMissile(Actor source, int type);

    /** {@code P_CheckMissileSpawn}. */
    void checkMissileSpawn(Actor missile);

    /**
     * An offensive weapon action of {@code p_pspr.c} ({@code A_Punch}, {@code A_Saw}, {@code A_FirePistol},
     * {@code A_FireShotgun}, {@code A_FireShotgun2}, {@code A_FireCGun}, {@code A_FireMissile}, {@code A_FirePlasma},
     * {@code A_FireBFG}), called from {@code P_SetPsprite}.
     */
    void weaponAction(StateAction action, Player player, PSprite psp);

    /** {@code A_BFGSpray} (a map-object action of the BFG ball's death states). */
    void bfgSpray(Actor mo);
}
