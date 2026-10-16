package doom.render.texture;

import doom.wad.LumpDirectory;
import doom.wad.gfx.Flat;
import doom.wad.texture.FlatDirectory;

/** The 64x64 flats of the {@code F_START..F_END} range, loaded on first use ({@code W_CacheLumpNum}). */
public final class FlatCache {

    private final LumpDirectory lumps;
    private final FlatDirectory directory;
    private final byte[][] cache;

    public FlatCache(LumpDirectory lumps) {
        this.lumps = lumps;
        this.directory = FlatDirectory.load(lumps);
        this.cache = new byte[directory.count()][];
    }

    public FlatDirectory directory() {
        return directory;
    }

    /** {@code numflats}. */
    public int count() {
        return cache.length;
    }

    /** The 4096 pixels of flat number {@code flat}, row by row. */
    public byte[] pixels(int flat) {
        byte[] p = cache[flat];
        if (p == null) {
            p = Flat.read(lumps, directory.lump(flat)).pixels();
            cache[flat] = p;
        }
        return p;
    }

    /** Loads every flat. */
    public void loadAll() {
        for (int i = 0; i < cache.length; i++) {
            if (lumps.length(directory.lump(i)) == 0) {
                continue; // F1_START-style namespace markers inside the flat range are not flats
            }
            pixels(i);
        }
    }
}
