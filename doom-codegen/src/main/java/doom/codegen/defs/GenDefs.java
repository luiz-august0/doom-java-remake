package doom.codegen.defs;

import java.io.IOException;
import java.nio.file.Path;

/**
 * {@code gen-defs <linuxdoom dir> <out dir> [<chocolate-doom dir>]}.
 *
 * <p>Reads the original definition tables (see {@link DefsParser}) and writes, below {@code <out dir>}:
 * <ul>
 *   <li>{@code main/java/doom/game/data/*.java}: the constant classes, the {@code StateAction} enum and the builder
 *       DSL grouped by actor and weapon (the bootstrap of ADR-014: after this run the code is edited by hand);</li>
 *   <li>{@code test/resources/doom/game/data/original-defs.txt}: the snapshot of the original tables that
 *       {@code DefsEquivalenceTest} compares against.</li>
 * </ul>
 * With {@code <out dir>} = {@code doom-game/src} the files land where the module expects them. Chocolate Doom's
 * source tree defaults to {@code $CHOCOLATE_SRC} or {@code $DOOM_HOME/tools/oracle/work/chocolate-doom}.
 */
public final class GenDefs {

    private GenDefs() {
    }

    public static int run(String[] args) throws IOException {
        if (args.length < 2 || args.length > 3) {
            System.err.println("usage: gen-defs <linuxdoom dir> <out dir> [<chocolate-doom dir>]");
            return 2;
        }
        Path linuxDoom = Path.of(args[0]);
        Path out = Path.of(args[1]);
        Path chocolate = args.length == 3 ? Path.of(args[2]) : defaultChocolate();
        OriginalDefs defs = DefsParser.parse(linuxDoom, chocolate);
        new DefsWriter(defs, out).write();
        System.out.println("gen-defs: " + defs.states().size() + " states, " + defs.things().size() + " actors, "
                + defs.sounds().size() + " sounds, " + defs.strings().size() + " strings written to " + out);
        return 0;
    }

    private static Path defaultChocolate() {
        String env = System.getenv("CHOCOLATE_SRC");
        if (env != null && !env.isEmpty()) {
            return Path.of(env);
        }
        String home = System.getenv("DOOM_HOME");
        if (home != null && !home.isEmpty()) {
            return Path.of(home, "tools/oracle/work/chocolate-doom");
        }
        return Path.of("tools/oracle/work/chocolate-doom");
    }
}
