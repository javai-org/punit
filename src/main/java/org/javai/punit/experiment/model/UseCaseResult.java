package org.javai.punit.experiment.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A neutral container for observed outcomes from a use case invocation.
 *
 * <p>{@code UseCaseResult} holds key-value pairs representing the observations
 * made during use case execution. It is:
 * <ul>
 *   <li><strong>Neutral and descriptive</strong>: Contains data, not judgments. Whether
 *       a value represents "success" or "failure" is determined by the interpreter
 *       (experiment or test), not the result itself.</li>
 *   <li><strong>Flexible</strong>: The {@code Map<String, Object>} allows domain-specific
 *       values without requiring framework changes.</li>
 *   <li><strong>Immutable</strong>: Once constructed, results cannot be modified.</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * UseCaseResult result = UseCaseResult.builder()
 *     .value("isValid", true)
 *     .value("score", 0.95)
 *     .value("responseText", "Generated content...")
 *     .value("tokensUsed", 150)
 *     .meta("requestId", "abc-123")
 *     .build();
 *
 * // Retrieve values with type safety
 * boolean isValid = result.getBoolean("isValid", false);
 * double score = result.getDouble("score", 0.0);
 * int tokens = result.getInt("tokensUsed", 0);
 * }</pre>
 *
 * @see org.javai.punit.experiment.api.UseCase
 */
public final class UseCaseResult {
    
    private final Map<String, Object> values;
    private final Map<String, Object> metadata;
    private final Instant timestamp;
    private final Duration executionTime;
    
    private UseCaseResult(Builder builder) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(builder.values));
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
        this.timestamp = builder.timestamp;
        this.executionTime = builder.executionTime;
    }
    
    /**
     * Creates a new builder for constructing a {@code UseCaseResult}.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Returns a value by key with type checking.
     *
     * @param <T> the expected value type
     * @param key the value key
     * @param type the expected value type class
     * @return an Optional containing the value, or empty if not present
     * @throws ClassCastException if the value exists but is not of the expected type
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getValue(String key, Class<T> type) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(type, "type must not be null");
        
        Object val = values.get(key);
        if (val == null) {
            return Optional.empty();
        }
        if (!type.isInstance(val)) {
            throw new ClassCastException("Value '" + key + "' is " +
                val.getClass().getName() + ", not " + type.getName());
        }
        return Optional.of((T) val);
    }
    
    /**
     * Returns a value by key with a default fallback.
     *
     * @param <T> the expected value type
     * @param key the value key
     * @param type the expected value type class
     * @param defaultValue the default value if not present
     * @return the value or the default value
     * @throws ClassCastException if the value exists but is not of the expected type
     */
    public <T> T getValue(String key, Class<T> type, T defaultValue) {
        return getValue(key, type).orElse(defaultValue);
    }
    
    /**
     * Returns a boolean value with a default fallback.
     *
     * @param key the value key
     * @param defaultValue the default value if not present
     * @return the boolean value or the default
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        return getValue(key, Boolean.class, defaultValue);
    }
    
    /**
     * Returns an integer value with a default fallback.
     *
     * <p>This method handles numeric type coercion: any {@link Number} value
     * will be converted to an integer.
     *
     * @param key the value key
     * @param defaultValue the default value if not present
     * @return the integer value or the default
     */
    public int getInt(String key, int defaultValue) {
        Object val = values.get(key);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        throw new ClassCastException("Value '" + key + "' is " +
            val.getClass().getName() + ", not a Number");
    }
    
    /**
     * Returns a long value with a default fallback.
     *
     * <p>This method handles numeric type coercion: any {@link Number} value
     * will be converted to a long.
     *
     * @param key the value key
     * @param defaultValue the default value if not present
     * @return the long value or the default
     */
    public long getLong(String key, long defaultValue) {
        Object val = values.get(key);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        throw new ClassCastException("Value '" + key + "' is " +
            val.getClass().getName() + ", not a Number");
    }
    
    /**
     * Returns a double value with a default fallback.
     *
     * <p>This method handles numeric type coercion: any {@link Number} value
     * will be converted to a double.
     *
     * @param key the value key
     * @param defaultValue the default value if not present
     * @return the double value or the default
     */
    public double getDouble(String key, double defaultValue) {
        Object val = values.get(key);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        throw new ClassCastException("Value '" + key + "' is " +
            val.getClass().getName() + ", not a Number");
    }
    
    /**
     * Returns a string value with a default fallback.
     *
     * @param key the value key
     * @param defaultValue the default value if not present
     * @return the string value or the default
     */
    public String getString(String key, String defaultValue) {
        return getValue(key, String.class, defaultValue);
    }
    
    /**
     * Returns all values as an unmodifiable map.
     *
     * @return unmodifiable map of all values
     */
    public Map<String, Object> getAllValues() {
        return values;
    }
    
    /**
     * Returns all metadata as an unmodifiable map.
     *
     * @return unmodifiable map of all metadata
     */
    public Map<String, Object> getAllMetadata() {
        return metadata;
    }
    
    /**
     * Returns the timestamp when this result was created.
     *
     * @return the creation timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }
    
    /**
     * Returns the execution time of the use case invocation.
     *
     * @return the execution duration
     */
    public Duration getExecutionTime() {
        return executionTime;
    }
    
    /**
     * Returns true if this result has a value for the given key.
     *
     * @param key the value key
     * @return true if a value exists for the key
     */
    public boolean hasValue(String key) {
        return values.containsKey(key);
    }
    
    @Override
    public String toString() {
        return "UseCaseResult{" +
            "values=" + values +
            ", metadata=" + metadata +
            ", timestamp=" + timestamp +
            ", executionTime=" + executionTime +
            '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UseCaseResult that = (UseCaseResult) o;
        return Objects.equals(values, that.values) &&
               Objects.equals(metadata, that.metadata) &&
               Objects.equals(timestamp, that.timestamp) &&
               Objects.equals(executionTime, that.executionTime);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(values, metadata, timestamp, executionTime);
    }
    
    /**
     * Builder for constructing {@code UseCaseResult} instances.
     */
    public static final class Builder {
        
        private final Map<String, Object> values = new LinkedHashMap<>();
        private final Map<String, Object> metadata = new LinkedHashMap<>();
        private Instant timestamp = Instant.now();
        private Duration executionTime = Duration.ZERO;
        
        private Builder() {}
        
        /**
         * Adds a value to the result.
         *
         * @param key the value key (must not be null)
         * @param val the value (may be null)
         * @return this builder
         */
        public Builder value(String key, Object val) {
            Objects.requireNonNull(key, "key must not be null");
            values.put(key, val);
            return this;
        }
        
        /**
         * Adds metadata to the result.
         *
         * <p>Metadata is for contextual information (e.g., request IDs, backend info)
         * that is not part of the primary observation.
         *
         * @param key the metadata key (must not be null)
         * @param val the metadata value (may be null)
         * @return this builder
         */
        public Builder meta(String key, Object val) {
            Objects.requireNonNull(key, "key must not be null");
            metadata.put(key, val);
            return this;
        }
        
        /**
         * Sets the execution time of the use case invocation.
         *
         * @param duration the execution duration
         * @return this builder
         */
        public Builder executionTime(Duration duration) {
            this.executionTime = Objects.requireNonNull(duration, "duration must not be null");
            return this;
        }
        
        /**
         * Sets the timestamp for this result.
         *
         * <p>By default, the timestamp is set to the current time when the builder
         * is created. This method allows overriding that timestamp.
         *
         * @param timestamp the timestamp
         * @return this builder
         */
        public Builder timestamp(Instant timestamp) {
            this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
            return this;
        }
        
        /**
         * Builds the {@code UseCaseResult}.
         *
         * @return the constructed result
         */
        public UseCaseResult build() {
            return new UseCaseResult(this);
        }
    }
}

