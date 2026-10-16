package doom.game.impl;

import doom.game.api.DemoPlayback;
import doom.game.api.GameState;
import doom.game.api.Skill;
import doom.game.data.SessionDefsView;
import doom.game.session.Game;
import doom.game.view.ActorView;
import doom.game.view.DefsView;
import doom.game.view.FinaleView;
import doom.game.view.GameView;
import doom.game.view.IntermissionView;
import doom.game.view.PlayerView;
import doom.game.view.WorldView;
import doom.game.world.World;
import doom.wad.iwad.GameMission;
import doom.wad.iwad.GameMode;
import doom.wad.iwad.GameVariant;
import doom.wad.iwad.GameVersion;

/** The session's {@link GameView}. */
final class SimGameView implements GameView {

    private final World w;
    private final Game game;
    private final WorldView world;
    private final ActorView actors;
    private final PlayerView[] players = new PlayerView[World.MAXPLAYERS];
    private final DefsView defs;

    SimGameView(World w, Game game) {
        this.w = w;
        this.game = game;
        this.world = new SimWorldView(w);
        this.actors = new SimActorView(w);
        for (int i = 0; i < players.length; i++) {
            players[i] = new SimPlayerView(w, i);
        }
        this.defs = new SessionDefsView(w.defs, w.strings);
    }

    @Override
    public GameState state() {
        return game.gameState;
    }

    @Override
    public int gametic() {
        return w.gametic;
    }

    @Override
    public int levelTime() {
        return w.levelTime;
    }

    @Override
    public GameMode mode() {
        return w.mode;
    }

    @Override
    public GameMission mission() {
        return w.mission;
    }

    @Override
    public GameVariant variant() {
        return w.variant;
    }

    @Override
    public GameVersion version() {
        return w.version;
    }

    @Override
    public Skill skill() {
        return Skill.of(w.gameSkill);
    }

    @Override
    public int episode() {
        return w.gameEpisode;
    }

    @Override
    public int map() {
        return w.gameMap;
    }

    @Override
    public boolean paused() {
        return w.paused;
    }

    @Override
    public boolean demoPlayback() {
        return w.demoPlayback;
    }

    @Override
    public DemoPlayback demoPlaybackMode() {
        return w.demoPlayback ? game.demoMode : null;
    }

    @Override
    public boolean demoRecording() {
        return game.demoRecording;
    }

    @Override
    public boolean netGame() {
        return w.netGame;
    }

    @Override
    public int deathmatch() {
        return w.deathmatch;
    }

    @Override
    public boolean userGame() {
        return game.userGame;
    }

    @Override
    public int consolePlayer() {
        return w.consolePlayer;
    }

    @Override
    public int displayPlayer() {
        return w.displayPlayer;
    }

    @Override
    public boolean playerInGame(int player) {
        return w.playerInGame[player];
    }

    @Override
    public PlayerView player(int player) {
        return players[player];
    }

    @Override
    public int totalKills() {
        return w.totalKills;
    }

    @Override
    public int totalItems() {
        return w.totalItems;
    }

    @Override
    public int totalSecrets() {
        return w.totalSecret;
    }

    @Override
    public String pageName() {
        return game.pageName;
    }

    @Override
    public IntermissionView intermission() {
        return game.intermission();
    }

    @Override
    public FinaleView finale() {
        return game.finale();
    }

    @Override
    public DefsView defs() {
        return defs;
    }

    @Override
    public WorldView world() {
        return world;
    }

    @Override
    public ActorView actors() {
        return actors;
    }
}
