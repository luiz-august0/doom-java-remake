package doom.platform;

/**
 * Service interface of a window/input backend, found with {@link java.util.ServiceLoader}. The provider with the
 * highest {@link #priority()} among the supported ones is used, unless the user picks one by {@link #name()}.
 */
public interface PlatformProvider {

    /** Short identifier, e.g. {@code "lwjgl"}. */
    String name();

    /** Higher wins when several backends are available. */
    int priority();

    /** Whether this backend can run here (native libraries present, display available...). */
    boolean isSupported();

    /** Creates the video sink (the window). */
    VideoSink createVideoSink();

    /** Creates the input source bound to the window of {@code video} (created by this provider). */
    InputSource createInputSource(VideoSink video);
}
