package doom.it.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.game.api.GameParams;
import doom.game.api.GameSetup;
import doom.game.api.GameState;
import doom.game.api.SimEvents;
import doom.game.api.Skill;
import doom.game.api.TicCmd;
import doom.game.impl.SessionFactory;
import doom.game.impl.SessionImpl;
import doom.game.view.FinaleView;
import doom.game.view.GameView;
import doom.game.view.IntermissionView;
import doom.game.view.MusicId;
import doom.testkit.Iwads;
import doom.wad.LumpDirectory;
import doom.wad.WadFile;
import doom.wad.iwad.IwadIdentifier;
import doom.wad.iwad.IwadInfo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * W3-D: level completion, the intermission state machine, the finale and the title loop, driven through the session
 * API with real IWADs. Rule areas other than the session flow are still stubs at this point, so levels are left with
 * {@code flow().exitLevel()} instead of walking to the exit.
 */
class GameFlowIT {

    /** A session plus a recording of the events the presentation would see. */
    private static final class Harness {
        final SessionImpl session;
        final List<int[]> music = new ArrayList<>();
        final List<Integer> sounds = new ArrayList<>();
        int finaleStarted;
        int intermissionStarted;
        int forceWipes;
        final TicCmd[] cmds = {new TicCmd(), new TicCmd(), new TicCmd(), new TicCmd()};

        Harness(String iwad, boolean noIwadDehacked) throws IOException {
            Path path = Iwads.require(iwad);
            IwadInfo info = IwadIdentifier.identify(WadFile.open(path));
            LumpDirectory lumps = LumpDirectory.open(List.of(path));
            SimEvents events = new SimEvents() {
                @Override
                public void changeMusic(int m, boolean looping) {
                    music.add(new int[] {m, looping ? 1 : 0});
                }

                @Override
                public void startSound(int origin, int sfx) {
                    sounds.add(sfx);
                }

                @Override
                public void intermissionStarted() {
                    intermissionStarted++;
                }

                @Override
                public void finaleStarted() {
                    finaleStarted++;
                }

                @Override
                public void forceWipe() {
                    forceWipes++;
                }
            };
            session = SessionFactory.createImpl(
                    new GameSetup(lumps, info, info.version(), List.of(), events, noIwadDehacked));
        }

        GameView view() {
            return session.view();
        }

        IntermissionView wi() {
            return session.view().intermission();
        }

        FinaleView finale() {
            return session.view().finale();
        }

        void tick() {
            session.tick(cmds, false);
        }

        void tick(int n) {
            for (int i = 0; i < n; i++) {
                tick();
            }
        }

        void buttons(int b) {
            cmds[0].buttons = (byte) b;
        }

        void newGame(int episode, int map) {
            newGame(episode, map, 1);
        }

        void newGame(int episode, int map, int skill) {
            session.newGame(new GameParams(Skill.of(skill), episode, map, 0, false, false, true, 1, 0));
            tick();
            assertThat(view().state()).isEqualTo(GameState.LEVEL);
        }

        /** Presses and releases the use button until the game state is no longer the intermission. */
        void skipIntermission() {
            for (int i = 0; i < 40 && view().state() == GameState.INTERMISSION; i++) {
                buttons(i % 2 == 0 ? TicCmd.BT_USE : 0);
                tick();
            }
            buttons(0);
        }

        boolean lastMusic(int m, boolean looping) {
            int[] last = music.get(music.size() - 1);
            return last[0] == m && (last[1] == 1) == looping;
        }
    }

    private static void requireIwad(String name) {
        assumeTrue(Iwads.find(name).isPresent(), name + " not available");
    }

    // ------------------------------------------------------------------------------------------ intermission

    @Test
    void singlePlayerIntermissionRunsItsStateMachineAndLoadsTheNextMap() throws IOException {
        requireIwad("doom1.wad");
        Harness h = new Harness("doom1.wad", false);
        h.newGame(1, 1);
        h.session.flow().exitLevel();
        h.tick();

        assertThat(h.view().state()).isEqualTo(GameState.INTERMISSION);
        assertThat(h.intermissionStarted).isEqualTo(1);
        IntermissionView wi = h.wi();
        assertThat(wi.stage()).isZero();
        assertThat(wi.spState()).isEqualTo(1);
        assertThat(wi.bcnt()).isEqualTo(1);
        assertThat(wi.episode()).isZero();
        assertThat(wi.lastMap()).isZero();
        assertThat(wi.nextMap()).isEqualTo(1);
        assertThat(wi.parTime()).isEqualTo(30 * 35);
        assertThat(wi.me()).isZero();
        assertThat(wi.playerPresent(0)).isTrue();
        assertThat(wi.playerPresent(1)).isFalse();
        assertThat(wi.countKills(0)).isEqualTo(-1);
        assertThat(wi.countTime()).isEqualTo(-1);
        assertThat(wi.maxKills()).isGreaterThan(0);
        // WI_Ticker starts the intermission music at bcnt == 1
        assertThat(h.lastMusic(MusicId.INTER, true)).isTrue();
        assertThat(wi.animCount()).isEqualTo(10);
        assertThat(wi.animFrame(0)).isEqualTo(-1);

        // the first pause is TICRATE tics, then the kill counter runs (nothing was killed)
        h.tick(34);
        assertThat(wi.bcnt()).isEqualTo(35);
        assertThat(wi.spState()).isEqualTo(2);
        h.tick();
        assertThat(wi.spState()).isEqualTo(3);
        assertThat(wi.countKills(0)).isZero();
        assertThat(h.sounds).contains(82); // sfx_barexp

        // a fresh press of the use button shows the final numbers
        h.buttons(TicCmd.BT_USE);
        h.tick();
        assertThat(wi.spState()).isEqualTo(10);
        assertThat(wi.countKills(0)).isZero();
        assertThat(wi.countItems(0)).isZero();
        assertThat(wi.countSecret(0)).isZero();
        assertThat(wi.countTime()).isEqualTo(wi.playerTime(0) / 35);
        assertThat(wi.countPar()).isEqualTo(30);

        // holding the button does nothing; releasing and pressing again leaves the statistics
        h.tick();
        assertThat(wi.stage()).isZero();
        h.buttons(0);
        h.tick();
        h.buttons(TicCmd.BT_USE);
        h.tick();
        assertThat(wi.stage()).isEqualTo(1);
        assertThat(wi.cnt()).isEqualTo(4 * 35);

        // and again: straight to the "no state" countdown
        h.buttons(0);
        h.tick();
        h.buttons(TicCmd.BT_USE);
        h.tick();
        assertThat(wi.stage()).isEqualTo(-1);
        assertThat(wi.cnt()).isEqualTo(10);
        h.buttons(0);
        h.tick(9);
        assertThat(wi.cnt()).isEqualTo(1);
        assertThat(h.view().state()).isEqualTo(GameState.INTERMISSION);
        h.tick(); // G_WorldDone: the game action is processed by the next G_Ticker
        assertThat(wi.cnt()).isZero();
        assertThat(h.view().state()).isEqualTo(GameState.INTERMISSION);
        h.tick();
        assertThat(h.view().state()).isEqualTo(GameState.LEVEL);
        assertThat(h.view().map()).isEqualTo(2);
        assertThat(h.view().episode()).isEqualTo(1);
    }

    @Test
    void theStatisticsWaitForAKeyPressAfterCounting() throws IOException {
        requireIwad("doom1.wad");
        Harness h = new Harness("doom1.wad", false);
        h.newGame(1, 1);
        h.session.flow().exitLevel();
        h.tick();
        h.tick(800);
        // every counting step ends by itself, but the screen stays until a button is pressed
        assertThat(h.view().state()).isEqualTo(GameState.INTERMISSION);
        assertThat(h.wi().stage()).isZero();
        assertThat(h.wi().spState()).isEqualTo(10);
    }

    @Test
    void secretExitGoesToMap9AndBackToTheNextMapOfTheEpisode() throws IOException {
        requireIwad("doom1.wad");
        Harness h = new Harness("doom1.wad", false);
        h.newGame(1, 3);
        h.session.flow().secretExitLevel();
        h.tick();
        assertThat(h.wi().nextMap()).isEqualTo(8);
        assertThat(h.wi().didSecret()).isFalse();
        h.skipIntermission();
        assertThat(h.view().state()).isEqualTo(GameState.LEVEL);
        assertThat(h.view().map()).isEqualTo(9);
        assertThat(h.view().player(0).didSecret()).isTrue();

        h.session.flow().exitLevel();
        h.tick();
        assertThat(h.view().state()).isEqualTo(GameState.INTERMISSION);
        assertThat(h.wi().lastMap()).isEqualTo(8);
        assertThat(h.wi().nextMap()).isEqualTo(3); // episode 1 returns to E1M4
        assertThat(h.wi().didSecret()).isTrue();
        h.skipIntermission();
        assertThat(h.view().map()).isEqualTo(4);
    }

    @Test
    void parTimesFollowTheTablesAndTheEpisode4Overflow() throws IOException {
        requireIwad("freedoom1.wad");
        Harness h = new Harness("freedoom1.wad", false);
        // pars[2][4] = 120, pars[3][4] = 150; episode 4 reads cpars[map]: cpars[1] = 90, cpars[5] = 150
        int[][] cases = {{2, 4, 120 * 35}, {3, 4, 150 * 35}, {4, 1, 90 * 35}, {4, 5, 150 * 35}};
        for (int[] c : cases) {
            h.newGame(c[0], c[1]);
            h.session.flow().exitLevel();
            h.tick();
            assertThat(h.wi().parTime()).as("E%dM%d", c[0], c[1]).isEqualTo(c[2]);
            assertThat(h.wi().episode()).isEqualTo(c[0] - 1);
        }
    }

    @Test
    void commercialIntermissionGoesStraightToTheNextMapAndFollowsTheSecretTable() throws IOException {
        requireIwad("freedoom2.wad");
        Harness h = new Harness("freedoom2.wad", false);
        h.newGame(1, 5);
        h.session.flow().exitLevel();
        h.tick();
        assertThat(h.wi().parTime()).isEqualTo(90 * 35); // cpars[4]
        assertThat(h.wi().nextMap()).isEqualTo(5);
        assertThat(h.lastMusic(MusicId.DM2INT, true)).isTrue();
        // a commercial intermission has no "show next location" screen
        h.buttons(TicCmd.BT_USE);
        for (int i = 0; i < 80 && h.wi().spState() != 10; i++) {
            h.buttons(i % 2 == 0 ? TicCmd.BT_USE : 0);
            h.tick();
        }
        h.buttons(0);
        h.tick();
        h.buttons(TicCmd.BT_USE);
        h.tick();
        assertThat(h.wi().stage()).isEqualTo(-1);
        h.skipIntermission();
        assertThat(h.view().map()).isEqualTo(6);

        // MAP15 secret exit goes to MAP31, MAP31 returns to MAP16
        h.newGame(1, 15);
        h.session.flow().secretExitLevel();
        h.tick();
        assertThat(h.wi().nextMap()).isEqualTo(30);
        h.session.flow().exitLevel();
        h.tick();
        assertThat(h.wi().nextMap()).isEqualTo(15);
    }

    // ------------------------------------------------------------------------------------------ finale

    @Test
    void episodeEndStartsTheTextFinaleAndSwitchesToTheArtScreen() throws IOException {
        requireIwad("doom1.wad");
        Harness h = new Harness("doom1.wad", false);
        h.newGame(1, 8);
        h.session.flow().exitLevel();
        h.tick();

        assertThat(h.view().state()).isEqualTo(GameState.FINALE);
        assertThat(h.finaleStarted).isEqualTo(1);
        assertThat(h.intermissionStarted).isZero();
        assertThat(h.lastMusic(MusicId.VICTOR, true)).isTrue();
        FinaleView f = h.finale();
        assertThat(f.stage()).isZero();
        assertThat(f.flat()).isEqualTo("FLOOR4_8");
        assertThat(f.text()).startsWith("Once you beat the big badasses and");
        assertThat(f.count()).isEqualTo(1);

        int length = f.text().length();
        int wipes = h.forceWipes;
        h.tick(length * 3 + 249);
        assertThat(f.stage()).isZero();
        assertThat(f.count()).isEqualTo(length * 3 + 250);
        h.tick();
        assertThat(f.stage()).isEqualTo(1);
        assertThat(f.count()).isZero();
        assertThat(h.forceWipes).isEqualTo(wipes + 1);
        // nothing happens after that in a DOOM 1 finale
        h.tick(100);
        assertThat(h.view().state()).isEqualTo(GameState.FINALE);
        assertThat(f.stage()).isEqualTo(1);
    }

    @Test
    void episode3FinaleSwitchesToTheBunnyMusic() throws IOException {
        requireIwad("freedoom1.wad");
        Harness h = new Harness("freedoom1.wad", true);
        h.newGame(3, 8);
        h.session.flow().exitLevel();
        h.tick();
        assertThat(h.view().state()).isEqualTo(GameState.FINALE);
        assertThat(h.finale().flat()).isEqualTo("MFLR8_4");
        int length = h.finale().text().length();
        h.tick(length * 3 + 250);
        assertThat(h.finale().stage()).isEqualTo(1);
        assertThat(h.lastMusic(MusicId.BUNNY, false)).isTrue();
    }

    @Test
    void commercialFinaleStartsAfterTheIntermissionAndAnyButtonContinues() throws IOException {
        requireIwad("freedoom2.wad");
        Harness h = new Harness("freedoom2.wad", true);
        h.newGame(1, 6);
        h.session.flow().exitLevel();
        h.tick();
        assertThat(h.view().state()).isEqualTo(GameState.INTERMISSION);
        h.skipIntermission();
        // G_WorldDone on MAP06 starts the finale instead of loading MAP07
        assertThat(h.view().state()).isEqualTo(GameState.FINALE);
        assertThat(h.view().map()).isEqualTo(6);
        assertThat(h.finale().flat()).isEqualTo("SLIME16");
        assertThat(h.finale().text()).isNotEmpty();
        assertThat(h.lastMusic(MusicId.READ_M, true)).isTrue();

        // buttons are ignored during the first 50 tics, then any button ends the finale (no art screen in DOOM II)
        h.buttons(TicCmd.BT_ATTACK);
        h.tick(10);
        assertThat(h.view().state()).isEqualTo(GameState.FINALE);
        h.buttons(0);
        while (h.finale().count() <= 50) {
            h.tick();
        }
        h.buttons(TicCmd.BT_ATTACK);
        h.tick(); // F_Ticker sets ga_worlddone
        assertThat(h.view().state()).isEqualTo(GameState.FINALE);
        h.tick(); // G_DoWorldDone
        assertThat(h.view().state()).isEqualTo(GameState.LEVEL);
        assertThat(h.view().map()).isEqualTo(7);
    }

    @Test
    void map30FinaleLeadsToTheCastCall() throws IOException {
        requireIwad("freedoom2.wad");
        Harness h = new Harness("freedoom2.wad", false);
        h.newGame(1, 30);
        h.session.flow().exitLevel();
        h.tick();
        h.skipIntermission();
        assertThat(h.view().state()).isEqualTo(GameState.FINALE);
        FinaleView f = h.finale();
        assertThat(f.stage()).isZero();
        while (f.count() <= 50) {
            h.tick();
        }
        h.buttons(TicCmd.BT_USE);
        h.tick();
        h.buttons(0);
        assertThat(f.stage()).isEqualTo(2);
        assertThat(f.castIndex()).isZero();
        assertThat(f.castName()).isEqualTo(h.view().defs().text("CC_ZOMBIE"));
        assertThat(h.lastMusic(MusicId.EVIL, true)).isTrue();

        // a key kills the monster; after its death frames the next one in the cast appears
        h.session.finaleKey();
        int sprite = f.castSprite();
        int frame = f.castFrame();
        h.tick();
        for (int i = 0; i < 300 && f.castIndex() == 0; i++) {
            h.tick();
        }
        assertThat(f.castIndex()).isEqualTo(1);
        assertThat(sprite).isGreaterThanOrEqualTo(0);
        assertThat(frame).isGreaterThanOrEqualTo(0);
        // the cast wraps around after the hero (17 members)
        int guard = 0;
        while (f.castIndex() != 0 && guard++ < 20000) {
            if (f.castIndex() != 0) {
                h.session.finaleKey();
            }
            h.tick();
        }
        assertThat(f.castIndex()).isZero();
    }

    // ------------------------------------------------------------------------------------------ title loop

    @Test
    void titleLoopOfDoom1AlternatesPagesAndDemos() throws IOException {
        requireIwad("doom1.wad");
        Harness h = new Harness("doom1.wad", false);
        h.session.startTitle();
        h.tick();
        assertThat(h.view().state()).isEqualTo(GameState.DEMOSCREEN);
        assertThat(h.view().pageName()).isEqualTo("TITLEPIC");
        assertThat(h.lastMusic(MusicId.INTRO, false)).isTrue();
        // pagetic = 170: the page times out in the 171st tick after the first; the demo starts one tic later
        h.tick(169);
        assertThat(h.view().state()).isEqualTo(GameState.DEMOSCREEN);
        h.tick();
        assertThat(h.view().state()).isEqualTo(GameState.DEMOSCREEN);
        h.tick();
        assertThat(h.view().state()).isEqualTo(GameState.LEVEL);
        assertThat(h.view().demoPlayback()).isTrue();
        assertThat(h.view().userGame()).isFalse();
        assertThat(h.view().map()).isEqualTo(5); // DEMO1 of doom1.wad is E1M5

        List<String> pages = new ArrayList<>();
        List<Integer> maps = new ArrayList<>();
        String last = null;
        for (int i = 0; i < 40000 && pages.size() < 5; i++) {
            h.tick();
            if (h.view().state() == GameState.DEMOSCREEN) {
                String page = h.view().pageName();
                if (!page.equals(last)) {
                    pages.add(page);
                    last = page;
                }
            } else {
                if (last != null) {
                    last = null;
                }
            }
            if (h.view().state() == GameState.LEVEL && h.view().demoPlayback() && last == null
                    && (maps.isEmpty() || maps.get(maps.size() - 1) != h.view().map())) {
                maps.add(h.view().map());
            }
        }
        // credit, demo2, help page, demo3, title again (the version 1.9 sequence has six steps)
        assertThat(pages).containsExactly("CREDIT", "HELP2", "TITLEPIC", "CREDIT", "HELP2");
        assertThat(maps).startsWith(5, 3, 7); // E1M5, E1M3, E1M7 are DEMO1..3 of doom1.wad
    }

    @Test
    void ultimateTitleLoopHasSevenStepsWithDemo4() throws IOException {
        requireIwad("freedoom1.wad");
        Harness h = new Harness("freedoom1.wad", false);
        h.session.startTitle();
        h.tick();
        List<String> sequence = new ArrayList<>();
        String last = "TITLEPIC";
        sequence.add(last);
        for (int i = 0; i < 40000 && sequence.size() < 8; i++) {
            h.tick();
            String now = h.view().state() == GameState.DEMOSCREEN ? h.view().pageName() : "demo";
            if (!now.equals(last)) {
                sequence.add(now);
                last = now;
            }
        }
        assertThat(sequence).containsExactly("TITLEPIC", "demo", "CREDIT", "demo", "CREDIT", "demo", "TITLEPIC", "demo");
    }

    @Test
    void commercialTitlePageLasts11Seconds() throws IOException {
        requireIwad("freedoom2.wad");
        Harness h = new Harness("freedoom2.wad", false);
        h.session.startTitle();
        h.tick();
        assertThat(h.lastMusic(MusicId.DM2TTL, false)).isTrue();
        h.tick(11 * 35 - 1);
        assertThat(h.view().state()).isEqualTo(GameState.DEMOSCREEN);
        h.tick();
        assertThat(h.view().state()).isEqualTo(GameState.DEMOSCREEN);
        h.tick();
        assertThat(h.view().state()).isEqualTo(GameState.LEVEL);
        assertThat(h.view().demoPlayback()).isTrue();
    }

    @Test
    void endGameReturnsToTheTitleLoop() throws IOException {
        requireIwad("doom1.wad");
        Harness h = new Harness("doom1.wad", false);
        h.newGame(1, 1);
        h.session.endGame();
        h.tick();
        assertThat(h.view().state()).isEqualTo(GameState.DEMOSCREEN);
        assertThat(h.view().pageName()).isEqualTo("TITLEPIC");
        assertThat(h.view().userGame()).isFalse();
    }

    // ------------------------------------------------------------------------------------------ -nodeh

    @Test
    void noIwadDehackedKeepsTheVanillaStrings() throws IOException {
        requireIwad("freedoom1.wad");
        requireIwad("doom1.wad");
        Harness with = new Harness("freedoom1.wad", false);
        Harness without = new Harness("freedoom1.wad", true);
        Harness vanilla = new Harness("doom1.wad", false);
        assertThat(without.view().defs().text("E1TEXT")).isEqualTo(vanilla.view().defs().text("E1TEXT"));
        assertThat(with.view().defs().text("E1TEXT")).isNotEqualTo(vanilla.view().defs().text("E1TEXT"));
    }
}
