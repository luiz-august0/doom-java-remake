package doom.game.world;

/**
 * {@code node_t}. The partition line is fixed point; {@code children} are unsigned 16-bit values where bit 15
 * ({@code NF_SUBSECTOR}) marks a subsector.
 */
public final class Node {
    public int x;
    public int y;
    public int dx;
    public int dy;
    /** {@code bbox[child][BOXTOP...BOXRIGHT]}, fixed point; child 0 is the right (front) side. */
    public final int[][] bbox = new int[2][4];
    public final int[] children = new int[2];
}
