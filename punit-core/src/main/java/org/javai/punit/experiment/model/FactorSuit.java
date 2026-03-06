package org.javai.punit.experiment.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A complete set of factor values for a use case.
 *
 * <p>The term "FactorSuit" (like a suit of cards or suit of armor) captures the
 * concept of a complete matching set of factors. It replaces the vague term
 * "configuration" which could mean many things.
 *
 * <p>FactorSuit is immutable. Use {@link #with(String, Object)} to create a modified copy.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Create from key-value pairs
 * FactorSuit suit = FactorSuit.of(
 *     "model", "gpt-4",
 *     "temperature", 0.7,
 *     "systemPrompt", "You are helpful."
 * );
 *
 * // Create modified copy
 * FactorSuit modified = suit.with("temperature", 0.9);
 *
 * // Access values
 * String model = suit.get("model");
 * }</pre>
 */
public final class FactorSuit {

    private final Map<String, Object> values;

    /**
     * Creates a FactorSuit from the given values.
     *
     * @param values the factor name to value mapping
     */
    public FactorSuit(Map<String, Object> values) {
        this.values = Map.copyOf(values);  // Defensive copy, immutable
    }

    /**
     * Get a factor value by name.
     *
     * @param factorName the name of the factor
     * @param <F> the expected type of the factor value
     * @return the factor value, or null if not present
     */
    @SuppressWarnings("unchecked")
    public <F> F get(String factorName) {
        return (F) values.get(factorName);
    }

    /**
     * Check if this suit contains a factor with the given name.
     *
     * @param factorName the factor name to check
     * @return true if the factor exists
     */
    public boolean contains(String factorName) {
        return values.containsKey(factorName);
    }

    /**
     * Get all factor names in this suit.
     *
     * @return unmodifiable set of factor names
     */
    public Set<String> factorNames() {
        return values.keySet();
    }

    /**
     * Get the number of factors in this suit.
     *
     * @return the factor count
     */
    public int size() {
        return values.size();
    }

    /**
     * Create a new FactorSuit with one factor value changed.
     *
     * <p>This method does not modify the current instance.
     *
     * @param factorName the factor to change
     * @param value the new value
     * @return a new FactorSuit with the updated value
     */
    public FactorSuit with(String factorName, Object value) {
        Map<String, Object> newValues = new HashMap<>(values);
        newValues.put(factorName, value);
        return new FactorSuit(newValues);
    }

    /**
     * Get an unmodifiable view of all factor values.
     *
     * @return the factor values map
     */
    public Map<String, Object> asMap() {
        return values;
    }

    /**
     * Create a FactorSuit from a map.
     *
     * @param values the factor values
     * @return a new FactorSuit
     */
    public static FactorSuit of(Map<String, Object> values) {
        return new FactorSuit(values);
    }

    /**
     * Create a FactorSuit from key-value pairs.
     *
     * @param keyValuePairs alternating keys (String) and values (Object)
     * @return a new FactorSuit
     * @throws IllegalArgumentException if odd number of arguments or non-String keys
     */
    public static FactorSuit of(Object... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Must provide key-value pairs (even number of arguments)");
        }
        Map<String, Object> values = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            if (!(keyValuePairs[i] instanceof String)) {
                throw new IllegalArgumentException("Keys must be strings, got: " + keyValuePairs[i].getClass());
            }
            values.put((String) keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return new FactorSuit(values);
    }

    /**
     * Create an empty FactorSuit.
     *
     * @return an empty FactorSuit
     */
    public static FactorSuit empty() {
        return new FactorSuit(Map.of());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FactorSuit that = (FactorSuit) o;
        return Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return "FactorSuit" + values;
    }
}
