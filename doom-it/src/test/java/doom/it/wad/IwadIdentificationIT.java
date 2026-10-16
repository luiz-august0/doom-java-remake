package doom.it.wad;

import static org.assertj.core.api.Assertions.assertThat;

import doom.wad.iwad.GameMission;
import doom.wad.iwad.GameMode;
import doom.wad.iwad.GameVariant;
import doom.wad.iwad.GameVersion;
import doom.wad.iwad.IwadInfo;
import org.junit.jupiter.api.Test;

/** Identification of the four IWADs of the test set (Chocolate Doom rules). */
class IwadIdentificationIT {

    private static void check(String file, GameMode mode, GameMission mission, GameVariant variant,
                              GameVersion version) throws Exception {
        IwadInfo info = IwadFiles.identify(file);
        assertThat(info.mode()).as("%s mode", file).isEqualTo(mode);
        assertThat(info.mission()).as("%s mission", file).isEqualTo(mission);
        assertThat(info.variant()).as("%s variant", file).isEqualTo(variant);
        assertThat(info.version()).as("%s version", file).isEqualTo(version);
    }

    @Test
    void doom1IsShareware() throws Exception {
        check("doom1.wad", GameMode.SHAREWARE, GameMission.DOOM, GameVariant.VANILLA, GameVersion.V1_9);
    }

    @Test
    void freedoom1IsRetail() throws Exception {
        check("freedoom1.wad", GameMode.RETAIL, GameMission.DOOM, GameVariant.FREEDOOM, GameVersion.ULTIMATE);
    }

    @Test
    void freedoom2IsCommercial() throws Exception {
        check("freedoom2.wad", GameMode.COMMERCIAL, GameMission.DOOM2, GameVariant.FREEDOOM, GameVersion.V1_9);
    }

    @Test
    void freedmIsCommercialDeathmatch() throws Exception {
        check("freedm.wad", GameMode.COMMERCIAL, GameMission.DOOM2, GameVariant.FREEDM, GameVersion.V1_9);
    }
}
