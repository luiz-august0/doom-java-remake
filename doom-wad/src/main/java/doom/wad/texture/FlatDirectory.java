package doom.wad.texture;

import doom.wad.LumpDirectory;
import doom.wad.LumpNames;
import doom.wad.WadFormatException;

/**
 * The flat range of the directory ({@code R_InitFlats}): every lump between the last {@code F_START} and the last
 * {@code F_END}. A flat number is the lump number minus {@code firstflat}.
 */
public final class FlatDirectory {

    private final LumpDirectory lumps;
    private final int firstFlat;
    private final int count;

    private FlatDirectory(LumpDirectory lumps, int firstFlat, int count) {
        this.lumps = lumps;
        this.firstFlat = firstFlat;
        this.count = count;
    }

    /**
     * {@code R_InitFlats}: {@code firstflat = F_START + 1}, {@code lastflat = F_END - 1}, both found by
     * {@code W_GetNumForName} (the last lump with that name).
     *
     * @throws WadFormatException when a marker is missing
     */
    public static FlatDirectory load(LumpDirectory lumps) {
        int first = lumps.get("F_START") + 1;
        int last = lumps.get("F_END") - 1;
        return new FlatDirectory(lumps, first, last - first + 1);
    }

    /** Number of flats ({@code numflats}). */
    public int count() {
        return count;
    }

    /** Lump number of flat 0 ({@code firstflat}). */
    public int firstLump() {
        return firstFlat;
    }

    /** Lump number of flat {@code flat}. */
    public int lump(int flat) {
        return firstFlat + flat;
    }

    /** Upper-case name of flat {@code flat}. */
    public String name(int flat) {
        return lumps.name(firstFlat + flat);
    }

    /**
     * {@code R_FlatNumForName}: lump number minus {@code firstflat}. As in vanilla the lump is found in the whole
     * directory (last one wins) and the result is not range-checked, so a name that is not a flat gives a number
     * outside {@code [0, count())}.
     *
     * @throws WadFormatException when no lump has that name
     */
    public int numForName(String name) {
        int i = lumps.find(name);
        if (i == -1) {
            throw new WadFormatException("R_FlatNumForName: " + LumpNames.key(name) + " not found");
        }
        return i - firstFlat;
    }

    /** True when {@code flat} addresses a lump inside the flat range. */
    public boolean isValid(int flat) {
        return flat >= 0 && flat < count;
    }
}
