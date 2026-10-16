package doom.codegen;

import java.util.Arrays;

/**
 * {@code doom-codegen gen-tables <linuxdoom dir> <output dir>} and {@code doom-codegen gen-defs <linuxdoom dir> <output dir>}.
 *
 * <p><b>Stub (Wave 0):</b> {@code gen-tables} by W1-B, {@code gen-defs} by W1-C.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: doom-codegen gen-tables|gen-defs <args...>");
            System.exit(2);
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        int status = switch (args[0]) {
            case "gen-tables" -> doom.codegen.tables.GenTables.run(rest);
            case "gen-defs" -> doom.codegen.defs.GenDefs.run(rest);
            default -> {
                System.err.println("Unknown generator: " + args[0]);
                yield 2;
            }
        };
        System.exit(status);
    }
}
