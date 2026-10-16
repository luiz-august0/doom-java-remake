package doom.it.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.sound.music.DmxVersion;
import doom.sound.music.OplMusicPlayer;
import doom.testkit.Iwads;
import doom.wad.LumpDirectory;
import doom.wad.audio.GenMidi;
import doom.wad.audio.MusicScore;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs the Java music driver (sequencer + DMX voice logic + Nuked OPL3 port) and Chocolate Doom's original
 * {@code i_oplmusic.c} + {@code opl3.c} (through the C harness) on the same song with the same block size, and requires
 * the OPL register writes (time, register, value) and the rendered PCM to be identical.
 */
class MusicDriverIT {

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of(Iwads.DOOM1, "D_E1M1", 48000, 512, 20.0, 64, false, true),
                Arguments.of(Iwads.DOOM1, "D_E1M1", 48000, 480, 8.0, 120, true, true),
                Arguments.of(Iwads.DOOM1, "D_E1M2", 44100, 4096, 12.0, 96, false, true),
                Arguments.of(Iwads.DOOM1, "D_INTROA", 48000, 256, 20.0, 64, false, true),
                Arguments.of(Iwads.DOOM1, "D_INTROA", 48000, 256, 20.0, 64, false, false),
                Arguments.of(Iwads.DOOM1, "D_E1M8", 48000, 512, 12.0, 127, false, true),
                Arguments.of(Iwads.FREEDOOM2, "D_RUNNIN", 48000, 512, 12.0, 64, false, true),
                Arguments.of(Iwads.FREEDOOM2, "D_THE_DA", 48000, 512, 12.0, 80, true, true),
                Arguments.of(Iwads.FREEDOOM2, "D_DM2TTL", 48000, 512, 15.0, 64, false, true),
                Arguments.of(Iwads.FREEDOOM2, "D_ROMERO", 48000, 512, 10.0, 8, false, true),
                Arguments.of(Iwads.FREEDOOM1, "D_E2M5", 48000, 512, 10.0, 64, false, true));
    }

    @ParameterizedTest(name = "{0} {1} rate={2} block={3} opl3={6}")
    @MethodSource("cases")
    void matchesChocolateRegisterWritesAndPcm(String wadName, String lumpName, int rate, int block, double seconds,
                                              int volume, boolean opl3, boolean loop) throws Exception {
        Path wad = Iwads.find(wadName).orElse(null);
        assumeTrue(wad != null, wadName + " not available");
        MusicRefTool tool = MusicRefTool.get();
        assumeTrue(tool != null, "C reference harness unavailable (clang / Chocolate Doom sources)");

        String base = wadName.replace(".wad", "") + "-" + lumpName + "-" + rate + "-" + block + (opl3 ? "-opl3" : "")
                + (loop ? "" : "-noloop");
        Path log = tool.workDir().resolve(base + ".reg");
        Path pcm = tool.workDir().resolve(base + ".pcm");
        tool.run(List.of("play", wad.toString(), lumpName, Integer.toString(rate), Integer.toString(block),
                Double.toString(seconds), Integer.toString(volume), opl3 ? "1" : "0", loop ? "1" : "0",
                log.toString(), pcm.toString()));

        LumpDirectory lumps = LumpDirectory.open(List.of(wad));
        GenMidi genMidi = GenMidi.parse(lumps.data(lumps.get("GENMIDI")));
        MusicScore score = MusicScore.fromLump(lumps.data(lumps.get(lumpName)), lumpName);

        List<String> javaLog = new ArrayList<>();
        OplMusicPlayer player = new OplMusicPlayer(genMidi, rate, opl3, DmxVersion.DOOM_1_9,
                (t, reg, v) -> javaLog.add(t + " " + reg + " " + v));
        player.setMusicVolume(volume);
        player.play(score, loop);

        int total = (int) (seconds * rate);
        short[] out = new short[total * 2];
        short[] blockBuf = new short[block * 2];
        for (int done = 0; done < total; ) {
            int n = Math.min(block, total - done);
            player.render(blockBuf, 0, n);
            System.arraycopy(blockBuf, 0, out, done * 2, n * 2);
            done += n;
        }

        List<String> cLog = Files.readAllLines(log);
        Files.write(tool.workDir().resolve(base + ".java.reg"), javaLog);
        assertThat(javaLog.size()).as("number of register writes").isEqualTo(cLog.size());
        for (int i = 0; i < cLog.size(); i++) {
            if (!cLog.get(i).equals(javaLog.get(i))) {
                throw new AssertionError("register write " + i + " differs: C '" + cLog.get(i) + "' Java '"
                        + javaLog.get(i) + "'");
            }
        }

        ByteBuffer ref = ByteBuffer.wrap(Files.readAllBytes(pcm)).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(ref.remaining() / 2).as("pcm samples").isEqualTo(out.length);
        int nonZero = 0;
        for (int i = 0; i < out.length; i++) {
            short expected = ref.getShort();
            if (expected != 0) {
                nonZero++;
            }
            if (expected != out[i]) {
                throw new AssertionError("pcm sample " + i + " (frame " + i / 2 + ") differs: C " + expected
                        + " Java " + out[i]);
            }
        }
        assertThat(nonZero).as("music is audible").isGreaterThan(out.length / 20);
    }
}
