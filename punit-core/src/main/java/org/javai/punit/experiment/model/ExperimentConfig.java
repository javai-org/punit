package org.javai.punit.experiment.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Represents a concrete combination of ExperimentLevels: one setting per factor.
 *
 * <p>An ExperimentConfig is:
 * <ul>
 *   <li><strong>Fully specified</strong>: All factor levels are defined</li>
 *   <li><strong>Executable</strong>: Can be used to run samples</li>
 *   <li><strong>Immutable</strong>: Cannot be modified after creation</li>
 * </ul>
 */
public final class ExperimentConfig {

    private final int index;
    private final String name;
    private final FactorSuit factorSuit;

    private ExperimentConfig(int index, String name, FactorSuit factorSuit) {
        this.index = index;
        this.name = name;
        this.factorSuit = factorSuit;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Creates a config with the given factor mappings.
     *
     * @param factors the factor-to-level mappings
     * @return the config
     */
    public static ExperimentConfig of(Map<String, Object> factors) {
        return new ExperimentConfig(0, null, FactorSuit.of(factors));
    }

    /**
     * Creates a config from a FactorSuit.
     *
     * @param factorSuit the factor suit
     * @return the config
     */
    public static ExperimentConfig of(FactorSuit factorSuit) {
        return new ExperimentConfig(0, null, factorSuit);
    }
    
    /**
     * Returns the config index (0-based position in config list).
     *
     * @return the index
     */
    public int getIndex() {
        return index;
    }
    
    /**
     * Returns the optional config name.
     *
     * @return the name, or null if not set
     */
    public String getName() {
        return name;
    }
    
    /**
     * Returns all factor-to-level mappings.
     *
     * @return unmodifiable map of factors
     */
    public Map<String, Object> getFactors() {
        return factorSuit.asMap();
    }

    /**
     * Returns the underlying FactorSuit.
     *
     * @return the factor suit
     */
    public FactorSuit getFactorSuit() {
        return factorSuit;
    }

    /**
     * Returns the level for a specific factor.
     *
     * @param factorName the factor name
     * @return the level, or null if not present
     */
    public Object getLevel(String factorName) {
        return factorSuit.get(factorName);
    }

    /**
     * Returns the level for a specific factor with type casting.
     *
     * @param <T> the expected type
     * @param factorName the factor name
     * @param type the expected type class
     * @return an Optional containing the level, or empty if not present
     * @throws ClassCastException if the level is not of the expected type
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getLevel(String factorName, Class<T> type) {
        Object level = factorSuit.get(factorName);
        if (level == null) {
            return Optional.empty();
        }
        if (!type.isInstance(level)) {
            throw new ClassCastException("Factor '" + factorName + "' level is " +
                level.getClass().getName() + ", not " + type.getName());
        }
        return Optional.of((T) level);
    }
    
    /**
     * Returns a string representation suitable for file naming.
     *
     * @return the config as a filename-safe string
     */
    public String toFilenameString() {
        if (name != null && !name.isEmpty()) {
            return name.replaceAll("[^a-zA-Z0-9-_]", "_");
        }
        return factorSuit.asMap().entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(","))
            .replaceAll("[^a-zA-Z0-9-_=,]", "_");
    }

    /**
     * Returns a display name for this config.
     *
     * @return the display name
     */
    public String getDisplayName() {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return factorSuit.asMap().entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(", ", "{", "}"));
    }

    @Override
    public String toString() {
        return "ExperimentConfig{" +
            "index=" + index +
            ", name='" + name + '\'' +
            ", factorSuit=" + factorSuit +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExperimentConfig that = (ExperimentConfig) o;
        return Objects.equals(factorSuit, that.factorSuit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(factorSuit);
    }
    
    public static final class Builder {

        private int index = 0;
        private String name;
        private final Map<String, Object> factors = new LinkedHashMap<>();

        private Builder() {}

        public Builder index(int index) {
            this.index = index;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder factor(String name, Object level) {
            factors.put(name, level);
            return this;
        }

        public Builder factors(Map<String, Object> factors) {
            if (factors != null) {
                this.factors.putAll(factors);
            }
            return this;
        }

        public Builder factorSuit(FactorSuit factorSuit) {
            if (factorSuit != null) {
                this.factors.putAll(factorSuit.asMap());
            }
            return this;
        }

        public ExperimentConfig build() {
            return new ExperimentConfig(index, name, FactorSuit.of(factors));
        }
    }
}

