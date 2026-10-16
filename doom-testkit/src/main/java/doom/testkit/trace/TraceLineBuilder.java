package doom.testkit.trace;

/**
 * Builds a tic line in the exact v1 layout: {@code key=value} pairs joined by {@code ;}, numbers in signed 32-bit
 * decimal joined by {@code ,}, hashes as 16 lowercase hexadecimal digits. Keys are written in call order, so callers
 * add them in the §6.2 order ({@code t, gs, r, lt, map, tot, p<N>..., a, s, l, d, x, m}).
 */
public final class TraceLineBuilder {

    private final StringBuilder sb = new StringBuilder(256);

    /** Adds {@code key=v1,v2,...}. */
    public TraceLineBuilder put(String key, int... values) {
        start(key);
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values[i]);
        }
        return this;
    }

    /** Adds {@code key=<hash>}. */
    public TraceLineBuilder putHash(String key, long hash) {
        start(key);
        sb.append(Fnv1a64.format(hash));
        return this;
    }

    /** Adds {@code key=<count>,<hash>}. */
    public TraceLineBuilder putCountHash(String key, int count, long hash) {
        start(key);
        sb.append(count).append(',').append(Fnv1a64.format(hash));
        return this;
    }

    /** Adds {@code key=<text>} verbatim. */
    public TraceLineBuilder putRaw(String key, String text) {
        start(key);
        sb.append(text);
        return this;
    }

    private void start(String key) {
        if (key.isEmpty() || key.indexOf('=') >= 0 || key.indexOf(';') >= 0) {
            throw new IllegalArgumentException("Bad trace key: " + key);
        }
        if (!sb.isEmpty()) {
            sb.append(';');
        }
        sb.append(key).append('=');
    }

    /** The line text. */
    public String build() {
        return sb.toString();
    }

    /** The parsed line. */
    public TraceLine toLine() {
        return TraceLine.parse(build(), 0);
    }
}
