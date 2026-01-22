package org.javai.punit.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A neutral container for observed outcomes from a use case invocation.
 *
 * @deprecated Use {@link org.javai.punit.contract.UseCaseOutcome} instead, which provides
 *             type-safe result access via a typed result object rather than a map of values.
 *
 * @see org.javai.punit.contract.UseCaseOutcome
 */
@Deprecated(forRemoval = true)
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
        // Map.copyOf doesn't support null values, so use LinkedHashMap for defensive copy
        values = values != null 
            ? Collections.unmodifiableMap(new LinkedHashMap<>(values)) 
            : Map.of();
        metadata = metadata != null 
            ? Collections.unmodifiableMap(new LinkedHashMap<>(metadata)) 
            : Map.of();
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
