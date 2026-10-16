package doom.game.data;

/**
 * One cheat code ({@code cheatseq_t}): the key sequence the player types, plus the parameter characters typed
 * after it. DEHACKED {@code Cheat} lines replace the sequence; the original length stays as the limit Chocolate
 * Doom enforces for patches without the {@code *allow-long-cheats*} comment.
 *
 * <p>Like Chocolate Doom's, the sequence lives in a fixed {@link #MAX_LENGTH} buffer and ends at the first NUL, so
 * a patch that fails half-way leaves the tail of the old sequence behind the new characters, as in the original.
 */
public final class CheatSequence {

    /** Chocolate Doom's {@code MAX_CHEAT_LEN}: sequence and parameters must fit in this many characters. */
    public static final int MAX_LENGTH = 25;

    private final CheatId id;
    private final int originalLength;
    private final char[] buffer;

    CheatSequence(CheatId id) {
        this.id = id;
        this.buffer = new char[MAX_LENGTH];
        String vanilla = id.vanillaSequence();
        vanilla.getChars(0, vanilla.length(), buffer, 0);
        this.originalLength = vanilla.length();
    }

    private CheatSequence(CheatId id, int originalLength, char[] buffer) {
        this.id = id;
        this.originalLength = originalLength;
        this.buffer = buffer;
    }

    public CheatId id() {
        return id;
    }

    /** The characters to type, without parameters. May be shorter (or, for long-cheat patches, longer) than vanilla. */
    public String sequence() {
        int n = 0;
        while (n < buffer.length && buffer[n] != 0) {
            n++;
        }
        return new String(buffer, 0, n);
    }

    /** Replaces the sequence (at most {@link #MAX_LENGTH} - 1 characters). */
    public void setSequence(String sequence) {
        if (sequence.length() >= MAX_LENGTH) {
            throw new IllegalArgumentException("cheat sequence too long: " + sequence);
        }
        java.util.Arrays.fill(buffer, '\0');
        sequence.getChars(0, sequence.length(), buffer, 0);
    }

    /** Writes one character of the raw buffer (the DEHACKED {@code Cheat} parser's primitive). */
    public void put(int index, char c) {
        buffer[index] = c;
    }

    /** One character of the raw buffer, including bytes behind the terminating NUL. */
    public char at(int index) {
        return buffer[index];
    }

    /** Length of the vanilla sequence ({@code sequence_len}). */
    public int originalLength() {
        return originalLength;
    }

    /** Characters typed after the sequence. */
    public int parameterChars() {
        return id.parameterChars();
    }

    public CheatSequence copy() {
        return new CheatSequence(id, originalLength, buffer.clone());
    }
}
