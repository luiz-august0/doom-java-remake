package doom.wad.map;

/**
 * One SEGS entry ({@code mapseg_t}, 12 bytes). All fields are signed 16-bit values; the loader turns {@code angle}
 * and {@code offset} into BAM and fixed point by shifting left 16 bits.
 *
 * @param v1      start vertex
 * @param v2      end vertex
 * @param angle   high 16 bits of the BAM angle
 * @param linedef the line this seg lies on
 * @param side    0 = front side of the line, 1 = back side
 * @param offset  distance along the line from its start to the seg start, map units
 */
public record MapSeg(int v1, int v2, int angle, int linedef, int side, int offset) {

    /** Bytes per entry. */
    public static final int SIZE = 12;
}
