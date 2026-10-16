package doom.ui.widget;

import java.util.function.IntConsumer;

/**
 * One row of a {@link MenuList}: {@code menuitem_t}. The original {@code status} maps to {@link Kind}: 1 = action,
 * 2 = slider (arrows work), 0 = disabled, -1 = spacer (the cursor skips it).
 */
public final class MenuItem {

    /** Behaviour of the row. */
    public enum Kind {
        /** Enter runs the action with the item index. */
        ACTION,
        /** Left/right call the action with 0/1; Enter calls it with 1. */
        SLIDER,
        /** Left/right/Enter call the action with 0/1 (extended pages). */
        TOGGLE,
        /** Enter starts text entry (save slots); the owner handles the typing. */
        TEXT_ENTRY,
        /** Not selectable. */
        SPACER
    }

    private final Kind kind;
    private final String patch;
    private final String label;
    private final int hotkey;
    private final IntConsumer action;
    private boolean enabled = true;

    private MenuItem(Kind kind, String patch, String label, int hotkey, IntConsumer action) {
        this.kind = kind;
        this.patch = patch;
        this.label = label;
        this.hotkey = hotkey;
        this.action = action;
    }

    /** A graphic row ({@code {1,"M_NGAME",M_NewGame,'n'}}). */
    public static MenuItem action(String patch, int hotkey, IntConsumer action) {
        return new MenuItem(Kind.ACTION, patch, "", hotkey, action);
    }

    /** A row with a text label in the WAD font. */
    public static MenuItem textAction(String label, int hotkey, IntConsumer action) {
        return new MenuItem(Kind.ACTION, "", label, hotkey, action);
    }

    /** A slider row ({@code status = 2}); {@code patch} may be empty for text rows. */
    public static MenuItem slider(String patch, String label, int hotkey, IntConsumer action) {
        return new MenuItem(Kind.SLIDER, patch, label, hotkey, action);
    }

    public static MenuItem toggle(String label, IntConsumer action) {
        return new MenuItem(Kind.TOGGLE, "", label, 0, action);
    }

    /** A save slot row: empty name, hot key '1'..'6'. */
    public static MenuItem textEntry(int hotkey, IntConsumer action) {
        return new MenuItem(Kind.TEXT_ENTRY, "", "", hotkey, action);
    }

    /** {@code {-1,"",0,'\0'}}. */
    public static MenuItem spacer() {
        return new MenuItem(Kind.SPACER, "", "", 0, null);
    }

    public Kind kind() {
        return kind;
    }

    /** Lump name of the row graphic, or "". */
    public String patch() {
        return patch;
    }

    /** Text label, or "". */
    public String label() {
        return label;
    }

    /** {@code alphaKey}: a lower-case character, or 0. */
    public int hotkey() {
        return hotkey;
    }

    public IntConsumer action() {
        return action;
    }

    /** {@code status != 0}: whether the cursor action works (Load menu slots without a file are disabled). */
    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** {@code status == -1}. */
    public boolean skipped() {
        return kind == Kind.SPACER;
    }

    /** {@code status == 2}: left/right act on it. */
    public boolean hasArrows() {
        return (kind == Kind.SLIDER || kind == Kind.TOGGLE) && action != null;
    }
}
