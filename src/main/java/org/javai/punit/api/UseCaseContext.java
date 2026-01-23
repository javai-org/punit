package org.javai.punit.api;

import java.util.Map;
import java.util.Optional;

/**
 * Provides execution context for use cases, including backend-specific configuration.
 *
 * <p>The context is injected into use case methods and provides access to:
 * <ul>
 *   <li>Backend identifier (e.g., "llm", "sensor", "distributed")</li>
 *   <li>Backend-specific parameters (e.g., model, temperature, provider)</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * @UseCase("usecase.json.generation")
 * UseCaseOutcome<JsonResult> generateJson(String prompt, UseCaseContext context) {
 *     String model = context.getParameter("model", String.class, "gpt-4");
 *     double temperature = context.getParameter("temperature", Double.class, 0.7);
 *
 *     // Use model and temperature to configure the LLM call
 *     LlmResponse response = llmClient.complete(prompt, model, temperature);
 *
 *     return UseCaseOutcome.withContract(jsonContract)
 *         .input(prompt)
 *         .execute(p -> new JsonResult(response.getContent(), response.getTokensUsed()))
 *         .build();
 * }
 * }</pre>
 *
 * @see UseCase
 */
public interface UseCaseContext {
    
    /**
     * Returns the backend identifier for this context.
     *
     * <p>Common backend identifiers:
     * <ul>
     *   <li>{@code "llm"} - Language model backend</li>
     *   <li>{@code "generic"} - Default passthrough backend</li>
     *   <li>{@code "sensor"} - Hardware sensor backend</li>
     *   <li>{@code "distributed"} - Distributed system backend</li>
     * </ul>
     *
     * @return the backend identifier
     */
    String getBackend();
    
    /**
     * Returns a parameter value, or empty if not present.
     *
     * @param <T> the expected parameter type
     * @param key the parameter name
     * @param type the expected parameter type class
     * @return an Optional containing the parameter value, or empty if not present
     * @throws ClassCastException if the parameter exists but is not of the expected type
     */
    <T> Optional<T> getParameter(String key, Class<T> type);
    
    /**
     * Returns a parameter value with a default fallback.
     *
     * @param <T> the expected parameter type
     * @param key the parameter name
     * @param type the expected parameter type class
     * @param defaultValue the default value if parameter is not present
     * @return the parameter value or the default value
     * @throws ClassCastException if the parameter exists but is not of the expected type
     */
    <T> T getParameter(String key, Class<T> type, T defaultValue);
    
    /**
     * Returns all parameters as an immutable map.
     *
     * @return unmodifiable map of all parameters
     */
    Map<String, Object> getAllParameters();
    
    /**
     * Returns true if this context has the specified backend.
     *
     * @param backend the backend identifier to check
     * @return true if this context's backend matches
     */
    default boolean hasBackend(String backend) {
        return backend != null && backend.equals(getBackend());
    }
    
    /**
     * Returns true if a parameter with the given key exists.
     *
     * @param key the parameter name
     * @return true if the parameter exists
     */
    default boolean hasParameter(String key) {
        return getAllParameters().containsKey(key);
    }
}

