package org.javai.punit.examples.usecases;

import java.util.List;
import org.javai.outcome.Outcome;
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
import org.javai.punit.examples.infrastructure.llm.ChatLlmProvider;
import org.javai.punit.examples.infrastructure.llm.ChatResponse;

/**
 * Use case for translating natural language shopping instructions to structured actions.
 *
 * <p>This use case demonstrates the <b>empirical approach</b> to probabilistic testing,
 * where thresholds are derived from measured baselines rather than external contracts.
 *
 * <h2>Domain</h2>
 * <p>A user provides natural language instructions like "Add 2 apples" or "Clear the basket",
 * and an LLM translates these into structured {@link ShoppingAction} objects that can be
 * executed against a shopping basket API.
 *
 * <h2>JSON Format</h2>
 * <pre>{@code
 * {
 *   "context": "SHOP",
 *   "name": "add",
 *   "parameters": [
 *     {"name": "item", "value": "apple"},
 *     {"name": "quantity", "value": "2"}
 *   ]
 * }
 * }</pre>
 *
 * <h2>Success Criteria</h2>
 * <ol>
 *   <li>Valid JSON (parseable)</li>
 *   <li>Deserializes to a valid {@link ShoppingAction}</li>
 *   <li>Action name is valid for the given context</li>
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
 * @see ShoppingAction
 * @see ShoppingActionValidator
 */
@UseCase(
        description = "Translate natural language shopping instructions to structured actions",
        covariates = {StandardCovariate.WEEKDAY_VERSUS_WEEKEND, StandardCovariate.TIME_OF_DAY},
        categorizedCovariates = {
                @Covariate(key = "llm_model", category = CovariateCategory.CONFIGURATION),
                @Covariate(key = "temperature", category = CovariateCategory.CONFIGURATION)
        }
)
public class ShoppingBasketUseCase {

    /**
     * Input parameters for the translation service.
     */
    private record ServiceInput(String systemPrompt, String instruction, String model, double temperature) {}

    /**
     * The service contract defining postconditions for translation results.
     */
    private static final ServiceContract<ServiceInput, ChatResponse> CONTRACT =
            ServiceContract.<ServiceInput, ChatResponse>define()
                    .ensure("Response has content", response ->
                            response.content() != null && !response.content().isBlank()
                                    ? Outcome.ok()
                                    : Outcome.fail("check", "content was null or blank"))
                    .derive("Valid shopping action", ShoppingActionValidator::validate)
                    .ensure("Contains valid actions", result -> {
                        if (result.actions().isEmpty()) {
                            return Outcome.fail("check", "No actions in result");
                        }
                        for (ShoppingAction action : result.actions()) {
                            if (!action.context().isValidAction(action.name())) {
                                return Outcome.fail("check",
                                        "Invalid action '%s' for context %s"
                                                .formatted(action.name(), action.context()));
                            }
                        }
                        return Outcome.ok();
                    })
                    .build();

    private final ChatLlm llm;
    private String model = "gpt-4o-mini";
    private double temperature = 0.3;
    private String systemPrompt = """
            You are a shopping assistant that converts natural language instructions into JSON actions.

            Respond ONLY with valid JSON in this exact format:
            {
              "context": "SHOP",
              "name": "<action>",
              "parameters": [
                {"name": "item", "value": "<item_name>"},
                {"name": "quantity", "value": "<number>"}
              ]
            }

            Valid actions for SHOP context: "add", "remove", "clear"
            For "clear" actions, parameters may be empty.

            Examples:
            - "Add 2 apples" -> {"context": "SHOP", "name": "add", "parameters": [{"name": "item", "value": "apple"}, {"name": "quantity", "value": "2"}]}
            - "Clear the basket" -> {"context": "SHOP", "name": "clear", "parameters": []}
            """;

    /**
     * Creates a use case with the LLM resolved from configuration.
     *
     * @see ChatLlmProvider#resolve()
     */
    public ShoppingBasketUseCase() {
        this(ChatLlmProvider.resolve());
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
     */
    public void resetTokenCount() {
        llm.resetTokenCount();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // USE CASE METHOD
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Translates a natural language shopping instruction to a structured action.
     *
     * @param instruction the natural language instruction (e.g., "Add 2 apples")
     * @return outcome containing the result and postcondition evaluations
     */
    public UseCaseOutcome<ChatResponse> translateInstruction(String instruction) {
        return UseCaseOutcome
                .withContract(CONTRACT)
                .input(new ServiceInput(systemPrompt, instruction, model, temperature))
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

    private ChatResponse executeTranslation(ServiceInput input) {
        return llm.chatWithMetadata(
                input.systemPrompt(),
                input.instruction(),
                input.model(),
                input.temperature()
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FACTOR PROVIDERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * A simple, single-item instruction.
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
     */
    @FactorProvider
    public static List<FactorArguments> multipleBasketInstructions() {
        return FactorArguments.configurations()
                .names("instruction")
                .values("Add 2 apples")
                .values("Remove the milk")
                .values("Add 1 loaf of bread")
                .values("Add 3 oranges and 2 bananas")
                .values("Add 5 tomatoes and remove the cheese")
                .values("Clear the basket")
                .values("Clear everything")
                .values("Remove 2 eggs from the basket")
                .values("Add a dozen eggs")
                .values("I'd like to remove all the vegetables")
                .stream().toList();
    }
}
