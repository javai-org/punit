package org.javai.punit.experiment.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a use case for probabilistic testing or experimentation.
 *
 * <p>A use case is a test/experiment-only function that:
 * <ul>
 *   <li>Invokes production code</li>
 *   <li>Captures observations as a {@link org.javai.punit.experiment.model.UseCaseResult}</li>
 *   <li>Is never called by production code</li>
 *   <li>Can be reused by both experiments and probabilistic tests</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * @UseCase("usecase.email.validation")
 * UseCaseResult validateEmailFormat(String email, UseCaseContext context) {
 *     ValidationResult result = emailValidator.validate(email);
 *     
 *     return UseCaseResult.builder()
 *         .value("isValid", result.isValid())
 *         .value("errorCode", result.getErrorCode())
 *         .value("processingTimeMs", result.getProcessingTimeMs())
 *         .build();
 * }
 * }</pre>
 *
 * <h2>Use Case ID Convention</h2>
 * <p>Use case IDs should follow a dot-separated namespace convention:
 * <ul>
 *   <li>{@code usecase.email.validation}</li>
 *   <li>{@code usecase.json.generation}</li>
 *   <li>{@code usecase.sentiment.analysis}</li>
 * </ul>
 *
 * @see org.javai.punit.experiment.model.UseCaseResult
 * @see UseCaseContext
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UseCase {
    
    /**
     * Unique identifier for this use case.
     *
     * <p>Convention: dot-separated namespace (e.g., "usecase.email.validation").
     *
     * @return the use case ID
     */
    String value();
    
    /**
     * Human-readable description of what this use case tests.
     *
     * @return description of the use case
     */
    String description() default "";
}

