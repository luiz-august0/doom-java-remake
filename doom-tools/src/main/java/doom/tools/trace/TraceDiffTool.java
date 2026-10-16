package doom.tools.trace;

import doom.testkit.trace.TraceComparator;
import doom.testkit.trace.TraceComparison;
import doom.testkit.trace.TraceFormatException;
import doom.tools.Tool;
import doom.tools.demo.CliArgs;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * {@code trace-diff <ref> <ours> [--context N]}: compares two trace files (plain or gzip) on the gated keys
 * (contracts §6.7) and prints the first divergence: tic, key, player field name, both values and the surrounding lines.
 * Exit status 0 = identical, 1 = divergent, 2 = error.
 */
public final class TraceDiffTool implements Tool {

    @Override
    public String name() {
        return "trace-diff";
    }

    @Override
    public String summary() {
        return "Compare two trace files and report the first divergence (exit 0 same, 1 differs, 2 error)";
    }

    @Override
    public int run(String[] args) {
        return run(args, System.out, System.err);
    }

    /** Testable form. */
    public int run(String[] args, PrintStream out, PrintStream err) {
        CliArgs cli;
        try {
            cli = CliArgs.parse(args, Set.of("context"), Set.of("help"));
        } catch (IllegalArgumentException e) {
            err.println("trace-diff: " + e.getMessage());
            usage(err);
            return 2;
        }
        if (cli.flag("help") || cli.positional().size() != 2) {
            usage(err);
            return cli.flag("help") ? 0 : 2;
        }
        int context;
        try {
            context = cli.getInt("context", TraceComparator.DEFAULT_CONTEXT);
            if (context < 0) {
                throw new IllegalArgumentException("--context must be >= 0");
            }
        } catch (IllegalArgumentException e) {
            err.println("trace-diff: " + e.getMessage());
            return 2;
        }
        Path ref = Path.of(cli.positional().get(0));
        Path ours = Path.of(cli.positional().get(1));
        for (Path p : new Path[] {ref, ours}) {
            if (!Files.isRegularFile(p)) {
                err.println("trace-diff: not a file: " + p);
                return 2;
            }
        }
        try {
            TraceComparison c = new TraceComparator(context).compare(ref, ours);
            out.print(c.describe());
            return c.identical() ? 0 : 1;
        } catch (TraceFormatException e) {
            err.println("trace-diff: malformed trace: " + e.getMessage());
            return 2;
        } catch (IOException | UncheckedIOException e) {
            err.println("trace-diff: " + e.getMessage());
            return 2;
        }
    }

    private static void usage(PrintStream err) {
        err.println("usage: trace-diff <reference.trace[.gz]> <ours.trace[.gz]> [--context N]");
        err.println("  Compares every key except 'm' line by line. Exit status: 0 identical, 1 divergent, 2 error.");
    }
}
