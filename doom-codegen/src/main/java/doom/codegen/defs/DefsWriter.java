package doom.codegen.defs;

import doom.codegen.defs.OriginalDefs.CheatRow;
import doom.codegen.defs.OriginalDefs.MiscRow;
import doom.codegen.defs.OriginalDefs.SoundRow;
import doom.codegen.defs.OriginalDefs.StateRow;
import doom.codegen.defs.OriginalDefs.StringEntry;
import doom.codegen.defs.OriginalDefs.ThingRow;
import doom.codegen.defs.OriginalDefs.WeaponRow;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Writes the Java sources and the test snapshot for {@code gen-defs}. */
final class DefsWriter {

    private static final String PACKAGE = "doom.game.data";
    private static final int MF_COUNTKILL = 0x400000;
    private static final int MF_MISSILE = 0x10000;
    private static final int MF_SPECIAL = 0x1;
    private static final int LINE_LIMIT = 118;

    /** Builder method names of the {@code mobjinfo_t} fields (same order as {@link OriginalDefs#THING_FIELDS}). */
    private static final List<String> THING_METHODS = List.of(
            "doomedNum", "spawnState", "spawnHealth", "seeState", "seeSound", "reactionTime", "attackSound",
            "painState", "painChance", "painSound", "meleeState", "missileState", "deathState", "xDeathState",
            "deathSound", "speed", "radius", "height", "mass", "damage", "activeSound", "flags", "raiseState");

    private static final List<String> THING_STATE_FIELDS = List.of(
            "spawnstate", "seestate", "painstate", "meleestate", "missilestate", "deathstate", "xdeathstate",
            "raisestate");

    private static final List<String> WEAPON_NAMES = List.of(
            "FIST", "PISTOL", "SHOTGUN", "CHAINGUN", "MISSILE", "PLASMA", "BFG", "CHAINSAW", "SUPERSHOTGUN");

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z_]\\w*|\\d+|[|*+\\-()]");

    /** One generated method: an actor or a weapon together with the states it owns. */
    private record Group(String method, String doc, ThingRow thing, WeaponRow weapon, List<Integer> states) {
    }

    private final OriginalDefs d;
    private final Path out;
    private final Path javaDir;

    DefsWriter(OriginalDefs defs, Path out) {
        this.d = defs;
        this.out = out;
        this.javaDir = out.resolve("main/java").resolve(PACKAGE.replace('.', '/'));
    }

    void write() throws IOException {
        constants("SpriteId", "Sprite numbers ({@code spritenum_t}); also the index into the sprite name table.",
                d.spriteEnum(), "SPR_");
        constants("StateId", "State numbers ({@code statenum_t}): the index of the state table, as in {@code info.h}.",
                d.stateEnum(), "S_");
        constants("MobjType", "Actor types ({@code mobjtype_t}): the index of the thing table, as in {@code info.h}.",
                d.mobjEnum(), "MT_");
        stateAction();
        groups();
        sounds();
        sprites();
        strings();
        cheats();
        misc();
        new SnapshotWriter(d).write(out.resolve("test/resources").resolve(PACKAGE.replace('.', '/'))
                .resolve("original-defs.txt"));
    }

    // ------------------------------------------------------------------------------------------------
    // Constant classes

    private void constants(String name, String doc, List<String> enumNames, String prefix) throws IOException {
        StringBuilder sb = header();
        sb.append("/**\n * ").append(doc).append("\n * Generated from {@code info.h}; the numbering is part of the game data and must not change.\n */\n");
        sb.append("public final class ").append(name).append(" {\n\n");
        for (int i = 0; i < enumNames.size(); i++) {
            sb.append("    public static final int ").append(constant(enumNames.get(i), prefix)).append(" = ").append(i)
                    .append(";\n");
        }
        sb.append("\n    /** Number of entries. */\n    public static final int COUNT = ").append(enumNames.size())
                .append(";\n\n    private ").append(name).append("() {\n    }\n}\n");
        write(name, sb);
    }

    private static String constant(String cName, String prefix) {
        String s = cName.startsWith(prefix) ? cName.substring(prefix.length()) : cName;
        if (!Character.isJavaIdentifierStart(s.charAt(0))) {
            throw new IllegalStateException("cannot derive a Java constant from " + cName);
        }
        return s.toUpperCase();
    }

    private void stateAction() throws IOException {
        StringBuilder sb = header();
        sb.append("/**\n * Every action function referenced by {@code states[]} in {@code info.c}, plus {@link #NONE} for {@code NULL}\n"
                + " * (ADR-022: one enum instead of method references). The constants keep the C names so that DEHACKED\n"
                + " * code pointers and the original source stay greppable; the simulation dispatches them with a {@code switch}.\n */\n");
        sb.append("public enum StateAction {\n\n    /** {@code NULL}: no action. */\n    NONE");
        for (String a : d.actions()) {
            sb.append(",\n    ").append(a);
        }
        sb.append(";\n}\n");
        write("StateAction", sb);
    }

    // ------------------------------------------------------------------------------------------------
    // Actor and weapon groups

    private void groups() throws IOException {
        int n = d.states().size();
        int[] owner = new int[n];
        Arrays.fill(owner, -1);
        // owner >= 0: actor index; owner <= -2: weapon (-2 - weapon); -1: unclaimed
        for (ThingRow t : d.things()) {
            for (String f : THING_STATE_FIELDS) {
                claim(owner, t.get(f), t.index());
            }
        }
        for (WeaponRow w : d.weapons()) {
            for (String f : List.of("upstate", "downstate", "readystate", "atkstate", "flashstate")) {
                claim(owner, w.get(f), -2 - w.index());
            }
        }
        // States that only code reaches (muzzle flash frames, A_VileAttack follow-ups ...) stay with the owner of
        // the preceding state in the enum, which keeps related states together.
        for (int i = 1; i < n; i++) {
            if (owner[i] == -1) {
                owner[i] = owner[i - 1];
            }
        }

        Map<String, List<Group>> byClass = new LinkedHashMap<>();
        for (String c : List.of("MonsterDefs", "ProjectileDefs", "PickupDefs", "ObjectDefs", "WeaponDefs",
                "MiscStateDefs")) {
            byClass.put(c, new ArrayList<>());
        }
        for (ThingRow t : d.things()) {
            int flags = t.get("flags");
            String cls = (flags & MF_COUNTKILL) != 0 ? "MonsterDefs"
                    : (flags & MF_MISSILE) != 0 ? "ProjectileDefs"
                    : (flags & MF_SPECIAL) != 0 ? "PickupDefs" : "ObjectDefs";
            byClass.get(cls).add(new Group(camel(t.name().substring(3)), t.name(), t, null, statesOf(owner, t.index())));
        }
        for (WeaponRow w : d.weapons()) {
            String name = WEAPON_NAMES.get(w.index());
            byClass.get("WeaponDefs").add(new Group(camel(name), "weapon " + name.toLowerCase(), null, w,
                    statesOf(owner, -2 - w.index())));
        }
        List<Integer> rest = statesOf(owner, -1);
        byClass.get("MiscStateDefs").add(new Group("unowned", "S_NULL, the only state without an owner", null, null, rest));

        String[] docs = {
            "Actors with {@code MF_COUNTKILL}: the monsters.",
            "Actors with {@code MF_MISSILE}: projectiles.",
            "Actors with {@code MF_SPECIAL}: items the player can pick up.",
            "The remaining actors: the player, scenery, decorations, effects, and the boss-brain machinery.",
            "Weapons ({@code weaponinfo[]}), ammunition limits and the weapon and muzzle-flash states.",
            "{@code S_NULL}, the one state that belongs to no actor or weapon."};
        int di = 0;
        for (Map.Entry<String, List<Group>> e : byClass.entrySet()) {
            writeGroupClass(e.getKey(), docs[di++], e.getValue());
        }
    }

    private void claim(int[] owner, int state, int who) {
        if (state == 0 || owner[state] != -1) {
            return;
        }
        owner[state] = who;
        claim(owner, d.states().get(state).next(), who);
    }

    private List<Integer> statesOf(int[] owner, int who) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < owner.length; i++) {
            if (owner[i] == who) {
                out.add(i);
            }
        }
        return out;
    }

    private void writeGroupClass(String cls, String doc, List<Group> groups) throws IOException {
        boolean weapons = cls.equals("WeaponDefs");
        StringBuilder sb = new StringBuilder();
        sb.append("/**\n * ").append(doc).append("\n * Each method defines one group; the global indices come from {@link StateId} and {@link MobjType},\n"
                + " * so the order of the methods is free and {@link DefsBuilder#build()} checks that nothing is missing.\n */\n");
        sb.append("final class ").append(cls).append(" {\n\n    private ").append(cls).append("() {\n    }\n\n");
        sb.append("    static void define(DefsBuilder b) {\n");
        if (weapons) {
            sb.append("        ammunition(b);\n");
        }
        for (Group g : groups) {
            sb.append("        ").append(g.method()).append("(b);\n");
        }
        sb.append("    }\n");
        if (weapons) {
            sb.append("\n    /** {@code maxammo[]} and {@code clipammo[]} of {@code p_inter.c}. */\n");
            sb.append("    private static void ammunition(DefsBuilder b) {\n");
            String[] ammo = {"CLIP", "SHELL", "CELL", "MISSILE"};
            for (int i = 0; i < ammo.length; i++) {
                sb.append("        b.ammo(AmmoType.").append(ammo[i]).append(", ").append(d.maxAmmo()[i]).append(", ")
                        .append(d.clipAmmo()[i]).append(");\n");
            }
            sb.append("    }\n");
        }
        for (Group g : groups) {
            sb.append("\n    /** ").append(g.doc()).append(". */\n");
            sb.append("    private static void ").append(g.method()).append("(DefsBuilder b) {\n");
            if (g.thing() != null) {
                thing(sb, g.thing());
            } else if (g.weapon() != null) {
                weapon(sb, g.weapon());
            }
            for (int s : g.states()) {
                state(sb, d.states().get(s));
            }
            sb.append("    }\n");
        }
        sb.append("}\n");
        String body = sb.toString();
        StringBuilder file = header();
        if (body.contains("FRACUNIT")) {
            file.append("import static doom.math.Fixed.FRACUNIT;\n\n");
        }
        for (String type : List.of("AmmoType", "FrameFlags", "MobjFlags", "SoundId", "Weapon")) {
            if (body.contains(type + ".")) {
                file.append("import doom.game.view.").append(type).append(";\n");
            }
        }
        file.append('\n').append(body);
        write(cls, file);
    }

    private void thing(StringBuilder sb, ThingRow t) {
        sb.append("        b.thing(MobjType.").append(constant(t.name(), "MT_")).append(")\n");
        List<String> lines = new ArrayList<>();
        for (int f = 0; f < OriginalDefs.THING_FIELDS.size(); f++) {
            String field = OriginalDefs.THING_FIELDS.get(f);
            boolean mandatory = field.equals("doomednum");
            if (t.values().get(f) == 0 && !mandatory) {
                continue;
            }
            lines.add("." + THING_METHODS.get(f) + "(" + javaExpr(t.expressions().get(f)) + ")");
        }
        for (int i = 0; i < lines.size(); i++) {
            sb.append("                ").append(lines.get(i)).append(i == lines.size() - 1 ? ";\n" : "\n");
        }
    }

    private void weapon(StringBuilder sb, WeaponRow w) {
        String name = WEAPON_NAMES.get(w.index());
        List<String> args = new ArrayList<>();
        args.add("Weapon." + name);
        for (String e : w.expressions()) {
            args.add(javaExpr(e));
        }
        appendCall(sb, "b.weapon(", args);
    }

    private void state(StringBuilder sb, StateRow s) {
        List<String> args = new ArrayList<>();
        args.add("StateId." + constant(s.name(), "S_"));
        args.add("SpriteId." + constant(d.spriteEnum().get(s.sprite()), "SPR_"));
        args.add(s.frame() >= 0x8000 ? "FrameFlags.FULLBRIGHT | " + (s.frame() & 0x7fff) : Integer.toString(s.frame()));
        args.add(Integer.toString(s.tics()));
        args.add("StateAction." + (s.action() == null ? "NONE" : s.action()));
        args.add("StateId." + constant(d.stateEnum().get(s.next()), "S_"));
        if (s.misc1() != 0 || s.misc2() != 0) {
            args.add(Integer.toString(s.misc1()));
            args.add(Integer.toString(s.misc2()));
        }
        appendCall(sb, "b.state(", args);
    }

    /** Appends {@code prefix a, b, c);} at indent 8, wrapping lines longer than the limit. */
    private static void appendCall(StringBuilder sb, String prefix, List<String> args) {
        StringBuilder line = new StringBuilder("        ").append(prefix);
        for (int i = 0; i < args.size(); i++) {
            String piece = args.get(i) + (i == args.size() - 1 ? ");" : ", ");
            if (line.length() + piece.length() > LINE_LIMIT && i > 0) {
                sb.append(line.toString().stripTrailing()).append('\n');
                line = new StringBuilder("                ");
            }
            line.append(piece);
        }
        sb.append(line).append('\n');
    }

    /** Converts a C initializer expression (tokens without spaces) into the Java expression of the DSL. */
    private String javaExpr(String c) {
        StringBuilder sb = new StringBuilder();
        Matcher m = TOKEN.matcher(c);
        int end = 0;
        String prev = "";
        while (m.find()) {
            if (m.start() != end) {
                throw new IllegalStateException("unexpected characters in expression " + c);
            }
            end = m.end();
            String t = m.group();
            switch (t) {
                case "|", "*", "+" -> sb.append(' ').append(t).append(' ');
                case "-" -> {
                    boolean unary = prev.isEmpty() || prev.equals("(") || "|*+-".contains(prev);
                    sb.append(unary ? "-" : " - ");
                }
                case "(", ")" -> sb.append(t);
                default -> sb.append(identifier(t));
            }
            prev = t;
        }
        if (end != c.length()) {
            throw new IllegalStateException("unexpected characters in expression " + c);
        }
        return sb.toString();
    }

    private String identifier(String t) {
        if (Character.isDigit(t.charAt(0)) || t.equals("FRACUNIT")) {
            return t;
        }
        if (t.startsWith("S_")) {
            return "StateId." + constant(t, "S_");
        }
        if (t.startsWith("sfx_")) {
            return "SoundId." + constant(t, "sfx_");
        }
        if (t.startsWith("MF_")) {
            return "MobjFlags." + t.substring(3);
        }
        if (t.startsWith("am_")) {
            return "AmmoType." + switch (t) {
                case "am_clip" -> "CLIP";
                case "am_shell" -> "SHELL";
                case "am_cell" -> "CELL";
                case "am_misl" -> "MISSILE";
                case "am_noammo" -> "NO_AMMO";
                default -> throw new IllegalStateException("ammo " + t);
            };
        }
        throw new IllegalStateException("unknown identifier in definitions: " + t);
    }

    private static String camel(String upper) {
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (char ch : upper.toLowerCase().toCharArray()) {
            if (ch == '_') {
                up = true;
            } else {
                sb.append(up ? Character.toUpperCase(ch) : ch);
                up = false;
            }
        }
        String s = sb.toString();
        return switch (s) {
            case "null", "new", "default", "switch", "case" -> s + "Actor";
            default -> s;
        };
    }

    // ------------------------------------------------------------------------------------------------
    // Sounds, sprites

    private void sounds() throws IOException {
        StringBuilder sb = header();
        sb.append("import static doom.game.data.DefsBuilder.NO_LINK;\n\n");
        sb.append("import doom.game.view.MusicId;\nimport doom.game.view.SoundId;\n\n");
        sb.append("/** Sound effects ({@code S_sfx[]}) and music ({@code S_music[]}) of {@code sounds.c}. */\n");
        sb.append("final class SoundDefs {\n\n    private SoundDefs() {\n    }\n\n");
        sb.append("    static void define(DefsBuilder b) {\n        effects(b);\n        music(b);\n    }\n\n");
        sb.append("    /** Name without the DS prefix, singularity, priority, linked sound, pitch and volume of the link. */\n");
        sb.append("    private static void effects(DefsBuilder b) {\n");
        for (SoundRow s : d.sounds()) {
            List<String> args = new ArrayList<>();
            args.add("SoundId." + constant(d.sfxEnum().get(s.index()), "sfx_"));
            args.add(quote(s.name()));
            args.add(Boolean.toString(s.singularity()));
            args.add(Integer.toString(s.priority()));
            args.add(s.link() < 0 ? "NO_LINK" : "SoundId." + constant(d.sfxEnum().get(s.link()), "sfx_"));
            args.add(Integer.toString(s.pitch()));
            args.add(Integer.toString(s.volume()));
            appendCall(sb, "b.sound(", args);
        }
        sb.append("    }\n\n    /** Music names without the D_ prefix; entry 0 has none. */\n");
        sb.append("    private static void music(DefsBuilder b) {\n");
        for (int i = 0; i < d.musicNames().size(); i++) {
            String name = d.musicNames().get(i);
            sb.append("        b.music(MusicId.").append(constant(d.musicEnum().get(i), "mus_")).append(", ")
                    .append(name == null ? "null" : quote(name)).append(");\n");
        }
        sb.append("    }\n}\n");
        write("SoundDefs", sb);
    }

    private void sprites() throws IOException {
        StringBuilder sb = header();
        sb.append("/** Sprite names ({@code sprnames[]}) of {@code info.c}. */\n");
        sb.append("final class SpriteDefs {\n\n    private SpriteDefs() {\n    }\n\n");
        sb.append("    static void define(DefsBuilder b) {\n");
        for (int i = 0; i < d.spriteNames().size(); i++) {
            sb.append("        b.sprite(SpriteId.").append(constant(d.spriteEnum().get(i), "SPR_")).append(", ")
                    .append(quote(d.spriteNames().get(i))).append(");\n");
        }
        sb.append("    }\n}\n");
        write("SpriteDefs", sb);
    }

    // ------------------------------------------------------------------------------------------------
    // Strings, cheats, misc

    private void strings() throws IOException {
        StringBuilder sb = header();
        sb.append("/**\n * Every text string of {@code d_englsh.h} and {@code dstrings.c} (Chocolate Doom 3.1.1), keyed by its BEX\n"
                + " * mnemonic when it has one. Entries flagged {@code bex} can be replaced by a {@code [STRINGS]} section.\n */\n");
        sb.append("final class OriginalStrings {\n\n    private OriginalStrings() {\n    }\n\n");
        sb.append("    static void define(GameStrings s) {\n");
        String origin = "";
        for (StringEntry e : d.strings()) {
            if (!e.origin().equals(origin)) {
                origin = e.origin();
                sb.append("        // ").append(origin).append('\n');
            }
            String call = "        s.define(" + quote(e.key()) + ", " + quote(e.text()) + ", " + e.bex() + ");";
            if (call.length() > LINE_LIMIT + 40) {
                call = "        s.define(" + quote(e.key()) + ",\n                " + quote(e.text()) + ", " + e.bex() + ");";
            }
            sb.append(call).append('\n');
        }
        sb.append("    }\n}\n");
        write("OriginalStrings", sb);
    }

    private void cheats() throws IOException {
        StringBuilder sb = header();
        sb.append("/**\n * The cheat sequences DEHACKED can replace, in the order of Chocolate Doom's {@code deh_cheat.c}, with their\n"
                + " * vanilla sequence and the number of parameter characters typed after it ({@code st_stuff.c}, {@code am_map.c}).\n */\n");
        sb.append("public enum CheatId {\n\n");
        for (int i = 0; i < d.cheats().size(); i++) {
            CheatRow c = d.cheats().get(i);
            sb.append("    /** {@code ").append(c.variable()).append("}. */\n");
            sb.append("    ").append(cheatConstant(c.variable())).append("(").append(quote(c.dehName())).append(", ")
                    .append(quote(c.sequence())).append(", ").append(c.parameterChars()).append(")")
                    .append(i == d.cheats().size() - 1 ? ";\n" : ",\n");
        }
        sb.append("\n    private final String dehName;\n    private final String sequence;\n    private final int parameterChars;\n\n");
        sb.append("    CheatId(String dehName, String sequence, int parameterChars) {\n        this.dehName = dehName;\n"
                + "        this.sequence = sequence;\n        this.parameterChars = parameterChars;\n    }\n\n");
        sb.append("    /** Name in the DEHACKED {@code Cheat} section. */\n    public String dehName() {\n        return dehName;\n    }\n\n");
        sb.append("    /** Vanilla sequence, without parameters. */\n    public String vanillaSequence() {\n        return sequence;\n    }\n\n");
        sb.append("    /** Characters typed after the sequence (the parameter, e.g. the music number of {@code idmus}). */\n"
                + "    public int parameterChars() {\n        return parameterChars;\n    }\n}\n");
        write("CheatId", sb);
    }

    private static String cheatConstant(String variable) {
        String s = variable.startsWith("cheat_") ? variable.substring(6) : variable;
        s = s.replace("[", "_").replace("]", "");
        return s.toUpperCase();
    }

    private void misc() throws IOException {
        StringBuilder sb = header();
        sb.append("/**\n * The DEHACKED {@code Misc} values ({@code deh_misc.c}) with their vanilla defaults ({@code deh_misc.h}).\n"
                + " * {@code Monsters Infight} is stored as 0 (off) or 1 (on); in a patch it is written 202 or 221.\n */\n");
        sb.append("public enum MiscSetting {\n\n");
        for (int i = 0; i < d.misc().size(); i++) {
            MiscRow m = d.misc().get(i);
            sb.append("    ").append(m.variable().substring(4).toUpperCase()).append("(").append(quote(m.dehName()))
                    .append(", ").append(m.defaultValue()).append(")").append(i == d.misc().size() - 1 ? ";\n" : ",\n");
        }
        sb.append("\n    private final String dehName;\n    private final int defaultValue;\n\n");
        sb.append("    MiscSetting(String dehName, int defaultValue) {\n        this.dehName = dehName;\n"
                + "        this.defaultValue = defaultValue;\n    }\n\n");
        sb.append("    /** Name in the DEHACKED {@code Misc} section. */\n    public String dehName() {\n        return dehName;\n    }\n\n");
        sb.append("    /** The vanilla value. */\n    public int defaultValue() {\n        return defaultValue;\n    }\n}\n");
        write("MiscSetting", sb);
    }

    // ------------------------------------------------------------------------------------------------
    // Helpers

    private static StringBuilder header() {
        return new StringBuilder("// Generated by `doom-codegen gen-defs` from the original sources; hand-edited since (ADR-014).\n"
                + "package " + PACKAGE + ";\n\n");
    }

    private void write(String className, StringBuilder sb) throws IOException {
        Files.createDirectories(javaDir);
        Files.writeString(javaDir.resolve(className + ".java"), sb.toString(), StandardCharsets.UTF_8);
    }

    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                case '\r' -> sb.append("\\r");
                default -> {
                    if (c < 32 || c > 126) {
                        sb.append(String.format("\\%03o", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
