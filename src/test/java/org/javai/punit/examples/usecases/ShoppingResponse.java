package org.javai.punit.examples.usecases;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response envelope containing one or more shopping actions.
 *
 * <p>This wrapper ensures consistent JSON structure regardless of whether the
 * instruction requires one action or multiple actions:
 *
 * <pre>{@code
 * {
 *   "actions": [
 *     {"context": "SHOP", "name": "add", "parameters": [...]},
 *     {"context": "SHOP", "name": "remove", "parameters": [...]}
 *   ]
 * }
 * }</pre>
 *
 * @param actions the list of actions to perform (must contain at least one)
 */
public record ShoppingResponse(
        @JsonProperty("actions") List<ShoppingAction> actions
) {

    /**
     * Compact constructor that validates and makes a defensive copy.
     */
    public ShoppingResponse {
        if (actions == null || actions.isEmpty()) {
            throw new IllegalArgumentException("Response must contain at least one action");
        }
        actions = List.copyOf(actions);
    }

    /**
     * JSON deserialization constructor.
     */
    @JsonCreator
    public static ShoppingResponse fromJson(
            @JsonProperty("actions") List<ShoppingAction> actions) {
        return new ShoppingResponse(actions);
    }

    /**
     * Creates a response containing a single action.
     *
     * @param action the action to wrap
     * @return a response containing the single action
     */
    public static ShoppingResponse of(ShoppingAction action) {
        return new ShoppingResponse(List.of(action));
    }

    /**
     * Creates a response containing multiple actions.
     *
     * @param actions the actions to include
     * @return a response containing all actions
     */
    public static ShoppingResponse of(ShoppingAction... actions) {
        return new ShoppingResponse(List.of(actions));
    }
}
