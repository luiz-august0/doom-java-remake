package doom.sound;

import static org.assertj.core.api.Assertions.assertThat;

import doom.sound.mixer.AudioEngine;
import doom.sound.mixer.AudioEngineConfig;
import doom.sound.mixer.SfxSample;
import org.junit.jupiter.api.Test;

class AudioEngineBackendTest {

    private static final int BLOCK = 256;

    private final AudioEngine engine = new AudioEngine(AudioEngineConfig.defaults().withBlockFrames(BLOCK), null);
    private final AudioEngineBackend backend = new AudioEngineBackend(engine, BLOCK);
    private final short[] out = new short[BLOCK * 2];

    private void render(int blocks) {
        for (int i = 0; i < blocks; i++) {
            engine.renderOffline(out, 0, BLOCK);
        }
    }

    @Test
    void aSoundStartedJustNowCountsAsPlayingBeforeTheMixerSawIt() {
        SfxSample s = SfxSample.ofPcm(new short[4000], 11025); // about 17000 output frames
        backend.startSfx(0, s, 100, 128, 128);
        assertThat(engine.isChannelPlaying(0)).isFalse(); // not mixed yet
        assertThat(backend.isPlaying(0)).isTrue(); // the grace period covers it
        render(1);
        assertThat(backend.isPlaying(0)).isTrue();
        render(3);
        assertThat(backend.isPlaying(0)).isTrue();
    }

    @Test
    void aFinishedSoundStopsPlayingAfterTheGracePeriod() {
        SfxSample s = SfxSample.ofPcm(new short[40], 11025); // a few hundred output frames
        backend.startSfx(1, s, 100, 128, 128);
        render(1);
        render(10);
        assertThat(backend.isPlaying(1)).isFalse();
    }

    @Test
    void stopChannelEndsTheSoundAtTheNextBlock() {
        SfxSample s = SfxSample.ofPcm(new short[20000], 11025);
        backend.startSfx(2, s, 100, 128, 128);
        render(3);
        assertThat(backend.isPlaying(2)).isTrue();
        backend.stopChannel(2);
        render(2);
        assertThat(backend.isPlaying(2)).isFalse();
        assertThat(engine.isChannelPlaying(2)).isFalse();
    }

    @Test
    void aNullSampleIsNeverPlaying() {
        backend.startSfx(3, null, 100, 128, 128);
        assertThat(backend.isPlaying(3)).isFalse();
    }
}
