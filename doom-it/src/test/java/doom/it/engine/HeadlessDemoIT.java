package doom.it.engine;

import static org.assertj.core.api.Assertions.assertThat;

import doom.engine.DoomEngine;
import doom.engine.EngineParams;
import doom.engine.cli.LaunchOptions;
import doom.engine.config.Config;
import doom.engine.config.DataDirs;
import doom.testkit.Iwads;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Plays IWAD demos headless through the engine, with the sound system running on the silent backend, and checks the
 * shape of the trace against the reference trace of the oracle: header, one line per tic, same tic numbers and game
 * states. The simulation's own divergences (map specials, AI, combat before Wave 3) are not judged here.
 */
class HeadlessDemoIT {

    @TempDir
    Path tmp;

    private static List<String> readTrace(Path file) throws Exception {
        List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                file.toString().endsWith(".gz") ? new GZIPInputStream(Files.newInputStream(file))
                        : Files.newInputStream(file), StandardCharsets.UTF_8))) {
            String l;
            while ((l = r.readLine()) != null) {
                lines.add(l);
            }
        }
        return lines;
    }

    private static String field(String line, String key) {
        for (String f : line.split(";")) {
            if (f.startsWith(key + "=")) {
                return f.substring(key.length() + 1);
            }
        }
        return null;
    }

    @Test
    void doom1Demo1WritesATraceOfTheReferenceShape() throws Exception {
        Path iwad = Iwads.assumeAvailable(Iwads.DOOM1);
        Path ref = Path.of("..", "testdata", "traces", "doom1-demo1.trace.gz");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(ref), "reference trace missing");
        Path out = tmp.resolve("ours.trace.gz");

        LaunchOptions options = LaunchOptions.parse("-headless", "-iwad", iwad.toString(), "-timedemo", "DEMO1",
                "-trace", out.toString());
        EngineParams params = new EngineParams(options).config(Config.defaults())
                .dirs(DataDirs.resolve("Linux", java.util.Map.of("XDG_CONFIG_HOME", tmp.toString()), tmp.toString()));
        try (DoomEngine engine = new DoomEngine(params)) {
            engine.run();
            assertThat(engine.demoEnded()).isTrue();
            assertThat(engine.timedemoResult()).isNotNull();
            assertThat(engine.timedemoResult().gameTics()).isEqualTo(engine.ranTics() - 1);
        }

        List<String> ours = readTrace(out);
        List<String> expected = readTrace(ref);
        assertThat(ours.get(0)).isEqualTo(expected.get(0));
        assertThat(ours).hasSameSizeAs(expected);
        for (int i = 1; i < ours.size(); i += 97) {
            assertThat(field(ours.get(i), "t")).isEqualTo(field(expected.get(i), "t"));
            assertThat(field(ours.get(i), "gs")).isEqualTo(field(expected.get(i), "gs"));
            assertThat(field(ours.get(i), "lt")).isEqualTo(field(expected.get(i), "lt"));
        }
    }
}
