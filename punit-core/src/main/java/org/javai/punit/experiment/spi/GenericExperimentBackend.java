package org.javai.punit.experiment.spi;

import java.util.Map;
import org.javai.punit.api.UseCaseContext;
import org.javai.punit.experiment.model.DefaultUseCaseContext;

/**
 * Default passthrough backend that accepts any parameters.
 *
 * <p>This backend is used when no specific backend is configured, or when
 * experimenting with domain-neutral systems that don't require specialized
 * context handling.
 */
public class GenericExperimentBackend implements ExperimentBackend {

	@Override
	public String getId() {
		return "generic";
	}

	@Override
	public UseCaseContext buildContext(Map<String, String> parameters) {
		DefaultUseCaseContext.Builder builder = DefaultUseCaseContext.builder()
				.backend("generic");

		if (parameters != null) {
			for (Map.Entry<String, String> entry : parameters.entrySet()) {
				builder.parameter(entry.getKey(), parseValue(entry.getValue()));
			}
		}

		return builder.build();
	}

	@Override
	public void validateParameters(Map<String, String> parameters) {
		// Generic backend accepts any parameters
	}

	@Override
	public Map<String, ParameterDoc> getParameterDocumentation() {
		return Map.of(
				"*", new ParameterDoc("Any parameter (passthrough)", "")
		);
	}

	private Object parseValue(String value) {
		if (value == null) {
			return null;
		}

		// Try to parse as boolean
		if ("true".equalsIgnoreCase(value)) return true;
		if ("false".equalsIgnoreCase(value)) return false;

		// Try to parse as number
		try {
			if (value.contains(".")) {
				return Double.parseDouble(value);
			}
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			return value;
		}
	}
}

