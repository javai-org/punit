package org.javai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a getter method on a use case as providing a factor's current value.
 *
 * <p>This annotation is the counterpart to {@link FactorSetter}. Together they
 * define the read/write interface for a factor on a use case.
 *
 * <h2>Factor Name Resolution</h2>
 * <p>The factor name is determined by:
 * <ol>
 *   <li>The annotation's {@code value} parameter, if provided</li>
 *   <li>Otherwise, derived from the method name by removing the "get" prefix
 *       and lowercasing the first character (e.g., {@code getTemperature} → "temperature")</li>
 * </ol>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @UseCase
 * public class ShoppingUseCase {
 *     private double temperature = 0.3;
 *
 *     @FactorGetter  // factor name derived as "temperature"
 *     public double getTemperature() {
 *         return temperature;
 *     }
 *
 *     @FactorSetter  // factor name derived as "temperature"
 *     public void setTemperature(double temperature) {
 *         this.temperature = temperature;
 *     }
 * }
 * }</pre>
 *
 * <h2>Usage in Optimization</h2>
 * <p>In {@link OptimizeExperiment}, the getter for the control factor provides
 * the initial value that the optimizer will iteratively refine:
 * <pre>{@code
 * @OptimizeExperiment(
 *     useCase = ShoppingUseCase.class,
 *     treatmentFactor = "systemPrompt",  // looks up @FactorGetter for this
 *     ...
 * )
 * void optimize(ShoppingUseCase useCase, @ControlFactor String prompt, OutcomeCaptor captor) {
 *     // prompt contains the current value from getSystemPrompt()
 * }
 * }</pre>
 *
 * @see FactorSetter
 * @see OptimizeExperiment
 * @see ControlFactor
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FactorGetter {

    /**
     * The name of the factor this method provides values for.
     *
     * <p>If empty (the default), the factor name is derived from the method name
     * by removing the "get" prefix and lowercasing the first character.
     *
     * @return the factor name, or empty to derive from method name
     */
    String value() default "";
}
