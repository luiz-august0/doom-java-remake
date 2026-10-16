package doom.game.actor;

/**
 * {@code thinker_t}: an element of the level's {@link ThinkerList}. The original keeps a function pointer whose value
 * also encodes state: {@code -1} means "marked for removal" ({@link #isRemoved()}), {@code NULL} means "no action"
 * (ceilings and platforms in stasis, {@link #hasNullFunction()}); anything else is called once per tic
 * ({@link #think()}).
 *
 * <p>Two kinds exist: {@link Actor} (map objects, function {@code P_MobjThinker}) and {@link SpecialThinker}
 * (sector effects owned by the map-special area).
 */
public abstract sealed class Thinker permits Actor, SpecialThinker {

    Thinker prev;
    Thinker next;
    /** {@code function == -1}. */
    boolean removed;
    /** Scratch value of the trace writer: position among the live thinkers (contracts §6.3). */
    public int traceIndex;
    /** Scratch value of the trace writer: the pass that set {@link #traceIndex}. */
    public int traceStamp;

    protected Thinker() {
    }

    /** True once the thinker was removed ({@code P_RemoveThinker}); it stays in the list until the runner passes. */
    public final boolean isRemoved() {
        return removed;
    }

    /**
     * True when the original function pointer is {@code NULL}: the runner skips the thinker but it stays live.
     * Ceilings and platforms in stasis override this.
     */
    public boolean hasNullFunction() {
        return false;
    }

    /** One tic of work: {@code thinker->function(thinker)}. */
    protected abstract void think();
}
