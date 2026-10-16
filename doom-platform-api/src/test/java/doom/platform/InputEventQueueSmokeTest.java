package doom.platform;

import static org.assertj.core.api.Assertions.assertThat;

import doom.platform.input.InputEvent;
import doom.platform.input.InputEventQueue;
import org.junit.jupiter.api.Test;

class InputEventQueueSmokeTest {

    @Test
    void queueGrowsAndReusesEvents() {
        InputEventQueue q = new InputEventQueue(16);
        for (int i = 0; i < 40; i++) {
            q.add(InputEvent.KEY_DOWN, i).code = i;
        }
        assertThat(q.size()).isEqualTo(40);
        assertThat(q.get(39).code).isEqualTo(39);
        InputEvent first = q.get(0);
        q.clear();
        assertThat(q.add(InputEvent.KEY_UP, 0)).isSameAs(first);
        assertThat(first.code).isZero();
    }
}
