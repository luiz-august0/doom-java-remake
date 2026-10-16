package doom.it.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.testkit.Iwads;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The core corpus ({@link CoreCorpus}) needs none of the Wave 3 areas, so every demo must sync with the oracle for its
 * whole length and hit no stub. Oracle traces are generated on demand (and cached); demos without a trace are skipped.
 * {@code -Dsync.filter=<regex>} selects demos by name.
 */
class CoreSyncIT {

    @Test
    void coreCorpusSyncsCompletely() throws IOException {
        Optional<Path> iwadDir = Iwads.directory();
        assumeTrue(iwadDir.isPresent(), "DOOM_IWAD_DIR not set");
        Pattern filter = Pattern.compile(System.getProperty("sync.filter", ".*"));
        Path work = Path.of("target", "core-corpus");

        StringBuilder report = new StringBuilder("demo\tref_tics\tour_tics\tfirst_div_tic\tkey\tfirst_stub_tic\t"
                + "stub\tgate\n");
        List<String> failures = new ArrayList<>();
        int ran = 0;
        for (CoreCorpus.Entry e : CoreCorpus.build(iwadDir.get(), work)) {
            if (!filter.matcher(e.name()).matches()) {
                continue;
            }
            Optional<Path> ref = CoreCorpus.oracleTrace(e, iwadDir.get());
            if (ref.isEmpty()) {
                continue;
            }
            DemoSync.Result r = DemoSync.run(e.name().toUpperCase(Locale.ROOT), iwadDir.get().resolve(e.iwad()),
                    List.of(e.pwad()), e.demo(), ref.get(), null, null, 0, -1);
            ran++;
            report.append(r.line()).append('\n');
            if (r.divergenceTic() >= 0 || r.error() != null || r.firstStubTic() >= 0) {
                failures.add(r.line() + "  [" + r.detail() + "] " + r.stubHits());
            }
        }
        Files.writeString(Path.of("target", "core-sync-report.tsv"), report, StandardCharsets.UTF_8);
        System.out.println(report);
        assumeTrue(ran > 0, "no core corpus demo with an oracle trace");
        assertThat(failures).as("core corpus demos that do not sync completely").isEmpty();
    }
}
