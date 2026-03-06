package org.javai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.javai.punit.experiment.engine.ExperimentExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks a method as an EXPLORE experiment that executes a use case across
 * multiple configurations to compare their effectiveness.
 *
 * <p>EXPLORE mode is designed for rapid configuration comparison with small sample sizes
 * per configuration (default: 1). It produces multiple specs in {@code explorations/}.
 *
 * <h2>When to Use EXPLORE</h2>
 * <ul>
 *   <li>To compare different models, temperatures, or prompt variations</li>
 *   <li>To quickly filter out broken or poor configurations</li>
 *   <li>Before running expensive MEASURE experiments</li>
 * </ul>
 *
 * <h2>Gradle Task</h2>
 * <pre>{@code ./gradlew explore}</pre>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @ExploreExperiment(
 *     useCase = ShoppingUseCase.class,
 *     samplesPerConfig = 1
 * )
 * @FactorSource("modelConfigs")
 * void exploreModels(
 *     @Factor("model") String model,
 *     @Factor("temperature") double temperature,
 *     ShoppingUseCase useCase,
 *     OutcomeCaptor captor
 * ) {
 *     captor.record(useCase.searchProducts("headphones"));
 * }
 *
 * static Stream<FactorArguments> modelConfigs() {
 *     return FactorArguments.configurations()
 *         .names("model", "temperature")
 *         .values("gpt-4", 0.0)
 *         .values("gpt-4", 0.7)
 *         .values("gpt-3.5-turbo", 0.0)
 *         .stream();
 * }
 * }</pre>
 *
 * <h2>Typical Workflow</h2>
 * <ol>
 *   <li>Phase 1: {@code samplesPerConfig = 1} — quick pass to find working configs</li>
 *   <li>Phase 2: {@code samplesPerConfig = 10} — compare promising configs</li>
 *   <li>Final: Switch to {@link MeasureExperiment} with 1000+ samples for chosen config</li>
 * </ol>
 *
 * <h2>Output</h2>
 * <p>Produces spec files at: {@code src/test/resources/punit/explorations/{UseCaseId}/{configName}.yaml}
 *
 * @see MeasureExperiment
 * @see FactorSource
 * @see Factor
 * @see UseCase
 * @see UseCaseProvider
 * @see OutcomeCaptor
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(ExperimentExtension.class)
@Tag("punit-experiment")
public @interface ExploreExperiment {

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
     * Number of samples per configuration.
     *
     * <p>Default: 0 (uses mode default of 1).
     * Increase to 10+ when comparing finalists for statistical rigor.
     *
     * @return the number of samples per config
     */
    int samplesPerConfig() default 1;

    /**
     * Maximum wall-clock time budget in milliseconds.
     *
     * <p>Applies across ALL configs combined.
     * 0 = unlimited. Default: 0.
     *
     * @return the time budget in milliseconds
     */
    long timeBudgetMs() default 0;

    /**
     * Maximum token budget for all samples.
     *
     * <p>Applies across ALL configs combined.
     * 0 = unlimited. Default: 0.
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
     * will display warnings as the expiration date approaches.
     *
     * <p><b>Default: 0 (no expiration)</b>
     *
     * @return validity period in days, or 0 for no expiration
     */
    int expiresInDays() default 0;
}