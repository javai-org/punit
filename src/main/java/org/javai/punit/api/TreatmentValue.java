package org.javai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter to receive the initial treatment factor value in an
 * {@link OptimizeExperiment}.
 *
 * <p>The value is resolved from the use case instance via a method annotated
 * with {@link TreatmentValueSource}, then injected into this parameter.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @OptimizeExperiment(
 *     useCase = ShoppingUseCase.class,
 *     treatmentFactor = "systemPrompt",
 *     scorer = SuccessRateScorer.class,
 *     mutator = LLMStringFactorMutator.class
 * )
 * void optimizePrompt(
 *     ShoppingUseCase useCase,
 *     @TreatmentValue String initialPrompt,  // Injected from use case
 *     ResultCaptor captor
 * ) {
 *     captor.record(useCase.searchProducts("headphones"));
 * }
 * }</pre>
 *
 * <h2>Multi-Factor Optimization (Future)</h2>
 * <p>When optimizing multiple factors, specify the factor name:
 * <pre>{@code
 * void optimizeMultiple(
 *     ShoppingUseCase useCase,
 *     @TreatmentValue("systemPrompt") String initialPrompt,
 *     @TreatmentValue("temperature") double initialTemp,
 *     ResultCaptor captor
 * ) { ... }
 * }</pre>
 *
 * @see TreatmentValueSource
 * @see OptimizeExperiment
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface TreatmentValue {

    /**
     * The treatment factor name.
     *
     * <p>Optional when there is only one treatment factor (the common case).
     * Required when optimizing multiple factors to disambiguate which
     * factor this parameter receives.
     *
     * @return the factor name, or empty string for the default treatment factor
     */
    String value() default "";
}
