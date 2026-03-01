package org.javai.punit.ptest.engine;

import java.util.List;

/**
 * Result of evaluating latency assertions against observed percentiles.
 *
 * <p>Contains per-percentile results plus an overall pass/fail verdict.
 * The result may be {@link #skipped()} if no successful samples were available.
 *
 * <p>Package-private: internal implementation detail of the test extension.
 *
 * @param passed true if all asserted percentiles are within thresholds
 * @param percentileResults per-percentile assertion results (only for asserted percentiles)
 * @param successfulSampleCount number of successful samples used for latency computation
 * @param skipped true if latency evaluation was skipped (e.g., zero successes)
 * @param caveats list of advisory messages (e.g., undersized sample warning)
 */
record LatencyAssertionResult(
        boolean passed,
        List<PercentileResult> percentileResults,
        int successfulSampleCount,
        boolean skipped,
        List<String> caveats
) {

    /**
     * Creates a skipped result (no latency data available).
     */
    static LatencyAssertionResult skipped(String reason) {
        return new LatencyAssertionResult(
                true, List.of(), 0, true, List.of(reason));
    }

    /**
     * Creates a result with no latency assertions requested.
     */
    static LatencyAssertionResult notRequested() {
        return new LatencyAssertionResult(
                true, List.of(), 0, true, List.of());
    }

    /**
     * Returns true if latency was actually evaluated (not skipped).
     */
    boolean wasEvaluated() {
        return !skipped;
    }

    /**
     * Result for a single percentile assertion.
     *
     * @param label the percentile label (e.g., "p95")
     * @param observedMs the observed value in milliseconds
     * @param thresholdMs the threshold value in milliseconds
     * @param passed true if observed <= threshold
     * @param indicative true if sample size is too small for reliable percentile
     * @param source the source of the threshold (e.g., "explicit", "from baseline", "baseline-capped")
     */
    record PercentileResult(
            String label,
            long observedMs,
            long thresholdMs,
            boolean passed,
            boolean indicative,
            String source
    ) {
        /**
         * Backward-compatible constructor without source.
         */
        PercentileResult(String label, long observedMs, long thresholdMs,
                         boolean passed, boolean indicative) {
            this(label, observedMs, thresholdMs, passed, indicative, "explicit");
        }
    }
}
