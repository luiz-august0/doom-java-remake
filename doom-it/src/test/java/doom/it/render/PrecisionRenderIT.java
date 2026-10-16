package doom.it.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.render.FrameBuffer;
import doom.render.RenderOptions;
import doom.testkit.Iwads;
import doom.testkit.oracle.OracleRunner;
import doom.tools.frame.GoldenFrames;
import doom.tools.frame.SceneSidecar;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The precise renderer paths ({@link RenderOptions#precisionFixes()}) against the plain generalisation of the original
 * (same options with the fixes off), and PNG output at the target resolutions (spec 04 section 3, W4-R).
 *
 * <p>The two paths use different arithmetic (exact line geometry against fine-angle tables), so single pixels differ
 * (rounding of the edges of walls and sprites), but the images must agree on the whole: a wrong sign or offset in the
 * precise maths would show as a large difference.
 */
class PrecisionRenderIT {

    /** PNGs are written to {@code target/render-png} for visual inspection (also set by {@code -Drender.png.dir}). */
    private static Path pngDir() throws IOException {
        String dir = System.getProperty("render.png.dir");
        Path p = dir != null ? Path.of(dir) : Path.of("target", "render-png");
        Files.createDirectories(p);
        return p;
    }

    private static List<Path> scenes(String demo) throws IOException {
        Iwads.assumeDirectory();
        Path dir = OracleRunner.cacheDirectory().resolve("frames").resolve(demo);
        assumeTrue(Files.isDirectory(dir), "no frames for " + demo);
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().endsWith(".scene")).sorted().forEach(out::add);
        }
        return out;
    }

    private static GoldenFrames open(List<Path> scenes) throws IOException {
        return new GoldenFrames(Iwads.require(SceneSidecar.read(scenes.get(0)).iwad));
    }

    private static double differing(FrameBuffer a, FrameBuffer b, int x0, int y0, int w, int h) {
        long diff = 0;
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                if (a.get(x, y) != b.get(x, y)) {
                    diff++;
                }
            }
        }
        return diff / (double) ((long) w * h);
    }

    @Test
    void preciseAgreesWithPlainAtOriginalResolution() throws IOException {
        for (String demo : new String[] {"doom1-demo1", "freedoom2-demo1", "freedoom1-demo2"}) {
            List<Path> scenes = scenes(demo);
            GoldenFrames gf = open(scenes);
            double worst = 0;
            for (Path scene : scenes) {
                SceneSidecar sc = SceneSidecar.read(scene);
                if (sc.automap != 0) {
                    continue;
                }
                RenderOptions plain = RenderOptions.of(320, 200).withScreenBlocks(sc.screenBlocks)
                        .withPrecisionFixes(false);
                FrameBuffer a = new FrameBuffer(320, 200);
                FrameBuffer b = new FrameBuffer(320, 200);
                gf.renderWith(plain, a, sc);
                gf.renderWith(plain.withPrecisionFixes(true), b, sc);
                double d = differing(a, b, sc.windowX, sc.windowY, sc.windowWidth, sc.windowHeight);
                byte[] raw = Files.readAllBytes(scene.resolveSibling(sc.rawName));
                FrameBuffer golden = new FrameBuffer(320, 200);
                System.arraycopy(raw, 0, golden.pixels(), 0, 320 * 200);
                if (System.getProperty("render.diff.dir") != null) {
                    byte[] rgba = new byte[960 * 200 * 4];
                    for (int y = 0; y < 200; y++) {
                        for (int x = 0; x < 320; x++) {
                            int[] v = {golden.get(x, y), b.get(x, y), golden.get(x, y) != b.get(x, y) ? 1 : 0};
                            for (int k = 0; k < 3; k++) {
                                int o = (y * 960 + k * 320 + x) * 4;
                                int rgb = k < 2 ? gf.palettes().rgb(sc.palette, v[k]) : (v[2] != 0 ? 0xFF2020 : 0);
                                rgba[o] = (byte) (rgb >> 16);
                                rgba[o + 1] = (byte) (rgb >> 8);
                                rgba[o + 2] = (byte) rgb;
                                rgba[o + 3] = (byte) 0xFF;
                            }
                        }
                    }
                    doom.render.image.PngWriter.writeRgba(Path.of(System.getProperty("render.diff.dir"),
                            demo + "-" + sc.tic + ".png"), rgba, 960, 200);
                }
                int wx = sc.windowX;
                int wy = sc.windowY + 10;
                int ww = sc.windowWidth;
                int wh = sc.windowHeight - 10;
                // the oracle frame is the ground truth at 320x200; the plain path reproduces its quantisation (differs
                // by under 4%), the exact one differs where the original's fine-angle tables are off (floors)
                assertThat(differing(a, golden, wx, wy, ww, wh)).as("plain %s tic %d", demo, sc.tic).isLessThan(0.05);
                assertThat(differing(b, golden, wx, wy, ww, wh)).as("precise %s tic %d", demo, sc.tic)
                        .isLessThan(0.25);
                worst = Math.max(worst, d);
                assertThat(d).as("%s tic %d", demo, sc.tic).isLessThan(0.25);
            }
            System.out.printf("PrecisionRenderIT %s 320x200: worst difference %.2f%%%n", demo, worst * 100);
        }
    }

    @Test
    void preciseAgreesWithPlainAtHighResolution() throws IOException {
        List<Path> scenes = scenes("freedoom2-demo1");
        GoldenFrames gf = open(scenes);
        for (int[] size : new int[][] {{2560, 900}, {3440, 1200}, {5120, 1200}, {1366, 640}}) {
            double worst = 0;
            for (Path scene : scenes) {
                SceneSidecar sc = SceneSidecar.read(scene);
                if (sc.automap != 0) {
                    continue;
                }
                RenderOptions plain = RenderOptions.of(size[0], size[1]).withScreenBlocks(11)
                        .withPrecisionFixes(false);
                FrameBuffer a = new FrameBuffer(size[0], size[1]);
                FrameBuffer b = new FrameBuffer(size[0], size[1]);
                gf.renderWith(plain, a, sc);
                gf.renderWith(plain.withPrecisionFixes(true), b, sc);
                double d = differing(a, b, 0, 0, size[0], size[1]);
                worst = Math.max(worst, d);
                assertThat(d).as("%dx%d tic %d", size[0], size[1], sc.tic).isLessThan(0.30);
            }
            System.out.printf("PrecisionRenderIT %dx%d: worst difference %.2f%%%n", size[0], size[1], worst * 100);
        }
    }


    /** Mean luminance of each group of 8 columns over the {@code band} rows around the horizon. */
    private static double[] horizonProfile(GoldenFrames gf, SceneSidecar sc, FrameBuffer fb, int band) {
        double[] out = new double[fb.width() / 8];
        int cy = fb.height() / 2;
        for (int c = 0; c < out.length; c++) {
            double sum = 0;
            for (int y = cy - band; y < cy + band; y++) {
                for (int x = c * 8; x < c * 8 + 8; x++) {
                    int rgb = gf.palettes().rgb(sc.palette, fb.get(x, y));
                    sum += (rgb >> 16 & 255) * 0.3 + (rgb >> 8 & 255) * 0.59 + (rgb & 255) * 0.11;
                }
            }
            out[c] = sum / (16.0 * band);
        }
        return out;
    }

    private static double meanAbs(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += Math.abs(a[i] - b[i]);
        }
        return sum / a.length;
    }

    /**
     * Square pixels: on a 4:3 screen the horizontal field of view stays 90 degrees, so a {@code 1280x960} frame of
     * square pixels shows the same columns as a {@code 1280x800} frame of 1.2-tall pixels (the vertical scale differs
     * by design: no stretch). A frame laid out with the wrong projection (1.6 H instead of 4/3 H) is clearly off.
     */
    @Test
    void squarePixelsKeepTheHorizontalFieldOfView() throws IOException {
        List<Path> scenes = scenes("doom1-demo2");
        GoldenFrames gf = open(scenes);
        for (int pick : new int[] {1, 3, 5, 7, 9}) {
            SceneSidecar sc = SceneSidecar.read(scenes.get(pick));
            if (sc.automap != 0) {
                continue;
            }
            FrameBuffer a = new FrameBuffer(1280, 800);
            FrameBuffer b = new FrameBuffer(1280, 960);
            FrameBuffer wrong = new FrameBuffer(1280, 960);
            gf.renderWith(RenderOptions.of(1280, 800).withScreenBlocks(11), a, sc);
            gf.renderWith(RenderOptions.of(1280, 960).withScreenBlocks(11).withSquarePixels(true), b, sc);
            gf.renderWith(RenderOptions.of(1280, 960).withScreenBlocks(11), wrong, sc);
            double ok = meanAbs(horizonProfile(gf, sc, a, 24), horizonProfile(gf, sc, b, 24));
            double bad = meanAbs(horizonProfile(gf, sc, a, 24), horizonProfile(gf, sc, wrong, 24));
            assertThat(ok).as("scene %d", sc.tic).isLessThan(bad);
        }
    }

    @Test
    void writesPngsAtTargetResolutions() throws IOException {
        Path dir = pngDir();
        for (String demo : new String[] {"doom1-demo1", "freedoom2-demo1"}) {
            List<Path> scenes = scenes(demo);
            GoldenFrames gf = open(scenes);
            int[] picks = {1, scenes.size() / 2, scenes.size() - 2};
            for (int pick : picks) {
                SceneSidecar sc = SceneSidecar.read(scenes.get(pick));
                if (sc.automap != 0) {
                    continue;
                }
                for (int[] size : new int[][] {{2560, 900}, {3440, 1200}}) {
                    for (boolean precise : new boolean[] {false, true}) {
                        RenderOptions o = RenderOptions.of(size[0], size[1]).withScreenBlocks(11)
                                .withPrecisionFixes(precise);
                        FrameBuffer fb = new FrameBuffer(size[0], size[1]);
                        gf.renderWith(o, fb, sc);
                        gf.writePng(dir.resolve(String.format("%s-%d-%dx%d-%s.png", demo, sc.tic, size[0], size[1],
                                precise ? "precise" : "plain")), fb, sc);
                    }
                }
            }
        }
    }
}
