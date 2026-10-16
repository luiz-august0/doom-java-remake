package doom.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TestkitModuleSmokeTest {

    @Test
    void moduleIsWired() {
        assertThat(getClass().getModule().getName()).isEqualTo("doom.testkit");
    }
}
