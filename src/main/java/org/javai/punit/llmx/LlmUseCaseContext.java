package org.javai.punit.llmx;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.javai.punit.experiment.api.UseCaseContext;

/**
 * LLM-specific use case context.
 *
 * <p>Provides typed accessors for common LLM parameters.
 */
public final class LlmUseCaseContext implements UseCaseContext {

	private final String model;
	private final double temperature;
	private final int maxTokens;
	private final String provider;
	private final Double topP;
	private final Double frequencyPenalty;
	private final Double presencePenalty;
	private final String systemPrompt;
	private final Map<String, Object> allParameters;

	private LlmUseCaseContext(Builder builder) {
		this.model = Objects.requireNonNull(builder.model, "model must not be null");
		this.temperature = builder.temperature;
		this.maxTokens = builder.maxTokens;
		this.provider = builder.provider;
		this.topP = builder.topP;
		this.frequencyPenalty = builder.frequencyPenalty;
		this.presencePenalty = builder.presencePenalty;
		this.systemPrompt = builder.systemPrompt;

		Map<String, Object> params = new LinkedHashMap<>();
		params.put("model", model);
		params.put("temperature", temperature);
		params.put("maxTokens", maxTokens);
		params.put("provider", provider);
		if (topP != null) params.put("topP", topP);
		if (frequencyPenalty != null) params.put("frequencyPenalty", frequencyPenalty);
		if (presencePenalty != null) params.put("presencePenalty", presencePenalty);
		if (systemPrompt != null) params.put("systemPrompt", systemPrompt);
		this.allParameters = Collections.unmodifiableMap(params);
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public String getBackend() {
		return "llm";
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Optional<T> getParameter(String key, Class<T> type) {
		Object value = allParameters.get(key);
		if (value == null) {
			return Optional.empty();
		}
		if (!type.isInstance(value)) {
			throw new ClassCastException("Parameter '" + key + "' is " +
					value.getClass().getName() + ", not " + type.getName());
		}
		return Optional.of((T) value);
	}

	@Override
	public <T> T getParameter(String key, Class<T> type, T defaultValue) {
		return getParameter(key, type).orElse(defaultValue);
	}

	@Override
	public Map<String, Object> getAllParameters() {
		return allParameters;
	}

	// Type-safe accessors

	public String getModel() {
		return model;
	}

	public double getTemperature() {
		return temperature;
	}

	public int getMaxTokens() {
		return maxTokens;
	}

	public String getProvider() {
		return provider;
	}

	public Optional<Double> getTopP() {
		return Optional.ofNullable(topP);
	}

	public Optional<Double> getFrequencyPenalty() {
		return Optional.ofNullable(frequencyPenalty);
	}

	public Optional<Double> getPresencePenalty() {
		return Optional.ofNullable(presencePenalty);
	}

	public Optional<String> getSystemPrompt() {
		return Optional.ofNullable(systemPrompt);
	}

	@Override
	public String toString() {
		return "LlmUseCaseContext{" +
				"model='" + model + '\'' +
				", temperature=" + temperature +
				", maxTokens=" + maxTokens +
				", provider='" + provider + '\'' +
				'}';
	}

	public static final class Builder {

		private String model = "gpt-4";
		private double temperature = 0.7;
		private int maxTokens = 1000;
		private String provider = "openai";
		private Double topP;
		private Double frequencyPenalty;
		private Double presencePenalty;
		private String systemPrompt;

		private Builder() {
		}

		public Builder model(String model) {
			this.model = model;
			return this;
		}

		public Builder temperature(double temperature) {
			this.temperature = temperature;
			return this;
		}

		public Builder maxTokens(int maxTokens) {
			this.maxTokens = maxTokens;
			return this;
		}

		public Builder provider(String provider) {
			this.provider = provider;
			return this;
		}

		public Builder topP(Double topP) {
			this.topP = topP;
			return this;
		}

		public Builder frequencyPenalty(Double frequencyPenalty) {
			this.frequencyPenalty = frequencyPenalty;
			return this;
		}

		public Builder presencePenalty(Double presencePenalty) {
			this.presencePenalty = presencePenalty;
			return this;
		}

		public Builder systemPrompt(String systemPrompt) {
			this.systemPrompt = systemPrompt;
			return this;
		}

		public LlmUseCaseContext build() {
			return new LlmUseCaseContext(this);
		}
	}
}

