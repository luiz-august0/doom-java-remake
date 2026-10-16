package doom.ui;

import doom.game.api.DemoPlayback;
import doom.game.api.GameFactory;
import doom.game.api.GameState;
import doom.game.api.Skill;
import doom.game.view.ActorView;
import doom.game.view.DefsView;
import doom.game.view.FinaleView;
import doom.game.view.GameView;
import doom.game.view.IntermissionView;
import doom.game.view.PlayerView;
import doom.game.view.WorldView;
import doom.wad.iwad.GameMission;
import doom.wad.iwad.GameMode;
import doom.wad.iwad.GameVariant;
import doom.wad.iwad.GameVersion;
import java.util.ArrayList;
import java.util.List;

/** Fake game state and recording collaborators for the unit tests. */
final class TestGame {

    final Fake<GameView> game = Fake.of(GameView.class);
    final Fake<PlayerView> player = Fake.of(PlayerView.class);
    final Fake<ActorView> actors = Fake.of(ActorView.class);
    final Fake<WorldView> world = Fake.of(WorldView.class);
    final Fake<IntermissionView> intermission = Fake.of(IntermissionView.class);
    final Fake<FinaleView> finale = Fake.of(FinaleView.class);
    final DefsView defs = GameFactory.loadDefs(null, null, List.of());

    final List<Integer> sounds = new ArrayList<>();
    final List<String> events = new ArrayList<>();
    final MemoryUiSettings settings = new MemoryUiSettings();
    final String[] saved = new String[SaveSlots.COUNT];

    final UiSounds soundSink = sounds::add;
    final UiActions actions = new UiActions() {
        @Override
        public void newGame(Skill skill, int episode, int map) {
            events.add("new " + skill + " " + episode + " " + map);
        }

        @Override
        public void loadGame(int slot) {
            events.add("load " + slot);
        }

        @Override
        public void saveGame(int slot, String description) {
            events.add("save " + slot + " " + description);
        }

        @Override
        public void endGame() {
            events.add("end");
        }

        @Override
        public void quit() {
            events.add("quit");
        }

        @Override
        public void cheatKey(int ch) {
            events.add("cheat " + (char) ch);
        }

        @Override
        public void finaleKey() {
            events.add("finaleKey");
        }
    };

    TestGame(GameMode mode, GameVersion version, GameMission mission) {
        game.set("mode", mode).set("version", version).set("mission", mission).set("variant", GameVariant.VANILLA)
            .set("state", GameState.LEVEL).set("userGame", true).set("skill", Skill.MEDIUM).set("episode", 1)
            .set("map", 1).set("defs", defs).set("world", world.get()).set("actors", actors.get())
            .set("intermission", intermission.get()).set("finale", finale.get())
            .set("pageName", "TITLEPIC").set("demoPlaybackMode", (DemoPlayback) null)
            .set("player", (java.util.function.Function<Object[], Object>) a -> player.get())
            .set("playerInGame", (java.util.function.Function<Object[], Object>) a -> (Integer) a[0] == 0);
    }

    static TestGame registered() {
        return new TestGame(GameMode.REGISTERED, GameVersion.V1_9, GameMission.DOOM);
    }

    Ui.Builder builder() {
        return Ui.builder(UiAssets.empty(), game.get()).sounds(soundSink).actions(actions).settings(settings)
            .saves(slot -> saved[slot]);
    }

    Ui ui() {
        return builder().build();
    }
}
