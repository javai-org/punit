package org.javai.punit.api.typed.spec;

/**
 * Why a configuration's sample loop stopped.
 *
 * <p>Attached to {@link SampleSummary} so the spec's
 * {@link DataGenerationSpec#consume(Configuration, SampleSummary)} callback and the
 * downstream reporters can distinguish a naturally-completed run from
 * one that exhausted a budget early.
 */
public enum TerminationReason {

    /** All requested samples completed. */
    COMPLETED,

    /** Wall-clock time budget reached before all samples completed. */
    TIME_BUDGET,

    /** Token budget reached before all samples completed. */
    TOKEN_BUDGET
}
