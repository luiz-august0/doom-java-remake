package doom.wad.iwad;

import doom.wad.WadFormatException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Finds the IWAD (spec 05 §2). Search order: {@code -iwad <file>} → {@code DOOMWADDIR} → the directories of
 * {@code DOOMWADPATH} → current directory → application data directory → bundled directories (the {@code iwads}
 * folder shipped with the application, and {@code /opt/doom-java/iwads} in the Docker image; ADR-023). In each directory the names of
 * {@link #IWAD_NAMES} are tried in order; the first directory that holds any of them wins (Chocolate Doom's
 * {@code D_FindIWAD}: directories outer loop, names inner loop).
 *
 * <p>As in Chocolate Doom, file names match case-insensitively ({@code M_FileCaseExists}: as given, lower case,
 * upper case, upper-case base with lower-case extension, capitalised), and an entry of {@code DOOMWADDIR} or
 * {@code DOOMWADPATH} may name an IWAD file directly instead of a directory.
 *
 * <p>Instances are immutable; {@link #fromEnvironment} reads the environment once.
 */
public final class IwadLocator {

    /** IWAD file names in search order. */
    public static final List<String> IWAD_NAMES = List.of(
            "doom2.wad", "plutonia.wad", "tnt.wad", "doom.wad", "doom1.wad", "freedoom2.wad", "freedoom1.wad",
            "freedm.wad");

    /** Where the Docker image keeps its IWADs. */
    public static final String DOCKER_IWAD_DIR = "/opt/doom-java/iwads";

    private final String iwadArgument;
    private final List<String> directories;

    /**
     * @param iwadArgument value of {@code -iwad}, or null
     * @param doomWadDir   value of {@code DOOMWADDIR}, or null
     * @param doomWadPath  value of {@code DOOMWADPATH} (entries separated by {@link File#pathSeparator}), or null
     * @param currentDir   the current directory, or null
     * @param appDataDir   the application data directory (spec 07), or null
     */
    public IwadLocator(String iwadArgument, String doomWadDir, String doomWadPath, Path currentDir, Path appDataDir) {
        this(iwadArgument, doomWadDir, doomWadPath, currentDir, appDataDir, List.of());
    }

    /**
     * @param bundledDirs  directories shipped with the application, searched last (see {@link #bundledDirectories})
     * @see #IwadLocator(String, String, String, Path, Path)
     */
    public IwadLocator(String iwadArgument, String doomWadDir, String doomWadPath, Path currentDir, Path appDataDir,
            List<Path> bundledDirs) {
        this.iwadArgument = iwadArgument;
        List<String> dirs = new ArrayList<>();
        if (doomWadDir != null && !doomWadDir.isEmpty()) {
            dirs.add(doomWadDir);
        }
        if (doomWadPath != null && !doomWadPath.isEmpty()) {
            for (String entry : doomWadPath.split(Pattern.quote(File.pathSeparator), -1)) {
                if (!entry.isEmpty()) {
                    dirs.add(entry);
                }
            }
        }
        if (currentDir != null) {
            dirs.add(currentDir.toString());
        }
        if (appDataDir != null) {
            dirs.add(appDataDir.toString());
        }
        for (Path bundled : bundledDirs) {
            dirs.add(bundled.toString());
        }
        this.directories = List.copyOf(dirs);
    }

    /** A locator over the process environment ({@code DOOMWADDIR}, {@code DOOMWADPATH}, {@code user.dir}). */
    public static IwadLocator fromEnvironment(String iwadArgument, Path appDataDir) {
        String cwd = System.getProperty("user.dir");
        return new IwadLocator(iwadArgument, System.getenv("DOOMWADDIR"), System.getenv("DOOMWADPATH"),
                cwd == null ? null : Path.of(cwd), appDataDir,
                bundledDirectories(System.getProperty("jpackage.app-path"), codeSourcePath(),
                        System.getenv("DOOM_APP_DIR")));
    }

    /**
     * The directories where an application package keeps its IWADs, in search order.
     *
     * <ul>
     *   <li>{@code DOOM_APP_DIR/iwads} and {@code DOOM_APP_DIR/.iwads} (a source checkout), when the variable is set;
     *   <li>from the jpackage launcher ({@code jpackage.app-path}, e.g. {@code App/bin/App}, {@code App/App.exe} or
     *       {@code App.app/Contents/MacOS/App}): an {@code iwads} folder next to the launcher, in its parent, and in
     *       {@code Contents/Resources} on macOS;
     *   <li>from the running jar: an {@code iwads} folder next to it and in its parent;
     *   <li>{@link #DOCKER_IWAD_DIR}.
     * </ul>
     *
     * @param jpackageAppPath value of the {@code jpackage.app-path} property, or null
     * @param codeSource      the jar or class directory the application runs from, or null
     * @param appDirEnv       value of {@code DOOM_APP_DIR}, or null
     */
    public static List<Path> bundledDirectories(String jpackageAppPath, Path codeSource, String appDirEnv) {
        List<Path> dirs = new ArrayList<>();
        if (appDirEnv != null && !appDirEnv.isEmpty()) {
            Path base = toPath(appDirEnv);
            if (base != null) {
                dirs.add(base.resolve("iwads"));
                dirs.add(base.resolve(".iwads")); // a source checkout (bin/doom-java)
            }
        }
        if (jpackageAppPath != null && !jpackageAppPath.isEmpty()) {
            Path launcher = toPath(jpackageAppPath);
            Path dir = launcher == null ? null : launcher.getParent();
            if (dir != null) {
                dirs.add(dir.resolve("iwads"));
                Path parent = dir.getParent();
                if (parent != null) {
                    dirs.add(parent.resolve("iwads"));
                    dirs.add(parent.resolve("Resources").resolve("iwads"));
                }
            }
        }
        if (codeSource != null) {
            Path dir = Files.isDirectory(codeSource) ? codeSource : codeSource.getParent();
            if (dir != null) {
                dirs.add(dir.resolve("iwads"));
                Path parent = dir.getParent();
                if (parent != null) {
                    dirs.add(parent.resolve("iwads"));
                }
            }
        }
        dirs.add(Path.of(DOCKER_IWAD_DIR));
        return dirs;
    }

    /** The jar (or class directory) this class was loaded from, or null when it has no file location. */
    private static Path codeSourcePath() {
        try {
            var source = IwadLocator.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return null;
            }
            return Path.of(source.getLocation().toURI());
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    /** The directories searched, in order (entries may also name IWAD files directly). */
    public List<String> searchPath() {
        return directories;
    }

    /**
     * Finds the IWAD: the {@code -iwad} file when given (looked up as a path first, then by name in the search
     * path), else the first known IWAD name found in the search path.
     */
    public Optional<Path> locate() {
        if (iwadArgument != null) {
            return findWadByName(iwadArgument);
        }
        for (String dir : directories) {
            for (String name : IWAD_NAMES) {
                Path p = checkDirectoryHasIwad(dir, name);
                if (p != null) {
                    return Optional.of(p);
                }
            }
        }
        return Optional.empty();
    }

    /** Like {@link #locate()}, but fails with the original messages ({@code D_FindIWAD}, {@code D_DoomMain}). */
    public Path require() {
        Optional<Path> p = locate();
        if (p.isPresent()) {
            return p.get();
        }
        if (iwadArgument != null) {
            throw new WadFormatException("IWAD file '" + iwadArgument + "' not found!");
        }
        throw new WadFormatException("Game mode indeterminate.  No IWAD file was found.  Try\n"
                + "specifying one with the '-iwad' command line parameter.\n");
    }

    /**
     * {@code D_FindWADByName}: the file itself when it exists (case-insensitively), else the first directory of the
     * search path that contains a file with that name. Used for {@code -iwad}, {@code -file} and {@code -merge}.
     */
    public Optional<Path> findWadByName(String name) {
        Path direct = fileCaseExists(name);
        if (direct != null) {
            return Optional.of(direct);
        }
        for (String dir : directories) {
            Path asFile = dirIsFile(dir, name);
            if (asFile != null) {
                return Optional.of(asFile);
            }
            Path p = fileCaseExists(join(dir, name));
            if (p != null) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /** {@code CheckDirectoryHasIWAD}. */
    private static Path checkDirectoryHasIwad(String dir, String iwadName) {
        Path asFile = dirIsFile(dir, iwadName);
        if (asFile != null) {
            return asFile;
        }
        return fileCaseExists(join(dir, iwadName));
    }

    /** {@code DirIsFile}: the "directory" names the wanted file directly. */
    private static Path dirIsFile(String dir, String fileName) {
        if (!dir.contains(File.separator) && !dir.contains("/")) {
            return null;
        }
        if (!baseName(dir).equalsIgnoreCase(fileName)) {
            return null;
        }
        return fileCaseExists(dir);
    }

    private static String join(String dir, String name) {
        return dir.equals(".") ? name : dir + File.separator + name;
    }

    private static String baseName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf(File.separatorChar));
        return path.substring(slash + 1);
    }

    private static Path toPath(String s) {
        try {
            return Path.of(s);
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /** {@code M_FileCaseExists}: tries the file name as given and in the usual case variants. */
    static Path fileCaseExists(String path) {
        Path p = toPath(path);
        if (p == null) {
            return null;
        }
        if (Files.isRegularFile(p)) {
            return p;
        }
        Path parent = p.getParent();
        Path fileName = p.getFileName();
        if (fileName == null) {
            return null;
        }
        String name = fileName.toString();
        String lower = name.toLowerCase(Locale.ROOT);
        String upper = name.toUpperCase(Locale.ROOT);
        List<String> variants = new ArrayList<>(4);
        variants.add(lower);
        variants.add(upper);
        int dot = upper.lastIndexOf('.');
        if (dot > 0) {
            variants.add(upper.substring(0, dot + 1) + upper.substring(dot + 1).toLowerCase(Locale.ROOT));
        }
        if (name.length() > 1) {
            variants.add(upper.charAt(0) + lower.substring(1));
        }
        for (String v : variants) {
            Path candidate = parent == null ? Path.of(v) : parent.resolve(v);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
