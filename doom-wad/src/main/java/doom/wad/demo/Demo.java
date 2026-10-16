package doom.wad.demo;

import doom.wad.LumpReader;
import doom.wad.WadFormatException;
import java.nio.ByteBuffer;

/**
 * A decoded demo: header plus the ticcmd stream. For each tic and each player present (in ascending player number)
 * the stream holds {@code forwardmove}, {@code sidemove}, the high byte of {@code angleturn} and {@code buttons}
 * (4 bytes); the stream ends with {@link DemoFormat#END_MARKER}.
 *
 * <p>Versions 109 (DOOM 1.9) and 110 (linuxdoom) are accepted and treated alike; anything else, including the
 * pre-1.4 headerless demos and the {@code 111} long-tics format, is rejected with a clear message.
 */
public final class Demo {

    private final DemoHeader header;
    private final byte[] cmds;
    private final int tics;
    private final int players;
    private final int[] slotOfPlayer = new int[4];
    private final boolean terminated;

    private Demo(DemoHeader header, byte[] cmds, int tics, boolean terminated) {
        this.header = header;
        this.cmds = cmds;
        this.tics = tics;
        this.players = header.playerCount();
        this.terminated = terminated;
        int slot = 0;
        for (int i = 0; i < 4; i++) {
            slotOfPlayer[i] = header.playerInGame()[i] ? slot++ : -1;
        }
    }

    /** Parses a demo from the bytes of a lump or {@code .lmp} file; {@code name} is used in error messages. */
    public static Demo read(byte[] data, String name) {
        return read(new LumpReader(ByteBuffer.wrap(data), null, name));
    }

    /** Parses a demo; see {@link #read(byte[], String)}. */
    public static Demo read(LumpReader r) {
        int version = r.u8(0);
        if (version != DemoFormat.VERSION_109 && version != DemoFormat.VERSION_110) {
            String hint = version <= 4 ? " (a pre-1.4 demo, not supported)"
                    : version == 111 ? " (a Doom 1.91 long-tics demo, not supported)" : "";
            throw r.error(0, "Demo is from a different game version! (read " + version + ", should be "
                    + DemoFormat.VERSION_109 + ")" + hint);
        }
        r.require(0, DemoFormat.HEADER_SIZE, "demo header");
        boolean[] in = new boolean[4];
        for (int i = 0; i < 4; i++) {
            in[i] = r.u8(9 + i) != 0;
        }
        DemoHeader header = new DemoHeader(version, r.u8(1), r.u8(2), r.u8(3), r.u8(4), r.u8(5), r.u8(6), r.u8(7),
                r.u8(8), in);
        int n = header.playerCount();
        if (n == 0) {
            throw r.error(9, "demo has no players");
        }
        int tic = n * DemoFormat.TICCMD_SIZE;
        int pos = DemoFormat.HEADER_SIZE;
        int count = 0;
        boolean terminated = false;
        while (pos < r.length()) {
            if (r.u8(pos) == DemoFormat.END_MARKER) {
                terminated = true;
                break;
            }
            if (pos + tic > r.length()) {
                throw r.error(pos, "truncated tic " + count + " (needs " + tic + " bytes, " + (r.length() - pos)
                        + " left)");
            }
            pos += tic;
            count++;
        }
        byte[] cmds = r.bytes(DemoFormat.HEADER_SIZE, count * tic);
        return new Demo(header, cmds, count, terminated);
    }

    public DemoHeader header() {
        return header;
    }

    /** Number of tics with commands (the end marker is not counted). */
    public int tics() {
        return tics;
    }

    /** Number of players present. */
    public int playerCount() {
        return players;
    }

    /** True when the stream ended with the 0x80 marker (a demo cut short by a crash may lack it). */
    public boolean hasEndMarker() {
        return terminated;
    }

    private int at(int tic, int player, int field) {
        if (tic < 0 || tic >= tics || player < 0 || player > 3 || slotOfPlayer[player] < 0) {
            throw new IllegalArgumentException("tic " + tic + " player " + player);
        }
        return (tic * players + slotOfPlayer[player]) * DemoFormat.TICCMD_SIZE + field;
    }

    /** {@code forwardmove} (signed byte). */
    public int forwardMove(int tic, int player) {
        return cmds[at(tic, player, 0)];
    }

    /** {@code sidemove} (signed byte). */
    public int sideMove(int tic, int player) {
        return cmds[at(tic, player, 1)];
    }

    /** High byte of {@code angleturn}, unsigned as stored. */
    public int angleTurnByte(int tic, int player) {
        return cmds[at(tic, player, 2)] & 0xFF;
    }

    /** {@code angleturn} as {@code G_ReadDemoTiccmd} rebuilds it: {@code (short) (byte << 8)}. */
    public int angleTurn(int tic, int player) {
        return (short) (angleTurnByte(tic, player) << 8);
    }

    /** {@code buttons} (unsigned byte). */
    public int buttons(int tic, int player) {
        return cmds[at(tic, player, 3)] & 0xFF;
    }
}
