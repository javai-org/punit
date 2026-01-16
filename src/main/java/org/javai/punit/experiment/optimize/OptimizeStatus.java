package org.javai.punit.experiment.optimize;

/**
 * Status of an optimization iteration.
 */
public enum OptimizeStatus {

    /**
     * Iteration completed successfully: use case executed, outcomes aggregated, and scored.
     */
    SUCCESS,

    /**
     * Iteration failed during use case execution.
     */
    EXECUTION_FAILED,

    /**
     * Iteration completed execution but scoring failed.
     */
    SCORING_FAILED
}
