package doom.game.world;

import doom.game.actor.ActionDispatcher;
import doom.game.actor.Actor;
import doom.game.actor.ActorRegistry;
import doom.game.actor.Mobjs;
import doom.game.actor.ThinkerList;
import doom.game.ai.MonsterAi;
import doom.game.api.SimEvents;
import doom.game.api.SoundOrigins;
import doom.game.combat.Combat;
import doom.game.data.GameDefs;
import doom.game.data.GameStrings;
import doom.game.mapspecial.MapSpecials;
import doom.game.physics.Physics;
import doom.game.physics.Sight;
import doom.game.player.Pickups;
import doom.game.player.PlayerSim;
import doom.game.player.Player;
import doom.game.player.Weapons;
import doom.math.Rng;
import doom.wad.LumpDirectory;
import doom.wad.iwad.GameMission;
import doom.wad.iwad.GameMode;
import doom.wad.iwad.GameVariant;
import doom.wad.iwad.GameVersion;
import doom.wad.texture.FlatDirectory;
import doom.wad.texture.TextureDirectory;

/**
 * The simulation state of one session: what the original keeps in globals ({@code doomstat.c}, {@code g_game.c},
 * {@code p_*.c}). One instance per {@link doom.game.api.GameSession}; the rule areas receive it at construction and
 * reach each other through the service fields ({@link #mobjs}, {@link #physics}, {@link #ai}, ...), which are wired
 * once by the session factory.
 */
public final class World {

    public static final int MAXPLAYERS = 4;
    /** {@code TICRATE}. */
    public static final int TICRATE = 35;
    /** {@code BODYQUESIZE}. */
    public static final int BODYQUESIZE = 32;
    /** {@code ITEMQUESIZE}. */
    public static final int ITEMQUESIZE = 128;
    /** {@code MAX_DM_STARTS}. */
    public static final int MAX_DM_STARTS = 10;

    // ------------------------------------------------------------------ session constants

    public final LumpDirectory lumps;
    public final GameMode mode;
    public final GameMission mission;
    public final GameVariant variant;
    public final GameVersion version;
    public final GameDefs defs;
    public final GameStrings strings;
    public final TextureDirectory textures;
    public final FlatDirectory flats;
    /** Never null (a no-op implementation when the setup has none). */
    public final SimEvents events;
    public final Rng rng = new Rng();
    public final Unimplemented unimplemented = new Unimplemented(this);

    // ------------------------------------------------------------------ services (wired by the session factory)

    public ActorRegistry actorIds;
    public Mobjs mobjs;
    public ActionDispatcher actions;
    public Physics physics;
    public Sight sight;
    public PlayerSim playerSim;
    public Weapons weapons;
    public Pickups pickups;
    public MonsterAi ai;
    public Combat combat;
    public MapSpecials specials;
    public GameFlow flow;

    // ------------------------------------------------------------------ game parameters (g_game.c / doomstat.c)

    /** {@code gameskill}, 0-based ({@code sk_baby} = 0). */
    public int gameSkill;
    public int gameEpisode;
    public int gameMap;
    public boolean netGame;
    public boolean netDemo;
    /** 0 coop/single, 1 deathmatch, 2 altdeath. */
    public int deathmatch;
    public boolean respawnParm;
    public boolean fastParm;
    public boolean noMonsters;
    /** {@code respawnmonsters}: Nightmare or {@code -respawn}. */
    public boolean respawnMonsters;
    public int consolePlayer;
    public int displayPlayer;
    public final boolean[] playerInGame = new boolean[MAXPLAYERS];
    public final Player[] players = new Player[MAXPLAYERS];
    public boolean paused;
    public boolean demoPlayback;
    /** Number of completed {@code G_Ticker} calls; equals the tic being run during a tick. */
    public int gametic;

    // ------------------------------------------------------------------ level state

    public Level level;
    /** {@code blocklinks}: first thing of each blockmap cell. */
    public Actor[] blockLinks = new Actor[0];
    public int validCount = 1;
    public final ThinkerList thinkers = new ThinkerList();
    public int levelTime;
    public int totalKills;
    public int totalItems;
    public int totalSecret;
    public int skyFlatNum;
    public int skyTexture;

    /** {@code playerstarts[]} and {@code playerstartsingame[]}. */
    public final SpawnPoint[] playerStarts = new SpawnPoint[MAXPLAYERS];
    public final boolean[] playerStartsInGame = new boolean[MAXPLAYERS];
    /** {@code deathmatchstarts[]}, {@code deathmatch_p - deathmatchstarts}. */
    public final SpawnPoint[] deathmatchStarts = new SpawnPoint[MAX_DM_STARTS];
    public int deathmatchCount;
    /** {@code bodyque[]}, {@code bodyqueslot}. */
    public final Actor[] bodyQue = new Actor[BODYQUESIZE];
    public int bodyQueSlot;
    /** {@code itemrespawnque[]}, {@code itemrespawntime[]}, {@code iquehead}, {@code iquetail}. */
    public final SpawnPoint[] itemRespawnQue = new SpawnPoint[ITEMQUESIZE];
    public final int[] itemRespawnTime = new int[ITEMQUESIZE];
    public int iqueHead;
    public int iqueTail;

    public World(LumpDirectory lumps, GameMode mode, GameMission mission, GameVariant variant, GameVersion version,
            GameDefs defs, GameStrings strings, TextureDirectory textures, FlatDirectory flats, SimEvents events) {
        this.lumps = lumps;
        this.mode = mode;
        this.mission = mission;
        this.variant = variant;
        this.version = version;
        this.defs = defs;
        this.strings = strings;
        this.textures = textures;
        this.flats = flats;
        this.events = events != null ? events : new SimEvents() {
        };
        for (int i = 0; i < MAXPLAYERS; i++) {
            players[i] = new Player(i);
            playerStarts[i] = new SpawnPoint();
        }
        for (int i = 0; i < MAX_DM_STARTS; i++) {
            deathmatchStarts[i] = new SpawnPoint();
        }
        for (int i = 0; i < ITEMQUESIZE; i++) {
            itemRespawnQue[i] = new SpawnPoint();
        }
    }

    /** {@code S_StartSound(origin, sfx)} with an actor origin (null = no origin). */
    public void startSound(Actor origin, int sfx) {
        events.startSound(origin == null ? -1 : origin.id, sfx);
    }

    /** {@code S_StartSound(&sec->soundorg, sfx)}. */
    public void startSectorSound(Sector sector, int sfx) {
        events.startSound(SoundOrigins.sector(sector.index), sfx);
    }

    /** Whether the game mode is {@code commercial} (DOOM II, Final DOOM). */
    public boolean commercial() {
        return mode == GameMode.COMMERCIAL;
    }
}
