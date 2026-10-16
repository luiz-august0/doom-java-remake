package doom.it.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.testkit.trace.Trace;
import doom.testkit.trace.TraceComparator;
import doom.testkit.trace.TraceFormat;
import doom.testkit.trace.TraceLine;
import doom.testkit.trace.TraceReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Reads the committed oracle traces of the IWAD demos ({@code testdata/traces}) with the testkit codec: the codec
 * accepts real oracle output, every line follows the §6.2 key order, tics are consecutive, and a trace is identical
 * to itself under the comparator. (The files are git-tracked game-independent data; no IWAD is needed.)
 */
class ReferenceTracesIT {

    private static final List<String> KEYS = List.of("t", "gs", "r", "lt", "map", "tot");

    private static Path tracesDir() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path p = dir.resolve("testdata/traces");
            if (Files.isDirectory(p)) {
                return p;
            }
        }
        return null;
    }

    @Test
    void realTracesParseAndAreWellFormed() throws IOException {
        Path dir = tracesDir();
        assumeTrue(dir != null, "testdata/traces not found");
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.getFileName().toString().endsWith(".trace.gz")).sorted().toList();
        }
        assumeTrue(!files.isEmpty(), "no reference traces");
        for (Path f : files) {
            Trace t = TraceReader.readAll(f);
            assertThat(t.lines()).as(f.getFileName().toString()).isNotEmpty();
            int expected = 0;
            for (TraceLine l : t.lines()) {
                assertThat(l.tic()).as(f + " tic").isEqualTo(expected++);
                assertThat(l.keys().subList(0, KEYS.size())).as(f + " key order").isEqualTo(KEYS);
                assertThat(l.keys()).contains("a", "s", "l", "d", "x", "m");
                assertThat(l.players()).isNotEmpty();
                for (int p : l.players()) {
                    assertThat(l.parts("p" + p)).hasSize(TraceFormat.PLAYER_FIELD_COUNT);
                }
            }
            assertThat(new TraceComparator().compare(f, f).identical()).as(f.getFileName().toString()).isTrue();
        }
    }
}
