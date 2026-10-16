package doom.render.wipe;

import static org.assertj.core.api.Assertions.assertThat;

import doom.render.FrameBuffer;
import doom.render.FrameLayout;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WipeTest {

    private static final int W = 320;
    private static final int H = 200;

    /** Straight transcription of f_wipe.c (16-bit "dpixel" strips, column-major start/end). */
    private static final class Reference {
        final short[] scr = new short[W / 2 * H];
        final short[] start = new short[W / 2 * H];
        final short[] end = new short[W / 2 * H];
        final int[] y = new int[W];

        Reference(byte[] from, byte[] to, java.util.function.IntSupplier rnd) {
            for (int r = 0; r < H; r++) {
                for (int i = 0; i < W / 2; i++) {
                    scr[r * (W / 2) + i] = pair(from, r, i);
                    start[i * H + r] = pair(from, r, i);
                    end[i * H + r] = pair(to, r, i);
                }
            }
            y[0] = -(rnd.getAsInt() % 16);
            for (int i = 1; i < W; i++) {
                int r = (rnd.getAsInt() % 3) - 1;
                y[i] = y[i - 1] + r;
                if (y[i] > 0) {
                    y[i] = 0;
                } else if (y[i] == -16) {
                    y[i] = -15;
                }
            }
        }

        static short pair(byte[] a, int r, int i) {
            return (short) (((a[r * W + 2 * i] & 0xFF) << 8) | (a[r * W + 2 * i + 1] & 0xFF));
        }

        boolean doMelt(int ticks) {
            boolean done = true;
            int width = W / 2;
            while (ticks-- > 0) {
                for (int i = 0; i < width; i++) {
                    if (y[i] < 0) {
                        y[i]++;
                        done = false;
                    } else if (y[i] < H) {
                        int dy = (y[i] < 16) ? y[i] + 1 : 8;
                        if (y[i] + dy >= H) {
                            dy = H - y[i];
                        }
                        int s = i * H + y[i];
                        int d = y[i] * width + i;
                        int idx = 0;
                        for (int j = dy; j != 0; j--) {
                            scr[d + idx] = end[s++];
                            idx += width;
                        }
                        y[i] += dy;
                        s = i * H;
                        d = y[i] * width + i;
                        idx = 0;
                        for (int j = H - y[i]; j != 0; j--) {
                            scr[d + idx] = start[s++];
                            idx += width;
                        }
                        done = false;
                    }
                }
            }
            return done;
        }

        byte pixel(int x, int r) {
            short p = scr[r * (W / 2) + x / 2];
            return (byte) ((x & 1) == 0 ? p >> 8 : p);
        }
    }

    private static FrameBuffer random(Random rnd, FrameLayout layout) {
        FrameBuffer fb = new FrameBuffer(W, H, layout);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                fb.set(x, y, rnd.nextInt(256));
            }
        }
        return fb;
    }

    private static byte[] rowMajor(FrameBuffer fb) {
        byte[] a = new byte[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                a[y * W + x] = (byte) fb.get(x, y);
            }
        }
        return a;
    }

    @Test
    void matchesTheOriginalMeltTickByTick() {
        for (FrameLayout layout : FrameLayout.values()) {
            Random rnd = new Random(42);
            FrameBuffer from = random(rnd, layout);
            FrameBuffer to = random(rnd, layout);
            Random seq1 = new Random(7);
            Random seq2 = new Random(7);
            Wipe wipe = new Wipe(() -> seq1.nextInt(256));
            Reference ref = new Reference(rowMajor(from), rowMajor(to), () -> seq2.nextInt(256));
            wipe.start(from, to);
            FrameBuffer out = new FrameBuffer(W, H, layout);
            int[] ticks = {1, 1, 2, 1, 3, 1, 1, 2, 5, 1};
            boolean done = false;
            int n = 0;
            while (!done && n < 400) {
                int t = ticks[n % ticks.length];
                done = wipe.tick(t, out);
                boolean refDone = ref.doMelt(t);
                assertThat(done).as("done after call %d", n).isEqualTo(refDone);
                for (int y = 0; y < H; y++) {
                    for (int x = 0; x < W; x++) {
                        assertThat((byte) out.get(x, y)).as("pixel %d,%d after call %d", x, y, n)
                                .isEqualTo(ref.pixel(x, y));
                    }
                }
                n++;
            }
            assertThat(done).isTrue();
            assertThat(wipe.active()).isFalse();
            // finished: the new screen
            for (int y = 0; y < H; y += 7) {
                for (int x = 0; x < W; x += 5) {
                    assertThat(out.get(x, y)).isEqualTo(to.get(x, y));
                }
            }
        }
    }

    @Test
    void drawsExactly320RandomNumbersAtAnyResolution() {
        for (int[] size : new int[][] {{320, 200}, {640, 400}, {2560, 900}, {1280, 720}}) {
            AtomicInteger calls = new AtomicInteger();
            Wipe wipe = new Wipe(() -> calls.getAndIncrement() * 37 & 0xFF);
            FrameBuffer a = new FrameBuffer(size[0], size[1]);
            FrameBuffer b = new FrameBuffer(size[0], size[1]);
            wipe.start(a, b);
            assertThat(calls.get()).isEqualTo(Wipe.RNG_DRAWS);
            FrameBuffer out = new FrameBuffer(size[0], size[1]);
            int guard = 0;
            while (!wipe.tick(1, out) && guard++ < 1000) {
                // keep melting
            }
            assertThat(wipe.active()).isFalse();
            assertThat(calls.get()).isEqualTo(Wipe.RNG_DRAWS);
        }
    }

    @Test
    void highResolutionShowsBothScreensDuringTheMelt() {
        Random seq = new Random(3);
        Wipe wipe = new Wipe(() -> seq.nextInt(256));
        FrameBuffer from = new FrameBuffer(1000, 500);
        FrameBuffer to = new FrameBuffer(1000, 500);
        from.clear(1);
        to.clear(2);
        wipe.start(from, to);
        FrameBuffer out = new FrameBuffer(1000, 500);
        for (int i = 0; i < 30; i++) {
            wipe.tick(1, out);
        }
        int ones = 0;
        int twos = 0;
        for (int i = 0; i < out.size(); i++) {
            if (out.pixels()[i] == 1) {
                ones++;
            } else if (out.pixels()[i] == 2) {
                twos++;
            }
        }
        assertThat(ones).isPositive();
        assertThat(twos).isPositive();
        assertThat(ones + twos).isEqualTo(out.size());
    }
}
