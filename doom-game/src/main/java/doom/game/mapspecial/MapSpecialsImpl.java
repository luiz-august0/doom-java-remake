package doom.game.mapspecial;

import doom.game.actor.Actor;
import doom.game.actor.SpecialThinker;
import doom.game.actor.Thinker;
import doom.game.data.MobjType;
import doom.game.player.Player;
import doom.game.view.CheatFlags;
import doom.game.view.LineFlags;
import doom.game.view.MobjFlags;
import doom.game.view.Power;
import doom.game.view.SoundId;
import doom.game.world.Line;
import doom.game.world.Sector;
import doom.game.world.World;
import doom.math.Angle;
import doom.math.DoomFatalException;
import doom.math.Fixed;
import doom.math.Tables;
import doom.math.annotation.VanillaRef;
import doom.wad.iwad.GameVersion;

/**
 * The map-special area ({@code p_spec.c} with {@code p_doors.c}, {@code p_floor.c}, {@code p_ceilng.c},
 * {@code p_plats.c}, {@code p_lights.c}, {@code p_switch.c} and {@code p_telept.c}).
 */
public final class MapSpecialsImpl implements MapSpecials {

    /** {@code MAXANIMS}. */
    private static final int MAXANIMS = 32;
    /** {@code MAXLINEANIMS}: more scrolling walls than this is a fatal error. */
    private static final int MAXLINEANIMS = 64;

    /** {@code animdefs[]}: is texture, end name, start name, speed. */
    private static final Object[][] ANIM_DEFS = {
        {false, "NUKAGE3", "NUKAGE1", 8},
        {false, "FWATER4", "FWATER1", 8},
        {false, "SWATER4", "SWATER1", 8},
        {false, "LAVA4", "LAVA1", 8},
        {false, "BLOOD3", "BLOOD1", 8},

        // DOOM II flat animations.
        {false, "RROCK08", "RROCK05", 8},
        {false, "SLIME04", "SLIME01", 8},
        {false, "SLIME08", "SLIME05", 8},
        {false, "SLIME12", "SLIME09", 8},

        {true, "BLODGR4", "BLODGR1", 8},
        {true, "SLADRIP3", "SLADRIP1", 8},

        {true, "BLODRIP4", "BLODRIP1", 8},
        {true, "FIREWALL", "FIREWALA", 8},
        {true, "GSTFONT3", "GSTFONT1", 8},
        {true, "FIRELAVA", "FIRELAV3", 8},
        {true, "FIREMAG3", "FIREMAG1", 8},
        {true, "FIREBLU2", "FIREBLU1", 8},
        {true, "ROCKRED3", "ROCKRED1", 8},

        {true, "BFALL4", "BFALL1", 8},
        {true, "SFALL4", "SFALL1", 8},
        {true, "WFALL4", "WFALL1", 8},
        {true, "DBRAIN4", "DBRAIN1", 8},
    };

    private final World world;
    private final Sectors sectors;
    private final Doors doors;
    private final Floors floors;
    private final Ceilings ceilings;
    private final Plats plats;
    private final Lights lights;
    private final Switches switches;
    private final SpecialsPersistence persistence;

    // anim_t anims[MAXANIMS]
    private final boolean[] animIsTexture = new boolean[MAXANIMS];
    private final int[] animBasePic = new int[MAXANIMS];
    private final int[] animNumPics = new int[MAXANIMS];
    private final int[] animSpeed = new int[MAXANIMS];
    private int numAnims;

    /** {@code texturetranslation[]} and {@code flattranslation[]}: session state, identity at start. */
    private int[] textureTranslation = new int[0];
    private int[] flatTranslation = new int[0];

    /** {@code linespeciallist[MAXLINEANIMS]} and {@code numlinespecials}. */
    private final Line[] lineSpecialList = new Line[MAXLINEANIMS];
    private int numLineSpecials;

    public MapSpecialsImpl(World world) {
        this.world = world;
        this.sectors = new Sectors(world);
        this.doors = new Doors(world, sectors);
        this.floors = new Floors(world, sectors);
        this.ceilings = new Ceilings(world, sectors);
        this.plats = new Plats(world, sectors);
        this.lights = new Lights(world, sectors);
        this.switches = new Switches(world);
        this.persistence = new SpecialsPersistence(world, doors, floors, ceilings, plats, lights);
    }

    // ============================================================================================ initialization

    /** {@code R_InitTextures} / {@code R_InitFlats} translation tables, {@code P_InitPicAnims}, {@code P_InitSwitchList}. */
    @Override
    public void init() {
        textureTranslation = new int[world.textures.count()];
        for (int i = 0; i < textureTranslation.length; i++) {
            textureTranslation[i] = i;
        }
        flatTranslation = new int[world.flats.count()];
        for (int i = 0; i < flatTranslation.length; i++) {
            flatTranslation[i] = i;
        }
        initPicAnims();
        switches.init();
    }

    /** {@code P_InitPicAnims}. */
    @VanillaRef("p_spec.c:P_InitPicAnims")
    private void initPicAnims() {
        numAnims = 0;
        for (Object[] def : ANIM_DEFS) {
            boolean isTexture = (Boolean) def[0];
            String startName = world.strings.translate((String) def[2]);
            String endName = world.strings.translate((String) def[1]);
            int picNum;
            int basePic;

            if (isTexture) {
                // different episode ?
                if (world.textures.checkNumForName(startName) == -1) {
                    continue;
                }
                picNum = world.textures.numForName(endName);
                basePic = world.textures.numForName(startName);
            } else {
                if (world.lumps.find(startName) == -1) {
                    continue;
                }
                picNum = world.flats.numForName(endName);
                basePic = world.flats.numForName(startName);
            }

            int numPics = picNum - basePic + 1;
            if (numPics < 2) {
                throw new DoomFatalException(
                        "P_InitPicAnims: bad cycle from " + startName + " to " + endName);
            }

            animIsTexture[numAnims] = isTexture;
            animBasePic[numAnims] = basePic;
            animNumPics[numAnims] = numPics;
            animSpeed[numAnims] = (Integer) def[3];
            numAnims++;
        }
    }

    // ============================================================================================ triggers

    /** {@code P_CrossSpecialLine}: called every time a thing origin is about to cross a line with a special. */
    @Override
    @VanillaRef("p_spec.c:P_CrossSpecialLine")
    public void crossSpecialLine(Line line, int side, Actor thing) {
        // gameversion <= exe_doom_1_2 is never true (every supported version is 1.9 or later)
        // Triggers that other things can activate
        if (thing.player == null) {
            // Things that should NOT trigger specials...
            switch (thing.type) {
                case MobjType.ROCKET, MobjType.PLASMA, MobjType.BFG, MobjType.TROOPSHOT, MobjType.HEADSHOT,
                        MobjType.BRUISERSHOT -> {
                    return;
                }
                default -> {
                }
            }
        }

        if (thing.player == null) {
            boolean ok = false;
            switch (line.special) {
                case 39, 97, 125, 126, 4, 10, 88 -> ok = true; // teleports, raise door, plat down-wait-up-stay
                default -> {
                }
            }
            if (!ok) {
                return;
            }
        }

        switch (line.special) {
            // TRIGGERS. All from here to RETRIGGERS.
            case 2 -> {
                // Open Door
                doors.doDoor(line, SpecialTypes.Door.OPEN);
                line.special = 0;
            }
            case 3 -> {
                // Close Door
                doors.doDoor(line, SpecialTypes.Door.CLOSE);
                line.special = 0;
            }
            case 4 -> {
                // Raise Door
                doors.doDoor(line, SpecialTypes.Door.NORMAL);
                line.special = 0;
            }
            case 5 -> {
                // Raise Floor
                floors.doFloor(line, SpecialTypes.Floor.RAISE_FLOOR);
                line.special = 0;
            }
            case 6 -> {
                // Fast Ceiling Crush & Raise
                ceilings.doCeiling(line, SpecialTypes.Ceiling.FAST_CRUSH_AND_RAISE);
                line.special = 0;
            }
            case 8 -> {
                // Build Stairs
                floors.buildStairs(line, SpecialTypes.Stair.BUILD_8);
                line.special = 0;
            }
            case 10 -> {
                // PlatDownWaitUp
                plats.doPlat(line, SpecialTypes.Plat.DOWN_WAIT_UP_STAY, 0);
                line.special = 0;
            }
            case 12 -> {
                // Light Turn On - brightest near
                lights.lightTurnOn(line, 0);
                line.special = 0;
            }
            case 13 -> {
                // Light Turn On 255
                lights.lightTurnOn(line, 255);
                line.special = 0;
            }
            case 16 -> {
                // Close Door 30
                doors.doDoor(line, SpecialTypes.Door.CLOSE_30_THEN_OPEN);
                line.special = 0;
            }
            case 17 -> {
                // Start Light Strobing
                lights.startLightStrobing(line);
                line.special = 0;
            }
            case 19 -> {
                // Lower Floor
                floors.doFloor(line, SpecialTypes.Floor.LOWER_FLOOR);
                line.special = 0;
            }
            case 22 -> {
                // Raise floor to nearest height and change texture
                plats.doPlat(line, SpecialTypes.Plat.RAISE_TO_NEAREST_AND_CHANGE, 0);
                line.special = 0;
            }
            case 25 -> {
                // Ceiling Crush and Raise
                ceilings.doCeiling(line, SpecialTypes.Ceiling.CRUSH_AND_RAISE);
                line.special = 0;
            }
            case 30 -> {
                // Raise floor to shortest texture height on either side of lines.
                floors.doFloor(line, SpecialTypes.Floor.RAISE_TO_TEXTURE);
                line.special = 0;
            }
            case 35 -> {
                // Lights Very Dark
                lights.lightTurnOn(line, 35);
                line.special = 0;
            }
            case 36 -> {
                // Lower Floor (TURBO)
                floors.doFloor(line, SpecialTypes.Floor.TURBO_LOWER);
                line.special = 0;
            }
            case 37 -> {
                // LowerAndChange
                floors.doFloor(line, SpecialTypes.Floor.LOWER_AND_CHANGE);
                line.special = 0;
            }
            case 38 -> {
                // Lower Floor To Lowest
                floors.doFloor(line, SpecialTypes.Floor.LOWER_FLOOR_TO_LOWEST);
                line.special = 0;
            }
            case 39 -> {
                // TELEPORT!
                teleport(line, side, thing);
                line.special = 0;
            }
            case 40 -> {
                // RaiseCeilingLowerFloor
                ceilings.doCeiling(line, SpecialTypes.Ceiling.RAISE_TO_HIGHEST);
                floors.doFloor(line, SpecialTypes.Floor.LOWER_FLOOR_TO_LOWEST);
                line.special = 0;
            }
            case 44 -> {
                // Ceiling Crush
                ceilings.doCeiling(line, SpecialTypes.Ceiling.LOWER_AND_CRUSH);
                line.special = 0;
            }
            case 52 -> {
                // EXIT!
                world.flow.exitLevel();
            }
            case 53 -> {
                // Perpetual Platform Raise
                plats.doPlat(line, SpecialTypes.Plat.PERPETUAL_RAISE, 0);
                line.special = 0;
            }
            case 54 -> {
                // Platform Stop
                plats.stopPlat(line);
                line.special = 0;
            }
            case 56 -> {
                // Raise Floor Crush
                floors.doFloor(line, SpecialTypes.Floor.RAISE_FLOOR_CRUSH);
                line.special = 0;
            }
            case 57 -> {
                // Ceiling Crush Stop
                ceilings.ceilingCrushStop(line);
                line.special = 0;
            }
            case 58 -> {
                // Raise Floor 24
                floors.doFloor(line, SpecialTypes.Floor.RAISE_FLOOR_24);
                line.special = 0;
            }
            case 59 -> {
                // Raise Floor 24 And Change
                floors.doFloor(line, SpecialTypes.Floor.RAISE_FLOOR_24_AND_CHANGE);
                line.special = 0;
            }
            case 104 -> {
                // Turn lights off in sector(tag)
                lights.turnTagLightsOff(line);
                line.special = 0;
            }
            case 108 -> {
                // Blazing Door Raise (faster than TURBO!)
                doors.doDoor(line, SpecialTypes.Door.BLAZE_RAISE);
                line.special = 0;
            }
            case 109 -> {
                // Blazing Door Open (faster than TURBO!)
                doors.doDoor(line, SpecialTypes.Door.BLAZE_OPEN);
                line.special = 0;
            }
            case 100 -> {
                // Build Stairs Turbo 16
                floors.buildStairs(line, SpecialTypes.Stair.TURBO_16);
                line.special = 0;
            }
            case 110 -> {
                // Blazing Door Close (faster than TURBO!)
                doors.doDoor(line, SpecialTypes.Door.BLAZE_CLOSE);
                line.special = 0;
            }
            case 119 -> {
                // Raise floor to nearest surr. floor
                floors.doFloor(line, SpecialTypes.Floor.RAISE_FLOOR_TO_NEAREST);
                line.special = 0;
            }
            case 121 -> {
                // Blazing PlatDownWaitUpStay
                plats.doPlat(line, SpecialTypes.Plat.BLAZE_DWUS, 0);
                line.special = 0;
            }
            case 124 -> {
                // Secret EXIT
                world.flow.secretExitLevel();
            }
            case 125 -> {
                // TELEPORT MonsterONLY
                if (thing.player == null) {
                    teleport(line, side, thing);
                    line.special = 0;
                }
            }
            case 130 -> {
                // Raise Floor Turbo
                floors.doFloor(line, SpecialTypes.Floor.RAISE_FLOOR_TURBO);
                line.special = 0;
            }
            case 141 -> {
                // Silent Ceiling Crush & Raise
                ceilings.doCeiling(line, SpecialTypes.Ceiling.SILENT_CRUSH_AND_RAISE);
                line.special = 0;
            }

            // RETRIGGERS. All from here till end.
            case 72 -> ceilings.doCeiling(line, SpecialTypes.Ceiling.LOWER_AND_CRUSH); // Ceiling Crush
            case 73 -> ceilings.doCeiling(line, SpecialTypes.Ceiling.CRUSH_AND_RAISE); // Ceiling Crush and Raise
            case 74 -> ceilings.ceilingCrushStop(line); // Ceiling Crush Stop
            case 75 -> doors.doDoor(line, SpecialTypes.Door.CLOSE); // Close Door
            case 76 -> doors.doDoor(line, SpecialTypes.Door.CLOSE_30_THEN_OPEN); // Close Door 30
            case 77 -> ceilings.doCeiling(line, SpecialTypes.Ceiling.FAST_CRUSH_AND_RAISE); // Fast Ceiling Crush & Raise
            case 79 -> lights.lightTurnOn(line, 35); // Lights Very Dark
            case 80 -> lights.lightTurnOn(line, 0); // Light Turn On - brightest near
            case 81 -> lights.lightTurnOn(line, 255); // Light Turn On 255
            case 82 -> floors.doFloor(line, SpecialTypes.Floor.LOWER_FLOOR_TO_LOWEST); // Lower Floor To Lowest
            case 83 -> floors.doFloor(line, SpecialTypes.Floor.LOWER_FLOOR); // Lower Floor
            case 84 -> floors.doFloor(line, SpecialTypes.Floor.LOWER_AND_CHANGE); // LowerAndChange
            case 86 -> doors.doDoor(line, SpecialTypes.Door.OPEN); // Open Door
            case 87 -> plats.doPlat(line, SpecialTypes.Plat.PERPETUAL_RAISE, 0); // Perpetual Platform Raise
            case 88 -> plats.doPlat(line, SpecialTypes.Plat.DOWN_WAIT_UP_STAY, 0); // PlatDownWaitUp
            case 89 -> plats.stopPlat(line); // Platform Stop
            case 90 -> doors.doDoor(line, SpecialTypes.Door.NORMAL); // Raise Door
            case 91 -> floors.doFloor(line, SpecialTypes.Floor.RAISE_FLOOR); // Raise Floor
            case 92 -> floors.doFloor(line, SpecialTypes.Floor.RAISE_FLOOR_24); // Raise Floor 24
            case 93 -> floors.doFloor(line, SpecialTypes.Floor.RAISE_FLOOR_24_AND_CHANGE); // Raise Floor 24 And Change
            case 94 -> floors.doFloor(line, SpecialTypes.Floor.RAISE_FLOOR_CRUSH); // Raise Floor Crush
            case 95 -> plats.doPlat(line, SpecialTypes.Plat.RAISE_TO_NEAREST_AND_CHANGE, 0); // nearest + change
            case 96 -> floors.doFloor(line, SpecialTypes.Floor.RAISE_TO_TEXTURE); // shortest texture height
            case 97 -> teleport(line, side, thing); // TELEPORT!
            case 98 -> floors.doFloor(line, SpecialTypes.Floor.TURBO_LOWER); // Lower Floor (TURBO)
            case 105 -> doors.doDoor(line, SpecialTypes.Door.BLAZE_RAISE); // Blazing Door Raise
            case 106 -> doors.doDoor(line, SpecialTypes.Door.BLAZE_OPEN); // Blazing Door Open
            case 107 -> doors.doDoor(line, SpecialTypes.Door.BLAZE_CLOSE); // Blazing Door Close
            case 120 -> plats.doPlat(line, SpecialTypes.Plat.BLAZE_DWUS, 0); // Blazing PlatDownWaitUpStay
            case 126 -> {
                // TELEPORT MonsterONLY.
                if (thing.player == null) {
                    teleport(line, side, thing);
                }
            }
            case 128 -> floors.doFloor(line, SpecialTypes.Floor.RAISE_FLOOR_TO_NEAREST); // Raise To Nearest Floor
            case 129 -> floors.doFloor(line, SpecialTypes.Floor.RAISE_FLOOR_TURBO); // Raise Floor Turbo
            default -> {
            }
        }
    }

    /** {@code P_UseSpecialLine}: called when a thing uses a special line; only the front sides are usable. */
    @Override
    @VanillaRef("p_switch.c:P_UseSpecialLine")
    public boolean useSpecialLine(Actor thing, Line line, int side) {
        // Err... Use the back sides of VERY SPECIAL lines...
        if (side != 0) {
            switch (line.special) {
                case 124 -> {
                    // Sliding door open&close, UNUSED?
                }
                default -> {
                    return false;
                }
            }
        }

        // Switches that other things can activate.
        if (thing.player == null) {
            // never open secret doors
            if ((line.flags & LineFlags.SECRET) != 0) {
                return false;
            }

            switch (line.special) {
                case 1, 32, 33, 34 -> {
                    // MANUAL DOOR RAISE, BLUE, RED, YELLOW
                }
                default -> {
                    return false;
                }
            }
        }

        // do something
        switch (line.special) {
            // MANUALS
            case 1, 26, 27, 28, 31, 32, 33, 34, 117, 118 -> doors.verticalDoor(line, thing);

            // SWITCHES
            case 7 -> {
                // Build Stairs
                if (floors.buildStairs(line, SpecialTypes.Stair.BUILD_8)) {
                    switches.changeSwitchTexture(line, false);
                }
            }
            case 9 -> {
                // Change Donut
                if (floors.doDonut(line)) {
                    switches.changeSwitchTexture(line, false);
                }
            }
            case 11 -> {
                // Exit level
                switches.changeSwitchTexture(line, false);
                world.flow.exitLevel();
            }
            case 14 -> {
                // Raise Floor 32 and change texture
                if (plats.doPlat(line, SpecialTypes.Plat.RAISE_AND_CHANGE, 32)) {
                    switches.changeSwitchTexture(line, false);
                }
            }
            case 15 -> {
                // Raise Floor 24 and change texture
                if (plats.doPlat(line, SpecialTypes.Plat.RAISE_AND_CHANGE, 24)) {
                    switches.changeSwitchTexture(line, false);
                }
            }
            case 18 -> {
                // Raise Floor to next highest floor
                if (floors.doFloor(line, SpecialTypes.Floor.RAISE_FLOOR_TO_NEAREST)) {
                    switches.changeSwitchTexture(line, false);
                }
            }
            case 20 -> {
                // Raise Plat next highest floor and change texture
                if (plats.doPlat(line, SpecialTypes.Plat.RAISE_TO_NEAREST_AND_CHANGE, 0)) {
                    switches.changeSwitchTexture(line, false);
                }
            }
            case 21 -> {
                // PlatDownWaitUpStay
                if (plats.doPlat(line, SpecialTypes.Plat.DOWN_WAIT_UP_STAY, 0)) {
                    switches.changeSwitchTexture(line, false);
                }
            }
            case 23 -> {
                // Lower Floor to Lowest
                if (floors.doFloor(line, SpecialTypes.Floor.LOWER_FLOOR_TO_LOWEST)) {
                    switches.changeSwitchTexture(line, false);
                }
            }
            case 29 -> {
                // Raise Door
                if (doors.doDoor(line, SpecialTypes.Door.NORMAL)) {
                    switches.changeSwitchTexture(line, false);
                }
            }
            case 41 -> {
                // Lower Ceiling to Floor
                if (ceilings.doCeiling(line, SpecialTypes.Ceiling.LOWER_TO_FLOOR)) {
                    switches.changeSwitchTexture(line, false);
                }
            }
            case 71 -> {
                // Turbo Lower Floor
                if (floors.doFloor(line, SpecialTypes.Floor.TURBO_LOWER)) {
                    switches.changeSwitchTexture(line, false);
                }
            }
            case 49 -> {
                // Ceiling Crush And Raise
                if (ceilings.doCeiling(line, SpecialTypes.Ceiling.CRUSH_AND_RAISE)) {
                    switches.changeSwitchTexture(line, false);
                }
            }
            case 50 -> {
                // Close Door
                if (doors.doDoor(line, SpecialTypes.Door.CLOSE)) {
                    switches.changeSwitchTexture(line, false);
                }
            }
            case 51 -> {
                // Secret EXIT
                switches.changeSwitchTexture(line, false);
                world.flow.secretExitLevel();
            }
            case 55 -> {
                // Raise Floor Crush
                if (floors.doFloor(line, SpecialTypes.Floor.RAISE_FLOOR_CRUSH)) {
                    switches.changeSwitchTexture(line, false);
                }
            }
            case 101 -> {
                // Raise Floor
                if (floors.doFloor(line, SpecialTypes.Floor.RAISE_FLOOR)) {
                    switches.changeSwitchTexture(line, false);
                }
            }
            case 102 -> {
                // Lower Floor to Surrounding floor height
                if (floors.doFloor(line, SpecialTypes.Floor.LOWER_FLOOR)) {
                    switches.changeSwitchTexture(line, false);
                }
            }
            case 103 -> {
                // Open Door
                if (doors.doDoor(line, SpecialTypes.Door.OPEN)) {
                    switches.changeSwitchTexture(line, false);
                }
            }
            case 111 -> {
                // Blazing Door Raise (faster than TURBO!)
                if (doors.doDoor(line, SpecialTypes.Door.BLAZE_RAISE)) {
                    switches.changeSwitchTexture(line, false);
                }
            }
            case 112 -> {
                // Blazing Door Open (faster than TURBO!)
                if (doors.doDoor(line, SpecialTypes.Door.BLAZE_OPEN)) {
                    switches.changeSwitchTexture(line, false);
                }
            }
            case 113 -> {
                // Blazing Door Close (faster than TURBO!)
                if (doors.doDoor(line, SpecialTypes.Door.BLAZE_CLOSE)) {
                    switches.changeSwitchTexture(line, false);
                }
            }
            case 122 -> {
                // Blazing PlatDownWaitUpStay
                if (plats.doPlat(line, SpecialTypes.Plat.BLAZE_DWUS, 0)) {
                    switches.changeSwitchTexture(line, false);
                }
            }
            case 127 -> {
                // Build Stairs Turbo 16
                if (floors.buildStairs(line, SpecialTypes.Stair.TURBO_16)) {
                    switches.changeSwitchTexture(line, false);
                }
            }
            case 131 -> {
                // Raise Floor Turbo
                if (floors.doFloor(line, SpecialTypes.Floor.RAISE_FLOOR_TURBO)) {
                    switches.changeSwitchTexture(line, false);
                }
            }
            case 133, 135, 137 -> {
                // BlzOpenDoor BLUE / RED / YELLOW
                if (doors.doLockedDoor(line, SpecialTypes.Door.BLAZE_OPEN, thing)) {
                    switches.changeSwitchTexture(line, false);
                }
            }
            case 140 -> {
                // Raise Floor 512
                if (floors.doFloor(line, SpecialTypes.Floor.RAISE_FLOOR_512)) {
                    switches.changeSwitchTexture(line, false);
                }
            }

            // BUTTONS
            case 42 -> {
                // Close Door
                if (doors.doDoor(line, SpecialTypes.Door.CLOSE)) {
                    switches.changeSwitchTexture(line, true);
                }
            }
            case 43 -> {
                // Lower Ceiling to Floor
                if (ceilings.doCeiling(line, SpecialTypes.Ceiling.LOWER_TO_FLOOR)) {
                    switches.changeSwitchTexture(line, true);
                }
            }
            case 45 -> {
                // Lower Floor to Surrounding floor height
                if (floors.doFloor(line, SpecialTypes.Floor.LOWER_FLOOR)) {
                    switches.changeSwitchTexture(line, true);
                }
            }
            case 60 -> {
                // Lower Floor to Lowest
                if (floors.doFloor(line, SpecialTypes.Floor.LOWER_FLOOR_TO_LOWEST)) {
                    switches.changeSwitchTexture(line, true);
                }
            }
            case 61 -> {
                // Open Door
                if (doors.doDoor(line, SpecialTypes.Door.OPEN)) {
                    switches.changeSwitchTexture(line, true);
                }
            }
            case 62 -> {
                // PlatDownWaitUpStay
                if (plats.doPlat(line, SpecialTypes.Plat.DOWN_WAIT_UP_STAY, 1)) {
                    switches.changeSwitchTexture(line, true);
                }
            }
            case 63 -> {
                // Raise Door
                if (doors.doDoor(line, SpecialTypes.Door.NORMAL)) {
                    switches.changeSwitchTexture(line, true);
                }
            }
            case 64 -> {
                // Raise Floor to ceiling
                if (floors.doFloor(line, SpecialTypes.Floor.RAISE_FLOOR)) {
                    switches.changeSwitchTexture(line, true);
                }
            }
            case 66 -> {
                // Raise Floor 24 and change texture
                if (plats.doPlat(line, SpecialTypes.Plat.RAISE_AND_CHANGE, 24)) {
                    switches.changeSwitchTexture(line, true);
                }
            }
            case 67 -> {
                // Raise Floor 32 and change texture
                if (plats.doPlat(line, SpecialTypes.Plat.RAISE_AND_CHANGE, 32)) {
                    switches.changeSwitchTexture(line, true);
                }
            }
            case 65 -> {
                // Raise Floor Crush
                if (floors.doFloor(line, SpecialTypes.Floor.RAISE_FLOOR_CRUSH)) {
                    switches.changeSwitchTexture(line, true);
                }
            }
            case 68 -> {
                // Raise Plat to next highest floor and change texture
                if (plats.doPlat(line, SpecialTypes.Plat.RAISE_TO_NEAREST_AND_CHANGE, 0)) {
                    switches.changeSwitchTexture(line, true);
                }
            }
            case 69 -> {
                // Raise Floor to next highest floor
                if (floors.doFloor(line, SpecialTypes.Floor.RAISE_FLOOR_TO_NEAREST)) {
                    switches.changeSwitchTexture(line, true);
                }
            }
            case 70 -> {
                // Turbo Lower Floor
                if (floors.doFloor(line, SpecialTypes.Floor.TURBO_LOWER)) {
                    switches.changeSwitchTexture(line, true);
                }
            }
            case 114 -> {
                // Blazing Door Raise (faster than TURBO!)
                if (doors.doDoor(line, SpecialTypes.Door.BLAZE_RAISE)) {
                    switches.changeSwitchTexture(line, true);
                }
            }
            case 115 -> {
                // Blazing Door Open (faster than TURBO!)
                if (doors.doDoor(line, SpecialTypes.Door.BLAZE_OPEN)) {
                    switches.changeSwitchTexture(line, true);
                }
            }
            case 116 -> {
                // Blazing Door Close (faster than TURBO!)
                if (doors.doDoor(line, SpecialTypes.Door.BLAZE_CLOSE)) {
                    switches.changeSwitchTexture(line, true);
                }
            }
            case 123 -> {
                // Blazing PlatDownWaitUpStay
                if (plats.doPlat(line, SpecialTypes.Plat.BLAZE_DWUS, 0)) {
                    switches.changeSwitchTexture(line, true);
                }
            }
            case 132 -> {
                // Raise Floor Turbo
                if (floors.doFloor(line, SpecialTypes.Floor.RAISE_FLOOR_TURBO)) {
                    switches.changeSwitchTexture(line, true);
                }
            }
            case 99, 134, 136 -> {
                // BlzOpenDoor BLUE / RED / YELLOW
                if (doors.doLockedDoor(line, SpecialTypes.Door.BLAZE_OPEN, thing)) {
                    switches.changeSwitchTexture(line, true);
                }
            }
            case 138 -> {
                // Light Turn On
                lights.lightTurnOn(line, 255);
                switches.changeSwitchTexture(line, true);
            }
            case 139 -> {
                // Light Turn Off
                lights.lightTurnOn(line, 35);
                switches.changeSwitchTexture(line, true);
            }
            default -> {
            }
        }

        return true;
    }

    /** {@code P_ShootSpecialLine}: called when a thing shoots a special line. */
    @Override
    @VanillaRef("p_spec.c:P_ShootSpecialLine")
    public void shootSpecialLine(Actor thing, Line line) {
        // Impacts that other things can activate.
        if (thing.player == null) {
            boolean ok = false;
            if (line.special == 46) {
                // OPEN DOOR IMPACT
                ok = true;
            }
            if (!ok) {
                return;
            }
        }

        switch (line.special) {
            case 24 -> {
                // RAISE FLOOR
                floors.doFloor(line, SpecialTypes.Floor.RAISE_FLOOR);
                switches.changeSwitchTexture(line, false);
            }
            case 46 -> {
                // OPEN DOOR
                doors.doDoor(line, SpecialTypes.Door.OPEN);
                switches.changeSwitchTexture(line, true);
            }
            case 47 -> {
                // RAISE FLOOR NEAR AND CHANGE
                plats.doPlat(line, SpecialTypes.Plat.RAISE_TO_NEAREST_AND_CHANGE, 0);
                switches.changeSwitchTexture(line, false);
            }
            default -> {
            }
        }
    }

    /** {@code P_PlayerInSpecialSector}: called every tic frame that the player origin is in a special sector. */
    @Override
    @VanillaRef("p_spec.c:P_PlayerInSpecialSector")
    public void playerInSpecialSector(Player player) {
        Sector sector = player.mo.subsector.sector;

        // Falling, not all the way down yet?
        if (player.mo.z != sector.floorHeight) {
            return;
        }

        // Has hitten ground.
        switch (sector.special) {
            case 5 -> {
                // HELLSLIME DAMAGE
                if (player.powers[Power.IRONFEET] == 0) {
                    if ((world.levelTime & 0x1f) == 0) {
                        world.combat.damageMobj(player.mo, null, null, 10);
                    }
                }
            }
            case 7 -> {
                // NUKAGE DAMAGE
                if (player.powers[Power.IRONFEET] == 0) {
                    if ((world.levelTime & 0x1f) == 0) {
                        world.combat.damageMobj(player.mo, null, null, 5);
                    }
                }
            }
            case 16, 4 -> {
                // SUPER HELLSLIME DAMAGE / STROBE HURT
                if (player.powers[Power.IRONFEET] == 0 || world.rng.game() < 5) {
                    if ((world.levelTime & 0x1f) == 0) {
                        world.combat.damageMobj(player.mo, null, null, 20);
                    }
                }
            }
            case 9 -> {
                // SECRET SECTOR
                player.secretCount++;
                sector.special = 0;
            }
            case 11 -> {
                // EXIT SUPER DAMAGE! (for E1M8 finale)
                player.cheats &= ~CheatFlags.GODMODE;

                if ((world.levelTime & 0x1f) == 0) {
                    world.combat.damageMobj(player.mo, null, null, 20);
                }

                if (player.health <= 10) {
                    world.flow.exitLevel();
                }
            }
            default -> throw new DoomFatalException(
                    "P_PlayerInSpecialSector: unknown special " + sector.special);
        }
    }

    /** {@code EV_Teleport}. */
    @VanillaRef("p_telept.c:EV_Teleport")
    private boolean teleport(Line line, int side, Actor thing) {
        // don't teleport missiles
        if ((thing.flags & MobjFlags.MISSILE) != 0) {
            return false;
        }

        // Don't teleport if hit back of line, so you can get out of teleporter.
        if (side == 1) {
            return false;
        }

        int tag = line.tag;
        Sector[] all = world.level.sectors;
        for (int i = 0; i < all.length; i++) {
            if (all[i].tag == tag) {
                for (Thinker thinker = world.thinkers.first(); thinker != null; thinker = world.thinkers.next(thinker)) {
                    // not a mobj
                    if (!(thinker instanceof Actor m) || m.isRemoved()) {
                        continue;
                    }

                    // not a teleportman
                    if (m.type != MobjType.TELEPORTMAN) {
                        continue;
                    }

                    Sector sector = m.subsector.sector;
                    // wrong sector
                    if (sector.index != i) {
                        continue;
                    }

                    int oldX = thing.x;
                    int oldY = thing.y;
                    int oldZ = thing.z;

                    if (!world.physics.teleportMove(thing, m.x, m.y)) {
                        return false;
                    }

                    // The first Final Doom executable does not set thing->z when teleporting. This quirk is unique
                    // to this particular version; the later version included in some versions of the Id Anthology
                    // fixed this.
                    if (world.version != GameVersion.FINAL) {
                        thing.z = thing.floorZ;
                    }
                    if (thing.player != null) {
                        thing.player.viewZ = thing.z + thing.player.viewHeight;
                    }

                    // spawn teleport fog at source and destination
                    Actor fog = world.mobjs.spawnMobj(oldX, oldY, oldZ, MobjType.TFOG);
                    world.startSound(fog, SoundId.TELEPT);
                    int an = m.angle >>> Angle.ANGLETOFINESHIFT;
                    fog = world.mobjs.spawnMobj(m.x + 20 * Tables.fineCosine(an), m.y + 20 * Tables.fineSine(an),
                            thing.z, MobjType.TFOG);

                    // emit sound, where?
                    world.startSound(fog, SoundId.TELEPT);

                    // don't move for a bit
                    if (thing.player != null) {
                        thing.reactionTime = 18;
                    }

                    thing.angle = m.angle;
                    thing.momX = 0;
                    thing.momY = 0;
                    thing.momZ = 0;
                    return true;
                }
            }
        }
        return false;
    }

    // ============================================================================================ level hooks

    /** {@code P_SpawnSpecials}: after the map has been loaded, scan for specials that spawn thinkers. */
    @Override
    @VanillaRef("p_spec.c:P_SpawnSpecials")
    public void spawnSpecials() {
        // The level timer (-timer, "timelimit") is a user setting that does not exist here: levelTimer is false.

        // Init special SECTORs.
        Sector[] all = world.level.sectors;
        for (int i = 0; i < all.length; i++) {
            Sector sector = all[i];
            if (sector.special == 0) {
                continue;
            }

            switch (sector.special) {
                case 1 -> lights.spawnLightFlash(sector); // FLICKERING LIGHTS
                case 2 -> lights.spawnStrobeFlash(sector, Lights.FASTDARK, 0); // STROBE FAST
                case 3 -> lights.spawnStrobeFlash(sector, Lights.SLOWDARK, 0); // STROBE SLOW
                case 4 -> {
                    // STROBE FAST/DEATH SLIME
                    lights.spawnStrobeFlash(sector, Lights.FASTDARK, 0);
                    sector.special = 4;
                }
                case 8 -> lights.spawnGlowingLight(sector); // GLOWING LIGHT
                case 9 -> world.totalSecret++; // SECRET SECTOR
                case 10 -> doors.spawnDoorCloseIn30(sector); // DOOR CLOSE IN 30 SECONDS
                case 12 -> lights.spawnStrobeFlash(sector, Lights.SLOWDARK, 1); // SYNC STROBE SLOW
                case 13 -> lights.spawnStrobeFlash(sector, Lights.FASTDARK, 1); // SYNC STROBE FAST
                case 14 -> doors.spawnDoorRaiseIn5Mins(sector); // DOOR RAISE IN 5 MINUTES
                case 17 -> {
                    // first introduced in official v1.4 beta (gameversion > exe_doom_1_2 always holds)
                    lights.spawnFireFlicker(sector);
                }
                default -> {
                }
            }
        }

        // Init line EFFECTs
        numLineSpecials = 0;
        Line[] lines = world.level.lines;
        for (Line line : lines) {
            if (line.special == 48) {
                if (numLineSpecials >= MAXLINEANIMS) {
                    int scrollers = 0;
                    for (Line l : lines) {
                        if (l.special == 48) {
                            scrollers++;
                        }
                    }
                    throw new DoomFatalException("Too many scrolling wall linedefs (" + scrollers
                            + ")! (Vanilla limit is 64)");
                }
                // EFFECT FIRSTCOL SCROLL+
                lineSpecialList[numLineSpecials] = line;
                numLineSpecials++;
            }
        }

        // Init other misc stuff
        ceilings.reset();
        plats.reset();
        switches.reset();
    }

    /** {@code P_UpdateSpecials}: animate planes, scroll walls, etc. */
    @Override
    @VanillaRef("p_spec.c:P_UpdateSpecials")
    public void updateSpecials() {
        // LEVEL TIMER: not available (see spawnSpecials)

        // ANIMATE FLATS AND TEXTURES GLOBALLY
        for (int a = 0; a < numAnims; a++) {
            int base = animBasePic[a];
            int numPics = animNumPics[a];
            for (int i = base; i < base + numPics; i++) {
                int pic = base + ((world.levelTime / animSpeed[a] + i) % numPics);
                if (animIsTexture[a]) {
                    textureTranslation[i] = pic;
                } else {
                    flatTranslation[i] = pic;
                }
            }
        }

        // ANIMATE LINE SPECIALS
        for (int i = 0; i < numLineSpecials; i++) {
            Line line = lineSpecialList[i];
            if (line.special == 48) {
                // EFFECT FIRSTCOL SCROLL +
                world.level.sides[line.sideNum[0]].textureOffset += Fixed.FRACUNIT;
            }
        }

        // DO BUTTONS
        switches.updateButtons();
    }

    // ============================================================================================ boss deaths, views

    @Override
    public boolean doFloor(Line line, int floorType) {
        return floors.doFloor(line, floorType);
    }

    @Override
    public boolean doDoor(Line line, int doorType) {
        return doors.doDoor(line, doorType);
    }

    @Override
    public int textureTranslation(int texture) {
        if (texture < 0 || texture >= textureTranslation.length) {
            return texture;
        }
        return textureTranslation[texture];
    }

    @Override
    public int flatTranslation(int flat) {
        if (flat < 0 || flat >= flatTranslation.length) {
            return flat;
        }
        return flatTranslation[flat];
    }

    // ============================================================================================ save games (W4-P)

    @Override
    public int[] saveThinker(SpecialThinker thinker) {
        return persistence.save(thinker);
    }

    @Override
    public SpecialThinker restoreThinker(int[] state) {
        return persistence.restore(state);
    }

    @Override
    public SpecialThinker[] activeCeilings() {
        return ceilings.slots().clone();
    }

    @Override
    public SpecialThinker[] activePlats() {
        return plats.slots().clone();
    }

    @Override
    public void restoreActiveSpecials(SpecialThinker[] ceilingSlots, SpecialThinker[] platSlots) {
        Ceilings.CeilingMove[] c = ceilings.slots();
        Plats.PlatRaise[] p = plats.slots();
        if (ceilingSlots.length != c.length || platSlots.length != p.length) {
            throw new IllegalArgumentException("wrong number of active ceiling / platform slots");
        }
        for (int i = 0; i < c.length; i++) {
            c[i] = (Ceilings.CeilingMove) ceilingSlots[i];
        }
        for (int i = 0; i < p.length; i++) {
            p[i] = (Plats.PlatRaise) platSlots[i];
        }
    }

    @Override
    public int[] buttons() {
        return switches.saveButtons();
    }

    @Override
    public void restoreButtons(int[] buttons) {
        switches.restoreButtons(buttons);
    }
}
