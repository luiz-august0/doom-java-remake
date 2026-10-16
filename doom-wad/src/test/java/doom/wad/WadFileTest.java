package doom.wad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WadFileTest {

    @Test
    void readsHeaderAndDirectory() {
        WadFile w = WadWriter.iwad().add("PLAYPAL", new byte[] {1, 2, 3}).marker("F_START").add("flat1", new byte[4])
                .marker("F_END").toWadFile("t.wad");
        assertThat(w.isIwad()).isTrue();
        assertThat(w.identification()).isEqualTo("IWAD");
        assertThat(w.lumpCount()).isEqualTo(4);
        assertThat(w.lumpName(2)).isEqualTo("FLAT1");
        assertThat(w.lumpSize(0)).isEqualTo(3);
        assertThat(w.lumpOffset(0)).isEqualTo(12);
        ByteBuffer d = w.lumpData(0);
        assertThat(d.remaining()).isEqualTo(3);
        assertThat(d.get(2)).isEqualTo((byte) 3);
        assertThat(w.lumpData(1).remaining()).isZero();
    }

    @Test
    void rejectsBadIdentification() {
        byte[] bytes = WadWriter.pwad().add("A", new byte[1]).toBytes();
        bytes[0] = 'X';
        assertThatThrownBy(() -> WadFile.of("bad.wad", ByteBuffer.wrap(bytes)))
                .isInstanceOf(WadFormatException.class)
                .hasMessageContaining("bad.wad")
                .hasMessageContaining("doesn't have IWAD or PWAD id");
    }

    @Test
    void rejectsTruncatedHeader() {
        assertThatThrownBy(() -> WadFile.of("short.wad", ByteBuffer.wrap(new byte[] {'P', 'W', 'A', 'D'})))
                .isInstanceOf(WadFormatException.class)
                .hasMessageContaining("short.wad");
    }

    @Test
    void rejectsDirectoryOutsideTheFile() {
        ByteBuffer b = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        b.put("PWAD".getBytes(StandardCharsets.US_ASCII)).putInt(5).putInt(4);
        assertThatThrownBy(() -> WadFile.of("dir.wad", b.flip()))
                .isInstanceOf(WadFormatException.class)
                .hasMessageContaining("directory outside the file");
        ByteBuffer neg = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        neg.put("PWAD".getBytes(StandardCharsets.US_ASCII)).putInt(-1).putInt(12);
        assertThatThrownBy(() -> WadFile.of("neg.wad", neg.flip())).isInstanceOf(WadFormatException.class);
    }

    @Test
    void rejectsLumpOutsideTheFile() {
        byte[] bytes = WadWriter.pwad().add("A", new byte[4]).toBytes();
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int dir = b.getInt(8);
        b.putInt(dir + 4, 1000);
        assertThatThrownBy(() -> WadFile.of("lump.wad", ByteBuffer.wrap(bytes)))
                .isInstanceOf(WadFormatException.class)
                .hasMessageContaining("lump.wad lump A")
                .hasMessageContaining("outside the file");
    }

    @Test
    void emptyLumpsMayHaveAnyOffset() {
        byte[] bytes = WadWriter.pwad().marker("S_START").toBytes();
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(b.getInt(8), 99999);
        WadFile w = WadFile.of("m.wad", ByteBuffer.wrap(bytes));
        assertThat(w.lumpOffset(0)).isEqualTo(99999);
        assertThat(w.lumpData(0).remaining()).isZero();
    }

    @Test
    void namesAreUpperCasedAsciiOnly() {
        byte[] bytes = WadWriter.pwad().add("x", new byte[0]).toBytes();
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int dir = b.getInt(8);
        byte[] name = {'a', (byte) 0xFF, 'b', 0, 'z', 'z', 'z', 'z'};
        b.put(dir + 8, name);
        WadFile w = WadFile.of("n.wad", ByteBuffer.wrap(bytes));
        assertThat(w.lumpName(0)).isEqualTo("AÿB");
    }

    @Test
    void computesNamespacesPerFile() {
        WadFile w = WadWriter.pwad()
                .add("THING", new byte[1])
                .marker("SS_START").add("TROOA1", new byte[1]).marker("S_END")
                .marker("FF_START").marker("F1_START").add("FLOOR0_1", new byte[1]).marker("F1_END").marker("FF_END")
                .marker("P_START").add("WALL00_1", new byte[1]).marker("P_END")
                .add("ENDOOM", new byte[1])
                .toWadFile("ns.wad");
        assertThat(w.namespace(0)).isEqualTo(LumpNamespace.GLOBAL);
        assertThat(w.namespace(1)).isEqualTo(LumpNamespace.MARKER);
        assertThat(w.namespace(2)).isEqualTo(LumpNamespace.SPRITES);
        assertThat(w.namespace(3)).isEqualTo(LumpNamespace.MARKER);
        assertThat(w.namespace(5)).isEqualTo(LumpNamespace.MARKER);
        assertThat(w.namespace(6)).isEqualTo(LumpNamespace.FLATS);
        assertThat(w.namespace(10)).isEqualTo(LumpNamespace.PATCHES);
        assertThat(w.namespace(12)).isEqualTo(LumpNamespace.GLOBAL);
    }

    @Test
    void nonWadFilesLoadAsSingleLumps(@TempDir Path dir) throws Exception {
        Path demo = dir.resolve("mydemo.v2.lmp");
        Files.write(demo, new byte[] {109, 2, 1, 1});
        WadFile w = WadFile.load(demo);
        assertThat(w.lumpCount()).isEqualTo(1);
        assertThat(w.lumpName(0)).isEqualTo("MYDEMO");
        assertThat(w.lumpSize(0)).isEqualTo(4);
        assertThat(w.path()).isEqualTo(demo);
        assertThat(WadFile.lumpNameForFile("dir/averyverylongname.lmp")).isEqualTo("AVERYVER");

        Path wad = dir.resolve("FOO.WAD");
        WadWriter.pwad().add("A", new byte[2]).write(wad);
        WadFile pw = WadFile.load(wad);
        assertThat(pw.identification()).isEqualTo("PWAD");
        assertThat(pw.lumpName(0)).isEqualTo("A");
        LumpDirectory d = LumpDirectory.open(List.of(wad, demo));
        assertThat(d.find("mydemo")).isEqualTo(1);
    }
}
