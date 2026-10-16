package doom.game.ai;

import doom.game.actor.Actor;
import doom.game.data.StateAction;

/**
 * Monster behaviour ({@code p_enemy.c}), implemented by W3-A. The simulation core calls it at the original call
 * points; implementations receive the session's {@link doom.game.world.World} at construction.
 */
public interface MonsterAi {

    /**
     * Runs a state action defined in {@code p_enemy.c} for a map object ({@code st->action.acp1(mobj)} in
     * {@code P_SetMobjState}): {@code A_Look}, {@code A_Chase}, {@code A_FaceTarget}, every monster attack,
     * {@code A_Explode}, {@code A_Fall}, {@code A_Scream}, {@code A_XScream}, {@code A_Pain}, {@code A_PlayerScream},
     * {@code A_BossDeath}, {@code A_KeenDie}, {@code A_Brain*}, {@code A_Spawn*}, {@code A_Tracer} and so on
     * (see {@link doom.game.actor.ActionDispatcher} for the exact routing).
     */
    void action(StateAction action, Actor actor);

    /** {@code P_NightmareRespawn} (called from {@code P_MobjThinker} after the original's checks and RNG call). */
    void nightmareRespawn(Actor actor);

    /** {@code P_NoiseAlert(target, emitter)}: sound propagation from a firing player ({@code P_FireWeapon}, weapons). */
    void noiseAlert(Actor target, Actor emitter);

    // ------------------------------------------------------------------ save games (W4-P)

    /** {@code braintargets[0..numbraintargets)} of the boss brain (state kept between tics). */
    default Actor[] brainTargets() {
        return new Actor[0];
    }

    /** {@code braintargeton}. */
    default int brainTargetOn() {
        return 0;
    }

    /** The function-local {@code static int easy} of {@code A_BrainSpit}. */
    default int brainEasy() {
        return 0;
    }

    /** Restores the boss brain state returned by the getters above. */
    default void restoreBrain(Actor[] targets, int targetOn, int easy) {
    }
}
