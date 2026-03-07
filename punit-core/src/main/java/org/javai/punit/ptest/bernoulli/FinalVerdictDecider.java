package org.javai.punit.ptest.bernoulli;

/**
 * Determines the final pass/fail verdict for a probabilistic test
 * based on the observed pass rate compared to the minimum required rate.
 *
 * <p>This class contains only decision logic. Message formatting is
 * handled by {@link VerdictMessageFormatter}.
 */
public class FinalVerdictDecider {

	private final VerdictMessageFormatter messageFormatter = new VerdictMessageFormatter();

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
	 * @param aggregator the aggregated sample results
	 * @param context the statistical context with baseline and confidence data
	 * @return a formatted failure message with statistical qualifications
	 */
	public String buildFailureMessage(SampleResultAggregator aggregator,
									  BernoulliFailureMessages.StatisticalContext context) {
		return messageFormatter.buildFailureMessage(aggregator, context);
	}

	/**
	 * Builds a summary message for a passing test.
	 *
	 * @param aggregator the aggregated sample results
	 * @param minPassRate the minimum required pass rate
	 * @return a formatted summary message
	 */
	public String buildSuccessMessage(SampleResultAggregator aggregator, double minPassRate) {
		return messageFormatter.buildSuccessMessage(aggregator, minPassRate);
	}
}
