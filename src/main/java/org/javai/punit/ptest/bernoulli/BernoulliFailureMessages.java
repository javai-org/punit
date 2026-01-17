package org.javai.punit.ptest.bernoulli;

/**
 * Failure message construction for Bernoulli trials-based probabilistic tests.
 *
 * <p>This utility constructs failure messages with full statistical context specific
 * to Bernoulli trials testing, ensuring:
 * <ul>
 *   <li><b>Auditability</b> — Every failure is self-documenting</li>
 *   <li><b>Reproducibility</b> — All parameters needed to understand the decision are present</li>
 *   <li><b>No hidden state</b> — The failure message alone explains the verdict</li>
 * </ul>
 *
 * <p>Failure messages include pass rate comparisons, confidence levels, and baseline context.
 */
public final class BernoulliFailureMessages {

    private BernoulliFailureMessages() {
        // Utility class - no instantiation
    }

    /**
     * Builds a failure message for a probabilistic test with full statistical context.
     *
     * <p>Format:
     * <pre>
     * PUnit FAILED with 95.0% confidence (alpha=0.050). Observed pass rate=87.0% (87/100) &lt; min pass rate=91.6%. Baseline=95.1% (N=1000), spec=json.generation:v3
     * </pre>
     *
     * @param context the statistical context containing all required parameters
     * @return formatted failure message
     */
    public static String probabilisticTestFailure(StatisticalContext context) {
        return String.format(
                "PUnit FAILED with %.1f%% confidence (alpha=%.3f). " +
                        "Observed pass rate=%.1f%% (%d/%d) < min pass rate=%.1f%%. " +
                        "Baseline=%.1f%% (N=%d), spec=%s",
                context.confidence() * 100.0,
                1.0 - context.confidence(),
                context.observedRate() * 100.0,
                context.successes(),
                context.samples(),
                context.threshold() * 100.0,
                context.baselineRate() * 100.0,
                context.baselineSamples(),
                context.specId()
        );
    }

    /**
     * Builds a failure message for a probabilistic test without spec (legacy mode).
     *
     * <p>Format:
     * <pre>
     * PUnit FAILED. Observed pass rate=87.0% (87/100) &lt; min pass rate=90.0%.
     * </pre>
     *
     * @param observedRate the observed pass rate (0.0 to 1.0)
     * @param successes number of successful samples
     * @param samples total number of samples executed
     * @param threshold the minimum required pass rate
     * @return formatted failure message
     */
    public static String probabilisticTestFailureLegacy(
            double observedRate,
            int successes,
            int samples,
            double threshold) {
        return String.format(
                "PUnit FAILED. Observed pass rate=%.1f%% (%d/%d) < min pass rate=%.1f%%.",
                observedRate * 100.0,
                successes,
                samples,
                threshold * 100.0
        );
    }

    /**
     * Builds a failure message for a latency regression test.
     *
     * <p>Format:
     * <pre>
     * PUnit LATENCY FAILED with 95.0% confidence (alpha=0.050). Observed exceedance rate=12.1% (12/99) > max allowed=8.0%. Threshold=45.20ms (baseline p95), baseline exceedance=5.0% (N=999), spec=checkout.service:v1
     * </pre>
     *
     * @param context the latency test statistical context
     * @return formatted failure message
     */
    public static String latencyRegressionFailure(LatencyStatisticalContext context) {
        return String.format(
                "PUnit LATENCY FAILED with %.1f%% confidence (alpha=%.3f). " +
                        "Observed exceedance rate=%.1f%% (%d/%d) > max allowed=%.1f%%. " +
                        "Threshold=%.2fms (baseline p%.0f), baseline exceedance=%.1f%% (N=%d), spec=%s",
                context.confidence() * 100.0,
                1.0 - context.confidence(),
                context.observedExceedanceRate() * 100.0,
                context.exceedances(),
                context.effectiveSamples(),
                context.maxAllowedExceedanceRate() * 100.0,
                context.thresholdMs(),
                context.thresholdQuantile() * 100.0,
                context.baselineExceedanceRate() * 100.0,
                context.baselineEffectiveSamples(),
                context.specId()
        );
    }

    /**
     * Builds a failure message for a latency test timeout threshold exceeded.
     *
     * <p>Format:
     * <pre>
     * PUnit LATENCY FAILED: timeout threshold exceeded. Timeouts=5 >= max allowed=5. Samples attempted=47/100, spec=checkout.service:v1
     * </pre>
     *
     * @param timeoutCount actual number of timeouts
     * @param maxAllowedTimeouts maximum allowed timeouts
     * @param samplesAttempted number of samples attempted before termination
     * @param samplesPlanned total samples planned
     * @param specId the specification identifier
     * @return formatted failure message
     */
    public static String latencyTimeoutFailure(
            int timeoutCount,
            int maxAllowedTimeouts,
            int samplesAttempted,
            int samplesPlanned,
            String specId) {
        return String.format(
                "PUnit LATENCY FAILED: timeout threshold exceeded. " +
                        "Timeouts=%d >= max allowed=%d. " +
                        "Samples attempted=%d/%d, spec=%s",
                timeoutCount,
                maxAllowedTimeouts,
                samplesAttempted,
                samplesPlanned,
                specId
        );
    }

    /**
     * Statistical context for probabilistic test failure messages.
     *
     * @param confidence the confidence level (0.0 to 1.0)
     * @param observedRate the observed pass rate (0.0 to 1.0)
     * @param successes number of successful samples
     * @param samples total samples executed
     * @param threshold the minimum required pass rate
     * @param baselineRate the baseline observed rate
     * @param baselineSamples the baseline sample count
     * @param specId the specification identifier (e.g., "json.generation:v3")
     */
    public record StatisticalContext(
            double confidence,
            double observedRate,
            int successes,
            int samples,
            double threshold,
            double baselineRate,
            int baselineSamples,
            String specId
    ) {
        /**
         * Creates a context for spec-less (legacy) mode where baseline data is unavailable.
         *
         * @param observedRate the observed pass rate
         * @param successes number of successful samples
         * @param samples total samples executed
         * @param threshold the minimum required pass rate
         * @return context with placeholder values for baseline fields
         */
        public static StatisticalContext forLegacyMode(
                double observedRate,
                int successes,
                int samples,
                double threshold) {
            return new StatisticalContext(
                    Double.NaN, // No confidence in legacy mode
                    observedRate,
                    successes,
                    samples,
                    threshold,
                    Double.NaN, // No baseline rate
                    0,          // No baseline samples
                    "(inline)"  // No spec
            );
        }

        /**
         * Returns true if this context represents a spec-driven test with full statistical data.
         */
        public boolean isSpecDriven() {
            return !Double.isNaN(confidence) && !Double.isNaN(baselineRate);
        }
    }

    /**
     * Statistical context for latency test failure messages.
     *
     * @param confidence the confidence level (0.0 to 1.0)
     * @param observedExceedanceRate the observed exceedance rate (0.0 to 1.0)
     * @param exceedances number of samples exceeding threshold
     * @param effectiveSamples effective sample count (after warmup exclusion)
     * @param maxAllowedExceedanceRate maximum allowed exceedance rate
     * @param thresholdMs the latency threshold in milliseconds
     * @param thresholdQuantile the quantile used for threshold (e.g., 0.95)
     * @param baselineExceedanceRate the baseline exceedance rate
     * @param baselineEffectiveSamples the baseline effective sample count
     * @param specId the specification identifier
     */
    public record LatencyStatisticalContext(
            double confidence,
            double observedExceedanceRate,
            int exceedances,
            int effectiveSamples,
            double maxAllowedExceedanceRate,
            double thresholdMs,
            double thresholdQuantile,
            double baselineExceedanceRate,
            int baselineEffectiveSamples,
            String specId
    ) {}
}

