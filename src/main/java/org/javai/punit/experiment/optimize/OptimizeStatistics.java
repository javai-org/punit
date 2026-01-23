package org.javai.punit.experiment.optimize;

import java.util.Map;
import org.javai.punit.experiment.model.EmpiricalSummary;

/**
 * Optimization statistics aggregated from N outcomes.
 *
 * <p>These are the metrics available to the {@link Scorer} for evaluation.
 * An aggregate represents the statistical summary of running a use case
 * N times with the same factor suit.
 *
 * <p>Implements {@link EmpiricalSummary} for compatibility with the broader
 * experiment framework.
 *
 * @param sampleCount number of outcomes aggregated
 * @param successCount count of successful outcomes
 * @param failureCount count of failed outcomes
 * @param successRate proportion of successful outcomes (0.0 to 1.0)
 * @param totalTokens total tokens consumed across all outcomes
 * @param meanLatencyMs mean latency in milliseconds
 */
public record OptimizeStatistics(
        int sampleCount,
        int successCount,
        int failureCount,
        double successRate,
        long totalTokens,
        double meanLatencyMs
) implements EmpiricalSummary {
    /**
     * Creates OptimizeStatistics with validation.
     */
    public OptimizeStatistics {
        if (sampleCount < 0) {
            throw new IllegalArgumentException("sampleCount must be non-negative");
        }
        if (successCount < 0) {
            throw new IllegalArgumentException("successCount must be non-negative");
        }
        if (failureCount < 0) {
            throw new IllegalArgumentException("failureCount must be non-negative");
        }
        if (successCount + failureCount != sampleCount) {
            throw new IllegalArgumentException(
                    "successCount + failureCount must equal sampleCount: " +
                            successCount + " + " + failureCount + " != " + sampleCount);
        }
        if (successRate < 0.0 || successRate > 1.0) {
            throw new IllegalArgumentException("successRate must be between 0.0 and 1.0");
        }
        if (totalTokens < 0) {
            throw new IllegalArgumentException("totalTokens must be non-negative");
        }
        if (meanLatencyMs < 0.0) {
            throw new IllegalArgumentException("meanLatencyMs must be non-negative");
        }
    }

    /**
     * Creates OptimizeStatistics computing successRate from counts.
     *
     * @param sampleCount total number of samples
     * @param successCount number of successful samples
     * @param totalTokens total tokens consumed
     * @param meanLatencyMs mean latency in milliseconds
     * @return new OptimizeStatistics instance
     */
    public static OptimizeStatistics fromCounts(
            int sampleCount,
            int successCount,
            long totalTokens,
            double meanLatencyMs
    ) {
        int failureCount = sampleCount - successCount;
        double successRate = sampleCount > 0 ? (double) successCount / sampleCount : 0.0;
        return new OptimizeStatistics(
                sampleCount, successCount, failureCount, successRate, totalTokens, meanLatencyMs
        );
    }

    /**
     * Creates an empty OptimizeStatistics (zero samples).
     *
     * @return empty statistics
     */
    public static OptimizeStatistics empty() {
        return new OptimizeStatistics(0, 0, 0, 0.0, 0L, 0.0);
    }

    // ========== EmpiricalSummary Implementation ==========

    @Override
    public int successes() {
        return successCount;
    }

    @Override
    public int failures() {
        return failureCount;
    }

    @Override
    public int samplesExecuted() {
        return sampleCount;
    }

    @Override
    public long avgLatencyMs() {
        return Math.round(meanLatencyMs);
    }

    @Override
    public long avgTokensPerSample() {
        return sampleCount > 0 ? totalTokens / sampleCount : 0L;
    }

    @Override
    public Map<String, Integer> failureDistribution() {
        // Failure categorization not tracked at this level.
        // Returns empty map; detailed failure analysis available via OptimizationRecord.
        return Map.of();
    }
}
