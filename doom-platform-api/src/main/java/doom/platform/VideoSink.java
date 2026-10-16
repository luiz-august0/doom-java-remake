package doom.platform;

/**
 * Presents indexed frames (ADR-009): the backend uploads the {@code byte[]} of palette indices as an {@code R8}
 * texture and resolves colours in a shader through a 256-entry palette texture. All methods are called on the
 * main thread.
 */
public interface VideoSink extends AutoCloseable {

    /** Creates the window and the GPU resources. */
    void open(VideoConfig config);

    /** Applies new settings (window mode, size, vsync, scaling) without restarting the game. */
    void reconfigure(VideoConfig config);

    /** Width of the drawable output in physical pixels (HiDPI aware); used to choose the internal resolution. */
    int outputWidth();

    /** Height of the drawable output in physical pixels (HiDPI aware). */
    int outputHeight();

    /**
     * Sets the palette used for the following frames: 256 entries of R, G, B bytes (768 bytes), with gamma already
     * applied by the caller. Cheap: uploads only when it changed.
     */
    void setPalette(byte[] rgb768);

    /**
     * Uploads and displays one frame of palette indices.
     *
     * @param indexed     the pixels, {@code width * height} bytes
     * @param width       frame width in pixels
     * @param height      frame height in pixels
     * @param columnMajor {@code false}: row-major ({@code indexed[y * width + x]});
     *                    {@code true}: transposed ({@code indexed[x * height + y]}, spec 04 §8)
     */
    void present(byte[] indexed, int width, int height, boolean columnMajor);

    /** True once the user asked to close the window. */
    boolean closeRequested();

    /**
     * Reads back the last presented output as RGBA bytes (row-major, top row first), for automated verification
     * and screenshots of the real output. Optional.
     *
     * @return {@code outputWidth() * outputHeight() * 4} bytes
     * @throws UnsupportedOperationException if the backend cannot read back
     */
    default byte[] readBackOutput() {
        throw new UnsupportedOperationException("read-back not supported by this backend");
    }

    @Override
    void close();
}
