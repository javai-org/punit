package org.javai.punit.examples2.experiments;

import java.util.stream.Stream;
import org.javai.punit.api.ExploreExperiment;
import org.javai.punit.api.Factor;
import org.javai.punit.api.FactorArguments;
import org.javai.punit.api.FactorSource;
import org.javai.punit.api.ResultCaptor;
import org.javai.punit.examples2.usecases.ShoppingBasketUseCase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestTemplate;

/**
 * EXPLORE experiment for comparing different instruction phrasings.
 *
 * <p>This experiment tests various instruction styles to understand how
 * different phrasings affect the LLM's ability to produce valid JSON operations.
 *
 * <h2>What This Demonstrates</h2>
 * <ul>
 *   <li>{@code @ExploreExperiment} annotation for configuration comparison</li>
 *   <li>{@code @Factor} annotation for marking parameters as factors</li>
 *   <li>{@code @FactorSource} for defining factor combinations</li>
 *   <li>Fixed temperature (0.1) for controlled comparison</li>
 * </ul>
 *
 * <h2>Output</h2>
 * <p>Generates exploration files in:
 * {@code src/test/resources/punit/explorations/ShoppingBasketUseCase/}
 *
 * <h2>Running</h2>
 * <pre>{@code
 * ./gradlew test --tests "ShoppingBasketExplore"
 * }</pre>
 *
 * <h2>Experiment Types: Conceptual Distinction</h2>
 * <p>EXPLORE experiments are <b>research artifacts</b>:
 * <ul>
 *   <li>Used to discover effective configurations</li>
 *   <li>Run during development/experimentation phase</li>
 *   <li>Help answer "what settings work best?"</li>
 * </ul>
 *
 * <p>Typical workflow:
 * <ol>
 *   <li>Phase 1: Quick exploration (samplesPerConfig=1)</li>
 *   <li>Phase 2: Moderate depth (samplesPerConfig=10)</li>
 *   <li>Winner selection → MEASURE experiment</li>
 * </ol>
 *
 * @see ShoppingBasketUseCase
 * @see ShoppingBasketMeasure
 */
@Disabled("Example experiment - run manually with ./gradlew test --tests ShoppingBasketExplore")
public class ShoppingBasketExplore {

    /**
     * Compares different instruction phrasings at fixed low temperature.
     *
     * <p>Using temperature 0.1 ensures high determinism, making differences
     * in instruction phrasing the primary variable being tested.
     *
     * @param useCase the use case instance (temperature is set to 0.1)
     * @param instruction the instruction to test
     * @param captor records outcomes for comparison
     */
    @TestTemplate
    @ExploreExperiment(
            useCase = ShoppingBasketUseCase.class,
            samplesPerConfig = 10,
            experimentId = "instruction-comparison-v1"
    )
    @FactorSource(value = "exploreInstructions", factors = {"instruction"})
    void compareInstructions(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction,
            ResultCaptor captor
    ) {
        // Use low temperature for controlled comparison
        useCase.setTemperature(0.1);
        captor.record(useCase.translateInstruction(instruction));
    }

    /**
     * Compares different temperature settings for the same instructions.
     *
     * <p>This explores how temperature affects reliability across different
     * instruction styles.
     *
     * @param useCase the use case instance
     * @param instruction the instruction
     * @param temperature the temperature setting
     * @param captor records outcomes
     */
    @TestTemplate
    @ExploreExperiment(
            useCase = ShoppingBasketUseCase.class,
            samplesPerConfig = 10,
            experimentId = "temperature-comparison-v1"
    )
    @FactorSource(value = "temperatureConfigurations", factors = {"instruction", "temperature"})
    void compareTemperatures(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction,
            @Factor("temperature") Double temperature,
            ResultCaptor captor
    ) {
        useCase.setTemperature(temperature);
        captor.record(useCase.translateInstruction(instruction));
    }

    /**
     * Factor source providing temperature configurations to test.
     *
     * @return configurations with instruction + temperature combinations
     */
    public static Stream<FactorArguments> temperatureConfigurations() {
        return FactorArguments.configurations()
                .names("instruction", "temperature")
                // Low temperature (deterministic)
                .values("Add 2 apples", 0.0)
                .values("Add 3 oranges and 2 bananas", 0.0)
                // Medium temperature (balanced)
                .values("Add 2 apples", 0.3)
                .values("Add 3 oranges and 2 bananas", 0.3)
                // Higher temperature (more creative)
                .values("Add 2 apples", 0.7)
                .values("Add 3 oranges and 2 bananas", 0.7)
                .stream();
    }
}
