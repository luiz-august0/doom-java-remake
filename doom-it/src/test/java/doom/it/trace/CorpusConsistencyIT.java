package doom.it.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.testkit.Iwads;
import doom.testkit.demo.Demo;
import doom.testkit.demo.DemoGenerator;
import doom.testkit.demo.DemoManifest;
import doom.testkit.demo.ManifestEntry;
import doom.wad.LumpDirectory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Checks the synthetic corpus ({@code testdata/demos}) against the real IWADs: every demo's map exists in its IWAD,
 * the map has the player starts the demo needs, and deathmatch demos find the four deathmatch starts vanilla requires
 * ({@code Only %i deathmatch spots, 4 required} is an {@code I_Error}). Catches a bad corpus entry before the oracle does.
 */
class CorpusConsistencyIT {

    private static final int THING_SIZE = 10;
    private static final int DEATHMATCH_START = 11;

    private static Path manifest() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path p = dir.resolve("testdata/demos/manifest.tsv");
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    @Test
    void everyDemoFitsItsMap() throws IOException {
        Iwads.assumeDirectory();
        Path manifest = manifest();
        assumeTrue(manifest != null, "testdata/demos/manifest.tsv not found");
        List<ManifestEntry> entries = DemoManifest.read(manifest);
        Map<String, LumpDirectory> wads = new HashMap<>();
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (ManifestEntry e : entries) {
            java.util.Optional<Path> iwad = Iwads.find(e.iwad());
            if (iwad.isEmpty()) {
                continue;
            }
            LumpDirectory lumps = wads.get(e.iwad());
            if (lumps == null) {
                lumps = LumpDirectory.open(List.of(iwad.get()));
                wads.put(e.iwad(), lumps);
            }
            int marker = lumps.find(e.spec().map());
            if (marker < 0 || !lumps.name(marker + 1).equals("THINGS")) {
                problems.add(e.name() + ": map " + e.spec().map() + " not found in " + e.iwad());
                continue;
            }
            ByteBuffer things = lumps.data(marker + 1).order(ByteOrder.LITTLE_ENDIAN);
            int[] starts = new int[5];
            int deathmatchStarts = 0;
            for (int i = 0; i + THING_SIZE <= things.limit(); i += THING_SIZE) {
                int type = things.getShort(i + 6);
                if (type >= 1 && type <= 4) {
                    starts[type]++;
                } else if (type == DEATHMATCH_START) {
                    deathmatchStarts++;
                }
            }
            for (int p = 1; p <= e.spec().players(); p++) {
                if (starts[p] == 0) {
                    problems.add(e.name() + ": no player " + p + " start on " + e.spec().map());
                }
            }
            if (e.spec().deathmatch() > 0 && deathmatchStarts < 4) {
                problems.add(e.name() + ": only " + deathmatchStarts + " deathmatch starts on " + e.spec().map());
            }
            checked++;
        }
        assertThat(problems).as("corpus problems (of %d checked demos)", checked).isEmpty();
        assertThat(checked).isGreaterThan(0);
    }

    @Test
    void demoFilesMatchTheGenerator() throws IOException {
        Path manifest = manifest();
        assumeTrue(manifest != null, "testdata/demos/manifest.tsv not found");
        Path dir = manifest.getParent();
        for (ManifestEntry e : DemoManifest.read(manifest)) {
            Demo onDisk = Demo.parse(Files.readAllBytes(dir.resolve(e.fileName())));
            assertThat(onDisk.tics()).as(e.name()).isEqualTo(e.spec().tics());
            assertThat(onDisk).as(e.name()).isEqualTo(DemoGenerator.generate(e.spec()));
        }
    }
}
