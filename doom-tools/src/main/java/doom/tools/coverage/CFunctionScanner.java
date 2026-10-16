package doom.tools.coverage;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the function definitions of a C source file (linuxdoom-1.10 style). It is a tokenizer-level scan, not a parser:
 * comments, string and character literals and preprocessor lines are blanked out, then every {@code '{'} found at brace
 * depth zero whose preceding declaration text ends in a parameter list and contains no {@code '='} is taken as a
 * function body, and the identifier in front of that parameter list is the function name.
 */
final class CFunctionScanner {

    /** A function definition: its name and the 1-based line of the name. */
    record Function(String name, int line) {
    }

    private CFunctionScanner() {
    }

    /** Returns the functions defined in {@code source}, in file order. */
    static List<Function> scan(String source) {
        char[] c = blank(source).toCharArray();
        List<Function> out = new ArrayList<>();
        int depth = 0;
        int headerStart = 0;
        for (int i = 0; i < c.length; i++) {
            switch (c[i]) {
                case '{' -> {
                    if (depth == 0) {
                        Function f = functionBefore(c, headerStart, i);
                        if (f != null) {
                            out.add(f);
                        }
                    }
                    depth++;
                }
                case '}' -> {
                    depth = Math.max(0, depth - 1);
                    if (depth == 0) {
                        headerStart = i + 1;
                    }
                }
                case ';' -> {
                    if (depth == 0) {
                        headerStart = i + 1;
                    }
                }
                default -> {
                }
            }
        }
        return out;
    }

    private static Function functionBefore(char[] c, int from, int brace) {
        int close = -1;
        for (int i = brace - 1; i >= from; i--) {
            if (c[i] == ')') {
                close = i;
                break;
            }
            if (!Character.isWhitespace(c[i])) {
                return null; // something other than a parameter list right before '{'
            }
        }
        if (close < 0) {
            return null;
        }
        int level = 0;
        int open = -1;
        for (int i = close; i >= from; i--) {
            if (c[i] == ')') {
                level++;
            } else if (c[i] == '(') {
                level--;
                if (level == 0) {
                    open = i;
                    break;
                }
            }
        }
        if (open < 0) {
            return null;
        }
        for (int i = from; i < open; i++) {
            if (c[i] == '=') {
                return null; // an initializer such as "int a[] = {"
            }
        }
        int end = open;
        while (end > from && Character.isWhitespace(c[end - 1])) {
            end--;
        }
        int start = end;
        while (start > from && (Character.isLetterOrDigit(c[start - 1]) || c[start - 1] == '_')) {
            start--;
        }
        if (start == end || Character.isDigit(c[start])) {
            return null;
        }
        String name = new String(c, start, end - start);
        if (name.equals("if") || name.equals("while") || name.equals("for") || name.equals("switch")) {
            return null;
        }
        int line = 1;
        for (int i = 0; i < start; i++) {
            if (c[i] == '\n') {
                line++;
            }
        }
        return new Function(name, line);
    }

    /** Replaces comments, literals and preprocessor lines by spaces, keeping the newlines (and so the line numbers). */
    static String blank(String s) {
        StringBuilder b = new StringBuilder(s.length());
        int n = s.length();
        boolean lineStart = true;
        int i = 0;
        while (i < n) {
            char ch = s.charAt(i);
            if (ch == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int end = s.indexOf("*/", i + 2);
                int stop = end < 0 ? n : end + 2;
                for (int k = i; k < stop; k++) {
                    b.append(s.charAt(k) == '\n' ? '\n' : ' ');
                }
                i = stop;
            } else if (ch == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') {
                    b.append(' ');
                    i++;
                }
            } else if (ch == '"' || ch == '\'') {
                b.append(' ');
                i++;
                while (i < n && s.charAt(i) != ch && s.charAt(i) != '\n') {
                    if (s.charAt(i) == '\\' && i + 1 < n) {
                        b.append(' ');
                        i++;
                    }
                    b.append(' ');
                    i++;
                }
                if (i < n && s.charAt(i) == ch) {
                    b.append(' ');
                    i++;
                }
                lineStart = false;
            } else if (ch == '#' && lineStart) {
                while (i < n) {
                    char p = s.charAt(i);
                    if (p == '\n') {
                        if (i > 0 && s.charAt(i - 1) == '\\') {
                            b.append('\n');
                            i++;
                            continue;
                        }
                        break;
                    }
                    b.append(' ');
                    i++;
                }
            } else {
                b.append(ch);
                if (ch == '\n') {
                    lineStart = true;
                } else if (!Character.isWhitespace(ch)) {
                    lineStart = false;
                }
                i++;
            }
        }
        return b.toString();
    }
}
