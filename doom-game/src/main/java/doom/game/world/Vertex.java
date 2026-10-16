package doom.game.world;

/** {@code vertex_t}: a map vertex in 16.16 fixed point. */
public final class Vertex {
    /** Position in the VERTEXES lump. */
    public int index;
    public int x;
    public int y;

    public Vertex(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
