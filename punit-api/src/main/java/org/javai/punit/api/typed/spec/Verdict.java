package org.javai.punit.api.typed.spec;

import java.util.List;
import java.util.Objects;

/**
 * Three-state statistical verdict returned by a
 * {@link ProbabilisticTest}'s {@link Spec#conclude() conclude()}
 * call.
 *
 * <p>Kept distinct from the legacy
 * {@code org.javai.punit.verdict.PUnitVerdict} used by the existing
 * reporting pipeline; Stage 8 reconciles the two.
 */
public enum Verdict {

    /** Observed pass rate meets or exceeds the threshold under the configured confidence. */
    PASS,

    /** Observed pass rate is below the threshold under the configured confidence. */
    FAIL,

    /** Covariate misalignment or statistical ambiguity prevents a confident verdict. */
    INCONCLUSIVE;

    /**
     * Compose an ordered list of evaluated criteria into a single
     * verdict. {@link CriterionRole#REPORT_ONLY REPORT_ONLY} entries
     * are filtered out; the contributing entries are combined by
     * three-valued logic:
     *
     * <ul>
     *   <li>Empty contributing list → {@link #PASS} (a spec with zero
     *       required criteria is trivially satisfied).</li>
     *   <li>Any contributing entry is {@link #INCONCLUSIVE} →
     *       {@link #INCONCLUSIVE}.</li>
     *   <li>Any contributing entry is {@link #FAIL} → {@link #FAIL}.</li>
     *   <li>Otherwise → {@link #PASS}.</li>
     * </ul>
     *
     * Pure function: same inputs, same output; order-independent.
     */
    public static Verdict compose(List<EvaluatedCriterion> evaluated) {
        Objects.requireNonNull(evaluated, "evaluated");
        boolean sawFail = false;
        boolean sawContributing = false;
        for (EvaluatedCriterion entry : evaluated) {
            Objects.requireNonNull(entry, "evaluated entry");
            if (entry.role() != CriterionRole.REQUIRED) {
                continue;
            }
            sawContributing = true;
            Verdict v = entry.result().verdict();
            if (v == INCONCLUSIVE) {
                return INCONCLUSIVE;
            }
            if (v == FAIL) {
                sawFail = true;
            }
        }
        if (!sawContributing) {
            return PASS;
        }
        return sawFail ? FAIL : PASS;
    }
}
