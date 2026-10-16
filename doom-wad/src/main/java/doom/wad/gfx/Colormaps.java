package doom.wad.gfx;

import doom.wad.LumpDirectory;
import doom.wad.WadFormatException;
import java.nio.ByteBuffer;

/**
 * The light tables of the {@code COLORMAP} lump (spec 05 §4): 34 maps of 256 palette indices. Maps 0–31 darken from
 * full bright (0) to darkest (31), map 32 is the invulnerability (inverse grey) map and map 33 is all black.
 *
 * <p>The tables are kept as one flat {@code byte[]} ({@code table()[map * 256 + colour]}) because the renderer's inner
 * loops index it directly (spec 04 §9). A lump with only 33 maps (everything the original code reads) is accepted and
 * the black map is synthesized as all zeros.
 */
public final class Colormaps {

    /** Lump name. */
    public static final String LUMP = "COLORMAP";
    /** Number of maps. */
    public static final int COUNT = GfxFormat.COLORMAP_COUNT;
    /** Light levels ({@code NUMCOLORMAPS}). */
    public static final int LIGHT_LEVELS = 32;
    /** The invulnerability map ({@code INVERSECOLORMAP}). */
    public static final int INVULNERABILITY = 32;
    /** The all-black map. */
    public static final int BLACK = 33;
    /** Bytes per map. */
    public static final int MAP_BYTES = 256;

    private static final int MIN_MAPS = INVULNERABILITY + 1;

    private final byte[] table;

    private Colormaps(byte[] table) {
        this.table = table;
    }

    /** Decodes a {@code COLORMAP} lump. */
    public static Colormaps decode(ByteBuffer lump) {
        return decode(lump, null, LUMP);
    }

    /**
     * Decodes a {@code COLORMAP} lump.
     *
     * @throws WadFormatException if the lump has fewer than 33 complete maps
     */
    public static Colormaps decode(ByteBuffer lump, String fileName, String lumpName) {
        ByteBuffer b = lump.slice();
        int len = b.remaining();
        if (len < MIN_MAPS * MAP_BYTES) {
            throw new WadFormatException(fileName, lumpName, 0,
                    "colormap lump too short: " + len + " bytes, need " + COUNT * MAP_BYTES + " (" + COUNT + " maps)");
        }
        byte[] table = new byte[COUNT * MAP_BYTES];
        b.get(0, table, 0, Math.min(len, table.length));
        return new Colormaps(table);
    }

    /** Decodes the last {@code COLORMAP} lump of the directory. */
    public static Colormaps read(LumpDirectory lumps) {
        int lump = lumps.get(LUMP);
        return decode(lumps.data(lump), lumps.wadOf(lump).fileName(), lumps.name(lump));
    }

    /** Number of maps ({@link #COUNT}). */
    public int count() {
        return COUNT;
    }

    /** Palette index that colour {@code colour} becomes under map {@code map}. */
    public int map(int map, int colour) {
        return table[map * MAP_BYTES + colour] & 0xFF;
    }

    /** Offset of map {@code map} in {@link #table()}. */
    public static int offset(int map) {
        return map * MAP_BYTES;
    }

    /** All maps, {@code COUNT * 256} bytes. Shared, never copied: do not modify. */
    public byte[] table() {
        return table;
    }
}
