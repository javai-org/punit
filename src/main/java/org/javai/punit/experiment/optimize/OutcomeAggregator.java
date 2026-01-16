package org.javai.punit.experiment.optimize;

import org.javai.punit.model.UseCaseOutcome;

import java.util.List;

/**
 * Aggregates use case outcomes into statistics.
 *
 * <p>Converts a list of individual outcomes from running a use case N times
 * into aggregated statistics suitable for scoring.
 */
@FunctionalInterface
public interface OutcomeAggregator {

    /**
     * Aggregate outcomes into statistics.
     *
     * @param outcomes the list of outcomes to aggregate
     * @return aggregated statistics
     */
    AggregateStatistics aggregate(List<UseCaseOutcome> outcomes);

    /**
     * Default aggregator that computes standard statistics.
     *
     * @return a default OutcomeAggregator implementation
     */
    static OutcomeAggregator defaultAggregator() {
        return new DefaultOutcomeAggregator();
    }
}
