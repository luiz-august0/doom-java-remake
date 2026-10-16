package doom.platform.lwjgl;

import doom.platform.ScaleMode;

/**
 * Where the frame lands inside the output (spec 04 §8, ADR-010). Pure arithmetic, no GL: unit-testable.
 *
 * <p>A frame pixel is displayed {@code pixelAspect} times taller than wide ({@code 1.2} for the original 5:6 pixels,
 * {@code 1.0} for square pixels). For the automatic resolution ({@code W = outputWidth}, {@code H = outputHeight/1.2})
 * the frame fills the output exactly with scale 1 horizontally.
 */
final class PresentGeometry {

    /** Destination rectangle in output pixels, origin top-left. */
    record Rect(int x, int y, int width, int height) {
    }

    private PresentGeometry() {
    }

    static Rect compute(ScaleMode mode, int outW, int outH, int frameW, int frameH, double pixelAspect) {
        if (outW <= 0 || outH <= 0 || frameW <= 0 || frameH <= 0) {
            return new Rect(0, 0, Math.max(outW, 0), Math.max(outH, 0));
        }
        double aspect = pixelAspect > 0 ? pixelAspect : 1.0;
        double logicalH = frameH * aspect;
        int dw;
        int dh;
        switch (mode) {
            case STRETCH -> {
                dw = outW;
                dh = outH;
            }
            case INTEGER -> {
                double fit = Math.min(outW / (double) frameW, outH / logicalH);
                int n = (int) Math.floor(fit + 1e-9);
                if (n < 1) {
                    dw = (int) Math.round(frameW * fit);
                    dh = (int) Math.round(logicalH * fit);
                } else {
                    dw = frameW * n;
                    dh = (int) Math.round(logicalH * n);
                }
            }
            default -> {
                double fit = Math.min(outW / (double) frameW, outH / logicalH);
                dw = (int) Math.round(frameW * fit);
                dh = (int) Math.round(logicalH * fit);
            }
        }
        dw = Math.max(1, Math.min(dw, outW));
        dh = Math.max(1, Math.min(dh, outH));
        return new Rect((outW - dw) / 2, (outH - dh) / 2, dw, dh);
    }
}
