package doom.game.api;

/**
 * Callbacks from the simulation to the presentation layer (contracts §11). They are <b>synchronous</b> and invoked at
 * the original call points, so that the misc RNG is consumed in the original order. Implementations may read the
 * views and call {@link MiscRandom}, but must not call any {@link GameSession} command. All methods default to no-op.
 */
public interface SimEvents {

    /** {@code S_StartSound(origin, sfx)}; {@code origin} is encoded per {@link SoundOrigins}. */
    default void startSound(int origin, int sfx) {
    }

    /** {@code S_StopSound(origin)}; also emitted when an actor is removed. */
    default void stopSound(int origin) {
    }

    /** {@code S_ChangeMusic(music, looping)} / {@code S_StartMusic}. */
    default void changeMusic(int music, boolean looping) {
    }

    /** {@code S_PauseSound} ({@code true}) / {@code S_ResumeSound} ({@code false}). */
    default void pauseSound(boolean paused) {
    }

    /** A message for a player's HUD ({@code player->message}). */
    default void playerMessage(int player, String text) {
    }

    /** A level was loaded and is about to run ({@code G_DoLoadLevel}): reset status bar, HUD and automap. */
    default void levelLoaded() {
    }

    /** {@code G_Ticker}, GS_LEVEL: the point where the original calls {@code ST_Ticker}, {@code AM_Ticker}, {@code HU_Ticker}. */
    default void levelTick() {
    }

    /** The intermission started ({@code WI_Start}). */
    default void intermissionStarted() {
    }

    /** The finale started ({@code F_StartFinale}). */
    default void finaleStarted() {
    }

    /** Force a screen wipe even if the game state did not change ({@code wipegamestate = -1}). */
    default void forceWipe() {
    }

    /** A save snapshot was produced ({@code G_DoSaveGame}); the presentation writes it to disk. */
    default void gameSaved(int slot, String description, byte[] data) {
    }

    /** A demo finished playing ({@code G_CheckDemoStatus}). */
    default void demoFinished(DemoPlayback mode, int gametics) {
    }
}
