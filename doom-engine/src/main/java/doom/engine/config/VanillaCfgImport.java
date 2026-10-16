package doom.engine.config;

import doom.platform.input.KeyCode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code -importcfg}: copies the settings of a vanilla (Chocolate Doom) {@code default.cfg} that have a counterpart
 * here: volumes, mouse sensitivity, screen size, detail, gamma, messages, channels and the key and mouse button
 * bindings. The file has one {@code name value} pair per line; key variables hold DOS scan codes.
 */
public final class VanillaCfgImport {

    private static final int[] SCAN_TO_KEY = new int[128];

    static {
        String top = "1234567890-=";
        for (int i = 0; i < top.length(); i++) {
            SCAN_TO_KEY[2 + i] = top.charAt(i);
        }
        SCAN_TO_KEY[1] = KeyCode.ESCAPE;
        SCAN_TO_KEY[14] = KeyCode.BACKSPACE;
        SCAN_TO_KEY[15] = KeyCode.TAB;
        String q = "qwertyuiop[]";
        for (int i = 0; i < q.length(); i++) {
            SCAN_TO_KEY[16 + i] = q.charAt(i);
        }
        SCAN_TO_KEY[28] = KeyCode.ENTER;
        SCAN_TO_KEY[29] = KeyCode.RCTRL;
        String a = "asdfghjkl;'`";
        for (int i = 0; i < a.length(); i++) {
            SCAN_TO_KEY[30 + i] = a.charAt(i);
        }
        SCAN_TO_KEY[42] = KeyCode.RSHIFT;
        SCAN_TO_KEY[43] = '\\';
        String z = "zxcvbnm,./";
        for (int i = 0; i < z.length(); i++) {
            SCAN_TO_KEY[44 + i] = z.charAt(i);
        }
        SCAN_TO_KEY[54] = KeyCode.RSHIFT;
        SCAN_TO_KEY[56] = KeyCode.RALT;
        SCAN_TO_KEY[57] = ' ';
        for (int i = 0; i < 10; i++) {
            SCAN_TO_KEY[59 + i] = KeyCode.F1 + i;
        }
        SCAN_TO_KEY[71] = KeyCode.HOME;
        SCAN_TO_KEY[72] = KeyCode.UPARROW;
        SCAN_TO_KEY[73] = KeyCode.PGUP;
        SCAN_TO_KEY[75] = KeyCode.LEFTARROW;
        SCAN_TO_KEY[77] = KeyCode.RIGHTARROW;
        SCAN_TO_KEY[79] = KeyCode.END;
        SCAN_TO_KEY[80] = KeyCode.DOWNARROW;
        SCAN_TO_KEY[81] = KeyCode.PGDN;
        SCAN_TO_KEY[82] = KeyCode.INS;
        SCAN_TO_KEY[83] = KeyCode.DEL;
        SCAN_TO_KEY[87] = KeyCode.F11;
        SCAN_TO_KEY[88] = KeyCode.F12;
    }

    private VanillaCfgImport() {
    }

    /**
     * Imports into {@code target}.
     *
     * @return one message per imported group and per problem, for the log
     */
    public static List<String> apply(String text, Config target) {
        Map<String, String> vars = new HashMap<>();
        for (String raw : text.split("\r?\n")) {
            String t = raw.strip();
            if (t.isEmpty() || t.charAt(0) == '#') {
                continue;
            }
            int sp = indexOfWhitespace(t);
            if (sp < 0) {
                continue;
            }
            String v = t.substring(sp).strip();
            if (v.length() >= 2 && v.charAt(0) == '"' && v.charAt(v.length() - 1) == '"') {
                v = v.substring(1, v.length() - 1);
            }
            vars.put(t.substring(0, sp), v);
        }
        List<String> messages = new ArrayList<>();
        int imported = 0;
        imported += copyInt(vars, "sfx_volume", Settings.SOUND_SFX_VOLUME, target, messages);
        imported += copyInt(vars, "music_volume", Settings.SOUND_MUSIC_VOLUME, target, messages);
        imported += copyInt(vars, "snd_channels", Settings.SOUND_CHANNELS, target, messages);
        imported += copyInt(vars, "mouse_sensitivity", Settings.INPUT_MOUSE_SENS_X, target, messages);
        imported += copyInt(vars, "mouse_sensitivity", Settings.INPUT_MOUSE_SENS_Y, target, messages);
        imported += copyInt(vars, "screenblocks", Settings.GAME_SCREEN_BLOCKS, target, messages);
        imported += copyInt(vars, "detaillevel", Settings.GAME_DETAIL, target, messages);
        imported += copyInt(vars, "usegamma", Settings.GAME_GAMMA, target, messages);
        String sm = vars.get("show_messages");
        if (sm != null) {
            target.set(Settings.GAME_SHOW_MESSAGES, !sm.equals("0"));
            imported++;
        }

        Map<Action, String> keyVars = new EnumMap<>(Action.class);
        keyVars.put(Action.FORWARD, "key_up");
        keyVars.put(Action.BACK, "key_down");
        keyVars.put(Action.TURN_LEFT, "key_left");
        keyVars.put(Action.TURN_RIGHT, "key_right");
        keyVars.put(Action.STRAFE_LEFT, "key_strafeleft");
        keyVars.put(Action.STRAFE_RIGHT, "key_straferight");
        keyVars.put(Action.STRAFE, "key_strafe");
        keyVars.put(Action.RUN, "key_speed");
        keyVars.put(Action.FIRE, "key_fire");
        keyVars.put(Action.USE, "key_use");
        keyVars.put(Action.PAUSE, "key_pause");
        for (int i = 0; i < 8; i++) {
            keyVars.put(Action.weapon(i), "key_weapon" + (i + 1));
        }
        Map<Action, String> mouseVars = new EnumMap<>(Action.class);
        mouseVars.put(Action.FIRE, "mouseb_fire");
        mouseVars.put(Action.STRAFE, "mouseb_strafe");
        mouseVars.put(Action.MOUSE_FORWARD, "mouseb_forward");
        for (Action action : Action.values()) {
            List<String> inputs = new ArrayList<>();
            String kv = keyVars.get(action);
            if (kv != null && vars.containsKey(kv)) {
                int key = keyOf(vars.get(kv));
                if (key > 0) {
                    inputs.add(InputNames.name(key));
                } else {
                    messages.add("cannot convert " + kv + " = " + vars.get(kv));
                }
            }
            String mv = mouseVars.get(action);
            if (mv != null && vars.containsKey(mv)) {
                int button = parseInt(vars.get(mv), -1);
                if (button >= 0 && button < InputNames.MOUSE_BUTTONS) {
                    inputs.add("MOUSE" + (button + 1));
                }
            }
            if (!inputs.isEmpty()) {
                target.setRaw(action.configKey(), String.join(", ", inputs));
                imported++;
            }
        }
        messages.add("imported " + imported + " settings");
        return messages;
    }

    private static int copyInt(Map<String, String> vars, String name, Setting<Integer> setting, Config target,
                               List<String> messages) {
        String raw = vars.get(name);
        if (raw == null) {
            return 0;
        }
        int v = parseInt(raw, Integer.MIN_VALUE);
        if (v == Integer.MIN_VALUE || setting.parse(Integer.toString(v)) == null) {
            messages.add("ignored " + name + " = " + raw);
            return 0;
        }
        target.set(setting, v);
        return 1;
    }

    /** Converts the value of a {@code key_*} variable (a DOS scan code, or a key code for modern files). */
    private static int keyOf(String raw) {
        int v = parseInt(raw, -1);
        if (v <= 0) {
            return -1;
        }
        if (v < SCAN_TO_KEY.length && SCAN_TO_KEY[v] != 0) {
            return SCAN_TO_KEY[v];
        }
        return v < InputNames.KEY_COUNT ? v : -1;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int indexOfWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}
