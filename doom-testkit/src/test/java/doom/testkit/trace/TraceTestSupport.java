package doom.testkit.trace;

import java.util.ArrayList;
import java.util.List;

/** Builders of synthetic traces for the trace tests. */
final class TraceTestSupport {

    private TraceTestSupport() {
    }

    static TraceHeader header() {
        return TraceHeader.of("doom1.wad", "demo1", "1.9");
    }

    /** A 61-value player entry; value {@code i} = base + i. */
    static int[] player(int base) {
        int[] v = new int[TraceFormat.PLAYER_FIELD_COUNT];
        for (int i = 0; i < v.length; i++) {
            v[i] = base + i;
        }
        return v;
    }

    /** One tic line in the §6.2 key order. */
    static String line(int tic, int rnd, int[] p0, String miscRng) {
        TraceLineBuilder b = new TraceLineBuilder()
                .put("t", tic)
                .put("gs", 0)
                .put("r", rnd)
                .put("lt", tic)
                .put("map", 1, 5, 2)
                .put("tot", 10, 20, 3)
                .put("p0", p0)
                .putCountHash("a", 12, Fnv1a64.of(tic, 1))
                .putHash("s", Fnv1a64.of(tic, 2))
                .putHash("l", Fnv1a64.of(3))
                .putHash("d", Fnv1a64.of(4))
                .putCountHash("x", 2, Fnv1a64.of(5));
        if (miscRng != null) {
            b.putRaw("m", miscRng);
        }
        return b.build();
    }

    static Trace trace(int tics) {
        List<TraceLine> lines = new ArrayList<>();
        for (int t = 0; t < tics; t++) {
            lines.add(TraceLine.parse(line(t, t % 256, player(t * 100), Integer.toString(t % 50)), t + 2));
        }
        return new Trace(header(), lines);
    }
}
