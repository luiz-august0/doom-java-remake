package doom.math;

import doom.math.annotation.Bam;

/**
 * Binary angle measurement ({@code angle_t}, ADR-006): a 32-bit {@code int} interpreted as <b>unsigned</b>,
 * where {@code 0x40000000} is 90 degrees. Every ordering comparison must go through {@link #lt}, {@link #gt} and
 * friends, and every shift used to index a table must be {@code >>>}.
 */
public final class Angle {

    public static final @Bam int ANG45 = 0x20000000;
    public static final @Bam int ANG90 = 0x40000000;
    /** 180 degrees. Negative as a Java {@code int}. */
    public static final @Bam int ANG180 = 0x80000000;
    public static final @Bam int ANG270 = 0xc0000000;
    /** The largest angle, just below 360 degrees ({@code ANGLE_MAX}). */
    public static final @Bam int ANGLE_MAX = 0xffffffff;
    /** One degree, truncated ({@code ANG45 / 45}), as used by the original automap and finale code. */
    public static final @Bam int ANG1 = ANG45 / 45;

    /** Number of entries per full turn in the fine tables ({@code FINEANGLES}). */
    public static final int FINEANGLES = 8192;
    /** {@code FINEANGLES - 1}. */
    public static final int FINEMASK = FINEANGLES - 1;
    /** Shift from a BAM angle to a fine table index ({@code ANGLETOFINESHIFT}). */
    public static final int ANGLETOFINESHIFT = 19;

    private Angle() {
    }

    /** Fine table index of an angle: {@code angle >>> ANGLETOFINESHIFT}. */
    public static int toFine(int angle) {
        return angle >>> ANGLETOFINESHIFT;
    }

    /** Unsigned {@code a < b}. */
    public static boolean lt(int a, int b) {
        return Integer.compareUnsigned(a, b) < 0;
    }

    /** Unsigned {@code a <= b}. */
    public static boolean le(int a, int b) {
        return Integer.compareUnsigned(a, b) <= 0;
    }

    /** Unsigned {@code a > b}. */
    public static boolean gt(int a, int b) {
        return Integer.compareUnsigned(a, b) > 0;
    }

    /** Unsigned {@code a >= b}. */
    public static boolean ge(int a, int b) {
        return Integer.compareUnsigned(a, b) >= 0;
    }
}
