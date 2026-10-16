package doom.wad.gfx;

import doom.wad.LumpDirectory;
import doom.wad.WadFormatException;
import java.nio.ByteBuffer;

/**
 * The palettes of the {@code PLAYPAL} lump (spec 05 §4): 14 palettes of 256 RGB triplets (768 bytes each).
 * Palette 0 is the normal one, 1–8 the damage (red) palettes, 9–12 the item pick-up (gold) palettes and 13 the
 * radiation suit (green) palette. Lumps with more than 14 palettes are accepted and every complete palette is kept.
 *
 * <p>Immutable. Palettes are handed out by copying, so callers never share the decoded bytes.
 */
public final class Palettes {

    /** Lump name. */
    public static final String LUMP = "PLAYPAL";
    /** Palettes the game uses. */
    public static final int COUNT = GfxFormat.PALETTE_COUNT;
    /** Bytes per palette. */
    public static final int BYTES = GfxFormat.PALETTE_BYTES;
    /** First damage palette ({@code STARTREDPALS}). */
    public static final int FIRST_DAMAGE = 1;
    /** Number of damage palettes ({@code NUMREDPALS}). */
    public static final int DAMAGE_COUNT = 8;
    /** First bonus palette ({@code STARTBONUSPALS}). */
    public static final int FIRST_BONUS = 9;
    /** Number of bonus palettes ({@code NUMBONUSPALS}). */
    public static final int BONUS_COUNT = 4;
    /** Radiation suit palette ({@code RADIATIONPAL}). */
    public static final int RADIATION = 13;

    private final byte[] rgb;
    private final int count;

    private Palettes(byte[] rgb, int count) {
        this.rgb = rgb;
        this.count = count;
    }

    /** Decodes a {@code PLAYPAL} lump. */
    public static Palettes decode(ByteBuffer lump) {
        return decode(lump, null, LUMP);
    }

    /**
     * Decodes a {@code PLAYPAL} lump.
     *
     * @throws WadFormatException if the lump holds fewer than {@link #COUNT} complete palettes
     */
    public static Palettes decode(ByteBuffer lump, String fileName, String lumpName) {
        ByteBuffer b = lump.slice();
        int len = b.remaining();
        if (len < COUNT * BYTES) {
            throw new WadFormatException(fileName, lumpName, 0,
                    "palette lump too short: " + len + " bytes, need " + COUNT * BYTES + " (" + COUNT + " palettes)");
        }
        int count = len / BYTES;
        byte[] rgb = new byte[count * BYTES];
        b.get(0, rgb, 0, rgb.length);
        return new Palettes(rgb, count);
    }

    /** Decodes the last {@code PLAYPAL} lump of the directory. */
    public static Palettes read(LumpDirectory lumps) {
        int lump = lumps.get(LUMP);
        return decode(lumps.data(lump), lumps.wadOf(lump).fileName(), lumps.name(lump));
    }

    /** Number of complete palettes in the lump (at least {@link #COUNT}). */
    public int count() {
        return count;
    }

    /** Red component (0..255) of colour {@code index} of palette {@code palette}. */
    public int red(int palette, int index) {
        return rgb[offset(palette, index)] & 0xFF;
    }

    /** Green component (0..255). */
    public int green(int palette, int index) {
        return rgb[offset(palette, index) + 1] & 0xFF;
    }

    /** Blue component (0..255). */
    public int blue(int palette, int index) {
        return rgb[offset(palette, index) + 2] & 0xFF;
    }

    /** Colour {@code index} of palette {@code palette} packed as {@code 0xRRGGBB}. */
    public int rgb(int palette, int index) {
        int o = offset(palette, index);
        return (rgb[o] & 0xFF) << 16 | (rgb[o + 1] & 0xFF) << 8 | (rgb[o + 2] & 0xFF);
    }

    /** Copies palette {@code palette} (768 bytes, R G B per entry) into {@code dst} at {@code offset}. No allocation. */
    public void copyTo(int palette, byte[] dst, int offset) {
        checkPalette(palette);
        System.arraycopy(rgb, palette * BYTES, dst, offset, BYTES);
    }

    /** A new 768-byte array with palette {@code palette}. */
    public byte[] toArray(int palette) {
        byte[] out = new byte[BYTES];
        copyTo(palette, out, 0);
        return out;
    }

    private int offset(int palette, int index) {
        checkPalette(palette);
        if (index < 0 || index > 255) {
            throw new IndexOutOfBoundsException("colour index " + index);
        }
        return palette * BYTES + index * 3;
    }

    private void checkPalette(int palette) {
        if (palette < 0 || palette >= count) {
            throw new IndexOutOfBoundsException("palette " + palette + " of " + count);
        }
    }
}
