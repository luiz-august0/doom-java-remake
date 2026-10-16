package doom.wad.map;

/**
 * One VERTEXES entry ({@code mapvertex_t}, 4 bytes): signed 16-bit map units, converted to fixed point by the loader.
 */
public record MapVertex(int x, int y) {

    /** Bytes per entry. */
    public static final int SIZE = 4;
}
