package doom.platform.lwjgl;

import static org.assertj.core.api.Assertions.assertThat;

import doom.platform.ScaleMode;
import doom.platform.input.KeyCode;
import org.junit.jupiter.api.Test;

class PresentGeometryTest {

    @Test
    void autoResolutionFillsTheOutput() {
        var r = PresentGeometry.compute(ScaleMode.FIT, 2560, 1080, 2560, 900, 1.2);
        assertThat(r).isEqualTo(new PresentGeometry.Rect(0, 0, 2560, 1080));
    }

    @Test
    void fitLetterboxesAndCentres() {
        // 320x200 with 5:6 pixels is a 4:3 picture; in 1920x1080 it is 1440 wide
        var r = PresentGeometry.compute(ScaleMode.FIT, 1920, 1080, 320, 200, 1.2);
        assertThat(r.height()).isEqualTo(1080);
        assertThat(r.width()).isEqualTo(1440);
        assertThat(r.x()).isEqualTo(240);
        assertThat(r.y()).isZero();
        var sq = PresentGeometry.compute(ScaleMode.FIT, 1000, 1000, 320, 200, 1.0);
        assertThat(sq.width()).isEqualTo(1000);
        assertThat(sq.height()).isEqualTo(625);
        assertThat(sq.y()).isEqualTo(187);
    }

    @Test
    void integerUsesWholeMultiples() {
        var r = PresentGeometry.compute(ScaleMode.INTEGER, 1920, 1080, 320, 200, 1.0);
        assertThat(r.width()).isEqualTo(1600);
        assertThat(r.height()).isEqualTo(1000);
        var tiny = PresentGeometry.compute(ScaleMode.INTEGER, 200, 100, 320, 200, 1.0);
        assertThat(tiny.width()).isLessThanOrEqualTo(200);
        assertThat(tiny.height()).isLessThanOrEqualTo(100);
    }

    @Test
    void stretchFillsEverything() {
        assertThat(PresentGeometry.compute(ScaleMode.STRETCH, 800, 300, 320, 200, 1.2))
                .isEqualTo(new PresentGeometry.Rect(0, 0, 800, 300));
    }

    @Test
    void keyMapFollowsChocolate() {
        assertThat(GlfwKeys.translate(65)).isEqualTo('a');
        assertThat(GlfwKeys.translate(90)).isEqualTo('z');
        assertThat(GlfwKeys.translate(48)).isEqualTo('0');
        assertThat(GlfwKeys.translate(32)).isEqualTo(' ');
        assertThat(GlfwKeys.translate(256)).isEqualTo(KeyCode.ESCAPE);
        assertThat(GlfwKeys.translate(257)).isEqualTo(KeyCode.ENTER);
        assertThat(GlfwKeys.translate(335)).isEqualTo(KeyCode.ENTER);
        assertThat(GlfwKeys.translate(262)).isEqualTo(KeyCode.RIGHTARROW);
        assertThat(GlfwKeys.translate(263)).isEqualTo(KeyCode.LEFTARROW);
        assertThat(GlfwKeys.translate(264)).isEqualTo(KeyCode.DOWNARROW);
        assertThat(GlfwKeys.translate(265)).isEqualTo(KeyCode.UPARROW);
        assertThat(GlfwKeys.translate(290)).isEqualTo(KeyCode.F1);
        assertThat(GlfwKeys.translate(299)).isEqualTo(KeyCode.F10);
        assertThat(GlfwKeys.translate(300)).isEqualTo(KeyCode.F11);
        assertThat(GlfwKeys.translate(301)).isEqualTo(KeyCode.F12);
        assertThat(GlfwKeys.translate(340)).isEqualTo(KeyCode.RSHIFT);
        assertThat(GlfwKeys.translate(344)).isEqualTo(KeyCode.RSHIFT);
        assertThat(GlfwKeys.translate(341)).isEqualTo(KeyCode.RCTRL);
        assertThat(GlfwKeys.translate(345)).isEqualTo(KeyCode.RCTRL);
        assertThat(GlfwKeys.translate(342)).isEqualTo(KeyCode.LALT);
        assertThat(GlfwKeys.translate(346)).isEqualTo(KeyCode.RALT);
        assertThat(GlfwKeys.translate(45)).isEqualTo(KeyCode.MINUS);
        assertThat(GlfwKeys.translate(61)).isEqualTo(KeyCode.EQUALS);
        assertThat(GlfwKeys.translate(259)).isEqualTo(KeyCode.BACKSPACE);
        assertThat(GlfwKeys.translate(320 + 8)).isEqualTo(KeyCode.UPARROW);
        assertThat(GlfwKeys.translate(320 + 5)).isEqualTo(KeyCode.KEYP_5);
        assertThat(GlfwKeys.translate(343)).isZero();
        assertThat(GlfwKeys.translate(302)).isZero();
    }
}
