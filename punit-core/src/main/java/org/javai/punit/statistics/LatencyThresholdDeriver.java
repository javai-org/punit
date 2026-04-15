package org.javai.punit.statistics;

import java.util.Arrays;
import java.util.Objects;

import org.apache.commons.statistics.distribution.BinomialDistribution;

/**
 * Derives latency thresholds from baseline data.
 *
 * <p>The canonical derivation (javai-R v1.1, &sect;12.4) is the
 * <b>exact binomial order-statistic upper confidence bound</b> on the baseline
 * percentile {@code Q(p)}:
 *
 * <pre>
 * tau = t_{(k)}
 * where k = qbinom(1 - alpha, n_s, p) + 1,
 *       clamped to [ceil(p * n_s), n_s]
 * </pre>
 *
 * <p>{@code t_{(k)}} is the {@code k}-th order statistic (1-indexed) of the sorted
 * baseline successful-response latencies. The threshold is therefore an
 * observed baseline latency value &mdash; integer-ms by construction.
 *
 * <p>This construction is non-parametric, distribution-free, and exact for any
 * continuous underlying latency distribution.
 */
public final class LatencyThresholdDeriver {

    private LatencyThresholdDeriver() {}

    /**
     * Derives a latency threshold using the exact binomial order-statistic
     * upper confidence bound.
     *
     * @param baselineLatencies observed successful-response latencies from the
     *                          baseline experiment (need not be sorted; must be
     *                          non-empty)
     * @param p                 percentile level in (0, 1) (e.g. 0.95)
     * @param confidence        one-sided confidence level in (0, 1) (e.g. 0.95)
     * @return the derived threshold, with rank, threshold value, point-estimate
     *         percentile, and sample count
     * @throws IllegalArgumentException if inputs are invalid
     */
    public static Threshold derive(double[] baselineLatencies, double p, double confidence) {
        Objects.requireNonNull(baselineLatencies, "baselineLatencies must not be null");
        if (baselineLatencies.length == 0) {
            throw new IllegalArgumentException("baselineLatencies must not be empty");
        }
        if (p <= 0.0 || p >= 1.0) {
            throw new IllegalArgumentException("p must be in (0, 1)");
        }
        if (confidence <= 0.0 || confidence >= 1.0) {
            throw new IllegalArgumentException("confidence must be in (0, 1)");
        }

        double[] sorted = baselineLatencies.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        double alpha = 1.0 - confidence;

        // Commons Statistics' BinomialDistribution.inverseCumulativeProbability(q)
        // returns the smallest x such that P(X <= x) >= q, matching R's qbinom(q, n, p).
        BinomialDistribution binomial = BinomialDistribution.of(n, p);
        int qb = binomial.inverseCumulativeProbability(1.0 - alpha);
        int k = qb + 1;

        int pointRank = (int) Math.ceil(p * n);
        if (pointRank < 1) pointRank = 1;
        k = Math.max(pointRank, Math.min(k, n));

        double threshold = sorted[k - 1];
        double baselinePercentile = LatencyStatistics.nearestRankPercentile(sorted, p);

        return new Threshold(k, threshold, baselinePercentile, n);
    }

    /**
     * Result of the exact binomial order-statistic threshold derivation.
     *
     * @param rank               the order-statistic rank {@code k} (1-indexed)
     * @param threshold          the {@code k}-th order statistic value
     * @param baselinePercentile the nearest-rank point estimate {@code Q(p)}
     * @param n                  the baseline sample count
     */
    public record Threshold(int rank, double threshold, double baselinePercentile, int n) {}

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
