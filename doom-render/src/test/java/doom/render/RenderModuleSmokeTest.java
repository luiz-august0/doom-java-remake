package doom.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RenderModuleSmokeTest {

    @Test
    void moduleIsWired() {
        assertThat(getClass().getModule().getName()).isEqualTo("doom.render");
    }
}
