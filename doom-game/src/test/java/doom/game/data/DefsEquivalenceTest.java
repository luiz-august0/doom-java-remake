package doom.game.data;

import static org.assertj.core.api.Assertions.assertThat;

import doom.game.view.AmmoType;
import doom.game.view.MusicId;
import doom.game.view.SoundId;
import doom.game.view.Weapon;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ADR-014: the tables built by the DSL equal, field by field, the original tables of {@code info.c},
 * {@code sounds.c}, {@code d_items.c}, {@code p_inter.c} and Chocolate Doom's strings, cheats and Misc defaults,
 * as captured in the committed snapshot {@code original-defs.txt}.
 */
class DefsEquivalenceTest {

    private static final OriginalSnapshot ORIGINAL = OriginalSnapshot.load();

    /** The 23 fields of {@code mobjinfo_t} in declaration order. */
    static int[] thingFields(ThingType t) {
        return new int[] {t.doomedNum, t.spawnState, t.spawnHealth, t.seeState, t.seeSound, t.reactionTime,
            t.attackSound, t.painState, t.painChance, t.painSound, t.meleeState, t.missileState, t.deathState,
            t.xDeathState, t.deathSound, t.speed, t.radius, t.height, t.mass, t.damage, t.activeSound, t.flags,
            t.raiseState};
    }

    static int[] weaponFields(WeaponInfo w) {
        return new int[] {w.ammo, w.upState, w.downState, w.readyState, w.atkState, w.flashState};
    }

    @Test
    void tableSizesAreTheOriginalOnes() {
        GameDefs defs = GameDefs.createDefault();
        assertThat(defs.states()).hasSize(967).hasSize(StateId.COUNT);
        assertThat(defs.things()).hasSize(137).hasSize(MobjType.COUNT);
        assertThat(defs.weapons()).hasSize(9);
        assertThat(defs.sounds()).hasSize(109);
        assertThat(defs.spriteNames()).hasSize(138).hasSize(SpriteId.COUNT);
        assertThat(defs.musicNames()).hasSize(68);
        assertThat(StateAction.values()).hasSize(75);
    }

    @Test
    void everyStateMatches() {
        GameDefs defs = GameDefs.createDefault();
        assertThat(ORIGINAL.states).hasSize(defs.states().length);
        for (OriginalSnapshot.StateRow o : ORIGINAL.states) {
            State s = defs.states()[o.index()];
            String where = "state " + o.index() + " " + o.name();
            assertThat(s.sprite).as(where + " sprite").isEqualTo(o.sprite());
            assertThat(s.frame).as(where + " frame").isEqualTo(o.frame());
            assertThat(s.tics).as(where + " tics").isEqualTo(o.tics());
            assertThat(s.action.name()).as(where + " action").isEqualTo(o.action());
            assertThat(s.nextState).as(where + " next").isEqualTo(o.next());
            assertThat(s.misc1).as(where + " misc1").isEqualTo(o.misc1());
            assertThat(s.misc2).as(where + " misc2").isEqualTo(o.misc2());
        }
    }

    @Test
    void everyActorMatches() {
        GameDefs defs = GameDefs.createDefault();
        assertThat(ORIGINAL.things).hasSize(defs.things().length);
        for (OriginalSnapshot.ThingRow o : ORIGINAL.things) {
            assertThat(thingFields(defs.things()[o.index()])).as("actor " + o.index() + " " + o.name())
                    .containsExactly(o.v());
        }
    }

    @Test
    void everyWeaponAndAmmoLimitMatches() {
        GameDefs defs = GameDefs.createDefault();
        for (OriginalSnapshot.WeaponRow o : ORIGINAL.weapons) {
            assertThat(weaponFields(defs.weapons()[o.index()])).as("weapon " + o.index()).containsExactly(o.v());
        }
        for (int i = 0; i < ORIGINAL.ammo.size(); i++) {
            assertThat(defs.maxAmmo()[i]).as("max ammo " + i).isEqualTo(ORIGINAL.ammo.get(i)[0]);
            assertThat(defs.clipAmmo()[i]).as("clip ammo " + i).isEqualTo(ORIGINAL.ammo.get(i)[1]);
        }
        assertThat(ORIGINAL.ammo).hasSize(AmmoType.COUNT);
    }

    @Test
    void everySoundSpriteAndMusicNameMatches() {
        GameDefs defs = GameDefs.createDefault();
        for (OriginalSnapshot.SoundRow o : ORIGINAL.sounds) {
            SoundInfo s = defs.sounds()[o.index()];
            String where = "sound " + o.index() + " " + o.name();
            assertThat(s.name()).as(where).isEqualTo(o.name());
            assertThat(s.singularity).as(where + " singularity").isEqualTo(o.singularity());
            assertThat(s.priority).as(where + " priority").isEqualTo(o.priority());
            assertThat(s.link).as(where + " link").isEqualTo(o.link());
            assertThat(s.pitch).as(where + " pitch").isEqualTo(o.pitch());
            assertThat(s.volume).as(where + " volume").isEqualTo(o.volume());
        }
        assertThat(List.of(defs.spriteNames())).isEqualTo(ORIGINAL.sprites);
        assertThat(java.util.Arrays.asList(defs.musicNames())).isEqualTo(ORIGINAL.music);
    }

    @Test
    void stringsCheatsAndMiscDefaultsMatch() {
        GameStrings strings = GameStrings.createDefault();
        GameDefs defs = GameDefs.createDefault();
        assertThat(strings.keys()).containsExactlyElementsOf(ORIGINAL.strings.stream().map(r -> r.key()).toList());
        for (OriginalSnapshot.StringRow o : ORIGINAL.strings) {
            assertThat(strings.original(o.key())).as(o.key()).isEqualTo(o.text());
            assertThat(strings.get(o.key())).as(o.key()).isEqualTo(o.text());
            assertThat(strings.isBexMnemonic(o.key())).as(o.key() + " bex").isEqualTo(o.bex());
        }
        assertThat(CheatId.values()).hasSize(ORIGINAL.cheats.size());
        for (OriginalSnapshot.CheatRow o : ORIGINAL.cheats) {
            CheatId id = CheatId.values()[o.index()];
            assertThat(id.dehName()).isEqualTo(o.dehName());
            assertThat(id.parameterChars()).as(o.dehName()).isEqualTo(o.parameterChars());
            assertThat(strings.cheat(id).sequence()).as(o.dehName()).isEqualTo(o.sequence());
            assertThat(strings.cheat(id).originalLength()).isEqualTo(o.sequence().length());
        }
        assertThat(MiscSetting.values()).hasSize(ORIGINAL.misc.size());
        for (int i = 0; i < ORIGINAL.misc.size(); i++) {
            MiscSetting m = MiscSetting.values()[i];
            assertThat(m.dehName()).isEqualTo(ORIGINAL.misc.get(i).dehName());
            assertThat(defs.misc().get(m)).as(m.dehName()).isEqualTo(ORIGINAL.misc.get(i).defaultValue());
        }
        assertThat(defs.misc().initialHealth()).isEqualTo(100);
        assertThat(defs.misc().initialBullets()).isEqualTo(50);
        assertThat(defs.misc().bfgCellsPerShot()).isEqualTo(40);
        assertThat(defs.misc().speciesInfighting()).isFalse();
    }

    @Test
    void idConstantsFollowTheOriginalNames() throws ReflectiveOperationException {
        for (OriginalSnapshot.StateRow o : ORIGINAL.states) {
            assertThat(constant(StateId.class, o.name().substring(2))).as(o.name()).isEqualTo(o.index());
        }
        for (OriginalSnapshot.ThingRow o : ORIGINAL.things) {
            assertThat(constant(MobjType.class, o.name().substring(3))).as(o.name()).isEqualTo(o.index());
        }
        for (int i = 0; i < ORIGINAL.sprites.size(); i++) {
            assertThat(constant(SpriteId.class, ORIGINAL.sprites.get(i))).isEqualTo(i);
        }
        // The view constants are what the presentation uses; they must agree with the table order.
        for (OriginalSnapshot.SoundRow o : ORIGINAL.sounds) {
            assertThat(constant(SoundId.class, o.name().toUpperCase())).as("SoundId " + o.name()).isEqualTo(o.index());
        }
        for (int i = 1; i < ORIGINAL.music.size(); i++) {
            assertThat(constant(MusicId.class, ORIGINAL.music.get(i).toUpperCase())).as("MusicId " + i).isEqualTo(i);
        }
        assertThat(SoundId.COUNT).isEqualTo(ORIGINAL.sounds.size());
        assertThat(MusicId.COUNT).isEqualTo(ORIGINAL.music.size());
        assertThat(Weapon.COUNT).isEqualTo(ORIGINAL.weapons.size());
    }

    @Test
    void everySessionGetsItsOwnCopy() {
        GameDefs a = GameDefs.createDefault();
        GameDefs b = GameDefs.createDefault();
        a.states()[StateId.POSS_STND].tics = 99;
        a.things()[MobjType.POSSESSED].spawnHealth = 1;
        a.misc().set(MiscSetting.INITIAL_HEALTH, 5);
        assertThat(b.states()[StateId.POSS_STND].tics).isEqualTo(10);
        assertThat(b.things()[MobjType.POSSESSED].spawnHealth).isEqualTo(20);
        assertThat(b.misc().initialHealth()).isEqualTo(100);

        GameDefs c = a.copy();
        c.states()[StateId.POSS_STND].tics = 7;
        assertThat(a.states()[StateId.POSS_STND].tics).isEqualTo(99);
        assertThat(c.misc().initialHealth()).isEqualTo(5);

        GameStrings s1 = GameStrings.createDefault();
        GameStrings s2 = GameStrings.createDefault();
        s1.replace(s1.original("QUITMSG"), "bye");
        s1.cheat(CheatId.GOD).setSequence("x");
        assertThat(s1.get("QUITMSG")).isEqualTo("bye");
        assertThat(s2.get("QUITMSG")).isEqualTo(s2.original("QUITMSG"));
        assertThat(s2.cheat(CheatId.GOD).sequence()).isEqualTo("iddqd");
    }

    @Test
    void theBuilderRejectsHolesAndDuplicates() {
        DefsBuilder b = new DefsBuilder();
        b.state(StateId.NULL, 0, 0, -1, StateAction.NONE, StateId.NULL);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> b.state(StateId.NULL, 0, 0, -1, StateAction.NONE, 0))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("defined twice");
        org.assertj.core.api.Assertions.assertThatThrownBy(b::build).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not defined");
    }

    private static int constant(Class<?> type, String name) throws ReflectiveOperationException {
        Field f = type.getField(name);
        return f.getInt(null);
    }
}
