package doom.testkit.oracle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Cache of oracle traces of the synthetic demos, by default {@code $DOOM_HOME/tools/oracle/work/cache/traces}
 * (git-ignored). A trace is stored as {@code <name>.<key>.trace.gz} where the key covers the demo bytes, the IWAD and
 * the oracle fingerprint, so changing any of them misses the cache; stale entries of the same demo are removed when
 * a new one is stored.
 */
public final class TraceCache {

    private final Path dir;

    /** A cache in {@code dir}. */
    public TraceCache(Path dir) {
        this.dir = dir;
    }

    /** The default cache: {@link OracleRunner#cacheDirectory()}{@code /traces}. */
    public static TraceCache defaultCache() {
        return new TraceCache(OracleRunner.cacheDirectory().resolve("traces"));
    }

    /** The cache directory. */
    public Path directory() {
        return dir;
    }

    /**
     * The cache key of a run.
     *
     * @param iwadName    IWAD file name
     * @param demoBytes   the demo
     * @param fingerprint {@link OracleRunner#fingerprint()}
     */
    public static String key(String iwadName, byte[] demoBytes, String fingerprint) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(iwadName.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(fingerprint.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(demoBytes);
            return HexFormat.of().formatHex(md.digest(), 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The file a trace of {@code name} with {@code key} is stored in. */
    public Path path(String name, String key) {
        return dir.resolve(name + "." + key + ".trace.gz");
    }

    /** Whether the trace is cached. */
    public boolean contains(String name, String key) {
        return Files.isRegularFile(path(name, key));
    }

    /** Any cached trace of {@code name} whatever its key (the newest one), for use when the oracle is unavailable. */
    public java.util.Optional<Path> findAny(String name) throws IOException {
        if (!Files.isDirectory(dir)) {
            return java.util.Optional.empty();
        }
        String prefix = name + ".";
        Path best = null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, prefix + "*.trace.gz")) {
            for (Path p : stream) {
                String file = p.getFileName().toString();
                String middle = file.substring(prefix.length(), file.length() - ".trace.gz".length());
                if (middle.matches("[0-9a-f]{16}") && (best == null
                        || Files.getLastModifiedTime(p).compareTo(Files.getLastModifiedTime(best)) > 0)) {
                    best = p;
                }
            }
        }
        return java.util.Optional.ofNullable(best);
    }

    /** Removes the cached traces of {@code name} whose key is not {@code keepKey}. */
    public void prune(String name, String keepKey) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        String prefix = name + ".";
        String keep = path(name, keepKey).getFileName().toString();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, prefix + "*.trace.gz")) {
            for (Path p : stream) {
                String file = p.getFileName().toString();
                // "<name>.<16 hex>.trace.gz": do not touch a different demo whose name starts with this one
                String middle = file.substring(prefix.length(), file.length() - ".trace.gz".length());
                if (!file.equals(keep) && middle.matches("[0-9a-f]{16}")) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }
}
