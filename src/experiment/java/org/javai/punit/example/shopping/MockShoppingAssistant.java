package org.javai.punit.example.shopping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Mock implementation of ShoppingAssistant that simulates LLM behavior.
 *
 * <p>This mock deliberately introduces non-deterministic failures to simulate
 * real LLM behavior, including:
 * <ul>
 *   <li>Occasional malformed JSON responses</li>
 *   <li>Missing required fields</li>
 *   <li>Products with missing attributes</li>
 *   <li>Products outside requested price range</li>
 *   <li>Incorrect result counts</li>
 * </ul>
 *
 * <p>The failure rates can be configured to test different reliability scenarios.
 */
public class MockShoppingAssistant implements ShoppingAssistant {

    private final Random random;
    private final MockConfiguration config;

    /**
     * Creates a mock with default configuration (simulates ~90% reliability).
     */
    public MockShoppingAssistant() {
        this(new Random(), MockConfiguration.defaultConfig());
    }

    /**
     * Creates a mock with a seeded random for reproducible tests.
     *
     * @param seed the random seed
     */
    public MockShoppingAssistant(long seed) {
        this(new Random(seed), MockConfiguration.defaultConfig());
    }

    /**
     * Creates a mock with custom configuration.
     *
     * @param random the random instance to use
     * @param config the mock configuration
     */
    public MockShoppingAssistant(Random random, MockConfiguration config) {
        this.random = random;
        this.config = config;
    }

    @Override
    public LlmResponse searchProducts(String query) {
        return generateResponse(query, null, null, 10);
    }

    @Override
    public LlmResponse searchProducts(String query, double maxPrice) {
        return generateResponse(query, maxPrice, null, 10);
    }

    @Override
    public LlmResponse searchProducts(String query, String category) {
        return generateResponse(query, null, category, 10);
    }

    @Override
    public LlmResponse searchProducts(String query, int maxResults) {
        return generateResponse(query, null, null, maxResults);
    }

    private LlmResponse generateResponse(String query, Double maxPrice, String category, int maxResults) {
        int tokensUsed = 200 + random.nextInt(300); // 200-500 tokens

        // Simulate malformed JSON
        if (shouldFail(config.malformedJsonRate())) {
            return LlmResponse.builder()
                .rawJson("{ invalid json missing closing brace")
                .validJson(false)
                .tokensUsed(tokensUsed)
                .build();
        }

        // Determine which fields to include
        boolean includeProducts = !shouldFail(config.missingFieldRate());
        boolean includeQuery = !shouldFail(config.missingFieldRate());
        boolean includeTotalResults = !shouldFail(config.missingFieldRate());

        Map<String, Boolean> presentFields = new HashMap<>();
        presentFields.put("products", includeProducts);
        presentFields.put("query", includeQuery);
        presentFields.put("totalResults", includeTotalResults);

        // Generate products
        List<Product> products = new ArrayList<>();
        if (includeProducts) {
            int productCount = Math.min(3 + random.nextInt(5), maxResults);
            
            // Occasionally exceed the limit
            if (shouldFail(config.resultCountViolationRate())) {
                productCount = maxResults + 1 + random.nextInt(3);
            }

            for (int i = 0; i < productCount; i++) {
                products.add(generateProduct(query, maxPrice, category));
            }
        }

        // Build JSON representation
        String rawJson = buildJson(query, products, includeQuery, includeTotalResults);

        return LlmResponse.builder()
            .rawJson(rawJson)
            .validJson(true)
            .tokensUsed(tokensUsed)
            .query(includeQuery ? query : null)
            .products(products)
            .totalResults(includeTotalResults ? products.size() : null)
            .presentFields(presentFields)
            .build();
    }

    private Product generateProduct(String query, Double maxPrice, String category) {
        // Generate base price
        double basePrice = 20 + random.nextDouble() * 200;
        
        // Occasionally violate price constraint
        double price;
        if (maxPrice != null && !shouldFail(config.priceViolationRate())) {
            price = Math.min(basePrice, maxPrice * (0.5 + random.nextDouble() * 0.5));
        } else {
            price = basePrice;
        }

        // Determine category
        String productCategory;
        if (category != null && !shouldFail(config.categoryViolationRate())) {
            productCategory = category;
        } else {
            productCategory = randomCategory();
        }

        // Generate relevance score
        double relevanceScore = shouldFail(config.lowRelevanceRate()) 
            ? 0.3 + random.nextDouble() * 0.3  // Low relevance: 0.3-0.6
            : 0.7 + random.nextDouble() * 0.3; // High relevance: 0.7-1.0

        // Generate product name based on query keywords
        String name = generateProductName(query, productCategory);

        // Occasionally return products with missing attributes
        if (shouldFail(config.missingAttributeRate())) {
            int missingType = random.nextInt(3);
            return switch (missingType) {
                case 0 -> Product.withMissingName(price, productCategory);
                case 1 -> Product.withMissingPrice(name, productCategory);
                default -> new Product(name, price, null, relevanceScore);
            };
        }

        return Product.of(name, price, productCategory, relevanceScore);
    }

    private String generateProductName(String query, String category) {
        String[] adjectives = {"Premium", "Professional", "Ultra", "Essential", "Classic", "Modern"};
        String[] suffixes = {"Pro", "Plus", "Elite", "Max", "Lite", "X"};
        
        String adjective = adjectives[random.nextInt(adjectives.length)];
        String suffix = random.nextBoolean() ? " " + suffixes[random.nextInt(suffixes.length)] : "";
        
        // Extract a keyword from the query for the product name
        String[] queryWords = query.split("\\s+");
        String keyword = queryWords.length > 0 
            ? capitalize(queryWords[random.nextInt(queryWords.length)])
            : category;

        return adjective + " " + keyword + suffix;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private String randomCategory() {
        String[] categories = {"Electronics", "Home & Garden", "Sports", "Clothing", "Books", "Toys"};
        return categories[random.nextInt(categories.length)];
    }

    private String buildJson(String query, List<Product> products, boolean includeQuery, boolean includeTotalResults) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        
        if (includeQuery) {
            sb.append("  \"query\": \"").append(escapeJson(query)).append("\",\n");
        }
        
        if (includeTotalResults) {
            sb.append("  \"totalResults\": ").append(products.size()).append(",\n");
        }
        
        sb.append("  \"products\": [\n");
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            sb.append("    {\n");
            if (p.name() != null) {
                sb.append("      \"name\": \"").append(escapeJson(p.name())).append("\",\n");
            }
            if (p.price() != null) {
                sb.append("      \"price\": ").append(String.format("%.2f", p.price())).append(",\n");
            }
            if (p.category() != null) {
                sb.append("      \"category\": \"").append(p.category()).append("\",\n");
            }
            if (p.relevanceScore() != null) {
                sb.append("      \"relevanceScore\": ").append(String.format("%.2f", p.relevanceScore())).append("\n");
            }
            sb.append("    }");
            if (i < products.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}");
        
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private boolean shouldFail(double rate) {
        return random.nextDouble() < rate;
    }

    /**
     * Configuration for mock failure rates.
     */
    public record MockConfiguration(
        double malformedJsonRate,
        double missingFieldRate,
        double missingAttributeRate,
        double priceViolationRate,
        double categoryViolationRate,
        double lowRelevanceRate,
        double resultCountViolationRate
    ) {
        /**
         * Default configuration simulating ~90% overall reliability.
         */
        public static MockConfiguration defaultConfig() {
            return new MockConfiguration(
                0.05,  // 5% malformed JSON
                0.08,  // 8% missing required fields
                0.10,  // 10% products with missing attributes
                0.10,  // 10% price violations
                0.05,  // 5% category violations
                0.15,  // 15% low relevance scores
                0.03   // 3% result count violations
            );
        }

        /**
         * Configuration with higher reliability (95%+).
         */
        public static MockConfiguration highReliability() {
            return new MockConfiguration(
                0.02,  // 2% malformed JSON
                0.03,  // 3% missing required fields
                0.05,  // 5% products with missing attributes
                0.05,  // 5% price violations
                0.02,  // 2% category violations
                0.08,  // 8% low relevance scores
                0.01   // 1% result count violations
            );
        }

        /**
         * Configuration with lower reliability (~80%).
         */
        public static MockConfiguration lowReliability() {
            return new MockConfiguration(
                0.10,  // 10% malformed JSON
                0.15,  // 15% missing required fields
                0.20,  // 20% products with missing attributes
                0.20,  // 20% price violations
                0.15,  // 15% category violations
                0.25,  // 25% low relevance scores
                0.10   // 10% result count violations
            );
        }
    }
}

