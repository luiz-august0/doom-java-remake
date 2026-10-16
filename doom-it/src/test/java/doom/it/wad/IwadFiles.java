package doom.it.wad;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.wad.LumpDirectory;
import doom.wad.WadFile;
import doom.wad.iwad.IwadIdentifier;
import doom.wad.iwad.IwadInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Locates the test IWADs through {@code DOOM_IWAD_DIR}; tests are skipped when a file is missing. */
final class IwadFiles {

    /** The IWADs of the test set. */
    static final List<String> NAMES = List.of("doom1.wad", "freedoom1.wad", "freedoom2.wad", "freedm.wad");

    private IwadFiles() {
    }

    /** Path of the IWAD, or skips the calling test. */
    static Path require(String name) {
        String dir = System.getenv("DOOM_IWAD_DIR");
        assumeTrue(dir != null && !dir.isEmpty(), "DOOM_IWAD_DIR not set");
        Path p = Path.of(dir, name);
        assumeTrue(Files.isRegularFile(p), name + " not found in " + dir);
        return p;
    }

    static LumpDirectory open(String name) throws IOException {
        return LumpDirectory.open(List.of(require(name)));
    }

    static IwadInfo identify(String name) throws IOException {
        return IwadIdentifier.identify(WadFile.open(require(name)));
    }
}
