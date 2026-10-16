package doom.game.data;

import doom.game.view.AmmoType;
import doom.game.view.SoundId;
import doom.game.view.Weapon;
import java.util.ArrayList;
import java.util.List;

/**
 * The builder behind the definition DSL ({@code MonsterDefs}, {@code WeaponDefs} ...). Every entry is placed at
 * its global index ({@link StateId}, {@link MobjType}, {@link SoundId} ...), so the DSL may group entries by actor
 * in any order; {@link #build()} fails unless every index of every table was defined exactly once (ADR-014).
 */
public final class DefsBuilder {

    /** The {@code link} of a sound that is not an alias. */
    public static final int NO_LINK = -1;

    private final State[] states = new State[StateId.COUNT];
    private final ThingType[] things = new ThingType[MobjType.COUNT];
    private final WeaponInfo[] weapons = new WeaponInfo[Weapon.COUNT];
    private final SoundInfo[] sounds = new SoundInfo[SoundId.COUNT];
    private final String[] sprites = new String[SpriteId.COUNT];
    private final String[] music = new String[doom.game.view.MusicId.COUNT];
    private final boolean[] musicDefined = new boolean[doom.game.view.MusicId.COUNT];
    private final int[] maxAmmo = new int[AmmoType.COUNT];
    private final int[] clipAmmo = new int[AmmoType.COUNT];
    private final boolean[] ammoDefined = new boolean[AmmoType.COUNT];

    /** Defines one state. */
    public DefsBuilder state(int id, int sprite, int frame, int tics, StateAction action, int nextState) {
        return state(id, sprite, frame, tics, action, nextState, 0, 0);
    }

    /** Defines one state with its {@code misc1} and {@code misc2}. */
    public DefsBuilder state(int id, int sprite, int frame, int tics, StateAction action, int nextState, int misc1,
            int misc2) {
        place(states, id, new State(sprite, frame, tics, action, nextState, misc1, misc2), "state");
        return this;
    }

    /** Starts the definition of an actor type; set its fields on the returned builder. */
    public ThingBuilder thing(int type) {
        ThingType t = new ThingType();
        place(things, type, t, "actor");
        return new ThingBuilder(t);
    }

    /** Defines a weapon. */
    public DefsBuilder weapon(int weapon, int ammo, int upState, int downState, int readyState, int atkState,
            int flashState) {
        place(weapons, weapon, new WeaponInfo(ammo, upState, downState, readyState, atkState, flashState), "weapon");
        return this;
    }

    /** Defines a sound effect. */
    public DefsBuilder sound(int id, String name, boolean singularity, int priority, int link, int pitch, int volume) {
        place(sounds, id, new SoundInfo(name, singularity, priority, link, pitch, volume), "sound");
        return this;
    }

    /** Defines a music name ({@code null} for {@link doom.game.view.MusicId#NONE}). */
    public DefsBuilder music(int id, String name) {
        if (musicDefined[id]) {
            throw new IllegalStateException("music " + id + " defined twice");
        }
        musicDefined[id] = true;
        music[id] = name;
        return this;
    }

    /** Defines a sprite name. */
    public DefsBuilder sprite(int id, String name) {
        place(sprites, id, name, "sprite");
        return this;
    }

    /** Defines the ammunition limits of one ammo type. */
    public DefsBuilder ammo(int type, int max, int clip) {
        if (ammoDefined[type]) {
            throw new IllegalStateException("ammo " + type + " defined twice");
        }
        ammoDefined[type] = true;
        maxAmmo[type] = max;
        clipAmmo[type] = clip;
        return this;
    }

    /** Verifies completeness and returns the tables, with vanilla {@code Misc} settings. */
    public GameDefs build() {
        requireComplete(states, "state");
        requireComplete(things, "actor");
        requireComplete(weapons, "weapon");
        requireComplete(sounds, "sound");
        requireComplete(sprites, "sprite");
        for (int i = 0; i < music.length; i++) {
            if (!musicDefined[i]) {
                throw new IllegalStateException("music " + i + " not defined");
            }
        }
        for (int i = 0; i < ammoDefined.length; i++) {
            if (!ammoDefined[i]) {
                throw new IllegalStateException("ammo " + i + " not defined");
            }
        }
        return new GameDefs(states, things, weapons, sounds, sprites, music, maxAmmo, clipAmmo, new MiscSettings());
    }

    private static <T> void place(T[] table, int id, T value, String what) {
        if (id < 0 || id >= table.length) {
            throw new IllegalStateException(what + " index " + id + " out of range 0.." + (table.length - 1));
        }
        if (table[id] != null) {
            throw new IllegalStateException(what + " " + id + " defined twice");
        }
        table[id] = value;
    }

    private static void requireComplete(Object[] table, String what) {
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < table.length; i++) {
            if (table[i] == null) {
                missing.add(i);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(missing.size() + " " + what + " entries not defined, first: " + missing.get(0));
        }
    }

    /** Fluent setter for the fields of one {@link ThingType}; unset fields are zero. */
    public static final class ThingBuilder {

        private final ThingType t;

        private ThingBuilder(ThingType t) {
            this.t = t;
        }

        public ThingBuilder doomedNum(int v) {
            t.doomedNum = v;
            return this;
        }

        public ThingBuilder spawnState(int v) {
            t.spawnState = v;
            return this;
        }

        public ThingBuilder spawnHealth(int v) {
            t.spawnHealth = v;
            return this;
        }

        public ThingBuilder seeState(int v) {
            t.seeState = v;
            return this;
        }

        public ThingBuilder seeSound(int v) {
            t.seeSound = v;
            return this;
        }

        public ThingBuilder reactionTime(int v) {
            t.reactionTime = v;
            return this;
        }

        public ThingBuilder attackSound(int v) {
            t.attackSound = v;
            return this;
        }

        public ThingBuilder painState(int v) {
            t.painState = v;
            return this;
        }

        public ThingBuilder painChance(int v) {
            t.painChance = v;
            return this;
        }

        public ThingBuilder painSound(int v) {
            t.painSound = v;
            return this;
        }

        public ThingBuilder meleeState(int v) {
            t.meleeState = v;
            return this;
        }

        public ThingBuilder missileState(int v) {
            t.missileState = v;
            return this;
        }

        public ThingBuilder deathState(int v) {
            t.deathState = v;
            return this;
        }

        public ThingBuilder xDeathState(int v) {
            t.xDeathState = v;
            return this;
        }

        public ThingBuilder deathSound(int v) {
            t.deathSound = v;
            return this;
        }

        public ThingBuilder speed(int v) {
            t.speed = v;
            return this;
        }

        public ThingBuilder radius(int v) {
            t.radius = v;
            return this;
        }

        public ThingBuilder height(int v) {
            t.height = v;
            return this;
        }

        public ThingBuilder mass(int v) {
            t.mass = v;
            return this;
        }

        public ThingBuilder damage(int v) {
            t.damage = v;
            return this;
        }

        public ThingBuilder activeSound(int v) {
            t.activeSound = v;
            return this;
        }

        public ThingBuilder flags(int v) {
            t.flags = v;
            return this;
        }

        public ThingBuilder raiseState(int v) {
            t.raiseState = v;
            return this;
        }
    }
}
