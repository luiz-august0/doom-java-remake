package doom.platform;

/**
 * Blocking PCM output. Called only from the dedicated audio thread (ADR-020); {@link #write} blocks while the device
 * buffer is full, which paces the mixer loop.
 */
public interface AudioSink extends AutoCloseable {

    /** Opens the device. */
    void open(AudioConfig config);

    /** Actual sample rate of the opened device. */
    int sampleRate();

    /**
     * Queues {@code frames} frames of interleaved signed 16-bit samples, blocking until there is room.
     *
     * @param interleaved samples, {@code frames * channels} values
     * @param frames      number of frames to write
     */
    void write(short[] interleaved, int frames);

    /** Number of buffer underruns observed so far (shown in the debug overlay). */
    long underruns();

    /** Approximate output latency of the device buffer, in nanoseconds. */
    long latencyNanos();

    @Override
    void close();
}
