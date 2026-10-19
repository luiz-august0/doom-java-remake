package doom.app;

import doom.engine.DoomEngine;
import doom.engine.EngineParams;
import doom.engine.cli.CommandLine;
import doom.engine.cli.LaunchOptions;
import doom.engine.cli.LooseFiles;
import doom.engine.config.Config;
import doom.engine.config.DataDirs;
import doom.engine.config.Settings;
import doom.engine.config.VanillaCfgImport;
import doom.math.DoomFatalException;
import doom.wad.WadFormatException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Starts the game: command line, configuration, logging, then the engine. The video and audio backends are found with
 * {@link java.util.ServiceLoader}. Fatal game errors ({@link DoomFatalException}) are printed and end the process with
 * status 1.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        int status = run(args);
        System.exit(status);
    }

    /** Runs the game and returns the process exit status. */
    public static int run(String[] args) {
        LaunchOptions options = LaunchOptions.parse(CommandLine.of(LooseFiles.expand(List.of(args))));
        if (options.help()) {
            System.out.print(LaunchOptions.usage());
            return 0;
        }

        DataDirs dirs = DataDirs.system();
        Path configFile = options.config() != null ? Path.of(options.config()) : dirs.configFile();
        Config config = Config.load(configFile);
        configureLogging(options, config, dirs);
        System.Logger log = System.getLogger(Main.class.getName());

        if (options.importCfg() != null) {
            try {
                String text = Files.readString(Path.of(options.importCfg()), StandardCharsets.ISO_8859_1);
                for (String m : VanillaCfgImport.apply(text, config)) {
                    log.log(System.Logger.Level.INFO, "importcfg: {0}", m);
                }
                config.save(configFile);
            } catch (IOException e) {
                System.err.println("Cannot import " + options.importCfg() + ": " + e.getMessage());
                return 1;
            }
        }

        EngineParams params = new EngineParams(options).config(config).dirs(dirs).saveConfigOnExit(!options.headless());
        try (DoomEngine engine = new DoomEngine(params)) {
            int status = engine.run();
            engine.printTimedemoResult();
            return status;
        } catch (DoomFatalException | WadFormatException e) {
            System.err.println("Error: " + e.getMessage());
            log.log(System.Logger.Level.ERROR, "fatal error", e);
            return 1;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e);
            log.log(System.Logger.Level.ERROR, "unexpected error", e);
            return 1;
        }
    }

    private static void configureLogging(LaunchOptions options, Config config, DataDirs dirs) {
        System.Logger.Level level = levelOf(options.logLevel() != null ? options.logLevel()
                : config.get(Settings.LOG_LEVEL));
        if (System.LoggerFinder.getLoggerFinder() instanceof ConsoleLoggerFinder finder) {
            // headless runs are tools and tests: no log file
            finder.configure(level, options.headless() ? null : dirs.logFile(), true);
        }
    }

    static System.Logger.Level levelOf(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "off" -> System.Logger.Level.OFF;
            case "error" -> System.Logger.Level.ERROR;
            case "warning" -> System.Logger.Level.WARNING;
            case "debug" -> System.Logger.Level.DEBUG;
            case "trace" -> System.Logger.Level.TRACE;
            default -> System.Logger.Level.INFO;
        };
    }
}
