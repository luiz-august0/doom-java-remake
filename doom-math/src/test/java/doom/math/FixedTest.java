package doom.math;

import static doom.math.Fixed.FRACUNIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FixedTest {

    @Test
    void conversions() {
        assertThat(Fixed.fromInt(1)).isEqualTo(FRACUNIT);
        assertThat(Fixed.fromInt(-3)).isEqualTo(-3 * FRACUNIT);
        assertThat(Fixed.toInt(FRACUNIT * 5 + 0xffff)).isEqualTo(5);
        // >> rounds toward negative infinity, like the original.
        assertThat(Fixed.toInt(-1)).isEqualTo(-1);
        assertThat(Fixed.toInt(-FRACUNIT - 1)).isEqualTo(-2);
        assertThat(Fixed.fromInt(0x8000)).isEqualTo(Integer.MIN_VALUE); // wraps like C
    }

    @Test
    void mulKeepsTheLow32BitsOfTheShiftedProduct() {
        assertThat(Fixed.mul(FRACUNIT, FRACUNIT)).isEqualTo(FRACUNIT);
        assertThat(Fixed.mul(3 * FRACUNIT, FRACUNIT / 2)).isEqualTo(3 * FRACUNIT / 2);
        assertThat(Fixed.mul(-FRACUNIT, FRACUNIT / 2)).isEqualTo(-FRACUNIT / 2);
        assertThat(Fixed.mul(-1, 1)).isEqualTo(-1); // arithmetic shift of -1
        assertThat(Fixed.mul(1, 1)).isZero();
        assertThat(Fixed.mul(Integer.MAX_VALUE, Integer.MAX_VALUE)).isEqualTo((int) ((long) Integer.MAX_VALUE * Integer.MAX_VALUE >> 16));
        assertThat(Fixed.mul(Integer.MIN_VALUE, Integer.MIN_VALUE)).isZero(); // 2^62 >> 16 = 2^46, low bits 0
        assertThat(Fixed.mul(Integer.MIN_VALUE, -FRACUNIT)).isEqualTo(Integer.MIN_VALUE); // 2^31 wraps
    }

    @Test
    void divSaturatesLikeTheOriginal() {
        assertThat(Fixed.div(FRACUNIT, 0)).isEqualTo(Integer.MAX_VALUE);
        assertThat(Fixed.div(-FRACUNIT, 0)).isEqualTo(Integer.MIN_VALUE);
        assertThat(Fixed.div(0, 0)).isEqualTo(Integer.MAX_VALUE);
        // (|a| >> 14) >= |b| saturates; the sign comes from a ^ b.
        assertThat(Fixed.div(1 << 14, 1)).isEqualTo(Integer.MAX_VALUE);
        assertThat(Fixed.div((1 << 14) - 1, 1)).isEqualTo(((1 << 14) - 1) << 16);
        assertThat(Fixed.div(-(1 << 14), 1)).isEqualTo(Integer.MIN_VALUE);
        assertThat(Fixed.div(1 << 14, -1)).isEqualTo(Integer.MIN_VALUE);
        assertThat(Fixed.div(-(1 << 14), -1)).isEqualTo(Integer.MAX_VALUE);
        // A divisor of MIN_VALUE has |b| = MIN_VALUE, so every dividend saturates.
        assertThat(Fixed.div(0, Integer.MIN_VALUE)).isEqualTo(Integer.MIN_VALUE);
        assertThat(Fixed.div(-1, Integer.MIN_VALUE)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void divTruncatesTowardZero() {
        assertThat(Fixed.div(FRACUNIT, 3 * FRACUNIT)).isEqualTo(21845);
        assertThat(Fixed.div(-FRACUNIT, 3 * FRACUNIT)).isEqualTo(-21845);
        assertThat(Fixed.div(7 * FRACUNIT, 2 * FRACUNIT)).isEqualTo(7 * FRACUNIT / 2);
        assertThat(Fixed.div(-7, 2)).isEqualTo((int) ((-7L << 16) / 2));
    }

    @Test
    void minValueDividendFollowsChocolateDoom() {
        // |MIN_VALUE| >> 14 is negative: no saturation, and the 64-bit quotient is truncated to 32 bits.
        assertThat(Fixed.div(Integer.MIN_VALUE, 1)).isZero();
        assertThat(Fixed.div(Integer.MIN_VALUE, FRACUNIT)).isEqualTo(Integer.MIN_VALUE);
        assertThat(Fixed.div(Integer.MIN_VALUE, -FRACUNIT)).isEqualTo(Integer.MIN_VALUE);
        assertThatThrownBy(() -> Fixed.div(Integer.MIN_VALUE, 0))
                .isInstanceOf(DoomFatalException.class)
                .hasMessage("FixedDiv: divide by zero");
    }
}
