package org.javai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter as the target for input injection from {@link InputSource}.
 *
 * <p>When a method has multiple parameters that could receive the input value,
 * this annotation explicitly identifies which parameter should receive it.
 *
 * <h2>When to Use</h2>
 * <p>Use this annotation when:
 * <ul>
 *   <li>The method has multiple candidate parameters (not UseCase, OutcomeCaptor, or @Factor)</li>
 *   <li>You want to be explicit about input injection for clarity</li>
 *   <li>The automatic detection is picking the wrong parameter</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @ExploreExperiment(useCase = ShoppingBasketUseCase.class, samplesPerConfig = 10)
 * @InputSource(file = "golden/shopping-instructions.json")
 * void exploreWithGoldenData(
 *         ShoppingBasketUseCase useCase,
 *         @Input GoldenInput input,      // Explicitly marked as input target
 *         OutcomeCaptor captor
 * ) {
 *     captor.record(useCase.translateInstruction(input.instruction()));
 * }
 * }</pre>
 *
 * <h2>Automatic Detection</h2>
 * <p>If no parameter is annotated with @Input, the framework automatically detects
 * the input parameter by excluding:
 * <ul>
 *   <li>UseCase types (by naming convention)</li>
 *   <li>{@link OutcomeCaptor} parameters</li>
 *   <li>{@link Factor @Factor}-annotated parameters</li>
 *   <li>{@link TokenChargeRecorder} parameters</li>
 * </ul>
 *
 * @see InputSource
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Input {
}
