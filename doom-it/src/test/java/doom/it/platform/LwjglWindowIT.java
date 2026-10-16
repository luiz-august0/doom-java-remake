package doom.it.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.platform.PlatformProvider;
import doom.platform.ScaleFilter;
import doom.platform.ScaleMode;
import doom.platform.VideoConfig;
import doom.platform.VideoSink;
import doom.platform.WindowMode;
import org.junit.jupiter.api.Test;

/**
 * Opens a real window through the LWJGL backend, presents a two-colour frame (row- and column-major) and checks the
 * read-back. Skipped when the backend is not usable in this JVM (no display, or macOS without -XstartOnFirstThread,
 * which Failsafe does not pass, or no OpenGL 3.3 driver as on GPU-less CI runners): {@code bin/viewer --glshot}
 * covers that case.
 */
class LwjglWindowIT {

    @Test
    void presentsIndexedFramesAndReadsThemBack() throws Exception {
        // On the class path there is no module descriptor, hence no ServiceLoader entry: instantiate directly.
        PlatformProvider provider = null;
        try {
            provider = (PlatformProvider) Class.forName("doom.platform.lwjgl.LwjglPlatformProvider")
                    .getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            // not on the class path
        }
        assumeTrue(provider != null && provider.isSupported(), "lwjgl backend not usable here");
        try (VideoSink video = provider.createVideoSink()) {
            try {
                video.open(new VideoConfig("it", WindowMode.WINDOWED, 320, 240, false, 0, ScaleMode.STRETCH,
                        ScaleFilter.NEAREST, 1.0));
            } catch (IllegalStateException e) {
                // CI runners without a GPU (e.g. Windows Server) have no OpenGL 3.3 core context
                assumeTrue(false, "no OpenGL 3.3 core context available: " + e.getMessage());
            }
            byte[] pal = new byte[768];
            pal[3] = (byte) 255; // index 1 = red
            pal[8] = (byte) 255; // index 2 = blue
            video.setPalette(pal);
            int w = 4;
            int h = 2;
            byte[] rowMajor = {1, 2, 1, 2, 2, 1, 2, 1};
            video.present(rowMajor, w, h, false);
            byte[] rgba = video.readBackOutput();
            int ow = video.outputWidth();
            int oh = video.outputHeight();
            assertThat(rgba).hasSize(ow * oh * 4);
            // top-left quarter cell is index 1 (red)
            assertThat(rgba[0] & 0xFF).isEqualTo(255);
            assertThat(rgba[2] & 0xFF).isZero();
            // column-major version of the same picture reads back identically
            byte[] colMajor = new byte[w * h];
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    colMajor[x * h + y] = rowMajor[y * w + x];
                }
            }
            video.present(colMajor, w, h, true);
            assertThat(video.readBackOutput()).isEqualTo(rgba);
            video.reconfigure(new VideoConfig("it2", WindowMode.WINDOWED, 400, 300, true, 0, ScaleMode.FIT,
                    ScaleFilter.SHARP_BILINEAR, 1.2));
            video.present(rowMajor, w, h, false);
            assertThat(video.outputWidth()).isGreaterThan(0);
        }
    }
}
