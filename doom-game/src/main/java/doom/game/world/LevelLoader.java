package doom.game.world;

import doom.math.BBox;
import doom.math.Fixed;
import doom.math.annotation.VanillaRef;
import doom.wad.LumpDirectory;
import doom.wad.WadFormatException;
import doom.wad.map.MapData;
import doom.wad.map.MapLinedef;
import doom.wad.map.MapNode;
import doom.wad.map.MapSector;
import doom.wad.map.MapSeg;
import doom.wad.map.MapSidedef;
import doom.wad.map.MapSubsector;
import doom.wad.map.MapVertex;
import doom.wad.texture.FlatDirectory;
import doom.wad.texture.TextureDirectory;

/**
 * Builds the geometry of a map from its lumps, reproducing the order and the quirks of {@code P_SetupLevel}
 * (blockmap, vertexes, sectors, sidedefs, linedefs, subsectors, nodes, segs, {@code P_GroupLines}, reject) as
 * Chocolate Doom 3.1.1 implements them. Data that would make DOOM crash or read garbage (indices out of range,
 * lines without a front side, malformed blockmap) throws {@link WadFormatException} instead.
 */
public final class LevelLoader {

    /** {@code MAXRADIUS}: 32 map units. */
    private static final int MAXRADIUS = 32 * Fixed.FRACUNIT;
    /** {@code MAPBLOCKSHIFT = FRACBITS + 7}. */
    private static final int MAPBLOCKSHIFT = Fixed.FRACBITS + 7;
    /** First 8 bytes of DOS memory at address 0, DOS 6.22 (Chocolate's default): the "null sector" heights. */
    private static final int NULL_SECTOR_FLOOR = 0x00199257;
    private static final int NULL_SECTOR_CEILING = 0x007006F4;

    private static final System.Logger LOG = System.getLogger(LevelLoader.class.getName());

    private final TextureDirectory textures;
    private final FlatDirectory flats;

    public LevelLoader(TextureDirectory textures, FlatDirectory flats) {
        this.textures = textures;
        this.flats = flats;
    }

    /**
     * Reads the map lumps from {@code lumps} and builds the level.
     *
     * @throws WadFormatException when the map is missing or malformed
     */
    public Level load(LumpDirectory lumps, String mapName) {
        MapData data = MapData.load(lumps, mapName);
        return build(data, lumps.wadOf(lumps.find(mapName)).fileName());
    }

    /** Builds the level from decoded lumps; {@code file} only appears in error messages. */
    @VanillaRef("p_setup.c:P_SetupLevel")
    public Level build(MapData data, String file) {
        Level level = new Level();
        level.name = data.name();
        Ctx c = new Ctx(file, data.name());

        loadBlockMap(c, level, data.blockmap());
        loadVertexes(level, data.vertexes());
        loadSectors(c, level, data.sectors());
        loadSideDefs(c, level, data.sidedefs());
        loadLineDefs(c, level, data.linedefs());
        loadSubsectors(level, data.subsectors());
        loadNodes(c, level, data.nodes());
        loadSegs(c, level, data.segs());
        groupLines(c, level);
        checkBlockmapLists(c, level);
        loadReject(level, data.reject());
        level.things = data.things();
        return level;
    }

    /** Error context. */
    private record Ctx(String file, String map) {
        WadFormatException error(String lump, String message) {
            return new WadFormatException(file, map + " " + lump, -1, message);
        }
    }

    // ------------------------------------------------------------------------------------------ blockmap

    @VanillaRef("p_setup.c:P_LoadBlockMap")
    private static void loadBlockMap(Ctx c, Level level, int[] lump) {
        if (lump.length < Blockmap.TABLE_START) {
            throw c.error("BLOCKMAP", "lump too short for the header (" + lump.length * 2 + " bytes)");
        }
        Blockmap b = new Blockmap();
        b.lump = lump;
        b.originX = lump[0] << Fixed.FRACBITS;
        b.originY = lump[1] << Fixed.FRACBITS;
        b.columns = lump[2];
        b.rows = lump[3];
        if (b.columns < 0 || b.rows < 0) {
            throw c.error("BLOCKMAP", "negative size " + b.columns + "x" + b.rows);
        }
        long cells = (long) b.columns * b.rows;
        if (Blockmap.TABLE_START + cells > lump.length) {
            throw c.error("BLOCKMAP", "the offset table (" + b.columns + "x" + b.rows
                    + " blocks) does not fit in the lump");
        }
        level.blockmap = b;
    }

    /**
     * Checks what {@code P_BlockLinesIterator} will read: every block offset must lie inside the lump and lead to a
     * list of valid line numbers ending in -1. Run once the number of lines is known.
     */
    private static void checkBlockmapLists(Ctx c, Level level) {
        Blockmap b = level.blockmap;
        int[] lump = b.lump;
        int cells = b.columns * b.rows;
        int numLines = level.lines.length;
        for (int i = 0; i < cells; i++) {
            int offset = lump[Blockmap.TABLE_START + i];
            if (offset < 0 || offset >= lump.length) {
                throw c.error("BLOCKMAP", "block " + i + " points outside the lump (offset " + offset + ")");
            }
            int p = offset;
            while (true) {
                if (p >= lump.length) {
                    throw c.error("BLOCKMAP", "the line list of block " + i + " is not terminated");
                }
                int line = lump[p++];
                if (line == -1) {
                    break;
                }
                if (line < 0 || line >= numLines) {
                    throw c.error("BLOCKMAP", "block " + i + " lists line " + line + " but the map has "
                            + numLines);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------ vertexes

    @VanillaRef("p_setup.c:P_LoadVertexes")
    private static void loadVertexes(Level level, MapVertex[] src) {
        Vertex[] v = new Vertex[src.length];
        for (int i = 0; i < v.length; i++) {
            v[i] = new Vertex(src[i].x() << Fixed.FRACBITS, src[i].y() << Fixed.FRACBITS);
            v[i].index = i;
        }
        level.vertexes = v;
    }

    // ------------------------------------------------------------------------------------------ sectors

    @VanillaRef("p_setup.c:P_LoadSectors")
    private void loadSectors(Ctx c, Level level, MapSector[] src) {
        Sector[] s = new Sector[src.length];
        for (int i = 0; i < s.length; i++) {
            MapSector ms = src[i];
            Sector ss = new Sector();
            ss.index = i;
            ss.floorHeight = ms.floorHeight() << Fixed.FRACBITS;
            ss.ceilingHeight = ms.ceilingHeight() << Fixed.FRACBITS;
            ss.floorPic = flat(c, ms.floorPic(), i);
            ss.ceilingPic = flat(c, ms.ceilingPic(), i);
            ss.lightLevel = ms.lightLevel();
            ss.special = ms.special();
            ss.tag = ms.tag();
            s[i] = ss;
        }
        level.sectors = s;
    }

    private int flat(Ctx c, String name, int sector) {
        int f = flats.numForName(name);
        if (!flats.isValid(f)) {
            throw c.error("SECTORS", "sector " + sector + " uses " + name + ", which is not a flat");
        }
        return f;
    }

    // ------------------------------------------------------------------------------------------ sidedefs

    @VanillaRef("p_setup.c:P_LoadSideDefs")
    private void loadSideDefs(Ctx c, Level level, MapSidedef[] src) {
        Side[] sides = new Side[src.length];
        for (int i = 0; i < sides.length; i++) {
            MapSidedef msd = src[i];
            Side sd = new Side();
            sd.index = i;
            sd.textureOffset = msd.textureOffset() << Fixed.FRACBITS;
            sd.rowOffset = msd.rowOffset() << Fixed.FRACBITS;
            sd.topTexture = textures.numForName(msd.topTexture());
            sd.bottomTexture = textures.numForName(msd.bottomTexture());
            sd.midTexture = textures.numForName(msd.midTexture());
            if (msd.sector() < 0 || msd.sector() >= level.sectors.length) {
                throw c.error("SIDEDEFS", "sidedef " + i + " refers to sector " + msd.sector() + " but the map has "
                        + level.sectors.length);
            }
            sd.sector = level.sectors[msd.sector()];
            sides[i] = sd;
        }
        level.sides = sides;
    }

    // ------------------------------------------------------------------------------------------ linedefs

    @VanillaRef("p_setup.c:P_LoadLineDefs")
    private static void loadLineDefs(Ctx c, Level level, MapLinedef[] src) {
        Line[] lines = new Line[src.length];
        int numVertexes = level.vertexes.length;
        int numSides = level.sides.length;
        for (int i = 0; i < lines.length; i++) {
            MapLinedef mld = src[i];
            Line ld = new Line();
            ld.index = i;
            ld.flags = mld.flags();
            ld.special = mld.special();
            ld.tag = mld.tag();
            if (mld.v1() < 0 || mld.v1() >= numVertexes || mld.v2() < 0 || mld.v2() >= numVertexes) {
                throw c.error("LINEDEFS", "linedef " + i + " uses vertex " + mld.v1() + " or " + mld.v2()
                        + " but the map has " + numVertexes);
            }
            Vertex v1 = level.vertexes[mld.v1()];
            Vertex v2 = level.vertexes[mld.v2()];
            ld.v1 = v1;
            ld.v2 = v2;
            ld.dx = v2.x - v1.x;
            ld.dy = v2.y - v1.y;

            if (ld.dx == 0) {
                ld.slopeType = Line.ST_VERTICAL;
            } else if (ld.dy == 0) {
                ld.slopeType = Line.ST_HORIZONTAL;
            } else if (Fixed.div(ld.dy, ld.dx) > 0) {
                ld.slopeType = Line.ST_POSITIVE;
            } else {
                ld.slopeType = Line.ST_NEGATIVE;
            }

            if (v1.x < v2.x) {
                ld.bbox[BBox.BOXLEFT] = v1.x;
                ld.bbox[BBox.BOXRIGHT] = v2.x;
            } else {
                ld.bbox[BBox.BOXLEFT] = v2.x;
                ld.bbox[BBox.BOXRIGHT] = v1.x;
            }
            if (v1.y < v2.y) {
                ld.bbox[BBox.BOXBOTTOM] = v1.y;
                ld.bbox[BBox.BOXTOP] = v2.y;
            } else {
                ld.bbox[BBox.BOXBOTTOM] = v2.y;
                ld.bbox[BBox.BOXTOP] = v1.y;
            }

            ld.sideNum[0] = mld.frontSide();
            ld.sideNum[1] = mld.backSide();
            for (int s = 0; s < 2; s++) {
                int n = ld.sideNum[s];
                if (n != -1 && (n < 0 || n >= numSides)) {
                    throw c.error("LINEDEFS", "linedef " + i + " refers to sidedef " + n + " but the map has "
                            + numSides);
                }
            }
            if (ld.sideNum[0] != -1) {
                ld.frontSector = level.sides[ld.sideNum[0]].sector;
            } else {
                ld.frontSector = null;
            }
            if (ld.sideNum[1] != -1) {
                ld.backSector = level.sides[ld.sideNum[1]].sector;
            } else {
                ld.backSector = null;
            }
            lines[i] = ld;
        }
        level.lines = lines;
    }

    // ------------------------------------------------------------------------------------------ subsectors

    @VanillaRef("p_setup.c:P_LoadSubsectors")
    private static void loadSubsectors(Level level, MapSubsector[] src) {
        Subsector[] ss = new Subsector[src.length];
        for (int i = 0; i < ss.length; i++) {
            Subsector s = new Subsector();
            s.index = i;
            s.numLines = src[i].segCount();
            s.firstLine = src[i].firstSeg();
            ss[i] = s;
        }
        level.subsectors = ss;
    }

    // ------------------------------------------------------------------------------------------ nodes

    @VanillaRef("p_setup.c:P_LoadNodes")
    private static void loadNodes(Ctx c, Level level, MapNode[] src) {
        Node[] nodes = new Node[src.length];
        for (int i = 0; i < nodes.length; i++) {
            MapNode mn = src[i];
            Node no = new Node();
            no.x = mn.x() << Fixed.FRACBITS;
            no.y = mn.y() << Fixed.FRACBITS;
            no.dx = mn.dx() << Fixed.FRACBITS;
            no.dy = mn.dy() << Fixed.FRACBITS;
            for (int j = 0; j < 2; j++) {
                int child = mn.child(j);
                no.children[j] = child;
                if ((child & MapNode.NF_SUBSECTOR) != 0) {
                    int ss = child & ~MapNode.NF_SUBSECTOR;
                    if (ss >= level.subsectors.length) {
                        throw c.error("NODES", "node " + i + " refers to subsector " + ss + " but the map has "
                                + level.subsectors.length);
                    }
                } else if (child >= src.length) {
                    throw c.error("NODES", "node " + i + " refers to node " + child + " but the map has "
                            + src.length);
                }
                for (int k = 0; k < 4; k++) {
                    no.bbox[j][k] = mn.bbox(j, k) << Fixed.FRACBITS;
                }
            }
            nodes[i] = no;
        }
        level.nodes = nodes;
    }

    // ------------------------------------------------------------------------------------------ segs

    @VanillaRef("p_setup.c:P_LoadSegs")
    private static void loadSegs(Ctx c, Level level, MapSeg[] src) {
        Sector nullSector = new Sector();
        nullSector.index = -1;
        nullSector.floorHeight = NULL_SECTOR_FLOOR;
        nullSector.ceilingHeight = NULL_SECTOR_CEILING;
        nullSector.lines = new Line[0];
        level.nullSector = nullSector;

        Seg[] segs = new Seg[src.length];
        int numVertexes = level.vertexes.length;
        int numSides = level.sides.length;
        for (int i = 0; i < segs.length; i++) {
            MapSeg ml = src[i];
            Seg li = new Seg();
            if (ml.v1() < 0 || ml.v1() >= numVertexes || ml.v2() < 0 || ml.v2() >= numVertexes) {
                throw c.error("SEGS", "seg " + i + " uses vertex " + ml.v1() + " or " + ml.v2()
                        + " but the map has " + numVertexes);
            }
            li.v1 = level.vertexes[ml.v1()];
            li.v2 = level.vertexes[ml.v2()];
            li.angle = ml.angle() << Fixed.FRACBITS;
            li.offset = ml.offset() << Fixed.FRACBITS;
            int linedef = ml.linedef();
            if (linedef < 0 || linedef >= level.lines.length) {
                throw c.error("SEGS", "seg " + i + " refers to linedef " + linedef + " but the map has "
                        + level.lines.length);
            }
            Line ldef = level.lines[linedef];
            li.lineDef = ldef;
            int side = ml.side();
            if (side != 0 && side != 1) {
                throw c.error("SEGS", "seg " + i + " has side " + side);
            }
            // e6y: check for wrong indexes (the same test and message as Chocolate Doom)
            if (ldef.sideNum[side] < 0 || ldef.sideNum[side] >= numSides) {
                throw c.error("SEGS", "P_LoadSegs: linedef " + linedef + " for seg " + i
                        + " references a non-existent sidedef " + (ldef.sideNum[side] & 0xFFFFFFFFL));
            }
            li.sideDef = level.sides[ldef.sideNum[side]];
            li.frontSector = li.sideDef.sector;

            if ((ldef.flags & 4) != 0) { // ML_TWOSIDED
                int sidenum = ldef.sideNum[side ^ 1];
                // Out-of-range back side: the "glass hack" of OTTAWAU.WAD. Chocolate Doom points at the sector
                // found at address 0 of DOS memory.
                if (sidenum < 0 || sidenum >= numSides) {
                    li.backSector = nullSector;
                } else {
                    li.backSector = level.sides[sidenum].sector;
                }
            } else {
                li.backSector = null;
            }
            segs[i] = li;
        }
        level.segs = segs;
    }

    // ------------------------------------------------------------------------------------------ group lines

    @VanillaRef("p_setup.c:P_GroupLines")
    private static void groupLines(Ctx c, Level level) {
        Subsector[] subsectors = level.subsectors;
        Seg[] segs = level.segs;
        for (int i = 0; i < subsectors.length; i++) {
            Subsector ss = subsectors[i];
            if (ss.numLines < 0 || ss.firstLine < 0 || ss.firstLine >= segs.length
                    || ss.firstLine + ss.numLines > segs.length) {
                throw c.error("SSECTORS", "subsector " + i + " uses segs " + ss.firstLine + ".."
                        + (ss.firstLine + ss.numLines - 1) + " but the map has " + segs.length);
            }
            ss.sector = segs[ss.firstLine].sideDef.sector;
        }

        // count number of lines in each sector
        Line[] lines = level.lines;
        int totalLines = 0;
        for (int i = 0; i < lines.length; i++) {
            Line li = lines[i];
            if (li.frontSector == null) {
                throw c.error("LINEDEFS", "linedef " + i + " has no front sidedef (DOOM crashes on it)");
            }
            totalLines++;
            li.frontSector.lineCount++;
            if (li.backSector != null && li.backSector != li.frontSector) {
                li.backSector.lineCount++;
                totalLines++;
            }
        }
        level.totalLines = totalLines;

        // build line tables for each sector
        Sector[] sectors = level.sectors;
        for (Sector sector : sectors) {
            sector.lines = new Line[sector.lineCount];
            sector.lineCount = 0;
        }

        // assign lines to sectors, in line order
        for (Line li : lines) {
            Sector sector = li.frontSector;
            sector.lines[sector.lineCount++] = li;
            if (li.backSector != null && li.frontSector != li.backSector) {
                sector = li.backSector;
                sector.lines[sector.lineCount++] = li;
            }
        }

        // bounding boxes, sound origins and block boxes
        Blockmap bm = level.blockmap;
        int[] bbox = new int[4];
        for (Sector sector : sectors) {
            BBox.clear(bbox);
            for (int j = 0; j < sector.lineCount; j++) {
                Line li = sector.lines[j];
                BBox.add(bbox, li.v1.x, li.v1.y);
                BBox.add(bbox, li.v2.x, li.v2.y);
            }

            // set the degenmobj_t to the middle of the bounding box
            sector.soundOrgX = (bbox[BBox.BOXRIGHT] + bbox[BBox.BOXLEFT]) / 2;
            sector.soundOrgY = (bbox[BBox.BOXTOP] + bbox[BBox.BOXBOTTOM]) / 2;

            // adjust bounding box to map blocks
            int block = (bbox[BBox.BOXTOP] - bm.originY + MAXRADIUS) >> MAPBLOCKSHIFT;
            block = block >= bm.rows ? bm.rows - 1 : block;
            sector.blockBox[BBox.BOXTOP] = block;

            block = (bbox[BBox.BOXBOTTOM] - bm.originY - MAXRADIUS) >> MAPBLOCKSHIFT;
            block = block < 0 ? 0 : block;
            sector.blockBox[BBox.BOXBOTTOM] = block;

            block = (bbox[BBox.BOXRIGHT] - bm.originX + MAXRADIUS) >> MAPBLOCKSHIFT;
            block = block >= bm.columns ? bm.columns - 1 : block;
            sector.blockBox[BBox.BOXRIGHT] = block;

            block = (bbox[BBox.BOXLEFT] - bm.originX - MAXRADIUS) >> MAPBLOCKSHIFT;
            block = block < 0 ? 0 : block;
            sector.blockBox[BBox.BOXLEFT] = block;
        }
    }

    // ------------------------------------------------------------------------------------------ reject

    @VanillaRef("p_setup.c:P_LoadReject")
    private static void loadReject(Level level, byte[] lump) {
        int numSectors = level.sectors.length;
        // The size the REJECT lump should have.
        int minLength = (numSectors * numSectors + 7) / 8;
        if (lump.length >= minLength) {
            level.reject = new Reject(lump);
            return;
        }
        byte[] matrix = new byte[minLength];
        System.arraycopy(lump, 0, matrix, 0, lump.length);
        padRejectArray(matrix, lump.length, minLength - lump.length, level.totalLines);
        level.reject = new Reject(matrix);
    }

    /**
     * Simulates the REJECT buffer overflow of Vanilla DOOM: the missing bytes read the header of the next zone block
     * (size, id, tag, zone id). Beyond the 16 known bytes the array is padded with zeros.
     */
    @VanillaRef("p_setup.c:PadRejectArray")
    private static void padRejectArray(byte[] array, int from, int len, int totalLines) {
        int[] rejectPad = {
            ((totalLines * 4 + 3) & ~3) + 24, // size
            0, // part of the z_zone block header
            50, // PU_LEVEL
            0x1d4a11 // DOOM_CONST_ZONEID
        };
        for (int i = 0; i < len && i < 16; i++) {
            array[from + i] = (byte) (rejectPad[i / 4] >>> ((i % 4) * 8));
        }
        if (len > 16) {
            LOG.log(System.Logger.Level.WARNING,
                    "PadRejectArray: REJECT lump too short to pad! ({0} > 16)", len);
        }
    }
}
