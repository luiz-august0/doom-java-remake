package doom.lint;

import static doom.lint.TestCompiler.source;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import doom.lint.TestCompiler.Mode;
import doom.lint.TestCompiler.Result;
import doom.lint.TestCompiler.Source;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.Diagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compiles snippets with the plugin. Every line that must be reported ends with {@code // ERR}; every other line
 * must compile without a report.
 */
class BamLintPluginTest {

    @TempDir
    Path out;

    /** Lines (1-based) of {@code text} that end with the marker {@code // ERR}. */
    private static List<Long> markedLines(String text) {
        List<Long> lines = new ArrayList<>();
        String[] split = text.split("\n", -1);
        for (int i = 0; i < split.length; i++) {
            if (split[i].stripTrailing().endsWith("// ERR")) {
                lines.add((long) i + 1);
            }
        }
        return lines;
    }

    private Result check(String path, String text) {
        Result r = TestCompiler.compile(out, source(path, text));
        List<Long> expected = markedLines(text);
        assertThat(r.lintLines()).as("reported lines; all messages: %s", r.messages()).containsExactlyElementsOf(expected);
        assertThat(r.success()).as("compilation result; messages: %s", r.messages()).isEqualTo(expected.isEmpty());
        assertThat(r.messages()).as("non-lint diagnostics").allMatch(m -> m.text().startsWith("[BamLint]"));
        return r;
    }

    @Test
    void comparisonsOfLocalsAndParameters() {
        check("p/A.java", """
                package p;
                import doom.math.annotation.Bam;
                class A {
                    boolean f(@Bam int angle, int plain) {
                        @Bam int local = angle;
                        boolean r = false;
                        r |= angle < plain; // ERR
                        r |= plain > angle; // ERR
                        r |= local <= 0; // ERR
                        r |= 0 >= local; // ERR
                        r |= angle == plain;
                        r |= angle != local;
                        r |= plain < 3;
                        return r;
                    }
                }
                """);
    }

    @Test
    void comparisonsOfFieldsConstantsAndReturnTypes() {
        check("p/B.java", """
                package p;
                import doom.math.annotation.Bam;
                class B {
                    static final @Bam int ANG180 = 0x80000000;
                    @Bam int angle;
                    int plain;
                    B other;
                    @Bam int angleOf(int i) { return angle + i; }
                    int plainOf(int i) { return i; }
                    boolean f(int x) {
                        boolean r = false;
                        r |= angle > x; // ERR
                        r |= this.angle < x; // ERR
                        r |= other.angle >= x; // ERR
                        r |= x < ANG180; // ERR
                        r |= x < B.ANG180; // ERR
                        r |= angleOf(1) < x; // ERR
                        r |= other.angleOf(2) <= x; // ERR
                        r |= plain < x;
                        r |= plainOf(3) > x;
                        r |= other.plain >= x;
                        return r;
                    }
                }
                """);
    }

    @Test
    void elementsOfBamArrays() {
        check("p/C.java", """
                package p;
                import doom.math.annotation.Bam;
                class C {
                    @Bam int[] angles = new int[4];
                    @Bam int[][] grid = new int[2][2];
                    int[] plain = new int[4];
                    @Bam int[] anglesOf() { return angles; }
                    boolean f(@Bam int[] param, int i) {
                        boolean r = false;
                        r |= angles[i] < 0; // ERR
                        r |= param[0] > i; // ERR
                        r |= grid[0][1] >= 2; // ERR
                        r |= anglesOf()[i] <= 1; // ERR
                        r |= angles[i] >> 1 == 0; // ERR
                        r |= plain[i] < 0;
                        r |= angles.length > 2;
                        r |= param.length < 1;
                        return r;
                    }
                }
                """);
    }

    @Test
    void arithmeticRightShifts() {
        check("p/D.java", """
                package p;
                import doom.math.annotation.Bam;
                class D {
                    @Bam int angle;
                    int f(@Bam int a, int b) {
                        int r = a >> 19; // ERR
                        r += a >>> 19;
                        r += b >> 1;
                        r += 1 >> a;
                        a >>= 1; // ERR
                        angle >>= 2; // ERR
                        a >>>= 1;
                        b >>= 1;
                        return r;
                    }
                }
                """);
    }

    @Test
    void signedDivisionAndRemainder() {
        check("p/E.java", """
                package p;
                import doom.math.annotation.Bam;
                class E {
                    int f(@Bam int a, int b) {
                        int r = a / 2; // ERR
                        r += a % 3; // ERR
                        r += b / a; // ERR
                        a /= 2; // ERR
                        a %= 7; // ERR
                        r += b / 2 + b % 3;
                        return r;
                    }
                }
                """);
    }

    @Test
    void bamFlowsThroughAngleArithmetic() {
        check("p/F.java", """
                package p;
                import doom.math.annotation.Bam;
                class F {
                    boolean f(@Bam int a, @Bam int b, int x, boolean c) {
                        boolean r = false;
                        @Bam int t;
                        int u;
                        r |= (u = a) < 2;
                        r |= a - b > 0x80000000; // ERR
                        r |= (a + x) < 0; // ERR
                        r |= -a < 0; // ERR
                        r |= ~a > 0; // ERR
                        r |= (c ? a : 0) > 1; // ERR
                        r |= (t = a) < 2; // ERR
                        r |= ((a)) >= 3; // ERR
                        r |= (a + 1 >> 2) == 0; // ERR
                        r |= x + 1 < 0;
                        r |= (c ? x : 0) > 1;
                        r |= (a & 0xff) < 2;
                        r |= (a >>> 19) < 4096;
                        r |= a * 2 > 0;
                        return r;
                    }
                }
                """);
    }

    @Test
    void castsAreTheEscapeHatch() {
        check("p/G.java", """
                package p;
                import doom.math.annotation.Bam;
                class G {
                    boolean f(@Bam int a, int x) {
                        boolean r = false;
                        r |= (int) a < 0;
                        r |= ((int) a) >> 1 == 0;
                        r |= (@Bam int) x < 0; // ERR
                        r |= (long) a > 0L;
                        return r;
                    }
                }
                """);
    }

    @Test
    void lambdasLoopsAndRecords() {
        check("p/H.java", """
                package p;
                import doom.math.annotation.Bam;
                import java.util.function.IntPredicate;
                class H {
                    record Mobj(@Bam int angle, int health) {
                        boolean behind() {
                            return angle > 0x40000000; // ERR
                        }
                    }
                    boolean f(Mobj m, @Bam int[] angles) {
                        IntPredicate p = (@Bam int v) -> v < 3; // ERR
                        IntPredicate q = v -> v < 3;
                        boolean r = m.angle() < 0; // ERR
                        r |= m.health() < 0;
                        for (@Bam int v : angles) {
                            r |= v > 1; // ERR
                        }
                        for (int v : angles) {
                            r |= v > 1;
                        }
                        return r && p.test(1) && q.test(2);
                    }
                }
                """);
    }

    @Test
    void nestedAndLocalClassesAreReportedOnce() {
        check("p/I.java", """
                package p;
                import doom.math.annotation.Bam;
                class I {
                    @Bam int angle;
                    class Inner {
                        boolean f() { return angle < 0; } // ERR
                        class Deeper {
                            boolean g(@Bam int a) { return a > 1; } // ERR
                        }
                    }
                    static class Nested {
                        boolean f(@Bam int a) { return a >= 2; } // ERR
                    }
                    Object local() {
                        class Local {
                            boolean f(@Bam int a) { return a <= 3; } // ERR
                        }
                        return new Object() {
                            boolean f(@Bam int a) { return a < 4; } // ERR
                        };
                    }
                }
                class SecondTopLevel {
                    boolean f(@Bam int a) { return a < 5; } // ERR
                }
                """);
    }

    @Test
    void suppressWarningsSilencesTheEnclosingDeclaration() {
        check("p/J.java", """
                package p;
                import doom.math.annotation.Bam;
                class J {
                    @SuppressWarnings("BamLint")
                    boolean f(@Bam int a) { return a < 0; }
                    boolean g(@Bam int a) {
                        @SuppressWarnings("BamLint")
                        boolean quiet = a < 0;
                        boolean loud = a < 0; // ERR
                        return quiet && loud;
                    }
                    @SuppressWarnings({"unchecked", "BamLint"})
                    static class Quiet {
                        boolean h(@Bam int a) { return a > 0 && a >> 1 == 0 && a / 2 == 0; }
                    }
                    @SuppressWarnings("unchecked")
                    boolean k(@Bam int a) { return a > 0; } // ERR
                }
                """);
    }

    @Test
    void annotationsAreReadFromClassFilesOfOtherCompilations() throws Exception {
        Path lib = Files.createDirectories(out.resolve("lib"));
        Result libResult = TestCompiler.compile(Mode.NONE, lib, List.of(),
                source("doom/math/annotation/Bam.java", TestCompiler.BAM_SOURCE),
                source("lib/Mobj.java", """
                        package lib;
                        import doom.math.annotation.Bam;
                        public class Mobj {
                            public static final @Bam int ANG90 = 0x40000000;
                            public @Bam int angle;
                            public @Bam int[] history = new int[4];
                            public @Bam int angleTo(Mobj other) { return 0; }
                        }
                        """));
        assertThat(libResult.success()).as("%s", libResult.messages()).isTrue();

        String client = """
                package app;
                import lib.Mobj;
                class Client {
                    boolean f(Mobj a, Mobj b, int x) {
                        boolean r = a.angle < x; // ERR
                        r |= a.angleTo(b) > Mobj.ANG90; // ERR
                        r |= a.history[0] >= x; // ERR
                        r |= (a.angle >> 1) == 0; // ERR
                        return r;
                    }
                }
                """;
        Path app = Files.createDirectories(out.resolve("app"));
        Result r = TestCompiler.compile(Mode.INSTANCE, app, List.of("-cp", lib.toString()), source("app/Client.java", client));
        assertThat(r.lintLines()).as("%s", r.messages()).containsExactlyElementsOf(markedLines(client));
        assertThat(r.success()).isFalse();
    }

    @Test
    void reportsAreErrorsThatNameTheOperator() {
        Result r = TestCompiler.compile(out, source("p/K.java", """
                package p;
                import doom.math.annotation.Bam;
                class K {
                    int f(@Bam int a) { return (a < 0 ? 1 : 0) + (a >> 1) + a / 2; }
                }
                """));
        assertThat(r.success()).isFalse();
        assertThat(r.lint()).extracting(TestCompiler.Message::kind).containsOnly(Diagnostic.Kind.ERROR);
        assertThat(r.lint()).extracting(TestCompiler.Message::text).containsExactly(
                "[BamLint] signed comparison '<' of a @Bam angle; use Angle.lt/le/gt/ge (unsigned)",
                "[BamLint] arithmetic shift '>>' of a @Bam angle; use '>>>'",
                "[BamLint] signed division '/' of a @Bam angle; use Integer.divideUnsigned / Integer.remainderUnsigned");
    }

    @Test
    void warnOptionDowngradesToWarnings() {
        Source k = source("p/L.java", """
                package p;
                import doom.math.annotation.Bam;
                class L {
                    boolean f(@Bam int a) { return a < 0; }
                }
                """);
        Result r = TestCompiler.compile(Mode.XPLUGIN, out, List.of("-Xplugin:BamLint warn"),
                k, source("doom/math/annotation/Bam.java", TestCompiler.BAM_SOURCE));
        assertThat(r.success()).as("%s", r.messages()).isTrue();
        assertThat(r.lint()).hasSize(1).allMatch(m -> m.kind() == Diagnostic.Kind.WARNING);
    }

    @Test
    void pluginIsDiscoveredWithXplugin() {
        Result r = TestCompiler.compile(Mode.XPLUGIN, out, List.of(),
                source("p/M.java", """
                        package p;
                        import doom.math.annotation.Bam;
                        class M {
                            boolean f(@Bam int a) { return a > 0; }
                        }
                        """),
                source("doom/math/annotation/Bam.java", TestCompiler.BAM_SOURCE));
        assertThat(r.success()).isFalse();
        assertThat(r.lint()).hasSize(1).allMatch(m -> m.kind() == Diagnostic.Kind.ERROR && m.line() == 4);
    }

    @Test
    void processorBootstrapRunsTheSameChecks() {
        String text = """
                package p;
                import doom.math.annotation.Bam;
                class O {
                    @Bam int angle;
                    boolean f(int x) {
                        boolean r = angle < x; // ERR
                        r |= (angle >> 1) == x; // ERR
                        r |= angle == x;
                        return r;
                    }
                }
                """;
        Result r = TestCompiler.compileForked(out, TestCompiler.processorOptions(), source("p/O.java", text),
                source("doom/math/annotation/Bam.java", TestCompiler.BAM_SOURCE));
        assertThat(r.success()).isFalse();
        assertThat(r.lintLines()).as("%s", r.messages()).containsExactlyElementsOf(markedLines(text));
        assertThat(r.messages()).as("only lint errors").allMatch(m -> m.text().startsWith("[BamLint]"));
    }

    @Test
    void processorWarnMode() {
        List<String> options = new ArrayList<>(TestCompiler.processorOptions());
        options.add("-Adoom.lint.mode=warn");
        Result r = TestCompiler.compileForked(out, options,
                source("p/Q.java", """
                        package p;
                        import doom.math.annotation.Bam;
                        class Q {
                            boolean f(@Bam int a) { return a >= 0; }
                        }
                        """),
                source("doom/math/annotation/Bam.java", TestCompiler.BAM_SOURCE));
        assertThat(r.success()).as("%s", r.messages()).isTrue();
        assertThat(r.lint()).hasSize(1).allMatch(m -> m.kind() == Diagnostic.Kind.WARNING);
    }

    @Test
    void codeWithoutBamCompilesCleanly() {
        check("p/N.java", """
                package p;
                class N {
                    int f(int a, int b, int[] c) {
                        int r = a < b ? a >> 1 : b / 2;
                        r += c[0] % 3 > 1 ? 1 : 0;
                        r >>= 1;
                        return r;
                    }
                }
                """);
    }

    @Test
    void unknownOptionIsRejected() {
        assertThatThrownBy(() -> new BamLintPlugin().init(null, "strict"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown option");
    }
}
