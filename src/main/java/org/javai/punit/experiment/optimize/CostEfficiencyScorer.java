package org.javai.punit.experiment.optimize;

/**
 * Scores by cost efficiency: success rate per 1000 tokens.
 *
 * <p>Balances accuracy against token consumption. Useful when optimizing
 * for both quality and cost. Use with {@link OptimizationObjective#MAXIMIZE}.
 *
 * <p>Formula: {@code (successRate * 1000) / totalTokens}
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Scorer<OptimizationIterationAggregate> scorer = new CostEfficiencyScorer();
 * double score = scorer.score(aggregate);
 * }</pre>
 */
public final class CostEfficiencyScorer implements Scorer<OptimizationIterationAggregate> {

    @Override
    public double score(OptimizationIterationAggregate aggregate) {
        double successRate = aggregate.statistics().successRate();
        long tokens = aggregate.statistics().totalTokens();
        if (tokens == 0) {
            return 0.0;
        }
        return (successRate * 1000.0) / tokens;
    }

    @Override
    public String description() {
        return "Cost efficiency: success per 1k tokens";
    }
}
