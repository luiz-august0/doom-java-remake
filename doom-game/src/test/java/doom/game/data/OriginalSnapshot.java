package doom.game.data;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@code original-defs.txt}, the committed snapshot of the original tables written by
 * {@code doom-codegen gen-defs}. Every list is indexed like the corresponding game table.
 */
final class OriginalSnapshot {

    record StateRow(int index, String name, int sprite, int frame, int tics, String action, int next, int misc1,
            int misc2) {
    }

    record ThingRow(int index, String name, int[] v) {
    }

    record WeaponRow(int index, int[] v) {
    }

    record SoundRow(int index, String name, boolean singularity, int priority, int link, int pitch, int volume) {
    }

    record StringRow(String key, boolean bex, String text) {
    }

    record CheatRow(int index, String dehName, String variable, int parameterChars, String sequence) {
    }

    record MiscRow(String dehName, String variable, int defaultValue) {
    }

    final List<String> sprites = new ArrayList<>();
    final List<StateRow> states = new ArrayList<>();
    final List<ThingRow> things = new ArrayList<>();
    final List<WeaponRow> weapons = new ArrayList<>();
    final List<int[]> ammo = new ArrayList<>();
    final List<SoundRow> sounds = new ArrayList<>();
    final List<String> music = new ArrayList<>();
    final List<StringRow> strings = new ArrayList<>();
    final List<CheatRow> cheats = new ArrayList<>();
    final List<MiscRow> misc = new ArrayList<>();

    static OriginalSnapshot load() {
        try (InputStream in = OriginalSnapshot.class.getResourceAsStream("original-defs.txt")) {
            if (in == null) {
                throw new IllegalStateException("original-defs.txt not found");
            }
            OriginalSnapshot s = new OriginalSnapshot();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isEmpty() && line.charAt(0) != '#') {
                    s.parse(line.split("\t", -1));
                }
            }
            return s;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void parse(String[] f) {
        switch (f[0]) {
            case "sprite" -> sprites.add(f[2]);
            case "state" -> states.add(new StateRow(i(f[1]), f[2], i(f[3]), i(f[4]), i(f[5]), f[6], i(f[7]), i(f[8]),
                    i(f[9])));
            case "thing" -> {
                int[] v = new int[f.length - 3];
                for (int k = 0; k < v.length; k++) {
                    v[k] = i(f[3 + k]);
                }
                things.add(new ThingRow(i(f[1]), f[2], v));
            }
            case "weapon" -> {
                int[] v = new int[f.length - 2];
                for (int k = 0; k < v.length; k++) {
                    v[k] = i(f[2 + k]);
                }
                weapons.add(new WeaponRow(i(f[1]), v));
            }
            case "ammo" -> ammo.add(new int[] {i(f[2]), i(f[3])});
            case "sfx" -> sounds.add(new SoundRow(i(f[1]), f[2], i(f[3]) != 0, i(f[4]), i(f[5]), i(f[6]), i(f[7])));
            case "music" -> music.add(f[2].equals("-") ? null : f[2]);
            case "string" -> strings.add(new StringRow(f[1], i(f[2]) != 0, unescape(f[3])));
            case "cheat" -> cheats.add(new CheatRow(i(f[1]), f[2], f[3], i(f[4]), unescape(f[5])));
            case "misc" -> misc.add(new MiscRow(f[1], f[2], i(f[3])));
            default -> throw new IllegalStateException("unknown snapshot record " + f[0]);
        }
    }

    private static int i(String s) {
        return Integer.parseInt(s);
    }

    static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '\\') {
                char n = s.charAt(++k);
                sb.append(switch (n) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    default -> n;
                });
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
