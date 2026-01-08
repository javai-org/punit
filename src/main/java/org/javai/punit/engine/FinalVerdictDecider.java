package org.javai.punit.engine;

import org.javai.punit.model.TerminationReason;

/**
 * Determines the final pass/fail verdict for a probabilistic test
 * based on the observed pass rate compared to the minimum required rate.
 */
public class FinalVerdictDecider {

	/**
	 * Determines whether the test passes based on aggregated results.
	 *
	 * <p>The test passes if and only if:
	 * {@code observedPassRate >= minPassRate}
	 *
	 * @param aggregator the aggregated sample results
	 * @param minPassRate the minimum required pass rate (0.0 to 1.0)
	 * @return true if the test passes, false otherwise
	 */
	public boolean isPassing(SampleResultAggregator aggregator, double minPassRate) {
		return aggregator.getObservedPassRate() >= minPassRate;
	}

	/**
	 * Calculates the number of successes required to meet the minimum pass rate.
	 *
	 * <p>Uses ceiling to ensure the threshold is met, not merely approached.
	 * For example, with samples=10 and minPassRate=0.95, requiredSuccesses=10.
	 *
	 * @param totalSamples the total number of samples
	 * @param minPassRate the minimum required pass rate
	 * @return the minimum number of successes required
	 */
	public int calculateRequiredSuccesses(int totalSamples, double minPassRate) {
		return (int) Math.ceil(totalSamples * minPassRate);
	}

	/**
	 * Builds a statistically qualified failure message for spec-driven tests.
	 *
	 * <p>This is the preferred method for spec-driven tests as it includes
	 * full statistical context: confidence, alpha, baseline rate, and spec ID.
	 *
	 * <p>Format:
	 * <pre>
	 * PUnit FAILED with 95.0% confidence (alpha=0.050). Observed pass rate=87.0% (87/100) < threshold=91.6%. Baseline=95.1% (N=1000), spec=json.generation:v3
	 * </pre>
	 *
	 * @param aggregator the aggregated sample results
	 * @param context the statistical context with baseline and confidence data
	 * @return a formatted failure message with statistical qualifications
	 */
	public String buildFailureMessage(SampleResultAggregator aggregator,
									  PunitFailureMessages.StatisticalContext context) {
		StringBuilder sb = new StringBuilder();

		// Primary failure message with statistical qualifications
		if (context.isSpecDriven()) {
			sb.append(PunitFailureMessages.probabilisticTestFailure(context));
		} else {
			sb.append(PunitFailureMessages.probabilisticTestFailureLegacy(
					context.observedRate(),
					context.successes(),
					context.samples(),
					context.threshold()));
		}
		sb.append("\n\n");

		// Additional context
		appendExecutionDetails(sb, aggregator);

		return sb.toString();
	}

	/**
	 * Builds a detailed failure message with statistics (legacy method for backward compatibility).
	 *
	 * <p>This method is retained for backward compatibility with tests that don't have
	 * statistical context. For new code, prefer {@link #buildFailureMessage(SampleResultAggregator, PunitFailureMessages.StatisticalContext)}.
	 *
	 * @param aggregator the aggregated sample results
	 * @param minPassRate the minimum required pass rate
	 * @return a formatted failure message
	 * @deprecated Use {@link #buildFailureMessage(SampleResultAggregator, PunitFailureMessages.StatisticalContext)} for full statistical context
	 */
	@Deprecated
	public String buildFailureMessage(SampleResultAggregator aggregator, double minPassRate) {
		StringBuilder sb = new StringBuilder();

		double observedRate = aggregator.getObservedPassRate();

		// Use legacy format without statistical qualifications
		sb.append(PunitFailureMessages.probabilisticTestFailureLegacy(
				observedRate,
				aggregator.getSuccesses(),
				aggregator.getSamplesExecuted(),
				minPassRate));
		sb.append("\n\n");

		appendExecutionDetails(sb, aggregator);

		return sb.toString();
	}

	/**
	 * Appends execution details to the failure message.
	 */
	private void appendExecutionDetails(StringBuilder sb, SampleResultAggregator aggregator) {
		sb.append(String.format("  Samples executed: %d of %d%n",
				aggregator.getSamplesExecuted(), aggregator.getTotalSamples()));
		sb.append(String.format("  Successes: %d%n", aggregator.getSuccesses()));
		sb.append(String.format("  Failures: %d%n", aggregator.getFailures()));

		// Add termination reason if early termination occurred
		aggregator.getTerminationReason().ifPresent(
				reason -> {
					sb.append(String.format("  Termination: %s%n", reason.name()));
					String details = aggregator.getTerminationDetails();
					if (details != null && !details.isEmpty()) {
						sb.append(String.format("  Reason: %s%n", details));
					}
				}
		);

		sb.append(String.format("  Elapsed: %dms%n", aggregator.getElapsedMs()));

		// Add example failures if available
		if (!aggregator.getExampleFailures().isEmpty()) {
			sb.append(String.format("%n  Example failures (showing %d of %d):%n",
					aggregator.getExampleFailures().size(), aggregator.getFailures()));

			int sampleIndex = 1;
			for (Throwable failure : aggregator.getExampleFailures()) {
				String message = failure.getMessage();
				if (message == null || message.isEmpty()) {
					message = failure.getClass().getSimpleName();
				}
				// Truncate long messages
				if (message.length() > 80) {
					message = message.substring(0, 77) + "...";
				}
				sb.append(String.format("    [Sample %d] %s%n", sampleIndex++, message));
			}
		}
	}

	/**
	 * Builds a summary message for a passing test.
	 *
	 * @param aggregator the aggregated sample results
	 * @param minPassRate the minimum required pass rate
	 * @return a formatted summary message
	 */
	public String buildSuccessMessage(SampleResultAggregator aggregator, double minPassRate) {
		return String.format(
				"Probabilistic test passed: %.2f%% >= %.2f%% (%d/%d samples succeeded)",
				aggregator.getObservedPassRate() * 100,
				minPassRate * 100,
				aggregator.getSuccesses(),
				aggregator.getSamplesExecuted());
	}
}
