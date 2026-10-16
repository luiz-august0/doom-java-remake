package doom.testkit.demo;

import java.util.SplittableRandom;

/**
 * Generates synthetic version-109 demos. The output depends only on the {@link DemoSpec} (seeded
 * {@link SplittableRandom}, one stream per player), so a manifest line regenerates a demo byte for byte.
 *
 * <p>The commands stay within what a keyboard player produces in vanilla DOOM: forward 0/+-25/+-50, side
 * 0/+-24/+-40, and turn speeds that fit the demo's one-byte {@code angleturn >> 8} (320, 640 and 1280 are stored as
 * 1, 3 and 5), so the demo holds exactly what the game reads back. Intent is held for 5..45 tics so the player
 * actually travels, with fire bursts, use pulses and weapon changes in {@link DemoPolicy#RANDOM_WALK} and nothing but
 * movement in {@link DemoPolicy#MOVEMENT_ONLY}.
 */
public final class DemoGenerator {

    private static final int[] FORWARD = {50, 25, 0, -25, -50};
    private static final int[] FORWARD_WEIGHT = {55, 15, 10, 8, 12};
    private static final int[] SIDE = {0, 24, -24, 40, -40};
    private static final int[] SIDE_WEIGHT = {62, 8, 8, 11, 11};
    private static final int[] TURN = {0, 1, -1, 3, -3, 5, -5};
    private static final int[] TURN_WEIGHT = {32, 10, 10, 15, 15, 9, 9};

    private DemoGenerator() {
    }

    /** Generates the demo of {@code spec}. */
    public static Demo generate(DemoSpec spec) {
        Demo.Header header = spec.header();
        SplittableRandom root = new SplittableRandom(spec.seed());
        Walker[] walkers = new Walker[spec.players()];
        for (int i = 0; i < walkers.length; i++) {
            walkers[i] = new Walker(root.split(), spec.policy() == DemoPolicy.RANDOM_WALK);
        }
        Demo.Cmd[][] cmds = new Demo.Cmd[spec.tics()][spec.players()];
        for (int t = 0; t < spec.tics(); t++) {
            for (int p = 0; p < walkers.length; p++) {
                cmds[t][p] = walkers[p].next();
            }
        }
        return new Demo(header, cmds);
    }

    /** One player's state machine. */
    private static final class Walker {
        private final SplittableRandom rnd;
        private final boolean buttons;
        private int forward;
        private int side;
        private int turn;
        private int moveLeft;
        private int fireLeft;
        private int useLeft;

        Walker(SplittableRandom rnd, boolean buttons) {
            this.rnd = rnd;
            this.buttons = buttons;
        }

        Demo.Cmd next() {
            if (moveLeft <= 0) {
                forward = pick(FORWARD, FORWARD_WEIGHT);
                side = pick(SIDE, SIDE_WEIGHT);
                turn = pick(TURN, TURN_WEIGHT);
                moveLeft = 5 + rnd.nextInt(41);
            }
            moveLeft--;
            int bits = 0;
            if (buttons) {
                if (fireLeft > 0) {
                    fireLeft--;
                    bits |= Demo.BT_ATTACK;
                } else if (rnd.nextInt(30) == 0) {
                    fireLeft = 2 + rnd.nextInt(38);
                    bits |= Demo.BT_ATTACK;
                }
                if (useLeft > 0) {
                    useLeft--;
                    bits |= Demo.BT_USE;
                } else if (rnd.nextInt(45) == 0) {
                    useLeft = rnd.nextInt(4);
                    bits |= Demo.BT_USE;
                }
                if (rnd.nextInt(120) == 0) {
                    bits |= Demo.BT_CHANGE | (rnd.nextInt(8) << Demo.BT_WEAPONSHIFT);
                }
            }
            return new Demo.Cmd(forward, side, turn, bits);
        }

        private int pick(int[] values, int[] weights) {
            int total = 0;
            for (int w : weights) {
                total += w;
            }
            int r = rnd.nextInt(total);
            for (int i = 0; i < values.length; i++) {
                r -= weights[i];
                if (r < 0) {
                    return values[i];
                }
            }
            return values[values.length - 1];
        }
    }
}
