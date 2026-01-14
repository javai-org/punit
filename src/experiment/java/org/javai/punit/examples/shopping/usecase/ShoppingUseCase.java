package org.javai.punit.examples.shopping.usecase;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.javai.punit.api.FactorSetter;
import org.javai.punit.api.StandardCovariate;
import org.javai.punit.api.UseCase;
import org.javai.punit.api.UseCaseContract;
import org.javai.punit.api.FactorArguments;
import org.javai.punit.model.UseCaseCriteria;
import org.javai.punit.model.UseCaseOutcome;
import org.javai.punit.model.UseCaseResult;

/**
 * Use cases for the LLM-powered shopping assistant.
 *
 * <p>This class encapsulates the business logic for product search operations
 * powered by an LLM. It captures observations about response quality, format
 * compliance, and relevance for probabilistic testing.
 *
 * <h2>Use Case ID</h2>
 * <p>Implements {@link UseCaseContract} to provide the use case ID. The default
 * implementation returns the simple class name ("ShoppingUseCase"), used for:
 * <ul>
 *   <li>Baseline file naming: {@code baselines/ShoppingUseCase.yaml}</li>
 *   <li>Spec file location: {@code specs/ShoppingUseCase/v1.yaml}</li>
 *   <li>Linking experiments and tests to specifications</li>
 *   <li>Covariates declated in the {@code @UseCase} accotation improve statistical robustness by making experiments
 *   and tests sensitive to environmental temporal factors</li>
 * </ul>
 *
 * <h2>Success Criteria</h2>
 * <p>Each use case method returns a {@link UseCaseOutcome} bundling the result
 * with method-specific success criteria. The {@link UseCaseContract#criteria}
 * method uses the default (trivial) implementation since criteria are per-method.
 *
 * <h2>Configuration</h2>
 * <p>Configuration (model, temperature, etc.) is provided at construction time,
 * not at each method call. This follows the principle that the use case instance
 * encapsulates its complete configuration.
 *
 * <h2>Dependency Injection</h2>
 * <p>Instances are created by {@link org.javai.punit.api.UseCaseProvider}.
 * In EXPLORE mode, the provider creates differently-configured instances
 * for each factor combination.
 *
 * @see org.javai.punit.api.UseCaseProvider
 * @see UseCaseContract
 */
@UseCase(covariates = { StandardCovariate.WEEKDAY_VERSUS_WEEKEND, StandardCovariate.TIME_OF_DAY, StandardCovariate.REGION })
public class ShoppingUseCase implements UseCaseContract {

    private ShoppingAssistant assistant;
    private String model = "default";
    private double temperature = 0.0;

    /**
     * Creates a use case with the given assistant and default configuration.
     */
    public ShoppingUseCase(ShoppingAssistant assistant) {
        this.assistant = assistant;
    }

    /**
     * Creates a use case with explicit configuration.
     *
     * <p>In EXPLORE mode, the UseCaseProvider creates instances with different
     * model/temperature combinations to compare configurations.
     *
     * @param assistant the shopping assistant implementation
     * @param model the model name (for metadata in results)
     * @param temperature the temperature setting (for metadata in results)
     */
    public ShoppingUseCase(ShoppingAssistant assistant, String model, double temperature) {
        this.assistant = assistant;
        this.model = model;
        this.temperature = temperature;
    }

    /**
     * Returns the model name for this use case configuration.
     */
    public String getModel() {
        return model;
    }

    /**
     * Returns the temperature setting for this use case configuration.
     */
    public double getTemperature() {
        return temperature;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // FACTOR PARAMETER SETTERS - For auto-wired factor injection
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Sets the model and reconfigures the assistant accordingly.
     *
     * <p>Called automatically by {@link org.javai.punit.api.UseCaseProvider}
     * when using {@code registerAutoWired} in EXPLORE mode.
     *
     * @param model the model name (e.g., "gpt-4", "gpt-3.5-turbo")
     */
    @FactorSetter("model")
    public void setModel(String model) {
        this.model = model;
        reconfigureAssistant();
    }
    
    /**
     * Sets the temperature and reconfigures the assistant accordingly.
     *
     * <p>Called automatically by {@link org.javai.punit.api.UseCaseProvider}
     * when using {@code registerAutoWired} in EXPLORE mode.
     *
     * @param temperature the temperature setting (0.0 to 1.0)
     */
    @FactorSetter("temp")
    public void setTemperature(double temperature) {
        this.temperature = temperature;
        reconfigureAssistant();
    }
    
    /**
     * Reconfigures the mock assistant based on current model/temperature.
     *
     * <p>Different configurations simulate different LLM reliability profiles.
     */
    private void reconfigureAssistant() {
        if (assistant instanceof MockShoppingAssistant mock) {
            MockShoppingAssistant.MockConfiguration config;
            if ("gpt-4".equals(model)) {
                config = temperature == 0.0 
                    ? MockShoppingAssistant.MockConfiguration.highReliability()
                    : MockShoppingAssistant.MockConfiguration.experimentRealistic();
            } else {
                config = temperature == 0.0
                    ? MockShoppingAssistant.MockConfiguration.experimentRealistic()
                    : MockShoppingAssistant.MockConfiguration.defaultConfig();
            }
            mock.setConfiguration(config);
        }
    }

    /**
     * Search for products matching a query.
     *
     * <p>The result contains <b>raw observations</b> only:
     * <ul>
     *   <li>Format observations: isValidJson, field presence flags</li>
     *   <li>Content observations: productCount, rawJson</li>
     *   <li>Resource usage: tokensUsed</li>
     * </ul>
     *
     * <p>The criteria define <b>success conditions</b> (postconditions):
     * <ul>
     *   <li>Valid JSON format</li>
     *   <li>Required fields present</li>
     *   <li>Products have required attributes</li>
     * </ul>
     *
     * @param query the search query
     * @return outcome containing result and success criteria
     */
    public UseCaseOutcome searchProducts(String query) {
        Instant start = Instant.now();
        
        LlmResponse response = assistant.searchProducts(query);
        
        Duration executionTime = Duration.between(start, Instant.now());

        // ═══════════════════════════════════════════════════════════════════
        // RAW OBSERVATIONS - factual, neutral, no judgments
        // ═══════════════════════════════════════════════════════════════════
        
        // Format observations
        boolean isValidJson = response.isValidJson();
        boolean hasProductsField = response.hasField("products");
        boolean hasQueryField = response.hasField("query");
        boolean hasTotalResultsField = response.hasField("totalResults");

        // Content observations
        List<Product> products = response.products();
        int productCount = products.size();
        boolean allProductsHaveRequiredAttributes = !products.isEmpty() && products.stream()
                .allMatch(p -> p.name() != null && p.price() != null && p.category() != null);

        UseCaseResult result = UseCaseResult.builder()
                // Format observations
                .value("isValidJson", isValidJson)
                .value("hasProductsField", hasProductsField)
                .value("hasQueryField", hasQueryField)
                .value("hasTotalResultsField", hasTotalResultsField)
                .value("allProductsHaveRequiredAttributes", allProductsHaveRequiredAttributes)
                // Content observations
                .value("productCount", productCount)
                .value("rawJson", response.rawJson())
                // Resource usage
                .value("tokensUsed", response.tokensUsed())
                // Context
                .meta("query", query)
                .meta("model", model)
                .meta("temperature", temperature)
                .executionTime(executionTime)
                .build();

        // ═══════════════════════════════════════════════════════════════════
        // SUCCESS CRITERIA - postconditions that define "success"
        // ═══════════════════════════════════════════════════════════════════
        
        UseCaseCriteria criteria = UseCaseCriteria.ordered()
            .criterion("Valid JSON", 
                () -> result.getBoolean("isValidJson", false))
            .criterion("Has required fields", 
                () -> result.getBoolean("hasProductsField", false)
                    && result.getBoolean("hasQueryField", false)
                    && result.getBoolean("hasTotalResultsField", false))
            .criterion("Products have required attributes", 
                () -> result.getBoolean("allProductsHaveRequiredAttributes", false))
            .build();

        return new UseCaseOutcome(result, criteria);
    }

    /**
     * Search for products within a price constraint.
     *
     * @param query the search query
     * @param maxPrice the maximum price constraint
     * @return outcome containing result and success criteria
     */
    public UseCaseOutcome searchProductsWithPriceConstraint(String query, double maxPrice) {
        Instant start = Instant.now();
        
        LlmResponse response = assistant.searchProducts(query, maxPrice);
        
        Duration executionTime = Duration.between(start, Instant.now());

        // Raw observations
        boolean isValidJson = response.isValidJson();
        boolean hasProductsField = response.hasField("products");
        boolean hasQueryField = response.hasField("query");
        boolean hasTotalResultsField = response.hasField("totalResults");

        List<Product> products = response.products();
        int productCount = products.size();
        
        boolean allProductsHaveRequiredAttributes = !products.isEmpty() && products.stream()
                .allMatch(p -> p.name() != null && p.price() != null && p.category() != null);
        
        boolean allProductsWithinPriceRange = products.stream()
                .allMatch(p -> p.price() != null && p.price() <= maxPrice);
        
        int productsExceedingPrice = (int) products.stream()
                .filter(p -> p.price() != null && p.price() > maxPrice)
                .count();

        UseCaseResult result = UseCaseResult.builder()
                .value("isValidJson", isValidJson)
                .value("hasProductsField", hasProductsField)
                .value("hasQueryField", hasQueryField)
                .value("hasTotalResultsField", hasTotalResultsField)
                .value("allProductsHaveRequiredAttributes", allProductsHaveRequiredAttributes)
                .value("allProductsWithinPriceRange", allProductsWithinPriceRange)
                .value("productsExceedingPrice", productsExceedingPrice)
                .value("productCount", productCount)
                .value("tokensUsed", response.tokensUsed())
                .meta("query", query)
                .meta("maxPrice", maxPrice)
                .meta("model", model)
                .meta("temperature", temperature)
                .executionTime(executionTime)
                .build();

        UseCaseCriteria criteria = UseCaseCriteria.ordered()
            .criterion("Valid JSON", 
                () -> result.getBoolean("isValidJson", false))
            .criterion("Has required fields", 
                () -> result.getBoolean("hasProductsField", false)
                    && result.getBoolean("hasQueryField", false)
                    && result.getBoolean("hasTotalResultsField", false))
            .criterion("Products have required attributes", 
                () -> result.getBoolean("allProductsHaveRequiredAttributes", false))
            .criterion("All products within price range", 
                () -> result.getBoolean("allProductsWithinPriceRange", false))
            .build();

        return new UseCaseOutcome(result, criteria);
    }

    /**
     * Search for products with a result count limit.
     *
     * @param query the search query
     * @param maxResults the maximum number of products to return
     * @return outcome containing result and success criteria
     */
    public UseCaseOutcome searchProductsWithLimit(String query, int maxResults) {
        Instant start = Instant.now();
        
        LlmResponse response = assistant.searchProducts(query, maxResults);
        
        Duration executionTime = Duration.between(start, Instant.now());

        List<Product> products = response.products();
        int productCount = products.size();
        boolean respectsResultLimit = productCount <= maxResults;
        
        Integer reportedTotal = response.totalResults();
        boolean totalResultsMatchesActual = reportedTotal != null && reportedTotal == productCount;

        UseCaseResult result = UseCaseResult.builder()
                .value("isValidJson", response.isValidJson())
                .value("hasProductsField", response.hasField("products"))
                .value("hasQueryField", response.hasField("query"))
                .value("hasTotalResultsField", response.hasField("totalResults"))
                .value("respectsResultLimit", respectsResultLimit)
                .value("totalResultsMatchesActual", totalResultsMatchesActual)
                .value("productCount", productCount)
                .value("reportedTotalResults", reportedTotal)
                .value("tokensUsed", response.tokensUsed())
                .meta("query", query)
                .meta("maxResults", maxResults)
                .meta("model", model)
                .meta("temperature", temperature)
                .executionTime(executionTime)
                .build();

        UseCaseCriteria criteria = UseCaseCriteria.ordered()
            .criterion("Valid JSON", 
                () -> result.getBoolean("isValidJson", false))
            .criterion("Has required fields", 
                () -> result.getBoolean("hasProductsField", false)
                    && result.getBoolean("hasQueryField", false)
                    && result.getBoolean("hasTotalResultsField", false))
            .criterion("Respects result limit", 
                () -> result.getBoolean("respectsResultLimit", false))
            .build();

        return new UseCaseOutcome(result, criteria);
    }

    /**
     * Search for products and evaluate relevance.
     *
     * @param query the search query
     * @param minRelevanceScore the minimum acceptable relevance score
     * @return outcome containing result and success criteria
     */
    public UseCaseOutcome searchProductsWithRelevanceCheck(String query, double minRelevanceScore) {
        Instant start = Instant.now();
        
        LlmResponse response = assistant.searchProducts(query);
        
        Duration executionTime = Duration.between(start, Instant.now());

        // Raw observations
        boolean isValidJson = response.isValidJson();

        List<Product> products = response.products();
        int productCount = products.size();
        
        boolean allProductsRelevant = products.stream()
                .allMatch(p -> p.relevanceScore() != null && p.relevanceScore() >= minRelevanceScore);
        
        double averageRelevance = products.stream()
                .filter(p -> p.relevanceScore() != null)
                .mapToDouble(Product::relevanceScore)
                .average()
                .orElse(0.0);
        
        int lowRelevanceCount = (int) products.stream()
                .filter(p -> p.relevanceScore() != null && p.relevanceScore() < minRelevanceScore)
                .count();

        UseCaseResult result = UseCaseResult.builder()
                .value("isValidJson", isValidJson)
                .value("allProductsRelevant", allProductsRelevant)
                .value("averageRelevance", averageRelevance)
                .value("lowRelevanceCount", lowRelevanceCount)
                .value("productCount", productCount)
                .value("tokensUsed", response.tokensUsed())
                .meta("query", query)
                .meta("minRelevanceScore", minRelevanceScore)
                .meta("model", model)
                .meta("temperature", temperature)
                .executionTime(executionTime)
                .build();

        UseCaseCriteria criteria = UseCaseCriteria.ordered()
            .criterion("Valid JSON", 
                () -> result.getBoolean("isValidJson", false))
            .criterion("All products relevant", 
                () -> result.getBoolean("allProductsRelevant", false))
            .build();

        return new UseCaseOutcome(result, criteria);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FACTOR SOURCE PROVIDERS - Co-located with use case for consistency
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Single-query factor source for controlled MEASURE experiments.
     *
     * <p><b>Why a single-entry factor source?</b> This may seem unusual, but it's
     * often the <em>ideal</em> choice for MEASURE experiments:
     *
     * <ul>
     *   <li><b>Statistical purity</b>: All 1000 samples use the exact same input,
     *       isolating the LLM's behavioral variance from input variance</li>
     *   <li><b>Clean baseline</b>: The resulting spec reflects behavior for THIS
     *       specific query, not a blend of different query characteristics</li>
     *   <li><b>Reproducibility</b>: The baseline is unambiguously tied to a single,
     *       well-understood input</li>
     * </ul>
     *
     * <p>This is the "Form 1" MEASURE pattern: one configuration, many samples.
     * Use {@link #standardProductQueries()} for "Form 2": varied inputs.
     *
     * <h2>Usage</h2>
     * <pre>{@code
     * @Experiment(mode = MEASURE, samples = 1000, ...)
     * @FactorSource("ShoppingUseCase#singleQuery")
     * void measureBaseline(ShoppingUseCase useCase, @Factor("query") String query, ...) {
     *     // query is always "wireless headphones" for all 1000 samples
     *     captor.record(useCase.searchProducts(query));
     * }
     * }</pre>
     *
     * @return a single factor argument used for all samples
     */
    public static List<FactorArguments> singleQuery() {
        return FactorArguments.configurations()
            .names("query")
            .values("wireless headphones")
            .stream().toList();
    }

    /**
     * Production-representative factor source for MEASURE experiments and probabilistic tests.
     *
     * <p>This factor source provides a fixed set of product queries that represent
     * typical production traffic. Both MEASURE experiments and probabilistic tests
     * should use this same source to ensure statistical consistency.
     *
     * <p>With {@code samples = 1000} and 10 queries, each query is used ~100 times
     * (cycling behavior). This is the "Form 2" MEASURE pattern: varied inputs,
     * establishing a baseline across representative production queries.
     *
     * <h2>Usage in MEASURE Experiment</h2>
     * <pre>{@code
     * @Experiment(mode = MEASURE, samples = 1000, ...)
     * @FactorSource("ShoppingUseCase#standardProductQueries")
     * void measureBaseline(ShoppingUseCase useCase, @Factor("query") String query, ...) { ... }
     * }</pre>
     *
     * <h2>Usage in Probabilistic Test</h2>
     * <pre>{@code
     * @ProbabilisticTest(samples = 100, useCase = ShoppingUseCase.class)
     * @FactorSource("ShoppingUseCase#standardProductQueries")
     * void testProductSearch(ShoppingUseCase useCase, @Factor("query") String query, ...) { ... }
     * }</pre>
     *
     * @return factor arguments representing production-like queries
     */
    public static List<FactorArguments> standardProductQueries() {
        return FactorArguments.configurations()
            .names("query")
            .values("wireless headphones")
            .values("laptop stand")
            .values("USB-C hub")
            .values("mechanical keyboard")
            .values("webcam 4k")
            .values("bluetooth speaker")
            .values("monitor arm")
            .values("gaming mouse")
            .values("desk lamp")
            .values("cable management")
            .stream().toList();
    }

    /**
     * Extended factor source for comprehensive MEASURE experiments.
     *
     * <p>A larger set of queries covering various product categories and search patterns.
     * Use this when you need more variety in your baseline measurements.
     *
     * @return extended factor arguments for broader coverage
     */
    public static List<FactorArguments> extendedProductQueries() {
        return FactorArguments.configurations()
            .names("query")
            // Electronics
            .values("wireless headphones")
            .values("laptop stand")
            .values("USB-C hub")
            .values("mechanical keyboard")
            .values("webcam 4k")
            // Audio
            .values("bluetooth speaker waterproof")
            .values("noise cancelling earbuds")
            .values("microphone for streaming")
            // Office supplies
            .values("monitor arm dual")
            .values("desk organizer")
            .values("ergonomic chair")
            .values("standing desk converter")
            // Gaming
            .values("gaming mouse rgb")
            .values("controller wireless")
            .values("gaming headset")
            // Miscellaneous
            .values("portable charger")
            .values("wireless charging pad")
            .values("smart home hub")
            .values("fitness tracker")
            .values("tablet stylus")
            .stream().toList();
    }
}
