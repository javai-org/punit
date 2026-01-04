package org.javai.punit.example.shopping;

/**
 * Interface for an LLM-powered shopping assistant.
 *
 * <p>The shopping assistant accepts natural language queries and returns
 * product recommendations. The implementation wraps an LLM call which may
 * produce non-deterministic results.
 */
public interface ShoppingAssistant {

    /**
     * Searches for products matching the given natural language query.
     *
     * @param query the natural language search query (e.g., "wireless headphones under $100")
     * @return the LLM response containing product recommendations
     */
    LlmResponse searchProducts(String query);

    /**
     * Searches for products with a maximum price constraint.
     *
     * @param query the natural language search query
     * @param maxPrice the maximum price filter
     * @return the LLM response containing product recommendations
     */
    LlmResponse searchProducts(String query, double maxPrice);

    /**
     * Searches for products within a specific category.
     *
     * @param query the natural language search query
     * @param category the category to filter by
     * @return the LLM response containing product recommendations
     */
    LlmResponse searchProducts(String query, String category);

    /**
     * Searches for products with a result count limit.
     *
     * @param query the natural language search query
     * @param maxResults the maximum number of products to return
     * @return the LLM response containing product recommendations
     */
    LlmResponse searchProducts(String query, int maxResults);
}

