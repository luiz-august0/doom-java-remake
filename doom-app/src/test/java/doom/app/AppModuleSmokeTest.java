package doom.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AppModuleSmokeTest {

    @Test
    void moduleIsWired() {
        assertThat(new ConsoleLoggerFinder().getLogger("x", getClass().getModule()).getName()).isEqualTo("x");
    }
}
