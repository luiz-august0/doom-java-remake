package doom.game.persist;

import java.nio.charset.StandardCharsets;

/** A bounds-checked little-endian reader over a byte range; every overrun is a {@link SaveGameException}. */
final class ByteSource {

    private final byte[] buf;
    private int pos;
    private final int end;
    private final String what;

    ByteSource(byte[] buf, int start, int end, String what) {
        this.buf = buf;
        this.pos = start;
        this.end = end;
        this.what = what;
    }

    int position() {
        return pos;
    }

    int remaining() {
        return end - pos;
    }

    private void need(int n) {
        if (n < 0 || n > end - pos) {
            throw new SaveGameException("Save game is truncated or corrupt (" + what + ")");
        }
    }

    int getByte() {
        need(1);
        return buf[pos++] & 0xFF;
    }

    int getShort() {
        need(2);
        int v = (buf[pos] & 0xFF) | ((buf[pos + 1] & 0xFF) << 8);
        pos += 2;
        return v;
    }

    int getInt() {
        need(4);
        int v = (buf[pos] & 0xFF) | ((buf[pos + 1] & 0xFF) << 8) | ((buf[pos + 2] & 0xFF) << 16)
                | ((buf[pos + 3] & 0xFF) << 24);
        pos += 4;
        return v;
    }

    boolean getBool() {
        int v = getInt();
        if (v != 0 && v != 1) {
            throw new SaveGameException("Save game is corrupt (" + what + ": bad flag " + v + ")");
        }
        return v == 1;
    }

    long getLong() {
        long lo = getInt() & 0xFFFFFFFFL;
        long hi = getInt();
        return lo | (hi << 32);
    }

    byte[] getBytes(int n) {
        need(n);
        byte[] out = new byte[n];
        System.arraycopy(buf, pos, out, 0, n);
        pos += n;
        return out;
    }

    String getString() {
        int n = getShort();
        return new String(getBytes(n), StandardCharsets.UTF_8);
    }

    String getTag() {
        need(4);
        String s = new String(buf, pos, 4, StandardCharsets.ISO_8859_1);
        pos += 4;
        return s;
    }

    /** A sub-reader over the next {@code n} bytes; this reader advances past them. */
    ByteSource slice(int n, String sliceWhat) {
        need(n);
        ByteSource s = new ByteSource(buf, pos, pos + n, sliceWhat);
        pos += n;
        return s;
    }

    /** A reader over the same range at the same position, leaving this reader unchanged. */
    ByteSource copy() {
        return new ByteSource(buf, pos, end, what);
    }

    /** A count of {@code unit}-byte elements that must fit in the rest of the data. */
    int getCount(int unit) {
        int n = getInt();
        if (n < 0 || (long) n * unit > remaining()) {
            throw new SaveGameException("Save game is corrupt (" + what + ": bad count " + n + ")");
        }
        return n;
    }

    /** An int in {@code [min, max]}. */
    int getRange(int min, int max, String field) {
        int v = getInt();
        if (v < min || v > max) {
            throw new SaveGameException("Save game is corrupt (" + what + ": " + field + " = " + v + ")");
        }
        return v;
    }

    /** Fails unless the whole range was consumed. */
    void expectEnd() {
        if (pos != end) {
            throw new SaveGameException("Save game is corrupt (" + what + ": " + (end - pos) + " unexpected bytes)");
        }
    }
}
