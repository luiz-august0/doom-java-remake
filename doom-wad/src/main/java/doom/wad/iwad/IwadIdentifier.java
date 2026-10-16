package doom.wad.iwad;

import doom.wad.LumpDirectory;
import doom.wad.WadFile;
import doom.wad.WadFormatException;
import java.nio.file.Path;
import java.util.List;

/**
 * Identifies an IWAD the way Chocolate Doom does ({@code D_FindIWAD} / {@code IdentifyIWADByName},
 * {@code W_CheckCorrectIWAD}, {@code D_IdentifyVersion}, {@code InitGameVersion} and the {@code gamevariant} checks
 * of {@code D_DoomMain}). Only the IWAD's own lumps are consulted, because the original identifies the game before
 * any PWAD is loaded.
 *
 * <ul>
 *   <li><b>Mission</b> by file name ({@code doom2.wad}, {@code tnt.wad}, {@code plutonia.wad}, {@code freedm.wad}…);
 *       an unknown name falls back to the first {@code MAP01} or {@code E1M1} lump.</li>
 *   <li><b>Mode</b>: DOOM 2 missions are {@link GameMode#COMMERCIAL}; DOOM 1 is {@link GameMode#RETAIL} with
 *       {@code E4M1}, {@link GameMode#REGISTERED} with {@code E3M1}, else {@link GameMode#SHAREWARE} (ADR-022).</li>
 *   <li><b>Version</b>: shareware, registered and DOOM II → {@link GameVersion#V1_9}; retail →
 *       {@link GameVersion#ULTIMATE}; TNT and Plutonia → {@link GameVersion#FINAL}.</li>
 *   <li><b>Variant</b>: a {@code FREEDOOM} lump means Freedoom ({@code FREEDM} too: FreeDM); {@code DMENUPIC} means
 *       the BFG Edition.</li>
 * </ul>
 */
public final class IwadIdentifier {

    /** {@code iwads[]} of Chocolate Doom's {@code d_iwad.c}, restricted to the games this remake plays. */
    private static final List<NameAndMission> KNOWN_NAMES = List.of(
            new NameAndMission("doom2.wad", GameMission.DOOM2),
            new NameAndMission("plutonia.wad", GameMission.PACK_PLUTONIA),
            new NameAndMission("tnt.wad", GameMission.PACK_TNT),
            new NameAndMission("doom.wad", GameMission.DOOM),
            new NameAndMission("doom1.wad", GameMission.DOOM),
            new NameAndMission("doom2f.wad", GameMission.DOOM2),
            new NameAndMission("freedoom2.wad", GameMission.DOOM2),
            new NameAndMission("freedoom1.wad", GameMission.DOOM),
            new NameAndMission("freedm.wad", GameMission.DOOM2));

    /** {@code unique_lumps[]} of {@code w_main.c}: lumps that only IWADs of other games contain. */
    private static final List<String[]> FOREIGN_LUMPS = List.of(
            new String[] {"IMPXA1", "Heretic", "heretic"},
            new String[] {"ETTNA1", "Hexen", "hexen"},
            new String[] {"AGRDA1", "Strife", "strife"});

    private record NameAndMission(String name, GameMission mission) {
    }

    private IwadIdentifier() {
    }

    /** Identifies {@code iwad} with the automatically detected version. */
    public static IwadInfo identify(WadFile iwad) {
        return identify(iwad, null);
    }

    /**
     * Identifies {@code iwad}.
     *
     * @param forcedVersion the {@code -gameversion} override, or null to detect it
     * @throws WadFormatException when the file is not a DOOM IWAD (original messages)
     */
    public static IwadInfo identify(WadFile iwad, GameVersion forcedVersion) {
        LumpDirectory lumps = LumpDirectory.of(List.of(iwad));
        Path path = iwad.path() != null ? iwad.path() : Path.of(iwad.fileName());

        GameMission mission = missionForFileName(iwad.fileName());
        if (mission == GameMission.NONE) {
            for (int i = 0; i < lumps.size(); i++) {
                String name = lumps.name(i);
                if (name.equals("MAP01")) {
                    mission = GameMission.DOOM2;
                    break;
                } else if (name.equals("E1M1")) {
                    mission = GameMission.DOOM;
                    break;
                }
            }
            if (mission == GameMission.NONE) {
                throw new WadFormatException(iwad.fileName(), null, -1, "Unknown or invalid IWAD file.");
            }
        }

        for (String[] foreign : FOREIGN_LUMPS) {
            if (lumps.find(foreign[0]) >= 0) {
                throw new WadFormatException(iwad.fileName(), foreign[0], -1, "You are trying to use a "
                        + foreign[1] + " IWAD file with the doom binary. This isn't going to work. "
                        + "You probably want to use the " + foreign[2] + " binary.");
            }
        }

        GameMode mode;
        if (mission == GameMission.DOOM) {
            if (lumps.find("E4M1") > 0) {
                mode = GameMode.RETAIL;
            } else if (lumps.find("E3M1") > 0) {
                mode = GameMode.REGISTERED;
            } else {
                mode = GameMode.SHAREWARE;
            }
        } else {
            mode = GameMode.COMMERCIAL;
        }

        GameVersion version = forcedVersion != null ? forcedVersion : defaultVersion(mode, mission);

        // The original executables before Ultimate DOOM do not support the fourth episode.
        if (!version.isAtLeast(GameVersion.ULTIMATE) && mode == GameMode.RETAIL) {
            mode = GameMode.REGISTERED;
        }
        // Executables before Final DOOM do not support Final DOOM.
        if (!version.isAtLeast(GameVersion.FINAL) && mode == GameMode.COMMERCIAL
                && (mission == GameMission.PACK_TNT || mission == GameMission.PACK_PLUTONIA)) {
            mission = GameMission.DOOM2;
        }

        GameVariant variant = GameVariant.VANILLA;
        if (lumps.find("FREEDOOM") >= 0) {
            variant = lumps.find("FREEDM") >= 0 ? GameVariant.FREEDM : GameVariant.FREEDOOM;
        } else if (lumps.find("DMENUPIC") >= 0) {
            variant = GameVariant.BFG_EDITION;
        }

        return new IwadInfo(path, mode, mission, variant, version);
    }

    /**
     * The executable version detected for a mode and mission ({@code InitGameVersion} for the 1.9 family).
     * Chocolate Doom also inspects the demo lumps to pick executables older than v1.9; those are not emulated here,
     * so such IWADs get {@link GameVersion#V1_9}.
     */
    public static GameVersion defaultVersion(GameMode mode, GameMission mission) {
        if (mode == GameMode.SHAREWARE || mode == GameMode.REGISTERED
                || (mode == GameMode.COMMERCIAL && mission == GameMission.DOOM2)) {
            return GameVersion.V1_9;
        } else if (mode == GameMode.RETAIL) {
            return GameVersion.ULTIMATE;
        } else if (mode == GameMode.COMMERCIAL) {
            return GameVersion.FINAL;
        }
        return GameVersion.V1_9;
    }

    /** {@code IdentifyIWADByName}: the mission of a known IWAD file name (case-insensitive), or NONE. */
    public static GameMission missionForFileName(String fileName) {
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String base = fileName.substring(slash + 1);
        for (NameAndMission n : KNOWN_NAMES) {
            if (n.name().equalsIgnoreCase(base)) {
                return n.mission();
            }
        }
        return GameMission.NONE;
    }
}
