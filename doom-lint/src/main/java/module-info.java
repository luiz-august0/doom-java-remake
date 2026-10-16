/**
 * javac plugin ({@code -Xplugin:BamLint}) that reports signed comparisons and arithmetic right shifts applied to
 * values annotated {@code @Bam} (ADR-006).
 */
module doom.lint {
    requires jdk.compiler;

    provides com.sun.source.util.Plugin with doom.lint.BamLintPlugin;
}
