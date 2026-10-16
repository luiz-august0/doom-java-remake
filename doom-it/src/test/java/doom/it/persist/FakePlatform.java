package doom.it.persist;

import doom.engine.EngineParams;
import doom.platform.Clock;
import doom.platform.InputSource;
import doom.platform.VideoConfig;
import doom.platform.VideoSink;
import doom.platform.input.InputEventQueue;
import doom.render.image.PngWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A window that is only a recording of what was presented, and a clock that runs as fast as it is asked to. */
final class FakePlatform {

    /** Clock: every call advances 3 milliseconds; waiting jumps to the deadline. */
    static final class ManualClock implements Clock {
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

    /** Records frames. */
    static final class RecordingVideo implements VideoSink {
        final int outW;
        final int outH;
        byte[] palette = new byte[768];
        byte[] lastPixels;
        int lastWidth;
        int lastHeight;
        boolean lastColumnMajor;
        int presented;
        final List<byte[]> palettesSeen = new ArrayList<>();

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
            lastPixels = java.util.Arrays.copyOf(indexed, width * height);
            lastWidth = width;
            lastHeight = height;
            lastColumnMajor = columnMajor;
            presented++;
        }

        @Override
        public boolean closeRequested() {
            return false;
        }

        @Override
        public void close() {
        }

        void writePng(Path file) throws IOException {
            java.nio.file.Files.createDirectories(file.toAbsolutePath().getParent());
            PngWriter.writeIndexed(file, lastPixels, lastWidth, lastHeight, lastColumnMajor, palette);
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
    }

    /** Input that delivers events scripted per poll. */
    static final class ScriptedInput implements InputSource {
        private final Consumer<InputEventQueue>[] script;
        private int poll;

        @SafeVarargs
        ScriptedInput(Consumer<InputEventQueue>... script) {
            this.script = script;
        }

        @Override
        public void poll(InputEventQueue queue) {
            if (poll < script.length && script[poll] != null) {
                script[poll].accept(queue);
            }
            poll++;
        }

        @Override
        public void setMouseCaptured(boolean captured) {
        }
    }

    private FakePlatform() {
    }

    static EngineParams.Platform platform(RecordingVideo video, InputSource input) {
        return new EngineParams.Platform(video, input, null);
    }
}
