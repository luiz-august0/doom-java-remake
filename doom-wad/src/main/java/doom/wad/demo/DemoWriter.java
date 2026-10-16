package doom.wad.demo;

import java.io.ByteArrayOutputStream;

/**
 * Writes a version 109 demo ({@code G_BeginRecording}, {@code G_WriteDemoTiccmd}, {@code G_CheckDemoStatus}).
 * Add the commands of every player present, in ascending player number, tic after tic; the size is unlimited
 * (the original stops recording when its buffer fills up).
 */
public final class DemoWriter {

    private final DemoHeader header;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final int players;
    private int cmdsInTic;
    private int tics;

    /** Starts a demo; the version byte of {@code header} is ignored: 109 is always written. */
    public DemoWriter(DemoHeader header) {
        this.header = header;
        this.players = header.playerCount();
        if (players == 0) {
            throw new IllegalArgumentException("a demo needs at least one player");
        }
        out.write(DemoFormat.VERSION_109);
        out.write(header.skill());
        out.write(header.episode());
        out.write(header.map());
        out.write(header.deathmatch());
        out.write(header.respawn());
        out.write(header.fast());
        out.write(header.noMonsters());
        out.write(header.consolePlayer());
        for (int i = 0; i < 4; i++) {
            out.write(header.playerInGame()[i] ? 1 : 0);
        }
    }

    /** The byte {@code G_WriteDemoTiccmd} stores for an {@code angleturn} value: {@code (angleturn + 128) >> 8}. */
    public static int angleTurnByte(int angleTurn) {
        return (angleTurn + 128) >> 8;
    }

    /**
     * Appends one command of the current tic.
     *
     * @param angleTurnByte high byte of angleturn (see {@link #angleTurnByte(int)})
     */
    public void addTicCmd(int forwardMove, int sideMove, int angleTurnByte, int buttons) {
        out.write(forwardMove);
        out.write(sideMove);
        out.write(angleTurnByte);
        out.write(buttons);
        if (++cmdsInTic == players) {
            cmdsInTic = 0;
            tics++;
        }
    }

    /** Number of complete tics written. */
    public int tics() {
        return tics;
    }

    /** The demo bytes with the end marker appended. */
    public byte[] toBytes() {
        if (cmdsInTic != 0) {
            throw new IllegalStateException("the last tic is incomplete (" + cmdsInTic + " of " + players + ")");
        }
        byte[] body = out.toByteArray();
        byte[] all = new byte[body.length + 1];
        System.arraycopy(body, 0, all, 0, body.length);
        all[body.length] = (byte) DemoFormat.END_MARKER;
        return all;
    }
}
