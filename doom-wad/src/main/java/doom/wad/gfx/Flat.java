package doom.wad.gfx;

import doom.wad.LumpDirectory;
import doom.wad.WadFormatException;
import java.nio.ByteBuffer;

/**
 * A flat (spec 05 §4): a raw 64×64 block of palette indices, row-major ({@code pixels()[y * 64 + x]}), found between
 * the {@code F_START}/{@code F_END} markers. Only the first 4096 bytes are used, like the original; longer lumps are
 * accepted.
 *
 * <p>Immutable by contract: {@link #pixels()} is shared and must not be modified.
 */
public final class Flat {

    /** Width and height in pixels. */
    public static final int SIZE = 64;
    /** Bytes of pixel data. */
    public static final int BYTES = GfxFormat.FLAT_BYTES;

    private final String name;
    private final byte[] pixels;

    private Flat(String name, byte[] pixels) {
        this.name = name;
        this.pixels = pixels;
    }

    /** Decodes a flat lump without a name. */
    public static Flat decode(ByteBuffer lump) {
        return decode(lump, null, null);
    }

    /**
     * Decodes a flat lump.
     *
     * @throws WadFormatException if the lump is shorter than 4096 bytes
     */
    public static Flat decode(ByteBuffer lump, String fileName, String lumpName) {
        ByteBuffer b = lump.slice();
        int len = b.remaining();
        if (len < BYTES) {
            throw new WadFormatException(fileName, lumpName, 0, "flat too short: " + len + " bytes, need " + BYTES);
        }
        byte[] pixels = new byte[BYTES];
        b.get(0, pixels, 0, BYTES);
        return new Flat(lumpName, pixels);
    }

    /** Wraps 4096 bytes of pixels (copied). */
    public static Flat of(String name, byte[] pixels) {
        if (pixels.length < BYTES) {
            throw new IllegalArgumentException("a flat needs " + BYTES + " bytes, got " + pixels.length);
        }
        byte[] copy = new byte[BYTES];
        System.arraycopy(pixels, 0, copy, 0, BYTES);
        return new Flat(name, copy);
    }

    /** Decodes lump number {@code lump}. */
    public static Flat read(LumpDirectory lumps, int lump) {
        return decode(lumps.data(lump), lumps.wadOf(lump).fileName(), lumps.name(lump));
    }

    /** Decodes the last lump named {@code name}; fails if there is none. */
    public static Flat read(LumpDirectory lumps, String name) {
        return read(lumps, lumps.get(name));
    }

    /** Lump name, or {@code null}. */
    public String name() {
        return name;
    }

    /** Pixel at ({@code x & 63}, {@code y & 63}): coordinates wrap, as when the flat is tiled. */
    public int pixel(int x, int y) {
        return pixels[((y & 63) << 6) | (x & 63)] & 0xFF;
    }

    /** The 4096 pixels, row-major. Shared, never copied: do not modify. */
    public byte[] pixels() {
        return pixels;
    }

    @Override
    public String toString() {
        return "Flat[" + (name == null ? "?" : name) + "]";
    }
}
