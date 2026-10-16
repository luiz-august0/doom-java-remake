package doom.engine.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The {@code doom-java.cfg} file: {@code key = value} lines, {@code #} comments (also after a value), read leniently
 * and written back preserving comments and the order of lines (ADR-017, spec 07 section 4).
 *
 * <p>Leniency: a malformed line, an unknown key, a duplicate key or an invalid value never fails; each produces a
 * warning (see {@link #warnings()}) and the default is used. Unknown keys are kept and written back unchanged.
 * Writing happens on a virtual thread ({@link #saveAsync}, ADR-020), atomically (temporary file, then move).
 * Not thread safe except for {@link #saveAsync} and {@link #awaitSaves}.
 */
public final class Config {

    private static final class Line {
        /** Text of the line without line terminator; rewritten when the value of a key line changes. */
        String raw;
        /** Key of a key line, {@code null} for comments and blank lines. */
        final String key;

        Line(String raw, String key) {
            this.raw = raw;
            this.key = key;
        }
    }

    private final List<Line> lines = new ArrayList<>();
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, Line> lineOf = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();
    private final Set<String> invalidReported = new HashSet<>();
    private final List<CompletableFuture<Void>> pendingSaves = new CopyOnWriteArrayList<>();
    private boolean dirty;

    private Config() {
    }

    /** A configuration holding only defaults and no lines. */
    public static Config empty() {
        return new Config();
    }

    /** The default file: every known key with its default value and a documenting comment. */
    public static Config defaults() {
        Config c = new Config();
        c.lines.add(new Line("# doom-java configuration. Lines are 'key = value'; '#' starts a comment.", null));
        c.lines.add(new Line("# Unknown keys are kept; invalid values fall back to the default with a warning.", null));
        for (Map.Entry<String, List<Setting<?>>> section : Settings.sections().entrySet()) {
            c.lines.add(new Line("", null));
            c.lines.add(new Line("# " + section.getKey(), null));
            for (Setting<?> s : section.getValue()) {
                c.addDefaultLine(s);
            }
        }
        return c;
    }

    private <T> void addDefaultLine(Setting<T> s) {
        String value = s.format(s.defaultValue());
        String hint = s.typeHint().equals("text") ? s.description() : s.description() + " [" + s.typeHint() + "]";
        Line line = new Line(s.key() + " = " + value + "  # " + hint, s.key());
        lines.add(line);
        lineOf.put(s.key(), line);
        values.put(s.key(), value);
    }

    /** Parses configuration text; problems become warnings. */
    public static Config parse(String text) {
        Config c = new Config();
        int number = 0;
        for (String raw : text.split("\r?\n", -1)) {
            number++;
            c.parseLine(raw, number);
        }
        if (!c.lines.isEmpty() && c.lines.get(c.lines.size() - 1).raw.isEmpty() && text.endsWith("\n")) {
            c.lines.remove(c.lines.size() - 1); // the empty piece after the final line feed
        }
        return c;
    }

    private void parseLine(String raw, int number) {
        String t = raw.strip();
        if (t.isEmpty() || t.charAt(0) == '#') {
            lines.add(new Line(raw, null));
            return;
        }
        int eq = t.indexOf('=');
        if (eq <= 0) {
            warnings.add("line " + number + ": expected 'key = value', ignored: " + t);
            lines.add(new Line(raw, null));
            return;
        }
        String key = t.substring(0, eq).strip();
        String value = valuePart(t.substring(eq + 1));
        if (values.containsKey(key)) {
            warnings.add("line " + number + ": duplicate key '" + key + "', the last value wins");
            Line old = lineOf.get(key);
            if (old != null) {
                // keep both lines in the file; only the later one counts
                lines.add(new Line(raw, null));
                values.put(key, value);
                return;
            }
        }
        if (Settings.find(key) == null) {
            warnings.add("line " + number + ": unknown key '" + key + "'");
        }
        Line line = new Line(raw, key);
        lines.add(line);
        lineOf.put(key, line);
        values.put(key, value);
    }

    private static String valuePart(String afterEquals) {
        String v = afterEquals;
        int hash = v.indexOf('#');
        if (hash >= 0) {
            v = v.substring(0, hash);
        }
        v = v.strip();
        if (v.length() >= 2 && v.charAt(0) == '"' && v.charAt(v.length() - 1) == '"') {
            v = v.substring(1, v.length() - 1);
        }
        return v;
    }

    /** Reads {@code file}; a missing file gives the documented defaults (not yet written). */
    public static Config load(Path file) {
        if (!Files.isRegularFile(file)) {
            Config c = defaults();
            c.dirty = true;
            return c;
        }
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            Config c = defaults();
            c.warnings.add("cannot read " + file + ": " + e.getMessage() + "; using defaults");
            c.dirty = true;
            return c;
        }
    }

    // ------------------------------------------------------------------ access

    /** The value of a setting: the configured one when valid, else the default (with a warning, once). */
    public <T> T get(Setting<T> setting) {
        String raw = values.get(setting.key());
        if (raw == null) {
            return setting.defaultValue();
        }
        T parsed = setting.parse(raw);
        if (parsed == null) {
            if (invalidReported.add(setting.key())) {
                warnings.add("invalid value '" + raw + "' for " + setting.key() + " (" + setting.typeHint()
                        + "), using " + setting.format(setting.defaultValue()));
            }
            return setting.defaultValue();
        }
        return parsed;
    }

    /** Raw text of a key, or {@code null} when absent (also for keys that are not in the catalogue). */
    public String getRaw(String key) {
        return values.get(key);
    }

    /** Whether the key is present in the file. */
    public boolean has(String key) {
        return values.containsKey(key);
    }

    /** Sets a value. The line keeps its position and trailing comment; a new key is appended at the end. */
    public <T> void set(Setting<T> setting, T value) {
        setRaw(setting.key(), setting.format(value));
    }

    /** Sets the raw text of a key. */
    public void setRaw(String key, String value) {
        if (value.equals(values.get(key))) {
            return;
        }
        values.put(key, value);
        invalidReported.remove(key);
        dirty = true;
        Line line = lineOf.get(key);
        if (line == null) {
            line = new Line(key + " = " + value, key);
            lines.add(line);
            lineOf.put(key, line);
            return;
        }
        line.raw = rewrite(line.raw, key, value);
    }

    private static String rewrite(String raw, String key, String value) {
        int indent = 0;
        while (indent < raw.length() && Character.isWhitespace(raw.charAt(indent))) {
            indent++;
        }
        int eq = raw.indexOf('=');
        String tail = raw.substring(eq + 1);
        int hash = tail.indexOf('#');
        String comment = hash >= 0 ? tail.substring(hash) : "";
        String out = raw.substring(0, indent) + key + " = " + value;
        return comment.isEmpty() ? out : out + "  " + comment;
    }

    /** Warnings collected while reading and using the configuration. */
    public List<String> warnings() {
        return warnings;
    }

    /** Whether something changed since reading or the last save. */
    public boolean isDirty() {
        return dirty;
    }

    // ------------------------------------------------------------------ writing

    /** The file text. */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        for (Line l : lines) {
            sb.append(l.raw).append('\n');
        }
        return sb.toString();
    }

    /** Writes the file now, atomically. */
    public void save(Path file) throws IOException {
        write(file, toText());
        dirty = false;
    }

    /**
     * Writes the file on a virtual thread; the text is captured now, so the caller may keep changing the
     * configuration. Failures are reported in the returned future.
     */
    public CompletableFuture<Void> saveAsync(Path file) {
        String text = toText();
        dirty = false;
        CompletableFuture<Void> done = new CompletableFuture<>();
        pendingSaves.add(done);
        Thread.ofVirtual().name("config-writer").start(() -> {
            try {
                write(file, text);
                done.complete(null);
            } catch (IOException | RuntimeException e) {
                done.completeExceptionally(e);
            } finally {
                pendingSaves.remove(done);
            }
        });
        return done;
    }

    /** Waits for the writes started with {@link #saveAsync}; failures are ignored here (see the futures). */
    public void awaitSaves() {
        for (CompletableFuture<Void> f : List.copyOf(pendingSaves)) {
            try {
                f.join();
            } catch (RuntimeException ignored) {
                // reported through the future returned by saveAsync
            }
        }
    }

    private static void write(Path file, String text) throws IOException {
        Path abs = file.toAbsolutePath();
        Path parent = abs.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = abs.resolveSibling(abs.getFileName() + ".tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, abs, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, abs, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
