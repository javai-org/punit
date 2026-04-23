package org.javai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter to receive the current control factor value in an
 * {@code OptimizeExperiment}.
 *
 * <p>The value is the current iteration's control factor — initially the
 * value returned by the method named by {@link OptimizeExperiment#initialFactor()},
 * and subsequently the value produced by the
 * {@link org.javai.punit.experiment.optimize.FactorMutator} between iterations.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @OptimizeExperiment(
 *     useCase = ShoppingUseCase.class,
 *     controlFactor = "systemPrompt",
 *     initialFactor = "initialPrompt",
 *     scorer = SuccessRateScorer.class,
 *     mutator = LLMStringFactorMutator.class
 * )
 * void optimizePrompt(
 *     ShoppingUseCase useCase,
 *     @ControlFactor String currentPrompt,
 *     OutcomeCaptor captor
 * ) {
 *     captor.record(useCase.searchProducts("headphones"));
 * }
 *
 * static String initialPrompt() { return "You are an assistant."; }
 * }</pre>
 *
 * <h2>Multi-Factor Optimization (Future)</h2>
 * <p>When optimizing multiple factors, specify the factor name:
 * <pre>{@code
 * void optimizeMultiple(
 *     ShoppingUseCase useCase,
 *     @ControlFactor("systemPrompt") String prompt,
 *     @ControlFactor("temperature") double temp,
 *     OutcomeCaptor captor
 * ) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ControlFactor {

    /**
     * The control factor name.
     *
     * <p>Optional when there is only one control factor (the common case).
     * Required when optimizing multiple factors to disambiguate which
     * factor this parameter receives.
     *
     * @return the factor name, or empty string for the default control factor
     */
    String value() default "";
}
