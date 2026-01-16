package org.javai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.javai.punit.experiment.model.FactorSuit;

/**
 * Specifies the source of fixed factor values for {@link OptimizeExperiment}.
 *
 * <p>Fixed factors are held constant throughout optimization while the treatment
 * factor is mutated. The referenced method provides a {@link FactorSuit} containing
 * all fixed factor values.
 *
 * <h2>Requirements</h2>
 * <p>The referenced method must:
 * <ul>
 *   <li>Be static</li>
 *   <li>Return {@link FactorSuit}</li>
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
 * static FactorSuit shoppingFactors() {
 *     return FactorSuit.of(
 *         "model", "gpt-4",
 *         "temperature", 0.7
 *     );
 * }
 * }</pre>
 *
 * @see OptimizeExperiment
 * @see InitialTreatmentValue
 * @see FactorSuit
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FixedFactors {

    /**
     * Name of the static method providing the fixed factor suit.
     *
     * <p>The method must be in the same class as the experiment method.
     *
     * @return the method name
     */
    String value();
}