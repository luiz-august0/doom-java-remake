package doom.engine.config;

import doom.platform.input.KeyCode;
import java.util.List;
import java.util.Locale;

/**
 * Names of bindable inputs and the numeric ids the ticcmd builder uses to look them up.
 *
 * <p>Id space: {@code 0..255} are keyboard codes ({@link KeyCode}: lower-case ASCII for printable keys), then mouse
 * buttons, the two wheel directions and gamepad buttons (the triggers are reported as buttons once pressed far
 * enough).
 */
public final class InputNames {

    public static final int KEY_COUNT = 256;
    public static final int MOUSE_BASE = 256;
    public static final int MOUSE_BUTTONS = 16;
    public static final int WHEEL_UP = 272;
    public static final int WHEEL_DOWN = 273;
    public static final int PAD_BASE = 280;
    public static final int PAD_BUTTONS = 15;
    /** Left trigger (gamepad axis 4) as a button. */
    public static final int PAD_LT = 295;
    /** Right trigger (gamepad axis 5) as a button. */
    public static final int PAD_RT = 296;
    /** Number of ids. */
    public static final int COUNT = 300;

    private record Named(String name, int id) {
    }

    private static final List<Named> NAMED = List.of(
            new Named("UP", KeyCode.UPARROW), new Named("DOWN", KeyCode.DOWNARROW),
            new Named("LEFT", KeyCode.LEFTARROW), new Named("RIGHT", KeyCode.RIGHTARROW),
            new Named("SPACE", ' '), new Named("ENTER", KeyCode.ENTER), new Named("ESC", KeyCode.ESCAPE),
            new Named("TAB", KeyCode.TAB), new Named("BACKSPACE", KeyCode.BACKSPACE),
            new Named("DEL", KeyCode.DEL), new Named("INS", KeyCode.INS), new Named("HOME", KeyCode.HOME),
            new Named("END", KeyCode.END), new Named("PGUP", KeyCode.PGUP), new Named("PGDN", KeyCode.PGDN),
            new Named("PAUSE", KeyCode.PAUSE), new Named("SHIFT", KeyCode.RSHIFT),
            new Named("CTRL", KeyCode.RCTRL), new Named("ALT", KeyCode.RALT),
            new Named("COMMA", ','), new Named("PERIOD", '.'), new Named("SLASH", '/'),
            new Named("SEMICOLON", ';'), new Named("MINUS", '-'), new Named("EQUALS", '='),
            new Named("LBRACKET", '['), new Named("RBRACKET", ']'), new Named("BACKSLASH", '\\'),
            new Named("QUOTE", '\''), new Named("GRAVE", '`'),
            new Named("F1", KeyCode.F1), new Named("F2", KeyCode.F2), new Named("F3", KeyCode.F3),
            new Named("F4", KeyCode.F4), new Named("F5", KeyCode.F5), new Named("F6", KeyCode.F6),
            new Named("F7", KeyCode.F7), new Named("F8", KeyCode.F8), new Named("F9", KeyCode.F9),
            new Named("F10", KeyCode.F10), new Named("F11", KeyCode.F11), new Named("F12", KeyCode.F12),
            new Named("WHEELUP", WHEEL_UP), new Named("WHEELDOWN", WHEEL_DOWN),
            new Named("PAD_A", PAD_BASE), new Named("PAD_B", PAD_BASE + 1), new Named("PAD_X", PAD_BASE + 2),
            new Named("PAD_Y", PAD_BASE + 3), new Named("PAD_LB", PAD_BASE + 4), new Named("PAD_RB", PAD_BASE + 5),
            new Named("PAD_BACK", PAD_BASE + 6), new Named("PAD_START", PAD_BASE + 7),
            new Named("PAD_GUIDE", PAD_BASE + 8), new Named("PAD_LTHUMB", PAD_BASE + 9),
            new Named("PAD_RTHUMB", PAD_BASE + 10), new Named("PAD_DUP", PAD_BASE + 11),
            new Named("PAD_DRIGHT", PAD_BASE + 12), new Named("PAD_DDOWN", PAD_BASE + 13),
            new Named("PAD_DLEFT", PAD_BASE + 14), new Named("PAD_LT", PAD_LT), new Named("PAD_RT", PAD_RT));

    private InputNames() {
    }

    /**
     * Parses one input name ({@code W}, {@code UP}, {@code F5}, {@code MOUSE2}, {@code PAD_A}, a printable character).
     *
     * @return the id, or -1 when the name is unknown
     */
    public static int parse(String name) {
        String n = name.strip().toUpperCase(Locale.ROOT);
        if (n.isEmpty()) {
            return -1;
        }
        for (Named named : NAMED) {
            if (named.name.equals(n)) {
                return named.id;
            }
        }
        if (n.startsWith("MOUSE") && n.length() > 5) {
            try {
                int button = Integer.parseInt(n.substring(5));
                return button >= 1 && button <= MOUSE_BUTTONS ? MOUSE_BASE + button - 1 : -1;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        if (n.length() == 1) {
            char c = n.charAt(0);
            if (c >= 'A' && c <= 'Z') {
                return Character.toLowerCase(c);
            }
            if (c > ' ' && c < 127) {
                return c;
            }
        }
        return -1;
    }

    /** The name written to a configuration file for an input id. */
    public static String name(int id) {
        for (Named named : NAMED) {
            if (named.id == id) {
                return named.name;
            }
        }
        if (id >= MOUSE_BASE && id < MOUSE_BASE + MOUSE_BUTTONS) {
            return "MOUSE" + (id - MOUSE_BASE + 1);
        }
        if (id > ' ' && id < 127) {
            return String.valueOf(Character.toUpperCase((char) id));
        }
        return "KEY" + id;
    }

    /** Id of a keyboard code event, or -1 when out of range. */
    public static int ofKey(int keyCode) {
        return keyCode >= 0 && keyCode < KEY_COUNT ? keyCode : -1;
    }

    /** Id of a mouse button event (0 = left), or -1. */
    public static int ofMouseButton(int button) {
        return button >= 0 && button < MOUSE_BUTTONS ? MOUSE_BASE + button : -1;
    }

    /** Id of a gamepad button event, or -1. */
    public static int ofPadButton(int button) {
        return button >= 0 && button < PAD_BUTTONS ? PAD_BASE + button : -1;
    }
}
