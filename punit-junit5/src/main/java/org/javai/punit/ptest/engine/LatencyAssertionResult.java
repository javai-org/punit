package org.javai.punit.ptest.engine;

import java.util.List;

/**
 * Result of evaluating latency assertions against observed percentiles.
 *
 * <p>Contains per-percentile results plus an overall pass/fail verdict.
 * The result may be {@link #skipped()} if no successful samples were available.
 *
 * <p>Also carries the full observed distribution (p50-max) for transparent
 * stats rendering, regardless of which percentiles were asserted.
 *
 * <p>Package-private: internal implementation detail of the test extension.
 *
 * @param passed true if all asserted percentiles are within thresholds
 * @param percentileResults per-percentile assertion results (only for asserted percentiles)
 * @param successfulSampleCount number of successful samples used for latency computation
 * @param skipped true if latency evaluation was skipped (e.g., zero successes)
 * @param caveats list of advisory messages (e.g., undersized sample warning)
 * @param maxLatencyMs slowest single sample in milliseconds (-1 if not available)
 * @param observedP50Ms observed p50 latency (-1 if not available)
 * @param observedP90Ms observed p90 latency (-1 if not available)
 * @param observedP95Ms observed p95 latency (-1 if not available)
 * @param observedP99Ms observed p99 latency (-1 if not available)
 */
record LatencyAssertionResult(
        boolean passed,
        List<PercentileResult> percentileResults,
        int successfulSampleCount,
        boolean skipped,
        List<String> caveats,
        long maxLatencyMs,
        long observedP50Ms,
        long observedP90Ms,
        long observedP95Ms,
        long observedP99Ms
) {

    /**
     * Backward-compatible constructor without distribution fields.
     */
    LatencyAssertionResult(
            boolean passed,
            List<PercentileResult> percentileResults,
            int successfulSampleCount,
            boolean skipped,
            List<String> caveats) {
        this(passed, percentileResults, successfulSampleCount, skipped, caveats,
                -1, -1, -1, -1, -1);
    }

    /**
     * Creates a skipped result (no latency data available).
     */
    static LatencyAssertionResult skipped(String reason) {
        return new LatencyAssertionResult(
                true, List.of(), 0, true, List.of(reason),
                -1, -1, -1, -1, -1);
    }

    /**
     * Creates a result with no latency assertions requested.
     */
    static LatencyAssertionResult notRequested() {
        return new LatencyAssertionResult(
                true, List.of(), 0, true, List.of(),
                -1, -1, -1, -1, -1);
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
