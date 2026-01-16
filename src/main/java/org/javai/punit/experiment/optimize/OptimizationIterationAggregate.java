package org.javai.punit.experiment.optimize;

import org.javai.punit.experiment.model.FactorSuit;

import java.time.Duration;
import java.time.Instant;

/**
 * Aggregate result of one optimization iteration.
 *
 * <p>This is analogous to what a single MEASURE experiment produces:
 * statistics aggregated from N outcomes with one factor suit.
 *
 * <p>The aggregate includes ALL factor values (fixed + treatment) so that:
 * <ol>
 *   <li>The scorer has full context for evaluation</li>
 *   <li>The history is self-describing and auditable</li>
 *   <li>Each iteration can be understood in isolation</li>
 * </ol>
 *
 * @param iterationNumber 0-indexed iteration number
 * @param factorSuit the complete factor suit for this iteration (fixed + treatment)
 * @param treatmentFactorName the name of the factor being optimized
 * @param statistics statistics aggregated from N outcomes
 * @param startTime when this iteration started
 * @param endTime when this iteration completed
 */
public record OptimizationIterationAggregate(
        int iterationNumber,
        FactorSuit factorSuit,
        String treatmentFactorName,
        OptimizeStatistics statistics,
        Instant startTime,
        Instant endTime
) {
    /**
     * Creates an OptimizationIterationAggregate with validation.
     */
    public OptimizationIterationAggregate {
        if (iterationNumber < 0) {
            throw new IllegalArgumentException("iterationNumber must be non-negative");
        }
        if (factorSuit == null) {
            throw new IllegalArgumentException("factorSuit must not be null");
        }
        if (treatmentFactorName == null || treatmentFactorName.isBlank()) {
            throw new IllegalArgumentException("treatmentFactorName must not be null or blank");
        }
        if (!factorSuit.contains(treatmentFactorName)) {
            throw new IllegalArgumentException(
                    "treatmentFactorName '" + treatmentFactorName + "' not found in factorSuit");
        }
        if (statistics == null) {
            throw new IllegalArgumentException("statistics must not be null");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("startTime must not be null");
        }
        if (endTime == null) {
            throw new IllegalArgumentException("endTime must not be null");
        }
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("endTime must not be before startTime");
        }
    }

    /**
     * Convenience method to get the current value of the treatment factor.
     *
     * @param <F> the type of the treatment factor
     * @return the treatment factor value
     */
    @SuppressWarnings("unchecked")
    public <F> F treatmentFactorValue() {
        return (F) factorSuit.get(treatmentFactorName);
    }

    /**
     * Duration of this iteration.
     *
     * @return the duration between start and end time
     */
    public Duration duration() {
        return Duration.between(startTime, endTime);
    }
}
