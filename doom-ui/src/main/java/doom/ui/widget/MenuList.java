package doom.ui.widget;

import java.util.ArrayList;
import java.util.List;

/**
 * The cursor logic of a menu ({@code menu_t} plus the navigation part of {@code M_Responder}). It plays no sound: each
 * call reports what happened so that the owner can play the original sounds.
 */
public final class MenuList {

    private final List<MenuItem> items;
    private int itemOn;
    private int lastOn;

    public MenuList(List<MenuItem> items, int firstItem) {
        this.items = new ArrayList<>(items);
        this.itemOn = firstItem;
        this.lastOn = firstItem;
    }

    public int size() {
        return items.size();
    }

    public MenuItem item(int i) {
        return items.get(i);
    }

    /** Drops the last items ({@code numitems--}). */
    public void truncate(int count) {
        while (items.size() > count) {
            items.remove(items.size() - 1);
        }
        if (itemOn >= items.size()) {
            itemOn = Math.max(0, items.size() - 1);
        }
    }

    public void replace(int i, MenuItem item) {
        items.set(i, item);
    }

    public void add(MenuItem item) {
        items.add(item);
    }

    /** {@code itemOn}. */
    public int itemOn() {
        return itemOn;
    }

    public void setItemOn(int i) {
        itemOn = i;
    }

    /** {@code lastOn}: where the cursor was when the menu was left. */
    public int lastOn() {
        return lastOn;
    }

    public void setLastOn(int i) {
        lastOn = i;
    }

    /** Moves down with wrap, skipping spacers. Returns the number of steps taken (each plays a sound in the original). */
    public int down() {
        int steps = 0;
        do {
            if (itemOn + 1 > items.size() - 1) {
                itemOn = 0;
            } else {
                itemOn++;
            }
            steps++;
        } while (items.get(itemOn).skipped() && steps <= items.size());
        return steps;
    }

    /** Moves up with wrap, skipping spacers. Returns the number of steps taken. */
    public int up() {
        int steps = 0;
        do {
            if (itemOn == 0) {
                itemOn = items.size() - 1;
            } else {
                itemOn--;
            }
            steps++;
        } while (items.get(itemOn).skipped() && steps <= items.size());
        return steps;
    }

    /**
     * Left/right on the current item. Returns true (and calls the action with 0 or 1) when the item has arrows,
     * false when nothing happened.
     */
    public boolean slide(int direction) {
        MenuItem it = items.get(itemOn);
        if (it.hasArrows()) {
            it.action().accept(direction);
            return true;
        }
        return false;
    }

    /**
     * Enter on the current item. Returns 0 when nothing happened, 1 when a slider/toggle was advanced, 2 when an action
     * ran (the original plays {@code sfx_stnmov} for 1 and {@code sfx_pistol} for 2).
     */
    public int activate() {
        MenuItem it = items.get(itemOn);
        if (it.action() == null || !it.enabled() || it.skipped()) {
            return 0;
        }
        lastOn = itemOn;
        if (it.hasArrows()) {
            it.action().accept(1);
            return 1;
        }
        it.action().accept(itemOn);
        return 2;
    }

    /**
     * Hot key search: from the item after the cursor to the end, then from the start up to the cursor
     * (including it). Returns true when the cursor moved.
     */
    public boolean shortcut(int ch) {
        for (int i = itemOn + 1; i < items.size(); i++) {
            if (items.get(i).hotkey() == ch) {
                itemOn = i;
                return true;
            }
        }
        for (int i = 0; i <= itemOn && i < items.size(); i++) {
            if (items.get(i).hotkey() == ch) {
                itemOn = i;
                return true;
            }
        }
        return false;
    }
}
