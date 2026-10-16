package doom.game.world;

import doom.wad.LumpDirectory;
import doom.wad.WadWriter;
import doom.wad.map.MapData;
import doom.wad.map.MapLinedef;
import doom.wad.map.MapNode;
import doom.wad.map.MapSector;
import doom.wad.map.MapSeg;
import doom.wad.map.MapSidedef;
import doom.wad.map.MapSubsector;
import doom.wad.map.MapThing;
import doom.wad.map.MapVertex;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A synthetic map of two 64x64 rooms side by side, joined by one two-sided line, with the textures and flats it
 * needs. The record lists are mutable so tests can break them before building.
 *
 * <pre>
 *   v5 --4-- v4 --3-- v3
 *    |  sec0  |  sec1  |
 *   v0 --0-- v1 --1-- v2     (line 5 closes the left side, line 2 the right side, line 6 is v1-v4)
 * </pre>
 */
final class TwoRoomMap {

    final List<MapVertex> vertexes = new ArrayList<>();
    final List<MapLinedef> linedefs = new ArrayList<>();
    final List<MapSidedef> sidedefs = new ArrayList<>();
    final List<MapSector> sectors = new ArrayList<>();
    final List<MapSeg> segs = new ArrayList<>();
    final List<MapSubsector> subsectors = new ArrayList<>();
    final List<MapNode> nodes = new ArrayList<>();
    final List<MapThing> things = new ArrayList<>();
    int[] blockmap;
    byte[] reject = new byte[0];

    TwoRoomMap() {
        int[][] v = {{0, 0}, {64, 0}, {128, 0}, {128, 64}, {64, 64}, {0, 64}};
        for (int[] p : v) {
            vertexes.add(new MapVertex(p[0], p[1]));
        }
        sectors.add(new MapSector(0, 128, "FLOOR", "CEIL", 160, 0, 0));
        sectors.add(new MapSector(16, 96, "FLOOR2", "F_SKY1", 200, 9, 3));
        int[] sideSector = {0, 1, 1, 1, 0, 0, 0, 1};
        for (int i = 0; i < 8; i++) {
            boolean twoSidedLine = i >= 6;
            sidedefs.add(new MapSidedef(i * 8, -i * 8, twoSidedLine ? "-" : "AAA", twoSidedLine ? "-" : "AAA",
                    twoSidedLine ? "-" : "WALL", sideSector[i]));
        }
        // lines 0..5 are one-sided (front side i), line 6 joins the rooms
        int[][] ends = {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 0}};
        for (int i = 0; i < 6; i++) {
            linedefs.add(new MapLinedef(ends[i][0], ends[i][1], 1, 0, 0, i, -1));
        }
        linedefs.add(new MapLinedef(1, 4, 4, 11, 5, 6, 7));
        // subsector 0 (sector 0), subsector 1 (sector 1)
        segs.add(new MapSeg(0, 1, 0, 0, 0, 0));
        segs.add(new MapSeg(1, 4, 0x4000, 6, 0, 0));
        segs.add(new MapSeg(4, 5, 0x8000 - 0x10000, 4, 0, 0));
        segs.add(new MapSeg(5, 0, -0x4000, 5, 0, 0));
        segs.add(new MapSeg(4, 1, -0x4000, 6, 1, 0));
        segs.add(new MapSeg(1, 2, 0, 1, 0, 0));
        segs.add(new MapSeg(2, 3, 0x4000, 2, 0, 0));
        segs.add(new MapSeg(3, 4, 0x8000 - 0x10000, 3, 0, 0));
        subsectors.add(new MapSubsector(4, 0));
        subsectors.add(new MapSubsector(4, 4));
        nodes.add(new MapNode(64, 0, 0, 64, 64, 0, 64, 128, 64, 0, 0, 64,
                MapNode.NF_SUBSECTOR | 1, MapNode.NF_SUBSECTOR));
        things.add(new MapThing(32, 32, 90, 1, 7));
        // origin (-64, -64), 2x2 blocks, every block lists lines 0..6
        blockmap = new int[] {-64, -64, 2, 2, 8, 8, 8, 8, 0, 1, 2, 3, 4, 5, 6, -1};
    }

    MapData data() {
        return new MapData("E1M1", things.toArray(new MapThing[0]), linedefs.toArray(new MapLinedef[0]),
                sidedefs.toArray(new MapSidedef[0]), vertexes.toArray(new MapVertex[0]),
                segs.toArray(new MapSeg[0]), subsectors.toArray(new MapSubsector[0]),
                nodes.toArray(new MapNode[0]), sectors.toArray(new MapSector[0]), reject, blockmap);
    }

    // ------------------------------------------------------------------------------ WAD with textures and flats

    /** Texture numbers in {@link #textureLump()}: AAA = 0, WALL = 1, SKY1 = 2. */
    LumpDirectory directory() {
        WadWriter w = WadWriter.iwad();
        w.add("PNAMES", pnames("P1"));
        w.add("TEXTURE1", textureLump());
        w.marker("P_START").add("P1", new byte[8]).marker("P_END");
        w.marker("F_START");
        for (String f : new String[] {"FLOOR", "CEIL", "FLOOR2", "F_SKY1"}) {
            w.add(f, new byte[4096]);
        }
        w.marker("F_END");
        w.marker("S_START").marker("S_END");
        w.marker("E1M1");
        w.add("THINGS", bytes(things.size() * MapThing.SIZE, b -> {
            for (MapThing t : things) {
                b.putShort((short) t.x()).putShort((short) t.y()).putShort((short) t.angle())
                        .putShort((short) t.type()).putShort((short) t.options());
            }
        }));
        w.add("LINEDEFS", bytes(linedefs.size() * MapLinedef.SIZE, b -> {
            for (MapLinedef l : linedefs) {
                b.putShort((short) l.v1()).putShort((short) l.v2()).putShort((short) l.flags())
                        .putShort((short) l.special()).putShort((short) l.tag()).putShort((short) l.frontSide())
                        .putShort((short) l.backSide());
            }
        }));
        w.add("SIDEDEFS", bytes(sidedefs.size() * MapSidedef.SIZE, b -> {
            for (MapSidedef s : sidedefs) {
                b.putShort((short) s.textureOffset()).putShort((short) s.rowOffset());
                name(b, s.topTexture());
                name(b, s.bottomTexture());
                name(b, s.midTexture());
                b.putShort((short) s.sector());
            }
        }));
        w.add("VERTEXES", bytes(vertexes.size() * MapVertex.SIZE, b -> {
            for (MapVertex v : vertexes) {
                b.putShort((short) v.x()).putShort((short) v.y());
            }
        }));
        w.add("SEGS", bytes(segs.size() * MapSeg.SIZE, b -> {
            for (MapSeg s : segs) {
                b.putShort((short) s.v1()).putShort((short) s.v2()).putShort((short) s.angle())
                        .putShort((short) s.linedef()).putShort((short) s.side()).putShort((short) s.offset());
            }
        }));
        w.add("SSECTORS", bytes(subsectors.size() * MapSubsector.SIZE, b -> {
            for (MapSubsector s : subsectors) {
                b.putShort((short) s.segCount()).putShort((short) s.firstSeg());
            }
        }));
        w.add("NODES", bytes(nodes.size() * MapNode.SIZE, b -> {
            for (MapNode n : nodes) {
                b.putShort((short) n.x()).putShort((short) n.y()).putShort((short) n.dx()).putShort((short) n.dy());
                for (int c = 0; c < 2; c++) {
                    for (int k = 0; k < 4; k++) {
                        b.putShort((short) n.bbox(c, k));
                    }
                }
                b.putShort((short) n.rightChild()).putShort((short) n.leftChild());
            }
        }));
        w.add("SECTORS", bytes(sectors.size() * MapSector.SIZE, b -> {
            for (MapSector s : sectors) {
                b.putShort((short) s.floorHeight()).putShort((short) s.ceilingHeight());
                name(b, s.floorPic());
                name(b, s.ceilingPic());
                b.putShort((short) s.lightLevel()).putShort((short) s.special()).putShort((short) s.tag());
            }
        }));
        w.add("REJECT", reject);
        w.add("BLOCKMAP", bytes(blockmap.length * 2, b -> {
            for (int v : blockmap) {
                b.putShort((short) v);
            }
        }));
        return LumpDirectory.of(List.of(w.toWadFile("two-rooms.wad")));
    }

    private interface Filler {
        void fill(ByteBuffer b);
    }

    private static byte[] bytes(int size, Filler f) {
        ByteBuffer b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        f.fill(b);
        return b.array();
    }

    private static void name(ByteBuffer b, String s) {
        byte[] raw = s.getBytes(StandardCharsets.ISO_8859_1);
        b.put(raw).put(new byte[8 - raw.length]);
    }

    private static byte[] pnames(String... names) {
        return bytes(4 + 8 * names.length, b -> {
            b.putInt(names.length);
            for (String n : names) {
                name(b, n);
            }
        });
    }

    private static byte[] textureLump() {
        String[] names = {"AAA", "WALL", "SKY1"};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBuffer head = ByteBuffer.allocate(4 + 4 * names.length).order(ByteOrder.LITTLE_ENDIAN);
        head.putInt(names.length);
        int pos = head.capacity();
        for (int i = 0; i < names.length; i++) {
            head.putInt(pos + i * 32);
        }
        out.writeBytes(head.array());
        for (String n : names) {
            out.writeBytes(bytes(32, b -> {
                name(b, n);
                b.putInt(0).putShort((short) 64).putShort((short) 128).putInt(0).putShort((short) 1);
                b.putShort((short) 0).putShort((short) 0).putShort((short) 0).putShort((short) 1)
                        .putShort((short) 0);
            }));
        }
        return out.toByteArray();
    }
}
