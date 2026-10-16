package doom.game.persist;

import doom.game.actor.Actor;
import doom.game.actor.SpecialThinker;
import doom.game.actor.Thinker;
import doom.game.data.MobjType;
import doom.game.data.State;
import doom.game.data.StateId;
import doom.game.data.ThingType;
import doom.game.player.Player;
import doom.game.view.AmmoType;
import doom.game.view.Card;
import doom.game.view.Power;
import doom.game.view.Weapon;
import doom.game.world.Level;
import doom.game.world.Line;
import doom.game.world.Sector;
import doom.game.world.Side;
import doom.game.world.SpawnPoint;
import doom.game.world.World;
import java.util.Arrays;

/**
 * Overlays a snapshot on a freshly loaded level ({@link SnapshotWriter} is the inverse). The caller has already run
 * {@code G_InitNew} for the saved map, so the geometry, the blockmap and the definition tables are in place and only
 * the state that the original game changes while playing is replaced.
 */
final class SnapshotReader {

    private final World w;
    private final SaveFormat.Parsed save;
    private final SessionData session;

    /** The thinker table of the THNK section. */
    private Thinker[] table;
    /** Table entries that are map objects (live or only referenced). */
    private Actor[] actors;
    private int live;

    SnapshotReader(World w, SaveFormat.Parsed save, SessionData session) {
        this.w = w;
        this.save = save;
        this.session = session;
    }

    /** The dimensions at the start of the WRLD section: sectors, lines, sides, blockmap columns and rows. */
    static int[] worldDimensions(SaveFormat.Parsed save) {
        ByteSource s = save.require(SaveFormat.WORLD);
        return new int[] {s.getRange(0, 65535, "sector count"), s.getRange(0, 65535, "line count"),
            s.getRange(0, 65535, "side count"), s.getRange(0, 65535, "blockmap columns"),
            s.getRange(0, 65535, "blockmap rows")};
    }

    /** Replaces the state of the running level with the snapshot. */
    void apply() {
        Level level = w.level;
        int[] dims = worldDimensions(save);
        if (dims[0] != level.sectors.length || dims[1] != level.lines.length || dims[2] != level.sides.length
                || dims[3] != level.blockmap.columns || dims[4] != level.blockmap.rows) {
            throw new SaveGameException("The save game does not match the map " + level.name
                    + " (the WADs are different)");
        }

        releaseCurrentLevelObjects(level);
        readThinkers();
        readSpecials();
        readWorld(level);
        readPlayers();
        applySession();
        snapInterpolation(level);
    }

    // ------------------------------------------------------------------ the objects of the freshly loaded level

    private void releaseCurrentLevelObjects(Level level) {
        for (Thinker t = w.thinkers.first(); t != null; t = w.thinkers.next(t)) {
            if (t instanceof Actor a && a.id >= 0) {
                w.events.stopSound(a.id);
                w.actorIds.release(a);
            }
        }
        w.thinkers.clear();
        for (Sector sec : level.sectors) {
            sec.thingList = null;
            sec.soundTarget = null;
            sec.specialData = null;
        }
        for (Line line : level.lines) {
            line.specialData = null;
        }
        Arrays.fill(w.blockLinks, null);
        for (Player p : w.players) {
            p.mo = null;
            p.attacker = null;
        }
        Arrays.fill(w.bodyQue, null);
    }

    // ------------------------------------------------------------------ THNK

    private void readThinkers() {
        ByteSource s = save.require(SaveFormat.THINKERS);
        int n = s.getCount(4);
        live = s.getRange(0, n, "live thinker count");
        table = new Thinker[n];
        actors = new Actor[n];
        int[] targetRef = new int[n];
        int[] tracerRef = new int[n];
        State[] states = w.defs.states();
        ThingType[] things = w.defs.things();
        Level level = w.level;

        for (int i = 0; i < n; i++) {
            int kind = s.getInt();
            boolean wantLive = i < live;
            switch (kind) {
                case SnapshotWriter.KIND_ACTOR, SnapshotWriter.KIND_GHOST_ACTOR -> {
                    if (wantLive != (kind == SnapshotWriter.KIND_ACTOR)) {
                        throw corrupt("thinker table order");
                    }
                    Actor a = new Actor(w.mobjs);
                    a.type = s.getRange(0, MobjType.COUNT - 1, "object type");
                    a.info = things[a.type];
                    a.x = s.getInt();
                    a.y = s.getInt();
                    a.z = s.getInt();
                    a.angle = s.getInt();
                    a.sprite = s.getInt();
                    a.frame = s.getInt();
                    a.floorZ = s.getInt();
                    a.ceilingZ = s.getInt();
                    a.radius = s.getInt();
                    a.height = s.getInt();
                    a.momX = s.getInt();
                    a.momY = s.getInt();
                    a.momZ = s.getInt();
                    a.validCount = s.getInt();
                    a.tics = s.getInt();
                    int stateNum = s.getRange(-1, StateId.COUNT - 1, "object state");
                    a.state = stateNum < 0 ? null : states[stateNum];
                    a.stateNum = stateNum < 0 ? StateId.NULL : stateNum;
                    a.flags = s.getInt();
                    a.health = s.getInt();
                    a.moveDir = s.getInt();
                    a.moveCount = s.getInt();
                    targetRef[i] = s.getRange(-1, n - 1, "target");
                    a.reactionTime = s.getInt();
                    a.threshold = s.getInt();
                    int playerNum = s.getRange(-1, World.MAXPLAYERS - 1, "object player");
                    a.player = playerNum < 0 ? null : w.players[playerNum];
                    a.lastLook = s.getInt();
                    SpawnPoint sp = a.spawnPoint;
                    sp.x = s.getInt();
                    sp.y = s.getInt();
                    sp.angle = s.getInt();
                    sp.type = s.getInt();
                    sp.options = s.getInt();
                    tracerRef[i] = s.getRange(-1, n - 1, "tracer");
                    int subsector = s.getRange(wantLive ? 0 : -1, level.subsectors.length - 1, "subsector");
                    a.subsector = subsector < 0 ? null : level.subsectors[subsector];
                    table[i] = a;
                    actors[i] = a;
                }
                case SnapshotWriter.KIND_SPECIAL, SnapshotWriter.KIND_GHOST_SPECIAL -> {
                    if (wantLive != (kind == SnapshotWriter.KIND_SPECIAL)) {
                        throw corrupt("thinker table order");
                    }
                    int length = s.getCount(4);
                    int[] state = new int[length];
                    for (int k = 0; k < length; k++) {
                        state[k] = s.getInt();
                    }
                    try {
                        table[i] = w.specials.restoreThinker(state);
                    } catch (IllegalArgumentException e) {
                        throw new SaveGameException("Save game is corrupt (" + e.getMessage() + ")", e);
                    }
                }
                default -> throw corrupt("thinker kind " + kind);
            }
        }
        s.expectEnd();

        for (int i = 0; i < n; i++) {
            if (actors[i] != null) {
                actors[i].target = targetRef[i] < 0 ? null : actorAt(targetRef[i]);
                actors[i].tracer = tracerRef[i] < 0 ? null : actorAt(tracerRef[i]);
            }
        }
        for (int i = 0; i < live; i++) {
            w.thinkers.add(table[i]);
            if (actors[i] != null) {
                w.actorIds.register(actors[i]);
            }
        }
        for (int i = live; i < n; i++) {
            w.thinkers.remove(table[i]); // referenced but gone from the list: removed objects, without an id
        }
    }

    private Actor actorAt(int ref) {
        if (ref < 0 || ref >= actors.length || actors[ref] == null) {
            throw corrupt("reference " + ref + " is not a map object");
        }
        return actors[ref];
    }

    private Actor actorOrNull(int ref) {
        return ref < 0 ? null : actorAt(ref);
    }

    private SpecialThinker specialOrNull(int ref) {
        if (ref < 0) {
            return null;
        }
        if (ref >= table.length || !(table[ref] instanceof SpecialThinker t)) {
            throw corrupt("reference " + ref + " is not a sector effect");
        }
        return t;
    }

    private static SaveGameException corrupt(String what) {
        return new SaveGameException("Save game is corrupt (" + what + ")");
    }

    // ------------------------------------------------------------------ WRLD

    private void readWorld(Level level) {
        ByteSource s = save.require(SaveFormat.WORLD);
        for (int i = 0; i < 5; i++) {
            s.getInt(); // dimensions, already checked
        }
        for (Sector sec : level.sectors) {
            sec.floorHeight = s.getInt();
            sec.ceilingHeight = s.getInt();
            sec.floorPic = s.getInt();
            sec.ceilingPic = s.getInt();
            sec.lightLevel = s.getInt();
            sec.special = s.getInt();
            sec.tag = s.getInt();
            sec.soundTraversed = s.getInt();
            sec.soundTarget = actorOrNull(s.getInt());
            sec.specialData = specialOrNull(s.getInt());
        }
        for (Line line : level.lines) {
            line.flags = s.getInt();
            line.special = s.getInt();
            line.tag = s.getInt();
            line.specialData = specialOrNull(s.getInt());
        }
        for (Side side : level.sides) {
            side.textureOffset = s.getInt();
            side.rowOffset = s.getInt();
            side.topTexture = s.getInt();
            side.bottomTexture = s.getInt();
            side.midTexture = s.getInt();
        }

        boolean[] inSector = new boolean[table.length];
        for (Sector sec : level.sectors) {
            int count = s.getCount(4);
            Actor prev = null;
            for (int k = 0; k < count; k++) {
                int ref = s.getInt();
                Actor a = actorAt(ref);
                if (inSector[ref]) {
                    throw corrupt("object listed in two sectors");
                }
                inSector[ref] = true;
                a.sprev = prev;
                if (prev == null) {
                    sec.thingList = a;
                } else {
                    prev.snext = a;
                }
                prev = a;
            }
        }

        int cells = level.blockmap.columns * level.blockmap.rows;
        if (w.blockLinks.length != cells) {
            throw corrupt("blockmap size");
        }
        boolean[] inBlock = new boolean[table.length];
        int nonEmpty = s.getRange(0, cells, "blockmap cell count");
        int lastCell = -1;
        for (int c = 0; c < nonEmpty; c++) {
            int cell = s.getRange(lastCell + 1, cells - 1, "blockmap cell");
            lastCell = cell;
            int count = s.getCount(4);
            Actor prev = null;
            for (int k = 0; k < count; k++) {
                int ref = s.getInt();
                Actor a = actorAt(ref);
                if (inBlock[ref]) {
                    throw corrupt("object listed in two blockmap cells");
                }
                inBlock[ref] = true;
                a.bprev = prev;
                if (prev == null) {
                    w.blockLinks[cell] = a;
                } else {
                    prev.bnext = a;
                }
                prev = a;
            }
        }

        expect(s, World.BODYQUESIZE, "body queue size");
        for (int i = 0; i < World.BODYQUESIZE; i++) {
            w.bodyQue[i] = actorOrNull(s.getInt());
        }
        w.bodyQueSlot = s.getInt();

        for (int i = 0; i < World.MAXPLAYERS; i++) {
            getSpawnPoint(s, w.playerStarts[i]);
            w.playerStartsInGame[i] = s.getBool();
        }
        expect(s, World.MAX_DM_STARTS, "deathmatch start count");
        w.deathmatchCount = s.getRange(0, World.MAX_DM_STARTS, "deathmatch starts");
        for (SpawnPoint p : w.deathmatchStarts) {
            getSpawnPoint(s, p);
        }

        expect(s, World.ITEMQUESIZE, "item respawn queue size");
        w.iqueHead = s.getRange(0, World.ITEMQUESIZE - 1, "item queue head");
        w.iqueTail = s.getRange(0, World.ITEMQUESIZE - 1, "item queue tail");
        for (int i = 0; i < World.ITEMQUESIZE; i++) {
            getSpawnPoint(s, w.itemRespawnQue[i]);
            w.itemRespawnTime[i] = s.getInt();
        }
        s.expectEnd();
    }

    private static void getSpawnPoint(ByteSource s, SpawnPoint p) {
        p.x = s.getInt();
        p.y = s.getInt();
        p.angle = s.getInt();
        p.type = s.getInt();
        p.options = s.getInt();
    }

    private static void expect(ByteSource s, int value, String what) {
        int v = s.getInt();
        if (v != value) {
            throw corrupt(what + " " + v);
        }
    }

    // ------------------------------------------------------------------ SPEC

    private void readSpecials() {
        ByteSource s = save.require(SaveFormat.SPECIALS);
        int nc = s.getCount(4);
        SpecialThinker[] ceilings = new SpecialThinker[nc];
        for (int i = 0; i < nc; i++) {
            ceilings[i] = specialOrNull(s.getInt());
        }
        int np = s.getCount(4);
        SpecialThinker[] plats = new SpecialThinker[np];
        for (int i = 0; i < np; i++) {
            plats[i] = specialOrNull(s.getInt());
        }
        int nb = s.getCount(4);
        int[] buttons = new int[nb];
        for (int i = 0; i < nb; i++) {
            buttons[i] = s.getInt();
        }
        int nt = s.getCount(4);
        Actor[] targets = new Actor[nt];
        for (int i = 0; i < nt; i++) {
            targets[i] = actorAt(s.getInt());
        }
        int targetOn = s.getInt();
        int easy = s.getInt();
        s.expectEnd();
        try {
            w.specials.restoreActiveSpecials(ceilings, plats);
            w.specials.restoreButtons(buttons);
        } catch (IllegalArgumentException | ClassCastException e) {
            throw new SaveGameException("Save game is corrupt (" + e.getMessage() + ")", e);
        }
        w.ai.restoreBrain(targets, targetOn, easy);
    }

    // ------------------------------------------------------------------ PLYR

    private void readPlayers() {
        ByteSource s = save.require(SaveFormat.PLAYERS);
        expect(s, World.MAXPLAYERS, "player count");
        expect(s, Power.COUNT, "power count");
        expect(s, Card.COUNT, "card count");
        expect(s, Weapon.COUNT, "weapon count");
        expect(s, AmmoType.COUNT, "ammo type count");
        State[] states = w.defs.states();
        for (Player p : w.players) {
            p.mo = actorOrNull(s.getInt());
            p.playerState = s.getRange(Player.PST_LIVE, Player.PST_REBORN, "player state");
            p.viewZ = s.getInt();
            p.viewHeight = s.getInt();
            p.deltaViewHeight = s.getInt();
            p.bob = s.getInt();
            p.health = s.getInt();
            p.armorPoints = s.getInt();
            p.armorType = s.getInt();
            for (int i = 0; i < Power.COUNT; i++) {
                p.powers[i] = s.getInt();
            }
            for (int i = 0; i < Card.COUNT; i++) {
                p.cards[i] = s.getBool();
            }
            p.backpack = s.getBool();
            for (int i = 0; i < p.frags.length; i++) {
                p.frags[i] = s.getInt();
            }
            p.readyWeapon = s.getRange(0, Weapon.COUNT - 1, "ready weapon");
            p.pendingWeapon = s.getRange(0, Weapon.NO_CHANGE, "pending weapon");
            for (int i = 0; i < Weapon.COUNT; i++) {
                p.weaponOwned[i] = s.getBool();
            }
            for (int i = 0; i < AmmoType.COUNT; i++) {
                p.ammo[i] = s.getInt();
            }
            for (int i = 0; i < AmmoType.COUNT; i++) {
                p.maxAmmo[i] = s.getInt();
            }
            p.attackDown = s.getBool();
            p.useDown = s.getBool();
            p.cheats = s.getInt();
            p.refire = s.getInt();
            p.killCount = s.getInt();
            p.itemCount = s.getInt();
            p.secretCount = s.getInt();
            p.damageCount = s.getInt();
            p.bonusCount = s.getInt();
            p.attacker = actorOrNull(s.getInt());
            p.extraLight = s.getInt();
            p.fixedColormap = s.getInt();
            p.colormap = s.getInt();
            for (int i = 0; i < Player.NUMPSPRITES; i++) {
                int stateNum = s.getRange(-1, StateId.COUNT - 1, "player sprite state");
                p.psprites[i].state = stateNum < 0 ? null : states[stateNum];
                p.psprites[i].stateNum = stateNum;
                p.psprites[i].tics = s.getInt();
                p.psprites[i].sx = s.getInt();
                p.psprites[i].sy = s.getInt();
            }
            p.didSecret = s.getBool();
            p.message = null;
            p.cmd.clear();
            if (w.playerInGame[p.index] && (p.mo == null || p.mo.player != p)) {
                throw corrupt("player " + (p.index + 1) + " has no body");
            }
        }
        s.expectEnd();
    }

    // ------------------------------------------------------------------ SESS

    private void applySession() {
        SessionData d = session;
        w.gametic = d.gametic;
        w.levelTime = d.levelTime;
        w.totalKills = d.totalKills;
        w.totalItems = d.totalItems;
        w.totalSecret = d.totalSecret;
        w.rng.setIndices(d.rngGame, d.rngMisc);
        w.respawnMonsters = d.respawnMonsters;
        w.displayPlayer = d.displayPlayer;
        w.skyTexture = d.skyTexture;
        w.skyFlatNum = d.skyFlatNum;
        w.validCount = d.validCount;
        w.playerSim.setOnGround(d.onGround);
        State[] states = w.defs.states();
        for (int i = 0; i < SessionData.SARG_STATES; i++) {
            states[StateId.SARG_RUN1 + i].tics = d.sargTics[i];
        }
        ThingType[] things = w.defs.things();
        things[MobjType.BRUISERSHOT].speed = d.bruiserShotSpeed;
        things[MobjType.HEADSHOT].speed = d.headShotSpeed;
        things[MobjType.TROOPSHOT].speed = d.troopShotSpeed;
    }

    // ------------------------------------------------------------------ presentation

    /** The loaded state is the start of the interpolation: nothing moves between "before" and "after". */
    private void snapInterpolation(Level level) {
        for (Actor a : actors) {
            if (a != null) {
                a.snapInterpolation();
            }
        }
        for (Sector sec : level.sectors) {
            sec.prevFloorHeight = sec.floorHeight;
            sec.prevCeilingHeight = sec.ceilingHeight;
        }
        for (Side side : level.sides) {
            side.prevTextureOffset = side.textureOffset;
        }
        for (Player p : w.players) {
            p.prevViewZ = p.viewZ;
            for (var ps : p.psprites) {
                ps.prevSx = ps.sx;
                ps.prevSy = ps.sy;
            }
        }
    }
}
