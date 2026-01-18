package org.javai.punit.examples2.usecases;

import org.javai.punit.api.Covariate;
import org.javai.punit.api.CovariateCategory;
import org.javai.punit.api.CovariateSource;
import org.javai.punit.api.FactorArguments;
import org.javai.punit.api.FactorSetter;
import org.javai.punit.api.StandardCovariate;
import org.javai.punit.api.TreatmentValueSource;
import org.javai.punit.api.UseCase;
import org.javai.punit.api.UseCaseContract;
import org.javai.punit.examples2.infrastructure.llm.ChatLlm;
import org.javai.punit.examples2.infrastructure.llm.ChatResponse;
import org.javai.punit.examples2.infrastructure.llm.MockChatLlm;
import org.javai.punit.model.UseCaseCriteria;
import org.javai.punit.model.UseCaseOutcome;
import org.javai.punit.model.UseCaseResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Use case for translating natural language shopping instructions to JSON operations.
 *
 * <p>This use case demonstrates the <b>empirical approach</b> to probabilistic testing,
 * where thresholds are derived from measured baselines rather than external contracts.
 *
 * <h2>Domain</h2>
 * <p>A user provides natural language instructions like "Add 2 apples and remove the bread",
 * and an LLM translates these into structured JSON operations that can be executed against
 * a shopping basket API.
 *
 * <h2>JSON Format</h2>
 * <pre>{@code
 * {
 *   "operations": [
 *     {"action": "add", "item": "apples", "quantity": 2},
 *     {"action": "remove", "item": "bread", "quantity": 1}
 *   ]
 * }
 * }</pre>
 *
 * <h2>Success Criteria</h2>
 * <ol>
 *   <li>Valid JSON (parseable)</li>
 *   <li>Has "operations" array</li>
 *   <li>Each operation has: action, item, quantity</li>
 *   <li>Actions are valid ("add", "remove", "clear")</li>
 *   <li>Quantities are positive integers</li>
 * </ol>
 *
 * <h2>Covariates</h2>
 * <p>This use case tracks covariates that may affect LLM behavior:
 * <ul>
 *   <li>{@code WEEKDAY_VERSUS_WEEKEND} - Temporal context (TEMPORAL)</li>
 *   <li>{@code TIME_OF_DAY} - Temporal context (TEMPORAL)</li>
 *   <li>{@code llm_model} - Which model is being used (CONFIGURATION)</li>
 *   <li>{@code temperature} - Temperature setting (CONFIGURATION)</li>
 * </ul>
 *
 * @see org.javai.punit.examples2.experiments.ShoppingBasketMeasure
 * @see org.javai.punit.examples2.tests.ShoppingBasketTest
 */
@UseCase(
        description = "Translate natural language shopping instructions to JSON basket operations",
        covariates = {StandardCovariate.WEEKDAY_VERSUS_WEEKEND, StandardCovariate.TIME_OF_DAY},
        categorizedCovariates = {
                @Covariate(key = "llm_model", category = CovariateCategory.CONFIGURATION),
                @Covariate(key = "temperature", category = CovariateCategory.CONFIGURATION)
        }
)
public class ShoppingBasketUseCase implements UseCaseContract {

    private static final Set<String> VALID_ACTIONS = Set.of("add", "remove", "clear");

    // Pattern to match operations array
    private static final Pattern OPERATIONS_ARRAY_PATTERN =
            Pattern.compile("\"operations\"\\s*:\\s*\\[([^\\]]*)]", Pattern.DOTALL);

    // Pattern to match individual operations
    private static final Pattern OPERATION_PATTERN =
            Pattern.compile("\\{[^}]*\"action\"\\s*:\\s*\"?([^\"\\},]+)\"?[^}]*\"item\"\\s*:\\s*\"([^\"]+)\"[^}]*\"quantity\"\\s*:\\s*(\\d+|-?\\d+|\"[^\"]*\"|null)[^}]*}|\\{[^}]*\"action\"\\s*:\\s*\"?([^\"\\},]+)\"?[^}]*}|\\{[^}]*\"item\"\\s*:\\s*\"([^\"]+)\"[^}]*}|\\{[^}]*\"quantity\"\\s*:\\s*(\\d+)[^}]*}");

    private final ChatLlm llm;
    private String model = "mock-llm";
    private double temperature = 0.3;
    private int lastTokensUsed = 0;
    private String systemPrompt = """
            You are a shopping assistant that converts natural language instructions into JSON operations.

            Respond ONLY with valid JSON in this exact format:
            {
              "operations": [
                {"action": "add", "item": "item_name", "quantity": 1},
                {"action": "remove", "item": "item_name", "quantity": 1}
              ]
            }

            Valid actions are: "add", "remove", "clear"
            Quantities must be positive integers.
            """;

    /**
     * Creates a use case with the default mock LLM.
     */
    public ShoppingBasketUseCase() {
        this(MockChatLlm.instance());
    }

    /**
     * Creates a use case with a specific LLM implementation.
     *
     * @param llm the chat LLM to use
     */
    public ShoppingBasketUseCase(ChatLlm llm) {
        this.llm = llm;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COVARIATE SOURCES
    // ═══════════════════════════════════════════════════════════════════════════

    @CovariateSource("llm_model")
    public String getModel() {
        return model;
    }

    @CovariateSource("temperature")
    public String getTemperatureAsString() {
        return String.valueOf(temperature);
    }

    public double getTemperature() {
        return temperature;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FACTOR SETTERS - For auto-wired factor injection
    // ═══════════════════════════════════════════════════════════════════════════

    @FactorSetter("model")
    public void setModel(String model) {
        this.model = model;
    }

    @FactorSetter("temperature")
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    @FactorSetter("systemPrompt")
    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    @TreatmentValueSource("systemPrompt")
    public String getSystemPrompt() {
        return systemPrompt;
    }

    @TreatmentValueSource("temperature")
    public Double getTreatmentTemperature() {
        return temperature;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TOKEN TRACKING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the token count from the most recent {@link #translateInstruction} call.
     *
     * <p>Use this method to record actual token usage with
     * {@link org.javai.punit.api.TokenChargeRecorder}:
     *
     * <pre>{@code
     * var outcome = useCase.translateInstruction(instruction);
     * tokenRecorder.recordTokens(useCase.getLastTokensUsed());
     * outcome.assertAll();
     * }</pre>
     *
     * @return the total tokens (prompt + completion) from the last call
     */
    public int getLastTokensUsed() {
        return lastTokensUsed;
    }

    /**
     * Returns the cumulative tokens used by the underlying LLM since the last reset.
     *
     * @return total tokens across all calls
     */
    public long getTotalTokensUsed() {
        return llm.getTotalTokensUsed();
    }

    /**
     * Resets the cumulative token counter in the underlying LLM.
     *
     * <p>Call this between test runs to start fresh token tracking.
     */
    public void resetTokenCount() {
        llm.resetTokenCount();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // USE CASE METHOD
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Translates a natural language shopping instruction to JSON operations.
     *
     * <p>After calling this method, use {@link #getLastTokensUsed()} to retrieve
     * the token count for this invocation. This is useful for dynamic token
     * budget tracking with {@link org.javai.punit.api.TokenChargeRecorder}.
     *
     * @param instruction the natural language instruction (e.g., "Add 2 apples and remove the bread")
     * @return outcome containing result and success criteria
     */
    public UseCaseOutcome translateInstruction(String instruction) {
        Instant start = Instant.now();

        // Call the LLM and track tokens
        ChatResponse chatResponse = llm.chatWithMetadata(systemPrompt, instruction, temperature);
        String response = chatResponse.content();
        lastTokensUsed = chatResponse.totalTokens();

        Duration executionTime = Duration.between(start, Instant.now());

        // Validate the response using simple parsing
        ValidationResult validation = validateResponse(response);

        UseCaseResult result = UseCaseResult.builder()
                .value("isValidJson", validation.isValidJson)
                .value("hasOperationsArray", validation.hasOperationsArray)
                .value("allOperationsValid", validation.allOperationsValid)
                .value("allActionsValid", validation.allActionsValid)
                .value("allQuantitiesPositive", validation.allQuantitiesPositive)
                .value("rawResponse", response)
                .value("parseError", validation.parseError)
                .meta("instruction", instruction)
                .meta("model", model)
                .meta("temperature", temperature)
                .executionTime(executionTime)
                .build();

        // Define success criteria
        UseCaseCriteria criteria = UseCaseCriteria.ordered()
                .criterion("Valid JSON",
                        () -> result.getBoolean("isValidJson", false))
                .criterion("Has operations array",
                        () -> result.getBoolean("hasOperationsArray", false))
                .criterion("Operations have required fields",
                        () -> result.getBoolean("allOperationsValid", false))
                .criterion("Actions are valid",
                        () -> result.getBoolean("allActionsValid", false))
                .criterion("Quantities are positive",
                        () -> result.getBoolean("allQuantitiesPositive", false))
                .build();

        return new UseCaseOutcome(result, criteria);
    }

    /**
     * Validates a JSON response using simple string/regex parsing.
     * <p>
     * Note: This is a simplified validator for the example. In production,
     * you would use a proper JSON parser like Jackson or Gson.
     */
    private ValidationResult validateResponse(String response) {
        ValidationResult result = new ValidationResult();

        if (response == null || response.isBlank()) {
            result.parseError = "Response is null or empty";
            return result;
        }

        // Basic JSON structure validation
        String trimmed = response.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            result.parseError = "Response does not start with { or end with }";
            return result;
        }

        // Check balanced braces and brackets
        if (!hasBalancedBraces(trimmed)) {
            result.parseError = "Unbalanced braces or brackets";
            return result;
        }

        result.isValidJson = true;

        // Check for operations array
        Matcher operationsMatcher = OPERATIONS_ARRAY_PATTERN.matcher(response);
        if (!operationsMatcher.find()) {
            result.hasOperationsArray = false;
            return result;
        }

        result.hasOperationsArray = true;
        String operationsContent = operationsMatcher.group(1);

        // Parse operations
        List<ParsedOperation> operations = parseOperations(operationsContent);

        if (operations.isEmpty() && !operationsContent.trim().isEmpty()) {
            // There's content but we couldn't parse it
            result.allOperationsValid = false;
            return result;
        }

        // Validate each operation
        result.allOperationsValid = true;
        result.allActionsValid = true;
        result.allQuantitiesPositive = true;

        for (ParsedOperation op : operations) {
            if (op.action == null || op.item == null || op.quantity == null) {
                result.allOperationsValid = false;
            }
            if (op.action != null && !VALID_ACTIONS.contains(op.action.toLowerCase())) {
                result.allActionsValid = false;
            }
            if (op.quantity == null || op.quantity <= 0) {
                result.allQuantitiesPositive = false;
            }
        }

        return result;
    }

    private boolean hasBalancedBraces(String s) {
        int braces = 0;
        int brackets = 0;
        boolean inString = false;
        char prev = 0;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c == '"' && prev != '\\') {
                inString = !inString;
            } else if (!inString) {
                switch (c) {
                    case '{' -> braces++;
                    case '}' -> braces--;
                    case '[' -> brackets++;
                    case ']' -> brackets--;
                }
                if (braces < 0 || brackets < 0) {
                    return false;
                }
            }
            prev = c;
        }

        return braces == 0 && brackets == 0;
    }

    private List<ParsedOperation> parseOperations(String content) {
        List<ParsedOperation> operations = new ArrayList<>();

        // Find all objects in the array
        Pattern objectPattern = Pattern.compile("\\{([^{}]*)}", Pattern.DOTALL);
        Matcher matcher = objectPattern.matcher(content);

        while (matcher.find()) {
            String objectContent = matcher.group(1);
            ParsedOperation op = parseOperation(objectContent);
            operations.add(op);
        }

        return operations;
    }

    private ParsedOperation parseOperation(String content) {
        ParsedOperation op = new ParsedOperation();

        // Extract action
        Pattern actionPattern = Pattern.compile("\"action\"\\s*:\\s*\"([^\"]+)\"");
        Matcher actionMatcher = actionPattern.matcher(content);
        if (actionMatcher.find()) {
            op.action = actionMatcher.group(1);
        }

        // Extract item
        Pattern itemPattern = Pattern.compile("\"item\"\\s*:\\s*\"([^\"]+)\"");
        Matcher itemMatcher = itemPattern.matcher(content);
        if (itemMatcher.find()) {
            op.item = itemMatcher.group(1);
        }

        // Extract quantity
        Pattern quantityPattern = Pattern.compile("\"quantity\"\\s*:\\s*(\\d+)");
        Matcher quantityMatcher = quantityPattern.matcher(content);
        if (quantityMatcher.find()) {
            try {
                op.quantity = Integer.parseInt(quantityMatcher.group(1));
            } catch (NumberFormatException e) {
                op.quantity = null;
            }
        }

        return op;
    }

    private static class ValidationResult {
        boolean isValidJson = false;
        boolean hasOperationsArray = false;
        boolean allOperationsValid = false;
        boolean allActionsValid = false;
        boolean allQuantitiesPositive = false;
        String parseError = null;
    }

    private static class ParsedOperation {
        String action;
        String item;
        Integer quantity;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FACTOR SOURCES - Co-located with use case for consistency
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Single instruction factor source for controlled MEASURE experiments.
     *
     * <p>All samples use the same instruction, isolating LLM behavioral variance
     * from input variance.
     *
     * @return a single factor argument used for all samples
     */
    public static List<FactorArguments> singleInstruction() {
        return FactorArguments.configurations()
                .names("instruction")
                .values("Add 2 apples and remove the bread")
                .stream().toList();
    }

    /**
     * Standard instructions for MEASURE experiments.
     *
     * <p>Covers common shopping basket operations with varying complexity.
     *
     * @return factor arguments representing typical instructions
     */
    public static List<FactorArguments> standardInstructions() {
        return FactorArguments.configurations()
                .names("instruction")
                .values("Add 2 apples")
                .values("Remove the milk")
                .values("Add 3 oranges and 2 bananas")
                .values("Clear the basket")
                .values("Add 1 loaf of bread")
                .values("Remove 2 eggs from the basket")
                .values("Add 5 tomatoes and remove the cheese")
                .values("Clear everything")
                .values("Add a dozen eggs")
                .values("Remove all the vegetables")
                .stream().toList();
    }

    /**
     * Instructions for EXPLORE experiments comparing different phrasings.
     *
     * @return factor arguments with varied instruction styles
     */
    public static List<FactorArguments> exploreInstructions() {
        return FactorArguments.configurations()
                .names("instruction")
                // Simple commands
                .values("Add 2 apples")
                .values("Please add two apples to my basket")
                .values("I'd like 2 apples")
                // Multiple items
                .values("Add 3 oranges and 2 bananas")
                .values("I need oranges (3) and bananas (2)")
                .values("Get me three oranges plus two bananas")
                // Complex requests
                .values("Add 5 tomatoes and remove the cheese")
                .values("Put in 5 tomatoes but take out any cheese")
                .values("I want tomatoes, five of them, and no cheese please")
                .stream().toList();
    }
}
