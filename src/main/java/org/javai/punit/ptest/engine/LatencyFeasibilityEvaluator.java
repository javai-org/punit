package org.javai.punit.ptest.engine;

/**
 * Evaluates whether the planned sample size is sufficient for reliable latency
 * percentile assertions in VERIFICATION mode.
 *
 * <p>Each percentile requires a minimum number of successful samples for the
 * observed value to be statistically meaningful:
 * <ul>
 *   <li>p50: 5 samples</li>
 *   <li>p90: 10 samples</li>
 *   <li>p95: 20 samples</li>
 *   <li>p99: 100 samples</li>
 * </ul>
 *
 * <p>The expected number of successful samples is {@code plannedSamples * expectedSuccessRate}.
 *
 * <p>Package-private: internal implementation detail of the test extension.
 */
class LatencyFeasibilityEvaluator {

    static final int MIN_SAMPLES_P50 = 5;
    static final int MIN_SAMPLES_P90 = 10;
    static final int MIN_SAMPLES_P95 = 20;
    static final int MIN_SAMPLES_P99 = 100;

    /**
     * Result of a latency feasibility evaluation.
     *
     * @param feasible true if all asserted percentiles can be reliably evaluated
     * @param message human-readable explanation (null if feasible)
     */
    record FeasibilityResult(boolean feasible, String message) {
        static FeasibilityResult ok() {
            return new FeasibilityResult(true, null);
        }

        static FeasibilityResult fail(String message) {
            return new FeasibilityResult(false, message);
        }
    }

    /**
     * Evaluates whether the planned sample size provides enough expected successful
     * samples for the asserted latency percentiles.
     *
     * @param config the latency assertion config (which percentiles are asserted)
     * @param plannedSamples the total planned samples
     * @param expectedSuccessRate the expected success rate (from minPassRate or baseline)
     * @return the feasibility result
     */
    static FeasibilityResult evaluate(LatencyAssertionConfig config,
                                      int plannedSamples,
                                      double expectedSuccessRate) {
        if (config == null || !config.isLatencyRequested() || !config.hasExplicitThresholds()) {
            return FeasibilityResult.ok();
        }

        int expectedSuccesses = (int) Math.floor(plannedSamples * expectedSuccessRate);

        if (config.hasP99() && expectedSuccesses < MIN_SAMPLES_P99) {
            return FeasibilityResult.fail(
                    formatMessage("p99", expectedSuccesses, MIN_SAMPLES_P99, plannedSamples, expectedSuccessRate));
        }
        if (config.hasP95() && expectedSuccesses < MIN_SAMPLES_P95) {
            return FeasibilityResult.fail(
                    formatMessage("p95", expectedSuccesses, MIN_SAMPLES_P95, plannedSamples, expectedSuccessRate));
        }
        if (config.hasP90() && expectedSuccesses < MIN_SAMPLES_P90) {
            return FeasibilityResult.fail(
                    formatMessage("p90", expectedSuccesses, MIN_SAMPLES_P90, plannedSamples, expectedSuccessRate));
        }
        if (config.hasP50() && expectedSuccesses < MIN_SAMPLES_P50) {
            return FeasibilityResult.fail(
                    formatMessage("p50", expectedSuccesses, MIN_SAMPLES_P50, plannedSamples, expectedSuccessRate));
        }

        return FeasibilityResult.ok();
    }

    private static String formatMessage(String percentile, int expectedSuccesses,
                                        int required, int plannedSamples, double expectedSuccessRate) {
        return String.format(
                "Latency %s assertion requires at least %d successful samples, " +
                "but only %d are expected (planned=%d, expected success rate=%.2f). " +
                "Increase sample size or remove the %s assertion.",
                percentile, required, expectedSuccesses, plannedSamples,
                expectedSuccessRate, percentile);
    }
}
