package doom.testkit;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Finds the test IWADs in {@code DOOM_IWAD_DIR} (contracts §5). Tests use {@link #assumeAvailable} so they are
 * <em>skipped</em>, not failed, when a WAD is missing.
 *
 * <p>The JUnit dependency is optional: {@link #assume} reaches {@code org.junit.jupiter.api.Assumptions} reflectively,
 * so doom-testkit itself carries no compile-time dependency on JUnit. Without JUnit on the class path it throws
 * {@link IllegalStateException} instead.
 */
public final class Iwads {

    /** Environment variable with the directory that holds the IWADs. */
    public static final String ENV = "DOOM_IWAD_DIR";
    /** Shareware Doom 1.9. */
    public static final String DOOM1 = "doom1.wad";
    /** Freedoom: Phase 1 (an Ultimate Doom replacement). */
    public static final String FREEDOOM1 = "freedoom1.wad";
    /** Freedoom: Phase 2 (a Doom II replacement). */
    public static final String FREEDOOM2 = "freedoom2.wad";
    /** FreeDM (deathmatch only). */
    public static final String FREEDM = "freedm.wad";
    /** The four IWADs the test corpus uses. */
    public static final List<String> ALL = List.of(DOOM1, FREEDOOM1, FREEDOOM2, FREEDM);

    private Iwads() {
    }

    /** The IWAD directory from {@code DOOM_IWAD_DIR}, when set and an existing directory. */
    public static Optional<Path> directory() {
        return directory(System.getenv(ENV));
    }

    /** The IWAD directory named by {@code value}, when non-blank and an existing directory (testable form). */
    static Optional<Path> directory(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        Path dir = Path.of(value.strip());
        return Files.isDirectory(dir) ? Optional.of(dir) : Optional.empty();
    }

    /** The IWAD file {@code fileName} (case-insensitive) in the IWAD directory, when it exists. */
    public static Optional<Path> find(String fileName) {
        return directory().flatMap(dir -> find(dir, fileName));
    }

    /** The IWAD file {@code fileName} in {@code dir} (exact name first, then a case-insensitive scan). */
    public static Optional<Path> find(Path dir, String fileName) {
        Path exact = dir.resolve(fileName);
        if (Files.isRegularFile(exact)) {
            return Optional.of(exact);
        }
        try (var stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                if (p.getFileName().toString().equalsIgnoreCase(fileName) && Files.isRegularFile(p)) {
                    return Optional.of(p);
                }
            }
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /** Whether {@code fileName} exists in the IWAD directory. */
    public static boolean isAvailable(String fileName) {
        return find(fileName).isPresent();
    }

    /** The path of {@code fileName}; throws when it is not available (use {@link #assumeAvailable} in tests). */
    public static Path require(String fileName) {
        return find(fileName).orElseThrow(() -> new IllegalStateException(missingMessage(fileName)));
    }

    /** The message used when an IWAD is missing. */
    public static String missingMessage(String fileName) {
        return directory().isEmpty()
                ? ENV + " is not set (or is not a directory): " + fileName + " is not available"
                : fileName + " not found in " + System.getenv(ENV);
    }

    /**
     * Skips the calling test (a JUnit assumption failure) unless {@code fileName} is available.
     *
     * @return the IWAD path
     */
    public static Path assumeAvailable(String fileName) {
        Optional<Path> p = find(fileName);
        assume(p.isPresent(), missingMessage(fileName));
        return p.orElseThrow();
    }

    /** Skips the calling test unless the IWAD directory is set. */
    public static Path assumeDirectory() {
        Optional<Path> dir = directory();
        assume(dir.isPresent(), ENV + " is not set (or is not a directory)");
        return dir.orElseThrow();
    }

    /**
     * {@code Assumptions.assumeTrue(condition, message)} of JUnit 5, reached reflectively.
     *
     * @throws IllegalStateException when JUnit Jupiter is not on the class path and the condition is false
     */
    public static void assume(boolean condition, String message) {
        if (condition) {
            return;
        }
        MethodHandle handle;
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class<?> assumptions = Class.forName("org.junit.jupiter.api.Assumptions", true,
                    loader != null ? loader : Iwads.class.getClassLoader());
            handle = MethodHandles.publicLookup().findStatic(assumptions, "assumeTrue",
                    MethodType.methodType(void.class, boolean.class, String.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("Assumption failed (JUnit not available): " + message, e);
        }
        try {
            handle.invoke(false, message);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
        throw new IllegalStateException("Assumption failed: " + message);
    }
}
