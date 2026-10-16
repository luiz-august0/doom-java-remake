package doom.tools.demo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal command-line parser for the tools: {@code --name value} options, {@code --flag} switches and positional
 * arguments. {@code --name=value} is accepted too. Unknown options are errors.
 */
public final class CliArgs {

    private final Map<String, String> options = new HashMap<>();
    private final Set<String> flags = new HashSet<>();
    private final List<String> positional = new ArrayList<>();

    private CliArgs() {
    }

    /**
     * Parses {@code args}.
     *
     * @param valueOptions names (without dashes) of the options that take a value
     * @param flagOptions  names of the switches
     * @throws IllegalArgumentException for an unknown option or a missing value
     */
    public static CliArgs parse(String[] args, Set<String> valueOptions, Set<String> flagOptions) {
        CliArgs out = new CliArgs();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--") || a.length() == 2) {
                out.positional.add(a);
                continue;
            }
            String name = a.substring(2);
            String inline = null;
            int eq = name.indexOf('=');
            if (eq >= 0) {
                inline = name.substring(eq + 1);
                name = name.substring(0, eq);
            }
            if (valueOptions.contains(name)) {
                if (inline == null) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Option --" + name + " needs a value");
                    }
                    inline = args[++i];
                }
                out.options.put(name, inline);
            } else if (flagOptions.contains(name) && inline == null) {
                out.flags.add(name);
            } else {
                throw new IllegalArgumentException("Unknown option: " + a);
            }
        }
        return out;
    }

    /** The positional arguments. */
    public List<String> positional() {
        return positional;
    }

    /** Whether the option was given. */
    public boolean has(String name) {
        return options.containsKey(name);
    }

    /** The option value, or {@code fallback}. */
    public String get(String name, String fallback) {
        return options.getOrDefault(name, fallback);
    }

    /** The option value as an int, or {@code fallback}. */
    public int getInt(String name, int fallback) {
        String v = options.get(name);
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Option --" + name + " needs a number, found '" + v + "'");
        }
    }

    /** The option value as a long, or {@code fallback}. */
    public long getLong(String name, long fallback) {
        String v = options.get(name);
        if (v == null) {
            return fallback;
        }
        try {
            return Long.parseLong(v.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Option --" + name + " needs a number, found '" + v + "'");
        }
    }

    /** Whether the switch was given. */
    public boolean flag(String name) {
        return flags.contains(name);
    }
}
