package doom.testkit.trace;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The first line of a trace: {@code #doom-trace v1 iwad=<file name> demo=<name> version=<1.9|ultimate|final|final2>}.
 *
 * @param iwad    IWAD file name
 * @param demo    demo name
 * @param version executable version ({@code 1.9}, {@code ultimate}, {@code final}, {@code final2})
 * @param raw     the header line as read (or as it will be written)
 */
public record TraceHeader(String iwad, String demo, String version, String raw) {

    /** Builds a header and its line. */
    public static TraceHeader of(String iwad, String demo, String version) {
        String raw = TraceFormat.HEADER_PREFIX + " iwad=" + iwad + " demo=" + demo + " version=" + version;
        return new TraceHeader(iwad, demo, version, raw);
    }

    /**
     * Parses a header line.
     *
     * @throws TraceFormatException when the line is not a v1 header
     */
    public static TraceHeader parse(String line) {
        if (!line.startsWith(TraceFormat.HEADER_PREFIX + " ") && !line.equals(TraceFormat.HEADER_PREFIX)) {
            throw new TraceFormatException("Not a trace v1 header: " + line, 1);
        }
        Map<String, String> fields = new LinkedHashMap<>();
        String last = null;
        for (String token : line.substring(TraceFormat.HEADER_PREFIX.length()).trim().split(" +")) {
            if (token.isEmpty()) {
                continue;
            }
            int eq = token.indexOf('=');
            if (eq > 0) {
                last = token.substring(0, eq);
                fields.put(last, token.substring(eq + 1));
            } else if (last != null) {
                // A value with spaces (for example a demo file name): keep it whole.
                fields.put(last, fields.get(last) + " " + token);
            } else {
                throw new TraceFormatException("Malformed trace header: " + line, 1);
            }
        }
        return new TraceHeader(fields.getOrDefault("iwad", ""), fields.getOrDefault("demo", ""),
                fields.getOrDefault("version", ""), line);
    }
}
