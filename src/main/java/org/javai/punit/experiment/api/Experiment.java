package org.javai.punit.experiment.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.javai.punit.api.UseCase;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.experiment.engine.ExperimentExtension;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks a method as an experiment that executes a use case repeatedly to gather empirical data.
 *
 * <p>Experiments are exploratory: they produce empirical specs but never pass/fail verdicts.
 * Experiment results are informational only and never gate CI.
 *
 * <h2>Experiment Modes (Mandatory)</h2>
 * <p>You must explicitly choose a mode:
 * <ul>
 *   <li><b>MEASURE</b>: Single configuration, large sample size (1000+)
 *       for precise statistical estimation. Output: {@code specs/}</li>
 *   <li><b>EXPLORE</b>: Multiple configurations from a {@link FactorSource},
 *       small sample size per config (default: 1) for rapid comparison. Output: {@code explorations/}</li>
 * </ul>
 *
 * <h2>Gradle Tasks</h2>
 * <ul>
 *   <li>{@code ./gradlew measure} — runs experiments with {@code mode = MEASURE}</li>
 *   <li>{@code ./gradlew explore} — runs experiments with {@code mode = EXPLORE}</li>
 * </ul>
 *
 * <h2>Example: MEASURE Mode</h2>
 * <pre>{@code
 * @Experiment(
 *     mode = ExperimentMode.MEASURE,
 *     useCase = ShoppingUseCase.class,
 *     samples = 1000,
 *     timeBudgetMs = 600_000
 * )
 * void measureProductSearchBaseline(ShoppingUseCase useCase, ResultCaptor captor) {
 *     captor.record(useCase.searchProducts("headphones", context));
 * }
 * }</pre>
 *
 * <h2>Example: EXPLORE Mode</h2>
 * <pre>{@code
 * @Experiment(
 *     mode = ExperimentMode.EXPLORE,
 *     useCase = ShoppingUseCase.class,
 *     samplesPerConfig = 1  // Default: 1 for fast exploration
 * )
 * @FactorSource("modelConfigs")
 * void exploreModels(
 *     @Factor("model") String model,
 *     @Factor("temperature") double temperature,
 *     UseCaseProvider provider,
 *     ResultCaptor captor
 * ) {
 *     // Configure use case with factor values
 *     provider.register(ShoppingUseCase.class, () ->
 *         new ShoppingUseCase(createAssistant(model, temperature))
 *     );
 *     ShoppingUseCase useCase = provider.getInstance(ShoppingUseCase.class);
 *     captor.record(useCase.searchProducts("headphones"));
 * }
 *
 * static Stream<Arguments> modelConfigs() {
 *     return Stream.of(
 *         Arguments.of("gpt-4", 0.0),
 *         Arguments.of("gpt-4", 0.7),
 *         Arguments.of("gpt-3.5-turbo", 0.0)
 *     );
 * }
 * }</pre>
 *
 * <h2>Key Characteristics</h2>
 * <ul>
 *   <li><strong>No pass/fail</strong>: Experiments never fail (except for infrastructure errors)</li>
 *   <li><strong>Produces empirical specs</strong>: After execution, generates spec file(s)</li>
 *   <li><strong>Never gates CI</strong>: Results are informational only</li>
 *   <li><strong>Use case injection</strong>: Configure via {@link UseCaseProvider}</li>
 *   <li><strong>Result capture</strong>: Use {@link ResultCaptor} to record results for aggregation</li>
 * </ul>
 *
 * @see ExperimentMode
 * @see FactorSource
 * @see Factor
 * @see UseCase
 * @see UseCaseProvider
 * @see ResultCaptor
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(ExperimentExtension.class)
public @interface Experiment {
    
    /**
     * The experiment mode: MEASURE or EXPLORE.
     *
     * <p><b>This is mandatory</b> — you must explicitly choose a mode.
     *
     * <ul>
     *   <li>{@link ExperimentMode#MEASURE}: Single configuration, many samples,
     *       outputs to {@code specs/}</li>
     *   <li>{@link ExperimentMode#EXPLORE}: Multiple configurations via {@link FactorSource},
     *       outputs to {@code explorations/}</li>
     * </ul>
     *
     * @return the experiment mode
     */
    ExperimentMode mode();
    
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
     * The use case ID to execute (legacy, prefer {@link #useCase()} class reference).
     *
     * <p>If both {@code useCaseId} and {@code useCase} are specified, the class reference
     * takes precedence.
     *
     * @return the use case ID
     * @deprecated Use {@link #useCase()} with class reference for type safety
     */
    @Deprecated
    String useCaseId() default "";
    
    /**
     * Number of sample invocations to execute in MEASURE mode.
     *
     * <p>This is used when {@code mode = ExperimentMode.MEASURE}.
     * For EXPLORE mode, use {@link #samplesPerConfig()} instead.
     *
     * <p>Must be ≥ 1. Default: 0 (indicates that the mode's default sample size will be used).
     * A high value such as 1000+ is recommended for reliable statistical estimation.
     *
     * @return the number of samples
     */
    int samples() default 0;
    
    /**
     * Number of samples per configuration in EXPLORE mode.
     *
     * <p>This is used when {@code mode = ExperimentMode.EXPLORE}.
     *
     * <p>Default: 0 (indicates that the mode's default sample size will be used).
     * Increase to 10+ when comparing finalists for statistical rigor.
     *
     * <p>Typical workflow:
     * <ol>
     *   <li>Phase 1: {@code samplesPerConfig = 1} — quick pass to find working configs</li>
     *   <li>Phase 2: {@code samplesPerConfig = 10} — compare promising configs</li>
     *   <li>Final: Switch to MEASURE mode with 1000+ samples for chosen config</li>
     * </ol>
     *
     * @return the number of samples per config
     */
    int samplesPerConfig() default 0;
    
    /**
     * Maximum wall-clock time budget in milliseconds.
     *
     * <p>For multi-config experiments, this applies across ALL configs combined.
     * 0 = unlimited. Default: 0.
     *
     * @return the time budget in milliseconds
     */
    long timeBudgetMs() default 0;
    
    /**
     * Maximum token budget for all samples.
     *
     * <p>For multi-config experiments, this applies across ALL configs combined.
     * 0 = unlimited. Default: 0.
     *
     * @return the token budget
     */
    long tokenBudget() default 0;
    
    /**
     * Unique identifier for this experiment.
     *
     * <p>Required for multi-config experiments; optional for single-config.
     * Used as part of output file naming.
     *
     * @return the experiment ID
     */
    String experimentId() default "";

    /**
     * Output format for spec files.
     *
     * <p>Supported values: "yaml" (default), "json".
     *
     * @return the output format
     */
    String outputFormat() default "yaml";
}
