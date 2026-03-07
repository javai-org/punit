package org.javai.punit.experiment.optimize;

/**
 * Status of an optimization iteration.
 */
public enum OptimizeStatus {

    /**
     * Iteration completed successfully with acceptable score.
     */
    SUCCESS,

    /**
     * Iteration completed but score is below the minimum acceptance threshold.
     * The iteration executed and scored correctly, but the configuration
     * doesn't meet minimum quality standards.
     */
    BELOW_THRESHOLD,

    /**
     * Iteration failed during use case execution.
     */
    EXECUTION_FAILED,

    /**
     * Iteration completed execution but scoring failed.
     */
    SCORING_FAILED
}
