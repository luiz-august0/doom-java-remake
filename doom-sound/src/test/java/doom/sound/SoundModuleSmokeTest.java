package doom.sound;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SoundModuleSmokeTest {

    @Test
    void moduleIsWired() {
        assertThat(getClass().getModule().getName()).isEqualTo("doom.sound");
    }
}
