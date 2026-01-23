package org.javai.punit.examples.experiments;

import org.javai.punit.api.ControlFactor;
import org.javai.punit.api.OptimizeExperiment;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.examples.usecases.ShoppingBasketUseCase;
import org.javai.punit.experiment.optimize.OptimizationObjective;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * OPTIMIZE experiment demonstrating temperature's effect on structured output reliability.
 *
 * <p>This experiment shows the relationship between LLM temperature and response
 * quality for structured JSON output tasks. Starting from a naive temperature of 1.0,
 * it systematically decreases to 0.0, revealing how lower temperatures improve
 * reliability for tasks requiring precise formatting.
 *
 * <h2>The Story</h2>
 * <p>A developer might naively set temperature=1.0 thinking "more creative responses
 * are better." This experiment demonstrates why that's wrong for structured output:
 * <ul>
 *   <li><b>Temperature 1.0</b>: High "creativity" leads to format deviations,
 *       hallucinated field names, and invalid values (~50% success)</li>
 *   <li><b>Temperature 0.5</b>: Moderate reliability (~75% success)</li>
 *   <li><b>Temperature 0.0</b>: Deterministic, follows instructions precisely (~100% success)</li>
 * </ul>
 *
 * <h2>Expected Progression</h2>
 * <pre>
 * Iteration  0: temp=1.0  →  ~50% success (high hallucination)
 * Iteration  1: temp=0.9  →  ~55% success
 * Iteration  2: temp=0.8  →  ~60% success
 * ...
 * Iteration 10: temp=0.0  →  ~100% success (deterministic)
 * </pre>
 *
 * <h2>What This Demonstrates</h2>
 * <ul>
 *   <li>{@code @OptimizeExperiment} with numeric control factor</li>
 *   <li>Simple linear search strategy via {@link TemperatureMutator}</li>
 *   <li>Clear correlation between parameter and success rate</li>
 *   <li>Practical insight: use low temperature for structured output tasks</li>
 * </ul>
 *
 * <h2>Output</h2>
 * <p>Generates: {@code src/test/resources/punit/optimizations/ShoppingBasketUseCase/}
 *
 * <h2>Running</h2>
 * <pre>{@code
 * ./gradlew exp -Prun=ShoppingBasketOptimizeTemperature
 * }</pre>
 *
 * @see ShoppingBasketUseCase
 * @see TemperatureMutator
 * @see ShoppingBasketSuccessRateScorer
 */
@Disabled("Example experiment - run manually with ./gradlew exp -Prun=ShoppingBasketOptimizeTemperature")
public class ShoppingBasketOptimizeTemperature {

    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    @BeforeEach
    void setUp() {
        provider.register(ShoppingBasketUseCase.class, ShoppingBasketUseCase::new);
    }

    /**
     * Provides the naive starting temperature of 1.0.
     *
     * <p>This represents a developer's naive assumption that "more creativity
     * is better" - maximum temperature for maximum expressiveness. The experiment
     * will show why this is wrong for structured output tasks.
     *
     * @return 1.0 (maximum temperature)
     */
    static Double naiveStartingTemperature() {
        return 1.0;
    }

    /**
     * Optimizes temperature by linear decrease from 1.0 to 0.0.
     *
     * <p>The {@link TemperatureMutator} decreases temperature by 0.1 each iteration,
     * starting from the naive value of 1.0. The experiment demonstrates that for
     * structured JSON output, lower temperatures yield higher success rates.
     *
     * @param useCase the use case instance
     * @param temperature the current temperature setting (1.0 → 0.0)
     * @param captor records outcomes for scoring
     */
    @TestTemplate
    @OptimizeExperiment(
            useCase = ShoppingBasketUseCase.class,
            controlFactor = "temperature",
            initialControlFactorSource = "naiveStartingTemperature",
            scorer = ShoppingBasketSuccessRateScorer.class,
            mutator = TemperatureMutator.class,
            objective = OptimizationObjective.MAXIMIZE,
            samplesPerIteration = 20,
            maxIterations = 11,  // Covers 1.0, 0.9, 0.8, ... 0.0
            noImprovementWindow = 20,  // Disable early termination - run all iterations
            experimentId = "temperature-optimization-v1"
    )
    void optimizeTemperature(
            ShoppingBasketUseCase useCase,
            @ControlFactor("temperature") Double temperature,
            OutcomeCaptor captor
    ) {
        // Temperature is automatically set via @FactorSetter on the use case
        assert useCase.getTemperature() == temperature;
        // Run with a fixed instruction
        captor.record(useCase.translateInstruction("Add 2 apples and remove the bread"));
    }
}
