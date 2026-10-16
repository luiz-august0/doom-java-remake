package doom.lint;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LintModuleSmokeTest {

    @Test
    void moduleIsWired() {
        assertThat(new BamLintPlugin().getName()).isEqualTo("BamLint");
    }
}
