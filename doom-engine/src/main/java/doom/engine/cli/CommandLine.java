package doom.engine.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vanilla-compatible command-line access (spec 07 section 5): a parameter is an argument starting with {@code -};
 * its values are the arguments that follow it up to the next parameter ({@code M_CheckParmWithArgs}). Parameter names
 * are compared case-insensitively. When a parameter is repeated the first occurrence wins, as in the original.
 */
public final class CommandLine {

    private final List<String> args;

    private CommandLine(List<String> args) {
        this.args = args;
    }

    public static CommandLine of(String... args) {
        return new CommandLine(List.of(args));
    }

    public static CommandLine of(List<String> args) {
        return new CommandLine(List.copyOf(args));
    }

    /** All arguments. */
    public List<String> args() {
        return args;
    }

    private static boolean isParameter(String s) {
        return s.length() > 1 && s.charAt(0) == '-' && !isNumber(s);
    }

    private static boolean isNumber(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private int indexOf(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (int i = 0; i < args.size(); i++) {
            if (isParameter(args.get(i)) && args.get(i).toLowerCase(Locale.ROOT).equals(n)) {
                return i;
            }
        }
        return -1;
    }

    /** {@code M_ParmExists}. */
    public boolean has(String name) {
        return indexOf(name) >= 0;
    }

    /** The values that follow the parameter, or an empty list when it is absent or has none. */
    public List<String> values(String name) {
        int i = indexOf(name);
        List<String> out = new ArrayList<>();
        if (i < 0) {
            return out;
        }
        for (int j = i + 1; j < args.size() && !isParameter(args.get(j)); j++) {
            out.add(args.get(j));
        }
        return out;
    }

    /** The first value of the parameter, or {@code null} when absent or without value. */
    public String value(String name) {
        List<String> v = values(name);
        return v.isEmpty() ? null : v.get(0);
    }

    /** The first value as an integer, or {@code fallback} when absent or not a number. */
    public int intValue(String name, int fallback) {
        String v = value(name);
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Parameters (arguments starting with {@code -}) in order. */
    public List<String> parameters() {
        List<String> out = new ArrayList<>();
        for (String a : args) {
            if (isParameter(a)) {
                out.add(a);
            }
        }
        return out;
    }
}
