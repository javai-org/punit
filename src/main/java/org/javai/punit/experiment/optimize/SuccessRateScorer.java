package org.javai.punit.experiment.optimize;

/**
 * Scores by success rate.
 *
 * <p>The simplest and most common scorer. Use with {@link OptimizationObjective#MAXIMIZE}.
 * A success rate of 0.95 scores higher than 0.90.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Scorer<OptimizationIterationAggregate> scorer = new SuccessRateScorer();
 * double score = scorer.score(aggregate); // Returns 0.0 to 1.0
 * }</pre>
 */
public final class SuccessRateScorer implements Scorer<OptimizationIterationAggregate> {

    @Override
    public double score(OptimizationIterationAggregate aggregate) {
        return aggregate.statistics().successRate();
    }

    @Override
    public String description() {
        return "Success rate (higher is better)";
    }
}
