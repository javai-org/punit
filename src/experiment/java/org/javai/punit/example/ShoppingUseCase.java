package org.javai.punit.example;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.javai.punit.example.shopping.LlmResponse;
import org.javai.punit.example.shopping.Product;
import org.javai.punit.example.shopping.ShoppingAssistant;
import org.javai.punit.experiment.api.UseCase;
import org.javai.punit.experiment.api.UseCaseContext;
import org.javai.punit.experiment.model.UseCaseResult;

/**
 * Use cases for the LLM-powered shopping assistant.
 *
 * <p>These use cases invoke the {@link ShoppingAssistant} and capture
 * observations about the response quality, format compliance, and relevance.
 */
public class ShoppingUseCase {

    private final ShoppingAssistant assistant;

    public ShoppingUseCase(ShoppingAssistant assistant) {
        this.assistant = assistant;
    }

    /**
     * Use case: Search for products matching a query.
     *
     * <p>Captures observations about:
     * <ul>
     *   <li>Whether the response is valid JSON</li>
     *   <li>Whether required fields are present (products, query, totalResults)</li>
     *   <li>Whether all products have required attributes (name, price, category)</li>
     *   <li>Token consumption</li>
     * </ul>
     *
     * @param query the search query
     * @param context the execution context
     * @return observations from the use case execution
     */
    @UseCase(value = "usecase.shopping.search", 
             description = "Search for products matching a natural language query")
    public UseCaseResult searchProducts(String query, UseCaseContext context) {
        Instant start = Instant.now();
        
        LlmResponse response = assistant.searchProducts(query);
        
        Duration executionTime = Duration.between(start, Instant.now());

        // Observe response format
        boolean isValidJson = response.isValidJson();
        boolean hasProductsField = response.hasField("products");
        boolean hasQueryField = response.hasField("query");
        boolean hasTotalResultsField = response.hasField("totalResults");
        boolean hasAllRequiredFields = hasProductsField && hasQueryField && hasTotalResultsField;

        // Observe product quality
        List<Product> products = response.products();
        boolean allProductsHaveRequiredAttributes = products.stream()
                .allMatch(p -> p.name() != null && p.price() != null && p.category() != null);
        
        int productCount = products.size();

        return UseCaseResult.builder()
                .value("isValidJson", isValidJson)
                .value("hasAllRequiredFields", hasAllRequiredFields)
                .value("hasProductsField", hasProductsField)
                .value("hasQueryField", hasQueryField)
                .value("hasTotalResultsField", hasTotalResultsField)
                .value("allProductsHaveRequiredAttributes", allProductsHaveRequiredAttributes)
                .value("productCount", productCount)
                .value("tokensUsed", response.tokensUsed())
                .value("rawJson", response.rawJson())
                .meta("query", query)
                .meta("backend", context.getBackend())
                .executionTime(executionTime)
                .build();
    }

    /**
     * Use case: Search for products within a price range.
     *
     * <p>Captures observations about price constraint compliance in addition
     * to standard format and quality checks.
     *
     * @param query the search query
     * @param maxPrice the maximum price constraint
     * @param context the execution context
     * @return observations from the use case execution
     */
    @UseCase(value = "usecase.shopping.search.price-constrained",
             description = "Search for products within a maximum price constraint")
    public UseCaseResult searchProductsWithPriceConstraint(String query, double maxPrice, UseCaseContext context) {
        Instant start = Instant.now();
        
        LlmResponse response = assistant.searchProducts(query, maxPrice);
        
        Duration executionTime = Duration.between(start, Instant.now());

        // Standard format observations
        boolean isValidJson = response.isValidJson();
        boolean hasAllRequiredFields = response.hasField("products") 
                && response.hasField("query") 
                && response.hasField("totalResults");

        // Price constraint compliance
        List<Product> products = response.products();
        boolean allProductsWithinPriceRange = products.stream()
                .allMatch(p -> p.price() != null && p.price() <= maxPrice);
        
        long productsExceedingPrice = products.stream()
                .filter(p -> p.price() != null && p.price() > maxPrice)
                .count();

        boolean allProductsHaveRequiredAttributes = products.stream()
                .allMatch(p -> p.name() != null && p.price() != null && p.category() != null);

        return UseCaseResult.builder()
                .value("isValidJson", isValidJson)
                .value("hasAllRequiredFields", hasAllRequiredFields)
                .value("allProductsWithinPriceRange", allProductsWithinPriceRange)
                .value("productsExceedingPrice", (int) productsExceedingPrice)
                .value("allProductsHaveRequiredAttributes", allProductsHaveRequiredAttributes)
                .value("productCount", products.size())
                .value("tokensUsed", response.tokensUsed())
                .meta("query", query)
                .meta("maxPrice", maxPrice)
                .meta("backend", context.getBackend())
                .executionTime(executionTime)
                .build();
    }

    /**
     * Use case: Search for products with a result count limit.
     *
     * <p>Captures observations about whether the assistant respects the
     * requested maximum number of results.
     *
     * @param query the search query
     * @param maxResults the maximum number of products to return
     * @param context the execution context
     * @return observations from the use case execution
     */
    @UseCase(value = "usecase.shopping.search.limited-results",
             description = "Search for products with a maximum result count")
    public UseCaseResult searchProductsWithLimit(String query, int maxResults, UseCaseContext context) {
        Instant start = Instant.now();
        
        LlmResponse response = assistant.searchProducts(query, maxResults);
        
        Duration executionTime = Duration.between(start, Instant.now());

        // Standard format observations
        boolean isValidJson = response.isValidJson();
        boolean hasAllRequiredFields = response.hasField("products") 
                && response.hasField("query") 
                && response.hasField("totalResults");

        // Result count compliance
        List<Product> products = response.products();
        boolean respectsResultLimit = products.size() <= maxResults;
        
        // Check if totalResults field matches actual count
        Integer reportedTotal = response.totalResults();
        boolean totalResultsMatchesActual = reportedTotal != null && reportedTotal == products.size();

        return UseCaseResult.builder()
                .value("isValidJson", isValidJson)
                .value("hasAllRequiredFields", hasAllRequiredFields)
                .value("respectsResultLimit", respectsResultLimit)
                .value("totalResultsMatchesActual", totalResultsMatchesActual)
                .value("productCount", products.size())
                .value("requestedMaxResults", maxResults)
                .value("tokensUsed", response.tokensUsed())
                .meta("query", query)
                .meta("maxResults", maxResults)
                .meta("backend", context.getBackend())
                .executionTime(executionTime)
                .build();
    }

    /**
     * Use case: Search for products and evaluate relevance.
     *
     * <p>Captures observations about product relevance scores.
     *
     * @param query the search query
     * @param minRelevanceScore the minimum acceptable relevance score
     * @param context the execution context
     * @return observations from the use case execution
     */
    @UseCase(value = "usecase.shopping.search.relevance",
             description = "Search for products and evaluate result relevance")
    public UseCaseResult searchProductsWithRelevanceCheck(String query, double minRelevanceScore, UseCaseContext context) {
        Instant start = Instant.now();
        
        LlmResponse response = assistant.searchProducts(query);
        
        Duration executionTime = Duration.between(start, Instant.now());

        // Standard format observations
        boolean isValidJson = response.isValidJson();

        // Relevance observations
        List<Product> products = response.products();
        boolean allProductsRelevant = products.stream()
                .allMatch(p -> p.relevanceScore() != null && p.relevanceScore() >= minRelevanceScore);
        
        double averageRelevance = products.stream()
                .filter(p -> p.relevanceScore() != null)
                .mapToDouble(Product::relevanceScore)
                .average()
                .orElse(0.0);
        
        long lowRelevanceCount = products.stream()
                .filter(p -> p.relevanceScore() != null && p.relevanceScore() < minRelevanceScore)
                .count();

        return UseCaseResult.builder()
                .value("isValidJson", isValidJson)
                .value("allProductsRelevant", allProductsRelevant)
                .value("averageRelevance", averageRelevance)
                .value("lowRelevanceCount", (int) lowRelevanceCount)
                .value("productCount", products.size())
                .value("tokensUsed", response.tokensUsed())
                .meta("query", query)
                .meta("minRelevanceScore", minRelevanceScore)
                .meta("backend", context.getBackend())
                .executionTime(executionTime)
                .build();
    }
}
