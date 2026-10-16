package doom.ui;

/**
 * The 3D view window in virtual 320×200 coordinates for a view size ({@code R_ExecuteSetViewSize}). The engine and
 * the renderer scale it with the canvas, so the status bar, the border and the pause sign line up with the view.
 *
 * @param x      {@code viewwindowx}
 * @param y      {@code viewwindowy}
 * @param width  {@code scaledviewwidth}
 * @param height {@code viewheight}
 */
public record ViewWindow(int x, int y, int width, int height) {

    /** Smallest and largest {@code screenblocks}. */
    public static final int MIN_BLOCKS = 3;
    public static final int MAX_BLOCKS = 11;

    /** Height of the status bar in virtual rows. */
    public static final int STATUS_BAR_HEIGHT = 32;

    /** Window for {@code screenblocks} (3..11; 11 = full screen, no status bar). */
    public static ViewWindow of(int screenBlocks) {
        int blocks = Math.max(MIN_BLOCKS, Math.min(MAX_BLOCKS, screenBlocks));
        int w;
        int h;
        if (blocks == MAX_BLOCKS) {
            w = 320;
            h = 200;
        } else {
            w = blocks * 32;
            h = (blocks * 168 / 10) & ~7;
        }
        int x = (320 - w) >> 1;
        int y = w == 320 ? 0 : (200 - STATUS_BAR_HEIGHT - h) >> 1;
        return new ViewWindow(x, y, w, h);
    }

    /** Whether the view covers the whole screen (no status bar, no border). */
    public boolean fullScreen() {
        return height == 200;
    }
}
