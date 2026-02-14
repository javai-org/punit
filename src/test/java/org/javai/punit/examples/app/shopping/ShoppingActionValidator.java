package org.javai.punit.examples.app.shopping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.javai.outcome.Outcome;
import org.javai.punit.examples.app.llm.ChatResponse;

/**
 * Validates and parses LLM responses into {@link ShoppingAction} instances.
 *
 * <p>This validator attempts to deserialize JSON content into shopping actions,
 * capturing validation failures as {@link Outcome} results rather than exceptions.
 */
public class ShoppingActionValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * The result of validating an LLM response.
     *
     * @param actions the parsed actions (empty if validation failed)
     */
	public record ValidationResult(List<ShoppingAction> actions) {
        static ValidationResult of(List<ShoppingAction> actions) {
            return new ValidationResult(List.copyOf(actions));
        }
    }

    /**
     * Parses and validates a chat response as shopping actions.
     *
     * <p>Expects the wrapped format: {@code {"actions": [{"context": "SHOP", ...}, ...]}}
     *
     * @param response the chat response containing JSON content
     * @return an outcome containing the validation result, or a failure with details
     */
    public static Outcome<ValidationResult> validate(ChatResponse response) {
        String json = response.content();
        if (json == null || json.isBlank()) {
            return Outcome.fail("validation", "Response content is null or blank");
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            return Outcome.fail("validation", "Invalid JSON: " + e.getMessage());
        }

        if (!root.isObject() || !root.has("actions")) {
            return Outcome.fail("validation", "Expected JSON object with 'actions' array");
        }

        JsonNode actionsNode = root.get("actions");
        if (!actionsNode.isArray()) {
            return Outcome.fail("validation", "Expected 'actions' to be an array");
        }

        return parseActionArray(actionsNode);
    }

    private static Outcome<ValidationResult> parseActionArray(JsonNode arrayNode) {
        List<ShoppingAction> actions = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        int index = 0;
        for (JsonNode node : arrayNode) {
            try {
                ShoppingAction action = MAPPER.treeToValue(node, ShoppingAction.class);
                actions.add(action);
            } catch (JsonProcessingException e) {
                errors.add("Action[%d]: %s".formatted(index, e.getMessage()));
            } catch (IllegalArgumentException e) {
                errors.add("Action[%d]: %s".formatted(index, e.getMessage()));
            }
            index++;
        }

        if (!errors.isEmpty()) {
            return Outcome.fail("validation", String.join("; ", errors));
        }

        if (actions.isEmpty()) {
            return Outcome.fail("validation", "Empty actions array");
        }

        return Outcome.ok(ValidationResult.of(actions));
    }
}
