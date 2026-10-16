package doom.platform.javasound;

import doom.platform.AudioProvider;
import doom.platform.AudioSink;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/**
 * {@code javax.sound.sampled} output (ADR-012): the default audio backend, found with {@link java.util.ServiceLoader}.
 */
public final class JavaSoundAudioProvider implements AudioProvider {

    @Override
    public String name() {
        return "javasound";
    }

    @Override
    public int priority() {
        return 100;
    }

    /**
     * Whether a 16-bit stereo output line exists here: false on headless machines without any audio mixer. The check
     * does not open the device.
     */
    @Override
    public boolean isSupported() {
        try {
            if (AudioSystem.getMixerInfo().length == 0) {
                return false;
            }
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, JavaSoundAudioSink.format(48000));
            return AudioSystem.isLineSupported(info);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    @Override
    public AudioSink createAudioSink() {
        return new JavaSoundAudioSink();
    }
}
