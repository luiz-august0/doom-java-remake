package doom.testkit.trace;

import java.io.Serial;

/** A trace file that does not follow format v1. */
public final class TraceFormatException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long lineNumber;

    public TraceFormatException(String message, long lineNumber) {
        super(lineNumber > 0 ? "line " + lineNumber + ": " + message : message);
        this.lineNumber = lineNumber;
    }

    /** 1-based line number in the file, or 0 when unknown. */
    public long lineNumber() {
        return lineNumber;
    }
}
