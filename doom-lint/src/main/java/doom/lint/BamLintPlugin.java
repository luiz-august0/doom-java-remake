package doom.lint;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

/**
 * The {@code BamLint} javac plugin (ADR-006): reports, as compilation errors, operations that treat a binary angle
 * ({@code angle_t}, an <i>unsigned</i> 32-bit value in the original, stored in a Java {@code int} annotated
 * {@code @doom.math.annotation.Bam}) as a signed number. See {@link BamScanner} for the exact rules and how to suppress
 * a report.
 *
 * <p>Two ways to enable it (javac looks plugins up with {@link java.util.ServiceLoader}; this module declares the
 * service in {@code module-info} only, because a {@code META-INF/services} entry would be picked up while compiling
 * this very module and break its build):
 * <ul>
 *   <li>{@code --processor-module-path <doom-lint jar or classes> -Xplugin:BamLint} (add {@code warn} after the name,
 *       {@code -Xplugin:"BamLint warn"}, to report warnings instead of errors);</li>
 *   <li>{@code -processorpath <doom-lint jar or classes> -processor doom.lint.BamLintProcessor}, which is what
 *       maven-compiler-plugin 3.x produces from {@code annotationProcessorPaths} and {@code annotationProcessors}
 *       (see {@link BamLintProcessor}).</li>
 * </ul>
 *
 * <p>The checks run on each class after javac's flow analysis ({@link TaskEvent.Kind#ANALYZE}), when every expression
 * is attributed; the trees are never modified.
 */
public final class BamLintPlugin implements Plugin {

    /** The name used with {@code -Xplugin:}. */
    public static final String NAME = "BamLint";
    /** Option that turns the reports into warnings. */
    public static final String OPTION_WARN = "warn";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void init(JavacTask task, String... args) {
        install(task, args);
    }

    /**
     * Registers the checks on a compilation task.
     *
     * @param args nothing, or {@value #OPTION_WARN}
     * @throws IllegalArgumentException for an unknown option
     */
    static void install(JavacTask task, String... args) {
        Diagnostic.Kind kind = Diagnostic.Kind.ERROR;
        for (String arg : args) {
            if (arg.equals(OPTION_WARN)) {
                kind = Diagnostic.Kind.WARNING;
            } else if (!arg.isBlank()) {
                throw new IllegalArgumentException(NAME + ": unknown option '" + arg + "' (supported: " + OPTION_WARN + ")");
            }
        }
        task.addTaskListener(new Listener(Trees.instance(task), kind));
    }

    /** Scans each analyzed class once. One instance per compilation task; never shared. */
    private static final class Listener implements TaskListener {

        private final Trees trees;
        private final Diagnostic.Kind kind;
        /** Class trees already scanned (a top-level scan covers nested classes, which may get their own event). */
        private final Set<Tree> scanned = Collections.newSetFromMap(new IdentityHashMap<>());

        Listener(Trees trees, Diagnostic.Kind kind) {
            this.trees = trees;
            this.kind = kind;
        }

        @Override
        public void finished(TaskEvent e) {
            if (e.getKind() != TaskEvent.Kind.ANALYZE) {
                return;
            }
            CompilationUnitTree unit = e.getCompilationUnit();
            TypeElement type = e.getTypeElement();
            if (unit == null || type == null) {
                return;
            }
            TreePath path = trees.getPath(type);
            if (path == null || !scanned.add(path.getLeaf())) {
                return;
            }
            new BamScanner(trees, unit, kind, scanned).scan(path, null);
        }
    }
}
