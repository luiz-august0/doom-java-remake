package doom.testkit.oracle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * Runs the patched Chocolate Doom (the oracle, contracts §7) through {@code tools/oracle/run.sh} and collects its
 * per-tic trace.
 *
 * <p>The script is called as {@code run.sh <iwad> <demo.lmp> -trace <file> [-traceverbose <file> -tracefrom <n>
 * -traceto <m>] <extra args>}: it adds the pinned configuration (fresh {@code HOME}, SDL dummy drivers,
 * {@code -nogui}), maps the {@code -timedemo} exit status 255 to 0 and forwards the rest to the binary.
 * {@code ORACLE_LOG} points to a file in the run's temporary directory, so the console output (where
 * {@code -timedemo} reports the number of tics) is available. Each run gets its own temporary directory; the demo is
 * copied there under its own name, so the lump name is the demo name. The directory is deleted afterwards.
 *
 * <p>{@code -timedemo} ends through {@code I_Error("timed N gametics ...")}; that message in the console output tells a
 * finished demo from a real engine error, which also leaves with status 255 (and which run.sh then reports as success).
 * Thread-safe: {@link #runAll} runs several oracles in parallel on virtual threads.
 */
public final class OracleRunner {

    /** Environment variable with the main working tree. */
    public static final String ENV_HOME = "DOOM_HOME";
    /** Environment variable with the oracle binary. */
    public static final String ENV_ORACLE = "DOOM_ORACLE";
    /** Environment variable with the runner script. */
    public static final String ENV_RUN = "DOOM_ORACLE_RUN";
    /** Environment variable with the cache directory. */
    public static final String ENV_CACHE = "DOOM_ORACLE_CACHE";
    /** Default per-run timeout. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    private static final Pattern TIMED = Pattern.compile("timed (\\d+) gametics");
    private static final Pattern ERROR_TEXT = Pattern.compile("(?i)\\b(error|fatal|failed|cannot|can't|not found)\\b");
    private static final int OUTPUT_TAIL = 16 * 1024;

    private final Path runScript;
    private final Path binary;
    private final Path tempRoot;

    /**
     * @param runScript the runner script
     * @param binary    the oracle binary (only used for the cache fingerprint and the availability check; may be null)
     * @param tempRoot  directory under which per-run directories are created (the system temp directory when null)
     */
    public OracleRunner(Path runScript, Path binary, Path tempRoot) {
        this.runScript = runScript;
        this.binary = binary;
        this.tempRoot = tempRoot;
    }

    /**
     * The oracle configured by the environment: {@code DOOM_ORACLE_RUN} or {@code $DOOM_HOME/tools/oracle/run.sh} or
     * the first {@code tools/oracle/run.sh} found walking up from the working directory; the binary likewise.
     * Empty when the script or the binary is missing.
     */
    public static Optional<OracleRunner> fromEnvironment() {
        Optional<Path> script = locate(ENV_RUN, "tools/oracle/run.sh");
        Optional<Path> bin = locate(ENV_ORACLE, "tools/oracle/work/build/src/chocolate-doom");
        if (script.isEmpty() || bin.isEmpty()) {
            return Optional.empty();
        }
        Path work = cacheDirectory().resolveSibling("tmp");
        return Optional.of(new OracleRunner(script.get(), bin.get(), work));
    }

    /** The cache directory: {@code DOOM_ORACLE_CACHE}, else {@code $DOOM_HOME/tools/oracle/work/cache}. */
    public static Path cacheDirectory() {
        String env = System.getenv(ENV_CACHE);
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }
        Optional<Path> work = locateDirectory("tools/oracle/work");
        return work.map(p -> p.resolve("cache"))
                .orElseGet(() -> Path.of(System.getProperty("java.io.tmpdir"), "doom-oracle-cache"));
    }

    private static Optional<Path> locate(String envName, String relative) {
        String env = System.getenv(envName);
        if (env != null && !env.isBlank()) {
            Path p = Path.of(env);
            return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
        }
        String home = System.getenv(ENV_HOME);
        if (home != null && !home.isBlank()) {
            Path p = Path.of(home).resolve(relative);
            if (Files.isRegularFile(p)) {
                return Optional.of(p);
            }
        }
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path p = dir.resolve(relative);
            if (Files.isRegularFile(p)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> locateDirectory(String relative) {
        String home = System.getenv(ENV_HOME);
        if (home != null && !home.isBlank()) {
            Path p = Path.of(home).resolve(relative);
            if (Files.isDirectory(p)) {
                return Optional.of(p);
            }
        }
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path p = dir.resolve(relative);
            if (Files.isDirectory(p)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /** The runner script. */
    public Path runScript() {
        return runScript;
    }

    /** The oracle binary, or null. */
    public Path binary() {
        return binary;
    }

    /**
     * A string that changes when the oracle script or binary changes (size and modification time), used to key the
     * trace cache.
     */
    public String fingerprint() {
        return describe(runScript) + "|" + (binary == null ? "-" : describe(binary));
    }

    private static String describe(Path p) {
        try {
            return p.getFileName() + ":" + Files.size(p) + ":" + Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return p.getFileName() + ":missing";
        }
    }

    /**
     * One oracle run.
     *
     * @param id          label for messages (usually the demo name)
     * @param iwad        the IWAD file
     * @param demo        the demo (.lmp) file
     * @param traceOut    where to store the trace; gzip-compressed when the name ends with {@code .gz}
     * @param verboseOut  where to store the verbose trace (§6.6), or null
     * @param verboseFrom first tic of the verbose range ({@code -tracefrom}), or -1 for all
     * @param verboseTo   last tic of the verbose range ({@code -traceto}), or -1 for all
     * @param extraArgs   further arguments for the oracle
     * @param timeout     time limit, or null for {@link #DEFAULT_TIMEOUT}
     */
    public record Request(String id, Path iwad, Path demo, Path traceOut, Path verboseOut, int verboseFrom,
                          int verboseTo, List<String> extraArgs, Duration timeout) {

        public Request {
            extraArgs = List.copyOf(extraArgs);
        }

        /** A plain request: trace only, no extra arguments, default timeout. */
        public static Request of(String id, Path iwad, Path demo, Path traceOut) {
            return new Request(id, iwad, demo, traceOut, null, -1, -1, List.of(), null);
        }
    }

    /** How a run ended. */
    public enum Status {
        /** The demo played to its end and the trace was stored. */
        OK,
        /** The time limit expired; the process was killed. */
        TIMEOUT,
        /** The oracle exited with an error (see {@link Result#message()}). */
        FAILED,
        /** The runner itself could not be started or the trace could not be stored. */
        ERROR
    }

    /**
     * The outcome of a run.
     *
     * @param id       the request id
     * @param status   how the run ended
     * @param exitCode the process exit status, or -1 when it did not run to the end
     * @param gametics the number of tics reported by {@code -timedemo} (-1 when unknown)
     * @param trace    the stored trace (null unless {@link Status#OK})
     * @param message  a one-line explanation for failures (the last lines of output)
     * @param output   the tail of the oracle's output
     * @param elapsed  wall-clock time
     */
    public record Result(String id, Status status, int exitCode, int gametics, Path trace, String message,
                         String output, Duration elapsed) {

        /** Whether the run succeeded. */
        public boolean ok() {
            return status == Status.OK;
        }
    }

    /** Runs one request. Never throws for oracle failures: they are reported in the {@link Result}. */
    public Result run(Request req) {
        long start = System.nanoTime();
        Path dir = null;
        try {
            if (tempRoot != null) {
                Files.createDirectories(tempRoot);
                dir = Files.createTempDirectory(tempRoot, "run-");
            } else {
                dir = Files.createTempDirectory("doom-oracle-");
            }
            String lump = req.demo().getFileName().toString();
            Path demoCopy = dir.resolve(lump);
            Files.copy(req.demo(), demoCopy, StandardCopyOption.REPLACE_EXISTING);
            Path trace = dir.resolve("trace.txt");
            Path verbose = req.verboseOut() == null ? null : dir.resolve("verbose.txt");

            List<String> cmd = new ArrayList<>();
            cmd.add("/bin/bash");
            cmd.add(runScript.toString());
            cmd.add(req.iwad().toAbsolutePath().toString());
            cmd.add(demoCopy.toString());
            cmd.add("-trace");
            cmd.add(trace.toString());
            if (verbose != null) {
                cmd.add("-traceverbose");
                cmd.add(verbose.toString());
                if (req.verboseFrom() >= 0) {
                    cmd.add("-tracefrom");
                    cmd.add(Integer.toString(req.verboseFrom()));
                }
                if (req.verboseTo() >= 0) {
                    cmd.add("-traceto");
                    cmd.add(Integer.toString(req.verboseTo()));
                }
            }
            cmd.addAll(req.extraArgs());

            Path consoleLog = dir.resolve("console.log");
            ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true);
            pb.environment().put("ORACLE_LOG", consoleLog.toString());
            Process process;
            try {
                process = pb.start();
            } catch (IOException e) {
                return fail(req, Status.ERROR, -1, "cannot start " + runScript + ": " + e.getMessage(), "", start);
            }
            TailCollector tail = new TailCollector(process.getInputStream());
            Thread reader = Thread.ofVirtual().name("oracle-output-" + req.id()).start(tail);
            Duration limit = req.timeout() == null ? DEFAULT_TIMEOUT : req.timeout();
            boolean finished;
            try {
                finished = process.waitFor(limit.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                killTree(process);
                Thread.currentThread().interrupt();
                return fail(req, Status.ERROR, -1, "interrupted", tail.text(), start);
            }
            if (!finished) {
                killTree(process);
                join(reader);
                return fail(req, Status.TIMEOUT, -1, "timed out after " + limit.toSeconds() + " s", tail.text(), start);
            }
            join(reader);
            int exit = process.exitValue();
            String output = tail.text() + readTail(consoleLog);
            Matcher timed = TIMED.matcher(output);
            int gametics = timed.find() ? Integer.parseInt(timed.group(1)) : -1;
            boolean exitOk = exit == 0 || exit == 255;
            boolean finishedDemo = gametics >= 0 || (exitOk && !ERROR_TEXT.matcher(output).find());
            if (!exitOk || !finishedDemo) {
                return fail(req, Status.FAILED, exit, lastLines(output, 3), output, start);
            }
            if (!Files.isRegularFile(trace) || Files.size(trace) == 0) {
                return fail(req, Status.FAILED, exit, "the oracle produced no trace", output, start);
            }
            store(trace, req.traceOut());
            if (verbose != null && Files.isRegularFile(verbose)) {
                store(verbose, req.verboseOut());
            }
            return new Result(req.id(), Status.OK, exit, gametics, req.traceOut(), "", output,
                    Duration.ofNanos(System.nanoTime() - start));
        } catch (IOException | RuntimeException e) {
            return fail(req, Status.ERROR, -1, e.toString(), "", start);
        } finally {
            if (dir != null) {
                deleteTree(dir);
            }
        }
    }

    /**
     * Runs several requests, at most {@code parallelism} at a time, each on its own virtual thread.
     *
     * @return the results, in request order
     */
    public List<Result> runAll(List<Request> requests, int parallelism) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be >= 1");
        }
        Semaphore permits = new Semaphore(parallelism);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Result>> futures = new ArrayList<>(requests.size());
            for (Request r : requests) {
                futures.add(pool.submit(() -> {
                    permits.acquire();
                    try {
                        return run(r);
                    } finally {
                        permits.release();
                    }
                }));
            }
            List<Result> out = new ArrayList<>(requests.size());
            for (int i = 0; i < futures.size(); i++) {
                try {
                    out.add(futures.get(i).get());
                } catch (java.util.concurrent.ExecutionException e) {
                    out.add(new Result(requests.get(i).id(), Status.ERROR, -1, -1, null, e.getCause().toString(), "",
                            Duration.ZERO));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    out.add(new Result(requests.get(i).id(), Status.ERROR, -1, -1, null, "interrupted", "",
                            Duration.ZERO));
                }
            }
            return out;
        }
    }

    private static Result fail(Request req, Status status, int exit, String message, String output, long startNanos) {
        return new Result(req.id(), status, exit, -1, null, message, output,
                Duration.ofNanos(System.nanoTime() - startNanos));
    }

    private static void store(Path from, Path to) throws IOException {
        Path parent = to.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path partial = to.resolveSibling(to.getFileName() + ".partial");
        if (to.getFileName().toString().endsWith(".gz")) {
            try (InputStream in = Files.newInputStream(from);
                 OutputStream out = new GZIPOutputStream(Files.newOutputStream(partial), 1 << 16)) {
                in.transferTo(out);
            }
        } else {
            Files.copy(from, partial, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(partial, to, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void killTree(Process p) {
        p.descendants().forEach(ProcessHandle::destroyForcibly);
        p.destroyForcibly();
        try {
            p.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void join(Thread t) {
        try {
            t.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The end of the oracle's console log (empty when there is none). */
    private static String readTail(Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                return "";
            }
            byte[] all = Files.readAllBytes(file);
            int from = Math.max(0, all.length - OUTPUT_TAIL);
            return "\n" + new String(all, from, all.length - from, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String lastLines(String text, int n) {
        String[] lines = text.strip().split("\\R");
        int from = Math.max(0, lines.length - n);
        return String.join(" | ", java.util.Arrays.copyOfRange(lines, from, lines.length)).strip();
    }

    private static void deleteTree(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    // best effort: a leftover temporary directory is harmless
                }
            });
        } catch (IOException e) {
            // best effort
        }
    }

    /** Keeps the last {@link #OUTPUT_TAIL} bytes of a process output. */
    private static final class TailCollector implements Runnable {
        private final InputStream in;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

        TailCollector(InputStream in) {
            this.in = in;
        }

        @Override
        public void run() {
            byte[] chunk = new byte[4096];
            try {
                int n;
                while ((n = in.read(chunk)) > 0) {
                    synchronized (buf) {
                        buf.write(chunk, 0, n);
                        if (buf.size() > 2 * OUTPUT_TAIL) {
                            byte[] all = buf.toByteArray();
                            buf.reset();
                            buf.write(all, all.length - OUTPUT_TAIL, OUTPUT_TAIL);
                        }
                    }
                }
            } catch (IOException e) {
                // the process was killed: whatever was read is the output
            }
        }

        String text() {
            synchronized (buf) {
                byte[] all = buf.toByteArray();
                int from = Math.max(0, all.length - OUTPUT_TAIL);
                return new String(all, from, all.length - from, StandardCharsets.UTF_8);
            }
        }
    }
}
