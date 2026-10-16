package doom.it.sync;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Development entry point (not a test): {@code SyncMain <iwad path> <demo lump name | .lmp path> <oracle trace>
 * [<verbose out> <from> <to>]} plays one demo and prints the sync result.
 */
public final class SyncMain {

    private SyncMain() {
    }

    public static void main(String[] args) throws Exception {
        Path iwad = Path.of(args[0]);
        byte[] demo;
        String name;
        if (args[1].toLowerCase(java.util.Locale.ROOT).endsWith(".lmp")) {
            demo = Files.readAllBytes(Path.of(args[1]));
            name = Path.of(args[1]).getFileName().toString().replace(".lmp", "").toUpperCase(java.util.Locale.ROOT);
        } else {
            doom.wad.LumpDirectory lumps = doom.wad.LumpDirectory.open(java.util.List.of(iwad));
            demo = lumps.bytes(lumps.get(args[1]));
            name = args[1];
        }
        Path verbose = args.length > 3 ? Path.of(args[3]) : null;
        int from = args.length > 4 ? Integer.parseInt(args[4]) : 0;
        int to = args.length > 5 ? Integer.parseInt(args[5]) : -1;
        DemoSync.Result r = DemoSync.run(name, iwad, demo, Path.of(args[2]), null, verbose, from, to);
        System.out.println(r.line());
        System.out.println("detail: " + r.detail());
        System.out.println("stubs: " + r.stubHits());
    }
}
