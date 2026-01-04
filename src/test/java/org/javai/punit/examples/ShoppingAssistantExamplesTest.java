package org.javai.punit.examples;

import static org.assertj.core.api.Assertions.assertThat;
import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.ProbabilisticTestBudget;
import org.javai.punit.api.TokenChargeRecorder;
import org.javai.punit.examples.shopping.usecase.MockShoppingAssistant;
import org.javai.punit.examples.shopping.usecase.ShoppingUseCase;
import org.javai.punit.experiment.api.UseCaseContext;
import org.javai.punit.experiment.model.DefaultUseCaseContext;
import org.javai.punit.experiment.model.UseCaseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;

/**
 * Example probabilistic tests for an LLM-powered shopping assistant.
 *
 * <h2>Scenario</h2>
 * <p>A shopping assistant uses an LLM to find products matching customer criteria.
 * The LLM is expected to return product recommendations in a specific JSON format:
 * <pre>{@code
 * {
 *   "products": [
 *     {
 *       "name": "Product Name",
 *       "price": 29.99,
 *       "category": "Electronics",
 *       "relevanceScore": 0.95
 *     }
 *   ],
 *   "query": "original search query",
 *   "totalResults": 5
 * }
 * }</pre>
 *
 * <h2>Challenges</h2>
 * <p>LLMs are non-deterministic and may:
 * <ul>
 *   <li>Return malformed JSON</li>
 *   <li>Omit required fields</li>
 *   <li>Include products that don't match criteria</li>
 *   <li>Return prices in wrong format (string vs number)</li>
 *   <li>Exceed the requested number of results</li>
 * </ul>
 *
 * <h2>Testing Strategy</h2>
 * <p>These probabilistic tests invoke {@link ShoppingUseCase} methods and assert
 * on the observations captured in the {@link UseCaseResult}. This demonstrates
 * how use cases decouple the act of invoking production code from the act of
 * asserting on outcomes.
 *
 * <p>The tests use token budgets to control LLM API costs during testing.
 */
@Disabled("Example - demonstrates probabilistic testing for LLM shopping assistant")
@ProbabilisticTestBudget(
    tokenBudget = 50000,      // Shared budget across all test methods
    timeBudgetMs = 60000,     // 1 minute max for entire test class
    onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL
)
class ShoppingAssistantExamplesTest {

    private ShoppingUseCase useCase;
    private UseCaseContext context;

    @BeforeEach
    void setUp() {
        // Create use case with mock shopping assistant
        useCase = new ShoppingUseCase(new MockShoppingAssistant());
        context = DefaultUseCaseContext.builder()
                .backend("mock")
                .parameter("simulatedReliability", "default")
                .build();
    }

    // ========== Response Format Validation ==========

    /**
     * Tests that the LLM returns responses in valid JSON format.
     *
     * <p>This is a fundamental requirement - the response must be parseable JSON
     * before we can validate its contents. We expect 90% of responses to be valid.
     */
    @ProbabilisticTest(
        samples = 30,
        minPassRate = 0.90,
        maxExampleFailures = 5
    )
    void shouldReturnValidJsonFormat(TokenChargeRecorder tokenRecorder) {
        String query = "wireless headphones under $100";
        
        UseCaseResult result = useCase.searchProducts(query, context);
        
        // Record tokens from the use case result
        tokenRecorder.recordTokens(result.getInt("tokensUsed", 0));

        // Assert on observations captured by the use case
        assertThat(result.getBoolean("isValidJson", false))
            .as("Response should be valid JSON")
            .isTrue();
    }

    /**
     * Tests that responses include all required fields.
     *
     * <p>A valid response must contain:
     * <ul>
     *   <li>{@code products} - array of product objects</li>
     *   <li>{@code query} - the original search query echoed back</li>
     *   <li>{@code totalResults} - count of returned products</li>
     * </ul>
     */
    @ProbabilisticTest(
        samples = 30,
        minPassRate = 0.85,
        maxExampleFailures = 5
    )
    void shouldIncludeAllRequiredFields(TokenChargeRecorder tokenRecorder) {
        String query = "laptop bag";
        
        UseCaseResult result = useCase.searchProducts(query, context);
        
        tokenRecorder.recordTokens(result.getInt("tokensUsed", 0));

        // Assert on individual field presence observations
        assertThat(result.getBoolean("hasProductsField", false))
            .as("Response should have 'products' field")
            .isTrue();
        assertThat(result.getBoolean("hasQueryField", false))
            .as("Response should have 'query' field")
            .isTrue();
        assertThat(result.getBoolean("hasTotalResultsField", false))
            .as("Response should have 'totalResults' field")
            .isTrue();
    }

    // ========== Product Data Quality ==========

    /**
     * Tests that each product in the response has required attributes.
     *
     * <p>Each product object must include:
     * <ul>
     *   <li>{@code name} - non-empty string</li>
     *   <li>{@code price} - positive number</li>
     *   <li>{@code category} - valid category string</li>
     * </ul>
     */
    @ProbabilisticTest(
        samples = 25,
        minPassRate = 0.85,
        maxExampleFailures = 5
    )
    void shouldReturnProductsWithRequiredAttributes(TokenChargeRecorder tokenRecorder) {
        String query = "running shoes";
        
        UseCaseResult result = useCase.searchProducts(query, context);
        
        tokenRecorder.recordTokens(result.getInt("tokensUsed", 0));

        // The use case already checked if all products have required attributes
        assertThat(result.getBoolean("allProductsHaveRequiredAttributes", false))
            .as("All products should have name, price, and category")
            .isTrue();
    }

    // ========== Search Relevance ==========

    /**
     * Tests that returned products are relevant to the search query.
     *
     * <p>Each product should have a relevance score above a minimum threshold.
     * We accept that some results may be less relevant, but at least 80% of
     * responses should contain only highly relevant products.
     */
    @ProbabilisticTest(
        samples = 20,
        minPassRate = 0.80,
        maxExampleFailures = 5
    )
    void shouldReturnRelevantProducts(TokenChargeRecorder tokenRecorder) {
        String query = "bluetooth speaker waterproof";
        double minRelevanceScore = 0.7;
        
        UseCaseResult result = useCase.searchProductsWithRelevanceCheck(query, minRelevanceScore, context);
        
        tokenRecorder.recordTokens(result.getInt("tokensUsed", 0));

        // Assert that all products meet the relevance threshold
        assertThat(result.getBoolean("allProductsRelevant", false))
            .as("All products should have relevance score >= %.1f (average was %.2f)", 
                minRelevanceScore, result.getDouble("averageRelevance", 0.0))
            .isTrue();
    }

    // ========== Filter Compliance ==========

    /**
     * Tests that price range filters are respected.
     *
     * <p>When a maximum price is specified, all returned products should
     * be at or below that price. LLMs sometimes include products slightly
     * above the limit, so we allow for some failures.
     */
    @ProbabilisticTest(
        samples = 25,
        minPassRate = 0.85,
        maxExampleFailures = 5
    )
    void shouldRespectPriceRangeFilter(TokenChargeRecorder tokenRecorder) {
        String query = "gift ideas under $50";
        double maxPrice = 50.00;
        
        UseCaseResult result = useCase.searchProductsWithPriceConstraint(query, maxPrice, context);
        
        tokenRecorder.recordTokens(result.getInt("tokensUsed", 0));

        // Assert that all products are within price range
        assertThat(result.getBoolean("allProductsWithinPriceRange", false))
            .as("All products should be under $%.2f (found %d exceeding price)", 
                maxPrice, result.getInt("productsExceedingPrice", 0))
            .isTrue();
    }

    // ========== Result Count Limits ==========

    /**
     * Tests that the number of results respects the requested limit.
     *
     * <p>When a maximum result count is specified, the LLM should not
     * return more products than requested.
     */
    @ProbabilisticTest(
        samples = 20,
        minPassRate = 0.95,
        maxExampleFailures = 2
    )
    void shouldRespectResultCountLimit(TokenChargeRecorder tokenRecorder) {
        String query = "coffee makers";
        int maxResults = 5;
        
        UseCaseResult result = useCase.searchProductsWithLimit(query, maxResults, context);
        
        tokenRecorder.recordTokens(result.getInt("tokensUsed", 0));

        // Assert on result limit compliance
        assertThat(result.getBoolean("respectsResultLimit", false))
            .as("Should return at most %d products (got %d)", 
                maxResults, result.getInt("productCount", 0))
            .isTrue();
    }

    /**
     * Tests that the totalResults field matches the actual product count.
     */
    @ProbabilisticTest(
        samples = 20,
        minPassRate = 0.90,
        maxExampleFailures = 3
    )
    void shouldHaveConsistentResultCount(TokenChargeRecorder tokenRecorder) {
        String query = "office supplies";
        int maxResults = 5;
        
        UseCaseResult result = useCase.searchProductsWithLimit(query, maxResults, context);
        
        tokenRecorder.recordTokens(result.getInt("tokensUsed", 0));

        // Assert that reported total matches actual count
        assertThat(result.getBoolean("totalResultsMatchesActual", false))
            .as("totalResults field should match actual product count")
            .isTrue();
    }

    // ========== Combined Validation ==========

    /**
     * Tests that responses are both valid JSON AND have all required fields.
     *
     * <p>This is a stricter test that combines format validation with field presence.
     */
    @ProbabilisticTest(
        samples = 30,
        minPassRate = 0.80,
        maxExampleFailures = 5
    )
    void shouldReturnCompleteValidResponse(TokenChargeRecorder tokenRecorder) {
        String query = "smartphone accessories";
        
        UseCaseResult result = useCase.searchProducts(query, context);
        
        tokenRecorder.recordTokens(result.getInt("tokensUsed", 0));

        // Combined assertion: valid JSON AND all required fields
        assertThat(result.getBoolean("isValidJson", false))
            .as("Response should be valid JSON")
            .isTrue();
        assertThat(result.getBoolean("hasAllRequiredFields", false))
            .as("Response should have all required fields")
            .isTrue();
    }
}
