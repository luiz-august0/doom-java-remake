package doom.it.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.render.FrameBuffer;
import doom.render.FrameLayout;
import doom.render.RenderOptions;
import doom.testkit.Iwads;
import doom.testkit.oracle.OracleRunner;
import doom.tools.frame.GoldenFrames;
import doom.tools.frame.SceneSidecar;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The renderer outside vanilla mode: any size, both framebuffer layouts, low detail, view sizes, no allocation. */
class HighResolutionRenderIT {

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

    private static GoldenFrames open(String demo, List<Path> scenes) throws IOException {
        SceneSidecar first = SceneSidecar.read(scenes.get(0));
        return new GoldenFrames(Iwads.require(first.iwad));
    }

    @Test
    void layoutsAndSizesRenderTheSameImage() throws IOException {
        List<Path> scenes = scenes("doom1-demo2");
        GoldenFrames gf = open("doom1-demo2", scenes);
        for (Path scene : scenes) {
            SceneSidecar sc = SceneSidecar.read(scene);
            for (int[] size : new int[][] {{2560, 900}, {1280, 540}, {3440, 1440}, {640, 400}, {800, 600}}) {
                RenderOptions o = RenderOptions.of(size[0], size[1]).withScreenBlocks(11);
                FrameBuffer rows = new FrameBuffer(size[0], size[1], FrameLayout.ROW_MAJOR);
                FrameBuffer cols = new FrameBuffer(size[0], size[1], FrameLayout.COLUMN_MAJOR);
                gf.renderWith(o, rows, sc);
                gf.renderWith(o, cols, sc);
                for (int y = 0; y < size[1]; y += 3) {
                    for (int x = 0; x < size[0]; x += 2) {
                        assertThat(cols.get(x, y)).as("%s %dx%d (%d,%d)", scene.getFileName(), size[0], size[1], x, y)
                                .isEqualTo(rows.get(x, y));
                    }
                }
            }
        }
    }

    @Test
    void viewSizesAndLowDetailRender() throws IOException {
        List<Path> scenes = scenes("freedoom2-demo1");
        GoldenFrames gf = open("freedoom2-demo1", scenes);
        SceneSidecar sc = SceneSidecar.read(scenes.get(3));
        for (int blocks = 3; blocks <= 11; blocks++) {
            for (int detail = 0; detail <= 1; detail++) {
                RenderOptions o = RenderOptions.of(1920, 810).withScreenBlocks(blocks).withDetail(detail);
                FrameBuffer fb = new FrameBuffer(1920, 810);
                fb.clear(255);
                gf.renderWith(o, fb, sc);
                int w = gf.wideRenderer().viewWindowWidth();
                int h = gf.wideRenderer().viewWindowHeight();
                int x0 = gf.wideRenderer().viewWindowX();
                int y0 = gf.wideRenderer().viewWindowY();
                assertThat(x0 + w).isLessThanOrEqualTo(1920);
                assertThat(y0 + h).isLessThanOrEqualTo(810);
                // the window is fully drawn; nothing outside it is touched
                int drawn = 0;
                for (int y = 0; y < 810; y++) {
                    for (int x = 0; x < 1920; x++) {
                        boolean inside = x >= x0 && x < x0 + w && y >= y0 && y < y0 + h;
                        if (!inside) {
                            assertThat(fb.get(x, y)).as("outside the window at %d,%d (blocks %d)", x, y, blocks)
                                    .isEqualTo(255);
                        } else if (fb.get(x, y) != 255) {
                            drawn++;
                        }
                    }
                }
                assertThat(drawn).as("blocks %d detail %d", blocks, detail).isGreaterThan(w * h * 9 / 10);
            }
        }
    }

    @Test
    void steadyStateAllocatesNothing() throws IOException {
        List<Path> scenes = scenes("doom1-demo1");
        GoldenFrames gf = open("doom1-demo1", scenes);
        SceneSidecar sc = SceneSidecar.read(scenes.get(1));
        RenderOptions o = RenderOptions.of(1280, 540).withScreenBlocks(11);
        FrameBuffer fb = new FrameBuffer(1280, 540);
        // warm up: the first frames load textures, flats and sprites and grow the pools
        for (int i = 0; i < 5; i++) {
            gf.renderWith(o, fb, sc);
        }
        com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long id = Thread.currentThread().threadId();
        // SceneView is built per call by the test helper: measure the renderer alone
        var renderer = gf.wideRenderer();
        var view = new doom.tools.frame.SceneView(gf.geometry(sc), sc);
        var cam = new doom.render.Camera().set(sc.viewX, sc.viewY, sc.viewZ, sc.viewAngle, sc.extraLight,
                sc.fixedColormap, view.viewActor());
        renderer.render(fb, view, view, view, cam);
        long before = mx.getThreadAllocatedBytes(id);
        for (int i = 0; i < 20; i++) {
            renderer.render(fb, view, view, view, cam);
        }
        long after = mx.getThreadAllocatedBytes(id);
        assertThat(after - before).as("bytes allocated by 20 frames").isLessThan(2048);
        assertThat(Arrays.asList(fb.pixels()).size()).isPositive();
    }
}
