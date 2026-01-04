package org.javai.punit.spec.registry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.javai.punit.spec.model.ExecutionSpecification;

/**
 * Loads execution specifications from YAML or JSON files.
 */
public final class SpecificationLoader {

	private static final Pattern YAML_KEY_VALUE = Pattern.compile("^\\s*(\\w+)\\s*:\\s*(.+?)\\s*$");

	private SpecificationLoader() {
	}

	/**
	 * Loads a specification from a file.
	 *
	 * <p>File format is detected by extension (.yaml/.yml vs .json).
	 *
	 * @param path the file path
	 * @return the loaded specification
	 * @throws IOException if loading fails
	 */
	public static ExecutionSpecification load(Path path) throws IOException {
		String filename = path.getFileName().toString().toLowerCase();
		String content = Files.readString(path);

		if (filename.endsWith(".json")) {
			return parseJson(content);
		} else {
			return parseYaml(content);
		}
	}

	/**
	 * Parses a specification from YAML content.
	 */
	public static ExecutionSpecification parseYaml(String content) {
		ExecutionSpecification.Builder builder = ExecutionSpecification.builder();
		Map<String, Object> executionContext = new LinkedHashMap<>();
		List<String> sourceBaselines = new ArrayList<>();

		String[] lines = content.split("\n");
		boolean inContext = false;
		boolean inRequirements = false;
		boolean inCostEnvelope = false;
		boolean inSourceBaselines = false;

		double minPassRate = 1.0;
		String successCriteria = "";
		long maxTimePerSampleMs = 0;
		long maxTokensPerSample = 0;
		long totalTokenBudget = 0;

		for (String line : lines) {
			if (line.trim().isEmpty() || line.trim().startsWith("#")) {
				continue;
			}

			// Detect section changes
			if (!line.startsWith(" ") && !line.startsWith("\t")) {
				inContext = false;
				inRequirements = false;
				inCostEnvelope = false;
				inSourceBaselines = false;

				if (line.startsWith("specId:")) {
					builder.specId(extractValue(line));
				} else if (line.startsWith("useCaseId:")) {
					builder.useCaseId(extractValue(line));
				} else if (line.startsWith("version:")) {
					builder.version(Integer.parseInt(extractValue(line)));
				} else if (line.startsWith("approvedAt:")) {
					builder.approvedAt(parseInstant(extractValue(line)));
				} else if (line.startsWith("approvedBy:")) {
					builder.approvedBy(extractValue(line));
				} else if (line.startsWith("approvalNotes:")) {
					builder.approvalNotes(extractMultilineValue(line, lines));
				} else if (line.startsWith("executionContext:")) {
					inContext = true;
				} else if (line.startsWith("requirements:")) {
					inRequirements = true;
				} else if (line.startsWith("costEnvelope:")) {
					inCostEnvelope = true;
				} else if (line.startsWith("sourceBaselines:")) {
					inSourceBaselines = true;
				}
				continue;
			}

			String trimmed = line.trim();

			if (inContext) {
				Matcher m = YAML_KEY_VALUE.matcher(trimmed);
				if (m.matches()) {
					executionContext.put(m.group(1), parseValue(m.group(2)));
				}
			} else if (inRequirements) {
				if (trimmed.startsWith("minPassRate:")) {
					minPassRate = Double.parseDouble(extractValue(trimmed));
				} else if (trimmed.startsWith("successCriteria:")) {
					successCriteria = extractValue(trimmed);
				}
			} else if (inCostEnvelope) {
				if (trimmed.startsWith("maxTimePerSampleMs:")) {
					maxTimePerSampleMs = Long.parseLong(extractValue(trimmed));
				} else if (trimmed.startsWith("maxTokensPerSample:")) {
					maxTokensPerSample = Long.parseLong(extractValue(trimmed));
				} else if (trimmed.startsWith("totalTokenBudget:")) {
					totalTokenBudget = Long.parseLong(extractValue(trimmed));
				}
			} else if (inSourceBaselines) {
				if (trimmed.startsWith("-")) {
					sourceBaselines.add(trimmed.substring(1).trim());
				}
			}
		}

		builder.executionContext(executionContext);
		builder.sourceBaselines(sourceBaselines);
		builder.requirements(minPassRate, successCriteria);
		builder.costEnvelope(maxTimePerSampleMs, maxTokensPerSample, totalTokenBudget);

		return builder.build();
	}

	/**
	 * Parses a specification from JSON content.
	 */
	public static ExecutionSpecification parseJson(String content) {
		// Simple JSON parsing (for production, use a proper JSON library)
		ExecutionSpecification.Builder builder = ExecutionSpecification.builder();

		String specId = extractJsonString(content, "specId");
		String useCaseId = extractJsonString(content, "useCaseId");
		int version = extractJsonInt(content, "version", 1);
		String approvedAt = extractJsonString(content, "approvedAt");
		String approvedBy = extractJsonString(content, "approvedBy");
		String approvalNotes = extractJsonString(content, "approvalNotes");

		builder.specId(specId)
				.useCaseId(useCaseId)
				.version(version)
				.approvedBy(approvedBy)
				.approvalNotes(approvalNotes);

		if (approvedAt != null && !approvedAt.isEmpty()) {
			builder.approvedAt(parseInstant(approvedAt));
		}

		// Extract requirements
		double minPassRate = extractJsonDouble(content, "minPassRate", 1.0);
		String successCriteria = extractJsonString(content, "successCriteria");
		builder.requirements(minPassRate, successCriteria != null ? successCriteria : "");

		return builder.build();
	}

	private static String extractValue(String line) {
		int colonIdx = line.indexOf(':');
		if (colonIdx < 0) return "";
		String value = line.substring(colonIdx + 1).trim();
		// Remove quotes if present
		if ((value.startsWith("\"") && value.endsWith("\"")) ||
				(value.startsWith("'") && value.endsWith("'"))) {
			value = value.substring(1, value.length() - 1);
		}
		// Handle YAML block indicator
		if (value.equals(">") || value.equals("|")) {
			return "";
		}
		return value;
	}

	private static String extractMultilineValue(String currentLine, String[] allLines) {
		// Simple handling - just return what's on the line
		return extractValue(currentLine);
	}

	private static Object parseValue(String value) {
		// Remove quotes if present
		if ((value.startsWith("\"") && value.endsWith("\"")) ||
				(value.startsWith("'") && value.endsWith("'"))) {
			return value.substring(1, value.length() - 1);
		}

		if ("true".equalsIgnoreCase(value)) return true;
		if ("false".equalsIgnoreCase(value)) return false;

		try {
			if (value.contains(".")) {
				return Double.parseDouble(value);
			}
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			return value;
		}
	}

	private static Instant parseInstant(String value) {
		if (value == null || value.isEmpty()) return null;
		try {
			return Instant.parse(value);
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	private static String extractJsonString(String json, String key) {
		Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
		Matcher m = p.matcher(json);
		if (m.find()) {
			return m.group(1);
		}
		return null;
	}

	private static int extractJsonInt(String json, String key, int defaultValue) {
		Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
		Matcher m = p.matcher(json);
		if (m.find()) {
			return Integer.parseInt(m.group(1));
		}
		return defaultValue;
	}

	private static double extractJsonDouble(String json, String key, double defaultValue) {
		Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*([\\d.]+)");
		Matcher m = p.matcher(json);
		if (m.find()) {
			return Double.parseDouble(m.group(1));
		}
		return defaultValue;
	}
}

