package doom.platform.lwjgl;

import doom.platform.InputSource;
import doom.platform.PlatformProvider;
import doom.platform.VideoSink;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;

/**
 * GLFW + OpenGL backend (ADR-003).
 *
 * <p>{@link #isSupported()} really probes the platform: on macOS the JVM must have been started with
 * {@code -XstartOnFirstThread} (GLFW needs the Cocoa main thread), the LWJGL natives must load, and GLFW must be able
 * to initialise (a display must be available). The probe initialises and terminates GLFW once and caches the result.
 */
public final class LwjglPlatformProvider implements PlatformProvider {

    private static final System.Logger LOG = System.getLogger("doom.platform.lwjgl");

    /** Message used when the JVM was not started with {@code -XstartOnFirstThread} on macOS. */
    static final String MAC_THREAD_MESSAGE = "On macOS the LWJGL/GLFW backend must run on the first thread: start the "
            + "JVM with -XstartOnFirstThread (bin/viewer and bin/doom-java do this).";

    private Boolean supported;

    @Override
    public String name() {
        return "lwjgl";
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public synchronized boolean isSupported() {
        if (supported == null) {
            supported = probe();
        }
        return supported;
    }

    private static boolean probe() {
        if (isMac() && !startedOnFirstThread()) {
            LOG.log(System.Logger.Level.WARNING, MAC_THREAD_MESSAGE);
            return false;
        }
        try {
            GLFWErrorCallback previous = GLFW.glfwSetErrorCallback(null);
            boolean ok = GLFW.glfwInit();
            if (ok) {
                GLFW.glfwTerminate();
            }
            if (previous != null) {
                GLFW.glfwSetErrorCallback(previous);
            }
            if (!ok) {
                LOG.log(System.Logger.Level.INFO, "GLFW could not be initialised (no display available?)");
            }
            return ok;
        } catch (Throwable t) {
            LOG.log(System.Logger.Level.INFO, "LWJGL backend not available: " + t);
            return false;
        }
    }

    @Override
    public VideoSink createVideoSink() {
        requireMainThreadOnMac();
        return new LwjglVideoSink();
    }

    @Override
    public InputSource createInputSource(VideoSink video) {
        if (!(video instanceof LwjglVideoSink sink)) {
            throw new IllegalArgumentException("the video sink was not created by the lwjgl provider: " + video);
        }
        return new LwjglInputSource(sink);
    }

    static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
    }

    /** True if the JVM was launched with {@code -XstartOnFirstThread} (the JVM exports a marker variable). */
    static boolean startedOnFirstThread() {
        return System.getenv("JAVA_STARTED_ON_FIRST_THREAD_" + ProcessHandle.current().pid()) != null;
    }

    /** Throws a clear error when GLFW cannot work on this thread (macOS without -XstartOnFirstThread). */
    static void requireMainThreadOnMac() {
        if (isMac() && !startedOnFirstThread()) {
            throw new IllegalStateException(MAC_THREAD_MESSAGE);
        }
    }
}
