package doom.testkit.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DemoGeneratorTest {

    private static DemoSpec spec(int players, DemoPolicy policy, long seed, int tics) {
        return new DemoSpec(2, "E1M1", players, 0, false, false, false, tics, seed, policy);
    }

    @Test
    void headerBytesFollowTheVanillaLayout() {
        DemoSpec s = new DemoSpec(3, "MAP07", 3, 1, true, true, true, 10, 1, DemoPolicy.RANDOM_WALK);
        byte[] b = DemoGenerator.generate(s).toBytes();
        assertThat(b).hasSize(13 + 10 * 3 * 4 + 1);
        assertThat(Arrays.copyOf(b, 13)).containsExactly(109, 3, 1, 7, 1, 1, 1, 1, 0, 1, 1, 1, 0);
        assertThat(b[b.length - 1] & 0xff).isEqualTo(0x80);

        DemoSpec e = new DemoSpec(0, "E4M9", 1, 0, false, false, false, 5, 1, DemoPolicy.MOVEMENT_ONLY);
        byte[] eb = DemoGenerator.generate(e).toBytes();
        assertThat(Arrays.copyOf(eb, 13)).containsExactly(109, 0, 4, 9, 0, 0, 0, 0, 0, 1, 0, 0, 0);
    }

    @Test
    void sameSpecGivesSameBytesAndSeedChangesThem() {
        byte[] a = DemoGenerator.generate(spec(2, DemoPolicy.RANDOM_WALK, 99, 800)).toBytes();
        byte[] b = DemoGenerator.generate(spec(2, DemoPolicy.RANDOM_WALK, 99, 800)).toBytes();
        byte[] c = DemoGenerator.generate(spec(2, DemoPolicy.RANDOM_WALK, 100, 800)).toBytes();
        assertThat(a).isEqualTo(b).isNotEqualTo(c);
    }

    /** Pins the generator: a change in the walk changes every checked-in demo, so it must be deliberate. */
    @Test
    void generatorOutputIsPinned() throws Exception {
        byte[] bytes = DemoGenerator.generate(spec(2, DemoPolicy.RANDOM_WALK, 12345, 1000)).toBytes();
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        assertThat(sha).isEqualTo(PINNED_SHA256);
    }

    private static final String PINNED_SHA256 = "8a92e4d4ef638469f8d94f23093746292f108eb3fd62eca32ab7028c463153c9";

    @Test
    void roundTripsThroughTheParser() {
        for (int players = 1; players <= 4; players++) {
            for (DemoPolicy policy : DemoPolicy.values()) {
                Demo demo = DemoGenerator.generate(spec(players, policy, 7L * players, 300));
                Demo parsed = Demo.parse(demo.toBytes());
                assertThat(parsed.header()).isEqualTo(demo.header());
                assertThat(parsed.tics()).isEqualTo(300);
                assertThat(parsed.header().playerCount()).isEqualTo(players);
                assertThat(parsed.cmds()).isDeepEqualTo(demo.cmds());
            }
        }
    }

    @Test
    void commandsStayWithinVanillaLimits() {
        Demo demo = DemoGenerator.generate(spec(4, DemoPolicy.RANDOM_WALK, 5, 3000));
        Set<Integer> forward = Set.of(50, 25, 0, -25, -50);
        Set<Integer> side = Set.of(0, 24, -24, 40, -40);
        int fires = 0;
        int uses = 0;
        int changes = 0;
        for (Demo.Cmd[] tic : demo.cmds()) {
            for (Demo.Cmd c : tic) {
                assertThat(forward).contains(c.forward());
                assertThat(side).contains(c.side());
                assertThat(Math.abs(c.turn())).isLessThanOrEqualTo(5);
                assertThat(c.buttons() & Demo.BT_SPECIAL).isZero();
                assertThat(c.angleTurn()).isEqualTo(c.turn() * 256);
                fires += c.attack() ? 1 : 0;
                uses += c.use() ? 1 : 0;
                if (c.weaponChange() >= 0) {
                    changes++;
                    assertThat(c.weaponChange()).isBetween(0, 7);
                }
            }
        }
        assertThat(fires).as("fire tics").isGreaterThan(500);
        assertThat(uses).as("use tics").isGreaterThan(100);
        assertThat(changes).as("weapon changes").isGreaterThan(20);
    }

    @Test
    void movementOnlyNeverPressesButtons() {
        Demo demo = DemoGenerator.generate(spec(3, DemoPolicy.MOVEMENT_ONLY, 8, 2000));
        int moving = 0;
        for (Demo.Cmd[] tic : demo.cmds()) {
            for (Demo.Cmd c : tic) {
                assertThat(c.buttons()).isZero();
                moving += c.forward() != 0 ? 1 : 0;
            }
        }
        assertThat(moving).isGreaterThan(3000);
    }

    @Test
    void playersWalkIndependently() {
        Demo demo = DemoGenerator.generate(spec(2, DemoPolicy.RANDOM_WALK, 3, 400));
        boolean differ = false;
        for (Demo.Cmd[] tic : demo.cmds()) {
            differ |= !tic[0].equals(tic[1]);
        }
        assertThat(differ).isTrue();
    }

    @Test
    void firstByteOfACommandIsNeverTheEndMarker() {
        // forward -128 would read as 0x80 and end the demo; the walk never produces it
        byte[] b = DemoGenerator.generate(spec(4, DemoPolicy.RANDOM_WALK, 1, 3000)).toBytes();
        assertThat(Demo.parse(b).tics()).isEqualTo(3000);
    }

    @Test
    void parserRejectsBadDemos() {
        byte[] good = DemoGenerator.generate(spec(1, DemoPolicy.RANDOM_WALK, 1, 20)).toBytes();
        assertThatThrownBy(() -> Demo.parse(Arrays.copyOf(good, good.length - 1))).isInstanceOf(IllegalArgumentException.class);
        byte[] v = good.clone();
        v[0] = 104;
        assertThatThrownBy(() -> Demo.parse(v)).hasMessageContaining("version");
        assertThatThrownBy(() -> Demo.parse(new byte[5])).isInstanceOf(IllegalArgumentException.class);
        byte[] none = good.clone();
        Arrays.fill(none, 9, 13, (byte) 0);
        assertThatThrownBy(() -> Demo.parse(none)).hasMessageContaining("no players");
    }

    @Test
    void specValidation() {
        assertThatThrownBy(() -> new DemoSpec(5, "E1M1", 1, 0, false, false, false, 10, 0, DemoPolicy.RANDOM_WALK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DemoSpec(1, "E1M1", 5, 0, false, false, false, 10, 0, DemoPolicy.RANDOM_WALK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DemoSpec(1, "level1", 1, 0, false, false, false, 10, 0, DemoPolicy.RANDOM_WALK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(DemoSpec.episodeOf("map12")).isEqualTo(1);
        assertThat(DemoSpec.mapOf("map12")).isEqualTo(12);
        assertThat(DemoSpec.episodeOf("e3m6")).isEqualTo(3);
        assertThat(DemoSpec.mapOf("e3m6")).isEqualTo(6);
    }
}
