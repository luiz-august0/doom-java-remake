package doom.ui.widget;

import doom.ui.UiAssets;

/**
 * Single-line text entry of the save slots ({@code saveStringEnter} and the default branch of {@code M_Responder}):
 * at most 23 characters, limited by the pixel width of the border graphic.
 */
public final class TextEntry {

    /** {@code SAVESTRINGSIZE}. */
    public static final int SIZE = 24;

    private final UiAssets assets;
    private final StringBuilder text = new StringBuilder();
    private String oldText = "";
    private boolean active;

    public TextEntry(UiAssets assets) {
        this.assets = assets;
    }

    public boolean active() {
        return active;
    }

    /** Starts editing {@code initial}; Escape restores {@code restore}. */
    public void begin(String initial, String restore) {
        text.setLength(0);
        text.append(initial);
        oldText = restore;
        active = true;
    }

    public String text() {
        return text.toString();
    }

    /** Backspace. */
    public void backspace() {
        if (text.length() > 0) {
            text.setLength(text.length() - 1);
        }
    }

    /** Escape: stop and return the text to restore. */
    public String cancel() {
        active = false;
        return oldText;
    }

    /** Enter: stop; returns the edited text. */
    public String accept() {
        active = false;
        return text.toString();
    }

    /** A typed character: upper-cased, ignored unless the font has it (or it is a space) and the limits allow it. */
    public boolean type(int ch) {
        if (ch >= 'a' && ch <= 'z') {
            ch -= 32;
        }
        if (ch != ' ' && (ch - UiAssets.FONT_START < 0 || ch - UiAssets.FONT_START >= UiAssets.FONT_SIZE)) {
            return false;
        }
        if (ch >= 32 && ch <= 127 && text.length() < SIZE - 1 && FontText.width(assets, text) < (SIZE - 2) * 8) {
            text.append((char) ch);
            return true;
        }
        return false;
    }
}
