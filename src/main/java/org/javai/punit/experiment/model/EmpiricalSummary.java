package org.javai.punit.experiment.model;

import java.util.Map;

/**
 * Summary of empirical results from experiment execution.
 *
 * <p>Provides aggregated statistics for goal evaluation and refinement.
 */
public interface EmpiricalSummary {

	/**
	 * Returns the observed success rate.
	 *
	 * @return success rate between 0.0 and 1.0
	 */
	double successRate();

	/**
	 * Returns the number of successful samples.
	 *
	 * @return success count
	 */
	int successes();

	/**
	 * Returns the number of failed samples.
	 *
	 * @return failure count
	 */
	int failures();

	/**
	 * Returns the total number of samples executed.
	 *
	 * @return sample count
	 */
	int samplesExecuted();

	/**
	 * Returns the average latency per sample in milliseconds.
	 *
	 * @return average latency
	 */
	long avgLatencyMs();

	/**
	 * Returns the average tokens consumed per sample.
	 *
	 * @return average tokens
	 */
	long avgTokensPerSample();

	/**
	 * Returns the distribution of failure categories.
	 *
	 * @return map of category to count
	 */
	Map<String, Integer> failureDistribution();

	/**
	 * Checks if this summary meets the specified goal.
	 *
	 * @param targetSuccessRate the target success rate (NaN to ignore)
	 * @param maxLatencyMs the maximum latency (MAX_VALUE to ignore)
	 * @param maxTokensPerSample the maximum tokens (MAX_VALUE to ignore)
	 * @return true if all applicable criteria are met
	 */
	default boolean meetsGoal(double targetSuccessRate, long maxLatencyMs, long maxTokensPerSample) {
		if (!Double.isNaN(targetSuccessRate) && successRate() < targetSuccessRate) {
			return false;
		}
		if (maxLatencyMs < Long.MAX_VALUE && avgLatencyMs() > maxLatencyMs) {
			return false;
		}
		return maxTokensPerSample >= Long.MAX_VALUE || avgTokensPerSample() <= maxTokensPerSample;
	}
}

