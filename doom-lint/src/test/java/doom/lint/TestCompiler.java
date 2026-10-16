package doom.lint;

import com.sun.source.util.JavacTask;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Compiles in-memory snippets with the system Java compiler, optionally with the {@link BamLintPlugin}. */
final class TestCompiler {

    /** A stand-in for {@code doom.math.annotation.Bam}; the plugin matches the annotation by its qualified name. */
    static final String BAM_SOURCE = """
            package doom.math.annotation;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Retention(RetentionPolicy.CLASS)
            @Target(ElementType.TYPE_USE)
            public @interface Bam {
            }
            """;

    /** How the plugin is attached to the task. */
    enum Mode {
        /** No plugin. */
        NONE,
        /** {@code new BamLintPlugin().init(task)} on the task before it runs. */
        INSTANCE,
        /** {@code -Xplugin:BamLint} with this module on the processor module path (ServiceLoader + module-info). */
        XPLUGIN
    }

    /**
     * The options of the Maven 3 style processor bootstrap
     * ({@code -processorpath <this module's classes> -processor doom.lint.BamLintProcessor}). Only meaningful in a
     * separate javac process, see {@link #compileForked}.
     */
    static List<String> processorOptions() {
        return List.of("-processorpath", pluginLocation().toString(), "-processor", BamLintProcessor.class.getName());
    }

    /** One diagnostic, simplified. */
    record Message(Diagnostic.Kind kind, String file, long line, String text) {

        @Override
        public String toString() {
            return kind + " " + file + ":" + line + " " + text;
        }
    }

    /** The outcome of one compilation. */
    record Result(boolean success, List<Message> messages) {

        List<Message> lint() {
            return messages.stream().filter(m -> m.text().startsWith("[BamLint]")).toList();
        }

        List<Long> lintLines() {
            return lint().stream().map(Message::line).toList();
        }
    }

    private TestCompiler() {
    }

    /** A source file: path such as {@code "p/A.java"} and its text. */
    record Source(String path, String text) {
    }

    static Source source(String path, String text) {
        return new Source(path, text);
    }

    static Result compile(Mode mode, Path outDir, List<String> extraOptions, Source... sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            List<JavaFileObject> units = new ArrayList<>();
            for (Source s : sources) {
                units.add(new StringSource(s.path(), s.text()));
            }
            List<String> options = new ArrayList<>(List.of("-d", outDir.toString(), "-proc:none", "-Xlint:none"));
            options.addAll(extraOptions);
            if (mode == Mode.XPLUGIN) {
                options.add("--processor-module-path");
                options.add(pluginLocation().toString());
                if (extraOptions.stream().noneMatch(o -> o.startsWith("-Xplugin:"))) {
                    options.add("-Xplugin:" + BamLintPlugin.NAME);
                }
            }
            JavacTask task = (JavacTask) compiler.getTask(null, fm, diagnostics, options, null, units);
            if (mode == Mode.INSTANCE) {
                new BamLintPlugin().init(task);
            }
            boolean ok = task.call();
            List<Message> messages = new ArrayList<>();
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                String file = d.getSource() == null ? "" : Path.of(d.getSource().toUri().getPath()).getFileName().toString();
                messages.add(new Message(d.getKind(), file, d.getLineNumber(), d.getMessage(Locale.ROOT)));
            }
            return new Result(ok, messages);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Compiles with a separate {@code javac} process (the JDK running the tests), with {@code options} added. Needed for
     * the processor bootstrap: inside this JVM, javac's processor class loader would find the plugin classes in the
     * already loaded (and not exported) {@code doom.lint} module first.
     */
    static Result compileForked(Path workDir, List<String> options, Source... sources) {
        try {
            Path src = Files.createDirectories(workDir.resolve("src"));
            Path classes = Files.createDirectories(workDir.resolve("classes"));
            List<String> command = new ArrayList<>();
            command.add(Path.of(System.getProperty("java.home"), "bin", "javac").toString());
            command.addAll(List.of("-d", classes.toString(), "-Xlint:none", "-XDrawDiagnostics"));
            command.addAll(options);
            for (Source s : sources) {
                Path file = src.resolve(s.path());
                Files.createDirectories(file.getParent());
                Files.writeString(file, s.text(), StandardCharsets.UTF_8);
                command.add(file.toString());
            }
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int status = p.waitFor();
            // -XDrawDiagnostics lines: "O.java:6:31: compiler.err.proc.messager: [BamLint] ..."
            List<Message> messages = new ArrayList<>();
            for (String line : output.split("\n")) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("^(\\S+?):(\\d+):\\d+: compiler\\.(err|warn)\\.[a-z.]+: (.*)$").matcher(line.strip());
                if (m.matches()) {
                    Diagnostic.Kind kind = m.group(3).equals("err") ? Diagnostic.Kind.ERROR : Diagnostic.Kind.WARNING;
                    messages.add(new Message(kind, m.group(1), Long.parseLong(m.group(2)), m.group(4)));
                } else if (line.startsWith("error:") || line.contains("compiler.err")) {
                    messages.add(new Message(Diagnostic.Kind.ERROR, "", -1, line.strip()));
                }
            }
            return new Result(status == 0, messages);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    static Result compile(Path outDir, Source... sources) {
        List<Source> all = new ArrayList<>(List.of(sources));
        all.add(source("doom/math/annotation/Bam.java", BAM_SOURCE));
        return compile(Mode.INSTANCE, outDir, List.of(), all.toArray(Source[]::new));
    }

    /** Directory (or jar) that contains this module's main classes, including module-info.class. */
    static Path pluginLocation() {
        try {
            Path p = Path.of(BamLintPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isDirectory(p) && !Files.exists(p.resolve("module-info.class"))) {
                throw new IllegalStateException("No module-info.class under " + p);
            }
            return p;
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class StringSource extends SimpleJavaFileObject {
        private final String text;

        StringSource(String path, String text) {
            super(URI.create("string:///" + path), Kind.SOURCE);
            this.text = text;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return text;
        }
    }
}
