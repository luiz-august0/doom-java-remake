package doom.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FrameBufferTest {

    @Test
    void layoutsIndexDifferently() {
        FrameBuffer row = new FrameBuffer(5, 3, FrameLayout.ROW_MAJOR);
        FrameBuffer col = new FrameBuffer(5, 3, FrameLayout.COLUMN_MAJOR);
        assertThat(row.index(2, 1)).isEqualTo(1 * 5 + 2);
        assertThat(col.index(2, 1)).isEqualTo(2 * 3 + 1);
        assertThat(row.rowStride()).isEqualTo(5);
        assertThat(row.columnStride()).isEqualTo(1);
        assertThat(col.rowStride()).isEqualTo(1);
        assertThat(col.columnStride()).isEqualTo(3);
        assertThat(col.columnMajor()).isTrue();
        row.set(4, 2, 200);
        col.set(4, 2, 200);
        assertThat(row.get(4, 2)).isEqualTo(200);
        assertThat(col.pixels()[4 * 3 + 2]).isEqualTo((byte) 200);
    }

    @Test
    void boundsAreChecked() {
        FrameBuffer fb = new FrameBuffer(4, 4);
        assertThatThrownBy(() -> fb.get(4, 0)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> fb.set(0, -1, 0)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> new FrameBuffer(0, 4)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resizeReusesTheArrayWhenItFits() {
        FrameBuffer fb = new FrameBuffer(100, 100);
        byte[] before = fb.pixels();
        assertThat(fb.resize(50, 100)).isTrue();
        assertThat(fb.pixels()).isSameAs(before);
        assertThat(fb.size()).isEqualTo(5000);
        assertThat(fb.resize(50, 100)).isFalse();
        fb.resize(200, 200);
        assertThat(fb.pixels()).hasSize(40000).isNotSameAs(before);
    }

    @Test
    void copyConvertsLayouts() {
        FrameBuffer a = new FrameBuffer(7, 5, FrameLayout.ROW_MAJOR);
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 7; x++) {
                a.set(x, y, x * 10 + y);
            }
        }
        FrameBuffer b = new FrameBuffer(7, 5, FrameLayout.COLUMN_MAJOR);
        b.copyFrom(a);
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 7; x++) {
                assertThat(b.get(x, y)).isEqualTo(x * 10 + y);
            }
        }
        FrameBuffer c = new FrameBuffer(7, 5, FrameLayout.ROW_MAJOR);
        c.copyFrom(b);
        assertThat(c.pixels()).containsExactly(a.pixels());
    }
}
