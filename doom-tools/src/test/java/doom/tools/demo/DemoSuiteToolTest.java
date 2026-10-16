package doom.tools.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.testkit.demo.DemoManifest;
import doom.testkit.demo.ManifestEntry;
import doom.testkit.oracle.OracleRunner;
import doom.testkit.oracle.TraceCache;
import doom.testkit.trace.TraceHeader;
import doom.testkit.trace.TraceLine;
import doom.testkit.trace.TraceReader;
import doom.testkit.trace.TraceWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DemoSuiteToolTest {

    private static final String FAKE = """
            #!/bin/bash
            iwad="$1"; demo="$2"; shift 2
            trace=""
            while [ $# -gt 0 ]; do
              case "$1" in
                -trace) trace="$2"; shift ;;
              esac
              shift
            done
            name=$(basename "$demo" .lmp)
            case "$name" in d1e1m2a) echo "Error: Only 2 deathmatch spots, 4 required"; exit 255 ;; esac
            {
              echo "#doom-trace v1 iwad=doom1.wad demo=$name version=1.9"
              for i in 0 1 2 3; do
                echo "t=$i;gs=0;r=$i;lt=$i;map=1,1,2;tot=0,0,0;p0=1,2,3;a=0,cbf29ce484222325;s=cbf29ce484222325;l=cbf29ce484222325;d=cbf29ce484222325;x=0,cbf29ce484222325;m=5"
              done
            } > "$trace"
            echo "timed 4 gametics in 1 realtics (0.0 fps)"
            exit 255
            """;

    @TempDir
    Path tmp;

    private Path setup() throws IOException {
        Path iwads = Files.createDirectories(tmp.resolve("iwads"));
        Files.write(iwads.resolve("doom1.wad"), new byte[] {1});
        Path demos = Files.createDirectories(tmp.resolve("demos"));
        // generate three real corpus demos through the tool
        new GenDemoTool().run(new String[] {"--corpus", demos.toString()}, new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()));
        Path script = tmp.resolve("run.sh");
        Files.writeString(script, FAKE, StandardCharsets.UTF_8);
        return iwads;
    }

    private DemoSuiteTool tool(DemoSuiteTool.OursTracer ours) {
        OracleRunner runner = new OracleRunner(tmp.resolve("run.sh"), null, tmp.resolve("work"));
        return new DemoSuiteTool(() -> Optional.of(runner), () -> new TraceCache(tmp.resolve("cache")), ours);
    }

    private int run(DemoSuiteTool tool, ByteArrayOutputStream out, ByteArrayOutputStream err, String... args) {
        return tool.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    @Test
    void runsOracleThenUsesTheCache() throws IOException {
        assumeTrue(Files.isExecutable(Path.of("/bin/bash")));
        Path iwads = setup();
        String[] args = {"--manifest", tmp.resolve("demos/manifest.tsv").toString(), "--iwad-dir", iwads.toString(),
            "--filter", "^d1e1m1", "--jobs", "3"};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(run(tool(null), out, new ByteArrayOutputStream(), args)).isZero();
        String text = out.toString(StandardCharsets.UTF_8);
        assertThat(text).contains("running the oracle on 5 demo(s)").contains("d1e1m1a").contains("ran (4 tics")
                .contains("engine hook pending").contains("5 demo(s): 5 ok, 0 failed");
        assertThat(Files.list(tmp.resolve("cache")).count()).isEqualTo(5);

        out.reset();
        assertThat(run(tool(null), out, new ByteArrayOutputStream(), args)).isZero();
        assertThat(out.toString(StandardCharsets.UTF_8)).doesNotContain("running the oracle").contains("cached");

        out.reset();
        run(tool(null), out, new ByteArrayOutputStream(), concat(args, "--refresh"));
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("running the oracle");
    }

    @Test
    void oracleFailureIsReportedPerDemo() throws IOException {
        assumeTrue(Files.isExecutable(Path.of("/bin/bash")));
        Path iwads = setup();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int status = run(tool(null), out, new ByteArrayOutputStream(), "--manifest",
                tmp.resolve("demos/manifest.tsv").toString(), "--iwad-dir", iwads.toString(), "--filter", "^d1e1m2a");
        assertThat(status).isEqualTo(1);
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("FAIL").contains("failed: Error: Only 2 deathmatch");
    }

    @Test
    void comparesWithOursFromAHookAndFromADirectory() throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/bash")));
        Path iwads = setup();
        String[] base = {"--manifest", tmp.resolve("demos/manifest.tsv").toString(), "--iwad-dir", iwads.toString(),
            "--filter", "^d1e1m1[ab]$"};

        // a perfect engine: copies the cached oracle trace
        Path cache = tmp.resolve("cache");
        DemoSuiteTool.OursTracer perfect = (entry, demo, iwad, out) -> {
            try (TraceReader r = TraceReader.open(newestTrace(cache, entry.name()));
                 TraceWriter w = TraceWriter.create(out, r.header())) {
                for (TraceLine l = r.next(); l != null; l = r.next()) {
                    w.write(l);
                }
            }
        };
        run(tool(null), new ByteArrayOutputStream(), new ByteArrayOutputStream(), base); // fill the cache
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(run(tool(perfect), out, new ByteArrayOutputStream(), base)).isZero();
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("match (4 tics)");

        // a wrong engine
        DemoSuiteTool.OursTracer wrong = (entry, demo, iwad, outFile) -> {
            try (TraceWriter w = TraceWriter.create(outFile, TraceHeader.of("doom1.wad", entry.name(), "1.9"))) {
                for (int i = 0; i < 4; i++) {
                    w.writeLine("t=" + i + ";gs=0;r=" + (i == 2 ? 99 : i) + ";lt=" + i
                            + ";map=1,1,2;tot=0,0,0;p0=1,2,3;a=0,cbf29ce484222325;s=cbf29ce484222325;"
                            + "l=cbf29ce484222325;d=cbf29ce484222325;x=0,cbf29ce484222325;m=5");
                }
            }
        };
        out.reset();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        assertThat(run(tool(wrong), out, err, base)).isEqualTo(1);
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("DIVERGED at tic 2 key r");
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("Divergence at tic 2");

        // --ours-dir
        Path oursDir = Files.createDirectories(tmp.resolve("ours"));
        perfect.trace(DemoManifest.read(tmp.resolve("demos/manifest.tsv")).stream()
                .filter(e -> e.name().equals("d1e1m1a")).findFirst().orElseThrow(), null, null, oursDir.resolve("d1e1m1a.trace.gz"));
        out.reset();
        assertThat(run(tool(null), out, new ByteArrayOutputStream(), concat(base, "--ours-dir", oursDir.toString())))
                .isEqualTo(1);
        String text = out.toString(StandardCharsets.UTF_8);
        assertThat(text).contains("match (4 tics)").contains("no trace in");
    }

    @Test
    void oracleUnavailableUsesCacheOrReports() throws IOException {
        Path iwads = setup();
        DemoSuiteTool noOracle = new DemoSuiteTool(Optional::empty, () -> new TraceCache(tmp.resolve("cache")), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int status = run(noOracle, out, err, "--manifest", tmp.resolve("demos/manifest.tsv").toString(), "--iwad-dir",
                iwads.toString(), "--filter", "^d1e1m1a");
        assertThat(status).isEqualTo(1);
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("oracle unavailable");
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("oracle is not installed");
    }

    @Test
    void missingIwadIsSkippedAndUsageErrorsExitTwo() throws IOException {
        Path iwads = setup();
        Files.delete(iwads.resolve("doom1.wad"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(run(tool(null), out, new ByteArrayOutputStream(), "--manifest",
                tmp.resolve("demos/manifest.tsv").toString(), "--iwad-dir", iwads.toString(), "--filter", "^d1e1m1a"))
                .isZero();
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("skipped (doom1.wad missing)");
        assertThat(run(tool(null), out, new ByteArrayOutputStream(), "--manifest", tmp.resolve("none.tsv").toString(),
                "--iwad-dir", iwads.toString())).isEqualTo(2);
        assertThat(run(tool(null), out, new ByteArrayOutputStream(), "--bogus")).isEqualTo(2);
        assertThat(run(tool(null), out, new ByteArrayOutputStream(), "--manifest",
                tmp.resolve("demos/manifest.tsv").toString(), "--iwad-dir", iwads.toString(), "--filter", "zzzz"))
                .isEqualTo(2);
        assertThat(ManifestEntry.COLUMNS).startsWith("name");
    }

    private static Path newestTrace(Path cache, String name) throws IOException {
        try (var stream = Files.list(cache)) {
            return stream.filter(p -> p.getFileName().toString().startsWith(name + "."))
                    .findFirst().orElseThrow();
        }
    }

    private static String[] concat(String[] a, String... b) {
        String[] out = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
