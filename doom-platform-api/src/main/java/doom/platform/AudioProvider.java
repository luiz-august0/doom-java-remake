package doom.platform;

/** Service interface of an audio output backend, found with {@link java.util.ServiceLoader}. */
public interface AudioProvider {

    /** Short identifier, e.g. {@code "javasound"}. */
    String name();

    /** Higher wins when several backends are available. */
    int priority();

    /** Whether this backend can run here. */
    boolean isSupported();

    /** Creates an unopened sink. */
    AudioSink createAudioSink();
}
