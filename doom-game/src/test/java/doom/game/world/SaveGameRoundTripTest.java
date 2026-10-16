package doom.game.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import doom.game.actor.ActionDispatcher;
import doom.game.actor.Actor;
import doom.game.actor.ActorRegistry;
import doom.game.actor.Mobjs;
import doom.game.actor.SpecialThinker;
import doom.game.actor.Thinker;
import doom.game.actor.TraceSink;
import doom.game.ai.MonsterAiStub;
import doom.game.combat.CombatStub;
import doom.game.data.GameDefs;
import doom.game.data.GameStrings;
import doom.game.data.MobjType;
import doom.game.data.StateId;
import doom.game.mapspecial.MapSpecialsImpl;
import doom.game.mapspecial.SpecialTypes;
import doom.game.persist.SaveGameCodec;
import doom.game.persist.SaveGameException;
import doom.game.persist.SaveInfo;
import doom.game.physics.Physics;
import doom.game.physics.Sight;
import doom.game.player.Pickups;
import doom.game.player.Player;
import doom.game.player.PlayerSim;
import doom.game.player.Weapons;
import doom.game.session.Game;
import doom.game.view.AmmoType;
import doom.game.view.Card;
import doom.game.view.Power;
import doom.game.view.Weapon;
import doom.math.Fixed;
import doom.wad.LumpDirectory;
import doom.wad.iwad.GameMission;
import doom.wad.iwad.GameMode;
import doom.wad.iwad.GameVariant;
import doom.wad.iwad.GameVersion;
import doom.wad.texture.FlatDirectory;
import doom.wad.texture.TextureDirectory;
import doom.wad.map.MapSector;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Consumer;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

/**
 * W4-P on the synthetic two-room map, where every kind of sector effect can be started by hand: a snapshot loaded into
 * a second, identically built world must serialize to the same bytes and then evolve identically.
 */
class SaveGameRoundTripTest {

    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC);

    /** One world with its game flow and codec. */
    private record Rig(World w, Game game, SaveGameCodec codec) {

        byte[] snapshot() {
            return codec.save(0, "unit test");
        }

        Line line() {
            return w.level.lines[6];
        }

        Actor player() {
            return w.players[0].mo;
        }
    }

    private static Rig rig(Consumer<TwoRoomMap> tweak, int skill) {
        TwoRoomMap map = new TwoRoomMap();
        tweak.accept(map);
        LumpDirectory dir = map.directory();
        TextureDirectory textures = TextureDirectory.load(dir);
        FlatDirectory flats = FlatDirectory.load(dir);
        World w = new World(dir, GameMode.REGISTERED, GameMission.DOOM, GameVariant.VANILLA, GameVersion.V1_9,
                GameDefs.createDefault(), GameStrings.createDefault(), textures, flats, null);
        w.actorIds = new ActorRegistry();
        w.mobjs = new Mobjs(w);
        w.actions = new ActionDispatcher(w);
        w.physics = new Physics(w);
        w.sight = new Sight(w);
        w.playerSim = new PlayerSim(w);
        w.weapons = new Weapons(w);
        w.pickups = new Pickups(w);
        w.ai = new MonsterAiStub(w);
        w.combat = new CombatStub(w);
        w.specials = new MapSpecialsImpl(w);
        Game game = new Game(w);
        w.flow = game;
        // (P_InitSwitchList needs the switch textures, which the synthetic WAD does not have)
        SaveGameCodec codec = new SaveGameCodec(w, game, CLOCK);
        game.setSaveGameHandler(codec);
        game.initNew(skill, 1, 1);
        return new Rig(w, game, codec);
    }

    private static Rig rig() {
        return rig(m -> {
        }, 2);
    }

    private static byte[] payload(byte[] snapshot) {
        SaveInfo info = SaveGameCodec.inspect(snapshot);
        return Arrays.copyOfRange(snapshot, info.payloadOffset(), snapshot.length - 12);
    }

    private static byte[] fixCrc(byte[] data) {
        byte[] out = data.clone();
        CRC32 crc = new CRC32();
        crc.update(out, 0, out.length - 12);
        long v = crc.getValue();
        for (int i = 0; i < 4; i++) {
            out[out.length - 4 + i] = (byte) (v >> (8 * i));
        }
        return out;
    }

    /** What the trace says about every live thinker, in list order, plus the sector and player basics. */
    private static List<Integer> describe(World w) {
        List<Integer> out = new ArrayList<>();
        for (Thinker t = w.thinkers.first(); t != null; t = w.thinkers.next(t)) {
            if (t.isRemoved()) {
                continue;
            }
            if (t instanceof SpecialThinker s) {
                out.add(s.traceCode());
                s.traceFields(new TraceSink() {
                    @Override
                    public void put(int value) {
                        out.add(value);
                    }

                    @Override
                    public int thinkerIndex(Thinker thinker) {
                        return -1;
                    }
                });
            } else if (t instanceof Actor a) {
                out.addAll(List.of(1000, a.type, a.x, a.y, a.z, a.angle, a.momX, a.momY, a.momZ, a.health,
                        a.stateNum, a.tics, a.flags, a.lastLook, a.moveDir, a.moveCount, a.reactionTime,
                        a.threshold, a.target == null ? -1 : a.target.type, a.target == null ? 0 : a.target.x));
            }
        }
        for (Sector s : w.level.sectors) {
            out.addAll(List.of(2000, s.floorHeight, s.ceilingHeight, s.lightLevel, s.special, s.tag,
                    s.specialData == null ? 0 : 1, s.soundTarget == null ? 0 : 1));
        }
        out.add(w.rng.gameIndex());
        out.add(w.levelTime);
        return out;
    }

    /** Saves {@code a}, loads into a fresh rig of the same map, checks the bytes, and returns the loaded rig. */
    private Rig roundTrip(Rig a, Consumer<TwoRoomMap> tweak, int skill) {
        byte[] snapshot = a.snapshot();
        Rig b = rig(tweak, skill == 4 ? 2 : skill); // the loaded game starts from another skill on purpose
        b.codec.load(snapshot);
        assertThat(payload(b.snapshot())).as("serialized again").isEqualTo(payload(snapshot));
        assertThat(describe(b.w)).as("state right after the load").isEqualTo(describe(a.w));
        return b;
    }

    private static void run(Rig r, int tics) {
        for (int i = 0; i < tics; i++) {
            r.w.thinkers.run();
            r.w.specials.updateSpecials();
            r.w.levelTime++;
        }
    }

    private static void sameEvolution(Rig a, Rig b, int tics) {
        for (int i = 0; i < tics; i++) {
            run(a, 1);
            run(b, 1);
            assertThat(describe(b.w)).as("tic %d after the load", i).isEqualTo(describe(a.w));
        }
    }

    // ------------------------------------------------------------------ sector effects

    @Test
    void flickeringAndGlowingLights() {
        Consumer<TwoRoomMap> tweak = m -> {
            m.sectors.set(0, new MapSector(0, 128, "FLOOR", "CEIL", 160, 17, 0));
            m.sectors.set(1, new MapSector(16, 96, "FLOOR2", "F_SKY1", 200, 8, 3));
        };
        Rig a = rig(tweak, 2);
        run(a, 37);
        Rig b = roundTrip(a, tweak, 2);
        assertThat(a.w.thinkers.liveCount()).as("two lights and the player").isEqualTo(3);
        sameEvolution(a, b, 120);
    }

    @Test
    void flashingAndStrobingLights() {
        Consumer<TwoRoomMap> tweak = m -> {
            m.sectors.set(0, new MapSector(0, 128, "FLOOR", "CEIL", 160, 1, 0));
            m.sectors.set(1, new MapSector(16, 96, "FLOOR2", "F_SKY1", 200, 3, 3));
        };
        Rig a = rig(tweak, 2);
        run(a, 61);
        Rig b = roundTrip(a, tweak, 2);
        assertThat(a.w.thinkers.liveCount()).as("two lights and the player").isEqualTo(3);
        sameEvolution(a, b, 120);
    }

    @Test
    void doorAndPlatformMidFlight() {
        Rig a = rig();
        Line line = a.line();
        a.w.level.sectors[0].tag = 4;
        line.tag = 3;
        assertThat(a.w.specials.doDoor(line, SpecialTypes.Door.NORMAL)).isTrue();
        line.tag = 4;
        line.special = 10; // PlatDownWaitUp
        a.w.specials.crossSpecialLine(line, 0, a.player());
        assertThat(a.w.level.sectors[0].specialData).isNotNull();
        run(a, 9);
        Consumer<TwoRoomMap> tweak = m -> {
        };
        Rig b = roundTripKeepingTag(a, tweak);
        sameEvolution(a, b, 200);
    }

    /** The loaded world gets the sector tag the test changed after loading the map (tags are part of the save). */
    private Rig roundTripKeepingTag(Rig a, Consumer<TwoRoomMap> tweak) {
        return roundTrip(a, tweak, 2);
    }

    @Test
    void crusherInStasisAndStairs() {
        Rig a = rig();
        Line line = a.line();
        line.tag = 3;
        line.special = 25; // crusher start
        a.w.specials.crossSpecialLine(line, 0, a.player());
        run(a, 5);
        line.special = 57; // crusher stop: the ceiling is in stasis, its function NULL
        a.w.specials.crossSpecialLine(line, 0, a.player());
        Sector s1 = a.w.level.sectors[1];
        assertThat(s1.specialData.hasNullFunction()).isTrue();
        // stairs in the other room: the crush field of the floor mover holds 10, not 1
        a.w.level.sectors[0].tag = 4;
        line.tag = 4;
        line.special = 8;
        a.w.specials.crossSpecialLine(line, 0, a.player());
        run(a, 3);

        Rig b = roundTrip(a, m -> {
        }, 2);
        assertThat(b.w.level.sectors[1].specialData.hasNullFunction()).isTrue();
        // wake the crusher in both and let it move
        line.tag = 3;
        line.special = 25;
        a.w.specials.crossSpecialLine(line, 0, a.player());
        Line bLine = b.line();
        bLine.tag = 3;
        bLine.special = 25;
        b.w.specials.crossSpecialLine(bLine, 0, b.player());
        assertThat(b.w.level.sectors[1].specialData.hasNullFunction()).isFalse();
        sameEvolution(a, b, 150);
    }

    @Test
    void floorRaiseAndMultipleFinishedEffects() {
        Rig a = rig();
        a.line().tag = 3;
        assertThat(a.w.specials.doFloor(a.line(), SpecialTypes.Floor.RAISE_FLOOR)).isTrue();
        run(a, 4);
        Rig b = roundTrip(a, m -> {
        }, 2);
        sameEvolution(a, b, 160); // the floor reaches its height and the thinker goes away in both
        assertThat(a.w.level.sectors[1].specialData).isNull();
        assertThat(b.w.level.sectors[1].specialData).isNull();
    }

    @Test
    void buttonTableRoundTrips() {
        Rig a = rig();
        int[] buttons = new int[16 * 4];
        Arrays.fill(buttons, 0);
        for (int i = 0; i < 16; i++) {
            buttons[i * 4] = -1;
        }
        buttons[0] = 6;
        buttons[1] = 2;
        buttons[2] = 5;
        buttons[3] = 35;
        a.w.specials.restoreButtons(buttons);
        assertThat(a.w.specials.buttons()).isEqualTo(buttons);
        Rig b = roundTrip(a, m -> {
        }, 2);
        assertThat(b.w.specials.buttons()).isEqualTo(buttons);
        assertThatThrownBy(() -> a.w.specials.restoreButtons(new int[3])).isInstanceOf(IllegalArgumentException.class);
        buttons[0] = 99;
        assertThatThrownBy(() -> a.w.specials.restoreButtons(buttons)).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ map objects

    @Test
    void thingListsKeepTheirOrderAndStaleReferencesSurvive() {
        Rig a = rig();
        World w = a.w;
        List<Actor> imps = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Actor imp = w.mobjs.spawnMobj((10 + i * 7) * Fixed.FRACUNIT, (20 + i) * Fixed.FRACUNIT, Mobjs.ONFLOORZ,
                    MobjType.TROOP);
            imp.health = 10 + i;
            imps.add(imp);
        }
        // move two things between cells and sectors so the list order is no longer the spawn order
        w.physics.unsetThingPosition(imps.get(1));
        imps.get(1).x = 90 * Fixed.FRACUNIT;
        w.physics.setThingPosition(imps.get(1));
        w.physics.unsetThingPosition(imps.get(3));
        imps.get(3).y = 40 * Fixed.FRACUNIT;
        w.physics.setThingPosition(imps.get(3));
        // a target that is removed after being targeted, a tracer and a sound target
        imps.get(0).target = imps.get(4);
        imps.get(2).tracer = imps.get(4);
        w.level.sectors[0].soundTarget = imps.get(4);
        w.bodyQue[3] = imps.get(5);
        w.mobjs.removeMobj(imps.get(4));
        w.players[0].attacker = imps.get(4);
        run(a, 3);

        Rig b = roundTrip(a, m -> {
        }, 2);

        // sector lists in snext order
        for (int s = 0; s < 2; s++) {
            List<String> la = new ArrayList<>();
            List<String> lb = new ArrayList<>();
            for (Actor x = a.w.level.sectors[s].thingList; x != null; x = x.snext) {
                la.add(x.type + "@" + x.x + "," + x.y);
            }
            for (Actor x = b.w.level.sectors[s].thingList; x != null; x = x.snext) {
                lb.add(x.type + "@" + x.x + "," + x.y);
            }
            assertThat(lb).as("sector %d things", s).isEqualTo(la);
            assertThat(la).isNotEmpty();
        }
        // blockmap lists in bnext order
        for (int c = 0; c < a.w.blockLinks.length; c++) {
            List<String> la = new ArrayList<>();
            List<String> lb = new ArrayList<>();
            for (Actor x = a.w.blockLinks[c]; x != null; x = x.bnext) {
                la.add(x.type + "@" + x.x + "," + x.y);
            }
            for (Actor x = b.w.blockLinks[c]; x != null; x = x.bnext) {
                lb.add(x.type + "@" + x.x + "," + x.y);
            }
            assertThat(lb).as("block %d things", c).isEqualTo(la);
        }
        // the removed target is still there for whoever points at it, with its data, and is not alive
        Actor stale = b.w.level.sectors[0].soundTarget;
        assertThat(stale).isNotNull();
        assertThat(stale.isRemoved()).isTrue();
        assertThat(stale.id).isEqualTo(-1);
        assertThat(stale.type).isEqualTo(MobjType.TROOP);
        assertThat(stale.x).isEqualTo(imps.get(4).x);
        assertThat(b.w.players[0].attacker).isSameAs(stale);
        assertThat(b.w.bodyQue[3]).isNotNull();
        assertThat(b.w.bodyQue[3].x).isEqualTo(imps.get(5).x);
        // every player's body is a live thinker that points back at the player
        assertThat(b.w.players[0].mo.player).isSameAs(b.w.players[0]);
        assertThat(b.w.actorIds.get(b.w.players[0].mo.id)).isSameAs(b.w.players[0].mo);
        // ids exist for live objects only
        for (Thinker t = b.w.thinkers.first(); t != null; t = b.w.thinkers.next(t)) {
            if (t instanceof Actor x && !x.isRemoved()) {
                assertThat(x.id).isGreaterThanOrEqualTo(0);
                assertThat(b.w.actorIds.get(x.id)).isSameAs(x);
            }
        }
        sameEvolution(a, b, 20);
    }

    // ------------------------------------------------------------------ players and session

    @Test
    void playersAndSessionState() {
        Rig a = rig(m -> {
        }, 4); // Nightmare: the definition tables were changed by G_InitNew
        World w = a.w;
        Player p = w.players[0];
        p.health = 37;
        p.armorPoints = 88;
        p.armorType = 2;
        p.powers[Power.INVULNERABILITY] = 700;
        p.powers[Power.IRONFEET] = 12;
        p.cards[Card.BLUE_CARD] = true;
        p.cards[Card.RED_SKULL] = true;
        p.backpack = true;
        p.frags[0] = 3;
        p.readyWeapon = Weapon.SHOTGUN;
        p.pendingWeapon = Weapon.NO_CHANGE;
        p.weaponOwned[Weapon.SHOTGUN] = true;
        p.weaponOwned[Weapon.CHAINSAW] = true;
        p.ammo[AmmoType.SHELL] = 33;
        p.maxAmmo[AmmoType.SHELL] = 100;
        p.cheats = 3;
        p.damageCount = 9;
        p.bonusCount = 4;
        p.extraLight = 2;
        p.fixedColormap = 5;
        p.colormap = 1;
        p.killCount = 7;
        p.itemCount = 8;
        p.secretCount = 1;
        p.didSecret = true;
        p.psprites[0].state = w.defs.states()[StateId.SGUN];
        p.psprites[0].stateNum = StateId.SGUN;
        p.psprites[0].tics = 4;
        p.psprites[0].sx = 5 * Fixed.FRACUNIT;
        p.psprites[1].state = null;
        p.psprites[1].stateNum = -1;
        w.totalKills = 20;
        w.totalItems = 30;
        w.totalSecret = 2;
        w.gametic = 777;
        w.levelTime = 444;
        a.game.levelStartTic = 333;
        for (int i = 0; i < 23; i++) {
            w.rng.game();
            w.rng.misc();
        }
        w.validCount = 9001;
        w.playerSim.setOnGround(true);
        w.skyTexture = 1;
        int sargRun1Tics = w.defs.states()[StateId.SARG_RUN1].tics;
        int troopSpeed = w.defs.things()[MobjType.TROOPSHOT].speed;

        Rig b = roundTrip(a, m -> {
        }, 2);
        Player q = b.w.players[0];
        assertThat(q.health).isEqualTo(37);
        assertThat(q.armorType).isEqualTo(2);
        assertThat(q.powers[Power.INVULNERABILITY]).isEqualTo(700);
        assertThat(q.cards[Card.RED_SKULL]).isTrue();
        assertThat(q.cards[Card.YELLOW_CARD]).isFalse();
        assertThat(q.weaponOwned[Weapon.CHAINSAW]).isTrue();
        assertThat(q.ammo[AmmoType.SHELL]).isEqualTo(33);
        assertThat(q.pendingWeapon).isEqualTo(Weapon.NO_CHANGE);
        assertThat(q.psprites[0].state).isSameAs(b.w.defs.states()[StateId.SGUN]);
        assertThat(q.psprites[0].sx).isEqualTo(5 * Fixed.FRACUNIT);
        assertThat(q.psprites[1].state).isNull();
        assertThat(q.didSecret).isTrue();
        assertThat(b.w.totalKills).isEqualTo(20);
        assertThat(b.w.gametic).isEqualTo(777);
        assertThat(b.w.levelTime).isEqualTo(444);
        assertThat(b.game.levelStartTic).isEqualTo(333);
        assertThat(b.w.rng.gameIndex()).isEqualTo(a.w.rng.gameIndex());
        assertThat(b.w.rng.miscIndex()).isEqualTo(a.w.rng.miscIndex());
        assertThat(b.w.validCount).isEqualTo(9001);
        assertThat(b.w.playerSim.onGround()).isTrue();
        assertThat(b.w.gameSkill).isEqualTo(4);
        assertThat(b.w.respawnMonsters).isTrue();
        // the Nightmare changes of the tables come from the save, not from the skill the loading world started with
        assertThat(b.w.defs.states()[StateId.SARG_RUN1].tics).isEqualTo(sargRun1Tics);
        assertThat(b.w.defs.things()[MobjType.TROOPSHOT].speed).isEqualTo(troopSpeed);
    }

    @Test
    void loadingTwiceAndOverAGameInProgressGivesTheSameState() {
        Rig a = rig();
        run(a, 10);
        byte[] snapshot = a.snapshot();
        Rig b = rig();
        // b is playing: put something else in its world first
        b.w.mobjs.spawnMobj(30 * Fixed.FRACUNIT, 30 * Fixed.FRACUNIT, Mobjs.ONFLOORZ, MobjType.TROOP);
        run(b, 25);
        b.codec.load(snapshot);
        b.codec.load(snapshot);
        assertThat(payload(b.snapshot())).isEqualTo(payload(snapshot));
        assertThat(b.codec.lastWarning()).isNull();
    }

    // ------------------------------------------------------------------ damage

    @Test
    void damagedFilesNeverThrowAnythingButSaveGameException() {
        Rig a = rig();
        a.line().tag = 3;
        a.w.specials.doDoor(a.line(), SpecialTypes.Door.NORMAL);
        a.w.mobjs.spawnMobj(40 * Fixed.FRACUNIT, 30 * Fixed.FRACUNIT, Mobjs.ONFLOORZ, MobjType.TROOP);
        run(a, 5);
        byte[] good = a.snapshot();
        SplittableRandom rnd = new SplittableRandom(12345);
        int loaded = 0;
        int refused = 0;
        for (int i = 0; i < 1500; i++) {
            byte[] bad = good.clone();
            int kind = rnd.nextInt(4);
            switch (kind) {
                case 0 -> bad[rnd.nextInt(bad.length - 12)] ^= (byte) (1 + rnd.nextInt(255));
                case 1 -> {
                    int at = rnd.nextInt(bad.length - 16);
                    int v = rnd.nextBoolean() ? Integer.MAX_VALUE : rnd.nextBoolean() ? -1 : rnd.nextInt(2000) - 5;
                    for (int k = 0; k < 4; k++) {
                        bad[at + k] = (byte) (v >> (8 * k));
                    }
                }
                case 2 -> bad = Arrays.copyOf(bad, rnd.nextInt(bad.length));
                default -> {
                    int from = rnd.nextInt(bad.length - 12);
                    Arrays.fill(bad, from, Math.min(bad.length - 12, from + 1 + rnd.nextInt(80)), (byte) 0xFF);
                }
            }
            if (kind != 2 && rnd.nextBoolean()) {
                bad = fixCrc(bad);
            }
            Rig b = rig();
            try {
                b.codec.load(bad);
                loaded++;
            } catch (SaveGameException e) {
                refused++;
                assertThat(e.getMessage()).isNotBlank();
            }
        }
        assertThat(refused).isGreaterThan(900);
        assertThat(loaded).isGreaterThan(0); // some damage lands on a free value and is a valid, different game
    }

    @Test
    void aSaveOfAnotherMapSizeIsRefusedBeforeTheGameIsTouched() {
        Rig a = rig();
        byte[] snapshot = a.snapshot();
        // a world whose map has one more sector
        Rig b = rig(m -> m.sectors.add(new MapSector(0, 64, "FLOOR", "CEIL", 100, 0, 0)), 2);
        run(b, 7);
        int before = b.w.levelTime;
        assertThatThrownBy(() -> b.codec.load(snapshot)).isInstanceOf(SaveGameException.class)
                .satisfies(e -> assertThat(((SaveGameException) e).worldModified()).isFalse());
        assertThat(b.w.levelTime).isEqualTo(before);
    }
}
