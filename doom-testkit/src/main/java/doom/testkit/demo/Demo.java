package doom.testkit.demo;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * A version-109 demo (.lmp) in memory: the 13-byte header, then {@code tics x players} commands of four bytes each
 * (forward move, side move, turn high byte, buttons), then the 0x80 end marker. Only the players flagged in the header
 * have a command per tic, in ascending player order (spec 08 §1.1, {@code g_game.c:G_ReadDemoTiccmd}).
 *
 * @param header the header
 * @param cmds   {@code cmds[tic][k]} is the command of the {@code k}-th player in game (ascending player number)
 */
public record Demo(Header header, Cmd[][] cmds) {

    /** Version byte written by DOOM v1.9 (and accepted by Chocolate Doom). */
    public static final int VERSION_109 = 109;
    /** Header size in bytes. */
    public static final int HEADER_SIZE = 13;
    /** End-of-demo marker. */
    public static final int END_MARKER = 0x80;

    /** {@code BT_ATTACK}. */
    public static final int BT_ATTACK = 1;
    /** {@code BT_USE}. */
    public static final int BT_USE = 2;
    /** {@code BT_SPECIAL} (pause / save game): never produced by the generator. */
    public static final int BT_SPECIAL = 0x80;
    /** {@code BT_CHANGE}: the weapon number in bits 3..5 is a weapon change request. */
    public static final int BT_CHANGE = 4;
    /** {@code BT_WEAPONMASK}. */
    public static final int BT_WEAPONMASK = 0x38;
    /** {@code BT_WEAPONSHIFT}. */
    public static final int BT_WEAPONSHIFT = 3;

    /**
     * The header of a demo.
     *
     * @param version      version byte (109)
     * @param skill        0..4 ({@code sk_baby}..{@code sk_nightmare})
     * @param episode      1..4 (1 for Doom II)
     * @param map          1..9 (episodes) or 1..32 (Doom II)
     * @param deathmatch   0 coop/single, 1 deathmatch, 2 altdeath
     * @param respawn      {@code -respawn}
     * @param fast         {@code -fast}
     * @param noMonsters   {@code -nomonsters}
     * @param consolePlayer 0..3
     * @param inGame       {@code playeringame[0..3]}
     */
    public record Header(int version, int skill, int episode, int map, int deathmatch, boolean respawn, boolean fast,
                         boolean noMonsters, int consolePlayer, boolean[] inGame) {

        public Header {
            if (inGame.length != 4) {
                throw new IllegalArgumentException("inGame must have 4 entries");
            }
            inGame = inGame.clone();
        }

        @Override
        public boolean[] inGame() {
            return inGame.clone();
        }

        /** Number of players flagged in game. */
        public int playerCount() {
            int n = 0;
            for (boolean b : inGame) {
                if (b) {
                    n++;
                }
            }
            return n;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Header h && h.version == version && h.skill == skill && h.episode == episode
                    && h.map == map && h.deathmatch == deathmatch && h.respawn == respawn && h.fast == fast
                    && h.noMonsters == noMonsters && h.consolePlayer == consolePlayer
                    && Arrays.equals(h.inGame, inGame);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(new Object[] {version, skill, episode, map, deathmatch, respawn, fast, noMonsters,
                consolePlayer, Arrays.hashCode(inGame)});
        }
    }

    /**
     * One player's command for one tic, as stored in a demo.
     *
     * @param forward forward move, signed byte (vanilla: 0, +-25, +-50 while walking or running)
     * @param side    side move, signed byte (vanilla: 0, +-24, +-40)
     * @param turn    the high byte of {@code angleturn}, signed (the demo stores {@code angleturn >> 8} only)
     * @param buttons {@code BT_*} bits
     */
    public record Cmd(int forward, int side, int turn, int buttons) {

        public Cmd {
            if (forward < -128 || forward > 127 || side < -128 || side > 127 || turn < -128 || turn > 127
                    || buttons < 0 || buttons > 255) {
                throw new IllegalArgumentException("Command out of byte range: " + forward + "," + side + ","
                        + turn + "," + buttons);
            }
        }

        /** The command's {@code angleturn} as the game sees it after reading the demo ({@code turn << 8}). */
        public int angleTurn() {
            return turn << 8;
        }

        /** Whether the attack bit is set. */
        public boolean attack() {
            return (buttons & BT_ATTACK) != 0;
        }

        /** Whether the use bit is set. */
        public boolean use() {
            return (buttons & BT_USE) != 0;
        }

        /** Weapon number requested (0..7), or -1 when this command is not a weapon change. */
        public int weaponChange() {
            return (buttons & BT_CHANGE) != 0 ? (buttons & BT_WEAPONMASK) >> BT_WEAPONSHIFT : -1;
        }
    }

    public Demo {
        int players = header.playerCount();
        cmds = cmds.clone();
        for (int t = 0; t < cmds.length; t++) {
            if (cmds[t].length != players) {
                throw new IllegalArgumentException("Tic " + t + " has " + cmds[t].length + " commands, expected " + players);
            }
            cmds[t] = cmds[t].clone();
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Demo d && d.header.equals(header) && Arrays.deepEquals(d.cmds, cmds);
    }

    @Override
    public int hashCode() {
        return 31 * header.hashCode() + Arrays.deepHashCode(cmds);
    }

    /** Number of tics. */
    public int tics() {
        return cmds.length;
    }

    /** Serializes the demo. */
    public byte[] toBytes() {
        int players = header.playerCount();
        ByteArrayOutputStream out = new ByteArrayOutputStream(HEADER_SIZE + cmds.length * players * 4 + 1);
        out.write(header.version);
        out.write(header.skill);
        out.write(header.episode);
        out.write(header.map);
        out.write(header.deathmatch);
        out.write(header.respawn ? 1 : 0);
        out.write(header.fast ? 1 : 0);
        out.write(header.noMonsters ? 1 : 0);
        out.write(header.consolePlayer);
        for (boolean b : header.inGame) {
            out.write(b ? 1 : 0);
        }
        for (Cmd[] tic : cmds) {
            for (Cmd c : tic) {
                out.write(c.forward & 0xff);
                out.write(c.side & 0xff);
                out.write(c.turn & 0xff);
                out.write(c.buttons);
            }
        }
        out.write(END_MARKER);
        return out.toByteArray();
    }

    /**
     * Parses a demo (version 109 or 110 headers).
     *
     * @throws IllegalArgumentException when the data is not a well-formed demo
     */
    public static Demo parse(byte[] data) {
        if (data.length < HEADER_SIZE + 1) {
            throw new IllegalArgumentException("Demo too short: " + data.length + " bytes");
        }
        int version = data[0] & 0xff;
        if (version != 109 && version != 110) {
            throw new IllegalArgumentException("Unsupported demo version " + version);
        }
        boolean[] inGame = new boolean[4];
        int players = 0;
        for (int i = 0; i < 4; i++) {
            inGame[i] = data[9 + i] != 0;
            if (inGame[i]) {
                players++;
            }
        }
        if (players == 0) {
            throw new IllegalArgumentException("Demo has no players");
        }
        Header header = new Header(version, data[1] & 0xff, data[2] & 0xff, data[3] & 0xff, data[4] & 0xff,
                data[5] != 0, data[6] != 0, data[7] != 0, data[8] & 0xff, inGame);
        int pos = HEADER_SIZE;
        int step = players * 4;
        int tics = 0;
        while (pos + tics * step < data.length && (data[pos + tics * step] & 0xff) != END_MARKER) {
            tics++;
        }
        if (pos + tics * step >= data.length) {
            throw new IllegalArgumentException("Demo has no end marker (0x80)");
        }
        int markerAt = pos + tics * step;
        if (markerAt != data.length - 1) {
            throw new IllegalArgumentException("Demo has " + (data.length - 1 - markerAt) + " bytes after the end marker");
        }
        Cmd[][] cmds = new Cmd[tics][players];
        for (int t = 0; t < tics; t++) {
            for (int k = 0; k < players; k++) {
                int o = pos + t * step + k * 4;
                cmds[t][k] = new Cmd(data[o], data[o + 1], data[o + 2], data[o + 3] & 0xff);
            }
        }
        return new Demo(header, cmds);
    }
}
