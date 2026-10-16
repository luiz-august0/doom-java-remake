package doom.render.canvas;

import doom.render.FrameBuffer;
import doom.wad.gfx.Flat;
import doom.wad.gfx.Picture;

/**
 * Virtual 320×200 canvas scaled (s = H/200) and centred into the framebuffer; draws patches, rectangles and tiled
 * flats (spec 04 §6, ADR-010).
 *
 * <h2>Coordinate model</h2>
 * Drawing calls take <em>virtual</em> coordinates, the 320×200 space of the original {@code V_*} functions. The
 * framebuffer is {@code W×H}; the scale is {@code s = H/200} (fractional) and the canvas is centred horizontally:
 * {@code xOffset = (W − 320·s)/2}. Vertically the canvas always fills the frame ({@code 200·s = H}).
 *
 * <p>Sampling is nearest and exact: virtual column {@code v} covers the physical columns
 * {@code [px(v), px(v+1))} with {@code px(v) = round(W/2 + H·(v − 160)/200)} (round half up, integer arithmetic) and
 * virtual row {@code r} covers {@code [py(r), py(r+1))} with {@code py(r) = round(H·r/200)}. The boundaries are
 * shared by neighbouring pixels, so adjacent patches never overlap or leave gaps, and at {@code 320×200} the mapping
 * is the identity: the output is pixel-identical to the original {@code V_DrawPatch}.
 *
 * <p>Virtual coordinates are not limited to 0..319 / 0..199: drawing is clipped against the real frame, so wide
 * pictures spill over the sides (they show in the pillarbox area) and the area left and right of the 320 space is
 * addressable (negative x, x ≥ 320).
 *
 * <h2>Draw semantics</h2>
 * {@link #drawPatch} follows {@code V_DrawPatch}: the picture's {@code leftoffset}/{@code topoffset} are subtracted
 * from the given position, posts are drawn in lump order, and (like the original) transparent pixels do not exist
 * inside posts: every post pixel overwrites the destination. Patches are clipped silently instead of raising
 * "Bad V_DrawPatch". Wide pictures (wider than 320) are placed by their {@code leftoffset}; see
 * {@link #drawPatchCentred}.
 *
 * <h2>Allocation</h2>
 * The lookup tables are rebuilt only when the {@link FrameBuffer} size changes (detected at the start of each call);
 * all drawing calls are allocation free.
 *
 * <p>Layout independent: pixels are written through the frame buffer's strides, so row- and column-major frames work.
 * Not thread-safe.
 */
public final class Canvas2D {

    /** Width of the virtual canvas. */
    public static final int VIRTUAL_WIDTH = 320;
    /** Height of the virtual canvas. */
    public static final int VIRTUAL_HEIGHT = 200;

    private final FrameBuffer frame;

    private int w = -1;
    private int h = -1;
    /** Virtual column of {@code xBound[0]}. */
    private int vxLo;
    /** Boundaries {@code px(vxLo + i)}, {@code i = 0..vxCount}. */
    private int[] xBound = new int[0];
    private int vxCount;
    /** {@code py(r)} for {@code r = 0..200}. */
    private final int[] yBound = new int[VIRTUAL_HEIGHT + 1];
    /** Virtual column of each physical column. */
    private int[] xInverse = new int[0];
    /** Virtual row of each physical row. */
    private int[] yInverse = new int[0];

    public Canvas2D(FrameBuffer frame) {
        if (frame == null) {
            throw new NullPointerException("frame");
        }
        this.frame = frame;
        relayout();
    }

    /** The frame this canvas draws into. */
    public FrameBuffer frame() {
        return frame;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Geometry
    // ---------------------------------------------------------------------------------------------------------

    /** Scale {@code s = H/200}. */
    public double scale() {
        return frame.height() / (double) VIRTUAL_HEIGHT;
    }

    /** Physical x of the left edge of the 320 space: {@code (W − 320·s)/2} (may be a half pixel). */
    public double xOffset() {
        return (frame.width() - VIRTUAL_WIDTH * scale()) / 2.0;
    }

    /** Physical column where virtual column {@code vx} starts (its left edge), unclipped. */
    public int physicalX(int vx) {
        return boundX(vx);
    }

    /** Physical row where virtual row {@code vy} starts (its top edge), unclipped. */
    public int physicalY(int vy) {
        return boundY(vy);
    }

    /** Virtual column that contains physical column {@code x} ({@code 0 <= x < W}). */
    public int virtualX(int x) {
        ensureLayout();
        return xInverse[x];
    }

    /** Virtual row that contains physical row {@code y} ({@code 0 <= y < H}). */
    public int virtualY(int y) {
        ensureLayout();
        return yInverse[y];
    }

    /** Physical column of the left edge of the 320 space, i.e. the width of the left pillarbox bar. */
    public int leftBarWidth() {
        return Math.max(0, Math.min(frame.width(), boundX(0)));
    }

    /** Physical column just after the right edge of the 320 space. */
    public int rightBarStart() {
        return Math.max(0, Math.min(frame.width(), boundX(VIRTUAL_WIDTH)));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Patches
    // ---------------------------------------------------------------------------------------------------------

    /** {@code V_DrawPatch}: draws {@code patch} with its offsets subtracted from ({@code x}, {@code y}). */
    public void drawPatch(Picture patch, int x, int y) {
        drawPatchImpl(patch, x, y, false, null);
    }

    /** {@code V_DrawPatchFlipped}: horizontally mirrored. */
    public void drawPatchFlipped(Picture patch, int x, int y) {
        drawPatchImpl(patch, x, y, true, null);
    }

    /**
     * Like {@link #drawPatch} but every pixel goes through {@code remap} (256 entries; a colour translation or a
     * colormap row copied out of {@code Colormaps.table()} with its offset applied by the caller).
     */
    public void drawPatch(Picture patch, int x, int y, byte[] remap) {
        drawPatchImpl(patch, x, y, false, remap);
    }

    /**
     * Draws a full-width picture such as a title page: horizontally centred on the canvas. The picture's
     * {@code leftoffset} decides the placement (widescreen assets store {@code width/2}); a picture without
     * {@code leftoffset} is centred by its width. Normal 320-wide pictures end up at x = 0.
     *
     * @param y virtual y (the {@code topoffset} is subtracted, as in {@link #drawPatch})
     */
    public void drawPatchCentred(Picture patch, int y) {
        int x = patch.leftOffset() != 0 ? VIRTUAL_WIDTH / 2 : VIRTUAL_WIDTH / 2 - patch.width() / 2;
        drawPatchImpl(patch, x, y, false, null);
    }

    private void drawPatchImpl(Picture patch, int x, int y, boolean flip, byte[] remap) {
        ensureLayout();
        x -= patch.leftOffset();
        y -= patch.topOffset();
        int pw = patch.width();
        int cLo = Math.max(0, vxLo - x);
        int cHi = Math.min(pw, vxLo + vxCount - x);
        final byte[] px = frame.pixels();
        final byte[] data = patch.data();
        final int colStride = frame.columnStride();
        final int rowStride = frame.rowStride();
        final int fw = w;
        final int[] yb = yBound;
        for (int c = cLo; c < cHi; c++) {
            int x0 = xBound[x + c - vxLo];
            int x1 = xBound[x + c + 1 - vxLo];
            if (x0 < 0) {
                x0 = 0;
            }
            if (x1 > fw) {
                x1 = fw;
            }
            if (x0 >= x1) {
                continue;
            }
            int sc = flip ? pw - 1 - c : c;
            for (int post = patch.firstPost(sc), end = patch.endPost(sc); post < end; post++) {
                int top = y + patch.postTop(post);
                int len = patch.postLength(post);
                int src = patch.postDataOffset(post);
                int r0 = Math.max(0, -top);
                int r1 = Math.min(len, VIRTUAL_HEIGHT - top);
                for (int r = r0; r < r1; r++) {
                    int y0 = yb[top + r];
                    int y1 = yb[top + r + 1];
                    if (y0 >= y1) {
                        continue;
                    }
                    byte colour = data[src + r];
                    if (remap != null) {
                        colour = remap[colour & 0xFF];
                    }
                    if (colStride == 1) {
                        // row-major frame: write contiguous runs
                        if (x1 - x0 == 1) {
                            for (int yy = y0; yy < y1; yy++) {
                                px[yy * rowStride + x0] = colour;
                            }
                        } else {
                            for (int yy = y0; yy < y1; yy++) {
                                int o = yy * rowStride;
                                java.util.Arrays.fill(px, o + x0, o + x1, colour);
                            }
                        }
                    } else {
                        for (int xx = x0; xx < x1; xx++) {
                            int base = xx * colStride;
                            for (int yy = y0; yy < y1; yy++) {
                                px[base + yy * rowStride] = colour;
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Rectangles and flats
    // ---------------------------------------------------------------------------------------------------------

    /** Fills the whole frame, including the pillarbox bars. */
    public void clear(int colour) {
        frame.clear(colour);
    }

    /** Fills a rectangle given in virtual coordinates (clipped to the frame). */
    public void fillRect(int vx, int vy, int vw, int vh, int colour) {
        ensureLayout();
        fillPixels(boundX(vx), boundY(vy), boundX(vx + vw), boundY(vy + vh), colour);
    }

    /**
     * Fills the physical pixel rectangle {@code [x0, x1) × [y0, y1)} (clipped). Used for the pillarbox bars and for
     * code that works in native pixels, such as the automap.
     */
    public void fillPixels(int x0, int y0, int x1, int y1, int colour) {
        x0 = Math.max(0, x0);
        y0 = Math.max(0, y0);
        x1 = Math.min(frame.width(), x1);
        y1 = Math.min(frame.height(), y1);
        if (x0 >= x1 || y0 >= y1) {
            return;
        }
        byte[] px = frame.pixels();
        byte c = (byte) colour;
        int colStride = frame.columnStride();
        int rowStride = frame.rowStride();
        if (rowStride == 1) {
            for (int x = x0; x < x1; x++) {
                int base = x * colStride;
                java.util.Arrays.fill(px, base + y0, base + y1, c);
            }
        } else {
            for (int y = y0; y < y1; y++) {
                int base = y * rowStride;
                java.util.Arrays.fill(px, base + x0, base + x1, c);
            }
        }
    }

    /**
     * Tiles {@code flat} over a rectangle given in virtual coordinates (clipped to the frame). The flat is scaled with
     * the canvas and its phase is anchored at virtual (0, 0) like {@code R_FillBackScreen}, so the tiles of the
     * status-bar border line up whichever part of the screen is filled.
     */
    public void tileFlat(Flat flat, int vx, int vy, int vw, int vh) {
        ensureLayout();
        tileFlatPixels(flat, boundX(vx), boundY(vy), boundX(vx + vw), boundY(vy + vh));
    }

    /** Tiles {@code flat} over the whole frame (virtual phase as in {@link #tileFlat}). */
    public void tileFlatFull(Flat flat) {
        ensureLayout();
        tileFlatPixels(flat, 0, 0, w, h);
    }

    /** Like {@link #tileFlat} for the physical pixel rectangle {@code [x0, x1) × [y0, y1)}. */
    public void tileFlatPixels(Flat flat, int x0, int y0, int x1, int y1) {
        ensureLayout();
        x0 = Math.max(0, x0);
        y0 = Math.max(0, y0);
        x1 = Math.min(w, x1);
        y1 = Math.min(h, y1);
        if (x0 >= x1 || y0 >= y1) {
            return;
        }
        byte[] px = frame.pixels();
        byte[] fp = flat.pixels();
        int colStride = frame.columnStride();
        int rowStride = frame.rowStride();
        int[] xi = xInverse;
        int[] yi = yInverse;
        if (rowStride == 1) {
            for (int x = x0; x < x1; x++) {
                int col = xi[x] & 63;
                int base = x * colStride;
                for (int y = y0; y < y1; y++) {
                    px[base + y] = fp[((yi[y] & 63) << 6) | col];
                }
            }
        } else {
            for (int y = y0; y < y1; y++) {
                int row = (yi[y] & 63) << 6;
                int base = y * rowStride;
                for (int x = x0; x < x1; x++) {
                    px[base + x] = fp[row | (xi[x] & 63)];
                }
            }
        }
    }

    /** Fills the pillarbox bars (left and right of the 320 space) with one colour. */
    public void fillSides(int colour) {
        ensureLayout();
        fillPixels(0, 0, leftBarWidth(), h, colour);
        fillPixels(rightBarStart(), 0, w, h, colour);
    }

    /** Tiles the pillarbox bars with {@code flat}. */
    public void tileSides(Flat flat) {
        ensureLayout();
        tileFlatPixels(flat, 0, 0, leftBarWidth(), h);
        tileFlatPixels(flat, rightBarStart(), 0, w, h);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Layout tables
    // ---------------------------------------------------------------------------------------------------------

    private void ensureLayout() {
        if (w != frame.width() || h != frame.height()) {
            relayout();
        }
    }

    /** {@code px(v) = round(W/2 + H·(v − 160)/200)}, half up. */
    private int boundX(long vx) {
        return (int) Math.floorDiv(100L * frame.width() + (long) frame.height() * (vx - VIRTUAL_WIDTH / 2) + 100L, 200L);
    }

    /** {@code py(r) = round(H·r/200)}, half up. */
    private int boundY(long vy) {
        return (int) Math.floorDiv((long) frame.height() * vy + 100L, 200L);
    }

    private void relayout() {
        w = frame.width();
        h = frame.height();
        // Visible virtual columns satisfy 160 - 100W/H <= v < 160 + 100W/H; add a margin of 2.
        int half = (int) ((100L * w + h - 1) / h) + 2;
        vxLo = VIRTUAL_WIDTH / 2 - half;
        vxCount = 2 * half;
        if (xBound.length < vxCount + 1) {
            xBound = new int[vxCount + 1];
        }
        for (int i = 0; i <= vxCount; i++) {
            xBound[i] = boundX(vxLo + i);
        }
        for (int r = 0; r <= VIRTUAL_HEIGHT; r++) {
            yBound[r] = boundY(r);
        }
        if (xInverse.length < w) {
            xInverse = new int[w];
        }
        if (yInverse.length < h) {
            yInverse = new int[h];
        }
        for (int i = 0; i < vxCount; i++) {
            int a = Math.max(0, xBound[i]);
            int b = Math.min(w, xBound[i + 1]);
            for (int x = a; x < b; x++) {
                xInverse[x] = vxLo + i;
            }
        }
        for (int r = 0; r < VIRTUAL_HEIGHT; r++) {
            int a = Math.max(0, yBound[r]);
            int b = Math.min(h, yBound[r + 1]);
            for (int y = a; y < b; y++) {
                yInverse[y] = r;
            }
        }
    }
}
