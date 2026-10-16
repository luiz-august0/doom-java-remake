package doom.engine;

/**
 * Chooses the internal resolution from the output size (spec 04 section 2): in automatic mode the width equals the
 * output width and the height is {@code outputHeight / 1.2} (the presentation stretches vertically by 1.2), so that
 * each internal pixel is one screen pixel horizontally. Vanilla mode is 320x200.
 *
 * @param width       internal width
 * @param height      internal height
 * @param pixelAspect height/width of one internal pixel when displayed (1.2 with aspect correction)
 */
public record FrameSizing(int width, int height, double pixelAspect) {

    /** Smallest internal frame (the UI and the canvas need at least the virtual 320x200 screen). */
    public static final int MIN_WIDTH = 320;
    public static final int MIN_HEIGHT = 200;
    /** Largest internal dimension. */
    public static final int MAX_DIMENSION = 8192;

    public static FrameSizing compute(int outputWidth, int outputHeight, boolean aspectCorrection, double renderScale,
                                      boolean vanilla) {
        if (vanilla) {
            return new FrameSizing(320, 200, 1.2);
        }
        double aspect = aspectCorrection ? 1.2 : 1.0;
        int w = (int) Math.round(Math.max(1, outputWidth) * renderScale);
        int h = (int) Math.round(Math.max(1, outputHeight) / aspect * renderScale);
        w = Math.max(MIN_WIDTH, Math.min(MAX_DIMENSION, w));
        h = Math.max(MIN_HEIGHT, Math.min(MAX_DIMENSION, h));
        return new FrameSizing(w, h, aspect);
    }
}
