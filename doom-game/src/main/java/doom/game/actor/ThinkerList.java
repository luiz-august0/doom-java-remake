package doom.game.actor;

import doom.math.annotation.VanillaRef;

/**
 * The level's thinker list ({@code thinkercap}): a circular doubly linked list in insertion order. Removal is
 * deferred exactly as in {@code P_RunThinkers}: {@link #remove} only marks the thinker; the runner unlinks it when it
 * reaches it. Thinkers added while the list runs are appended at the end and run in the same pass.
 */
public final class ThinkerList {

    /** The list head; never run. */
    private final Thinker cap = new Cap();

    private static final class Cap extends SpecialThinker {
        @Override
        protected void think() {
        }

        @Override
        public int traceCode() {
            return 0;
        }

        @Override
        public void traceFields(TraceSink sink) {
        }
    }

    public ThinkerList() {
        clear();
    }

    /** {@code P_InitThinkers}: empties the list (the level's thinkers are dropped). */
    @VanillaRef("p_tick.c:P_InitThinkers")
    public void clear() {
        cap.prev = cap;
        cap.next = cap;
    }

    /** {@code P_AddThinker}: appends at the end. */
    @VanillaRef("p_tick.c:P_AddThinker")
    public void add(Thinker thinker) {
        cap.prev.next = thinker;
        thinker.next = cap;
        thinker.prev = cap.prev;
        cap.prev = thinker;
        thinker.removed = false;
    }

    /** {@code P_RemoveThinker}: marks the thinker; it is unlinked lazily by {@link #run()}. */
    @VanillaRef("p_tick.c:P_RemoveThinker")
    public void remove(Thinker thinker) {
        thinker.removed = true;
    }

    /** {@code P_RunThinkers}. */
    @VanillaRef("p_tick.c:P_RunThinkers")
    public void run() {
        Thinker current = cap.next;
        while (current != cap) {
            Thinker nextThinker;
            if (current.removed) {
                // time to remove it
                nextThinker = current.next;
                current.next.prev = current.prev;
                current.prev.next = current.next;
            } else {
                if (!current.hasNullFunction()) {
                    current.think();
                }
                nextThinker = current.next;
            }
            current = nextThinker;
        }
    }

    /** First thinker in list order (including removed ones still linked), or null when empty. */
    public Thinker first() {
        return cap.next == cap ? null : cap.next;
    }

    /** The thinker after {@code t} in list order, or null at the end. {@code t} must still be linked. */
    public Thinker next(Thinker t) {
        return t.next == cap ? null : t.next;
    }

    /** Number of linked thinkers that are not removed. */
    public int liveCount() {
        int n = 0;
        for (Thinker t = cap.next; t != cap; t = t.next) {
            if (!t.removed) {
                n++;
            }
        }
        return n;
    }
}
