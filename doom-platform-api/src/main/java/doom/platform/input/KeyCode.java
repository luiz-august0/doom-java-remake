package doom.platform.input;

/**
 * Key codes, numbered like the original {@code doomkeys.h} (Chocolate Doom's extended set): printable keys use their
 * lower-case ASCII value, special keys use the values below. Backends map native key codes to these.
 */
public final class KeyCode {

    public static final int RIGHTARROW = 0xae;
    public static final int LEFTARROW = 0xac;
    public static final int UPARROW = 0xad;
    public static final int DOWNARROW = 0xaf;
    public static final int ESCAPE = 27;
    public static final int ENTER = 13;
    public static final int TAB = 9;
    public static final int F1 = 0x80 + 0x3b;
    public static final int F2 = 0x80 + 0x3c;
    public static final int F3 = 0x80 + 0x3d;
    public static final int F4 = 0x80 + 0x3e;
    public static final int F5 = 0x80 + 0x3f;
    public static final int F6 = 0x80 + 0x40;
    public static final int F7 = 0x80 + 0x41;
    public static final int F8 = 0x80 + 0x42;
    public static final int F9 = 0x80 + 0x43;
    public static final int F10 = 0x80 + 0x44;
    public static final int F11 = 0x80 + 0x57;
    public static final int F12 = 0x80 + 0x58;

    public static final int BACKSPACE = 0x7f;
    public static final int PAUSE = 0xff;

    public static final int EQUALS = 0x3d;
    public static final int MINUS = 0x2d;

    public static final int RSHIFT = 0x80 + 0x36;
    public static final int RCTRL = 0x80 + 0x1d;
    public static final int RALT = 0x80 + 0x38;
    public static final int LALT = RALT;

    public static final int CAPSLOCK = 0x80 + 0x3a;
    public static final int NUMLOCK = 0x80 + 0x45;
    public static final int SCRLCK = 0x80 + 0x46;
    public static final int PRTSCR = 0x80 + 0x59;

    public static final int HOME = 0x80 + 0x47;
    public static final int END = 0x80 + 0x4f;
    public static final int PGUP = 0x80 + 0x49;
    public static final int PGDN = 0x80 + 0x51;
    public static final int INS = 0x80 + 0x52;
    public static final int DEL = 0x80 + 0x53;

    public static final int KEYP_0 = 0;
    public static final int KEYP_1 = END;
    public static final int KEYP_2 = DOWNARROW;
    public static final int KEYP_3 = PGDN;
    public static final int KEYP_4 = LEFTARROW;
    public static final int KEYP_5 = 0x80 + 0x4c;
    public static final int KEYP_6 = RIGHTARROW;
    public static final int KEYP_7 = HOME;
    public static final int KEYP_8 = UPARROW;
    public static final int KEYP_9 = PGUP;
    public static final int KEYP_DIVIDE = '/';
    public static final int KEYP_PLUS = '+';
    public static final int KEYP_MINUS = '-';
    public static final int KEYP_MULTIPLY = '*';
    public static final int KEYP_PERIOD = 0;
    public static final int KEYP_EQUALS = EQUALS;
    public static final int KEYP_ENTER = ENTER;

    private KeyCode() {
    }
}
