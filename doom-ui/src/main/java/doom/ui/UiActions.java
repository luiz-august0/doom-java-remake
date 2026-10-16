package doom.ui;

import doom.game.api.Skill;

/**
 * Game-flow requests raised by the menu. Implemented by the engine, which owns the {@code GameSession}, the files
 * and the process. Every method is called from inside {@link Ui#keyDown} (the main thread, between tics), never
 * from a simulation callback.
 */
public interface UiActions {

    /** {@code G_DeferedInitNew(skill, episode, map)}: the engine adds its own options (respawn, fast, no monsters). */
    void newGame(Skill skill, int episode, int map);

    /** {@code G_LoadGame}: read the save file of {@code slot} (0..5) and hand it to {@code GameSession.loadGame}. */
    void loadGame(int slot);

    /** {@code G_SaveGame}: {@code GameSession.requestSave(slot, description)}; the file is written on {@code SimEvents.gameSaved}. */
    void saveGame(int slot, String description);

    /** {@code M_EndGameResponse -> D_StartTitle}: {@code GameSession.endGame()}. */
    void endGame();

    /**
     * {@code M_QuitResponse -> I_Quit}: quit the program. The UI has already started the quit sound; the original
     * then waits {@code I_WaitVBL(105)} (about 1.5 s) so that it can be heard, which the engine may reproduce.
     */
    void quit();

    /** {@code G_ScreenShot}. */
    default void screenshot() {
    }

    /** A typed character for the cheat detector ({@code GameSession.cheatKey}); called for every key press in a level. */
    default void cheatKey(int ch) {
    }

    /**
     * A key was pressed during the DOOM II cast call ({@code F_CastResponder}: the shown monster dies). The session has no
     * command for it yet; see the contract requests of W2-C.
     */
    default void finaleKey() {
    }

    /** An action sink that does nothing (tests, tools). */
    UiActions NONE = new UiActions() {
        @Override
        public void newGame(Skill skill, int episode, int map) {
        }

        @Override
        public void loadGame(int slot) {
        }

        @Override
        public void saveGame(int slot, String description) {
        }

        @Override
        public void endGame() {
        }

        @Override
        public void quit() {
        }
    };
}
