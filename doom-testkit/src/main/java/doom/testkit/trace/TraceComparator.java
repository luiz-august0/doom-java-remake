package doom.testkit.trace;

import doom.testkit.trace.TraceDivergence.Kind;
import doom.testkit.trace.TraceDivergence.ObjectDetail;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Compares a reference trace with ours, line by line, on the gated keys (every key except {@code m}, §6.2) and
 * reports the first divergence: the tic, the key, the value index and name (the §6.4 field name for {@code p<N>}),
 * both values and the surrounding lines. When both sides are verbose traces (§6.6) and the divergence is in
 * {@code a} or {@code x}, the first differing object and field are reported too.
 *
 * <p>Streaming: memory use is bounded by the context size, so long traces are fine. Immutable and thread-safe.
 */
public final class TraceComparator {

    /** Default number of context lines before and after the divergence. */
    public static final int DEFAULT_CONTEXT = 3;

    private final int context;

    public TraceComparator() {
        this(DEFAULT_CONTEXT);
    }

    /** @param context number of lines to show before and after the divergent line (both sides) */
    public TraceComparator(int context) {
        if (context < 0) {
            throw new IllegalArgumentException("context must be >= 0");
        }
        this.context = context;
    }

    /** Compares two trace files (plain or gzip). */
    public TraceComparison compare(Path ref, Path ours) throws IOException {
        try (TraceReader r = TraceReader.open(ref); TraceReader o = TraceReader.open(ours)) {
            return compare(r, o);
        }
    }

    /** Compares two open traces from their current position. */
    public TraceComparison compare(TraceReader ref, TraceReader ours) {
        return compare(ref.header(), ref::next, ours.header(), ours::next);
    }

    /** Compares two traces in memory. */
    public TraceComparison compare(Trace ref, Trace ours) {
        return compare(ref.header(), supplier(ref.lines()), ours.header(), supplier(ours.lines()));
    }

    private static Supplier<TraceLine> supplier(List<TraceLine> lines) {
        Iterator<TraceLine> it = lines.iterator();
        return () -> it.hasNext() ? it.next() : null;
    }

    private TraceComparison compare(TraceHeader refHeader, Supplier<TraceLine> ref, TraceHeader oursHeader,
                                    Supplier<TraceLine> ours) {
        List<String> notes = headerNotes(refHeader, oursHeader);
        ArrayDeque<TraceLine> refBefore = new ArrayDeque<>();
        ArrayDeque<TraceLine> oursBefore = new ArrayDeque<>();
        int index = 0;
        while (true) {
            TraceLine r = ref.get();
            TraceLine o = ours.get();
            if (r == null && o == null) {
                return new TraceComparison(refHeader, oursHeader, index, notes, null);
            }
            LineDiff diff;
            if (r == null) {
                diff = new LineDiff(Kind.REF_ENDED, null, -1, null, null, null);
            } else if (o == null) {
                diff = new LineDiff(Kind.OURS_ENDED, null, -1, null, null, null);
            } else {
                diff = diffLines(r, o);
            }
            if (diff != null) {
                List<TraceLine> refContext = new ArrayList<>(refBefore);
                List<TraceLine> oursContext = new ArrayList<>(oursBefore);
                readAfter(r, ref, refContext);
                readAfter(o, ours, oursContext);
                int tic = r != null ? r.tic() : o.tic();
                ObjectDetail detail = r != null && o != null && diff.key != null
                        && (diff.key.equals("a") || diff.key.equals("x")) ? objectDetail(diff.key.charAt(0), r, o) : null;
                TraceDivergence d = new TraceDivergence(diff.kind, index, tic, diff.key, diff.fieldIndex,
                        diff.fieldName, diff.refValue, diff.oursValue, refContext, oursContext, detail);
                return new TraceComparison(refHeader, oursHeader, index, notes, d);
            }
            push(refBefore, r);
            push(oursBefore, o);
            index++;
        }
    }

    private void push(ArrayDeque<TraceLine> before, TraceLine line) {
        if (context == 0) {
            return;
        }
        if (before.size() == context) {
            before.removeFirst();
        }
        before.addLast(line);
    }

    private void readAfter(TraceLine current, Supplier<TraceLine> source, List<TraceLine> out) {
        if (current == null) {
            return;
        }
        out.add(current);
        for (int i = 0; i < context; i++) {
            TraceLine next = source.get();
            if (next == null) {
                return;
            }
            out.add(next);
        }
    }

    /** One difference within a pair of lines. */
    private record LineDiff(Kind kind, String key, int fieldIndex, String fieldName, String refValue, String oursValue) {
    }

    /** The first gated difference between two tic lines, or null when they are equivalent. */
    static LineDiff diffLines(TraceLine r, TraceLine o) {
        List<String> refKeys = gatedKeys(r);
        List<String> oursKeys = gatedKeys(o);
        for (String key : refKeys) {
            String rv = r.get(key);
            String ov = o.get(key);
            if (ov == null) {
                return new LineDiff(Kind.MISSING_KEY, key, -1, null, rv, null);
            }
            if (!rv.equals(ov)) {
                return valueDiff(key, rv, ov);
            }
        }
        for (String key : oursKeys) {
            if (!r.has(key)) {
                return new LineDiff(Kind.MISSING_KEY, key, -1, null, null, o.get(key));
            }
        }
        if (!refKeys.equals(oursKeys)) {
            return new LineDiff(Kind.KEY_ORDER, refKeys.toString(), -1, null, String.join(";", refKeys),
                    String.join(";", oursKeys));
        }
        return null;
    }

    private static LineDiff valueDiff(String key, String rv, String ov) {
        String[] rp = rv.split(",", -1);
        String[] op = ov.split(",", -1);
        if (rp.length == 1 && op.length == 1) {
            return new LineDiff(Kind.VALUE, key, -1, null, rv, ov);
        }
        int n = Math.min(rp.length, op.length);
        int i = 0;
        while (i < n && rp[i].equals(op[i])) {
            i++;
        }
        return new LineDiff(Kind.VALUE, key, i, TraceFormat.fieldName(key, i),
                i < rp.length ? rp[i] : null, i < op.length ? op[i] : null);
    }

    private static List<String> gatedKeys(TraceLine line) {
        List<String> keys = new ArrayList<>();
        for (String k : line.keys()) {
            if (TraceFormat.isGated(k)) {
                keys.add(k);
            }
        }
        return keys;
    }

    /** The first differing verbose object of a kind, or null when neither line has verbose entries of it. */
    static ObjectDetail objectDetail(char kind, TraceLine r, TraceLine o) {
        List<VerboseEntry> ra = r.verbose(kind);
        List<VerboseEntry> oa = o.verbose(kind);
        if (ra.isEmpty() && oa.isEmpty()) {
            return null;
        }
        int n = Math.max(ra.size(), oa.size());
        for (int i = 0; i < n; i++) {
            VerboseEntry re = i < ra.size() ? ra.get(i) : null;
            VerboseEntry oe = i < oa.size() ? oa.get(i) : null;
            if (re == null || oe == null) {
                return new ObjectDetail(kind, i, -1, null, re, oe);
            }
            int m = Math.max(re.size(), oe.size());
            for (int j = 0; j < m; j++) {
                boolean same = j < re.size() && j < oe.size() && re.value(j) == oe.value(j);
                if (!same) {
                    String name = j < re.size() ? re.fieldName(j) : oe.fieldName(j);
                    return new ObjectDetail(kind, i, j, name, re, oe);
                }
            }
        }
        return null;
    }

    private static List<String> headerNotes(TraceHeader ref, TraceHeader ours) {
        List<String> notes = new ArrayList<>();
        if (!ref.iwad().equalsIgnoreCase(ours.iwad())) {
            notes.add("iwad differs: ref=" + ref.iwad() + " ours=" + ours.iwad());
        }
        if (!ref.version().equals(ours.version())) {
            notes.add("version differs: ref=" + ref.version() + " ours=" + ours.version());
        }
        if (!ref.demo().equalsIgnoreCase(ours.demo())) {
            notes.add("demo name differs: ref=" + ref.demo() + " ours=" + ours.demo());
        }
        return notes;
    }
}
