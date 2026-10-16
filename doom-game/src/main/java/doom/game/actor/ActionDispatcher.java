package doom.game.actor;

import doom.game.data.StateAction;
import doom.game.player.PSprite;
import doom.game.player.Player;
import doom.game.world.World;

/**
 * Routes the state actions of {@link StateAction} (ADR-022 §4) to the rule areas, by the original source file:
 * <ul>
 *   <li>{@code p_enemy.c} actions (monsters, {@code A_Explode}, {@code A_Fall}, {@code A_Scream}, ...) →
 *       {@link doom.game.ai.MonsterAi#action};</li>
 *   <li>offensive {@code p_pspr.c} actions → {@link doom.game.combat.Combat#weaponAction}, {@code A_BFGSpray} →
 *       {@link doom.game.combat.Combat#bfgSpray};</li>
 *   <li>non-offensive {@code p_pspr.c} actions (raise, lower, ready, refire, reload, flash, lights, sounds) →
 *       {@link doom.game.player.Weapons}.</li>
 * </ul>
 * An action used in the wrong kind of state (possible with DEHACKED) is ignored; the original calls it with
 * mismatched arguments (undefined behaviour).
 */
public final class ActionDispatcher {

    private final World world;

    public ActionDispatcher(World world) {
        this.world = world;
    }

    /** {@code st->action.acp1(mobj)} from {@code P_SetMobjState}. */
    public void mobjAction(StateAction action, Actor mo) {
        switch (action) {
            case NONE -> {
            }
            // p_pspr.c actions take (player, psp): not callable on a map object.
            case A_Light0, A_WeaponReady, A_Lower, A_Raise, A_Punch, A_ReFire, A_FirePistol, A_Light1,
                 A_FireShotgun, A_Light2, A_FireShotgun2, A_CheckReload, A_OpenShotgun2, A_LoadShotgun2,
                 A_CloseShotgun2, A_FireCGun, A_GunFlash, A_FireMissile, A_Saw, A_FirePlasma, A_BFGsound,
                 A_FireBFG -> {
                // DIVERGE: undefined behaviour in the original (see docs/divergences/W2-A.md).
            }
            case A_BFGSpray -> world.combat.bfgSpray(mo);
            default -> world.ai.action(action, mo);
        }
    }

    /** {@code state->action.acp2(player, psp)} from {@code P_SetPsprite}. */
    public void pspAction(StateAction action, Player player, PSprite psp) {
        switch (action) {
            case A_Light0 -> world.weapons.light(player, 0);
            case A_Light1 -> world.weapons.light(player, 1);
            case A_Light2 -> world.weapons.light(player, 2);
            case A_WeaponReady -> world.weapons.weaponReady(player, psp);
            case A_Lower -> world.weapons.lower(player, psp);
            case A_Raise -> world.weapons.raise(player, psp);
            case A_ReFire -> world.weapons.reFire(player, psp);
            case A_CheckReload -> world.weapons.checkReload(player, psp);
            case A_GunFlash -> world.weapons.gunFlash(player, psp);
            case A_OpenShotgun2 -> world.weapons.openShotgun2(player, psp);
            case A_LoadShotgun2 -> world.weapons.loadShotgun2(player, psp);
            case A_CloseShotgun2 -> world.weapons.closeShotgun2(player, psp);
            case A_BFGsound -> world.weapons.bfgSound(player, psp);
            case A_Punch, A_FirePistol, A_FireShotgun, A_FireShotgun2, A_FireCGun, A_FireMissile, A_Saw,
                 A_FirePlasma, A_FireBFG -> world.combat.weaponAction(action, player, psp);
            default -> {
                // NONE, or a map-object action in a weapon state: undefined behaviour in the original.
            }
        }
    }
}
