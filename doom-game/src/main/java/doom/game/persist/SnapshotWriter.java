package doom.game.persist;

import doom.game.actor.Actor;
import doom.game.actor.SpecialThinker;
import doom.game.actor.Thinker;
import doom.game.data.MobjType;
import doom.game.data.StateId;
import doom.game.player.Player;
import doom.game.session.Game;
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
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Takes the snapshot: the whole state of the running level as one {@code byte[]} (see {@link SaveFormat}). Runs
 * synchronously inside the tic, reads the state and never changes it.
 *
 * <p>References between objects are indices into the thinker table of the THNK section: first every live thinker in
 * list order, then the map objects that are only referenced (a removed target, for instance) so that the loaded game
 * sees exactly the stale objects the running game sees.
 */
final class SnapshotWriter {

    /** Number of ints of a map object record. */
    static final int ACTOR_INTS = 33;
    /** Kinds of thinker table entries. */
    static final int KIND_ACTOR = 0;
    static final int KIND_SPECIAL = 1;
    static final int KIND_GHOST_ACTOR = 2;
    static final int KIND_GHOST_SPECIAL = 3;

    private final World w;
    private final Game game;

    private final List<Thinker> table = new ArrayList<>();
    private final IdentityHashMap<Thinker, Integer> index = new IdentityHashMap<>();
    private int liveCount;

    SnapshotWriter(World w, Game game) {
        this.w = w;
        this.game = game;
    }

    byte[] write(SaveFormat.Header header) {
        Level level = w.level;
        if (level == null) {
            throw new SaveGameException("There is no level to save");
        }
        for (Thinker t = w.thinkers.first(); t != null; t = w.thinkers.next(t)) {
            if (!t.isRemoved()) {
                index.put(t, table.size());
                table.add(t);
            }
        }
        liveCount = table.size();

        ByteSink session = session();
        ByteSink players = players();
        ByteSink world = world(level);
        ByteSink specials = specials();
        ByteSink thinkers = thinkers(); // last: it also writes the objects discovered by the other sections

        ByteSink out = new ByteSink(session.size() + players.size() + world.size() + thinkers.size()
                + specials.size() + 256);
        SaveFormat.writeHeader(out, header);
        out.putSection(SaveFormat.SESSION, session);
        out.putSection(SaveFormat.PLAYERS, players);
        out.putSection(SaveFormat.WORLD, world);
        out.putSection(SaveFormat.THINKERS, thinkers);
        out.putSection(SaveFormat.SPECIALS, specials);
        ByteSink crc = new ByteSink(4);
        crc.putInt(out.crc32());
        out.putSection(SaveFormat.CRC, crc);
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ references

    private int ref(Thinker t) {
        if (t == null) {
            return -1;
        }
        Integer i = index.get(t);
        if (i == null) {
            i = table.size();
            index.put(t, i);
            table.add(t);
        }
        return i;
    }

    // ------------------------------------------------------------------ SESS

    private ByteSink session() {
        ByteSink s = new ByteSink(512);
        s.putInt(w.gameSkill);
        s.putInt(w.gameEpisode);
        s.putInt(w.gameMap);
        s.putInt(w.gametic);
        s.putInt(w.levelTime);
        s.putInt(game.levelStartTic);
        s.putInt(w.totalKills);
        s.putInt(w.totalItems);
        s.putInt(w.totalSecret);
        s.putInt(w.rng.gameIndex());
        s.putInt(w.rng.miscIndex());
        s.putBool(w.netGame);
        s.putInt(w.deathmatch);
        s.putBool(w.respawnParm);
        s.putBool(w.fastParm);
        s.putBool(w.noMonsters);
        s.putBool(w.respawnMonsters);
        s.putInt(w.consolePlayer);
        s.putInt(w.displayPlayer);
        for (int i = 0; i < World.MAXPLAYERS; i++) {
            s.putBool(w.playerInGame[i]);
        }
        s.putInt(w.skyTexture);
        s.putInt(w.skyFlatNum);
        s.putInt(w.validCount);
        s.putBool(w.playerSim.onGround());
        // -fast / Nightmare change the definition tables relative to the previous skill: store the result
        int states = StateId.SARG_PAIN2 - StateId.SARG_RUN1 + 1;
        s.putInt(states);
        for (int i = StateId.SARG_RUN1; i <= StateId.SARG_PAIN2; i++) {
            s.putInt(w.defs.states()[i].tics);
        }
        s.putInt(w.defs.things()[MobjType.BRUISERSHOT].speed);
        s.putInt(w.defs.things()[MobjType.HEADSHOT].speed);
        s.putInt(w.defs.things()[MobjType.TROOPSHOT].speed);
        return s;
    }

    // ------------------------------------------------------------------ PLYR

    private ByteSink players() {
        ByteSink s = new ByteSink(2048);
        s.putInt(World.MAXPLAYERS);
        s.putInt(Power.COUNT);
        s.putInt(Card.COUNT);
        s.putInt(Weapon.COUNT);
        s.putInt(AmmoType.COUNT);
        for (Player p : w.players) {
            s.putInt(ref(p.mo));
            s.putInt(p.playerState);
            s.putInt(p.viewZ);
            s.putInt(p.viewHeight);
            s.putInt(p.deltaViewHeight);
            s.putInt(p.bob);
            s.putInt(p.health);
            s.putInt(p.armorPoints);
            s.putInt(p.armorType);
            for (int v : p.powers) {
                s.putInt(v);
            }
            for (boolean v : p.cards) {
                s.putBool(v);
            }
            s.putBool(p.backpack);
            for (int v : p.frags) {
                s.putInt(v);
            }
            s.putInt(p.readyWeapon);
            s.putInt(p.pendingWeapon);
            for (boolean v : p.weaponOwned) {
                s.putBool(v);
            }
            for (int v : p.ammo) {
                s.putInt(v);
            }
            for (int v : p.maxAmmo) {
                s.putInt(v);
            }
            s.putBool(p.attackDown);
            s.putBool(p.useDown);
            s.putInt(p.cheats);
            s.putInt(p.refire);
            s.putInt(p.killCount);
            s.putInt(p.itemCount);
            s.putInt(p.secretCount);
            s.putInt(p.damageCount);
            s.putInt(p.bonusCount);
            s.putInt(ref(p.attacker));
            s.putInt(p.extraLight);
            s.putInt(p.fixedColormap);
            s.putInt(p.colormap);
            for (int i = 0; i < Player.NUMPSPRITES; i++) {
                s.putInt(p.psprites[i].state == null ? -1 : p.psprites[i].stateNum);
                s.putInt(p.psprites[i].tics);
                s.putInt(p.psprites[i].sx);
                s.putInt(p.psprites[i].sy);
            }
            s.putBool(p.didSecret);
        }
        return s;
    }

    // ------------------------------------------------------------------ WRLD

    private ByteSink world(Level level) {
        ByteSink s = new ByteSink(1 << 16);
        int columns = level.blockmap.columns;
        int rows = level.blockmap.rows;
        s.putInt(level.sectors.length);
        s.putInt(level.lines.length);
        s.putInt(level.sides.length);
        s.putInt(columns);
        s.putInt(rows);

        for (Sector sec : level.sectors) {
            s.putInt(sec.floorHeight);
            s.putInt(sec.ceilingHeight);
            s.putInt(sec.floorPic);
            s.putInt(sec.ceilingPic);
            s.putInt(sec.lightLevel);
            s.putInt(sec.special);
            s.putInt(sec.tag);
            s.putInt(sec.soundTraversed);
            s.putInt(ref(sec.soundTarget));
            s.putInt(ref(sec.specialData));
        }
        for (Line line : level.lines) {
            s.putInt(line.flags);
            s.putInt(line.special);
            s.putInt(line.tag);
            s.putInt(ref(line.specialData));
        }
        for (Side side : level.sides) {
            s.putInt(side.textureOffset);
            s.putInt(side.rowOffset);
            s.putInt(side.topTexture);
            s.putInt(side.bottomTexture);
            s.putInt(side.midTexture);
        }

        // thing lists of the sectors, in snext order (the order is part of the simulation)
        for (Sector sec : level.sectors) {
            List<Actor> chain = new ArrayList<>();
            int guard = 0;
            for (Actor a = sec.thingList; a != null; a = a.snext) {
                chain.add(a);
                if (++guard > 1_000_000) {
                    throw new SaveGameException("Sector thing list is corrupt");
                }
            }
            s.putInt(chain.size());
            for (Actor a : chain) {
                s.putInt(ref(a));
            }
        }

        // blockmap thing lists, in bnext order; empty cells are not written
        int cells = columns * rows;
        int nonEmpty = 0;
        for (int c = 0; c < cells && c < w.blockLinks.length; c++) {
            if (w.blockLinks[c] != null) {
                nonEmpty++;
            }
        }
        s.putInt(nonEmpty);
        for (int c = 0; c < cells && c < w.blockLinks.length; c++) {
            if (w.blockLinks[c] == null) {
                continue;
            }
            List<Actor> chain = new ArrayList<>();
            int guard = 0;
            for (Actor a = w.blockLinks[c]; a != null; a = a.bnext) {
                chain.add(a);
                if (++guard > 1_000_000) {
                    throw new SaveGameException("Blockmap thing list is corrupt");
                }
            }
            s.putInt(c);
            s.putInt(chain.size());
            for (Actor a : chain) {
                s.putInt(ref(a));
            }
        }

        s.putInt(World.BODYQUESIZE);
        for (Actor a : w.bodyQue) {
            s.putInt(ref(a));
        }
        s.putInt(w.bodyQueSlot);

        for (int i = 0; i < World.MAXPLAYERS; i++) {
            putSpawnPoint(s, w.playerStarts[i]);
            s.putBool(w.playerStartsInGame[i]);
        }
        s.putInt(World.MAX_DM_STARTS);
        s.putInt(w.deathmatchCount);
        for (SpawnPoint p : w.deathmatchStarts) {
            putSpawnPoint(s, p);
        }

        s.putInt(World.ITEMQUESIZE);
        s.putInt(w.iqueHead);
        s.putInt(w.iqueTail);
        for (int i = 0; i < World.ITEMQUESIZE; i++) {
            putSpawnPoint(s, w.itemRespawnQue[i]);
            s.putInt(w.itemRespawnTime[i]);
        }
        return s;
    }

    private static void putSpawnPoint(ByteSink s, SpawnPoint p) {
        s.putInt(p.x);
        s.putInt(p.y);
        s.putInt(p.angle);
        s.putInt(p.type);
        s.putInt(p.options);
    }

    // ------------------------------------------------------------------ SPEC

    private ByteSink specials() {
        ByteSink s = new ByteSink(1024);
        SpecialThinker[] ceilings = w.specials.activeCeilings();
        s.putInt(ceilings.length);
        for (SpecialThinker t : ceilings) {
            s.putInt(ref(t));
        }
        SpecialThinker[] plats = w.specials.activePlats();
        s.putInt(plats.length);
        for (SpecialThinker t : plats) {
            s.putInt(ref(t));
        }
        int[] buttons = w.specials.buttons();
        s.putInt(buttons.length);
        for (int v : buttons) {
            s.putInt(v);
        }
        Actor[] targets = w.ai.brainTargets();
        s.putInt(targets.length);
        for (Actor a : targets) {
            s.putInt(ref(a));
        }
        s.putInt(w.ai.brainTargetOn());
        s.putInt(w.ai.brainEasy());
        return s;
    }

    // ------------------------------------------------------------------ THNK

    private ByteSink thinkers() {
        ByteSink body = new ByteSink(1 << 16);
        // the table can grow while it is written: a record can refer to an object that is not in it yet
        for (int i = 0; i < table.size(); i++) {
            Thinker t = table.get(i);
            boolean ghost = i >= liveCount;
            if (t instanceof Actor a) {
                body.putInt(ghost ? KIND_GHOST_ACTOR : KIND_ACTOR);
                putActor(body, a);
            } else if (t instanceof SpecialThinker st) {
                body.putInt(ghost ? KIND_GHOST_SPECIAL : KIND_SPECIAL);
                int[] state = w.specials.saveThinker(st);
                body.putInt(state.length);
                for (int v : state) {
                    body.putInt(v);
                }
            } else {
                throw new SaveGameException("Unknown thinker " + t);
            }
        }
        ByteSink s = new ByteSink(body.size() + 16);
        s.putInt(table.size());
        s.putInt(liveCount);
        s.putBytes(body);
        return s;
    }

    private void putActor(ByteSink s, Actor a) {
        s.putInt(a.type);
        s.putInt(a.x);
        s.putInt(a.y);
        s.putInt(a.z);
        s.putInt(a.angle);
        s.putInt(a.sprite);
        s.putInt(a.frame);
        s.putInt(a.floorZ);
        s.putInt(a.ceilingZ);
        s.putInt(a.radius);
        s.putInt(a.height);
        s.putInt(a.momX);
        s.putInt(a.momY);
        s.putInt(a.momZ);
        s.putInt(a.validCount);
        s.putInt(a.tics);
        s.putInt(a.state == null ? -1 : a.stateNum);
        s.putInt(a.flags);
        s.putInt(a.health);
        s.putInt(a.moveDir);
        s.putInt(a.moveCount);
        s.putInt(ref(a.target));
        s.putInt(a.reactionTime);
        s.putInt(a.threshold);
        s.putInt(a.player == null ? -1 : a.player.index);
        s.putInt(a.lastLook);
        s.putInt(a.spawnPoint.x);
        s.putInt(a.spawnPoint.y);
        s.putInt(a.spawnPoint.angle);
        s.putInt(a.spawnPoint.type);
        s.putInt(a.spawnPoint.options);
        s.putInt(ref(a.tracer));
        s.putInt(a.subsector == null ? -1 : a.subsector.index);
    }
}
