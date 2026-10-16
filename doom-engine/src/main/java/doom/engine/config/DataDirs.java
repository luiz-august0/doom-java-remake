package doom.engine.config;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Per-OS application data directory (spec 07 section 4) and the folders below it.
 *
 * <ul>
 *   <li>macOS: {@code ~/Library/Application Support/doom-java/}</li>
 *   <li>Windows: {@code %APPDATA%\doom-java\}</li>
 *   <li>Linux and others: {@code $XDG_CONFIG_HOME/doom-java/}, falling back to {@code ~/.config/doom-java/}</li>
 * </ul>
 *
 * @param root the data directory
 */
public record DataDirs(Path root) {

    /** Resolves the directory for the running system. */
    public static DataDirs system() {
        return resolve(System.getProperty("os.name", ""), System.getenv(), System.getProperty("user.home", "."));
    }

    /** Resolves the directory from explicit inputs (testable). */
    public static DataDirs resolve(String osName, Map<String, String> env, String home) {
        String os = osName.toLowerCase(Locale.ROOT);
        Path homePath = Path.of(home);
        if (os.contains("mac") || os.contains("darwin")) {
            return new DataDirs(homePath.resolve("Library").resolve("Application Support").resolve("doom-java"));
        }
        if (os.contains("win")) {
            String appData = env.get("APPDATA");
            Path base = appData != null && !appData.isBlank() ? Path.of(appData)
                    : homePath.resolve("AppData").resolve("Roaming");
            return new DataDirs(base.resolve("doom-java"));
        }
        String xdg = env.get("XDG_CONFIG_HOME");
        Path base = xdg != null && !xdg.isBlank() ? Path.of(xdg) : homePath.resolve(".config");
        return new DataDirs(base.resolve("doom-java"));
    }

    /** The default configuration file. */
    public Path configFile() {
        return root.resolve("doom-java.cfg");
    }

    /** Save games of one IWAD (kept separate per IWAD, spec 07 section 4). */
    public Path saveDir(String iwadId) {
        return root.resolve("saves").resolve(iwadId);
    }

    public Path screenshotDir() {
        return root.resolve("screenshots");
    }

    public Path logFile() {
        return root.resolve("logs").resolve("doom-java.log");
    }
}
