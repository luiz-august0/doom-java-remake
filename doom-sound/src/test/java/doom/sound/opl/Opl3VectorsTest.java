package doom.sound.opl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Replays the register sequences recorded by tools/opl3-vectors/vectors.c against the original opl3.c and checks that
 * the Java port produces exactly the same samples (compared as FNV-1a hashes of 128-frame blocks).
 */
class Opl3VectorsTest {

    private static final int BLOCK = 128;

    private static long fnv(short[] p, int from, int count) {
        long h = 0xcbf29ce484222325L;
        for (int i = from; i < from + count; i++) {
            int v = p[i] & 0xFFFF;
            h ^= v & 0xff;
            h *= 0x100000001b3L;
            h ^= v >>> 8;
            h *= 0x100000001b3L;
        }
        return h;
    }

    private record Vector(String name, List<String> lines) {
    }

    private static List<Vector> load() throws IOException {
        List<Vector> vectors = new ArrayList<>();
        try (InputStream raw = Opl3VectorsTest.class.getResourceAsStream("/opl3/vectors.txt.gz");
                BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.US_ASCII))) {
            String line;
            Vector current = null;
            String name = null;
            List<String> lines = null;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("vector ")) {
                    name = line.substring(7);
                    lines = new ArrayList<>();
                } else if (line.equals("end")) {
                    current = new Vector(name, lines);
                    vectors.add(current);
                } else if (lines != null) {
                    lines.add(line);
                }
            }
        }
        return vectors;
    }

    private static void replay(Vector v) {
        String[] header = v.name().split(" ");
        int rate = Integer.parseInt(header[header.length - 1]);
        Opl3 chip = new Opl3(rate);
        short[] buf = new short[2 * 400_000];
        short[] one = new short[2];
        List<String> lines = v.lines();
        for (int i = 0; i < lines.size(); i++) {
            String[] f = lines.get(i).split(" ");
            switch (f[0]) {
                case "w" -> chip.writeRegBuffered(Integer.parseInt(f[1]), Integer.parseInt(f[2]));
                case "W" -> chip.writeReg(Integer.parseInt(f[1]), Integer.parseInt(f[2]));
                case "s", "n" -> {
                    int frames = Integer.parseInt(f[1]);
                    if (f[0].equals("s")) {
                        chip.generateStream(buf, 0, frames);
                    } else {
                        for (int k = 0; k < frames; k++) {
                            chip.generate(one);
                            buf[k * 2] = one[0];
                            buf[k * 2 + 1] = one[1];
                        }
                    }
                    int blocks = (frames + BLOCK - 1) / BLOCK;
                    for (int b = 0; b < blocks; b++) {
                        String hl = lines.get(i + 1 + b);
                        assertThat(hl).startsWith("h ");
                        long expected = Long.parseUnsignedLong(hl.substring(2), 16);
                        int n = Math.min(BLOCK, frames - b * BLOCK);
                        long actual = fnv(buf, b * BLOCK * 2, n * 2);
                        assertThat(actual)
                                .as("vector %s, op line %d (%s %d), block %d", v.name(), i, f[0], frames, b)
                                .isEqualTo(expected);
                    }
                    i += blocks;
                }
                default -> throw new IllegalStateException("bad vector line: " + lines.get(i));
            }
        }
    }

    @TestFactory
    List<DynamicTest> referenceVectorsMatchBitForBit() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (Vector v : load()) {
            tests.add(DynamicTest.dynamicTest(v.name(), () -> replay(v)));
        }
        assertThat(tests).isNotEmpty();
        return tests;
    }
}
