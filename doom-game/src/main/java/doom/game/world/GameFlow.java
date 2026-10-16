package doom.game.world;

/** Game-flow requests the rule areas make to the session ({@code g_game.c}). */
public interface GameFlow {

    /** {@code G_ExitLevel}: leave the level at the next tic (normal exit). */
    void exitLevel();

    /** {@code G_SecretExitLevel}. */
    void secretExitLevel();

    /** {@code G_DeferedInitNew(skill, episode, map)} (IDCLEV). */
    void deferredInitNew(int skill, int episode, int map);
}
