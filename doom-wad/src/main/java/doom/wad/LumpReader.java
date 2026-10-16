package doom.wad;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Bounds-checked little-endian reads from one lump. Every out-of-range access becomes a {@link WadFormatException}
 * naming the file, the lump and the offset, so decoders never leak a raw {@code IndexOutOfBoundsException}
 * (spec 02 §9).
 */
public final class LumpReader {

    private final ByteBuffer data;
    private final String file;
    private final String lump;

    /**
     * @param data lump bytes (position 0 is the start of the lump; the buffer's limit is the lump length)
     * @param file file name for error messages (may be null)
     * @param lump lump name or label for error messages (may be null)
     */
    public LumpReader(ByteBuffer data, String file, String lump) {
        this.data = data.slice().order(ByteOrder.LITTLE_ENDIAN);
        this.file = file;
        this.lump = lump;
    }

    /** Reader over lump {@code lump} of {@code dir}. */
    public static LumpReader of(LumpDirectory dir, int lump) {
        return new LumpReader(dir.data(lump), dir.wadOf(lump).fileName(), dir.name(lump));
    }

    /** Reader over lump {@code lump} of {@code dir}, with a custom label (for example {@code "E1M1 THINGS"}). */
    public static LumpReader of(LumpDirectory dir, int lump, String label) {
        return new LumpReader(dir.data(lump), dir.wadOf(lump).fileName(), label);
    }

    public int length() {
        return data.limit();
    }

    public String file() {
        return file;
    }

    public String lump() {
        return lump;
    }

    /** Fails unless {@code [at, at + count)} lies inside the lump. */
    public void require(long at, long count, String what) {
        if (at < 0 || count < 0 || at + count > data.limit()) {
            throw error(at, what + " outside the lump (needs " + count + " bytes at " + at + ", lump has "
                    + data.limit() + ")");
        }
    }

    /** Unsigned byte. */
    public int u8(int at) {
        require(at, 1, "byte");
        return data.get(at) & 0xFF;
    }

    /** Signed byte. */
    public int s8(int at) {
        require(at, 1, "byte");
        return data.get(at);
    }

    /** Signed 16-bit value ({@code short}). */
    public int s16(int at) {
        require(at, 2, "short");
        return data.getShort(at);
    }

    /** Unsigned 16-bit value ({@code unsigned short}). */
    public int u16(int at) {
        require(at, 2, "unsigned short");
        return data.getShort(at) & 0xFFFF;
    }

    /** Signed 32-bit value. */
    public int s32(int at) {
        require(at, 4, "int");
        return data.getInt(at);
    }

    /** A {@code char[8]} name, original case (see {@link LumpNames#read}). */
    public String name8(int at) {
        require(at, LumpNames.MAX_LENGTH, "name");
        return LumpNames.read(data, at);
    }

    /** Copy of {@code count} bytes at {@code at}. */
    public byte[] bytes(int at, int count) {
        require(at, count, "data");
        byte[] out = new byte[count];
        data.get(at, out);
        return out;
    }

    /** Copy of the whole lump. */
    public byte[] bytes() {
        return bytes(0, data.limit());
    }

    /** An exception about this lump at byte offset {@code at}. */
    public WadFormatException error(long at, String message) {
        return new WadFormatException(file, lump, at, message);
    }
}
