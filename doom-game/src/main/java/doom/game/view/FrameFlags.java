package doom.game.view;

/** Bits of a state frame number. */
public final class FrameFlags {
    /** {@code FF_FULLBRIGHT}: drawn at full brightness. */
    public static final int FULLBRIGHT = 0x8000;
    /** {@code FF_FRAMEMASK}. */
    public static final int FRAMEMASK = 0x7fff;

    private FrameFlags() {
    }
}
