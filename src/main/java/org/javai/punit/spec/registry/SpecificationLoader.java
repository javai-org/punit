package org.javai.punit.spec.registry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.javai.punit.model.ExpirationPolicy;
import org.javai.punit.spec.model.ExecutionSpecification;

/**
 * Loads execution specifications from YAML files.
 *
 * <p>Validates schema version compatibility and content integrity via fingerprint verification.
 *
 * <h2>Supported Schema Versions</h2>
 * <ul>
 *   <li>{@code punit-spec-1}: Original format with required approval metadata</li>
 *   <li>{@code punit-spec-2}: Simplified format with optional approval metadata</li>
 * </ul>
 */
public final class SpecificationLoader {

	private static final Pattern YAML_KEY_VALUE = Pattern.compile("^\\s*(\\w+)\\s*:\\s*(.+?)\\s*$");

	/** Supported schema versions. */
	private static final Set<String> SUPPORTED_SCHEMA_VERSIONS = Set.of("punit-spec-1", "punit-spec-2");

	/** Field name for content fingerprint. */
	private static final String FINGERPRINT_FIELD = "contentFingerprint";

	/** Field name for schema version. */
	private static final String SCHEMA_VERSION_FIELD = "schemaVersion";

	private SpecificationLoader() {
	}

	/**
	 * Loads a specification from a YAML file.
	 *
	 * <p>This method performs full validation including:
	 * <ul>
	 *   <li>Schema structure validation (all required fields)</li>
	 *   <li>Content integrity validation (fingerprint)</li>
	 * </ul>
	 *
	 * @param path the file path (must be .yaml or .yml)
	 * @return the loaded specification
	 * @throws IOException if loading fails
	 * @throws SpecificationIntegrityException if validation fails
	 */
	public static ExecutionSpecification load(Path path) throws IOException {
		String filename = path.getFileName().toString().toLowerCase();
		
		if (!filename.endsWith(".yaml") && !filename.endsWith(".yml")) {
			throw new SpecificationIntegrityException(
					"Unsupported file format: " + filename + ". Only YAML files (.yaml, .yml) are supported.");
		}
		
		String content = Files.readString(path);
		
		// Full validation when loading from file
		SpecSchemaValidator.validateOrThrow(content);
		validateIntegrity(content);
		
		return parseYamlInternal(content);
	}

	/**
	 * Parses a specification from YAML content with integrity validation.
	 *
	 * <p>This method validates schema version and fingerprint, but not the full schema.
	 * Use {@link #load(Path)} for full validation.
	 *
	 * @throws SpecificationIntegrityException if schema version is unsupported or fingerprint doesn't match
	 */
	public static ExecutionSpecification parseYaml(String content) {
		// Validate content integrity (fingerprint)
		validateIntegrity(content);
		
		return parseYamlInternal(content);
	}
	
	/**
	 * Internal parser without validation.
	 */
	private static ExecutionSpecification parseYamlInternal(String content) {

		ExecutionSpecification.Builder builder = ExecutionSpecification.builder();
		Map<String, Object> executionContext = new LinkedHashMap<>();
		List<String> sourceBaselines = new ArrayList<>();

		String[] lines = content.split("\n");

		// Section tracking
		boolean inContext = false;
		boolean inRequirements = false;
		boolean inCostEnvelope = false;
		boolean inSourceBaselines = false;
		boolean inBaselineData = false;
		boolean inEmpiricalBasis = false;
		boolean inExtendedStatistics = false;
		boolean inFailureDistribution = false;
		boolean inExpiration = false;

		// Requirements fields
		double minPassRate = 1.0;
		String successCriteria = "";

		// Cost envelope fields
		long maxTimePerSampleMs = 0;
		long maxTokensPerSample = 0;
		long totalTokenBudget = 0;

		// Empirical basis / baseline data fields (both map to the same thing)
		int samples = 0;
		int successes = 0;
		Instant basisGeneratedAt = null;

		// Extended statistics fields
		double standardError = 0.0;
		double ciLower = 0.0;
		double ciUpper = 0.0;
		Map<String, Integer> failureDistribution = new LinkedHashMap<>();
		long totalTimeMs = 0;
		long avgTimePerSampleMs = 0;
		long totalTokens = 0;
		long avgTokensPerSample = 0;

		// Expiration fields
		int expiresInDays = 0;
		Instant baselineEndTime = null;

		for (String line : lines) {
			if (line.trim().isEmpty() || line.trim().startsWith("#")) {
				continue;
			}

			// Detect section changes (top-level fields don't start with whitespace)
			if (!line.startsWith(" ") && !line.startsWith("\t")) {
				// Reset all section flags
				inContext = false;
				inRequirements = false;
				inCostEnvelope = false;
				inSourceBaselines = false;
				inBaselineData = false;
				inEmpiricalBasis = false;
				inExtendedStatistics = false;
				inFailureDistribution = false;
				inExpiration = false;

				if (line.startsWith("specId:") || line.startsWith("useCaseId:")) {
					// Both specId (legacy) and useCaseId map to useCaseId
					builder.useCaseId(extractValue(line));
				} else if (line.startsWith("version:")) {
					builder.version(Integer.parseInt(extractValue(line)));
				} else if (line.startsWith("generatedAt:")) {
					builder.generatedAt(parseInstant(extractValue(line)));
				} else if (line.startsWith("approvedAt:")) {
					builder.approvedAt(parseInstant(extractValue(line)));
				} else if (line.startsWith("approvedBy:")) {
					builder.approvedBy(extractValue(line));
				} else if (line.startsWith("approvalNotes:")) {
					builder.approvalNotes(extractMultilineValue(line, lines));
				} else if (line.startsWith("executionContext:") || line.startsWith("configuration:")) {
					inContext = true;
				} else if (line.startsWith("requirements:")) {
					inRequirements = true;
				} else if (line.startsWith("costEnvelope:")) {
					inCostEnvelope = true;
				} else if (line.startsWith("sourceBaselines:")) {
					inSourceBaselines = true;
				} else if (line.startsWith("baselineData:")) {
					inBaselineData = true;
				} else if (line.startsWith("empiricalBasis:")) {
					inEmpiricalBasis = true;
				} else if (line.startsWith("extendedStatistics:")) {
					inExtendedStatistics = true;
				} else if (line.startsWith("expiration:")) {
					inExpiration = true;
				}
				// Skip schemaVersion and contentFingerprint - already validated
				continue;
			}

			String trimmed = line.trim();

			// Handle nested sections
			if (inExtendedStatistics && trimmed.startsWith("failureDistribution:")) {
				inFailureDistribution = true;
				continue;
			}

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
			} else if (inBaselineData || inEmpiricalBasis) {
				if (trimmed.startsWith("samples:")) {
					samples = Integer.parseInt(extractValue(trimmed));
				} else if (trimmed.startsWith("successes:")) {
					successes = Integer.parseInt(extractValue(trimmed));
				} else if (trimmed.startsWith("generatedAt:")) {
					basisGeneratedAt = parseInstant(extractValue(trimmed));
				}
			} else if (inExtendedStatistics) {
				if (inFailureDistribution) {
					// Parse failure distribution entries (key: value pairs)
					Matcher m = YAML_KEY_VALUE.matcher(trimmed);
					if (m.matches()) {
						try {
							failureDistribution.put(m.group(1), Integer.parseInt(m.group(2).trim()));
						} catch (NumberFormatException e) {
							// Skip non-integer values
						}
					}
				} else if (trimmed.startsWith("standardError:")) {
					standardError = Double.parseDouble(extractValue(trimmed));
				} else if (trimmed.startsWith("confidenceIntervalLower:")) {
					ciLower = Double.parseDouble(extractValue(trimmed));
				} else if (trimmed.startsWith("confidenceIntervalUpper:")) {
					ciUpper = Double.parseDouble(extractValue(trimmed));
				} else if (trimmed.startsWith("totalTimeMs:")) {
					totalTimeMs = Long.parseLong(extractValue(trimmed));
				} else if (trimmed.startsWith("avgTimePerSampleMs:")) {
					avgTimePerSampleMs = Long.parseLong(extractValue(trimmed));
				} else if (trimmed.startsWith("totalTokens:")) {
					totalTokens = Long.parseLong(extractValue(trimmed));
				} else if (trimmed.startsWith("avgTokensPerSample:")) {
					avgTokensPerSample = Long.parseLong(extractValue(trimmed));
				}
			} else if (inExpiration) {
				if (trimmed.startsWith("expiresInDays:")) {
					expiresInDays = Integer.parseInt(extractValue(trimmed));
				} else if (trimmed.startsWith("baselineEndTime:")) {
					baselineEndTime = parseInstant(extractValue(trimmed));
				}
				// expirationDate is computed, no need to parse
			}
		}

		builder.executionContext(executionContext);
		builder.sourceBaselines(sourceBaselines);
		builder.requirements(minPassRate, successCriteria);
		builder.costEnvelope(maxTimePerSampleMs, maxTokensPerSample, totalTokenBudget);

		// Set empirical basis if we found samples
		if (samples > 0) {
			builder.empiricalBasis(samples, successes, basisGeneratedAt);
		}

		// Set extended statistics if we have any data
		if (standardError > 0 || !failureDistribution.isEmpty() || totalTimeMs > 0) {
			builder.extendedStatistics(new ExecutionSpecification.ExtendedStatistics(
					standardError, ciLower, ciUpper, failureDistribution,
					totalTimeMs, avgTimePerSampleMs, totalTokens, avgTokensPerSample
			));
		}

		// Set expiration policy if we found expiration data
		if (expiresInDays > 0) {
			// Use baselineEndTime if available, otherwise fall back to basisGeneratedAt
			Instant endTime = baselineEndTime != null ? baselineEndTime : basisGeneratedAt;
			if (endTime != null) {
				builder.expirationPolicy(ExpirationPolicy.of(expiresInDays, endTime));
			}
		}

		return builder.build();
	}

	/**
	 * Validates schema version and content integrity.
	 *
	 * @throws SpecificationIntegrityException if validation fails
	 */
	private static void validateIntegrity(String content) {
		String schemaVersion = extractFieldValue(content, SCHEMA_VERSION_FIELD);
		String storedFingerprint = extractFieldValue(content, FINGERPRINT_FIELD);

		// Validate schema version
		if (schemaVersion == null || schemaVersion.isEmpty()) {
			throw new SpecificationIntegrityException(
					"Missing schemaVersion field. Spec files must include schemaVersion.");
		}
		if (!SUPPORTED_SCHEMA_VERSIONS.contains(schemaVersion)) {
			throw new SpecificationIntegrityException(
					"Unsupported schema version: " + schemaVersion +
							". Supported versions: " + SUPPORTED_SCHEMA_VERSIONS);
		}

		// Validate content fingerprint
		if (storedFingerprint == null || storedFingerprint.isEmpty()) {
			throw new SpecificationIntegrityException(
					"Missing contentFingerprint field. Spec files must include a content fingerprint.");
		}

		// Compute expected fingerprint (content up to and including schemaVersion line)
		String contentForHashing = extractContentForHashing(content);
		String computedFingerprint = computeFingerprint(contentForHashing);

		if (!storedFingerprint.equals(computedFingerprint)) {
			throw new SpecificationIntegrityException(
					"Content fingerprint mismatch. The spec file may have been modified outside " +
							"the approval workflow. Expected: " + computedFingerprint +
							", Found: " + storedFingerprint);
		}
	}

	/**
	 * Extracts the content that should be used for fingerprint computation.
	 * This is all content up to and including the schemaVersion line.
	 */
	private static String extractContentForHashing(String content) {
		int fingerprintIdx = content.indexOf(FINGERPRINT_FIELD + ":");
		if (fingerprintIdx < 0) {
			return content;
		}
		return content.substring(0, fingerprintIdx);
	}

	/**
	 * Extracts a top-level field value from YAML content.
	 */
	private static String extractFieldValue(String content, String fieldName) {
		String prefix = fieldName + ":";
		int idx = content.indexOf(prefix);
		if (idx < 0) return null;

		int lineEnd = content.indexOf('\n', idx);
		if (lineEnd < 0) lineEnd = content.length();

		String line = content.substring(idx, lineEnd);
		return extractValue(line);
	}

	/**
	 * Computes a SHA-256 fingerprint of the given content.
	 */
	static String computeFingerprint(String content) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 algorithm not available", e);
		}
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
}
