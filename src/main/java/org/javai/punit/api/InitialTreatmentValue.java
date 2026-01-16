package org.javai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the source of the initial value for the treatment factor in
 * {@link OptimizeExperiment}.
 *
 * <p>The initial treatment value is the starting point for optimization.
 * The mutator will generate variations from this value across iterations.
 *
 * <h2>Requirements</h2>
 * <p>The referenced method must:
 * <ul>
 *   <li>Be static</li>
 *   <li>Return the treatment factor type (e.g., String for system prompts)</li>
 *   <li>Accept no parameters</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @OptimizeExperiment(
 *     useCase = ShoppingUseCase.class,
 *     treatmentFactor = "systemPrompt",
 *     scorer = SuccessRateScorer.class,
 *     mutator = LLMStringFactorMutator.class
 * )
 * @FixedFactors("shoppingFactors")
 * @InitialTreatmentValue("initialSystemPrompt")
 * void optimizePrompt(ShoppingUseCase useCase, ResultCaptor captor) {
 *     captor.record(useCase.searchProducts("headphones"));
 * }
 *
 * static String initialSystemPrompt() {
 *     return "You are a helpful shopping assistant.";
 * }
 * }</pre>
 *
 * @see OptimizeExperiment
 * @see FixedFactors
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InitialTreatmentValue {

    /**
     * Name of the static method providing the initial treatment factor value.
     *
     * <p>The method must be in the same class as the experiment method.
     *
     * @return the method name
     */
    String value();
}