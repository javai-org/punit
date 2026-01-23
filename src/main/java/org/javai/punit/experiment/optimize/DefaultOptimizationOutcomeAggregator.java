package org.javai.punit.experiment.optimize;

import java.util.List;
import org.javai.punit.contract.UseCaseOutcome;

/**
 * Default implementation of OptimizationOutcomeAggregator.
 *
 * <p>Computes standard statistics from a list of use case outcomes.
 *
 * <p>Token counts are extracted from the outcome's metadata using common keys
 * ("tokensUsed", "tokens", "totalTokens").
 */
public final class DefaultOptimizationOutcomeAggregator implements OptimizationOutcomeAggregator {

    @Override
    public OptimizeStatistics aggregate(List<UseCaseOutcome<?>> outcomes) {
        if (outcomes == null || outcomes.isEmpty()) {
            return OptimizeStatistics.empty();
        }

        int sampleCount = outcomes.size();
        int successCount = 0;
        long totalTokens = 0;
        long totalLatencyMs = 0;

        for (UseCaseOutcome<?> outcome : outcomes) {
            if (outcome.allPostconditionsSatisfied()) {
                successCount++;
            }
            // Extract tokens from metadata using common keys
            totalTokens += outcome.getMetadataLong("tokensUsed", "tokens", "totalTokens").orElse(0L);
            // Get latency from execution time
            totalLatencyMs += outcome.executionTime().toMillis();
        }

        double meanLatencyMs = sampleCount > 0 ? (double) totalLatencyMs / sampleCount : 0.0;

        return OptimizeStatistics.fromCounts(sampleCount, successCount, totalTokens, meanLatencyMs);
    }
}
