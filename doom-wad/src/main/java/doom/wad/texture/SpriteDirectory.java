package doom.wad.texture;

import doom.wad.LumpDirectory;
import doom.wad.LumpNames;
import doom.wad.WadFormatException;

/**
 * The sprite lump range ({@code R_InitSpriteLumps}): every lump between the last {@code S_START} and the last
 * {@code S_END}. A sprite lump index is the lump number minus {@code firstspritelump}.
 */
public final class SpriteDirectory {

    private final LumpDirectory lumps;
    private final int firstSpriteLump;
    private final int count;

    private SpriteDirectory(LumpDirectory lumps, int firstSpriteLump, int count) {
        this.lumps = lumps;
        this.firstSpriteLump = firstSpriteLump;
        this.count = count;
    }

    /**
     * {@code firstspritelump = S_START + 1}, {@code lastspritelump = S_END - 1}.
     *
     * @throws WadFormatException when a marker is missing
     */
    public static SpriteDirectory load(LumpDirectory lumps) {
        int first = lumps.get("S_START") + 1;
        int last = lumps.get("S_END") - 1;
        return new SpriteDirectory(lumps, first, last - first + 1);
    }

    /** Number of sprite lumps ({@code numspritelumps}). */
    public int count() {
        return count;
    }

    /** Lump number of sprite lump 0 ({@code firstspritelump}). */
    public int firstLump() {
        return firstSpriteLump;
    }

    /** Lump number of sprite lump {@code i}. */
    public int lump(int i) {
        return firstSpriteLump + i;
    }

    /** Upper-case name of sprite lump {@code i}. */
    public String name(int i) {
        return lumps.name(firstSpriteLump + i);
    }

    /** Sprite lump index of the last lump of the range with this name, or -1. */
    public int find(String name) {
        String key = LumpNames.key(name);
        for (int i = count - 1; i >= 0; i--) {
            if (lumps.name(firstSpriteLump + i).equals(key)) {
                return i;
            }
        }
        return -1;
    }
}
