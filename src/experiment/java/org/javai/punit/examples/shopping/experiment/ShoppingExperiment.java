package org.javai.punit.examples.shopping.experiment;

import org.javai.punit.examples.shopping.usecase.MockShoppingAssistant;
import org.javai.punit.examples.shopping.usecase.ShoppingUseCase;
import org.javai.punit.experiment.api.Experiment;
import org.javai.punit.experiment.api.ExperimentContext;
import org.junit.jupiter.api.BeforeEach;

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
 * <h2>Usage</h2>
 * <p>Experiments use JUnit's {@code @TestTemplate} mechanism under the hood.
 * Run them using the {@code experimentTests} Gradle task:
 * <pre>{@code
 * ./gradlew experimentTests --tests "ShoppingExperiment"
 * }</pre>
 *
 * <p>Or run a specific experiment method:
 * <pre>{@code
 * ./gradlew experimentTests --tests "ShoppingExperiment.measureBasicSearchReliability"
 * }</pre>
 *
 * <h2>Output</h2>
 * <p>Each experiment generates a baseline file in:
 * <pre>
 * src/test/resources/punit/baselines/
 * </pre>
 *
 * <p>These baselines can then be used to create execution specifications
 * for probabilistic conformance tests.
 *
 * <h2>Implementation Note</h2>
 * <p>The {@code experimentTests} task is a standard JUnit {@code Test} task
 * configured for the {@code src/experiment/java} source set. This provides
 * full IDE integration, debugging support, and familiar Gradle test filtering.
 *
 * @see org.javai.punit.examples.shopping.usecase.ShoppingUseCase
 */
public class ShoppingExperiment extends ShoppingUseCase {

    /**
     * Creates a ShoppingExperiment with a mock shopping assistant.
     * 
     * <p>The mock assistant simulates LLM behavior with configurable
     * reliability levels for testing different scenarios.
     */
    public ShoppingExperiment() {
        super(new MockShoppingAssistant());
    }

    // ========== Basic Search Experiments ==========

    /**
     * Experiment: Measure basic product search reliability.
     *
     * <p>Gathers empirical data about:
     * <ul>
     *   <li>JSON validity rate</li>
     *   <li>Required field presence rate</li>
     *   <li>Product attribute completeness</li>
     *   <li>Token consumption per query</li>
     * </ul>
     *
     * <p>Expected outcome: ~90% success rate for valid JSON with all required fields.
     */
    @Experiment(
        useCase = "usecase.shopping.search",
        samples = 100,
        tokenBudget = 50000,
        timeBudgetMs = 120000,
        experimentId = "shopping-search-baseline"
    )
    @ExperimentContext(
        backend = "mock",
        parameters = {
            "simulatedReliability = default",
            "query = wireless headphones"
        }
    )
    void measureBasicSearchReliability() {
        // Method body is optional—execution is driven by the use case
    }

    /**
     * Experiment: Measure search reliability with high-reliability configuration.
     *
     * <p>Uses a more reliable mock configuration to establish an upper bound
     * on expected success rates.
     */
    @Experiment(
        useCase = "usecase.shopping.search",
        samples = 100,
        tokenBudget = 50000,
        timeBudgetMs = 120000,
        experimentId = "shopping-search-high-reliability"
    )
    @ExperimentContext(
        backend = "mock",
        parameters = {
            "simulatedReliability = high",
            "query = laptop bag"
        }
    )
    void measureSearchWithHighReliability() {
        // Method body is optional—execution is driven by the use case
    }

    // ========== Price Constraint Experiments ==========

    /**
     * Experiment: Measure price constraint compliance.
     *
     * <p>Gathers empirical data about how well the assistant respects
     * maximum price filters. Key metrics:
     * <ul>
     *   <li>Rate of responses with all products within price range</li>
     *   <li>Average number of price violations per response</li>
     *   <li>Correlation between price limit and violation rate</li>
     * </ul>
     */
    @Experiment(
        useCase = "usecase.shopping.search.price-constrained",
        samples = 100,
        tokenBudget = 50000,
        timeBudgetMs = 120000,
        experimentId = "shopping-price-constraint-baseline"
    )
    @ExperimentContext(
        backend = "mock",
        parameters = {
            "simulatedReliability = default",
            "query = gift ideas",
            "maxPrice = 50.00"
        }
    )
    void measurePriceConstraintCompliance() {
        // Method body is optional—execution is driven by the use case
    }

    /**
     * Experiment: Measure price constraint with tight budget.
     *
     * <p>Tests behavior with a very low price limit to understand
     * edge case handling.
     */
    @Experiment(
        useCase = "usecase.shopping.search.price-constrained",
        samples = 50,
        tokenBudget = 25000,
        timeBudgetMs = 60000,
        experimentId = "shopping-price-constraint-tight"
    )
    @ExperimentContext(
        backend = "mock",
        parameters = {
            "simulatedReliability = default",
            "query = budget accessories",
            "maxPrice = 20.00"
        }
    )
    void measureTightPriceConstraint() {
        // Method body is optional—execution is driven by the use case
    }

    // ========== Result Limit Experiments ==========

    /**
     * Experiment: Measure result count limit compliance.
     *
     * <p>Gathers empirical data about how well the assistant respects
     * the requested maximum number of results. Key metrics:
     * <ul>
     *   <li>Rate of responses respecting the limit</li>
     *   <li>Consistency between totalResults field and actual count</li>
     * </ul>
     */
    @Experiment(
        useCase = "usecase.shopping.search.limited-results",
        samples = 100,
        tokenBudget = 50000,
        timeBudgetMs = 120000,
        experimentId = "shopping-result-limit-baseline"
    )
    @ExperimentContext(
        backend = "mock",
        parameters = {
            "simulatedReliability = default",
            "query = coffee makers",
            "maxResults = 5"
        }
    )
    void measureResultLimitCompliance() {
        // Method body is optional—execution is driven by the use case
    }

    // ========== Relevance Experiments ==========

    /**
     * Experiment: Measure product relevance quality.
     *
     * <p>Gathers empirical data about the relevance of returned products.
     * Key metrics:
     * <ul>
     *   <li>Rate of responses where all products meet minimum relevance</li>
     *   <li>Average relevance score across all responses</li>
     *   <li>Distribution of low-relevance product counts</li>
     * </ul>
     */
    @Experiment(
        useCase = "usecase.shopping.search.relevance",
        samples = 100,
        tokenBudget = 50000,
        timeBudgetMs = 120000,
        experimentId = "shopping-relevance-baseline"
    )
    @ExperimentContext(
        backend = "mock",
        parameters = {
            "simulatedReliability = default",
            "query = bluetooth speaker waterproof",
            "minRelevanceScore = 0.7"
        }
    )
    void measureProductRelevance() {
        // Method body is optional—execution is driven by the use case
    }

    /**
     * Experiment: Measure relevance with stricter threshold.
     *
     * <p>Uses a higher minimum relevance score to understand
     * the upper bound of relevance quality.
     */
    @Experiment(
        useCase = "usecase.shopping.search.relevance",
        samples = 50,
        tokenBudget = 25000,
        timeBudgetMs = 60000,
        experimentId = "shopping-relevance-strict"
    )
    @ExperimentContext(
        backend = "mock",
        parameters = {
            "simulatedReliability = high",
            "query = premium headphones noise cancelling",
            "minRelevanceScore = 0.85"
        }
    )
    void measureStrictRelevanceThreshold() {
        // Method body is optional—execution is driven by the use case
    }

    // ========== Comparative Experiments ==========

    /**
     * Experiment: Compare reliability across different mock configurations.
     *
     * <p>Uses low reliability configuration to establish a lower bound
     * and understand degraded behavior patterns.
     */
    @Experiment(
        useCase = "usecase.shopping.search",
        samples = 100,
        tokenBudget = 50000,
        timeBudgetMs = 120000,
        experimentId = "shopping-search-low-reliability"
    )
    @ExperimentContext(
        backend = "mock",
        parameters = {
            "simulatedReliability = low",
            "query = electronics sale"
        }
    )
    void measureSearchWithLowReliability() {
        // Method body is optional—execution is driven by the use case
    }
}
