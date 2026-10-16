package doom.math;

import static doom.math.BBox.BOXBOTTOM;
import static doom.math.BBox.BOXLEFT;
import static doom.math.BBox.BOXRIGHT;
import static doom.math.BBox.BOXTOP;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BBoxTest {

    @Test
    void clearUsesTheExtremeValues() {
        int[] box = {1, 2, 3, 4};
        BBox.clear(box);
        assertThat(box[BOXTOP]).isEqualTo(Integer.MIN_VALUE);
        assertThat(box[BOXRIGHT]).isEqualTo(Integer.MIN_VALUE);
        assertThat(box[BOXBOTTOM]).isEqualTo(Integer.MAX_VALUE);
        assertThat(box[BOXLEFT]).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void firstPointOnlySetsLeftAndBottom() {
        // M_AddToBox uses "else if": the first point after M_ClearBox moves left and bottom but not right and top.
        int[] box = new int[4];
        BBox.clear(box);
        BBox.add(box, 10, 20);
        assertThat(box[BOXLEFT]).isEqualTo(10);
        assertThat(box[BOXBOTTOM]).isEqualTo(20);
        assertThat(box[BOXRIGHT]).isEqualTo(Integer.MIN_VALUE);
        assertThat(box[BOXTOP]).isEqualTo(Integer.MIN_VALUE);
    }

    @Test
    void laterPointsGrowTheBox() {
        int[] box = new int[4];
        BBox.clear(box);
        BBox.add(box, 10, 20);
        BBox.add(box, 30, 40);
        BBox.add(box, -5, 25);
        BBox.add(box, 15, -8);
        assertThat(box[BOXLEFT]).isEqualTo(-5);
        assertThat(box[BOXRIGHT]).isEqualTo(30);
        assertThat(box[BOXBOTTOM]).isEqualTo(-8);
        assertThat(box[BOXTOP]).isEqualTo(40);
    }

    @Test
    void aPointLeftOfTheBoxNeverMovesTheRightEdge() {
        int[] box = {100, 0, 0, 50};
        BBox.add(box, -10, 50);
        assertThat(box).containsExactly(100, 0, -10, 50);
    }
}
