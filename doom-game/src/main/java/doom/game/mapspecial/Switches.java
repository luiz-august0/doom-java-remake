package doom.game.mapspecial;

import doom.game.api.SoundOrigins;
import doom.game.view.SoundId;
import doom.game.world.Line;
import doom.game.world.Side;
import doom.game.world.World;
import doom.math.DoomFatalException;
import doom.math.annotation.VanillaRef;
import java.util.Arrays;

/** {@code p_switch.c}: wall switches and buttons. */
final class Switches {

    /** {@code BUTTONTIME}: one second, in tics. */
    static final int BUTTONTIME = 35;
    /** {@code MAXBUTTONS}: 4 players, 4 buttons each at once, max. A fifth is a fatal error. */
    static final int MAXBUTTONS = 16;

    /** {@code bwhere_e}. */
    private static final int TOP = 0;
    private static final int MIDDLE = 1;
    private static final int BOTTOM = 2;

    /** {@code alphSwitchList[]}: name 1, name 2, episode. */
    private static final String[][] ALPH_SWITCH_LIST = {
        // Doom shareware episode 1 switches
        {"SW1BRCOM", "SW2BRCOM", "1"},
        {"SW1BRN1", "SW2BRN1", "1"},
        {"SW1BRN2", "SW2BRN2", "1"},
        {"SW1BRNGN", "SW2BRNGN", "1"},
        {"SW1BROWN", "SW2BROWN", "1"},
        {"SW1COMM", "SW2COMM", "1"},
        {"SW1COMP", "SW2COMP", "1"},
        {"SW1DIRT", "SW2DIRT", "1"},
        {"SW1EXIT", "SW2EXIT", "1"},
        {"SW1GRAY", "SW2GRAY", "1"},
        {"SW1GRAY1", "SW2GRAY1", "1"},
        {"SW1METAL", "SW2METAL", "1"},
        {"SW1PIPE", "SW2PIPE", "1"},
        {"SW1SLAD", "SW2SLAD", "1"},
        {"SW1STARG", "SW2STARG", "1"},
        {"SW1STON1", "SW2STON1", "1"},
        {"SW1STON2", "SW2STON2", "1"},
        {"SW1STONE", "SW2STONE", "1"},
        {"SW1STRTN", "SW2STRTN", "1"},
        // Doom registered episodes 2&3 switches
        {"SW1BLUE", "SW2BLUE", "2"},
        {"SW1CMT", "SW2CMT", "2"},
        {"SW1GARG", "SW2GARG", "2"},
        {"SW1GSTON", "SW2GSTON", "2"},
        {"SW1HOT", "SW2HOT", "2"},
        {"SW1LION", "SW2LION", "2"},
        {"SW1SATYR", "SW2SATYR", "2"},
        {"SW1SKIN", "SW2SKIN", "2"},
        {"SW1VINE", "SW2VINE", "2"},
        {"SW1WOOD", "SW2WOOD", "2"},
        // Doom II switches
        {"SW1PANEL", "SW2PANEL", "3"},
        {"SW1ROCK", "SW2ROCK", "3"},
        {"SW1MET2", "SW2MET2", "3"},
        {"SW1WDMET", "SW2WDMET", "3"},
        {"SW1BRIK", "SW2BRIK", "3"},
        {"SW1MOD1", "SW2MOD1", "3"},
        {"SW1ZIM", "SW2ZIM", "3"},
        {"SW1STON6", "SW2STON6", "3"},
        {"SW1TEK", "SW2TEK", "3"},
        {"SW1MARB", "SW2MARB", "3"},
        {"SW1SKULL", "SW2SKULL", "3"},
    };

    private final World world;

    /** {@code switchlist[MAXSWITCHES * 2]}: texture numbers, pairs (off, on). */
    private int[] switchList = new int[0];
    private int numSwitches;

    /** {@code button_t}: parallel arrays of the fields (a slot is free when its timer is 0). */
    private final Line[] buttonLine = new Line[MAXBUTTONS];
    private final int[] buttonWhere = new int[MAXBUTTONS];
    private final int[] buttonTexture = new int[MAXBUTTONS];
    private final int[] buttonTimer = new int[MAXBUTTONS];

    Switches(World world) {
        this.world = world;
    }

    /** Save games: per slot line index (-1 none), where, texture, timer. */
    int[] saveButtons() {
        int[] out = new int[MAXBUTTONS * 4];
        for (int i = 0; i < MAXBUTTONS; i++) {
            out[i * 4] = buttonLine[i] == null ? -1 : buttonLine[i].index;
            out[i * 4 + 1] = buttonWhere[i];
            out[i * 4 + 2] = buttonTexture[i];
            out[i * 4 + 3] = buttonTimer[i];
        }
        return out;
    }

    /** Save games: the inverse of {@link #saveButtons()}. */
    void restoreButtons(int[] data) {
        if (data.length != MAXBUTTONS * 4) {
            throw new IllegalArgumentException("button table has " + data.length + " values");
        }
        for (int i = 0; i < MAXBUTTONS; i++) {
            int line = data[i * 4];
            if (line < -1 || line >= world.level.lines.length) {
                throw new IllegalArgumentException("button line " + line + " out of range");
            }
            buttonLine[i] = line < 0 ? null : world.level.lines[line];
            buttonWhere[i] = data[i * 4 + 1];
            buttonTexture[i] = data[i * 4 + 2];
            buttonTimer[i] = data[i * 4 + 3];
        }
    }

    /** {@code P_SpawnSpecials}: {@code memset(&buttonlist[i], 0, sizeof(button_t))}. */
    void reset() {
        Arrays.fill(buttonLine, null);
        Arrays.fill(buttonWhere, 0);
        Arrays.fill(buttonTexture, 0);
        Arrays.fill(buttonTimer, 0);
    }

    /** {@code P_InitSwitchList}: only called at game initialization. */
    @VanillaRef("p_switch.c:P_InitSwitchList")
    void init() {
        // Note that this is called "episode" here but it's actually something quite different. As we progress from
        // Shareware->Registered->Doom II we support more switch textures.
        int episode = switch (world.mode) {
            case REGISTERED, RETAIL -> 2;
            case COMMERCIAL -> 3;
            default -> 1;
        };

        int[] list = new int[ALPH_SWITCH_LIST.length * 2];
        int slindex = 0;
        for (String[] entry : ALPH_SWITCH_LIST) {
            if (Integer.parseInt(entry[2]) <= episode) {
                list[slindex++] = world.textures.numForName(world.strings.translate(entry[0]));
                list[slindex++] = world.textures.numForName(world.strings.translate(entry[1]));
            }
        }
        numSwitches = slindex / 2;
        switchList = Arrays.copyOf(list, slindex);
    }

    /** {@code P_StartButton}: start a button counting down till it turns off. */
    @VanillaRef("p_switch.c:P_StartButton")
    private void startButton(Line line, int where, int texture, int time) {
        // See if button is already pressed
        for (int i = 0; i < MAXBUTTONS; i++) {
            if (buttonTimer[i] != 0 && buttonLine[i] == line) {
                return;
            }
        }

        for (int i = 0; i < MAXBUTTONS; i++) {
            if (buttonTimer[i] == 0) {
                buttonLine[i] = line;
                buttonWhere[i] = where;
                buttonTexture[i] = texture;
                buttonTimer[i] = time;
                return;
            }
        }

        throw new DoomFatalException("P_StartButton: no button slots left!");
    }

    /** Number of switch texture pairs (tests). */
    int switchCount() {
        return numSwitches;
    }

    /** The sound origin {@code buttonlist->soundorg} of the vanilla code: the first slot's front sector, if used. */
    private int firstButtonOrigin() {
        if (buttonTimer[0] != 0) {
            return SoundOrigins.sector(buttonLine[0].frontSector.index);
        }
        return SoundOrigins.NONE;
    }

    /**
     * {@code P_ChangeSwitchTexture}: changes the texture of a wall switch to its opposite. {@code useAgain} tells if
     * the switch is ok to use again (a button).
     */
    @VanillaRef("p_switch.c:P_ChangeSwitchTexture")
    void changeSwitchTexture(Line line, boolean useAgain) {
        if (!useAgain) {
            line.special = 0;
        }

        Side side = world.level.sides[line.sideNum[0]];
        int texTop = side.topTexture;
        int texMid = side.midTexture;
        int texBot = side.bottomTexture;

        int sound = SoundId.SWTCHN;

        // EXIT SWITCH?
        if (line.special == 11) {
            sound = SoundId.SWTCHX;
        }

        for (int i = 0; i < numSwitches * 2; i++) {
            if (switchList[i] == texTop) {
                // VANILLA-BUG: the origin is buttonlist->soundorg (always the first slot), not the switch
                world.events.startSound(firstButtonOrigin(), sound);
                side.topTexture = switchList[i ^ 1];

                if (useAgain) {
                    startButton(line, TOP, switchList[i], BUTTONTIME);
                }
                return;
            } else {
                if (switchList[i] == texMid) {
                    world.events.startSound(firstButtonOrigin(), sound);
                    side.midTexture = switchList[i ^ 1];

                    if (useAgain) {
                        startButton(line, MIDDLE, switchList[i], BUTTONTIME);
                    }
                    return;
                } else {
                    if (switchList[i] == texBot) {
                        world.events.startSound(firstButtonOrigin(), sound);
                        side.bottomTexture = switchList[i ^ 1];

                        if (useAgain) {
                            startButton(line, BOTTOM, switchList[i], BUTTONTIME);
                        }
                        return;
                    }
                }
            }
        }
    }

    /** The "DO BUTTONS" part of {@code P_UpdateSpecials}. */
    @VanillaRef("p_spec.c:P_UpdateSpecials")
    void updateButtons() {
        for (int i = 0; i < MAXBUTTONS; i++) {
            if (buttonTimer[i] != 0) {
                buttonTimer[i]--;
                if (buttonTimer[i] == 0) {
                    Side side = world.level.sides[buttonLine[i].sideNum[0]];
                    switch (buttonWhere[i]) {
                        case TOP -> side.topTexture = buttonTexture[i];
                        case MIDDLE -> side.midTexture = buttonTexture[i];
                        case BOTTOM -> side.bottomTexture = buttonTexture[i];
                        default -> {
                        }
                    }
                    // VANILLA-BUG: the original passes &buttonlist[i].soundorg (the address of the pointer field,
                    // read as a mobj) as the origin. The position is garbage far from any listener, so the sound is
                    // inaudible; only MAP08 / E?M8 have no distance cut-off and still draw from the misc RNG.
                    // DIVERGE: modelled as "no origin" on map 8, nothing elsewhere (see docs/divergences.md D-C01)
                    if (world.gameMap == 8) {
                        world.events.startSound(SoundOrigins.NONE, SoundId.SWTCHN);
                    }
                    buttonLine[i] = null;
                    buttonWhere[i] = 0;
                    buttonTexture[i] = 0;
                    buttonTimer[i] = 0;
                }
            }
        }
    }
}
