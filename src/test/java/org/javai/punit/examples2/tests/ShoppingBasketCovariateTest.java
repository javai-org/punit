package org.javai.punit.examples2.tests;

import org.javai.punit.api.Factor;
import org.javai.punit.api.FactorSource;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.examples2.usecases.ShoppingBasketUseCase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestTemplate;

/**
 * Demonstrates the covariate system for environment-aware baseline matching.
 *
 * <p>Covariates are contextual factors that may influence system behavior but
 * aren't directly controlled by the test. PUnit uses covariates to:
 * <ul>
 *   <li>Record environmental context in baselines</li>
 *   <li>Match tests to appropriate baselines</li>
 *   <li>Explain unexpected variance in results</li>
 * </ul>
 *
 * <h2>What This Demonstrates</h2>
 * <ul>
 *   <li>{@code StandardCovariate.WEEKDAY_VERSUS_WEEKEND} - Built-in temporal covariate</li>
 *   <li>{@code StandardCovariate.TIME_OF_DAY} - Built-in temporal covariate</li>
 *   <li>Custom covariates via {@code @Covariate} in {@code @UseCase}</li>
 *   <li>{@code @CovariateSource} methods for custom covariate values</li>
 *   <li>{@code CovariateCategory} - How covariates affect baseline matching</li>
 * </ul>
 *
 * <h2>Covariate Categories</h2>
 * <ul>
 *   <li><b>TEMPORAL</b> - Time-based (weekday/weekend, time of day)</li>
 *   <li><b>CONFIGURATION</b> - System configuration (model, temperature)</li>
 *   <li><b>OPERATIONAL</b> - Operational context (region, environment)</li>
 * </ul>
 *
 * <h2>How ShoppingBasketUseCase Uses Covariates</h2>
 * <p>The use case is annotated with:
 * <pre>{@code
 * @UseCase(
 *     covariates = {WEEKDAY_VERSUS_WEEKEND, TIME_OF_DAY},
 *     categorizedCovariates = {
 *         @Covariate(key = "llm_model", category = CONFIGURATION),
 *         @Covariate(key = "temperature", category = CONFIGURATION)
 *     }
 * )
 * }</pre>
 *
 * <p>The {@code @CovariateSource} methods provide values:
 * <pre>{@code
 * @CovariateSource("llm_model")
 * public String getModel() { return model; }
 *
 * @CovariateSource("temperature")
 * public double getTemperature() { return temperature; }
 * }</pre>
 *
 * <h2>Running</h2>
 * <pre>{@code
 * ./gradlew test --tests "ShoppingBasketCovariateTest"
 * }</pre>
 *
 * @see ShoppingBasketUseCase
 */
@Disabled("Example test - run manually after generating baseline")
public class ShoppingBasketCovariateTest {

    /**
     * Test that demonstrates automatic covariate recording.
     *
     * <p>When this test runs:
     * <ul>
     *   <li>PUnit captures current temporal covariates (weekday/weekend, time of day)</li>
     *   <li>Retrieves configuration covariates from use case methods</li>
     *   <li>Records all covariates in the test results</li>
     *   <li>Uses covariates to find matching baseline</li>
     * </ul>
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     */
    @TestTemplate
    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 50
    )
    @FactorSource(value = "standardInstructions", factors = {"instruction"})
    void testWithAutomaticCovariates(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction
    ) {
        useCase.translateInstruction(instruction).assertAll();
    }

    /**
     * Test with explicitly configured model covariate.
     *
     * <p>By setting the model explicitly, this test will match baselines
     * that were recorded with the same model setting.
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     */
    @TestTemplate
    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 50
    )
    @FactorSource(value = "standardInstructions", factors = {"instruction"})
    void testWithExplicitModelCovariate(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction
    ) {
        // Set model to a specific value
        // This affects the "llm_model" covariate via @CovariateSource
        useCase.setModel("gpt-4-turbo");

        useCase.translateInstruction(instruction).assertAll();
    }

    /**
     * Test with different temperature settings.
     *
     * <p>Temperature affects the "temperature" configuration covariate.
     * Different temperatures will match different baselines (if available).
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     */
    @TestTemplate
    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 50
    )
    @FactorSource(value = "standardInstructions", factors = {"instruction"})
    void testWithLowTemperature(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction
    ) {
        // Low temperature for high reliability
        useCase.setTemperature(0.1);

        useCase.translateInstruction(instruction).assertAll();
    }

    /**
     * Test demonstrating covariate impact on baseline selection.
     *
     * <p>When baselines exist for multiple covariate combinations, PUnit
     * selects the most appropriate baseline based on current covariate values.
     * If no exact match exists, it may use a default or report the mismatch.
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     */
    @TestTemplate
    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 50
    )
    @FactorSource(value = "standardInstructions", factors = {"instruction"})
    void testWithHighTemperature(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction
    ) {
        // Higher temperature - may have different reliability characteristics
        useCase.setTemperature(0.7);

        useCase.translateInstruction(instruction).assertAll();
    }
}
