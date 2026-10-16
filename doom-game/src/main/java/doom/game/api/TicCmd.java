package doom.game.api;

/**
 * One player's input for one tic ({@code ticcmd_t}), the only input of the simulation. Mutable and reused; the field
 * types keep the original signedness: {@code forwardMove}/{@code sideMove} are {@code signed char},
 * {@code angleTurn}/{@code consistency} are {@code short}, {@code chatChar}/{@code buttons} are unsigned bytes stored
 * in a Java {@code byte} (mask with {@code 0xFF} when the numeric value matters).
 */
public final class TicCmd {

    public static final int BT_ATTACK = 1;
    public static final int BT_USE = 2;
    /** The command is a special action (pause, save) instead of a normal one. */
    public static final int BT_SPECIAL = 128;
    public static final int BT_SPECIALMASK = 3;
    public static final int BT_CHANGE = 4;
    public static final int BT_WEAPONMASK = 8 + 16 + 32;
    public static final int BT_WEAPONSHIFT = 3;
    public static final int BTS_PAUSE = 1;
    public static final int BTS_SAVEGAME = 2;
    public static final int BTS_SAVEMASK = 4 + 8 + 16;
    public static final int BTS_SAVESHIFT = 2;

    /** Units per tic forward (positive) or backward. */
    public byte forwardMove;
    /** Units per tic right (positive) or left. */
    public byte sideMove;
    /** Turn, added to the player's angle as {@code angleTurn << 16}. */
    public short angleTurn;
    /** Network consistency check (unused in single player). */
    public short consistency;
    public byte chatChar;
    public byte buttons;

    public void clear() {
        forwardMove = 0;
        sideMove = 0;
        angleTurn = 0;
        consistency = 0;
        chatChar = 0;
        buttons = 0;
    }

    public void copyFrom(TicCmd other) {
        forwardMove = other.forwardMove;
        sideMove = other.sideMove;
        angleTurn = other.angleTurn;
        consistency = other.consistency;
        chatChar = other.chatChar;
        buttons = other.buttons;
    }

    @Override
    public String toString() {
        return "TicCmd[fwd=" + forwardMove + ", side=" + sideMove + ", turn=" + angleTurn + ", buttons=" + (buttons & 0xFF) + "]";
    }
}
