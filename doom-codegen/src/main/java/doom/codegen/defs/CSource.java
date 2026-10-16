package doom.codegen.defs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Just enough of a C front end to read the data tables of the DOOM sources: comment and {@code #if 0} removal,
 * a tokenizer, brace-initializer trees, enums, {@code #define}s and a tiny constant-expression evaluator.
 * It is deliberately strict: anything unexpected is an {@link IllegalStateException} naming the file.
 */
final class CSource {

    /** Token kinds. */
    enum Kind { IDENT, NUMBER, STRING, CHAR, PUNCT }

    /** One token; {@code text} is the raw spelling (string and char literals keep their quotes). */
    record Token(Kind kind, String text, int line) {

        boolean is(String s) {
            return text.equals(s);
        }

        @Override
        public String toString() {
            return text;
        }
    }

    /** A node of a brace initializer: either a {@code {...}} group or a leaf expression (its tokens). */
    sealed interface Init permits Group, Leaf {
    }

    /** {@code { a, b, ... }}. */
    record Group(List<Init> items) implements Init {
    }

    /** One initializer expression. */
    record Leaf(List<Token> tokens) implements Init {

        String text() {
            StringBuilder sb = new StringBuilder();
            for (Token t : tokens) {
                sb.append(t.text());
            }
            return sb.toString();
        }
    }

    /** One enumerator. */
    record Enumerator(String name, int value) {
    }

    private final String fileName;
    /** Source with comments blanked and {@code #if 0} blocks removed; line structure preserved. */
    private final String text;

    private CSource(String fileName, String text) {
        this.fileName = fileName;
        this.text = text;
    }

    static CSource read(Path file) throws IOException {
        String raw = Files.readString(file, StandardCharsets.ISO_8859_1).replace("\r\n", "\n").replace('\r', '\n');
        return new CSource(file.getFileName().toString(), removeIfZero(stripComments(raw)));
    }

    static CSource of(String fileName, String raw) {
        return new CSource(fileName, removeIfZero(stripComments(raw.replace("\r\n", "\n"))));
    }

    String fileName() {
        return fileName;
    }

    String text() {
        return text;
    }

    IllegalStateException error(String message) {
        return new IllegalStateException(fileName + ": " + message);
    }

    // ------------------------------------------------------------------------------------------------
    // Preprocessing

    /** Replaces comments by spaces (newlines kept), leaving string and character literals intact. */
    static String stripComments(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && s.charAt(j) != c && s.charAt(j) != '\n') {
                    j += s.charAt(j) == '\\' ? 2 : 1;
                }
                j = Math.min(j + 1, n);
                out.append(s, i, j);
                i = j;
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int end = s.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                for (int k = i; k < end; k++) {
                    out.append(s.charAt(k) == '\n' ? '\n' : ' ');
                }
                i = end;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** Blanks the lines of {@code #if 0 ... #endif} blocks (nested conditionals inside are skipped too). */
    static String removeIfZero(String s) {
        String[] lines = s.split("\n", -1);
        StringBuilder out = new StringBuilder(s.length());
        int skipDepth = 0;
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].strip();
            if (skipDepth > 0) {
                if (t.startsWith("#if")) {
                    skipDepth++;
                } else if (t.startsWith("#endif")) {
                    skipDepth--;
                }
                lines[i] = "";
            } else if (t.matches("#\\s*if\\s+0\\b.*")) {
                skipDepth = 1;
                lines[i] = "";
            }
            out.append(lines[i]);
            if (i + 1 < lines.length) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------------------------------------
    // Tokenizer

    static List<Token> tokenize(String s, int firstLine) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        int n = s.length();
        int line = firstLine;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\n') {
                line++;
                i++;
            } else if (Character.isWhitespace(c) || c == '\\') {
                i++;
            } else if (c == '#') {
                // A preprocessor line inside a tokenized region: skip to the end of the line.
                while (i < n && s.charAt(i) != '\n') {
                    i++;
                }
            } else if (Character.isLetter(c) || c == '_') {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) {
                    j++;
                }
                out.add(new Token(Kind.IDENT, s.substring(i, j), line));
                i = j;
            } else if (Character.isDigit(c)) {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(s.charAt(j)))) {
                    j++;
                }
                out.add(new Token(Kind.NUMBER, s.substring(i, j), line));
                i = j;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && s.charAt(j) != c) {
                    if (s.charAt(j) == '\n') {
                        throw new IllegalStateException("unterminated literal at line " + line);
                    }
                    j += s.charAt(j) == '\\' ? 2 : 1;
                }
                out.add(new Token(c == '"' ? Kind.STRING : Kind.CHAR, s.substring(i, j + 1), line));
                i = j + 1;
            } else {
                out.add(new Token(Kind.PUNCT, String.valueOf(c), line));
                i++;
            }
        }
        return out;
    }

    /** Decodes a C string or character literal (with its quotes) into its characters. */
    static String decodeLiteral(String raw) {
        char quote = raw.charAt(0);
        if (raw.length() < 2 || raw.charAt(raw.length() - 1) != quote) {
            throw new IllegalStateException("bad literal " + raw);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < raw.length() - 1; i++) {
            char c = raw.charAt(i);
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char e = raw.charAt(++i);
            switch (e) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case 'a' -> sb.append('\u0007');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'v' -> sb.append('\u000b');
                case '\\', '\'', '"', '?' -> sb.append(e);
                case 'x' -> {
                    int j = i + 1;
                    while (j < raw.length() - 1 && Character.digit(raw.charAt(j), 16) >= 0) {
                        j++;
                    }
                    sb.append((char) Integer.parseInt(raw.substring(i + 1, j), 16));
                    i = j - 1;
                }
                default -> {
                    if (e >= '0' && e <= '7') {
                        int j = i;
                        while (j < raw.length() - 1 && j < i + 3 && raw.charAt(j) >= '0' && raw.charAt(j) <= '7') {
                            j++;
                        }
                        sb.append((char) Integer.parseInt(raw.substring(i, j), 8));
                        i = j - 1;
                    } else {
                        throw new IllegalStateException("unknown escape \\" + e + " in " + raw);
                    }
                }
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------------------------------------
    // Declarations

    /**
     * Parses the brace initializer of the declaration whose text before {@code =} matches {@code declRegex}
     * (for example {@code "state_t\\s+states\\s*\\[[^\\]]*\\]"}).
     */
    Group initializer(String declRegex) {
        Matcher m = Pattern.compile(declRegex + "\\s*=\\s*\\{").matcher(text);
        if (!m.find()) {
            throw error("declaration not found: " + declRegex);
        }
        int open = m.end() - 1;
        int close = matchingBrace(open);
        int firstLine = lineOf(open + 1);
        List<Token> tokens = tokenize(text.substring(open + 1, close), firstLine);
        int[] pos = {0};
        Group g = parseGroup(tokens, pos);
        if (pos[0] != tokens.size()) {
            throw error("trailing tokens in initializer " + declRegex);
        }
        return g;
    }

    private int matchingBrace(int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                int j = i + 1;
                while (text.charAt(j) != c) {
                    j += text.charAt(j) == '\\' ? 2 : 1;
                }
                i = j;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw error("unbalanced braces");
    }

    private int lineOf(int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** Parses comma-separated items until the end of the token list or a closing brace (consumed by the caller). */
    private Group parseGroup(List<Token> tokens, int[] pos) {
        List<Init> items = new ArrayList<>();
        while (pos[0] < tokens.size() && !tokens.get(pos[0]).is("}")) {
            Token t = tokens.get(pos[0]);
            if (t.is("{")) {
                pos[0]++;
                Group inner = parseGroup(tokens, pos);
                if (pos[0] >= tokens.size() || !tokens.get(pos[0]).is("}")) {
                    throw error("missing } near line " + t.line());
                }
                pos[0]++;
                items.add(inner);
            } else {
                List<Token> expr = new ArrayList<>();
                int depth = 0;
                while (pos[0] < tokens.size()) {
                    Token e = tokens.get(pos[0]);
                    if (depth == 0 && (e.is(",") || e.is("}"))) {
                        break;
                    }
                    if (e.is("(") || e.is("[")) {
                        depth++;
                    } else if (e.is(")") || e.is("]")) {
                        depth--;
                    }
                    expr.add(e);
                    pos[0]++;
                }
                items.add(new Leaf(List.copyOf(expr)));
            }
            if (pos[0] < tokens.size() && tokens.get(pos[0]).is(",")) {
                pos[0]++;
            }
        }
        return new Group(List.copyOf(items));
    }

    /** Parses {@code typedef enum { ... } typeName;}. Enumerators without a value continue from the previous one. */
    List<Enumerator> enumeration(String typeName, Map<String, Integer> constants) {
        Matcher m = Pattern.compile("typedef\\s+enum\\s*\\{([^}]*)\\}\\s*" + Pattern.quote(typeName) + "\\s*;")
                .matcher(text);
        if (!m.find()) {
            throw error("enum not found: " + typeName);
        }
        List<Token> tokens = tokenize(m.group(1), lineOf(m.start(1)));
        List<Enumerator> out = new ArrayList<>();
        Map<String, Integer> scope = new LinkedHashMap<>(constants);
        int next = 0;
        int i = 0;
        while (i < tokens.size()) {
            Token name = tokens.get(i++);
            if (name.kind() != Kind.IDENT) {
                throw error("bad enumerator " + name + " at line " + name.line());
            }
            int value = next;
            if (i < tokens.size() && tokens.get(i).is("=")) {
                List<Token> expr = new ArrayList<>();
                i++;
                while (i < tokens.size() && !tokens.get(i).is(",")) {
                    expr.add(tokens.get(i++));
                }
                value = evaluate(expr, scope);
            }
            out.add(new Enumerator(name.text(), value));
            scope.put(name.text(), value);
            next = value + 1;
            if (i < tokens.size()) {
                if (!tokens.get(i).is(",")) {
                    throw error("expected , after " + name + " at line " + name.line());
                }
                i++;
            }
        }
        return out;
    }

    /** One {@code #define NAME body} with continuation lines joined. */
    record Define(String name, List<Token> body, int line) {
    }

    /** All object-like {@code #define}s, in source order. */
    List<Define> defines() {
        List<Define> out = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            int startLine = i + 1;
            StringBuilder logical = new StringBuilder(lines[i]);
            while (logical.toString().stripTrailing().endsWith("\\") && i + 1 < lines.length) {
                String cur = logical.toString().stripTrailing();
                logical.setLength(0);
                logical.append(cur, 0, cur.length() - 1).append(' ').append(lines[++i]);
            }
            Matcher m = Pattern.compile("^\\s*#\\s*define\\s+(\\w+)(\\s.*)?$").matcher(logical.toString());
            if (m.matches()) {
                String body = m.group(2) == null ? "" : m.group(2);
                out.add(new Define(m.group(1), tokenize(body, startLine), startLine));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------
    // Expressions

    /**
     * Evaluates an integer constant expression made of numbers, identifiers from {@code constants},
     * parentheses, unary minus, {@code *}, {@code +}, {@code -}, {@code <<} and {@code |}.
     */
    int evaluate(List<Token> tokens, Map<String, Integer> constants) {
        int[] pos = {0};
        int v = evalOr(tokens, pos, constants);
        if (pos[0] != tokens.size()) {
            throw error("cannot evaluate " + tokens);
        }
        return v;
    }

    private int evalOr(List<Token> t, int[] pos, Map<String, Integer> c) {
        int v = evalShift(t, pos, c);
        while (pos[0] < t.size() && t.get(pos[0]).is("|")) {
            pos[0]++;
            v |= evalShift(t, pos, c);
        }
        return v;
    }

    private int evalShift(List<Token> t, int[] pos, Map<String, Integer> c) {
        int v = evalAdd(t, pos, c);
        while (pos[0] + 1 < t.size() && t.get(pos[0]).is("<") && t.get(pos[0] + 1).is("<")) {
            pos[0] += 2;
            v <<= evalAdd(t, pos, c);
        }
        return v;
    }

    private int evalAdd(List<Token> t, int[] pos, Map<String, Integer> c) {
        int v = evalMul(t, pos, c);
        while (pos[0] < t.size() && (t.get(pos[0]).is("+") || t.get(pos[0]).is("-"))) {
            boolean plus = t.get(pos[0]++).is("+");
            int r = evalMul(t, pos, c);
            v = plus ? v + r : v - r;
        }
        return v;
    }

    private int evalMul(List<Token> t, int[] pos, Map<String, Integer> c) {
        int v = evalUnary(t, pos, c);
        while (pos[0] < t.size() && t.get(pos[0]).is("*")) {
            pos[0]++;
            v *= evalUnary(t, pos, c);
        }
        return v;
    }

    private int evalUnary(List<Token> t, int[] pos, Map<String, Integer> c) {
        if (pos[0] >= t.size()) {
            throw error("unexpected end of expression " + t);
        }
        Token tok = t.get(pos[0]++);
        if (tok.is("-")) {
            return -evalUnary(t, pos, c);
        }
        if (tok.is("(")) {
            int v = evalOr(t, pos, c);
            if (pos[0] >= t.size() || !t.get(pos[0]++).is(")")) {
                throw error("missing ) in " + t);
            }
            return v;
        }
        return switch (tok.kind()) {
            case NUMBER -> parseNumber(tok.text());
            case IDENT -> {
                Integer v = c.get(tok.text());
                if (v == null) {
                    throw error("unknown identifier " + tok.text() + " at line " + tok.line());
                }
                yield v;
            }
            case CHAR -> decodeLiteral(tok.text()).charAt(0);
            default -> throw error("unexpected token " + tok + " at line " + tok.line());
        };
    }

    static int parseNumber(String s) {
        String digits = s.replaceAll("[uUlL]+$", "");
        if (digits.startsWith("0x") || digits.startsWith("0X")) {
            return (int) Long.parseLong(digits.substring(2), 16);
        }
        if (digits.length() > 1 && digits.startsWith("0")) {
            return (int) Long.parseLong(digits.substring(1), 8);
        }
        return (int) Long.parseLong(digits);
    }
}
