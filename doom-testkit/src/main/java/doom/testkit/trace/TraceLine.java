package doom.testkit.trace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One tic line of a trace (§6.2): {@code key=value} pairs separated by {@code ;}, in file order, plus the verbose
 * object lines that followed it (§6.6, empty for normal traces). Values are kept as the exact text of the file.
 * Immutable.
 */
public final class TraceLine {

    private final String raw;
    private final String[] keys;
    private final String[] values;
    private final long lineNumber;
    private final List<VerboseEntry> verbose;

    private TraceLine(String raw, String[] keys, String[] values, long lineNumber, List<VerboseEntry> verbose) {
        this.raw = raw;
        this.keys = keys;
        this.values = values;
        this.lineNumber = lineNumber;
        this.verbose = List.copyOf(verbose);
    }

    /**
     * Parses a tic line.
     *
     * @param lineNumber 1-based line number in the file, for messages (0 when unknown)
     * @throws TraceFormatException when the line is malformed (empty key, missing {@code =}, duplicate key,
     *                              missing or non-numeric {@code t})
     */
    public static TraceLine parse(String raw, long lineNumber) {
        String[] parts = raw.split(";", -1);
        String[] keys = new String[parts.length];
        String[] values = new String[parts.length];
        for (int i = 0; i < parts.length; i++) {
            int eq = parts[i].indexOf('=');
            if (eq <= 0) {
                throw new TraceFormatException("Expected key=value, found '" + parts[i] + "'", lineNumber);
            }
            keys[i] = parts[i].substring(0, eq);
            values[i] = parts[i].substring(eq + 1);
            for (int j = 0; j < i; j++) {
                if (keys[j].equals(keys[i])) {
                    throw new TraceFormatException("Duplicate key '" + keys[i] + "'", lineNumber);
                }
            }
        }
        if (parts.length == 0 || !keys[0].equals(TraceFormat.KEY_TIC)) {
            throw new TraceFormatException("A tic line starts with t=<tic>: " + raw, lineNumber);
        }
        try {
            Integer.parseInt(values[0]);
        } catch (NumberFormatException e) {
            throw new TraceFormatException("Non-numeric tic: " + values[0], lineNumber);
        }
        return new TraceLine(raw, keys, values, lineNumber, List.of());
    }

    /** A copy of this line with verbose object lines attached. */
    public TraceLine withVerbose(List<VerboseEntry> entries) {
        return new TraceLine(raw, keys, values, lineNumber, entries);
    }

    /** The line exactly as in the file (without the line feed). */
    public String raw() {
        return raw;
    }

    /** 1-based line number in the file (0 when built in memory). */
    public long lineNumber() {
        return lineNumber;
    }

    /** The value of {@code t}. */
    public int tic() {
        return Integer.parseInt(values[0]);
    }

    /** The keys in file order. */
    public List<String> keys() {
        return List.of(keys);
    }

    /** Whether the line has {@code key}. */
    public boolean has(String key) {
        return indexOf(key) >= 0;
    }

    /** The value text of {@code key}, or null when absent. */
    public String get(String key) {
        int i = indexOf(key);
        return i < 0 ? null : values[i];
    }

    /** The comma-separated parts of {@code key}'s value (empty when absent). */
    public List<String> parts(String key) {
        String v = get(key);
        return v == null ? List.of() : Arrays.asList(v.split(",", -1));
    }

    /** Value {@code index} of {@code key} as an int (hashes excluded). */
    public int intPart(String key, int index) {
        List<String> p = parts(key);
        if (index >= p.size()) {
            throw new IndexOutOfBoundsException(key + "[" + index + "] in: " + raw);
        }
        return Integer.parseInt(p.get(index));
    }

    /** The players present ({@code p<N>} keys), in file order. */
    public List<Integer> players() {
        List<Integer> out = new ArrayList<>(4);
        for (String k : keys) {
            if (TraceFormat.isPlayerKey(k)) {
                out.add(k.charAt(1) - '0');
            }
        }
        return out;
    }

    /** The verbose object lines that followed this line (§6.6). */
    public List<VerboseEntry> verbose() {
        return verbose;
    }

    /** The verbose entries of one kind ({@code 'a'} or {@code 'x'}), in file order. */
    public List<VerboseEntry> verbose(char kind) {
        List<VerboseEntry> out = new ArrayList<>();
        for (VerboseEntry e : verbose) {
            if (e.kind() == kind) {
                out.add(e);
            }
        }
        return out;
    }

    private int indexOf(String key) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(key)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TraceLine l && l.raw.equals(raw) && l.verbose.equals(verbose);
    }

    @Override
    public int hashCode() {
        return raw.hashCode();
    }

    @Override
    public String toString() {
        return raw;
    }
}
