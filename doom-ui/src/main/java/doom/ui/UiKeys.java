package doom.ui;

/**
 * Key codes understood by {@link Ui}. The numbering is the one of {@code doomkeys.h} / {@code doom.platform.input.KeyCode}
 * (printable keys use their lower-case ASCII value), so the engine can pass {@code InputEvent.code} straight through.
 * The UI does not depend on the platform module; this class mirrors the values it needs.
 */
public final class UiKeys {
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
    public static final int CAPSLOCK = 0x80 + 0x3a;
    public static final int NUMLOCK = 0x80 + 0x45;
    public static final int SCRLCK = 0x80 + 0x46;
    public static final int PRTSCR = 0x80 + 0x59;

    private UiKeys() {
    }
}
