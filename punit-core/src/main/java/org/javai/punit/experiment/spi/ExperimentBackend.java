package org.javai.punit.experiment.spi;

import java.util.Map;
import org.javai.punit.api.UseCaseContext;

/**
 * SPI for experiment backends that provide domain-specific execution context.
 *
 * <p>Backends are discovered via {@link java.util.ServiceLoader}. To register a backend,
 * create a file {@code META-INF/services/org.javai.punit.experiment.spi.ExperimentBackend}
 * containing the fully-qualified class name of the implementation.
 *
 * <h2>Example Implementation</h2>
 * <pre>{@code
 * public class LlmExperimentBackend implements ExperimentBackend {
 *
 *     @Override
 *     public String getId() {
 *         return "llm";
 *     }
 *
 *     @Override
 *     public UseCaseContext buildContext(Map<String, String> parameters) {
 *         String model = parameters.getOrDefault("model", "gpt-4");
 *         double temperature = Double.parseDouble(
 *             parameters.getOrDefault("temperature", "0.7"));
 *         return new LlmUseCaseContext(model, temperature);
 *     }
 *
 *     @Override
 *     public void validateParameters(Map<String, String> parameters) {
 *         // Validate model is supported, temperature in range, etc.
 *     }
 * }
 * }</pre>
 */
public interface ExperimentBackend {

	/**
	 * Returns the backend identifier (e.g., "llm", "sensor", "distributed").
	 *
	 * <p>This identifier is used in {@code UseCaseContext.getBackend()}.
	 *
	 * @return the unique backend identifier
	 */
	String getId();

	/**
	 * Builds a UseCaseContext from the given parameters.
	 *
	 * @param parameters the backend-specific parameters
	 * @return the constructed context
	 */
	UseCaseContext buildContext(Map<String, String> parameters);

	/**
	 * Validates that required parameters are present and valid.
	 *
	 * @param parameters the parameters to validate
	 * @throws IllegalArgumentException if validation fails
	 */
	void validateParameters(Map<String, String> parameters) throws IllegalArgumentException;

	/**
	 * Returns parameter documentation for this backend.
	 *
	 * @return map of parameter names to their documentation
	 */
	default Map<String, ParameterDoc> getParameterDocumentation() {
		return Map.of();
	}

	/**
	 * Parameter documentation.
	 */
	record ParameterDoc(String description, String defaultValue) {
	}
}

