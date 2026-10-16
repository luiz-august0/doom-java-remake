package doom.it.audio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Builds and runs the C reference harness {@code tools/opl3-vectors/music} (Chocolate Doom's original i_oplmusic.c,
 * mus2mid.c, midifile.c and opl3.c around a stubbed OPL layer). Needs {@code clang} and the Chocolate Doom sources of
 * the oracle checkout; callers skip their test when {@link #available()} is false.
 */
final class MusicRefTool {

    private static MusicRefTool instance;
    private static boolean tried;

    private final Path binary;
    private final Path workDir;

    private MusicRefTool(Path binary, Path workDir) {
        this.binary = binary;
        this.workDir = workDir;
    }

    /** Repository root: the nearest ancestor of the working directory that contains {@code tools/opl3-vectors}. */
    static Path repoRoot() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("tools/opl3-vectors"))) {
            p = p.getParent();
        }
        return p;
    }

    /** The harness, built on first use, or {@code null} when it cannot be built here. */
    static synchronized MusicRefTool get() {
        if (!tried) {
            tried = true;
            try {
                instance = build();
            } catch (IOException | InterruptedException | RuntimeException e) {
                System.err.println("musicref harness unavailable: " + e);
            }
        }
        return instance;
    }

    private static MusicRefTool build() throws IOException, InterruptedException {
        Path root = repoRoot();
        if (root == null) {
            return null;
        }
        String home = System.getenv("DOOM_HOME");
        Path doomHome = home != null ? Path.of(home) : Path.of("/Users/luizaugustomarques/Documents/projetos/doom-java");
        Path choc = doomHome.resolve("tools/oracle/work/chocolate-doom/opl/opl3.c");
        if (!Files.isRegularFile(choc)) {
            return null;
        }
        Path work = root.resolve("target/audio-refs");
        Files.createDirectories(work);
        Path binary = work.resolve("musicref");
        ProcessBuilder pb = new ProcessBuilder(root.resolve("tools/opl3-vectors/music/build.sh").toString());
        pb.environment().put("OUT", binary.toString());
        pb.environment().put("DOOM_HOME", doomHome.toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes());
        if (!proc.waitFor(5, TimeUnit.MINUTES) || proc.exitValue() != 0 || !Files.isRegularFile(binary)) {
            System.err.println("musicref build failed:\n" + output);
            return null;
        }
        return new MusicRefTool(binary, work);
    }

    Path workDir() {
        return workDir;
    }

    /** Runs the harness with the given arguments; fails the caller on a non-zero exit. */
    void run(List<String> args) throws IOException, InterruptedException {
        java.util.ArrayList<String> cmd = new java.util.ArrayList<>();
        cmd.add(binary.toString());
        cmd.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes());
        if (!proc.waitFor(5, TimeUnit.MINUTES) || proc.exitValue() != 0) {
            throw new IllegalStateException("musicref " + args + " failed: " + output);
        }
    }
}
