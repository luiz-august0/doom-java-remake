package doom.sound.music;

/**
 * Binary min-heap of timed callbacks, a port of Chocolate Doom's {@code opl_queue.c} (64 entries, same tie behaviour).
 * Entries carry an integer kind instead of a function pointer. Times are unsigned microseconds.
 */
final class OplCallbackQueue {

    private static final int MAX_ENTRIES = 64;

    private final long[] times = new long[MAX_ENTRIES];
    private final int[] kinds = new int[MAX_ENTRIES];
    private int numEntries;
    private int poppedKind;

    boolean isEmpty() {
        return numEntries == 0;
    }

    void clear() {
        numEntries = 0;
    }

    /** Kind of the entry removed by the last successful {@link #pop()}. */
    int poppedKind() {
        return poppedKind;
    }

    void push(int kind, long time) {
        if (numEntries >= MAX_ENTRIES) {
            return; // "Exceeded maximum callbacks": the original drops the entry too.
        }
        int entryId = numEntries;
        ++numEntries;
        while (entryId > 0) {
            int parentId = (entryId - 1) / 2;
            if (Long.compareUnsigned(time, times[parentId]) >= 0) {
                break;
            }
            times[entryId] = times[parentId];
            kinds[entryId] = kinds[parentId];
            entryId = parentId;
        }
        times[entryId] = time;
        kinds[entryId] = kind;
    }

    boolean pop() {
        if (numEntries <= 0) {
            return false;
        }
        poppedKind = kinds[0];
        --numEntries;
        long entryTime = times[numEntries];
        int entryKind = kinds[numEntries];
        int i = 0;
        for (;;) {
            int child1 = i * 2 + 1;
            int child2 = i * 2 + 2;
            int nextI;
            if (child1 < numEntries && Long.compareUnsigned(times[child1], entryTime) < 0) {
                if (child2 < numEntries && Long.compareUnsigned(times[child2], times[child1]) < 0) {
                    nextI = child2;
                } else {
                    nextI = child1;
                }
            } else if (child2 < numEntries && Long.compareUnsigned(times[child2], entryTime) < 0) {
                nextI = child2;
            } else {
                break;
            }
            times[i] = times[nextI];
            kinds[i] = kinds[nextI];
            i = nextI;
        }
        times[i] = entryTime;
        kinds[i] = entryKind;
        return true;
    }

    long peek() {
        return numEntries > 0 ? times[0] : 0;
    }

    /** {@code OPL_Queue_AdjustCallbacks}: scales the remaining time of every entry by {@code 1 / factor} (float math). */
    void adjust(long now, float factor) {
        for (int i = 0; i < numEntries; ++i) {
            long offset = times[i] - now;
            times[i] = now + (long) ((float) offset / factor);
        }
    }
}
