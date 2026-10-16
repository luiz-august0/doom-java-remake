package doom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import doom.ui.widget.MenuItem;
import doom.ui.widget.MenuList;
import doom.ui.widget.Screen;
import doom.ui.widget.ScreenStack;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WidgetTest {

    @Test
    void menuListWrapsAndSkipsSpacers() {
        List<Integer> calls = new ArrayList<>();
        MenuList l = new MenuList(List.of(MenuItem.action("A", 'a', calls::add), MenuItem.spacer(),
            MenuItem.action("B", 'b', calls::add)), 0);
        assertThat(l.down()).isEqualTo(2);
        assertThat(l.itemOn()).isEqualTo(2);
        assertThat(l.down()).isEqualTo(1);
        assertThat(l.itemOn()).isZero();
        assertThat(l.up()).isEqualTo(1);
        assertThat(l.itemOn()).isEqualTo(2);
        assertThat(l.activate()).isEqualTo(2);
        assertThat(calls).containsExactly(2);
        assertThat(l.lastOn()).isEqualTo(2);
    }

    @Test
    void allSpacersDoNotLoopForever() {
        MenuList l = new MenuList(List.of(MenuItem.spacer(), MenuItem.spacer()), 0);
        assertThat(l.down()).isPositive();
    }

    @Test
    void slidersGetDirectionsAndEnterMovesRight() {
        List<Integer> calls = new ArrayList<>();
        MenuList l = new MenuList(List.of(MenuItem.slider("S", "", 's', calls::add)), 0);
        assertThat(l.slide(0)).isTrue();
        assertThat(l.slide(1)).isTrue();
        assertThat(l.activate()).isEqualTo(1);
        assertThat(calls).containsExactly(0, 1, 1);
    }

    @Test
    void disabledItemsDoNothing() {
        List<Integer> calls = new ArrayList<>();
        MenuItem it = MenuItem.textEntry('1', calls::add);
        it.setEnabled(false);
        MenuList l = new MenuList(List.of(it), 0);
        assertThat(l.activate()).isZero();
        assertThat(calls).isEmpty();
    }

    @Test
    void shortcutSearchStartsAfterTheCursorAndWraps() {
        MenuList l = new MenuList(List.of(MenuItem.action("A", 'x', c -> {
        }), MenuItem.action("B", 'y', c -> {
        }), MenuItem.action("C", 'x', c -> {
        })), 0);
        assertThat(l.shortcut('x')).isTrue();
        assertThat(l.itemOn()).isEqualTo(2);
        assertThat(l.shortcut('x')).isTrue();
        assertThat(l.itemOn()).isZero();
        assertThat(l.shortcut('q')).isFalse();
    }

    @Test
    void screenStackDrawsFromTheLowestOpaqueScreen() {
        List<String> log = new ArrayList<>();
        ScreenStack stack = new ScreenStack();
        stack.push(screen("a", false, log));
        stack.push(screen("b", true, log));
        stack.push(screen("c", false, log));
        stack.push(screen("d", true, log));
        stack.draw(null, 0);
        assertThat(log).containsExactly("show a", "show b", "show c", "show d", "draw c", "draw d");
        stack.tick();
        assertThat(log).endsWith("tick d");
        stack.pop();
        assertThat(log).endsWith("hide d");
        assertThat(stack.size()).isEqualTo(3);
    }

    private static Screen screen(String name, boolean transparent, List<String> log) {
        return new Screen() {
            @Override
            public void tick() {
                log.add("tick " + name);
            }

            @Override
            public void draw(doom.render.canvas.Canvas2D canvas, float alpha) {
                log.add("draw " + name);
            }

            @Override
            public void onShow() {
                log.add("show " + name);
            }

            @Override
            public void onHide() {
                log.add("hide " + name);
            }

            @Override
            public boolean transparent() {
                return transparent;
            }
        };
    }
}
