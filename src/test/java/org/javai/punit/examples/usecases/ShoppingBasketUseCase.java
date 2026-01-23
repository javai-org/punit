package org.javai.punit.examples.usecases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.javai.punit.api.Covariate;
import org.javai.punit.api.CovariateCategory;
import org.javai.punit.api.CovariateSource;
import org.javai.punit.api.FactorArguments;
import org.javai.punit.api.FactorGetter;
import org.javai.punit.api.FactorProvider;
import org.javai.punit.api.FactorSetter;
import org.javai.punit.api.StandardCovariate;
import org.javai.punit.api.UseCase;
import org.javai.punit.contract.ServiceContract;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.punit.examples.infrastructure.llm.ChatLlm;
import org.javai.punit.examples.infrastructure.llm.ChatResponse;
import org.javai.punit.examples.infrastructure.llm.MockChatLlm;

import java.util.List;
import java.util.Set;

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
 * @see org.javai.punit.examples.experiments.ShoppingBasketMeasure
 * @see org.javai.punit.examples.tests.ShoppingBasketTest
 */
@UseCase(
        description = "Translate natural language shopping instructions to JSON basket operations",
        covariates = {StandardCovariate.WEEKDAY_VERSUS_WEEKEND, StandardCovariate.TIME_OF_DAY},
        categorizedCovariates = {
                @Covariate(key = "llm_model", category = CovariateCategory.CONFIGURATION),
                @Covariate(key = "temperature", category = CovariateCategory.CONFIGURATION)
        }
)
public class ShoppingBasketUseCase {

    private static final Set<String> VALID_ACTIONS = Set.of("add", "remove", "clear");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Input parameters for the translation service.
     *
     * @param systemPrompt the system prompt for the LLM
     * @param instruction the user's natural language instruction
     * @param temperature the LLM temperature setting
     */
    private record ServiceInput(String systemPrompt, String instruction, double temperature) {}

    /**
     * Result of translating a natural language instruction to JSON operations.
     *
     * @param rawResponse the raw LLM response
     * @param isValidJson true if response is valid JSON
     * @param hasOperationsArray true if JSON has "operations" array
     * @param allOperationsValid true if all operations have required fields
     * @param allActionsValid true if all actions are valid (add/remove/clear)
     * @param allQuantitiesPositive true if all quantities are positive integers
     * @param parseError error message if parsing failed, null otherwise
     */
    public record TranslationResult(
            String rawResponse,
            boolean isValidJson,
            boolean hasOperationsArray,
            boolean allOperationsValid,
            boolean allActionsValid,
            boolean allQuantitiesPositive,
            String parseError
    ) {}

    /**
     * The service contract defining postconditions for translation results.
     *
     * <p>This contract is defined once and reused for all invocations.
     * Postconditions are evaluated lazily when {@link UseCaseOutcome#evaluatePostconditions()}
     * or {@link UseCaseOutcome#assertAll()} is called.
     */
    private static final ServiceContract<ServiceInput, TranslationResult> CONTRACT =
            ServiceContract.<ServiceInput, TranslationResult>define()
                    .require("System prompt valid", si -> si.systemPrompt() != null && !si.systemPrompt().isBlank())
                    .require("Instruction valid", si -> si.instruction() != null && !si.instruction().isBlank())
                    .require("Temperature valid", si -> si.temperature() >= 0.0 && si.temperature() <= 1.0)
                    .ensure("Valid JSON", TranslationResult::isValidJson)
                    .ensure("Has operations array", TranslationResult::hasOperationsArray)
                    .ensure("Operations have required fields", TranslationResult::allOperationsValid)
                    .ensure("Actions are valid", TranslationResult::allActionsValid)
                    .ensure("Quantities are positive", TranslationResult::allQuantitiesPositive)
                    .build();

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
    // FACTOR GETTERS AND COVARIATE SOURCES
    // ═══════════════════════════════════════════════════════════════════════════

    @FactorGetter
    @CovariateSource("llm_model")
    public String getModel() {
        return model;
    }

    @FactorGetter
    @CovariateSource
    public double getTemperature() {
        return temperature;
    }

    @FactorGetter
    public String getSystemPrompt() {
        return systemPrompt;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FACTOR SETTERS
    // ═══════════════════════════════════════════════════════════════════════════

    @FactorSetter("llm_model")
    public void setModel(String model) {
        this.model = model;
    }

    @FactorSetter
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    @FactorSetter
    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
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
     * <p>This method uses the fluent {@link UseCaseOutcome} builder API which:
     * <ul>
     *   <li>Automatically captures execution timing</li>
     *   <li>Evaluates postconditions lazily</li>
     *   <li>Bundles metadata with the result</li>
     * </ul>
     *
     * <p>After calling this method, use {@link #getLastTokensUsed()} to retrieve
     * the token count for this invocation. This is useful for dynamic token
     * budget tracking with {@link org.javai.punit.api.TokenChargeRecorder}.
     *
     * @param instruction the natural language instruction (e.g., "Add 2 apples and remove the bread")
     * @return outcome containing typed result and postconditions
     */
    public UseCaseOutcome<TranslationResult> translateInstruction(String instruction) {
        return UseCaseOutcome
                .withContract(CONTRACT)
                .input(new ServiceInput(systemPrompt, instruction, temperature))
                .execute(this::executeTranslation)
                .meta("instruction", instruction)
                .meta("model", model)
                .meta("temperature", temperature)
                .meta("tokensUsed", lastTokensUsed)
                .build();
    }

    /**
     * Executes the translation by calling the LLM and validating the response.
     *
     * <p>This method is called by the fluent builder's {@code execute()} step.
     * It handles the actual service interaction and result construction.
     *
     * @param input the service input parameters
     * @return the translation result with validation flags
     */
    private TranslationResult executeTranslation(ServiceInput input) {
        // Call the LLM and track tokens
        ChatResponse chatResponse = llm.chatWithMetadata(
                input.systemPrompt(),
                input.instruction(),
                input.temperature()
        );
        String response = chatResponse.content();
        lastTokensUsed = chatResponse.totalTokens();

        // Validate the response using Jackson
        ValidationResult validation = validateResponse(response);

        // Build and return typed result
        return new TranslationResult(
                response,
                validation.isValidJson,
                validation.hasOperationsArray,
                validation.allOperationsValid,
                validation.allActionsValid,
                validation.allQuantitiesPositive,
                validation.parseError
        );
    }

    /**
     * Validates a JSON response using Jackson.
     */
    private ValidationResult validateResponse(String response) {
        ValidationResult result = new ValidationResult();

        if (response == null || response.isBlank()) {
            result.parseError = "Response is null or empty";
            return result;
        }

        // Parse JSON
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(response);
        } catch (Exception e) {
            result.parseError = "Invalid JSON: " + e.getMessage();
            return result;
        }

        result.isValidJson = true;

        // Check for operations array
        JsonNode operations = root.get("operations");
        if (operations == null || !operations.isArray()) {
            result.hasOperationsArray = false;
            return result;
        }

        result.hasOperationsArray = true;

        // Validate each operation
        result.allOperationsValid = true;
        result.allActionsValid = true;
        result.allQuantitiesPositive = true;

        for (JsonNode op : operations) {
            JsonNode actionNode = op.get("action");
            JsonNode itemNode = op.get("item");
            JsonNode quantityNode = op.get("quantity");

            // Check required fields
            if (actionNode == null || itemNode == null || quantityNode == null) {
                result.allOperationsValid = false;
            }

            // Validate action
            if (actionNode != null) {
                String action = actionNode.asText();
                if (!VALID_ACTIONS.contains(action.toLowerCase())) {
                    result.allActionsValid = false;
                }
            }

            // Validate quantity
            if (quantityNode == null || !quantityNode.isInt() || quantityNode.asInt() <= 0) {
                result.allQuantitiesPositive = false;
            }
        }

        return result;
    }

    private static class ValidationResult {
        boolean isValidJson = false;
        boolean hasOperationsArray = false;
        boolean allOperationsValid = false;
        boolean allActionsValid = false;
        boolean allQuantitiesPositive = false;
        String parseError = null;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FACTOR PROVIDERS - Realistic input values
    //
    // These methods provide representative input values that the system should
    // handle reliably. Using them is optional, but they serve as useful examples
    // of the kinds of inputs production traffic will include.
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * A simple, single-item instruction.
     *
     * <p>Represents the most straightforward user request: adding a specific
     * quantity of one item. The system should handle this reliably.
     *
     * @return a single simple instruction
     */
    @FactorProvider
    public static List<FactorArguments> simpleBasketInstruction() {
        return FactorArguments.configurations()
                .names("instruction")
                .values("Add 2 apples")
                .stream().toList();
    }

    /**
     * A complex, multi-operation instruction.
     *
     * <p>Represents a more challenging user request: multiple operations
     * (add and remove) with specific quantities in a single instruction.
     * The system should parse and execute all operations correctly.
     *
     * @return a single complex instruction
     */
    @FactorProvider
    public static List<FactorArguments> complexBasketInstruction() {
        return FactorArguments.configurations()
                .names("instruction")
                .values("Add 3 oranges and 2 bananas, then remove the milk")
                .stream().toList();
    }

    /**
     * A variety of instructions representing realistic production traffic.
     *
     * <p>Includes the range of inputs the system should handle reliably:
     * <ul>
     *   <li>Simple single-item operations ("Add 2 apples")</li>
     *   <li>Multi-item operations ("Add 3 oranges and 2 bananas")</li>
     *   <li>Clear operations ("Clear the basket")</li>
     *   <li>Natural language variations ("I'd like to remove all the vegetables")</li>
     *   <li>Colloquial quantities ("Add a dozen eggs")</li>
     * </ul>
     *
     * @return factor arguments representing varied basket instructions
     */
    @FactorProvider
    public static List<FactorArguments> multipleBasketInstructions() {
        return FactorArguments.configurations()
                .names("instruction")
                // Simple single-item operations
                .values("Add 2 apples")
                .values("Remove the milk")
                .values("Add 1 loaf of bread")
                // Multi-item operations
                .values("Add 3 oranges and 2 bananas")
                .values("Add 5 tomatoes and remove the cheese")
                // Clear operations
                .values("Clear the basket")
                .values("Clear everything")
                // Natural language variations
                .values("Remove 2 eggs from the basket")
                .values("Add a dozen eggs")
                .values("I'd like to remove all the vegetables")
                .stream().toList();
    }
}
