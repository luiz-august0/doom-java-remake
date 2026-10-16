package doom.ui;

/**
 * Values shown by the debug overlay (spec 07 §2); the engine fills it each frame. Mutable on purpose (no allocation).
 */
public final class DebugInfo {
    public double fps;
    public double frameMillis;
    public int tic;
    public int audioUnderruns;
    public int internalWidth;
    public int internalHeight;
    /** Extra free-form line (may be null). */
    public String extra;
}
