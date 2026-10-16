package doom.math.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an {@code int} that holds a binary angle ({@code angle_t}, an unsigned 32-bit value in the original).
 * The {@code doom-lint} javac plugin reports signed comparisons ({@code <}, {@code >}, ...) and arithmetic right shifts
 * ({@code >>}) applied to such values; use {@link doom.math.Angle} helpers and {@code >>>} instead (ADR-006).
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE_USE)
public @interface Bam {
}
