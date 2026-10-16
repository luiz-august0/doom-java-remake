package doom.wad.demo;

/**
 * Constants of the demo (.lmp) format (spec 08 §1.1).
 *
 * <p><b>Skeleton (Wave 0):</b> owned and implemented by W1-A.
 */
public final class DemoFormat {
    /** Version byte written by DOOM v1.9 and by this remake. */
    public static final int VERSION_109 = 109;
    /** Version byte written by linuxdoom-1.10; read as 1.9. */
    public static final int VERSION_110 = 110;
    /** Header size in bytes for versions 109/110. */
    public static final int HEADER_SIZE = 13;
    /** Bytes per player per tic. */
    public static final int TICCMD_SIZE = 4;
    /** End-of-demo marker. */
    public static final int END_MARKER = 0x80;

    private DemoFormat() {
    }
}
