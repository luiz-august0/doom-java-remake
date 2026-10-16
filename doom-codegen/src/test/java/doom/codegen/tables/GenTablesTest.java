package doom.codegen.tables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenTablesTest {

    /** The committed resource, relative to this module (Surefire runs in the module directory). */
    private static final Path COMMITTED = Path.of("../doom-math/src/main/resources/doom/math/tables.bin");

    @Test
    void encodesTheDocumentedLayout() {
        long[] tangent = new long[4096];
        long[] sine = new long[10240];
        long[] atan = new long[2049];
        long[] rnd = new long[256];
        long[] gamma = new long[1280];
        tangent[0] = -1;
        atan[2048] = 0xffff_ffffL;
        rnd[255] = 249;
        gamma[1279] = 255;
        byte[] bytes = GenTables.encode(new GenTables.TableSet(tangent, sine, atan, rnd, gamma));
        assertThat(bytes).hasSize(8 + (12 + 4096 * 4) + (12 + 10240 * 4) + (12 + 2049 * 4) + (12 + 256) + (12 + 1280) + 4);
        assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("DMTB");
        assertThat(bytes[7]).isEqualTo((byte) 1); // version 1, big-endian
        assertThat(new String(bytes, 8, 4, StandardCharsets.US_ASCII)).isEqualTo("FTAN");
        // First finetangent value, big-endian -1.
        assertThat(bytes[20]).isEqualTo((byte) 0xff);
        assertThat(new String(bytes, bytes.length - 4, 4, StandardCharsets.US_ASCII)).isEqualTo("END.");
        assertThat(bytes[bytes.length - 5]).isEqualTo((byte) 255);
    }

    @Test
    void usageErrorReturnsTwo() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int status = GenTables.run(new String[] {"only-one"}, new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        assertThat(status).isEqualTo(2);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("usage");
    }

    /** Regenerates the resource from the original sources (when {@code LINUXDOOM_SRC} is set) and compares it. */
    @Test
    void regeneratingFromLinuxDoomReproducesTheCommittedResource(@TempDir Path out) throws Exception {
        String linuxdoom = System.getenv("LINUXDOOM_SRC");
        assumeTrue(linuxdoom != null && Files.isRegularFile(Path.of(linuxdoom, "tables.c")),
                "LINUXDOOM_SRC not set or without tables.c");
        String chocolate = System.getenv("CHOCOLATE_SRC");
        String[] args = chocolate != null && Files.isRegularFile(Path.of(chocolate, "src/tables.c"))
                ? new String[] {linuxdoom, out.toString(), "--verify", chocolate}
                : new String[] {linuxdoom, out.toString()};
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        int status = GenTables.run(args, new PrintStream(stdout, true, StandardCharsets.UTF_8), System.err);
        assertThat(status).isZero();
        assertThat(Files.readAllBytes(out.resolve(GenTables.OUTPUT_NAME))).isEqualTo(Files.readAllBytes(COMMITTED));
    }
}
