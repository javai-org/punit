package org.javai.punit.examples.shopping.usecase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Mock implementation of ShoppingAssistant that simulates LLM behavior.
 *
 * <p>This mock deliberately introduces non-deterministic failures to simulate
 * real LLM behavior. Failures are varied and realistic:
 * <ul>
 *   <li><b>Malformed JSON</b> - Syntax errors like unclosed braces</li>
 *   <li><b>Hallucinated fields</b> - Wrong or misspelled field names</li>
 *   <li><b>Invalid values</b> - Wrong types (string instead of number, etc.)</li>
 *   <li><b>Missing fields</b> - Required fields absent from response</li>
 * </ul>
 *
 * <p>Use {@link MockConfiguration#experimentRealistic()} for a configuration
 * with ~95% success rate and varied, realistic failure modes.
 *
 * @see FailureMode
 */
public class MockShoppingAssistant implements ShoppingAssistant {

    private final Random random;
    private MockConfiguration config;
    private String systemPrompt = "";

    /**
     * Creates a mock with default configuration (simulates ~90% reliability).
     */
    public MockShoppingAssistant() {
        this(new Random(), MockConfiguration.defaultConfig());
    }
    
    /**
     * Updates the configuration for this mock.
     *
     * <p>Used by {@link ShoppingUseCase} to reconfigure the mock when
     * factor parameters (model, temperature) are injected.
     *
     * @param config the new configuration
     */
    public void setConfiguration(MockConfiguration config) {
        this.config = config;
    }

    /**
     * Sets the system prompt that influences mock behavior.
     *
     * <p>The mock analyzes the prompt for keywords that indicate desired behavior,
     * and adjusts failure rates accordingly. This simulates how a real LLM would
     * follow instructions in a system prompt.
     *
     * <p>Recognized patterns (case-insensitive):
     * <ul>
     *   <li>"valid JSON" / "proper JSON" → reduces malformed JSON rate</li>
     *   <li>"products, query, totalResults" / "exact field names" → reduces hallucinated fields</li>
     *   <li>"numeric" / "number" for prices → reduces invalid values</li>
     *   <li>"required fields" / "must include" → reduces missing fields</li>
     * </ul>
     *
     * @param systemPrompt the system prompt text
     */
    @Override
    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt != null ? systemPrompt : "";
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

        // First, determine if and how this response fails
        FailureMode failureMode = selectFailureMode();

        // Generate products (needed for some failure modes)
        List<Product> products = generateProducts(query, maxPrice, category, maxResults);

        return switch (failureMode) {
            case MALFORMED_JSON -> buildMalformedJsonResponse(tokensUsed);
            case HALLUCINATED_FIELDS -> buildHallucinatedFieldsResponse(query, products, tokensUsed);
            case INVALID_VALUES -> buildInvalidValuesResponse(query, products, tokensUsed);
            case MISSING_FIELDS -> buildMissingFieldsResponse(query, products, tokensUsed);
            case NONE -> buildValidResponse(query, products, tokensUsed);
        };
    }

    /**
     * Selects a failure mode based on configured rates, adjusted by system prompt.
     *
     * <p>The failure distribution for {@code experimentRealistic()} is:
     * <ul>
     *   <li>2.0% - Malformed JSON</li>
     *   <li>1.5% - Hallucinated fields</li>
     *   <li>1.0% - Invalid values</li>
     *   <li>0.5% - Missing fields</li>
     *   <li>95.0% - Valid response</li>
     * </ul>
     *
     * <p>When the system prompt contains relevant instructions, failure rates
     * are reduced by up to 80%, simulating how a real LLM follows instructions.
     */
    private FailureMode selectFailureMode() {
        double roll = random.nextDouble();
        double cumulative = 0.0;

        // Calculate prompt-adjusted rates
        double malformedRate = adjustedMalformedJsonRate();
        double hallucinatedRate = adjustedHallucinatedFieldsRate();
        double invalidRate = adjustedInvalidValuesRate();
        double missingRate = adjustedMissingFieldsRate();

        cumulative += malformedRate;
        if (roll < cumulative) {
            return FailureMode.MALFORMED_JSON;
        }

        cumulative += hallucinatedRate;
        if (roll < cumulative) {
            return FailureMode.HALLUCINATED_FIELDS;
        }

        cumulative += invalidRate;
        if (roll < cumulative) {
            return FailureMode.INVALID_VALUES;
        }

        cumulative += missingRate;
        if (roll < cumulative) {
            return FailureMode.MISSING_FIELDS;
        }

        return FailureMode.NONE;
    }

    /**
     * Calculates the adjusted malformed JSON rate based on system prompt.
     *
     * <p>Keywords that reduce this rate:
     * <ul>
     *   <li>"valid JSON" - 50% reduction</li>
     *   <li>"proper JSON format" - 50% reduction</li>
     *   <li>"well-formed JSON" - 50% reduction</li>
     *   <li>"syntactically correct" - 30% reduction</li>
     * </ul>
     */
    private double adjustedMalformedJsonRate() {
        double rate = config.malformedJsonRate();
        String lowerPrompt = systemPrompt.toLowerCase();

        if (lowerPrompt.contains("valid json")) rate *= 0.5;
        if (lowerPrompt.contains("proper json")) rate *= 0.5;
        if (lowerPrompt.contains("well-formed")) rate *= 0.5;
        if (lowerPrompt.contains("syntactically correct")) rate *= 0.7;

        return Math.max(rate, 0.001); // Never reduce to zero
    }

    /**
     * Calculates the adjusted hallucinated fields rate based on system prompt.
     *
     * <p>Keywords that reduce this rate:
     * <ul>
     *   <li>"products, query, totalResults" - 60% reduction</li>
     *   <li>"exact field names" - 50% reduction</li>
     *   <li>"use these fields" - 40% reduction</li>
     * </ul>
     */
    private double adjustedHallucinatedFieldsRate() {
        double rate = config.hallucinatedFieldsRate();
        String lowerPrompt = systemPrompt.toLowerCase();

        if (lowerPrompt.contains("products") && lowerPrompt.contains("query")
                && lowerPrompt.contains("totalresults")) {
            rate *= 0.4;
        }
        if (lowerPrompt.contains("exact field")) rate *= 0.5;
        if (lowerPrompt.contains("use these fields")) rate *= 0.6;
        if (lowerPrompt.contains("field names:")) rate *= 0.5;

        return Math.max(rate, 0.001);
    }

    /**
     * Calculates the adjusted invalid values rate based on system prompt.
     *
     * <p>Keywords that reduce this rate:
     * <ul>
     *   <li>"numeric price" / "price as number" - 50% reduction</li>
     *   <li>"correct types" - 50% reduction</li>
     *   <li>"string for name" - 30% reduction</li>
     * </ul>
     */
    private double adjustedInvalidValuesRate() {
        double rate = config.invalidValuesRate();
        String lowerPrompt = systemPrompt.toLowerCase();

        if (lowerPrompt.contains("numeric") && lowerPrompt.contains("price")) rate *= 0.5;
        if (lowerPrompt.contains("price as number")) rate *= 0.5;
        if (lowerPrompt.contains("correct type")) rate *= 0.5;
        if (lowerPrompt.contains("string for name")) rate *= 0.7;
        if (lowerPrompt.contains("number for price")) rate *= 0.5;

        return Math.max(rate, 0.001);
    }

    /**
     * Calculates the adjusted missing fields rate based on system prompt.
     *
     * <p>Keywords that reduce this rate:
     * <ul>
     *   <li>"required fields" - 50% reduction</li>
     *   <li>"must include" - 50% reduction</li>
     *   <li>"always include" - 50% reduction</li>
     * </ul>
     */
    private double adjustedMissingFieldsRate() {
        double rate = config.missingFieldRate();
        String lowerPrompt = systemPrompt.toLowerCase();

        if (lowerPrompt.contains("required field")) rate *= 0.5;
        if (lowerPrompt.contains("must include")) rate *= 0.5;
        if (lowerPrompt.contains("always include")) rate *= 0.5;
        if (lowerPrompt.contains("never omit")) rate *= 0.4;

        return Math.max(rate, 0.001);
    }

    // ==================== Failure Mode Generators ====================

    /**
     * Generates a malformed JSON response with syntax errors.
     */
    private LlmResponse buildMalformedJsonResponse(int tokensUsed) {
        String malformedJson = switch (random.nextInt(5)) {
            case 0 -> "{ \"products\": [";                          // Unclosed brace
            case 1 -> "{ products: [] }";                           // Missing quotes on key
            case 2 -> "{ \"products\": [], \"query\": }";           // Missing value
            case 3 -> "{ \"products\": [,] }";                      // Trailing comma
            default -> "I apologize, but I cannot process this request."; // Not JSON at all
        };

        return LlmResponse.builder()
                .rawJson(malformedJson)
                .validJson(false)
                .tokensUsed(tokensUsed)
                .failureMode(FailureMode.MALFORMED_JSON)
                .build();
    }

    /**
     * Generates a response with hallucinated (wrong/misspelled) field names.
     *
     * <p>The JSON is syntactically valid but uses unexpected field names.
     */
    private LlmResponse buildHallucinatedFieldsResponse(String query, List<Product> products, int tokensUsed) {
        // Pick random hallucinated field names
        String[] productFieldVariants = {"prodcuts", "items", "results", "productList", "merchandise"};
        String[] queryFieldVariants = {"search_query", "q", "searchTerm", "user_input", "request"};
        String[] totalFieldVariants = {"count", "total", "num_results", "resultCount", "itemCount"};

        String productField = productFieldVariants[random.nextInt(productFieldVariants.length)];
        String queryField = queryFieldVariants[random.nextInt(queryFieldVariants.length)];
        String totalField = totalFieldVariants[random.nextInt(totalFieldVariants.length)];

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"").append(queryField).append("\": \"").append(escapeJson(query)).append("\",\n");
        sb.append("  \"").append(totalField).append("\": ").append(products.size()).append(",\n");
        sb.append("  \"").append(productField).append("\": [\n");

        for (int i = 0; i < Math.min(products.size(), 3); i++) {
            Product p = products.get(i);
            sb.append("    {\n");
            sb.append("      \"name\": \"").append(escapeJson(p.name())).append("\",\n");
            sb.append("      \"price\": ").append(String.format("%.2f", p.price())).append(",\n");
            sb.append("      \"category\": \"").append(p.category()).append("\"\n");
            sb.append("    }");
            if (i < Math.min(products.size(), 3) - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  ]\n");
        sb.append("}");

        // The JSON is valid, but it won't parse correctly because field names are wrong
        Map<String, Boolean> presentFields = new HashMap<>();
        presentFields.put("products", false);  // Wrong field name used
        presentFields.put("query", false);     // Wrong field name used
        presentFields.put("totalResults", false); // Wrong field name used

        return LlmResponse.builder()
                .rawJson(sb.toString())
                .validJson(true)  // Syntactically valid JSON
                .tokensUsed(tokensUsed)
                .products(List.of())  // Empty because field name is wrong
                .presentFields(presentFields)
                .failureMode(FailureMode.HALLUCINATED_FIELDS)
                .build();
    }

    /**
     * Generates a response with invalid field values (wrong types, nonsense).
     *
     * <p>The JSON structure is correct but values are wrong types.
     */
    private LlmResponse buildInvalidValuesResponse(String query, List<Product> products, int tokensUsed) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"query\": \"").append(escapeJson(query)).append("\",\n");
        // String instead of number
        sb.append("  \"totalResults\": \"").append(products.size()).append("\",\n");
        sb.append("  \"products\": [\n");

        for (int i = 0; i < Math.min(products.size(), 3); i++) {
            sb.append("    {\n");
            // Mix of wrong types
            int wrongType = random.nextInt(4);
            switch (wrongType) {
                case 0 -> {
                    // Number instead of string for name
                    sb.append("      \"name\": ").append(random.nextInt(10000)).append(",\n");
                    sb.append("      \"price\": ").append(String.format("%.2f", products.get(i).price())).append(",\n");
                }
                case 1 -> {
                    // String instead of number for price
                    sb.append("      \"name\": \"").append(escapeJson(products.get(i).name())).append("\",\n");
                    sb.append("      \"price\": \"expensive\",\n");
                }
                case 2 -> {
                    // Null for required fields
                    sb.append("      \"name\": null,\n");
                    sb.append("      \"price\": null,\n");
                }
                default -> {
                    // Array instead of string for category
                    sb.append("      \"name\": \"").append(escapeJson(products.get(i).name())).append("\",\n");
                    sb.append("      \"price\": ").append(String.format("%.2f", products.get(i).price())).append(",\n");
                    sb.append("      \"category\": [\"Electronics\", \"Gadgets\"],\n");
                }
            }
            sb.append("      \"relevanceScore\": \"high\"\n");  // String instead of number
            sb.append("    }");
            if (i < Math.min(products.size(), 3) - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  ]\n");
        sb.append("}");

        Map<String, Boolean> presentFields = new HashMap<>();
        presentFields.put("products", true);
        presentFields.put("query", true);
        presentFields.put("totalResults", true);

        return LlmResponse.builder()
                .rawJson(sb.toString())
                .validJson(true)  // Syntactically valid JSON
                .tokensUsed(tokensUsed)
                .query(query)
                .products(List.of())  // Can't parse products due to type errors
                .presentFields(presentFields)
                .failureMode(FailureMode.INVALID_VALUES)
                .build();
    }

    /**
     * Generates a response with missing required fields.
     */
    private LlmResponse buildMissingFieldsResponse(String query, List<Product> products, int tokensUsed) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        // Randomly decide which fields to omit
        boolean includeQuery = random.nextBoolean();
        boolean includeProducts = random.nextBoolean();
        boolean includeTotalResults = random.nextBoolean();

        // Ensure at least one field is missing
        if (includeQuery && includeProducts && includeTotalResults) {
            int omit = random.nextInt(3);
            switch (omit) {
                case 0 -> includeQuery = false;
                case 1 -> includeProducts = false;
                default -> includeTotalResults = false;
            }
        }

        boolean first = true;
        if (includeQuery) {
            sb.append("  \"query\": \"").append(escapeJson(query)).append("\"");
            first = false;
        }

        if (includeTotalResults) {
            if (!first) sb.append(",\n");
            sb.append("  \"totalResults\": ").append(products.size());
            first = false;
        }

        if (includeProducts) {
            if (!first) sb.append(",\n");
            sb.append("  \"products\": []");  // Empty array
        }

        sb.append("\n}");

        Map<String, Boolean> presentFields = new HashMap<>();
        presentFields.put("products", includeProducts);
        presentFields.put("query", includeQuery);
        presentFields.put("totalResults", includeTotalResults);

        return LlmResponse.builder()
                .rawJson(sb.toString())
                .validJson(true)
                .tokensUsed(tokensUsed)
                .query(includeQuery ? query : null)
                .products(includeProducts ? List.of() : null)
                .totalResults(includeTotalResults ? products.size() : null)
                .presentFields(presentFields)
                .failureMode(FailureMode.MISSING_FIELDS)
                .build();
    }

    /**
     * Generates a valid, well-formed response.
     */
    private LlmResponse buildValidResponse(String query, List<Product> products, int tokensUsed) {
        String rawJson = buildJson(query, products, true, true);

        Map<String, Boolean> presentFields = new HashMap<>();
        presentFields.put("products", true);
        presentFields.put("query", true);
        presentFields.put("totalResults", true);

        return LlmResponse.builder()
                .rawJson(rawJson)
                .validJson(true)
                .tokensUsed(tokensUsed)
                .query(query)
                .products(products)
                .totalResults(products.size())
                .presentFields(presentFields)
                .failureMode(FailureMode.NONE)
                .build();
    }

    // ==================== Product Generation ====================

    private List<Product> generateProducts(String query, Double maxPrice, String category, int maxResults) {
        List<Product> products = new ArrayList<>();
        int productCount = Math.min(3 + random.nextInt(5), maxResults);

        // Occasionally exceed the limit
        if (shouldFail(config.resultCountViolationRate())) {
            productCount = maxResults + 1 + random.nextInt(3);
        }

        for (int i = 0; i < productCount; i++) {
            products.add(generateProduct(query, maxPrice, category));
        }

        return products;
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

    // ==================== JSON Building ====================

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

    // ==================== Configuration ====================

    /**
     * Configuration for mock failure rates.
     *
     * <p>Failure modes are distributed across categories:
     * <ul>
     *   <li>{@code malformedJsonRate} - Syntax errors in JSON</li>
     *   <li>{@code hallucinatedFieldsRate} - Wrong/misspelled field names</li>
     *   <li>{@code invalidValuesRate} - Wrong types for field values</li>
     *   <li>{@code missingFieldRate} - Required fields omitted</li>
     * </ul>
     *
     * <p>Additional rates control product-level issues (which don't cause
     * complete response failures but affect data quality).
     */
    public record MockConfiguration(
            double malformedJsonRate,
            double hallucinatedFieldsRate,
            double invalidValuesRate,
            double missingFieldRate,
            double missingAttributeRate,
            double priceViolationRate,
            double categoryViolationRate,
            double lowRelevanceRate,
            double resultCountViolationRate
    ) {
        /**
         * Default configuration simulating ~90% overall reliability.
         *
         * <p>Failure distribution (~10% total):
         * <ul>
         *   <li>5% malformed JSON</li>
         *   <li>2% hallucinated fields</li>
         *   <li>2% invalid values</li>
         *   <li>1% missing fields</li>
         * </ul>
         */
        public static MockConfiguration defaultConfig() {
            return new MockConfiguration(
                    0.05,   // 5% malformed JSON
                    0.02,   // 2% hallucinated fields
                    0.02,   // 2% invalid values
                    0.01,   // 1% missing fields
                    0.10,   // 10% products with missing attributes
                    0.10,   // 10% price violations
                    0.05,   // 5% category violations
                    0.15,   // 15% low relevance scores
                    0.03    // 3% result count violations
            );
        }

        /**
         * Configuration with higher reliability (95%+).
         */
        public static MockConfiguration highReliability() {
            return new MockConfiguration(
                    0.02,   // 2% malformed JSON
                    0.01,   // 1% hallucinated fields
                    0.01,   // 1% invalid values
                    0.01,   // 1% missing fields
                    0.05,   // 5% products with missing attributes
                    0.05,   // 5% price violations
                    0.02,   // 2% category violations
                    0.08,   // 8% low relevance scores
                    0.01    // 1% result count violations
            );
        }

        /**
         * Configuration with lower reliability (~80%).
         */
        public static MockConfiguration lowReliability() {
            return new MockConfiguration(
                    0.08,   // 8% malformed JSON
                    0.05,   // 5% hallucinated fields
                    0.04,   // 4% invalid values
                    0.03,   // 3% missing fields
                    0.20,   // 20% products with missing attributes
                    0.20,   // 20% price violations
                    0.15,   // 15% category violations
                    0.25,   // 25% low relevance scores
                    0.10    // 10% result count violations
            );
        }

        /**
         * Configuration simulating realistic LLM behavior with ~95% success rate
         * and varied failure modes.
         *
         * <p>This is the recommended configuration for experiments that aim to
         * establish realistic baselines. The ~5% failure rate is distributed
         * across different failure modes to simulate real LLM behavior.
         *
         * <p>Failure distribution (~5% total):
         * <ul>
         *   <li>2.0% - Malformed JSON (syntax errors)</li>
         *   <li>1.5% - Hallucinated field names</li>
         *   <li>1.0% - Invalid field values (wrong types)</li>
         *   <li>0.5% - Missing required fields</li>
         * </ul>
         */
        public static MockConfiguration experimentRealistic() {
            return new MockConfiguration(
                    0.020,  // 2.0% malformed JSON
                    0.015,  // 1.5% hallucinated fields
                    0.010,  // 1.0% invalid values
                    0.005,  // 0.5% missing fields
                    0.02,   // 2% products with missing attributes
                    0.02,   // 2% price violations
                    0.01,   // 1% category violations
                    0.03,   // 3% low relevance scores
                    0.01    // 1% result count violations
            );
        }
    }
}
