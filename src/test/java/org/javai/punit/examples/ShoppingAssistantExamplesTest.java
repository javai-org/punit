package org.javai.punit.examples;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Random;
import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.ProbabilisticTestBudget;
import org.javai.punit.api.TokenChargeRecorder;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.examples.shopping.usecase.MockShoppingAssistant;
import org.javai.punit.examples.shopping.usecase.ShoppingUseCase;
import org.javai.punit.model.UseCaseOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Example probabilistic tests for an LLM-powered shopping assistant.
 *
 * <p><b>NOTE:</b> These tests contain failing samples BY DESIGN. They use simulated
 * LLM behavior with intentional failure rates to demonstrate probabilistic testing.
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
 * <h2>Post-EXPLORE Configuration</h2>
 * <p>These tests assume the EXPLORE phase has already determined optimal settings:
 * <ul>
 *   <li>Model: gpt-4</li>
 *   <li>Temperature: 0.7</li>
 * </ul>
 * <p>Each test explicitly sets these values, as they are now fixed post-EXPLORE.
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
 * on the observations captured in the {@link UseCaseOutcome}. The use case methods
 * now return a unified outcome containing both the result and its success criteria.
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

	// ═══════════════════════════════════════════════════════════════════════════
	// USE CASE PROVIDER (INSTANCE-OWNED)
	// ═══════════════════════════════════════════════════════════════════════════

	@RegisterExtension
	UseCaseProvider provider = new UseCaseProvider();

	@BeforeEach
	void setUp() {
		provider.register(ShoppingUseCase.class, () ->
			new ShoppingUseCase(
				new MockShoppingAssistant(
					new Random(),
					MockShoppingAssistant.MockConfiguration.experimentRealistic()
				),
				"gpt-4",    // llm_model - matches expected configuration
				0.7         // temperature - matches expected configuration
			)
		);
	}

	// ========== Response Format Validation ==========

	/**
	 * Tests that the LLM returns responses in valid JSON format.
	 *
	 * <p>This is a fundamental requirement - the response must be parseable JSON
	 * before we can validate its contents. We expect 90% of responses to be valid.
	 */
	@ProbabilisticTest(
			useCase = ShoppingUseCase.class,
			samples = 30,
			// TODO replace hard-coded params with spec-driven probabilistic test
			minPassRate = 0.90,
			maxExampleFailures = 5
	)
	void shouldReturnValidJsonFormat(
			ShoppingUseCase useCase,
			TokenChargeRecorder tokenRecorder) {
		UseCaseOutcome outcome = useCase.searchProducts("wireless headphones");

		// Record tokens from the use case result
		tokenRecorder.recordTokens(outcome.result().getInt("tokensUsed", 0));

		// Assert on observations captured by the use case
		assertThat(outcome.result().getBoolean("isValidJson", false))
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
			useCase = ShoppingUseCase.class,
			samples = 30,
			// TODO replace hard-coded params with spec-driven probabilistic test
			minPassRate = 0.85,
			maxExampleFailures = 5
	)
	void shouldIncludeAllRequiredFields(
			ShoppingUseCase useCase,
			TokenChargeRecorder tokenRecorder) {
		UseCaseOutcome outcome = useCase.searchProducts("laptop stand");

		tokenRecorder.recordTokens(outcome.result().getInt("tokensUsed", 0));

		// Assert on individual field presence observations
		assertThat(outcome.result().getBoolean("hasProductsField", false))
				.as("Response should have 'products' field")
				.isTrue();
		assertThat(outcome.result().getBoolean("hasQueryField", false))
				.as("Response should have 'query' field")
				.isTrue();
		assertThat(outcome.result().getBoolean("hasTotalResultsField", false))
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
			useCase = ShoppingUseCase.class,
			samples = 25,
			// TODO replace hard-coded params with spec-driven probabilistic test
			minPassRate = 0.85,
			maxExampleFailures = 5
	)
	void shouldReturnProductsWithRequiredAttributes(
			ShoppingUseCase useCase,
			TokenChargeRecorder tokenRecorder) {
		UseCaseOutcome outcome = useCase.searchProducts("USB-C hub");

		tokenRecorder.recordTokens(outcome.result().getInt("tokensUsed", 0));

		// The use case already checked if all products have required attributes
		assertThat(outcome.result().getBoolean("allProductsHaveRequiredAttributes", false))
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
			useCase = ShoppingUseCase.class,
			samples = 20,
			// TODO replace hard-coded params with spec-driven probabilistic test
			minPassRate = 0.80,
			maxExampleFailures = 5
	)
	void shouldReturnRelevantProducts(
			ShoppingUseCase useCase,
			TokenChargeRecorder tokenRecorder) {
		double minRelevanceScore = 0.7;

		UseCaseOutcome outcome = useCase.searchProductsWithRelevanceCheck("mechanical keyboard", minRelevanceScore);

		tokenRecorder.recordTokens(outcome.result().getInt("tokensUsed", 0));

		// Assert that all products meet the relevance threshold
		assertThat(outcome.result().getBoolean("allProductsRelevant", false))
				.as("All products should have relevance score >= %.1f (average was %.2f)",
						minRelevanceScore, outcome.result().getDouble("averageRelevance", 0.0))
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
			useCase = ShoppingUseCase.class,
			samples = 25,
			// TODO replace hard-coded params with spec-driven probabilistic test
			minPassRate = 0.85,
			maxExampleFailures = 5
	)
	void shouldRespectPriceRangeFilter(
			ShoppingUseCase useCase,
			TokenChargeRecorder tokenRecorder) {
		double maxPrice = 50.00;

		UseCaseOutcome outcome = useCase.searchProductsWithPriceConstraint("webcam 4k", maxPrice);

		tokenRecorder.recordTokens(outcome.result().getInt("tokensUsed", 0));

		// Assert that all products are within price range
		assertThat(outcome.result().getBoolean("allProductsWithinPriceRange", false))
				.as("All products should be under $%.2f (found %d exceeding price)",
						maxPrice, outcome.result().getInt("productsExceedingPrice", 0))
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
			useCase = ShoppingUseCase.class,
			samples = 20,
			// TODO replace hard-coded params with spec-driven probabilistic test
			minPassRate = 0.95,
			maxExampleFailures = 2
	)
	void shouldRespectResultCountLimit(
			ShoppingUseCase useCase,
			TokenChargeRecorder tokenRecorder) {
		int maxResults = 5;

		UseCaseOutcome outcome = useCase.searchProductsWithLimit("bluetooth speaker waterproof", maxResults);

		tokenRecorder.recordTokens(outcome.result().getInt("tokensUsed", 0));

		// Assert on result limit compliance
		assertThat(outcome.result().getBoolean("respectsResultLimit", false))
				.as("Should return at most %d products (got %d)",
						maxResults, outcome.result().getInt("productCount", 0))
				.isTrue();
	}

	/**
	 * Tests that the totalResults field matches the actual product count.
	 */
	@ProbabilisticTest(
			useCase = ShoppingUseCase.class,
			samples = 20,
			// TODO replace hard-coded params with spec-driven probabilistic test
			minPassRate = 0.90,
			maxExampleFailures = 3
	)
	void shouldHaveConsistentResultCount(
			ShoppingUseCase useCase,
			TokenChargeRecorder tokenRecorder) {
		int maxResults = 5;

		UseCaseOutcome outcome = useCase.searchProductsWithLimit("noise cancelling earbuds", maxResults);

		tokenRecorder.recordTokens(outcome.result().getInt("tokensUsed", 0));

		// Assert that reported total matches actual count
		assertThat(outcome.result().getBoolean("totalResultsMatchesActual", false))
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
			useCase = ShoppingUseCase.class,
			samples = 30,
			// TODO replace hard-coded params with spec-driven probabilistic test
			minPassRate = 0.80,
			maxExampleFailures = 5
	)
	void shouldReturnCompleteValidResponse(
			ShoppingUseCase useCase,
			TokenChargeRecorder tokenRecorder) {
		UseCaseOutcome outcome = useCase.searchProducts("microphone for streaming");

		tokenRecorder.recordTokens(outcome.result().getInt("tokensUsed", 0));

		// Combined assertion: valid JSON AND all required fields
		assertThat(outcome.result().getBoolean("isValidJson", false))
				.as("Response should be valid JSON")
				.isTrue();
		assertThat(outcome.result().getBoolean("hasAllRequiredFields", false))
				.as("Response should have all required fields")
				.isTrue();
	}

	// ========== Using Success Criteria ==========

	/**
	 * Tests using the use case's success criteria bundled with the outcome.
	 *
	 * <p>This demonstrates the recommended pattern: the use case method returns
	 * a {@link UseCaseOutcome} containing both the result and its success criteria,
	 * ensuring type-safe binding and consistency between experiments and tests.
	 *
	 * <h2>Benefits</h2>
	 * <ul>
	 *   <li><b>Type safety</b> - Criteria is bound to its result, no mismatches</li>
	 *   <li><b>Consistency</b> - Same criteria used in MEASURE experiments and tests</li>
	 *   <li><b>Single source of truth</b> - Success definition lives with the use case</li>
	 *   <li><b>Cleaner tests</b> - Just call outcome.assertAll()</li>
	 * </ul>
	 */
	@ProbabilisticTest(
			useCase = ShoppingUseCase.class,
			samples = 30,
			minPassRate = 0.85,
			maxExampleFailures = 5
	)
	void shouldPassAllSuccessCriteria(
			ShoppingUseCase useCase,
			TokenChargeRecorder tokenRecorder) {
		UseCaseOutcome outcome = useCase.searchProducts("wireless headphones");

		tokenRecorder.recordTokens(outcome.result().getInt("tokensUsed", 0));

		// Assert all criteria bundled with the outcome - same as used in experiments!
		// This ensures evaluator consistency between MEASURE and test.
		outcome.assertAll();
	}

	/**
	 * Tests using the outcome's criteria for product search.
	 *
	 * <p>Demonstrates using the bundled criteria for validation.
	 */
	@ProbabilisticTest(
			useCase = ShoppingUseCase.class,
			samples = 25,
			minPassRate = 0.80,
			maxExampleFailures = 5
	)
	void shouldPassCriteriaForProductSearch(
			ShoppingUseCase useCase,
			TokenChargeRecorder tokenRecorder) {
		UseCaseOutcome outcome = useCase.searchProducts("laptop stand");

		tokenRecorder.recordTokens(outcome.result().getInt("tokensUsed", 0));

		// The criteria is bundled with the outcome
		outcome.assertAll();
	}
}
