package doom.game.session;

import doom.game.api.GameState;
import doom.game.data.CheatId;
import doom.game.data.CheatSequence;
import doom.game.data.MiscSettings;
import doom.game.player.Player;
import doom.game.view.AmmoType;
import doom.game.view.Card;
import doom.game.view.CheatFlags;
import doom.game.view.MusicId;
import doom.game.view.Power;
import doom.game.view.Weapon;
import doom.game.world.World;
import doom.math.annotation.VanillaRef;
import doom.wad.iwad.GameMission;
import doom.wad.iwad.GameMode;
import doom.wad.iwad.GameVersion;

/**
 * The cheat codes of {@code ST_Responder}, fed one typed character at a time ({@code cht_CheckCheat}). The cheat
 * sequences come from the session strings (DEHACKED may change them).
 */
public final class Cheats {

    private final World w;
    private final Game game;
    /** {@code chars_read}, {@code param_chars_read} and {@code parameter_buf} per cheat. */
    private final int[] charsRead = new int[CheatId.values().length];
    private final int[] paramCharsRead = new int[CheatId.values().length];
    private final char[][] paramBuf = new char[CheatId.values().length][CheatSequence.MAX_LENGTH];

    public Cheats(World world, Game game) {
        this.w = world;
        this.game = game;
    }

    /** {@code cht_CheckCheat}: true when the key completes the cheat (and its parameters). */
    @VanillaRef("m_cheat.c:cht_CheckCheat")
    private boolean check(CheatId id, int key) {
        CheatSequence cht = w.strings.cheat(id);
        int i = id.ordinal();
        String sequence = cht.sequence();
        // if we make a short sequence on a cheat with parameters, this will not work in vanilla doom.
        if (cht.parameterChars() > 0 && sequence.length() < cht.originalLength()) {
            return false;
        }
        char k = (char) (key & 0xFF);
        if (charsRead[i] < sequence.length()) {
            // still reading characters from the cheat code and verifying; reset if a key is wrong
            if (k == sequence.charAt(charsRead[i])) {
                ++charsRead[i];
            } else {
                charsRead[i] = 0;
            }
            paramCharsRead[i] = 0;
        } else if (paramCharsRead[i] < cht.parameterChars()) {
            // we have passed the end of the cheat sequence and are entering parameters now
            paramBuf[i][paramCharsRead[i]] = k;
            ++paramCharsRead[i];
        }
        if (charsRead[i] >= sequence.length() && paramCharsRead[i] >= cht.parameterChars()) {
            charsRead[i] = paramCharsRead[i] = 0;
            return true;
        }
        // cheat not matched yet
        return false;
    }

    private void message(Player plyr, String key) {
        String text = w.strings.get(key);
        plyr.message = text;
        w.events.playerMessage(plyr.index, text);
    }

    /** {@code ST_Responder}, key-down part. */
    @VanillaRef("st_stuff.c:ST_Responder")
    public void key(int ch) {
        // G_Responder eats keys during demo playback (they open the menu) and ST_Responder runs only in a level.
        if (game.gameState != GameState.LEVEL || (w.demoPlayback && !game.singleDemo)) {
            return;
        }
        Player plyr = w.players[w.consolePlayer];
        MiscSettings misc = w.defs.misc();

        if (!w.netGame && w.gameSkill != 4) {
            if (check(CheatId.GOD, ch)) {
                // 'dqd' cheat for toggleable god mode
                plyr.cheats ^= CheatFlags.GODMODE;
                if ((plyr.cheats & CheatFlags.GODMODE) != 0) {
                    if (plyr.mo != null) {
                        plyr.mo.health = misc.godModeHealth();
                    }
                    plyr.health = misc.godModeHealth();
                    message(plyr, "STSTR_DQDON");
                } else {
                    message(plyr, "STSTR_DQDOFF");
                }
            } else if (check(CheatId.AMMONOKEY, ch)) {
                // 'fa' cheat for killer fucking arsenal
                plyr.armorPoints = misc.idfaArmor();
                plyr.armorType = misc.idfaArmorClass();
                for (int i = 0; i < Weapon.COUNT; i++) {
                    plyr.weaponOwned[i] = true;
                }
                for (int i = 0; i < AmmoType.COUNT; i++) {
                    plyr.ammo[i] = plyr.maxAmmo[i];
                }
                message(plyr, "STSTR_FAADDED");
            } else if (check(CheatId.AMMO, ch)) {
                // 'kfa' cheat for key full ammo
                plyr.armorPoints = misc.idkfaArmor();
                plyr.armorType = misc.idkfaArmorClass();
                for (int i = 0; i < Weapon.COUNT; i++) {
                    plyr.weaponOwned[i] = true;
                }
                for (int i = 0; i < AmmoType.COUNT; i++) {
                    plyr.ammo[i] = plyr.maxAmmo[i];
                }
                for (int i = 0; i < Card.COUNT; i++) {
                    plyr.cards[i] = true;
                }
                message(plyr, "STSTR_KFAADDED");
            } else if (check(CheatId.MUS, ch)) {
                // 'mus' cheat for changing music
                message(plyr, "STSTR_MUS");
                char[] buf = paramBuf[CheatId.MUS.ordinal()];
                if (w.mode == GameMode.COMMERCIAL || !w.version.isAtLeast(GameVersion.ULTIMATE)) {
                    int n = (buf[0] - '0') * 10 + buf[1] - '0';
                    int musnum = MusicId.RUNNIN + n - 1;
                    if (n > 35) {
                        message(plyr, "STSTR_NOMUS");
                    } else {
                        w.events.changeMusic(musnum, true);
                    }
                } else {
                    int n = (buf[0] - '1') * 9 + (buf[1] - '1');
                    if (n > 31) {
                        message(plyr, "STSTR_NOMUS");
                    } else {
                        w.events.changeMusic(MusicId.E1M1 + n, true);
                    }
                }
            } else if ((w.mission == GameMission.DOOM && check(CheatId.NOCLIP, ch))
                    || (w.mission != GameMission.DOOM && check(CheatId.COMMERCIAL_NOCLIP, ch))) {
                // Noclip cheat: idspispopd for Doom 1, idclip for all others
                plyr.cheats ^= CheatFlags.NOCLIP;
                if ((plyr.cheats & CheatFlags.NOCLIP) != 0) {
                    message(plyr, "STSTR_NCON");
                } else {
                    message(plyr, "STSTR_NCOFF");
                }
            }
            // 'behold?' power-up cheats
            CheatId[] powerups = {CheatId.POWERUP_0, CheatId.POWERUP_1, CheatId.POWERUP_2, CheatId.POWERUP_3,
                CheatId.POWERUP_4, CheatId.POWERUP_5};
            for (int i = 0; i < 6; i++) {
                if (check(powerups[i], ch)) {
                    if (plyr.powers[i] == 0) {
                        w.pickups.givePower(plyr, i);
                    } else if (i != Power.STRENGTH) {
                        plyr.powers[i] = 1;
                    } else {
                        plyr.powers[i] = 0;
                    }
                    message(plyr, "STSTR_BEHOLDX");
                }
            }

            if (check(CheatId.POWERUP_6, ch)) {
                // 'behold' power-up menu
                message(plyr, "STSTR_BEHOLD");
            } else if (check(CheatId.CHOPPERS, ch)) {
                // 'choppers' invulnerability & chainsaw
                plyr.weaponOwned[Weapon.CHAINSAW] = true;
                plyr.powers[Power.INVULNERABILITY] = 1;
                message(plyr, "STSTR_CHOPPERS");
            } else if (check(CheatId.MYPOS, ch)) {
                // 'mypos' for player position
                Player cp = w.players[w.consolePlayer];
                String text = "ang=0x" + Integer.toHexString(cp.mo.angle) + ";x,y=(0x" + Integer.toHexString(cp.mo.x)
                        + ",0x" + Integer.toHexString(cp.mo.y) + ")";
                plyr.message = text;
                w.events.playerMessage(plyr.index, text);
            }
        }

        // 'clev' change-level cheat
        if (!w.netGame && check(CheatId.CLEV, ch)) {
            char[] buf = paramBuf[CheatId.CLEV.ordinal()];
            int epsd;
            int map;
            if (w.mode == GameMode.COMMERCIAL) {
                epsd = 0;
                map = (buf[0] - '0') * 10 + buf[1] - '0';
            } else {
                epsd = buf[0] - '0';
                map = buf[1] - '0';
            }

            // Catch invalid maps.
            if (w.mode != GameMode.COMMERCIAL) {
                if (epsd < 1 || epsd > 4 || (epsd == 4 && !w.version.isAtLeast(GameVersion.ULTIMATE))
                        || map < 1 || map > 9) {
                    return;
                }
            } else {
                if (map < 1 || map > 40) {
                    return;
                }
            }

            // So be it.
            message(plyr, "STSTR_CLEV");
            game.deferredInitNew(w.gameSkill, epsd, map);
        }
    }
}
