package doom.render.image;

import doom.render.FrameBuffer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * PNG encoder for indexed frames with a palette and for RGBA images, using {@code java.util.zip} only (ADR-021).
 *
 * <p>Indexed output is colour type 3 (8-bit palette, {@code PLTE} with the 256 entries); RGBA output is colour type 6
 * (8 bits per channel). Rows are written unfiltered (filter type 0) and deflated; the files are valid PNG for every
 * decoder. Not used on hot paths: it allocates its working buffers per call.
 */
public final class PngWriter {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private PngWriter() {
    }

    // ---------------------------------------------------------------------------------------------------------
    // Indexed
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Writes an indexed image.
     *
     * @param pixels      palette indices, {@code width * height} bytes
     * @param columnMajor {@code false}: {@code pixels[y * width + x]}; {@code true}: {@code pixels[x * height + y]}
     * @param palette     768 bytes, R G B per entry (only the first 3·256 are used)
     */
    public static void writeIndexed(OutputStream out, byte[] pixels, int width, int height, boolean columnMajor,
                                    byte[] palette) throws IOException {
        checkSize(width, height);
        if (pixels.length < width * height) {
            throw new IllegalArgumentException("pixels too short: " + pixels.length + " < " + width * height);
        }
        if (palette.length < 768) {
            throw new IllegalArgumentException("palette needs 768 bytes, got " + palette.length);
        }
        byte[] raw = new byte[(width + 1) * height];
        int o = 0;
        for (int y = 0; y < height; y++) {
            raw[o++] = 0; // filter: none
            if (columnMajor) {
                for (int x = 0; x < width; x++) {
                    raw[o++] = pixels[x * height + y];
                }
            } else {
                System.arraycopy(pixels, y * width, raw, o, width);
                o += width;
            }
        }
        out.write(SIGNATURE);
        writeHeader(out, width, height, 3);
        byte[] plte = new byte[768];
        System.arraycopy(palette, 0, plte, 0, 768);
        writeChunk(out, "PLTE", plte, plte.length);
        writeIdat(out, raw);
        writeChunk(out, "IEND", new byte[0], 0);
    }

    /** Writes an indexed image to a file. */
    public static void writeIndexed(Path file, byte[] pixels, int width, int height, boolean columnMajor,
                                    byte[] palette) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            writeIndexed(out, pixels, width, height, columnMajor, palette);
        }
    }

    /** Writes the whole {@link FrameBuffer} (either layout) as an indexed PNG. */
    public static void writeIndexed(Path file, FrameBuffer frame, byte[] palette) throws IOException {
        writeIndexed(file, frame.pixels(), frame.width(), frame.height(), frame.columnMajor(), palette);
    }

    // ---------------------------------------------------------------------------------------------------------
    // RGBA
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Writes a 32-bit image.
     *
     * @param rgba {@code width * height * 4} bytes, row-major, top row first, R G B A order
     */
    public static void writeRgba(OutputStream out, byte[] rgba, int width, int height) throws IOException {
        checkSize(width, height);
        int stride = width * 4;
        if (rgba.length < stride * height) {
            throw new IllegalArgumentException("rgba too short: " + rgba.length + " < " + stride * height);
        }
        byte[] raw = new byte[(stride + 1) * height];
        for (int y = 0; y < height; y++) {
            int o = y * (stride + 1);
            raw[o] = 0;
            System.arraycopy(rgba, y * stride, raw, o + 1, stride);
        }
        out.write(SIGNATURE);
        writeHeader(out, width, height, 6);
        writeIdat(out, raw);
        writeChunk(out, "IEND", new byte[0], 0);
    }

    /** Writes a 32-bit image to a file. */
    public static void writeRgba(Path file, byte[] rgba, int width, int height) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            writeRgba(out, rgba, width, height);
        }
    }

    // ---------------------------------------------------------------------------------------------------------

    private static void checkSize(int width, int height) {
        if (width <= 0 || height <= 0 || (long) (width + 1) * height * 4 > Integer.MAX_VALUE - 64) {
            throw new IllegalArgumentException("invalid image size " + width + "x" + height);
        }
    }

    private static void writeHeader(OutputStream out, int width, int height, int colourType) throws IOException {
        byte[] ihdr = new byte[13];
        putInt(ihdr, 0, width);
        putInt(ihdr, 4, height);
        ihdr[8] = 8; // bit depth
        ihdr[9] = (byte) colourType;
        ihdr[10] = 0; // compression
        ihdr[11] = 0; // filter method
        ihdr[12] = 0; // no interlace
        writeChunk(out, "IHDR", ihdr, ihdr.length);
    }

    private static void writeIdat(OutputStream out, byte[] raw) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(raw.length / 4 + 64);
        Deflater deflater = new Deflater(6);
        try (DeflaterOutputStream dos = new DeflaterOutputStream(bos, deflater)) {
            dos.write(raw);
        } finally {
            deflater.end();
        }
        byte[] z = bos.toByteArray();
        writeChunk(out, "IDAT", z, z.length);
    }

    private static void writeChunk(OutputStream out, String type, byte[] data, int length) throws IOException {
        byte[] head = new byte[8];
        putInt(head, 0, length);
        for (int i = 0; i < 4; i++) {
            head[4 + i] = (byte) type.charAt(i);
        }
        CRC32 crc = new CRC32();
        crc.update(head, 4, 4);
        crc.update(data, 0, length);
        byte[] tail = new byte[4];
        putInt(tail, 0, (int) crc.getValue());
        out.write(head);
        out.write(data, 0, length);
        out.write(tail);
    }

    private static void putInt(byte[] b, int off, int v) {
        b[off] = (byte) (v >>> 24);
        b[off + 1] = (byte) (v >>> 16);
        b[off + 2] = (byte) (v >>> 8);
        b[off + 3] = (byte) v;
    }
}
