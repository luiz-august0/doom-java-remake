package doom.tools.demo;

import static org.assertj.core.api.Assertions.assertThat;

import doom.testkit.demo.Demo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenDemoToolTest {

    @TempDir
    Path tmp;

    private int run(ByteArrayOutputStream err, String... args) {
        return new GenDemoTool().run(args, new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    @Test
    void generatesOneDemoFromOptions() throws IOException {
        Path file = tmp.resolve("x/test.lmp");
        int status = run(new ByteArrayOutputStream(), "--out", file.toString(), "--map", "MAP05", "--skill", "4",
                "--players", "3", "--deathmatch", "1", "--fast", "--respawn", "--nomonsters", "--tics", "250",
                "--seed", "9", "--policy", "movement-only");
        assertThat(status).isZero();
        Demo demo = Demo.parse(Files.readAllBytes(file));
        assertThat(demo.tics()).isEqualTo(250);
        assertThat(demo.header().playerCount()).isEqualTo(3);
        assertThat(demo.header().skill()).isEqualTo(4);
        assertThat(demo.header().map()).isEqualTo(5);
        assertThat(demo.header().episode()).isEqualTo(1);
        assertThat(demo.header().deathmatch()).isEqualTo(1);
        assertThat(demo.header().fast() && demo.header().respawn() && demo.header().noMonsters()).isTrue();
        // movement only: no buttons at all
        for (Demo.Cmd[] tic : demo.cmds()) {
            for (Demo.Cmd c : tic) {
                assertThat(c.buttons()).isZero();
            }
        }
    }

    @Test
    void sameOptionsSameFile() throws IOException {
        Path a = tmp.resolve("a.lmp");
        Path b = tmp.resolve("b.lmp");
        assertThat(run(new ByteArrayOutputStream(), "--out", a.toString(), "--map", "E2M3", "--seed", "5")).isZero();
        assertThat(run(new ByteArrayOutputStream(), "--out", b.toString(), "--map", "E2M3", "--seed", "5")).isZero();
        assertThat(Files.readAllBytes(a)).isEqualTo(Files.readAllBytes(b));
    }

    @Test
    void usageErrorsExitTwo() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        assertThat(run(err)).isEqualTo(2);
        assertThat(run(err, "--out", tmp.resolve("x").toString(), "--map", "nonsense")).isEqualTo(2);
        assertThat(run(err, "--out", tmp.resolve("x").toString(), "--map", "E1M1", "--players", "9")).isEqualTo(2);
        assertThat(run(err, "--bogus")).isEqualTo(2);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("usage");
    }

    @Test
    void corpusIsWrittenAndChecked() throws IOException {
        Path dir = tmp.resolve("corpus");
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        assertThat(run(err, "--corpus", dir.toString())).isZero();
        assertThat(dir.resolve("manifest.tsv")).exists();
        assertThat(dir.resolve("d1e1m1a.lmp")).exists();
        assertThat(run(err, "--corpus", dir.toString(), "--check")).isZero();
        Files.write(dir.resolve("d1e1m1a.lmp"), new byte[] {1, 2, 3});
        assertThat(run(err, "--corpus", dir.toString(), "--check")).isEqualTo(1);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("d1e1m1a.lmp");
        // regenerate from the manifest only
        assertThat(run(err, "--manifest", dir.resolve("manifest.tsv").toString())).isZero();
        assertThat(run(err, "--corpus", dir.toString(), "--check")).isZero();
    }
}
