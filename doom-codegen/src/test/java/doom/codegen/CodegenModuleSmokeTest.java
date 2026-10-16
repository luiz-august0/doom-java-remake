package doom.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CodegenModuleSmokeTest {

    @Test
    void moduleIsWired() {
        assertThat(getClass().getModule().getName()).isEqualTo("doom.codegen");
    }
}
