package org.javai.punit.llmx;

import java.util.Map;
import org.javai.punit.experiment.api.UseCaseContext;
import org.javai.punit.experiment.spi.ExperimentBackend;

/**
 * LLM-specific experiment backend.
 *
 * <p>This backend provides context for experiments involving Large Language Models.
 * It supports common LLM parameters like model, temperature, max tokens, etc.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * @Experiment(useCase = "usecase.json.generation", samples = 100)
 * @ExperimentContext(backend = "llm", parameters = {
 *     "model = gpt-4",
 *     "temperature = 0.7",
 *     "maxTokens = 1000"
 * })
 * void measureJsonGeneration() {
 *     // ...
 * }
 * }</pre>
 *
 * <h2>Supported Parameters</h2>
 * <ul>
 *   <li>{@code model} - The LLM model identifier (default: gpt-4)</li>
 *   <li>{@code temperature} - Sampling temperature 0.0-2.0 (default: 0.7)</li>
 *   <li>{@code maxTokens} - Maximum tokens to generate (default: 1000)</li>
 *   <li>{@code provider} - LLM provider (default: openai)</li>
 *   <li>{@code topP} - Nucleus sampling parameter (optional)</li>
 *   <li>{@code frequencyPenalty} - Frequency penalty (optional)</li>
 *   <li>{@code presencePenalty} - Presence penalty (optional)</li>
 * </ul>
 */
public class LlmExperimentBackend implements ExperimentBackend {

	@Override
	public String getId() {
		return "llm";
	}

	@Override
	public UseCaseContext buildContext(Map<String, String> parameters) {
		String model = getOrDefault(parameters, "model", "gpt-4");
		double temperature = parseDouble(getOrDefault(parameters, "temperature", "0.7"));
		int maxTokens = parseInt(getOrDefault(parameters, "maxTokens", "1000"));
		String provider = getOrDefault(parameters, "provider", "openai");

		LlmUseCaseContext.Builder builder = LlmUseCaseContext.builder()
				.model(model)
				.temperature(temperature)
				.maxTokens(maxTokens)
				.provider(provider);

		// Optional parameters
		if (parameters != null) {
			if (parameters.containsKey("topP")) {
				builder.topP(parseDouble(parameters.get("topP")));
			}
			if (parameters.containsKey("frequencyPenalty")) {
				builder.frequencyPenalty(parseDouble(parameters.get("frequencyPenalty")));
			}
			if (parameters.containsKey("presencePenalty")) {
				builder.presencePenalty(parseDouble(parameters.get("presencePenalty")));
			}
			if (parameters.containsKey("systemPrompt")) {
				builder.systemPrompt(parameters.get("systemPrompt"));
			}
		}

		return builder.build();
	}

	@Override
	public void validateParameters(Map<String, String> parameters) throws IllegalArgumentException {
		if (parameters == null) {
			return;
		}

		// Validate temperature range
		if (parameters.containsKey("temperature")) {
			double temp = parseDouble(parameters.get("temperature"));
			if (temp < 0.0 || temp > 2.0) {
				throw new IllegalArgumentException(
						"temperature must be between 0.0 and 2.0, got: " + temp);
			}
		}

		// Validate maxTokens
		if (parameters.containsKey("maxTokens")) {
			int maxTokens = parseInt(parameters.get("maxTokens"));
			if (maxTokens <= 0) {
				throw new IllegalArgumentException(
						"maxTokens must be positive, got: " + maxTokens);
			}
		}

		// Validate topP range
		if (parameters.containsKey("topP")) {
			double topP = parseDouble(parameters.get("topP"));
			if (topP < 0.0 || topP > 1.0) {
				throw new IllegalArgumentException(
						"topP must be between 0.0 and 1.0, got: " + topP);
			}
		}
	}

	@Override
	public Map<String, ParameterDoc> getParameterDocumentation() {
		return Map.of(
				"model", new ParameterDoc("LLM model identifier", "gpt-4"),
				"temperature", new ParameterDoc("Sampling temperature (0.0-2.0)", "0.7"),
				"maxTokens", new ParameterDoc("Maximum tokens to generate", "1000"),
				"provider", new ParameterDoc("LLM provider (openai, anthropic, etc.)", "openai"),
				"topP", new ParameterDoc("Nucleus sampling parameter (0.0-1.0)", ""),
				"frequencyPenalty", new ParameterDoc("Frequency penalty (-2.0 to 2.0)", ""),
				"presencePenalty", new ParameterDoc("Presence penalty (-2.0 to 2.0)", ""),
				"systemPrompt", new ParameterDoc("System prompt for the conversation", "")
		);
	}

	private String getOrDefault(Map<String, String> params, String key, String defaultValue) {
		if (params == null || !params.containsKey(key)) {
			return defaultValue;
		}
		return params.get(key);
	}

	private double parseDouble(String value) {
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			return 0.0;
		}
	}

	private int parseInt(String value) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}

