package doom.tools.demo;

import doom.testkit.demo.CorpusPlan;
import doom.testkit.demo.Demo;
import doom.testkit.demo.DemoGenerator;
import doom.testkit.demo.DemoManifest;
import doom.testkit.demo.DemoPolicy;
import doom.testkit.demo.DemoSpec;
import doom.testkit.demo.ManifestEntry;
import doom.tools.Tool;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * {@code gen-demo}: generates synthetic version-109 demos.
 *
 * <ul>
 *   <li>one demo: {@code gen-demo --out d.lmp --map E1M1 [--skill 2] [--players 1] [--deathmatch 0] [--respawn]
 *       [--fast] [--nomonsters] [--tics 2000] [--seed 1] [--policy random-walk|movement-only]};</li>
 *   <li>the whole corpus: {@code gen-demo --corpus <dir>} writes {@code manifest.tsv} and every {@code .lmp};</li>
 *   <li>from a manifest: {@code gen-demo --manifest <file> --out-dir <dir>};</li>
 *   <li>{@code --check} (with {@code --corpus} or {@code --manifest}) compares instead of writing; exit 1 on a difference.</li>
 * </ul>
 */
public final class GenDemoTool implements Tool {

    @Override
    public String name() {
        return "gen-demo";
    }

    @Override
    public String summary() {
        return "Generate synthetic demos (version 109): one demo, a manifest, or the whole corpus";
    }

    @Override
    public int run(String[] args) {
        return run(args, System.out, System.err);
    }

    /** Testable form. */
    public int run(String[] args, PrintStream out, PrintStream err) {
        CliArgs cli;
        try {
            cli = CliArgs.parse(args,
                    Set.of("out", "map", "skill", "players", "deathmatch", "tics", "seed", "policy", "corpus", "manifest",
                            "out-dir"),
                    Set.of("respawn", "fast", "nomonsters", "check", "help"));
        } catch (IllegalArgumentException e) {
            err.println("gen-demo: " + e.getMessage());
            usage(err);
            return 2;
        }
        if (cli.flag("help")) {
            usage(err);
            return 0;
        }
        try {
            if (cli.has("corpus")) {
                Path dir = Path.of(cli.get("corpus", ""));
                return batch(CorpusPlan.build(), dir, dir.resolve("manifest.tsv"), true, cli.flag("check"), out, err);
            }
            if (cli.has("manifest")) {
                Path manifest = Path.of(cli.get("manifest", ""));
                Path dir = Path.of(cli.get("out-dir", manifest.toAbsolutePath().getParent().toString()));
                return batch(DemoManifest.read(manifest), dir, manifest, false, cli.flag("check"), out, err);
            }
            if (!cli.has("out") || !cli.has("map")) {
                usage(err);
                return 2;
            }
            DemoSpec spec = new DemoSpec(cli.getInt("skill", 2), cli.get("map", ""), cli.getInt("players", 1),
                    cli.getInt("deathmatch", 0), cli.flag("respawn"), cli.flag("fast"), cli.flag("nomonsters"),
                    cli.getInt("tics", 2000), cli.getLong("seed", 1), DemoPolicy.parse(cli.get("policy", "random-walk")));
            Path file = Path.of(cli.get("out", ""));
            Demo demo = DemoGenerator.generate(spec);
            write(file, demo.toBytes());
            out.printf("%s: %d tics, %d player(s), %d bytes%n", file, demo.tics(), spec.players(), Files.size(file));
            return 0;
        } catch (IllegalArgumentException | IOException e) {
            err.println("gen-demo: " + e.getMessage());
            return 2;
        }
    }

    private static int batch(List<ManifestEntry> entries, Path dir, Path manifest, boolean writeManifest, boolean check,
                             PrintStream out, PrintStream err) throws IOException {
        int different = 0;
        for (ManifestEntry e : entries) {
            byte[] bytes = DemoGenerator.generate(e.spec()).toBytes();
            Path file = dir.resolve(e.fileName());
            if (check) {
                if (!Files.isRegularFile(file) || !Arrays.equals(Files.readAllBytes(file), bytes)) {
                    err.println("differs or missing: " + file);
                    different++;
                }
            } else {
                write(file, bytes);
            }
        }
        if (check) {
            if (writeManifest && (!Files.isRegularFile(manifest) || !DemoManifest.read(manifest).equals(entries))) {
                err.println("manifest differs from the corpus plan: " + manifest);
                different++;
            }
            out.printf("checked %d demos: %d differ%n", entries.size(), different);
            return different == 0 ? 0 : 1;
        }
        if (writeManifest) {
            DemoManifest.write(manifest, entries);
        }
        out.printf("wrote %d demos to %s%n", entries.size(), dir);
        return 0;
    }

    private static void write(Path file, byte[] bytes) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(file, bytes);
    }

    private static void usage(PrintStream err) {
        err.println("usage: gen-demo --out <file.lmp> --map <E1M1|MAP01> [--skill 0..4] [--players 1..4] [--deathmatch 0..2]");
        err.println("                [--respawn] [--fast] [--nomonsters] [--tics N] [--seed N] [--policy random-walk|movement-only]");
        err.println("       gen-demo --corpus <dir> [--check]            (writes manifest.tsv and every demo of the corpus)");
        err.println("       gen-demo --manifest <file> [--out-dir <dir>] [--check]");
    }
}
