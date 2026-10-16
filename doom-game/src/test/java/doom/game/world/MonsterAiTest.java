package doom.game.world;

import static org.assertj.core.api.Assertions.assertThat;

import doom.game.actor.ActionDispatcher;
import doom.game.actor.Actor;
import doom.game.actor.ActorRegistry;
import doom.game.actor.Mobjs;
import doom.game.actor.Thinker;
import doom.game.ai.MonsterAiImpl;
import doom.game.api.SimEvents;
import doom.game.combat.CombatStub;
import doom.game.data.GameDefs;
import doom.game.data.GameStrings;
import doom.game.data.MobjType;
import doom.game.data.StateAction;
import doom.game.mapspecial.MapSpecialsStub;
import doom.game.physics.Physics;
import doom.game.physics.Sight;
import doom.game.player.Pickups;
import doom.game.player.PlayerSim;
import doom.game.player.Weapons;
import doom.game.view.MobjFlags;
import doom.math.Fixed;
import doom.wad.LumpDirectory;
import doom.wad.iwad.GameMission;
import doom.wad.iwad.GameMode;
import doom.wad.iwad.GameVariant;
import doom.wad.iwad.GameVersion;
import doom.wad.texture.FlatDirectory;
import doom.wad.texture.TextureDirectory;
import org.junit.jupiter.api.Test;

/** Rules of {@code p_enemy.c} that are easy to check on the synthetic two-room map (W3-A). */
class MonsterAiTest {

    private static final int FU = Fixed.FRACUNIT;

    private int exits;

    private World world(GameVersion version) {
        TwoRoomMap map = new TwoRoomMap();
        LumpDirectory dir = map.directory();
        TextureDirectory textures = TextureDirectory.load(dir);
        FlatDirectory flats = FlatDirectory.load(dir);
        World w = new World(dir, GameMode.REGISTERED, GameMission.DOOM, GameVariant.VANILLA, version,
                GameDefs.createDefault(), GameStrings.createDefault(), textures, flats, new SimEvents() {
                });
        w.actorIds = new ActorRegistry();
        w.mobjs = new Mobjs(w);
        w.actions = new ActionDispatcher(w);
        w.physics = new Physics(w);
        w.sight = new Sight(w);
        w.playerSim = new PlayerSim(w);
        w.weapons = new Weapons(w);
        w.pickups = new Pickups(w);
        w.ai = new MonsterAiImpl(w);
        w.combat = new CombatStub(w);
        w.specials = new MapSpecialsStub(w);
        w.flow = new GameFlow() {
            @Override
            public void exitLevel() {
                exits++;
            }

            @Override
            public void secretExitLevel() {
            }

            @Override
            public void deferredInitNew(int skill, int episode, int map) {
            }
        };
        w.level = new LevelLoader(textures, flats).load(dir, "E1M1");
        w.blockLinks = new Actor[w.level.blockmap.columns * w.level.blockmap.rows];
        return w;
    }

    private static int count(World w, int type) {
        int n = 0;
        for (Thinker t = w.thinkers.first(); t != null; t = w.thinkers.next(t)) {
            if (t instanceof Actor a && !a.isRemoved() && a.type == type) {
                n++;
            }
        }
        return n;
    }

    @Test
    void noiseAlertFloodsThroughTheOpenTwoSidedLine() {
        World w = world(GameVersion.V1_9);
        Actor player = w.mobjs.spawnMobj(32 * FU, 32 * FU, Mobjs.ONFLOORZ, MobjType.PLAYER);
        w.ai.noiseAlert(player, player);
        Sector s0 = w.level.sectors[0];
        Sector s1 = w.level.sectors[1];
        assertThat(s0.soundTarget).isSameAs(player);
        assertThat(s0.soundTraversed).isEqualTo(1);
        assertThat(s1.soundTarget).isSameAs(player);
        assertThat(s1.soundTraversed).isEqualTo(1);
    }

    @Test
    void nightmareRespawnSpawnsTwoFogsAndTheMonsterAndRemovesTheCorpse() {
        World w = world(GameVersion.V1_9);
        Actor imp = w.mobjs.spawnMobj(32 * FU, 32 * FU, Mobjs.ONFLOORZ, MobjType.TROOP);
        imp.spawnPoint.x = 40;
        imp.spawnPoint.y = 20;
        imp.spawnPoint.angle = 90;
        imp.spawnPoint.options = 8;
        int before = w.rng.gameIndex();

        w.ai.nightmareRespawn(imp);

        assertThat(imp.isRemoved()).isTrue();
        assertThat(count(w, MobjType.TFOG)).isEqualTo(2);
        assertThat(count(w, MobjType.TROOP)).isEqualTo(1);
        // four P_SpawnMobj calls in all (three here, the fogs and the imp), one P_Random each
        assertThat(w.rng.gameIndex() - before).isEqualTo(3);
        Actor reborn = null;
        for (Thinker t = w.thinkers.first(); t != null; t = w.thinkers.next(t)) {
            if (t instanceof Actor a && !a.isRemoved() && a.type == MobjType.TROOP) {
                reborn = a;
            }
        }
        assertThat(reborn).isNotNull();
        assertThat(reborn.x).isEqualTo(40 * FU);
        assertThat(reborn.y).isEqualTo(20 * FU);
        assertThat(reborn.reactionTime).isEqualTo(18);
        assertThat(reborn.angle).isEqualTo(0x20000000 * 2);
        assertThat(reborn.flags & MobjFlags.AMBUSH).isNotZero();
        assertThat(reborn.spawnPoint.x).isEqualTo(40);
    }

    @Test
    void bossDeathUsesTheEpisodeRulesOfTheVersion() {
        // v1.9: any boss on map 8 ends the game, except a Baron outside episode 1
        World w = world(GameVersion.V1_9);
        w.playerInGame[0] = true;
        w.players[0].health = 100;
        w.gameMap = 8;
        w.gameEpisode = 2;
        Actor baron = w.mobjs.spawnMobj(32 * FU, 32 * FU, Mobjs.ONFLOORZ, MobjType.BRUISER);
        w.ai.action(StateAction.A_BossDeath, baron);
        assertThat(exits).isZero();
        Actor cyber = w.mobjs.spawnMobj(32 * FU, 32 * FU, Mobjs.ONFLOORZ, MobjType.CYBORG);
        cyber.health = 0;
        w.ai.action(StateAction.A_BossDeath, cyber);
        assertThat(exits).isEqualTo(1);

        // Ultimate: the Cyberdemon only counts in episode 2 map 8, the Spider Mastermind not
        World u = world(GameVersion.ULTIMATE);
        u.playerInGame[0] = true;
        u.players[0].health = 100;
        u.gameMap = 8;
        u.gameEpisode = 2;
        Actor spider = u.mobjs.spawnMobj(32 * FU, 32 * FU, Mobjs.ONFLOORZ, MobjType.SPIDER);
        spider.health = 0;
        exits = 0;
        u.ai.action(StateAction.A_BossDeath, spider);
        assertThat(exits).isZero();
    }

    @Test
    void bossDeathWaitsForTheOtherBossesOfTheSameType() {
        World w = world(GameVersion.V1_9);
        w.playerInGame[0] = true;
        w.players[0].health = 100;
        w.gameMap = 8;
        w.gameEpisode = 2;
        Actor a = w.mobjs.spawnMobj(32 * FU, 32 * FU, Mobjs.ONFLOORZ, MobjType.CYBORG);
        Actor b = w.mobjs.spawnMobj(40 * FU, 32 * FU, Mobjs.ONFLOORZ, MobjType.CYBORG);
        a.health = 0;
        w.ai.action(StateAction.A_BossDeath, a);
        assertThat(exits).isZero();
        b.health = 0;
        w.ai.action(StateAction.A_BossDeath, b);
        assertThat(exits).isEqualTo(1);
    }

    @Test
    void painElementalDoesNotSpitAboveTwentyLostSouls() {
        World w = world(GameVersion.V1_9);
        Actor pain = w.mobjs.spawnMobj(32 * FU, 32 * FU, Mobjs.ONFLOORZ, MobjType.PAIN);
        for (int i = 0; i < 21; i++) {
            w.mobjs.spawnMobj(32 * FU, 32 * FU, Mobjs.ONFLOORZ, MobjType.SKULL);
        }
        int rng = w.rng.gameIndex();
        pain.flags &= ~MobjFlags.SOLID;
        w.ai.action(StateAction.A_PainDie, pain);
        assertThat(count(w, MobjType.SKULL)).isEqualTo(21);
        assertThat(w.rng.gameIndex()).isEqualTo(rng);
        assertThat(pain.flags & MobjFlags.SOLID).isZero();

        // with exactly 20 on the level one more is still spawned (the limit is "more than 20")
        World v = world(GameVersion.V1_9);
        Actor pain2 = v.mobjs.spawnMobj(32 * FU, 32 * FU, Mobjs.ONFLOORZ, MobjType.PAIN);
        for (int i = 0; i < 20; i++) {
            v.mobjs.spawnMobj(32 * FU, 32 * FU, Mobjs.ONFLOORZ, MobjType.SKULL);
        }
        v.ai.action(StateAction.A_PainDie, pain2);
        assertThat(count(v, MobjType.SKULL)).isEqualTo(21);
    }
}
