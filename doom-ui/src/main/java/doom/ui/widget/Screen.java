package doom.ui.widget;

import doom.render.canvas.Canvas2D;

/** A full UI screen or a modal overlay on the {@link ScreenStack}. Time advances only in {@link #tick()}. */
public interface Screen {

    /** One game tic (35 Hz). */
    default void tick() {
    }

    /** Draws only; never changes state. */
    void draw(Canvas2D canvas, float alpha);

    /** Called when the screen becomes the top of the stack. */
    default void onShow() {
    }

    /** Called when the screen leaves the stack. */
    default void onHide() {
    }

    /** Key press; return true when consumed. */
    default boolean key(int code) {
        return false;
    }

    /** Whether the screens below are still drawn (an overlay) or not. */
    default boolean transparent() {
        return false;
    }
}
