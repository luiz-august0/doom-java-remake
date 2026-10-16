package doom.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.wad.LumpDirectory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Smoke integration test: the shareware IWAD opens and contains E1M1. Skipped without DOOM_IWAD_DIR. */
class IwadDirectoryIT {

    @Test
    void sharewareIwadOpens() throws Exception {
        String dir = System.getenv("DOOM_IWAD_DIR");
        assumeTrue(dir != null, "DOOM_IWAD_DIR not set");
        Path wad = Path.of(dir, "doom1.wad");
        assumeTrue(Files.isRegularFile(wad), "doom1.wad not found");
        LumpDirectory lumps = LumpDirectory.open(List.of(wad));
        assertThat(lumps.find("E1M1")).isGreaterThanOrEqualTo(0);
        assertThat(lumps.find("E2M1")).isEqualTo(-1);
        assertThat(lumps.length(lumps.get("PLAYPAL"))).isEqualTo(14 * 768);
    }
}
