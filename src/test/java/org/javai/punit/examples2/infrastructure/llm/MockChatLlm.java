package org.javai.punit.examples2.infrastructure.llm;

import java.util.Random;

/**
 * Mock implementation of {@link ChatLlm} that simulates realistic LLM behavior.
 *
 * <p>This singleton mock provides controlled, reproducible LLM responses with
 * configurable failure modes. It's designed for demonstrating PUnit's probabilistic
 * testing capabilities.
 *
 * <h2>Temperature-Based Reliability</h2>
 * <p>Temperature controls the reliability of responses:
 * <ul>
 *   <li>{@code 0.0} - ~99% valid responses (highly deterministic)</li>
 *   <li>{@code 0.5} - ~90% valid responses (balanced)</li>
 *   <li>{@code 1.0} - ~70% valid responses (creative but error-prone)</li>
 * </ul>
 *
 * <h2>Failure Modes</h2>
 * <p>When failures occur, they manifest as realistic LLM errors:
 * <ul>
 *   <li><b>Malformed JSON</b> - Syntax errors like unclosed braces, missing quotes</li>
 *   <li><b>Hallucinated fields</b> - Wrong field names (e.g., "items" instead of "operations")</li>
 *   <li><b>Invalid values</b> - Wrong types (e.g., string instead of int for quantity)</li>
 *   <li><b>Missing fields</b> - Omitted required fields</li>
 * </ul>
 *
 * <h2>Token Tracking</h2>
 * <p>The mock tracks token usage to simulate real LLM costs:
 * <ul>
 *   <li>Prompt tokens: ~4 tokens per word in system + user messages</li>
 *   <li>Completion tokens: ~4 tokens per word in response</li>
 *   <li>Cumulative tracking via {@link #getTotalTokensUsed()}</li>
 *   <li>Reset between tests via {@link #resetTokenCount()}</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ChatLlm llm = MockChatLlm.instance();
 *
 * // Simple usage
 * String response = llm.chat(systemPrompt, userMessage, 0.3);
 *
 * // With token tracking
 * ChatResponse resp = llm.chatWithMetadata(systemPrompt, userMessage, 0.3);
 * int tokens = resp.totalTokens();
 *
 * // Check cumulative usage
 * long total = llm.getTotalTokensUsed();
 * llm.resetTokenCount();
 * }</pre>
 *
 * @see ChatLlm
 * @see ChatResponse
 */
public final class MockChatLlm implements ChatLlm {

    private static final MockChatLlm INSTANCE = new MockChatLlm();

    /** Approximate tokens per word (GPT-style tokenization) */
    private static final double TOKENS_PER_WORD = 1.3;

    private final Random random;
    private long seed;
    private long totalTokensUsed;

    private MockChatLlm() {
        this.seed = System.currentTimeMillis();
        this.random = new Random(seed);
        this.totalTokensUsed = 0;
    }

    /**
     * Returns the singleton instance.
     *
     * @return the shared MockChatLlm instance
     */
    public static MockChatLlm instance() {
        return INSTANCE;
    }

    /**
     * Resets the random seed for reproducible test runs.
     *
     * @param seed the seed value
     */
    public void setSeed(long seed) {
        this.seed = seed;
        this.random.setSeed(seed);
    }

    /**
     * Returns the current seed.
     *
     * @return the seed value
     */
    public long getSeed() {
        return seed;
    }

    @Override
    public String chat(String systemMessage, String userMessage, double temperature) {
        return chatWithMetadata(systemMessage, userMessage, temperature).content();
    }

    @Override
    public ChatResponse chatWithMetadata(String systemMessage, String userMessage, double temperature) {
        // Calculate base failure rate from temperature
        // temp 0.0 -> 1% failure, temp 0.5 -> 10% failure, temp 1.0 -> 30% failure
        double baseFailureRate = 0.01 + (temperature * 0.29);

        // System prompt quality reduces failure rate
        double promptBonus = analyzeSystemPrompt(systemMessage);
        double effectiveFailureRate = baseFailureRate * (1.0 - promptBonus);

        // Generate response
        String response;
        if (random.nextDouble() < effectiveFailureRate) {
            response = generateFailedResponse(userMessage);
        } else {
            response = generateValidResponse(userMessage);
        }

        // Calculate token usage
        int promptTokens = estimateTokens(systemMessage) + estimateTokens(userMessage);
        int completionTokens = estimateTokens(response);

        // Track cumulative usage
        totalTokensUsed += promptTokens + completionTokens;

        return new ChatResponse(response, promptTokens, completionTokens);
    }

    @Override
    public long getTotalTokensUsed() {
        return totalTokensUsed;
    }

    @Override
    public void resetTokenCount() {
        totalTokensUsed = 0;
    }

    /**
     * Estimates token count for a string using approximate word-based tokenization.
     *
     * <p>Real tokenizers (tiktoken, etc.) produce ~1.3 tokens per word on average
     * for English text. This mock uses that approximation.
     *
     * @param text the text to estimate tokens for
     * @return estimated token count
     */
    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        // Count words (split on whitespace)
        String[] words = text.trim().split("\\s+");
        return (int) Math.ceil(words.length * TOKENS_PER_WORD);
    }

    /**
     * Analyzes the system prompt for quality indicators.
     *
     * <p>A well-crafted system prompt reduces failure rates by up to 70%.
     *
     * @param systemPrompt the system prompt to analyze
     * @return a bonus factor between 0.0 and 0.7
     */
    private double analyzeSystemPrompt(String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return 0.0;
        }

        double bonus = 0.0;
        String lower = systemPrompt.toLowerCase();

        // JSON format instructions
        if (lower.contains("valid json") || lower.contains("json format")) {
            bonus += 0.15;
        }
        if (lower.contains("operations") && lower.contains("array")) {
            bonus += 0.15;
        }

        // Field name instructions
        if (lower.contains("action") && lower.contains("item") && lower.contains("quantity")) {
            bonus += 0.15;
        }

        // Type instructions
        if (lower.contains("integer") || lower.contains("positive number")) {
            bonus += 0.10;
        }

        // Valid action values
        if (lower.contains("add") && lower.contains("remove") && lower.contains("clear")) {
            bonus += 0.10;
        }

        // Example included
        if (lower.contains("example") || lower.contains("{\"operations\"")) {
            bonus += 0.05;
        }

        return Math.min(bonus, 0.70);
    }

    /**
     * Generates a valid JSON response for the user message.
     */
    private String generateValidResponse(String userMessage) {
        // Parse the user message to extract operations
        StringBuilder json = new StringBuilder();
        json.append("{\"operations\": [");

        String lower = userMessage.toLowerCase();
        boolean first = true;

        // Look for "add X <item>" patterns
        if (lower.contains("add")) {
            if (!first) json.append(", ");
            first = false;

            int quantity = extractQuantity(userMessage, "add");
            String item = extractItem(userMessage, "add");
            json.append(String.format("{\"action\": \"add\", \"item\": \"%s\", \"quantity\": %d}",
                    item, quantity));
        }

        // Look for "remove X <item>" patterns
        if (lower.contains("remove")) {
            if (!first) json.append(", ");
            first = false;

            int quantity = extractQuantity(userMessage, "remove");
            String item = extractItem(userMessage, "remove");
            json.append(String.format("{\"action\": \"remove\", \"item\": \"%s\", \"quantity\": %d}",
                    item, quantity));
        }

        // Look for "clear" patterns
        if (lower.contains("clear")) {
            if (!first) json.append(", ");
            String item = lower.contains("clear the") ? extractItemAfter(userMessage, "clear the") : "basket";
            json.append(String.format("{\"action\": \"clear\", \"item\": \"%s\", \"quantity\": 1}", item));
        }

        // If no operations detected, create a sensible default
        if (first) {
            json.append("{\"action\": \"add\", \"item\": \"item\", \"quantity\": 1}");
        }

        json.append("]}");
        return json.toString();
    }

    /**
     * Generates a failed response with a random failure mode.
     */
    private String generateFailedResponse(String userMessage) {
        FailureMode mode = selectFailureMode();
        return switch (mode) {
            case MALFORMED_JSON -> generateMalformedJson(userMessage);
            case HALLUCINATED_FIELDS -> generateHallucinatedFields(userMessage);
            case INVALID_VALUES -> generateInvalidValues(userMessage);
            case MISSING_FIELDS -> generateMissingFields(userMessage);
        };
    }

    private FailureMode selectFailureMode() {
        double roll = random.nextDouble();
        if (roll < 0.35) return FailureMode.MALFORMED_JSON;
        if (roll < 0.60) return FailureMode.HALLUCINATED_FIELDS;
        if (roll < 0.85) return FailureMode.INVALID_VALUES;
        return FailureMode.MISSING_FIELDS;
    }

    private String generateMalformedJson(String userMessage) {
        int variant = random.nextInt(5);
        return switch (variant) {
            case 0 -> "{\"operations\": [";  // Unclosed
            case 1 -> "{ operations: [] }";  // Missing quotes on key
            case 2 -> "{\"operations\": [,]}";  // Trailing comma
            case 3 -> "{\"operations\": [{\"action\": \"add\"}";  // Partial
            default -> "I'd be happy to help with your shopping basket!";  // Not JSON
        };
    }

    private String generateHallucinatedFields(String userMessage) {
        // Valid JSON structure but wrong field names
        String[] operationVariants = {"items", "commands", "actions", "tasks", "requests"};
        String[] actionVariants = {"type", "command", "op", "verb", "operation_type"};
        String[] itemVariants = {"product", "name", "object", "thing", "target"};
        String[] quantityVariants = {"count", "amount", "num", "number", "qty"};

        String opField = operationVariants[random.nextInt(operationVariants.length)];
        String actionField = actionVariants[random.nextInt(actionVariants.length)];
        String itemField = itemVariants[random.nextInt(itemVariants.length)];
        String qtyField = quantityVariants[random.nextInt(quantityVariants.length)];

        return String.format("{\"%s\": [{\"%s\": \"add\", \"%s\": \"apples\", \"%s\": 2}]}",
                opField, actionField, itemField, qtyField);
    }

    private String generateInvalidValues(String userMessage) {
        int variant = random.nextInt(4);
        return switch (variant) {
            case 0 -> // String instead of int for quantity
                    "{\"operations\": [{\"action\": \"add\", \"item\": \"apples\", \"quantity\": \"two\"}]}";
            case 1 -> // Negative quantity
                    "{\"operations\": [{\"action\": \"add\", \"item\": \"apples\", \"quantity\": -5}]}";
            case 2 -> // Invalid action
                    "{\"operations\": [{\"action\": \"purchase\", \"item\": \"apples\", \"quantity\": 2}]}";
            default -> // Null values
                    "{\"operations\": [{\"action\": null, \"item\": \"apples\", \"quantity\": 2}]}";
        };
    }

    private String generateMissingFields(String userMessage) {
        int variant = random.nextInt(3);
        return switch (variant) {
            case 0 -> // Missing action
                    "{\"operations\": [{\"item\": \"apples\", \"quantity\": 2}]}";
            case 1 -> // Missing item
                    "{\"operations\": [{\"action\": \"add\", \"quantity\": 2}]}";
            default -> // Missing quantity
                    "{\"operations\": [{\"action\": \"add\", \"item\": \"apples\"}]}";
        };
    }

    private int extractQuantity(String message, String action) {
        // Simple extraction: look for a number near the action word
        String lower = message.toLowerCase();
        int actionIdx = lower.indexOf(action);
        if (actionIdx == -1) return 1;

        // Look for numbers in a window around the action
        String window = message.substring(Math.max(0, actionIdx - 5),
                Math.min(message.length(), actionIdx + 30));

        for (String word : window.split("\\s+")) {
            try {
                int num = Integer.parseInt(word);
                if (num > 0 && num < 1000) return num;
            } catch (NumberFormatException ignored) {
                // Try word numbers
                if (word.equalsIgnoreCase("one") || word.equals("a") || word.equals("an")) return 1;
                if (word.equalsIgnoreCase("two")) return 2;
                if (word.equalsIgnoreCase("three")) return 3;
            }
        }
        return 1;
    }

    private String extractItem(String message, String action) {
        String lower = message.toLowerCase();
        int actionIdx = lower.indexOf(action);
        if (actionIdx == -1) return "item";

        // Get text after the action and any number
        String afterAction = message.substring(actionIdx + action.length()).trim();

        // Skip leading numbers
        afterAction = afterAction.replaceFirst("^\\d+\\s*", "");

        // Take the first word(s) that look like an item
        String[] words = afterAction.split("\\s+");
        if (words.length == 0) return "item";

        // Return first noun-like word, stopping at conjunctions
        StringBuilder item = new StringBuilder();
        for (String word : words) {
            String clean = word.replaceAll("[^a-zA-Z]", "").toLowerCase();
            if (clean.isEmpty()) continue;
            if (clean.equals("and") || clean.equals("the") || clean.equals("from")) break;
            if (item.length() > 0) item.append(" ");
            item.append(clean);
            if (item.length() > 20) break;
        }

        return item.length() > 0 ? item.toString() : "item";
    }

    private String extractItemAfter(String message, String prefix) {
        String lower = message.toLowerCase();
        int idx = lower.indexOf(prefix.toLowerCase());
        if (idx == -1) return "basket";

        String after = message.substring(idx + prefix.length()).trim();
        String[] words = after.split("\\s+");
        return words.length > 0 ? words[0].replaceAll("[^a-zA-Z]", "").toLowerCase() : "basket";
    }

    /**
     * Failure modes for the mock LLM.
     */
    public enum FailureMode {
        MALFORMED_JSON,
        HALLUCINATED_FIELDS,
        INVALID_VALUES,
        MISSING_FIELDS
    }
}
