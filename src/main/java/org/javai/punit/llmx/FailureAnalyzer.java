package org.javai.punit.llmx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.javai.punit.experiment.model.FailureObservation;

/**
 * Analyzes failure observations to identify patterns for prompt refinement.
 *
 * <p>This component extracts structured information from failures to guide
 * the prompt refinement process.
 */
public class FailureAnalyzer {

	/**
	 * Analyzes a list of failures and returns structured analysis.
	 *
	 * @param failures the failures to analyze
	 * @return the analysis result
	 */
	public FailureAnalysis analyze(List<FailureObservation> failures) {
		if (failures == null || failures.isEmpty()) {
			return new FailureAnalysis("No failures to analyze", List.of(), Map.of());
		}

		// Group by category
		Map<String, List<FailureObservation>> byCategory = failures.stream()
				.collect(Collectors.groupingBy(FailureObservation::category));

		// Count categories
		Map<String, Integer> categoryCounts = new HashMap<>();
		for (Map.Entry<String, List<FailureObservation>> entry : byCategory.entrySet()) {
			categoryCounts.put(entry.getKey(), entry.getValue().size());
		}

		// Identify patterns
		List<FailurePattern> patterns = identifyPatterns(failures);

		// Build summary
		String summary = buildSummary(failures.size(), categoryCounts, patterns);

		return new FailureAnalysis(summary, patterns, categoryCounts);
	}

	private List<FailurePattern> identifyPatterns(List<FailureObservation> failures) {
		List<FailurePattern> patterns = new ArrayList<>();

		// Pattern: Missing required fields
		Map<String, Integer> missingFieldCounts = new HashMap<>();
		for (FailureObservation failure : failures) {
			for (String criterion : failure.unmetCriteria()) {
				if (criterion.contains("missing") || criterion.contains("required")) {
					missingFieldCounts.merge(criterion, 1, Integer::sum);
				}
			}
		}
		for (Map.Entry<String, Integer> entry : missingFieldCounts.entrySet()) {
			if (entry.getValue() >= 2) {
				patterns.add(new FailurePattern(
						"MISSING_FIELD",
						entry.getKey(),
						entry.getValue()
				));
			}
		}

		// Pattern: Invalid format
		long formatErrors = failures.stream()
				.filter(f -> f.category().contains("FORMAT") || f.category().contains("PARSE"))
				.count();
		if (formatErrors >= 2) {
			patterns.add(new FailurePattern(
					"FORMAT_ERROR",
					"Responses frequently have invalid format",
					(int) formatErrors
			));
		}

		// Pattern: Validation failures
		Map<String, Integer> validationFailures = new HashMap<>();
		for (FailureObservation failure : failures) {
			for (String criterion : failure.unmetCriteria()) {
				if (criterion.contains("valid") || criterion.contains("constraint")) {
					validationFailures.merge(criterion, 1, Integer::sum);
				}
			}
		}
		for (Map.Entry<String, Integer> entry : validationFailures.entrySet()) {
			if (entry.getValue() >= 2) {
				patterns.add(new FailurePattern(
						"VALIDATION_FAILURE",
						entry.getKey(),
						entry.getValue()
				));
			}
		}

		// Sort by occurrence count (descending)
		patterns.sort(Comparator.comparing(FailurePattern::occurrences).reversed());

		return patterns;
	}

	private String buildSummary(int totalFailures, Map<String, Integer> categoryCounts,
								List<FailurePattern> patterns) {
		StringBuilder sb = new StringBuilder();

		sb.append(totalFailures).append(" failures analyzed.\n");

		if (!categoryCounts.isEmpty()) {
			sb.append("\nBy category:\n");
			categoryCounts.entrySet().stream()
					.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
					.forEach(e -> sb.append("  - ").append(e.getKey())
							.append(": ").append(e.getValue()).append("\n"));
		}

		if (!patterns.isEmpty()) {
			sb.append("\nTop patterns:\n");
			patterns.stream()
					.limit(5)
					.forEach(p -> sb.append("  - ").append(p.description())
							.append(" (").append(p.occurrences()).append("x)\n"));
		}

		return sb.toString();
	}

	/**
	 * Result of failure analysis.
	 */
	public static final class FailureAnalysis {

		private final String summary;
		private final List<FailurePattern> patterns;
		private final Map<String, Integer> categoryCounts;

		public FailureAnalysis(String summary, List<FailurePattern> patterns,
							   Map<String, Integer> categoryCounts) {
			this.summary = Objects.requireNonNull(summary);
			this.patterns = patterns != null ? List.copyOf(patterns) : List.of();
			this.categoryCounts = categoryCounts != null
					? Map.copyOf(categoryCounts)
					: Map.of();
		}

		public String getSummary() {
			return summary;
		}

		public List<FailurePattern> getPatterns() {
			return patterns;
		}

		public Map<String, Integer> getCategoryCounts() {
			return categoryCounts;
		}
	}

	/**
	 * A failure pattern identified in the analysis.
	 */
	public record FailurePattern(String type, String description, int occurrences) {
	}
}

