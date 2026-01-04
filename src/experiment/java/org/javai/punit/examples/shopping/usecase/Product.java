package org.javai.punit.examples.shopping.usecase;

/**
 * Represents a product returned by the shopping assistant.
 */
public record Product(
    String name,
    Double price,
    String category,
    Double relevanceScore
) {
    /**
     * Creates a product with all fields populated.
     */
    public static Product of(String name, double price, String category, double relevanceScore) {
        return new Product(name, price, category, relevanceScore);
    }

    /**
     * Creates a product with only name and price (missing optional fields).
     */
    public static Product withNameAndPrice(String name, double price) {
        return new Product(name, price, null, null);
    }

    /**
     * Creates a product with a null name (simulates LLM error).
     */
    public static Product withMissingName(double price, String category) {
        return new Product(null, price, category, 0.5);
    }

    /**
     * Creates a product with a null price (simulates LLM error).
     */
    public static Product withMissingPrice(String name, String category) {
        return new Product(name, null, category, 0.5);
    }
}

