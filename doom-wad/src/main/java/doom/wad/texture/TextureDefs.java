package doom.wad.texture;

import doom.wad.LumpDirectory;
import doom.wad.LumpReader;
import doom.wad.WadFormatException;
import java.util.ArrayList;
import java.util.List;

/**
 * Composite texture definitions from {@code TEXTURE1}, then {@code TEXTURE2} when present, resolved against
 * {@code PNAMES} the way {@code R_InitTextures} does. The order is the vanilla texture numbering.
 *
 * @param patchNames the PNAMES lump
 * @param textures   definitions: TEXTURE1 entries first, then TEXTURE2
 */
public record TextureDefs(PatchNames patchNames, TextureDef[] textures) {

    /** Bytes of a {@code maptexture_t} before its patches. */
    private static final int TEXTURE_HEADER = 22;
    /** Bytes of a {@code mappatch_t}. */
    private static final int PATCH_SIZE = 10;

    /**
     * One composite texture ({@code maptexture_t}).
     *
     * @param name    texture name, original case
     * @param masked  the (unused) {@code masked} flag
     * @param width   pixels
     * @param height  pixels
     * @param patches the patches, in drawing order
     */
    public record TextureDef(String name, boolean masked, int width, int height, TexturePatch[] patches) {
    }

    /**
     * One patch placed on a texture ({@code mappatch_t}).
     *
     * @param originX    x offset of the patch inside the texture
     * @param originY    y offset of the patch inside the texture
     * @param patchIndex index into PNAMES
     * @param lump       lump number of the patch ({@code patchlookup[]}), or -1 when no such lump exists
     */
    public record TexturePatch(int originX, int originY, int patchIndex, int lump) {
    }

    /**
     * Reads the definitions from a lump directory. {@code TEXTURE1} and {@code PNAMES} must exist.
     *
     * @throws WadFormatException on malformed data or missing lumps
     */
    public static TextureDefs read(LumpDirectory lumps) {
        PatchNames names = PatchNames.read(LumpReader.of(lumps, lumps.get(PatchNames.LUMP)));
        int[] patchLookup = new int[names.count()];
        for (int i = 0; i < patchLookup.length; i++) {
            patchLookup[i] = lumps.find(names.name(i));
        }
        List<TextureDef> all = new ArrayList<>();
        readLump(LumpReader.of(lumps, lumps.get("TEXTURE1")), names, patchLookup, all);
        int t2 = lumps.find("TEXTURE2");
        if (t2 >= 0) {
            readLump(LumpReader.of(lumps, t2), names, patchLookup, all);
        }
        return new TextureDefs(names, all.toArray(new TextureDef[0]));
    }

    /** Decodes one TEXTURE lump and appends its textures to {@code out}. */
    static void readLump(LumpReader r, PatchNames names, int[] patchLookup, List<TextureDef> out) {
        int count = r.s32(0);
        if (count < 0) {
            throw r.error(0, "negative texture count " + count);
        }
        r.require(4, 4L * count, "texture directory");
        for (int i = 0; i < count; i++) {
            int offset = r.s32(4 + 4 * i);
            // R_InitTextures only rejects offset > lump length; a texture that does not fit is also unreadable.
            if (offset < 0 || offset > r.length()) {
                throw r.error(4 + 4 * i, "bad texture directory (offset " + offset + ")");
            }
            r.require(offset, TEXTURE_HEADER, "texture header");
            String name = r.name8(offset);
            boolean masked = r.s32(offset + 8) != 0;
            int width = r.s16(offset + 12);
            int height = r.s16(offset + 14);
            int patchCount = r.s16(offset + 20);
            if (width < 0 || height < 0 || patchCount < 0) {
                throw r.error(offset, "texture " + name + " has a negative size or patch count");
            }
            r.require(offset + TEXTURE_HEADER, (long) PATCH_SIZE * patchCount, "patches of texture " + name);
            TexturePatch[] patches = new TexturePatch[patchCount];
            for (int j = 0; j < patchCount; j++) {
                int p = offset + TEXTURE_HEADER + PATCH_SIZE * j;
                int index = r.s16(p + 4);
                if (index < 0 || index >= patchLookup.length) {
                    throw r.error(p + 4, "texture " + name + " uses patch " + index + " but PNAMES has only "
                            + patchLookup.length + " names");
                }
                patches[j] = new TexturePatch(r.s16(p), r.s16(p + 2), index, patchLookup[index]);
            }
            out.add(new TextureDef(name, masked, width, height, patches));
        }
    }
}
