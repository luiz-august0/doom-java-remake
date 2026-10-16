package doom.platform;

import java.util.concurrent.locks.LockSupport;

/** Monotonic time source for the main loop, replaceable in tests. */
public interface Clock {

    /** Monotonic nanoseconds. */
    long nanoTime();

    /**
     * Waits until {@code deadlineNanos} (in {@link #nanoTime()} units): parks while far away, then spins briefly
     * (hybrid wait, spec 04 §7).
     */
    default void waitUntil(long deadlineNanos) {
        while (true) {
            long remaining = deadlineNanos - nanoTime();
            if (remaining <= 0) {
                return;
            }
            if (remaining > 2_000_000L) {
                LockSupport.parkNanos(remaining - 1_000_000L);
            } else {
                Thread.onSpinWait();
            }
        }
    }

    /** The system clock ({@link System#nanoTime()}). */
    static Clock system() {
        return System::nanoTime;
    }
}
