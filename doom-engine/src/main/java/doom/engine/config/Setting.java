package doom.engine.config;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * One configuration key with its type, default and documentation. Instances are immutable; the catalogue of known
 * settings is {@link Settings}.
 *
 * @param <T> the value type
 */
public final class Setting<T> {

    private final String key;
    private final T defaultValue;
    private final String description;
    private final String typeHint;
    private final Function<String, T> parser;

    private Setting(String key, T defaultValue, String description, String typeHint, Function<String, T> parser) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.description = description;
        this.typeHint = typeHint;
        this.parser = parser;
    }

    /** An integer in {@code [min, max]}. */
    public static Setting<Integer> integer(String key, int def, int min, int max, String description) {
        return new Setting<>(key, def, description, min + ".." + max, raw -> {
            try {
                int v = Integer.parseInt(raw);
                return v < min || v > max ? null : v;
            } catch (NumberFormatException e) {
                return null;
            }
        });
    }

    /** A decimal number in {@code [min, max]}. */
    public static Setting<Double> decimal(String key, double def, double min, double max, String description) {
        return new Setting<>(key, def, description, min + ".." + max, raw -> {
            try {
                double v = Double.parseDouble(raw);
                return Double.isNaN(v) || v < min || v > max ? null : v;
            } catch (NumberFormatException e) {
                return null;
            }
        });
    }

    /** {@code true}/{@code false} (also {@code on/off}, {@code yes/no}, {@code 1/0}). */
    public static Setting<Boolean> bool(String key, boolean def, String description) {
        return new Setting<>(key, def, description, "true | false", raw -> switch (raw.toLowerCase(Locale.ROOT)) {
            case "true", "on", "yes", "1" -> Boolean.TRUE;
            case "false", "off", "no", "0" -> Boolean.FALSE;
            default -> null;
        });
    }

    /** Free text (never invalid). */
    public static Setting<String> text(String key, String def, String description) {
        return new Setting<>(key, def, description, "text", raw -> raw);
    }

    /** One of the given words, compared case-insensitively; the value is returned in lower case. */
    public static Setting<String> choice(String key, String def, String description, String... options) {
        List<String> list = List.of(options);
        return new Setting<>(key, def, description, String.join(" | ", options), raw -> {
            String v = raw.toLowerCase(Locale.ROOT);
            return list.contains(v) ? v : null;
        });
    }

    public String key() {
        return key;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public String description() {
        return description;
    }

    /** Range or list of accepted values, for the documentation comment. */
    public String typeHint() {
        return typeHint;
    }

    /** Parses {@code raw}; {@code null} when it is not a valid value. */
    public T parse(String raw) {
        return parser.apply(raw.strip());
    }

    /** The text written to the file for a value. */
    public String format(T value) {
        if (value instanceof Double d) {
            return d == Math.rint(d) && Math.abs(d) < 1e9 ? Long.toString(d.longValue()) : Double.toString(d);
        }
        return String.valueOf(value);
    }

    @Override
    public String toString() {
        return key;
    }
}
