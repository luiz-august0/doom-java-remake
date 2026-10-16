package doom.game.world;

/** {@code seg_t}. {@code angle} is a BAM angle, {@code offset} fixed point. */
public final class Seg {
    public Vertex v1;
    public Vertex v2;
    public int offset;
    public int angle;
    public Side sideDef;
    public Line lineDef;
    public Sector frontSector;
    /** Null for one-sided lines; the level's null sector for the "glass hack" ({@code P_LoadSegs}). */
    public Sector backSector;
}
