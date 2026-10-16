package doom.it.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.testkit.Iwads;
import doom.testkit.oracle.OracleRunner;
import doom.tools.frame.GoldenFrames;
import doom.tools.frame.SceneSidecar;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The 3D renderer against the oracle: every cached scene sidecar is rendered at 320x200 in vanilla mode and the view
 * window ({@code window} record) must equal the oracle's raw frame byte for byte.
 *
 * <p>Justified exception (documented in docs/divergences/W2-B.md): the oracle frame is the whole screen, so the HUD
 * message line ("Picked up a clip.") is drawn over the top rows of the view window by the 2D layer. Differences inside
 * the first {@link GoldenFrames#HUD_MESSAGE_ROWS} rows of the window are ignored; everything below must be identical.
 * Mismatches write a side-by-side PNG (oracle | ours | difference) to {@code target/golden-frames}.
 */
class GoldenFrameIT {

    private static final Path OUT = Path.of("target/golden-frames");

    @Test
    void viewWindowsAreIdenticalToTheOracle() throws IOException {
        Iwads.assumeDirectory();
        Path frames = OracleRunner.cacheDirectory().resolve("frames");
        assumeTrue(Files.isDirectory(frames), "no oracle frame cache at " + frames);
        List<String> demos = new ArrayList<>();
        try (Stream<Path> s = Files.list(frames)) {
            s.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().forEach(demos::add);
        }
        assumeTrue(!demos.isEmpty(), "no frames in the cache");

        List<String> failures = new ArrayList<>();
        int total = 0;
        for (String demo : demos) {
            List<Path> scenes = new ArrayList<>();
            try (Stream<Path> s = Files.list(frames.resolve(demo))) {
                s.filter(p -> p.getFileName().toString().endsWith(".scene")).sorted().forEach(scenes::add);
            }
            if (scenes.isEmpty()) {
                continue;
            }
            SceneSidecar first = SceneSidecar.read(scenes.get(0));
            if (!Iwads.isAvailable(first.iwad)) {
                continue;
            }
            GoldenFrames gf = new GoldenFrames(Iwads.require(first.iwad));
            int identical = 0;
            int hudOnly = 0;
            for (Path scene : scenes) {
                GoldenFrames.Result r = gf.compare(scene, OUT);
                total++;
                if (r.identical()) {
                    identical++;
                } else if (r.identicalExceptHud()) {
                    hudOnly++;
                } else {
                    failures.add(r.name() + ": " + r.differingBelowHud() + " px differ below the HUD rows, first at ("
                            + r.firstX() + "," + r.firstY() + ") " + r.png());
                }
            }
            System.out.printf("GoldenFrameIT %s: %d frames, %d identical, %d identical except HUD message rows%n",
                    demo, scenes.size(), identical, hudOnly);
        }
        assertThat(total).isGreaterThan(0);
        assertThat(failures).as("frames with differences in the view window").isEmpty();
    }
}
