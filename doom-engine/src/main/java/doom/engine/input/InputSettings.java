package doom.engine.input;

import doom.engine.config.Config;
import doom.engine.config.Settings;

/**
 * Input tuning read from the configuration.
 *
 * @param mouseSensX     horizontal sensitivity on the vanilla scale ({@code (sens + 5) / 10})
 * @param mouseSensY     vertical sensitivity
 * @param mouseMoveForward vertical mouse movement walks forward and backward (otherwise ignored)
 * @param alwaysRun      run unless the run key is held
 * @param padDeadzone    gamepad dead zone 0..0.9
 * @param padMoveSens    {@code joystick_move_sensitivity}
 * @param padTurnSens    {@code joystick_turn_sensitivity}
 */
public record InputSettings(int mouseSensX, int mouseSensY, boolean mouseMoveForward, boolean alwaysRun,
                            double padDeadzone, int padMoveSens, int padTurnSens) {

    public static InputSettings defaults() {
        return from(Config.empty());
    }

    public static InputSettings from(Config c) {
        return new InputSettings(c.get(Settings.INPUT_MOUSE_SENS_X), c.get(Settings.INPUT_MOUSE_SENS_Y),
                c.get(Settings.INPUT_MOUSE_VERTICAL).equals("move"), c.get(Settings.INPUT_ALWAYS_RUN),
                c.get(Settings.PAD_DEADZONE), c.get(Settings.PAD_MOVE_SENS), c.get(Settings.PAD_TURN_SENS));
    }
}
