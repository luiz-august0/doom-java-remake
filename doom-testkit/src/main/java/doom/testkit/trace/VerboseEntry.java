package doom.testkit.trace;

import java.util.Arrays;

/**
 * One object line of a verbose trace (§6.6): {@code "  a<i>=<26 mobj fields>"} or {@code "  x<i>=<code>,<fields>"}.
 *
 * @param kind       {@code 'a'} (map object) or {@code 'x'} (special thinker)
 * @param index      the {@code <i>} label of the line
 * @param values     the comma-separated numbers (for {@code 'x'}, {@code values[0]} is the kind code)
 * @param lineNumber 1-based line number in the file (0 when built in memory)
 */
public record VerboseEntry(char kind, int index, int[] values, long lineNumber) {

    public VerboseEntry {
        if (kind != 'a' && kind != 'x') {
            throw new IllegalArgumentException("Verbose entry kind must be 'a' or 'x': " + kind);
        }
        values = values.clone();
    }

    @Override
    public int[] values() {
        return values.clone();
    }

    /** Number of values. */
    public int size() {
        return values.length;
    }

    /** Value {@code i}. */
    public int value(int i) {
        return values[i];
    }

    /** Name of value {@code i}: a mobj field for {@code 'a'}, the code or a thinker field for {@code 'x'}. */
    public String fieldName(int i) {
        if (kind == 'a') {
            return i < TraceFormat.MOBJ_FIELDS.size() ? TraceFormat.MOBJ_FIELDS.get(i) : null;
        }
        return TraceFormat.specialFieldName(values.length > 0 ? values[0] : -1, i);
    }

    /** The line as written in a trace, with the two-space indent. */
    public String format() {
        StringBuilder sb = new StringBuilder(TraceFormat.VERBOSE_INDENT).append(kind).append(index).append('=');
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values[i]);
        }
        return sb.toString();
    }

    /**
     * Parses a verbose line (with or without the indent).
     *
     * @throws TraceFormatException when the line is malformed
     */
    public static VerboseEntry parse(String line, long lineNumber) {
        String s = line.strip();
        int eq = s.indexOf('=');
        if (s.length() < 3 || eq < 2 || (s.charAt(0) != 'a' && s.charAt(0) != 'x')) {
            throw new TraceFormatException("Malformed verbose line: " + line, lineNumber);
        }
        try {
            int index = Integer.parseInt(s.substring(1, eq));
            String body = s.substring(eq + 1);
            int[] values = body.isEmpty() ? new int[0] : Arrays.stream(body.split(",", -1)).mapToInt(Integer::parseInt).toArray();
            return new VerboseEntry(s.charAt(0), index, values, lineNumber);
        } catch (NumberFormatException e) {
            throw new TraceFormatException("Malformed number in verbose line: " + line, lineNumber);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof VerboseEntry v && v.kind == kind && v.index == index && Arrays.equals(v.values, values);
    }

    @Override
    public int hashCode() {
        return (kind * 31 + index) * 31 + Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return format().strip();
    }
}
