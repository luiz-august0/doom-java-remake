package doom.wad.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import doom.wad.LumpDirectory;
import doom.wad.LumpReader;
import doom.wad.WadFormatException;
import doom.wad.WadWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class MapLumpsTest {

    private static ByteBuffer le(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static LumpReader reader(byte[] bytes) {
        return new LumpReader(ByteBuffer.wrap(bytes), "t.wad", "TEST");
    }

    private static void name(ByteBuffer b, String s) {
        byte[] raw = s.getBytes(StandardCharsets.ISO_8859_1);
        b.put(raw);
        b.put(new byte[8 - raw.length]);
    }

    @Test
    void thingsKeepSignedValues() {
        ByteBuffer b = le(20);
        b.putShort((short) -64).putShort((short) 32).putShort((short) 90).putShort((short) 3004).putShort((short) 7);
        b.putShort((short) 0).putShort((short) 0).putShort((short) 0).putShort((short) 1).putShort((short) 0x8001);
        MapThing[] t = MapLumps.things(reader(b.array()));
        assertThat(t).hasSize(2);
        assertThat(t[0]).isEqualTo(new MapThing(-64, 32, 90, 3004, 7));
        assertThat(t[1].options()).isEqualTo(-32767);
    }

    @Test
    void trailingPartialRecordIsIgnoredLikeVanilla() {
        assertThat(MapLumps.vertexes(reader(new byte[10]))).hasSize(2);
        assertThat(MapLumps.vertexes(reader(new byte[3]))).isEmpty();
    }

    @Test
    void linedefSideNumbersAreSigned() {
        ByteBuffer b = le(14);
        b.putShort((short) 1).putShort((short) 2).putShort((short) 5).putShort((short) 11).putShort((short) 9);
        b.putShort((short) 0).putShort((short) 0xFFFF);
        MapLinedef l = MapLumps.linedefs(reader(b.array()))[0];
        assertThat(l.backSide()).isEqualTo(-1);
        assertThat(l.side(0)).isZero();
        assertThat(l.side(1)).isEqualTo(-1);
        assertThat(l.special()).isEqualTo(11);
        assertThat(l.tag()).isEqualTo(9);
    }

    @Test
    void sidedefsAndSectorsReadNames() {
        ByteBuffer b = le(30);
        b.putShort((short) -8).putShort((short) 16);
        name(b, "UPPER");
        name(b, "-");
        name(b, "Mid");
        b.putShort((short) 3);
        MapSidedef s = MapLumps.sidedefs(reader(b.array()))[0];
        assertThat(s.textureOffset()).isEqualTo(-8);
        assertThat(s.topTexture()).isEqualTo("UPPER");
        assertThat(s.bottomTexture()).isEqualTo("-");
        assertThat(s.midTexture()).isEqualTo("Mid"); // original case kept
        assertThat(s.sector()).isEqualTo(3);

        ByteBuffer c = le(26);
        c.putShort((short) -16).putShort((short) 128);
        name(c, "FLOOR4_8");
        name(c, "CEIL3_5");
        c.putShort((short) 255).putShort((short) 9).putShort((short) 4);
        MapSector sec = MapLumps.sectors(reader(c.array()))[0];
        assertThat(sec).isEqualTo(new MapSector(-16, 128, "FLOOR4_8", "CEIL3_5", 255, 9, 4));
    }

    @Test
    void nodeChildrenAreUnsigned() {
        ByteBuffer b = le(28);
        for (int i = 0; i < 12; i++) {
            b.putShort((short) (i + 1));
        }
        b.putShort((short) 0x8003).putShort((short) 0xFFFF);
        MapNode n = MapLumps.nodes(reader(b.array()))[0];
        assertThat(n.rightChild()).isEqualTo(0x8003);
        assertThat(n.leftChild()).isEqualTo(0xFFFF);
        assertThat(n.bbox(0, 0)).isEqualTo(5); // right top
        assertThat(n.bbox(1, 3)).isEqualTo(12); // left right
        assertThat(n.child(0) & MapNode.NF_SUBSECTOR).isNotZero();
    }

    @Test
    void segsAndSubsectors() {
        ByteBuffer b = le(12);
        b.putShort((short) 4).putShort((short) 5).putShort((short) -16384).putShort((short) 6).putShort((short) 1)
                .putShort((short) 20);
        MapSeg s = MapLumps.segs(reader(b.array()))[0];
        assertThat(s).isEqualTo(new MapSeg(4, 5, -16384, 6, 1, 20));
        ByteBuffer c = le(4);
        c.putShort((short) 3).putShort((short) 7);
        assertThat(MapLumps.subsectors(reader(c.array()))[0]).isEqualTo(new MapSubsector(3, 7));
    }

    @Test
    void blockmapIsSignedShorts() {
        ByteBuffer b = le(12);
        b.putShort((short) -128).putShort((short) -256).putShort((short) 1).putShort((short) 1).putShort((short) 5)
                .putShort((short) -1);
        int[] bm = MapLumps.blockmap(reader(b.array()));
        assertThat(bm).containsExactly(-128, -256, 1, 1, 5, -1);
    }

    @Test
    void blockmapShorterThanItsHeaderIsMalformed() {
        assertThatThrownBy(() -> MapLumps.blockmap(reader(new byte[6]))).isInstanceOf(WadFormatException.class)
                .hasMessageContaining("t.wad").hasMessageContaining("TEST");
    }

    @Test
    void mapDataChecksLumpOrder() {
        WadWriter w = WadWriter.pwad().marker("MAP01");
        for (MapLump l : MapLump.values()) {
            w.add(l.name(), l == MapLump.BLOCKMAP ? new byte[8] : new byte[0]);
        }
        LumpDirectory ok = LumpDirectory.of(List.of(w.toWadFile("ok.wad")));
        MapData d = MapData.load(ok, "MAP01");
        assertThat(d.things()).isEmpty();
        assertThat(d.blockmap()).hasSize(4);

        WadWriter bad = WadWriter.pwad().marker("MAP01").add("THINGS", new byte[0]).add("SIDEDEFS", new byte[0]);
        LumpDirectory broken = LumpDirectory.of(List.of(bad.toWadFile("bad.wad")));
        assertThatThrownBy(() -> MapData.load(broken, "MAP01")).isInstanceOf(WadFormatException.class)
                .hasMessageContaining("bad.wad").hasMessageContaining("LINEDEFS");

        assertThatThrownBy(() -> MapData.load(ok, "MAP02")).isInstanceOf(WadFormatException.class)
                .hasMessageContaining("MAP02");
    }
}
