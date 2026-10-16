package doom.wad;

/**
 * Marker section a lump belongs to inside its own WAD file. Sections are delimited by the standard marker lumps,
 * including the deutex variants used by PWADs: {@code S_START}/{@code S_END} or {@code SS_START}/{@code SS_END}
 * (sprites), {@code F_START}/{@code F_END} or {@code FF_START}/{@code FF_END} (flats), {@code P_START}/{@code P_END}
 * or {@code PP_START}/{@code PP_END} (patches). A start marker of one kind may be closed by the end marker of the
 * other spelling ({@code FF_START} … {@code F_END}), as deutex and Chocolate Doom accept.
 *
 * <p>Vanilla lookups ({@code W_CheckNumForName}, {@code R_FlatNumForName}) ignore namespaces; they are informational
 * and used by namespace-aware lookups ({@link LumpDirectory#find(String, LumpNamespace)}).
 */
public enum LumpNamespace {
    /** Outside any section. */
    GLOBAL,
    /** A section marker ({@code S_START}, {@code F1_END}, {@code PP_START}, …). */
    MARKER,
    /** Inside a sprite section. */
    SPRITES,
    /** Inside a flat section. */
    FLATS,
    /** Inside a patch section. */
    PATCHES;

    /** The namespace opened by a start marker, or null when {@code name} (upper case) is not one. */
    static LumpNamespace opening(String name) {
        return switch (name) {
            case "S_START", "SS_START" -> SPRITES;
            case "F_START", "FF_START" -> FLATS;
            case "P_START", "PP_START" -> PATCHES;
            default -> null;
        };
    }

    /** The namespace closed by an end marker, or null when {@code name} (upper case) is not one. */
    static LumpNamespace closing(String name) {
        return switch (name) {
            case "S_END", "SS_END" -> SPRITES;
            case "F_END", "FF_END" -> FLATS;
            case "P_END", "PP_END" -> PATCHES;
            default -> null;
        };
    }

    /** True for the numbered sub-section markers ({@code F1_START} … {@code P3_END}). */
    static boolean isSubMarker(String name) {
        if (name.length() != 6 && name.length() != 8) {
            return false;
        }
        char kind = name.charAt(0);
        char digit = name.charAt(1);
        if ((kind != 'F' && kind != 'P') || digit < '1' || digit > '3') {
            return false;
        }
        String rest = name.substring(2);
        return rest.equals("_START") || rest.equals("_END");
    }
}
