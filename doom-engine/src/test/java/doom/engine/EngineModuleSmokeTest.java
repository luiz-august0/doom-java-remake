package doom.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EngineModuleSmokeTest {

    @Test
    void moduleIsWired() {
        assertThat(getClass().getModule().getName()).isEqualTo("doom.engine");
    }
}
