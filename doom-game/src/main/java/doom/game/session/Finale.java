package doom.game.session;

import doom.game.api.GameState;
import doom.game.data.MobjType;
import doom.game.data.State;
import doom.game.data.StateId;
import doom.game.data.ThingType;
import doom.game.view.FinaleView;
import doom.game.view.MusicId;
import doom.game.view.SoundId;
import doom.game.world.World;
import doom.math.annotation.VanillaRef;
import doom.wad.iwad.GameMission;
import doom.wad.iwad.GameMode;

/**
 * The finale logic ({@code f_finale.c}): the text crawl timing, the art screens and the DOOM II cast call. The UI
 * draws from the {@link FinaleView} side; a key press during the cast call arrives through {@link #castKey()}.
 */
public final class Finale implements FinaleView {

    private static final int F_STAGE_TEXT = 0;
    private static final int F_STAGE_ARTSCREEN = 1;
    private static final int F_STAGE_CAST = 2;

    private static final int TEXTSPEED = 3;
    private static final int TEXTWAIT = 250;

    /** One {@code textscreen_t}: mission, episode, map, background flat, text key. */
    private record TextScreen(GameMission mission, int episode, int level, String background, String text) {
    }

    private static final TextScreen[] TEXTSCREENS = {
        new TextScreen(GameMission.DOOM, 1, 8, "FLOOR4_8", "E1TEXT"),
        new TextScreen(GameMission.DOOM, 2, 8, "SFLR6_1", "E2TEXT"),
        new TextScreen(GameMission.DOOM, 3, 8, "MFLR8_4", "E3TEXT"),
        new TextScreen(GameMission.DOOM, 4, 8, "MFLR8_3", "E4TEXT"),
        new TextScreen(GameMission.DOOM2, 1, 6, "SLIME16", "C1TEXT"),
        new TextScreen(GameMission.DOOM2, 1, 11, "RROCK14", "C2TEXT"),
        new TextScreen(GameMission.DOOM2, 1, 20, "RROCK07", "C3TEXT"),
        new TextScreen(GameMission.DOOM2, 1, 30, "RROCK17", "C4TEXT"),
        new TextScreen(GameMission.DOOM2, 1, 15, "RROCK13", "C5TEXT"),
        new TextScreen(GameMission.DOOM2, 1, 31, "RROCK19", "C6TEXT"),
        new TextScreen(GameMission.PACK_TNT, 1, 6, "SLIME16", "T1TEXT"),
        new TextScreen(GameMission.PACK_TNT, 1, 11, "RROCK14", "T2TEXT"),
        new TextScreen(GameMission.PACK_TNT, 1, 20, "RROCK07", "T3TEXT"),
        new TextScreen(GameMission.PACK_TNT, 1, 30, "RROCK17", "T4TEXT"),
        new TextScreen(GameMission.PACK_TNT, 1, 15, "RROCK13", "T5TEXT"),
        new TextScreen(GameMission.PACK_TNT, 1, 31, "RROCK19", "T6TEXT"),
        new TextScreen(GameMission.PACK_PLUTONIA, 1, 6, "SLIME16", "P1TEXT"),
        new TextScreen(GameMission.PACK_PLUTONIA, 1, 11, "RROCK14", "P2TEXT"),
        new TextScreen(GameMission.PACK_PLUTONIA, 1, 20, "RROCK07", "P3TEXT"),
        new TextScreen(GameMission.PACK_PLUTONIA, 1, 30, "RROCK17", "P4TEXT"),
        new TextScreen(GameMission.PACK_PLUTONIA, 1, 15, "RROCK13", "P5TEXT"),
        new TextScreen(GameMission.PACK_PLUTONIA, 1, 31, "RROCK19", "P6TEXT"),
    };

    /** {@code castorder[]}: monster types in order of appearance and the string key of their names. */
    private static final int[] CAST_TYPES = {
        MobjType.POSSESSED, MobjType.SHOTGUY, MobjType.CHAINGUY, MobjType.TROOP, MobjType.SERGEANT, MobjType.SKULL,
        MobjType.HEAD, MobjType.KNIGHT, MobjType.BRUISER, MobjType.BABY, MobjType.PAIN, MobjType.UNDEAD,
        MobjType.FATSO, MobjType.VILE, MobjType.SPIDER, MobjType.CYBORG, MobjType.PLAYER
    };
    private static final String[] CAST_NAMES = {
        "CC_ZOMBIE", "CC_SHOTGUN", "CC_HEAVY", "CC_IMP", "CC_DEMON", "CC_LOST", "CC_CACO", "CC_HELL", "CC_BARON",
        "CC_ARACH", "CC_PAIN", "CC_REVEN", "CC_MANCU", "CC_ARCH", "CC_SPIDER", "CC_CYBER", "CC_HERO"
    };

    private final World w;
    private final Game game;

    private int finalestage;
    /** {@code finalecount}. */
    private int finalecount;
    private String finaletext = "";
    private String finaleflat = "FLOOR4_8";
    /** {@code strlen(finaletext)}: the C string ends at the first NUL. */
    private int finaletextLength;

    private int castnum;
    private int casttics;
    private State caststate;
    private boolean castdeath;
    private int castframes;
    private int castonmelee;
    private boolean castattacking;

    Finale(World world, Game game) {
        this.w = world;
        this.game = game;
    }

    // ==================================================================================== F_StartFinale

    /** {@code F_StartFinale}. */
    @VanillaRef("f_finale.c:F_StartFinale")
    void start() {
        game.gameAction = Game.GameAction.NOTHING;
        game.gameState = GameState.FINALE;

        if (w.mission == GameMission.DOOM) {
            w.events.changeMusic(MusicId.VICTOR, true);
        } else {
            w.events.changeMusic(MusicId.READ_M, true);
        }

        // Find the right screen and set the text and background. (If none matches, the previous values stay, as
        // the statics of the original do; this cannot happen for a finale that G_DoCompleted / G_WorldDone start.)
        for (TextScreen screen : TEXTSCREENS) {
            if (w.mission == screen.mission()
                    && (w.mission != GameMission.DOOM || w.gameEpisode == screen.episode())
                    && w.gameMap == screen.level()) {
                // Do dehacked substitutions of strings
                finaletext = w.strings.get(screen.text());
                finaleflat = w.strings.translate(screen.background());
            }
        }
        int nul = finaletext.indexOf('\0');
        finaletextLength = nul < 0 ? finaletext.length() : nul;

        finalestage = F_STAGE_TEXT;
        finalecount = 0;
        w.events.finaleStarted();
    }

    // ==================================================================================== F_Ticker

    /** {@code F_Ticker}. */
    @VanillaRef("f_finale.c:F_Ticker")
    void ticker() {
        // check for skipping
        if (w.mode == GameMode.COMMERCIAL && finalecount > 50) {
            // go on to the next level
            int i;
            for (i = 0; i < World.MAXPLAYERS; i++) {
                if (w.players[i].cmd.buttons != 0) {
                    break;
                }
            }
            if (i < World.MAXPLAYERS) {
                if (w.gameMap == 30) {
                    startCast();
                } else {
                    game.gameAction = Game.GameAction.WORLDDONE;
                }
            }
        }

        // advance animation
        finalecount++;

        if (finalestage == F_STAGE_CAST) {
            castTicker();
            return;
        }

        if (w.mode == GameMode.COMMERCIAL) {
            return;
        }

        if (finalestage == F_STAGE_TEXT && finalecount > finaletextLength * TEXTSPEED + TEXTWAIT) {
            finalecount = 0;
            finalestage = F_STAGE_ARTSCREEN;
            w.events.forceWipe(); // wipegamestate = -1: force a wipe
            if (w.gameEpisode == 3) {
                w.events.changeMusic(MusicId.BUNNY, false);
            }
        }
    }

    // ==================================================================================== cast call

    /** {@code F_StartCast}. */
    @VanillaRef("f_finale.c:F_StartCast")
    void startCast() {
        w.events.forceWipe(); // wipegamestate = -1: force a screen wipe
        castnum = 0;
        caststate = state(thing(castnum).seeState);
        casttics = caststate.tics;
        castdeath = false;
        finalestage = F_STAGE_CAST;
        castframes = 0;
        castonmelee = 0;
        castattacking = false;
        w.events.changeMusic(MusicId.EVIL, true);
    }

    private ThingType thing(int castIndex) {
        return w.defs.things()[CAST_TYPES[castIndex]];
    }

    private State state(int index) {
        return w.defs.states()[index];
    }

    /** {@code F_CastTicker}. */
    @VanillaRef("f_finale.c:F_CastTicker")
    private void castTicker() {
        if (--casttics > 0) {
            return; // not time to change state yet
        }

        boolean stopAttack = false;
        if (caststate.tics == -1 || caststate.nextState == StateId.NULL) {
            // switch from deathstate to next monster
            castnum++;
            castdeath = false;
            if (castnum >= CAST_TYPES.length) {
                castnum = 0;
            }
            if (thing(castnum).seeSound != 0) {
                w.events.startSound(-1, thing(castnum).seeSound);
            }
            caststate = state(thing(castnum).seeState);
            castframes = 0;
        } else {
            // just advance to next state in animation
            if (caststate == state(StateId.PLAY_ATK1)) {
                stopAttack = true; // Oh, gross hack!
            } else {
                int st = caststate.nextState;
                caststate = state(st);
                castframes++;

                int sfx = castSound(st);
                if (sfx != 0) {
                    w.events.startSound(-1, sfx);
                }
            }
        }

        if (!stopAttack) {
            if (castframes == 12) {
                // go into attack frame
                castattacking = true;
                if (castonmelee != 0) {
                    caststate = state(thing(castnum).meleeState);
                } else {
                    caststate = state(thing(castnum).missileState);
                }
                castonmelee ^= 1;
                if (caststate == state(StateId.NULL)) {
                    if (castonmelee != 0) {
                        caststate = state(thing(castnum).meleeState);
                    } else {
                        caststate = state(thing(castnum).missileState);
                    }
                }
            }

            if (castattacking) {
                if (castframes == 24 || caststate == state(thing(castnum).seeState)) {
                    stopAttack = true;
                }
            }
        }

        if (stopAttack) {
            castattacking = false;
            castframes = 0;
            caststate = state(thing(castnum).seeState);
        }

        casttics = caststate.tics;
        if (casttics == -1) {
            casttics = 15;
        }
    }

    /** The sound the cast call plays when it enters state {@code st} (0 = none). */
    private static int castSound(int st) {
        return switch (st) {
            case StateId.PLAY_ATK1 -> SoundId.DSHTGN;
            case StateId.POSS_ATK2 -> SoundId.PISTOL;
            case StateId.SPOS_ATK2 -> SoundId.SHOTGN;
            case StateId.VILE_ATK2 -> SoundId.VILATK;
            case StateId.SKEL_FIST2 -> SoundId.SKESWG;
            case StateId.SKEL_FIST4 -> SoundId.SKEPCH;
            case StateId.SKEL_MISS2 -> SoundId.SKEATK;
            case StateId.FATT_ATK8, StateId.FATT_ATK5, StateId.FATT_ATK2 -> SoundId.FIRSHT;
            case StateId.CPOS_ATK2, StateId.CPOS_ATK3, StateId.CPOS_ATK4 -> SoundId.SHOTGN;
            case StateId.TROO_ATK3 -> SoundId.CLAW;
            case StateId.SARG_ATK2 -> SoundId.SGTATK;
            case StateId.BOSS_ATK2, StateId.BOS2_ATK2, StateId.HEAD_ATK2 -> SoundId.FIRSHT;
            case StateId.SKULL_ATK2 -> SoundId.SKLATK;
            case StateId.SPID_ATK2, StateId.SPID_ATK3 -> SoundId.SHOTGN;
            case StateId.BSPI_ATK2 -> SoundId.PLASMA;
            case StateId.CYBER_ATK2, StateId.CYBER_ATK4, StateId.CYBER_ATK6 -> SoundId.RLAUNC;
            case StateId.PAIN_ATK3 -> SoundId.SKLATK;
            default -> 0;
        };
    }

    /**
     * {@code F_CastResponder}: a key press during the cast call kills the current monster. Ignored in the other
     * stages ({@code F_Responder} only forwards events during the cast).
     */
    @VanillaRef({"f_finale.c:F_Responder", "f_finale.c:F_CastResponder"})
    void castKey() {
        if (game.gameState != GameState.FINALE || finalestage != F_STAGE_CAST) {
            return;
        }
        if (castdeath) {
            return; // already in dying frames
        }

        // go into death frame
        castdeath = true;
        caststate = state(thing(castnum).deathState);
        casttics = caststate.tics;
        castframes = 0;
        castattacking = false;
        if (thing(castnum).deathSound != 0) {
            w.events.startSound(-1, thing(castnum).deathSound);
        }
    }

    // ==================================================================================== FinaleView

    @Override
    public int stage() {
        return finalestage;
    }

    @Override
    public int count() {
        return finalecount;
    }

    @Override
    public String text() {
        return finaletext;
    }

    @Override
    public String flat() {
        return finaleflat;
    }

    @Override
    public int castIndex() {
        return castnum;
    }

    @Override
    public String castName() {
        return w.strings.get(CAST_NAMES[castnum]);
    }

    @Override
    public int castSprite() {
        return caststate == null ? 0 : caststate.sprite;
    }

    @Override
    public int castFrame() {
        return caststate == null ? 0 : caststate.frame;
    }
}
