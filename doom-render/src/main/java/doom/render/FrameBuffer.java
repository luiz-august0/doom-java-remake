package doom.render;

import java.util.Arrays;

/**
 * The indexed framebuffer (spec 04 §8): {@code width × height} palette indices in a {@code byte[]}, laid out as
 * described by {@link FrameLayout}. Everything that draws into the frame (3D renderer, {@code Canvas2D}, wipe) goes
 * through this class or reads {@link #pixels()}, {@link #rowStride()} and {@link #columnStride()} directly:
 *
 * <pre>
 *   index(x, y) = x * columnStride() + y * rowStride()
 *   ROW_MAJOR:    rowStride = width,  columnStride = 1
 *   COLUMN_MAJOR: rowStride = 1,      columnStride = height
 * </pre>
 *
 * <p>The buffer is resizable. {@link #resize(int, int)} reuses the backing array when it is large enough, so
 * {@code pixels().length} may exceed {@code width * height}; only the first {@code width * height} bytes are meaningful.
 * Resizing never allocates when the new size fits, so the steady state is allocation free.
 *
 * <p>Not thread-safe: the main thread owns it.
 */
public final class FrameBuffer {

    private final FrameLayout layout;
    private byte[] pixels;
    private int width;
    private int height;

    /** Creates a zero-filled buffer. */
    public FrameBuffer(int width, int height, FrameLayout layout) {
        if (layout == null) {
            throw new NullPointerException("layout");
        }
        checkSize(width, height);
        this.layout = layout;
        this.width = width;
        this.height = height;
        this.pixels = new byte[width * height];
    }

    /** Creates a zero-filled row-major buffer. */
    public FrameBuffer(int width, int height) {
        this(width, height, FrameLayout.ROW_MAJOR);
    }

    private static void checkSize(int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height > Integer.MAX_VALUE - 16) {
            throw new IllegalArgumentException("invalid framebuffer size " + width + "x" + height);
        }
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public FrameLayout layout() {
        return layout;
    }

    /** True for {@link FrameLayout#COLUMN_MAJOR}; this is the {@code columnMajor} flag of {@code VideoSink.present}. */
    public boolean columnMajor() {
        return layout == FrameLayout.COLUMN_MAJOR;
    }

    /** Number of meaningful bytes: {@code width * height}. */
    public int size() {
        return width * height;
    }

    /** The backing array (at least {@link #size()} bytes). Shared: the caller draws directly into it. */
    public byte[] pixels() {
        return pixels;
    }

    /** Distance in {@link #pixels()} between a pixel and the one below it. */
    public int rowStride() {
        return layout == FrameLayout.ROW_MAJOR ? width : 1;
    }

    /** Distance in {@link #pixels()} between a pixel and the one to its right. */
    public int columnStride() {
        return layout == FrameLayout.ROW_MAJOR ? 1 : height;
    }

    /** Index of pixel ({@code x}, {@code y}) in {@link #pixels()}; no bounds check. */
    public int index(int x, int y) {
        return layout == FrameLayout.ROW_MAJOR ? y * width + x : x * height + y;
    }

    /** Pixel at ({@code x}, {@code y}) as an unsigned palette index. */
    public int get(int x, int y) {
        checkXY(x, y);
        return pixels[index(x, y)] & 0xFF;
    }

    /** Sets pixel ({@code x}, {@code y}). */
    public void set(int x, int y, int colour) {
        checkXY(x, y);
        pixels[index(x, y)] = (byte) colour;
    }

    private void checkXY(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IndexOutOfBoundsException("pixel (" + x + "," + y + ") outside " + width + "x" + height);
        }
    }

    /** Fills the whole frame with {@code colour}. */
    public void clear(int colour) {
        Arrays.fill(pixels, 0, width * height, (byte) colour);
    }

    /**
     * Changes the size. The content becomes undefined (the caller redraws). The backing array is reused when it is
     * large enough, otherwise a new zero-filled array is allocated.
     *
     * @return {@code true} if the size changed
     */
    public boolean resize(int newWidth, int newHeight) {
        checkSize(newWidth, newHeight);
        if (newWidth == width && newHeight == height) {
            return false;
        }
        int needed = newWidth * newHeight;
        if (pixels.length < needed) {
            pixels = new byte[needed];
        }
        width = newWidth;
        height = newHeight;
        return true;
    }

    /**
     * Copies the pixels of {@code src} (same size, any layout) into this buffer, converting the layout if needed.
     *
     * @throws IllegalArgumentException if the sizes differ
     */
    public void copyFrom(FrameBuffer src) {
        if (src.width != width || src.height != height) {
            throw new IllegalArgumentException("size mismatch: " + src.width + "x" + src.height + " vs " + width + "x"
                    + height);
        }
        if (src.layout == layout) {
            System.arraycopy(src.pixels, 0, pixels, 0, width * height);
            return;
        }
        byte[] s = src.pixels;
        byte[] d = pixels;
        int sRow = src.rowStride();
        int sCol = src.columnStride();
        int dRow = rowStride();
        int dCol = columnStride();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                d[x * dCol + y * dRow] = s[x * sCol + y * sRow];
            }
        }
    }

    @Override
    public String toString() {
        return "FrameBuffer[" + width + "x" + height + " " + layout + "]";
    }
}
