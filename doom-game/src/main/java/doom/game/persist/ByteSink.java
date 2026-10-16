package doom.game.persist;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** A growable little-endian byte buffer for writing a snapshot. */
final class ByteSink {

    private byte[] buf;
    private int size;

    ByteSink() {
        this(1 << 16);
    }

    ByteSink(int capacity) {
        buf = new byte[capacity];
    }

    int size() {
        return size;
    }

    private void ensure(int extra) {
        if (size + extra > buf.length) {
            buf = Arrays.copyOf(buf, (buf.length * 2 > size + extra ? buf.length * 2 : size + extra));
        }
    }

    void putByte(int v) {
        ensure(1);
        buf[size++] = (byte) v;
    }

    void putShort(int v) {
        ensure(2);
        buf[size++] = (byte) v;
        buf[size++] = (byte) (v >> 8);
    }

    void putInt(int v) {
        ensure(4);
        buf[size++] = (byte) v;
        buf[size++] = (byte) (v >> 8);
        buf[size++] = (byte) (v >> 16);
        buf[size++] = (byte) (v >> 24);
    }

    void putBool(boolean v) {
        putInt(v ? 1 : 0);
    }

    void putLong(long v) {
        putInt((int) v);
        putInt((int) (v >> 32));
    }

    void putBytes(byte[] b) {
        ensure(b.length);
        System.arraycopy(b, 0, buf, size, b.length);
        size += b.length;
    }

    void putBytes(ByteSink other) {
        ensure(other.size);
        System.arraycopy(other.buf, 0, buf, size, other.size);
        size += other.size;
    }

    /** A string: unsigned 16-bit length, then UTF-8. */
    void putString(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        if (b.length > 0xFFFF) {
            throw new IllegalArgumentException("string too long");
        }
        putShort(b.length);
        putBytes(b);
    }

    /** A four-character tag. */
    void putTag(String tag) {
        for (int i = 0; i < 4; i++) {
            putByte(tag.charAt(i));
        }
    }

    /** A whole section: tag, payload length, payload. */
    void putSection(String tag, ByteSink payload) {
        putTag(tag);
        putInt(payload.size);
        putBytes(payload);
    }

    /** The bytes written so far (a copy). */
    byte[] toByteArray() {
        return Arrays.copyOf(buf, size);
    }

    /** CRC-32 of the bytes written so far. */
    int crc32() {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(buf, 0, size);
        return (int) crc.getValue();
    }
}
