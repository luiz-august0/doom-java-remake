package doom.tools.demo;

import doom.testkit.Iwads;
import doom.testkit.demo.DemoManifest;
import doom.testkit.demo.ManifestEntry;
import doom.testkit.oracle.OracleRunner;
import doom.testkit.oracle.TraceCache;
import doom.testkit.trace.TraceComparator;
import doom.testkit.trace.TraceComparison;
import doom.tools.Tool;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * {@code demo-suite}: runs a demo corpus through the oracle (and, once the engine can play demos, through ours) and
 * compares the traces.
 *
 * <pre>
 * demo-suite [--manifest testdata/demos/manifest.tsv] [--iwad-dir DIR] [--filter REGEX] [--jobs N]
 *            [--timeout SECONDS] [--refresh] [--ours-dir DIR] [--report FILE] [--context N]
 * </pre>
 *
 * <p><b>Oracle side.</b> For each manifest entry the oracle trace is looked up in the cache
 * ({@code $DOOM_ORACLE_CACHE/traces}, keyed by demo bytes, IWAD and oracle fingerprint) and produced by
 * {@link OracleRunner} when missing, {@code --jobs} runs at a time. When the oracle is not installed, cached traces are
 * used as they are and the other demos are reported as {@code oracle unavailable}.
 *
 * <p><b>Our side.</b> {@link OursTracer} is the hook for the engine: the engine tool plugs in an implementation that
 * plays the demo headless and writes our trace ({@code demo-run}). Until then, {@code --ours-dir DIR} compares with
 * traces already written there as {@code <name>.trace[.gz]}; without either, only the oracle side is exercised.
 *
 * <p>Exit status: 0 when every demo ran and every compared trace is identical; 1 when any demo failed, any trace
 * diverged or the oracle was unavailable for some demo; 2 for usage errors.
 */
public final class DemoSuiteTool implements Tool {

    /** Produces our trace for a demo: plays it headless with the engine and writes the trace file. */
    @FunctionalInterface
    public interface OursTracer {
        /**
         * @param entry    the manifest entry
         * @param demo     the demo file
         * @param iwad     the IWAD file
         * @param traceOut where to write our trace (gzip when the name ends with {@code .gz})
         */
        void trace(ManifestEntry entry, Path demo, Path iwad, Path traceOut) throws Exception;
    }

    private final Supplier<Optional<OracleRunner>> oracle;
    private final Supplier<TraceCache> cache;
    private final OursTracer ours;

    /** The tool as registered in {@link doom.tools.Main}: environment oracle, default cache, no engine hook yet. */
    public DemoSuiteTool() {
        this(OracleRunner::fromEnvironment, TraceCache::defaultCache, null);
    }

    /** Full constructor, for tests and for the engine integration. {@code ours} may be null. */
    public DemoSuiteTool(Supplier<Optional<OracleRunner>> oracle, Supplier<TraceCache> cache, OursTracer ours) {
        this.oracle = oracle;
        this.cache = cache;
        this.ours = ours;
    }

    @Override
    public String name() {
        return "demo-suite";
    }

    @Override
    public String summary() {
        return "Run a demo corpus through the oracle (cached) and compare with our traces";
    }

    @Override
    public int run(String[] args) {
        return run(args, System.out, System.err);
    }

    /** Result of one demo, for the report. */
    private record Row(ManifestEntry entry, String oracle, String ours, boolean good) {
        String line() {
            return String.format("%-8s %-14s %-8s p=%d tics=%-5d oracle: %-28s ours: %s", entry.name(), entry.iwad(),
                    entry.spec().map(), entry.spec().players(), entry.spec().tics(), oracle, ours);
        }
    }

    /** Testable form. */
    public int run(String[] args, PrintStream out, PrintStream err) {
        CliArgs cli;
        try {
            cli = CliArgs.parse(args, Set.of("manifest", "iwad-dir", "filter", "jobs", "timeout", "ours-dir", "report",
                    "context"), Set.of("refresh", "help"));
        } catch (IllegalArgumentException e) {
            err.println("demo-suite: " + e.getMessage());
            usage(err);
            return 2;
        }
        if (cli.flag("help")) {
            usage(err);
            return 0;
        }
        try {
            Path manifest = cli.has("manifest") ? Path.of(cli.get("manifest", "")) : findManifest();
            if (manifest == null || !Files.isRegularFile(manifest)) {
                err.println("demo-suite: manifest not found (use --manifest)");
                return 2;
            }
            Optional<Path> iwadDir = cli.has("iwad-dir") ? Optional.of(Path.of(cli.get("iwad-dir", "")))
                    : Iwads.directory();
            if (iwadDir.isEmpty() || !Files.isDirectory(iwadDir.get())) {
                err.println("demo-suite: no IWAD directory (set DOOM_IWAD_DIR or use --iwad-dir)");
                return 2;
            }
            int jobs = cli.getInt("jobs", Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() / 2)));
            int timeout = cli.getInt("timeout", 300);
            int context = cli.getInt("context", TraceComparator.DEFAULT_CONTEXT);
            List<ManifestEntry> entries = DemoManifest.filter(DemoManifest.read(manifest), cli.get("filter", null));
            if (entries.isEmpty()) {
                err.println("demo-suite: no demo matches");
                return 2;
            }
            Path oursDir = cli.has("ours-dir") ? Path.of(cli.get("ours-dir", "")) : null;
            return suite(entries, manifest.toAbsolutePath().getParent(), iwadDir.get(), jobs, Duration.ofSeconds(timeout),
                    cli.flag("refresh"), oursDir, context, cli.has("report") ? Path.of(cli.get("report", "")) : null,
                    out, err);
        } catch (IllegalArgumentException | IOException e) {
            err.println("demo-suite: " + e.getMessage());
            return 2;
        }
    }

    private int suite(List<ManifestEntry> entries, Path demoDir, Path iwadDir, int jobs, Duration timeout,
                      boolean refresh, Path oursDir, int context, Path report, PrintStream out, PrintStream err)
            throws IOException {
        Optional<OracleRunner> runner = oracle.get();
        TraceCache cache = this.cache.get();
        String fingerprint = runner.map(OracleRunner::fingerprint).orElse("unavailable");
        if (runner.isEmpty()) {
            err.println("demo-suite: the oracle is not installed (tools/oracle/run.sh or the chocolate-doom binary is"
                    + " missing): only cached traces can be used");
        }

        // Phase 1: resolve the oracle trace of every demo (cache hit, or a batch of oracle runs).
        int n = entries.size();
        Path[] traces = new Path[n];
        String[] oracleStatus = new String[n];
        boolean[] oracleGood = new boolean[n];
        List<Integer> toRun = new ArrayList<>();
        List<OracleRunner.Request> requests = new ArrayList<>();
        String[] keys = new String[n];
        for (int i = 0; i < n; i++) {
            ManifestEntry e = entries.get(i);
            Path demo = demoDir.resolve(e.fileName());
            Optional<Path> iwad = Iwads.find(iwadDir, e.iwad());
            if (!Files.isRegularFile(demo)) {
                oracleStatus[i] = "demo file missing";
                continue;
            }
            if (iwad.isEmpty()) {
                oracleStatus[i] = "skipped (" + e.iwad() + " missing)";
                oracleGood[i] = true;
                traces[i] = null;
                continue;
            }
            keys[i] = TraceCache.key(e.iwad(), Files.readAllBytes(demo), fingerprint);
            if (!refresh && cache.contains(e.name(), keys[i])) {
                traces[i] = cache.path(e.name(), keys[i]);
                oracleStatus[i] = "cached";
                oracleGood[i] = true;
            } else if (runner.isPresent()) {
                toRun.add(i);
                requests.add(new OracleRunner.Request(e.name(), iwad.get(), demo, cache.path(e.name(), keys[i]), null,
                        -1, -1, List.of(), timeout));
            } else {
                Optional<Path> any = cache.findAny(e.name());
                if (any.isPresent()) {
                    traces[i] = any.get();
                    oracleStatus[i] = "cached (stale key)";
                    oracleGood[i] = true;
                } else {
                    oracleStatus[i] = "oracle unavailable";
                }
            }
        }
        if (!requests.isEmpty()) {
            out.printf("running the oracle on %d demo(s), %d at a time...%n", requests.size(), jobs);
            List<OracleRunner.Result> results = runner.orElseThrow().runAll(requests, jobs);
            for (int k = 0; k < results.size(); k++) {
                int i = toRun.get(k);
                OracleRunner.Result r = results.get(k);
                if (r.ok()) {
                    traces[i] = r.trace();
                    oracleStatus[i] = String.format("ran (%d tics, %.1fs)", r.gametics(), r.elapsed().toMillis() / 1000.0);
                    oracleGood[i] = true;
                    cache.prune(entries.get(i).name(), keys[i]);
                } else {
                    oracleStatus[i] = r.status().name().toLowerCase(java.util.Locale.ROOT) + ": " + shorten(r.message());
                }
            }
        }

        // Phase 2: ours.
        List<Row> rows = new ArrayList<>();
        int divergent = 0;
        for (int i = 0; i < n; i++) {
            ManifestEntry e = entries.get(i);
            String oursStatus = "-";
            boolean good = oracleGood[i];
            if (traces[i] != null && good && (oursDir != null || ours != null)) {
                try {
                    Path oursTrace = oursDir != null ? findOurs(oursDir, e.name()) : tempTrace(e);
                    if (oursDir == null) {
                        ours.trace(e, demoDir.resolve(e.fileName()), Iwads.find(iwadDir, e.iwad()).orElseThrow(), oursTrace);
                    }
                    if (oursTrace == null) {
                        oursStatus = "no trace in " + oursDir;
                        good = false;
                    } else {
                        TraceComparison c = new TraceComparator(context).compare(traces[i], oursTrace);
                        if (c.identical()) {
                            oursStatus = "match (" + c.linesCompared() + " tics)";
                        } else {
                            oursStatus = "DIVERGED at tic " + c.divergence().tic() + " key "
                                    + c.divergence().key() + fieldSuffix(c);
                            good = false;
                            divergent++;
                            err.println("== " + e.name() + " ==");
                            err.print(c.describe());
                        }
                    }
                } catch (Exception ex) {
                    oursStatus = "error: " + shorten(String.valueOf(ex.getMessage()));
                    good = false;
                }
            } else if (oursDir == null && ours == null) {
                oursStatus = "not run (engine hook pending)";
            }
            rows.add(new Row(e, oracleStatus[i], oursStatus, good));
        }

        int failed = 0;
        for (Row r : rows) {
            out.println((r.good() ? "ok   " : "FAIL ") + r.line());
            if (!r.good()) {
                failed++;
            }
        }
        out.printf("%d demo(s): %d ok, %d failed, %d divergent%n", rows.size(), rows.size() - failed, failed, divergent);
        if (report != null) {
            StringBuilder sb = new StringBuilder("name\tiwad\tmap\tplayers\ttics\toracle\tours\tgood\n");
            for (Row r : rows) {
                ManifestEntry e = r.entry();
                sb.append(String.join("\t", e.name(), e.iwad(), e.spec().map(), Integer.toString(e.spec().players()),
                        Integer.toString(e.spec().tics()), r.oracle(), r.ours(), Boolean.toString(r.good()))).append('\n');
            }
            Files.writeString(report, sb.toString(), StandardCharsets.UTF_8);
        }
        return failed == 0 ? 0 : 1;
    }

    private static String fieldSuffix(TraceComparison c) {
        var d = c.divergence();
        return d.fieldName() != null ? " (" + d.fieldName() + ")" : "";
    }

    private static Path findOurs(Path dir, String name) {
        for (String suffix : new String[] {".trace.gz", ".trace"}) {
            Path p = dir.resolve(name + suffix);
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    private Path tempTrace(ManifestEntry e) throws IOException {
        Path dir = Files.createTempDirectory("doom-suite-");
        dir.toFile().deleteOnExit();
        Path p = dir.resolve(e.name() + ".trace.gz");
        p.toFile().deleteOnExit();
        return p;
    }

    private static String shorten(String s) {
        String one = s.replace('\n', ' ').strip();
        return one.length() > 90 ? one.substring(0, 87) + "..." : one;
    }

    private static Path findManifest() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path p = dir.resolve("testdata/demos/manifest.tsv");
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    private static void usage(PrintStream err) {
        err.println("usage: demo-suite [--manifest FILE] [--iwad-dir DIR] [--filter REGEX] [--jobs N] [--timeout SECONDS]");
        err.println("                  [--refresh] [--ours-dir DIR] [--report FILE] [--context N]");
        err.println("  Produces (or reuses the cached) oracle traces of the corpus; with --ours-dir, compares them with ours.");
    }
}
