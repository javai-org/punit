package org.javai.punit.api.spec;

import java.util.Objects;

/**
 * Per-criterion sample-outcome counts across a run, keyed by the
 * criterion's stable identifier. One record per
 * {@link org.javai.punit.api.criterion.Criterion} the
 * {@link org.javai.punit.api.Contract} declares.
 *
 * <p>The denominator under today's marginal-totals policy is
 * {@link #total()} = {@link #pass()} + {@link #fail()} +
 * {@link #inconclusive()}; the per-criterion observed pass rate is
 * {@link #pass()} / {@link #total()}, with INCONCLUSIVE samples
 * contributing zero to the numerator. The methodology's "n_c = 0 →
 * INCONCLUSIVE" rule applies only when {@link #total()} is zero,
 * which under the marginal policy means the entire run had zero
 * samples and the feasibility gate has independently refused the
 * test.
 *
 * @param criterionId the criterion's stable identifier
 * @param pass count of samples whose per-criterion outcome was PASS
 * @param fail count of samples whose per-criterion outcome was FAIL
 * @param inconclusive count of samples whose per-criterion outcome
 *                     was INCONCLUSIVE (transform failure or other
 *                     evaluation gap)
 */
public record CriterionSampleCounts(
        String criterionId,
        int pass,
        int fail,
        int inconclusive) {

    public CriterionSampleCounts {
        Objects.requireNonNull(criterionId, "criterionId");
        if (pass < 0 || fail < 0 || inconclusive < 0) {
            throw new IllegalArgumentException(
                    "counts must be non-negative; got pass=" + pass
                            + ", fail=" + fail
                            + ", inconclusive=" + inconclusive);
        }
    }

    /** Sum of all per-criterion sample outcomes — the marginal denominator. */
    public int total() {
        return pass + fail + inconclusive;
    }

    /**
     * Observed pass rate under the marginal denominator policy:
     * {@link #pass()} / {@link #total()}. Returns {@code NaN} when
     * {@link #total()} is zero — callers should treat that as
     * INCONCLUSIVE per the methodology's $n_c = 0$ rule.
     */
    public double observedPassRate() {
        int t = total();
        return t == 0 ? Double.NaN : (double) pass / (double) t;
    }
}
