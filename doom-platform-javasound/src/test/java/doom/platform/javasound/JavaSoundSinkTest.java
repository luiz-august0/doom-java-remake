package doom.platform.javasound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.platform.AudioConfig;
import doom.platform.AudioSink;
import org.junit.jupiter.api.Test;

class JavaSoundSinkTest {

    @Test
    void providerIsDiscoverableAndReportsSupportWithoutOpeningTheDevice() {
        // The module descriptor declares the service; the engine finds it through ServiceLoader.
        var descriptor = JavaSoundAudioProvider.class.getModule().getDescriptor();
        assertThat(descriptor).isNotNull();
        assertThat(descriptor.provides()).anyMatch(p -> p.service().equals("doom.platform.AudioProvider")
                && p.providers().contains(JavaSoundAudioProvider.class.getName()));
        JavaSoundAudioProvider provider = new JavaSoundAudioProvider();
        assertThat(provider.priority()).isEqualTo(100);
        // Must not throw on a headless machine; the value depends on the machine.
        provider.isSupported();
    }

    @Test
    void silenceCanBeWrittenToTheRealLine() {
        JavaSoundAudioProvider provider = new JavaSoundAudioProvider();
        assumeTrue(provider.isSupported(), "no audio output on this machine");
        AudioSink sink = provider.createAudioSink();
        try {
            sink.open(new AudioConfig(48000, 2, 2048, null));
        } catch (IllegalStateException e) {
            assumeTrue(false, "audio line cannot be opened here: " + e.getMessage());
        }
        try {
            assertThat(sink.sampleRate()).isEqualTo(48000);
            short[] silence = new short[512 * 2];
            for (int i = 0; i < 20; i++) {
                sink.write(silence, 512);
            }
            assertThat(sink.latencyNanos()).isGreaterThan(0);
        } finally {
            sink.close();
        }
    }
}
