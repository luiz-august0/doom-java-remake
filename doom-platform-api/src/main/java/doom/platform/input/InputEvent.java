package doom.platform.input;

/**
 * One input event. Instances are owned and reused by {@link InputEventQueue}; never keep a reference after the
 * queue is cleared. Fields are public for allocation-free hot-path access.
 */
public final class InputEvent {

    /** A key was pressed; {@link #code} is a {@link KeyCode}. Auto-repeat produces more KEY_DOWN events. */
    public static final int KEY_DOWN = 1;
    /** A key was released; {@link #code} is a {@link KeyCode}. */
    public static final int KEY_UP = 2;
    /** Text input; {@link #code} is the Unicode code point (used for typing save names and cheats). */
    public static final int CHAR = 3;
    /** Relative mouse motion; {@link #dx}, {@link #dy} in raw counts (positive y = down). */
    public static final int MOUSE_MOVE = 4;
    /** Mouse button pressed; {@link #code} is the button index (0 left, 1 right, 2 middle, 3+ extra). */
    public static final int MOUSE_DOWN = 5;
    /** Mouse button released. */
    public static final int MOUSE_UP = 6;
    /** Mouse wheel; {@link #dy} is the scroll amount (positive = away from the user). */
    public static final int WHEEL = 7;
    /** Gamepad button pressed; {@link #code} is the GLFW/SDL gamepad button index, {@link #device} the pad. */
    public static final int PAD_DOWN = 8;
    /** Gamepad button released. */
    public static final int PAD_UP = 9;
    /** Gamepad axis moved; {@link #code} is the axis index, {@link #value} in [-1, 1]. */
    public static final int PAD_AXIS = 10;
    /** The window gained focus. */
    public static final int FOCUS_GAINED = 11;
    /** The window lost focus (release captured mouse, clear held keys). */
    public static final int FOCUS_LOST = 12;
    /** The output size changed; {@link #code} = new width, {@link #device} = new height (physical pixels). */
    public static final int RESIZED = 13;

    public int type;
    public int code;
    public int device;
    public double dx;
    public double dy;
    public double value;
    /** Modifier state when the event happened: bit 0 shift, bit 1 ctrl, bit 2 alt, bit 3 super. */
    public int modifiers;
    public long timeNanos;

    void clear() {
        type = 0;
        code = 0;
        device = 0;
        dx = 0;
        dy = 0;
        value = 0;
        modifiers = 0;
        timeNanos = 0;
    }
}
