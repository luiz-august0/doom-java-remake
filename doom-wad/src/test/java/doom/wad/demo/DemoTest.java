package doom.wad.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import doom.wad.WadFormatException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DemoTest {

    private static DemoHeader header(boolean... players) {
        return new DemoHeader(109, 3, 1, 7, 0, 0, 0, 0, 0, players);
    }

    @Test
    void roundTripsASinglePlayerDemo() {
        DemoWriter w = new DemoWriter(header(true, false, false, false));
        w.addTicCmd(25, -24, 3, 1);
        w.addTicCmd(-50, 40, DemoWriter.angleTurnByte(-1280), 0xC4);
        byte[] bytes = w.toBytes();
        assertThat(bytes).hasSize(DemoFormat.HEADER_SIZE + 8 + 1);
        assertThat(bytes[0]).isEqualTo((byte) 109);
        assertThat(bytes[bytes.length - 1]).isEqualTo((byte) 0x80);

        Demo d = Demo.read(bytes, "test");
        assertThat(d.header().version()).isEqualTo(109);
        assertThat(d.header().skill()).isEqualTo(3);
        assertThat(d.header().episode()).isEqualTo(1);
        assertThat(d.header().map()).isEqualTo(7);
        assertThat(d.playerCount()).isEqualTo(1);
        assertThat(d.tics()).isEqualTo(2);
        assertThat(d.hasEndMarker()).isTrue();
        assertThat(d.forwardMove(0, 0)).isEqualTo(25);
        assertThat(d.sideMove(0, 0)).isEqualTo(-24);
        assertThat(d.angleTurnByte(0, 0)).isEqualTo(3);
        assertThat(d.angleTurn(0, 0)).isEqualTo(3 << 8);
        assertThat(d.forwardMove(1, 0)).isEqualTo(-50);
        assertThat(d.angleTurn(1, 0)).isEqualTo(-1280);
        assertThat(d.buttons(1, 0)).isEqualTo(0xC4);
        assertThatThrownBy(() -> d.forwardMove(2, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> d.forwardMove(0, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void multiplayerCommandsAreInterleavedByPlayer() {
        DemoHeader h = new DemoHeader(109, 2, 1, 1, 1, 0, 0, 0, 2, new boolean[] {true, false, true, true});
        DemoWriter w = new DemoWriter(h);
        for (int tic = 0; tic < 3; tic++) {
            w.addTicCmd(tic, 0, 0, 0); // player 1
            w.addTicCmd(10 + tic, 0, 0, 0); // player 3
            w.addTicCmd(20 + tic, 0, 0, 0); // player 4
        }
        Demo d = Demo.read(w.toBytes(), "mp");
        assertThat(d.playerCount()).isEqualTo(3);
        assertThat(d.tics()).isEqualTo(3);
        assertThat(d.header().deathmatch()).isEqualTo(1);
        assertThat(d.header().consolePlayer()).isEqualTo(2);
        assertThat(d.forwardMove(2, 0)).isEqualTo(2);
        assertThat(d.forwardMove(2, 2)).isEqualTo(12);
        assertThat(d.forwardMove(1, 3)).isEqualTo(21);
        assertThatThrownBy(() -> d.forwardMove(0, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void version110IsReadAsAndWrittenBack109() {
        byte[] bytes = new DemoWriter(header(true, false, false, false)).toBytes();
        bytes[0] = 110;
        Demo d = Demo.read(bytes, "lx");
        assertThat(d.header().version()).isEqualTo(110);
        assertThat(d.tics()).isZero();
        assertThat(new DemoWriter(d.header()).toBytes()[0]).isEqualTo((byte) 109);
    }

    @Test
    void otherVersionsAreRejectedWithAClearMessage() {
        byte[] bytes = new DemoWriter(header(true, false, false, false)).toBytes();
        for (int version : new int[] {0, 4, 105, 111, 255}) {
            bytes[0] = (byte) version;
            assertThatThrownBy(() -> Demo.read(bytes, "old.lmp")).isInstanceOf(WadFormatException.class)
                    .hasMessageContaining("old.lmp")
                    .hasMessageContaining("different game version")
                    .hasMessageContaining("read " + version);
        }
    }

    @Test
    void truncatedDataIsMalformed() {
        assertThatThrownBy(() -> Demo.read(new byte[0], "e")).isInstanceOf(WadFormatException.class);
        byte[] shortHeader = Arrays.copyOf(new DemoWriter(header(true, false, false, false)).toBytes(), 8);
        assertThatThrownBy(() -> Demo.read(shortHeader, "e")).isInstanceOf(WadFormatException.class);

        DemoWriter w = new DemoWriter(header(true, false, false, false));
        w.addTicCmd(1, 2, 3, 4);
        byte[] full = w.toBytes();
        byte[] partialTic = Arrays.copyOf(full, DemoFormat.HEADER_SIZE + 4 + 2);
        partialTic[DemoFormat.HEADER_SIZE + 4] = 7; // not the end marker
        assertThatThrownBy(() -> Demo.read(partialTic, "e")).isInstanceOf(WadFormatException.class)
                .hasMessageContaining("truncated tic 1");
    }

    @Test
    void aDemoWithoutPlayersIsMalformed() {
        byte[] bytes = new byte[DemoFormat.HEADER_SIZE + 1];
        bytes[0] = 109;
        bytes[DemoFormat.HEADER_SIZE] = (byte) 0x80;
        assertThatThrownBy(() -> Demo.read(bytes, "e")).isInstanceOf(WadFormatException.class)
                .hasMessageContaining("no players");
    }

    @Test
    void streamWithoutEndMarkerIsAcceptedButFlagged() {
        DemoWriter w = new DemoWriter(header(true, false, false, false));
        w.addTicCmd(1, 2, 3, 4);
        byte[] full = w.toBytes();
        Demo d = Demo.read(Arrays.copyOf(full, full.length - 1), "cut");
        assertThat(d.tics()).isEqualTo(1);
        assertThat(d.hasEndMarker()).isFalse();
    }

    @Test
    void writerRejectsIncompleteTics() {
        DemoWriter w = new DemoWriter(header(true, true, false, false));
        w.addTicCmd(0, 0, 0, 0);
        assertThatThrownBy(w::toBytes).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new DemoWriter(header(false, false, false, false)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void angleTurnRoundsLikeG_WriteDemoTiccmd() {
        assertThat(DemoWriter.angleTurnByte(640)).isEqualTo(3); // (640 + 128) >> 8
        assertThat(DemoWriter.angleTurnByte(-640)).isEqualTo(-2);
    }
}
