package doom.game.persist;

/**
 * A save game could not be written or read: the data is corrupt, truncated, from a newer format, or does not fit the
 * running game. The message is meant for the player.
 */
public final class SaveGameException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final boolean worldModified;

    public SaveGameException(String message) {
        this(message, false, null);
    }

    public SaveGameException(String message, Throwable cause) {
        this(message, false, cause);
    }

    /**
     * @param worldModified true when the failure happened after the session state was already replaced, so the caller
     *                      cannot keep playing the old game
     */
    public SaveGameException(String message, boolean worldModified, Throwable cause) {
        super(message, cause);
        this.worldModified = worldModified;
    }

    /** True when the running game was already changed by the failed load. */
    public boolean worldModified() {
        return worldModified;
    }
}
