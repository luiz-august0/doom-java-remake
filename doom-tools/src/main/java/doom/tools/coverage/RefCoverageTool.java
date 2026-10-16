package doom.tools.coverage;

import doom.tools.Tool;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Lists the linuxdoom-1.10 simulation functions that no {@code @VanillaRef("file.c:Function")} refers to (ADR-007).
 *
 * <pre>
 * ref-coverage [--linuxdoom DIR] [--java DIR]... [--files REGEX] [--all] [--fail-under PERCENT]
 * </pre>
 *
 * <ul>
 *   <li>{@code --linuxdoom}: the original C sources (default: the {@code LINUXDOOM_SRC} environment variable);</li>
 *   <li>{@code --java}: a directory scanned for {@code @VanillaRef} (repeatable; default: every {@code src/main/java}
 *       of the {@code doom-*} modules below the current directory);</li>
 *   <li>{@code --files}: regular expression on the C file name; the default selects the simulation files
 *       ({@link #SIMULATION_FILES}); {@code --all} selects every {@code .c} file;</li>
 *   <li>{@code --fail-under}: exit status 1 when the coverage is below the given percentage.</li>
 * </ul>
 *
 * Besides the uncovered functions it reports references that name no function of the scanned sources (typos or
 * Chocolate-only functions), which is informational.
 */
public final class RefCoverageTool implements Tool {

    /**
     * Files whose functions define the simulation: thinkers, map geometry and specials, combat, weapons, the player,
     * level setup, fixed-point math and the RNG, the game loop and demo handling, the static data tables. Rendering,
     * UI, sound, networking and the original save-game format ({@code p_saveg.c}, replaced by DJSV) are not included.
     */
    static final String SIMULATION_FILES =
            "p_(?!saveg)[a-z]+\\.c|m_fixed\\.c|m_random\\.c|m_bbox\\.c|g_game\\.c|info\\.c|d_items\\.c|tables\\.c";

    private static final Pattern REF_ANNOTATION =
            Pattern.compile("@VanillaRef\\s*\\(([^)]*)\\)", Pattern.DOTALL);
    private static final Pattern STRING = Pattern.compile("\"([^\"]*)\"");

    @Override
    public String name() {
        return "ref-coverage";
    }

    @Override
    public String summary() {
        return "List linuxdoom functions not covered by @VanillaRef";
    }

    @Override
    public int run(String[] args) throws IOException {
        return run(args, System.out, System.err);
    }

    int run(String[] args, PrintStream out, PrintStream err) throws IOException {
        Path linuxdoom = null;
        List<Path> javaDirs = new ArrayList<>();
        String files = SIMULATION_FILES;
        double failUnder = -1;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--linuxdoom" -> linuxdoom = Path.of(value(args, ++i));
                case "--java" -> javaDirs.add(Path.of(value(args, ++i)));
                case "--files" -> files = value(args, ++i);
                case "--all" -> files = ".*\\.c";
                case "--fail-under" -> failUnder = Double.parseDouble(value(args, ++i));
                default -> {
                    err.println("ref-coverage: unknown option " + args[i]);
                    err.println("usage: ref-coverage [--linuxdoom DIR] [--java DIR]... [--files REGEX] [--all]"
                            + " [--fail-under PERCENT]");
                    return 2;
                }
            }
        }
        if (linuxdoom == null) {
            String env = System.getenv("LINUXDOOM_SRC");
            if (env == null || env.isBlank()) {
                err.println("ref-coverage: pass --linuxdoom DIR or set LINUXDOOM_SRC");
                return 2;
            }
            linuxdoom = Path.of(env);
        }
        if (!Files.isDirectory(linuxdoom)) {
            err.println("ref-coverage: not a directory: " + linuxdoom);
            return 2;
        }
        if (javaDirs.isEmpty()) {
            javaDirs.addAll(defaultJavaDirs(Path.of(".")));
        }
        Pattern fileFilter = Pattern.compile(files);

        Map<String, List<CFunctionScanner.Function>> functions = new TreeMap<>();
        try (Stream<Path> s = Files.list(linuxdoom)) {
            for (Path p : (Iterable<Path>) s.sorted()::iterator) {
                String name = p.getFileName().toString();
                if (name.endsWith(".c") && fileFilter.matcher(name).matches()) {
                    functions.put(name, CFunctionScanner.scan(Files.readString(p, StandardCharsets.ISO_8859_1)));
                }
            }
        }
        Set<String> refs = collectRefs(javaDirs);
        double pct = report(functions, refs, out);
        return failUnder >= 0 && pct < failUnder ? 1 : 0;
    }

    private static String value(String[] args, int i) {
        if (i >= args.length) {
            throw new IllegalArgumentException("missing value for " + args[i - 1]);
        }
        return args[i];
    }

    private static List<Path> defaultJavaDirs(Path root) throws IOException {
        List<Path> dirs = new ArrayList<>();
        try (Stream<Path> s = Files.list(root)) {
            for (Path module : (Iterable<Path>) s.sorted()::iterator) {
                Path src = module.resolve("src/main/java");
                if (module.getFileName().toString().startsWith("doom-") && Files.isDirectory(src)) {
                    dirs.add(src);
                }
            }
        }
        return dirs;
    }

    /** Every {@code "file.c:Function"} string found in a {@code @VanillaRef(...)} below the given directories. */
    static Set<String> collectRefs(List<Path> dirs) throws IOException {
        Set<String> refs = new LinkedHashSet<>();
        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> s = Files.walk(dir)) {
                for (Path p : (Iterable<Path>) s.filter(f -> f.toString().endsWith(".java")).sorted()::iterator) {
                    Matcher m = REF_ANNOTATION.matcher(Files.readString(p, StandardCharsets.UTF_8));
                    while (m.find()) {
                        Matcher str = STRING.matcher(m.group(1));
                        while (str.find()) {
                            refs.add(str.group(1).trim());
                        }
                    }
                }
            }
        }
        return refs;
    }

    /** Prints the report and returns the coverage in percent. */
    private static double report(Map<String, List<CFunctionScanner.Function>> functions, Set<String> refs,
            PrintStream out) {
        long total = 0;
        long covered = 0;
        Set<String> known = new TreeSet<>();
        StringBuilder detail = new StringBuilder();
        for (Map.Entry<String, List<CFunctionScanner.Function>> e : functions.entrySet()) {
            List<CFunctionScanner.Function> missing = new ArrayList<>();
            int fileCovered = 0;
            for (CFunctionScanner.Function f : e.getValue()) {
                known.add(e.getKey() + ":" + f.name());
                if (refs.contains(e.getKey() + ":" + f.name())) {
                    fileCovered++;
                } else {
                    missing.add(f);
                }
            }
            total += e.getValue().size();
            covered += fileCovered;
            detail.append(String.format("%-12s %3d/%-3d", e.getKey(), fileCovered, e.getValue().size())).append('\n');
            missing.sort(Comparator.comparingInt(CFunctionScanner.Function::line));
            for (CFunctionScanner.Function f : missing) {
                detail.append("    uncovered  ").append(e.getKey()).append(':').append(f.name())
                        .append("  (line ").append(f.line()).append(")\n");
            }
        }
        List<String> dangling = new ArrayList<>();
        for (String r : refs) {
            int colon = r.indexOf(':');
            if (colon > 0 && functions.containsKey(r.substring(0, colon)) && !known.contains(r)) {
                dangling.add(r);
            }
        }
        double pct = total == 0 ? 100.0 : 100.0 * covered / total;
        out.printf(java.util.Locale.ROOT, "ref-coverage: %d/%d functions referenced by @VanillaRef (%.1f%%) in %d C files; %d distinct references%n",
                covered, total, pct, functions.size(), refs.size());
        out.print(detail);
        if (!dangling.isEmpty()) {
            out.println("references naming no function of the scanned files (" + dangling.size() + "):");
            for (String d : dangling) {
                out.println("    " + d);
            }
        }
        return pct;
    }
}
