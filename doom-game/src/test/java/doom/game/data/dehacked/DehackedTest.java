package doom.game.data.dehacked;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import doom.game.api.DehackedPatch;
import doom.game.data.CheatId;
import doom.game.data.GameDefs;
import doom.game.data.GameStrings;
import doom.game.data.MiscSetting;
import doom.game.data.MobjType;
import doom.game.data.StateAction;
import doom.game.data.StateId;
import doom.game.view.AmmoType;
import doom.game.view.SoundId;
import doom.game.view.Weapon;
import doom.math.DoomFatalException;
import org.junit.jupiter.api.Test;

class DehackedTest {

    private static final String HEADER = "Patch File for DeHackEd v3.0\nDoom version = 19\nPatch format = 6\n\n";

    private final GameDefs defs = GameDefs.createDefault();
    private final GameStrings strings = GameStrings.createDefault();

    private Dehacked.Result apply(String body) {
        return Dehacked.apply(defs, strings, new DehackedPatch("test", HEADER + body));
    }

    @Test
    void thingFieldsAreIndexedFromOne() {
        Dehacked.Result r = apply("Thing 2 (Zombieman)\nHit points = 33\nSpeed = 9\nBits = 4194310\nBogus = 1\n\n");
        assertThat(defs.things()[MobjType.POSSESSED].spawnHealth).isEqualTo(33);
        assertThat(defs.things()[MobjType.POSSESSED].speed).isEqualTo(9);
        assertThat(r.warnings()).hasSize(1).first().asString().contains("Field named 'Bogus' not found");
        assertThat(r.hadError()).isFalse();
    }

    @Test
    void valuesGoThroughAtoiAndSectionNumbersThroughScanfI() {
        apply("Thing 0x2\nHit points = 12abc\nMass = -7\nWidth = abc\n\n");
        assertThat(defs.things()[MobjType.POSSESSED].spawnHealth).isEqualTo(12);
        assertThat(defs.things()[MobjType.POSSESSED].mass).isEqualTo(-7);
        assertThat(defs.things()[MobjType.POSSESSED].radius).isEqualTo(0);
    }

    @Test
    void frameAndPointerSections() {
        apply("Frame 10\nDuration = 5\nSprite subnumber = 32773\nNext frame = 3\nUnknown 1 = 9\nCodep frame = 1\n\n"
                + "Pointer 0 (Frame 11)\nCodep frame = 183\n\n");
        assertThat(defs.states()[10].tics).isEqualTo(5);
        assertThat(defs.states()[10].frame).isEqualTo(32773);
        assertThat(defs.states()[10].nextState).isEqualTo(3);
        assertThat(defs.states()[10].misc1).isEqualTo(9);
        // Pointer N (Frame M): states[M].action = the vanilla action of state "Codep frame"
        assertThat(defs.states()[11].action).isEqualTo(GameDefs.createDefault().states()[183].action);
        assertThat(defs.states()[11].action).isNotEqualTo(StateAction.NONE);
    }

    @Test
    void theLastFrameOverflowsIntoTheFistWeapon() {
        Dehacked.Result r = apply("Frame 966\nDuration = 3\nNext frame = 8\nCodep frame = 6\n\n");
        assertThat(defs.weapons()[Weapon.FIST].ammo).isEqualTo(3);
        assertThat(defs.weapons()[Weapon.FIST].downState).isEqualTo(8);
        assertThat(defs.weapons()[Weapon.FIST].upState).isEqualTo(6);
        assertThat(r.warnings()).anyMatch(w -> w.contains("Attempt to modify frame 966"));
        assertThatThrownBy(() -> apply("Frame 966\nSprite number = 3\n\n")).isInstanceOf(DoomFatalException.class);
    }

    @Test
    void soundAmmoWeaponAndMisc() {
        apply("Sound 1\nValue = 99\nZero 2 = 120\nOffset = 5\n\n"
                + "Ammo 1\nMax ammo = 75\nPer ammo = 6\n\n"
                + "Weapon 1\nAmmo type = 5\nFiring frame = 4\n\n"
                + "Misc 0\nInitial Health = 150\nMax Armor = 300\nBFG Cells/Shot = 20\nMonsters Infight = 221\nNo Such = 1\n\n");
        assertThat(defs.sounds()[SoundId.PISTOL].priority).isEqualTo(99);
        assertThat(defs.sounds()[SoundId.PISTOL].pitch).isEqualTo(120);
        assertThat(defs.maxAmmo()[AmmoType.SHELL]).isEqualTo(75);
        assertThat(defs.clipAmmo()[AmmoType.SHELL]).isEqualTo(6);
        assertThat(defs.weapons()[Weapon.PISTOL].ammo).isEqualTo(5);
        assertThat(defs.weapons()[Weapon.PISTOL].flashState).isEqualTo(4);
        assertThat(defs.misc().initialHealth()).isEqualTo(150);
        assertThat(defs.misc().maxArmor()).isEqualTo(300);
        assertThat(defs.misc().bfgCellsPerShot()).isEqualTo(20);
        assertThat(defs.misc().speciesInfighting()).isTrue();
        assertThat(defs.misc().get(MiscSetting.GOD_MODE_HEALTH)).isEqualTo(100);
    }

    @Test
    void invalidIndexesAreWarningsAndTheSectionIsSkipped() {
        Dehacked.Result r = apply("Thing 999\nHit points = 1\n\nWeapon 9\nAmmo type = 1\n\nAmmo 4\nMax ammo = 1\n\n");
        assertThat(r.warnings()).hasSize(3);
        assertThat(defs.things()[0].spawnHealth).isEqualTo(100);
    }

    @Test
    void textReplacesByOriginalText() {
        // "Text 4 5" replaces the 4-character text with the 5-character one (TXT_MaxStringLength(4) == 7)
        apply("Text 4 5\nTROOhello\n\n");
        assertThat(strings.translate("TROO")).isEqualTo("hello");
        assertThat(strings.translate("SHTG")).isEqualTo("SHTG");
        String quit = strings.original("QUITMSG");
        apply("Text " + quit.length() + " 3\n" + quit + "bye\n\n");
        assertThat(strings.get("QUITMSG")).isEqualTo("bye");
    }

    @Test
    void textLongerThanDosAllowsIsAnError() {
        Dehacked.Result r = Dehacked.apply(defs, strings,
                new DehackedPatch("test", HEADER + "Text 4 40\nTROO0123456789012345678901234567890123456789\n\n"),
                Dehacked.Options.iwadLump());
        assertThat(r.hadError()).isTrue();
        assertThat(strings.translate("TROO")).isEqualTo("TROO");
        assertThatThrownBy(() -> apply("Text 4 40\n" + "x".repeat(44) + "\n\n")).isInstanceOf(DoomFatalException.class);

        String magic = "# *allow-long-strings*\nText 4 12\nTROOabcdefghijkl\n\n";
        apply(magic);
        assertThat(strings.translate("TROO")).isEqualTo("abcdefghijkl");
    }

    @Test
    void bexStringsNeedTheMagicComment() {
        String section = "[STRINGS]\nQUITMSG = so long,\\n\\\n    and thanks\nNOT_A_KEY = x\n\n";
        apply(section);
        assertThat(strings.get("QUITMSG")).isEqualTo(strings.original("QUITMSG"));

        apply("# *allow-extended-strings*\n" + section);
        assertThat(strings.get("QUITMSG")).isEqualTo("so long,\nand thanks");
        assertThat(strings.substitutions()).hasSize(1);
    }

    @Test
    void unknownSectionsAreSkipped() {
        Dehacked.Result r = apply("[PARS]\npar 1 1 30\n\n[CODEPTR]\nFrame 10 = Light0\n\nThing 2\nHit points = 5\n\n");
        assertThat(r.warnings()).isEmpty();
        assertThat(defs.things()[MobjType.POSSESSED].spawnHealth).isEqualTo(5);
    }

    @Test
    void cheatSequencesFollowTheVanillaLengthLimit() {
        apply("Cheat 0\nGod mode = idkfa\nAmmo = id\nChange music = idmusic\n\n");
        assertThat(strings.cheat(CheatId.GOD).sequence()).isEqualTo("idkfa");
        assertThat(strings.cheat(CheatId.AMMONOKEY).sequence()).isEqualTo("id");
        // longer than the vanilla 5 characters: truncated with a warning
        assertThat(strings.cheat(CheatId.MUS).sequence()).isEqualTo("idmus");

        apply("# *allow-long-cheats*\nCheat 0\nGod mode = iddqdiddqd\n\n");
        assertThat(strings.cheat(CheatId.GOD).sequence()).isEqualTo("iddqdiddqd");
    }

    @Test
    void shorterCheatLeavesTheOldTailBehindTheTerminator() {
        apply("Cheat 0\nNo Clipping 1 = idclip\n\n");
        assertThat(strings.cheat(CheatId.NOCLIP).sequence()).isEqualTo("idclip");
        assertThat(strings.cheat(CheatId.NOCLIP).at(6)).isEqualTo('\0');
        assertThat(strings.cheat(CheatId.NOCLIP).at(7)).isEqualTo('o');
    }

    @Test
    void withoutCheatsTheCheatSectionIsIgnored() {
        Dehacked.apply(defs, strings, new DehackedPatch("test", HEADER + "Cheat 0\nGod mode = idkfa\n\n"),
                Dehacked.Options.file().withoutCheats());
        assertThat(strings.cheat(CheatId.GOD).sequence()).isEqualTo("iddqd");
    }

    @Test
    void crlfAndLoneCrAreHandled() {
        apply("Thing 2\r\nHit points = 44\r\n\r\n");
        assertThat(defs.things()[MobjType.POSSESSED].spawnHealth).isEqualTo(44);
    }

    @Test
    void aBadSignatureIsFatalUnlessErrorsAreAllowed() {
        DehackedPatch bad = new DehackedPatch("bad", "Not a patch\nThing 2\nHit points = 1\n\n");
        assertThatThrownBy(() -> Dehacked.apply(defs, strings, bad)).isInstanceOf(DoomFatalException.class)
                .hasMessage("Error parsing dehacked file");
        Dehacked.Result r = Dehacked.apply(defs, strings, bad, Dehacked.Options.iwadLump());
        assertThat(r.hadError()).isTrue();
        assertThat(defs.things()[MobjType.POSSESSED].spawnHealth).isEqualTo(20);
    }

    @Test
    void anErrorStopsTheParseButKeepsEarlierChanges() {
        Dehacked.Result r = Dehacked.apply(defs, strings,
                new DehackedPatch("test", HEADER + "Thing 2\nHit points = 5\n\nSound 1\nZero 1 = 3\nValue = 7\n\n"),
                Dehacked.Options.iwadLump());
        assertThat(r.hadError()).isTrue();
        assertThat(defs.things()[MobjType.POSSESSED].spawnHealth).isEqualTo(5);
        assertThat(defs.states()[StateId.NULL].tics).isEqualTo(-1);
    }
}
