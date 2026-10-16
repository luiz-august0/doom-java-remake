package doom.sound.mixer;

/**
 * Soft limiter for the final mix: linear below {@link #THRESHOLD}, then a smooth rational knee that approaches but never
 * exceeds 32767. Continuous with slope 1 at the threshold, monotonic, odd-symmetric, integer only.
 */
final class SoftLimiter {

    /** Level where compression starts. */
    static final int THRESHOLD = 24576;
    private static final int HEADROOM = 32767 - THRESHOLD;

    private SoftLimiter() {
    }

    /** Maps an accumulated sample (any {@code int}) to a 16-bit sample. */
    static short apply(int x) {
        int a = x < 0 ? -x : x;
        if (a <= THRESHOLD) {
            return (short) x;
        }
        long over = (long) a - THRESHOLD;
        int y = THRESHOLD + (int) (over * HEADROOM / (over + HEADROOM));
        return (short) (x < 0 ? -y : y);
    }
}
