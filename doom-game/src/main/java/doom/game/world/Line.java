package doom.game.world;

import doom.game.actor.Thinker;

/**
 * {@code line_t}. {@code flags}, {@code special} and {@code tag} keep the sign of the original {@code short}
 * fields; {@code sideNum} holds the side indices (-1 = none).
 */
public final class Line {
    /** {@code ST_HORIZONTAL}. */
    public static final int ST_HORIZONTAL = 0;
    /** {@code ST_VERTICAL}. */
    public static final int ST_VERTICAL = 1;
    /** {@code ST_POSITIVE}. */
    public static final int ST_POSITIVE = 2;
    /** {@code ST_NEGATIVE}. */
    public static final int ST_NEGATIVE = 3;

    /** Position in the LINEDEFS lump. */
    public int index;
    public Vertex v1;
    public Vertex v2;
    /** {@code v2 - v1}, fixed point. */
    public int dx;
    public int dy;
    public int flags;
    public int special;
    public int tag;
    /** {@code sidenum[2]}: front (0) and back (1) side index, -1 when absent. */
    public final int[] sideNum = new int[2];
    /** Bounding box, fixed point, indexed by {@code BBox.BOXTOP}.... */
    public final int[] bbox = new int[4];
    public int slopeType;
    public Sector frontSector;
    /** Null for one-sided lines. */
    public Sector backSector;
    /** {@code validcount}: marks the line as checked by the current blockmap / sight iteration. */
    public int validCount;
    /** {@code specialdata}: unused by vanilla DOOM's own code, kept for the special-line areas. */
    public Thinker specialData;
}
