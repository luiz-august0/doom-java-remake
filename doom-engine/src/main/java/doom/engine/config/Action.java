package doom.engine.config;

/**
 * An action the player can bind inputs to (spec 07 section 3). The names mirror the variables of
 * {@code g_game.c G_BuildTiccmd}, so that the ticcmd builder keeps the original structure. Each action has a
 * configuration key {@code key.<name>} holding a comma separated list of input names (see {@link InputNames}).
 */
public enum Action {
    /** {@code key_up}. */
    FORWARD("forward", "W, UP", "Walk forward"),
    /** {@code key_down}. */
    BACK("back", "S, DOWN", "Walk backward"),
    /** {@code key_left}. */
    TURN_LEFT("turnLeft", "LEFT", "Turn left (strafes while the strafe key is held)"),
    /** {@code key_right}. */
    TURN_RIGHT("turnRight", "RIGHT", "Turn right (strafes while the strafe key is held)"),
    /** {@code key_strafeleft}. */
    STRAFE_LEFT("strafeLeft", "A, COMMA", "Strafe left"),
    /** {@code key_straferight}. */
    STRAFE_RIGHT("strafeRight", "D, PERIOD", "Strafe right"),
    /** {@code key_strafe} / {@code mousebstrafe}. */
    STRAFE("strafe", "ALT, MOUSE2", "Strafe on (turning inputs strafe instead)"),
    /** {@code key_speed} / {@code mousebspeed}. */
    RUN("run", "SHIFT, PAD_LT", "Run"),
    /** {@code key_fire} / {@code mousebfire}. */
    FIRE("fire", "CTRL, MOUSE1, PAD_RT", "Fire the weapon"),
    /** {@code key_use} / {@code mousebuse}. */
    USE("use", "E, SPACE, PAD_A", "Use (open doors, press switches)"),
    /** {@code mousebforward}: a mouse button that walks forward. */
    MOUSE_FORWARD("mouseForward", "MOUSE3", "Walk forward with a mouse button"),
    /** {@code mousebbackward}. */
    MOUSE_BACKWARD("mouseBackward", "", "Walk backward with a mouse button"),
    WEAPON_1("weapon1", "1", "Select weapon 1 (fist, chainsaw)"),
    WEAPON_2("weapon2", "2", "Select weapon 2 (pistol)"),
    WEAPON_3("weapon3", "3", "Select weapon 3 (shotgun, super shotgun)"),
    WEAPON_4("weapon4", "4", "Select weapon 4 (chaingun)"),
    WEAPON_5("weapon5", "5", "Select weapon 5 (rocket launcher)"),
    WEAPON_6("weapon6", "6", "Select weapon 6 (plasma rifle)"),
    WEAPON_7("weapon7", "7", "Select weapon 7 (BFG 9000)"),
    WEAPON_8("weapon8", "8", "Select weapon 8 (chainsaw)"),
    /** {@code key_nextweapon}. */
    NEXT_WEAPON("nextWeapon", "WHEELDOWN, PAD_RB", "Select the next weapon"),
    /** {@code key_prevweapon}. */
    PREV_WEAPON("prevWeapon", "WHEELUP, PAD_LB", "Select the previous weapon"),
    /** {@code key_pause}. */
    PAUSE("pause", "PAUSE", "Pause the game");

    private final String keyName;
    private final String defaultInputs;
    private final String description;

    Action(String keyName, String defaultInputs, String description) {
        this.keyName = keyName;
        this.defaultInputs = defaultInputs;
        this.description = description;
    }

    /** The configuration key, e.g. {@code key.forward}. */
    public String configKey() {
        return "key." + keyName;
    }

    /** Default bindings as written in the configuration file. */
    public String defaultInputs() {
        return defaultInputs;
    }

    public String description() {
        return description;
    }

    /** The action for weapon key {@code n} (0..7), i.e. {@code WEAPON_1} for 0. */
    public static Action weapon(int n) {
        return values()[WEAPON_1.ordinal() + n];
    }
}
