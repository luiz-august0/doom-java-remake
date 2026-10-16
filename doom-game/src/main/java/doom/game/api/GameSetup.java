package doom.game.api;

import doom.wad.LumpDirectory;
import doom.wad.iwad.GameVersion;
import doom.wad.iwad.IwadInfo;
import java.util.List;

/**
 * Everything a session needs once, at creation.
 *
 * @param lumps    the loaded WADs (IWAD + PWADs)
 * @param iwad     IWAD identification (mode, mission, variant)
 * @param version  executable version to emulate (auto-detected or forced by {@code -gameversion})
 * @param dehacked DEHACKED patches to apply, in order (empty for none)
 * @param events   callbacks into the presentation layer; may be {@code null} for none (headless traces)
 * @param noIwadDehacked {@code -nodeh}: do not apply the IWAD's own DEHACKED lump (Freedoom variants)
 */
public record GameSetup(LumpDirectory lumps, IwadInfo iwad, GameVersion version, List<DehackedPatch> dehacked,
                        SimEvents events, boolean noIwadDehacked) {

    public GameSetup {
        dehacked = List.copyOf(dehacked);
    }

    /** Setup that applies the IWAD's DEHACKED lump when the variant has one. */
    public GameSetup(LumpDirectory lumps, IwadInfo iwad, GameVersion version, List<DehackedPatch> dehacked, SimEvents events) {
        this(lumps, iwad, version, dehacked, events, false);
    }
}
