package doom.engine.input;

import doom.engine.TicCmdSource;
import doom.engine.config.Action;
import doom.engine.config.InputNames;
import doom.engine.config.KeyBindings;
import doom.game.api.GameState;
import doom.game.api.TicCmd;
import doom.game.view.GameView;
import doom.game.view.Power;
import doom.game.view.PlayerView;
import doom.game.view.Weapon;
import doom.math.Fixed;
import doom.math.annotation.VanillaRef;
import doom.platform.input.InputEvent;
import doom.wad.iwad.GameMission;
import doom.wad.iwad.GameMode;

/**
 * The local player's input: keeps the state of keys, mouse and gamepad and turns it into a {@link TicCmd} with the exact
 * semantics of {@code g_game.c G_BuildTiccmd} (walk and run speeds, two-stage accelerative turning, strafe,
 * mouse, weapon change bits, previous/next weapon, ticcmd limits).
 *
 * <p>Differences from vanilla that only concern input handling are listed in {@code docs/divergences/W2-D.md}:
 * mouse motion is accumulated between tics instead of keeping the last event, and the mouse turn since the last tic
 * is exposed ({@link #pendingTurn()}) for the per-frame camera (spec 04 section 7).
 *
 * <p>Main thread only. No allocation after construction.
 */
public final class LocalTicSource implements TicCmdSource {

    /** {@code forwardmove[]}: walk and run. */
    static final int[] FORWARD_MOVE = {0x19, 0x32};
    /** {@code sidemove[]}. */
    static final int[] SIDE_MOVE = {0x18, 0x28};
    /** {@code angleturn[]}: run, fast and slow (accelerative) turn. */
    static final int[] ANGLE_TURN = {640, 1280, 320};
    /** {@code MAXPLMOVE}. */
    static final int MAX_PL_MOVE = FORWARD_MOVE[1];
    /** {@code SLOWTURNTICS}. */
    static final int SLOW_TURN_TICS = 6;

    /** {@code weapon_order_table}: the weapons in switching order and the weapon number each one selects. */
    private static final int[] ORDER_WEAPON = {Weapon.FIST, Weapon.CHAINSAW, Weapon.PISTOL, Weapon.SHOTGUN,
        Weapon.SUPERSHOTGUN, Weapon.CHAINGUN, Weapon.MISSILE, Weapon.PLASMA, Weapon.BFG};
    private static final int[] ORDER_NUM = {Weapon.FIST, Weapon.FIST, Weapon.PISTOL, Weapon.SHOTGUN,
        Weapon.SHOTGUN, Weapon.CHAINGUN, Weapon.MISSILE, Weapon.PLASMA, Weapon.BFG};

    private static final double TRIGGER_THRESHOLD = 0.5;

    private final GameView view;
    private KeyBindings bindings;
    private InputSettings settings;

    private final boolean[] down = new boolean[InputNames.COUNT];
    /** Wheel "buttons" are pulses: released after the next command. */
    private boolean wheelPulse;

    private int turnHeld;
    private int nextWeapon;
    private boolean pauseRequested;

    private double mouseDx;
    private double mouseDy;

    private double padLeftX;
    private double padLeftY;
    private double padRightX;

    private boolean lowResTurn;
    private short carry;

    public LocalTicSource(GameView view, KeyBindings bindings, InputSettings settings) {
        this.view = view;
        this.bindings = bindings;
        this.settings = settings;
    }

    public void setBindings(KeyBindings bindings) {
        this.bindings = bindings;
    }

    public void setSettings(InputSettings settings) {
        this.settings = settings;
    }

    /**
     * Demo recording writes one byte of turn per tic: round the turn to a multiple of 256 and carry the error to the
     * next tic ({@code lowres_turn}).
     */
    public void setLowResTurn(boolean on) {
        this.lowResTurn = on;
        this.carry = 0;
    }

    /** Releases everything (focus lost, new game). */
    public void releaseAll() {
        java.util.Arrays.fill(down, false);
        mouseDx = 0;
        mouseDy = 0;
        padLeftX = 0;
        padLeftY = 0;
        padRightX = 0;
        nextWeapon = 0;
        turnHeld = 0;
    }

    /** Whether the pause key was pressed since the last call. */
    public boolean takePauseRequest() {
        boolean r = pauseRequested;
        pauseRequested = false;
        return r;
    }

    // ------------------------------------------------------------------ events

    /** Updates the input state from an event the UI did not consume (key and button releases always come here). */
    public void onEvent(InputEvent e) {
        switch (e.type) {
            case InputEvent.KEY_DOWN -> press(InputNames.ofKey(e.code), true);
            case InputEvent.KEY_UP -> press(InputNames.ofKey(e.code), false);
            case InputEvent.MOUSE_DOWN -> press(InputNames.ofMouseButton(e.code), true);
            case InputEvent.MOUSE_UP -> press(InputNames.ofMouseButton(e.code), false);
            case InputEvent.MOUSE_MOVE -> {
                mouseDx += e.dx;
                mouseDy += e.dy;
            }
            case InputEvent.WHEEL -> {
                if (e.dy > 0) {
                    pulse(InputNames.WHEEL_UP);
                } else if (e.dy < 0) {
                    pulse(InputNames.WHEEL_DOWN);
                }
            }
            case InputEvent.PAD_DOWN -> press(InputNames.ofPadButton(e.code), true);
            case InputEvent.PAD_UP -> press(InputNames.ofPadButton(e.code), false);
            case InputEvent.PAD_AXIS -> axis(e.code, e.value);
            case InputEvent.FOCUS_LOST -> releaseAll();
            default -> {
            }
        }
    }

    private void pulse(int id) {
        press(id, true);
        wheelPulse = true;
    }

    private void press(int id, boolean isDown) {
        if (id < 0) {
            return;
        }
        boolean wasDown = down[id];
        for (Action a : bindings.actionsFor(id)) {
            if (isDown && (!wasDown || id < InputNames.KEY_COUNT)) {
                // key repeat produces more KEY_DOWN, as in the original (the pause key then toggles repeatedly)
                if (a == Action.NEXT_WEAPON) {
                    nextWeapon = 1;
                } else if (a == Action.PREV_WEAPON) {
                    nextWeapon = -1;
                } else if (a == Action.PAUSE) {
                    pauseRequested = true;
                }
            }
        }
        boolean isPause = false;
        for (Action a : bindings.actionsFor(id)) {
            isPause |= a == Action.PAUSE;
        }
        if (!isPause) {
            down[id] = isDown; // the original does not track the pause key
        }
    }

    private void axis(int axis, double value) {
        switch (axis) {
            case 0 -> padLeftX = value;
            case 1 -> padLeftY = value;
            case 2 -> padRightX = value;
            case 4 -> trigger(InputNames.PAD_LT, value);
            case 5 -> trigger(InputNames.PAD_RT, value);
            default -> {
            }
        }
    }

    private void trigger(int id, double value) {
        boolean on = value > TRIGGER_THRESHOLD;
        if (on != down[id]) {
            press(id, on);
        }
    }

    private boolean isDown(Action action) {
        for (int id : bindings.inputs(action)) {
            if (down[id]) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ G_BuildTiccmd

    private double deadzoned(double v) {
        double a = Math.abs(v);
        double dz = settings.padDeadzone();
        if (a <= dz) {
            return 0;
        }
        double scaled = Math.min(1.0, (a - dz) / (1.0 - dz));
        return v < 0 ? -scaled : scaled;
    }

    private static int toFixed(double v) {
        return (int) Math.rint(v * Fixed.FRACUNIT);
    }

    /** Mouse counts converted with the vanilla {@code (sensitivity + 5) / 10}, truncated toward zero. */
    private static int scaledMouse(double raw, int sensitivity) {
        return (int) (raw * (sensitivity + 5) / 10.0);
    }

    /**
     * The mouse turn accumulated since the last command, as a BAM angle to add to the camera immediately
     * (spec 04 section 7). Zero while strafing.
     */
    public int pendingTurn() {
        if (isStrafing()) {
            return 0;
        }
        int units = scaledMouse(mouseDx, settings.mouseSensX());
        return (short) (-units * 8) << 16;
    }

    private boolean isStrafing() {
        return isDown(Action.STRAFE);
    }

    @Override
    @VanillaRef("g_game.c:G_BuildTiccmd")
    public void next(TicCmd cmd) {
        cmd.clear();

        boolean strafe = isStrafing();
        int speed = (isDown(Action.RUN) ^ settings.alwaysRun()) ? 1 : 0;

        int forward = 0;
        int side = 0;

        int joyTurn = toFixed(deadzoned(padRightX));
        int joyMove = toFixed(deadzoned(padLeftY));
        int joyStrafe = toFixed(deadzoned(padLeftX));
        int turnSens = settings.padTurnSens();
        int moveSens = settings.padMoveSens();

        // use two stage accelerative turning on the keyboard and joystick
        if (joyTurn != 0 || isDown(Action.TURN_RIGHT) || isDown(Action.TURN_LEFT)) {
            turnHeld++;
        } else {
            turnHeld = 0;
        }
        int tspeed = turnHeld < SLOW_TURN_TICS ? 2 : speed;

        int angleTurn = 0;

        // let movement keys cancel each other out
        if (strafe) {
            if (isDown(Action.TURN_RIGHT)) {
                side += SIDE_MOVE[speed];
            }
            if (isDown(Action.TURN_LEFT)) {
                side -= SIDE_MOVE[speed];
            }
            if (joyTurn != 0) {
                int j = joyTurn * moveSens / 10;
                j = Math.min(j, Fixed.FRACUNIT);
                j = Math.max(j, -Fixed.FRACUNIT);
                side += Fixed.mul(SIDE_MOVE[speed], j);
            }
        } else {
            if (isDown(Action.TURN_RIGHT)) {
                angleTurn -= ANGLE_TURN[tspeed];
            }
            if (isDown(Action.TURN_LEFT)) {
                angleTurn += ANGLE_TURN[tspeed];
            }
            if (joyTurn != 0) {
                // cubic response curve allows for finer control when stick deflection is small
                int j = Fixed.mul(Fixed.mul(joyTurn, joyTurn), joyTurn);
                j = j * turnSens / 10;
                angleTurn -= Fixed.mul(ANGLE_TURN[1], j);
            }
        }

        if (isDown(Action.FORWARD)) {
            forward += FORWARD_MOVE[speed];
        }
        if (isDown(Action.BACK)) {
            forward -= FORWARD_MOVE[speed];
        }
        if (joyMove != 0) {
            int j = joyMove * moveSens / 10;
            j = Math.min(j, Fixed.FRACUNIT);
            j = Math.max(j, -Fixed.FRACUNIT);
            forward -= Fixed.mul(FORWARD_MOVE[speed], j);
        }

        if (isDown(Action.STRAFE_LEFT)) {
            side -= SIDE_MOVE[speed];
        }
        if (isDown(Action.STRAFE_RIGHT)) {
            side += SIDE_MOVE[speed];
        }
        if (joyStrafe != 0) {
            int j = joyStrafe * moveSens / 10;
            j = Math.min(j, Fixed.FRACUNIT);
            j = Math.max(j, -Fixed.FRACUNIT);
            side += Fixed.mul(SIDE_MOVE[speed], j);
        }

        // buttons
        int buttons = 0;
        if (isDown(Action.FIRE)) {
            buttons |= TicCmd.BT_ATTACK;
        }
        if (isDown(Action.USE)) {
            buttons |= TicCmd.BT_USE;
        }

        // If the previous or next weapon button is pressed, nextWeapon is set to change weapons when we generate a
        // ticcmd. Choose a new weapon.
        if (view.state() == GameState.LEVEL && nextWeapon != 0) {
            int i = nextWeapon(nextWeapon);
            buttons |= TicCmd.BT_CHANGE;
            buttons |= i << TicCmd.BT_WEAPONSHIFT;
        } else {
            // Check weapon keys.
            for (int i = 0; i < 8; i++) {
                if (isDown(Action.weapon(i))) {
                    buttons |= TicCmd.BT_CHANGE;
                    buttons |= i << TicCmd.BT_WEAPONSHIFT;
                    break;
                }
            }
        }
        nextWeapon = 0;

        // mouse
        if (isDown(Action.MOUSE_FORWARD)) {
            forward += FORWARD_MOVE[speed];
        }
        if (isDown(Action.MOUSE_BACKWARD)) {
            forward -= FORWARD_MOVE[speed];
        }

        int mouseX = scaledMouse(mouseDx, settings.mouseSensX());
        int mouseY = settings.mouseMoveForward() ? -scaledMouse(mouseDy, settings.mouseSensY()) : 0;
        // keep the fraction that did not make a whole unit, so slow movements are not lost
        mouseDx -= mouseX * 10.0 / (settings.mouseSensX() + 5);
        mouseDy = settings.mouseMoveForward() ? mouseDy - (-mouseY) * 10.0 / (settings.mouseSensY() + 5) : 0;

        forward += mouseY;
        if (strafe) {
            side += mouseX * 2;
        } else {
            angleTurn -= mouseX * 0x8;
        }

        if (forward > MAX_PL_MOVE) {
            forward = MAX_PL_MOVE;
        } else if (forward < -MAX_PL_MOVE) {
            forward = -MAX_PL_MOVE;
        }
        if (side > MAX_PL_MOVE) {
            side = MAX_PL_MOVE;
        } else if (side < -MAX_PL_MOVE) {
            side = -MAX_PL_MOVE;
        }

        cmd.forwardMove = (byte) forward;
        cmd.sideMove = (byte) side;
        cmd.angleTurn = (short) angleTurn;
        cmd.buttons = (byte) buttons;

        // low-res turning
        if (lowResTurn) {
            short desired = (short) (cmd.angleTurn + carry);
            // round angleturn to the nearest 256 unit boundary for recording demos with single byte values for turn
            cmd.angleTurn = (short) ((desired + 128) & 0xff00);
            // carry forward the error from the reduced resolution to the next tic
            carry = (short) (desired - cmd.angleTurn);
        }

        if (wheelPulse) {
            down[InputNames.WHEEL_UP] = false;
            down[InputNames.WHEEL_DOWN] = false;
            wheelPulse = false;
        }
    }

    // ------------------------------------------------------------------ weapons

    @VanillaRef("g_game.c:WeaponSelectable")
    private boolean weaponSelectable(int weapon) {
        PlayerView player = view.player(view.consolePlayer());
        // Can't select the super shotgun in Doom 1.
        if (weapon == Weapon.SUPERSHOTGUN && logicalMissionIsDoom()) {
            return false;
        }
        // These weapons aren't available in shareware.
        if ((weapon == Weapon.PLASMA || weapon == Weapon.BFG) && view.mission() == GameMission.DOOM
                && view.mode() == GameMode.SHAREWARE) {
            return false;
        }
        // Can't select a weapon if we don't own it.
        if (!player.weaponOwned(weapon)) {
            return false;
        }
        // Can't select the fist if we have the chainsaw, unless we also have the berserk pack.
        return !(weapon == Weapon.FIST && player.weaponOwned(Weapon.CHAINSAW)
                && player.power(Power.STRENGTH) == 0);
    }

    private boolean logicalMissionIsDoom() {
        return view.mission() == GameMission.DOOM;
    }

    @VanillaRef("g_game.c:G_NextWeapon")
    private int nextWeapon(int direction) {
        PlayerView player = view.player(view.consolePlayer());
        int weapon = player.pendingWeapon() == Weapon.NO_CHANGE ? player.readyWeapon() : player.pendingWeapon();
        int n = ORDER_WEAPON.length;
        int i = 0;
        while (i < n && ORDER_WEAPON[i] != weapon) {
            i++;
        }
        if (i >= n) {
            throw new doom.math.DoomFatalException(
                    "Internal error: weapon " + weapon + " not present in weapon_order_table");
        }
        // Switch weapon. Don't loop forever.
        int start = i;
        do {
            i += direction;
            i = (i + n) % n;
        } while (i != start && !weaponSelectable(ORDER_WEAPON[i]));
        return ORDER_NUM[i];
    }
}
