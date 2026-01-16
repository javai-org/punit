package org.javai.punit.examples.shopping.experiment;

import java.util.Random;
import java.util.stream.Stream;
import org.javai.punit.api.Pacing;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.examples.shopping.usecase.MockShoppingAssistant;
import org.javai.punit.examples.shopping.usecase.ShoppingUseCase;
import org.javai.punit.api.ExploreExperiment;
import org.javai.punit.api.Factor;
import org.javai.punit.api.MeasureExperiment;
import org.javai.punit.api.FactorArguments;
import org.javai.punit.api.FactorSource;
import org.javai.punit.api.ResultCaptor;
import org.javai.punit.model.UseCaseOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Experiments for the LLM-powered shopping assistant.
 *
 * <h2>Purpose</h2>
 * <p>These experiments execute use cases repeatedly to gather empirical data about
 * the shopping assistant's behavior. The results are used to:
 * <ul>
 *   <li>Establish baseline success rates for different operations</li>
 *   <li>Understand failure mode distribution</li>
 *   <li>Measure token consumption patterns</li>
 *   <li>Inform appropriate pass rate thresholds for probabilistic tests</li>
 * </ul>
 *
 * <h2>Key Design: Configuration at Construction</h2>
 * <p>Use case configuration (model, temperature, etc.) is provided at construction
 * time, not at each method call. The use case instance encapsulates its complete
 * configuration. This is clean separation of concerns:
 * <ul>
 *   <li><b>Configuration factors</b> - Control how use case is built (handled by provider)</li>
 *   <li><b>Input factors</b> - Vary the inputs to use case methods (passed to method)</li>
 * </ul>
 *
 * <h2>UseCaseOutcome Pattern</h2>
 * <p>Use case methods now return {@link UseCaseOutcome}, which bundles both the
 * result and its success criteria. This ensures type-safe binding and evaluator
 * consistency between experiments and tests.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * # Run MEASURE experiments (generate specs in src/test/resources/punit/specs/)
 * ./gradlew measure --tests "ShoppingExperiment.measureRealisticSearchBaseline"
 *
 * # Run EXPLORE experiments (generate specs in src/test/resources/punit/explorations/)
 * ./gradlew explore --tests "ShoppingExperiment.exploreModelConfigurations"
 * }</pre>
 *
 * @see ShoppingUseCase
 * @see UseCaseProvider
 */
public class ShoppingExperiment {

    // ═══════════════════════════════════════════════════════════════════════════
    // USE CASE PROVIDER CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * The use case provider handles construction and injection of use cases.
     */
    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    /**
     * Configures the use case provider before each experiment sample.
     *
     * <h2>Two Registration Patterns</h2>
     * <ul>
     *   <li><b>Regular factory</b> - For MEASURE mode with fixed configuration</li>
     *   <li><b>Factor factory</b> - For EXPLORE mode with configuration from factors</li>
     * </ul>
     */
    @BeforeEach
    void setUp() {
        // MEASURE mode: Fixed configuration with ~95% success rate
        provider.register(ShoppingUseCase.class, () ->
            new ShoppingUseCase(
                new MockShoppingAssistant(
                    new Random(),
                    MockShoppingAssistant.MockConfiguration.experimentRealistic()
                ),
                "default",
                0.0
            )
        );
        
        // EXPLORE mode: Auto-wired factory
        // @FactorSetter annotations on ShoppingUseCase handle model/temp injection!
        // The provider automatically calls setModel() and setTemperature().
        provider.registerAutoWired(ShoppingUseCase.class, () ->
            new ShoppingUseCase(
                new MockShoppingAssistant(new Random(), MockShoppingAssistant.MockConfiguration.defaultConfig())
            )
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EXPLORE MODE EXPERIMENT - Factor-based exploration
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * EXPLORE experiment: Compare different model, temperature, and query configurations.
     *
     * <h2>Clean Design</h2>
     * <ul>
     *   <li>Factor names are defined WITH values via {@link FactorArguments#withFactors()}</li>
     *   <li>Use case is injected PRE-CONFIGURED with model/temperature</li>
     *   <li>Use {@code @Factor} to inject any factor you need directly</li>
     *   <li>Method only declares parameters it actually uses</li>
     * </ul>
     *
     * <h2>UseCaseOutcome</h2>
     * <p>The use case method returns a {@link UseCaseOutcome} containing both the
     * result and its success criteria, ensuring type-safe binding.
     *
     * <h2>Output</h2>
     * <pre>
     * src/test/resources/punit/explorations/ShoppingUseCase/
     * ├── model-gpt-4_temp-0.0_query-wireless_headphones.yaml
     * └── ... (one file per configuration)
     * </pre>
     */
    @ExploreExperiment(
        useCase = ShoppingUseCase.class,
        // samplesPerConfig = 1, not necessary because EXPLORE mode implies a default sample size
        experimentId = "explore-model-configs"
    )
    @FactorSource("modelConfigurations")
    void exploreModelConfigurations(
            ShoppingUseCase useCase,       // Pre-configured with model/temp
            @Factor("query") String query, // Injected directly - clean!
            ResultCaptor captor
    ) {
        UseCaseOutcome outcome = useCase.searchProducts(query);
        captor.record(outcome);  // Records both result and criteria
    }

    /**
     * Factor source for EXPLORE experiments: Explicit configurations to explore.
     *
     * <p>Names declared once, then each configuration listed explicitly.
     * This is clearer than Cartesian products and reflects real experiment design.
     *
     * <p><b>Location:</b> Defined locally within the experiment class because
     * these factors are specific to exploration. MEASURE experiments and
     * probabilistic tests use the shared source in {@link ShoppingUseCase}.
     */
    static Stream<FactorArguments> modelConfigurations() {
        return FactorArguments.configurations()
            .names("model", "temp", "query")
            .values("gpt-4", 0.0, "wireless headphones")
            .values("gpt-4", 0.7, "wireless headphones")
            .values("gpt-4", 0.0, "laptop stand")
            .values("gpt-4", 0.7, "laptop stand")
            .values("gpt-3.5-turbo", 0.0, "wireless headphones")
            .values("gpt-3.5-turbo", 0.7, "wireless headphones")
            .values("gpt-3.5-turbo", 0.0, "laptop stand")
            .values("gpt-3.5-turbo", 0.7, "laptop stand")
            .stream();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIMARY MEASURE EXPERIMENT (1000 samples)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * MEASURE experiment: Establish reliable statistics for product search.
     *
     * <p>This is the primary experiment for generating a statistically reliable
     * spec. It runs 1000 samples with fixed configuration (~95% success rate).
     *
     * <h2>Key Design Points</h2>
     * <ul>
     *   <li>Use case is pre-configured by UseCaseProvider (set in @BeforeEach)</li>
     *   <li>Factor source is co-located with the use case (recommended pattern)</li>
     *   <li>The same factor source should be used by probabilistic tests</li>
     * </ul>
     *
     * <h2>UseCaseOutcome</h2>
     * <p>The use case method returns a {@link UseCaseOutcome} containing both the
     * result and its success criteria. Recording the outcome automatically records
     * both the raw result values and the criteria for aggregation.
     *
     * <h2>Factor Consistency</h2>
     * <p>The {@code @FactorSource} annotation references a method in {@link ShoppingUseCase}.
     * When probabilistic tests reference the same factor source, PUnit can verify
     * that both the experiment and the test use identical inputs, ensuring
     * statistical consistency.
     *
     * @param useCase the shopping use case (injected, pre-configured)
     * @param query the search query (injected from factor source)
     * @param captor the result captor
     */
    @MeasureExperiment(
        useCase = ShoppingUseCase.class,
        //samples = 1000, // not necessary because MEASURE mode implies a default sample size
        tokenBudget = 500000,
        timeBudgetMs = 600000,
        experimentId = "shopping-search-realistic-v1"
    )
    @FactorSource("ShoppingUseCase#standardProductQueries")
    //@Pacing(maxRequestsPerMinute = 30)
    void measureRealisticSearchBaseline(
            ShoppingUseCase useCase,
            @Factor("query") String query,
            ResultCaptor captor) {
        // Model and temp were determined as the result of the EXPLORE phase, hence:
        useCase.setModel("gpt-4");
        useCase.setTemperature(0.7);
        
        // Query varies from factor source (cycling through representative inputs)
        UseCaseOutcome outcome = useCase.searchProducts(query);
        captor.record(outcome);  // Records both result and criteria
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ADDITIONAL EXPERIMENTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Additional measurement experiment with tighter budgets.
     *
     * <p>Demonstrates using the extended factor source for broader query coverage.
     */
    @MeasureExperiment(
        useCase = ShoppingUseCase.class,
        samples = 1000,
        tokenBudget = 50000,
        timeBudgetMs = 120000,
        experimentId = "shopping-search-baseline"
    )
    @FactorSource("ShoppingUseCase#extendedProductQueries")
    void measureBasicSearchReliability(
            ShoppingUseCase useCase,
            @Factor("query") String query,
            ResultCaptor captor) {
        // Configuration determined during EXPLORE phase - set directly
        useCase.setModel("gpt-4");
        useCase.setTemperature(0.7);
        
        // Query varies from factor source (cycling through extended inputs)
        UseCaseOutcome outcome = useCase.searchProducts(query);
        captor.record(outcome);  // Records both result and criteria
    }
}
