package doom.tools.waddump;

import doom.game.api.GameFactory;
import doom.tools.Tool;
import doom.wad.Lump;
import doom.wad.LumpDirectory;
import doom.wad.WadFile;
import doom.wad.WadFormatException;
import doom.wad.demo.Demo;
import doom.wad.demo.DemoHeader;
import doom.wad.iwad.IwadIdentifier;
import doom.wad.iwad.IwadInfo;
import doom.wad.map.MapData;
import doom.wad.map.MapLump;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Lists the lumps of one or more WAD files, identifies an IWAD, summarises its maps and prints its demo headers.
 *
 * <pre>
 * wad-dump &lt;file.wad&gt; [more.wad ...] [--lumps] [--ident] [--maps] [--demos]
 * </pre>
 *
 * Without a section option, {@code --ident --maps --demos} are printed. The first file is the base (IWAD) and the
 * others are loaded over it, last one wins, like {@code -file}.
 */
public final class WadDumpTool implements Tool {

    private static final Pattern MAP_NAME = Pattern.compile("E\\dM\\d|MAP\\d\\d");
    private static final Pattern DEMO_NAME = Pattern.compile("DEMO\\d");

    @Override
    public String name() {
        return "wad-dump";
    }

    @Override
    public String summary() {
        return "List the lumps of a WAD and identify IWADs";
    }

    @Override
    public int run(String[] args) {
        return run(args, System.out, System.err);
    }

    /** Runs the tool with explicit streams (for tests). */
    public int run(String[] args, PrintStream out, PrintStream err) {
        List<Path> files = new ArrayList<>();
        boolean lumps = false;
        boolean ident = false;
        boolean maps = false;
        boolean demos = false;
        for (String a : args) {
            switch (a) {
                case "--lumps" -> lumps = true;
                case "--ident" -> ident = true;
                case "--maps" -> maps = true;
                case "--demos" -> demos = true;
                case "-h", "--help" -> {
                    usage(err);
                    return 0;
                }
                default -> {
                    if (a.startsWith("--")) {
                        err.println("wad-dump: unknown option " + a);
                        usage(err);
                        return 2;
                    }
                    files.add(Path.of(a));
                }
            }
        }
        if (files.isEmpty()) {
            usage(err);
            return 2;
        }
        if (!lumps && !ident && !maps && !demos) {
            ident = maps = demos = true;
        }
        try {
            LumpDirectory dir = LumpDirectory.open(files);
            IwadInfo info = null;
            if (ident || maps) {
                WadFile first = dir.wads().get(0);
                if (first.isIwad()) {
                    try {
                        info = IwadIdentifier.identify(first);
                    } catch (WadFormatException e) {
                        if (ident) {
                            out.println("identification: " + e.getMessage());
                        }
                    }
                } else if (ident) {
                    out.println("identification: " + first.fileName() + " is a " + first.identification()
                            + ", not an IWAD");
                }
            }
            if (ident && info != null) {
                printIdent(out, info);
            }
            if (lumps) {
                printLumps(out, dir);
            }
            if (maps) {
                printMaps(out, dir, info);
            }
            if (demos) {
                printDemos(out, dir);
            }
            return 0;
        } catch (IOException | WadFormatException e) {
            err.println("wad-dump: " + e.getMessage());
            return 1;
        }
    }

    private static void usage(PrintStream err) {
        err.println("usage: wad-dump <file.wad> [more.wad ...] [--lumps] [--ident] [--maps] [--demos]");
    }

    private static void printIdent(PrintStream out, IwadInfo info) {
        out.printf(Locale.ROOT, "iwad: %s%n", info.path());
        out.printf(Locale.ROOT, "  game:    %s%n", info.description());
        out.printf(Locale.ROOT, "  mode:    %s%n", info.mode());
        out.printf(Locale.ROOT, "  mission: %s%n", info.mission());
        out.printf(Locale.ROOT, "  variant: %s%n", info.variant());
        out.printf(Locale.ROOT, "  version: %s%n", info.version());
    }

    private static void printLumps(PrintStream out, LumpDirectory dir) {
        out.printf(Locale.ROOT, "lumps: %d in %d file(s)%n", dir.size(), dir.wads().size());
        out.printf(Locale.ROOT, "  %6s  %-8s  %8s  %9s  %-9s  %s%n", "#", "name", "size", "offset", "section", "file");
        for (int i = 0; i < dir.size(); i++) {
            Lump l = dir.lump(i);
            out.printf(Locale.ROOT, "  %6d  %-8s  %8d  %9d  %-9s  %s%n", l.number(), l.name(), l.size(), l.offset(),
                    l.namespace().name().toLowerCase(Locale.ROOT), l.wad().fileName());
        }
    }

    private static void printMaps(PrintStream out, LumpDirectory dir, IwadInfo info) {
        out.println("maps:");
        out.printf(Locale.ROOT, "  %-6s %6s %6s %6s %6s %6s %6s %6s %6s  %s%n", "name", "things", "lines", "sides", "verts",
                "segs", "ssect", "nodes", "sects", "geometry");
        int found = 0;
        for (int i = 0; i < dir.size(); i++) {
            String name = dir.name(i);
            if (!MAP_NAME.matcher(name).matches() || !isMapMarker(dir, i)) {
                continue;
            }
            found++;
            try {
                MapData m = MapData.load(dir, name);
                String geometry = "-";
                if (info != null) {
                    try {
                        GameFactory.loadLevelGeometry(dir, info, name);
                        geometry = "ok";
                    } catch (RuntimeException e) {
                        geometry = "FAILED: " + e.getMessage();
                    }
                }
                out.printf(Locale.ROOT, "  %-6s %6d %6d %6d %6d %6d %6d %6d %6d  %s%n", name, m.things().length,
                        m.linedefs().length, m.sidedefs().length, m.vertexes().length, m.segs().length,
                        m.subsectors().length, m.nodes().length, m.sectors().length, geometry);
            } catch (WadFormatException e) {
                out.printf(Locale.ROOT, "  %-6s %s%n", name, e.getMessage());
            }
        }
        if (found == 0) {
            out.println("  (none)");
        }
    }

    /** True when the ten map lumps follow lump {@code i}. */
    private static boolean isMapMarker(LumpDirectory dir, int i) {
        for (MapLump l : MapLump.values()) {
            int n = i + l.offsetFromMarker();
            if (n >= dir.size() || !dir.name(n).equals(l.name())) {
                return false;
            }
        }
        return true;
    }

    private static void printDemos(PrintStream out, LumpDirectory dir) {
        out.println("demos:");
        int found = 0;
        for (int i = 0; i < dir.size(); i++) {
            String name = dir.name(i);
            if (!DEMO_NAME.matcher(name).matches()) {
                continue;
            }
            found++;
            try {
                Demo d = Demo.read(dir.bytes(i), name);
                DemoHeader h = d.header();
                StringBuilder players = new StringBuilder();
                for (int p = 0; p < 4; p++) {
                    if (h.playerInGame()[p]) {
                        players.append(p + 1);
                    }
                }
                out.printf(Locale.ROOT, "  %-6s version %d  skill %d  episode %d  map %d  deathmatch %d  respawn %d  fast %d"
                                + "  nomonsters %d  console %d  players %s  tics %d (%.1f s)%s%n",
                        name, h.version(), h.skill(), h.episode(), h.map(), h.deathmatch(), h.respawn(), h.fast(),
                        h.noMonsters(), h.consolePlayer() + 1, players, d.tics(), d.tics() / 35.0,
                        d.hasEndMarker() ? "" : "  (no end marker)");
            } catch (WadFormatException e) {
                out.printf(Locale.ROOT, "  %-6s %s%n", name, e.getMessage());
            }
        }
        if (found == 0) {
            out.println("  (none)");
        }
    }
}
