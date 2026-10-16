package doom.wad.iwad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import doom.wad.WadFile;
import doom.wad.WadFormatException;
import doom.wad.WadWriter;
import org.junit.jupiter.api.Test;

class IwadIdentifierTest {

    private static WadFile wad(String file, String... markers) {
        WadWriter w = WadWriter.iwad().add("PLAYPAL", new byte[1]);
        for (String m : markers) {
            w.marker(m);
        }
        return w.toWadFile(file);
    }

    private static void check(IwadInfo info, GameMode mode, GameMission mission, GameVariant variant,
                              GameVersion version) {
        assertThat(info.mode()).isEqualTo(mode);
        assertThat(info.mission()).isEqualTo(mission);
        assertThat(info.variant()).isEqualTo(variant);
        assertThat(info.version()).isEqualTo(version);
    }

    @Test
    void doomModesDependOnEpisodeMaps() {
        check(IwadIdentifier.identify(wad("doom1.wad", "E1M1")),
                GameMode.SHAREWARE, GameMission.DOOM, GameVariant.VANILLA, GameVersion.V1_9);
        check(IwadIdentifier.identify(wad("doom.wad", "E1M1", "E2M1", "E3M1")),
                GameMode.REGISTERED, GameMission.DOOM, GameVariant.VANILLA, GameVersion.V1_9);
        // E2M1 alone does not make a registered IWAD (Chocolate Doom tests E3M1).
        check(IwadIdentifier.identify(wad("doom.wad", "E1M1", "E2M1")),
                GameMode.SHAREWARE, GameMission.DOOM, GameVariant.VANILLA, GameVersion.V1_9);
        check(IwadIdentifier.identify(wad("DOOM.WAD", "E1M1", "E3M1", "E4M1")),
                GameMode.RETAIL, GameMission.DOOM, GameVariant.VANILLA, GameVersion.ULTIMATE);
    }

    @Test
    void doom2MissionsComeFromTheFileName() {
        check(IwadIdentifier.identify(wad("doom2.wad", "MAP01")),
                GameMode.COMMERCIAL, GameMission.DOOM2, GameVariant.VANILLA, GameVersion.V1_9);
        check(IwadIdentifier.identify(wad("tnt.wad", "MAP01")),
                GameMode.COMMERCIAL, GameMission.PACK_TNT, GameVariant.VANILLA, GameVersion.FINAL);
        check(IwadIdentifier.identify(wad("Plutonia.WAD", "MAP01")),
                GameMode.COMMERCIAL, GameMission.PACK_PLUTONIA, GameVariant.VANILLA, GameVersion.FINAL);
    }

    @Test
    void unknownNamesAreIdentifiedByTheFirstMapLump() {
        check(IwadIdentifier.identify(wad("custom.wad", "MAP01", "E1M1")),
                GameMode.COMMERCIAL, GameMission.DOOM2, GameVariant.VANILLA, GameVersion.V1_9);
        check(IwadIdentifier.identify(wad("custom.wad", "E1M1", "MAP01", "E4M1")),
                GameMode.RETAIL, GameMission.DOOM, GameVariant.VANILLA, GameVersion.ULTIMATE);
        assertThatThrownBy(() -> IwadIdentifier.identify(wad("custom.wad", "TITLEPIC")))
                .isInstanceOf(WadFormatException.class)
                .hasMessageContaining("Unknown or invalid IWAD file.");
    }

    @Test
    void variantsFollowChocolateDoom() {
        check(IwadIdentifier.identify(wad("freedoom1.wad", "FREEDOOM", "E1M1", "E3M1", "E4M1")),
                GameMode.RETAIL, GameMission.DOOM, GameVariant.FREEDOOM, GameVersion.ULTIMATE);
        check(IwadIdentifier.identify(wad("freedoom2.wad", "FREEDOOM", "MAP01")),
                GameMode.COMMERCIAL, GameMission.DOOM2, GameVariant.FREEDOOM, GameVersion.V1_9);
        check(IwadIdentifier.identify(wad("freedm.wad", "FREEDOOM", "FREEDM", "MAP01")),
                GameMode.COMMERCIAL, GameMission.DOOM2, GameVariant.FREEDM, GameVersion.V1_9);
        check(IwadIdentifier.identify(wad("doom2.wad", "DMENUPIC", "MAP01")),
                GameMode.COMMERCIAL, GameMission.DOOM2, GameVariant.BFG_EDITION, GameVersion.V1_9);
    }

    @Test
    void forcedVersionsAdjustModeAndMission() {
        check(IwadIdentifier.identify(wad("doom.wad", "E1M1", "E3M1", "E4M1"), GameVersion.V1_9),
                GameMode.REGISTERED, GameMission.DOOM, GameVariant.VANILLA, GameVersion.V1_9);
        check(IwadIdentifier.identify(wad("tnt.wad", "MAP01"), GameVersion.ULTIMATE),
                GameMode.COMMERCIAL, GameMission.DOOM2, GameVariant.VANILLA, GameVersion.ULTIMATE);
        check(IwadIdentifier.identify(wad("tnt.wad", "MAP01"), GameVersion.FINAL2),
                GameMode.COMMERCIAL, GameMission.PACK_TNT, GameVariant.VANILLA, GameVersion.FINAL2);
    }

    @Test
    void foreignIwadsAreRejected() {
        assertThatThrownBy(() -> IwadIdentifier.identify(wad("heretic.wad", "E1M1", "IMPXA1")))
                .isInstanceOf(WadFormatException.class)
                .hasMessageContaining("Heretic IWAD");
    }

    @Test
    void descriptions() {
        assertThat(IwadIdentifier.identify(wad("doom1.wad", "E1M1")).description()).isEqualTo("DOOM Shareware");
        assertThat(IwadIdentifier.identify(wad("freedm.wad", "FREEDOOM", "FREEDM", "MAP01")).description())
                .isEqualTo("FreeDM");
        assertThat(IwadIdentifier.missionForFileName("/x/y/TNT.WAD")).isEqualTo(GameMission.PACK_TNT);
        assertThat(IwadIdentifier.missionForFileName("other.wad")).isEqualTo(GameMission.NONE);
    }
}
