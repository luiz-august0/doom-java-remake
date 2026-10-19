package doom.engine.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LooseFilesTest {

    @TempDir
    Path dir;

    private String wad(String name, String id) throws IOException {
        Path p = dir.resolve(name);
        Files.write(p, (id + "\0\0\0\0\0\0\0\0").getBytes(StandardCharsets.US_ASCII));
        return p.toString();
    }

    @Test
    void anIwadBecomesIwadAndAPwadIsMerged() throws IOException {
        String iwad = wad("My Game.WAD", "IWAD"); // any name: the header decides
        String pwad = wad("mod.wad", "PWAD");
        assertThat(LooseFiles.expand(List.of(pwad, iwad)))
                .containsExactly("-iwad", iwad, "-merge", pwad);
        assertThat(LaunchOptions.parse(CommandLine.of(LooseFiles.expand(List.of(iwad)))).iwad()).isEqualTo(iwad);
    }

    @Test
    void onlyTheFirstIwadIsTheIwad() throws IOException {
        String a = wad("a.wad", "IWAD");
        String b = wad("b.wad", "IWAD");
        assertThat(LooseFiles.expand(List.of(a, b))).containsExactly("-iwad", a, "-merge", b);
    }

    @Test
    void patchesAndDemos() {
        assertThat(LooseFiles.expand(List.of("x.deh", "y.BEX", "d1.lmp", "d2.lmp")))
                .containsExactly("d2.lmp", "-deh", "x.deh", "y.BEX", "-playdemo", "d1.lmp");
    }

    @Test
    void anExplicitParameterWins() throws IOException {
        String loose = wad("freedoom2.wad", "IWAD");
        List<String> args = LooseFiles.expand(List.of(loose, "-iwad", "doom1.wad", "-warp", "1"));
        assertThat(args).containsExactly("-iwad", "doom1.wad", "-warp", "1", "-iwad", loose);
        assertThat(LaunchOptions.parse(CommandLine.of(args)).iwad()).isEqualTo("doom1.wad");
    }

    @Test
    void argumentsAfterAParameterAreUntouched() {
        List<String> args = List.of("-file", "a.wad", "b.deh", "-skill", "3");
        assertThat(LooseFiles.expand(args)).isEqualTo(args);
        assertThat(LooseFiles.expand(List.of())).isEmpty();
    }

    @Test
    void aMissingWadIsMergedSoTheUsualErrorIsReported() {
        assertThat(LooseFiles.expand(List.of("missing.wad"))).containsExactly("-merge", "missing.wad");
    }

    @Test
    void otherArgumentsStayInPlace() {
        assertThat(LooseFiles.expand(List.of("readme.txt", "-nomonsters"))).containsExactly("readme.txt", "-nomonsters");
    }
}
