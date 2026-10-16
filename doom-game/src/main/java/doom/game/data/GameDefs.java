package doom.game.data;

import doom.game.view.AmmoType;
import doom.game.view.Weapon;

/**
 * The definition tables of one game session: states, actor types, weapons, sounds, sprite and music names, ammo
 * limits and the DEHACKED {@code Misc} values. Everything is mutable and owned by the session (ADR-014, contracts
 * section 10): {@link #createDefault()} rebuilds the tables from the DSL, so two sessions never share an entry.
 *
 * <p>The tables are flat arrays indexed by the global ids of {@link StateId}, {@link MobjType},
 * {@link doom.game.view.Weapon}, {@link doom.game.view.SoundId}, {@link SpriteId} and
 * {@link doom.game.view.MusicId}, exactly as in the original.
 */
public final class GameDefs {

    private final State[] states;
    private final ThingType[] things;
    private final WeaponInfo[] weapons;
    private final SoundInfo[] sounds;
    private final String[] spriteNames;
    private final String[] musicNames;
    private final int[] maxAmmo;
    private final int[] clipAmmo;
    private final MiscSettings misc;

    GameDefs(State[] states, ThingType[] things, WeaponInfo[] weapons, SoundInfo[] sounds, String[] spriteNames,
            String[] musicNames, int[] maxAmmo, int[] clipAmmo, MiscSettings misc) {
        this.states = states;
        this.things = things;
        this.weapons = weapons;
        this.sounds = sounds;
        this.spriteNames = spriteNames;
        this.musicNames = musicNames;
        this.maxAmmo = maxAmmo;
        this.clipAmmo = clipAmmo;
        this.misc = misc;
    }

    /** A fresh set of the vanilla DOOM v1.9 tables, built by the DSL. */
    public static GameDefs createDefault() {
        DefsBuilder b = new DefsBuilder();
        SpriteDefs.define(b);
        MonsterDefs.define(b);
        ProjectileDefs.define(b);
        PickupDefs.define(b);
        ObjectDefs.define(b);
        WeaponDefs.define(b);
        MiscStateDefs.define(b);
        SoundDefs.define(b);
        return b.build();
    }

    /** A deep copy (for example to snapshot the tables before a skill change). */
    public GameDefs copy() {
        State[] s = new State[states.length];
        for (int i = 0; i < s.length; i++) {
            s[i] = states[i].copy();
        }
        ThingType[] t = new ThingType[things.length];
        for (int i = 0; i < t.length; i++) {
            t[i] = things[i].copy();
        }
        WeaponInfo[] w = new WeaponInfo[weapons.length];
        for (int i = 0; i < w.length; i++) {
            w[i] = weapons[i].copy();
        }
        SoundInfo[] f = new SoundInfo[sounds.length];
        for (int i = 0; i < f.length; i++) {
            f[i] = sounds[i].copy();
        }
        return new GameDefs(s, t, w, f, spriteNames.clone(), musicNames.clone(), maxAmmo.clone(), clipAmmo.clone(),
                misc.copy());
    }

    /** {@code states[]}, indexed by {@link StateId}. The live array: edits are visible to the session. */
    public State[] states() {
        return states;
    }

    /** {@code mobjinfo[]}, indexed by {@link MobjType}. */
    public ThingType[] things() {
        return things;
    }

    /** {@code weaponinfo[]}, indexed by {@link Weapon}. */
    public WeaponInfo[] weapons() {
        return weapons;
    }

    /** {@code S_sfx[]}, indexed by {@link doom.game.view.SoundId}. */
    public SoundInfo[] sounds() {
        return sounds;
    }

    /** {@code sprnames[]}, indexed by {@link SpriteId}. */
    public String[] spriteNames() {
        return spriteNames;
    }

    /** Music names without the {@code D_} prefix, indexed by {@link doom.game.view.MusicId}; entry 0 is null. */
    public String[] musicNames() {
        return musicNames;
    }

    /** {@code maxammo[]}, indexed by {@link AmmoType}. */
    public int[] maxAmmo() {
        return maxAmmo;
    }

    /** {@code clipammo[]}, indexed by {@link AmmoType}. */
    public int[] clipAmmo() {
        return clipAmmo;
    }

    /** The DEHACKED {@code Misc} values. */
    public MiscSettings misc() {
        return misc;
    }
}
