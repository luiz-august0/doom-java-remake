package doom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import doom.game.view.GameView;
import doom.ui.hud.Hud;
import org.junit.jupiter.api.Test;

class HudTest {

    private final TestGame t = TestGame.registered();

    @Test
    void messageShowsForFourSeconds() {
        Ui ui = t.ui();
        ui.playerMessage(0, "You got the blue key");
        assertThat(ui.hud().messageOn()).isFalse(); // shown at the next HU_Ticker
        ui.levelTick();
        assertThat(ui.hud().messageOn()).isTrue();
        assertThat(ui.hud().currentMessage()).isEqualTo("You got the blue key");
        for (int i = 0; i < Hud.MESSAGE_TIMEOUT - 1; i++) {
            ui.levelTick();
        }
        assertThat(ui.hud().messageOn()).isTrue();
        ui.levelTick();
        assertThat(ui.hud().messageOn()).isFalse();
    }

    @Test
    void messagesOfOtherPlayersAreIgnored() {
        Ui ui = t.ui();
        ui.playerMessage(1, "not mine");
        ui.levelTick();
        assertThat(ui.hud().messageOn()).isFalse();
    }

    @Test
    void messagesOffSuppressesNormalMessages() {
        t.settings.setShowMessages(false);
        Ui ui = t.ui();
        ui.playerMessage(0, "hidden");
        ui.levelTick();
        assertThat(ui.hud().messageOn()).isFalse();
    }

    @Test
    void aNewMessageReplacesTheOldOne() {
        Ui ui = t.ui();
        ui.playerMessage(0, "one");
        ui.levelTick();
        ui.playerMessage(0, "two");
        ui.levelTick();
        assertThat(ui.hud().currentMessage()).isEqualTo("two");
    }

    @Test
    void aProtectedMessageHoldsBackNormalOnesUntilItExpires() {
        Ui ui = t.ui();
        ui.hud().post("Messages ON", true);
        ui.levelTick();
        assertThat(ui.hud().currentMessage()).isEqualTo("Messages ON");
        ui.playerMessage(0, "pickup");
        ui.levelTick();
        assertThat(ui.hud().currentMessage()).isEqualTo("Messages ON");
        for (int i = 0; i < Hud.MESSAGE_TIMEOUT; i++) {
            ui.levelTick();
        }
        // the counter expired, the pending message is shown on the following tic
        ui.levelTick();
        assertThat(ui.hud().currentMessage()).isEqualTo("pickup");
    }

    @Test
    void mapTitleComesFromTheLevelNames() {
        t.game.set("episode", 1).set("map", 1);
        Ui ui = t.ui();
        ui.levelLoaded();
        assertThat(ui.hud().title()).isEqualTo("E1M1: Hangar");

        TestGame d2 = new TestGame(doom.wad.iwad.GameMode.COMMERCIAL, doom.wad.iwad.GameVersion.V1_9,
            doom.wad.iwad.GameMission.DOOM2);
        d2.game.set("map", 7);
        Ui ui2 = d2.ui();
        ui2.levelLoaded();
        assertThat(ui2.hud().title()).isEqualTo(d2.defs.text("HUSTR_7"));
        GameView v = d2.game.get();
        assertThat(v.map()).isEqualTo(7);
    }

    @Test
    void viewWindowMatchesTheOriginalFormula() {
        assertThat(ViewWindow.of(10)).isEqualTo(new ViewWindow(0, 0, 320, 168));
        assertThat(ViewWindow.of(11)).isEqualTo(new ViewWindow(0, 0, 320, 200));
        assertThat(ViewWindow.of(9)).isEqualTo(new ViewWindow(16, 12, 288, 144));
        assertThat(ViewWindow.of(3)).isEqualTo(new ViewWindow(112, 60, 96, 48));
        assertThat(ViewWindow.of(11).fullScreen()).isTrue();
        assertThat(ViewWindow.of(10).fullScreen()).isFalse();
    }
}
