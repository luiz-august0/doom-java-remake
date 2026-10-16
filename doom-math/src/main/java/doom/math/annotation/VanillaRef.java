package doom.math.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Points to the original function(s) whose rule the annotated code reproduces, for example
 * {@code @VanillaRef("p_map.c:P_TryMove")}. Mandatory on simulation methods that reproduce a specific rule
 * (ADR-007); the {@code ref-coverage} tool cross-references these values with the linuxdoom functions.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE, ElementType.FIELD})
public @interface VanillaRef {

    /** One or more references in the form {@code "file.c:Function"}. */
    String[] value();
}
