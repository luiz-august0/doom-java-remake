package doom.game.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import doom.math.BBox;
import doom.wad.LumpDirectory;
import doom.wad.WadFormatException;
import doom.wad.map.MapLinedef;
import doom.wad.map.MapNode;
import doom.wad.map.MapSeg;
import doom.wad.map.MapSidedef;
import doom.wad.map.MapSubsector;
import doom.wad.map.MapVertex;
import doom.wad.texture.FlatDirectory;
import doom.wad.texture.TextureDirectory;
import org.junit.jupiter.api.Test;

class LevelLoaderTest {

    private static final int FU = 1 << 16;

    private static Level load(TwoRoomMap m) {
        LumpDirectory d = m.directory();
        return new LevelLoader(TextureDirectory.load(d), FlatDirectory.load(d)).build(m.data(), "two-rooms.wad");
    }

    private static void assertMalformed(TwoRoomMap m, String... fragments) {
        assertThatThrownBy(() -> load(m)).isInstanceOf(WadFormatException.class)
                .hasMessageContainingAll(fragments);
    }

    @Test
    void convertsUnitsAndResolvesNames() {
        Level l = load(new TwoRoomMap());
        assertThat(l.vertexes).hasSize(6);
        assertThat(l.vertexes[2].x).isEqualTo(128 * FU);
        assertThat(l.vertexes[2].index).isEqualTo(2);
        Sector s1 = l.sectors[1];
        assertThat(s1.floorHeight).isEqualTo(16 * FU);
        assertThat(s1.ceilingHeight).isEqualTo(96 * FU);
        assertThat(s1.floorPic).isEqualTo(2); // FLOOR2
        assertThat(s1.ceilingPic).isEqualTo(3); // F_SKY1
        assertThat(s1.lightLevel).isEqualTo(200);
        assertThat(s1.special).isEqualTo(9);
        assertThat(s1.tag).isEqualTo(3);
        Side side = l.sides[3];
        assertThat(side.textureOffset).isEqualTo(24 * FU);
        assertThat(side.rowOffset).isEqualTo(-24 * FU);
        assertThat(side.topTexture).isZero(); // AAA
        assertThat(side.midTexture).isEqualTo(1); // WALL
        assertThat(l.sides[6].midTexture).isZero(); // "-"
        assertThat(side.sector).isSameAs(l.sectors[1]);
        assertThat(l.things).hasSize(1);
        assertThat(l.name).isEqualTo("E1M1");
    }

    @Test
    void linesGetDeltasBoxesSlopesAndSectors() {
        TwoRoomMap m = new TwoRoomMap();
        m.vertexes.add(new MapVertex(0, 0)); // 6
        m.vertexes.add(new MapVertex(10, 10)); // 7
        m.vertexes.add(new MapVertex(10, -10)); // 8
        m.linedefs.add(new MapLinedef(6, 7, 0, 0, 0, 0, -1));
        m.linedefs.add(new MapLinedef(6, 8, 0, 0, 0, 0, -1));
        Level l = load(m);
        Line bottom = l.lines[0];
        assertThat(bottom.dx).isEqualTo(64 * FU);
        assertThat(bottom.dy).isZero();
        assertThat(bottom.slopeType).isEqualTo(Line.ST_HORIZONTAL);
        assertThat(l.lines[6].slopeType).isEqualTo(Line.ST_VERTICAL);
        assertThat(l.lines[7].slopeType).isEqualTo(Line.ST_POSITIVE);
        assertThat(l.lines[8].slopeType).isEqualTo(Line.ST_NEGATIVE);
        Line mid = l.lines[6];
        assertThat(mid.bbox[BBox.BOXLEFT]).isEqualTo(64 * FU);
        assertThat(mid.bbox[BBox.BOXRIGHT]).isEqualTo(64 * FU);
        assertThat(mid.bbox[BBox.BOXBOTTOM]).isZero();
        assertThat(mid.bbox[BBox.BOXTOP]).isEqualTo(64 * FU);
        assertThat(mid.flags).isEqualTo(4);
        assertThat(mid.special).isEqualTo(11);
        assertThat(mid.tag).isEqualTo(5);
        assertThat(mid.frontSector).isSameAs(l.sectors[0]);
        assertThat(mid.backSector).isSameAs(l.sectors[1]);
        assertThat(mid.sideNum).containsExactly(6, 7);
        assertThat(bottom.backSector).isNull();
        // the reversed line keeps the box ordered
        assertThat(l.lines[8].bbox[BBox.BOXBOTTOM]).isEqualTo(-10 * FU);
        assertThat(l.lines[8].bbox[BBox.BOXTOP]).isZero();
    }

    @Test
    void segsGetAnglesOffsetsAndBackSectors() {
        TwoRoomMap m = new TwoRoomMap();
        m.segs.set(1, new MapSeg(1, 4, -16384, 6, 0, 24));
        Level l = load(m);
        Seg s = l.segs[1];
        assertThat(s.angle).isEqualTo(-16384 << 16);
        assertThat(s.offset).isEqualTo(24 * FU);
        assertThat(s.sideDef).isSameAs(l.sides[6]);
        assertThat(s.lineDef).isSameAs(l.lines[6]);
        assertThat(s.frontSector).isSameAs(l.sectors[0]);
        assertThat(s.backSector).isSameAs(l.sectors[1]);
        Seg back = l.segs[4]; // same line, back side
        assertThat(back.sideDef).isSameAs(l.sides[7]);
        assertThat(back.frontSector).isSameAs(l.sectors[1]);
        assertThat(back.backSector).isSameAs(l.sectors[0]);
        assertThat(l.segs[0].backSector).isNull(); // one-sided line
        assertThat(l.segs[0].v2).isSameAs(l.vertexes[1]);
    }

    @Test
    void glassHackPointsAtTheNullSector() {
        TwoRoomMap m = new TwoRoomMap();
        // two-sided flag but no back side: ML_TWOSIDED with sidenum[1] = -1
        m.linedefs.set(6, new MapLinedef(1, 4, 4, 0, 0, 6, -1));
        m.segs.set(4, new MapSeg(1, 2, 0, 1, 0, 0)); // the back-side seg of line 6 can no longer exist
        Level l = load(m);
        Seg s = l.segs[1];
        assertThat(s.backSector).isSameAs(l.nullSector);
        assertThat(l.nullSector.index).isEqualTo(-1);
        assertThat(l.nullSector.floorHeight).isEqualTo(0x00199257);
        assertThat(l.nullSector.ceilingHeight).isEqualTo(0x007006F4);
        // the line itself has no back sector, so it is not counted twice in P_GroupLines
        assertThat(l.lines[6].backSector).isNull();
        assertThat(l.sectors[1].lineCount).isEqualTo(3);
    }

    @Test
    void sectorLinesFollowLineOrderAndBoxesAreComputed() {
        Level l = load(new TwoRoomMap());
        assertThat(lineIndices(l.sectors[0])).containsExactly(0, 4, 5, 6);
        assertThat(lineIndices(l.sectors[1])).containsExactly(1, 2, 3, 6);
        assertThat(l.totalLines).isEqualTo(8);
        assertThat(l.subsectors[0].sector).isSameAs(l.sectors[0]);
        assertThat(l.subsectors[1].sector).isSameAs(l.sectors[1]);

        Sector s0 = l.sectors[0];
        assertThat(s0.soundOrgX).isEqualTo(32 * FU);
        assertThat(s0.soundOrgY).isEqualTo(32 * FU);
        assertThat(l.sectors[1].soundOrgX).isEqualTo(96 * FU);
        // blockmap origin (-64,-64), 128-unit blocks, MAXRADIUS 32
        assertThat(s0.blockBox[BBox.BOXTOP]).isEqualTo(1);
        assertThat(s0.blockBox[BBox.BOXBOTTOM]).isEqualTo(0);
        assertThat(s0.blockBox[BBox.BOXLEFT]).isEqualTo(0);
        assertThat(s0.blockBox[BBox.BOXRIGHT]).isEqualTo(1);
        Sector s1 = l.sectors[1];
        assertThat(s1.blockBox[BBox.BOXLEFT]).isEqualTo(0);
        assertThat(s1.blockBox[BBox.BOXRIGHT]).isEqualTo(1);
    }

    private static int[] lineIndices(Sector s) {
        int[] out = new int[s.lineCount];
        for (int i = 0; i < out.length; i++) {
            out[i] = s.lines[i].index;
        }
        return out;
    }

    @Test
    void blockmapAndNodesAreLoaded() {
        Level l = load(new TwoRoomMap());
        assertThat(l.blockmap.originX).isEqualTo(-64 * FU);
        assertThat(l.blockmap.originY).isEqualTo(-64 * FU);
        assertThat(l.blockmap.columns).isEqualTo(2);
        assertThat(l.blockmap.rows).isEqualTo(2);
        assertThat(l.blockmap.listOffset(1, 1)).isEqualTo(8);
        assertThat(l.nodes).hasSize(1);
        Node n = l.nodes[0];
        assertThat(n.x).isEqualTo(64 * FU);
        assertThat(n.dy).isEqualTo(64 * FU);
        assertThat(n.children[0]).isEqualTo(MapNode.NF_SUBSECTOR | 1);
        assertThat(n.children[1]).isEqualTo(MapNode.NF_SUBSECTOR);
        assertThat(n.bbox[0][BBox.BOXTOP]).isEqualTo(64 * FU);
        assertThat(n.bbox[1][BBox.BOXRIGHT]).isEqualTo(64 * FU);
    }

    @Test
    void shortRejectIsPaddedLikeChocolateDoom() {
        Level l = load(new TwoRoomMap()); // 2 sectors need 1 byte; the lump is empty
        assertThat(l.reject.matrix).hasSize(1);
        assertThat(l.reject.matrix[0]).isEqualTo((byte) (((8 * 4 + 3) & ~3) + 24)); // 56

        TwoRoomMap longer = new TwoRoomMap();
        longer.reject = new byte[] {1, 2, 3};
        assertThat(load(longer).reject.matrix).containsExactly(1, 2, 3);

        // enough sectors that the missing part exceeds the 16 known bytes
        TwoRoomMap many = new TwoRoomMap();
        for (int i = 2; i < 40; i++) {
            many.sectors.add(many.sectors.get(0));
        }
        Level big = load(many); // 40 sectors: 200 bytes
        assertThat(big.reject.matrix).hasSize(200);
        assertThat(big.reject.matrix[0]).isEqualTo((byte) 56);
        assertThat(big.reject.matrix[8]).isEqualTo((byte) 50);
        assertThat(big.reject.matrix[12]).isEqualTo((byte) 0x11);
        assertThat(big.reject.matrix[13]).isEqualTo((byte) 0x4a);
        assertThat(big.reject.matrix[14]).isEqualTo((byte) 0x1d);
        assertThat(big.reject.matrix[16]).isZero();
        assertThat(big.reject.matrix[199]).isZero();
    }

    // ------------------------------------------------------------------------------ malformed data

    @Test
    void badSectorIndexInASidedef() {
        TwoRoomMap m = new TwoRoomMap();
        m.sidedefs.set(2, new MapSidedef(0, 0, "-", "-", "-", 5));
        assertMalformed(m, "two-rooms.wad", "SIDEDEFS", "sidedef 2", "sector 5");
    }

    @Test
    void unknownTextureAndFlatNames() {
        TwoRoomMap m = new TwoRoomMap();
        m.sidedefs.set(0, new MapSidedef(0, 0, "NOPE", "-", "-", 0));
        assertMalformed(m, "R_TextureNumForName: NOPE not found");

        TwoRoomMap f = new TwoRoomMap();
        f.sectors.set(0, new doom.wad.map.MapSector(0, 8, "NOFLAT", "CEIL", 0, 0, 0));
        assertMalformed(f, "R_FlatNumForName: NOFLAT not found");

        TwoRoomMap notFlat = new TwoRoomMap();
        notFlat.sectors.set(0, new doom.wad.map.MapSector(0, 8, "PNAMES", "CEIL", 0, 0, 0));
        assertMalformed(notFlat, "SECTORS", "not a flat");
    }

    @Test
    void badVertexAndSideIndicesInLines() {
        TwoRoomMap v = new TwoRoomMap();
        v.linedefs.set(0, new MapLinedef(0, 99, 1, 0, 0, 0, -1));
        assertMalformed(v, "LINEDEFS", "linedef 0", "vertex");

        TwoRoomMap s = new TwoRoomMap();
        s.linedefs.set(0, new MapLinedef(0, 1, 1, 0, 0, 40, -1));
        assertMalformed(s, "LINEDEFS", "sidedef 40");

        TwoRoomMap noFront = new TwoRoomMap();
        noFront.linedefs.add(new MapLinedef(0, 1, 1, 0, 0, -1, -1)); // no seg uses it
        assertMalformed(noFront, "linedef 7", "no front");
    }

    @Test
    void badSegReferences() {
        TwoRoomMap v = new TwoRoomMap();
        v.segs.set(0, new MapSeg(0, 77, 0, 0, 0, 0));
        assertMalformed(v, "SEGS", "seg 0", "vertex");

        TwoRoomMap line = new TwoRoomMap();
        line.segs.set(0, new MapSeg(0, 1, 0, 50, 0, 0));
        assertMalformed(line, "SEGS", "linedef 50");

        TwoRoomMap noSide = new TwoRoomMap();
        noSide.segs.set(0, new MapSeg(0, 1, 0, 0, 1, 0)); // line 0 has no back side
        assertMalformed(noSide, "P_LoadSegs: linedef 0 for seg 0 references a non-existent sidedef 4294967295");

        TwoRoomMap badSide = new TwoRoomMap();
        badSide.segs.set(0, new MapSeg(0, 1, 0, 0, 2, 0));
        assertMalformed(badSide, "seg 0 has side 2");
    }

    @Test
    void badSubsectorsNodesAndBlockmap() {
        TwoRoomMap ss = new TwoRoomMap();
        ss.subsectors.set(1, new MapSubsector(5, 4));
        assertMalformed(ss, "SSECTORS", "subsector 1");

        TwoRoomMap neg = new TwoRoomMap();
        neg.subsectors.set(0, new MapSubsector(-1, 0));
        assertMalformed(neg, "subsector 0");

        TwoRoomMap node = new TwoRoomMap();
        node.nodes.set(0, new MapNode(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, MapNode.NF_SUBSECTOR | 9, 0));
        assertMalformed(node, "NODES", "subsector 9");

        TwoRoomMap node2 = new TwoRoomMap();
        node2.nodes.set(0, new MapNode(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 0));
        assertMalformed(node2, "NODES", "node 3");

        TwoRoomMap tiny = new TwoRoomMap();
        tiny.blockmap = new int[] {0, 0, 1};
        assertMalformed(tiny, "BLOCKMAP", "header");

        TwoRoomMap table = new TwoRoomMap();
        table.blockmap = new int[] {0, 0, 100, 100, 8};
        assertMalformed(table, "BLOCKMAP", "does not fit");

        TwoRoomMap offset = new TwoRoomMap();
        offset.blockmap = new int[] {-64, -64, 2, 2, 8, 8, 8, 99, 0, 1, 2, 3, 4, 5, 6, -1};
        assertMalformed(offset, "BLOCKMAP", "block 3", "outside");

        TwoRoomMap line = new TwoRoomMap();
        line.blockmap = new int[] {-64, -64, 2, 2, 8, 8, 8, 8, 0, 1, 2, 3, 4, 5, 6, 7, -1};
        assertMalformed(line, "BLOCKMAP", "lists line 7");

        TwoRoomMap open = new TwoRoomMap();
        open.blockmap = new int[] {-64, -64, 2, 2, 8, 8, 8, 8, 0, 1, 2};
        assertMalformed(open, "BLOCKMAP", "not terminated");
    }
}
