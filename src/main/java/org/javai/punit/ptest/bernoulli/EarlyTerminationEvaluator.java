package org.javai.punit.ptest.bernoulli;

import java.util.Optional;
import org.javai.punit.model.TerminationReason;

/**
 * Evaluates whether a probabilistic test should terminate early.
 *
 * <p>Early termination occurs in two cases:
 * <ul>
 *   <li><b>Impossibility</b>: It becomes mathematically impossible to reach the required pass rate</li>
 *   <li><b>Success Guaranteed</b>: The required pass rate has already been achieved</li>
 * </ul>
 *
 * <p>Both are deterministic, lossless optimizations that accelerate test execution
 * without changing the outcome.
 *
 * <h2>Impossibility Detection</h2>
 * <p>After each sample k, we check:
 * <pre>
 *   successes_so_far + remaining_samples &lt; required_successes
 * </pre>
 * If true, even if all remaining samples pass, we cannot reach the threshold.
 *
 * <h2>Success Guaranteed Detection</h2>
 * <p>After each sample k, we check:
 * <pre>
 *   successes_so_far &gt;= required_successes
 * </pre>
 * If true, we've already achieved the required pass rate and can stop early.
 */
public class EarlyTerminationEvaluator {

    private final int totalSamples;
    private final int requiredSuccesses;

    /**
     * Creates an evaluator for the given test configuration.
     *
     * @param totalSamples the total number of planned samples
     * @param minPassRate the minimum required pass rate (0.0 to 1.0)
     */
    public EarlyTerminationEvaluator(int totalSamples, double minPassRate) {
        this.totalSamples = totalSamples;
        this.requiredSuccesses = calculateRequiredSuccesses(totalSamples, minPassRate);
    }

    /**
     * Calculates the number of successes required to meet the minimum pass rate.
     * Uses ceiling to ensure the threshold is met, not merely approached.
     *
     * <p>If minPassRate is NaN (should not happen in normal operation), returns
     * Integer.MAX_VALUE to prevent false early termination.
     *
     * @param totalSamples total number of samples
     * @param minPassRate minimum pass rate (0.0 to 1.0)
     * @return minimum number of successes required
     */
    public static int calculateRequiredSuccesses(int totalSamples, double minPassRate) {
        if (Double.isNaN(minPassRate)) {
            // NaN should not reach here (should be resolved to default earlier),
            // but if it does, prevent false early termination
            return Integer.MAX_VALUE;
        }
        return (int) Math.ceil(totalSamples * minPassRate);
    }

    /**
     * Evaluates whether the test should terminate early after a sample has completed.
     *
     * <p>Checks for two conditions:
     * <ol>
     *   <li><b>Success Guaranteed</b>: We've already achieved the required pass rate</li>
     *   <li><b>Impossibility</b>: We can no longer achieve the required pass rate</li>
     * </ol>
     *
     * @param successesSoFar number of successful samples so far
     * @param samplesExecuted total samples executed so far
     * @return the termination reason if early termination should occur, empty otherwise
     */
    public Optional<TerminationReason> shouldTerminate(int successesSoFar, int samplesExecuted) {
        int remainingSamples = totalSamples - samplesExecuted;
        
        // Check for success guaranteed: have we already achieved the required successes?
        // Only terminate early if there are remaining samples (otherwise it's normal completion)
        if (successesSoFar >= requiredSuccesses && remainingSamples > 0) {
            return Optional.of(TerminationReason.SUCCESS_GUARANTEED);
        }
        
        // Check for impossibility: can we still reach the required successes?
        int maxPossibleSuccesses = successesSoFar + remainingSamples;
        if (maxPossibleSuccesses < requiredSuccesses) {
            return Optional.of(TerminationReason.IMPOSSIBILITY);
        }

        return Optional.empty();
    }

    /**
     * Returns the number of successes required to pass the test.
     *
     * @return required number of successes
     */
    public int getRequiredSuccesses() {
        return requiredSuccesses;
    }

    /**
     * Returns the total number of planned samples.
     *
     * @return total samples
     */
    public int getTotalSamples() {
        return totalSamples;
    }

    /**
     * Calculates how many more failures can occur before impossibility is triggered.
     * Useful for understanding how close we are to early termination.
     *
     * @param successesSoFar current number of successes
     * @param samplesExecuted current number of samples executed
     * @return number of additional failures allowed before impossibility
     */
    public int getFailuresUntilImpossibility(int successesSoFar, int samplesExecuted) {
        int remainingSamples = totalSamples - samplesExecuted;
        int maxPossibleSuccesses = successesSoFar + remainingSamples;
        return maxPossibleSuccesses - requiredSuccesses;
    }

    /**
     * Builds a detailed explanation of why impossibility was triggered.
     *
     * @param successesSoFar successes at time of termination
     * @param samplesExecuted samples executed at time of termination
     * @return explanation string
     */
    public String buildImpossibilityExplanation(int successesSoFar, int samplesExecuted) {
        int remainingSamples = totalSamples - samplesExecuted;
        int maxPossibleSuccesses = successesSoFar + remainingSamples;
        
        return String.format(
            "After %d samples with %d successes, maximum possible successes (%d + %d = %d) " +
            "is less than required (%d)",
            samplesExecuted, successesSoFar,
            successesSoFar, remainingSamples, maxPossibleSuccesses,
            requiredSuccesses);
    }

    /**
     * Builds a detailed explanation of why success was guaranteed.
     *
     * @param successesSoFar successes at time of termination
     * @param samplesExecuted samples executed at time of termination
     * @return explanation string
     */
    public String buildSuccessGuaranteedExplanation(int successesSoFar, int samplesExecuted) {
        int remainingSamples = totalSamples - samplesExecuted;
        double observedPassRate = (double) successesSoFar / samplesExecuted;
        
        return String.format(
            "After %d samples with %d successes (%.1f%%), required min pass rate (%d successes) " +
            "already met. Skipping %d remaining samples.",
            samplesExecuted, successesSoFar, observedPassRate * 100,
            requiredSuccesses, remainingSamples);
    }

    /**
     * Builds an explanation for the given termination reason.
     *
     * @param reason the termination reason
     * @param successesSoFar successes at time of termination
     * @param samplesExecuted samples executed at time of termination
     * @return explanation string
     */
    public String buildExplanation(TerminationReason reason, int successesSoFar, int samplesExecuted) {
        return switch (reason) {
            case IMPOSSIBILITY -> buildImpossibilityExplanation(successesSoFar, samplesExecuted);
            case SUCCESS_GUARANTEED -> buildSuccessGuaranteedExplanation(successesSoFar, samplesExecuted);
            default -> reason.getDescription();
        };
    }
}

