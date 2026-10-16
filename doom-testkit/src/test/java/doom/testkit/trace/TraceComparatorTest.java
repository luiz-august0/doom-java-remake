package doom.testkit.trace;

import static org.assertj.core.api.Assertions.assertThat;

import doom.testkit.trace.TraceDivergence.Kind;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TraceComparatorTest {

    @TempDir
    Path tmp;

    private final TraceComparator comparator = new TraceComparator(2);

    /** A copy of the trace with line {@code index} rewritten. */
    private static Trace mutate(Trace t, int index, UnaryOperator<String> edit) {
        List<TraceLine> lines = new ArrayList<>(t.lines());
        lines.set(index, TraceLine.parse(edit.apply(lines.get(index).raw()), index + 2));
        return new Trace(t.header(), lines);
    }

    @Test
    void identicalTracesAreIdentical() {
        Trace t = TraceTestSupport.trace(50);
        TraceComparison c = comparator.compare(t, t);
        assertThat(c.identical()).isTrue();
        assertThat(c.linesCompared()).isEqualTo(50);
        assertThat(c.describe()).contains("Identical: 50");
    }

    @Test
    void miscRngIsNeverGated() {
        Trace ref = TraceTestSupport.trace(20);
        Trace ours = mutate(ref, 7, l -> l.replaceAll(";m=\\d+$", ";m=999"));
        assertThat(ours.lines().get(7).get("m")).isEqualTo("999");
        assertThat(comparator.compare(ref, ours).identical()).isTrue();
        // ... and a missing m is fine too
        Trace noMisc = mutate(ref, 3, l -> l.substring(0, l.lastIndexOf(";m=")));
        assertThat(comparator.compare(ref, noMisc).identical()).isTrue();
    }

    @Test
    void reportsFirstDivergentTicWithPlayerFieldName() {
        Trace ref = TraceTestSupport.trace(30);
        // Field 8 of p0 (mo->health) at tic 12: value 12*100+7 = 1207 -> 1100. A later difference must not matter.
        Trace ours = mutate(mutate(ref, 12, l -> l.replace(",1207,", ",1100,")), 20, l -> l.replace("r=20;", "r=21;"));
        TraceComparison c = comparator.compare(ref, ours);
        assertThat(c.identical()).isFalse();
        TraceDivergence d = c.divergence();
        assertThat(d.kind()).isEqualTo(Kind.VALUE);
        assertThat(d.tic()).isEqualTo(12);
        assertThat(d.lineIndex()).isEqualTo(12);
        assertThat(d.key()).isEqualTo("p0");
        assertThat(d.fieldIndex()).isEqualTo(7);
        assertThat(d.fieldName()).isEqualTo("mo->health");
        assertThat(d.refValue()).isEqualTo("1207");
        assertThat(d.oursValue()).isEqualTo("1100");
        assertThat(c.linesCompared()).isEqualTo(12);
        // two lines before, the line itself, two after, on both sides
        assertThat(d.refContext()).extracting(TraceLine::tic).containsExactly(10, 11, 12, 13, 14);
        assertThat(d.oursContext()).extracting(TraceLine::tic).containsExactly(10, 11, 12, 13, 14);
        String text = d.describe();
        assertThat(text).contains("tic 12").contains("key 'p0'").contains("field 8 'mo->health'")
                .contains("ref : 1207").contains("ours: 1100");
    }

    @Test
    void firstKeyInLineOrderWinsWhenSeveralDiffer() {
        Trace ref = TraceTestSupport.trace(5);
        Trace ours = mutate(ref, 2, l -> l.replace("gs=0;", "gs=1;").replace("r=2;", "r=3;"));
        TraceDivergence d = comparator.compare(ref, ours).divergence();
        assertThat(d.key()).isEqualTo("gs");
        assertThat(d.fieldIndex()).isEqualTo(-1);
        assertThat(d.refValue()).isEqualTo("0");
        assertThat(d.oursValue()).isEqualTo("1");
    }

    @Test
    void divergenceInDivergentLastKey() {
        Trace ref = TraceTestSupport.trace(5);
        Trace ours = mutate(ref, 4, l -> l.replaceAll(";x=2,[0-9a-f]{16}", ";x=3,0000000000000000"));
        TraceDivergence d = comparator.compare(ref, ours).divergence();
        assertThat(d.key()).isEqualTo("x");
        assertThat(d.fieldName()).isEqualTo("count");
        assertThat(d.refContext()).extracting(TraceLine::tic).containsExactly(2, 3, 4);
    }

    @Test
    void namedComponentsForMapAndTotals() {
        Trace ref = TraceTestSupport.trace(3);
        TraceDivergence map = comparator.compare(ref, mutate(ref, 1, l -> l.replace("map=1,5,2", "map=1,5,3"))).divergence();
        assertThat(map.fieldName()).isEqualTo("gameskill");
        TraceDivergence tot = comparator.compare(ref, mutate(ref, 1, l -> l.replace("tot=10,20,3", "tot=10,21,3"))).divergence();
        assertThat(tot.fieldName()).isEqualTo("totalitems");
        assertThat(tot.fieldIndex()).isEqualTo(1);
    }

    @Test
    void secondPlayerIsNamedToo() {
        Trace ref = twoPlayerTrace(10, false);
        Trace ours = twoPlayerTrace(10, true);
        TraceDivergence d = comparator.compare(ref, ours).divergence();
        assertThat(d.tic()).isEqualTo(4);
        assertThat(d.key()).isEqualTo("p1");
        assertThat(d.fieldName()).isEqualTo("ammo[2]");
    }

    private static Trace twoPlayerTrace(int tics, boolean broken) {
        List<TraceLine> lines = new ArrayList<>();
        for (int t = 0; t < tics; t++) {
            int[] p1 = TraceTestSupport.player(500);
            if (broken && t >= 4) {
                p1[21] += 1; // field 22 = ammo[2]
            }
            String l = new TraceLineBuilder().put("t", t).put("gs", 0).put("r", t).put("lt", t)
                    .put("map", 1, 1, 2).put("tot", 0, 0, 0)
                    .put("p0", TraceTestSupport.player(0)).put("p1", p1)
                    .putCountHash("a", 1, 1).putHash("s", 2).putHash("l", 3).putHash("d", 4).putCountHash("x", 0, 5)
                    .put("m", t).build();
            lines.add(TraceLine.parse(l, t + 2));
        }
        return new Trace(TraceTestSupport.header(), lines);
    }

    @Test
    void missingKeyAndKeyOrderAreDivergences() {
        Trace ref = TraceTestSupport.trace(4);
        Trace noD = mutate(ref, 2, l -> l.replaceAll(";d=[0-9a-f]{16}", ""));
        TraceDivergence d1 = comparator.compare(ref, noD).divergence();
        assertThat(d1.kind()).isEqualTo(Kind.MISSING_KEY);
        assertThat(d1.key()).isEqualTo("d");
        assertThat(d1.oursValue()).isNull();
        TraceDivergence d2 = comparator.compare(noD, ref).divergence();
        assertThat(d2.kind()).isEqualTo(Kind.MISSING_KEY);
        assertThat(d2.refValue()).isNull();

        Trace swapped = mutate(ref, 1, l -> l.replace("gs=0;r=1;", "r=1;gs=0;"));
        TraceDivergence d3 = comparator.compare(ref, swapped).divergence();
        assertThat(d3.kind()).isEqualTo(Kind.KEY_ORDER);
        assertThat(d3.tic()).isEqualTo(1);
    }

    @Test
    void lengthMismatchIsReported() {
        Trace full = TraceTestSupport.trace(10);
        Trace shortTrace = new Trace(full.header(), full.lines().subList(0, 6));
        TraceDivergence ours = comparator.compare(full, shortTrace).divergence();
        assertThat(ours.kind()).isEqualTo(Kind.OURS_ENDED);
        assertThat(ours.tic()).isEqualTo(6);
        assertThat(ours.describe()).contains("our trace ends");
        TraceDivergence ref = comparator.compare(shortTrace, full).divergence();
        assertThat(ref.kind()).isEqualTo(Kind.REF_ENDED);
        assertThat(ref.tic()).isEqualTo(6);
        assertThat(ref.describe()).contains("reference trace ends");
    }

    @Test
    void divergenceOnFirstLineHasNoLeadingContext() {
        Trace ref = TraceTestSupport.trace(5);
        Trace ours = mutate(ref, 0, l -> l.replace("lt=0;", "lt=9;"));
        TraceDivergence d = comparator.compare(ref, ours).divergence();
        assertThat(d.tic()).isEqualTo(0);
        assertThat(d.refContext()).extracting(TraceLine::tic).containsExactly(0, 1, 2);
    }

    @Test
    void headerDifferencesAreNotesNotDivergences() {
        Trace ref = TraceTestSupport.trace(3);
        Trace ours = new Trace(TraceHeader.of("freedoom1.wad", "DEMO1", "ultimate"), ref.lines());
        TraceComparison c = comparator.compare(ref, ours);
        assertThat(c.identical()).isTrue();
        assertThat(c.headerNotes()).hasSize(2);
        assertThat(c.describe()).contains("note: iwad differs").contains("note: version differs");
        // the demo name differs only in case: no note
        Trace upper = new Trace(TraceHeader.of("doom1.wad", "DEMO1", "1.9"), ref.lines());
        assertThat(comparator.compare(ref, upper).headerNotes()).isEmpty();
    }

    @Test
    void verboseTracesPinpointTheObject() {
        int[] a0 = new int[TraceFormat.MOBJ_FIELD_COUNT];
        int[] a1 = new int[TraceFormat.MOBJ_FIELD_COUNT];
        a1[1] = 5000;
        int[] a1bad = a1.clone();
        a1bad[5] = -64; // momx
        TraceLine base = TraceLine.parse(TraceTestSupport.line(3, 3, TraceTestSupport.player(0), "1"), 2);
        TraceLine good = base.withVerbose(List.of(new VerboseEntry('a', 0, a0, 0), new VerboseEntry('a', 1, a1, 0)));
        TraceLine bad = TraceLine.parse(base.raw().replaceAll(";a=12,[0-9a-f]{16}", ";a=12,00000000000000ff"), 2)
                .withVerbose(List.of(new VerboseEntry('a', 0, a0, 0), new VerboseEntry('a', 1, a1bad, 0)));
        Trace ref = new Trace(TraceTestSupport.header(), List.of(good));
        Trace ours = new Trace(TraceTestSupport.header(), List.of(bad));
        TraceDivergence d = comparator.compare(ref, ours).divergence();
        assertThat(d.key()).isEqualTo("a");
        assertThat(d.objectDetail()).isNotNull();
        assertThat(d.objectDetail().position()).isEqualTo(1);
        assertThat(d.objectDetail().fieldName()).isEqualTo("momx");
        assertThat(d.describe()).contains("first differing object: a #1, field 6 'momx'");
    }

    @Test
    void comparesFilesOnDisk() throws IOException {
        Trace ref = TraceTestSupport.trace(25);
        Trace ours = mutate(ref, 17, l -> l.replace("lt=17;", "lt=18;"));
        Path refFile = tmp.resolve("ref.trace.gz");
        Path oursFile = tmp.resolve("ours.trace");
        write(refFile, ref);
        write(oursFile, ours);
        TraceComparison c = comparator.compare(refFile, oursFile);
        assertThat(c.divergence().tic()).isEqualTo(17);
        assertThat(c.divergence().key()).isEqualTo("lt");
        assertThat(comparator.compare(refFile, refFile).identical()).isTrue();
    }

    @Test
    void largeTracesStream() throws IOException {
        Trace ref = TraceTestSupport.trace(20_000);
        Path a = tmp.resolve("a.trace.gz");
        write(a, ref);
        TraceComparison c = new TraceComparator().compare(a, a);
        assertThat(c.identical()).isTrue();
        assertThat(c.linesCompared()).isEqualTo(20_000);
    }

    private static void write(Path file, Trace t) throws IOException {
        try (TraceWriter w = TraceWriter.create(file, t.header())) {
            for (TraceLine l : t.lines()) {
                w.write(l);
            }
        }
    }
}
