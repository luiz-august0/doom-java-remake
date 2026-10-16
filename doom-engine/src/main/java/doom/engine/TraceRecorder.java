package doom.engine;

import doom.game.api.GameSession;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

/**
 * Writes the per-tic trace v1 (docs/contracts.md section 6): UTF-8, LF line endings, gzip when the name ends in
 * {@code .gz}, header {@code #doom-trace v1 iwad=<file> demo=<name> version=<ver>} and one line per game tic. With a
 * verbose file, the object lines of the tics in {@code [from, to]} follow the normal line.
 */
public final class TraceRecorder implements Closeable {

    private final BufferedWriter main;
    private final BufferedWriter verbose;
    private final int verboseFrom;
    private final int verboseTo;
    private final StringBuilder line = new StringBuilder(2048);
    private final StringBuilder extra = new StringBuilder(2048);
    private int tic;

    /**
     * @param traceFile   the trace file, or null
     * @param verboseFile the verbose trace file, or null
     * @param header      the header line, without the line feed
     */
    public TraceRecorder(Path traceFile, Path verboseFile, String header, int verboseFrom, int verboseTo) {
        this.verboseFrom = verboseFrom;
        this.verboseTo = verboseTo;
        try {
            this.main = traceFile == null ? null : open(traceFile, header);
            this.verbose = verboseFile == null ? null : open(verboseFile, header);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BufferedWriter open(Path file, String header) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        OutputStream out = Files.newOutputStream(file);
        if (file.getFileName().toString().endsWith(".gz")) {
            out = new GZIPOutputStream(out, 1 << 16);
        }
        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), 1 << 16);
        w.write(header);
        w.write('\n');
        return w;
    }

    /** The header line for a run. */
    public static String header(String iwadFileName, String demoName, String versionCliName) {
        return "#doom-trace v1 iwad=" + iwadFileName + " demo=" + demoName + " version=" + versionCliName;
    }

    /** Writes the line of the tic the session just ran. */
    public void record(GameSession session) {
        try {
            line.setLength(0);
            session.appendTraceLine(line);
            if (line.isEmpty()) {
                return; // the tick ended the program inside G_Ticker: no line (contracts 6.1)
            }
            line.append('\n');
            if (main != null) {
                main.append(line);
            }
            if (verbose != null) {
                verbose.append(line);
                if (tic >= verboseFrom && tic <= verboseTo) {
                    extra.setLength(0);
                    session.appendTraceVerbose(extra);
                    verbose.append(extra);
                }
            }
            tic++;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Number of lines written. */
    public int lines() {
        return tic;
    }

    @Override
    public void close() throws IOException {
        if (main != null) {
            main.close();
        }
        if (verbose != null) {
            verbose.close();
        }
    }
}
