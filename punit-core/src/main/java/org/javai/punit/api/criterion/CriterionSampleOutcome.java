package org.javai.punit.api.criterion;

/**
 * The three-valued outcome of evaluating one criterion against one
 * sample's produced value.
 *
 * <p>A criterion partitions the functional dimension into one
 * independently-evaluated statistical stream. For a single sample,
 * that evaluation lands in exactly one of:
 *
 * <ul>
 *   <li>{@link #PASS} — the criterion's postcondition chain ran to
 *       completion and every postcondition passed.</li>
 *   <li>{@link #FAIL} — the criterion's postcondition chain ran to
 *       completion and at least one postcondition failed.</li>
 *   <li>{@link #INCONCLUSIVE} — the criterion's transform (if any)
 *       returned {@code Outcome.Fail} or threw, so the postcondition
 *       chain was not evaluated. The sample is undefined under this
 *       criterion's predicate; the per-trial indicator is neither 0
 *       nor 1.</li>
 * </ul>
 *
 * <p>The criterion-aggregate verdict (PASS / FAIL / INCONCLUSIVE for
 * the whole criterion across all samples) is a separate concept built
 * from a sequence of per-sample outcomes by an aggregation policy.
 * This enum captures only the per-sample case.
 */
public enum CriterionSampleOutcome {
    PASS,
    FAIL,
    INCONCLUSIVE
}
