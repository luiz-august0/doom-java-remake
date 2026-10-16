package doom.wad;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class WadFileSmokeTest {

    @Test
    void lastLumpWithANameWins() {
        LumpDirectory dir = LumpDirectory.of(List.of(wad("IWAD", "A", "B"), wad("PWAD", "a")));
        assertThat(dir.size()).isEqualTo(3);
        assertThat(dir.find("A")).isEqualTo(2);
        assertThat(dir.find("b")).isEqualTo(1);
        assertThat(dir.find("C")).isEqualTo(-1);
        assertThat(dir.bytes(2)).containsExactly((byte) 'a');
    }

    /** Builds a WAD whose lumps are one byte long, containing the first character of their name. */
    private static WadFile wad(String id, String... lumps) {
        int dirOffset = 12 + lumps.length;
        ByteBuffer b = ByteBuffer.allocate(dirOffset + lumps.length * 16).order(ByteOrder.LITTLE_ENDIAN);
        b.put(id.getBytes(StandardCharsets.US_ASCII)).putInt(lumps.length).putInt(dirOffset);
        for (String l : lumps) {
            b.put((byte) l.charAt(0));
        }
        for (int i = 0; i < lumps.length; i++) {
            b.putInt(12 + i).putInt(1);
            byte[] name = new byte[8];
            byte[] src = lumps[i].getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(src, 0, name, 0, src.length);
            b.put(name);
        }
        return WadFile.of("test.wad", b.flip());
    }
}
