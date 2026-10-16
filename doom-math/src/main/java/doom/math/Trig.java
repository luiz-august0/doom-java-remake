package doom.math;

import doom.math.annotation.Bam;
import doom.math.annotation.VanillaRef;

/**
 * Geometry helpers that the <b>simulation</b> uses with their exact vanilla behaviour
 * ({@code R_PointToAngle2}, {@code P_AproxDistance}). The renderer may have higher-precision versions under other
 * names, never these (ADR-008).
 */
public final class Trig {

    private Trig() {
    }

    /**
     * Angle of the vector from (x1, y1) to (x2, y2), by octant and {@code tantoangle}, exactly as
     * {@code R_PointToAngle2} (which sets the view origin to (x1, y1) and calls {@code R_PointToAngle}).
     *
     * <p>The differences {@code x2 - x1} and {@code y2 - y1}, the negations and the ordering tests use 32-bit
     * two's-complement arithmetic and <b>signed</b> comparisons, like the original on x86; {@code SlopeDiv} then
     * reinterprets its operands as unsigned. This is bit-identical to the C code compiled with wrap-around semantics,
     * including overflowing differences and {@code Integer.MIN_VALUE} (see {@code src/test/c/crosscheck.c}).
     * Unlike the original, this method has no side effect: it does not overwrite the renderer's {@code viewx} and
     * {@code viewy} (the simulation only ever calls {@code R_PointToAngle2}, so the result is the same).
     */
    @VanillaRef({"r_main.c:R_PointToAngle2", "r_main.c:R_PointToAngle"})
    public static @Bam int pointToAngle2(int x1, int y1, int x2, int y2) {
        int x = x2 - x1;
        int y = y2 - y1;

        if (x == 0 && y == 0) {
            return 0;
        }
        if (x >= 0) {
            if (y >= 0) {
                if (x > y) {
                    return Tables.tanToAngle(Tables.slopeDiv(y, x)); // octant 0
                }
                return Angle.ANG90 - 1 - Tables.tanToAngle(Tables.slopeDiv(x, y)); // octant 1
            }
            y = -y;
            if (x > y) {
                return -Tables.tanToAngle(Tables.slopeDiv(y, x)); // octant 8
            }
            return Angle.ANG270 + Tables.tanToAngle(Tables.slopeDiv(x, y)); // octant 7
        }
        x = -x;
        if (y >= 0) {
            if (x > y) {
                return Angle.ANG180 - 1 - Tables.tanToAngle(Tables.slopeDiv(y, x)); // octant 3
            }
            return Angle.ANG90 + Tables.tanToAngle(Tables.slopeDiv(x, y)); // octant 2
        }
        y = -y;
        if (x > y) {
            return Angle.ANG180 + Tables.tanToAngle(Tables.slopeDiv(y, x)); // octant 4
        }
        return Angle.ANG270 - 1 - Tables.tanToAngle(Tables.slopeDiv(x, y)); // octant 5
    }

    /** Approximate distance {@code |dx| + |dy| - min(|dx|, |dy|) / 2} ({@code P_AproxDistance}). */
    @VanillaRef("p_maputl.c:P_AproxDistance")
    public static int approxDistance(int dx, int dy) {
        dx = Math.abs(dx);
        dy = Math.abs(dy);
        if (dx < dy) {
            return dx + dy - (dx >> 1);
        }
        return dx + dy - (dy >> 1);
    }
}
