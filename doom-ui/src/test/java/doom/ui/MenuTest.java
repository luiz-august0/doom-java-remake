package doom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import doom.game.api.GameState;
import doom.game.view.SoundId;
import doom.ui.menu.MenuSystem;
import doom.wad.iwad.GameMission;
import doom.wad.iwad.GameMode;
import doom.wad.iwad.GameVersion;
import java.util.List;
import org.junit.jupiter.api.Test;

class MenuTest {

    private static void press(Ui ui, int... keys) {
        for (int k : keys) {
            ui.keyDown(k);
        }
    }

    @Test
    void escapeOpensTheMainMenuAndClosesIt() {
        TestGame t = TestGame.registered();
        Ui ui = t.ui();
        assertThat(ui.menuActive()).isFalse();
        assertThat(ui.keyDown(UiKeys.ESCAPE)).isTrue();
        assertThat(ui.menuActive()).isTrue();
        assertThat(ui.menu().currentName()).isEqualTo("main");
        assertThat(t.sounds).containsExactly(SoundId.SWTCHN);
        ui.keyDown(UiKeys.ESCAPE);
        assertThat(ui.menuActive()).isFalse();
        assertThat(t.sounds).containsExactly(SoundId.SWTCHN, SoundId.SWTCHX);
    }

    @Test
    void gameKeysAreNotConsumedWhenTheMenuIsClosed() {
        Ui ui = TestGame.registered().ui();
        assertThat(ui.keyDown('w')).isFalse();
        assertThat(ui.keyDown(UiKeys.UPARROW)).isFalse();
    }

    @Test
    void cursorWrapsAndPlaysTheMoveSound() {
        TestGame t = TestGame.registered();
        Ui ui = t.ui();
        ui.keyDown(UiKeys.ESCAPE);
        t.sounds.clear();
        press(ui, UiKeys.UPARROW);
        assertThat(ui.menu().itemOn()).isEqualTo(5);
        press(ui, UiKeys.DOWNARROW);
        assertThat(ui.menu().itemOn()).isEqualTo(0);
        assertThat(t.sounds).containsExactly(SoundId.PSTOP, SoundId.PSTOP);
    }

    @Test
    void cursorSkipsTheBlankRowsOfTheOptionsMenu() {
        TestGame t = TestGame.registered();
        Ui ui = t.ui();
        ui.keyDown(UiKeys.ESCAPE);
        press(ui, 'o');
        press(ui, UiKeys.ENTER);
        assertThat(ui.menu().currentName()).isEqualTo("options");
        press(ui, UiKeys.DOWNARROW, UiKeys.DOWNARROW, UiKeys.DOWNARROW); // detail, scrnsize... messages
        assertThat(ui.menu().itemOn()).isEqualTo(3);
        t.sounds.clear();
        press(ui, UiKeys.DOWNARROW); // 4 is blank -> 5
        assertThat(ui.menu().itemOn()).isEqualTo(5);
        assertThat(t.sounds).containsExactly(SoundId.PSTOP, SoundId.PSTOP);
        press(ui, UiKeys.UPARROW);
        assertThat(ui.menu().itemOn()).isEqualTo(3);
    }

    @Test
    void hotKeyJumpsToTheItem() {
        Ui ui = TestGame.registered().ui();
        ui.keyDown(UiKeys.ESCAPE);
        press(ui, 'q');
        assertThat(ui.menu().itemOn()).isEqualTo(5);
        press(ui, 'n');
        assertThat(ui.menu().itemOn()).isEqualTo(0);
    }

    @Test
    void newGameWalksThroughEpisodeAndSkill() {
        TestGame t = TestGame.registered();
        Ui ui = t.ui();
        ui.keyDown(UiKeys.ESCAPE);
        press(ui, UiKeys.ENTER);
        assertThat(ui.menu().currentName()).isEqualTo("episode");
        press(ui, UiKeys.DOWNARROW, UiKeys.ENTER); // episode 2
        assertThat(ui.menu().currentName()).isEqualTo("skill");
        assertThat(ui.menu().itemOn()).isEqualTo(2); // "Hurt me plenty" is the default
        press(ui, UiKeys.DOWNARROW, UiKeys.ENTER); // Ultra-violence
        assertThat(t.events).containsExactly("new HARD 2 1");
        assertThat(ui.menuActive()).isFalse();
    }

    @Test
    void backGoesToThePreviousMenu() {
        Ui ui = TestGame.registered().ui();
        ui.keyDown(UiKeys.ESCAPE);
        press(ui, UiKeys.ENTER, UiKeys.ENTER);
        assertThat(ui.menu().currentName()).isEqualTo("skill");
        press(ui, UiKeys.BACKSPACE);
        assertThat(ui.menu().currentName()).isEqualTo("episode");
        press(ui, UiKeys.BACKSPACE);
        assertThat(ui.menu().currentName()).isEqualTo("main");
    }

    @Test
    void nightmareNeedsConfirmation() {
        TestGame t = TestGame.registered();
        Ui ui = t.ui();
        ui.keyDown(UiKeys.ESCAPE);
        press(ui, UiKeys.ENTER, UiKeys.ENTER);
        press(ui, 'n', UiKeys.ENTER); // hot key 'n' moves the cursor to Nightmare, Enter selects it
        assertThat(ui.menu().messageUp()).isTrue();
        assertThat(t.events).isEmpty();
        assertThat(ui.keyDown('x')).isFalse(); // other keys are ignored by a y/n question (the game sees them)
        assertThat(ui.menu().messageUp()).isTrue();
        t.events.clear();
        press(ui, 'y');
        assertThat(t.events).containsExactly("new NIGHTMARE 1 1");
        assertThat(ui.menuActive()).isFalse();
    }

    @Test
    void refusingNightmareStartsNothingAndClosesTheMenu() {
        TestGame t = TestGame.registered();
        Ui ui = t.ui();
        ui.keyDown(UiKeys.ESCAPE);
        press(ui, UiKeys.ENTER, UiKeys.ENTER, 'n', UiKeys.ENTER, 'n');
        assertThat(t.events).isEmpty();
        assertThat(ui.menuActive()).isFalse();
    }

    @Test
    void sharewareWarnsAboutTheOtherEpisodes() {
        TestGame t = new TestGame(GameMode.SHAREWARE, GameVersion.V1_9, GameMission.DOOM);
        Ui ui = t.ui();
        ui.keyDown(UiKeys.ESCAPE);
        press(ui, UiKeys.ENTER, UiKeys.DOWNARROW, UiKeys.ENTER);
        assertThat(ui.menu().messageUp()).isTrue();
        assertThat(ui.menu().currentName()).isEqualTo("read1");
        press(ui, UiKeys.ENTER);
        assertThat(ui.menu().messageUp()).isFalse();
        assertThat(t.events).isEmpty();
    }

    @Test
    void doomTwoHasNoReadThisAndSkipsTheEpisodeMenu() {
        TestGame t = new TestGame(GameMode.COMMERCIAL, GameVersion.V1_9, GameMission.DOOM2);
        Ui ui = t.ui();
        ui.keyDown(UiKeys.ESCAPE);
        assertThat(ui.menu().currentName()).isEqualTo("main");
        press(ui, UiKeys.UPARROW);
        assertThat(ui.menu().itemOn()).isEqualTo(4); // Quit Game
        press(ui, UiKeys.DOWNARROW, UiKeys.ENTER);
        assertThat(ui.menu().currentName()).isEqualTo("skill");
        press(ui, UiKeys.ENTER);
        assertThat(t.events).containsExactly("new MEDIUM 1 1");
    }

    @Test
    void slidersChangeSettingsWithinLimits() {
        TestGame t = TestGame.registered();
        Ui ui = t.ui();
        ui.keyDown(UiKeys.ESCAPE);
        press(ui, UiKeys.DOWNARROW, UiKeys.ENTER); // options
        press(ui, 's'); // screen size slider (hot key 's' after the cursor...)
        assertThat(ui.menu().itemOn()).isEqualTo(3);
        t.settings.setScreenBlocks(10);
        press(ui, UiKeys.RIGHTARROW, UiKeys.RIGHTARROW, UiKeys.RIGHTARROW);
        assertThat(t.settings.screenBlocks()).isEqualTo(11);
        for (int i = 0; i < 20; i++) {
            press(ui, UiKeys.LEFTARROW);
        }
        assertThat(t.settings.screenBlocks()).isEqualTo(3);
        // mouse sensitivity
        press(ui, UiKeys.DOWNARROW);
        assertThat(ui.menu().itemOn()).isEqualTo(5);
        for (int i = 0; i < 20; i++) {
            press(ui, UiKeys.RIGHTARROW);
        }
        assertThat(t.settings.mouseSensitivity()).isEqualTo(9);
    }

    @Test
    void soundVolumeMenuAdjustsSfxAndMusic() {
        TestGame t = TestGame.registered();
        Ui ui = t.ui();
        ui.keyDown(UiKeys.F4);
        assertThat(ui.menu().currentName()).isEqualTo("sound");
        press(ui, UiKeys.RIGHTARROW);
        assertThat(t.settings.sfxVolume()).isEqualTo(9);
        press(ui, UiKeys.DOWNARROW);
        press(ui, UiKeys.LEFTARROW, UiKeys.LEFTARROW);
        assertThat(t.settings.musicVolume()).isEqualTo(6);
        for (int i = 0; i < 30; i++) {
            press(ui, UiKeys.RIGHTARROW);
        }
        assertThat(t.settings.musicVolume()).isEqualTo(15);
    }

    @Test
    void messagesAndDetailTogglePostHudMessages() {
        TestGame t = TestGame.registered();
        Ui ui = t.ui();
        ui.keyDown(UiKeys.F8);
        assertThat(t.settings.showMessages()).isFalse();
        ui.levelTick();
        assertThat(ui.hud().messageOn()).isTrue(); // "Messages OFF" is shown even though messages are off
        assertThat(ui.hud().currentMessage()).isEqualTo("Messages OFF");
        ui.keyDown(UiKeys.F5);
        assertThat(t.settings.detailLevel()).isEqualTo(1);
    }

    @Test
    void gammaKeyCyclesTheFiveLevels() {
        TestGame t = TestGame.registered();
        Ui ui = t.ui();
        for (int i = 1; i <= 5; i++) {
            ui.keyDown(UiKeys.F11);
            assertThat(t.settings.gamma()).isEqualTo(i % 5);
        }
    }

    @Test
    void saveMenuTypesADescriptionAndSaves() {
        TestGame t = TestGame.registered();
        Ui ui = t.ui();
        ui.keyDown(UiKeys.F2);
        assertThat(ui.menu().currentName()).isEqualTo("save");
        press(ui, UiKeys.DOWNARROW, UiKeys.ENTER);
        assertThat(ui.menu().textEntryActive()).isTrue();
        for (char c : "my save".toCharArray()) {
            ui.charTyped(c);
        }
        assertThat(ui.menu().saveString(1)).isEqualTo("MY SAVE");
        ui.keyDown(UiKeys.BACKSPACE);
        assertThat(ui.menu().saveString(1)).isEqualTo("MY SAV");
        ui.keyDown(UiKeys.ENTER);
        assertThat(t.events).containsExactly("save 1 MY SAV");
        assertThat(ui.menuActive()).isFalse();
    }

    @Test
    void escapeCancelsTheDescriptionEdit() {
        TestGame t = TestGame.registered();
        t.saved[0] = "OLD NAME";
        Ui ui = t.ui();
        ui.keyDown(UiKeys.F2);
        ui.keyDown(UiKeys.ENTER);
        ui.charTyped('x');
        assertThat(ui.menu().saveString(0)).isEqualTo("OLD NAMEX");
        ui.keyDown(UiKeys.ESCAPE);
        assertThat(ui.menu().saveString(0)).isEqualTo("OLD NAME");
        assertThat(ui.menu().textEntryActive()).isFalse();
        assertThat(t.events).isEmpty();
        assertThat(ui.menuActive()).isTrue();
    }

    @Test
    void descriptionsAreLimitedTo23Characters() {
        TestGame t = TestGame.registered();
        Ui ui = t.ui();
        ui.keyDown(UiKeys.F2);
        ui.keyDown(UiKeys.ENTER);
        for (int i = 0; i < 40; i++) {
            ui.charTyped('a');
        }
        assertThat(ui.menu().saveString(0)).hasSize(23);
    }

    @Test
    void emptyDescriptionIsNotSaved() {
        TestGame t = TestGame.registered();
        Ui ui = t.ui();
        ui.keyDown(UiKeys.F2);
        ui.keyDown(UiKeys.ENTER);
        ui.keyDown(UiKeys.ENTER);
        assertThat(t.events).isEmpty();
    }

    @Test
    void saveNeedsAGameInProgress() {
        TestGame t = TestGame.registered();
        t.game.set("userGame", false);
        Ui ui = t.ui();
        ui.keyDown(UiKeys.F2);
        assertThat(ui.menu().messageUp()).isTrue();
        assertThat(ui.menu().currentName()).isEqualTo("main");
    }

    @Test
    void loadMenuOnlySelectsExistingSlots() {
        TestGame t = TestGame.registered();
        t.saved[2] = "LEVEL TWO";
        Ui ui = t.ui();
        ui.keyDown(UiKeys.F3);
        assertThat(ui.menu().currentName()).isEqualTo("load");
        ui.keyDown(UiKeys.ENTER); // slot 0 is empty
        assertThat(t.events).isEmpty();
        assertThat(ui.menuActive()).isTrue();
        press(ui, UiKeys.DOWNARROW, UiKeys.DOWNARROW, UiKeys.ENTER);
        assertThat(t.events).containsExactly("load 2");
        assertThat(ui.menuActive()).isFalse();
    }

    @Test
    void quickSaveFirstPicksASlotThenAsksForConfirmation() {
        TestGame t = TestGame.registered();
        Ui ui = t.ui();
        ui.keyDown(UiKeys.F6);
        assertThat(ui.menu().currentName()).isEqualTo("save");
        assertThat(ui.menu().quickSaveSlot()).isEqualTo(-2);
        press(ui, UiKeys.DOWNARROW, UiKeys.ENTER);
        for (char c : "qs".toCharArray()) {
            ui.charTyped(c);
        }
        ui.keyDown(UiKeys.ENTER);
        assertThat(ui.menu().quickSaveSlot()).isEqualTo(1);
        t.events.clear();
        t.saved[1] = "QS";
        ui.keyDown(UiKeys.F6);
        assertThat(ui.menu().messageUp()).isTrue();
        ui.keyDown('y');
        assertThat(t.events).containsExactly("save 1 QS");
    }

    @Test
    void quickLoadAsksFirstAndWarnsWithoutASlot() {
        TestGame t = TestGame.registered();
        Ui ui = t.ui();
        ui.keyDown(UiKeys.F9);
        assertThat(ui.menu().messageUp()).isTrue(); // "you haven't picked a quicksave slot yet!"
        ui.keyDown(' ');
        assertThat(t.events).isEmpty();
    }

    @Test
    void quitMessageDependsOnGametic() {
        TestGame t = TestGame.registered();
        t.game.set("gametic", 4 * 3 + 1);
        Ui ui = t.ui();
        ui.keyDown(UiKeys.F10);
        assertThat(ui.menu().messageUp()).isTrue();
        t.sounds.clear();
        ui.keyDown('y');
        assertThat(t.events).containsExactly("quit");
        // (gametic >> 2) & 7 == 3 -> sfx_slop for DOOM 1
        assertThat(t.sounds).contains(SoundId.SLOP);
    }

    @Test
    void doomTwoQuitSoundsDiffer() {
        TestGame t = new TestGame(GameMode.COMMERCIAL, GameVersion.V1_9, GameMission.DOOM2);
        t.game.set("gametic", 4 * 4);
        Ui ui = t.ui();
        ui.keyDown(UiKeys.F10);
        t.sounds.clear();
        ui.keyDown('y');
        assertThat(t.sounds).contains(SoundId.SKESWG);
    }

    @Test
    void endGameConfirmationRunsTheAction() {
        TestGame t = TestGame.registered();
        Ui ui = t.ui();
        ui.keyDown(UiKeys.F7);
        assertThat(ui.menu().messageUp()).isTrue();
        ui.keyDown('y');
        assertThat(t.events).containsExactly("end");
    }

    @Test
    void endGameWithoutAGamePlaysOof() {
        TestGame t = TestGame.registered();
        t.game.set("userGame", false);
        Ui ui = t.ui();
        ui.keyDown(UiKeys.F7);
        assertThat(t.sounds).contains(SoundId.OOF);
        assertThat(ui.menu().messageUp()).isFalse();
    }

    @Test
    void helpKeyShowsTheRightPage() {
        TestGame reg = TestGame.registered();
        Ui ui = reg.ui();
        ui.keyDown(UiKeys.F1);
        assertThat(ui.menu().currentName()).isEqualTo("read1");
        ui.keyDown(UiKeys.ENTER);
        assertThat(ui.menu().currentName()).isEqualTo("read2");
        ui.keyDown(UiKeys.ENTER);
        assertThat(ui.menu().currentName()).isEqualTo("main");

        TestGame ult = new TestGame(GameMode.RETAIL, GameVersion.ULTIMATE, GameMission.DOOM);
        Ui ui2 = ult.ui();
        ui2.keyDown(UiKeys.F1);
        assertThat(ui2.menu().currentName()).isEqualTo("read2");
    }

    @Test
    void screenSizeKeysWorkOutsideTheMenuButNotInTheAutomap() {
        TestGame t = TestGame.registered();
        Ui ui = t.ui();
        assertThat(ui.keyDown(UiKeys.EQUALS)).isTrue();
        assertThat(t.settings.screenBlocks()).isEqualTo(11);
        ui.keyDown(UiKeys.TAB); // opens the automap
        assertThat(ui.automapActive()).isTrue();
        assertThat(ui.keyDown(UiKeys.MINUS)).isTrue(); // consumed by the automap (zoom)
        assertThat(t.settings.screenBlocks()).isEqualTo(11);
    }

    @Test
    void anyKeyOpensTheMenuDuringTheTitleLoop() {
        TestGame t = TestGame.registered();
        t.game.set("state", GameState.DEMOSCREEN);
        Ui ui = t.ui();
        assertThat(ui.keyDown('x')).isTrue();
        assertThat(ui.menuActive()).isTrue();
    }

    @Test
    void extendedPagesAreEditable() {
        TestGame t = TestGame.registered();
        boolean[] flag = {false};
        int[] choice = {0};
        int[] slider = {5};
        t.settings.setExtendedPages(List.of(new SettingsPage("Video", List.of(
            new SettingsItem.Toggle("VSync", () -> flag[0], v -> flag[0] = v),
            new SettingsItem.Choice("Filter", List.of("A", "B", "C"), () -> choice[0], v -> choice[0] = v),
            new SettingsItem.Slider("Max FPS", 0, 10, 2, () -> slider[0], v -> slider[0] = v, null)))));
        Ui ui = t.ui();
        ui.keyDown(UiKeys.ESCAPE);
        press(ui, 'o', UiKeys.ENTER);
        press(ui, 'x'); // "Extended Options"
        assertThat(ui.menu().itemOn()).isEqualTo(8);
        press(ui, UiKeys.ENTER);
        assertThat(ui.menu().currentName()).isEqualTo("extended");
        press(ui, UiKeys.ENTER);
        assertThat(ui.menu().currentName()).isEqualTo("page:Video");
        press(ui, UiKeys.ENTER);
        assertThat(flag[0]).isTrue();
        press(ui, UiKeys.DOWNARROW, UiKeys.LEFTARROW);
        assertThat(choice[0]).isEqualTo(2); // wraps
        press(ui, UiKeys.DOWNARROW, UiKeys.RIGHTARROW, UiKeys.RIGHTARROW, UiKeys.RIGHTARROW);
        assertThat(slider[0]).isEqualTo(10); // clamped
        press(ui, UiKeys.BACKSPACE, UiKeys.BACKSPACE);
        assertThat(ui.menu().currentName()).isEqualTo("options");
    }

    @Test
    void vanillaModeHidesTheExtendedOptions() {
        TestGame t = TestGame.registered();
        t.settings.setExtendedPages(List.of(new SettingsPage("Video", List.of())));
        MenuSystem m = t.builder().config(UiConfig.defaults().withVanillaMode(true)).build().menu();
        assertThat(m).isNotNull();
        Ui ui = t.builder().config(UiConfig.defaults().withVanillaMode(true)).build();
        ui.keyDown(UiKeys.ESCAPE);
        press(ui, 'o', UiKeys.ENTER, 'x');
        assertThat(ui.menu().itemOn()).isEqualTo(0); // 'x' matches nothing
    }

    @Test
    void mouseMovesTheCursorAfterEnoughMotion() {
        TestGame t = TestGame.registered();
        Ui ui = t.ui();
        ui.keyDown(UiKeys.ESCAPE);
        assertThat(ui.mouseMove(0, 10)).isFalse();
        assertThat(ui.mouseMove(0, 25)).isTrue();
        assertThat(ui.menu().itemOn()).isEqualTo(1);
        assertThat(ui.mouseMove(0, 100)).isFalse(); // mouse wait of 5 tics
        for (int i = 0; i < 6; i++) {
            ui.tick();
        }
        assertThat(ui.mouseButton(0, true)).isTrue(); // left button = Enter
        assertThat(ui.menu().currentName()).isEqualTo("options");
    }

    @Test
    void padButtonsDriveTheMenu() {
        TestGame t = TestGame.registered();
        Ui ui = t.ui();
        assertThat(ui.padButton(7)).isTrue(); // START opens
        assertThat(ui.menuActive()).isTrue();
        ui.padButton(13);
        assertThat(ui.menu().itemOn()).isEqualTo(1);
        ui.padButton(1); // B = back
        ui.padButton(7);
        assertThat(ui.menuActive()).isFalse();
    }
}
