package doom.it.render;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.engine.DoomEngine;
import doom.engine.EngineParams;
import doom.engine.cli.LaunchOptions;
import doom.engine.config.Config;
import doom.engine.config.DataDirs;
import doom.engine.config.Settings;
import doom.platform.InputSource;
import doom.platform.VideoConfig;
import doom.platform.VideoSink;
import doom.platform.input.InputEventQueue;
import doom.testkit.Iwads;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code -timedemo} benchmark of the whole engine frame (simulation, 3D renderer, UI, palette) at several internal
 * resolutions, without a window (spec 04 section 9, {@code docs/notes/performance.md}). Skipped unless
 * {@code -Dbench.timedemo=true}; run with {@code -Dit.test=TimedemoBenchmark}.
 */
class TimedemoBenchmark {

    @TempDir
    Path tmp;

    private static final class NullVideo implements VideoSink {
        private final int outW;
        private final int outH;

        NullVideo(int outW, int outH) {
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
        }

        @Override
        public void present(byte[] indexed, int width, int height, boolean columnMajor) {
        }

        @Override
        public boolean closeRequested() {
            return false;
        }

        @Override
        public void close() {
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

    private double run(String iwadName, String demo, int outW, int outH, boolean vanilla, boolean precise)
            throws Exception {
        return run(iwadName, demo, outW, outH, vanilla, precise, false);
    }

    private double run(String iwadName, String demo, int outW, int outH, boolean vanilla, boolean precise,
                       boolean headless) throws Exception {
        Path iwad = Iwads.assumeAvailable(iwadName);
        Config config = Config.defaults();
        config.set(Settings.RENDER_PRECISION_FIXES, precise);
        config.set(Settings.RENDER_VANILLA_MODE, vanilla);
        config.set(Settings.VIDEO_VSYNC, false);
        LaunchOptions options = headless
                ? LaunchOptions.parse("-headless", "-iwad", iwad.toString(), "-timedemo", demo, "-nosound")
                : LaunchOptions.parse("-iwad", iwad.toString(), "-timedemo", demo, "-nosound");
        EngineParams params = new EngineParams(options).config(config)
                .dirs(DataDirs.resolve("Linux", Map.of("XDG_CONFIG_HOME", tmp.toString()), tmp.toString()))
                .out(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()))
                .platform(new EngineParams.Platform(new NullVideo(outW, outH), new NoInput(), null));
        try (DoomEngine engine = new DoomEngine(params)) {
            engine.run();
            return engine.timedemoResult().framesPerSecond();
        }
    }

    @Test
    void simulationOnly() throws Exception {
        assumeTrue(Boolean.getBoolean("bench.timedemo"), "set -Dbench.timedemo=true");
        for (String[] d : new String[][] {{"freedoom2.wad", "DEMO1"}, {"freedoom2.wad", "DEMO3"}, {"doom1.wad", "DEMO1"}}) {
            run(d[0], d[1], 320, 200, true, true, true);
            System.out.printf("BENCH sim-only %s %s: %.0f tics/s%n", d[0], d[1], run(d[0], d[1], 320, 200, true, true, true));
        }
    }

    @Test
    void timedemo() throws Exception {
        assumeTrue(Boolean.getBoolean("bench.timedemo"), "set -Dbench.timedemo=true");
        int[][] sizes = Boolean.getBoolean("bench.small") ? new int[][] {{320, 200}} : new int[][] {{320, 200}, {2560, 1080}, {3840, 2160}};
        String[][] demos = {{"freedoom2.wad", "DEMO1"}, {"freedoom2.wad", "DEMO3"}, {"doom1.wad", "DEMO1"}};
        for (String[] d : demos) {
            for (int[] s : sizes) {
                boolean vanilla = s[0] == 320;
                // one warm-up pass so that the JIT is hot, then the measured one
                run(d[0], d[1], s[0], s[1], vanilla, true);
                double fps = run(d[0], d[1], s[0], s[1], vanilla, true);
                double plain = vanilla ? fps : run(d[0], d[1], s[0], s[1], false, false);
                System.out.printf("BENCH %s %s output %dx%d%s: %.0f fps (precisionFixes off: %.0f fps)%n", d[0], d[1],
                        s[0], s[1], vanilla ? " (vanilla 320x200)" : "", fps, plain);
            }
        }
    }
}
