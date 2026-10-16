package doom.game.world;

/**
 * The blockmap: {@code blockmaplump} (signed 16-bit values), origin and size ({@code P_LoadBlockMap}). The offset
 * table starts at lump index {@link #TABLE_START}; each offset is counted from the start of the lump and addresses a
 * line list ending in -1.
 */
public final class Blockmap {
    /** {@code blockmap = blockmaplump + 4}. */
    public static final int TABLE_START = 4;

    /** {@code blockmaplump[]}. */
    public int[] lump;
    /** {@code bmaporgx}, fixed point. */
    public int originX;
    /** {@code bmaporgy}, fixed point. */
    public int originY;
    /** {@code bmapwidth}. */
    public int columns;
    /** {@code bmapheight}. */
    public int rows;

    /** Offset (into {@link #lump}) of the line list of block ({@code x}, {@code y}). */
    public int listOffset(int x, int y) {
        return lump[TABLE_START + y * columns + x];
    }
}
