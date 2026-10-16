package doom.wad.map;

/**
 * One SSECTORS entry ({@code mapsubsector_t}, 4 bytes): signed 16-bit seg count and first seg.
 */
public record MapSubsector(int segCount, int firstSeg) {

    /** Bytes per entry. */
    public static final int SIZE = 4;
}
