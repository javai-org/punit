package org.javai.punit.examples.experiments;

import org.javai.punit.experiment.optimize.OptimizationIterationAggregate;
import org.javai.punit.experiment.optimize.Scorer;

/**
 * Scorer that evaluates iterations based on success rate.
 *
 * <p>Use with {@code OptimizationObjective.MAXIMIZE} - higher success rates are better.
 *
 * <p><b>Demo mode:</b> When no real samples are collected (sampleCount == 0),
 * returns simulated scores showing the expected improvement progression:
 * <pre>
 * Iteration 0: 0.30 (weak prompt)
 * Iteration 1: 0.50 (+ JSON format)
 * Iteration 2: 0.65 (+ schema structure)
 * Iteration 3: 0.80 (+ required fields)
 * Iteration 4: 0.90 (+ valid actions)
 * Iteration 5+: 0.95 (+ quantity constraints)
 * </pre>
 *
 * @see org.javai.punit.experiment.optimize.Scorer
 */
public class SuccessRateScorer implements Scorer<OptimizationIterationAggregate> {

    /**
     * Simulated success rates for demo mode, matching the expected
     * progression documented in {@link ShoppingBasketPromptMutator}.
     */
    private static final double[] DEMO_SCORES = {0.30, 0.50, 0.65, 0.80, 0.90, 0.95};

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
