package doom.math;

import doom.math.annotation.VanillaRef;

/**
 * 16.16 fixed-point arithmetic on primitive {@code int} ({@code fixed_t} in the original, ADR-006).
 * Addition and subtraction are plain {@code int} operations with two's-complement wrap-around.
 */
public final class Fixed {

    /** Number of fractional bits. */
    public static final int FRACBITS = 16;

    /** 1.0 in fixed point. */
    public static final int FRACUNIT = 1 << FRACBITS;

    private Fixed() {
    }

    /** Converts an integer to fixed point ({@code x << FRACBITS}). */
    public static int fromInt(int x) {
        return x << FRACBITS;
    }

    /** Integer part, rounding toward negative infinity ({@code x >> FRACBITS}), as the original does. */
    public static int toInt(int x) {
        return x >> FRACBITS;
    }

    /** Fixed-point multiplication, identical to the original {@code FixedMul}. */
    @VanillaRef("m_fixed.c:FixedMul")
    public static int mul(int a, int b) {
        return (int) (((long) a * b) >> FRACBITS);
    }

    /**
     * Fixed-point division, identical to the vanilla {@code FixedDiv} including its overflow saturation.
     * The second step uses 64-bit integer division, as Chocolate Doom does (spec 03 §2.1), and keeps the low 32 bits
     * of the quotient.
     *
     * <p>{@code Math.abs(Integer.MIN_VALUE)} is {@code Integer.MIN_VALUE}, as {@code abs} is on x86, so a dividend of
     * {@code Integer.MIN_VALUE} never saturates (unless the divisor is {@code Integer.MIN_VALUE} too) and its quotient
     * is truncated to 32 bits like Chocolate Doom's. The only input that reaches a division by zero is
     * {@code div(Integer.MIN_VALUE, 0)}; the original crashes there, and this method throws
     * {@link DoomFatalException} with linuxdoom's {@code I_Error} message.
     *
     * @throws DoomFatalException for {@code div(Integer.MIN_VALUE, 0)}
     */
    @VanillaRef({"m_fixed.c:FixedDiv", "m_fixed.c:FixedDiv2"})
    public static int div(int a, int b) {
        if ((Math.abs(a) >> 14) >= Math.abs(b)) {
            return (a ^ b) < 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }
        if (b == 0) {
            // DIVERGE: the original crashes (divide error / SIGFPE); we raise the fatal error instead
            // (see docs/divergences/W1-B.md).
            throw new DoomFatalException("FixedDiv: divide by zero");
        }
        return (int) (((long) a << FRACBITS) / b);
    }
}
