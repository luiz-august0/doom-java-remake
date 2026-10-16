package doom.it.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.testkit.Iwads;
import doom.testkit.demo.DemoManifest;
import doom.testkit.demo.ManifestEntry;
import doom.testkit.oracle.OracleRunner;
import doom.testkit.oracle.OracleRunner.Request;
import doom.testkit.oracle.OracleRunner.Result;
import doom.testkit.trace.Trace;
import doom.testkit.trace.TraceComparator;
import doom.testkit.trace.TraceReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the real oracle on a sample of the synthetic corpus: each demo must play to its end and give a well-formed
 * trace, and running the same demo twice (here in parallel) must give identical traces. Skipped without
 * {@code DOOM_IWAD_DIR} or without the oracle ({@code tools/oracle/run.sh} and the patched Chocolate Doom binary).
 */
class OracleCorpusIT {

    /** One single-player, one multiplayer and one movement-only demo per IWAD. */
    private static final Set<String> SAMPLE = Set.of("d1e1m1a", "d1e1m1b", "d1e1m1m", "f1e1m1a", "f1e2m1b", "f2m01a",
            "f2m02b", "f2m01m", "fmm01a", "fmm03p");

    @TempDir
    Path tmp;

    @Test
    void sampleDemosPlayAndAreDeterministic() throws IOException {
        Iwads.assumeDirectory();
        OracleRunner oracle = OracleRunner.fromEnvironment().orElse(null);
        assumeTrue(oracle != null, "the oracle is not installed (tools/oracle/run.sh + chocolate-doom binary)");
        Path manifest = null;
        for (Path dir = Path.of("").toAbsolutePath(); dir != null && manifest == null; dir = dir.getParent()) {
            Path p = dir.resolve("testdata/demos/manifest.tsv");
            manifest = Files.isRegularFile(p) ? p : null;
        }
        assumeTrue(manifest != null, "testdata/demos/manifest.tsv not found");

        List<Request> requests = new ArrayList<>();
        List<ManifestEntry> sample = new ArrayList<>();
        for (ManifestEntry e : DemoManifest.read(manifest)) {
            if (SAMPLE.contains(e.name()) && Iwads.isAvailable(e.iwad())) {
                sample.add(e);
                for (String run : List.of("x", "y")) {
                    requests.add(Request.of(e.name() + run, Iwads.require(e.iwad()),
                            manifest.getParent().resolve(e.fileName()), tmp.resolve(e.name() + "." + run + ".trace.gz")));
                }
            }
        }
        assumeTrue(!sample.isEmpty(), "no sample IWAD available");

        List<Result> results = oracle.runAll(requests, 4);
        for (Result r : results) {
            assertThat(r.ok()).as("%s: %s %s", r.id(), r.status(), r.message()).isTrue();
        }
        for (ManifestEntry e : sample) {
            Path x = tmp.resolve(e.name() + ".x.trace.gz");
            Path y = tmp.resolve(e.name() + ".y.trace.gz");
            Trace t = TraceReader.readAll(x);
            assertThat(t.header().iwad()).as(e.name()).isEqualToIgnoringCase(e.iwad());
            // Contract 6.1 / FORMAT.md: one line per G_Ticker call, none for the call that reads the end marker.
            assertThat(t.lines().size()).as("%s: trace lines vs demo tics", e.name()).isEqualTo(e.spec().tics());
            assertThat(t.lines().getFirst().tic()).isZero();
            assertThat(t.lines().getLast().players()).hasSize(e.spec().players());
            assertThat(new TraceComparator().compare(x, y).identical()).as(e.name() + " is deterministic").isTrue();
        }
    }
}
