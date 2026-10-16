package doom.game.view;

/** {@code ML_*} line flags. */
public final class LineFlags {
    public static final int BLOCKING = 1;
    public static final int BLOCKMONSTERS = 2;
    public static final int TWOSIDED = 4;
    public static final int DONTPEGTOP = 8;
    public static final int DONTPEGBOTTOM = 16;
    /** Drawn as one-sided on the automap. */
    public static final int SECRET = 32;
    public static final int SOUNDBLOCK = 64;
    /** Never drawn on the automap. */
    public static final int DONTDRAW = 128;
    /** Already seen; kept by the renderer, not by the simulation. */
    public static final int MAPPED = 256;

    private LineFlags() {
    }
}
