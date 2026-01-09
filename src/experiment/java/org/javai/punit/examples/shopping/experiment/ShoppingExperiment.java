package org.javai.punit.examples.shopping.experiment;

import java.util.Random;
import java.util.stream.Stream;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.examples.shopping.usecase.MockShoppingAssistant;
import org.javai.punit.examples.shopping.usecase.ShoppingUseCase;
import org.javai.punit.experiment.api.Experiment;
import org.javai.punit.experiment.api.ExperimentMode;
import org.javai.punit.experiment.api.Factor;
import org.javai.punit.experiment.api.FactorArguments;
import org.javai.punit.experiment.api.FactorSource;
import org.javai.punit.experiment.api.ResultCaptor;
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
     * <h2>Output</h2>
     * <pre>
     * src/test/resources/punit/explorations/ShoppingUseCase/
     * ├── model-gpt-4_temp-0.0_query-wireless_headphones.yaml
     * └── ... (one file per configuration)
     * </pre>
     */
    @Experiment(
        mode = ExperimentMode.EXPLORE,
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
        captor.record(useCase.searchProducts(query));
    }

    /**
     * Factor source: Explicit configurations to explore.
     *
     * <p>Names declared once, then each configuration listed explicitly.
     * This is clearer than Cartesian products and reflects real experiment design.
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
     *   <li>No context parameter needed - configuration is internal to use case</li>
     *   <li>Method is minimal: just invoke and record</li>
     * </ul>
     *
     * @param useCase the shopping use case (injected, pre-configured)
     * @param captor the result captor
     */
    @Experiment(
        mode = ExperimentMode.MEASURE,
        useCase = ShoppingUseCase.class,
        // samples = 1000, not necessary because MEASURE mode implies a default sample size
        tokenBudget = 500000,
        timeBudgetMs = 600000,
        experimentId = "shopping-search-realistic-v1"
    )
    void measureRealisticSearchBaseline(ShoppingUseCase useCase, ResultCaptor captor) {
        // Clean! Use case is pre-configured. Just invoke and record.
        captor.record(useCase.searchProducts("wireless headphones"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ADDITIONAL EXPERIMENTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Additional measurement experiment with tighter budgets.
     */
    @Experiment(
        mode = ExperimentMode.MEASURE,
        useCase = ShoppingUseCase.class,
        samples = 1000,
        tokenBudget = 50000,
        timeBudgetMs = 120000,
        experimentId = "shopping-search-baseline"
    )
    void measureBasicSearchReliability(ShoppingUseCase useCase, ResultCaptor captor) {
        captor.record(useCase.searchProducts("wireless headphones"));
    }
}
