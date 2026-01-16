package org.javai.punit.experiment.optimize;

import org.javai.punit.model.UseCaseOutcome;

import java.util.List;

/**
 * Default implementation of OptimizationOutcomeAggregator.
 *
 * <p>Computes standard statistics from a list of use case outcomes.
 *
 * <p>Token counts are extracted from the outcome's result values using the key "tokensUsed".
 * If not present, tokens are counted as 0.
 */
public final class DefaultOptimizationOutcomeAggregator implements OptimizationOutcomeAggregator {

    /** The default key for token count in result values. */
    public static final String TOKEN_COUNT_KEY = "tokensUsed";

    @Override
    public OptimizeStatistics aggregate(List<UseCaseOutcome> outcomes) {
        if (outcomes == null || outcomes.isEmpty()) {
            return OptimizeStatistics.empty();
        }

        int sampleCount = outcomes.size();
        int successCount = 0;
        long totalTokens = 0;
        long totalLatencyMs = 0;

        for (UseCaseOutcome outcome : outcomes) {
            if (outcome.allPassed()) {
                successCount++;
            }
            // Extract tokens from result values (common convention: "tokensUsed")
            totalTokens += outcome.result().getLong(TOKEN_COUNT_KEY, 0);
            // Get latency from execution time
            totalLatencyMs += outcome.executionTime().toMillis();
        }

        double meanLatencyMs = sampleCount > 0 ? (double) totalLatencyMs / sampleCount : 0.0;

        return OptimizeStatistics.fromCounts(sampleCount, successCount, totalTokens, meanLatencyMs);
    }
}
