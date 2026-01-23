package org.javai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.javai.punit.experiment.engine.ExperimentExtension;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks a method as a MEASURE experiment that executes a use case repeatedly
 * to gather statistically significant empirical data for a single configuration.
 *
 * <p>MEASURE mode is designed for precise statistical estimation with large sample sizes
 * (1000+ recommended). It produces a single baseline spec in {@code specs/}.
 *
 * <h2>When to Use MEASURE</h2>
 * <ul>
 *   <li>After EXPLORE has identified the winning configuration</li>
 *   <li>To establish a reliable baseline with tight confidence intervals</li>
 *   <li>For final measurements before production deployment</li>
 * </ul>
 *
 * <h2>Gradle Task</h2>
 * <pre>{@code ./gradlew measure}</pre>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @MeasureExperiment(
 *     useCase = ShoppingUseCase.class,
 *     samples = 1000,
 *     timeBudgetMs = 600_000
 * )
 * void measureProductSearchBaseline(ShoppingUseCase useCase, OutcomeCaptor captor) {
 *     captor.record(useCase.searchProducts("headphones", context));
 * }
 * }</pre>
 *
 * <h2>Output</h2>
 * <p>Produces a spec file at: {@code src/test/resources/punit/specs/{UseCaseId}.yaml}
 *
 * @see ExploreExperiment
 * @see UseCase
 * @see UseCaseProvider
 * @see OutcomeCaptor
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(ExperimentExtension.class)
public @interface MeasureExperiment {

    /**
     * The use case class to execute.
     *
     * <p>The use case ID is resolved from the class:
     * <ol>
     *   <li>If {@code @UseCase} annotation is present with a value, use that</li>
     *   <li>Otherwise, use the simple class name</li>
     * </ol>
     *
     * <p>The use case instance is obtained from a {@link UseCaseProvider} registered
     * as a JUnit extension. Configure the provider in {@code @BeforeEach}.
     *
     * @return the use case class
     */
    Class<?> useCase() default Void.class;

    /**
     * Number of sample invocations to execute.
     *
     * <p>Must be ≥ 1. Default: 0 (uses mode default of 1000).
     * A high value such as 1000+ is recommended for reliable statistical estimation.
     *
     * @return the number of samples
     */
    int samples() default 1000;

    /**
     * Maximum wall-clock time budget in milliseconds.
     *
     * <p>0 = unlimited. Default: 0.
     *
     * @return the time budget in milliseconds
     */
    long timeBudgetMs() default 0;

    /**
     * Maximum token budget for all samples.
     *
     * <p>0 = unlimited. Default: 0.
     *
     * @return the token budget
     */
    long tokenBudget() default 0;

    /**
     * Unique identifier for this experiment.
     *
     * <p>Used as part of output file naming.
     *
     * @return the experiment ID
     */
    String experimentId() default "";

    /**
     * Number of days for which the baseline remains valid.
     *
     * <p>When set to a positive value, probabilistic tests using this baseline
     * will display warnings as the expiration date approaches, and prominent
     * warnings after expiration.
     *
     * <p><b>Default: 0 (no expiration)</b>
     *
     * <p>Typical values:
     * <ul>
     *   <li>7-14 days: Rapidly evolving systems (LLM APIs, A/B tests)</li>
     *   <li>30 days: Standard recommendation for most use cases</li>
     *   <li>90 days: Stable internal systems</li>
     *   <li>0: Algorithms with no expected drift</li>
     * </ul>
     *
     * @return validity period in days, or 0 for no expiration
     */
    int expiresInDays() default 0;
}
