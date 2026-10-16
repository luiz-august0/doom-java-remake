package doom.ui.widget;

import doom.render.canvas.Canvas2D;
import java.util.ArrayList;
import java.util.List;

/** Stack of screens: input goes to the top, drawing goes from the lowest opaque screen up to the top. */
public final class ScreenStack {

    private final List<Screen> screens = new ArrayList<>();

    public boolean isEmpty() {
        return screens.isEmpty();
    }

    public int size() {
        return screens.size();
    }

    public Screen top() {
        return screens.isEmpty() ? null : screens.get(screens.size() - 1);
    }

    public void push(Screen s) {
        screens.add(s);
        s.onShow();
    }

    /** Removes and returns the top screen, or null. */
    public Screen pop() {
        if (screens.isEmpty()) {
            return null;
        }
        Screen s = screens.remove(screens.size() - 1);
        s.onHide();
        return s;
    }

    /** Replaces the top screen. */
    public void replace(Screen s) {
        pop();
        push(s);
    }

    public void clear() {
        while (!screens.isEmpty()) {
            pop();
        }
    }

    /** Ticks the top screen only. */
    public void tick() {
        Screen t = top();
        if (t != null) {
            t.tick();
        }
    }

    public boolean key(int code) {
        Screen t = top();
        return t != null && t.key(code);
    }

    public void draw(Canvas2D canvas, float alpha) {
        int first = screens.size() - 1;
        while (first > 0 && screens.get(first).transparent()) {
            first--;
        }
        for (int i = Math.max(first, 0); i < screens.size(); i++) {
            screens.get(i).draw(canvas, alpha);
        }
    }
}
