package doom.tools.trace;

import static org.assertj.core.api.Assertions.assertThat;

import doom.testkit.trace.Fnv1a64;
import doom.testkit.trace.TraceHeader;
import doom.testkit.trace.TraceLineBuilder;
import doom.testkit.trace.TraceWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TraceDiffToolTest {

    @TempDir
    Path tmp;

    private static void write(Path file, int tics, int breakAt) throws IOException {
        try (TraceWriter w = TraceWriter.create(file, TraceHeader.of("doom1.wad", "d", "1.9"))) {
            for (int t = 0; t < tics; t++) {
                int[] p = new int[61];
                p[13] = 100 - (t >= breakAt ? 5 : 0); // health
                w.writeLine(new TraceLineBuilder().put("t", t).put("gs", 0).put("r", t).put("lt", t)
                        .put("map", 1, 1, 2).put("tot", 0, 0, 0).put("p0", p)
                        .putCountHash("a", 0, Fnv1a64.INIT).putHash("s", 1).putHash("l", 2).putHash("d", 3)
                        .putCountHash("x", 0, Fnv1a64.INIT).put("m", 77).build());
            }
        }
    }

    private int run(ByteArrayOutputStream out, ByteArrayOutputStream err, String... args) {
        return new TraceDiffTool().run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    @Test
    void exitZeroWhenIdentical() throws IOException {
        Path a = tmp.resolve("a.trace.gz");
        Path b = tmp.resolve("b.trace");
        write(a, 30, 1000);
        write(b, 30, 1000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(run(out, new ByteArrayOutputStream(), a.toString(), b.toString())).isZero();
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("Identical: 30");
    }

    @Test
    void exitOneAndReportWhenDivergent() throws IOException {
        Path a = tmp.resolve("a.trace");
        Path b = tmp.resolve("b.trace");
        write(a, 30, 1000);
        write(b, 30, 17);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(run(out, new ByteArrayOutputStream(), a.toString(), b.toString(), "--context", "1")).isEqualTo(1);
        String text = out.toString(StandardCharsets.UTF_8);
        assertThat(text).contains("tic 17").contains("'p0'").contains("'health'").contains("ref : 100")
                .contains("ours: 95").contains("t=16;").contains("t=18;").doesNotContain("t=15;");
    }

    @Test
    void exitTwoOnErrors() throws IOException {
        Path a = tmp.resolve("a.trace");
        write(a, 3, 1000);
        Path bad = tmp.resolve("bad.trace");
        Files.writeString(bad, "not a trace\n");
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        assertThat(run(new ByteArrayOutputStream(), err, a.toString(), tmp.resolve("missing").toString())).isEqualTo(2);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("not a file");
        err.reset();
        assertThat(run(new ByteArrayOutputStream(), err, a.toString(), bad.toString())).isEqualTo(2);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("malformed");
        assertThat(run(new ByteArrayOutputStream(), new ByteArrayOutputStream(), a.toString())).isEqualTo(2);
        assertThat(run(new ByteArrayOutputStream(), new ByteArrayOutputStream(), a.toString(), a.toString(), "--bogus"))
                .isEqualTo(2);
        assertThat(run(new ByteArrayOutputStream(), new ByteArrayOutputStream(), a.toString(), a.toString(), "--context",
                "x")).isEqualTo(2);
    }

    @Test
    void registeredUnderItsName() {
        assertThat(new TraceDiffTool().name()).isEqualTo("trace-diff");
    }
}
