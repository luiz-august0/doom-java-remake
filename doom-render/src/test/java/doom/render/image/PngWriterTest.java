package doom.render.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.junit.jupiter.api.Test;

class PngWriterTest {

    record Chunk(String type, byte[] data) {
    }

    /** Parsed PNG: validates the signature and every CRC. */
    static List<Chunk> chunks(byte[] png) {
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        for (int i = 0; i < 8; i++) {
            assertThat(png[i]).isEqualTo(sig[i]);
        }
        ByteBuffer b = ByteBuffer.wrap(png);
        b.position(8);
        List<Chunk> out = new ArrayList<>();
        while (b.hasRemaining()) {
            int len = b.getInt();
            byte[] type = new byte[4];
            b.get(type);
            byte[] data = new byte[len];
            b.get(data);
            int crc = b.getInt();
            CRC32 c = new CRC32();
            c.update(type);
            c.update(data);
            assertThat(crc).isEqualTo((int) c.getValue());
            out.add(new Chunk(new String(type, java.nio.charset.StandardCharsets.US_ASCII), data));
        }
        return out;
    }

    static byte[] inflateIdat(List<Chunk> chunks, int expected) throws DataFormatException {
        ByteArrayOutputStream zbytes = new ByteArrayOutputStream();
        for (Chunk c : chunks) {
            if (c.type().equals("IDAT")) {
                zbytes.writeBytes(c.data());
            }
        }
        Inflater inf = new Inflater();
        inf.setInput(zbytes.toByteArray());
        byte[] raw = new byte[expected];
        int n = inf.inflate(raw);
        assertThat(n).isEqualTo(expected);
        assertThat(inf.finished()).isTrue();
        inf.end();
        return raw;
    }

    @Test
    void indexedStructureAndPixels() throws IOException, DataFormatException {
        int w = 13;
        int h = 7;
        byte[] pixels = new byte[w * h];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (byte) (i * 5);
        }
        byte[] pal = new byte[768];
        for (int i = 0; i < 768; i++) {
            pal[i] = (byte) (i * 3 + 1);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PngWriter.writeIndexed(bos, pixels, w, h, false, pal);
        List<Chunk> chunks = chunks(bos.toByteArray());
        assertThat(chunks.stream().map(Chunk::type).toList()).containsExactly("IHDR", "PLTE", "IDAT", "IEND");
        ByteBuffer ihdr = ByteBuffer.wrap(chunks.get(0).data());
        assertThat(ihdr.getInt()).isEqualTo(w);
        assertThat(ihdr.getInt()).isEqualTo(h);
        assertThat(ihdr.get()).isEqualTo((byte) 8);
        assertThat(ihdr.get()).isEqualTo((byte) 3);
        assertThat(chunks.get(1).data()).isEqualTo(pal);
        byte[] raw = inflateIdat(chunks, (w + 1) * h);
        for (int y = 0; y < h; y++) {
            assertThat(raw[y * (w + 1)]).isZero();
            for (int x = 0; x < w; x++) {
                assertThat(raw[y * (w + 1) + 1 + x]).isEqualTo(pixels[y * w + x]);
            }
        }
    }

    @Test
    void columnMajorIsTransposed() throws IOException, DataFormatException {
        int w = 6;
        int h = 4;
        byte[] colMajor = new byte[w * h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                colMajor[x * h + y] = (byte) (x * 16 + y);
            }
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PngWriter.writeIndexed(bos, colMajor, w, h, true, new byte[768]);
        byte[] raw = inflateIdat(chunks(bos.toByteArray()), (w + 1) * h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                assertThat(raw[y * (w + 1) + 1 + x]).isEqualTo((byte) (x * 16 + y));
            }
        }
    }

    @Test
    void rgbaStructureAndPixels() throws IOException, DataFormatException {
        int w = 5;
        int h = 3;
        byte[] rgba = new byte[w * h * 4];
        for (int i = 0; i < rgba.length; i++) {
            rgba[i] = (byte) (i * 7);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PngWriter.writeRgba(bos, rgba, w, h);
        List<Chunk> chunks = chunks(bos.toByteArray());
        assertThat(chunks.stream().map(Chunk::type).toList()).containsExactly("IHDR", "IDAT", "IEND");
        assertThat(chunks.get(0).data()[9]).isEqualTo((byte) 6);
        byte[] raw = inflateIdat(chunks, (w * 4 + 1) * h);
        for (int y = 0; y < h; y++) {
            for (int i = 0; i < w * 4; i++) {
                assertThat(raw[y * (w * 4 + 1) + 1 + i]).isEqualTo(rgba[y * w * 4 + i]);
            }
        }
    }

    @Test
    void largeImageCompresses() throws IOException {
        byte[] pixels = new byte[2560 * 900];
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PngWriter.writeIndexed(bos, pixels, 2560, 900, false, new byte[768]);
        assertThat(bos.size()).isLessThan(50_000);
    }
}
