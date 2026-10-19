package doom.engine.cli;

import doom.wad.iwad.GameVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Everything the command line says, interpreted (spec 07 section 5). Values that depend on the IWAD (the meaning of
 * {@code -warp}) are kept raw. {@link #problems} lists unknown or malformed parameters; they are warnings, not errors.
 *
 * @param iwad        {@code -iwad}, or null
 * @param files       {@code -file} values (PWADs, .deh files)
 * @param merge       {@code -merge} values
 * @param deh         {@code -deh} values
 * @param noDeh       {@code -nodeh}
 * @param warp        {@code -warp} values (one number for DOOM II, episode and map for DOOM 1), empty when absent
 * @param skill       {@code -skill}: 0 (baby) to 4 (nightmare), or -1 when absent
 * @param episode     {@code -episode}, or -1
 * @param noMonsters  {@code -nomonsters}
 * @param respawn     {@code -respawn}
 * @param fast        {@code -fast}
 * @param deathmatch  0, or 1 for {@code -deathmatch}, 2 for {@code -altdeath}
 * @param playDemo    {@code -playdemo} value (lump name or file), or null
 * @param timeDemo    {@code -timedemo} value, or null
 * @param record      {@code -record} value, or null
 * @param loadGame    {@code -loadgame} slot, or -1
 * @param gameVersion {@code -gameversion}, or null
 * @param config      {@code -config}, or null
 * @param importCfg   {@code -importcfg}, or null
 * @param window      {@code -window}
 * @param fullscreen  {@code -fullscreen}
 * @param width       {@code -width}, or 0
 * @param height      {@code -height}, or 0
 * @param vanilla     {@code -vanilla}
 * @param headless    {@code -headless}
 * @param trace       {@code -trace} file, or null
 * @param traceVerbose {@code -traceverbose} file, or null
 * @param traceFrom   {@code -tracefrom}, or 0
 * @param traceTo     {@code -traceto}, or {@link Integer#MAX_VALUE}
 * @param maxTics     {@code -maxtics}: stop after this many game tics, or -1
 * @param noSound     {@code -nosound}
 * @param noMusic     {@code -nomusic}
 * @param logLevel    {@code -loglevel}, or null
 * @param help        {@code -help} / {@code --help} / {@code -h}
 * @param problems    messages about unknown or malformed parameters
 */
public record LaunchOptions(String iwad, List<String> files, List<String> merge, List<String> deh, boolean noDeh,
                            List<Integer> warp, int skill, int episode, boolean noMonsters, boolean respawn,
                            boolean fast, int deathmatch, String playDemo, String timeDemo, String record,
                            int loadGame, GameVersion gameVersion, String config, String importCfg, boolean window,
                            boolean fullscreen, int width, int height, boolean vanilla, boolean headless,
                            String trace, String traceVerbose, int traceFrom, int traceTo, int maxTics,
                            boolean noSound, boolean noMusic, String logLevel, boolean help,
                            List<String> problems) {

    private static final Set<String> KNOWN = Set.of("-iwad", "-file", "-merge", "-deh", "-nodeh", "-warp", "-skill",
            "-episode", "-nomonsters", "-respawn", "-fast", "-deathmatch", "-altdeath", "-playdemo", "-timedemo",
            "-record", "-loadgame", "-gameversion", "-config", "-importcfg", "-window", "-fullscreen", "-width",
            "-height", "-vanilla", "-headless", "-trace", "-traceverbose", "-tracefrom", "-traceto", "-maxtics",
            "-nosound", "-nomusic", "-loglevel", "-help", "--help", "-h", "-turbo", "-devparm", "-nodraw");

    /** Interprets a command line. */
    public static LaunchOptions parse(CommandLine cl) {
        List<String> problems = new ArrayList<>();
        for (String p : cl.parameters()) {
            if (!KNOWN.contains(p.toLowerCase(Locale.ROOT))) {
                problems.add("unknown parameter " + p);
            }
        }

        List<Integer> warp = new ArrayList<>();
        for (String w : cl.values("-warp")) {
            // vanilla reads single digits for DOOM 1 ("-warp 1 3" or "-warp 13"); accept both forms
            try {
                warp.add(Integer.parseInt(w.strip()));
            } catch (NumberFormatException e) {
                problems.add("-warp needs numbers, found '" + w + "'");
                break;
            }
        }
        if (cl.has("-warp") && warp.isEmpty()) {
            problems.add("-warp needs a map");
        }

        int skill = -1;
        if (cl.has("-skill")) {
            int s = cl.intValue("-skill", 0);
            if (s >= 1 && s <= 5) {
                skill = s - 1;
            } else {
                problems.add("-skill needs a number from 1 to 5");
            }
        }

        GameVersion version = null;
        if (cl.has("-gameversion")) {
            String v = cl.value("-gameversion");
            version = v == null ? null : GameVersion.fromCliName(v);
            if (version == null) {
                problems.add("-gameversion must be one of 1.9, ultimate, final, final2");
            }
        }

        int deathmatch = cl.has("-altdeath") ? 2 : cl.has("-deathmatch") ? 1 : 0;
        boolean help = cl.has("-help") || cl.has("--help") || cl.has("-h")
                || cl.args().contains("--help") || cl.args().contains("-h");

        return new LaunchOptions(cl.value("-iwad"), cl.values("-file"), cl.values("-merge"), cl.values("-deh"),
                cl.has("-nodeh"), List.copyOf(warp), skill, cl.has("-episode") ? cl.intValue("-episode", -1) : -1,
                cl.has("-nomonsters"), cl.has("-respawn"), cl.has("-fast"), deathmatch, cl.value("-playdemo"),
                cl.value("-timedemo"), cl.value("-record"), cl.has("-loadgame") ? cl.intValue("-loadgame", -1) : -1,
                version, cl.value("-config"), cl.value("-importcfg"), cl.has("-window"), cl.has("-fullscreen"),
                cl.intValue("-width", 0), cl.intValue("-height", 0), cl.has("-vanilla"), cl.has("-headless"),
                cl.value("-trace"), cl.value("-traceverbose"), cl.intValue("-tracefrom", 0),
                cl.intValue("-traceto", Integer.MAX_VALUE), cl.intValue("-maxtics", -1), cl.has("-nosound"),
                cl.has("-nomusic"), cl.value("-loglevel"), help, List.copyOf(problems));
    }

    /** Convenience: parse raw arguments. */
    public static LaunchOptions parse(String... args) {
        return parse(CommandLine.of(args));
    }

    /** Whether a demo is played from the command line. */
    public boolean demoRequested() {
        return playDemo != null || timeDemo != null;
    }

    /** The usage text for {@code -help}. */
    public static String usage() {
        return """
                usage: doom-java [files...] [options]

                  files                    .wad (IWAD → -iwad, PWAD → -merge), .deh/.bex (-deh), .lmp (-playdemo)

                  -iwad <file>             IWAD to use (otherwise searched: DOOMWADDIR, DOOMWADPATH, ., data directory)
                  -file <files...>         PWADs (and .deh patches) to load
                  -merge <files...>        PWADs whose sprites and flats are merged
                  -deh <files...>          DEHACKED patches      -nodeh   do not apply the IWAD's own patch
                  -warp <e m | mm>         start on a map        -skill <1-5>   -episode <n>
                  -nomonsters  -respawn  -fast  -deathmatch  -altdeath
                  -playdemo <file|lump>    play a demo and quit
                  -timedemo <file|lump>    play a demo as fast as possible and print tics/time/FPS
                  -record <name>           record a demo         -loadgame <slot>
                  -gameversion <1.9|ultimate|final|final2>
                  -config <file>           configuration file    -importcfg <default.cfg>
                  -window  -fullscreen  -width <n>  -height <n>  -vanilla
                  -headless                no window, no audio (tests, CI)
                  -nosound  -nomusic
                  -trace <file>            write the per-tic trace (.gz is compressed)
                  -traceverbose <file> [-tracefrom <tic> -traceto <tic>]
                  -maxtics <n>             quit after n game tics
                  -loglevel <level>        off, error, warning, info, debug, trace
                """;
    }
}
