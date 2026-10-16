package doom.sound.mixer;

import static org.assertj.core.api.Assertions.assertThat;

import doom.wad.audio.GenMidi;
import doom.wad.audio.MusicScore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class AudioEngineTest {

    /** A GENMIDI lump where every instrument is the same sustained FM voice. */
    static GenMidi syntheticGenMidi() {
        byte[] lump = new byte[8 + 175 * 36 + 175 * 32];
        byte[] magic = "#OPL_II#".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, lump, 0, 8);
        for (int i = 0; i < 175; i++) {
            int at = 8 + i * 36 + 4;
            // modulator: tremolo/mult 1 with sustain, attack, sustain, waveform, scale, level
            lump[at] = 0x21;
            lump[at + 1] = (byte) 0xF0;
            lump[at + 2] = (byte) 0x0F;
            lump[at + 3] = 0;
            lump[at + 4] = 0;
            lump[at + 5] = 0x18;
            lump[at + 6] = 0x06; // feedback 3, FM
            // carrier
            lump[at + 7] = 0x21;
            lump[at + 8] = (byte) 0xF0;
            lump[at + 9] = (byte) 0x0F;
            lump[at + 10] = 0;
            lump[at + 11] = 0;
            lump[at + 12] = 0;
        }
        return GenMidi.parse(lump);
    }

    static SfxSample constant(int value, int length, int rate) {
        short[] pcm = new short[length];
        java.util.Arrays.fill(pcm, (short) value);
        return SfxSample.ofPcm(pcm, rate);
    }

    private static AudioEngine engine() {
        return new AudioEngine(AudioEngineConfig.defaults().withBlockFrames(256), null);
    }

    @Test
    void centredSoundUsesVanillaPanLaw() {
        AudioEngine e = engine();
        e.commands().startSfx(0, constant(16384, 11025, 11025), 127, 127, 128);
        short[] out = new short[512 * 2];
        e.renderOffline(out, 0, 512);
        // left = (127 * 127) / 127 = 127, right = same; (16384 * 127) >> 8 = 8128
        assertThat(out[100]).isEqualTo((short) 8128);
        assertThat(out[101]).isEqualTo((short) 8128);
        assertThat(e.isChannelPlaying(0)).isTrue();
    }

    @Test
    void hardPanSendsSoundToOneEar() {
        AudioEngine e = engine();
        e.commands().startSfx(0, constant(10000, 11025, 11025), 127, 0, 128);
        e.commands().startSfx(1, constant(10000, 11025, 11025), 127, 254, 128);
        short[] out = new short[256 * 2];
        e.renderOffline(out, 0, 256);
        // channel 0: left 254, right 0; channel 1: left 0, right 254 -> both ears get (10000*254)>>8 = 9921
        assertThat(out[10]).isEqualTo((short) 9921);
        assertThat(out[11]).isEqualTo((short) 9921);
    }

    @Test
    void volumeScalesAndSfxMasterAppliesOnTop() {
        AudioEngine e = engine();
        e.commands().setVolumes(0, 8);
        e.commands().startSfx(0, constant(16384, 11025, 11025), 127, 127, 128);
        short[] out = new short[256 * 2];
        e.renderOffline(out, 0, 256);
        assertThat(out[50]).isZero();
        e.commands().setVolumes(15, 8);
        e.renderOffline(out, 0, 256);
        assertThat(out[50]).isEqualTo((short) 8128);
        e.commands().updateChannel(0, 64, 127);
        e.renderOffline(out, 0, 256);
        assertThat(out[50]).isEqualTo((short) ((16384 * 64) >> 8));
    }

    @Test
    void soundEndsAfterItsDurationAndFreesTheChannel() {
        AudioEngine e = engine();
        SfxSample s = constant(8000, 1102, 11025); // 0.1 s -> 4800 output frames
        e.commands().startSfx(2, s, 100, 127, 128);
        short[] out = new short[256 * 2];
        e.renderOffline(out, 0, 256);
        assertThat(e.isChannelPlaying(2)).isTrue();
        int blocks = 1;
        while (e.isChannelPlaying(2) && blocks < 100) {
            e.renderOffline(out, 0, 256);
            blocks++;
        }
        // 1102 samples * 48000 / 11025 = 4797.9 frames -> 19 blocks of 256
        assertThat(blocks).isBetween(18, 20);
        assertThat(e.isChannelPlaying(2)).isFalse();
    }

    @Test
    void stopChannelAndReplacement() {
        AudioEngine e = engine();
        e.commands().startSfx(0, constant(1000, 11025, 11025), 127, 127, 128);
        short[] out = new short[256 * 2];
        e.renderOffline(out, 0, 256);
        e.commands().startSfx(0, constant(2000, 11025, 11025), 127, 127, 128);
        e.renderOffline(out, 0, 256);
        assertThat(out[10]).isEqualTo((short) ((2000 * 127) >> 8));
        e.commands().stopChannel(0);
        e.renderOffline(out, 0, 256);
        assertThat(e.isChannelPlaying(0)).isFalse();
        assertThat(out[10]).isZero();
    }

    @Test
    void linearAndNearestResamplingDiffer() {
        short[] ramp = new short[200];
        for (int i = 0; i < ramp.length; i++) {
            ramp[i] = (short) (i * 100);
        }
        SfxSample s = SfxSample.ofPcm(ramp, 11025);
        short[] a = new short[256 * 2];
        short[] b = new short[256 * 2];
        AudioEngine lin = new AudioEngine(AudioEngineConfig.defaults().withBlockFrames(256), null);
        lin.commands().startSfx(0, s, 127, 127, 128);
        lin.renderOffline(a, 0, 256);
        AudioEngine near = new AudioEngine(AudioEngineConfig.defaults().withBlockFrames(256).withLinearInterpolation(false), null);
        near.commands().startSfx(0, s, 127, 127, 128);
        near.renderOffline(b, 0, 256);
        // Nearest repeats each source sample ~4.35 times (steps); linear is a smooth ramp.
        int stepsNearest = 0;
        int stepsLinear = 0;
        for (int i = 1; i < 200; i++) {
            if (b[i * 2] != b[(i - 1) * 2]) {
                stepsNearest++;
            }
            if (a[i * 2] != a[(i - 1) * 2]) {
                stepsLinear++;
            }
        }
        assertThat(stepsLinear).isGreaterThan(stepsNearest * 2);
    }

    @Test
    void manyVoicesStayWithinRangeThroughTheLimiter() {
        AudioEngine e = new AudioEngine(AudioEngineConfig.defaults().withSfxChannels(32).withBlockFrames(256), null);
        for (int c = 0; c < 32; c++) {
            e.commands().startSfx(c, constant(32000, 11025, 11025), 127, 0, 128);
        }
        short[] out = new short[256 * 2];
        e.renderOffline(out, 0, 256);
        assertThat(out[20]).isBetween((short) 30000, (short) 32767);
    }

    @Test
    void softLimiterIsContinuousMonotonicAndBounded() {
        int previous = Integer.MIN_VALUE;
        for (int x = -3_000_000; x <= 3_000_000; x += 7) {
            int y = SoftLimiter.apply(x);
            assertThat(y).isGreaterThanOrEqualTo(previous);
            assertThat(y).isBetween(-32767, 32767);
            previous = y;
        }
        for (int x = -SoftLimiter.THRESHOLD; x <= SoftLimiter.THRESHOLD; x += 13) {
            assertThat(SoftLimiter.apply(x)).isEqualTo((short) x);
        }
        assertThat((int) SoftLimiter.apply(SoftLimiter.THRESHOLD + 1)).isBetween(SoftLimiter.THRESHOLD, SoftLimiter.THRESHOLD + 1);
        assertThat(SoftLimiter.apply(-40000)).isEqualTo((short) -SoftLimiter.apply(40000));
    }

    @Test
    void musicPlaysThroughTheEngineAndStops() {
        AudioEngine e = new AudioEngine(AudioEngineConfig.defaults().withBlockFrames(256), syntheticGenMidi());
        // note on C4 after 0 ticks, off after 140 ticks (one second), end 70 ticks later
        MusicScore score = MusicScore.of(70, 500_000, new int[] {0, 140, 210},
                new int[] {0x90, 0x80, MusicScore.TYPE_END}, new int[] {0, 0, 0}, new int[] {60, 60, 0},
                new int[] {100, 0, 0});
        e.commands().setVolumes(15, 15);
        e.commands().playMusic(score, false);
        short[] out = new short[48000 * 2];
        e.renderOffline(out, 0, 48000);
        long energy = 0;
        for (short s : out) {
            energy += Math.abs(s);
        }
        assertThat(energy).as("music is audible").isGreaterThan(1_000_000L);
        assertThat(e.isMusicPlaying()).isTrue();
        e.renderOffline(out, 0, 48000);
        assertThat(e.isMusicPlaying()).as("finished after 1.5 s").isFalse();
        e.commands().playMusic(score, true);
        e.renderOffline(out, 0, 48000 * 2 / 2);
        e.commands().pauseMusic();
        e.renderOffline(out, 0, 256);
        assertThat(e.isMusicPaused()).isTrue();
        e.commands().resumeMusic();
        e.commands().stopMusic();
        e.renderOffline(out, 0, 256);
        assertThat(e.isMusicPlaying()).isFalse();
    }

    @Test
    void commandQueueIsOrderedAcrossThreads() throws Exception {
        AudioCommandQueue q = new AudioCommandQueue(64);
        int total = 200_000;
        AtomicBoolean failed = new AtomicBoolean();
        Thread consumer = new Thread(() -> {
            int expected = 0;
            while (expected < total) {
                AudioCommandQueue.Command c = q.poll();
                if (c == null) {
                    Thread.onSpinWait();
                    continue;
                }
                if (c.vol != expected || c.kind != AudioCommandQueue.UPDATE_CHANNEL) {
                    failed.set(true);
                    return;
                }
                q.consumed();
                expected++;
            }
        });
        consumer.start();
        for (int i = 0; i < total; i++) {
            while (!q.updateChannel(0, i, 0)) {
                Thread.onSpinWait();
            }
        }
        consumer.join(20_000);
        assertThat(consumer.isAlive()).isFalse();
        assertThat(failed.get()).isFalse();
        assertThat(q.pending()).isZero();
    }

    @Test
    void fullQueueDropsInsteadOfBlocking() {
        AudioCommandQueue q = new AudioCommandQueue(4);
        assertThat(q.stopChannel(0)).isTrue();
        assertThat(q.stopChannel(0)).isTrue();
        assertThat(q.stopChannel(0)).isTrue();
        assertThat(q.stopChannel(0)).isTrue();
        assertThat(q.stopChannel(0)).isFalse();
        assertThat(q.pending()).isEqualTo(4);
    }
}
