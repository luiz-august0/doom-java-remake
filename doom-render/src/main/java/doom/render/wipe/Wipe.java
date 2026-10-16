package doom.render.wipe;

import doom.game.api.MiscRandom;
import doom.math.annotation.VanillaRef;
import doom.render.FrameBuffer;

/**
 * The melt wipe between screens (spec 04 §6), timed in tics; consumes the misc RNG exactly as {@code wipe_initMelt}
 * does: {@value #RNG_DRAWS} draws at the start, independent of the resolution.
 *
 * <p>The original melts 2-pixel strips of the 320x200 screen: each strip starts to fall after a random delay (the
 * column offsets {@code y[i]} are a random walk), the old screen slides down and the new one shows above it. Here the
 * state stays in 320x200 "virtual" units, so the timing is identical at every size; strips are {@code 2·s} pixels wide
 * ({@code s = H/200}) and the fall is scaled to the framebuffer height. In widescreen the extra strips left and right
 * of the 4:3 area reuse the other random-walk values of the same draw.
 *
 * <p>Usage: {@link #start} with the old and the new screen, then {@link #tick} once per frame with the number of tics
 * elapsed ({@code wipe_ScreenWipe}); {@code tick} returns {@code true} when the wipe is over.
 */
public final class Wipe {

    /** {@code SCREENWIDTH} draws of {@code M_Random} at the start ({@code y[0]} plus 319 columns). */
    public static final int RNG_DRAWS = 320;
    private static final int VIRTUAL_HEIGHT = 200;

    private final MiscRandom rng;
    private final int[] y = new int[RNG_DRAWS];
    private final boolean[] used = new boolean[RNG_DRAWS];
    private byte[] startCols = new byte[0];
    private byte[] endCols = new byte[0];
    private int[] columnStrip = new int[0];
    private int width;
    private int height;
    private boolean active;

    public Wipe(MiscRandom rng) {
        this.rng = rng;
    }

    /** Whether a wipe is running. */
    public boolean active() {
        return active;
    }

    /**
     * Starts a wipe ({@code wipe_StartScreen}, {@code wipe_EndScreen}, {@code wipe_initMelt}). Copies both screens
     * (they must have the same size) and draws {@link #RNG_DRAWS} values from the misc RNG.
     */
    @VanillaRef("f_wipe.c:wipe_initMelt")
    public void start(FrameBuffer from, FrameBuffer to) {
        if (from.width() != to.width() || from.height() != to.height()) {
            throw new IllegalArgumentException("screens differ in size");
        }
        width = from.width();
        height = from.height();
        int n = width * height;
        if (startCols.length < n) {
            startCols = new byte[n];
            endCols = new byte[n];
        }
        if (columnStrip.length < width) {
            columnStrip = new int[width];
        }
        toColumns(from, startCols);
        toColumns(to, endCols);
        y[0] = -(rng.next() % 16);
        for (int i = 1; i < RNG_DRAWS; i++) {
            int r = (rng.next() % 3) - 1;
            y[i] = y[i - 1] + r;
            if (y[i] > 0) {
                y[i] = 0;
            } else if (y[i] == -16) {
                y[i] = -15;
            }
        }
        // physical column -> strip of the virtual 320x200 screen (2 virtual columns per strip), centred
        for (int px = 0; px < width; px++) {
            int v = Math.floorDiv(2 * (2 * px - width) * VIRTUAL_HEIGHT + 2 * 320 * height, 4 * height);
            columnStrip[px] = Math.floorMod(Math.floorDiv(v, 2), RNG_DRAWS);
        }
        java.util.Arrays.fill(used, false);
        for (int px = 0; px < width; px++) {
            used[columnStrip[px]] = true;
        }
        active = true;
    }

    private static void toColumns(FrameBuffer fb, byte[] dst) {
        int w = fb.width();
        int h = fb.height();
        byte[] px = fb.pixels();
        int rs = fb.rowStride();
        int cs = fb.columnStride();
        for (int x = 0; x < w; x++) {
            int o = x * h;
            for (int r = 0; r < h; r++) {
                dst[o + r] = px[x * cs + r * rs];
            }
        }
    }

    /**
     * {@code wipe_doMelt} for {@code tics} tics, then draws the current state into {@code dest} (same size as the
     * screens).
     *
     * @return {@code true} when the wipe is finished (the original's return value of {@code wipe_ScreenWipe})
     */
    @VanillaRef("f_wipe.c:wipe_doMelt")
    public boolean tick(int tics, FrameBuffer dest) {
        if (!active) {
            throw new IllegalStateException("no wipe running");
        }
        if (dest.width() != width || dest.height() != height) {
            throw new IllegalArgumentException("destination differs in size");
        }
        boolean done = true;
        int strips = 160;
        while (tics-- > 0) {
            for (int i = 0; i < strips; i++) {
                if (y[i] < 0) {
                    y[i]++;
                    done = false;
                } else if (y[i] < VIRTUAL_HEIGHT) {
                    int dy = (y[i] < 16) ? y[i] + 1 : 8;
                    if (y[i] + dy >= VIRTUAL_HEIGHT) {
                        dy = VIRTUAL_HEIGHT - y[i];
                    }
                    y[i] += dy;
                    done = false;
                }
            }
            // strips outside the 4:3 area (widescreen) follow the same rules
            for (int i = strips; i < RNG_DRAWS; i++) {
                if (y[i] < 0) {
                    y[i]++;
                    done &= !used[i];
                } else if (y[i] < VIRTUAL_HEIGHT) {
                    int dy = (y[i] < 16) ? y[i] + 1 : 8;
                    if (y[i] + dy >= VIRTUAL_HEIGHT) {
                        dy = VIRTUAL_HEIGHT - y[i];
                    }
                    y[i] += dy;
                    done &= !used[i];
                }
            }
        }
        render(dest);
        if (done) {
            active = false;
        }
        return done;
    }

    private void render(FrameBuffer dest) {
        final byte[] px = dest.pixels();
        final int rs = dest.rowStride();
        final int cs = dest.columnStride();
        final int h = height;
        for (int x = 0; x < width; x++) {
            int yv = y[columnStrip[x]];
            int split;
            if (yv < 0) {
                split = 0;
            } else if (yv >= VIRTUAL_HEIGHT) {
                split = h;
            } else {
                split = (int) (((long) yv * h + VIRTUAL_HEIGHT / 2) / VIRTUAL_HEIGHT);
            }
            int col = x * h;
            int d = x * cs;
            // rows above the split: the new screen in place; below: the old screen sliding down
            for (int r = 0; r < split; r++) {
                px[d + r * rs] = endCols[col + r];
            }
            for (int r = split; r < h; r++) {
                px[d + r * rs] = startCols[col + r - split];
            }
        }
    }
}
