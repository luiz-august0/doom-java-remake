package doom.codegen.tables;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the integer initializer of a C array definition such as {@code int finetangent[4096] = { ... };}
 * or {@code byte gammatable[5][256] = { {...}, ... };} from C source text.
 *
 * <p>The parser is deliberately strict: comments and the contents of string literals are ignored, the initializer
 * may contain only integer literals,
 * commas, braces and white space, and the number of values must equal the product of the declared dimensions.
 * Values are returned exactly as written (no arithmetic is ever applied to them).
 */
final class CArrayParser {

    private static final Pattern NUMBER = Pattern.compile("([-+]?)\\s*(0[xX][0-9a-fA-F]+|[0-9]+)[uUlL]*");

    private CArrayParser() {
    }

    /**
     * Parses the initializer of array {@code name}.
     *
     * @param source     the C source text
     * @param name       the array name, e.g. {@code "finesine"}
     * @param dimensions the expected dimensions, e.g. {@code 10240} or {@code 5, 256}
     * @param min        smallest accepted value
     * @param max        largest accepted value
     * @return the values in source order (row-major for several dimensions)
     * @throws IllegalArgumentException when the array is missing, malformed or does not match the expectations
     */
    static long[] parse(String source, String name, int[] dimensions, long min, long max) {
        String text = stripComments(source);
        StringBuilder dims = new StringBuilder();
        for (int i = 0; i < dimensions.length; i++) {
            dims.append("\\[\\s*(\\d+)\\s*\\]\\s*");
        }
        Pattern decl = Pattern.compile("\\b" + Pattern.quote(name) + "\\s*" + dims + "=\\s*\\{");
        Matcher m = decl.matcher(text);
        if (!m.find()) {
            throw new IllegalArgumentException("Array definition not found: " + name + Arrays.toString(dimensions));
        }
        int expected = 1;
        for (int i = 0; i < dimensions.length; i++) {
            int declared = Integer.parseInt(m.group(i + 1));
            if (declared != dimensions[i]) {
                throw new IllegalArgumentException(name + ": declared dimension " + i + " is " + declared
                        + ", expected " + dimensions[i]);
            }
            expected *= declared;
        }
        if (findDuplicate(decl, text, m.end())) {
            throw new IllegalArgumentException("Array defined more than once: " + name);
        }
        String body = initializerBody(text, m.end() - 1, name);
        checkCharacters(body, name);

        long[] values = new long[expected];
        int count = 0;
        Matcher n = NUMBER.matcher(body);
        while (n.find()) {
            String digits = n.group(2);
            long v = digits.startsWith("0x") || digits.startsWith("0X")
                    ? Long.parseLong(digits.substring(2), 16)
                    : Long.parseLong(digits);
            if (n.group(1).equals("-")) {
                v = -v;
            }
            if (v < min || v > max) {
                throw new IllegalArgumentException(name + "[" + count + "] = " + v + " is outside [" + min + ", " + max + "]");
            }
            if (count == expected) {
                throw new IllegalArgumentException(name + ": more than " + expected + " values");
            }
            values[count++] = v;
        }
        if (count != expected) {
            throw new IllegalArgumentException(name + ": found " + count + " values, expected " + expected);
        }
        return values;
    }

    private static boolean findDuplicate(Pattern decl, String text, int from) {
        return decl.matcher(text).find(from);
    }

    /** Returns the text between the brace at {@code open} and its matching closing brace (exclusive). */
    private static String initializerBody(String text, int open, String name) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(open + 1, i);
                }
            }
        }
        throw new IllegalArgumentException(name + ": unterminated initializer");
    }

    private static void checkCharacters(String body, String name) {
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            boolean ok = Character.isWhitespace(c) || c == ',' || c == '{' || c == '}' || c == '-' || c == '+'
                    || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                    || c == 'x' || c == 'X' || c == 'u' || c == 'U' || c == 'l' || c == 'L';
            if (!ok) {
                throw new IllegalArgumentException(name + ": unexpected character '" + c + "' in the initializer");
            }
        }
    }

    /** Removes C and C++ comments and empties string and character literals. */
    static String stripComments(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int end = s.indexOf("*/", i + 2);
                if (end < 0) {
                    throw new IllegalArgumentException("Unterminated comment");
                }
                out.append(' ');
                i = end + 2;
            } else if (c == '"' || c == '\'') {
                // String and character literals become empty literals: their contents are never needed and must not
                // be mistaken for an array definition.
                int j = i + 1;
                while (j < n && s.charAt(j) != c) {
                    j += s.charAt(j) == '\\' ? 2 : 1;
                }
                out.append(c).append(c);
                i = j + 1;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
