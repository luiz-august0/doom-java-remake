package doom.wad.texture;

import doom.wad.LumpDirectory;
import doom.wad.LumpNames;
import doom.wad.WadFormatException;
import doom.wad.texture.TextureDefs.TextureDef;
import doom.wad.texture.TextureDefs.TexturePatch;
import java.util.HashMap;
import java.util.Map;

/**
 * Composite texture names and sizes with vanilla numbering (TEXTURE1 entries first, then TEXTURE2), and the
 * {@code R_TextureNumForName} / {@code R_CheckTextureNumForName} lookups. When two textures share a name the first
 * one wins, as in vanilla's linear search.
 */
public final class TextureDirectory {

    private final TextureDefs defs;
    private final Map<String, Integer> byName = new HashMap<>();

    private TextureDirectory(TextureDefs defs) {
        this.defs = defs;
        TextureDef[] t = defs.textures();
        for (int i = 0; i < t.length; i++) {
            byName.putIfAbsent(LumpNames.key(t[i].name()), i);
        }
    }

    /**
     * Loads {@code PNAMES}, {@code TEXTURE1} and {@code TEXTURE2} ({@code R_InitTextures}).
     *
     * @throws WadFormatException on malformed data or when a texture uses a patch that is not in the directory
     */
    public static TextureDirectory load(LumpDirectory lumps) {
        return of(TextureDefs.read(lumps));
    }

    /** Builds the directory from already decoded definitions; fails on missing patch lumps like vanilla. */
    public static TextureDirectory of(TextureDefs defs) {
        for (TextureDef t : defs.textures()) {
            for (TexturePatch p : t.patches()) {
                if (p.lump() < 0) {
                    throw new WadFormatException("R_InitTextures: Missing patch in texture " + t.name());
                }
            }
        }
        return new TextureDirectory(defs);
    }

    /** The decoded definitions. */
    public TextureDefs defs() {
        return defs;
    }

    /** Number of textures ({@code numtextures}). */
    public int count() {
        return defs.textures().length;
    }

    /** The definition of texture {@code tex}. */
    public TextureDef def(int tex) {
        return defs.textures()[tex];
    }

    /** Name of texture {@code tex}, original case. */
    public String name(int tex) {
        return defs.textures()[tex].name();
    }

    /** Width in pixels. */
    public int width(int tex) {
        return defs.textures()[tex].width();
    }

    /** Height in pixels. */
    public int heightPixels(int tex) {
        return defs.textures()[tex].height();
    }

    /** Height in 16.16 fixed point ({@code textureheight[]}). */
    public int height(int tex) {
        return defs.textures()[tex].height() << 16;
    }

    /** {@code texturewidthmask[]}: the largest power of two not above the width, minus one. */
    public int widthMask(int tex) {
        int w = defs.textures()[tex].width();
        int j = 1;
        while (j * 2 <= w) {
            j <<= 1;
        }
        return j - 1;
    }

    /**
     * {@code R_CheckTextureNumForName}: the texture number, 0 for the "-" (no texture) marker, or -1 when the name is
     * unknown. Names are compared case-insensitively over 8 characters.
     */
    public int checkNumForName(String name) {
        if (!name.isEmpty() && name.charAt(0) == '-') {
            return 0;
        }
        Integer n = byName.get(LumpNames.key(name));
        return n == null ? -1 : n;
    }

    /**
     * {@code R_TextureNumForName}.
     *
     * @throws WadFormatException when the texture does not exist
     */
    public int numForName(String name) {
        int i = checkNumForName(name);
        if (i == -1) {
            throw new WadFormatException("R_TextureNumForName: " + LumpNames.key(name) + " not found");
        }
        return i;
    }
}
