package doom.testkit.trace;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Streams a trace file (§6.1): the header, then one {@link TraceLine} per tic with its verbose object lines attached.
 * Gzip is detected from the content (magic bytes), so {@code .gz} and plain files both work whatever their names.
 * Comment lines other than the header are skipped. Not thread-safe.
 */
public final class TraceReader implements Closeable {

    private final BufferedReader in;
    private final String source;
    private final TraceHeader header;
    private long lineNumber;
    /** The next raw line already read (lookahead), or null. */
    private String pending;

    private TraceReader(InputStream raw, String source) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(raw, 1 << 16);
        buffered.mark(2);
        int b0 = buffered.read();
        int b1 = buffered.read();
        buffered.reset();
        InputStream data = b0 == 0x1f && b1 == 0x8b ? new GZIPInputStream(buffered, 1 << 16) : buffered;
        this.in = new BufferedReader(new InputStreamReader(data, StandardCharsets.UTF_8), 1 << 16);
        this.source = source;
        String first = readRaw();
        if (first == null) {
            throw new TraceFormatException(source + ": empty trace", 0);
        }
        this.header = TraceHeader.parse(first);
        this.pending = readRaw();
    }

    /** Opens a trace file (plain or gzip). */
    public static TraceReader open(Path file) throws IOException {
        InputStream in = Files.newInputStream(file);
        try {
            return new TraceReader(in, file.toString());
        } catch (IOException | RuntimeException e) {
            in.close();
            throw e;
        }
    }

    /** Reads from a stream (plain or gzip); closing the reader closes the stream. */
    public static TraceReader of(InputStream in, String sourceName) throws IOException {
        return new TraceReader(in, sourceName);
    }

    /** Reads a whole trace into memory. */
    public static Trace readAll(Path file) throws IOException {
        try (TraceReader r = open(file)) {
            List<TraceLine> lines = new ArrayList<>();
            for (TraceLine l = r.next(); l != null; l = r.next()) {
                lines.add(l);
            }
            return new Trace(r.header(), lines);
        }
    }

    /** The header line. */
    public TraceHeader header() {
        return header;
    }

    /** Name of the source, for messages. */
    public String source() {
        return source;
    }

    /**
     * The next tic line with its verbose entries, or null at the end of the file.
     *
     * @throws TraceFormatException when a line is malformed
     * @throws UncheckedIOException when reading fails
     */
    public TraceLine next() {
        while (pending != null && (pending.isEmpty() || pending.startsWith("#"))) {
            pending = readRaw();
        }
        if (pending == null) {
            return null;
        }
        if (pending.startsWith(TraceFormat.VERBOSE_INDENT)) {
            throw new TraceFormatException(source + ": verbose line before any tic line", lineNumber);
        }
        TraceLine line = TraceLine.parse(pending, lineNumber);
        List<VerboseEntry> verbose = null;
        pending = readRaw();
        while (pending != null && (pending.startsWith(TraceFormat.VERBOSE_INDENT) || pending.startsWith("#"))) {
            if (!pending.startsWith("#")) {
                if (verbose == null) {
                    verbose = new ArrayList<>();
                }
                verbose.add(VerboseEntry.parse(pending, lineNumber));
            }
            pending = readRaw();
        }
        return verbose == null ? line : line.withVerbose(verbose);
    }

    private String readRaw() {
        try {
            String s = in.readLine();
            if (s != null) {
                lineNumber++;
                if (s.endsWith("\r")) {
                    s = s.substring(0, s.length() - 1);
                }
            }
            return s;
        } catch (IOException e) {
            throw new UncheckedIOException(source + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
