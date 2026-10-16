package doom.game.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The text strings and cheat sequences of one game session.
 *
 * <p>Strings follow Chocolate Doom's {@code deh_str.c}: the game text is identified by its BEX mnemonic
 * (e.g. {@code "HUSTR_E1M1"}, {@code "QUITMSG"}), and a DEHACKED {@code Text} or {@code [STRINGS]} entry does not
 * replace "the string with that key" but registers a substitution <em>from the original text</em>: wherever the
 * game would use that text it gets the replacement. {@link #get(String)} applies the substitution to the
 * original text of a key; {@link #translate(String)} applies it to any text, which is how lump names such as
 * {@code "TITLEPIC"}, sprite names and sound names are replaced.
 */
public final class GameStrings {

    private final Map<String, String> originals = new LinkedHashMap<>();
    private final Map<String, Boolean> bex = new HashMap<>();
    private final Map<String, String> substitutions = new HashMap<>();
    private final CheatSequence[] cheats;

    private GameStrings(CheatSequence[] cheats) {
        this.cheats = cheats;
    }

    /** A fresh set with the vanilla strings (Chocolate Doom {@code d_englsh.h}) and cheat sequences. */
    public static GameStrings createDefault() {
        CheatId[] ids = CheatId.values();
        CheatSequence[] cheats = new CheatSequence[ids.length];
        for (CheatId id : ids) {
            cheats[id.ordinal()] = new CheatSequence(id);
        }
        GameStrings s = new GameStrings(cheats);
        OriginalStrings.define(s);
        return s;
    }

    /** A deep copy. */
    public GameStrings copy() {
        CheatSequence[] c = new CheatSequence[cheats.length];
        for (int i = 0; i < c.length; i++) {
            c[i] = cheats[i].copy();
        }
        GameStrings s = new GameStrings(c);
        s.originals.putAll(originals);
        s.bex.putAll(bex);
        s.substitutions.putAll(substitutions);
        return s;
    }

    /** Registers a vanilla string; used by the generated {@code OriginalStrings}. */
    void define(String key, String text, boolean bexMnemonic) {
        if (originals.put(key, text) != null) {
            throw new IllegalStateException("duplicate string key " + key);
        }
        bex.put(key, bexMnemonic);
    }

    /** All keys, in definition order (the order of {@code d_englsh.h}). */
    public List<String> keys() {
        return new ArrayList<>(originals.keySet());
    }

    public boolean has(String key) {
        return originals.containsKey(key);
    }

    /** The vanilla text of a key. */
    public String original(String key) {
        String text = originals.get(key);
        if (text == null) {
            throw new IllegalArgumentException("unknown string key: " + key);
        }
        return text;
    }

    /** The text in effect for a key: the vanilla text after DEHACKED substitution ({@code DEH_String}). */
    public String get(String key) {
        return translate(original(key));
    }

    /** Whether a BEX {@code [STRINGS]} section can address this key (it is in Chocolate Doom's mnemonic table). */
    public boolean isBexMnemonic(String key) {
        return Boolean.TRUE.equals(bex.get(key));
    }

    /** {@code DEH_String}: the replacement registered for exactly this text, or the text itself. */
    public String translate(String text) {
        String to = substitutions.get(text);
        return to != null ? to : text;
    }

    /** {@code DEH_AddStringReplacement}: from now on {@code from} is replaced by {@code to}. */
    public void replace(String from, String to) {
        substitutions.put(from, to);
    }

    /** All active substitutions, original text to replacement (what a {@code -dumpdefs} oracle dump lists). */
    public Map<String, String> substitutions() {
        return Map.copyOf(substitutions);
    }

    /** The cheat sequence with this id. */
    public CheatSequence cheat(CheatId id) {
        return cheats[id.ordinal()];
    }

    /** Finds a cheat by its DEHACKED name, ignoring case; {@code null} when there is none. */
    public CheatSequence cheatByDehName(String name) {
        for (CheatSequence c : cheats) {
            if (c.id().dehName().equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }
}
