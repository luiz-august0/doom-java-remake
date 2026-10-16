package doom.testkit.demo;

import java.util.ArrayList;
import java.util.List;

/**
 * One line of {@code testdata/demos/manifest.tsv}: a synthetic demo and everything needed to regenerate it.
 *
 * <p>Columns (tab separated): {@code name iwad map skill players deathmatch flags tics seed policy}.
 * {@code skill} is the header byte 0..4 ({@code sk_baby}..{@code sk_nightmare}); {@code flags} is a comma-separated
 * subset of {@code respawn,fast,nomonsters} or {@code -}; {@code policy} is a {@link DemoPolicy#id()}.
 * The demo file is {@code <name>.lmp}; names are at most 8 characters so they are valid lump names.
 *
 * @param name       demo name (file {@code name.lmp})
 * @param iwad       IWAD file name, for example {@code freedoom2.wad}
 * @param spec       the generation parameters
 */
public record ManifestEntry(String name, String iwad, DemoSpec spec) {

    /** Header line of the manifest file. */
    public static final String COLUMNS = "name\tiwad\tmap\tskill\tplayers\tdeathmatch\tflags\ttics\tseed\tpolicy";

    public ManifestEntry {
        if (name.isEmpty() || name.length() > 8 || !name.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Demo name must be 1..8 letters/digits/underscore: " + name);
        }
    }

    /** The demo file name. */
    public String fileName() {
        return name + ".lmp";
    }

    /** The {@code flags} column. */
    public String flags() {
        List<String> f = new ArrayList<>(3);
        if (spec.respawn()) {
            f.add("respawn");
        }
        if (spec.fast()) {
            f.add("fast");
        }
        if (spec.noMonsters()) {
            f.add("nomonsters");
        }
        return f.isEmpty() ? "-" : String.join(",", f);
    }

    /** The manifest line (no line feed). */
    public String toLine() {
        return String.join("\t", name, iwad, spec.map(), Integer.toString(spec.skill()),
                Integer.toString(spec.players()), Integer.toString(spec.deathmatch()), flags(),
                Integer.toString(spec.tics()), Long.toString(spec.seed()), spec.policy().id());
    }

    /**
     * Parses a manifest line.
     *
     * @throws IllegalArgumentException when the line is malformed
     */
    public static ManifestEntry parse(String line) {
        String[] c = line.split("\t", -1);
        if (c.length != 10) {
            throw new IllegalArgumentException("Expected 10 tab-separated columns, found " + c.length + ": " + line);
        }
        boolean respawn = false;
        boolean fast = false;
        boolean noMonsters = false;
        if (!c[6].equals("-")) {
            for (String f : c[6].split(",")) {
                switch (f) {
                    case "respawn" -> respawn = true;
                    case "fast" -> fast = true;
                    case "nomonsters" -> noMonsters = true;
                    default -> throw new IllegalArgumentException("Unknown flag '" + f + "' in: " + line);
                }
            }
        }
        try {
            DemoSpec spec = new DemoSpec(Integer.parseInt(c[3]), c[2], Integer.parseInt(c[4]), Integer.parseInt(c[5]),
                    respawn, fast, noMonsters, Integer.parseInt(c[7]), Long.parseLong(c[8]), DemoPolicy.parse(c[9]));
            return new ManifestEntry(c[0], c[1], spec);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bad number in: " + line, e);
        }
    }
}
