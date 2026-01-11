package org.javai.punit.experiment.model;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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
 *   <li><strong>Immutable</strong>: Guaranteed by the record specification.</li>
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
public record UseCaseResult(
    Map<String, Object> values,
    Map<String, Object> metadata,
    Instant timestamp,
    Duration executionTime
) {
    
    /**
     * Compact constructor for defensive copying and validation.
     */
    public UseCaseResult {
        values = values != null ? Map.copyOf(values) : Map.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(executionTime, "executionTime must not be null");
    }
    
    /**
     * Creates a new builder for constructing a {@code UseCaseResult}.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    // ========== Diffable Content ==========
    
    /**
     * Returns the default diffable content for this result.
     *
     * <p>The default implementation:
     * <ul>
     *   <li>Iterates over the values map</li>
     *   <li>Converts each value to a string via {@code toString()}</li>
     *   <li>Sorts entries alphabetically by key</li>
     *   <li>Formats as "key: value" lines</li>
     *   <li>Truncates values exceeding maxLineLength with ellipsis (…)</li>
     * </ul>
     *
     * @param maxLineLength maximum characters per line (including key and separator)
     * @return list of formatted key-value lines, alphabetically ordered
     */
    public List<String> getDiffableContent(int maxLineLength) {
        return values.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> formatLine(e.getKey(), e.getValue(), maxLineLength))
            .toList();
    }
    
    private String formatLine(String key, Object value, int maxLineLength) {
        String valueStr = normalizeValue(value);
        String prefix = key + ": ";
        int maxValueLength = maxLineLength - prefix.length();
        
        if (maxValueLength <= 0) {
            // Key is too long; truncate key with ellipsis
            return key.substring(0, Math.min(key.length(), maxLineLength - 1)) + "…";
        }
        
        if (valueStr.length() > maxValueLength) {
            valueStr = valueStr.substring(0, maxValueLength - 1) + "…";
        }
        
        return prefix + valueStr;
    }
    
    private String normalizeValue(Object value) {
        if (value == null) {
            return "null";
        }
        // Single-line representation: escape control characters
        return value.toString()
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
    
    // ========== Convenience Accessors ==========
    
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
        Object val = values.get(key);
        return val instanceof Boolean b ? b : defaultValue;
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
     * @throws ClassCastException if the value exists but is not a Number
     */
    public int getInt(String key, int defaultValue) {
        Object val = values.get(key);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Number n) {
            return n.intValue();
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
     * @throws ClassCastException if the value exists but is not a Number
     */
    public long getLong(String key, long defaultValue) {
        Object val = values.get(key);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Number n) {
            return n.longValue();
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
     * @throws ClassCastException if the value exists but is not a Number
     */
    public double getDouble(String key, double defaultValue) {
        Object val = values.get(key);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Number n) {
            return n.doubleValue();
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
        Object val = values.get(key);
        return val instanceof String s ? s : defaultValue;
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
    
    // ========== Deprecated Bridge Methods ==========
    
    /**
     * Returns all values as an unmodifiable map.
     *
     * @return unmodifiable map of all values
     * @deprecated Use {@link #values()} instead. This method will be removed in a future version.
     */
    @Deprecated(forRemoval = true)
    public Map<String, Object> getAllValues() {
        return values;
    }
    
    /**
     * Returns all metadata as an unmodifiable map.
     *
     * @return unmodifiable map of all metadata
     * @deprecated Use {@link #metadata()} instead. This method will be removed in a future version.
     */
    @Deprecated(forRemoval = true)
    public Map<String, Object> getAllMetadata() {
        return metadata;
    }
    
    /**
     * Returns the timestamp when this result was created.
     *
     * @return the creation timestamp
     * @deprecated Use {@link #timestamp()} instead. This method will be removed in a future version.
     */
    @Deprecated(forRemoval = true)
    public Instant getTimestamp() {
        return timestamp;
    }
    
    /**
     * Returns the execution time of the use case invocation.
     *
     * @return the execution duration
     * @deprecated Use {@link #executionTime()} instead. This method will be removed in a future version.
     */
    @Deprecated(forRemoval = true)
    public Duration getExecutionTime() {
        return executionTime;
    }
    
    // ========== Builder ==========
    
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
         * Copies all values from an existing result.
         *
         * @param source the source result to copy values from
         * @return this builder
         */
        public Builder valuesFrom(UseCaseResult source) {
            Objects.requireNonNull(source, "source must not be null");
            values.putAll(source.values());
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
         * Copies all metadata from an existing result.
         *
         * @param source the source result to copy metadata from
         * @return this builder
         */
        public Builder metadataFrom(UseCaseResult source) {
            Objects.requireNonNull(source, "source must not be null");
            metadata.putAll(source.metadata());
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
            return new UseCaseResult(values, metadata, timestamp, executionTime);
        }
    }
}
