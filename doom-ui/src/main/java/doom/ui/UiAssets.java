package doom.ui;

import doom.wad.LumpDirectory;
import doom.wad.gfx.Flat;
import doom.wad.gfx.Picture;
import java.util.HashMap;
import java.util.Map;

/**
 * Graphics of the UI, decoded lazily from the lump directory and cached ({@code W_CacheLumpName}). Missing lumps
 * give {@code null}; every drawing routine of the UI skips a missing patch, so partial WADs (and the unit tests
 * with {@link #empty()}) still work.
 */
public final class UiAssets {

    /** {@code HU_FONTSTART}: first character of the STCFN font ('!'). */
    public static final int FONT_START = '!';
    /** {@code HU_FONTSIZE}: '!' .. '_'. */
    public static final int FONT_SIZE = '_' - '!' + 1;

    private static final System.Logger LOG = System.getLogger(UiAssets.class.getName());

    private final LumpDirectory lumps;
    private final Map<String, Picture> patches = new HashMap<>();
    private final Map<String, Flat> flats = new HashMap<>();
    private final Picture[] font = new Picture[FONT_SIZE];
    private final boolean tallPatches;
    private boolean fontLoaded;

    public UiAssets(LumpDirectory lumps) {
        this(lumps, true);
    }

    /** @param tallPatches decode posts with the DeePsea relative-offset convention (false in vanilla mode) */
    public UiAssets(LumpDirectory lumps, boolean tallPatches) {
        this.lumps = lumps;
        this.tallPatches = tallPatches;
    }

    /** Assets with no lumps at all. */
    public static UiAssets empty() {
        return new UiAssets(null);
    }

    public LumpDirectory lumps() {
        return lumps;
    }

    /** Whether a lump with this name exists ({@code W_CheckNumForName >= 0}). */
    public boolean has(String name) {
        return lumps != null && lumps.find(name) >= 0;
    }

    /** The patch named {@code name}, or null when absent or not a valid picture. */
    public Picture patch(String name) {
        if (lumps == null) {
            return null;
        }
        if (patches.containsKey(name)) {
            return patches.get(name);
        }
        Picture p = null;
        int lump = lumps.find(name);
        if (lump >= 0) {
            try {
                p = Picture.read(lumps, lump, tallPatches);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "cannot decode patch {0}: {1}", name, e.getMessage());
            }
        }
        patches.put(name, p);
        return p;
    }

    /** The 64×64 flat named {@code name}, or null. */
    public Flat flat(String name) {
        if (lumps == null) {
            return null;
        }
        if (flats.containsKey(name)) {
            return flats.get(name);
        }
        Flat f = null;
        int lump = lumps.find(name);
        if (lump >= 0) {
            try {
                f = Flat.read(lumps, lump);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "cannot decode flat {0}: {1}", name, e.getMessage());
            }
        }
        flats.put(name, f);
        return f;
    }

    /**
     * Font glyph for character {@code ch} after {@code toupper}, or null when the character has no glyph (the
     * original advances 4 pixels for it).
     */
    public Picture glyph(int ch) {
        if (ch >= 'a' && ch <= 'z') {
            ch -= 32;
        }
        int i = ch - FONT_START;
        if (i < 0 || i >= FONT_SIZE) {
            return null;
        }
        if (!fontLoaded) {
            loadFont();
        }
        return font[i];
    }

    private void loadFont() {
        fontLoaded = true;
        for (int i = 0; i < FONT_SIZE; i++) {
            font[i] = patch(String.format("STCFN%03d", FONT_START + i));
        }
    }

    /** Height of the font ({@code hu_font[0]->height}), 7 when the font is missing. */
    public int fontHeight() {
        glyph('!');
        return font[0] != null ? font[0].height() : 7;
    }
}
