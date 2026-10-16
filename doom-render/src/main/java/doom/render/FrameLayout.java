package doom.render;

/**
 * Memory layout of the indexed framebuffer (spec 04 §8). The renderer accesses pixels only through the layout.
 *
 * <p><b>Skeleton (Wave 0):</b> owned and implemented by W1-E.
 */
public enum FrameLayout {
    /** {@code pixels[y * width + x]}. */
    ROW_MAJOR,
    /** {@code pixels[x * height + y]}: columns are contiguous; transposed back in the shader. */
    COLUMN_MAJOR
}
