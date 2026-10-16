package doom.wad.gfx;

/**
 * Sizes of the graphics lumps (spec 05 §4). The decoders are {@link Picture}, {@link Palettes}, {@link Colormaps} and
 * {@link Flat}.
 */
public final class GfxFormat {
    /** Palettes in PLAYPAL. */
    public static final int PALETTE_COUNT = 14;
    /** Bytes per palette (256 RGB triplets). */
    public static final int PALETTE_BYTES = 768;
    /** Light tables in COLORMAP (32 light levels, invulnerability, black). */
    public static final int COLORMAP_COUNT = 34;
    /** Bytes per flat (64×64). */
    public static final int FLAT_BYTES = 4096;

    private GfxFormat() {
    }
}
