package doom.wad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class LumpReaderTest {

    @Test
    void readsLittleEndianValuesWithSignedness() {
        LumpReader r = new LumpReader(ByteBuffer.wrap(new byte[] {
            (byte) 0xFF, (byte) 0xFF, 0x34, 0x12, 0x78, 0x56, 0x34, (byte) 0x82, 'a', 'B', 0, 'x', 0, 0, 0, 0}),
                "f.wad", "L");
        assertThat(r.length()).isEqualTo(16);
        assertThat(r.s16(0)).isEqualTo(-1);
        assertThat(r.u16(0)).isEqualTo(0xFFFF);
        assertThat(r.u8(0)).isEqualTo(255);
        assertThat(r.s8(0)).isEqualTo(-1);
        assertThat(r.s16(2)).isEqualTo(0x1234);
        assertThat(r.s32(4)).isEqualTo(0x82345678);
        assertThat(r.name8(8)).isEqualTo("aB");
        assertThat(r.bytes(2, 2)).containsExactly(0x34, 0x12);
    }

    @Test
    void outOfRangeReadsBecomeWadFormatExceptions() {
        LumpReader r = new LumpReader(ByteBuffer.wrap(new byte[3]), "f.wad", "THINGS");
        assertThatThrownBy(() -> r.s32(0))
                .isInstanceOf(WadFormatException.class)
                .hasMessageStartingWith("f.wad lump THINGS @0:");
        assertThatThrownBy(() -> r.s16(2)).isInstanceOf(WadFormatException.class);
        assertThatThrownBy(() -> r.u8(-1)).isInstanceOf(WadFormatException.class);
        assertThatThrownBy(() -> r.name8(0)).isInstanceOf(WadFormatException.class);
        assertThatThrownBy(() -> r.bytes(1, 5)).isInstanceOf(WadFormatException.class);
    }

    @Test
    void namesMatchLikeStrncasecmp() {
        assertThat(LumpNames.key("e1m1")).isEqualTo("E1M1");
        assertThat(LumpNames.key("ABCDEFGHIJ")).isEqualTo("ABCDEFGH");
        assertThat(LumpNames.key("AB\0CD")).isEqualTo("AB");
        assertThat(LumpNames.matches("sky1", "SKY1")).isTrue();
        assertThat(LumpNames.matches("SKY1", "SKY12")).isFalse();
        assertThat(LumpNames.upper("éa")).isEqualTo("éA");
    }
}
