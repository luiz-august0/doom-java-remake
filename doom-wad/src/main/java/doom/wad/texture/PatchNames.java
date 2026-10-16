package doom.wad.texture;

import doom.wad.LumpReader;

/**
 * The {@code PNAMES} lump: the names of the patches that composite textures refer to by index.
 *
 * @param names patch names in lump order, original case (lookups are case-insensitive)
 */
public record PatchNames(String[] names) {

    /** The lump name. */
    public static final String LUMP = "PNAMES";

    /** Number of names ({@code nummappatches}). */
    public int count() {
        return names.length;
    }

    /** Name of patch {@code i}. */
    public String name(int i) {
        return names[i];
    }

    /**
     * Decodes PNAMES: an {@code int32} count followed by that many {@code char[8]} names.
     *
     * @throws doom.wad.WadFormatException when the count is negative or the lump is too short
     */
    public static PatchNames read(LumpReader r) {
        int count = r.s32(0);
        if (count < 0) {
            throw r.error(0, "negative patch count " + count);
        }
        r.require(4, 8L * count, "patch names");
        String[] names = new String[count];
        for (int i = 0; i < count; i++) {
            names[i] = r.name8(4 + 8 * i);
        }
        return new PatchNames(names);
    }
}
