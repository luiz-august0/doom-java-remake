package doom.tools;

import java.util.Arrays;
import java.util.List;

/** Dispatches {@code doom-tools <command> [args...]} to the registered {@link Tool}s. */
public final class Main {

    private static final List<Tool> TOOLS = List.of(
            new doom.tools.waddump.WadDumpTool(),
            new doom.tools.trace.TraceDiffTool(),
            new doom.tools.demo.GenDemoTool(),
            new doom.tools.demo.DemoSuiteTool(doom.testkit.oracle.OracleRunner::fromEnvironment,
                    doom.testkit.oracle.TraceCache::defaultCache,
                    (entry, demo, iwad, out) -> {
                        if (!doom.tools.demo.DemoRunTool.trace(iwad, java.util.List.of(), demo.toString(), out)) {
                            throw new IllegalStateException("demo-run failed for " + demo);
                        }
                    }),
            new doom.tools.demo.DemoRunTool(),
            new doom.tools.frame.FrameDumpTool(),
            new doom.tools.coverage.RefCoverageTool(),
            new doom.tools.bench.BenchSuiteTool());

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        System.exit(run(args));
    }

    /** Runs a command and returns its exit status. */
    public static int run(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("-h") || args[0].equals("--help")) {
            usage();
            return args.length == 0 ? 2 : 0;
        }
        for (Tool t : TOOLS) {
            if (t.name().equals(args[0])) {
                return t.run(Arrays.copyOfRange(args, 1, args.length));
            }
        }
        System.err.println("Unknown command: " + args[0]);
        usage();
        return 2;
    }

    /** The registered tools. */
    public static List<Tool> tools() {
        return TOOLS;
    }

    private static void usage() {
        System.err.println("usage: doom-tools <command> [args...]");
        for (Tool t : TOOLS) {
            System.err.printf("  %-14s %s%n", t.name(), t.summary());
        }
    }
}
