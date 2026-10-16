package doom.it.persist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.game.api.GameState;
import doom.game.persist.SaveGameCodec;
import doom.game.persist.SaveInfo;
import doom.testkit.Iwads;
import doom.testkit.demo.Demo;
import doom.testkit.demo.DemoGenerator;
import doom.testkit.demo.DemoPolicy;
import doom.testkit.demo.DemoSpec;
import doom.wad.LumpDirectory;
import doom.wad.WadFile;
import doom.wad.WadWriter;
import doom.wad.iwad.IwadInfo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.zip.CRC32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Damaged and mismatching save games: the loader reports them to the player and the running game goes on; it never
 * lets an exception out of the tic.
 */
class SaveRobustnessIT {

    private Path path;
    private LumpDirectory lumps;
    private IwadInfo info;
    private Demo demo;

    @BeforeEach
    void setUp() throws IOException {
        Assumptions.check();
        path = Iwads.require(Iwads.FREEDOOM2);
        lumps = SaveHarness.open(path);
        info = SaveHarness.identify(path);
        demo = DemoGenerator.generate(new DemoSpec(2, "MAP01", 1, 0, false, false, false, 1200, 77,
                DemoPolicy.RANDOM_WALK));
    }

    /** The IWAD guard, kept in one place. */
    private static final class Assumptions {
        static void check() {
            assumeTrue(Iwads.directory().isPresent() && Iwads.isAvailable(Iwads.FREEDOOM2),
                    "freedoom2.wad not available");
        }
    }

    private SaveHarness play(int tics) {
        SaveHarness h = new SaveHarness(lumps, info);
        h.newGame(demo);
        for (int t = 0; t < tics; t++) {
            h.tick(demo, t);
        }
        return h;
    }

    private byte[] snapshotAfter(int tics) {
        return play(tics).snapshot();
    }

    private static boolean rejected(SaveHarness h) {
        return h.messages.stream().anyMatch(m -> m.startsWith("Couldn't load game"));
    }

    /** Recomputes the trailing checksum so that a damaged payload reaches the deeper checks. */
    static byte[] fixCrc(byte[] data) {
        if (data.length < 12) {
            return data;
        }
        byte[] out = data.clone();
        CRC32 crc = new CRC32();
        crc.update(out, 0, out.length - 4);
        // the CRC covers every byte before the CRC section (tag + length + value = 12 bytes)
        crc = new CRC32();
        crc.update(out, 0, out.length - 12);
        long v = crc.getValue();
        out[out.length - 4] = (byte) v;
        out[out.length - 3] = (byte) (v >> 8);
        out[out.length - 2] = (byte) (v >> 16);
        out[out.length - 1] = (byte) (v >> 24);
        return out;
    }

    // ------------------------------------------------------------------ corruption

    @Test
    void aDamagedFileLeavesTheRunningGameUntouched() {
        byte[] good = snapshotAfter(300);
        SplittableRandom rnd = new SplittableRandom(4242);
        int rejected = 0;
        for (int i = 0; i < 150; i++) {
            byte[] bad = damage(good, rnd, false);
            if (Arrays.equals(bad, good)) {
                continue; // the damage happened to change nothing (zeroing zeros)
            }
            SaveHarness control = play(200);
            SaveHarness h = play(200);
            h.session.loadGame(bad);
            String lineH = h.tick(demo, 200);
            String lineC = control.tick(demo, 200);
            assertThat(rejected(h)).as("damage %d is reported", i).isTrue();
            rejected++;
            assertThat(SaveHarness.gated(lineH)).as("the game goes on after damage %d", i)
                    .isEqualTo(SaveHarness.gated(lineC));
            assertThat(h.session.view().state()).isEqualTo(GameState.LEVEL);
        }
        assertThat(rejected).isGreaterThan(100);
    }

    @Test
    void damageBehindAValidChecksumNeverEscapesTheLoad() {
        byte[] good = snapshotAfter(300);
        SplittableRandom rnd = new SplittableRandom(99);
        int loaded = 0;
        int refused = 0;
        int crashedLater = 0;
        for (int i = 0; i < 400; i++) {
            byte[] bad = damage(good, rnd, true);
            SaveHarness h = new SaveHarness(lumps, info);
            h.session.loadGame(bad);
            h.tick(demo, 0); // must not throw
            if (rejected(h)) {
                refused++;
                continue;
            }
            loaded++;
            try {
                for (int t = 1; t < 40; t++) {
                    h.tick(demo, t);
                }
            } catch (RuntimeException e) {
                crashedLater++; // absurd but well-formed values (a monster at x = 2^31) can break the simulation
            }
        }
        System.out.println("fuzz with a valid checksum: refused " + refused + ", loaded " + loaded
                + ", simulation failed afterwards " + crashedLater);
        assertThat(refused).as("structurally damaged files are refused").isGreaterThan(0);
    }

    private static byte[] damage(byte[] good, SplittableRandom rnd, boolean fixChecksum) {
        byte[] bad;
        switch (rnd.nextInt(5)) {
            case 0 -> { // truncate
                bad = Arrays.copyOf(good, rnd.nextInt(good.length));
            }
            case 1 -> { // flip a few bytes
                bad = good.clone();
                for (int k = 0; k < 1 + rnd.nextInt(4); k++) {
                    bad[rnd.nextInt(bad.length - 12)] ^= (byte) (1 + rnd.nextInt(255));
                }
            }
            case 2 -> { // zero a range
                bad = good.clone();
                int from = rnd.nextInt(bad.length - 12);
                int to = Math.min(bad.length - 12, from + 1 + rnd.nextInt(200));
                Arrays.fill(bad, from, to, (byte) 0);
            }
            case 3 -> { // overwrite a 32-bit value with an extreme one
                bad = good.clone();
                int at = rnd.nextInt(bad.length - 16);
                int v = rnd.nextBoolean() ? Integer.MAX_VALUE : Integer.MIN_VALUE;
                for (int k = 0; k < 4; k++) {
                    bad[at + k] = (byte) (v >> (8 * k));
                }
            }
            default -> { // drop a chunk in the middle
                int from = rnd.nextInt(good.length - 12);
                int len = 1 + rnd.nextInt(64);
                bad = new byte[good.length - Math.min(len, good.length - 12 - from)];
                System.arraycopy(good, 0, bad, 0, from);
                int rest = good.length - from - Math.min(len, good.length - 12 - from);
                System.arraycopy(good, good.length - rest, bad, from, rest);
            }
        }
        return fixChecksum ? fixCrc(bad) : bad;
    }

    @Test
    void notASaveGameAtAll() {
        for (byte[] junk : new byte[][] {new byte[0], new byte[] {1, 2, 3}, new byte[5000], "DJSV".getBytes()}) {
            SaveHarness h = play(50);
            h.session.loadGame(junk);
            h.tick(demo, 50);
            assertThat(rejected(h)).isTrue();
            assertThat(h.session.view().state()).isEqualTo(GameState.LEVEL);
        }
    }

    // ------------------------------------------------------------------ mismatches

    @Test
    void aSaveOfAnotherGameIsRefusedAndTheGameGoesOn() throws IOException {
        Path doom1 = Iwads.require(Iwads.DOOM1);
        SaveHarness other = new SaveHarness(SaveHarness.open(doom1), SaveHarness.identify(doom1));
        Demo d1 = DemoGenerator.generate(new DemoSpec(2, "E1M1", 1, 0, false, false, false, 300, 5,
                DemoPolicy.RANDOM_WALK));
        other.newGame(d1);
        for (int t = 0; t < 100; t++) {
            other.tick(d1, t);
        }
        byte[] doom1Save = other.snapshot();

        SaveHarness h = play(100);
        h.session.loadGame(doom1Save);
        h.tick(demo, 100);
        assertThat(h.messages).anyMatch(m -> m.startsWith("Couldn't load game") && m.contains("another game"));
        assertThat(h.session.view().state()).isEqualTo(GameState.LEVEL);
        assertThat(h.session.view().map()).isEqualTo(1);
    }

    @Test
    void anotherWadWithTheSameMapNameIsRefusedByTheLevelCheck() throws IOException {
        // FreeDM and Freedoom 2 are both commercial games with a MAP01, but the maps are different
        assumeTrue(Iwads.isAvailable(Iwads.FREEDM), "freedm.wad not available");
        Path dm = Iwads.require(Iwads.FREEDM);
        SaveHarness other = new SaveHarness(SaveHarness.open(dm), SaveHarness.identify(dm));
        Demo d = DemoGenerator.generate(new DemoSpec(2, "MAP01", 2, 1, false, false, false, 300, 5,
                DemoPolicy.RANDOM_WALK));
        other.newGame(d);
        for (int t = 0; t < 100; t++) {
            other.tick(d, t);
        }
        byte[] dmSave = other.snapshot();

        SaveHarness h = play(100);
        h.session.loadGame(dmSave);
        h.tick(demo, 100);
        assertThat(rejected(h)).isTrue();
        assertThat(h.session.view().state()).isEqualTo(GameState.LEVEL);
    }

    @Test
    void differentWadsWarnButLoad() throws IOException {
        byte[] save = snapshotAfter(300);
        // the same IWAD plus a PWAD that does not touch the map
        WadFile pwad = WadWriter.pwad().add("DUMMY", new byte[] {1, 2, 3}).toWadFile("extra.wad");
        LumpDirectory withPwad = LumpDirectory.of(List.of(WadFile.open(path), pwad));
        SaveHarness h = new SaveHarness(withPwad, info);
        h.session.loadGame(save);
        h.tick(demo, 300);
        assertThat(h.messages).anyMatch(m -> m.contains("WARNING") && m.contains("WADs"));
        assertThat(rejected(h)).isFalse();
        assertThat(h.session.view().state()).isEqualTo(GameState.LEVEL);
        assertThat(h.session.view().map()).isEqualTo(1);

        // the same WADs do not warn
        SaveHarness same = new SaveHarness(lumps, info);
        same.session.loadGame(save);
        same.tick(demo, 300);
        assertThat(same.messages).noneMatch(m -> m.contains("WARNING"));
    }

    // ------------------------------------------------------------------ header and flow

    @Test
    void theHeaderDescribesTheSave() {
        SaveHarness h = play(120);
        long start = System.nanoTime();
        byte[] data = h.session.saveHandler().save(3, "a description that is much longer than twenty-four characters");
        long first = System.nanoTime() - start;
        start = System.nanoTime();
        h.session.saveHandler().save(3, "again");
        System.out.printf("first save %.1f ms (hashes the WADs), next %.2f ms, %d bytes%n", first / 1e6,
                (System.nanoTime() - start) / 1e6, data.length);
        SaveInfo i = SaveGameCodec.inspect(data);
        assertThat(i.description()).hasSize(24).startsWith("a description that is mu");
        assertThat(i.gameMode()).isEqualTo("COMMERCIAL");
        assertThat(i.gameVersion()).isEqualTo(info.version().cliName());
        assertThat(i.wads()).hasSize(1);
        assertThat(i.wads().get(0)).isEqualToIgnoringCase("freedoom2.wad");
        assertThat(i.wadHashes().get(0)).hasSize(64).matches("[0-9a-f]+");
        assertThat(i.timestamp()).isGreaterThan(1_700_000_000_000L);
        assertThat(i.formatVersion()).isEqualTo(1);
    }

    @Test
    void savingOutsideALevelDoesNothing() {
        SaveHarness h = new SaveHarness(lumps, info);
        h.session.startTitle();
        h.session.requestSave(0, "title");
        for (int t = 0; t < 40; t++) {
            h.tick(demo, t);
        }
        assertThat(h.saved).isEmpty();
        assertThat(h.session.view().state()).isEqualTo(GameState.DEMOSCREEN);
        assertThatSnapshotIsRefused(h);
    }

    private static void assertThatSnapshotIsRefused(SaveHarness h) {
        try {
            h.snapshot();
        } catch (doom.game.persist.SaveGameException e) {
            assertThat(e.getMessage()).contains("no game in progress");
            return;
        }
        throw new AssertionError("a snapshot outside a level must be refused");
    }

    @Test
    void aSaveLoadsFromAnyStateAndAfterANewLevel() {
        byte[] save = snapshotAfter(250);
        SaveHarness a = new SaveHarness(lumps, info);
        // from the title loop
        a.session.startTitle();
        for (int t = 0; t < 30; t++) {
            a.tick(demo, t);
        }
        a.session.loadGame(save);
        a.tick(demo, 250);
        assertThat(a.session.view().state()).isEqualTo(GameState.LEVEL);
        assertThat(a.session.view().map()).isEqualTo(1);

        // from another map and from the intermission
        SaveHarness b = new SaveHarness(lumps, info);
        Demo other = DemoGenerator.generate(new DemoSpec(3, "MAP05", 1, 0, false, false, false, 400, 8,
                DemoPolicy.RANDOM_WALK));
        b.newGame(other);
        for (int t = 0; t < 100; t++) {
            b.tick(other, t);
        }
        assertThat(b.session.view().map()).isEqualTo(5);
        b.session.loadGame(save);
        b.tick(demo, 250);
        assertThat(b.session.view().map()).isEqualTo(1);
        b.session.flow().exitLevel();
        for (int t = 251; t < 300 && b.session.view().state() != GameState.INTERMISSION; t++) {
            b.tick(demo, t);
        }
        assertThat(b.session.view().state()).isEqualTo(GameState.INTERMISSION);
        b.session.loadGame(save);
        b.tick(demo, 250);
        assertThat(b.session.view().state()).isEqualTo(GameState.LEVEL);
        assertThat(b.session.view().map()).isEqualTo(1);
        assertThat(b.messages).noneMatch(m -> m.startsWith("Couldn't"));
    }

    @Test
    void anImmediateLoadGivesTheSameStateBack() {
        SaveHarness a = play(450);
        byte[] snapshot = a.snapshot();
        SaveHarness c = new SaveHarness(lumps, info);
        c.session.saveHandler().load(snapshot);
        assertThat(SaveHarness.payload(c.snapshot())).isEqualTo(SaveHarness.payload(snapshot));
        // and the loaded session keeps going exactly like the original
        for (int t = 450; t < 650; t++) {
            assertThat(SaveHarness.gated(c.tick(demo, t))).isEqualTo(SaveHarness.gated(a.tick(demo, t)));
        }
    }
}
