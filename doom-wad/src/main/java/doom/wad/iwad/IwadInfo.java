package doom.wad.iwad;

import java.nio.file.Path;

/**
 * Identification result for an IWAD: where it is and what it is. Produced by {@link IwadIdentifier}.
 *
 * @param path     the IWAD file
 * @param mode     game mode (decided by the presence of MAP01 / E4M1 / E3M1)
 * @param mission  game mission (TNT and Plutonia by file name)
 * @param variant  release variant (Freedoom, FreeDM, BFG Edition or vanilla)
 * @param version  executable version to emulate (auto-detected; may be overridden by {@code -gameversion})
 */
public record IwadInfo(Path path, GameMode mode, GameMission mission, GameVariant variant, GameVersion version) {

    /** Human-readable game name, as Chocolate Doom's {@code D_SetGameDescription} prints it. */
    public String description() {
        if (mission == GameMission.DOOM) {
            if (variant == GameVariant.FREEDOOM) {
                return "Freedoom: Phase 1";
            }
            return switch (mode) {
                case RETAIL -> "The Ultimate DOOM";
                case REGISTERED -> "DOOM Registered";
                case SHAREWARE -> "DOOM Shareware";
                default -> "Unknown";
            };
        }
        if (variant == GameVariant.FREEDM) {
            return "FreeDM";
        } else if (variant == GameVariant.FREEDOOM) {
            return "Freedoom: Phase 2";
        }
        return switch (mission) {
            case DOOM2 -> "DOOM 2: Hell on Earth";
            case PACK_PLUTONIA -> "DOOM 2: Plutonia Experiment";
            case PACK_TNT -> "DOOM 2: TNT - Evilution";
            default -> "Unknown";
        };
    }
}
