package org.javai.punit.examples.app.shopping;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a parameter for a {@link ShoppingAction}.
 *
 * <p>Parameters are simple name-value pairs. The interpretation of the value
 * depends on the action and parameter name (e.g., "quantity" values are expected
 * to be numeric strings).
 *
 * @param name the parameter name (e.g., "item", "quantity")
 * @param value the parameter value as a string
 */
public record ShoppingActionParameter(
        @JsonProperty("name") String name,
        @JsonProperty("value") String value
) {

    /**
     * Compact constructor that validates parameters are non-null.
     */
    public ShoppingActionParameter {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Parameter name cannot be null or blank");
        }
        if (value == null) {
            throw new IllegalArgumentException("Parameter value cannot be null");
        }
    }

    /**
     * JSON deserialization constructor.
     */
    @JsonCreator
    public static ShoppingActionParameter fromJson(
            @JsonProperty("name") String name,
            @JsonProperty("value") String value) {
        return new ShoppingActionParameter(name, value);
    }

    /**
     * Returns the value as an integer.
     *
     * @return the value parsed as an integer
     * @throws NumberFormatException if the value is not a valid integer
     */
    public int valueAsInt() {
        return Integer.parseInt(value);
    }

    /**
     * Returns the value as a double.
     *
     * @return the value parsed as a double
     * @throws NumberFormatException if the value is not a valid number
     */
    public double valueAsDouble() {
        return Double.parseDouble(value);
    }
}
