package doom.math;

import java.io.Serial;

/**
 * Fatal game error, the equivalent of the original {@code I_Error}. The message mirrors the original one.
 * The application shows it and exits with a non-zero status (spec 02 §9).
 */
public final class DoomFatalException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DoomFatalException(String message) {
        super(message);
    }

    public DoomFatalException(String message, Throwable cause) {
        super(message, cause);
    }
}
