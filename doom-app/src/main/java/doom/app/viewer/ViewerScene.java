package doom.app.viewer;

import doom.render.FrameBuffer;
import doom.render.canvas.Canvas2D;
import doom.wad.LumpDirectory;
import doom.wad.gfx.Flat;
import doom.wad.gfx.Palettes;
import doom.wad.gfx.Picture;

/**
 * What the viewer shows: one full-screen picture (TITLEPIC by default) on the 2D canvas, with a black or flat-tiled
 * pillarbox. Also holds the CPU model of the GPU presentation, used to compare against the real window.
 */
final class ViewerScene {

    /** Flat used for the tiled pillarbox when none is named. */
    static final String DEFAULT_FLAT = "FLOOR4_8";

    private final Picture picture;
    private final Flat flat;
    private final Palettes palettes;

    ViewerScene(LumpDirectory lumps, String lumpName, String flatName, boolean vanilla) {
        this.picture = Picture.read(lumps, lumpName, !vanilla);
        this.palettes = Palettes.read(lumps);
        Flat f = null;
        String[] candidates = {flatName, DEFAULT_FLAT, "FLOOR0_1"};
        for (String c : candidates) {
            if (c != null && lumps.find(c) >= 0) {
                f = Flat.read(lumps, c);
                break;
            }
        }
        this.flat = f;
    }

    Palettes palettes() {
        return palettes;
    }

    Picture picture() {
        return picture;
    }

    boolean hasFlat() {
        return flat != null;
    }

    /** Draws the scene into {@code frame}. */
    void draw(FrameBuffer frame, Canvas2D canvas, boolean flatSides) {
        canvas.clear(0);
        if (flatSides && flat != null) {
            canvas.tileSides(flat);
        }
        canvas.drawPatchCentred(picture, 0);
    }

    /** Frame size for an output of {@code outW × outH}: vanilla 320×200, otherwise H = outH/1.2, W = outW. */
    static int[] frameSize(boolean vanilla, boolean square, int outW, int outH) {
        if (vanilla) {
            return new int[] {320, 200};
        }
        return new int[] {Math.max(1, outW), Math.max(1, square ? outH : (int) Math.round(outH / 1.2))};
    }

    /**
     * CPU model of the GPU presentation (FIT, nearest, pixel aspect 1.2 or 1.0): RGBA of {@code outW × outH}, black
     * bars. Used to compare against {@code readBackOutput()}.
     */
    static byte[] presentOnCpu(FrameBuffer frame, byte[] palette768, int outW, int outH, double pixelAspect) {
        byte[] out = new byte[outW * outH * 4];
        for (int i = 3; i < out.length; i += 4) {
            out[i] = (byte) 0xFF;
        }
        double logicalH = frame.height() * pixelAspect;
        double fit = Math.min(outW / (double) frame.width(), outH / logicalH);
        int dw = Math.max(1, Math.min(outW, (int) Math.round(frame.width() * fit)));
        int dh = Math.max(1, Math.min(outH, (int) Math.round(logicalH * fit)));
        int x0 = (outW - dw) / 2;
        int y0 = (outH - dh) / 2;
        for (int y = 0; y < dh; y++) {
            int fy = Math.min(frame.height() - 1, (int) ((y + 0.5) / dh * frame.height()));
            for (int x = 0; x < dw; x++) {
                int fx = Math.min(frame.width() - 1, (int) ((x + 0.5) / dw * frame.width()));
                int idx = frame.get(fx, fy);
                int o = ((y0 + y) * outW + x0 + x) * 4;
                out[o] = palette768[idx * 3];
                out[o + 1] = palette768[idx * 3 + 1];
                out[o + 2] = palette768[idx * 3 + 2];
            }
        }
        return out;
    }
}
