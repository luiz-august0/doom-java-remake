package doom.render.canvas;

import static org.assertj.core.api.Assertions.assertThat;

import doom.render.FrameBuffer;
import doom.render.FrameLayout;
import doom.wad.gfx.Flat;
import doom.wad.gfx.Picture;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import org.junit.jupiter.api.Test;

class Canvas2DTest {

    // ------------------------------------------------------------------------------------------------------
    // Reference implementations
    // ------------------------------------------------------------------------------------------------------

    /** Straightforward transcription of v_video.c V_DrawPatch / V_DrawPatchFlipped working on the raw lump. */
    static void refDrawPatch(byte[] screen, int sw, int sh, byte[] lump, int x, int y, boolean flip) {
        ByteBuffer b = ByteBuffer.wrap(lump).order(ByteOrder.LITTLE_ENDIAN);
        int w = b.getShort(0);
        int left = b.getShort(4);
        int top = b.getShort(6);
        y -= top;
        x -= left;
        for (int col = 0; col < w; col++, x++) {
            int c = flip ? w - 1 - col : col;
            int p = b.getInt(8 + 4 * c);
            while ((lump[p] & 0xFF) != 0xFF) {
                int topdelta = lump[p] & 0xFF;
                int length = lump[p + 1] & 0xFF;
                for (int i = 0; i < length; i++) {
                    int dy = y + topdelta + i;
                    if (x >= 0 && x < sw && dy >= 0 && dy < sh) {
                        screen[dy * sw + x] = lump[p + 3 + i];
                    }
                }
                p += length + 4;
            }
        }
    }

    static byte[] randomPatch(Random r, int w, int h, int left, int top) {
        PictureBuilder pb = new PictureBuilder(w, h).offsets(left, top);
        for (int c = 0; c < w; c++) {
            int row = r.nextInt(3);
            while (row < h) {
                int len = 1 + r.nextInt(Math.min(h - row, 20));
                int[] px = new int[len];
                for (int i = 0; i < len; i++) {
                    px[i] = 1 + r.nextInt(255);
                }
                pb.post(c, row, px);
                row += len + r.nextInt(6);
            }
        }
        return pb.build();
    }

    static Picture decode(byte[] lump) {
        return Picture.decode(ByteBuffer.wrap(lump).order(ByteOrder.LITTLE_ENDIAN), "test", "P", true);
    }

    static int bx(int w, int h, int v) {
        return (int) Math.floorDiv(100L * w + (long) h * (v - 160) + 100, 200);
    }

    static int by(int h, int v) {
        return (int) Math.floorDiv((long) h * v + 100, 200);
    }

    // ------------------------------------------------------------------------------------------------------
    // Pixel-identical at 320x200
    // ------------------------------------------------------------------------------------------------------

    @Test
    void identicalToVDrawPatchAt320x200_bothLayouts() {
        Random r = new Random(1234);
        for (FrameLayout layout : FrameLayout.values()) {
            FrameBuffer fb = new FrameBuffer(320, 200, layout);
            Canvas2D canvas = new Canvas2D(fb);
            byte[] ref = new byte[320 * 200];
            for (int iter = 0; iter < 300; iter++) {
                int w = 1 + r.nextInt(60);
                int h = 1 + r.nextInt(80);
                byte[] lump = randomPatch(r, w, h, r.nextInt(30) - 10, r.nextInt(30) - 10);
                Picture p = decode(lump);
                int x = r.nextInt(360) - 20;
                int y = r.nextInt(240) - 20;
                boolean flip = r.nextBoolean();
                refDrawPatch(ref, 320, 200, lump, x, y, flip);
                if (flip) {
                    canvas.drawPatchFlipped(p, x, y);
                } else {
                    canvas.drawPatch(p, x, y);
                }
            }
            for (int yy = 0; yy < 200; yy++) {
                for (int xx = 0; xx < 320; xx++) {
                    assertThat(fb.get(xx, yy)).as("%s (%d,%d)", layout, xx, yy).isEqualTo(ref[yy * 320 + xx] & 0xFF);
                }
            }
        }
    }

    @Test
    void identityMappingAt320x200() {
        Canvas2D c = new Canvas2D(new FrameBuffer(320, 200));
        assertThat(c.scale()).isEqualTo(1.0);
        assertThat(c.xOffset()).isEqualTo(0.0);
        for (int v = 0; v <= 320; v++) {
            assertThat(c.physicalX(v)).isEqualTo(v);
        }
        for (int v = 0; v <= 200; v++) {
            assertThat(c.physicalY(v)).isEqualTo(v);
        }
        assertThat(c.leftBarWidth()).isZero();
        assertThat(c.rightBarStart()).isEqualTo(320);
    }

    // ------------------------------------------------------------------------------------------------------
    // Scaling and centring
    // ------------------------------------------------------------------------------------------------------

    @Test
    void boundariesMatchRoundedFloatingPointDefinition() {
        int[][] sizes = {{320, 200}, {427, 200}, {640, 400}, {800, 450}, {2560, 900}, {1000, 333}, {100, 77}};
        for (int[] s : sizes) {
            int w = s[0];
            int h = s[1];
            Canvas2D c = new Canvas2D(new FrameBuffer(w, h));
            double scale = h / 200.0;
            double off = (w - 320 * scale) / 2.0;
            assertThat(c.scale()).isEqualTo(scale);
            assertThat(c.xOffset()).isEqualTo(off);
            for (int v = -50; v <= 400; v++) {
                double exact = off + v * scale;
                if (Math.abs(exact - Math.floor(exact) - 0.5) < 1e-6) {
                    continue; // exact tie: rounding direction is a convention
                }
                assertThat(c.physicalX(v)).as("%dx%d x %d", w, h, v).isEqualTo((int) Math.floor(exact + 0.5));
            }
            for (int v = 0; v <= 200; v++) {
                double exact = v * scale;
                if (Math.abs(exact - Math.floor(exact) - 0.5) < 1e-6) {
                    continue;
                }
                assertThat(c.physicalY(v)).isEqualTo((int) Math.floor(exact + 0.5));
            }
            assertThat(c.physicalY(0)).isZero();
            assertThat(c.physicalY(200)).isEqualTo(h);
        }
    }

    @Test
    void integerScaleReplicatesPixels() {
        Random r = new Random(7);
        byte[] lump = randomPatch(r, 30, 40, 4, 5);
        Picture p = decode(lump);
        FrameBuffer small = new FrameBuffer(320, 200);
        new Canvas2D(small).drawPatch(p, 100, 60);
        FrameBuffer big = new FrameBuffer(960, 600, FrameLayout.COLUMN_MAJOR);
        new Canvas2D(big).drawPatch(p, 100, 60);
        for (int y = 0; y < 600; y++) {
            for (int x = 0; x < 960; x++) {
                assertThat(big.get(x, y)).isEqualTo(small.get(x / 3, y / 3));
            }
        }
    }

    @Test
    void fractionalScaleAndCentringMatchIndependentSampling() {
        int[][] sizes = {{427, 200}, {800, 450}, {1000, 333}, {2560, 900}, {301, 150}, {500, 230}};
        Random r = new Random(99);
        for (int[] s : sizes) {
            int w = s[0];
            int h = s[1];
            for (FrameLayout layout : FrameLayout.values()) {
                // virtual canvas with margin: virtual x in [-MARGIN, 320 + MARGIN)
                final int margin = 700;
                int vw = 320 + 2 * margin;
                byte[] ref = new byte[vw * 200];
                FrameBuffer fb = new FrameBuffer(w, h, layout);
                Canvas2D canvas = new Canvas2D(fb);
                fb.clear(0);
                for (int i = 0; i < 25; i++) {
                    byte[] lump = randomPatch(r, 1 + r.nextInt(50), 1 + r.nextInt(70), r.nextInt(20), r.nextInt(20));
                    Picture p = decode(lump);
                    int x = r.nextInt(400) - 40;
                    int y = r.nextInt(240) - 20;
                    boolean flip = r.nextInt(4) == 0;
                    refDrawPatch(ref, vw, 200, lump, x + margin, y, flip);
                    if (flip) {
                        canvas.drawPatchFlipped(p, x, y);
                    } else {
                        canvas.drawPatch(p, x, y);
                    }
                }
                for (int yy = 0; yy < h; yy++) {
                    int vy = 0;
                    while (by(h, vy + 1) <= yy) {
                        vy++;
                    }
                    for (int xx = 0; xx < w; xx++) {
                        int vx = 160 - 100 * w / h - 3;
                        while (bx(w, h, vx + 1) <= xx) {
                            vx++;
                        }
                        int expected = ref[vy * vw + (vx + margin)] & 0xFF;
                        assertThat(fb.get(xx, yy)).as("%dx%d %s (%d,%d) v(%d,%d)", w, h, layout, xx, yy, vx, vy)
                                .isEqualTo(expected);
                    }
                }
            }
        }
    }

    @Test
    void canvasIsCentredHorizontally() {
        // 400x200: 320 space starts at x = 40
        FrameBuffer fb = new FrameBuffer(400, 200);
        Canvas2D c = new Canvas2D(fb);
        byte[] lump = new PictureBuilder(1, 1).post(0, 0, 9).build();
        c.drawPatch(decode(lump), 0, 0);
        assertThat(fb.get(40, 0)).isEqualTo(9);
        assertThat(fb.get(39, 0)).isZero();
        assertThat(fb.get(41, 0)).isZero();
        c.drawPatch(decode(lump), 319, 199);
        assertThat(fb.get(359, 199)).isEqualTo(9);
        assertThat(c.leftBarWidth()).isEqualTo(40);
        assertThat(c.rightBarStart()).isEqualTo(360);
    }

    @Test
    void downscaleDoesNotCrashAndKeepsSomePixels() {
        FrameBuffer fb = new FrameBuffer(160, 100);
        Canvas2D c = new Canvas2D(fb);
        byte[] lump = randomPatch(new Random(3), 320, 200, 0, 0);
        c.drawPatch(decode(lump), 0, 0);
        int nonZero = 0;
        for (int y = 0; y < 100; y++) {
            for (int x = 0; x < 160; x++) {
                if (fb.get(x, y) != 0) {
                    nonZero++;
                }
            }
        }
        assertThat(nonZero).isGreaterThan(1000);
    }

    // ------------------------------------------------------------------------------------------------------
    // Clipping, wide patches
    // ------------------------------------------------------------------------------------------------------

    @Test
    void clippingFarOutsideIsSilent() {
        FrameBuffer fb = new FrameBuffer(640, 400);
        Canvas2D c = new Canvas2D(fb);
        Picture p = decode(randomPatch(new Random(5), 20, 20, 0, 0));
        int[][] positions = {{-1000, 0}, {5000, 0}, {0, -1000}, {0, 5000}, {-10, -10}, {315, 195}, {Integer.MAX_VALUE / 4, 0},
                {0, Integer.MIN_VALUE / 4}};
        for (int[] pos : positions) {
            c.drawPatch(p, pos[0], pos[1]);
            c.drawPatchFlipped(p, pos[0], pos[1]);
        }
        // partially visible: the bottom-right corner of the screen got something
        c.drawPatch(decode(new PictureBuilder(5, 5).post(0, 0, 1, 1, 1, 1, 1).post(4, 4, 7).build()), 315, 195);
        assertThat(fb.get(639, 399)).isEqualTo(7);
    }

    @Test
    void widePatchIsCentredByLeftOffsetAndSpillsIntoTheBars() {
        // 1000x200 frame: bars of 340 pixels each side
        FrameBuffer fb = new FrameBuffer(1000, 200);
        Canvas2D c = new Canvas2D(fb);
        int wide = 500;
        PictureBuilder pb = new PictureBuilder(wide, 1).offsets(wide / 2, 0);
        for (int x = 0; x < wide; x++) {
            pb.post(x, 0, 1 + x % 200);
        }
        Picture p = decode(pb.build());
        c.drawPatchCentred(p, 0);
        // virtual x of column i is 160 - 250 + i = i - 90 ; physical x = 340 + (i - 90)
        assertThat(fb.get(340 - 90, 0)).isEqualTo(1);
        assertThat(fb.get(340 - 90 + 499, 0)).isEqualTo(1 + 499 % 200);
        assertThat(fb.get(340 - 91, 0)).isZero();
        assertThat(fb.get(340 - 90 + 500, 0)).isZero();
        // without leftoffset: centred by width
        FrameBuffer fb2 = new FrameBuffer(1000, 200);
        PictureBuilder pb2 = new PictureBuilder(wide, 1);
        for (int x = 0; x < wide; x++) {
            pb2.post(x, 0, 5);
        }
        new Canvas2D(fb2).drawPatchCentred(decode(pb2.build()), 0);
        assertThat(fb2.get(250, 0)).isEqualTo(5);
        assertThat(fb2.get(249, 0)).isZero();
        assertThat(fb2.get(749, 0)).isEqualTo(5);
        assertThat(fb2.get(750, 0)).isZero();
    }

    @Test
    void remapTranslatesColours() {
        FrameBuffer fb = new FrameBuffer(320, 200);
        Canvas2D c = new Canvas2D(fb);
        byte[] remap = new byte[256];
        for (int i = 0; i < 256; i++) {
            remap[i] = (byte) (255 - i);
        }
        c.drawPatch(decode(new PictureBuilder(1, 1).post(0, 0, 10).build()), 3, 4, remap);
        assertThat(fb.get(3, 4)).isEqualTo(245);
    }

    // ------------------------------------------------------------------------------------------------------
    // Fills and flats
    // ------------------------------------------------------------------------------------------------------

    @Test
    void fillRectScalesAndClips() {
        FrameBuffer fb = new FrameBuffer(800, 400, FrameLayout.COLUMN_MAJOR);
        Canvas2D c = new Canvas2D(fb);
        c.fillRect(10, 20, 30, 40, 77);
        int s = 2;
        int ox = 80;
        assertThat(fb.get(ox + 10 * s, 20 * s)).isEqualTo(77);
        assertThat(fb.get(ox + 10 * s - 1, 20 * s)).isZero();
        assertThat(fb.get(ox + 40 * s - 1, 60 * s - 1)).isEqualTo(77);
        assertThat(fb.get(ox + 40 * s, 60 * s - 1)).isZero();
        assertThat(fb.get(ox + 40 * s - 1, 60 * s)).isZero();
        c.fillRect(-5000, -5000, 10000, 10000, 3);
        assertThat(fb.get(0, 0)).isEqualTo(3);
        assertThat(fb.get(799, 399)).isEqualTo(3);
        c.fillRect(50, 50, 0, 10, 9);
        c.fillRect(50, 50, -5, 10, 9);
        assertThat(fb.get(ox + 100, 100)).isEqualTo(3);
    }

    @Test
    void sidesFillAndTile() {
        byte[] flatBytes = new byte[4096];
        for (int i = 0; i < 4096; i++) {
            flatBytes[i] = (byte) (1 + (i % 64) + (i / 64) % 7);
        }
        Flat flat = Flat.of("F", flatBytes);
        for (FrameLayout layout : FrameLayout.values()) {
            FrameBuffer fb = new FrameBuffer(700, 200, layout);
            Canvas2D c = new Canvas2D(fb);
            c.clear(1);
            c.fillRect(0, 0, 320, 200, 2);
            c.fillSides(9);
            assertThat(fb.get(0, 0)).isEqualTo(9);
            assertThat(fb.get(189, 199)).isEqualTo(9);
            assertThat(fb.get(190, 0)).isEqualTo(2);
            assertThat(fb.get(509, 0)).isEqualTo(2);
            assertThat(fb.get(510, 0)).isEqualTo(9);
            assertThat(fb.get(699, 199)).isEqualTo(9);
            c.tileSides(flat);
            for (int y = 0; y < 200; y += 17) {
                for (int x = 0; x < 700; x += 13) {
                    if (x >= 190 && x < 510) {
                        assertThat(fb.get(x, y)).isEqualTo(2);
                    } else {
                        int vx = x - 190;
                        assertThat(fb.get(x, y)).isEqualTo(flat.pixel(vx, y));
                    }
                }
            }
        }
    }

    @Test
    void tileFlatScalesWithCanvas() {
        byte[] flatBytes = new byte[4096];
        for (int i = 0; i < 4096; i++) {
            flatBytes[i] = (byte) (i * 31 + i / 64);
        }
        Flat flat = Flat.of("F", flatBytes);
        FrameBuffer fb = new FrameBuffer(1280, 800);
        Canvas2D c = new Canvas2D(fb);
        c.tileFlat(flat, 0, 168, 320, 32);
        // 4x scale, offset (1280 - 1280)/2 = 0
        for (int y = 168 * 4; y < 800; y += 5) {
            for (int x = 0; x < 1280; x += 7) {
                assertThat(fb.get(x, y)).isEqualTo(flat.pixel(x / 4, y / 4));
            }
        }
        assertThat(fb.get(5, 168 * 4 - 1)).isZero();
    }

    @Test
    void resizingTheFrameRelayouts() {
        FrameBuffer fb = new FrameBuffer(320, 200);
        Canvas2D c = new Canvas2D(fb);
        fb.resize(640, 400);
        c.fillRect(0, 0, 320, 200, 5);
        assertThat(fb.get(0, 0)).isEqualTo(5);
        assertThat(fb.get(639, 399)).isEqualTo(5);
        fb.resize(1000, 400);
        fb.clear(0);
        c.fillRect(0, 0, 320, 200, 6);
        assertThat(fb.get(179, 0)).isZero();
        assertThat(fb.get(180, 0)).isEqualTo(6);
        assertThat(fb.get(819, 399)).isEqualTo(6);
        assertThat(fb.get(820, 399)).isZero();
    }
}
