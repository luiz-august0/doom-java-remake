package doom.game.api;

import doom.game.view.ActorView;
import doom.game.view.GameView;
import doom.game.view.WorldView;

/**
 * One game session: the simulation driven tic by tic. Commands between tics follow the original "game action"
 * semantics: most are <i>deferred</i> and take effect inside the next {@link #tick} ({@code G_Ticker} processes
 * {@code gameaction} first). Single-threaded; main thread only.
 */
public interface GameSession {

    // ------------------------------------------------------------------ commands

    /** {@code D_StartTitle}: start the title loop (pages and demos). */
    void startTitle();

    /** {@code G_DeferedInitNew}: start a new game with these parameters at the next tic. */
    void newGame(GameParams params);

    /**
     * {@code G_DeferedPlayDemo}: play a demo at the next tic. The demo provides the commands of all its players;
     * the commands passed to {@link #tick} are ignored while it plays.
     *
     * @param name display name (lump name or file name)
     * @param demo the .lmp bytes (version 109, or 110 read as 109)
     */
    void playDemo(String name, byte[] demo, DemoPlayback mode);

    /** {@code G_RecordDemo}: start a new game with {@code params} and record it. */
    void recordDemo(String name, GameParams params);

    /** Stops recording and returns the .lmp bytes (version 109, ending with 0x80); null if not recording. */
    byte[] finishDemoRecording();

    /** {@code G_LoadGame}: replace the state with a save snapshot at the next tic. */
    void loadGame(byte[] saveData);

    /**
     * {@code G_SaveGame}: save into {@code slot} with {@code description}. Like the original, the request travels as a
     * {@link TicCmd#BTS_SAVEGAME} special command; the snapshot arrives through {@link SimEvents#gameSaved}.
     */
    void requestSave(int slot, String description);

    /** Toggle pause through a {@link TicCmd#BTS_PAUSE} special command, like the original pause key. */
    void requestPause();

    /** {@code M_EndGame} confirmed: back to the title loop. */
    void endGame();

    /** A typed character for the cheat detector ({@code cht_CheckCheat} in {@code ST_Responder}); applied immediately. */
    void cheatKey(int ch);

    /** A key press during the DOOM II cast call ({@code F_CastResponder}): the current monster dies. */
    default void finaleKey() {
    }

    /** F12 during demos / netgames: show the next player's view ({@code displayplayer}). */
    void cycleDisplayPlayer();

    // ------------------------------------------------------------------ simulation

    /**
     * Runs one game tic ({@code D_DoAdvanceDemo} when pending, then {@code G_Ticker}).
     *
     * @param cmds       one command per player slot (length 4); only players in game are read
     * @param menuActive whether the menu is open (pauses a single-player level, as {@code P_Ticker} does)
     */
    void tick(TicCmd[] cmds, boolean menuActive);

    // ------------------------------------------------------------------ state

    GameView view();

    WorldView world();

    ActorView actors();

    MiscRandom miscRandom();

    // ------------------------------------------------------------------ trace (docs/contracts.md §6)

    /** Appends the trace v1 line of the tic just run (without the line feed). */
    void appendTraceLine(StringBuilder out);

    /** Appends the verbose object lines of the tic just run (§6.6), each ending with a line feed. */
    void appendTraceVerbose(StringBuilder out);
}
