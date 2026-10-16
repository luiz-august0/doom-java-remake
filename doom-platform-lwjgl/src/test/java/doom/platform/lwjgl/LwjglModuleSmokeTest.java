package doom.platform.lwjgl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LwjglModuleSmokeTest {

    @Test
    void moduleIsWired() {
        assertThat(new LwjglPlatformProvider().name()).isEqualTo("lwjgl");
    }
}
