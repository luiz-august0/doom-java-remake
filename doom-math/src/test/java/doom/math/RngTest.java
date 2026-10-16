package doom.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RngTest {

    @Test
    void gameAdvancesBeforeReading() {
        Rng rng = new Rng();
        assertThat(rng.gameIndex()).isZero();
        // rndtable[1], [2], [3]: the first call reads index 1, never index 0.
        assertThat(rng.game()).isEqualTo(8);
        assertThat(rng.game()).isEqualTo(109);
        assertThat(rng.game()).isEqualTo(220);
        assertThat(rng.gameIndex()).isEqualTo(3);
        assertThat(rng.miscIndex()).isZero();
    }

    @Test
    void miscHasItsOwnIndex() {
        Rng rng = new Rng();
        rng.game();
        rng.game();
        assertThat(rng.misc()).isEqualTo(8);
        assertThat(rng.miscIndex()).isEqualTo(1);
        assertThat(rng.gameIndex()).isEqualTo(2);
    }

    @Test
    void indexWrapsAfter255() {
        Rng rng = new Rng();
        for (int i = 0; i < 255; i++) {
            rng.game();
        }
        assertThat(rng.gameIndex()).isEqualTo(255);
        assertThat(rng.game()).isEqualTo(Tables.rnd(0)).isZero();
        assertThat(rng.gameIndex()).isZero();
        assertThat(rng.game()).isEqualTo(8);
    }

    @Test
    void fullCycleReadsTheWholeTableInOrder() {
        Rng rng = new Rng();
        int sum = 0;
        for (int i = 1; i <= 256; i++) {
            int v = rng.misc();
            assertThat(v).isEqualTo(Tables.rnd(i & 0xff));
            sum += v;
        }
        int expected = 0;
        for (int i = 0; i < 256; i++) {
            expected += Tables.rnd(i);
        }
        assertThat(sum).isEqualTo(expected);
        assertThat(rng.miscIndex()).isZero();
    }

    @Test
    void subRandomIsFirstMinusSecond() {
        Rng rng = new Rng();
        assertThat(rng.subRandom()).isEqualTo(8 - 109);
        assertThat(rng.subRandom()).isEqualTo(220 - 222);
        assertThat(rng.gameIndex()).isEqualTo(4);
    }

    @Test
    void clearResetsBothIndices() {
        Rng rng = new Rng();
        rng.game();
        rng.misc();
        rng.misc();
        rng.clear();
        assertThat(rng.gameIndex()).isZero();
        assertThat(rng.miscIndex()).isZero();
        assertThat(rng.game()).isEqualTo(8);
    }

    @Test
    void indicesCanBeRestored() {
        Rng rng = new Rng();
        rng.setIndices(254, 17);
        assertThat(rng.gameIndex()).isEqualTo(254);
        assertThat(rng.miscIndex()).isEqualTo(17);
        assertThat(rng.game()).isEqualTo(Tables.rnd(255));
        assertThat(rng.misc()).isEqualTo(Tables.rnd(18));
        rng.setGameIndex(0);
        rng.setMiscIndex(255);
        assertThat(rng.gameIndex()).isZero();
        assertThat(rng.miscIndex()).isEqualTo(255);

        assertThatThrownBy(() -> rng.setIndices(256, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rng.setIndices(0, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rng.setGameIndex(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rng.setMiscIndex(300)).isInstanceOf(IllegalArgumentException.class);
        assertThat(rng.gameIndex()).isZero();
    }

    @Test
    void instancesAreIndependent() {
        Rng a = new Rng();
        Rng b = new Rng();
        a.game();
        a.game();
        assertThat(b.game()).isEqualTo(8);
        assertThat(a.gameIndex()).isEqualTo(2);
    }
}
