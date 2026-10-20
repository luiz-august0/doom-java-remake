package doom.lint;

import com.sun.source.util.JavacTask;
import java.lang.reflect.Method;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

/**
 * Starts the {@link BamLintPlugin} checks from an annotation processor, for builds that can only pass a class-path
 * style processor path: {@code -processorpath <doom-lint> -processor doom.lint.BamLintProcessor} (maven-compiler-plugin
 * 3.x {@code annotationProcessorPaths} + {@code annotationProcessors}). The processor claims no annotations and
 * generates nothing; it only registers the plugin's task listener on the current javac task.
 *
 * <p>Option: {@code -Adoom.lint.mode=warn} reports warnings instead of errors.
 *
 * <p>IntelliJ IDEA's build (JPS) wraps the processing environment, which {@link JavacTask#instance} rejects: the
 * wrapper is unwrapped the way JetBrains documents, and when the javac task still cannot be reached the checks are
 * skipped with a note instead of failing the build (Maven and CI still run them).
 */
public final class BamLintProcessor extends AbstractProcessor {

    /** {@code -A} option that selects the report kind ({@code error}, the default, or {@code warn}). */
    public static final String MODE_OPTION = "doom.lint.mode";

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of("*");
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Set.of(MODE_OPTION);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        String mode = processingEnv.getOptions().getOrDefault(MODE_OPTION, "error");
        String[] args = switch (mode) {
            case "error" -> new String[0];
            case BamLintPlugin.OPTION_WARN -> new String[] {BamLintPlugin.OPTION_WARN};
            default -> throw new IllegalArgumentException(
                    BamLintPlugin.NAME + ": unknown " + MODE_OPTION + " '" + mode + "' (supported: error, warn)");
        };
        JavacTask task;
        try {
            task = JavacTask.instance(unwrap(processingEnv));
        } catch (IllegalArgumentException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, BamLintPlugin.NAME
                    + ": not a javac task (" + processingEnv.getClass().getName() + "), checks skipped");
            return;
        }
        BamLintPlugin.install(task, args);
    }

    /** The javac environment behind IntelliJ IDEA's wrapper ({@code org.jetbrains.jps.javac.APIWrappers}), else {@code env}. */
    static ProcessingEnvironment unwrap(ProcessingEnvironment env) {
        try {
            Class<?> wrappers = env.getClass().getClassLoader().loadClass("org.jetbrains.jps.javac.APIWrappers");
            Method unwrap = wrappers.getDeclaredMethod("unwrap", Class.class, Object.class);
            Object unwrapped = unwrap.invoke(null, ProcessingEnvironment.class, env);
            return unwrapped instanceof ProcessingEnvironment p ? p : env;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return env;
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return false;
    }
}
