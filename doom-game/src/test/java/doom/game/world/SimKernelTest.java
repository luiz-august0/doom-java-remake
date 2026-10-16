package doom.game.world;

import static org.assertj.core.api.Assertions.assertThat;

import doom.game.actor.ActionDispatcher;
import doom.game.actor.Actor;
import doom.game.actor.ActorRegistry;
import doom.game.actor.Mobjs;
import doom.game.actor.SpecialThinker;
import doom.game.actor.Thinker;
import doom.game.actor.ThinkerList;
import doom.game.actor.TraceSink;
import doom.game.ai.MonsterAiStub;
import doom.game.api.SimEvents;
import doom.game.combat.CombatStub;
import doom.game.data.GameDefs;
import doom.game.data.GameStrings;
import doom.game.data.MobjType;
import doom.game.data.State;
import doom.game.data.StateAction;
import doom.game.data.StateId;
import doom.game.mapspecial.MapSpecialsStub;
import doom.game.physics.Physics;
import doom.game.physics.Sight;
import doom.game.player.Pickups;
import doom.game.player.PlayerSim;
import doom.game.player.Weapons;
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

/** Thinker list semantics, the state machine and basic movement on the synthetic two-room map. */
class SimKernelTest {

    private final List<Integer> stoppedSounds = new ArrayList<>();

    private World world() {
        TwoRoomMap map = new TwoRoomMap();
        LumpDirectory dir = map.directory();
        TextureDirectory textures = TextureDirectory.load(dir);
        FlatDirectory flats = FlatDirectory.load(dir);
        SimEvents events = new SimEvents() {
            @Override
            public void stopSound(int origin) {
                stoppedSounds.add(origin);
            }
        };
        World w = new World(dir, GameMode.REGISTERED, GameMission.DOOM, GameVariant.VANILLA, GameVersion.V1_9,
                GameDefs.createDefault(), GameStrings.createDefault(), textures, flats, events);
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
        w.specials = new MapSpecialsStub(w);
        w.level = new LevelLoader(textures, flats).load(dir, "E1M1");
        w.blockLinks = new Actor[w.level.blockmap.columns * w.level.blockmap.rows];
        return w;
    }

    /** A thinker that runs a callback and logs its name. */
    private static final class Probe extends SpecialThinker {
        final String name;
        final List<String> log;
        Runnable onThink = () -> {
        };

        Probe(String name, List<String> log) {
            this.name = name;
            this.log = log;
        }

        @Override
        protected void think() {
            log.add(name);
            onThink.run();
        }

        @Override
        public int traceCode() {
            return 0;
        }

        @Override
        public void traceFields(TraceSink sink) {
        }
    }

    @Test
    void thinkersRunInInsertionOrderWithDeferredRemoval() {
        ThinkerList list = new ThinkerList();
        List<String> log = new ArrayList<>();
        Probe a = new Probe("a", log);
        Probe b = new Probe("b", log);
        Probe c = new Probe("c", log);
        Probe d = new Probe("d", log);
        list.add(a);
        list.add(b);
        list.add(c);
        // b removes itself and adds d while the list runs: d runs in the same pass
        b.onThink = () -> {
            list.remove(b);
            list.add(d);
            b.onThink = () -> {
            };
        };
        list.run();
        assertThat(log).containsExactly("a", "b", "c", "d");
        assertThat(b.isRemoved()).isTrue();
        assertThat(list.liveCount()).isEqualTo(3);

        // the removed thinker is still linked until the next pass reaches it, and does not run
        List<Thinker> linked = new ArrayList<>();
        for (Thinker t = list.first(); t != null; t = list.next(t)) {
            linked.add(t);
        }
        assertThat(linked).containsExactly(a, b, c, d);

        log.clear();
        list.run();
        assertThat(log).containsExactly("a", "c", "d");
        linked.clear();
        for (Thinker t = list.first(); t != null; t = list.next(t)) {
            linked.add(t);
        }
        assertThat(linked).containsExactly(a, c, d);
    }

    @Test
    void thinkerRemovedBeforeItsTurnIsSkippedAndUnlinked() {
        ThinkerList list = new ThinkerList();
        List<String> log = new ArrayList<>();
        Probe a = new Probe("a", log);
        Probe b = new Probe("b", log);
        list.add(a);
        list.add(b);
        a.onThink = () -> list.remove(b);
        list.run();
        assertThat(log).containsExactly("a");
        assertThat(list.first()).isSameAs(a);
        assertThat(list.next(a)).isNull();
    }

    @Test
    void spawnUsesOneRandomNumberAndLinksTheActor() {
        World w = world();
        Actor mo = w.mobjs.spawnMobj(32 * Fixed.FRACUNIT, 32 * Fixed.FRACUNIT, Mobjs.ONFLOORZ, MobjType.BARREL);
        assertThat(w.rng.gameIndex()).isEqualTo(1);
        assertThat(mo.subsector.sector.index).isEqualTo(0);
        assertThat(mo.z).isEqualTo(0);
        assertThat(mo.subsector.sector.thingList).isSameAs(mo);
        assertThat(w.thinkers.first()).isSameAs(mo);
        assertThat(mo.id).isEqualTo(0);

        Actor high = w.mobjs.spawnMobj(96 * Fixed.FRACUNIT, 32 * Fixed.FRACUNIT, Mobjs.ONCEILINGZ, MobjType.BARREL);
        assertThat(high.z).isEqualTo(96 * Fixed.FRACUNIT - high.info.height);
    }

    @Test
    void zeroTicStatesChainInTheSameCallAndRunEachAction() {
        World w = world();
        State[] states = w.defs.states();
        states[100] = new State(0, 0, 0, StateAction.A_Look, 101, 0, 0);
        states[101] = new State(0, 1, 0, StateAction.A_Chase, 102, 0, 0);
        states[102] = new State(0, 2, 5, StateAction.NONE, 100, 0, 0);
        Actor mo = w.mobjs.spawnMobj(32 * Fixed.FRACUNIT, 32 * Fixed.FRACUNIT, Mobjs.ONFLOORZ, MobjType.BARREL);

        assertThat(w.mobjs.setMobjState(mo, 100)).isTrue();
        assertThat(mo.stateNum).isEqualTo(102);
        assertThat(mo.tics).isEqualTo(5);
        assertThat(mo.frame).isEqualTo(2);
        assertThat(w.unimplemented.hits()).containsKeys("ai.A_Look", "ai.A_Chase");
    }

    @Test
    void enteringTheNullStateRemovesTheActor() {
        World w = world();
        Actor mo = w.mobjs.spawnMobj(32 * Fixed.FRACUNIT, 32 * Fixed.FRACUNIT, Mobjs.ONFLOORZ, MobjType.BARREL);
        int id = mo.id;
        assertThat(w.mobjs.setMobjState(mo, StateId.NULL)).isFalse();
        assertThat(mo.isRemoved()).isTrue();
        assertThat(mo.state).isNull();
        assertThat(mo.subsector.sector.thingList).isNull();
        assertThat(stoppedSounds).containsExactly(id);
        assertThat(w.actorIds.get(id)).isNull();
        // the id is reused only after the stop
        Actor next = w.mobjs.spawnMobj(32 * Fixed.FRACUNIT, 32 * Fixed.FRACUNIT, Mobjs.ONFLOORZ, MobjType.BARREL);
        assertThat(next.id).isEqualTo(id);
    }

    @Test
    void tryMoveStepsUpOntoTheNextRoomAndCrossesTheSpecialLine() {
        World w = world();
        Actor mo = w.mobjs.spawnMobj(32 * Fixed.FRACUNIT, 32 * Fixed.FRACUNIT, Mobjs.ONFLOORZ, MobjType.PLAYER);
        // the destination box does not touch line 6, so the line is not "crossed" (vanilla checks only the end)
        assertThat(w.physics.tryMove(mo, 96 * Fixed.FRACUNIT, 32 * Fixed.FRACUNIT)).isTrue();
        assertThat(mo.subsector.sector.index).isEqualTo(1);
        assertThat(mo.floorZ).isEqualTo(16 * Fixed.FRACUNIT);
        assertThat(w.unimplemented.hits()).doesNotContainKey("mapspecial.crossSpecialLine");
        // back to the left room with a box touching the line: the special line is crossed
        assertThat(w.physics.tryMove(mo, 60 * Fixed.FRACUNIT, 32 * Fixed.FRACUNIT)).isTrue();
        assertThat(w.unimplemented.hits()).containsKey("mapspecial.crossSpecialLine");
        assertThat(w.physics.tryMove(mo, 96 * Fixed.FRACUNIT, 32 * Fixed.FRACUNIT)).isTrue();
        // into the outer wall: blocked
        assertThat(w.physics.tryMove(mo, 120 * Fixed.FRACUNIT, 32 * Fixed.FRACUNIT)).isFalse();
        assertThat(mo.x).isEqualTo(96 * Fixed.FRACUNIT);
    }

    @Test
    void xyMovementAppliesFrictionAndStopSpeed() {
        World w = world();
        Actor mo = w.mobjs.spawnMobj(32 * Fixed.FRACUNIT, 32 * Fixed.FRACUNIT, Mobjs.ONFLOORZ, MobjType.BARREL);
        mo.momX = Fixed.FRACUNIT;
        w.mobjs.xyMovement(mo);
        assertThat(mo.x).isEqualTo(33 * Fixed.FRACUNIT);
        assertThat(mo.momX).isEqualTo(0xe800); // FixedMul(FRACUNIT, FRICTION)

        mo.momX = 0xfff; // below STOPSPEED
        mo.momY = -0xfff;
        w.mobjs.xyMovement(mo);
        assertThat(mo.momX).isZero();
        assertThat(mo.momY).isZero();
    }

    @Test
    void xyMovementClampsMomentumAndSplitsLargeMoves() {
        World w = world();
        Actor mo = w.mobjs.spawnMobj(32 * Fixed.FRACUNIT, 32 * Fixed.FRACUNIT, Mobjs.ONFLOORZ, MobjType.BARREL);
        mo.flags |= doom.game.view.MobjFlags.NOCLIP | doom.game.view.MobjFlags.MISSILE; // no friction, no clipping
        mo.momX = 100 * Fixed.FRACUNIT;
        w.mobjs.xyMovement(mo);
        assertThat(mo.momX).isEqualTo(Mobjs.MAXMOVE);
        assertThat(mo.x).isEqualTo(32 * Fixed.FRACUNIT + Mobjs.MAXMOVE);
    }

    @Test
    void zMovementAppliesGravityTwiceOnTheFirstFallingTic() {
        World w = world();
        Actor mo = w.mobjs.spawnMobj(32 * Fixed.FRACUNIT, 32 * Fixed.FRACUNIT, 50 * Fixed.FRACUNIT, MobjType.BARREL);
        w.mobjs.zMovement(mo);
        assertThat(mo.momZ).isEqualTo(-2 * Mobjs.GRAVITY);
        w.mobjs.zMovement(mo);
        assertThat(mo.z).isEqualTo(48 * Fixed.FRACUNIT);
        assertThat(mo.momZ).isEqualTo(-3 * Mobjs.GRAVITY);
    }
}
