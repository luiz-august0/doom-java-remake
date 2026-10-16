package doom.game.session;

/**
 * Persistence hook for {@code G_DoSaveGame} / {@code G_DoLoadGame}. The save format itself is not part of the game
 * flow ({@link doom.game.persist.SaveGameCodec} implements it and {@code SessionFactory} installs it); the session only
 * schedules the actions at the original points and calls this handler when the corresponding game action runs inside
 * {@code G_Ticker}. Failures are reported with {@link doom.game.persist.SaveGameException}, which the session turns
 * into a message for the player.
 */
public interface SaveGameHandler {

    /**
     * {@code G_DoSaveGame}: produce the snapshot of the current state.
     *
     * @param slot        the save slot requested through the {@code BTS_SAVEGAME} special command
     * @param description the description typed by the player ({@code "NET GAME"} when empty)
     * @return the snapshot bytes, handed to {@link doom.game.api.SimEvents#gameSaved}
     */
    byte[] save(int slot, String description);

    /**
     * {@code G_DoLoadGame}: replace the whole state with the snapshot. Runs with {@code gameaction} already reset to
     * {@code ga_nothing}, like the original.
     */
    void load(byte[] data);
}
