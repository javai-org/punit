package org.javai.punit.experiment.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.javai.punit.api.UseCaseContext;

/**
 * Default implementation of {@link UseCaseContext}.
 *
 * <p>This class provides a simple, immutable implementation of the use case context
 * interface backed by a map of parameters.
 */
public final class DefaultUseCaseContext implements UseCaseContext {
    
    private final String backend;
    private final Map<String, Object> parameters;
    
    private DefaultUseCaseContext(String backend, Map<String, Object> parameters) {
        this.backend = Objects.requireNonNull(backend, "backend must not be null");
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }
    
    /**
     * Creates a new builder for constructing a {@code DefaultUseCaseContext}.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Creates a context with the generic backend and no parameters.
     *
     * @return a generic context with no parameters
     */
    public static DefaultUseCaseContext generic() {
        return builder().backend("generic").build();
    }
    
    /**
     * Creates a context with the specified backend and no parameters.
     *
     * @param backend the backend identifier
     * @return a context with the specified backend
     */
    public static DefaultUseCaseContext of(String backend) {
        return builder().backend(backend).build();
    }
    
    @Override
    public String getBackend() {
        return backend;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getParameter(String key, Class<T> type) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(type, "type must not be null");
        
        Object val = parameters.get(key);
        if (val == null) {
            return Optional.empty();
        }
        if (!type.isInstance(val)) {
            throw new ClassCastException("Parameter '" + key + "' is " +
                val.getClass().getName() + ", not " + type.getName());
        }
        return Optional.of((T) val);
    }
    
    @Override
    public <T> T getParameter(String key, Class<T> type, T defaultValue) {
        return getParameter(key, type).orElse(defaultValue);
    }
    
    @Override
    public Map<String, Object> getAllParameters() {
        return parameters;
    }
    
    @Override
    public String toString() {
        return "DefaultUseCaseContext{" +
            "backend='" + backend + '\'' +
            ", parameters=" + parameters +
            '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefaultUseCaseContext that = (DefaultUseCaseContext) o;
        return Objects.equals(backend, that.backend) &&
               Objects.equals(parameters, that.parameters);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(backend, parameters);
    }
    
    /**
     * Builder for constructing {@code DefaultUseCaseContext} instances.
     */
    public static final class Builder {
        
        private String backend = "generic";
        private final Map<String, Object> parameters = new LinkedHashMap<>();
        
        private Builder() {}
        
        /**
         * Sets the backend identifier.
         *
         * @param backend the backend identifier
         * @return this builder
         */
        public Builder backend(String backend) {
            this.backend = Objects.requireNonNull(backend, "backend must not be null");
            return this;
        }
        
        /**
         * Adds a parameter to the context.
         *
         * @param key the parameter key
         * @param value the parameter value
         * @return this builder
         */
        public Builder parameter(String key, Object value) {
            Objects.requireNonNull(key, "key must not be null");
            parameters.put(key, value);
            return this;
        }
        
        /**
         * Adds all parameters from a map.
         *
         * @param params the parameters to add
         * @return this builder
         */
        public Builder parameters(Map<String, Object> params) {
            if (params != null) {
                parameters.putAll(params);
            }
            return this;
        }
        
        /**
         * Builds the {@code DefaultUseCaseContext}.
         *
         * @return the constructed context
         */
        public DefaultUseCaseContext build() {
            return new DefaultUseCaseContext(backend, parameters);
        }
    }
}

