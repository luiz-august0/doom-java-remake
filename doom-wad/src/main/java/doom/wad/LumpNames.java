package doom.wad;

import java.nio.ByteBuffer;

/**
 * Lump and texture names: {@code char[8]}, NUL padded, not necessarily NUL terminated, compared case-insensitively over
 * at most 8 characters ({@code strncasecmp(a, b, 8)}, as Chocolate Doom's {@code W_CheckNumForName} does).
 *
 * <p>Bytes are mapped to characters one to one (ISO-8859-1) and only ASCII letters change case, like C's
 * {@code toupper} in the "C" locale.
 */
public final class LumpNames {

    /** Maximum length of a name. */
    public static final int MAX_LENGTH = 8;

    private LumpNames() {
    }

    /** Reads a {@code char[8]} name at {@code at}: stops at the first NUL, keeps the original case. */
    public static String read(ByteBuffer buf, int at) {
        char[] chars = new char[MAX_LENGTH];
        int len = 0;
        for (int i = 0; i < MAX_LENGTH; i++) {
            int c = buf.get(at + i) & 0xFF;
            if (c == 0) {
                break;
            }
            chars[len++] = (char) c;
        }
        return new String(chars, 0, len);
    }

    /**
     * Canonical lookup key of a name: cut at the first NUL, truncated to 8 characters and upper-cased (ASCII only).
     * Two names match under {@code strncasecmp(a, b, 8)} exactly when their keys are equal.
     */
    public static String key(String name) {
        int len = name.length();
        int nul = name.indexOf('\0');
        if (nul >= 0) {
            len = nul;
        }
        if (len > MAX_LENGTH) {
            len = MAX_LENGTH;
        }
        char[] out = new char[len];
        for (int i = 0; i < len; i++) {
            out[i] = upper(name.charAt(i));
        }
        return new String(out);
    }

    /** {@code strncasecmp(a, b, 8) == 0}. */
    public static boolean matches(String a, String b) {
        return key(a).equals(key(b));
    }

    /** ASCII-only upper case of one character. */
    public static char upper(char c) {
        return c >= 'a' && c <= 'z' ? (char) (c - ('a' - 'A')) : c;
    }

    /** ASCII-only upper case of a whole string (no truncation). */
    public static String upper(String s) {
        char[] out = new char[s.length()];
        for (int i = 0; i < out.length; i++) {
            out[i] = upper(s.charAt(i));
        }
        return new String(out);
    }
}
