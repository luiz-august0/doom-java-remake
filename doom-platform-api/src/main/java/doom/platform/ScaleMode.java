package doom.platform;

/** How the internal frame is scaled to the output (spec 04 §8). */
public enum ScaleMode {
    /** Largest size that keeps the aspect ratio, with black bars. */
    FIT,
    /** Largest integer multiple that fits. */
    INTEGER,
    /** Fill the output, ignoring the aspect ratio. */
    STRETCH
}
