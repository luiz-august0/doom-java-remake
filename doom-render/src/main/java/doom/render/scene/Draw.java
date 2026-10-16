package doom.render.scene;

import doom.math.annotation.VanillaRef;
import doom.render.RenderOptions;

/**
 * The column and span drawers of {@code r_draw.c}, writing into the framebuffer through its strides.
 *
 * <p>Parameters are passed in fields exactly as the original's {@code dc_*} / {@code ds_*} globals. Low detail draws two
 * adjacent pixels per column / span element.
 */
final class Draw {

    /** Source index handling of the column drawers. */
    static final int WRAP_VANILLA = 0;
    /** Wall columns outside vanilla mode: wrap at {@link #dcWrapHeight} texels. */
    static final int WRAP_HEIGHT = 1;
    /** Posts outside vanilla mode: clamp to {@code [0, dcLength)}. */
    static final int WRAP_CLAMP = 2;

    static final int COL_BASE = 0;
    static final int COL_FUZZ = 1;
    static final int COL_TRANSLATED = 2;

    private static final int FUZZTABLE = 50;
    /** {@code fuzzoffset[]}: +1 = the row below, -1 = the row above. */
    private static final int[] FUZZ_OFFSET = {
        1, -1, 1, -1, 1, 1, -1,
        1, 1, -1, 1, 1, 1, -1,
        1, 1, 1, -1, -1, -1, -1,
        1, -1, -1, 1, 1, 1, 1, -1,
        1, -1, 1, 1, -1, -1, 1,
        1, -1, -1, -1, -1, 1, 1,
        1, 1, -1, 1, 1, -1, 1
    };

    private final RenderState rs;

    // dc_*
    int dcX;
    int dcYl;
    int dcYh;
    int dcIscale;
    /** Precise mode: texels per pixel with 32 fractional bits; used instead of {@link #dcIscale} when {@link #dcFine}. */
    long dcIscaleP;
    /** Draw columns with {@link #dcIscaleP} (precise paths only; never in vanilla mode). */
    boolean dcFine;
    int dcTextureMid;
    byte[] dcSource;
    int dcSourceOfs;
    /** Offset of the light table in the colormap array. */
    int dcColormap;
    /** Offset of the translation table (0, 256 or 512). */
    int dcTranslation;
    int dcWrapMode;
    int dcWrapHeight;
    int dcLength;
    int colMode;

    // ds_*
    int dsY;
    int dsX1;
    int dsX2;
    int dsColormap;
    int dsXfrac;
    int dsYfrac;
    int dsXstep;
    int dsYstep;
    /** Precise mode: 32.32 positions and steps (used by {@link #drawSpanFine}). */
    long dsXfracP;
    long dsYfracP;
    long dsXstepP;
    long dsYstepP;
    byte[] dsSource;

    // fuzz state
    private int fuzzBlockX = Integer.MIN_VALUE;
    private int fuzzBlockStart;

    Draw(RenderState rs) {
        this.rs = rs;
    }

    /** {@code colfunc ()}. */
    void colfunc() {
        switch (colMode) {
            case COL_FUZZ -> drawFuzzColumn();
            case COL_TRANSLATED -> drawTranslatedColumn();
            default -> drawColumn();
        }
    }

    // ===================================================================================== R_DrawColumn

    @VanillaRef({"r_draw.c:R_DrawColumn", "r_draw.c:R_DrawColumnLow"})
    void drawColumn() {
        int count = dcYh - dcYl;
        if (count < 0) {
            return;
        }
        if (dcFine) {
            drawColumnFine(count);
            return;
        }
        final byte[] px = rs.pixels;
        final byte[] cm = rs.colormaps;
        final int cmo = dcColormap;
        final byte[] src = dcSource;
        final int so = dcSourceOfs;
        final int rowStride = rs.rowStride;
        int dest = rs.base + dcYl * rowStride + (dcX << rs.detailShift) * rs.colStride;
        final int fracstep = dcIscale;
        int frac = dcTextureMid + (dcYl - rs.centerY) * fracstep;
        final boolean low = rs.detailShift != 0;
        final int second = rs.colStride;
        switch (dcWrapMode) {
            case WRAP_VANILLA -> {
                if (!low) {
                    do {
                        px[dest] = cm[cmo + (src[so + ((frac >> 16) & 127)] & 0xFF)];
                        dest += rowStride;
                        frac += fracstep;
                    } while (count-- > 0);
                } else {
                    do {
                        byte c = cm[cmo + (src[so + ((frac >> 16) & 127)] & 0xFF)];
                        px[dest] = c;
                        px[dest + second] = c;
                        dest += rowStride;
                        frac += fracstep;
                    } while (count-- > 0);
                }
            }
            case WRAP_HEIGHT -> {
                final int hf = dcWrapHeight << 16;
                frac = Math.floorMod(frac, hf);
                do {
                    byte c = cm[cmo + (src[so + (frac >> 16)] & 0xFF)];
                    px[dest] = c;
                    if (low) {
                        px[dest + second] = c;
                    }
                    dest += rowStride;
                    frac += fracstep;
                    if (frac >= hf) {
                        frac -= hf;
                        if (frac >= hf) {
                            frac %= hf;
                        }
                    }
                } while (count-- > 0);
            }
            default -> {
                final int last = dcLength - 1;
                do {
                    int t = frac >> 16;
                    if (t < 0) {
                        t = 0;
                    } else if (t > last) {
                        t = last;
                    }
                    byte c = cm[cmo + (src[so + t] & 0xFF)];
                    px[dest] = c;
                    if (low) {
                        px[dest + second] = c;
                    }
                    dest += rowStride;
                    frac += fracstep;
                } while (count-- > 0);
            }
        }
    }

    /**
     * Precise columns: the texture position advances with 32 fractional bits (the 16.16 step of the original loses up
     * to a texel over a tall column at high resolution). Wall and post wrap modes only.
     */
    private void drawColumnFine(int count) {
        final byte[] px = rs.pixels;
        final byte[] cm = rs.colormaps;
        final int cmo = dcColormap;
        final byte[] src = dcSource;
        final int so = dcSourceOfs;
        final int rowStride = rs.rowStride;
        int dest = rs.base + dcYl * rowStride + (dcX << rs.detailShift) * rs.colStride;
        final long fracstep = dcIscaleP;
        long frac = ((long) dcTextureMid << 16) + (long) (dcYl - rs.centerY) * fracstep;
        final boolean low = rs.detailShift != 0;
        final int second = rs.colStride;
        if (dcWrapMode == WRAP_HEIGHT) {
            final long hf = (long) dcWrapHeight << 32;
            frac = Math.floorMod(frac, hf);
            do {
                byte c = cm[cmo + (src[so + (int) (frac >>> 32)] & 0xFF)];
                px[dest] = c;
                if (low) {
                    px[dest + second] = c;
                }
                dest += rowStride;
                frac += fracstep;
                if (frac >= hf) {
                    frac -= hf;
                    if (frac >= hf) {
                        frac %= hf;
                    }
                }
            } while (count-- > 0);
        } else {
            final int last = dcLength - 1;
            do {
                int t = (int) (frac >> 32);
                if (t < 0) {
                    t = 0;
                } else if (t > last) {
                    t = last;
                }
                byte c = cm[cmo + (src[so + t] & 0xFF)];
                px[dest] = c;
                if (low) {
                    px[dest + second] = c;
                }
                dest += rowStride;
                frac += fracstep;
            } while (count-- > 0);
        }
    }

    // ===================================================================================== fuzz

    /**
     * Spectre / invisibility: every pixel becomes colormap 6 of the pixel one row above or below, chosen by the 50-entry
     * table (position {@code fuzzpos}, which advances per pixel drawn).
     */
    @VanillaRef({"r_draw.c:R_DrawFuzzColumn", "r_draw.c:R_DrawFuzzColumnLow"})
    void drawFuzzColumn() {
        if (dcYl == 0) {
            dcYl = 1;
        }
        if (dcYh == rs.viewHeight - 1) {
            dcYh = rs.viewHeight - 2;
        }
        int count = dcYh - dcYl;
        if (count < 0) {
            return;
        }
        final byte[] px = rs.pixels;
        final byte[] cm = rs.colormaps;
        final int rowStride = rs.rowStride;
        int dest = rs.base + dcYl * rowStride + (dcX << rs.detailShift) * rs.colStride;
        final boolean low = rs.detailShift != 0;
        final int second = rs.colStride;
        int fuzzpos = rs.fuzzPos;
        final boolean scaled = rs.options.fuzzStyle() == RenderOptions.FuzzStyle.SCALED && rs.fbH >= 400;
        if (!scaled) {
            do {
                int off = FUZZ_OFFSET[fuzzpos] * rowStride;
                px[dest] = cm[6 * 256 + (px[dest + off] & 0xFF)];
                if (low) {
                    px[dest + second] = cm[6 * 256 + (px[dest + second + off] & 0xFF)];
                }
                if (++fuzzpos == FUZZTABLE) {
                    fuzzpos = 0;
                }
                dest += rowStride;
            } while (count-- > 0);
            rs.fuzzPos = fuzzpos;
            return;
        }
        // SCALED: blocks of k x k pixels share one table entry; the offset is k rows
        final int k = Math.max(1, Math.round(rs.fbH / 200f));
        final int bx = dcX / k;
        if (bx != fuzzBlockX) {
            fuzzBlockX = bx;
            fuzzBlockStart = fuzzpos;
        } else {
            fuzzpos = fuzzBlockStart;
        }
        int run = 0;
        int y = dcYl;
        do {
            int off = FUZZ_OFFSET[fuzzpos] * k * rowStride;
            // keep the source row inside the window
            int srcRow = y + FUZZ_OFFSET[fuzzpos] * k;
            if (srcRow < 0 || srcRow >= rs.viewHeight) {
                off = 0;
            }
            px[dest] = cm[6 * 256 + (px[dest + off] & 0xFF)];
            if (low) {
                px[dest + second] = cm[6 * 256 + (px[dest + second + off] & 0xFF)];
            }
            if (++run == k) {
                run = 0;
                if (++fuzzpos == FUZZTABLE) {
                    fuzzpos = 0;
                }
            }
            y++;
            dest += rowStride;
        } while (count-- > 0);
        if (run != 0 && ++fuzzpos == FUZZTABLE) {
            fuzzpos = 0;
        }
        rs.fuzzPos = fuzzpos;
    }

    /** Resets the per-frame fuzz block memory (scaled style). */
    void startFrame() {
        dcFine = false;
        fuzzBlockX = Integer.MIN_VALUE;
    }

    // ===================================================================================== translated

    @VanillaRef({"r_draw.c:R_DrawTranslatedColumn", "r_draw.c:R_DrawTranslatedColumnLow"})
    void drawTranslatedColumn() {
        int count = dcYh - dcYl;
        if (count < 0) {
            return;
        }
        final byte[] px = rs.pixels;
        final byte[] cm = rs.colormaps;
        final byte[] tr = rs.translations;
        final int cmo = dcColormap;
        final int tro = dcTranslation;
        final byte[] src = dcSource;
        final int so = dcSourceOfs;
        final int rowStride = rs.rowStride;
        int dest = rs.base + dcYl * rowStride + (dcX << rs.detailShift) * rs.colStride;
        final int fracstep = dcIscale;
        int frac = dcTextureMid + (dcYl - rs.centerY) * fracstep;
        final boolean low = rs.detailShift != 0;
        final int second = rs.colStride;
        final boolean clamp = dcWrapMode == WRAP_CLAMP;
        final int last = dcLength - 1;
        do {
            int t = frac >> 16;
            if (clamp) {
                if (t < 0) {
                    t = 0;
                } else if (t > last) {
                    t = last;
                }
            }
            byte c = cm[cmo + (tr[tro + (src[so + t] & 0xFF)] & 0xFF)];
            px[dest] = c;
            if (low) {
                px[dest + second] = c;
            }
            dest += rowStride;
            frac += fracstep;
        } while (count-- > 0);
    }

    // ===================================================================================== spans

    /**
     * {@code R_DrawSpan}. Vanilla mode reproduces Chocolate Doom's packed 6.10 + 6.10 position (the carry of the
     * v fraction into the u field included); otherwise u and v advance in 16.16.
     */
    @VanillaRef({"r_draw.c:R_DrawSpan", "r_draw.c:R_DrawSpanLow"})
    void drawSpan() {
        final byte[] px = rs.pixels;
        final byte[] cm = rs.colormaps;
        final int cmo = dsColormap;
        final byte[] src = dsSource;
        final int colStride = rs.colStride;
        final boolean low = rs.detailShift != 0;
        int count = dsX2 - dsX1;
        int dest = rs.base + dsY * rs.rowStride + (dsX1 << rs.detailShift) * colStride;
        if (rs.vanilla) {
            int position = ((dsXfrac << 10) & 0xffff0000) | ((dsYfrac >> 6) & 0x0000ffff);
            final int step = ((dsXstep << 10) & 0xffff0000) | ((dsYstep >> 6) & 0x0000ffff);
            if (!low) {
                do {
                    int spot = (position >>> 26) | ((position >>> 4) & 0x0fc0);
                    px[dest] = cm[cmo + (src[spot] & 0xFF)];
                    dest += colStride;
                    position += step;
                } while (count-- > 0);
            } else {
                do {
                    int spot = (position >>> 26) | ((position >>> 4) & 0x0fc0);
                    byte c = cm[cmo + (src[spot] & 0xFF)];
                    px[dest] = c;
                    px[dest + colStride] = c;
                    dest += 2 * colStride;
                    position += step;
                } while (count-- > 0);
            }
            return;
        }
        int xf = dsXfrac;
        int yf = dsYfrac;
        final int xs = dsXstep;
        final int ys = dsYstep;
        do {
            int spot = ((yf >> 10) & 0x0fc0) | ((xf >> 16) & 63);
            byte c = cm[cmo + (src[spot] & 0xFF)];
            px[dest] = c;
            if (low) {
                px[dest + colStride] = c;
                dest += 2 * colStride;
            } else {
                dest += colStride;
            }
            xf += xs;
            yf += ys;
        } while (count-- > 0);
    }

    /** Precise spans: 32.32 position and step ({@code dsXfracP} ...), flat coordinates taken from the high bits. */
    void drawSpanFine() {
        final byte[] px = rs.pixels;
        final byte[] cm = rs.colormaps;
        final int cmo = dsColormap;
        final byte[] src = dsSource;
        final int colStride = rs.colStride;
        final boolean low = rs.detailShift != 0;
        int count = dsX2 - dsX1;
        int dest = rs.base + dsY * rs.rowStride + (dsX1 << rs.detailShift) * colStride;
        long xf = dsXfracP;
        long yf = dsYfracP;
        final long xs = dsXstepP;
        final long ys = dsYstepP;
        do {
            int spot = ((int) (yf >> 26) & 0x0fc0) | ((int) (xf >> 32) & 63);
            byte c = cm[cmo + (src[spot] & 0xFF)];
            px[dest] = c;
            if (low) {
                px[dest + colStride] = c;
                dest += 2 * colStride;
            } else {
                dest += colStride;
            }
            xf += xs;
            yf += ys;
        } while (count-- > 0);
    }
}
