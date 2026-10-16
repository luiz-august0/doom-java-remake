package doom.game.session;

import doom.game.actor.Actor;
import doom.game.actor.Thinker;
import doom.game.api.DemoPlayback;
import doom.game.api.GameParams;
import doom.game.api.GameState;
import doom.game.api.TicCmd;
import doom.game.data.MobjType;
import doom.game.data.StateId;
import doom.game.data.State;
import doom.game.data.ThingType;
import doom.game.player.Player;
import doom.game.view.MobjFlags;
import doom.game.view.MusicId;
import doom.game.view.SoundId;
import doom.game.world.GameFlow;
import doom.game.world.Level;
import doom.game.world.LevelLoader;
import doom.game.world.Sector;
import doom.game.world.Side;
import doom.game.world.SpawnPoint;
import doom.game.world.Subsector;
import doom.game.world.World;
import doom.math.Angle;
import doom.math.DoomFatalException;
import doom.math.Fixed;
import doom.math.Tables;
import doom.math.annotation.VanillaRef;
import doom.wad.iwad.GameMode;
import doom.wad.iwad.GameVariant;
import doom.wad.iwad.GameVersion;
import doom.wad.map.MapThing;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * The game flow of a session ({@code g_game.c}, the game-tic part of {@code d_main.c}, {@code P_SetupLevel},
 * {@code P_Ticker}): game actions, new game, level loading, player respawn, demo playback and recording.
 *
 * <p>W3-D completed the flow: level completion ({@code G_DoCompleted}, {@code G_WorldDone}), the intermission
 * ({@link Intermission}), the finale ({@link Finale}) and the title loop ({@code D_DoAdvanceDemo},
 * {@code D_PageTicker}). Saving and loading call a {@link SaveGameHandler} when one is installed.
 */
public final class Game implements GameFlow {

    private static final System.Logger LOG = System.getLogger(Game.class.getName());

    /** {@code gameaction_t}. */
    public enum GameAction {
        NOTHING, LOADLEVEL, NEWGAME, LOADGAME, SAVEGAME, PLAYDEMO, COMPLETED, VICTORY, WORLDDONE, SCREENSHOT
    }

    private static final int DEMOMARKER = 0x80;
    private static final int TURBOTHRESHOLD = 0x32;
    private static final int[] SPMUS = {
        MusicId.E3M4, MusicId.E3M2, MusicId.E3M3, MusicId.E1M5, MusicId.E2M7, MusicId.E2M4, MusicId.E2M6,
        MusicId.E2M5, MusicId.E1M9
    };
    private static final String[] PLAYER_NAME_KEYS = {"HUSTR_PLRGREEN", "HUSTR_PLRINDIGO", "HUSTR_PLRBROWN",
        "HUSTR_PLRRED"};

    private final World w;
    private final LevelLoader loader;

    public GameState gameState = GameState.DEMOSCREEN;
    private GameState oldGameState = GameState.DEMOSCREEN;
    public GameAction gameAction = GameAction.NOTHING;
    public boolean userGame;
    public boolean secretExit;
    /** {@code levelstarttic}. */
    public int levelStartTic;
    /** Lump name of the page shown in the title loop. */
    public String pageName = "TITLEPIC";

    // deferred new game
    private int dSkill;
    private int dEpisode;
    private int dMap;
    private GameParams dParams;

    // demo playback
    private String defDemoName;
    private byte[] defDemo;
    private DemoPlayback defDemoMode;
    private byte[] demoBuffer;
    private int demoP;
    public DemoPlayback demoMode;
    public boolean singleDemo;
    public boolean timingDemo;
    /** The program ended inside the last tick ({@code I_Error} / {@code I_Quit} of a finished demo). */
    public boolean quit;
    private boolean advanceDemo;

    // demo recording
    public boolean demoRecording;
    private byte[] recordBuffer;
    private int recordP;
    private String recordName;

    // special commands requested by the presentation
    private boolean sendPause;
    private boolean sendSave;
    private int saveSlot;
    private String saveDescription;

    private final boolean[] turboDetected = new boolean[World.MAXPLAYERS];
    private final TicCmd[] noCmds = {new TicCmd(), new TicCmd(), new TicCmd(), new TicCmd()};

    /** {@code wminfo}: parameters of the intermission, kept for the whole session. */
    private final WbStart wminfo = new WbStart();
    private final Intermission intermission;
    private final Finale finale;

    // title loop (d_main.c)
    private int demoSequence;
    private int pageTic;

    // save and load plumbing
    private SaveGameHandler saveGameHandler;
    private byte[] loadData;

    public Game(World world) {
        this.w = world;
        this.loader = new LevelLoader(world.textures, world.flats);
        this.intermission = new Intermission(world, this);
        this.finale = new Finale(world, this);
        // D_CheckNetGame: a single-player session starts with player 0 in the game (the title loop traces it)
        world.playerInGame[0] = true;
    }

    /** The intermission state, as the view for the presentation. */
    public Intermission intermission() {
        return intermission;
    }

    /** The finale state, as the view for the presentation. */
    public Finale finale() {
        return finale;
    }

    /** The installed persistence hook, or null. */
    public SaveGameHandler saveGameHandler() {
        return saveGameHandler;
    }

    /** Installs the persistence hook ({@code G_DoSaveGame} / {@code G_DoLoadGame}); null removes it. */
    public void setSaveGameHandler(SaveGameHandler handler) {
        this.saveGameHandler = handler;
    }

    /** {@code G_LoadGame}: replaces the state with the snapshot at the next tic. */
    @VanillaRef("g_game.c:G_LoadGame")
    public void loadGame(byte[] data) {
        loadData = data;
        gameAction = GameAction.LOADGAME;
    }

    /** {@code F_CastResponder} (through {@code F_Responder}): a key press during the DOOM II cast call. */
    public void finaleKey() {
        finale.castKey();
    }

    // ==================================================================================== commands

    /** {@code G_DeferedInitNew} with all start parameters. */
    public void deferredNewGame(GameParams params) {
        dParams = params;
        dSkill = params.skill().ordinal();
        dEpisode = params.episode();
        dMap = params.map();
        gameAction = GameAction.NEWGAME;
    }

    @Override
    @VanillaRef("g_game.c:G_DeferedInitNew")
    public void deferredInitNew(int skill, int episode, int map) {
        dParams = null;
        dSkill = skill;
        dEpisode = episode;
        dMap = map;
        gameAction = GameAction.NEWGAME;
    }

    /** {@code G_DeferedPlayDemo} / {@code G_TimeDemo}. */
    public void deferredPlayDemo(String name, byte[] demo, DemoPlayback mode) {
        defDemoName = name;
        defDemo = demo.clone();
        defDemoMode = mode;
        singleDemo = mode == DemoPlayback.SINGLE;
        timingDemo = mode == DemoPlayback.TIMEDEMO;
        gameAction = GameAction.PLAYDEMO;
    }

    public void requestPause() {
        sendPause = true;
    }

    public void requestSave(int slot, String description) {
        sendSave = true;
        saveSlot = slot;
        saveDescription = description;
    }

    /**
     * {@code D_StartTitle}: restarts the title loop. Like the original it only schedules the first page
     * ({@code D_AdvanceDemo}); {@code D_DoAdvanceDemo} runs at the start of the next tic.
     */
    @VanillaRef("d_main.c:D_StartTitle")
    public void startTitle() {
        gameAction = GameAction.NOTHING;
        demoSequence = -1;
        advanceDemo = true; // D_AdvanceDemo
    }

    @Override
    @VanillaRef("g_game.c:G_ExitLevel")
    public void exitLevel() {
        secretExit = false;
        gameAction = GameAction.COMPLETED;
    }

    @Override
    @VanillaRef("g_game.c:G_SecretExitLevel")
    public void secretExitLevel() {
        // IF NO WOLF3D LEVELS, NO SECRET EXIT!
        secretExit = !(w.mode == GameMode.COMMERCIAL && w.lumps.find("MAP31") < 0);
        gameAction = GameAction.COMPLETED;
    }

    /** F12: view the next player ({@code G_Responder}, {@code key_spy}). */
    public void cycleDisplayPlayer() {
        if (gameState == GameState.LEVEL && (singleDemo || w.deathmatch == 0)) {
            do {
                w.displayPlayer++;
                if (w.displayPlayer == World.MAXPLAYERS) {
                    w.displayPlayer = 0;
                }
            } while (!w.playerInGame[w.displayPlayer] && w.displayPlayer != w.consolePlayer);
        }
    }

    // ==================================================================================== tick

    /** One game tic: {@code D_DoAdvanceDemo} when pending, then {@code G_Ticker}. */
    public void tick(TicCmd[] cmds, boolean menuActive) {
        if (quit) {
            return;
        }
        if (cmds == null) {
            cmds = noCmds;
        }
        snapshotInterpolation();
        if (advanceDemo) {
            doAdvanceDemo();
        }
        ticker(cmds, menuActive);
        if (!quit) {
            w.gametic++;
        }
    }

    /** {@code G_Ticker}: make ticcmd_ts for the players, run the game. */
    @VanillaRef("g_game.c:G_Ticker")
    private void ticker(TicCmd[] netcmds, boolean menuActive) {
        // do player reborns if needed
        for (int i = 0; i < World.MAXPLAYERS; i++) {
            if (w.playerInGame[i] && w.players[i].playerState == Player.PST_REBORN) {
                doReborn(i);
            }
        }

        // do things to change the game state
        while (gameAction != GameAction.NOTHING) {
            switch (gameAction) {
                case LOADLEVEL -> doLoadLevel();
                case NEWGAME -> doNewGame();
                case LOADGAME -> doLoadGame();
                case SAVEGAME -> doSaveGame();
                case PLAYDEMO -> doPlayDemo();
                case COMPLETED -> doCompleted();
                case VICTORY -> finale.start(); // F_StartFinale
                case WORLDDONE -> doWorldDone();
                case SCREENSHOT -> gameAction = GameAction.NOTHING;
                default -> gameAction = GameAction.NOTHING;
            }
        }

        // get commands
        for (int i = 0; i < World.MAXPLAYERS; i++) {
            if (w.playerInGame[i]) {
                TicCmd cmd = w.players[i].cmd;
                cmd.copyFrom(netcmds[i]);
                if (!w.demoPlayback && i == w.consolePlayer) {
                    addSpecialButtons(cmd);
                }

                if (w.demoPlayback) {
                    readDemoTiccmd(cmd);
                    if (quit) {
                        return; // I_Error / I_Quit inside G_Ticker
                    }
                }
                if (demoRecording) {
                    writeDemoTiccmd(cmd);
                }

                // check for turbo cheats
                if (cmd.forwardMove > TURBOTHRESHOLD) {
                    turboDetected[i] = true;
                }
                if ((w.gametic & 31) == 0 && ((w.gametic >> 5) % World.MAXPLAYERS) == i && turboDetected[i]) {
                    String message = w.strings.get(PLAYER_NAME_KEYS[i]) + " is turbo!";
                    w.players[w.consolePlayer].message = message;
                    w.events.playerMessage(w.consolePlayer, message);
                    turboDetected[i] = false;
                }
            }
        }

        // check for special buttons
        for (int i = 0; i < World.MAXPLAYERS; i++) {
            if (w.playerInGame[i]) {
                int buttons = w.players[i].cmd.buttons & 0xFF;
                if ((buttons & TicCmd.BT_SPECIAL) != 0) {
                    switch (buttons & TicCmd.BT_SPECIALMASK) {
                        case TicCmd.BTS_PAUSE -> {
                            w.paused = !w.paused;
                            w.events.pauseSound(w.paused);
                        }
                        case TicCmd.BTS_SAVEGAME -> {
                            if (saveDescription == null || saveDescription.isEmpty()) {
                                saveDescription = "NET GAME";
                            }
                            saveSlot = (buttons & TicCmd.BTS_SAVEMASK) >> TicCmd.BTS_SAVESHIFT;
                            gameAction = GameAction.SAVEGAME;
                        }
                        default -> {
                        }
                    }
                }
            }
        }

        oldGameState = gameState;

        // do main actions
        switch (gameState) {
            case LEVEL -> {
                pTicker(menuActive);
                w.events.levelTick();
            }
            case INTERMISSION -> intermission.ticker();
            case FINALE -> finale.ticker();
            case DEMOSCREEN -> pageTicker();
            default -> {
            }
        }
    }

    /** {@code G_BuildTiccmd}'s {@code sendpause} / {@code sendsave} part. */
    private void addSpecialButtons(TicCmd cmd) {
        if (sendPause) {
            sendPause = false;
            cmd.buttons = (byte) (TicCmd.BT_SPECIAL | TicCmd.BTS_PAUSE);
        }
        if (sendSave) {
            sendSave = false;
            cmd.buttons = (byte) (TicCmd.BT_SPECIAL | TicCmd.BTS_SAVEGAME | (saveSlot << TicCmd.BTS_SAVESHIFT));
        }
    }

    /** {@code P_Ticker}. */
    @VanillaRef("p_tick.c:P_Ticker")
    private void pTicker(boolean menuActive) {
        // run the tic
        if (w.paused) {
            return;
        }
        // pause if in menu and at least one tic has been run
        if (!w.netGame && menuActive && !w.demoPlayback && w.players[w.consolePlayer].viewZ != 1) {
            return;
        }

        for (int i = 0; i < World.MAXPLAYERS; i++) {
            if (w.playerInGame[i]) {
                w.playerSim.playerThink(w.players[i]);
            }
        }

        w.thinkers.run();
        w.specials.updateSpecials();
        w.mobjs.respawnSpecials();

        // for par times
        w.levelTime++;
    }

    /** Writes the {@code prev*} interpolation fields (ADR-011); never read by the simulation. */
    private void snapshotInterpolation() {
        for (Thinker t = w.thinkers.first(); t != null; t = w.thinkers.next(t)) {
            if (t instanceof Actor a) {
                a.snapInterpolation();
            }
        }
        Level level = w.level;
        if (level != null) {
            for (Sector s : level.sectors) {
                s.prevFloorHeight = s.floorHeight;
                s.prevCeilingHeight = s.ceilingHeight;
            }
            for (Side sd : level.sides) {
                sd.prevTextureOffset = sd.textureOffset;
            }
        }
        for (Player p : w.players) {
            p.prevViewZ = p.viewZ;
            p.psprites[0].prevSx = p.psprites[0].sx;
            p.psprites[0].prevSy = p.psprites[0].sy;
            p.psprites[1].prevSx = p.psprites[1].sx;
            p.psprites[1].prevSy = p.psprites[1].sy;
        }
    }

    // ==================================================================================== new game / level

    /** {@code G_DoNewGame}. */
    @VanillaRef("g_game.c:G_DoNewGame")
    private void doNewGame() {
        w.demoPlayback = false;
        w.netDemo = false;
        GameParams p = dParams;
        if (p == null) {
            // menu / IDCLEV: single player
            w.netGame = false;
            w.deathmatch = 0;
            w.playerInGame[1] = w.playerInGame[2] = w.playerInGame[3] = false;
            w.respawnParm = false;
            w.fastParm = false;
            w.noMonsters = false;
            w.consolePlayer = 0;
        } else {
            // command line start (-warp and friends): the parameters are set before G_InitNew
            for (int i = 0; i < World.MAXPLAYERS; i++) {
                w.playerInGame[i] = (p.playersInGame() & (1 << i)) != 0;
            }
            w.netGame = (p.playersInGame() & ~1) != 0;
            w.deathmatch = p.deathmatch();
            w.respawnParm = p.respawn();
            w.fastParm = p.fast();
            w.noMonsters = p.noMonsters();
            w.consolePlayer = p.consolePlayer();
        }
        initNew(dSkill, dEpisode, dMap);
        gameAction = GameAction.NOTHING;
    }

    /** {@code G_InitNew}. */
    @VanillaRef("g_game.c:G_InitNew")
    public void initNew(int skill, int episode, int map) {
        if (w.paused) {
            w.paused = false;
            w.events.pauseSound(false);
        }

        if (skill > 4) {
            skill = 4;
        }

        if (w.version.isAtLeast(GameVersion.ULTIMATE)) {
            if (episode == 0) {
                episode = 4;
            }
        } else {
            if (episode < 1) {
                episode = 1;
            }
            if (episode > 3) {
                episode = 3;
            }
        }

        if (episode > 1 && w.mode == GameMode.SHAREWARE) {
            episode = 1;
        }

        if (map < 1) {
            map = 1;
        }
        if (map > 9 && w.mode != GameMode.COMMERCIAL) {
            map = 9;
        }

        w.rng.clear(); // M_ClearRandom

        w.respawnMonsters = skill == 4 || w.respawnParm;

        State[] states = w.defs.states();
        ThingType[] things = w.defs.things();
        if (w.fastParm || (skill == 4 && w.gameSkill != 4)) {
            for (int i = StateId.SARG_RUN1; i <= StateId.SARG_PAIN2; i++) {
                states[i].tics >>= 1;
            }
            things[MobjType.BRUISERSHOT].speed = 20 * Fixed.FRACUNIT;
            things[MobjType.HEADSHOT].speed = 20 * Fixed.FRACUNIT;
            things[MobjType.TROOPSHOT].speed = 20 * Fixed.FRACUNIT;
        } else if (skill != 4 && w.gameSkill == 4) {
            for (int i = StateId.SARG_RUN1; i <= StateId.SARG_PAIN2; i++) {
                states[i].tics <<= 1;
            }
            things[MobjType.BRUISERSHOT].speed = 15 * Fixed.FRACUNIT;
            things[MobjType.HEADSHOT].speed = 10 * Fixed.FRACUNIT;
            things[MobjType.TROOPSHOT].speed = 10 * Fixed.FRACUNIT;
        }

        // force players to be initialized upon first level load
        for (int i = 0; i < World.MAXPLAYERS; i++) {
            w.players[i].playerState = Player.PST_REBORN;
        }

        userGame = true; // will be set false if a demo
        w.paused = false;
        w.demoPlayback = false;
        w.gameEpisode = episode;
        w.gameMap = map;
        w.gameSkill = skill;

        // Set the sky to use. This IS broken ("sky never changes in Doom II"), but it is how Vanilla Doom behaves.
        String skyName;
        if (w.mode == GameMode.COMMERCIAL) {
            skyName = map < 12 ? "SKY1" : map < 21 ? "SKY2" : "SKY3";
        } else {
            skyName = switch (episode) {
                case 2 -> "SKY2";
                case 3 -> "SKY3";
                case 4 -> "SKY4"; // Special Edition sky
                default -> "SKY1";
            };
        }
        w.skyTexture = w.textures.numForName(w.strings.translate(skyName));

        doLoadLevel();
    }

    /** {@code G_DoLoadLevel}. */
    @VanillaRef("g_game.c:G_DoLoadLevel")
    private void doLoadLevel() {
        w.skyFlatNum = w.flats.numForName(w.strings.translate("F_SKY1"));

        // The "Sky never changes in Doom II" bug was fixed in the id Anthology version of doom2.exe for Final Doom.
        if (w.mode == GameMode.COMMERCIAL && w.version == GameVersion.FINAL2) {
            String sky = w.gameMap < 12 ? "SKY1" : w.gameMap < 21 ? "SKY2" : "SKY3";
            w.skyTexture = w.textures.numForName(w.strings.translate(sky));
        }

        levelStartTic = w.gametic; // for time calculation

        if (gameState == GameState.LEVEL) {
            w.events.forceWipe(); // wipegamestate = -1
        }
        gameState = GameState.LEVEL;

        for (int i = 0; i < World.MAXPLAYERS; i++) {
            turboDetected[i] = false;
            if (w.playerInGame[i] && w.players[i].playerState == Player.PST_DEAD) {
                w.players[i].playerState = Player.PST_REBORN;
            }
            Arrays.fill(w.players[i].frags, 0);
        }

        setupLevel(w.gameEpisode, w.gameMap);
        w.displayPlayer = w.consolePlayer; // view the guy you are playing
        gameAction = GameAction.NOTHING;

        // clear cmd building stuff
        sendPause = sendSave = false;
        w.paused = false;
        w.events.levelLoaded();
    }

    /** {@code P_SetupLevel}. */
    @VanillaRef("p_setup.c:P_SetupLevel")
    private void setupLevel(int episode, int map) {
        w.totalKills = w.totalItems = w.totalSecret = 0;
        for (int i = 0; i < World.MAXPLAYERS; i++) {
            w.players[i].killCount = w.players[i].secretCount = w.players[i].itemCount = 0;
        }

        // Initial height of PointOfView will be set by player think.
        w.players[w.consolePlayer].viewZ = 1;

        // Make sure all sounds are stopped before the level is freed (S_Start).
        releaseLevelActors();
        w.events.changeMusic(levelMusic(episode, map), true);

        w.thinkers.clear();

        // find map name
        String lumpName;
        if (w.mode == GameMode.COMMERCIAL) {
            lumpName = map < 10 ? "MAP0" + map : "MAP" + map;
        } else {
            lumpName = "E" + episode + "M" + map;
        }
        if (w.lumps.find(lumpName) < 0) {
            throw new DoomFatalException("W_GetNumForName: " + lumpName + " not found!");
        }

        w.levelTime = 0;

        // note: most of this ordering is important
        Level level = loader.load(w.lumps, lumpName);
        w.level = level;
        w.blockLinks = new Actor[level.blockmap.columns * level.blockmap.rows];

        w.bodyQueSlot = 0;
        w.deathmatchCount = 0;
        loadThings(level.things);

        // if deathmatch, randomly spawn the active players
        if (w.deathmatch != 0) {
            for (int i = 0; i < World.MAXPLAYERS; i++) {
                if (w.playerInGame[i]) {
                    w.players[i].mo = null;
                    deathMatchSpawnPlayer(i);
                }
            }
        }

        // clear special respawning que
        w.iqueHead = w.iqueTail = 0;

        // set up world state
        w.specials.spawnSpecials();
    }

    /** {@code S_Start}'s music choice. */
    @VanillaRef("s_sound.c:S_Start")
    private int levelMusic(int episode, int map) {
        if (w.mode == GameMode.COMMERCIAL) {
            return MusicId.RUNNIN + map - 1;
        }
        if (episode < 4) {
            return MusicId.E1M1 + (episode - 1) * 9 + map - 1;
        }
        return SPMUS[map - 1];
    }

    /** The previous level's actors go away with {@code Z_FreeTags}: stop their sounds and free their ids. */
    private void releaseLevelActors() {
        for (Thinker t = w.thinkers.first(); t != null; t = w.thinkers.next(t)) {
            if (t instanceof Actor a && a.id >= 0) {
                w.events.stopSound(a.id);
                w.actorIds.release(a);
            }
        }
        for (Player p : w.players) {
            if (p.mo != null && p.mo.isRemoved()) {
                p.mo = null;
            }
        }
    }

    /** {@code P_LoadThings}. */
    @VanillaRef("p_setup.c:P_LoadThings")
    private void loadThings(MapThing[] things) {
        SpawnPoint spawnThing = new SpawnPoint();
        for (MapThing mt : things) {
            boolean spawn = true;

            // Do not spawn cool, new monsters if !commercial
            if (w.mode != GameMode.COMMERCIAL) {
                switch (mt.type()) {
                    case 68, 64, 88, 89, 69, 67, 71, 65, 66, 84 -> spawn = false;
                    default -> {
                    }
                }
            }
            if (!spawn) {
                break;
            }

            // Do spawn all other stuff.
            spawnThing.set(mt);
            w.mobjs.spawnMapThing(spawnThing);
        }

        if (w.deathmatch == 0) {
            for (int i = 0; i < World.MAXPLAYERS; i++) {
                if (w.playerInGame[i] && !w.playerStartsInGame[i]) {
                    throw new DoomFatalException("P_LoadThings: Player " + (i + 1)
                            + " start missing (vanilla crashes here)");
                }
                w.playerStartsInGame[i] = false;
            }
        }
    }

    // ==================================================================================== respawn

    /** {@code G_CheckSpot}: false if the player cannot be respawned at the spot because something is there. */
    @VanillaRef("g_game.c:G_CheckSpot")
    private boolean checkSpot(int playernum, SpawnPoint mthing) {
        if (w.players[playernum].mo == null) {
            // first spawn of level, before corpses
            for (int i = 0; i < playernum; i++) {
                Actor mo = w.players[i].mo;
                // DIVERGE: the original dereferences a null pointer for a player not in the game.
                if (mo != null && mo.x == mthing.x << Fixed.FRACBITS && mo.y == mthing.y << Fixed.FRACBITS) {
                    return false;
                }
            }
            return true;
        }

        int x = mthing.x << Fixed.FRACBITS;
        int y = mthing.y << Fixed.FRACBITS;

        if (!w.physics.checkPosition(w.players[playernum].mo, x, y)) {
            return false;
        }

        // flush an old corpse if needed
        if (w.bodyQueSlot >= World.BODYQUESIZE) {
            w.mobjs.removeMobj(w.bodyQue[w.bodyQueSlot % World.BODYQUESIZE]);
        }
        w.bodyQue[w.bodyQueSlot % World.BODYQUESIZE] = w.players[playernum].mo;
        w.bodyQueSlot++;

        // spawn a teleport fog
        Subsector ss = w.physics.pointInSubsector(x, y);

        // 'an' can be a signed value in the DOS version: a negative index reads finetangent[] (PrBoom+ emulation).
        int an = (Angle.ANG45 >>> Angle.ANGLETOFINESHIFT) * (mthing.angle / 45);
        int xa;
        int ya;
        switch (an) {
            case 4096 -> { // -4096
                xa = Tables.fineTangent(2048); // finecosine[-4096]
                ya = Tables.fineTangent(0); // finesine[-4096]
            }
            case 5120 -> { // -3072
                xa = Tables.fineTangent(3072);
                ya = Tables.fineTangent(1024);
            }
            case 6144 -> { // -2048
                xa = Tables.fineSine(0);
                ya = Tables.fineTangent(2048);
            }
            case 7168 -> { // -1024
                xa = Tables.fineSine(1024);
                ya = Tables.fineTangent(3072);
            }
            case 0, 1024, 2048, 3072 -> {
                xa = Tables.fineCosine(an);
                ya = Tables.fineSine(an);
            }
            default -> throw new DoomFatalException("G_CheckSpot: unexpected angle " + an);
        }
        Actor mo = w.mobjs.spawnMobj(x + 20 * xa, y + 20 * ya, ss.sector.floorHeight, MobjType.TFOG);

        if (w.players[w.consolePlayer].viewZ != 1) {
            w.startSound(mo, SoundId.TELEPT); // don't start sound on first frame
        }
        return true;
    }

    /** {@code G_DeathMatchSpawnPlayer}: spawns a player at one of the random death match spots. */
    @VanillaRef("g_game.c:G_DeathMatchSpawnPlayer")
    private void deathMatchSpawnPlayer(int playernum) {
        int selections = w.deathmatchCount;
        if (selections < 4) {
            throw new DoomFatalException("Only " + selections + " deathmatch spots, 4 required");
        }
        for (int j = 0; j < 20; j++) {
            int i = w.rng.game() % selections;
            if (checkSpot(playernum, w.deathmatchStarts[i])) {
                w.deathmatchStarts[i].type = playernum + 1;
                w.mobjs.spawnPlayer(w.deathmatchStarts[i]);
                return;
            }
        }
        // no good spot, so the player will probably get stuck
        w.mobjs.spawnPlayer(w.playerStarts[playernum]);
    }

    /** {@code G_DoReborn}. */
    @VanillaRef("g_game.c:G_DoReborn")
    private void doReborn(int playernum) {
        if (!w.netGame) {
            // reload the level from scratch
            gameAction = GameAction.LOADLEVEL;
            return;
        }

        // respawn at the start; first dissasociate the corpse
        w.players[playernum].mo.player = null;

        // spawn at random spot if in death match
        if (w.deathmatch != 0) {
            deathMatchSpawnPlayer(playernum);
            return;
        }

        if (checkSpot(playernum, w.playerStarts[playernum])) {
            w.mobjs.spawnPlayer(w.playerStarts[playernum]);
            return;
        }

        // try to spawn at one of the other players spots
        for (int i = 0; i < World.MAXPLAYERS; i++) {
            if (checkSpot(playernum, w.playerStarts[i])) {
                w.playerStarts[i].type = playernum + 1; // fake as other player
                w.mobjs.spawnPlayer(w.playerStarts[i]);
                w.playerStarts[i].type = i + 1; // restore
                return;
            }
            // he's going to be inside something. Too bad.
        }
        w.mobjs.spawnPlayer(w.playerStarts[playernum]);
    }

    /** {@code G_PlayerFinishLevel}: take away cards and stuff. */
    @VanillaRef("g_game.c:G_PlayerFinishLevel")
    private void playerFinishLevel(int player) {
        Player p = w.players[player];
        Arrays.fill(p.powers, 0);
        Arrays.fill(p.cards, false);
        p.mo.flags &= ~MobjFlags.SHADOW; // cancel invisibility
        p.extraLight = 0; // cancel gun flashes
        p.fixedColormap = 0; // cancel ir gogles
        p.damageCount = 0; // no palette changes
        p.bonusCount = 0;
    }

    // ==================================================================================== level completion

    /** {@code pars[][]}: DOOM par times in seconds, by episode (1-based) and map. */
    private static final int[][] PARS = {
        {0},
        {0, 30, 75, 120, 90, 165, 180, 180, 30, 165},
        {0, 90, 90, 90, 120, 90, 360, 240, 30, 170},
        {0, 90, 45, 90, 150, 90, 90, 165, 30, 135}
    };

    /** {@code cpars[]}: DOOM II par times in seconds. */
    private static final int[] CPARS = {
        30, 90, 120, 120, 90, 150, 120, 120, 270, 90, // 1-10
        210, 150, 150, 150, 210, 150, 420, 150, 210, 150, // 11-20
        240, 150, 180, 150, 150, 300, 330, 420, 300, 180, // 21-30
        120, 30 // 31-32
    };

    /** {@code G_DoCompleted}: the level is over; sets up the intermission or the victory finale. */
    @VanillaRef("g_game.c:G_DoCompleted")
    private void doCompleted() {
        gameAction = GameAction.NOTHING;

        for (int i = 0; i < World.MAXPLAYERS; i++) {
            if (w.playerInGame[i]) {
                playerFinishLevel(i); // take away cards and stuff
            }
        }

        // (AM_Stop: the automap is closed by the presentation when the game state changes.)

        if (w.mode != GameMode.COMMERCIAL) {
            // (Chex Quest, which ends after map 5, is not a supported game version.)
            switch (w.gameMap) {
                case 8 -> {
                    gameAction = GameAction.VICTORY;
                    return;
                }
                case 9 -> {
                    for (int i = 0; i < World.MAXPLAYERS; i++) {
                        w.players[i].didSecret = true;
                    }
                }
                default -> {
                }
            }
        }
        // The original repeats the same two tests again below ("Hmmm - why?"); they cannot change anything.

        wminfo.didSecret = w.players[w.consolePlayer].didSecret;
        wminfo.epsd = w.gameEpisode - 1;
        wminfo.last = w.gameMap - 1;

        // wminfo.next is 0 biased, unlike gamemap. Paths that assign nothing keep the value of the previous level.
        if (w.mode == GameMode.COMMERCIAL) {
            if (secretExit) {
                switch (w.gameMap) {
                    case 15 -> wminfo.next = 30;
                    case 31 -> wminfo.next = 31;
                    default -> {
                    }
                }
            } else {
                switch (w.gameMap) {
                    case 31, 32 -> wminfo.next = 15;
                    default -> wminfo.next = w.gameMap;
                }
            }
        } else {
            if (secretExit) {
                wminfo.next = 8; // go to secret level
            } else if (w.gameMap == 9) {
                // returning from secret level
                switch (w.gameEpisode) {
                    case 1 -> wminfo.next = 3;
                    case 2 -> wminfo.next = 5;
                    case 3 -> wminfo.next = 6;
                    case 4 -> wminfo.next = 2;
                    default -> {
                    }
                }
            } else {
                wminfo.next = w.gameMap; // go to next level
            }
        }

        wminfo.maxKills = w.totalKills;
        wminfo.maxItems = w.totalItems;
        wminfo.maxSecret = w.totalSecret;
        wminfo.maxFrags = 0;
        wminfo.parTime = parTime();
        wminfo.pnum = w.consolePlayer;

        for (int i = 0; i < World.MAXPLAYERS; i++) {
            WbStart.Player pl = wminfo.plyr[i];
            pl.in = w.playerInGame[i];
            pl.skills = w.players[i].killCount;
            pl.sitems = w.players[i].itemCount;
            pl.ssecret = w.players[i].secretCount;
            pl.stime = w.levelTime;
            System.arraycopy(w.players[i].frags, 0, pl.frags, 0, World.MAXPLAYERS);
        }

        gameState = GameState.INTERMISSION;
        // (StatCopy: -statdump is not supported.)
        intermission.start(wminfo); // WI_Start
        w.events.intermissionStarted();
    }

    /**
     * The par time of the level just completed, in tics (the {@code wminfo.partime} block of
     * {@code G_DoCompleted}), including its quirks: MAP33 reads its par time from the bytes of the
     * {@code GAMMALVL0} string, and episode 4 reads past {@code pars[]} into {@code cpars[]}.
     */
    private int parTime() {
        int gamemap = w.gameMap;
        if (w.mode == GameMode.COMMERCIAL) {
            // map33 reads its par time from beyond the cpars[] array
            if (gamemap == 33) {
                byte[] text = w.strings.get("GAMMALVL0").getBytes(StandardCharsets.ISO_8859_1);
                int cpars32 = 0;
                for (int i = 0; i < 4; i++) {
                    // memcpy of 4 bytes, little endian; the string is NUL terminated
                    int b = i < text.length ? text[i] & 0xFF : 0;
                    cpars32 |= b << (8 * i);
                }
                return World.TICRATE * cpars32;
            }
            return World.TICRATE * cpar(gamemap - 1);
        }
        // Doom episode 4 doesn't have a par time, so this overflows into the cpars array.
        if (w.gameEpisode < 4) {
            return World.TICRATE * PARS[w.gameEpisode][gamemap];
        }
        return World.TICRATE * cpar(gamemap);
    }

    /** DIVERGE: an index outside {@code cpars[]} reads 0 instead of unrelated memory (cannot happen for real maps). */
    private static int cpar(int index) {
        return index >= 0 && index < CPARS.length ? CPARS[index] : 0;
    }

    /** {@code G_WorldDone}: called by the intermission when it is over (and by the finale). */
    @VanillaRef("g_game.c:G_WorldDone")
    void worldDone() {
        gameAction = GameAction.WORLDDONE;

        if (secretExit) {
            w.players[w.consolePlayer].didSecret = true;
        }

        if (w.mode == GameMode.COMMERCIAL) {
            switch (w.gameMap) {
                case 15, 31 -> {
                    if (secretExit) {
                        finale.start(); // F_StartFinale (the switch falls through)
                    }
                }
                case 6, 11, 20, 30 -> finale.start();
                default -> {
                }
            }
        }
    }

    /** {@code G_DoWorldDone}. */
    @VanillaRef("g_game.c:G_DoWorldDone")
    private void doWorldDone() {
        gameState = GameState.LEVEL;
        w.gameMap = wminfo.next + 1;
        doLoadLevel();
        gameAction = GameAction.NOTHING;
    }

    // ==================================================================================== save and load

    /**
     * {@code G_DoLoadGame}. A save game that cannot be read is reported to the player and the game goes on; if the
     * failure came after the running game had been replaced, the session returns to the title loop.
     */
    @VanillaRef("g_game.c:G_DoLoadGame")
    private void doLoadGame() {
        gameAction = GameAction.NOTHING;
        byte[] data = loadData;
        loadData = null;
        if (saveGameHandler == null) {
            w.unimplemented.hit("session.loadGame");
            return;
        }
        try {
            saveGameHandler.load(data);
        } catch (doom.game.persist.SaveGameException e) {
            LOG.log(System.Logger.Level.WARNING, "cannot load the save game: " + e.getMessage(), e);
            reportToPlayer("Couldn't load game: " + e.getMessage());
            if (e.worldModified()) {
                abandonGame();
            }
        }
    }

    /** {@code G_DoSaveGame}. */
    @VanillaRef("g_game.c:G_DoSaveGame")
    private void doSaveGame() {
        if (saveGameHandler == null) {
            w.unimplemented.hit("session.saveGame");
            gameAction = GameAction.NOTHING;
            return;
        }
        String description = saveDescription;
        gameAction = GameAction.NOTHING;
        saveDescription = null; // savedescription[0] = 0
        if (gameState != GameState.LEVEL) {
            return; // M_SaveGame only offers saving in a level
        }
        byte[] data;
        try {
            data = saveGameHandler.save(saveSlot, description);
        } catch (doom.game.persist.SaveGameException e) {
            LOG.log(System.Logger.Level.WARNING, "cannot save the game: " + e.getMessage(), e);
            reportToPlayer("Couldn't save game: " + e.getMessage());
            return;
        }
        w.events.gameSaved(saveSlot, description, data);
        reportToPlayer(w.strings.get("GGSAVED"));
    }

    /**
     * The state was replaced by a load that then failed: nothing of the old game is left and the new one is not
     * complete, so go back to the title loop at once (this tic's main action is the title page's).
     */
    private void abandonGame() {
        gameState = GameState.DEMOSCREEN;
        userGame = false;
        Arrays.fill(w.playerInGame, false);
        w.playerInGame[0] = true;
        w.consolePlayer = 0;
        w.displayPlayer = 0;
        w.netGame = false;
        w.deathmatch = 0;
        startTitle();
    }

    private void reportToPlayer(String message) {
        w.players[w.consolePlayer].message = message;
        w.events.playerMessage(w.consolePlayer, message);
    }

    // ==================================================================================== title loop

    /** {@code D_PageTicker}: the pages of the title loop time out and advance the loop. */
    @VanillaRef("d_main.c:D_PageTicker")
    private void pageTicker() {
        if (--pageTic < 0) {
            advanceDemo = true; // D_AdvanceDemo
        }
    }

    /**
     * {@code D_DoAdvanceDemo}: cycles through the demo sequence (TITLEPIC, demo1, CREDIT, demo2, HELP2/CREDIT/TITLEPIC
     * again, demo3, and DEMO4 for Ultimate DOOM and Final DOOM).
     */
    @VanillaRef("d_main.c:D_DoAdvanceDemo")
    private void doAdvanceDemo() {
        w.players[w.consolePlayer].playerState = Player.PST_LIVE; // not reborn
        advanceDemo = false;
        userGame = false; // no save / end game here
        w.paused = false;
        gameAction = GameAction.NOTHING;

        // The Ultimate Doom executable changed the demo sequence to add a DEMO4 demo. Final Doom was based on
        // Ultimate, so also includes this change; however, the Final Doom IWADs do not include a DEMO4 lump, so the
        // game bombs out with an error when it reaches this point in the demo sequence. However! There is an
        // alternate version of Final Doom that includes a fixed executable.
        if (w.version == GameVersion.ULTIMATE || w.version == GameVersion.FINAL) {
            demoSequence = (demoSequence + 1) % 7;
        } else {
            demoSequence = (demoSequence + 1) % 6;
        }

        boolean commercial = w.mode == GameMode.COMMERCIAL;
        switch (demoSequence) {
            case 0 -> {
                pageTic = commercial ? World.TICRATE * 11 : 170;
                gameState = GameState.DEMOSCREEN;
                pageName = w.strings.translate("TITLEPIC");
                w.events.changeMusic(commercial ? MusicId.DM2TTL : MusicId.INTRO, false);
            }
            case 1 -> deferredPlayTitleDemo("demo1");
            case 2 -> {
                pageTic = 200;
                gameState = GameState.DEMOSCREEN;
                pageName = w.strings.translate("CREDIT");
            }
            case 3 -> deferredPlayTitleDemo("demo2");
            case 4 -> {
                gameState = GameState.DEMOSCREEN;
                if (commercial) {
                    pageTic = World.TICRATE * 11;
                    pageName = w.strings.translate("TITLEPIC");
                    w.events.changeMusic(MusicId.DM2TTL, false);
                } else {
                    pageTic = 200;
                    pageName = w.strings.translate(w.version.isAtLeast(GameVersion.ULTIMATE) ? "CREDIT" : "HELP2");
                }
            }
            case 5 -> deferredPlayTitleDemo("demo3");
            case 6 -> deferredPlayTitleDemo("demo4"); // THE DEFINITIVE DOOM Special Edition demo
            default -> {
            }
        }

        // The Doom 3: BFG Edition version of doom2.wad does not have a TITLEPIC lump. Use INTERPIC instead.
        if (w.variant == GameVariant.BFG_EDITION && pageName.equalsIgnoreCase("TITLEPIC")
                && w.lumps.find("titlepic") < 0) {
            pageName = w.strings.translate("INTERPIC");
        }
    }

    /** {@code G_DeferedPlayDemo(DEH_String(name))} for a demo lump of the IWAD. */
    private void deferredPlayTitleDemo(String lumpName) {
        String name = w.strings.translate(lumpName);
        int lump = w.lumps.find(name);
        if (lump < 0) {
            throw new DoomFatalException("W_GetNumForName: " + name + " not found!");
        }
        deferredPlayDemo(name, w.lumps.bytes(lump), DemoPlayback.TITLE);
    }

    // ==================================================================================== demos

    /** {@code G_DoPlayDemo}. */
    @VanillaRef("g_game.c:G_DoPlayDemo")
    private void doPlayDemo() {
        gameAction = GameAction.NOTHING;
        demoBuffer = defDemo;
        demoMode = defDemoMode;
        demoP = 0;

        if (demoBuffer.length < 13) {
            throw new DoomFatalException("Demo " + defDemoName + " is too short");
        }
        int demoVersion = demoBuffer[demoP++] & 0xFF;
        boolean oldDemo = false;
        if (demoVersion <= 4) {
            oldDemo = true;
            demoP--;
        }
        // 110 (linuxdoom) is read as 109 (GameSession contract).
        if (demoVersion != 109 && demoVersion != 110) {
            throw new DoomFatalException("Demo is from a different game version!\n(read " + demoVersion
                    + ", should be 109)\n\n*** You may need to upgrade your version of Doom to v1.9. ***\n"
                    + "    See: https://www.doomworld.com/classicdoom/info/patches.php\n    This appears to be "
                    + demoVersionDescription(demoVersion) + ".");
        }

        int skill = demoBuffer[demoP++] & 0xFF;
        int episode = demoBuffer[demoP++] & 0xFF;
        int map = demoBuffer[demoP++] & 0xFF;
        if (!oldDemo) {
            w.deathmatch = demoBuffer[demoP++] & 0xFF;
            w.respawnParm = demoBuffer[demoP++] != 0;
            w.fastParm = demoBuffer[demoP++] != 0;
            w.noMonsters = demoBuffer[demoP++] != 0;
            w.consolePlayer = demoBuffer[demoP++] & 0xFF;
        } else {
            w.deathmatch = 0;
            w.respawnParm = false;
            w.fastParm = false;
            w.noMonsters = false;
            w.consolePlayer = 0;
        }

        for (int i = 0; i < World.MAXPLAYERS; i++) {
            w.playerInGame[i] = demoBuffer[demoP++] != 0;
        }
        if (w.playerInGame[1]) {
            w.netGame = true;
            w.netDemo = true;
        }

        initNew(skill, episode, map);

        userGame = false;
        w.demoPlayback = true;
    }

    private static String demoVersionDescription(int version) {
        return switch (version) {
            case 104 -> "v1.4";
            case 105 -> "v1.5";
            case 106 -> "v1.6/v1.666";
            case 107 -> "v1.7/v1.7a";
            case 108 -> "v1.8";
            case 109 -> "v1.9";
            case 111 -> "v1.91 hack demo?";
            default -> version <= 4 ? "v1.0/v1.1/v1.2" : (version / 100) + "." + (version % 100) + " (unknown)";
        };
    }

    /** {@code G_ReadDemoTiccmd}. */
    @VanillaRef("g_game.c:G_ReadDemoTiccmd")
    private void readDemoTiccmd(TicCmd cmd) {
        // DIVERGE: a demo without its end marker ends at the end of the data instead of reading past the buffer.
        if (demoP >= demoBuffer.length || (demoBuffer[demoP] & 0xFF) == DEMOMARKER
                || demoP + 4 > demoBuffer.length) {
            // end of demo data stream
            checkDemoStatus();
            return;
        }
        cmd.forwardMove = demoBuffer[demoP++];
        cmd.sideMove = demoBuffer[demoP++];
        cmd.angleTurn = (short) ((demoBuffer[demoP++] & 0xFF) << 8);
        cmd.buttons = demoBuffer[demoP++];
    }

    /**
     * {@code G_CheckDemoStatus}: called when a demo ends. {@code -timedemo} and {@code -playdemo} end the program
     * ({@link #quit}); a title-loop demo advances the loop.
     */
    @VanillaRef("g_game.c:G_CheckDemoStatus")
    public boolean checkDemoStatus() {
        if (timingDemo) {
            timingDemo = false;
            w.demoPlayback = false;
            quit = true;
            w.events.demoFinished(DemoPlayback.TIMEDEMO, w.gametic);
            return true;
        }

        if (w.demoPlayback) {
            DemoPlayback mode = demoMode;
            w.demoPlayback = false;
            w.netDemo = false;
            w.netGame = false;
            w.deathmatch = 0;
            w.playerInGame[1] = w.playerInGame[2] = w.playerInGame[3] = false;
            w.respawnParm = false;
            w.fastParm = false;
            w.noMonsters = false;
            w.consolePlayer = 0;
            demoMode = null;

            if (singleDemo) {
                quit = true;
                w.events.demoFinished(DemoPlayback.SINGLE, w.gametic);
            } else {
                advanceDemo = true; // D_AdvanceDemo
                w.events.demoFinished(mode == null ? DemoPlayback.TITLE : mode, w.gametic);
            }
            return true;
        }

        if (demoRecording) {
            // the recorder hands the bytes out through finishDemoRecording()
            demoRecording = false;
        }
        return false;
    }

    /** {@code G_RecordDemo} + {@code G_BeginRecording} (the game starts at the next tic). */
    @VanillaRef({"g_game.c:G_RecordDemo", "g_game.c:G_BeginRecording"})
    public void recordDemo(String name, GameParams params) {
        userGame = false;
        recordName = name;
        recordBuffer = new byte[0x20000];
        recordP = 0;
        demoRecording = true;
        deferredNewGame(params);
        // the header is written when the game starts (G_BeginRecording after G_InitNew)
        pendingHeader = true;
    }

    private boolean pendingHeader;

    private void writeHeaderIfPending() {
        if (!pendingHeader) {
            return;
        }
        pendingHeader = false;
        recordP = 0;
        recordBuffer[recordP++] = 109;
        recordBuffer[recordP++] = (byte) w.gameSkill;
        recordBuffer[recordP++] = (byte) w.gameEpisode;
        recordBuffer[recordP++] = (byte) w.gameMap;
        recordBuffer[recordP++] = (byte) w.deathmatch;
        recordBuffer[recordP++] = (byte) (w.respawnParm ? 1 : 0);
        recordBuffer[recordP++] = (byte) (w.fastParm ? 1 : 0);
        recordBuffer[recordP++] = (byte) (w.noMonsters ? 1 : 0);
        recordBuffer[recordP++] = (byte) w.consolePlayer;
        for (int i = 0; i < World.MAXPLAYERS; i++) {
            recordBuffer[recordP++] = (byte) (w.playerInGame[i] ? 1 : 0);
        }
    }

    /** {@code G_WriteDemoTiccmd}: records the command and re-reads it, so the turn is quantized like the original. */
    @VanillaRef("g_game.c:G_WriteDemoTiccmd")
    private void writeDemoTiccmd(TicCmd cmd) {
        writeHeaderIfPending();
        if (recordP + 16 > recordBuffer.length) {
            recordBuffer = Arrays.copyOf(recordBuffer, recordBuffer.length * 2);
        }
        int start = recordP;
        recordBuffer[recordP++] = cmd.forwardMove;
        recordBuffer[recordP++] = cmd.sideMove;
        recordBuffer[recordP++] = (byte) (cmd.angleTurn >> 8);
        recordBuffer[recordP++] = cmd.buttons;
        // make SURE it is exactly the same
        cmd.forwardMove = recordBuffer[start];
        cmd.sideMove = recordBuffer[start + 1];
        cmd.angleTurn = (short) ((recordBuffer[start + 2] & 0xFF) << 8);
        cmd.buttons = recordBuffer[start + 3];
    }

    /** Stops recording and returns the .lmp bytes (ending with the 0x80 marker), or null if not recording. */
    public byte[] finishDemoRecording() {
        if (!demoRecording && recordBuffer == null) {
            return null;
        }
        writeHeaderIfPending();
        byte[] out = Arrays.copyOf(recordBuffer, recordP + 1);
        out[recordP] = (byte) DEMOMARKER;
        demoRecording = false;
        recordBuffer = null;
        return out;
    }

    /** Name given to {@link #recordDemo}. */
    public String recordName() {
        return recordName;
    }

    /** Name of the demo being played, or null. */
    public String demoName() {
        return defDemoName;
    }

    /** The previous {@code gamestate} ({@code oldgamestate}). */
    public GameState oldGameState() {
        return oldGameState;
    }
}
