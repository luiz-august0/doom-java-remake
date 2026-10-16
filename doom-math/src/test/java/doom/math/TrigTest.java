package doom.math;

import static doom.math.Fixed.FRACUNIT;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TrigTest {

    @Test
    void axisDirections() {
        assertThat(Trig.pointToAngle2(0, 0, 0, 0)).isZero();
        assertThat(Trig.pointToAngle2(0, 0, FRACUNIT, 0)).isZero(); // east
        assertThat(Trig.pointToAngle2(0, 0, 0, FRACUNIT)).isEqualTo(Angle.ANG90 - 1); // north (octant 1)
        assertThat(Trig.pointToAngle2(0, 0, -FRACUNIT, 0)).isEqualTo(Angle.ANG180 - 1); // west (octant 3)
        // South has x == 0, which takes the x >= 0 branch (octant 7), so there is no "- 1" here.
        assertThat(Trig.pointToAngle2(0, 0, 0, -FRACUNIT)).isEqualTo(Angle.ANG270);
    }

    @Test
    void diagonals() {
        assertThat(Trig.pointToAngle2(0, 0, FRACUNIT, FRACUNIT)).isEqualTo(Angle.ANG90 - 1 - Angle.ANG45);
        assertThat(Trig.pointToAngle2(0, 0, -FRACUNIT, FRACUNIT)).isEqualTo(Angle.ANG90 + Angle.ANG45);
        assertThat(Trig.pointToAngle2(0, 0, -FRACUNIT, -FRACUNIT)).isEqualTo(Angle.ANG270 - 1 - Angle.ANG45);
        assertThat(Trig.pointToAngle2(0, 0, FRACUNIT, -FRACUNIT)).isEqualTo(Angle.ANG270 + Angle.ANG45);
    }

    @Test
    void originIsTheFirstPoint() {
        int x = 1234 * FRACUNIT;
        int y = -567 * FRACUNIT;
        assertThat(Trig.pointToAngle2(x, y, x + 10 * FRACUNIT, y + 3 * FRACUNIT))
                .isEqualTo(Trig.pointToAngle2(0, 0, 10 * FRACUNIT, 3 * FRACUNIT));
    }

    @Test
    void tinyDistancesHitTheSlopeDivClamp() {
        // SlopeDiv returns SLOPERANGE when the larger coordinate is below 512, so a vector of a few fixed-point units
        // straight north reads tantoangle[2048] (45 degrees) in octant 1: the vanilla quirk.
        assertThat(Trig.pointToAngle2(0, 0, 0, 1)).isEqualTo(Angle.ANG90 - 1 - Angle.ANG45);
        assertThat(Trig.pointToAngle2(0, 0, 0, 512)).isEqualTo(Angle.ANG90 - 1);
    }

    @Test
    void anglesIncreaseCounterClockwise() {
        int previous = -1;
        for (int deg = 0; deg < 360; deg += 15) {
            // Points on a large square around the origin, walked counter-clockwise from east.
            int x;
            int y;
            int s = 1000 * FRACUNIT;
            int t = (int) ((long) s * (deg % 90) / 90);
            switch (deg / 90) {
                case 0 -> {
                    x = s - t;
                    y = t;
                }
                case 1 -> {
                    x = -t;
                    y = s - t;
                }
                case 2 -> {
                    x = -s + t;
                    y = -t;
                }
                default -> {
                    x = t;
                    y = -s + t;
                }
            }
            int angle = Trig.pointToAngle2(0, 0, x, y);
            if (previous != -1) {
                assertThat(Angle.gt(angle, previous)).as("angle at %d degrees", deg).isTrue();
            }
            previous = angle;
        }
    }

    @Test
    void approxDistance() {
        assertThat(Trig.approxDistance(3 * FRACUNIT, 4 * FRACUNIT)).isEqualTo(3 * FRACUNIT + 4 * FRACUNIT - 3 * FRACUNIT / 2);
        assertThat(Trig.approxDistance(-4 * FRACUNIT, 3 * FRACUNIT)).isEqualTo(4 * FRACUNIT + 3 * FRACUNIT - 3 * FRACUNIT / 2);
        assertThat(Trig.approxDistance(0, 0)).isZero();
        assertThat(Trig.approxDistance(5, 5)).isEqualTo(5 + 5 - 2);
        // abs(MIN_VALUE) stays negative, as on x86.
        assertThat(Trig.approxDistance(Integer.MIN_VALUE, 0)).isEqualTo(Integer.MIN_VALUE / 2);
    }
}
