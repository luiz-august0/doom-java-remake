package doom.testkit.trace;

import java.util.List;
import java.util.Map;

/**
 * Constants of trace format v1 (docs/contracts.md §6): header, keys, field names and hash parameters.
 */
public final class TraceFormat {
    /** Header prefix of a v1 trace. */
    public static final String HEADER_PREFIX = "#doom-trace v1";
    /** FNV-1a 64-bit offset basis. */
    public static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    /** FNV-1a 64-bit prime. */
    public static final long FNV_PRIME = 0x100000001b3L;

    /** Key of the tic counter. */
    public static final String KEY_TIC = "t";
    /** Key of the misc RNG index: informational only, never gated (§6.2). */
    public static final String KEY_MISC_RNG = "m";
    /** Prefix of the verbose object lines (§6.6). */
    public static final String VERBOSE_INDENT = "  ";

    /** Number of comma-separated values of a {@code p<N>} entry. */
    public static final int PLAYER_FIELD_COUNT = 61;
    /** Number of int32 values per map object in the {@code a} hash and in verbose {@code a<i>} lines. */
    public static final int MOBJ_FIELD_COUNT = 26;

    /** Player field names in {@code p<N>} order (§6.4), index 0 = field 1. */
    public static final List<String> PLAYER_FIELDS = List.of(
            "mo->x", "mo->y", "mo->z", "mo->angle", "mo->momx", "mo->momy", "mo->momz", "mo->health",
            "viewz", "viewheight", "deltaviewheight", "bob",
            "playerstate", "health", "armorpoints", "armortype", "readyweapon", "pendingweapon", "weaponowned",
            "ammo[0]", "ammo[1]", "ammo[2]", "ammo[3]",
            "maxammo[0]", "maxammo[1]", "maxammo[2]", "maxammo[3]",
            "cards", "backpack",
            "powers[0]", "powers[1]", "powers[2]", "powers[3]", "powers[4]", "powers[5]",
            "cheats", "refire", "killcount", "itemcount", "secretcount", "damagecount", "bonuscount",
            "extralight", "fixedcolormap", "colormap", "attacker",
            "psprites[ps_weapon].state", "psprites[ps_weapon].tics", "psprites[ps_weapon].sx", "psprites[ps_weapon].sy",
            "psprites[ps_flash].state", "psprites[ps_flash].tics", "psprites[ps_flash].sx", "psprites[ps_flash].sy",
            "attackdown", "usedown",
            "frags[0]", "frags[1]", "frags[2]", "frags[3]",
            "didsecret");

    /** Map object field names in hash order (§6.5 {@code a}). */
    public static final List<String> MOBJ_FIELDS = List.of(
            "type", "x", "y", "z", "angle", "momx", "momy", "momz", "floorz", "ceilingz", "radius", "height",
            "state", "tics", "sprite", "frame", "flags", "health", "movedir", "movecount", "reactiontime",
            "threshold", "lastlook", "target", "tracer", "player");

    /** Field names of the special thinkers by kind code (§6.5 {@code x}), not counting the code itself. */
    public static final Map<Integer, List<String>> SPECIAL_FIELDS = Map.of(
            1, List.of("type", "sector", "topheight", "speed", "direction", "topwait", "topcountdown"),
            2, List.of("type", "sector", "bottomheight", "topheight", "speed", "crush", "direction", "tag", "olddirection"),
            3, List.of("type", "crush", "sector", "direction", "newspecial", "texture", "floordestheight", "speed"),
            4, List.of("type", "sector", "speed", "low", "high", "wait", "count", "status", "oldstatus", "crush", "tag"),
            5, List.of("sector", "count", "maxlight", "minlight", "maxtime", "mintime"),
            6, List.of("sector", "count", "minlight", "maxlight", "darktime", "brighttime"),
            7, List.of("sector", "minlight", "maxlight", "direction"),
            8, List.of("sector", "count", "maxlight", "minlight"),
            102, List.of("type", "sector", "bottomheight", "topheight", "speed", "crush", "direction", "tag", "olddirection"),
            104, List.of("type", "sector", "speed", "low", "high", "wait", "count", "status", "oldstatus", "crush", "tag"));

    /** Names of the values of the multi-value keys other than {@code p<N>}. */
    public static final Map<String, List<String>> KEY_FIELDS = Map.of(
            "map", List.of("gameepisode", "gamemap", "gameskill"),
            "tot", List.of("totalkills", "totalitems", "totalsecret"),
            "a", List.of("count", "hash"),
            "x", List.of("count", "hash"));

    /** Readable descriptions of the keys (§6.2). */
    public static final Map<String, String> KEY_DESCRIPTIONS = Map.ofEntries(
            Map.entry("t", "tic"),
            Map.entry("gs", "gamestate"),
            Map.entry("r", "game RNG index (prndindex)"),
            Map.entry("lt", "leveltime"),
            Map.entry("map", "episode, map, skill"),
            Map.entry("tot", "totals (kills, items, secrets)"),
            Map.entry("a", "map objects (count, hash)"),
            Map.entry("s", "sectors hash"),
            Map.entry("l", "lines hash"),
            Map.entry("d", "sides hash"),
            Map.entry("x", "special thinkers (count, hash)"),
            Map.entry("m", "misc RNG index (not gated)"));

    private TraceFormat() {
    }

    /** Whether a key takes part in the comparison (every key except {@code m}). */
    public static boolean isGated(String key) {
        return !KEY_MISC_RNG.equals(key);
    }

    /** Whether the key is a player entry {@code p0}..{@code p3}. */
    public static boolean isPlayerKey(String key) {
        return key.length() == 2 && key.charAt(0) == 'p' && key.charAt(1) >= '0' && key.charAt(1) <= '3';
    }

    /**
     * Name of value {@code index} (0-based) of {@code key}: a player field name for {@code p<N>}, the documented
     * component name for {@code map}, {@code tot}, {@code a} and {@code x}, otherwise null.
     */
    public static String fieldName(String key, int index) {
        List<String> names = isPlayerKey(key) ? PLAYER_FIELDS : KEY_FIELDS.get(key);
        return names != null && index >= 0 && index < names.size() ? names.get(index) : null;
    }

    /** Name of value {@code index} of a verbose special-thinker entry ({@code index 0} is the kind code). */
    public static String specialFieldName(int code, int index) {
        if (index == 0) {
            return "code";
        }
        List<String> names = SPECIAL_FIELDS.get(code);
        return names != null && index - 1 < names.size() ? names.get(index - 1) : null;
    }
}
