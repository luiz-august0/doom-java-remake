package doom.codegen.defs;

import java.util.List;

/**
 * The definition tables exactly as the original sources declare them. Indices are positions in the lists.
 *
 * @param spriteEnum  {@code spritenum_t} names ({@code SPR_TROO} ...), without {@code NUMSPRITES}
 * @param spriteNames {@code sprnames[]}
 * @param stateEnum   {@code statenum_t} names ({@code S_NULL} ...)
 * @param states      {@code states[]}
 * @param actions     the {@code A_*} functions declared by {@code info.c}, in declaration order
 * @param mobjEnum    {@code mobjtype_t} names ({@code MT_PLAYER} ...)
 * @param things      {@code mobjinfo[]}
 * @param ammoEnum    {@code ammotype_t} names, including {@code NUMAMMO} and {@code am_noammo}
 * @param weapons     {@code weaponinfo[]}
 * @param maxAmmo     {@code maxammo[]} ({@code p_inter.c})
 * @param clipAmmo    {@code clipammo[]} ({@code p_inter.c})
 * @param sfxEnum     {@code sfxenum_t} names ({@code sfx_None} ...)
 * @param sounds      {@code S_sfx[]}
 * @param musicEnum   {@code musicenum_t} names ({@code mus_None} ...)
 * @param musicNames  {@code S_music[].name} ({@code null} for entry 0)
 * @param strings     text strings with their lookup keys
 * @param cheats      cheat sequences in the order of Chocolate Doom's DEHACKED {@code Cheat} table
 * @param misc        DEHACKED {@code Misc} values and their vanilla defaults
 */
record OriginalDefs(
        List<String> spriteEnum,
        List<String> spriteNames,
        List<String> stateEnum,
        List<StateRow> states,
        List<String> actions,
        List<String> mobjEnum,
        List<ThingRow> things,
        List<String> ammoEnum,
        List<WeaponRow> weapons,
        int[] maxAmmo,
        int[] clipAmmo,
        List<String> sfxEnum,
        List<SoundRow> sounds,
        List<String> musicEnum,
        List<String> musicNames,
        List<StringEntry> strings,
        List<CheatRow> cheats,
        List<MiscRow> misc) {

    /** Field names of {@code mobjinfo_t}, in declaration order. */
    static final List<String> THING_FIELDS = List.of(
            "doomednum", "spawnstate", "spawnhealth", "seestate", "seesound", "reactiontime", "attacksound",
            "painstate", "painchance", "painsound", "meleestate", "missilestate", "deathstate", "xdeathstate",
            "deathsound", "speed", "radius", "height", "mass", "damage", "activesound", "flags", "raisestate");

    /** Field names of {@code weaponinfo_t}, in declaration order. */
    static final List<String> WEAPON_FIELDS = List.of(
            "ammo", "upstate", "downstate", "readystate", "atkstate", "flashstate");

    /** One {@code state_t}. {@code action} is the C function name, or {@code null} for {@code {NULL}}. */
    record StateRow(int index, String name, int sprite, int frame, int tics, String action, int next,
            int misc1, int misc2) {
    }

    /**
     * One {@code mobjinfo_t}.
     *
     * @param values      the 23 evaluated fields, in {@link #THING_FIELDS} order
     * @param expressions the 23 initializer expressions as written (tokens joined without spaces)
     */
    record ThingRow(int index, String name, List<Integer> values, List<String> expressions) {

        int get(String field) {
            return values.get(THING_FIELDS.indexOf(field));
        }

        String expression(String field) {
            return expressions.get(THING_FIELDS.indexOf(field));
        }
    }

    /** One {@code weaponinfo_t}; {@code values} in {@link #WEAPON_FIELDS} order. */
    record WeaponRow(int index, List<Integer> values, List<String> expressions) {

        int get(String field) {
            return values.get(WEAPON_FIELDS.indexOf(field));
        }
    }

    /** One {@code sfxinfo_t}: {@code link} is a sound index or -1. */
    record SoundRow(int index, String name, boolean singularity, int priority, int link, int pitch, int volume) {
    }

    /**
     * One text string.
     *
     * @param key    lookup key: the BEX mnemonic when there is one, otherwise the C macro or a descriptive name
     * @param text   the original text
     * @param bex    whether Chocolate Doom's BEX {@code [STRINGS]} parser recognizes {@code key}
     * @param origin where the string comes from, for documentation
     */
    record StringEntry(String key, String text, boolean bex, String origin) {
    }

    /**
     * One cheat sequence.
     *
     * @param dehName        name in the DEHACKED {@code Cheat} section
     * @param variable       C variable ({@code cheat_mus}, {@code cheat_powerup[0]} ...)
     * @param sequence       the sequence typed by the player, without parameters
     * @param parameterChars number of parameter characters typed after the sequence
     */
    record CheatRow(int index, String dehName, String variable, String sequence, int parameterChars) {
    }

    /** One DEHACKED {@code Misc} value: its DEHACKED name, the Chocolate variable and the vanilla default. */
    record MiscRow(String dehName, String variable, int defaultValue) {
    }
}
