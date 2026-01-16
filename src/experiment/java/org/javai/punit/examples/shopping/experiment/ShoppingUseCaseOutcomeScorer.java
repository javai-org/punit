package org.javai.punit.examples.shopping.experiment;

import org.javai.punit.experiment.optimize.OptimizationIterationAggregate;
import org.javai.punit.experiment.optimize.Scorer;

/**
 * Scorer for ShoppingUseCase optimization experiments.
 *
 * <p>Scores iterations based on success rate - the percentage of samples
 * where all success criteria passed. Use with {@code OptimizationObjective.MAXIMIZE}.
 *
 * <p>A higher success rate indicates the system prompt is more effective
 * at guiding the LLM to produce valid, well-formatted responses.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @OptimizeExperiment(
 *     useCase = ShoppingUseCase.class,
 *     treatmentFactor = "systemPrompt",
 *     scorer = ShoppingUseCaseOutcomeScorer.class,
 *     objective = OptimizationObjective.MAXIMIZE,
 *     ...
 * )
 * }</pre>
 *
 * @see org.javai.punit.experiment.optimize.SuccessRateScorer
 */
public class ShoppingUseCaseOutcomeScorer implements Scorer<OptimizationIterationAggregate> {

    @Override
    public double score(OptimizationIterationAggregate aggregate) {
        return aggregate.statistics().successRate();
    }

    @Override
    public String description() {
        return "Success rate (higher is better) - measures % of samples where all criteria passed";
    }
}
