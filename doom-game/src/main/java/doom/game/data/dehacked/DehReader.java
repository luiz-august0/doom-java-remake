package doom.game.data.dehacked;

import java.util.ArrayList;
import java.util.List;

/**
 * The input side of Chocolate Doom's {@code deh_io.c}: a character stream with CRLF folding and a line reader that
 * knows the extended (BEX {@code [STRINGS]}) escapes. Also collects the warnings and errors of one patch.
 */
final class DehReader {

    private final String source;
    private final String text;
    private int pos;
    private int lineNumber;
    private boolean lastWasNewline = true;
    private boolean hadError;
    private final List<String> warnings = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    DehReader(String source, String text) {
        this.source = source;
        this.text = text;
    }

    boolean hadError() {
        return hadError;
    }

    List<String> warnings() {
        return warnings;
    }

    List<String> errors() {
        return errors;
    }

    /** {@code DEH_Warning}. */
    void warning(String message) {
        warnings.add(source + ":" + lineNumber + ": warning: " + message);
    }

    /** {@code DEH_Error}: stops the parse at the end of the current line or section start. */
    void error(String message) {
        errors.add(source + ":" + lineNumber + ": " + message);
        hadError = true;
    }

    private int rawChar() {
        return pos < text.length() ? text.charAt(pos++) : -1;
    }

    /** {@code DEH_GetChar}: one character, CRLF read as LF, a lone CR kept; -1 at the end. */
    int getChar() {
        if (lastWasNewline) {
            lineNumber++;
        }
        boolean lastWasCr = false;
        int result;
        do {
            result = rawChar();
            if (lastWasCr && result != '\n') {
                if (result >= 0) {
                    pos--;
                }
                return '\r';
            }
            lastWasCr = result == '\r';
        } while (lastWasCr);
        lastWasNewline = result == '\n';
        return result;
    }

    /**
     * {@code DEH_ReadLine}: the next line without its terminator, or {@code null} at the end of the data.
     * With {@code extended}, {@code \n} stands for a line feed inside the string and a backslash at the end of a
     * line joins the next one (indentation of the continuation is dropped).
     */
    String readLine(boolean extended) {
        StringBuilder line = new StringBuilder();
        boolean escaped = false;
        for (;;) {
            int c = getChar();
            if (c < 0 && line.length() == 0) {
                return null;
            }
            if (extended && c == '\\') {
                c = getChar();
                if (c == 'n') {
                    line.append('\n');
                    continue;
                }
                if (c == '\n') {
                    escaped = true;
                    continue;
                }
            }
            if (escaped && c >= 0 && CScan.isSpace(c) && c != '\n') {
                continue;
            }
            escaped = false;
            if (c == '\n' || c < 0) {
                return line.toString();
            } else if (c != 0) {
                line.append((char) c);
            }
        }
    }
}
