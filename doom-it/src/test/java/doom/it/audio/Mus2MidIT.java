package doom.it.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.testkit.Iwads;
import doom.wad.LumpDirectory;
import doom.wad.WadFormatException;
import doom.wad.audio.MusConverter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Converts every {@code D_*} lump of the four IWADs with the Java {@link MusConverter} and with Chocolate Doom's
 * original {@code mus2mid.c} (through the C harness) and requires identical MIDI files (size and FNV-1a hash).
 */
class Mus2MidIT {

    private static long fnv(byte[] data) {
        long h = 0xcbf29ce484222325L;
        for (byte b : data) {
            h ^= b & 0xFF;
            h *= 0x100000001b3L;
        }
        return h;
    }

    @Test
    void allMusicLumpsConvertLikeChocolate() throws Exception {
        assumeTrue(Iwads.directory().isPresent(), "DOOM_IWAD_DIR not set");
        MusicRefTool tool = MusicRefTool.get();
        assumeTrue(tool != null, "C reference harness unavailable (clang / Chocolate Doom sources)");
        int compared = 0;
        for (String name : Iwads.ALL) {
            Path wad = Iwads.find(name).orElse(null);
            if (wad == null) {
                continue;
            }
            Path out = tool.workDir().resolve("mus2mid-" + name + ".txt");
            tool.run(List.of("mus2mid", wad.toString(), out.toString()));
            LumpDirectory lumps = LumpDirectory.open(List.of(wad));
            List<String> lines = Files.readAllLines(out);
            assertThat(lines).isNotEmpty();
            for (String line : lines) {
                String[] f = line.split(" ");
                int lump = lumps.find(f[0]);
                byte[] data = lumps.bytes(lump);
                if (f[1].equals("MIDI")) {
                    assertThat(data[0]).as(name + " " + f[0]).isEqualTo((byte) 'M');
                } else if (f[1].equals("FAIL")) {
                    try {
                        MusConverter.toMidi(data);
                        throw new AssertionError(name + " " + f[0] + ": Chocolate fails but Java converts");
                    } catch (WadFormatException expected) {
                        // both reject the lump
                    }
                } else {
                    byte[] midi = MusConverter.toMidi(data);
                    assertThat(midi.length).as(name + " " + f[0] + " size").isEqualTo(Integer.parseInt(f[1]));
                    assertThat(String.format("%016x", fnv(midi))).as(name + " " + f[0] + " hash").isEqualTo(f[2]);
                }
                compared++;
            }
        }
        assertThat(compared).isGreaterThan(20);
    }
}
