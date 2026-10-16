package doom.testkit.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Reads and writes {@code manifest.tsv} (see {@link ManifestEntry}). Lines starting with {@code #} are comments. */
public final class DemoManifest {

    private DemoManifest() {
    }

    /**
     * Reads a manifest.
     *
     * @throws IllegalArgumentException on a malformed line (with the line number) or a duplicate name
     */
    public static List<ManifestEntry> read(Path file) throws IOException {
        List<ManifestEntry> out = new ArrayList<>();
        Set<String> names = new HashSet<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.startsWith("#") || line.equals(ManifestEntry.COLUMNS)) {
                continue;
            }
            try {
                ManifestEntry e = ManifestEntry.parse(line);
                if (!names.add(e.name().toLowerCase(java.util.Locale.ROOT))) {
                    throw new IllegalArgumentException("Duplicate demo name " + e.name());
                }
                out.add(e);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(file + ":" + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        return out;
    }

    /** Writes a manifest with a comment header and the column line. */
    public static void write(Path file, List<ManifestEntry> entries) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Synthetic demo corpus (version 109). Regenerate the demos with: doom-tools gen-demo --corpus <dir>\n");
        sb.append("# skill: 0..4 (sk_baby..sk_nightmare); deathmatch: 0 coop/single, 1 deathmatch, 2 altdeath;\n");
        sb.append("# flags: respawn,fast,nomonsters or -; seed/policy: see doom.testkit.demo.DemoGenerator.\n");
        sb.append(ManifestEntry.COLUMNS).append('\n');
        for (ManifestEntry e : entries) {
            sb.append(e.toLine()).append('\n');
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    /** The entries whose name or {@code iwad:map} matches the regular expression (all entries when it is null). */
    public static List<ManifestEntry> filter(List<ManifestEntry> entries, String regex) {
        if (regex == null || regex.isEmpty()) {
            return entries;
        }
        Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        List<ManifestEntry> out = new ArrayList<>();
        for (ManifestEntry e : entries) {
            if (p.matcher(e.name()).find() || p.matcher(e.iwad() + ":" + e.spec().map()).find()) {
                out.add(e);
            }
        }
        return out;
    }
}
