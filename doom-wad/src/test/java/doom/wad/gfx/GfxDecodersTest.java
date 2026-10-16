package doom.wad.gfx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import doom.wad.WadFormatException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

class GfxDecodersTest {

    @Test
    void decodesHeaderPostsAndPads() {
        byte[] lump = new PictureBuilder(2, 10).offsets(-3, 7)
                .post(0, 2, 11, 12, 13)
                .post(0, 8, 21)
                .post(1, 0, 31, 32)
                .pads(0xAA, 0xBB).build();
        Picture p = Picture.decode(ByteBuffer.wrap(lump));
        assertThat(p.width()).isEqualTo(2);
        assertThat(p.height()).isEqualTo(10);
        assertThat(p.leftOffset()).isEqualTo(-3);
        assertThat(p.topOffset()).isEqualTo(7);
        assertThat(p.postCount()).isEqualTo(3);
        assertThat(p.firstPost(0)).isZero();
        assertThat(p.endPost(0)).isEqualTo(2);
        assertThat(p.endPost(1)).isEqualTo(3);
        assertThat(p.postTop(0)).isEqualTo(2);
        assertThat(p.postLength(0)).isEqualTo(3);
        assertThat(p.postPixel(0, 2)).isEqualTo(13);
        assertThat(p.postPadBefore(0)).isEqualTo(0xAA);
        assertThat(p.postPadAfter(0)).isEqualTo(0xBB);
        assertThat(p.postTop(1)).isEqualTo(8);
        assertThat(p.postPixel(2, 1)).isEqualTo(32);
        assertThat(p.data().length).isEqualTo(p.lumpLength() + Picture.DATA_PADDING);
        assertThat(p.hasTallPosts()).isFalse();
    }

    @Test
    void emptyColumnsAndZeroWidthAreAccepted() {
        Picture p = Picture.decode(ByteBuffer.wrap(new PictureBuilder(3, 3).build()));
        assertThat(p.postCount()).isZero();
        assertThat(p.endPost(2)).isZero();
        assertThat(Picture.decode(ByteBuffer.wrap(new PictureBuilder(0, 3).build())).width()).isZero();
    }

    @Test
    void vanillaIgnoresRelativeOffsetsButTallModeAppliesThem() {
        byte[] lump = new PictureBuilder(1, 300).post(0, 254, 1, 2).post(0, 5, 3).build();
        Picture vanilla = Picture.decode(ByteBuffer.wrap(lump), "t.wad", "TALL", false);
        assertThat(vanilla.postTop(1)).isEqualTo(5);
        assertThat(vanilla.hasTallPosts()).isFalse();
        Picture tall = Picture.decode(ByteBuffer.wrap(lump), "t.wad", "TALL", true);
        assertThat(tall.postTop(1)).isEqualTo(259);
        assertThat(tall.postTopDelta(1)).isEqualTo(5);
        assertThat(tall.hasTallPosts()).isTrue();
    }

    @Test
    void badPicturesThrowWadFormatException() {
        assertThatThrownBy(() -> Picture.decode(ByteBuffer.wrap(new byte[4]), "f.wad", "X", false))
                .isInstanceOf(WadFormatException.class).hasMessageContaining("X");
        byte[] good = new PictureBuilder(2, 4).post(0, 0, 1, 2).post(1, 0, 3).build();
        for (int n = 0; n < good.length; n++) {
            byte[] cut = Arrays.copyOf(good, n);
            try {
                Picture.decode(ByteBuffer.wrap(cut));
            } catch (WadFormatException expected) {
                // fine: truncated data must be reported this way
            }
        }
        byte[] bad = good.clone();
        bad[8] = 0x7f;
        assertThatThrownBy(() -> Picture.decode(ByteBuffer.wrap(bad))).isInstanceOf(WadFormatException.class);
        byte[] neg = good.clone();
        neg[1] = (byte) 0x80;
        assertThatThrownBy(() -> Picture.decode(ByteBuffer.wrap(neg))).isInstanceOf(WadFormatException.class);
    }

    @Test
    void randomGarbageNeverEscapesAsRawException() {
        Random r = new Random(42);
        for (int i = 0; i < 2000; i++) {
            byte[] b = new byte[r.nextInt(200)];
            r.nextBytes(b);
            if (b.length >= 4) {
                b[1] = 0;
                b[3] = 0;
                b[0] = (byte) (b[0] & 7);
            }
            try {
                Picture.decode(ByteBuffer.wrap(b), "g.wad", "G", i % 2 == 0);
            } catch (WadFormatException expected) {
                // fine
            }
        }
    }

    @Test
    void palettes() {
        byte[] lump = new byte[14 * 768];
        for (int i = 0; i < lump.length; i++) {
            lump[i] = (byte) (i % 251);
        }
        Palettes p = Palettes.decode(ByteBuffer.wrap(lump));
        assertThat(p.count()).isEqualTo(14);
        assertThat(p.red(1, 2)).isEqualTo((768 + 6) % 251);
        assertThat(p.rgb(0, 1)).isEqualTo(3 << 16 | 4 << 8 | 5);
        assertThat(p.toArray(13)).hasSize(768).startsWith(lump[13 * 768], lump[13 * 768 + 1]);
        byte[] dst = new byte[800];
        p.copyTo(2, dst, 32);
        assertThat(dst[32]).isEqualTo(lump[2 * 768]);
        assertThatThrownBy(() -> Palettes.decode(ByteBuffer.wrap(new byte[768 * 3])))
                .isInstanceOf(WadFormatException.class);
    }

    @Test
    void colormaps() {
        byte[] lump = new byte[34 * 256];
        for (int i = 0; i < lump.length; i++) {
            lump[i] = (byte) (i * 7);
        }
        Colormaps c = Colormaps.decode(ByteBuffer.wrap(lump));
        assertThat(c.map(5, 9)).isEqualTo(lump[5 * 256 + 9] & 0xFF);
        assertThat(c.table()).hasSize(34 * 256);
        Colormaps c33 = Colormaps.decode(ByteBuffer.wrap(Arrays.copyOf(lump, 33 * 256)));
        assertThat(c33.map(Colormaps.BLACK, 200)).isZero();
        assertThat(c33.map(32, 3)).isEqualTo(lump[32 * 256 + 3] & 0xFF);
        assertThatThrownBy(() -> Colormaps.decode(ByteBuffer.wrap(new byte[100])))
                .isInstanceOf(WadFormatException.class);
    }

    @Test
    void flats() {
        byte[] lump = new byte[4096];
        lump[64 * 3 + 5] = 42;
        Flat f = Flat.decode(ByteBuffer.wrap(lump));
        assertThat(f.pixel(5, 3)).isEqualTo(42);
        assertThat(f.pixel(5 + 64, 3 + 128)).isEqualTo(42);
        assertThat(f.pixel(-59, 3)).isEqualTo(42);
        assertThatThrownBy(() -> Flat.decode(ByteBuffer.wrap(new byte[4095]))).isInstanceOf(WadFormatException.class);
    }
}
