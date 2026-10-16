package doom.codegen.tables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CArrayParserTest {

    @Test
    void parsesAOneDimensionalArray() {
        String src = """
                static const char rcsid[] = "$Id: finesine[3] = {9, 9, 9} $";
                // int finesine[3] = { 7, 7, 7 };
                int finesine[3] =
                {
                    25, /* a comment */ -75,
                    0x7f
                };
                """;
        assertThat(CArrayParser.parse(src, "finesine", new int[] {3}, Integer.MIN_VALUE, Integer.MAX_VALUE))
                .containsExactly(25, -75, 127);
    }

    @Test
    void parsesATwoDimensionalArrayInRowMajorOrder() {
        String src = "const byte gammatable[2][3] = { {1,2,3}, {4,5,6} };";
        assertThat(CArrayParser.parse(src, "gammatable", new int[] {2, 3}, 0, 255)).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void acceptsUnsignedValuesAboveIntMax() {
        String src = "angle_t tantoangle[2] = { 0, 4294967295u };";
        assertThat(CArrayParser.parse(src, "tantoangle", new int[] {2}, 0, 0xffff_ffffL)).containsExactly(0, 0xffff_ffffL);
    }

    @Test
    void rejectsAWrongCount() {
        String src = "int rndtable[3] = { 1, 2 };";
        assertThatThrownBy(() -> CArrayParser.parse(src, "rndtable", new int[] {3}, 0, 255))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("found 2 values, expected 3");
        String more = "int rndtable[2] = { 1, 2, 3 };";
        assertThatThrownBy(() -> CArrayParser.parse(more, "rndtable", new int[] {2}, 0, 255))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than 2");
    }

    @Test
    void rejectsAWrongDimensionOrAMissingArray() {
        String src = "int rndtable[4] = { 1, 2, 3, 4 };";
        assertThatThrownBy(() -> CArrayParser.parse(src, "rndtable", new int[] {3}, 0, 255))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("declared dimension");
        assertThatThrownBy(() -> CArrayParser.parse(src, "finesine", new int[] {4}, 0, 255))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void rejectsValuesOutOfRangeAndExpressions() {
        assertThatThrownBy(() -> CArrayParser.parse("byte t[2] = { 1, 256 };", "t", new int[] {2}, 0, 255))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside");
        assertThatThrownBy(() -> CArrayParser.parse("int t[2] = { 1, FRACUNIT };", "t", new int[] {2}, 0, 255))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unexpected character");
        assertThatThrownBy(() -> CArrayParser.parse("int t[2] = { 1, 2*3 };", "t", new int[] {2}, 0, 255))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unexpected character");
    }

    @Test
    void rejectsADuplicateDefinition() {
        String src = "int t[1] = { 1 };\nint t[1] = { 2 };";
        assertThatThrownBy(() -> CArrayParser.parse(src, "t", new int[] {1}, 0, 255))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than once");
    }

    @Test
    void ignoresExternDeclarations() {
        String src = "extern int t[2];\nint t[2] = { 5, 6 };";
        assertThat(CArrayParser.parse(src, "t", new int[] {2}, 0, 255)).containsExactly(5, 6);
    }
}
