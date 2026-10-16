package doom.game.world;

import doom.wad.map.MapThing;

/**
 * The geometry of one map as {@code P_SetupLevel} leaves it before things are spawned: the arrays of the original
 * ({@code vertexes}, {@code sectors}, {@code sides}, {@code lines}, {@code segs}, {@code subsectors}, {@code nodes}),
 * the blockmap, the reject matrix and the parsed THINGS lump. Fields are public and mutable because the simulation
 * changes them (heights, textures, special).
 */
public final class Level {
    /** Map marker lump name, upper case. */
    public String name;
    public Vertex[] vertexes;
    public Sector[] sectors;
    public Side[] sides;
    public Line[] lines;
    public Seg[] segs;
    public Subsector[] subsectors;
    public Node[] nodes;
    public Blockmap blockmap;
    public Reject reject;
    /** THINGS exactly as stored (spawn filtering is done by the spawner). */
    public MapThing[] things;
    /** {@code totallines}: sum of the line counts of all sectors. */
    public int totalLines;
    /** The sector {@code P_LoadSegs} substitutes for a missing back side (index -1). */
    public Sector nullSector;
}
