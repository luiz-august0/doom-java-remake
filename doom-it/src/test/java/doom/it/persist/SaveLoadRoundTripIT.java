package doom.it.persist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.testkit.Iwads;
import doom.testkit.demo.Demo;
import doom.testkit.demo.DemoGenerator;
import doom.testkit.demo.DemoPolicy;
import doom.testkit.demo.DemoSpec;
import doom.wad.LumpDirectory;
import doom.wad.iwad.IwadInfo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * W4-P gate: for every map of freedoom1, freedoom2 and doom1, a synthetic demo (random walk with fire, use and weapon
 * changes, monsters on) is played in a session, saved through the real path ({@code requestSave}, the snapshot arrives
 * through {@code gameSaved} at the top of the next tic), loaded into a fresh session, and continued with the same
 * commands. Every trace line from the load tic on must equal the uninterrupted run, and the snapshots taken later by
 * both sessions must be identical byte for byte (nothing the format captures may differ).
 *
 * <p>{@code -Dpersist.filter=<regex>} selects maps by {@code iwad:map} name, {@code -Dpersist.save=<tic>} and
 * {@code -Dpersist.after=<tics>} change the save point and the number of tics continued.
 */
class SaveLoadRoundTripIT {

    private static final String[] IWADS = {Iwads.DOOM1, Iwads.FREEDOOM1, Iwads.FREEDOOM2};

    private static int property(String name, int fallback) {
        return Integer.getInteger(name, fallback);
    }

    @TestFactory
    Stream<DynamicTest> everyMapSingleHardPlayer() throws IOException {
        Optional<Path> dir = Iwads.directory();
        assumeTrue(dir.isPresent(), "DOOM_IWAD_DIR not set");
        String filter = System.getProperty("persist.filter", ".*");
        int[] savePoints = {property("persist.save", 350), property("persist.save2", 1100)};
        int after = property("persist.after", 300);
        List<DynamicTest> tests = new ArrayList<>();
        for (String iwad : IWADS) {
            if (!Iwads.isAvailable(iwad)) {
                continue;
            }
            Path path = Iwads.require(iwad);
            LumpDirectory lumps = SaveHarness.open(path);
            IwadInfo info = SaveHarness.identify(path);
            for (String map : mapsOf(lumps)) {
                String name = iwad + ":" + map;
                if (!name.matches(filter)) {
                    continue;
                }
                for (int saveAt : savePoints) {
                    long seed = 0x5a7e6a3eL + name.hashCode() + saveAt;
                    DemoSpec spec = new DemoSpec(2, map, 1, 0, false, false, false, saveAt + after + 60, seed,
                            DemoPolicy.RANDOM_WALK);
                    tests.add(DynamicTest.dynamicTest(name + " saved at tic " + saveAt,
                            () -> roundTrip(lumps, info, spec, saveAt, after)));
                }
            }
        }
        return tests.stream();
    }

    @TestFactory
    Stream<DynamicTest> multiplayerAndSkillVariants() throws IOException {
        Optional<Path> dir = Iwads.directory();
        assumeTrue(dir.isPresent(), "DOOM_IWAD_DIR not set");
        int saveAt = property("persist.save", 500);
        int after = property("persist.after", 400);
        // {iwad, map, skill, players, deathmatch, respawn, fast}
        Object[][] variants = {
            {Iwads.FREEDOOM2, "MAP01", 3, 4, 0, false, false},
            {Iwads.FREEDOOM2, "MAP10", 3, 2, 0, false, false},
            {Iwads.FREEDOOM2, "MAP03", 4, 1, 0, true, true},
            {Iwads.FREEDOOM2, "MAP07", 1, 3, 2, false, false},
            {Iwads.FREEDOOM2, "MAP23", 0, 2, 0, false, false},
            {Iwads.FREEDOOM2, "MAP30", 3, 1, 0, false, false},
            {Iwads.FREEDOOM1, "E4M6", 3, 3, 0, false, false},
            {Iwads.FREEDOOM1, "E2M4", 4, 3, 0, false, true},
            {Iwads.FREEDOOM1, "E3M8", 3, 1, 0, false, false},
            {Iwads.DOOM1, "E1M5", 3, 1, 0, false, false},
            {Iwads.FREEDM, "MAP01", 3, 4, 1, false, false},
            {Iwads.FREEDM, "MAP05", 2, 3, 2, false, false},
        };
        List<DynamicTest> tests = new ArrayList<>();
        for (Object[] v : variants) {
            String iwad = (String) v[0];
            if (!Iwads.isAvailable(iwad)) {
                continue;
            }
            Path path = Iwads.require(iwad);
            LumpDirectory lumps = SaveHarness.open(path);
            String map = (String) v[1];
            if (lumps.find(map) < 0) {
                continue;
            }
            IwadInfo info = SaveHarness.identify(path);
            String name = iwad + ":" + map + " skill " + v[2] + " players " + v[3] + " dm " + v[4]
                    + (((Boolean) v[5]) ? " respawn" : "") + (((Boolean) v[6]) ? " fast" : "");
            DemoSpec spec = new DemoSpec((Integer) v[2], map, (Integer) v[3], (Integer) v[4], (Boolean) v[5],
                    (Boolean) v[6], false, saveAt + after + 60, 0x7a11L + name.hashCode(), DemoPolicy.RANDOM_WALK);
            tests.add(DynamicTest.dynamicTest(name, () -> roundTrip(lumps, info, spec, saveAt, after)));
        }
        return tests.stream();
    }

    /** The map lumps of the WAD that the game can play: {@code ExMy} or {@code MAPnn}. */
    static List<String> mapsOf(LumpDirectory lumps) {
        List<String> maps = new ArrayList<>();
        for (int e = 1; e <= 4; e++) {
            for (int m = 1; m <= 9; m++) {
                String name = "E" + e + "M" + m;
                if (lumps.find(name) >= 0) {
                    maps.add(name);
                }
            }
        }
        for (int m = 1; m <= 32; m++) {
            String name = m < 10 ? "MAP0" + m : "MAP" + m;
            if (lumps.find(name) >= 0) {
                maps.add(name);
            }
        }
        return maps;
    }

    private static void roundTrip(LumpDirectory lumps, IwadInfo info, DemoSpec spec, int saveAt, int after) {
        Demo demo = DemoGenerator.generate(spec);
        int total = demo.tics();

        // the uninterrupted run, which saves once
        SaveHarness a = new SaveHarness(lumps, info);
        a.newGame(demo);
        String[] lineA = new String[total];
        int attempt = saveAt;
        int loadTic = -1;
        int last = -1;
        for (int t = 0; t < total; t++) {
            if (t == attempt) {
                a.session.requestSave(0, "round trip");
            }
            lineA[t] = a.tick(demo, t);
            last = t;
            if (loadTic < 0 && !a.saved.isEmpty()) {
                loadTic = a.savedAt.get(0);
            } else if (loadTic < 0 && t == attempt + 1) {
                attempt += 25; // the save was overridden by a level reload in that tic: ask again later
            }
            if (loadTic >= 0 && t == loadTic + after) {
                break;
            }
        }
        assertThat(loadTic).as("the save game was taken").isGreaterThan(0);
        assertThat(last).as("the run reached the end of the comparison").isEqualTo(loadTic + after);
        byte[] snapshot = a.saved.get(0);
        byte[] finalA = SaveHarness.payload(a.snapshot());

        // a fresh session loads it and continues with the same commands
        SaveHarness b = new SaveHarness(lumps, info);
        b.session.loadGame(snapshot);
        for (int t = loadTic; t <= loadTic + after; t++) {
            String lineB = b.tick(demo, t);
            assertThat(SaveHarness.gated(lineB)).as("trace line of tic %d (%d tics after the load)", t, t - loadTic)
                    .isEqualTo(SaveHarness.gated(lineA[t]));
            assertThat(b.messages).as("messages of the loaded session").noneMatch(m -> m.contains("Couldn't"));
        }
        // loading outside a tic and saving again gives the same bytes back
        SaveHarness c = new SaveHarness(lumps, info);
        c.session.saveHandler().load(snapshot);
        assertThat(SaveHarness.payload(c.snapshot())).as("save after load").isEqualTo(SaveHarness.payload(snapshot));

        // the state both sessions hold now must serialize identically
        assertThat(SaveHarness.payload(b.snapshot())).as("snapshot after continuing").isEqualTo(finalA);
    }
}
