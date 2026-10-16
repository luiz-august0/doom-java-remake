package doom.game.world;

import doom.game.actor.Actor;
import doom.game.actor.Thinker;

/**
 * {@code sector_t}. Heights are 16.16 fixed point; {@code floorPic}/{@code ceilingPic} are flat numbers
 * ({@code lump - firstflat}).
 */
public final class Sector {
    /** Position in the SECTORS lump; -1 for the null sector of {@code P_LoadSegs}. */
    public int index;
    public int floorHeight;
    public int ceilingHeight;
    public int floorPic;
    public int ceilingPic;
    public int lightLevel;
    public int special;
    public int tag;
    /** {@code soundorg.x}: the middle of the bounding box, fixed point. */
    public int soundOrgX;
    /** {@code soundorg.y}: the middle of the bounding box, fixed point. */
    public int soundOrgY;
    /** Number of lines in {@link #lines} ({@code linecount}). */
    public int lineCount;
    /** Lines bordering the sector, in line-definition order ({@code lines[]}). */
    public Line[] lines;
    /** Bounding box in blockmap cells ({@code blockbox[]}, indexed by {@code BBox.BOXTOP}...). */
    public final int[] blockBox = new int[4];

    // ------------------------------------------------------------------ simulation state (W2-A)

    /** {@code validcount}: marks the sector as visited by the current sound propagation. */
    public int validCount;
    /** {@code soundtraversed}: 0 = not reached, 1 = reached, 2 = reached through one sound-blocking line. */
    public int soundTraversed;
    /** {@code soundtarget}: the thing that made the last noise heard here, or null. */
    public Actor soundTarget;
    /** {@code thinglist}: first thing of the sector's thing list (linked through {@code snext}). */
    public Actor thingList;
    /** {@code specialdata}: the sector effect thinker currently moving this sector, or null. */
    public Thinker specialData;

    // ------------------------------------------------------------------ presentation only (never read by the simulation)

    /** Floor height at the start of the last tic, for interpolation (ADR-011). */
    public int prevFloorHeight;
    /** Ceiling height at the start of the last tic, for interpolation (ADR-011). */
    public int prevCeilingHeight;
}
