package org.javai.punit.examples2.experiments;

import org.javai.punit.experiment.optimize.OptimizationIterationAggregate;
import org.javai.punit.experiment.optimize.Scorer;

/**
 * Scorer that evaluates iterations based on success rate.
 *
 * <p>Use with {@code OptimizationObjective.MAXIMIZE} - higher success rates are better.
 *
 * @see org.javai.punit.experiment.optimize.Scorer
 */
public class SuccessRateScorer implements Scorer<OptimizationIterationAggregate> {

    @Override
    public double score(OptimizationIterationAggregate aggregate) {
        return aggregate.statistics().successRate();
    }

    @Override
    public String description() {
        return "Success rate (higher is better) - measures % of samples where all criteria passed";
    }
}
