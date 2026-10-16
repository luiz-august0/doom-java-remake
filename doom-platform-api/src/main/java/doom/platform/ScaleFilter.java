package doom.platform;

/** Texture filtering used when scaling the frame (spec 04 §8). */
public enum ScaleFilter {
    NEAREST,
    /** Bilinear only across pixel edges: crisp pixels without shimmering at non-integer scales. */
    SHARP_BILINEAR
}
