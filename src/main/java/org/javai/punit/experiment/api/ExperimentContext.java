package org.javai.punit.experiment.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the execution context for an experiment, including backend and parameters.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * @Experiment(useCase = "usecase.json.generation", samples = 100)
 * @ExperimentContext(
 *     backend = "llm",
 *     parameters = {
 *         "model = gpt-4",
 *         "temperature = 0.7",
 *         "maxTokens = 1000"
 *     }
 * )
 * void experimentWithContext() {
 *     // ...
 * }
 * }</pre>
 *
 * <h2>Parameter Syntax</h2>
 * <p>Parameters use the format: {@code "key = value"}
 *
 * <p>For multi-config experiments, parameters can reference variables:
 * <pre>{@code
 * @ExperimentContext(
 *     backend = "llm",
 *     template = {
 *         "model = ${model}",        // Variable (substituted per config)
 *         "temperature = ${temp}",   // Variable
 *         "maxTokens = 1000"         // Fixed value
 *     }
 * )
 * }</pre>
 *
 * @see Experiment
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ExperimentContexts.class)
public @interface ExperimentContext {
    
    /**
     * Backend identifier for this context.
     *
     * <p>Common backend identifiers:
     * <ul>
     *   <li>{@code "llm"} - Language model backend</li>
     *   <li>{@code "generic"} - Default passthrough backend</li>
     *   <li>{@code "sensor"} - Hardware sensor backend</li>
     * </ul>
     *
     * @return the backend identifier
     */
    String backend() default "generic";
    
    /**
     * Key-value pairs for backend-specific configuration.
     *
     * <p>Format: "key = value"
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "model = gpt-4"} - fixed value</li>
     *   <li>{@code "temperature = 0.7"} - fixed value</li>
     * </ul>
     *
     * @return the configuration parameters
     */
    String[] parameters() default {};
    
    /**
     * Template with key-value pairs that may include variable references.
     *
     * <p>Format: "key = value" or "key = ${variableName}"
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "model = gpt-4"} - fixed value</li>
     *   <li>{@code "model = ${model}"} - variable (substituted per config)</li>
     *   <li>{@code "temperature = ${temp}"} - variable</li>
     * </ul>
     *
     * @return the template parameters
     */
    String[] template() default {};
}

