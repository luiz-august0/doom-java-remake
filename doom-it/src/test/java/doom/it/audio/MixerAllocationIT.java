package doom.it.audio;

import static org.assertj.core.api.Assertions.assertThat;

import doom.sound.mixer.AudioEngine;
import doom.sound.mixer.AudioEngineConfig;
import doom.sound.mixer.SfxSample;
import doom.wad.audio.GenMidi;
import doom.wad.audio.MusicScore;
import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.Test;

/** The audio loop must not allocate in steady state (contracts §3, hot paths). */
class MixerAllocationIT {

    private static GenMidi genMidi() {
        byte[] lump = new byte[8 + 175 * 36 + 175 * 32];
        byte[] magic = "#OPL_II#".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, lump, 0, 8);
        for (int i = 0; i < 175; i++) {
            int at = 8 + i * 36 + 4;
            lump[at] = 0x21;
            lump[at + 1] = (byte) 0xF0;
            lump[at + 2] = (byte) 0x7F;
            lump[at + 5] = 0x18;
            lump[at + 6] = 0x06;
            lump[at + 7] = 0x21;
            lump[at + 8] = (byte) 0xF0;
            lump[at + 9] = (byte) 0x7F;
        }
        return GenMidi.parse(lump);
    }

    @Test
    void steadyStateMixingDoesNotAllocate() {
        com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        AudioEngine engine = new AudioEngine(AudioEngineConfig.defaults(), genMidi());

        // A song with many notes, looping, plus sound effects restarted all the time.
        int notes = 400;
        int[] ticks = new int[notes * 2 + 1];
        int[] types = new int[notes * 2 + 1];
        int[] channels = new int[notes * 2 + 1];
        int[] p1 = new int[notes * 2 + 1];
        int[] p2 = new int[notes * 2 + 1];
        for (int i = 0; i < notes; i++) {
            ticks[i * 2] = i * 7;
            types[i * 2] = 0x90;
            channels[i * 2] = i % 6;
            p1[i * 2] = 40 + (i * 5) % 40;
            p2[i * 2] = 100;
            ticks[i * 2 + 1] = i * 7 + 20;
            types[i * 2 + 1] = 0x80;
            channels[i * 2 + 1] = i % 6;
            p1[i * 2 + 1] = 40 + (i * 5) % 40;
        }
        ticks[notes * 2] = notes * 7 + 40;
        types[notes * 2] = MusicScore.TYPE_END;
        MusicScore score = MusicScore.of(70, 500_000, ticks, types, channels, p1, p2);

        short[] pcm = new short[4000];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) ((i * 53) % 20000 - 10000);
        }
        SfxSample sample = SfxSample.ofPcm(pcm, 11025);
        short[] out = new short[512 * 2 * 8];

        engine.commands().playMusic(score, true);
        for (int i = 0; i < 100; i++) { // warm-up: JIT, lazy class loading
            engine.commands().startSfx(i % 8, sample, 100, 128, 128);
            engine.renderOffline(out, 0, 512);
        }

        long id = Thread.currentThread().threadId();
        long before = mx.getThreadAllocatedBytes(id);
        for (int i = 0; i < 400; i++) {
            engine.commands().startSfx(i % 8, sample, 90, (i * 31) % 255, 128);
            engine.commands().updateChannel((i + 3) % 8, 80, 100);
            engine.renderOffline(out, 0, 512);
        }
        long allocated = mx.getThreadAllocatedBytes(id) - before;
        assertThat(allocated).as("bytes allocated by 400 mixed blocks").isLessThan(2048L);
    }
}
