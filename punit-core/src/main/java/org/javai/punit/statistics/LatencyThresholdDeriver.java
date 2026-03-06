package org.javai.punit.statistics;

import org.apache.commons.statistics.distribution.NormalDistribution;

/**
 * Derives upper confidence bounds for latency percentiles from baseline data.
 *
 * <p>Given a baseline percentile value, its standard deviation, and sample count,
 * this class computes an upper bound that accounts for sampling uncertainty.
 * The derived threshold allows for normal variance while catching genuine
 * latency degradation.
 *
 * <h2>Formula</h2>
 * <pre>
 * upperBound = baselinePercentile + z * estimatedStdErr
 * estimatedStdErr = stdDev / sqrt(sampleCount)
 * </pre>
 *
 * <p>Where {@code z} is the z-score for the desired confidence level (one-sided).
 *
 * <p>This class operates on primitives only and has no dependencies on
 * framework types (annotations, engine, spec, etc.).
 */
public final class LatencyThresholdDeriver {

    private static final NormalDistribution STANDARD_NORMAL = NormalDistribution.of(0, 1);

    private LatencyThresholdDeriver() {}

    /**
     * Derives an upper-bound latency threshold for a single percentile.
     *
     * @param baselinePercentileMs the observed percentile value from the baseline (ms)
     * @param baselineStdDevMs the standard deviation of latencies from the baseline (ms)
     * @param baselineSampleCount the number of samples in the baseline
     * @param confidence the confidence level (e.g., 0.95 for 95% one-sided)
     * @return the derived upper-bound threshold in milliseconds
     * @throws IllegalArgumentException if inputs are invalid
     */
    public static long deriveUpperBound(long baselinePercentileMs, long baselineStdDevMs,
                                        int baselineSampleCount, double confidence) {
        if (baselineSampleCount <= 0) {
            throw new IllegalArgumentException("baselineSampleCount must be positive");
        }
        if (confidence <= 0.0 || confidence >= 1.0) {
            throw new IllegalArgumentException("confidence must be in (0, 1)");
        }

        // z-score for one-sided upper bound at given confidence
        double z = STANDARD_NORMAL.inverseCumulativeProbability(confidence);

        // Estimated standard error of the percentile
        double stdErr = baselineStdDevMs / Math.sqrt(baselineSampleCount);

        // Upper bound
        double upperBound = baselinePercentileMs + z * stdErr;

        // Round up to ensure we don't accidentally create a threshold tighter than baseline
        return Math.max(baselinePercentileMs, (long) Math.ceil(upperBound));
    }

    /**
     * Result of deriving a latency threshold for a percentile.
     *
     * @param label the percentile label (e.g., "p95")
     * @param thresholdMs the derived threshold in ms
     * @param baselineMs the baseline percentile value in ms
     * @param source description of the threshold source
     */
    public record DerivedThreshold(
            String label,
            long thresholdMs,
            long baselineMs,
            String source
    ) {
    }
}
