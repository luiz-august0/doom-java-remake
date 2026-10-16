package doom.platform.lwjgl;

import doom.platform.input.KeyCode;

/**
 * Translates GLFW key tokens to {@link KeyCode} values the way Chocolate Doom's {@code i_input.c} does with
 * {@code vanilla_keyboard_mapping}: by key position on a US keyboard (GLFW key tokens are layout independent for the
 * printable keys), both Shift keys give {@code RSHIFT}, both Ctrl keys give {@code RCTRL}, the left Alt gives
 * {@code LALT}. Keys the game has no code for map to {@code 0}.
 *
 * <p>The GLFW token values are the stable constants of {@code glfw3.h}; they are spelled out here so that the mapping
 * is testable without loading the native library.
 */
final class GlfwKeys {

    static final int GLFW_KEY_SPACE = 32;
    static final int GLFW_KEY_APOSTROPHE = 39;
    static final int GLFW_KEY_COMMA = 44;
    static final int GLFW_KEY_MINUS = 45;
    static final int GLFW_KEY_PERIOD = 46;
    static final int GLFW_KEY_SLASH = 47;
    static final int GLFW_KEY_0 = 48;
    static final int GLFW_KEY_9 = 57;
    static final int GLFW_KEY_SEMICOLON = 59;
    static final int GLFW_KEY_EQUAL = 61;
    static final int GLFW_KEY_A = 65;
    static final int GLFW_KEY_Z = 90;
    static final int GLFW_KEY_LEFT_BRACKET = 91;
    static final int GLFW_KEY_BACKSLASH = 92;
    static final int GLFW_KEY_RIGHT_BRACKET = 93;
    static final int GLFW_KEY_GRAVE_ACCENT = 96;
    static final int GLFW_KEY_ESCAPE = 256;
    static final int GLFW_KEY_ENTER = 257;
    static final int GLFW_KEY_TAB = 258;
    static final int GLFW_KEY_BACKSPACE = 259;
    static final int GLFW_KEY_INSERT = 260;
    static final int GLFW_KEY_DELETE = 261;
    static final int GLFW_KEY_RIGHT = 262;
    static final int GLFW_KEY_LEFT = 263;
    static final int GLFW_KEY_DOWN = 264;
    static final int GLFW_KEY_UP = 265;
    static final int GLFW_KEY_PAGE_UP = 266;
    static final int GLFW_KEY_PAGE_DOWN = 267;
    static final int GLFW_KEY_HOME = 268;
    static final int GLFW_KEY_END = 269;
    static final int GLFW_KEY_CAPS_LOCK = 280;
    static final int GLFW_KEY_SCROLL_LOCK = 281;
    static final int GLFW_KEY_NUM_LOCK = 282;
    static final int GLFW_KEY_PRINT_SCREEN = 283;
    static final int GLFW_KEY_PAUSE = 284;
    static final int GLFW_KEY_F1 = 290;
    static final int GLFW_KEY_F12 = 301;
    static final int GLFW_KEY_KP_0 = 320;
    static final int GLFW_KEY_KP_9 = 329;
    static final int GLFW_KEY_KP_DECIMAL = 330;
    static final int GLFW_KEY_KP_DIVIDE = 331;
    static final int GLFW_KEY_KP_MULTIPLY = 332;
    static final int GLFW_KEY_KP_SUBTRACT = 333;
    static final int GLFW_KEY_KP_ADD = 334;
    static final int GLFW_KEY_KP_ENTER = 335;
    static final int GLFW_KEY_KP_EQUAL = 336;
    static final int GLFW_KEY_LEFT_SHIFT = 340;
    static final int GLFW_KEY_LEFT_CONTROL = 341;
    static final int GLFW_KEY_LEFT_ALT = 342;
    static final int GLFW_KEY_RIGHT_SHIFT = 344;
    static final int GLFW_KEY_RIGHT_CONTROL = 345;
    static final int GLFW_KEY_RIGHT_ALT = 346;

    private static final int[] KEYPAD = {
        KeyCode.KEYP_0, KeyCode.KEYP_1, KeyCode.KEYP_2, KeyCode.KEYP_3, KeyCode.KEYP_4,
        KeyCode.KEYP_5, KeyCode.KEYP_6, KeyCode.KEYP_7, KeyCode.KEYP_8, KeyCode.KEYP_9,
    };

    private GlfwKeys() {
    }

    /** The {@link KeyCode} for a GLFW key token, or 0 if the game does not use the key. */
    static int translate(int glfwKey) {
        if (glfwKey >= GLFW_KEY_A && glfwKey <= GLFW_KEY_Z) {
            return glfwKey - GLFW_KEY_A + 'a';
        }
        if (glfwKey >= GLFW_KEY_0 && glfwKey <= GLFW_KEY_9) {
            return glfwKey;
        }
        if (glfwKey >= GLFW_KEY_F1 && glfwKey <= GLFW_KEY_F12) {
            return KeyCode.F1 + (glfwKey - GLFW_KEY_F1) + (glfwKey >= GLFW_KEY_F1 + 10 ? 0x57 - 0x44 - 1 : 0);
        }
        if (glfwKey >= GLFW_KEY_KP_0 && glfwKey <= GLFW_KEY_KP_9) {
            return KEYPAD[glfwKey - GLFW_KEY_KP_0];
        }
        return switch (glfwKey) {
            case GLFW_KEY_SPACE -> ' ';
            case GLFW_KEY_APOSTROPHE -> '\'';
            case GLFW_KEY_COMMA -> ',';
            case GLFW_KEY_MINUS -> KeyCode.MINUS;
            case GLFW_KEY_PERIOD -> '.';
            case GLFW_KEY_SLASH -> '/';
            case GLFW_KEY_SEMICOLON -> ';';
            case GLFW_KEY_EQUAL -> KeyCode.EQUALS;
            case GLFW_KEY_LEFT_BRACKET -> '[';
            case GLFW_KEY_BACKSLASH -> '\\';
            case GLFW_KEY_RIGHT_BRACKET -> ']';
            case GLFW_KEY_GRAVE_ACCENT -> '`';
            case GLFW_KEY_ESCAPE -> KeyCode.ESCAPE;
            case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER -> KeyCode.ENTER;
            case GLFW_KEY_TAB -> KeyCode.TAB;
            case GLFW_KEY_BACKSPACE -> KeyCode.BACKSPACE;
            case GLFW_KEY_INSERT -> KeyCode.INS;
            case GLFW_KEY_DELETE -> KeyCode.DEL;
            case GLFW_KEY_RIGHT -> KeyCode.RIGHTARROW;
            case GLFW_KEY_LEFT -> KeyCode.LEFTARROW;
            case GLFW_KEY_DOWN -> KeyCode.DOWNARROW;
            case GLFW_KEY_UP -> KeyCode.UPARROW;
            case GLFW_KEY_PAGE_UP -> KeyCode.PGUP;
            case GLFW_KEY_PAGE_DOWN -> KeyCode.PGDN;
            case GLFW_KEY_HOME -> KeyCode.HOME;
            case GLFW_KEY_END -> KeyCode.END;
            case GLFW_KEY_CAPS_LOCK -> KeyCode.CAPSLOCK;
            case GLFW_KEY_SCROLL_LOCK -> KeyCode.SCRLCK;
            case GLFW_KEY_NUM_LOCK -> KeyCode.NUMLOCK;
            case GLFW_KEY_PRINT_SCREEN -> KeyCode.PRTSCR;
            case GLFW_KEY_PAUSE -> KeyCode.PAUSE;
            case GLFW_KEY_KP_DIVIDE -> KeyCode.KEYP_DIVIDE;
            case GLFW_KEY_KP_MULTIPLY -> KeyCode.KEYP_MULTIPLY;
            case GLFW_KEY_KP_SUBTRACT -> KeyCode.KEYP_MINUS;
            case GLFW_KEY_KP_ADD -> KeyCode.KEYP_PLUS;
            case GLFW_KEY_KP_EQUAL -> KeyCode.KEYP_EQUALS;
            case GLFW_KEY_KP_DECIMAL -> KeyCode.KEYP_PERIOD;
            case GLFW_KEY_LEFT_SHIFT, GLFW_KEY_RIGHT_SHIFT -> KeyCode.RSHIFT;
            case GLFW_KEY_LEFT_CONTROL, GLFW_KEY_RIGHT_CONTROL -> KeyCode.RCTRL;
            case GLFW_KEY_LEFT_ALT -> KeyCode.LALT;
            case GLFW_KEY_RIGHT_ALT -> KeyCode.RALT;
            default -> 0;
        };
    }
}
