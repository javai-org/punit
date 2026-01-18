package org.javai.punit.examples2.experiments;

import org.javai.punit.api.OptimizeExperiment;
import org.javai.punit.api.ResultCaptor;
import org.javai.punit.api.TreatmentValue;
import org.javai.punit.examples2.usecases.ShoppingBasketUseCase;
import org.javai.punit.experiment.optimize.OptimizationObjective;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestTemplate;

/**
 * OPTIMIZE experiment for tuning the temperature numeric parameter.
 *
 * <p>This experiment demonstrates numeric parameter optimization by
 * searching for the optimal temperature value that maximizes success rate.
 *
 * <h2>What This Demonstrates</h2>
 * <ul>
 *   <li>{@code @OptimizeExperiment} with numeric treatment factor</li>
 *   <li>{@link TemperatureMutator} for gradient-like numeric search</li>
 *   <li>Balancing reliability vs. creativity (implicit in temperature)</li>
 * </ul>
 *
 * <h2>Temperature Trade-offs</h2>
 * <ul>
 *   <li>Lower temperature (0.0-0.3): More deterministic, higher reliability</li>
 *   <li>Higher temperature (0.7-1.0): More creative, but more errors</li>
 * </ul>
 *
 * <p>The optimizer will find the sweet spot where reliability is maximized
 * while maintaining acceptable response quality.
 *
 * <h2>Output</h2>
 * <p>Generates: {@code src/test/resources/punit/optimizations/ShoppingBasketUseCase/}
 *
 * <h2>Running</h2>
 * <pre>{@code
 * ./gradlew test --tests "ShoppingBasketOptimizeTemperature"
 * }</pre>
 *
 * @see ShoppingBasketUseCase
 * @see TemperatureMutator
 * @see SuccessRateScorer
 */
@Disabled("Example experiment - run manually with ./gradlew test --tests ShoppingBasketOptimizeTemperature")
public class ShoppingBasketOptimizeTemperature {

    /**
     * Optimizes temperature to maximize success rate.
     *
     * <p>The mutator ({@link TemperatureMutator}) uses a gradient-like search:
     * <ul>
     *   <li>Starts at 0.5 (middle of range)</li>
     *   <li>Adjusts based on improvement direction</li>
     *   <li>Reduces step size when oscillating</li>
     *   <li>Respects bounds [0.0, 1.0]</li>
     * </ul>
     *
     * @param useCase the use case instance
     * @param temperature the current temperature setting
     * @param captor records outcomes for scoring
     */
    @TestTemplate
    @OptimizeExperiment(
            useCase = ShoppingBasketUseCase.class,
            treatmentFactor = "temperature",
            scorer = SuccessRateScorer.class,
            mutator = TemperatureMutator.class,
            objective = OptimizationObjective.MAXIMIZE,
            samplesPerIteration = 25,
            maxIterations = 15,
            noImprovementWindow = 4,
            experimentId = "temperature-optimization-v1"
    )
    void optimizeTemperature(
            ShoppingBasketUseCase useCase,
            @TreatmentValue("temperature") Double temperature,
            ResultCaptor captor
    ) {
        // Temperature is automatically set via @FactorSetter
        // Run with a fixed instruction
        captor.record(useCase.translateInstruction("Add 2 apples and remove the bread"));
    }
}
