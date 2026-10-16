package doom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import doom.game.view.CheatFlags;
import doom.game.view.Power;
import doom.math.Angle;
import doom.ui.hud.FaceLogic;
import doom.ui.hud.PaletteSelector;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StatusBarTest {

    private static final int DEAD_FACE = 41;
    private static final int GOD_FACE = 40;

    private final TestGame t = TestGame.registered();
    private final AtomicInteger rndCalls = new AtomicInteger();
    private int rndValue = 1;

    private FaceLogic face() {
        t.game.set("consolePlayer", 0);
        t.player.set("health", 100);
        return new FaceLogic(t.game.get(), () -> {
            rndCalls.incrementAndGet();
            return rndValue;
        });
    }

    @Test
    void theMiscRngIsCalledOncePerLevelTick() {
        Ui ui = t.builder().miscRandom(() -> {
            rndCalls.incrementAndGet();
            return 0;
        }).build();
        t.player.set("health", 100);
        for (int i = 0; i < 7; i++) {
            ui.levelTick();
        }
        assertThat(rndCalls.get()).isEqualTo(7);
    }

    @Test
    void healthyFaceLooksStraightAndPicksTheDirectionFromTheRandomNumber() {
        FaceLogic f = face();
        rndValue = 2;
        f.tick();
        assertThat(f.faceIndex()).isEqualTo(2);
        assertThat(f.randomNumber()).isEqualTo(2);
    }

    @Test
    void painFaceFollowsTheHealth() {
        FaceLogic f = face();
        t.player.set("health", 50);
        rndValue = 0;
        f.tick();
        assertThat(f.faceIndex()).isEqualTo(8 * ((100 - 50) * 5 / 101));
        t.player.set("health", 1);
        // the face keeps its look until the 15 tic "straight face" count expires
        for (int i = 0; i < 20; i++) {
            f.tick();
        }
        assertThat(f.faceIndex()).isEqualTo(8 * 4);
    }

    @Test
    void deadFaceTakesPrecedence() {
        FaceLogic f = face();
        t.player.set("health", 0);
        f.tick();
        assertThat(f.faceIndex()).isEqualTo(DEAD_FACE);
    }

    @Test
    void godModeShowsTheGodFace() {
        FaceLogic f = face();
        t.player.set("cheats", CheatFlags.GODMODE);
        f.tick();
        assertThat(f.faceIndex()).isEqualTo(GOD_FACE);
    }

    @Test
    void invulnerabilityShowsTheGodFace() {
        FaceLogic f = face();
        t.player.setInt("power", p -> p == Power.INVULNERABILITY ? 100 : 0);
        f.tick();
        assertThat(f.faceIndex()).isEqualTo(GOD_FACE);
    }

    @Test
    void pickingUpAWeaponGivesAnEvilGrin() {
        FaceLogic f = face();
        f.start();
        f.tick();
        boolean[] owned = new boolean[9];
        t.player.setInt("weaponOwned", w -> owned[w]);
        owned[2] = true;
        t.player.set("bonusCount", 6);
        f.tick();
        assertThat(f.faceIndex()).isEqualTo(6);
    }

    private void attackedFrom(int ax, int ay, int playerAngle) {
        t.player.set("x", 0).set("y", 0).set("angle", playerAngle).set("actor", 1);
        t.player.set("attacker", 2).set("damageCount", 5);
        t.actors.set("exists", (java.util.function.Function<Object[], Object>) a -> true);
        t.actors.setInt("x", id -> id == 2 ? ax : 0).setInt("y", id -> id == 2 ? ay : 0);
    }

    @Test
    void faceTurnsTowardsTheAttacker() {
        FaceLogic f = face();
        f.tick(); // sets the previous health
        attackedFrom(0, 1000 << 16, 0); // attacker due north, player looking east
        f.tick();
        assertThat(f.faceIndex()).isEqualTo(3 + 1); // turn left
    }

    @Test
    void faceTurnsRightWhenTheAttackerIsOnTheRight() {
        FaceLogic f = face();
        f.tick();
        attackedFrom(0, -1000 << 16, 0); // due south
        f.tick();
        assertThat(f.faceIndex()).isEqualTo(3);
    }

    @Test
    void headOnAttackShowsTheRampageFace() {
        FaceLogic f = face();
        f.tick();
        attackedFrom(1000 << 16, 0, 0);
        f.tick();
        assertThat(f.faceIndex()).isEqualTo(7);
    }

    @Test
    void bigHealthGainWithDamageShowsTheOuchFace() {
        FaceLogic g = face();
        t.player.set("health", 40);
        g.tick();
        t.player.set("health", 100).set("damageCount", 1).set("attacker", -1);
        g.tick();
        assertThat(g.faceIndex()).isEqualTo(5);
    }

    @Test
    void paletteFollowsDamageBonusAndRadiationSuit() {
        assertThat(PaletteSelector.select(t.player.get())).isZero();
        t.player.set("damageCount", 10);
        assertThat(PaletteSelector.select(t.player.get())).isEqualTo(1 + ((10 + 7) >> 3));
        t.player.set("damageCount", 255);
        assertThat(PaletteSelector.select(t.player.get())).isEqualTo(8);
        t.player.set("damageCount", 0).set("bonusCount", 5);
        assertThat(PaletteSelector.select(t.player.get())).isEqualTo(9 + 1);
        t.player.set("bonusCount", 0);
        t.player.setInt("power", p -> p == Power.IRONFEET ? 4 * 32 + 1 : 0);
        assertThat(PaletteSelector.select(t.player.get())).isEqualTo(13);
        t.player.setInt("power", p -> p == Power.IRONFEET ? 8 : 0);
        assertThat(PaletteSelector.select(t.player.get())).isEqualTo(13);
        t.player.setInt("power", p -> p == Power.IRONFEET ? 4 : 0);
        assertThat(PaletteSelector.select(t.player.get())).isZero();
    }

    @Test
    void berserkFadesTheRedPalette() {
        t.player.setInt("power", p -> p == Power.STRENGTH ? 1 : 0);
        // bzc = 12 - (1 >> 6) = 12 -> palette (12+7)>>3 = 2 -> 3
        assertThat(PaletteSelector.select(t.player.get())).isEqualTo(3);
        t.player.setInt("power", p -> p == Power.STRENGTH ? 12 * 64 : 0);
        assertThat(PaletteSelector.select(t.player.get())).isZero();
    }

    @Test
    void uiPaletteIsZeroOutsideLevels() {
        t.player.set("damageCount", 100);
        Ui ui = t.ui();
        assertThat(ui.paletteIndex()).isEqualTo(8);
        t.game.set("state", doom.game.api.GameState.INTERMISSION);
        assertThat(ui.paletteIndex()).isZero();
        assertThat(Angle.ANG180).isNotZero();
    }
}
