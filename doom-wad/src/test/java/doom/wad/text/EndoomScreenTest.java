package doom.wad.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import doom.wad.LumpDirectory;
import doom.wad.LumpReader;
import doom.wad.WadFormatException;
import doom.wad.WadWriter;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;

class EndoomScreenTest {

    private static EndoomScreen screen(byte[] data) {
        return EndoomScreen.read(new LumpReader(ByteBuffer.wrap(data), "t.wad", "ENDOOM"));
    }

    @Test
    void decodesCharactersAndAttributes() {
        byte[] data = new byte[EndoomScreen.SIZE];
        data[0] = 'D';
        data[1] = 0x1F; // white on blue
        int last = (24 * 80 + 79) * 2;
        data[last] = (byte) 0xC9; // box drawing
        data[last + 1] = (byte) 0xF4; // blink, white background, red
        EndoomScreen s = screen(data);
        assertThat(s.code(0, 0)).isEqualTo('D');
        assertThat(s.character(0, 0)).isEqualTo('D');
        assertThat(s.foreground(0, 0)).isEqualTo(15);
        assertThat(s.background(0, 0)).isEqualTo(1);
        assertThat(s.blink(0, 0)).isFalse();
        assertThat(s.character(79, 24)).isEqualTo('╔');
        assertThat(s.foreground(79, 24)).isEqualTo(4);
        assertThat(s.background(79, 24)).isEqualTo(7);
        assertThat(s.blink(79, 24)).isTrue();
        assertThat(s.rowText(0)).hasSize(80).startsWith("D");
        assertThat(s.toBytes()).isEqualTo(data);
    }

    @Test
    void codePageCoversAllByteValues() {
        byte[] data = new byte[EndoomScreen.SIZE];
        for (int i = 0; i < 256; i++) {
            data[i * 2] = (byte) i;
        }
        EndoomScreen s = screen(data);
        assertThat(s.character((1) % 80, (1) / 80)).isEqualTo('☺');
        assertThat(s.character((0x41) % 80, (0x41) / 80)).isEqualTo('A');
        assertThat(s.character((0x7F) % 80, (0x7F) / 80)).isEqualTo('⌂');
        assertThat(s.character((0x80) % 80, (0x80) / 80)).isEqualTo('Ç');
        assertThat(s.character((0xB0) % 80, (0xB0) / 80)).isEqualTo('░');
        assertThat(s.character((0xDB) % 80, (0xDB) / 80)).isEqualTo('█');
        assertThat(s.character((0xE1) % 80, (0xE1) / 80)).isEqualTo('ß');
        assertThat(s.character((0xFF) % 80, (0xFF) / 80)).isEqualTo(' ');
    }

    @Test
    void shortLumpIsMalformed() {
        assertThatThrownBy(() -> screen(new byte[EndoomScreen.SIZE - 1])).isInstanceOf(WadFormatException.class)
                .hasMessageContaining("t.wad").hasMessageContaining("ENDOOM");
    }

    @Test
    void readsTheLumpFromADirectory() {
        LumpDirectory d = LumpDirectory.of(List.of(
                WadWriter.iwad().add("ENDOOM", new byte[EndoomScreen.SIZE]).toWadFile("t.wad")));
        assertThat(EndoomScreen.read(d).code(5, 5)).isZero();
        assertThatThrownBy(() -> screen(new byte[EndoomScreen.SIZE]).code(80, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
