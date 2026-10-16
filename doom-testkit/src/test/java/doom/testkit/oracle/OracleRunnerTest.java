package doom.testkit.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.testkit.oracle.OracleRunner.Request;
import doom.testkit.oracle.OracleRunner.Result;
import doom.testkit.oracle.OracleRunner.Status;
import doom.testkit.trace.Trace;
import doom.testkit.trace.TraceReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the runner against a fake {@code run.sh} that mimics the oracle's command line, output and exit status. */
class OracleRunnerTest {

    /** Parses -timedemo/-trace like Chocolate, plays "nothing", writes a trace of N lines and exits 255. */
    private static final String FAKE = """
            #!/bin/bash
            iwad="$1"; demo="$2"; shift 2
            trace=""; verbose=""; from=""; to=""
            while [ $# -gt 0 ]; do
              case "$1" in
                -trace) trace="$2"; shift ;;
                -traceverbose) verbose="$2"; shift ;;
                -tracefrom) from="$2"; shift ;;
                -traceto) to="$2"; shift ;;
              esac
              shift
            done
            name=$(basename "$demo" .lmp)
            case "$name" in
              *crash*) echo "Error: Only 2 deathmatch spots, 4 required" ; exit 255 ;;
              *hang*) sleep 30; exit 0 ;;
              *silent*) exit 255 ;;
            esac
            tics=$(( $(wc -c < "$demo") ))
            {
              echo "#doom-trace v1 iwad=x.wad demo=$name version=1.9"
              i=0
              while [ $i -lt 5 ]; do
                echo "t=$i;gs=0;r=$i;lt=$i;map=1,1,2;tot=0,0,0;a=0,cbf29ce484222325;s=cbf29ce484222325;l=cbf29ce484222325;d=cbf29ce484222325;x=0,cbf29ce484222325;m=0"
                i=$((i+1))
              done
            } > "$trace"
            if [ -n "$verbose" ]; then echo "#doom-trace v1 iwad=x.wad demo=$name version=1.9 range=$from-$to" > "$verbose"; fi
            echo "timed 5 gametics in 3 realtics (0.0 fps) bytes=$tics"
            exit 255
            """;

    @TempDir
    Path tmp;

    private OracleRunner runner() throws IOException {
        assumeTrue(Files.isExecutable(Path.of("/bin/bash")), "needs /bin/bash");
        Path script = tmp.resolve("run.sh");
        Files.writeString(script, FAKE, StandardCharsets.UTF_8);
        return new OracleRunner(script, null, tmp.resolve("work"));
    }

    private Path demo(String name) throws IOException {
        Path p = tmp.resolve("demos").resolve(name + ".lmp");
        Files.createDirectories(p.getParent());
        Files.write(p, new byte[] {109, 2, 1, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, (byte) 0x80});
        return p;
    }

    @Test
    void successfulRunStoresAGzipTrace() throws IOException {
        Path out = tmp.resolve("out/ok.trace.gz");
        Result r = runner().run(Request.of("ok", tmp.resolve("x.wad"), demo("ok"), out));
        assertThat(r.status()).as(r.message() + r.output()).isEqualTo(Status.OK);
        assertThat(r.exitCode()).isEqualTo(255);
        assertThat(r.gametics()).isEqualTo(5);
        assertThat(r.trace()).isEqualTo(out);
        Trace t = TraceReader.readAll(out);
        assertThat(t.lines()).hasSize(5);
        assertThat(t.header().demo()).isEqualTo("ok");
        assertThat(Files.readAllBytes(out)[0] & 0xff).isEqualTo(0x1f);
        assertThat(Files.list(tmp.resolve("work")).count()).as("per-run directories are removed").isZero();
    }

    @Test
    void plainOutputAndVerboseRange() throws IOException {
        Path out = tmp.resolve("out/p.trace");
        Path verbose = tmp.resolve("out/p.verbose");
        Request req = new Request("p", tmp.resolve("x.wad"), demo("p"), out, verbose, 2, 4, List.of(), null);
        Result r = runner().run(req);
        assertThat(r.ok()).isTrue();
        assertThat(Files.readString(out)).startsWith("#doom-trace v1");
        assertThat(Files.readString(verbose)).contains("range=2-4");
    }

    @Test
    void engineErrorsAreFailuresEvenWithExit255() throws IOException {
        Result r = runner().run(Request.of("crash", tmp.resolve("x.wad"), demo("crash"), tmp.resolve("c.trace.gz")));
        assertThat(r.status()).isEqualTo(Status.FAILED);
        assertThat(r.message()).contains("deathmatch spots");
        assertThat(tmp.resolve("c.trace.gz")).doesNotExist();
    }

    @Test
    void noTraceIsAFailure() throws IOException {
        Result r = runner().run(Request.of("silent", tmp.resolve("x.wad"), demo("silent"), tmp.resolve("s.trace.gz")));
        assertThat(r.status()).isEqualTo(Status.FAILED);
        assertThat(r.message()).contains("no trace");
    }

    @Test
    void timeoutKillsTheRun() throws IOException {
        Request req = new Request("hang", tmp.resolve("x.wad"), demo("hang"), tmp.resolve("h.trace.gz"), null, -1, -1,
                List.of(), Duration.ofMillis(700));
        long start = System.nanoTime();
        Result r = runner().run(req);
        assertThat(r.status()).isEqualTo(Status.TIMEOUT);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(20));
    }

    @Test
    void missingScriptIsAnErrorNotAnException() throws IOException {
        OracleRunner broken = new OracleRunner(tmp.resolve("absent.sh"), null, tmp.resolve("work"));
        Result r = broken.run(Request.of("x", tmp.resolve("x.wad"), demo("x"), tmp.resolve("x.trace.gz")));
        assertThat(r.ok()).isFalse();
    }

    @Test
    void batchRunsInParallelAndKeepsOrder() throws IOException {
        OracleRunner runner = runner();
        List<Request> requests = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String name = i == 5 ? "crash5" : "demo" + i;
            requests.add(Request.of(name, tmp.resolve("x.wad"), demo(name), tmp.resolve("batch/" + name + ".trace.gz")));
        }
        List<Result> results = runner.runAll(requests, 4);
        assertThat(results).extracting(Result::id).containsExactlyElementsOf(requests.stream().map(Request::id).toList());
        assertThat(results).filteredOn(Result::ok).hasSize(11);
        assertThat(results.get(5).status()).isEqualTo(Status.FAILED);
        assertThat(tmp.resolve("batch/demo11.trace.gz")).exists();
    }

    @Test
    void fingerprintChangesWithTheScript() throws IOException {
        OracleRunner runner = runner();
        String before = runner.fingerprint();
        Files.writeString(runner.runScript(), FAKE + "\n# changed\n", StandardCharsets.UTF_8);
        assertThat(runner.fingerprint()).isNotEqualTo(before);
    }

    @Test
    void cacheKeysCoverDemoIwadAndOracle() throws IOException {
        byte[] demo = {1, 2, 3};
        String k = TraceCache.key("doom1.wad", demo, "fp");
        assertThat(k).hasSize(16).matches("[0-9a-f]+");
        assertThat(TraceCache.key("DOOM1.WAD", demo, "fp")).isEqualTo(k);
        assertThat(TraceCache.key("doom1.wad", new byte[] {1, 2, 4}, "fp")).isNotEqualTo(k);
        assertThat(TraceCache.key("freedm.wad", demo, "fp")).isNotEqualTo(k);
        assertThat(TraceCache.key("doom1.wad", demo, "fp2")).isNotEqualTo(k);

        TraceCache cache = new TraceCache(tmp.resolve("cache"));
        String newer = TraceCache.key("doom1.wad", demo, "fp3");
        Files.createDirectories(cache.directory());
        Files.write(cache.path("d1e1m1a", k), new byte[] {1});
        Files.write(cache.path("d1e1m1a", newer), new byte[] {1});
        Files.write(cache.path("d1e1m1ab", k), new byte[] {1}); // a different demo with a longer name
        assertThat(cache.contains("d1e1m1a", k)).isTrue();
        cache.prune("d1e1m1a", newer);
        assertThat(cache.contains("d1e1m1a", k)).isFalse();
        assertThat(cache.contains("d1e1m1a", newer)).isTrue();
        assertThat(cache.contains("d1e1m1ab", k)).isTrue();
    }
}
