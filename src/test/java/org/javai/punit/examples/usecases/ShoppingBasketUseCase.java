package org.javai.punit.examples.usecases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.javai.outcome.Outcome;
import org.javai.outcome.boundary.Boundary;
import org.javai.punit.api.Covariate;
import org.javai.punit.api.CovariateCategory;
import org.javai.punit.api.CovariateSource;
import org.javai.punit.api.FactorArguments;
import org.javai.punit.api.FactorGetter;
import org.javai.punit.api.FactorProvider;
import org.javai.punit.api.FactorSetter;
import org.javai.punit.api.StandardCovariate;
import org.javai.punit.api.UseCase;
import org.javai.punit.contract.Outcomes;
import org.javai.punit.contract.ServiceContract;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.punit.examples.infrastructure.llm.ChatLlm;
import org.javai.punit.examples.infrastructure.llm.ChatResponse;
import org.javai.punit.examples.infrastructure.llm.MockChatLlm;

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
     * The service contract defining postconditions for translation results.
     *
     * <p>This contract is defined once and reused for all invocations.
     * Postconditions are evaluated lazily when {@link UseCaseOutcome#evaluatePostconditions()}
     * or {@link UseCaseOutcome#assertAll()} is called.
     */
    private static final ServiceContract<ServiceInput, ChatResponse> CONTRACT =
            ServiceContract.<ServiceInput, ChatResponse>define()
                    .deriving("Response content", cr -> Outcome.ok(cr.content()))
                    .ensure("Viable response content", c ->
                            c != null && !c.isBlank() ? Outcome.ok() : Outcomes.fail("content was null or blank"))
                    .derive("Valid Json", ShoppingBasketUseCase::parseJSON)
                    .ensure("Has operations array", ShoppingBasketUseCase::hasOperationsArray)
                    .ensure("All operations valid", ShoppingBasketUseCase::allOperationsValid)
                    .build();

    private static Outcome<JsonNode> parseJSON(ChatResponse chatResponse) {
        String responseContent = chatResponse.content();
        return Boundary.silent().call("parseJSON", () -> OBJECT_MAPPER.readTree(responseContent));
    }

    private final ChatLlm llm;
    private String model = "mock-llm";
    private double temperature = 0.3;
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
     *   <li>Bundles metadata with the result, including token usage extracted from the response</li>
     * </ul>
     *
     * <p>Token counts are automatically extracted from the response and stored as metadata:
     * {@code tokensUsed}, {@code promptTokens}, and {@code completionTokens}.
     *
     * @param instruction the natural language instruction (e.g., "Add 2 apples and remove the bread")
     * @return outcome containing typed result and postconditions
     */
    public UseCaseOutcome<ChatResponse> translateInstruction(String instruction) {
        return UseCaseOutcome
                .withContract(CONTRACT)
                .input(new ServiceInput(systemPrompt, instruction, temperature))
                .execute(this::executeTranslation)
                .withResult((response, meta) -> meta
                        .meta("tokensUsed", response.totalTokens())
                        .meta("promptTokens", response.promptTokens())
                        .meta("completionTokens", response.completionTokens()))
                .meta("instruction", instruction)
                .meta("model", model)
                .meta("temperature", temperature)
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
    private ChatResponse executeTranslation(ServiceInput input) {
        // Call the LLM and track tokens
        return llm.chatWithMetadata(
                input.systemPrompt(),
                input.instruction(),
                input.temperature()
        );
    }

    private static Outcome<Void> hasOperationsArray(JsonNode root) {
        JsonNode operations = root.get("operations");
        if (operations == null) {
            return Outcomes.fail("missing 'operations' field");
        }
        if (!operations.isArray()) {
            return Outcomes.fail("'operations' is not an array");
        }
        return Outcome.ok();
    }

    private static Outcome<Void> allOperationsValid(JsonNode root) {
        JsonNode operations = root.get("operations");
        List<String> problems = new ArrayList<>();
        for (JsonNode op : operations) {
            JsonNode actionNode = op.get("action");
            if (actionNode == null) {
                problems.add("Missing action");
            } else {
                String action = actionNode.asText();
                if (!VALID_ACTIONS.contains(action.toLowerCase())) {
                    problems.add("Invalid action: " + action);
                }
            }
            JsonNode itemNode = op.get("item");
            if (itemNode == null) {
                problems.add("Missing item");
            }
            JsonNode quantityNode = op.get("quantity");
            if (quantityNode == null) {
                problems.add("Missing quantity");
            } else if (!quantityNode.isInt() || quantityNode.asInt() <= 0) {
                problems.add("Invalid quantity: " + quantityNode.asText());
            }
        }
        if (problems.isEmpty()) {
            return Outcome.ok();
        }
        return Outcomes.fail(String.join(", ", problems));
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
