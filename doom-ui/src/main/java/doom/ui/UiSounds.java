package doom.ui;

/** Sound output of the UI (menu clicks, quit sound, bunny-scroll shots). Implemented by the engine on top of {@code S_StartSound}. */
@FunctionalInterface
public interface UiSounds {

    /** Plays sound effect {@code sfx} ({@link doom.game.view.SoundId} constant) at full volume with no origin ({@code S_StartSound(NULL, sfx)}). */
    void startSound(int sfx);

    /** A sink that plays nothing. */
    UiSounds SILENT = sfx -> {
    };
}
