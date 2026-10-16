package doom.it.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.sound.mixer.AudioEngine;
import doom.sound.mixer.AudioEngineConfig;
import doom.sound.mixer.SfxSample;
import doom.testkit.Iwads;
import doom.wad.LumpDirectory;
import doom.wad.audio.DmxSound;
import doom.wad.audio.GenMidi;
import doom.wad.audio.MusicScore;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Format and rendering checks against real IWAD content: every GENMIDI lump parses, every sound effect lump decodes,
 * every music lump converts, and the engine renders audible WAV files (written under {@code target/audio/}).
 */
class AudioRenderIT {

    private static Path outputDir() throws IOException {
        Path dir = MusicRefTool.repoRoot().resolve("target/audio");
        Files.createDirectories(dir);
        return dir;
    }

    static void writeWav(Path path, short[] interleaved, int rate) throws IOException {
        int dataBytes = interleaved.length * 2;
        ByteBuffer b = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + dataBytes).put("WAVE".getBytes());
        b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 2).putInt(rate).putInt(rate * 4)
                .putShort((short) 4).putShort((short) 16);
        b.put("data".getBytes()).putInt(dataBytes);
        for (short s : interleaved) {
            b.putShort(s);
        }
        Files.write(path, b.array());
    }

    static double rms(short[] pcm, int from, int to) {
        double sum = 0;
        for (int i = from; i < to; i++) {
            sum += (double) pcm[i] * pcm[i];
        }
        return Math.sqrt(sum / Math.max(1, to - from));
    }

    @Test
    void genMidiSoundsAndMusicDecodeInEveryIwad() throws Exception {
        assumeTrue(Iwads.directory().isPresent(), "DOOM_IWAD_DIR not set");
        int wads = 0;
        for (String name : Iwads.ALL) {
            Path wad = Iwads.find(name).orElse(null);
            if (wad == null) {
                continue;
            }
            wads++;
            LumpDirectory lumps = LumpDirectory.open(List.of(wad));
            GenMidi genMidi = GenMidi.parse(lumps.data(lumps.get("GENMIDI")));
            assertThat(genMidi.hasValidHeader()).as(name).isTrue();
            assertThat(genMidi.size()).isEqualTo(175);
            int sounds = 0;
            int songs = 0;
            for (int i = 0; i < lumps.size(); i++) {
                String lump = lumps.name(i);
                if (lump.startsWith("DS") && lumps.length(i) > 8) {
                    DmxSound s = DmxSound.tryParse(lumps.data(i));
                    if (s != null) {
                        sounds++;
                        assertThat(s.sampleRate()).as(name + " " + lump).isBetween(4000, 48000);
                    }
                } else if (lump.startsWith("D_") && lumps.length(i) > 0) {
                    MusicScore score = MusicScore.fromLump(lumps.data(i), lump);
                    assertThat(score.size()).as(name + " " + lump).isGreaterThan(2);
                    assertThat(score.nominalSeconds()).as(name + " " + lump).isGreaterThan(1.0);
                    songs++;
                }
            }
            assertThat(sounds).as(name + " sound effects").isGreaterThan(50);
            assertThat(songs).as(name + " songs").isGreaterThan(5);
        }
        assertThat(wads).isGreaterThan(0);
    }

    private static void renderSong(String wadName, String lumpName, String outName, double seconds) throws Exception {
        Path wad = Iwads.find(wadName).orElse(null);
        assumeTrue(wad != null, wadName + " not available");
        LumpDirectory lumps = LumpDirectory.open(List.of(wad));
        GenMidi genMidi = GenMidi.parse(lumps.data(lumps.get("GENMIDI")));
        AudioEngine engine = new AudioEngine(AudioEngineConfig.defaults(), genMidi);
        MusicScore score = MusicScore.fromLump(lumps.data(lumps.get(lumpName)), lumpName);

        String[] sfxNames = {"DSPISTOL", "DSSHOTGN", "DSBAREXP", "DSDMPAIN", "DSPOSIT1", "DSTELEPT"};
        SfxSample[] sfx = new SfxSample[sfxNames.length];
        for (int i = 0; i < sfx.length; i++) {
            int l = lumps.find(sfxNames[i]);
            if (l >= 0) {
                sfx[i] = SfxSample.of(DmxSound.parse(lumps.data(l), sfxNames[i]));
            }
        }

        int rate = engine.sampleRate();
        int frames = (int) (seconds * rate);
        short[] out = new short[frames * 2];
        engine.commands().setVolumes(15, 8);
        engine.commands().playMusic(score, true);
        for (int t = 0; t < frames; ) {
            int n = Math.min(frames - t, rate / 4); // a quarter second per step: sfx start between steps
            int step = t / (rate / 4);
            if (step % 4 == 1 && sfx[(step / 4) % sfx.length] != null) {
                // spread the sounds from left to right
                engine.commands().startSfx(step % 8, sfx[(step / 4) % sfx.length], 100, 20 + (step * 37) % 200, 128);
            }
            engine.renderOffline(out, t, n);
            t += n;
        }

        Path wav = outputDir().resolve(outName);
        writeWav(wav, out, rate);
        double loud = rms(out, rate * 2, out.length);
        assertThat(loud).as(outName + " RMS").isGreaterThan(300.0);
        int peak = 0;
        for (short s : out) {
            peak = Math.max(peak, Math.abs(s));
        }
        assertThat(peak).as("peak").isBetween(2000, 32767);
        assertThat(engine.isMusicPlaying()).isTrue();
    }

    @Test
    void rendersDoom1E1M1WithSoundEffects() throws Exception {
        assumeTrue(Iwads.directory().isPresent(), "DOOM_IWAD_DIR not set");
        renderSong(Iwads.DOOM1, "D_E1M1", "doom1-d_e1m1.wav", 10.0);
    }

    @Test
    void rendersFreedoom2SongWithSoundEffects() throws Exception {
        assumeTrue(Iwads.directory().isPresent(), "DOOM_IWAD_DIR not set");
        renderSong(Iwads.FREEDOOM2, "D_RUNNIN", "freedoom2-d_runnin.wav", 10.0);
    }
}
