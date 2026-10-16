package doom.game.data;

import java.util.Arrays;

/**
 * The DEHACKED {@code Misc} values in effect (see {@link MiscSetting} for the names and vanilla defaults).
 * Mutable; one instance per {@link GameDefs}.
 */
public final class MiscSettings {

    private final int[] values;

    /** The vanilla values. */
    public MiscSettings() {
        MiscSetting[] all = MiscSetting.values();
        values = new int[all.length];
        for (MiscSetting s : all) {
            values[s.ordinal()] = s.defaultValue();
        }
    }

    private MiscSettings(int[] values) {
        this.values = values;
    }

    public int get(MiscSetting setting) {
        return values[setting.ordinal()];
    }

    public void set(MiscSetting setting, int value) {
        values[setting.ordinal()] = value;
    }

    /** Finds a setting by its DEHACKED name, ignoring case; {@code null} when there is none. */
    public static MiscSetting byDehName(String name) {
        for (MiscSetting s : MiscSetting.values()) {
            if (s.dehName().equalsIgnoreCase(name)) {
                return s;
            }
        }
        return null;
    }

    public MiscSettings copy() {
        return new MiscSettings(Arrays.copyOf(values, values.length));
    }

    /** {@code deh_initial_health}: health of a player starting anew. */
    public int initialHealth() {
        return get(MiscSetting.INITIAL_HEALTH);
    }

    /** {@code deh_initial_bullets}: bullets of a player starting anew. */
    public int initialBullets() {
        return get(MiscSetting.INITIAL_BULLETS);
    }

    /** {@code deh_max_health}: ceiling of health bonuses. */
    public int maxHealth() {
        return get(MiscSetting.MAX_HEALTH);
    }

    /** {@code deh_max_armor}: ceiling of armor bonuses. */
    public int maxArmor() {
        return get(MiscSetting.MAX_ARMOR);
    }

    /** {@code deh_green_armor_class}. */
    public int greenArmorClass() {
        return get(MiscSetting.GREEN_ARMOR_CLASS);
    }

    /** {@code deh_blue_armor_class}. */
    public int blueArmorClass() {
        return get(MiscSetting.BLUE_ARMOR_CLASS);
    }

    /** {@code deh_max_soulsphere}. */
    public int maxSoulsphere() {
        return get(MiscSetting.MAX_SOULSPHERE);
    }

    /** {@code deh_soulsphere_health}. */
    public int soulsphereHealth() {
        return get(MiscSetting.SOULSPHERE_HEALTH);
    }

    /** {@code deh_megasphere_health}. */
    public int megasphereHealth() {
        return get(MiscSetting.MEGASPHERE_HEALTH);
    }

    /** {@code deh_god_mode_health}: health set by IDDQD. */
    public int godModeHealth() {
        return get(MiscSetting.GOD_MODE_HEALTH);
    }

    /** {@code deh_idfa_armor}. */
    public int idfaArmor() {
        return get(MiscSetting.IDFA_ARMOR);
    }

    /** {@code deh_idfa_armor_class}. */
    public int idfaArmorClass() {
        return get(MiscSetting.IDFA_ARMOR_CLASS);
    }

    /** {@code deh_idkfa_armor}. */
    public int idkfaArmor() {
        return get(MiscSetting.IDKFA_ARMOR);
    }

    /** {@code deh_idkfa_armor_class}. */
    public int idkfaArmorClass() {
        return get(MiscSetting.IDKFA_ARMOR_CLASS);
    }

    /** {@code deh_bfg_cells_per_shot}. */
    public int bfgCellsPerShot() {
        return get(MiscSetting.BFG_CELLS_PER_SHOT);
    }

    /** {@code deh_species_infighting}: whether monsters of one species hurt each other. */
    public boolean speciesInfighting() {
        return get(MiscSetting.SPECIES_INFIGHTING) != 0;
    }
}
