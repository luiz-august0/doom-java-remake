package doom.it.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.testkit.Iwads;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * W3-D: level exits and the intermission against the oracle. {@code testdata/flow} holds PWADs that turn a line of
 * Freedoom's E1M1 (right in front of the player start) into an exit (special 52) or a secret exit (124), no-monsters
 * demos that walk into it and press the use button at different times, and the oracle traces of those runs
 * (see {@code make-flow-testdata.py}). The PWADs are generated from Freedoom at test time ({@link FlowWads}); the
 * trace covers the exit, the intermission (its length depends on the
 * accelerating button presses), and the first tics of the next level.
 */
class FlowSyncIT {

    private static Path projectRoot() {
        Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("testdata"))) {
            p = p.getParent();
        }
        return p;
    }

    private void check(String demo, String pwad, String ref) throws IOException {
        assumeTrue(Iwads.find("freedoom1.wad").isPresent(), "freedoom1.wad not available");
        Path root = projectRoot();
        assumeTrue(root != null, "testdata not found");
        Path dir = root.resolve("testdata/flow");
        Path out = Path.of("target", "sync-traces");
        Files.createDirectories(out);
        Path wads = FlowWads.generate(Iwads.require("freedoom1.wad"), out.resolve("flow-wads"));
        DemoSync.Result r = DemoSync.run(demo, Iwads.require("freedoom1.wad"), List.of(wads.resolve(pwad)),
                Files.readAllBytes(dir.resolve(demo + ".lmp")), dir.resolve(ref), out.resolve(demo + "-" + pwad + ".gz"),
                null, 0, -1);
        System.out.println(r.line());
        assertThat(r.error()).isNull();
        assertThat(r.divergenceTic()).as("first divergence: %s %s", r.divergenceKey(), r.detail()).isEqualTo(-1);
    }

    @Test
    void exitWithFastAcceleration() throws IOException {
        check("exda", "exit.wad", "exit-a.trace.gz");
    }

    @Test
    void exitWithSlowAcceleration() throws IOException {
        check("exdb", "exit.wad", "exit-b.trace.gz");
    }

    @Test
    void coopExitOfTwoPlayers() throws IOException {
        check("exdc", "exit.wad", "exit-c.trace.gz");
    }

    @Test
    void secretExit() throws IOException {
        check("exda", "secret.wad", "secret-a.trace.gz");
    }
}
