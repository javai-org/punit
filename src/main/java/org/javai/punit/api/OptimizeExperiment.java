package org.javai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.javai.punit.experiment.engine.ExperimentExtension;
import org.javai.punit.experiment.optimize.FactorMutator;
import org.javai.punit.experiment.optimize.OptimizationIterationAggregate;
import org.javai.punit.experiment.optimize.OptimizationObjective;
import org.javai.punit.experiment.optimize.Scorer;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks a method as an OPTIMIZE experiment that iteratively refines a single
 * control factor to find its optimal value.
 *
 * <p>OPTIMIZE mode is designed for automated parameter tuning through iterative
 * mutation and evaluation. It runs multiple samples per iteration, scores each
 * iteration, and mutates the control factor until termination conditions are met.
 *
 * <h2>When to Use OPTIMIZE</h2>
 * <ul>
 *   <li>After EXPLORE has identified a promising configuration</li>
 *   <li>To automatically tune a single factor (e.g., system prompt)</li>
 *   <li>When manual iteration would be too slow or expensive</li>
 * </ul>
 *
 * <h2>Workflow Context</h2>
 * <pre>{@code
 * EXPLORE → Select winning config → OPTIMIZE one factor → MEASURE (establish baseline)
 * }</pre>
 *
 * <h2>Gradle Task</h2>
 * <pre>{@code ./gradlew optimize}</pre>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @OptimizeExperiment(
 *     useCase = ShoppingUseCase.class,
 *     controlFactor = "systemPrompt",
 *     scorer = SuccessRateScorer.class,
 *     mutator = LLMStringFactorMutator.class,
 *     samplesPerIteration = 20,
 *     maxIterations = 20,
 *     noImprovementWindow = 5
 * )
 * void optimizeSystemPrompt(
 *     ShoppingUseCase useCase,
 *     @ControlFactor String currentPrompt,  // Current value for this iteration
 *     OutcomeCaptor captor
 * ) {
 *     UseCaseOutcome outcome = useCase.searchProducts("wireless headphones");
 *     captor.record(outcome);
 * }
 * }</pre>
 *
 * <h2>Initial Value Resolution</h2>
 * <p>The initial control factor value is resolved in this order:
 * <ol>
 *   <li>{@link #initialControlFactorValue()} - inline value (for short strings, numbers)</li>
 *   <li>{@link #initialControlFactorSource()} - method reference returning the value</li>
 *   <li>{@link FactorGetter} on the use case - fallback to use case's current value</li>
 * </ol>
 *
 * <p>Example with initial value source:
 * <pre>{@code
 * @OptimizeExperiment(
 *     useCase = ShoppingUseCase.class,
 *     controlFactor = "systemPrompt",
 *     initialControlFactorSource = "createMinimalPrompt",
 *     ...
 * )
 * void optimize(...) { }
 *
 * static String createMinimalPrompt() {
 *     return "You are an assistant.";  // Deliberately weak starting point
 * }
 * }</pre>
 *
 * <h2>Output</h2>
 * <p>Produces a history file at: {@code src/test/resources/punit/optimizations/{UseCaseId}/{experimentId}.yaml}
 *
 * <p>The primary output is the best value found for the control factor.
 *
 * @see MeasureExperiment
 * @see ExploreExperiment
 * @see ControlFactor
 * @see FactorGetter
 * @see Scorer
 * @see FactorMutator
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(ExperimentExtension.class)
public @interface OptimizeExperiment {

    /**
     * The use case class to execute.
     *
     * <p>The use case ID is resolved from the class:
     * <ol>
     *   <li>If {@code @UseCase} annotation is present with a value, use that</li>
     *   <li>Otherwise, use the simple class name</li>
     * </ol>
     *
     * @return the use case class
     */
    Class<?> useCase() default Void.class;

    /**
     * Name of the factor to optimize (control factor).
     *
     * <p>Must match a factor name known to the use case (via {@code @FactorSetter}).
     * This is the factor whose value will be mutated between iterations.
     *
     * <p><b>Required.</b>
     *
     * @return the control factor name
     */
    String controlFactor();

    /**
     * Initial value for the control factor.
     *
     * <p>Use this for simple inline values (short strings, numbers as strings).
     * For complex or long values, use {@link #initialControlFactorSource()} instead.
     *
     * <p>If both this and {@link #initialControlFactorSource()} are empty,
     * the initial value is obtained from the use case via {@link FactorGetter}.
     *
     * <p>Mutually exclusive with {@link #initialControlFactorSource()}.
     *
     * @return the initial value as a string, or empty to use other resolution
     */
    String initialControlFactorValue() default "";

    /**
     * Method name that provides the initial control factor value.
     *
     * <p>The method must be static, take no parameters, and return the appropriate type.
     * It is looked up on the experiment class.
     *
     * <p>Example:
     * <pre>{@code
     * @OptimizeExperiment(
     *     controlFactor = "systemPrompt",
     *     initialControlFactorSource = "createStartingPrompt",
     *     ...
     * )
     * void optimize(...) { }
     *
     * static String createStartingPrompt() {
     *     return "You are an assistant that...";
     * }
     * }</pre>
     *
     * <p>Mutually exclusive with {@link #initialControlFactorValue()}.
     *
     * @return the method name, or empty to use other resolution
     */
    String initialControlFactorSource() default "";

    /**
     * Scorer class for evaluating iteration aggregates.
     *
     * <p>Must have a no-arg constructor. The scorer evaluates each iteration's
     * aggregate statistics and returns a scalar score for comparison.
     *
     * <p><b>Required.</b>
     *
     * @return the scorer class
     * @see Scorer
     */
    Class<? extends Scorer<OptimizationIterationAggregate>> scorer();

    /**
     * FactorMutator class for generating new control factor values.
     *
     * <p>Must have a no-arg constructor. The mutator receives the current
     * control factor value and optimization history, and returns a new
     * value to try in the next iteration.
     *
     * <p><b>Required.</b>
     *
     * @return the mutator class
     * @see FactorMutator
     */
    Class<? extends FactorMutator<?>> mutator();

    /**
     * Optimization objective: MAXIMIZE or MINIMIZE.
     *
     * <p>Determines how scores are compared to identify the best iteration.
     * Default: MAXIMIZE (higher scores are better).
     *
     * @return the optimization objective
     */
    OptimizationObjective objective() default OptimizationObjective.MAXIMIZE;

    /**
     * Number of samples per iteration.
     *
     * <p>Each iteration runs this many samples before aggregating results
     * and scoring. Like MEASURE's sample count, but within each iteration.
     *
     * <p>Default: 20.
     *
     * @return the number of samples per iteration
     */
    int samplesPerIteration() default 20;

    /**
     * Maximum number of iterations before termination.
     *
     * <p>Optimization stops when this limit is reached, even if improvement
     * is still occurring.
     *
     * <p>Default: 20.
     *
     * @return the maximum iterations
     */
    int maxIterations() default 20;

    /**
     * Number of consecutive iterations without improvement before termination.
     *
     * <p>If no new best score is found for this many iterations, optimization
     * terminates early.
     *
     * <p>Default: 5.
     *
     * @return the no-improvement window size
     */
    int noImprovementWindow() default 5;

    /**
     * Maximum wall-clock time budget in milliseconds.
     *
     * <p>Applies across ALL iterations combined.
     * 0 = unlimited. Default: 0.
     *
     * @return the time budget in milliseconds
     */
    long timeBudgetMs() default 0;

    /**
     * Maximum token budget for all samples across all iterations.
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
}
