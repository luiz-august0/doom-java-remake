package doom.tools.waddump;

import static org.assertj.core.api.Assertions.assertThat;

import doom.wad.WadWriter;
import doom.wad.demo.DemoHeader;
import doom.wad.demo.DemoWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WadDumpToolTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(String... args) {
        return new WadDumpTool().run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void listsLumpsDemosAndMapsOfAPwad(@TempDir Path dir) throws IOException {
        DemoWriter demo = new DemoWriter(new DemoHeader(109, 2, 1, 3, 0, 0, 0, 0, 0,
                new boolean[] {true, false, false, false}));
        demo.addTicCmd(25, 0, 0, 0);
        WadWriter w = WadWriter.pwad().add("DEMO1", demo.toBytes()).marker("MAP07");
        for (String l : new String[] {"THINGS", "LINEDEFS", "SIDEDEFS", "VERTEXES", "SEGS", "SSECTORS", "NODES",
                "SECTORS", "REJECT"}) {
            w.add(l, new byte[0]);
        }
        w.add("BLOCKMAP", new byte[8]);
        Path file = dir.resolve("test.wad");
        w.write(file);

        assertThat(run(file.toString(), "--lumps", "--maps", "--demos", "--ident")).isZero();
        String text = stdout();
        assertThat(text).contains("lumps: 12 in 1 file(s)").contains("DEMO1").contains("BLOCKMAP");
        assertThat(text).contains("identification:").contains("not an IWAD");
        assertThat(text).containsPattern("MAP07 +0 +0 +0 +0 +0 +0 +0 +0");
        assertThat(text).containsPattern("DEMO1 +version 109 +skill 2 +episode 1 +map 3");
        assertThat(text).contains("tics 1 ");
    }

    @Test
    void failsCleanlyOnBadInput(@TempDir Path dir) throws IOException {
        assertThat(run()).isEqualTo(2);
        assertThat(run("--bogus", "x.wad")).isEqualTo(2);
        Path junk = dir.resolve("junk.wad");
        java.nio.file.Files.write(junk, new byte[] {1, 2, 3});
        assertThat(run(junk.toString())).isEqualTo(1);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("wad-dump:").contains("junk.wad");
        assertThat(run(dir.resolve("missing.wad").toString())).isEqualTo(1);
    }
}
