package doom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import doom.math.Fixed;
import doom.render.FrameBuffer;
import doom.render.canvas.Canvas2D;
import doom.ui.automap.Automap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutomapTest {

    private static final int[][] VERTS = {{-512, -512}, {512, -512}, {512, 512}, {-512, 512}};

    private final TestGame t = TestGame.registered();
    private Ui ui;

    @BeforeEach
    void setUp() {
        t.world.setInt("vertexX", i -> VERTS[i][0] << Fixed.FRACBITS).setInt("vertexY", i -> VERTS[i][1] << Fixed.FRACBITS)
            .set("vertexCount", 4).set("lineCount", 4).set("sectorCount", 1);
        t.world.setInt("lineV1", i -> i).setInt("lineV2", i -> (i + 1) % 4).setInt("lineBackSector", i -> -1);
        t.world.set("lineFlags", (java.util.function.Function<Object[], Object>) a -> 0);
        t.player.set("x", 0).set("y", 0).set("angle", 0);
        t.game.set("map", 1);
        ui = t.builder().mappedLines(line -> true).build();
    }

    @Test
    void tabTogglesTheAutomap() {
        assertThat(ui.automapActive()).isFalse();
        assertThat(ui.keyDown(UiKeys.TAB)).isTrue();
        assertThat(ui.automapActive()).isTrue();
        assertThat(ui.wantsWorldView()).isFalse();
        assertThat(ui.keyDown(UiKeys.TAB)).isTrue();
        assertThat(ui.automapActive()).isFalse();
        assertThat(ui.wantsWorldView()).isTrue();
    }

    @Test
    void followModeAndGridToggleWithMessages() {
        ui.keyDown(UiKeys.TAB);
        assertThat(ui.automap().followPlayer()).isTrue();
        ui.keyDown('f');
        assertThat(ui.automap().followPlayer()).isFalse();
        ui.levelTick();
        assertThat(ui.hud().currentMessage()).isEqualTo("Follow Mode OFF");
        ui.keyDown('g');
        assertThat(ui.automap().gridOn()).isTrue();
        ui.levelTick();
        assertThat(ui.hud().currentMessage()).isEqualTo("Grid ON");
    }

    @Test
    void marksAreNumberedAndCleared() {
        ui.keyDown(UiKeys.TAB);
        ui.keyDown('m');
        ui.keyDown('m');
        assertThat(ui.automap().markCount()).isEqualTo(2);
        ui.levelTick();
        assertThat(ui.hud().currentMessage()).isEqualTo("Marked Spot 1");
        ui.keyDown('c');
        assertThat(ui.automap().markCount()).isZero();
    }

    @Test
    void panKeysMoveTheWindowOnlyOutsideFollowMode() {
        ui.keyDown(UiKeys.TAB);
        // in follow mode the arrow keys belong to the game
        assertThat(ui.keyDown(UiKeys.RIGHTARROW)).isFalse();
        ui.keyDown('f');
        int x0 = ui.automap().viewX();
        assertThat(ui.keyDown(UiKeys.RIGHTARROW)).isTrue();
        ui.levelTick();
        ui.levelTick();
        ui.keyUp(UiKeys.RIGHTARROW);
        ui.levelTick();
        assertThat(ui.automap().viewX()).isGreaterThan(x0);
        int x1 = ui.automap().viewX();
        ui.levelTick();
        assertThat(ui.automap().viewX()).isEqualTo(x1);
    }

    @Test
    void zoomKeysChangeTheScaleWithinLimits() {
        ui.keyDown(UiKeys.TAB);
        int s0 = ui.automap().scale();
        ui.keyDown(UiKeys.EQUALS);
        for (int i = 0; i < 5; i++) {
            ui.levelTick();
        }
        ui.keyUp(UiKeys.EQUALS);
        assertThat(ui.automap().scale()).isGreaterThan(s0);
        ui.keyDown(UiKeys.MINUS);
        for (int i = 0; i < 400; i++) {
            ui.levelTick();
        }
        ui.keyUp(UiKeys.MINUS);
        int min = ui.automap().scale();
        ui.levelTick();
        assertThat(ui.automap().scale()).isEqualTo(min);
        assertThat(min).isLessThan(s0);
    }

    @Test
    void iddtCyclesTheCheatLevel() {
        ui.keyDown(UiKeys.TAB);
        for (int round = 1; round <= 3; round++) {
            for (char c : "iddt".toCharArray()) {
                ui.keyDown(c);
            }
            assertThat(ui.automap().cheating()).isEqualTo(round % 3);
        }
    }

    @Test
    void iddtIsIgnoredInDeathmatch() {
        t.game.set("deathmatch", 1);
        ui.keyDown(UiKeys.TAB);
        for (char c : "iddt".toCharArray()) {
            ui.keyDown(c);
        }
        assertThat(ui.automap().cheating()).isZero();
    }

    @Test
    void mapsDrawnAtNativeResolutionWithThickerLines() {
        FrameBuffer fb = new FrameBuffer(1280, 450);
        Canvas2D canvas = new Canvas2D(fb);
        ui.keyDown(UiKeys.TAB);
        ui.levelTick();
        ui.draw(canvas, 1f);
        int red = 0;
        int nonBlack = 0;
        for (int y = 0; y < fb.height(); y++) {
            for (int x = 0; x < fb.width(); x++) {
                int p = fb.get(x, y);
                if (p == 176) {
                    red++;
                }
                if (p != 0) {
                    nonBlack++;
                }
            }
        }
        assertThat(red).isGreaterThan(500); // the four walls
        assertThat(nonBlack).isGreaterThan(red); // plus the player arrow, crosshair
        assertThat(ui.automap().windowWidth()).isEqualTo(1280);
        assertThat(ui.automap().windowHeight()).isEqualTo(450 - 72);
    }

    @Test
    void theAutomapStopsWhenTheIntermissionStarts() {
        ui.keyDown(UiKeys.TAB);
        ui.intermissionStarted();
        assertThat(ui.automapActive()).isFalse();
    }

    @Test
    void aNewLevelSwitchesTheAutomapOff() {
        ui.keyDown(UiKeys.TAB);
        ui.levelLoaded();
        assertThat(ui.automapActive()).isFalse();
    }
}
