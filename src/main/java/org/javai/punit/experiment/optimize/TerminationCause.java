package org.javai.punit.experiment.optimize;

/**
 * Cause of optimization termination.
 */
public enum TerminationCause {

    /**
     * Maximum iteration count reached.
     */
    MAX_ITERATIONS,

    /**
     * No improvement for the configured window of iterations.
     */
    NO_IMPROVEMENT,

    /**
     * Time budget exhausted.
     */
    TIME_BUDGET_EXHAUSTED,

    /**
     * Token budget exhausted.
     */
    TOKEN_BUDGET_EXHAUSTED,

    /**
     * Target score threshold reached (early success).
     */
    SCORE_THRESHOLD_REACHED,

    /**
     * Mutator failed to produce a valid new value.
     */
    MUTATION_FAILURE,

    /**
     * Scorer failed to evaluate an iteration.
     */
    SCORING_FAILURE
}
