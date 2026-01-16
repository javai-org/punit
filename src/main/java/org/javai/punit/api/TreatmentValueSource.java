package org.javai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method on a use case class as the source of a treatment factor's
 * initial value for {@link OptimizeExperiment}.
 *
 * <p>The extension calls this method on the use case instance to obtain the
 * initial value, which is then injected into experiment method parameters
 * annotated with {@link TreatmentValue}.
 *
 * <h2>Requirements</h2>
 * <p>The annotated method must:
 * <ul>
 *   <li>Be an instance method (not static)</li>
 *   <li>Accept no parameters</li>
 *   <li>Return the treatment factor type</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @UseCase
 * public class ShoppingUseCase {
 *
 *     private String systemPrompt;
 *
 *     @TreatmentValueSource("systemPrompt")
 *     public String getSystemPrompt() {
 *         return this.systemPrompt;
 *     }
 *
 *     @FactorSetter("systemPrompt")
 *     public void setSystemPrompt(String prompt) {
 *         this.systemPrompt = prompt;
 *     }
 * }
 * }</pre>
 *
 * <p>The {@code @TreatmentValueSource} and {@code @FactorSetter} annotations
 * typically come in pairs - one to read the current value, one to set new values
 * during optimization iterations.
 *
 * @see TreatmentValue
 * @see OptimizeExperiment
 * @see FactorSetter
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TreatmentValueSource {

    /**
     * The treatment factor name this method provides values for.
     *
     * <p>Must match the {@code treatmentFactor} specified in
     * {@link OptimizeExperiment#treatmentFactor()}.
     *
     * @return the factor name
     */
    String value();
}
