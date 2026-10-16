package doom.tools.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RefCoverageToolTest {

    private static final String C_SOURCE = """
            #include "doomdef.h"
            #define MACRO(x) { x; \\
                   foo(x) }
            /* void Commented(void) { } */
            static int table[] = { 1, 2, 3 };
            typedef struct { int a; } thing_t;

            void
            P_First
            ( mobj_t* mo )
            {
                if (mo) { puts("{ not a brace"); }
            }

            int P_Second(int a, int (*cb)(int))
            {
                return a;
            }
            // void LineComment(void) { }
            void A_Third (void) { while (1) { } }
            """;

    @Test
    void scannerFindsOnlyFunctionDefinitions() {
        assertThat(CFunctionScanner.scan(C_SOURCE)).extracting(CFunctionScanner.Function::name)
                .containsExactly("P_First", "P_Second", "A_Third");
        assertThat(CFunctionScanner.scan(C_SOURCE).get(0).line()).isEqualTo(9);
    }

    @Test
    void reportsUncoveredFunctionsAndDanglingReferences(@TempDir Path dir) throws IOException {
        Path c = Files.createDirectory(dir.resolve("c"));
        Files.writeString(c.resolve("p_test.c"), C_SOURCE, StandardCharsets.ISO_8859_1);
        Files.writeString(c.resolve("r_draw.c"), "void R_Draw(void) { }\n");
        Path java = Files.createDirectories(dir.resolve("j"));
        Files.writeString(java.resolve("X.java"), """
                class X {
                    @VanillaRef("p_test.c:P_First")
                    void a() { }
                    @VanillaRef({"p_test.c:A_Third", "p_test.c:P_Gone"})
                    void b() { }
                }
                """);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int status = new RefCoverageTool().run(
                new String[] {"--linuxdoom", c.toString(), "--java", java.toString(), "--fail-under", "90"},
                new PrintStream(bytes, true, StandardCharsets.UTF_8), System.err);

        String text = bytes.toString(StandardCharsets.UTF_8);
        assertThat(status).isEqualTo(1);
        assertThat(text).contains("2/3 functions").contains("uncovered  p_test.c:P_Second")
                .doesNotContain("R_Draw").contains("p_test.c:P_Gone");
    }
}
