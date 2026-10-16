package doom.testkit.trace;

/**
 * FNV-1a 64-bit over the little-endian bytes of int32 values, the hash of trace format v1 (§6.5).
 * Immutable helpers on a {@code long} state: {@code h = Fnv1a64.add(h, value)} starting from {@link #INIT}.
 */
public final class Fnv1a64 {

    /** Hash of the empty sequence (the offset basis). */
    public static final long INIT = TraceFormat.FNV_OFFSET_BASIS;

    private Fnv1a64() {
    }

    /** Feeds the four little-endian bytes of {@code value}. */
    public static long add(long hash, int value) {
        long h = hash;
        for (int i = 0; i < 4; i++) {
            h ^= (value >>> (8 * i)) & 0xff;
            h *= TraceFormat.FNV_PRIME;
        }
        return h;
    }

    /** Feeds every value in order. */
    public static long addAll(long hash, int[] values) {
        long h = hash;
        for (int v : values) {
            h = add(h, v);
        }
        return h;
    }

    /** The hash of the given values. */
    public static long of(int... values) {
        return addAll(INIT, values);
    }

    /** The trace representation: 16 lowercase hexadecimal digits. */
    public static String format(long hash) {
        String s = Long.toHexString(hash);
        return "0".repeat(16 - s.length()) + s;
    }

    /** Parses the trace representation. */
    public static long parse(String hex) {
        if (hex.length() != 16) {
            throw new IllegalArgumentException("A trace hash has 16 hexadecimal digits: " + hex);
        }
        return Long.parseUnsignedLong(hex, 16);
    }
}
