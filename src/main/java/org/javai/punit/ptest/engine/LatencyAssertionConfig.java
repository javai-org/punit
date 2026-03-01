package org.javai.punit.ptest.engine;

import org.javai.punit.api.Latency;

/**
 * Resolved latency assertion configuration from a {@link Latency} annotation.
 *
 * <p>Encapsulates which percentiles are asserted and their threshold values.
 * A threshold of {@code -1} means that percentile is not asserted.
 *
 * <p>Package-private: internal implementation detail of the test extension.
 */
record LatencyAssertionConfig(
        long p50Ms,
        long p90Ms,
        long p95Ms,
        long p99Ms,
        boolean baselineRequested
) {

    /**
     * Creates a config from a {@link Latency} annotation.
     *
     * @param latency the annotation
     * @param latencyBaseline whether baseline-derived thresholds are requested
     * @return the resolved config
     */
    static LatencyAssertionConfig fromAnnotation(Latency latency, boolean latencyBaseline) {
        return new LatencyAssertionConfig(
                latency.p50Ms(),
                latency.p90Ms(),
                latency.p95Ms(),
                latency.p99Ms(),
                latencyBaseline
        );
    }

    /**
     * Returns true if any latency assertion is requested (explicit thresholds
     * or baseline derivation).
     */
    boolean isLatencyRequested() {
        return hasExplicitThresholds() || baselineRequested;
    }

    /**
     * Returns true if at least one explicit percentile threshold is set.
     */
    boolean hasExplicitThresholds() {
        return p50Ms >= 0 || p90Ms >= 0 || p95Ms >= 0 || p99Ms >= 0;
    }

    /**
     * Returns true if the given percentile has an explicit threshold.
     */
    boolean hasP50() {
        return p50Ms >= 0;
    }

    boolean hasP90() {
        return p90Ms >= 0;
    }

    boolean hasP95() {
        return p95Ms >= 0;
    }

    boolean hasP99() {
        return p99Ms >= 0;
    }
}
