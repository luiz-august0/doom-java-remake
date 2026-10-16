package doom.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MainSmokeTest {

    @Test
    void everyToolHasAUniqueName() {
        assertThat(Main.tools()).extracting(Tool::name).doesNotHaveDuplicates().contains("wad-dump", "trace-diff");
    }
}
