package doom.render;

/**
 * Settings of the 3D renderer (spec 04 §2).
 *
 * @param width          framebuffer width in pixels
 * @param height         framebuffer height in pixels; the projection follows {@code H} (Hor+, ADR-010): the horizontal
 *                       field of view grows with the aspect ratio while the vertical one stays that of the original
 * @param vanilla        reproduce the original frame exactly: only valid at 320x200; keeps the {@code & 127} texture
 *                       wrap, the original light tables and the 8-bit style clipping rules
 * @param precisionFixes use wide arithmetic and exact trigonometry at the known high-resolution artifacts (spec 04
 *                       §3): wall scale and texture columns, vertex angles, sprite limits and floor span steps;
 *                       ignored in vanilla mode
 * @param detail         0 = high detail, 1 = low detail (every column and span is drawn two pixels wide)
 * @param screenBlocks   view size, 3..11 ({@code screenblocks}); 10 and 11 span the whole width, 11 also the status bar
 * @param fuzzStyle      how the spectre fuzz maps to high resolutions
 * @param squarePixels   the frame is shown with square pixels (no 1.2 vertical stretch): the projection uses a 4:3
 *                       view of {@code H x 4/3} instead of {@code H x 1.6} so that the horizontal field of view of a
 *                       4:3 screen stays 90 degrees (ADR-010)
 */
public record RenderOptions(int width, int height, boolean vanilla, boolean precisionFixes, int detail,
                            int screenBlocks, FuzzStyle fuzzStyle, boolean squarePixels) {

    /** Fuzz offsets for resolutions above 320x200 (spec 04 §4). */
    public enum FuzzStyle {
        /** One row up or down per pixel, as the original (fine grain at high resolution). */
        PIXEL,
        /** The offset and the table position are applied in blocks of {@code H/200} rows and columns. */
        SCALED
    }

    /** Options with the 1.2 pixel aspect (no square-pixel projection). */
    public RenderOptions(int width, int height, boolean vanilla, boolean precisionFixes, int detail, int screenBlocks,
                         FuzzStyle fuzzStyle) {
        this(width, height, vanilla, precisionFixes, detail, screenBlocks, fuzzStyle, false);
    }

    public RenderOptions {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("invalid size " + width + "x" + height);
        }
        if (detail != 0 && detail != 1) {
            throw new IllegalArgumentException("detail must be 0 or 1: " + detail);
        }
        if (screenBlocks < 3 || screenBlocks > 11) {
            throw new IllegalArgumentException("screenBlocks must be 3..11: " + screenBlocks);
        }
        if (vanilla && (width != 320 || height != 200)) {
            throw new IllegalArgumentException("vanilla mode requires 320x200, got " + width + "x" + height);
        }
        if (fuzzStyle == null) {
            throw new NullPointerException("fuzzStyle");
        }
        if (vanilla && squarePixels) {
            throw new IllegalArgumentException("vanilla mode has the 1.2 pixel aspect");
        }
    }

    /** 320x200, vanilla mode, high detail, screenblocks 10 (the oracle configuration). */
    public static RenderOptions vanillaMode() {
        return new RenderOptions(320, 200, true, false, 0, 10, FuzzStyle.PIXEL);
    }

    /** Non-vanilla options for a {@code width x height} framebuffer: screenblocks 10, high detail, scaled fuzz. */
    public static RenderOptions of(int width, int height) {
        return new RenderOptions(width, height, false, true, 0, 10, FuzzStyle.SCALED);
    }

    public RenderOptions withSize(int w, int h) {
        return new RenderOptions(w, h, vanilla, precisionFixes, detail, screenBlocks, fuzzStyle, squarePixels);
    }

    public RenderOptions withScreenBlocks(int blocks) {
        return new RenderOptions(width, height, vanilla, precisionFixes, detail, blocks, fuzzStyle, squarePixels);
    }

    public RenderOptions withDetail(int d) {
        return new RenderOptions(width, height, vanilla, precisionFixes, d, screenBlocks, fuzzStyle, squarePixels);
    }

    public RenderOptions withFuzzStyle(FuzzStyle f) {
        return new RenderOptions(width, height, vanilla, precisionFixes, detail, screenBlocks, f, squarePixels);
    }

    public RenderOptions withPrecisionFixes(boolean on) {
        return new RenderOptions(width, height, vanilla, on, detail, screenBlocks, fuzzStyle, squarePixels);
    }

    /** Square-pixel projection (see {@link #squarePixels()}); ignored (kept false) in vanilla mode. */
    public RenderOptions withSquarePixels(boolean on) {
        return new RenderOptions(width, height, vanilla, precisionFixes, detail, screenBlocks, fuzzStyle,
                on && !vanilla);
    }

    /** {@code true} when the precise (wide arithmetic) renderer paths are used: outside vanilla with the fixes on. */
    public boolean precise() {
        return !vanilla && precisionFixes;
    }

    /** {@code true} when textures wrap at their real height (everything but vanilla mode). */
    public boolean correctTextureWrap() {
        return !vanilla;
    }
}
