package org.javai.punit.spec.model;

/**
 * Latency baseline data recorded from a measure experiment.
 *
 * <p>This record captures the distributional latency profile of successful
 * samples. It is stored in the spec YAML under the {@code statistics.latency}
 * section and later used for baseline-derived latency threshold computation.
 *
 * <p>All time values are in milliseconds.
 *
 * @param sampleCount number of successful samples contributing to the distribution
 * @param meanMs mean latency
 * @param standardDeviationMs standard deviation of latencies (sample stddev, n-1)
 * @param p50Ms 50th percentile (median) latency
 * @param p90Ms 90th percentile latency
 * @param p95Ms 95th percentile latency
 * @param p99Ms 99th percentile latency
 * @param maxMs maximum observed latency
 */
public record LatencyBaseline(
        int sampleCount,
        long meanMs,
        long standardDeviationMs,
        long p50Ms,
        long p90Ms,
        long p95Ms,
        long p99Ms,
        long maxMs
) {
    /**
     * Validates the latency baseline data.
     */
    public LatencyBaseline {
        if (sampleCount < 0) {
            throw new IllegalArgumentException("sampleCount must be non-negative");
        }
        if (meanMs < 0) {
            throw new IllegalArgumentException("meanMs must be non-negative");
        }
    }
}
