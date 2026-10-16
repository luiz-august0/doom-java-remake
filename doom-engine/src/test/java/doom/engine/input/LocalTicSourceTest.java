package doom.engine.input;

import static org.assertj.core.api.Assertions.assertThat;

import doom.engine.config.Action;
import doom.engine.config.InputNames;
import doom.engine.config.KeyBindings;
import doom.game.api.GameState;
import doom.game.api.TicCmd;
import doom.game.view.GameView;
import doom.game.view.PlayerView;
import doom.game.view.Power;
import doom.game.view.Weapon;
import doom.platform.input.InputEvent;
import doom.platform.input.InputEventQueue;
import doom.platform.input.KeyCode;
import doom.wad.iwad.GameMission;
import doom.wad.iwad.GameMode;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalTicSourceTest {

    private final boolean[] owned = new boolean[Weapon.COUNT];
    private int ready = Weapon.PISTOL;
    private int pending = Weapon.NO_CHANGE;
    private boolean strength;
    private GameMission mission = GameMission.DOOM2;
    private GameMode mode = GameMode.COMMERCIAL;

    private LocalTicSource source;
    private final InputEventQueue queue = new InputEventQueue();
    private final TicCmd cmd = new TicCmd();

    @BeforeEach
    void setUp() {
        owned[Weapon.FIST] = true;
        owned[Weapon.PISTOL] = true;
        PlayerView player = (PlayerView) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {PlayerView.class}, (p, m, a) -> switch (m.getName()) {
                    case "weaponOwned" -> owned[(Integer) a[0]];
                    case "readyWeapon" -> ready;
                    case "pendingWeapon" -> pending;
                    case "power" -> (Integer) a[0] == Power.STRENGTH && strength ? 1000 : 0;
                    default -> throw new UnsupportedOperationException(m.getName());
                });
        GameView view = (GameView) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {GameView.class}, (p, m, a) -> switch (m.getName()) {
                    case "state" -> GameState.LEVEL;
                    case "consolePlayer" -> 0;
                    case "player" -> player;
                    case "mission" -> mission;
                    case "mode" -> mode;
                    default -> throw new UnsupportedOperationException(m.getName());
                });
        source = new LocalTicSource(view, KeyBindings.defaults(), InputSettings.defaults());
    }

    private void event(int type, int code) {
        queue.clear();
        InputEvent e = queue.add(type, 0);
        e.code = code;
        source.onEvent(e);
    }

    private void down(int key) {
        event(InputEvent.KEY_DOWN, key);
    }

    private void up(int key) {
        event(InputEvent.KEY_UP, key);
    }

    private void mouse(double dx, double dy) {
        queue.clear();
        InputEvent e = queue.add(InputEvent.MOUSE_MOVE, 0);
        e.dx = dx;
        e.dy = dy;
        source.onEvent(e);
    }

    private TicCmd next() {
        source.next(cmd);
        return cmd;
    }

    @Test
    void idleGivesAnEmptyCommand() {
        TicCmd c = next();
        assertThat(c.forwardMove).isZero();
        assertThat(c.sideMove).isZero();
        assertThat(c.angleTurn).isZero();
        assertThat(c.buttons).isZero();
    }

    @Test
    void walkAndRunSpeeds() {
        down(KeyCode.UPARROW);
        assertThat(next().forwardMove).isEqualTo((byte) 0x19);
        down(KeyCode.RSHIFT);
        assertThat(next().forwardMove).isEqualTo((byte) 0x32);
        up(KeyCode.UPARROW);
        down(KeyCode.DOWNARROW);
        assertThat(next().forwardMove).isEqualTo((byte) -0x32);
    }

    @Test
    void oppositeKeysCancelOut() {
        down(KeyCode.UPARROW);
        down(KeyCode.DOWNARROW);
        assertThat(next().forwardMove).isZero();
        down(',');
        down('.');
        assertThat(next().sideMove).isZero();
    }

    @Test
    void strafeKeysUseSidemoveTable() {
        down('.');
        assertThat(next().sideMove).isEqualTo((byte) 0x18);
        down(KeyCode.RSHIFT);
        assertThat(next().sideMove).isEqualTo((byte) 0x28);
    }

    @Test
    void turningAcceleratesAfterSixTics() {
        down(KeyCode.RIGHTARROW);
        for (int i = 1; i <= 5; i++) {
            assertThat(next().angleTurn).as("tic %d", i).isEqualTo((short) -320); // slow turn
        }
        assertThat(next().angleTurn).isEqualTo((short) -640);
        down(KeyCode.RSHIFT);
        assertThat(next().angleTurn).isEqualTo((short) -1280);
        up(KeyCode.RIGHTARROW);
        down(KeyCode.LEFTARROW);
        assertThat(next().angleTurn).as("switching direction keeps the acceleration").isEqualTo((short) 1280);
        up(KeyCode.LEFTARROW);
        next();
        down(KeyCode.LEFTARROW);
        assertThat(next().angleTurn).as("a tic without turning restarts it slow").isEqualTo((short) 320);
    }

    @Test
    void releasingTheTurnKeyRestartsTheSlowTurn() {
        down(KeyCode.LEFTARROW);
        for (int i = 0; i < 8; i++) {
            next();
        }
        up(KeyCode.LEFTARROW);
        next();
        down(KeyCode.LEFTARROW);
        assertThat(next().angleTurn).isEqualTo((short) 320);
    }

    @Test
    void strafeKeyTurnsTheTurnKeysIntoStrafing() {
        down(KeyCode.RALT);
        down(KeyCode.RIGHTARROW);
        TicCmd c = next();
        assertThat(c.sideMove).isEqualTo((byte) 0x18);
        assertThat(c.angleTurn).isZero();
        down(KeyCode.LEFTARROW);
        assertThat(next().sideMove).isZero();
    }

    @Test
    void forwardKeyAndMouseButtonAddUpAndClampAtMaxMove() {
        down(KeyCode.UPARROW);
        queue.clear();
        InputEvent e = queue.add(InputEvent.MOUSE_DOWN, 0);
        e.code = 2; // middle button = mousebforward
        source.onEvent(e);
        assertThat(next().forwardMove).isEqualTo((byte) 0x32); // 0x19 + 0x19, clamped to MAXPLMOVE
    }

    @Test
    void mouseTurnsAtEightUnitsPerCountAndCarriesTheFraction() {
        mouse(10, 0);
        assertThat(next().angleTurn).isEqualTo((short) -80);
        assertThat(next().angleTurn).as("mouse values are used once").isZero();
        // sensitivity 5 is (5+5)/10 = 1: no fraction. With sensitivity 0 half a count is lost per count.
        source.setSettings(new InputSettings(0, 0, true, false, 0.2, 10, 10));
        mouse(1, 0);
        assertThat(next().angleTurn).as("0.5 counts are not a whole unit yet").isZero();
        mouse(1, 0);
        assertThat(next().angleTurn).as("the carried half and the new half make one").isEqualTo((short) -8);
    }

    @Test
    void mouseStrafesTwiceTheCountsWhileStrafeIsHeld() {
        down(KeyCode.RALT);
        mouse(5, 0);
        TicCmd c = next();
        assertThat(c.sideMove).isEqualTo((byte) 10);
        assertThat(c.angleTurn).isZero();
    }

    @Test
    void mouseForwardMovesAndIsClamped() {
        mouse(0, -10); // pushing the mouse away from you
        assertThat(next().forwardMove).isEqualTo((byte) 10);
        mouse(0, 200);
        assertThat(next().forwardMove).isEqualTo((byte) -0x32);
    }

    @Test
    void verticalMouseCanBeSwitchedOff() {
        source.setSettings(new InputSettings(5, 5, false, false, 0.2, 10, 10));
        mouse(0, -10);
        assertThat(next().forwardMove).isZero();
    }

    @Test
    void pendingTurnPreviewsTheMouseTurnBeforeTheTic() {
        mouse(10, 0);
        assertThat(source.pendingTurn()).isEqualTo(-80 << 16);
        next();
        assertThat(source.pendingTurn()).isZero();
    }

    @Test
    void fireAndUseButtons() {
        down(KeyCode.RCTRL);
        assertThat(next().buttons & TicCmd.BT_ATTACK).isNotZero();
        down(' ');
        assertThat(next().buttons & (TicCmd.BT_ATTACK | TicCmd.BT_USE)).isEqualTo(TicCmd.BT_ATTACK | TicCmd.BT_USE);
        up(KeyCode.RCTRL);
        up(' ');
        assertThat(next().buttons).isZero();
    }

    @Test
    void weaponKeysSetTheChangeBits() {
        down('3');
        int b = next().buttons & 0xFF;
        assertThat(b & TicCmd.BT_CHANGE).isNotZero();
        assertThat((b & TicCmd.BT_WEAPONMASK) >> TicCmd.BT_WEAPONSHIFT).isEqualTo(2);
        up('3');
        down('8');
        assertThat((next().buttons & TicCmd.BT_WEAPONMASK) >> TicCmd.BT_WEAPONSHIFT).isEqualTo(7);
    }

    @Test
    void nextAndPreviousWeaponSkipWeaponsThatCannotBeSelected() {
        owned[Weapon.SHOTGUN] = true;
        owned[Weapon.CHAINSAW] = true;
        owned[Weapon.SUPERSHOTGUN] = true;
        // order: fist, chainsaw, pistol, shotgun, supershotgun(->shotgun), chaingun...
        ready = Weapon.PISTOL;
        queue.clear();
        InputEvent wheel = queue.add(InputEvent.WHEEL, 0);
        wheel.dy = -1; // WHEELDOWN = next
        source.onEvent(wheel);
        int b = next().buttons & 0xFF;
        assertThat(b & TicCmd.BT_CHANGE).isNotZero();
        assertThat((b & TicCmd.BT_WEAPONMASK) >> TicCmd.BT_WEAPONSHIFT).isEqualTo(Weapon.SHOTGUN);
        assertThat(next().buttons & TicCmd.BT_CHANGE).as("one change per press").isZero();

        // previous from the pistol: the chainsaw selects the fist's slot only when owned; fist is refused while
        // the chainsaw is owned without berserk, so the chainsaw (7) is chosen... which maps to the fist number
        queue.clear();
        InputEvent wheelUp = queue.add(InputEvent.WHEEL, 0);
        wheelUp.dy = 1;
        source.onEvent(wheelUp);
        int prev = (next().buttons & TicCmd.BT_WEAPONMASK) >> TicCmd.BT_WEAPONSHIFT;
        assertThat(prev).isEqualTo(Weapon.FIST);
    }

    @Test
    void doom1CannotSelectTheSuperShotgunOrUnownedWeapons() {
        mission = GameMission.DOOM;
        mode = GameMode.RETAIL;
        owned[Weapon.SUPERSHOTGUN] = true;
        owned[Weapon.SHOTGUN] = true;
        ready = Weapon.SHOTGUN;
        queue.clear();
        InputEvent wheel = queue.add(InputEvent.WHEEL, 0);
        wheel.dy = -1;
        source.onEvent(wheel);
        // next after the shotgun in the table is the super shotgun (refused), then the chaingun (not owned), plasma...
        // ... all unowned until the fist (refused: no chainsaw here, so allowed)
        int sel = (next().buttons & TicCmd.BT_WEAPONMASK) >> TicCmd.BT_WEAPONSHIFT;
        assertThat(sel).isEqualTo(Weapon.FIST);
    }

    @Test
    void pauseKeyIsReportedOnceAndNotTracked() {
        down(KeyCode.PAUSE);
        assertThat(source.takePauseRequest()).isTrue();
        assertThat(source.takePauseRequest()).isFalse();
        assertThat(next().buttons).isZero();
    }

    @Test
    void lowResTurnRoundsToHundredsAndCarriesTheError() {
        source.setLowResTurn(true);
        mouse(10, 0); // angleturn -80
        assertThat(next().angleTurn).isEqualTo((short) 0);
        mouse(10, 0); // -80 + carry(-80) = -160 -> (-160 + 128) & 0xff00 = 0xff00 = -256
        assertThat(next().angleTurn).isEqualTo((short) -256);
        mouse(0, 0); // carry is -160 - (-256) = 96 -> (96 + 128) & 0xff00 = 0
        assertThat(next().angleTurn).isEqualTo((short) 0);
    }

    @Test
    void gamepadSticksStayWithinTheTicCmdLimits() {
        queue.clear();
        InputEvent e = queue.add(InputEvent.PAD_AXIS, 0);
        e.code = 1; // left stick Y: up is negative
        e.value = -1.0;
        source.onEvent(e);
        assertThat(next().forwardMove).isEqualTo((byte) 0x19);
        source.setSettings(new InputSettings(5, 5, true, true, 0.2, 40, 10)); // always run, high sensitivity
        assertThat(next().forwardMove).isEqualTo((byte) 0x32);
        e = queue.add(InputEvent.PAD_AXIS, 0);
        e.code = 0; // left stick X: strafe right
        e.value = 1.0;
        source.onEvent(e);
        assertThat(next().sideMove).isEqualTo((byte) 0x28);
    }

    @Test
    void gamepadDeadzoneIgnoresSmallDeflection() {
        queue.clear();
        InputEvent e = queue.add(InputEvent.PAD_AXIS, 0);
        e.code = 1;
        e.value = -0.15;
        source.onEvent(e);
        assertThat(next().forwardMove).isZero();
    }

    @Test
    void padRightTriggerFires() {
        queue.clear();
        InputEvent e = queue.add(InputEvent.PAD_AXIS, 0);
        e.code = 5;
        e.value = 1.0;
        source.onEvent(e);
        assertThat(next().buttons & TicCmd.BT_ATTACK).isNotZero();
        e = queue.add(InputEvent.PAD_AXIS, 0);
        e.code = 5;
        e.value = -1.0;
        source.onEvent(e);
        assertThat(next().buttons & TicCmd.BT_ATTACK).isZero();
    }

    @Test
    void focusLossReleasesEverything() {
        down(KeyCode.UPARROW);
        queue.clear();
        source.onEvent(queue.add(InputEvent.FOCUS_LOST, 0));
        assertThat(next().forwardMove).isZero();
    }

    @Test
    void alwaysRunInvertsTheRunKey() {
        source.setSettings(new InputSettings(5, 5, true, true, 0.2, 10, 10));
        down(KeyCode.UPARROW);
        assertThat(next().forwardMove).isEqualTo((byte) 0x32);
        down(KeyCode.RSHIFT);
        assertThat(next().forwardMove).isEqualTo((byte) 0x19);
    }

    @Test
    void customBindingsApply() {
        source.setBindings(KeyBindings.defaults().with(Action.FORWARD, 'i'));
        down('i');
        assertThat(next().forwardMove).isEqualTo((byte) 0x19);
        up('i');
        down(KeyCode.UPARROW);
        assertThat(next().forwardMove).isZero();
        assertThat(InputNames.ofKey('i')).isEqualTo('i');
    }
}
