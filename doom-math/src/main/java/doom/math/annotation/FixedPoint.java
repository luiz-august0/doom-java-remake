package doom.math.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an {@code int} that holds a 16.16 fixed-point value ({@code fixed_t} in the original).
 * Named {@code FixedPoint} rather than {@code Fixed} to avoid clashing with {@link doom.math.Fixed} (ADR-022).
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE_USE)
public @interface FixedPoint {
}
