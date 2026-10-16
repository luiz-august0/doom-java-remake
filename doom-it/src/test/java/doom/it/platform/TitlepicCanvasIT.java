package doom.it.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.render.FrameBuffer;
import doom.render.FrameLayout;
import doom.render.canvas.Canvas2D;
import doom.render.image.PngWriter;
import doom.wad.LumpDirectory;
import doom.wad.gfx.Palettes;
import doom.wad.gfx.Picture;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The 2D canvas against the original {@code V_DrawPatch} on the real TITLEPIC of the available IWADs: at 320×200 the
 * frame must equal a straight transcription of the C code reading the raw lump (pixel identical).
 */
class TitlepicCanvasIT {

    private static LumpDirectory open(String name) throws Exception {
        String dir = System.getenv("DOOM_IWAD_DIR");
        assumeTrue(dir != null, "DOOM_IWAD_DIR not set");
        Path wad = Path.of(dir, name);
        assumeTrue(Files.isRegularFile(wad), name + " not found");
        return LumpDirectory.open(List.of(wad));
    }

    /** v_video.c V_DrawPatch on the raw lump bytes. */
    private static byte[] referenceDrawPatch(byte[] lump, int x, int y) {
        byte[] screen = new byte[320 * 200];
        ByteBuffer b = ByteBuffer.wrap(lump).order(ByteOrder.LITTLE_ENDIAN);
        int w = b.getShort(0);
        y -= b.getShort(6);
        x -= b.getShort(4);
        for (int col = 0; col < w; col++, x++) {
            int p = b.getInt(8 + 4 * col);
            while ((lump[p] & 0xFF) != 0xFF) {
                int top = lump[p] & 0xFF;
                int len = lump[p + 1] & 0xFF;
                for (int i = 0; i < len; i++) {
                    int dy = y + top + i;
                    if (x >= 0 && x < 320 && dy >= 0 && dy < 200) {
                        screen[dy * 320 + x] = lump[p + 3 + i];
                    }
                }
                p += len + 4;
            }
        }
        return screen;
    }

    @ParameterizedTest
    @ValueSource(strings = {"doom1.wad", "freedoom1.wad", "freedoom2.wad", "freedm.wad"})
    void titlepicIsPixelIdenticalAt320x200(String iwad) throws Exception {
        LumpDirectory lumps = open(iwad);
        int lump = lumps.get("TITLEPIC");
        byte[] raw = lumps.bytes(lump);
        byte[] ref = referenceDrawPatch(raw, 0, 0);
        Picture pic = Picture.read(lumps, lump, false);
        for (FrameLayout layout : FrameLayout.values()) {
            FrameBuffer fb = new FrameBuffer(320, 200, layout);
            new Canvas2D(fb).drawPatch(pic, 0, 0);
            for (int y = 0; y < 200; y++) {
                for (int x = 0; x < 320; x++) {
                    assertThat(fb.get(x, y)).as("%s %s (%d,%d)", iwad, layout, x, y).isEqualTo(ref[y * 320 + x] & 0xFF);
                }
            }
        }
    }

    @Test
    void titlepicPillarboxedAt2560x900WritesAValidPng() throws Exception {
        LumpDirectory lumps = open("doom1.wad");
        Picture pic = Picture.read(lumps, "TITLEPIC", true);
        FrameBuffer fb = new FrameBuffer(2560, 900);
        Canvas2D canvas = new Canvas2D(fb);
        canvas.clear(0);
        canvas.drawPatchCentred(pic, 0);
        // 320 space is 1440 wide, centred: bars of 560 pixels
        assertThat(canvas.leftBarWidth()).isEqualTo(560);
        assertThat(canvas.rightBarStart()).isEqualTo(2000);
        for (int y = 0; y < 900; y += 37) {
            assertThat(fb.get(0, y)).isZero();
            assertThat(fb.get(559, y)).isZero();
            assertThat(fb.get(2000, y)).isZero();
        }
        // the picture covers its whole area: no black pixel strip inside is guaranteed, but colour must exist
        int nonZero = 0;
        for (int x = 560; x < 2000; x += 11) {
            if (fb.get(x, 450) != 0) {
                nonZero++;
            }
        }
        assertThat(nonZero).isGreaterThan(50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PngWriter.writeIndexed(out, fb.pixels(), fb.width(), fb.height(), fb.columnMajor(),
                Palettes.read(lumps).toArray(0));
        assertThat(out.size()).isGreaterThan(1000);
        Path target = Path.of("target");
        if (Files.isDirectory(target)) {
            Files.write(target.resolve("titlepic-2560x900.png"), out.toByteArray());
        }
    }
}
