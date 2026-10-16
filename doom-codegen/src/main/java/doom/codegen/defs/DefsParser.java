package doom.codegen.defs;

import doom.codegen.defs.CSource.Define;
import doom.codegen.defs.CSource.Enumerator;
import doom.codegen.defs.CSource.Group;
import doom.codegen.defs.CSource.Init;
import doom.codegen.defs.CSource.Kind;
import doom.codegen.defs.CSource.Leaf;
import doom.codegen.defs.CSource.Token;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the original definition tables.
 *
 * <ul>
 *   <li>From linuxdoom-1.10: {@code info.h}, {@code info.c} (sprites, states, actions, mobjinfo), {@code p_mobj.h}
 *       (flag values), {@code doomdef.h} (ammo types), {@code d_items.c} (weaponinfo), {@code p_inter.c}
 *       (maxammo, clipammo), {@code sounds.h} and {@code sounds.c} (sfx and music).</li>
 *   <li>From Chocolate Doom 3.1.1 ({@code src/doom}), the DOS v1.9 reference for text and DEHACKED:
 *       {@code d_englsh.h} (it differs from linuxdoom only in {@code DOSY}), {@code dstrings.h}, {@code dstrings.c}
 *       (quit messages), {@code deh_bexstr.c} (BEX mnemonics), {@code st_stuff.c} and {@code am_map.c}
 *       (cheat sequences), {@code deh_cheat.c} (cheat names), {@code deh_misc.h} and {@code deh_misc.c}
 *       (Misc values).</li>
 * </ul>
 * Chocolate's {@code info.c} and {@code d_items.c} are parsed too and must match linuxdoom exactly.
 */
final class DefsParser {

    private static final int FRACUNIT = 1 << 16;

    private DefsParser() {
    }

    /** Resolves Chocolate Doom's {@code src/doom} from the repository root or from {@code src/doom} itself. */
    static Path chocolateDoomDir(Path dir) {
        if (Files.isRegularFile(dir.resolve("src/doom/d_englsh.h"))) {
            return dir.resolve("src/doom");
        }
        if (Files.isRegularFile(dir.resolve("d_englsh.h")) && Files.isRegularFile(dir.resolve("deh_bexstr.c"))) {
            return dir;
        }
        throw new IllegalArgumentException("not a Chocolate Doom source tree (no src/doom/d_englsh.h): " + dir);
    }

    static OriginalDefs parse(Path linuxDoom, Path chocolateRoot) throws IOException {
        Path choco = chocolateDoomDir(chocolateRoot);
        Map<String, Integer> constants = new HashMap<>();
        constants.put("FRACUNIT", FRACUNIT);
        constants.put("FRACBITS", 16);
        constants.put("NULL", 0);
        constants.put("false", 0);
        constants.put("true", 1);

        CSource infoH = CSource.read(linuxDoom.resolve("info.h"));
        List<String> spriteEnum = names(enumWithoutCount(infoH, "spritenum_t", "NUMSPRITES", constants));
        List<String> stateEnum = names(enumWithoutCount(infoH, "statenum_t", "NUMSTATES", constants));
        List<String> mobjEnum = names(enumWithoutCount(infoH, "mobjtype_t", "NUMMOBJTYPES", constants));

        CSource mobjH = CSource.read(linuxDoom.resolve("p_mobj.h"));
        for (Enumerator e : mobjH.enumeration("mobjflag_t", constants)) {
            constants.put(e.name(), e.value());
        }
        CSource doomdef = CSource.read(linuxDoom.resolve("doomdef.h"));
        List<Enumerator> ammo = doomdef.enumeration("ammotype_t", constants);
        ammo.forEach(e -> constants.put(e.name(), e.value()));
        List<Enumerator> weaponTypes = doomdef.enumeration("weapontype_t", constants);
        weaponTypes.forEach(e -> constants.put(e.name(), e.value()));

        CSource soundsH = CSource.read(linuxDoom.resolve("sounds.h"));
        List<String> sfxEnum = names(enumWithoutCount(soundsH, "sfxenum_t", "NUMSFX", constants));
        List<String> musicEnum = names(enumWithoutCount(soundsH, "musicenum_t", "NUMMUSIC", constants));

        Path infoCPath = linuxDoom.resolve("info.c");
        CSource infoC = CSource.read(infoCPath);
        List<String> spriteNames = strings(infoC, infoC.initializer("char\\s*\\*\\s*sprnames\\s*\\[[^\\]]*\\]"));
        check(spriteNames.size() == spriteEnum.size(), "sprnames has " + spriteNames.size() + " entries");
        for (int i = 0; i < spriteNames.size(); i++) {
            check(spriteEnum.get(i).equals("SPR_" + spriteNames.get(i)), "sprite " + i + " name mismatch");
        }

        List<String> actions = new ArrayList<>();
        Matcher am = Pattern.compile("void\\s+(A_\\w+)\\s*\\(\\s*\\)\\s*;").matcher(infoC.text());
        while (am.find()) {
            actions.add(am.group(1));
        }
        check(new HashSet<>(actions).size() == actions.size(), "duplicate action declarations");

        List<StateRow> states = states(infoC, stateEnum, constants);
        checkStateComments(infoCPath, stateEnum);
        for (StateRow s : states) {
            check(s.action() == null || actions.contains(s.action()), "undeclared action " + s.action());
        }
        List<ThingRow> things = things(infoC, mobjEnum, constants);

        CSource items = CSource.read(linuxDoom.resolve("d_items.c"));
        List<WeaponRow> weapons = weapons(items, constants);
        int numWeapons = constants.get("NUMWEAPONS");
        check(weapons.size() == numWeapons, "weaponinfo has " + weapons.size() + " entries");

        CSource inter = CSource.read(linuxDoom.resolve("p_inter.c"));
        int[] maxAmmo = ints(inter, inter.initializer("int\\s+maxammo\\s*\\[[^\\]]*\\]"), constants);
        int[] clipAmmo = ints(inter, inter.initializer("int\\s+clipammo\\s*\\[[^\\]]*\\]"), constants);
        int numAmmo = constants.get("NUMAMMO");
        check(maxAmmo.length == numAmmo && clipAmmo.length == numAmmo, "ammo tables");

        CSource soundsC = CSource.read(linuxDoom.resolve("sounds.c"));
        List<SoundRow> sounds = sounds(soundsC, sfxEnum, constants);
        List<String> musicNames = music(soundsC, musicEnum);

        verifyAgainstChocolate(choco, states, things, weapons, constants, stateEnum, mobjEnum);

        List<StringEntry> strings = strings(choco);
        List<CheatRow> cheats = cheats(choco);
        List<MiscRow> misc = misc(choco);

        return new OriginalDefs(spriteEnum, spriteNames, stateEnum, states, List.copyOf(actions), mobjEnum, things,
                names(ammo), weapons, maxAmmo, clipAmmo, sfxEnum, sounds, musicEnum, musicNames, strings, cheats, misc);
    }

    // ------------------------------------------------------------------------------------------------
    // info.h / info.c

    private static List<Enumerator> enumWithoutCount(CSource src, String type, String count,
            Map<String, Integer> constants) {
        List<Enumerator> all = new ArrayList<>(src.enumeration(type, constants));
        Enumerator last = all.removeLast();
        if (!last.name().equals(count) || last.value() != all.size()) {
            throw src.error(type + " does not end with " + count);
        }
        for (Enumerator e : all) {
            constants.put(e.name(), e.value());
        }
        constants.put(count, last.value());
        return all;
    }

    private static List<String> names(List<Enumerator> list) {
        return list.stream().map(Enumerator::name).toList();
    }

    private static List<StateRow> states(CSource src, List<String> stateEnum, Map<String, Integer> constants) {
        Group table = src.initializer("state_t\\s+states\\s*\\[[^\\]]*\\]");
        check(table.items().size() == stateEnum.size(), "states has " + table.items().size() + " entries");
        List<StateRow> out = new ArrayList<>();
        for (int i = 0; i < table.items().size(); i++) {
            if (!(table.items().get(i) instanceof Group g) || g.items().size() != 7) {
                throw src.error("state " + i + " is not a 7-field initializer");
            }
            int sprite = src.evaluate(leaf(src, g, 0).tokens(), constants);
            int frame = src.evaluate(leaf(src, g, 1).tokens(), constants);
            int tics = src.evaluate(leaf(src, g, 2).tokens(), constants);
            if (!(g.items().get(3) instanceof Group actionGroup) || actionGroup.items().size() != 1) {
                throw src.error("state " + i + ": action is not {name}");
            }
            String action = ((Leaf) actionGroup.items().getFirst()).text();
            int next = src.evaluate(leaf(src, g, 4).tokens(), constants);
            int misc1 = src.evaluate(leaf(src, g, 5).tokens(), constants);
            int misc2 = src.evaluate(leaf(src, g, 6).tokens(), constants);
            out.add(new StateRow(i, stateEnum.get(i), sprite, frame, tics, action.equals("NULL") ? null : action,
                    next, misc1, misc2));
        }
        return List.copyOf(out);
    }

    /** Cross-checks the {@code // S_NAME} comment that ends every line of {@code states[]}. */
    private static void checkStateComments(Path infoC, List<String> stateEnum) throws IOException {
        Pattern p = Pattern.compile("^\\s*\\{SPR_\\w+,.*\\}\\s*,?\\s*//\\s*(S_\\w+)\\s*$");
        int i = 0;
        for (String line : Files.readAllLines(infoC, StandardCharsets.ISO_8859_1)) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
                check(i < stateEnum.size() && stateEnum.get(i).equals(m.group(1)),
                        "info.c state comment " + m.group(1) + " at index " + i);
                i++;
            }
        }
        check(i == stateEnum.size(), "found " + i + " commented states");
    }

    private static List<ThingRow> things(CSource src, List<String> mobjEnum, Map<String, Integer> constants) {
        Group table = src.initializer("mobjinfo_t\\s+mobjinfo\\s*\\[[^\\]]*\\]");
        check(table.items().size() == mobjEnum.size(), "mobjinfo has " + table.items().size() + " entries");
        int fields = OriginalDefs.THING_FIELDS.size();
        List<ThingRow> out = new ArrayList<>();
        for (int i = 0; i < table.items().size(); i++) {
            if (!(table.items().get(i) instanceof Group g) || g.items().size() != fields) {
                throw src.error("mobjinfo " + i + " is not a " + fields + "-field initializer");
            }
            List<Integer> values = new ArrayList<>();
            List<String> exprs = new ArrayList<>();
            for (int f = 0; f < fields; f++) {
                Leaf l = leaf(src, g, f);
                values.add(src.evaluate(l.tokens(), constants));
                exprs.add(l.text());
            }
            out.add(new ThingRow(i, mobjEnum.get(i), List.copyOf(values), List.copyOf(exprs)));
        }
        return List.copyOf(out);
    }

    private static List<WeaponRow> weapons(CSource src, Map<String, Integer> constants) {
        Group table = src.initializer("weaponinfo_t\\s+weaponinfo\\s*\\[[^\\]]*\\]");
        int fields = OriginalDefs.WEAPON_FIELDS.size();
        List<WeaponRow> out = new ArrayList<>();
        for (int i = 0; i < table.items().size(); i++) {
            if (!(table.items().get(i) instanceof Group g) || g.items().size() != fields) {
                throw src.error("weaponinfo " + i + " is not a " + fields + "-field initializer");
            }
            List<Integer> values = new ArrayList<>();
            List<String> exprs = new ArrayList<>();
            for (int f = 0; f < fields; f++) {
                Leaf l = leaf(src, g, f);
                values.add(src.evaluate(l.tokens(), constants));
                exprs.add(l.text());
            }
            out.add(new WeaponRow(i, List.copyOf(values), List.copyOf(exprs)));
        }
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------------------------------------
    // sounds.c

    private static List<SoundRow> sounds(CSource src, List<String> sfxEnum, Map<String, Integer> constants) {
        Group table = src.initializer("sfxinfo_t\\s+S_sfx\\s*\\[\\s*\\]");
        check(table.items().size() == sfxEnum.size(), "S_sfx has " + table.items().size() + " entries");
        List<SoundRow> out = new ArrayList<>();
        for (int i = 0; i < table.items().size(); i++) {
            if (!(table.items().get(i) instanceof Group g) || g.items().size() != 7) {
                throw src.error("S_sfx " + i + " is not a 7-field initializer");
            }
            String name = CSource.decodeLiteral(single(src, leaf(src, g, 0), Kind.STRING).text());
            check(sfxEnum.get(i).equalsIgnoreCase("sfx_" + name), "sfx " + i + " name " + name);
            boolean singularity = src.evaluate(leaf(src, g, 1).tokens(), constants) != 0;
            int priority = src.evaluate(leaf(src, g, 2).tokens(), constants);
            List<Token> linkTokens = leaf(src, g, 3).tokens();
            int link;
            if (linkTokens.size() == 1 && linkTokens.getFirst().is("0")) {
                link = -1;
            } else if (linkTokens.size() == 5 && linkTokens.get(0).is("&") && linkTokens.get(1).is("S_sfx")
                    && linkTokens.get(2).is("[") && linkTokens.get(4).is("]")) {
                link = src.evaluate(List.of(linkTokens.get(3)), constants);
            } else {
                throw src.error("unexpected sfx link " + linkTokens);
            }
            int pitch = src.evaluate(leaf(src, g, 4).tokens(), constants);
            int volume = src.evaluate(leaf(src, g, 5).tokens(), constants);
            check(src.evaluate(leaf(src, g, 6).tokens(), constants) == 0, "sfx data must be 0");
            out.add(new SoundRow(i, name, singularity, priority, link, pitch, volume));
        }
        return List.copyOf(out);
    }

    private static List<String> music(CSource src, List<String> musicEnum) {
        Group table = src.initializer("musicinfo_t\\s+S_music\\s*\\[\\s*\\]");
        check(table.items().size() == musicEnum.size(), "S_music has " + table.items().size() + " entries");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < table.items().size(); i++) {
            if (!(table.items().get(i) instanceof Group g)) {
                throw src.error("S_music " + i + " is not an initializer");
            }
            Leaf first = leaf(src, g, 0);
            if (first.tokens().size() == 1 && first.tokens().getFirst().is("0")) {
                check(i == 0, "only mus_None has no name");
                out.add(null);
            } else {
                String name = CSource.decodeLiteral(single(src, first, Kind.STRING).text());
                check(musicEnum.get(i).equals("mus_" + name), "music " + i + " name " + name);
                out.add(name);
            }
        }
        return java.util.Collections.unmodifiableList(out);
    }

    // ------------------------------------------------------------------------------------------------
    // Chocolate Doom cross-check

    private static void verifyAgainstChocolate(Path choco, List<StateRow> states, List<ThingRow> things,
            List<WeaponRow> weapons, Map<String, Integer> constants, List<String> stateEnum, List<String> mobjEnum)
            throws IOException {
        CSource infoC = CSource.read(choco.resolve("info.c"));
        List<StateRow> cStates = states(infoC, stateEnum, constants);
        check(cStates.equals(states), "Chocolate Doom states[] differs from linuxdoom");
        List<ThingRow> cThings = things(infoC, mobjEnum, constants);
        for (int i = 0; i < things.size(); i++) {
            check(cThings.get(i).values().equals(things.get(i).values()),
                    "Chocolate Doom mobjinfo[" + i + "] differs from linuxdoom");
        }
        List<WeaponRow> cWeapons = weapons(CSource.read(choco.resolve("d_items.c")), constants);
        for (int i = 0; i < weapons.size(); i++) {
            check(cWeapons.get(i).values().equals(weapons.get(i).values()),
                    "Chocolate Doom weaponinfo[" + i + "] differs from linuxdoom");
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Strings (Chocolate Doom)

    private static List<StringEntry> strings(Path choco) throws IOException {
        CSource englsh = CSource.read(choco.resolve("d_englsh.h"));
        Map<String, String> macros = new LinkedHashMap<>();
        for (Define d : englsh.defines()) {
            String text = stringMacro(englsh, d, macros);
            if (text != null) {
                macros.put(d.name(), text);
            }
        }
        CSource dstringsH = CSource.read(choco.resolve("dstrings.h"));
        Map<String, String> extra = new LinkedHashMap<>();
        for (Define d : dstringsH.defines()) {
            String text = stringMacro(dstringsH, d, macros);
            if (text != null) {
                extra.put(d.name(), text);
            }
        }

        CSource bexC = CSource.read(choco.resolve("deh_bexstr.c"));
        Group table = bexC.initializer("bex_string_t\\s+bex_stringtable\\s*\\[\\s*\\]");
        Map<String, String> bexText = new LinkedHashMap<>();
        Map<String, String> bexOrigin = new LinkedHashMap<>();
        for (Init item : table.items()) {
            if (!(item instanceof Group g) || g.items().size() != 2) {
                throw bexC.error("bad bex_stringtable entry " + item);
            }
            String mnemonic = CSource.decodeLiteral(single(bexC, leaf(bexC, g, 0), Kind.STRING).text());
            Token value = ((Leaf) g.items().get(1)).tokens().getFirst();
            String text;
            String origin;
            if (value.kind() == Kind.STRING) {
                text = CSource.decodeLiteral(value.text());
                origin = "deh_bexstr.c";
            } else if (macros.containsKey(value.text())) {
                text = macros.get(value.text());
                origin = "d_englsh.h";
                check(value.text().equals(mnemonic), "BEX mnemonic " + mnemonic + " names macro " + value);
            } else if (extra.containsKey(value.text())) {
                text = extra.get(value.text());
                origin = "dstrings.h";
                check(value.text().equals(mnemonic), "BEX mnemonic " + mnemonic + " names macro " + value);
            } else {
                throw bexC.error("unknown macro in bex_stringtable: " + value);
            }
            check(bexText.put(mnemonic, text) == null, "duplicate BEX mnemonic " + mnemonic);
            bexOrigin.put(mnemonic, origin);
        }

        List<StringEntry> out = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Map.Entry<String, String> e : macros.entrySet()) {
            out.add(new StringEntry(e.getKey(), e.getValue(), bexText.containsKey(e.getKey()), "d_englsh.h"));
            keys.add(e.getKey());
        }
        for (Map.Entry<String, String> e : bexText.entrySet()) {
            if (keys.add(e.getKey())) {
                out.add(new StringEntry(e.getKey(), e.getValue(), true, bexOrigin.get(e.getKey())));
            }
        }
        CSource dstringsC = CSource.read(choco.resolve("dstrings.c"));
        for (String list : List.of("doom1", "doom2")) {
            List<String> msgs = strings(dstringsC,
                    dstringsC.initializer("const\\s+char\\s*\\*\\s*" + list + "_endmsg\\s*\\[\\s*\\]"));
            for (int i = 0; i < msgs.size(); i++) {
                String key = "ENDMSG_" + list.toUpperCase() + "_" + i;
                check(keys.add(key), "duplicate key " + key);
                out.add(new StringEntry(key, msgs.get(i), false, "dstrings.c " + list + "_endmsg[" + i + "]"));
            }
        }
        return List.copyOf(out);
    }

    /** Evaluates a string macro (literals and references to earlier string macros); null for other macros. */
    private static String stringMacro(CSource src, Define d, Map<String, String> macros) {
        if (d.body().isEmpty()) {
            return null;
        }
        if (d.body().size() == 1 && d.body().getFirst().kind() == Kind.CHAR) {
            return null;
        }
        boolean anyString = false;
        StringBuilder sb = new StringBuilder();
        for (Token t : d.body()) {
            if (t.kind() == Kind.STRING) {
                sb.append(CSource.decodeLiteral(t.text()));
                anyString = true;
            } else if (t.kind() == Kind.IDENT && macros.containsKey(t.text())) {
                sb.append(macros.get(t.text()));
                anyString = true;
            } else {
                return null;
            }
        }
        if (!anyString) {
            throw src.error("macro " + d.name() + " is not a string");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------------------------------------
    // Cheats and Misc (Chocolate Doom)

    private static List<CheatRow> cheats(Path choco) throws IOException {
        Map<String, String[]> seqs = new HashMap<>();
        Pattern single = Pattern.compile(
                "cheatseq_t\\s+(\\w+)\\s*=\\s*CHEAT\\s*\\(\\s*(\"(?:[^\"\\\\]|\\\\.)*\")\\s*,\\s*(\\d+)\\s*\\)\\s*;");
        Pattern call = Pattern.compile("CHEAT\\s*\\(\\s*(\"(?:[^\"\\\\]|\\\\.)*\")\\s*,\\s*(\\d+)\\s*\\)");
        for (String file : List.of("st_stuff.c", "am_map.c")) {
            CSource src = CSource.read(choco.resolve(file));
            Matcher m = single.matcher(src.text());
            while (m.find()) {
                seqs.put(m.group(1), new String[] {CSource.decodeLiteral(m.group(2)), m.group(3)});
            }
            Matcher arr = Pattern.compile("cheatseq_t\\s+(\\w+)\\s*\\[\\s*\\d*\\s*\\]\\s*=\\s*\\{([^;]*)\\}\\s*;")
                    .matcher(src.text());
            while (arr.find()) {
                Matcher c = call.matcher(arr.group(2));
                int i = 0;
                while (c.find()) {
                    seqs.put(arr.group(1) + "[" + i++ + "]",
                            new String[] {CSource.decodeLiteral(c.group(1)), c.group(2)});
                }
            }
        }
        CSource dehCheat = CSource.read(choco.resolve("deh_cheat.c"));
        Group table = dehCheat.initializer("deh_cheat_t\\s+allcheats\\s*\\[\\s*\\]");
        List<CheatRow> out = new ArrayList<>();
        for (Init item : table.items()) {
            if (!(item instanceof Group g) || g.items().size() != 2) {
                throw dehCheat.error("bad allcheats entry " + item);
            }
            String name = CSource.decodeLiteral(single(dehCheat, leaf(dehCheat, g, 0), Kind.STRING).text());
            String variable = leaf(dehCheat, g, 1).text();
            check(variable.startsWith("&"), "cheat variable " + variable);
            variable = variable.substring(1);
            String[] seq = seqs.get(variable);
            check(seq != null, "cheat sequence not found: " + variable);
            out.add(new CheatRow(out.size(), name, variable, seq[0], Integer.parseInt(seq[1])));
        }
        return List.copyOf(out);
    }

    private static List<MiscRow> misc(Path choco) throws IOException {
        CSource header = CSource.read(choco.resolve("deh_misc.h"));
        Map<String, Integer> defaults = new HashMap<>();
        for (Define d : header.defines()) {
            if (d.name().startsWith("DEH_DEFAULT_")) {
                defaults.put(d.name(), header.evaluate(d.body(), Map.of()));
            }
        }
        CSource src = CSource.read(choco.resolve("deh_misc.c"));
        Group table = src.initializer("misc_settings\\s*\\[\\s*\\]");
        List<MiscRow> out = new ArrayList<>();
        for (Init item : table.items()) {
            if (!(item instanceof Group g) || g.items().size() != 2) {
                throw src.error("bad misc_settings entry " + item);
            }
            String name = CSource.decodeLiteral(single(src, leaf(src, g, 0), Kind.STRING).text());
            String variable = leaf(src, g, 1).text().substring(1);
            out.add(new MiscRow(name, variable, defaultFor(defaults, variable)));
        }
        // Handled separately in DEH_MiscParseLine (202 = off, 221 = on).
        check(src.text().contains("\"Monsters Infight\""), "Monsters Infight not found");
        out.add(new MiscRow("Monsters Infight", "deh_species_infighting",
                defaultFor(defaults, "deh_species_infighting")));
        return List.copyOf(out);
    }

    private static int defaultFor(Map<String, Integer> defaults, String variable) {
        check(variable.startsWith("deh_"), "misc variable " + variable);
        Integer v = defaults.get("DEH_DEFAULT_" + variable.substring(4).toUpperCase());
        check(v != null, "no default for " + variable);
        return v;
    }

    // ------------------------------------------------------------------------------------------------
    // Helpers

    private static Leaf leaf(CSource src, Group g, int i) {
        if (i >= g.items().size() || !(g.items().get(i) instanceof Leaf l)) {
            throw src.error("expected an expression at position " + i + " of " + g);
        }
        return l;
    }

    private static Token single(CSource src, Leaf l, Kind kind) {
        if (l.tokens().size() != 1 || l.tokens().getFirst().kind() != kind) {
            throw src.error("expected a single " + kind + " but found " + l.tokens());
        }
        return l.tokens().getFirst();
    }

    private static List<String> strings(CSource src, Group g) {
        List<String> out = new ArrayList<>();
        for (Init item : g.items()) {
            if (!(item instanceof Leaf l)) {
                throw src.error("expected a string list");
            }
            if (l.tokens().size() == 1 && l.tokens().getFirst().is("NULL")) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            for (Token t : l.tokens()) {
                if (t.kind() != Kind.STRING) {
                    throw src.error("expected string literals but found " + t);
                }
                sb.append(CSource.decodeLiteral(t.text()));
            }
            out.add(sb.toString());
        }
        return List.copyOf(out);
    }

    private static int[] ints(CSource src, Group g, Map<String, Integer> constants) {
        int[] out = new int[g.items().size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = src.evaluate(leaf(src, g, i).tokens(), constants);
        }
        return out;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
