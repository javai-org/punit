package org.javai.punit.examples.tests;

import java.util.stream.Stream;
import org.javai.punit.api.InputSource;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.examples.usecases.ShoppingBasketUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;

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
 *   <li>{@code covariateDayOfWeek} - Day-of-week partitioning via {@code @DayGroup}</li>
 *   <li>{@code covariateTimeOfDay} - Time-of-day partitioning</li>
 *   <li>Custom covariates via {@code @Covariate} in {@code @UseCase}</li>
 *   <li>{@code @CovariateSource} methods for custom covariate values</li>
 *   <li>{@code CovariateCategory} - How covariates affect baseline matching</li>
 * </ul>
 *
 * <h2>Covariate Categories</h2>
 * <ul>
 *   <li><b>TEMPORAL</b> - Time-based (day of week, time of day)</li>
 *   <li><b>CONFIGURATION</b> - System configuration (model, temperature)</li>
 *   <li><b>OPERATIONAL</b> - Operational context (region, timezone)</li>
 * </ul>
 *
 * <h2>How ShoppingBasketUseCase Uses Covariates</h2>
 * <p>The use case is annotated with:
 * <pre>{@code
 * @UseCase(
 *     covariateDayOfWeek = { @DayGroup({SATURDAY, SUNDAY}) },
 *     covariateTimeOfDay = { "08:00/4h", "16:00/4h" },
 *     covariates = {
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

    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    @BeforeEach
    void setUp() {
        provider.register(ShoppingBasketUseCase.class, ShoppingBasketUseCase::new);
    }

    /**
     * Test that demonstrates automatic covariate recording.
     *
     * <p>When this test runs:
     * <ul>
     *   <li>PUnit captures current temporal covariates (day of week, time of day)</li>
     *   <li>Retrieves configuration covariates from use case methods</li>
     *   <li>Records all covariates in the test results</li>
     *   <li>Uses covariates to find matching baseline</li>
     * </ul>
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     */

    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 50
    )
    @InputSource("standardInstructions")
    void testWithAutomaticCovariates(
            ShoppingBasketUseCase useCase,
            String instruction
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

    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 50
    )
    @InputSource("standardInstructions")
    void testWithExplicitModelCovariate(
            ShoppingBasketUseCase useCase,
            String instruction
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

    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 50
    )
    @InputSource("standardInstructions")
    void testWithLowTemperature(
            ShoppingBasketUseCase useCase,
            String instruction
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

    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 50
    )
    @InputSource("standardInstructions")
    void testWithHighTemperature(
            ShoppingBasketUseCase useCase,
            String instruction
    ) {
        // Higher temperature - may have different reliability characteristics
        useCase.setTemperature(0.7);

        useCase.translateInstruction(instruction).assertAll();
    }

    static Stream<String> standardInstructions() {
        return Stream.of(
                "Add 2 apples",
                "Remove the milk",
                "Add 1 loaf of bread",
                "Add 3 oranges and 2 bananas",
                "Clear the basket"
        );
    }
}
