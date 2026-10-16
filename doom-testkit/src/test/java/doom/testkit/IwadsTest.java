package doom.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

class IwadsTest {

    @TempDir
    Path tmp;

    @Test
    void directoryParsing() {
        assertThat(Iwads.directory(null)).isEmpty();
        assertThat(Iwads.directory("  ")).isEmpty();
        assertThat(Iwads.directory(tmp.resolve("nope").toString())).isEmpty();
        assertThat(Iwads.directory(tmp.toString())).contains(tmp);
    }

    @Test
    void findsExactAndCaseInsensitiveNames() throws IOException {
        Files.write(tmp.resolve("DOOM1.WAD"), new byte[] {1});
        Files.write(tmp.resolve("freedoom2.wad"), new byte[] {1});
        assertThat(Iwads.find(tmp, Iwads.FREEDOOM2)).contains(tmp.resolve("freedoom2.wad"));
        // On a case-insensitive file system the exact lookup already succeeds; either spelling is the same file.
        assertThat(Iwads.find(tmp, Iwads.DOOM1)).isPresent();
        assertThat(Iwads.find(tmp, Iwads.DOOM1).orElseThrow().getFileName().toString()).isEqualToIgnoringCase("doom1.wad");
        assertThat(Iwads.find(tmp, Iwads.FREEDM)).isEmpty();
        assertThat(Iwads.find(tmp.resolve("missing"), Iwads.DOOM1)).isEmpty();
    }

    @Test
    void assumeFalseAbortsTheTest() {
        assertThatThrownBy(() -> Iwads.assume(false, "no wad here")).isInstanceOf(TestAbortedException.class)
                .hasMessageContaining("no wad here");
        Iwads.assume(true, "unused");
    }

    @Test
    void missingMessageNamesTheVariable() {
        assertThat(Iwads.missingMessage("doom1.wad")).contains("doom1.wad");
    }

    @Test
    void allFourIwadsAreListed() {
        assertThat(Iwads.ALL).containsExactly("doom1.wad", "freedoom1.wad", "freedoom2.wad", "freedm.wad");
    }
}
