package doom.it.render;

import static org.assertj.core.api.Assertions.assertThat;

import doom.engine.DoomEngine;
import doom.engine.EngineParams;
import doom.engine.FrameSizing;
import doom.engine.cli.LaunchOptions;
import doom.engine.config.Config;
import doom.engine.config.DataDirs;
import doom.engine.config.Settings;
import doom.platform.Clock;
import doom.platform.InputSource;
import doom.platform.VideoConfig;
import doom.platform.VideoSink;
import doom.platform.input.InputEventQueue;
import doom.render.image.PngWriter;
import doom.testkit.Iwads;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The whole engine (real renderer, UI, interpolation, wipe) against a recording window at widescreen sizes: frame
 * sizing for aspect ratios up to 32:9, render scale, square pixels, status bar sides and the minimal full-screen HUD
 * (PNGs in {@code target/render-png} for a look with the eyes), and the simulation staying in sync with the reference
 * trace while frames are interpolated.
 */
class WidescreenEngineIT {

    private static final Path SHOTS = Path.of("target", "render-png");

    @TempDir
    Path tmp;

    /** Every call advances 3 milliseconds (about 330 frames per second); waiting jumps to the deadline. */
    private static final class ManualClock implements Clock {
        private long now;

        @Override
        public long nanoTime() {
            now += 3_000_000;
            return now;
        }

        @Override
        public void waitUntil(long deadlineNanos) {
            now = Math.max(now, deadlineNanos);
        }
    }

    private static final class RecordingVideo implements VideoSink {
        final int outW;
        final int outH;
        byte[] palette = new byte[768];
        byte[] lastPixels;
        int lastWidth;
        int lastHeight;
        int presented;

        RecordingVideo(int outW, int outH) {
            this.outW = outW;
            this.outH = outH;
        }

        @Override
        public void open(VideoConfig config) {
        }

        @Override
        public void reconfigure(VideoConfig config) {
        }

        @Override
        public int outputWidth() {
            return outW;
        }

        @Override
        public int outputHeight() {
            return outH;
        }

        @Override
        public void setPalette(byte[] rgb768) {
            palette = rgb768.clone();
        }

        @Override
        public void present(byte[] indexed, int width, int height, boolean columnMajor) {
            lastPixels = Arrays.copyOf(indexed, width * height);
            lastWidth = width;
            lastHeight = height;
            presented++;
        }

        @Override
        public boolean closeRequested() {
            return false;
        }

        @Override
        public void close() {
        }

        int pixel(int x, int y) {
            return lastPixels[y * lastWidth + x] & 0xFF;
        }

        int distinctColours() {
            boolean[] seen = new boolean[256];
            int n = 0;
            for (byte b : lastPixels) {
                if (!seen[b & 0xFF]) {
                    seen[b & 0xFF] = true;
                    n++;
                }
            }
            return n;
        }

        void writePng(Path file) throws Exception {
            Files.createDirectories(file.toAbsolutePath().getParent());
            PngWriter.writeIndexed(file, lastPixels, lastWidth, lastHeight, false, palette);
        }
    }

    private static final class NoInput implements InputSource {
        @Override
        public void poll(InputEventQueue queue) {
        }

        @Override
        public void setMouseCaptured(boolean captured) {
        }
    }

    private DoomEngine engine(RecordingVideo video, Config config, String... args) {
        LaunchOptions options = LaunchOptions.parse(args);
        EngineParams params = new EngineParams(options).config(config)
                .dirs(DataDirs.resolve("Linux", Map.of("XDG_CONFIG_HOME", tmp.toString()), tmp.toString()))
                .clock(new ManualClock())
                .platform(new EngineParams.Platform(video, new NoInput(), null));
        return new DoomEngine(params);
    }

    private record Case(String name, int outW, int outH, boolean aspect, double scale, int blocks, boolean hud) {
    }

    @Test
    void widescreenFramesAreSizedAndDrawn() throws Exception {
        Path iwad = Iwads.assumeAvailable(Iwads.DOOM1);
        List<Case> cases = List.of(
                new Case("21x9-2560x1080", 2560, 1080, true, 1.0, 10, false),
                new Case("43x18-3440x1440", 3440, 1440, true, 1.0, 10, false),
                new Case("32x9-5120x1440", 5120, 1440, true, 1.0, 10, false),
                new Case("16x9-hud-1920x1080", 1920, 1080, true, 1.0, 11, true),
                new Case("21x9-hud-3440x1440", 3440, 1440, true, 1.0, 11, true),
                new Case("square-pixels-1920x1080", 1920, 1080, false, 1.0, 10, false),
                new Case("scale-0.5-2560x1080", 2560, 1080, true, 0.5, 10, false),
                new Case("scale-2.0-1280x720", 1280, 720, true, 2.0, 10, false),
                new Case("small-view-2560x1080", 2560, 1080, true, 1.0, 7, false));
        for (Case c : cases) {
            Config config = Config.defaults();
            config.set(Settings.VIDEO_ASPECT_CORRECTION, c.aspect());
            config.set(Settings.VIDEO_RENDER_SCALE, c.scale());
            config.set(Settings.GAME_SCREEN_BLOCKS, c.blocks());
            config.set(Settings.HUD_FULLSCREEN, c.hud() ? "minimal" : "off");
            RecordingVideo video = new RecordingVideo(c.outW(), c.outH());
            try (DoomEngine engine = engine(video, config, "-iwad", iwad.toString(), "-warp", "1", "1", "-skill", "3",
                    "-maxtics", "150")) {
                engine.run();
            }
            FrameSizing size = FrameSizing.compute(c.outW(), c.outH(), c.aspect(), c.scale(), false);
            assertThat(video.lastWidth).as(c.name()).isEqualTo(size.width());
            assertThat(video.lastHeight).as(c.name()).isEqualTo(size.height());
            assertThat(video.distinctColours()).as(c.name()).isGreaterThan(40);
            video.writePng(SHOTS.resolve("engine-" + c.name() + ".png"));
        }
    }

    // ------------------------------------------------------------------------------------------ sync

    private static List<String> readTrace(Path file) throws Exception {
        List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                file.toString().endsWith(".gz") ? new GZIPInputStream(Files.newInputStream(file))
                        : Files.newInputStream(file), StandardCharsets.UTF_8))) {
            String l;
            while ((l = r.readLine()) != null) {
                lines.add(l);
            }
        }
        return lines;
    }

    /** A trace line without the informational misc RNG index (which depends on rendering). */
    private static String gated(String line) {
        int i = line.lastIndexOf(";m=");
        return i < 0 ? line : line.substring(0, i);
    }

    /** Plays a demo with the real renderer and interpolation on and compares the gated trace with the oracle's. */
    private void checkSync(String iwadName, String demo, String refName, int tics) throws Exception {
        Path iwad = Iwads.assumeAvailable(iwadName);
        Path ref = Path.of("..", "testdata", "traces", refName);
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(ref), "reference trace missing");
        Path out = tmp.resolve(refName.replace(".gz", "") + ".ours.gz");
        RecordingVideo video = new RecordingVideo(2560, 1080);
        Config config = Config.defaults();
        assertThat(config.get(Settings.VIDEO_INTERPOLATION)).isTrue();
        try (DoomEngine engine = engine(video, config, "-iwad", iwad.toString(), "-playdemo", demo, "-trace",
                out.toString(), "-maxtics", Integer.toString(tics))) {
            engine.run();
            assertThat(engine.ranTics()).isEqualTo(tics);
        }
        assertThat(video.presented).as("frames drawn while the demo ran").isGreaterThan(tics);
        List<String> ours = readTrace(out);
        List<String> expected = readTrace(ref);
        assertThat(ours.size()).isGreaterThan(tics - 5);
        int checked = 0;
        for (int i = 1; i < ours.size() && i < expected.size(); i++) {
            assertThat(gated(ours.get(i))).as("%s trace line %d", demo, i).isEqualTo(gated(expected.get(i)));
            checked++;
        }
        assertThat(checked).isGreaterThan(tics - 5);
    }

    @Test
    void interpolatedDoom1DemoStaysInSync() throws Exception {
        checkSync(Iwads.DOOM1, "DEMO1", "doom1-demo1.trace.gz", 2500);
    }

    @Test
    void interpolatedCoopDemoStaysInSync() throws Exception {
        checkSync("freedoom1.wad", "DEMO2", "freedoom1-demo2.trace.gz", 1800);
    }
}
