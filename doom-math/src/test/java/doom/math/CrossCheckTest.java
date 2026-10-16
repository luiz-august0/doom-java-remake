package doom.math;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Replays the vectors produced by the C cross-check ({@code src/test/c/crosscheck.c}, built by {@code run.sh} against
 * the Chocolate Doom 3.1.1 sources): explicit edge cases and digests over 2^21 pseudo-random inputs per
 * function and distribution (about 36 million inputs in total).
 *
 * <p>The input generator below mirrors the C one statement by statement: SplitMix64, the same distributions and the
 * same draw order. Results are hashed with FNV-1a 64 over their little-endian bytes, like the C side.
 */
class CrossCheckTest {

    /** Result recorded by the C side for the one input that divides by zero, {@code FixedDiv(INT_MIN, 0)}. */
    private static final int DIV_BY_ZERO_MARKER = 0x7eadbeef;

    private static final List<String[]> LINES = new ArrayList<>();

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = CrossCheckTest.class.getResourceAsStream("crosscheck-vectors.txt")) {
            assertThat(in).as("crosscheck-vectors.txt (generate with src/test/c/run.sh)").isNotNull();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty() && !line.startsWith("#")) {
                    LINES.add(line.split(" "));
                }
            }
        }
    }

    @Test
    void explicitEdgeCasesMatchChocolateDoom() {
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (String[] f : LINES) {
            if (f[0].equals("digest")) {
                continue;
            }
            int expected = Integer.parseInt(f[f.length - 1]);
            int actual = switch (f[0]) {
                case "mul" -> Fixed.mul(arg(f, 1), arg(f, 2));
                case "div" -> div(arg(f, 1), arg(f, 2));
                case "slope" -> Tables.slopeDiv(arg(f, 1), arg(f, 2));
                case "approx" -> Trig.approxDistance(arg(f, 1), arg(f, 2));
                case "p2a" -> Trig.pointToAngle2(arg(f, 1), arg(f, 2), arg(f, 3), arg(f, 4));
                default -> throw new IllegalStateException("Unknown vector kind: " + f[0]);
            };
            if (actual != expected) {
                failures.add(String.join(" ", f) + " -> Java " + actual);
            }
            checked++;
        }
        assertThat(checked).isGreaterThan(5000);
        assertThat(failures).as("vectors that differ from C").isEmpty();
    }

    @Test
    void randomDigestsMatchChocolateDoom() {
        int digests = 0;
        long inputs = 0;
        for (String[] f : LINES) {
            if (!f[0].equals("digest")) {
                continue;
            }
            String fn = f[1];
            String dist = f[2];
            long seed = Long.parseLong(f[3]);
            int count = Integer.parseInt(f[4]);
            long expected = Long.parseUnsignedLong(f[5], 16);
            long actual = fn.equals("p2a") ? digestPointToAngle(dist, seed, count) : digest2(fn, dist, seed, count);
            assertThat(Long.toHexString(actual)).as("digest %s %s", fn, dist).isEqualTo(Long.toHexString(expected));
            digests++;
            inputs += count;
        }
        assertThat(digests).isEqualTo(17);
        assertThat(inputs).isGreaterThan(30_000_000L);
    }

    private static int arg(String[] f, int i) {
        return Integer.parseInt(f[i]);
    }

    private static int div(int a, int b) {
        try {
            return Fixed.div(a, b);
        } catch (DoomFatalException e) {
            return DIV_BY_ZERO_MARKER;
        }
    }

    // ------------------------------------------------------------------------------------- C mirror

    /** SplitMix64, as in crosscheck.c. */
    private static final class SplitMix {
        private long state;

        SplitMix(long seed) {
            state = seed;
        }

        long next64() {
            long z = (state += 0x9e3779b97f4a7c15L);
            z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
            z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
            return z ^ (z >>> 31);
        }

        /** {@code u32()}: the high 32 bits, as an int bit pattern. */
        int u32() {
            return (int) (next64() >>> 32);
        }

        /** {@code magnitude()}: a random value shifted right (arithmetic) by a random 0..31. */
        int magnitude() {
            int v = u32();
            int shift = u32() & 31;
            return v >> shift;
        }

        /** {@code small_delta()}: {@code (u32() % 5) - 2} with an unsigned modulo. */
        int smallDelta() {
            return Integer.remainderUnsigned(u32(), 5) - 2;
        }
    }

    private static final class Fnv {
        private long hash = 0xcbf29ce484222325L;

        void add(int v) {
            for (int i = 0; i < 4; i++) {
                hash ^= (v >>> (8 * i)) & 0xff;
                hash *= 0x100000001b3L;
            }
        }
    }

    private static long digest2(String fn, String dist, long seed, int count) {
        SplitMix rng = new SplitMix(seed);
        Fnv fnv = new Fnv();
        int[] ab = new int[2];
        for (int i = 0; i < count; i++) {
            draw2(dist, rng, ab);
            int a = ab[0];
            int b = ab[1];
            int r = switch (fn) {
                case "mul" -> Fixed.mul(a, b);
                case "div" -> div(a, b);
                case "slope" -> Tables.slopeDiv(a, b);
                case "approx" -> Trig.approxDistance(a, b);
                default -> throw new IllegalStateException("Unknown function: " + fn);
            };
            fnv.add(r);
        }
        return fnv.hash;
    }

    private static void draw2(String dist, SplitMix rng, int[] out) {
        switch (dist) {
            case "uniform" -> {
                out[0] = rng.u32();
                out[1] = rng.u32();
            }
            case "magnitude" -> {
                out[0] = rng.magnitude();
                out[1] = rng.magnitude();
            }
            case "mixed" -> {
                out[0] = rng.u32();
                out[1] = rng.magnitude();
            }
            case "divedge" -> {
                int b = rng.magnitude();
                int m = b < 0 ? -b : b;
                int delta = rng.smallDelta();
                int a = (m << 14) + delta;
                if ((rng.u32() & 1) != 0) {
                    a = -a;
                }
                out[0] = a;
                out[1] = b;
            }
            case "smallden" -> {
                out[0] = rng.magnitude();
                out[1] = rng.u32() & 0x3ff;
            }
            default -> throw new IllegalStateException("Unknown distribution: " + dist);
        }
    }

    private static long digestPointToAngle(String dist, long seed, int count) {
        SplitMix rng = new SplitMix(seed);
        Fnv fnv = new Fnv();
        for (int i = 0; i < count; i++) {
            int x1;
            int y1;
            int x2;
            int y2;
            if (dist.equals("magnitude")) {
                x1 = rng.magnitude();
                y1 = rng.magnitude();
                x2 = rng.magnitude();
                y2 = rng.magnitude();
            } else {
                x1 = rng.u32();
                y1 = rng.u32();
                switch (dist) {
                    case "uniform" -> {
                        x2 = rng.u32();
                        y2 = rng.u32();
                    }
                    case "near" -> {
                        int dx = rng.magnitude() >> 8;
                        int dy = rng.magnitude() >> 8;
                        x2 = x1 + dx;
                        y2 = y1 + dy;
                    }
                    case "diag" -> {
                        int d = rng.magnitude();
                        int e1 = rng.smallDelta();
                        int e2 = rng.smallDelta();
                        int sx = rng.u32() & 1;
                        int sy = rng.u32() & 1;
                        int dx = (sx != 0 ? -d : d) + e1;
                        int dy = (sy != 0 ? -d : d) + e2;
                        x2 = x1 + dx;
                        y2 = y1 + dy;
                    }
                    case "axis" -> {
                        int k = Integer.remainderUnsigned(rng.u32(), 3);
                        int dx;
                        int dy;
                        if (k == 0) {
                            dx = 0;
                            dy = rng.magnitude();
                        } else if (k == 1) {
                            dx = rng.magnitude();
                            dy = 0;
                        } else {
                            dx = rng.smallDelta();
                            dy = rng.smallDelta();
                        }
                        x2 = x1 + dx;
                        y2 = y1 + dy;
                    }
                    default -> throw new IllegalStateException("Unknown distribution: " + dist);
                }
            }
            fnv.add(Trig.pointToAngle2(x1, y1, x2, y2));
        }
        return fnv.hash;
    }
}
