package doom.wad.map;

import doom.wad.LumpReader;

/**
 * Decoders for the fixed-size map lumps. Like DOOM ({@code W_LumpLength / sizeof(record)}), the number of entries is
 * the lump length divided by the record size and a trailing partial record is ignored. Values keep the signedness of
 * the original structures (see the record classes); range checks that need the other lumps are done by the level
 * loader.
 */
public final class MapLumps {

    private MapLumps() {
    }

    /** {@code THINGS}. */
    public static MapThing[] things(LumpReader r) {
        MapThing[] out = new MapThing[r.length() / MapThing.SIZE];
        for (int i = 0; i < out.length; i++) {
            int at = i * MapThing.SIZE;
            out[i] = new MapThing(r.s16(at), r.s16(at + 2), r.s16(at + 4), r.s16(at + 6), r.s16(at + 8));
        }
        return out;
    }

    /** {@code LINEDEFS}. */
    public static MapLinedef[] linedefs(LumpReader r) {
        MapLinedef[] out = new MapLinedef[r.length() / MapLinedef.SIZE];
        for (int i = 0; i < out.length; i++) {
            int at = i * MapLinedef.SIZE;
            out[i] = new MapLinedef(r.s16(at), r.s16(at + 2), r.s16(at + 4), r.s16(at + 6), r.s16(at + 8),
                    r.s16(at + 10), r.s16(at + 12));
        }
        return out;
    }

    /** {@code SIDEDEFS}. */
    public static MapSidedef[] sidedefs(LumpReader r) {
        MapSidedef[] out = new MapSidedef[r.length() / MapSidedef.SIZE];
        for (int i = 0; i < out.length; i++) {
            int at = i * MapSidedef.SIZE;
            out[i] = new MapSidedef(r.s16(at), r.s16(at + 2), r.name8(at + 4), r.name8(at + 12), r.name8(at + 20),
                    r.s16(at + 28));
        }
        return out;
    }

    /** {@code VERTEXES}. */
    public static MapVertex[] vertexes(LumpReader r) {
        MapVertex[] out = new MapVertex[r.length() / MapVertex.SIZE];
        for (int i = 0; i < out.length; i++) {
            int at = i * MapVertex.SIZE;
            out[i] = new MapVertex(r.s16(at), r.s16(at + 2));
        }
        return out;
    }

    /** {@code SEGS}. */
    public static MapSeg[] segs(LumpReader r) {
        MapSeg[] out = new MapSeg[r.length() / MapSeg.SIZE];
        for (int i = 0; i < out.length; i++) {
            int at = i * MapSeg.SIZE;
            out[i] = new MapSeg(r.s16(at), r.s16(at + 2), r.s16(at + 4), r.s16(at + 6), r.s16(at + 8),
                    r.s16(at + 10));
        }
        return out;
    }

    /** {@code SSECTORS}. */
    public static MapSubsector[] subsectors(LumpReader r) {
        MapSubsector[] out = new MapSubsector[r.length() / MapSubsector.SIZE];
        for (int i = 0; i < out.length; i++) {
            int at = i * MapSubsector.SIZE;
            out[i] = new MapSubsector(r.s16(at), r.s16(at + 2));
        }
        return out;
    }

    /** {@code NODES}. */
    public static MapNode[] nodes(LumpReader r) {
        MapNode[] out = new MapNode[r.length() / MapNode.SIZE];
        for (int i = 0; i < out.length; i++) {
            int a = i * MapNode.SIZE;
            out[i] = new MapNode(r.s16(a), r.s16(a + 2), r.s16(a + 4), r.s16(a + 6),
                    r.s16(a + 8), r.s16(a + 10), r.s16(a + 12), r.s16(a + 14),
                    r.s16(a + 16), r.s16(a + 18), r.s16(a + 20), r.s16(a + 22),
                    r.u16(a + 24), r.u16(a + 26));
        }
        return out;
    }

    /** {@code SECTORS}. */
    public static MapSector[] sectors(LumpReader r) {
        MapSector[] out = new MapSector[r.length() / MapSector.SIZE];
        for (int i = 0; i < out.length; i++) {
            int at = i * MapSector.SIZE;
            out[i] = new MapSector(r.s16(at), r.s16(at + 2), r.name8(at + 4), r.name8(at + 12), r.s16(at + 20),
                    r.s16(at + 22), r.s16(at + 24));
        }
        return out;
    }

    /**
     * {@code BLOCKMAP} as signed 16-bit values ({@code short *blockmaplump}): header (origin x, origin y, columns,
     * rows), one offset per block (counted from the start of the lump), then the line lists ending in -1.
     * A lump shorter than the 8-byte header is malformed.
     */
    public static int[] blockmap(LumpReader r) {
        r.require(0, 8, "blockmap header");
        int[] out = new int[r.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = r.s16(i * 2);
        }
        return out;
    }
}
