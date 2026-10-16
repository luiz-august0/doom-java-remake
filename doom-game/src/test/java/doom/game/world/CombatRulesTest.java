package doom.game.world;

import static org.assertj.core.api.Assertions.assertThat;

import doom.game.actor.ActionDispatcher;
import doom.game.actor.Actor;
import doom.game.actor.ActorRegistry;
import doom.game.actor.Mobjs;
import doom.game.ai.MonsterAiStub;
import doom.game.combat.CombatImpl;
import doom.game.data.GameDefs;
import doom.game.data.GameStrings;
import doom.game.data.MobjType;
import doom.game.data.StateId;
import doom.game.mapspecial.MapSpecialsStub;
import doom.game.physics.Physics;
import doom.game.physics.Sight;
import doom.game.player.Pickups;
import doom.game.player.Player;
import doom.game.player.PlayerSim;
import doom.game.player.Weapons;
import doom.game.view.CheatFlags;
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

/** Damage, armor, god mode, death drops and puff/blood spawning of {@link CombatImpl} on the synthetic map. */
class CombatRulesTest {

    private static final int POS = 32 * Fixed.FRACUNIT;

    private World world() {
        TwoRoomMap map = new TwoRoomMap();
        LumpDirectory dir = map.directory();
        TextureDirectory textures = TextureDirectory.load(dir);
        FlatDirectory flats = FlatDirectory.load(dir);
        World w = new World(dir, GameMode.COMMERCIAL, GameMission.DOOM2, GameVariant.VANILLA, GameVersion.V1_9,
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
        w.combat = new CombatImpl(w);
        w.specials = new MapSpecialsStub(w);
        w.level = new LevelLoader(textures, flats).load(dir, "E1M1");
        w.blockLinks = new Actor[w.level.blockmap.columns * w.level.blockmap.rows];
        w.gameSkill = 2;
        return w;
    }

    private Actor player(World w) {
        Actor mo = w.mobjs.spawnMobj(POS, POS, Mobjs.ONFLOORZ, MobjType.PLAYER);
        Player p = w.players[0];
        p.mo = mo;
        mo.player = p;
        p.health = 100;
        mo.health = 100;
        return mo;
    }

    @Test
    void greenArmorAbsorbsAThirdAndIsUsedUp() {
        World w = world();
        Actor mo = player(w);
        Player p = mo.player;
        p.armorType = 1;
        p.armorPoints = 10;
        w.combat.damageMobj(mo, null, null, 30);
        // saved = 30 / 3 = 10 >= armorpoints -> armor used up; damage 20
        assertThat(p.armorType).isZero();
        assertThat(p.armorPoints).isZero();
        assertThat(p.health).isEqualTo(80);
        assertThat(mo.health).isEqualTo(80);
        assertThat(p.damageCount).isEqualTo(20);
    }

    @Test
    void godModeIgnoresDamageBelow1000ButNotAbove() {
        World w = world();
        Actor mo = player(w);
        mo.player.cheats |= CheatFlags.GODMODE;
        w.combat.damageMobj(mo, null, null, 999);
        assertThat(mo.health).isEqualTo(100);
        w.combat.damageMobj(mo, null, null, 1000);
        assertThat(mo.health).isLessThanOrEqualTo(0);
        assertThat(mo.player.playerState).isEqualTo(Player.PST_DEAD);
    }

    @Test
    void babySkillHalvesDamageToPlayers() {
        World w = world();
        w.gameSkill = 0;
        Actor mo = player(w);
        w.combat.damageMobj(mo, null, null, 21);
        assertThat(mo.player.health).isEqualTo(90);
    }

    @Test
    void killingAZombieCountsTheKillDropsAClipAndUsesOneRandomCall() {
        World w = world();
        Actor killer = player(w);
        Actor zombie = w.mobjs.spawnMobj(POS + 64 * Fixed.FRACUNIT, POS, Mobjs.ONFLOORZ, MobjType.POSSESSED);
        int before = w.rng.gameIndex();
        int heightBefore = zombie.height;
        w.combat.damageMobj(zombie, killer, killer, 1000);

        assertThat(killer.player.killCount).isEqualTo(1);
        assertThat(zombie.flags & MobjFlags.SHOOTABLE).isZero();
        assertThat(zombie.flags & MobjFlags.CORPSE).isNotZero();
        assertThat(zombie.height).isEqualTo(heightBefore >> 2);
        assertThat(zombie.stateNum).isEqualTo(StateId.POSS_XDIE1);
        // thrust draw (damage < 40 is false: no draw), tics draw, clip spawn lastLook draw
        assertThat(w.rng.gameIndex() - before).isEqualTo(2);
        Actor drop = null;
        for (var t = w.thinkers.first(); t != null; t = w.thinkers.next(t)) {
            if (t instanceof Actor a && a.type == MobjType.CLIP) {
                drop = a;
            }
        }
        assertThat(drop).isNotNull();
        assertThat(drop.flags & MobjFlags.DROPPED).isNotZero();
    }

    @Test
    void meleeRangePuffsSkipTheSpark() {
        World w = world();
        w.physics.attackRange = Physics.MELEERANGE;
        w.combat.spawnPuff(POS, POS, 0);
        Actor puff = null;
        for (var t = w.thinkers.first(); t != null; t = w.thinkers.next(t)) {
            if (t instanceof Actor a && a.type == MobjType.PUFF) {
                puff = a;
            }
        }
        assertThat(puff).isNotNull();
        assertThat(puff.stateNum).isEqualTo(StateId.PUFF3);
    }
}
