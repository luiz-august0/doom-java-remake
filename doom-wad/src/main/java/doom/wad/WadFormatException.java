package doom.wad;

import java.io.Serial;

/**
 * Malformed WAD data. Always names the file, the lump and the byte offset involved (spec 05 §1), so a bad PWAD
 * produces a clear message instead of a generic exception.
 */
public final class WadFormatException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public WadFormatException(String file, String lump, long offset, String message) {
        super(format(file, lump, offset, message));
    }

    public WadFormatException(String message) {
        super(message);
    }

    private static String format(String file, String lump, long offset, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append(file == null ? "<memory>" : file);
        if (lump != null) {
            sb.append(" lump ").append(lump);
        }
        if (offset >= 0) {
            sb.append(" @").append(offset);
        }
        return sb.append(": ").append(message).toString();
    }
}
