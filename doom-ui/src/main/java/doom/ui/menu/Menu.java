package doom.ui.menu;

import doom.render.canvas.Canvas2D;
import doom.ui.widget.MenuList;
import java.util.function.Consumer;

/** {@code menu_t}: a list, its screen position, the menu to go back to and a draw routine. */
final class Menu {

    static final int LINE_HEIGHT = 16;

    final String name;
    final MenuList list;
    Menu prev;
    int x;
    int y;
    int lineHeight = LINE_HEIGHT;
    /** The drawer draws the rows and the cursor itself (extended pages). */
    boolean customBody;
    /** The menu is a full-screen help page. */
    boolean fullScreen;
    Consumer<Canvas2D> drawer;

    Menu(String name, MenuList list, Menu prev, int x, int y, Consumer<Canvas2D> drawer) {
        this.name = name;
        this.list = list;
        this.prev = prev;
        this.x = x;
        this.y = y;
        this.drawer = drawer;
    }

    @Override
    public String toString() {
        return name;
    }
}
