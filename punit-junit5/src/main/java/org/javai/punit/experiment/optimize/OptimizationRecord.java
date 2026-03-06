package org.javai.punit.experiment.optimize;

import java.util.Optional;

/**
 * A scored iteration record stored in the optimization history.
 *
 * <p>Combines the aggregate (what was evaluated) with the score (how good it was).
 *
 * @param aggregate the iteration's aggregate result
 * @param score score computed by the Scorer (0.0 if scoring failed)
 * @param status whether this iteration succeeded or failed
 * @param failureReason failure reason if status != SUCCESS
 */
public record OptimizationRecord(
        OptimizationIterationAggregate aggregate,
        double score,
        OptimizeStatus status,
        Optional<String> failureReason
) {
    /**
     * Creates an OptimizationRecord with validation.
     */
    public OptimizationRecord {
        if (aggregate == null) {
            throw new IllegalArgumentException("aggregate must not be null");
        }
        if (failureReason == null) {
            throw new IllegalArgumentException("failureReason must not be null (use Optional.empty())");
        }
        if (status == OptimizeStatus.SUCCESS && failureReason.isPresent()) {
            throw new IllegalArgumentException("SUCCESS status should not have a failure reason");
        }
        if (status != OptimizeStatus.SUCCESS && failureReason.isEmpty()) {
            throw new IllegalArgumentException("Failed status must have a failure reason");
        }
    }

    /**
     * Get the iteration number from the aggregate.
     *
     * @return the 0-indexed iteration number
     */
    public int iterationNumber() {
        return aggregate.iterationNumber();
    }

    /**
     * Check if this iteration completed successfully.
     *
     * @return true if status is SUCCESS
     */
    public boolean isSuccessful() {
        return status == OptimizeStatus.SUCCESS;
    }

    /**
     * Creates a successful iteration record.
     *
     * @param aggregate the iteration aggregate
     * @param score the computed score
     * @return a new successful OptimizationRecord
     */
    public static OptimizationRecord success(OptimizationIterationAggregate aggregate, double score) {
        return new OptimizationRecord(aggregate, score, OptimizeStatus.SUCCESS, Optional.empty());
    }

    /**
     * Creates a failed iteration record due to execution failure.
     *
     * @param aggregate the iteration aggregate (may have partial statistics)
     * @param reason the failure reason
     * @return a new failed OptimizationRecord
     */
    public static OptimizationRecord executionFailed(OptimizationIterationAggregate aggregate, String reason) {
        return new OptimizationRecord(aggregate, 0.0, OptimizeStatus.EXECUTION_FAILED, Optional.of(reason));
    }

    /**
     * Creates a failed iteration record due to scoring failure.
     *
     * @param aggregate the iteration aggregate
     * @param reason the failure reason
     * @return a new failed OptimizationRecord
     */
    public static OptimizationRecord scoringFailed(OptimizationIterationAggregate aggregate, String reason) {
        return new OptimizationRecord(aggregate, 0.0, OptimizeStatus.SCORING_FAILED, Optional.of(reason));
    }

    /**
     * Creates an iteration record for a score below the acceptance threshold.
     *
     * <p>The iteration executed and scored correctly, but the score is below
     * the minimum acceptable level defined by the scorer.
     *
     * @param aggregate the iteration aggregate
     * @param score the computed score (below threshold)
     * @param threshold the minimum acceptance threshold
     * @return a new below-threshold OptimizationRecord
     */
    public static OptimizationRecord belowThreshold(
            OptimizationIterationAggregate aggregate, double score, double threshold) {
        String reason = String.format("Score %.2f is below minimum threshold %.2f", score, threshold);
        return new OptimizationRecord(aggregate, score, OptimizeStatus.BELOW_THRESHOLD, Optional.of(reason));
    }

    /**
     * Creates the appropriate record based on score and threshold.
     *
     * @param aggregate the iteration aggregate
     * @param score the computed score
     * @param threshold the minimum acceptance threshold
     * @return SUCCESS if score >= threshold, BELOW_THRESHOLD otherwise
     */
    public static OptimizationRecord successOrBelowThreshold(
            OptimizationIterationAggregate aggregate, double score, double threshold) {
        if (score >= threshold) {
            return success(aggregate, score);
        }
        return belowThreshold(aggregate, score, threshold);
    }
}
