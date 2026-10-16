package doom.platform.javasound;

import doom.platform.AudioConfig;
import doom.platform.AudioSink;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

/**
 * {@link AudioSink} on a {@code javax.sound.sampled.SourceDataLine}: 16-bit signed little-endian stereo PCM.
 *
 * <p>{@link #write} blocks while the line buffer is full, which paces the audio thread. An underrun is counted when
 * the line is found completely empty at the start of a write after playback has begun (the device ran dry); the audio
 * thread then simply writes the next block, so an underrun is heard as a short gap, never as a stall.
 *
 * <p>Allocation: the byte conversion buffer grows on the first write of a larger block, then is reused.
 */
public final class JavaSoundAudioSink implements AudioSink {

    private static final System.Logger LOG = System.getLogger(JavaSoundAudioSink.class.getName());
    private static final int CHANNELS = 2;
    private static final int FRAME_BYTES = CHANNELS * 2;

    private SourceDataLine line;
    private int sampleRate;
    private int lineBufferFrames;
    private byte[] bytes = new byte[0];
    private volatile long underruns;
    private boolean started;
    private String deviceName = "default";

    /** The PCM format used for a given rate. */
    static AudioFormat format(int sampleRate) {
        return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16, CHANNELS, FRAME_BYTES, sampleRate, false);
    }

    @Override
    public void open(AudioConfig config) {
        if (line != null) {
            throw new IllegalStateException("already open");
        }
        if (config.channels() != CHANNELS) {
            throw new IllegalArgumentException("only stereo output is supported: " + config.channels());
        }
        AudioFormat format = format(config.sampleRate());
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        try {
            SourceDataLine opened = null;
            String wanted = config.deviceName();
            if (wanted != null && !wanted.isBlank()) {
                for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                    if (mi.getName().toLowerCase(java.util.Locale.ROOT).contains(wanted.toLowerCase(java.util.Locale.ROOT))) {
                        Mixer mixer = AudioSystem.getMixer(mi);
                        if (mixer.isLineSupported(info)) {
                            opened = (SourceDataLine) mixer.getLine(info);
                            deviceName = mi.getName();
                            break;
                        }
                    }
                }
                if (opened == null) {
                    LOG.log(System.Logger.Level.WARNING, "audio device ''{0}'' not found, using the default", wanted);
                }
            }
            if (opened == null) {
                opened = AudioSystem.getSourceDataLine(format);
            }
            int bufferFrames = Math.max(config.bufferFrames(), 128);
            opened.open(format, bufferFrames * FRAME_BYTES);
            line = opened;
        } catch (LineUnavailableException | IllegalArgumentException e) {
            throw new IllegalStateException("cannot open audio output at " + config.sampleRate() + " Hz: " + e, e);
        }
        sampleRate = (int) line.getFormat().getSampleRate();
        lineBufferFrames = line.getBufferSize() / FRAME_BYTES;
        underruns = 0;
        started = false;
        LOG.log(System.Logger.Level.INFO, "audio output ''{0}'' {1} Hz, buffer {2} frames ({3} ms)", deviceName,
                sampleRate, lineBufferFrames, lineBufferFrames * 1000 / sampleRate);
    }

    @Override
    public int sampleRate() {
        return sampleRate;
    }

    @Override
    public void write(short[] interleaved, int frames) {
        SourceDataLine l = line;
        if (l == null) {
            return;
        }
        int needed = frames * FRAME_BYTES;
        if (bytes.length < needed) {
            bytes = new byte[needed];
        }
        int n = frames * CHANNELS;
        for (int i = 0, o = 0; i < n; i++) {
            short s = interleaved[i];
            bytes[o++] = (byte) s;
            bytes[o++] = (byte) (s >> 8);
        }
        int written = 0;
        if (!started) {
            // Prime the line before starting it, so playback does not begin with an underrun. Never write more than
            // fits: an unstarted line would block forever.
            int first = Math.min(needed, l.available());
            written = l.write(bytes, 0, first);
            l.start();
            started = true;
        } else if (l.available() >= l.getBufferSize()) {
            underruns++;
        }
        while (written < needed) {
            int w = l.write(bytes, written, needed - written);
            if (w <= 0) {
                break; // line closed
            }
            written += w;
        }
    }

    @Override
    public long underruns() {
        return underruns;
    }

    @Override
    public long latencyNanos() {
        return sampleRate == 0 ? 0 : lineBufferFrames * 1_000_000_000L / sampleRate;
    }

    /** Frames the device has consumed so far (for diagnostics and the latency spike). */
    public long framePosition() {
        SourceDataLine l = line;
        return l == null ? 0 : l.getLongFramePosition();
    }

    /** Granted size of the device buffer in frames. */
    public int bufferFrames() {
        return lineBufferFrames;
    }

    /** Frames currently queued in the device buffer. */
    public int queuedFrames() {
        SourceDataLine l = line;
        return l == null ? 0 : (l.getBufferSize() - l.available()) / FRAME_BYTES;
    }

    /** Name of the Java Sound mixer in use. */
    public String deviceName() {
        return deviceName;
    }

    @Override
    public void close() {
        SourceDataLine l = line;
        line = null;
        if (l != null) {
            l.stop();
            l.flush();
            l.close();
        }
    }
}
