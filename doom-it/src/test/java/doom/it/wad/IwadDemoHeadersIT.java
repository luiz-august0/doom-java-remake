package doom.it.wad;

import static org.assertj.core.api.Assertions.assertThat;

import doom.wad.LumpDirectory;
import doom.wad.demo.Demo;
import doom.wad.demo.DemoFormat;
import doom.wad.demo.DemoWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** The IWAD demos (DEMO1..DEMO4 of the four IWADs, 14 in all) parse; the tic stream round-trips through the writer. */
class IwadDemoHeadersIT {

    @TestFactory
    Stream<DynamicTest> everyDemoParses() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String file : IwadFiles.NAMES) {
            for (int n = 1; n <= 4; n++) {
                String lump = "DEMO" + n;
                tests.add(DynamicTest.dynamicTest(file + " " + lump, () -> checkDemo(file, lump)));
            }
        }
        return tests.stream();
    }

    private static void checkDemo(String file, String lump) throws Exception {
        LumpDirectory lumps = IwadFiles.open(file);
        int n = lumps.find(lump);
        if (n < 0) {
            return;
        }
        Demo demo = Demo.read(lumps.bytes(n), file + " " + lump);
        assertThat(demo.header().version()).isIn(DemoFormat.VERSION_109, DemoFormat.VERSION_110);
        assertThat(demo.header().skill()).isBetween(0, 4);
        assertThat(demo.header().map()).isPositive();
        assertThat(demo.playerCount()).isBetween(1, 4);
        assertThat(demo.header().playerInGame()[demo.header().consolePlayer()]).isTrue();
        assertThat(demo.hasEndMarker()).isTrue();
        assertThat(demo.tics()).isPositive();
        // data size = header + tics * players * 4 + marker (a lump may carry padding after the marker)
        int size = DemoFormat.HEADER_SIZE + demo.tics() * demo.playerCount() * DemoFormat.TICCMD_SIZE + 1;
        assertThat(lumps.length(n)).isGreaterThanOrEqualTo(size);

        // Rewriting as version 109 reproduces the stream (except the version byte of 110 demos).
        DemoWriter w = new DemoWriter(demo.header());
        for (int t = 0; t < demo.tics(); t++) {
            for (int p = 0; p < 4; p++) {
                if (demo.header().playerInGame()[p]) {
                    w.addTicCmd(demo.forwardMove(t, p), demo.sideMove(t, p), demo.angleTurnByte(t, p),
                            demo.buttons(t, p));
                }
            }
        }
        byte[] original = java.util.Arrays.copyOf(lumps.bytes(n), size);
        byte[] written = w.toBytes();
        assertThat(written).hasSize(size);
        original[0] = (byte) DemoFormat.VERSION_109;
        assertThat(written).isEqualTo(original);
    }

    @Test
    void fourteenDemosInTheTestSet() throws Exception {
        int total = 0;
        for (String file : IwadFiles.NAMES) {
            LumpDirectory lumps = IwadFiles.open(file);
            for (int n = 1; n <= 4; n++) {
                if (lumps.find("DEMO" + n) >= 0) {
                    total++;
                }
            }
        }
        assertThat(total).isEqualTo(14);
    }
}
