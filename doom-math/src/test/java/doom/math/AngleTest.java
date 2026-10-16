package doom.math;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class AngleTest {

    @Test
    void constantsMatchTablesH() {
        assertThat(Angle.ANG45).isEqualTo(0x20000000);
        assertThat(Angle.ANG90).isEqualTo(0x40000000);
        assertThat(Angle.ANG180).isEqualTo(0x80000000).isNegative();
        assertThat(Angle.ANG270).isEqualTo(0xc0000000);
        assertThat(Angle.ANGLE_MAX).isEqualTo(-1);
        assertThat(Angle.ANG1).isEqualTo(11930464);
        assertThat(Angle.FINEMASK).isEqualTo(8191);
    }

    @Test
    void comparisonsAreUnsigned() {
        assertThat(Angle.gt(Angle.ANG180, Angle.ANG90)).isTrue();
        assertThat(Angle.lt(Angle.ANG90, Angle.ANG270)).isTrue();
        assertThat(Angle.gt(Angle.ANGLE_MAX, 0)).isTrue();
        assertThat(Angle.ge(Angle.ANG180, Angle.ANG180)).isTrue();
        assertThat(Angle.le(Angle.ANG180, Angle.ANG180)).isTrue();
        assertThat(Angle.lt(Angle.ANG180, Angle.ANG180)).isFalse();
    }

    @Test
    void comparisonsAgreeWithUnsignedLongArithmetic() {
        SplittableRandom random = new SplittableRandom(42);
        int[] specials = {0, 1, -1, Angle.ANG90, Angle.ANG180, Angle.ANG180 - 1, Angle.ANG270, Integer.MAX_VALUE};
        for (int i = 0; i < 200_000; i++) {
            int a = i < 64 ? specials[i & 7] : random.nextInt();
            int b = i < 64 ? specials[i >>> 3] : random.nextInt();
            long ua = a & 0xffffffffL;
            long ub = b & 0xffffffffL;
            assertThat(Angle.lt(a, b)).isEqualTo(ua < ub);
            assertThat(Angle.le(a, b)).isEqualTo(ua <= ub);
            assertThat(Angle.gt(a, b)).isEqualTo(ua > ub);
            assertThat(Angle.ge(a, b)).isEqualTo(ua >= ub);
        }
    }

    @Test
    void toFineIsALogicalShift() {
        assertThat(Angle.toFine(0)).isZero();
        assertThat(Angle.toFine(Angle.ANG90)).isEqualTo(2048);
        assertThat(Angle.toFine(Angle.ANG180)).isEqualTo(4096);
        assertThat(Angle.toFine(Angle.ANGLE_MAX)).isEqualTo(8191);
        SplittableRandom random = new SplittableRandom(7);
        for (int i = 0; i < 100_000; i++) {
            int a = random.nextInt();
            assertThat(Angle.toFine(a)).isBetween(0, Angle.FINEMASK).isEqualTo((int) ((a & 0xffffffffL) >> 19));
        }
    }

    @Test
    void additionWrapsAroundTheCircle() {
        assertThat(Angle.ANG270 + Angle.ANG180).isEqualTo(Angle.ANG90);
        assertThat(Angle.ANG90 - Angle.ANG180).isEqualTo(Angle.ANG270);
        assertThat(-Angle.ANG90).isEqualTo(Angle.ANG270);
    }
}
