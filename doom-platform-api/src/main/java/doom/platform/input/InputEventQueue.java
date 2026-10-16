package doom.platform.input;

/**
 * Pre-allocated, growable queue of {@link InputEvent}s, filled by the backend and drained by the engine every frame.
 * No allocation in steady state (spec 02 §8). Not thread-safe: main thread only.
 */
public final class InputEventQueue {

    private InputEvent[] events;
    private int size;

    public InputEventQueue() {
        this(256);
    }

    public InputEventQueue(int initialCapacity) {
        events = new InputEvent[Math.max(16, initialCapacity)];
        for (int i = 0; i < events.length; i++) {
            events[i] = new InputEvent();
        }
    }

    /** Appends a cleared event and returns it for the caller to fill. */
    public InputEvent add(int type, long timeNanos) {
        if (size == events.length) {
            InputEvent[] bigger = new InputEvent[events.length * 2];
            System.arraycopy(events, 0, bigger, 0, events.length);
            for (int i = events.length; i < bigger.length; i++) {
                bigger[i] = new InputEvent();
            }
            events = bigger;
        }
        InputEvent e = events[size++];
        e.clear();
        e.type = type;
        e.timeNanos = timeNanos;
        return e;
    }

    public int size() {
        return size;
    }

    public InputEvent get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(index);
        }
        return events[index];
    }

    public void clear() {
        size = 0;
    }
}
