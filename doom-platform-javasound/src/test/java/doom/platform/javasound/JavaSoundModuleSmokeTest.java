package doom.platform.javasound;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JavaSoundModuleSmokeTest {

    @Test
    void moduleIsWired() {
        assertThat(new JavaSoundAudioProvider().name()).isEqualTo("javasound");
    }
}
