package org.javai.punit.examples.app.shopping;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Set;

/**
 * Represents an action that can be performed within the shopping application.
 *
 * <p>Actions are context-specific: each {@link AppContext} defines its own set of valid
 * action names. This allows the same DSL structure to support different areas of the
 * application (e.g., basket operations vs recipe suggestions).
 *
 * @param context the application context this action belongs to
 * @param name the action name (must be valid for the given context)
 * @param parameters the action parameters
 */
public record ShoppingAction(
        @JsonProperty("context") AppContext context,
        @JsonProperty("name") String name,
        @JsonProperty("parameters") List<ShoppingActionParameter> parameters
) {

    /**
     * Compact constructor that validates the action name against the context.
     */
    public ShoppingAction {
        if (!context.isValidAction(name)) {
            throw new IllegalArgumentException(
                    "Invalid action '%s' for context %s. Valid actions: %s"
                            .formatted(name, context, context.validActions()));
        }
        parameters = List.copyOf(parameters);
    }

    /**
     * JSON deserialization constructor.
     */
    @JsonCreator
    public static ShoppingAction fromJson(
            @JsonProperty("context") AppContext context,
            @JsonProperty("name") String name,
            @JsonProperty("parameters") List<ShoppingActionParameter> parameters) {
        return new ShoppingAction(context, name, parameters != null ? parameters : List.of());
    }

    /**
     * Application contexts, each defining its own set of valid actions.
     */
    public enum  AppContext {
        /**
         * Shopping basket operations: adding, removing, and clearing items.
         */
        SHOP("add", "remove", "clear"),

        /**
         * Recipe discovery operations: suggesting, filtering, and saving recipes.
         */
        RECIPE("suggest", "filter", "save", "share");

        private final Set<String> validActions;

        AppContext(String... actions) {
            this.validActions = Set.of(actions);
        }

        /**
         * Checks if the given action name is valid for this context.
         *
         * @param action the action name to validate
         * @return true if the action is valid for this context
         */
        public boolean isValidAction(String action) {
            return action != null && validActions.contains(action.toLowerCase());
        }

        /**
         * Returns the set of valid action names for this context.
         *
         * @return unmodifiable set of valid action names
         */
        public Set<String> validActions() {
            return validActions;
        }
    }
}
