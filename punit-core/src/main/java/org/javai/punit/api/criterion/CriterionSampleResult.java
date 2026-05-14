package org.javai.punit.api.criterion;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.javai.outcome.Outcome;
import org.javai.punit.api.PostconditionResult;

/**
 * The full per-sample evaluation record for one criterion: the
 * three-valued outcome, the per-postcondition results when the chain
 * ran, and the reason the chain did not when the sample is
 * INCONCLUSIVE.
 *
 * <p>Constructed by a {@link Criterion#evaluate(Object)} call. The
 * record is the single contract between a criterion and downstream
 * consumers (verdict path, reports, sentinel) — anything those
 * consumers need about the criterion's behaviour on this sample
 * rides here.
 *
 * <p>An INCONCLUSIVE sample carries a {@code reason} — an
 * {@link Outcome.Fail} whose symbolic name names the kind of
 * undefined-evaluation that occurred. Today's only source is a
 * transform failure (the criterion's pre-postcondition transform
 * returned {@code Outcome.Fail} or threw); later steps may
 * introduce other sources (availability-gate failure, apply-level
 * failure) without changing this record's shape.
 *
 * @param criterionId  the criterion's stable identifier, copied onto
 *                     the result for downstream addressing
 * @param outcome      the per-sample outcome
 * @param postconditionResults the per-postcondition results when the
 *                     chain ran (empty when {@code outcome ==
 *                     INCONCLUSIVE} — no postcondition was reached)
 * @param reason       the reason the chain did not run when
 *                     {@code outcome == INCONCLUSIVE} (empty
 *                     otherwise). Preserves the failure name and
 *                     message for diagnostics.
 */
public record CriterionSampleResult(
        String criterionId,
        CriterionSampleOutcome outcome,
        List<PostconditionResult> postconditionResults,
        Optional<Outcome.Fail<?>> reason) {

    public CriterionSampleResult {
        Objects.requireNonNull(criterionId, "criterionId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(postconditionResults, "postconditionResults");
        Objects.requireNonNull(reason, "reason");
        postconditionResults = List.copyOf(postconditionResults);

        if (outcome == CriterionSampleOutcome.INCONCLUSIVE) {
            if (reason.isEmpty()) {
                throw new IllegalArgumentException(
                        "INCONCLUSIVE result must carry a reason");
            }
            if (!postconditionResults.isEmpty()) {
                throw new IllegalArgumentException(
                        "INCONCLUSIVE result must not carry postcondition results");
            }
        } else if (reason.isPresent()) {
            throw new IllegalArgumentException(
                    "Non-INCONCLUSIVE result must not carry a reason");
        }
    }

    public static CriterionSampleResult pass(
            String criterionId, List<PostconditionResult> results) {
        return new CriterionSampleResult(
                criterionId, CriterionSampleOutcome.PASS, results, Optional.empty());
    }

    public static CriterionSampleResult fail(
            String criterionId, List<PostconditionResult> results) {
        return new CriterionSampleResult(
                criterionId, CriterionSampleOutcome.FAIL, results, Optional.empty());
    }

    public static CriterionSampleResult inconclusive(
            String criterionId, Outcome.Fail<?> reason) {
        Objects.requireNonNull(reason, "reason");
        return new CriterionSampleResult(
                criterionId,
                CriterionSampleOutcome.INCONCLUSIVE,
                List.of(),
                Optional.of(reason));
    }
}
