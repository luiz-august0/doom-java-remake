package doom.game.view;

import doom.game.api.DemoPlayback;
import doom.game.api.GameState;
import doom.game.api.Skill;
import doom.wad.iwad.GameMission;
import doom.wad.iwad.GameMode;
import doom.wad.iwad.GameVariant;
import doom.wad.iwad.GameVersion;

/** Session-level state: game flow, players, intermission and finale state, definitions. */
public interface GameView {

    GameState state();

    /** Number of game tics run since the session started ({@code gametic}). */
    int gametic();

    int levelTime();

    GameMode mode();

    GameMission mission();

    GameVariant variant();

    GameVersion version();

    Skill skill();

    int episode();

    int map();

    boolean paused();

    boolean demoPlayback();

    /** How the current demo is played, or null when no demo plays. */
    DemoPlayback demoPlaybackMode();

    boolean demoRecording();

    boolean netGame();

    /** 0 = coop/single, 1 = deathmatch, 2 = altdeath. */
    int deathmatch();

    /** A game started by the user (not a demo); saving is allowed in it ({@code usergame}). */
    boolean userGame();

    int consolePlayer();

    int displayPlayer();

    boolean playerInGame(int player);

    PlayerView player(int player);

    int totalKills();

    int totalItems();

    int totalSecrets();

    /** Lump name of the page shown in {@link GameState#DEMOSCREEN} (TITLEPIC, CREDIT, HELP2 ...). */
    String pageName();

    IntermissionView intermission();

    FinaleView finale();

    DefsView defs();

    WorldView world();

    ActorView actors();
}
