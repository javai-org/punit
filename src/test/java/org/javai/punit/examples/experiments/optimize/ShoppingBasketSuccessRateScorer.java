package org.javai.punit.examples.experiments.optimize;

import org.javai.punit.experiment.optimize.OptimizationIterationAggregate;
import org.javai.punit.experiment.optimize.Scorer;

/**
 * Scorer for shopping basket experiments that evaluates iterations based on success rate.
 *
 * <p>Use with {@code OptimizationObjective.MAXIMIZE} - higher success rates are better.
 *
 * <h2>Minimum Acceptance Threshold</h2>
 * <p>This scorer defines a <b>50% minimum acceptance threshold</b>. Iterations scoring
 * below this threshold are marked as {@code BELOW_THRESHOLD} in the optimization output,
 * indicating they don't meet minimum quality standards for the shopping basket use case.
 *
 * @see org.javai.punit.experiment.optimize.Scorer
 * @see ShoppingBasketPromptMutator
 * @see ShoppingBasketOptimizePrompt
 * @see ShoppingBasketOptimizeTemperature
 */
public class ShoppingBasketSuccessRateScorer implements Scorer<OptimizationIterationAggregate> {

    @Override
    public double score(OptimizationIterationAggregate aggregate) {
        // Use the actual success rate from samples
        return aggregate.statistics().successRate();
    }

    @Override
    public String description() {
        return "Success rate (higher is better) - measures % of samples where all criteria passed";
    }

    /**
     * Minimum acceptable success rate: 50%.
     *
     * <p>Iterations below this threshold are marked as BELOW_THRESHOLD
     * to clearly indicate they don't meet minimum quality standards.
     *
     * @return 0.50 (50% minimum acceptance rate)
     */
    @Override
    public double minimumAcceptanceThreshold() {
        return 0.50;
    }
}
