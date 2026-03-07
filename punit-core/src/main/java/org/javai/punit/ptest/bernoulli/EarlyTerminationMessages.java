package org.javai.punit.ptest.bernoulli;

import org.javai.punit.model.TerminationReason;
import org.javai.punit.reporting.RateFormat;

/**
 * Builds human-readable explanation messages for early termination events.
 *
 * <p>This class is a formatting companion to {@link EarlyTerminationEvaluator},
 * keeping the evaluator free of reporting dependencies. The evaluator determines
 * <em>whether</em> to terminate; this class explains <em>why</em>.
 */
public final class EarlyTerminationMessages {

    private EarlyTerminationMessages() {
        // Utility class — no instantiation
    }

    /**
     * Builds a detailed explanation of why impossibility was triggered.
     *
     * @param successesSoFar successes at time of termination
     * @param samplesExecuted samples executed at time of termination
     * @param totalSamples total planned samples
     * @param requiredSuccesses minimum successes required to pass
     * @return explanation string
     */
    public static String buildImpossibilityExplanation(
            int successesSoFar, int samplesExecuted, int totalSamples, int requiredSuccesses) {
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
     * @param totalSamples total planned samples
     * @param requiredSuccesses minimum successes required to pass
     * @return explanation string
     */
    public static String buildSuccessGuaranteedExplanation(
            int successesSoFar, int samplesExecuted, int totalSamples, int requiredSuccesses) {
        int remainingSamples = totalSamples - samplesExecuted;
        double observedPassRate = (double) successesSoFar / samplesExecuted;

        return String.format(
            "After %d samples with %d successes (%s), required min pass rate (%d successes) " +
            "already met. Skipping %d remaining samples.",
            samplesExecuted, successesSoFar, RateFormat.format(observedPassRate),
            requiredSuccesses, remainingSamples);
    }

    /**
     * Builds an explanation for the given termination reason.
     *
     * @param reason the termination reason
     * @param successesSoFar successes at time of termination
     * @param samplesExecuted samples executed at time of termination
     * @param totalSamples total planned samples
     * @param requiredSuccesses minimum successes required to pass
     * @return explanation string
     */
    public static String buildExplanation(
            TerminationReason reason, int successesSoFar, int samplesExecuted,
            int totalSamples, int requiredSuccesses) {
        return switch (reason) {
            case IMPOSSIBILITY -> buildImpossibilityExplanation(
                    successesSoFar, samplesExecuted, totalSamples, requiredSuccesses);
            case SUCCESS_GUARANTEED -> buildSuccessGuaranteedExplanation(
                    successesSoFar, samplesExecuted, totalSamples, requiredSuccesses);
            default -> reason.getDescription();
        };
    }
}
