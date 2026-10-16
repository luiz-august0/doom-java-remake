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
import doom.game.mapspecial.MapSpecialsImpl;
import doom.game.mapspecial.SpecialTypes;
import doom.game.physics.Physics;
import doom.game.physics.Sight;
import doom.game.player.Pickups;
import doom.game.player.PlayerSim;
import doom.game.player.Weapons;
import doom.math.DoomFatalException;
import doom.math.Fixed;
import doom.wad.LumpDirectory;
import doom.wad.iwad.GameMission;
import doom.wad.iwad.GameMode;
import doom.wad.iwad.GameVariant;
import doom.wad.iwad.GameVersion;
import doom.wad.texture.FlatDirectory;
import doom.wad.texture.TextureDirectory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The W3-C sector effects on the synthetic two-room map (sector 1 has tag 3, line 6 joins the rooms). */
class MapSpecialsTest {

    private World world() {
        TwoRoomMap map = new TwoRoomMap();
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
        w.level = new LevelLoader(textures, flats).load(dir, "E1M1");
        w.blockLinks = new Actor[w.level.blockmap.columns * w.level.blockmap.rows];
        return w;
    }

    private static List<Integer> trace(Thinker t) {
        List<Integer> out = new ArrayList<>();
        SpecialThinker s = (SpecialThinker) t;
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
        return out;
    }

    @Test
    void doorOpensToLowestSurroundingCeilingMinusFour() {
        World w = world();
        Line line = w.level.lines[6];
        line.tag = 3;
        Sector s1 = w.level.sectors[1];
        assertThat(w.specials.doDoor(line, SpecialTypes.Door.OPEN)).isTrue();
        assertThat(s1.specialData).isNotNull();
        // type, sector, topheight, speed, direction, topwait, topcountdown
        assertThat(trace(s1.specialData)).containsExactly(1, SpecialTypes.Door.OPEN, 1,
                124 * Fixed.FRACUNIT, 2 * Fixed.FRACUNIT, 1, 150, 0);
        // a second request finds the sector busy
        assertThat(w.specials.doDoor(line, SpecialTypes.Door.OPEN)).isFalse();
        for (int i = 0; i < 20; i++) {
            w.thinkers.run();
        }
        assertThat(s1.ceilingHeight).isEqualTo(124 * Fixed.FRACUNIT);
        assertThat(s1.specialData).isNull();
        assertThat(w.thinkers.liveCount()).isZero();
    }

    @Test
    void floorLowersToHighestSurroundingFloor() {
        World w = world();
        Sector s1 = w.level.sectors[1];
        w.level.lines[6].tag = 3;
        assertThat(w.specials.doFloor(w.level.lines[6], SpecialTypes.Floor.LOWER_FLOOR)).isTrue();
        // type, crush, sector, direction, newspecial, texture, floordestheight, speed
        assertThat(trace(s1.specialData)).containsExactly(3, SpecialTypes.Floor.LOWER_FLOOR, 0, 1, -1, 0, 0, 0,
                Fixed.FRACUNIT);
        for (int i = 0; i < 20; i++) {
            w.thinkers.run();
        }
        assertThat(s1.floorHeight).isEqualTo(0);
        assertThat(s1.specialData).isNull();
    }

    @Test
    void stairsCrushFieldIsTenAndPrintsAsOne() {
        World w = world();
        Sector s1 = w.level.sectors[1];
        w.level.lines[6].special = 8;
        w.level.lines[6].tag = 3;
        Actor player = w.mobjs.spawnMobj(32 * Fixed.FRACUNIT, 32 * Fixed.FRACUNIT, 0, 0);
        player.player = w.players[0];
        w.specials.crossSpecialLine(w.level.lines[6], 0, player);
        assertThat(s1.specialData).isNotNull();
        assertThat(trace(s1.specialData).get(2)).isEqualTo(1);
        assertThat(w.level.lines[6].special).isZero();
    }

    @Test
    void ceilingInStasisReportsNullFunction() {
        World w = world();
        Line line = w.level.lines[6];
        line.tag = 3;
        Sector s1 = w.level.sectors[1];
        assertThat(w.specials.doDoor(line, SpecialTypes.Door.NORMAL)).isTrue();
        s1.specialData = null;
        // crushers: use the cross trigger entry point (special 25 then 57)
        Actor player = w.mobjs.spawnMobj(32 * Fixed.FRACUNIT, 32 * Fixed.FRACUNIT, 0, 0);
        player.player = w.players[0];
        line.special = 25;
        w.specials.crossSpecialLine(line, 0, player);
        Thinker ceiling = w.level.sectors[1].specialData;
        assertThat(trace(ceiling).get(0)).isEqualTo(2);
        line.special = 57;
        w.specials.crossSpecialLine(line, 0, player);
        assertThat(ceiling.hasNullFunction()).isTrue();
        assertThat(trace(ceiling).get(0)).isEqualTo(102);
        line.special = 25;
        w.specials.crossSpecialLine(line, 0, player);
        assertThat(ceiling.hasNullFunction()).isFalse();
    }

    @Test
    void spawnSpecialsCountsSecretAndClearsLightSpecials() {
        World w = world();
        w.level.sectors[1].special = 9;
        w.level.sectors[0].special = 8;
        w.specials.spawnSpecials();
        assertThat(w.totalSecret).isEqualTo(1);
        assertThat(w.level.sectors[0].special).isZero();
        assertThat(w.thinkers.liveCount()).isEqualTo(1);
    }

    @Test
    void unknownPlayerSectorSpecialIsFatal() {
        World w = world();
        w.level.sectors[1].special = 6;
        Actor mo = w.mobjs.spawnMobj(96 * Fixed.FRACUNIT, 32 * Fixed.FRACUNIT, 16 * Fixed.FRACUNIT, 0);
        w.players[0].mo = mo;
        assertThatThrownBy(() -> w.specials.playerInSpecialSector(w.players[0]))
                .isInstanceOf(DoomFatalException.class).hasMessageContaining("unknown special 6");
    }
}
