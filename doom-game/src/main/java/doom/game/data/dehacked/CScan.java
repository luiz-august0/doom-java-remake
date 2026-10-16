package doom.game.data.dehacked;

/**
 * The few C library behaviours Chocolate Doom's DEHACKED parser depends on: {@code isspace}, {@code atoi}, the
 * {@code %i}, {@code %*i} and {@code %*s} conversions of {@code sscanf} and ASCII-only case folding.
 */
final class CScan {

    private final String s;
    private int pos;

    CScan(String s) {
        this.s = s;
    }

    /** C locale {@code isspace}. */
    static boolean isSpace(int c) {
        return c == ' ' || (c >= '\t' && c <= '\r');
    }

    /** {@code strcasecmp(a, b) == 0} in the C locale. */
    static boolean equalsIgnoreCase(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        for (int i = 0; i < a.length(); i++) {
            if (fold(a.charAt(i)) != fold(b.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** {@code strncasecmp(prefix, s, prefix.length()) == 0}. */
    static boolean startsWithIgnoreCase(String s, String prefix) {
        return s.length() >= prefix.length() && equalsIgnoreCase(s.substring(0, prefix.length()), prefix);
    }

    private static char fold(char c) {
        return c >= 'A' && c <= 'Z' ? (char) (c + 32) : c;
    }

    /** {@code atoi}: decimal, optional leading whitespace and sign, stops at the first other character. */
    static int atoi(String s) {
        return (int) strtol(s, 0, 10, new int[1]);
    }

    /**
     * {@code strtol} with 64-bit saturation. {@code end[0]} receives the index after the number, or {@code from}
     * when there is none.
     */
    private static long strtol(String s, int from, int base, int[] end) {
        int i = from;
        int n = s.length();
        while (i < n && isSpace(s.charAt(i))) {
            i++;
        }
        boolean neg = false;
        if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
            neg = s.charAt(i) == '-';
            i++;
        }
        if (base == 0) {
            if (i + 2 < n && s.charAt(i) == '0' && (s.charAt(i + 1) == 'x' || s.charAt(i + 1) == 'X')
                    && Character.digit(s.charAt(i + 2), 16) >= 0 && s.charAt(i + 2) < 128) {
                base = 16;
                i += 2;
            } else if (i < n && s.charAt(i) == '0') {
                base = 8;
            } else {
                base = 10;
            }
        }
        int start = i;
        long value = 0;
        boolean overflow = false;
        while (i < n) {
            char c = s.charAt(i);
            int digit = c < 128 ? Character.digit(c, base) : -1;
            if (digit < 0) {
                break;
            }
            if (!overflow) {
                long next = value * base + digit;
                if (value > (Long.MAX_VALUE - digit) / base) {
                    overflow = true;
                } else {
                    value = next;
                }
            }
            i++;
        }
        if (i == start) {
            end[0] = from;
            return 0;
        }
        end[0] = i;
        if (overflow) {
            return neg ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        return neg ? -value : value;
    }

    // ------------------------------------------------------------------------------------------------
    // sscanf

    /** Matches literal text; whitespace in {@code literal} matches any amount of whitespace. */
    boolean literal(String literal) {
        for (int k = 0; k < literal.length(); k++) {
            char c = literal.charAt(k);
            if (isSpace(c)) {
                while (pos < s.length() && isSpace(s.charAt(pos))) {
                    pos++;
                }
            } else if (pos < s.length() && s.charAt(pos) == c) {
                pos++;
            } else {
                return false;
            }
        }
        return true;
    }

    /** {@code %i} (or {@code %*i}): returns the value converted to {@code int}, or {@code null} on failure. */
    Integer integer() {
        int[] end = new int[1];
        long v = strtol(s, pos, 0, end);
        if (end[0] == pos) {
            return null;
        }
        pos = end[0];
        return (int) v;
    }

    /** {@code %*s}: skips whitespace, then a run of non-whitespace; false when there is none. */
    boolean word() {
        while (pos < s.length() && isSpace(s.charAt(pos))) {
            pos++;
        }
        int start = pos;
        while (pos < s.length() && !isSpace(s.charAt(pos))) {
            pos++;
        }
        return pos > start;
    }

    /** {@code %<width>s}: the next whitespace-delimited word of at most {@code width} characters, or null. */
    String word(int width) {
        while (pos < s.length() && isSpace(s.charAt(pos))) {
            pos++;
        }
        int start = pos;
        while (pos < s.length() && !isSpace(s.charAt(pos)) && pos - start < width) {
            pos++;
        }
        return pos > start ? s.substring(start, pos) : null;
    }
}
