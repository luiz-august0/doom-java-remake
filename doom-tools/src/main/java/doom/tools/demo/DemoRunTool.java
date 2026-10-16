package doom.tools.demo;

import doom.engine.DoomEngine;
import doom.engine.EngineParams;
import doom.engine.GameSources;
import doom.engine.cli.LaunchOptions;
import doom.engine.config.Config;
import doom.engine.config.DataDirs;
import doom.testkit.Iwads;
import doom.tools.Tool;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * {@code demo-run}: plays a demo headless with the engine and writes its per-tic trace (contracts section 6), the
 * counterpart of the oracle run used by {@code demo-suite --ours-dir}.
 *
 * <pre>
 * demo-run --iwad doom1 --demo DEMO1 --trace out.trace.gz
 *          [--file a.wad,b.wad] [--gameversion 1.9|ultimate|final|final2] [--nodeh]
 *          [--verbose out.verbose.gz [--from N --to M]] [--maxtics N]
 * </pre>
 *
 * <p>{@code --iwad} is a path, or a bare name such as {@code doom1} / {@code freedoom2.wad} looked up in
 * {@code DOOM_IWAD_DIR}. {@code --demo} is a lump name of the loaded WADs or a {@code .lmp} file. Exit status: 0 when
 * the demo ran to its end marker, 1 when the run stopped otherwise (error, tic limit), 2 for usage errors.
 */
public final class DemoRunTool implements Tool {

    @Override
    public String name() {
        return "demo-run";
    }

    @Override
    public String summary() {
        return "Play a demo headless and write its trace";
    }

    @Override
    public int run(String[] args) throws Exception {
        CliArgs cli;
        try {
            cli = CliArgs.parse(args, Set.of("iwad", "demo", "trace", "file", "gameversion", "verbose", "from", "to",
                    "maxtics"), Set.of("nodeh", "help"));
        } catch (IllegalArgumentException e) {
            System.err.println(name() + ": " + e.getMessage());
            usage();
            return 2;
        }
        if (cli.flag("help") || !cli.has("iwad") || !cli.has("demo") || !cli.has("trace")) {
            usage();
            return cli.flag("help") ? 0 : 2;
        }
        Optional<Path> iwad = resolveIwad(cli.get("iwad", ""));
        if (iwad.isEmpty()) {
            System.err.println(name() + ": IWAD not found: " + cli.get("iwad", "") + " (set DOOM_IWAD_DIR or give a path)");
            return 2;
        }
        List<Path> pwads = new ArrayList<>();
        if (cli.has("file")) {
            for (String f : cli.get("file", "").split(",")) {
                if (!f.isBlank()) {
                    pwads.add(Path.of(f.strip()));
                }
            }
        }
        List<String> engineArgs = new ArrayList<>(List.of("-headless", "-timedemo", cli.get("demo", ""),
                "-iwad", iwad.get().toString(), "-trace", cli.get("trace", "")));
        if (!pwads.isEmpty()) {
            engineArgs.add("-file");
            pwads.forEach(p -> engineArgs.add(p.toString()));
        }
        if (cli.has("gameversion")) {
            engineArgs.addAll(List.of("-gameversion", cli.get("gameversion", "")));
        }
        if (cli.flag("nodeh")) {
            engineArgs.add("-nodeh");
        }
        if (cli.has("verbose")) {
            engineArgs.addAll(List.of("-traceverbose", cli.get("verbose", "")));
            engineArgs.addAll(List.of("-tracefrom", cli.get("from", "0"), "-traceto",
                    cli.get("to", Integer.toString(Integer.MAX_VALUE))));
        }
        if (cli.has("maxtics")) {
            engineArgs.addAll(List.of("-maxtics", cli.get("maxtics", "-1")));
        }
        boolean ended = play(LaunchOptions.parse(engineArgs.toArray(new String[0])), System.err);
        return ended ? 0 : 1;
    }

    /**
     * Plays one demo headless and writes the trace: the entry point for {@code demo-suite}'s {@code OursTracer}.
     *
     * @param iwad     the IWAD file
     * @param pwads    PWADs to load after it
     * @param demo     the demo file ({@code .lmp}) or a lump name
     * @param traceOut where to write the trace (gzip when the name ends with {@code .gz})
     * @return whether the demo reached its end marker
     */
    public static boolean trace(Path iwad, List<Path> pwads, String demo, Path traceOut) {
        List<String> engineArgs = new ArrayList<>(List.of("-headless", "-timedemo", demo, "-iwad", iwad.toString(),
                "-trace", traceOut.toString()));
        if (!pwads.isEmpty()) {
            engineArgs.add("-file");
            pwads.forEach(p -> engineArgs.add(p.toString()));
        }
        return play(LaunchOptions.parse(engineArgs.toArray(new String[0])), System.err);
    }

    private static boolean play(LaunchOptions options, PrintStream log) {
        DataDirs dirs = DataDirs.system();
        GameSources sources = GameSources.load(options, dirs);
        EngineParams params = new EngineParams(options).config(Config.defaults()).dirs(dirs).sources(sources).out(log);
        try (DoomEngine engine = new DoomEngine(params)) {
            engine.run();
            engine.printTimedemoResult();
            log.printf(Locale.ROOT, "demo-run: %d tics%s%n", engine.ranTics(),
                    engine.demoEnded() ? "" : " (the demo did not reach its end marker)");
            return engine.demoEnded();
        }
    }

    private static Optional<Path> resolveIwad(String arg) {
        Path direct = Path.of(arg);
        if (Files.isRegularFile(direct)) {
            return Optional.of(direct);
        }
        String name = arg.toLowerCase(Locale.ROOT).endsWith(".wad") ? arg : arg + ".wad";
        return Iwads.find(name);
    }

    private void usage() {
        System.err.println("usage: demo-run --iwad <file|name> --demo <lump|file.lmp> --trace <out[.gz]>");
        System.err.println("                [--file a.wad,b.wad] [--gameversion V] [--nodeh]");
        System.err.println("                [--verbose <out> [--from N --to M]] [--maxtics N]");
    }
}
