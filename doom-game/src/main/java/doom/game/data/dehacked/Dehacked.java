package doom.game.data.dehacked;

import doom.game.api.DehackedPatch;
import doom.game.data.CheatSequence;
import doom.game.data.GameDefs;
import doom.game.data.GameStrings;
import doom.game.data.MiscSetting;
import doom.game.data.MiscSettings;
import doom.game.data.MobjType;
import doom.game.data.SoundInfo;
import doom.game.data.State;
import doom.game.data.StateAction;
import doom.game.data.StateId;
import doom.game.data.ThingType;
import doom.game.data.WeaponInfo;
import doom.game.view.AmmoType;
import doom.game.view.SoundId;
import doom.game.view.Weapon;
import doom.math.DoomFatalException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DEHACKED patch application, compatible with Chocolate Doom 3.1.1 ({@code deh_main.c} and
 * {@code src/doom/deh_*.c}, ADR-022): the sections Thing, Frame, Pointer, Sound, Ammo, Weapon, Misc, Text, Cheat
 * and BEX {@code [STRINGS]}. Any other section (BEX {@code [PARS]}, {@code [CODEPTR]} ...) is skipped without a
 * message, as Chocolate Doom does, and unknown fields only produce a warning.
 *
 * <p>The reader is a line-by-line port, so quirks carry over: values go through {@code atoi}, section headers
 * through {@code sscanf}, {@code Text} replaces by original text, and the frame after the last vanilla state
 * overflows into {@code weaponinfo[0]}. The magic comments {@code *allow-long-strings*},
 * {@code *allow-long-cheats*} and {@code *allow-extended-strings*} apply to the patch that contains them.
 *
 * <p>The patch text must be decoded as ISO-8859-1 so that every byte of the lump becomes one character.
 */
public final class Dehacked {

    private static final System.Logger LOG = System.getLogger(Dehacked.class.getName());

    /** {@code DEH_VANILLA_NUMSTATES}: states after this index are not addressable by DOS DEHACKED. */
    private static final int VANILLA_NUMSTATES = 966;
    /** {@code DEH_VANILLA_NUMSFX}. */
    private static final int VANILLA_NUMSFX = 107;
    private static final int MAX_LINE_TOKEN = 19;

    private static final String[] SIGNATURES = {"Patch File for DeHackEd v2.3", "Patch File for DeHackEd v3.0"};

    /**
     * How a patch is loaded, which decides what Chocolate Doom allows and how it reacts to errors.
     *
     * @param lump       loaded from a WAD lump ({@code DEH_LoadLump}) rather than from a file ({@code -deh})
     * @param allowLong  start with long strings and long cheats allowed (only the lump loader can set this)
     * @param allowError ignore a parse error instead of stopping the game (the patch stays applied up to the error)
     * @param applyCheats {@code false} for {@code -nocheats}: {@code Cheat} sections are parsed and ignored
     */
    public record Options(boolean lump, boolean allowLong, boolean allowError, boolean applyCheats) {

        /** {@code -deh <file>}: strict, no long strings. */
        public static Options file() {
            return new Options(false, false, false, true);
        }

        /** {@code DEH_LoadLump(lump, allow_long, allow_error)}. */
        public static Options lump(boolean allowLong, boolean allowError) {
            return new Options(true, allowLong, allowError, true);
        }

        /** The lump of a Freedoom or FreeDM IWAD: errors are ignored (old IWADs have invalid lumps). */
        public static Options iwadLump() {
            return lump(false, true);
        }

        public Options withoutCheats() {
            return new Options(lump, allowLong, allowError, false);
        }
    }

    /**
     * What a patch produced.
     *
     * @param warnings messages in Chocolate Doom's {@code file:line: warning: ...} form
     * @param errors   messages of the (at most one) error that stopped the parse
     */
    public record Result(List<String> warnings, List<String> errors) {

        public boolean hadError() {
            return !errors.isEmpty();
        }
    }

    private enum Section {
        AMMO("Ammo"), CHEAT("Cheat"), FRAME("Frame"), MISC("Misc"), POINTER("Pointer"), SOUND("Sound"), TEXT("Text"),
        THING("Thing"), WEAPON("Weapon"), BEX_STRINGS("[STRINGS]");

        final String name;

        Section(String name) {
            this.name = name;
        }
    }

    /** A field of a table entry: a setter, or {@code null} for the fields DEHACKED knows but Chocolate rejects. */
    private interface Setter<T> {
        void set(T target, int value);
    }

    private static final Map<String, Setter<ThingType>> THING_FIELDS = thingFields();
    private static final Map<String, Setter<State>> FRAME_FIELDS = frameFields();
    private static final Map<String, Setter<WeaponInfo>> WEAPON_FIELDS = weaponFields();
    private static final Map<String, Setter<SoundInfo>> SOUND_FIELDS = soundFields();

    private final GameDefs defs;
    private final GameStrings strings;
    private final Options options;
    private final DehReader in;
    /** The actions of the vanilla states, which Pointer sections refer to by state number ({@code codeptrs[]}). */
    private final StateAction[] codePointers;

    private boolean allowLongStrings;
    private boolean allowLongCheats;
    private boolean allowExtendedStrings;

    private Dehacked(GameDefs defs, GameStrings strings, DehackedPatch patch, Options options) {
        this.defs = defs;
        this.strings = strings;
        this.options = options;
        this.in = new DehReader(patch.source(), patch.text());
        this.allowLongStrings = options.allowLong();
        this.allowLongCheats = options.allowLong();
        GameDefs pristine = GameDefs.createDefault();
        this.codePointers = new StateAction[pristine.states().length];
        for (int i = 0; i < codePointers.length; i++) {
            codePointers[i] = pristine.states()[i].action;
        }
    }

    /** Applies a patch loaded like {@code -deh <file>}. */
    public static Result apply(GameDefs defs, GameStrings strings, DehackedPatch patch) {
        return apply(defs, strings, patch, Options.file());
    }

    /**
     * Applies a patch to the session's tables.
     *
     * @throws DoomFatalException on a parse error, unless {@link Options#allowError()}
     */
    // Chocolate Doom: deh_main.c DEH_LoadLump
    public static Result apply(GameDefs defs, GameStrings strings, DehackedPatch patch, Options options) {
        Dehacked d = new Dehacked(defs, strings, patch, options);
        d.parse();
        for (String w : d.in.warnings()) {
            LOG.log(System.Logger.Level.DEBUG, w);
        }
        for (String e : d.in.errors()) {
            LOG.log(System.Logger.Level.WARNING, e);
        }
        if (d.in.hadError() && !options.allowError()) {
            throw new DoomFatalException(options.lump() ? "Error parsing dehacked lump" : "Error parsing dehacked file");
        }
        return new Result(List.copyOf(d.in.warnings()), List.copyOf(d.in.errors()));
    }

    // ------------------------------------------------------------------------------------------------
    // Main loop

    // Chocolate Doom: deh_main.c DEH_ParseContext
    private void parse() {
        String first = in.readLine(false);
        boolean valid = false;
        if (first != null) {
            for (String signature : SIGNATURES) {
                if (signature.equals(first)) {
                    valid = true;
                }
            }
        }
        if (!valid) {
            in.error("This is not a valid dehacked patch file!");
        }

        Section current = null;
        Object tag = null;
        while (!in.hadError()) {
            String line = in.readLine(current == Section.BEX_STRINGS);
            if (line == null) {
                return;
            }
            int skip = 0;
            while (skip < line.length() && CScan.isSpace(line.charAt(skip))) {
                skip++;
            }
            line = line.substring(skip);

            if (!line.isEmpty() && line.charAt(0) == '#') {
                parseComment(line);
                continue;
            }
            if (isWhitespace(line)) {
                current = null;
            } else if (current != null) {
                parseLine(current, line, tag);
            } else {
                String name = new CScan(line).word(MAX_LINE_TOKEN);
                current = sectionByName(name);
                if (current != null) {
                    tag = start(current, line);
                }
            }
        }
    }

    private static boolean isWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!CScan.isSpace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** The Chocolate Doom specific overrides hidden in comments. */
    private void parseComment(String comment) {
        if (comment.contains("*allow-long-strings*")) {
            allowLongStrings = true;
        }
        if (comment.contains("*allow-long-cheats*")) {
            allowLongCheats = true;
        }
        if (comment.contains("*allow-extended-strings*")) {
            allowExtendedStrings = true;
        }
    }

    private Section sectionByName(String name) {
        if (!allowExtendedStrings && CScan.startsWithIgnoreCase(name, "[STRINGS]")) {
            return null;
        }
        for (Section s : Section.values()) {
            if (CScan.equalsIgnoreCase(s.name, name)) {
                return s;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------
    // Section starts. The returned tag identifies the entry the following lines modify.

    private Object start(Section section, String line) {
        return switch (section) {
            case THING -> startThing(line);
            case FRAME -> startFrame(line);
            case POINTER -> startPointer(line);
            case SOUND -> startSound(line);
            case AMMO -> startAmmo(line);
            case WEAPON -> startWeapon(line);
            case TEXT -> startText(line);
            case BEX_STRINGS -> {
                String s = new CScan(line).word(9);
                if (s == null || !"[STRINGS]".equals(s)) {
                    in.warning("Parse error on section start");
                }
                yield null;
            }
            case CHEAT, MISC -> null;
        };
    }

    private Integer scanNumber(String line, String keyword) {
        CScan sc = new CScan(line);
        Integer n = sc.literal(keyword + " ") ? sc.integer() : null;
        if (n == null) {
            in.warning("Parse error on section start");
        }
        return n;
    }

    // Chocolate Doom: deh_thing.c DEH_ThingStart
    private Object startThing(String line) {
        Integer n = scanNumber(line, "Thing");
        if (n == null) {
            return null;
        }
        // DEHACKED files are indexed from 1
        int index = n - 1;
        if (index < 0 || index >= MobjType.COUNT) {
            in.warning("Invalid thing number: " + index);
            return null;
        }
        return defs.things()[index];
    }

    // Chocolate Doom: deh_frame.c DEH_FrameStart
    private Object startFrame(String line) {
        Integer n = scanNumber(line, "Frame");
        if (n == null) {
            return null;
        }
        if (n < 0 || n >= StateId.COUNT) {
            in.warning("Invalid frame number: " + n);
            return null;
        }
        if (n >= VANILLA_NUMSTATES) {
            in.warning("Attempt to modify frame " + n + ": this will cause problems in Vanilla dehacked.");
        }
        return new FrameTag(n);
    }

    // Chocolate Doom: deh_ptr.c DEH_PointerStart
    private Object startPointer(String line) {
        CScan sc = new CScan(line);
        Integer frame = null;
        if (sc.literal("Pointer ") && sc.integer() != null && sc.literal(" (") && sc.word() && sc.literal(" ")) {
            frame = sc.integer();
        }
        if (frame == null) {
            in.warning("Parse error on section start");
            return null;
        }
        if (frame < 0 || frame >= StateId.COUNT) {
            in.warning("Invalid frame number: " + frame);
            return null;
        }
        return defs.states()[frame];
    }

    // Chocolate Doom: deh_sound.c DEH_SoundStart
    private Object startSound(String line) {
        Integer n = scanNumber(line, "Sound");
        if (n == null) {
            return null;
        }
        if (n < 0 || n >= SoundId.COUNT) {
            in.warning("Invalid sound number: " + n);
            return null;
        }
        if (n >= VANILLA_NUMSFX) {
            in.warning("Attempt to modify SFX " + n + ".  This will cause problems in Vanilla dehacked.");
        }
        return defs.sounds()[n];
    }

    // Chocolate Doom: deh_ammo.c DEH_AmmoStart
    private Object startAmmo(String line) {
        Integer n = scanNumber(line, "Ammo");
        if (n == null) {
            return null;
        }
        if (n < 0 || n >= AmmoType.COUNT) {
            in.warning("Invalid ammo number: " + n);
            return null;
        }
        return new AmmoTag(n);
    }

    // Chocolate Doom: deh_weapon.c DEH_WeaponStart
    private Object startWeapon(String line) {
        Integer n = scanNumber(line, "Weapon");
        if (n == null) {
            return null;
        }
        if (n < 0 || n >= Weapon.COUNT) {
            in.warning("Invalid weapon number: " + n);
            return null;
        }
        return defs.weapons()[n];
    }

    /**
     * {@code Text <from length> <to length>}: the two texts follow the header line directly, so they are read
     * character by character from the stream, not line by line.
     */
    // Chocolate Doom: deh_text.c DEH_TextStart
    private Object startText(String line) {
        CScan sc = new CScan(line);
        Integer fromLen = sc.literal("Text ") ? sc.integer() : null;
        Integer toLen = fromLen != null ? sc.literal(" ") ? sc.integer() : null : null;
        if (fromLen == null || toLen == null) {
            in.warning("Parse error on section start");
            return null;
        }
        // Only allow string replacements that are possible in Vanilla Doom.
        if (!allowLongStrings && toLen > maxStringLength(fromLen)) {
            in.error("Replacement string is longer than the maximum possible in doom.exe");
            return null;
        }
        if (fromLen < 0 || toLen < 0) {
            in.error("Invalid string length");
            return null;
        }
        String from = readFixed(fromLen);
        String to = readFixed(toLen);
        strings.replace(from, to);
        return null;
    }

    /** {@code TXT_MaxStringLength}: strings sit on 4-byte boundaries in DOOM.EXE. */
    private static int maxStringLength(int len) {
        len += 1;
        len += (4 - (len % 4)) % 4;
        return len - 1;
    }

    /** Reads {@code count} characters and cuts the result at the first NUL, like the C string it becomes. */
    private String readFixed(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            int c = in.getChar();
            sb.append((char) (c & 0xff));
        }
        int nul = sb.indexOf("\0");
        return nul >= 0 ? sb.substring(0, nul) : sb.toString();
    }

    // ------------------------------------------------------------------------------------------------
    // Section lines

    private record FrameTag(int index) {
    }

    private record AmmoTag(int index) {
    }

    private void parseLine(Section section, String line, Object tag) {
        switch (section) {
            case TEXT -> {
                // not used
            }
            case CHEAT -> parseCheatLine(line);
            case MISC -> parseMiscLine(line);
            case BEX_STRINGS -> parseBexLine(line);
            default -> {
                if (tag == null) {
                    return;
                }
                parseEntryLine(section, line, tag);
            }
        }
    }

    private void parseEntryLine(Section section, String line, Object tag) {
        String[] assignment = parseAssignment(line);
        if (assignment == null) {
            in.warning("Failed to parse assignment");
            return;
        }
        String name = assignment[0];
        int value = CScan.atoi(assignment[1]);
        switch (section) {
            case THING -> setField(THING_FIELDS, (ThingType) tag, name, value);
            case WEAPON -> setField(WEAPON_FIELDS, (WeaponInfo) tag, name, value);
            case FRAME -> parseFrameLine((FrameTag) tag, name, value);
            case POINTER -> parsePointerLine((State) tag, name, value);
            case SOUND -> parseSoundLine((SoundInfo) tag, name, value);
            case AMMO -> parseAmmoLine((AmmoTag) tag, name, value);
            default -> throw new IllegalStateException(section.name);
        }
    }

    /** {@code DEH_ParseAssignment}: split at the first '=' and trim both sides; null when there is no '='. */
    private static String[] parseAssignment(String line) {
        int eq = line.indexOf('=');
        if (eq < 0) {
            return null;
        }
        return new String[] {clean(line.substring(0, eq)), clean(line.substring(eq + 1))};
    }

    private static String clean(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && CScan.isSpace(s.charAt(start))) {
            start++;
        }
        while (end > start && CScan.isSpace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(start, end);
    }

    /** {@code DEH_SetMapping}: sets a field by its DEHACKED name. */
    private <T> void setField(Map<String, Setter<T>> fields, T target, String name, int value) {
        String key = name.toLowerCase(Locale.ROOT);
        if (!fields.containsKey(key)) {
            in.warning("Field named '" + name + "' not found");
            return;
        }
        Setter<T> setter = fields.get(key);
        if (setter == null) {
            in.warning("Field '" + name + "' is unsupported");
            return;
        }
        setter.set(target, value);
    }

    private void parseFrameLine(FrameTag tag, String name, int value) {
        State state = defs.states()[tag.index()];
        if (tag.index() == StateId.COUNT - 1) {
            frameOverflow(name, value);
        } else {
            setField(FRAME_FIELDS, state, name, value);
        }
    }

    /**
     * DOS DEHACKED allocates 966 frames but DOOM.EXE has 967; setting the last one overwrites {@code weaponinfo[0]}
     * (this is why Batman Doom cannot switch away from the fist).
     */
    private void frameOverflow(String name, int value) {
        WeaponInfo fist = defs.weapons()[0];
        if (CScan.equalsIgnoreCase(name, "Duration")) {
            fist.ammo = value;
        } else if (CScan.equalsIgnoreCase(name, "Codep frame")) {
            fist.upState = value;
        } else if (CScan.equalsIgnoreCase(name, "Next frame")) {
            fist.downState = value;
        } else if (CScan.equalsIgnoreCase(name, "Unknown 1")) {
            fist.readyState = value;
        } else if (CScan.equalsIgnoreCase(name, "Unknown 2")) {
            fist.atkState = value;
        } else {
            in.error("Unable to simulate frame overflow: field '" + name + "'");
        }
    }

    private void parsePointerLine(State state, String name, int value) {
        if (CScan.equalsIgnoreCase(name, "Codep frame")) {
            if (value < 0 || value >= StateId.COUNT) {
                in.warning("Invalid state '" + value + "'");
            } else {
                state.action = codePointers[value];
            }
        } else {
            in.warning("Unknown variable name '" + name + "'");
        }
    }

    private void parseSoundLine(SoundInfo sound, String name, int value) {
        if (CScan.equalsIgnoreCase(name, "Zero 1")) {
            // The mapped field is a pointer (sfxinfo_t.link): 8 bytes on the 64-bit oracle, which the mapping
            // code rejects.
            in.error("Unknown field type for 'Zero 1' (BUG)");
            return;
        }
        setField(SOUND_FIELDS, sound, name, value);
    }

    private void parseAmmoLine(AmmoTag tag, String name, int value) {
        if (CScan.equalsIgnoreCase(name, "Per ammo")) {
            defs.clipAmmo()[tag.index()] = value;
        } else if (CScan.equalsIgnoreCase(name, "Max ammo")) {
            defs.maxAmmo()[tag.index()] = value;
        } else {
            in.warning("Field named '" + name + "' not found");
        }
    }

    // Chocolate Doom: deh_misc.c DEH_MiscParseLine
    private void parseMiscLine(String line) {
        String[] assignment = parseAssignment(line);
        if (assignment == null) {
            in.warning("Failed to parse assignment");
            return;
        }
        int value = CScan.atoi(assignment[1]);
        MiscSettings misc = defs.misc();
        if (CScan.equalsIgnoreCase(assignment[0], "Monsters Infight")) {
            // The value is weird: 202 means off, 221 means on.
            if (value == 202) {
                misc.set(MiscSetting.SPECIES_INFIGHTING, 0);
            } else if (value == 221) {
                misc.set(MiscSetting.SPECIES_INFIGHTING, 1);
            } else {
                in.warning("Invalid value for 'Monsters Infight': " + value);
            }
            return;
        }
        for (MiscSetting s : MiscSetting.values()) {
            if (s != MiscSetting.SPECIES_INFIGHTING && CScan.equalsIgnoreCase(assignment[0], s.dehName())) {
                misc.set(s, value);
                return;
            }
        }
        in.warning("Unknown Misc variable '" + assignment[0] + "'");
    }

    // Chocolate Doom: deh_cheat.c DEH_CheatParseLine
    private void parseCheatLine(String line) {
        String[] assignment = parseAssignment(line);
        if (assignment == null) {
            in.warning("Failed to parse assignment");
            return;
        }
        CheatSequence cheat = strings.cheatByDehName(assignment[0]);
        if (cheat == null) {
            in.warning("Unknown cheat '" + assignment[0] + "'");
            return;
        }
        String value = assignment[1];
        int i = 0;
        while (i < value.length() && value.charAt(i) != 0 && value.charAt(i) != 0xff) {
            // If the cheat length exceeds the vanilla limit, stop.
            if (!allowLongCheats && i >= cheat.originalLength()) {
                in.warning("Cheat sequence longer than supported by Vanilla dehacked");
                break;
            }
            if (options.applyCheats()) {
                cheat.put(i, value.charAt(i));
            }
            i++;
            // Absolute limit
            if (i >= CheatSequence.MAX_LENGTH - cheat.parameterChars()) {
                in.error("Cheat sequence too long!");
                return;
            }
        }
        if (options.applyCheats()) {
            cheat.put(i, '\0');
        }
    }

    // Chocolate Doom: deh_bexstr.c DEH_BEXStrParseLine
    private void parseBexLine(String line) {
        String[] assignment = parseAssignment(line);
        if (assignment == null) {
            in.warning("Failed to parse assignment");
            return;
        }
        if (strings.isBexMnemonic(assignment[0])) {
            strings.replace(strings.original(assignment[0]), assignment[1]);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Field tables (DEH_MAPPING in the Chocolate Doom sources)

    private static Map<String, Setter<ThingType>> thingFields() {
        Map<String, Setter<ThingType>> m = new HashMap<>();
        m.put("id #", (t, v) -> t.doomedNum = v);
        m.put("initial frame", (t, v) -> t.spawnState = v);
        m.put("hit points", (t, v) -> t.spawnHealth = v);
        m.put("first moving frame", (t, v) -> t.seeState = v);
        m.put("alert sound", (t, v) -> t.seeSound = v);
        m.put("reaction time", (t, v) -> t.reactionTime = v);
        m.put("attack sound", (t, v) -> t.attackSound = v);
        m.put("injury frame", (t, v) -> t.painState = v);
        m.put("pain chance", (t, v) -> t.painChance = v);
        m.put("pain sound", (t, v) -> t.painSound = v);
        m.put("close attack frame", (t, v) -> t.meleeState = v);
        m.put("far attack frame", (t, v) -> t.missileState = v);
        m.put("death frame", (t, v) -> t.deathState = v);
        m.put("exploding frame", (t, v) -> t.xDeathState = v);
        m.put("death sound", (t, v) -> t.deathSound = v);
        m.put("speed", (t, v) -> t.speed = v);
        m.put("width", (t, v) -> t.radius = v);
        m.put("height", (t, v) -> t.height = v);
        m.put("mass", (t, v) -> t.mass = v);
        m.put("missile damage", (t, v) -> t.damage = v);
        m.put("action sound", (t, v) -> t.activeSound = v);
        m.put("bits", (t, v) -> t.flags = v);
        m.put("respawn frame", (t, v) -> t.raiseState = v);
        return Map.copyOf(m);
    }

    private static Map<String, Setter<State>> frameFields() {
        Map<String, Setter<State>> m = new HashMap<>();
        m.put("sprite number", (s, v) -> s.sprite = v);
        m.put("sprite subnumber", (s, v) -> s.frame = v);
        m.put("duration", (s, v) -> s.tics = v);
        m.put("next frame", (s, v) -> s.nextState = v);
        m.put("unknown 1", (s, v) -> s.misc1 = v);
        m.put("unknown 2", (s, v) -> s.misc2 = v);
        // "Codep frame" is known but unsupported in a Frame section (see the Pointer section).
        return withUnsupported(m, "codep frame");
    }

    private static Map<String, Setter<WeaponInfo>> weaponFields() {
        Map<String, Setter<WeaponInfo>> m = new HashMap<>();
        m.put("ammo type", (w, v) -> w.ammo = v);
        m.put("deselect frame", (w, v) -> w.upState = v);
        m.put("select frame", (w, v) -> w.downState = v);
        m.put("bobbing frame", (w, v) -> w.readyState = v);
        m.put("shooting frame", (w, v) -> w.atkState = v);
        m.put("firing frame", (w, v) -> w.flashState = v);
        return Map.copyOf(m);
    }

    private static Map<String, Setter<SoundInfo>> soundFields() {
        Map<String, Setter<SoundInfo>> m = new HashMap<>();
        m.put("value", (s, v) -> s.priority = v);
        m.put("zero 2", (s, v) -> s.pitch = v);
        m.put("zero 3", (s, v) -> s.volume = v);
        // Run-time bookkeeping of the sound code (usefulness, lump number): accepted, nothing to store.
        m.put("neg. one 1", (s, v) -> { });
        m.put("neg. one 2", (s, v) -> { });
        return withUnsupported(m, "offset", "zero/one", "zero 4");
    }

    private static <T> Map<String, Setter<T>> withUnsupported(Map<String, Setter<T>> m, String... names) {
        for (String n : names) {
            m.put(n, null);
        }
        // Map.copyOf rejects null values, so keep a plain unmodifiable view.
        return java.util.Collections.unmodifiableMap(m);
    }
}
