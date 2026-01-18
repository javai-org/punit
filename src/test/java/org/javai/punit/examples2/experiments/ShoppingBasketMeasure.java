package org.javai.punit.examples2.experiments;

import org.javai.punit.api.Factor;
import org.javai.punit.api.FactorSource;
import org.javai.punit.api.MeasureExperiment;
import org.javai.punit.api.ResultCaptor;
import org.javai.punit.examples2.usecases.ShoppingBasketUseCase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestTemplate;

/**
 * MEASURE experiment for establishing baseline success rate for ShoppingBasketUseCase.
 *
 * <p>This experiment runs 1000 samples to establish a reliable statistical baseline
 * that can be used by probabilistic tests to derive thresholds.
 *
 * <h2>What This Demonstrates</h2>
 * <ul>
 *   <li>{@code @MeasureExperiment} annotation for baseline establishment</li>
 *   <li>Large sample size (1000) for statistical reliability</li>
 *   <li>Spec generation to {@code src/test/resources/punit/specs/}</li>
 *   <li>Use of {@code @FactorSource} for input variation</li>
 * </ul>
 *
 * <h2>Output</h2>
 * <p>Generates: {@code src/test/resources/punit/specs/ShoppingBasketUseCase.yaml}
 *
 * <h2>Running</h2>
 * <pre>{@code
 * ./gradlew test --tests "ShoppingBasketMeasure"
 * }</pre>
 *
 * <h2>Experiment Types: Conceptual Distinction</h2>
 * <p>MEASURE experiments are <b>production artifacts</b>:
 * <ul>
 *   <li>Establish baselines that power probabilistic tests</li>
 *   <li>Must be re-run periodically to keep baselines fresh</li>
 *   <li>Enable reliable regression testing over time</li>
 *   <li>Answer "what is our current reliability?"</li>
 * </ul>
 *
 * <p>This contrasts with EXPLORE experiments (research artifacts) which help
 * discover effective configurations but aren't used in production testing.
 *
 * @see ShoppingBasketUseCase
 * @see org.javai.punit.examples2.tests.ShoppingBasketTest
 */
@Disabled("Example experiment - run manually with ./gradlew test --tests ShoppingBasketMeasure")
public class ShoppingBasketMeasure {

    /**
     * Establishes baseline success rate for the shopping basket instruction translation.
     *
     * <p>Configuration:
     * <ul>
     *   <li>Samples: 1000 (for statistical reliability)</li>
     *   <li>Temperature: 0.3 (fixed, moderate reliability)</li>
     *   <li>Instructions: Cycles through standard instructions</li>
     * </ul>
     *
     * @param useCase the use case instance (injected by PUnit)
     * @param instruction the natural language instruction
     * @param captor records outcomes for aggregation
     */
    @TestTemplate
    @MeasureExperiment(
            useCase = ShoppingBasketUseCase.class,
            samples = 1000,
            experimentId = "baseline-v1"
    )
    @FactorSource(value = "standardInstructions", factors = {"instruction"})
    void measureBaseline(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction,
            ResultCaptor captor
    ) {
        captor.record(useCase.translateInstruction(instruction));
    }

    /**
     * Establishes baseline with a single instruction for controlled measurement.
     *
     * <p>This "Form 1" MEASURE pattern uses one configuration for all samples,
     * isolating LLM behavioral variance from input variance.
     *
     * @param useCase the use case instance
     * @param instruction the instruction (always "Add 2 apples and remove the bread")
     * @param captor records outcomes
     */
    @TestTemplate
    @MeasureExperiment(
            useCase = ShoppingBasketUseCase.class,
            samples = 1000,
            experimentId = "controlled-baseline-v1"
    )
    @FactorSource(value = "singleInstruction", factors = {"instruction"})
    void measureControlledBaseline(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction,
            ResultCaptor captor
    ) {
        captor.record(useCase.translateInstruction(instruction));
    }
}
