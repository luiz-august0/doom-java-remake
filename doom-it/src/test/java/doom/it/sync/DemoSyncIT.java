package doom.it.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.testkit.Iwads;
import doom.testkit.demo.DemoManifest;
import doom.testkit.demo.ManifestEntry;
import doom.testkit.oracle.TraceCache;
import doom.wad.LumpDirectory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Demo sync gate of W2-A: every demo must replay bit-identically to the oracle up to the first tic where a rule of a
 * not-yet-implemented area (a stub) was needed. Covers the 14 IWAD demos and the synthetic movement-only corpus
 * ({@code testdata/demos}, names ending in {@code m} or {@code p}); {@code -Dsync.filter=<regex>} selects demos by
 * name ({@code doom1-demo1}, {@code d1e1m1m}, ...), {@code -Dsync.synthetic=all} adds the whole synthetic corpus.
 * The report is written to {@code target/sync-report.tsv}; our traces go to {@code target/sync-traces/}.
 * {@code -Dsync.verbose=<demo>:<from>:<to>} also writes our verbose trace of that range.
 */
class DemoSyncIT {

    private static final String[][] IWAD_DEMOS = {
        {"doom1.wad", "DEMO1"}, {"doom1.wad", "DEMO2"}, {"doom1.wad", "DEMO3"},
        {"freedoom1.wad", "DEMO1"}, {"freedoom1.wad", "DEMO2"}, {"freedoom1.wad", "DEMO3"}, {"freedoom1.wad", "DEMO4"},
        {"freedoom2.wad", "DEMO1"}, {"freedoom2.wad", "DEMO2"}, {"freedoom2.wad", "DEMO3"}, {"freedoom2.wad", "DEMO4"},
        {"freedm.wad", "DEMO1"}, {"freedm.wad", "DEMO2"}, {"freedm.wad", "DEMO3"},
    };

    private static Path projectRoot() {
        Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("testdata"))) {
            p = p.getParent();
        }
        return p;
    }

    @Test
    void demosSyncUpToTheFirstStub() throws IOException {
        Optional<Path> iwadDir = Iwads.directory();
        assumeTrue(iwadDir.isPresent(), "DOOM_IWAD_DIR not set");
        Path root = projectRoot();
        assumeTrue(root != null, "testdata not found");

        Pattern filter = Pattern.compile(System.getProperty("sync.filter", ".*"));
        String verboseSpec = System.getProperty("sync.verbose", "");
        boolean allSynthetic = "all".equals(System.getProperty("sync.synthetic", ""));
        Path out = Path.of("target", "sync-traces");
        Files.createDirectories(out);

        List<DemoSync.Result> results = new ArrayList<>();

        for (String[] d : IWAD_DEMOS) {
            String name = d[0].replace(".wad", "") + "-" + d[1].toLowerCase(java.util.Locale.ROOT);
            if (!filter.matcher(name).matches() || !Iwads.isAvailable(d[0])) {
                continue;
            }
            Path ref = root.resolve("testdata/traces/" + name + ".trace.gz");
            if (!Files.isRegularFile(ref)) {
                continue;
            }
            Path iwad = Iwads.require(d[0]);
            LumpDirectory lumps = LumpDirectory.open(List.of(iwad));
            byte[] demo = lumps.bytes(lumps.get(d[1]));
            results.add(runOne(name, iwad, demo, ref, out, verboseSpec));
        }

        Path manifest = root.resolve("testdata/demos/manifest.tsv");
        if (Files.isRegularFile(manifest)) {
            TraceCache cache = TraceCache.defaultCache();
            for (ManifestEntry e : DemoManifest.read(manifest)) {
                boolean movementOnly = e.name().endsWith("m") || e.name().endsWith("p");
                if (!filter.matcher(e.name()).matches() || !(allSynthetic || movementOnly)
                        || !Iwads.isAvailable(e.iwad())) {
                    continue;
                }
                Optional<Path> ref = cache.findAny(e.name());
                if (ref.isEmpty()) {
                    continue;
                }
                byte[] demo = Files.readAllBytes(root.resolve("testdata/demos/" + e.fileName()));
                results.add(runOne(e.name(), Iwads.require(e.iwad()), demo, ref.get(), out, verboseSpec));
            }
        }

        StringBuilder report = new StringBuilder(
                "demo\tref_tics\tour_tics\tfirst_div_tic\tkey\tfirst_stub_tic\tstub\tgate\n");
        List<String> failures = new ArrayList<>();
        java.util.Map<String, String[]> exceptions = new java.util.HashMap<>();
        Path exFile = root.resolve("testdata/demos/exceptions.tsv");
        if (Files.isRegularFile(exFile)) {
            for (String l : Files.readAllLines(exFile, StandardCharsets.UTF_8)) {
                if (!l.isBlank() && !l.startsWith("#")) {
                    String[] f = l.split("\t", 4);
                    exceptions.put(f[0].toUpperCase(java.util.Locale.ROOT), f);
                }
            }
        }
        for (DemoSync.Result r : results) {
            report.append(r.line()).append('\n');
            String[] ex = exceptions.get(r.line().split("\t", 2)[0].toUpperCase(java.util.Locale.ROOT));
            String[] cols = r.line().split("\t");
            boolean excused = ex != null && cols.length > 4 && !cols[3].equals("-")
                    && Integer.parseInt(cols[3]) >= Integer.parseInt(ex[1]) && cols[4].equals(ex[2]);
            if (!r.gatePasses() && !excused) {
                failures.add(r.line() + "  [" + r.detail() + "]");
            }
        }
        Files.writeString(Path.of("target", "sync-report.tsv"), report, StandardCharsets.UTF_8);
        System.out.println(report);
        assertThat(failures).as("demos diverging before the first stub hit").isEmpty();
    }

    private static DemoSync.Result runOne(String name, Path iwad, byte[] demo, Path ref, Path out, String verboseSpec)
            throws IOException {
        Path verbose = null;
        int from = 0;
        int to = -1;
        if (verboseSpec.startsWith(name + ":")) {
            String[] p = verboseSpec.split(":");
            from = Integer.parseInt(p[1]);
            to = Integer.parseInt(p[2]);
            verbose = out.resolve(name + ".verbose.txt");
        }
        return DemoSync.run(name.toUpperCase(java.util.Locale.ROOT), iwad, demo, ref,
                out.resolve(name + ".trace.gz"), verbose, from, to);
    }
}
