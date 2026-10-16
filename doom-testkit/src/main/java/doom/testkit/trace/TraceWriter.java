package doom.testkit.trace;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

/**
 * Writes a trace file (§6.1): UTF-8, LF line endings, gzip-compressed when the file name ends with {@code .gz}.
 * Not thread-safe.
 */
public final class TraceWriter implements Closeable {

    private final Writer out;

    private TraceWriter(OutputStream stream, boolean gzip, TraceHeader header) throws IOException {
        OutputStream data = gzip ? new GZIPOutputStream(stream, 1 << 16) : stream;
        this.out = new BufferedWriter(new OutputStreamWriter(data, StandardCharsets.UTF_8), 1 << 16);
        writeRaw(header.raw());
    }

    /** Creates (or replaces) {@code file} and writes the header. */
    public static TraceWriter create(Path file, TraceHeader header) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        OutputStream stream = Files.newOutputStream(file);
        try {
            return new TraceWriter(stream, file.getFileName().toString().endsWith(".gz"), header);
        } catch (IOException | RuntimeException e) {
            stream.close();
            throw e;
        }
    }

    /** Writes to a stream; closing the writer closes the stream. */
    public static TraceWriter of(OutputStream stream, boolean gzip, TraceHeader header) throws IOException {
        return new TraceWriter(stream, gzip, header);
    }

    /** Writes a tic line and its verbose entries. */
    public void write(TraceLine line) throws IOException {
        writeRaw(line.raw());
        for (VerboseEntry e : line.verbose()) {
            writeRaw(e.format());
        }
    }

    /** Writes a tic line given as text (without the line feed). */
    public void writeLine(String raw) throws IOException {
        if (raw.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("A trace line cannot contain a line feed");
        }
        writeRaw(raw);
    }

    /** Writes a comment line ({@code #} is added). */
    public void writeComment(String text) throws IOException {
        writeLine("#" + text);
    }

    private void writeRaw(String s) throws IOException {
        out.write(s);
        out.write('\n');
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
