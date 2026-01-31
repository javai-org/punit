package org.javai.punit.examples.infrastructure.llm;

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
 * // Simple usage - model parameter is accepted but ignored
 * String response = llm.chat(systemPrompt, userMessage, "gpt-4o-mini", 0.3);
 *
 * // With token tracking
 * ChatResponse resp = llm.chatWithMetadata(systemPrompt, userMessage, "gpt-4o-mini", 0.3);
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
    public String chat(String systemMessage, String userMessage, String model, double temperature) {
        return chatWithMetadata(systemMessage, userMessage, model, temperature).content();
    }

    @Override
    public ChatResponse chatWithMetadata(String systemMessage, String userMessage, String model, double temperature) {
        // Note: model parameter is accepted for interface compatibility but ignored.
        // Mock behavior is determined solely by temperature and prompt content.
        // Analyze what the prompt specifies - this determines response quality
        PromptRequirements requirements = analyzePromptRequirements(systemMessage);

        // Generate response based on what the prompt asks for
        // If the prompt is vague, the response will have issues that fail validation
        String response = generateResponse(userMessage, requirements, temperature);

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
     * Analyzes what the system prompt explicitly requires.
     *
     * <p>Returns a structured record of what the prompt specifies,
     * which determines what kind of response the mock LLM produces.
     */
    private PromptRequirements analyzePromptRequirements(String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return new PromptRequirements(false, false, false, false, false);
        }

        String lower = systemPrompt.toLowerCase();

        // Does the prompt require JSON-only output?
        boolean requiresJsonOnly = lower.contains("only") && lower.contains("json") ||
                                   lower.contains("no explanation") || lower.contains("no markdown");

        // Does the prompt specify the "operations" array structure?
        boolean specifiesSchema = lower.contains("operations") &&
                                  (lower.contains("{") || lower.contains("structure") || lower.contains("format"));

        // Does the prompt name the required fields?
        boolean specifiesFields = lower.contains("action") &&
                                  lower.contains("item") &&
                                  lower.contains("quantity");

        // Does the prompt enumerate valid actions?
        boolean specifiesActions = (lower.contains("\"add\"") && lower.contains("\"remove\"")) ||
                                   (lower.contains("add") && lower.contains("remove") && lower.contains("clear") &&
                                    (lower.contains("must be") || lower.contains("one of")));

        // Does the prompt specify quantity constraints?
        boolean specifiesConstraints = lower.contains("positive") ||
                                       lower.contains("integer") ||
                                       lower.contains("≥") ||
                                       lower.contains(">= 1");

        return new PromptRequirements(
                requiresJsonOnly, specifiesSchema, specifiesFields, specifiesActions, specifiesConstraints);
    }

    /**
     * Generates a response based on what the prompt specifies and the temperature.
     *
     * <p>Temperature affects the likelihood of deviation from the prompt's instructions:
     * <ul>
     *   <li>{@code 0.0}: Follows prompt faithfully (deterministic)</li>
     *   <li>{@code 0.5}: Occasional deviations (~25% chance per aspect)</li>
     *   <li>{@code 1.0}: Frequent deviations (~50% chance per aspect)</li>
     * </ul>
     *
     * <p>This models real LLM behavior where higher temperature increases creativity
     * but also increases the chance of not following structured output requirements.
     */
    private String generateResponse(String userMessage, PromptRequirements req, double temperature) {
        // Temperature determines deviation probability for each aspect
        // At temp=0: 0% deviation, at temp=1: 50% deviation
        double deviationChance = temperature * 0.5;

        StringBuilder response = new StringBuilder();

        // If prompt doesn't require JSON-only, might add prose (temperature-dependent)
        boolean addProse = !req.requiresJsonOnly && random.nextDouble() < (0.3 + deviationChance);
        if (addProse) {
            response.append("I'd be happy to help! Here's the JSON:\n\n");
        }

        // Determine action value - deviate based on temperature
        boolean deviateActions = !req.specifiesActions || random.nextDouble() < deviationChance;
        String actionValue = deviateActions ? randomAction() : "add";

        // Determine quantity value - deviate based on temperature
        boolean deviateQuantity = !req.specifiesConstraints || random.nextDouble() < deviationChance;
        Object quantityValue = deviateQuantity ? randomQuantity() : extractQuantity(userMessage, "add");

        // Extract item from user message
        String item = extractItem(userMessage, "add");
        if (item.equals("item")) item = "apple";  // Default for demo

        // Determine if we should deviate from the expected schema
        boolean deviateSchema = random.nextDouble() < deviationChance;

        if (deviateSchema) {
            // Generate old-style schema (wrong format)
            response.append(String.format("{\"operations\": [{\"action\": \"%s\", ", actionValue));
            response.append(String.format("\"item\": \"%s\", ", item));
            response.append(String.format("\"quantity\": %s}]}", quantityValue));
        } else {
            // Generate correct ShoppingAction schema
            response.append("{\"context\": \"SHOP\", ");
            response.append(String.format("\"name\": \"%s\", ", actionValue));
            response.append("\"parameters\": [");
            response.append(String.format("{\"name\": \"item\", \"value\": \"%s\"}, ", item));
            response.append(String.format("{\"name\": \"quantity\", \"value\": \"%s\"}", quantityValue));
            response.append("]}");
        }

        // Additional chance of malformed JSON at high temperature
        if (random.nextDouble() < deviationChance * 0.3) {
            return corruptJson(response.toString());
        }

        return response.toString();
    }

    private String randomAction() {
        // 70% chance of valid SHOP actions, 30% chance of invalid/hallucinated actions
        if (random.nextDouble() < 0.7) {
            String[] validOptions = {"add", "remove", "clear"};
            return validOptions[random.nextInt(validOptions.length)];
        } else {
            String[] invalidOptions = {"purchase", "buy", "insert", "delete"};
            return invalidOptions[random.nextInt(invalidOptions.length)];
        }
    }

    private Object randomQuantity() {
        int choice = random.nextInt(5);
        return switch (choice) {
            case 0 -> -1;           // Negative
            case 1 -> 0;            // Zero
            case 2 -> "two";        // String instead of int
            case 3 -> 2;            // Valid
            default -> 1;           // Valid
        };
    }

    private String corruptJson(String json) {
        int corruption = random.nextInt(3);
        return switch (corruption) {
            case 0 -> json.substring(0, json.length() - 2);  // Truncate
            case 1 -> json.replace(":", " ");                // Remove colons
            default -> json.replace("\"", "'");              // Wrong quotes
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
     * Structured requirements extracted from the system prompt.
     *
     * <p>Each field indicates whether the prompt explicitly specifies
     * a particular aspect of the expected response format.
     *
     * @param requiresJsonOnly    prompt requires JSON without prose/explanation
     * @param specifiesSchema     prompt specifies the "operations" array structure
     * @param specifiesFields     prompt names the required fields (action, item, quantity)
     * @param specifiesActions    prompt enumerates valid actions (add, remove, clear)
     * @param specifiesConstraints prompt specifies quantity constraints (positive integer)
     */
    private record PromptRequirements(
            boolean requiresJsonOnly,
            boolean specifiesSchema,
            boolean specifiesFields,
            boolean specifiesActions,
            boolean specifiesConstraints
    ) {}
}
