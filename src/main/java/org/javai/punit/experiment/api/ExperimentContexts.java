package org.javai.punit.experiment.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container annotation for multiple {@link ExperimentContext} annotations.
 *
 * <p>This annotation is automatically used when multiple {@code @ExperimentContext}
 * annotations are placed on a method.
 *
 * @see ExperimentContext
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExperimentContexts {
    
    /**
     * The experiment context annotations.
     *
     * @return array of experiment contexts
     */
    ExperimentContext[] value();
}

