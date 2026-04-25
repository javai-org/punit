package org.javai.punit.api.typed.spec;

import java.util.List;
import java.util.Objects;

/**
 * Three-state statistical verdict returned by a
 * {@link ProbabilisticTestSpec}'s {@link DataGenerationSpec#conclude() conclude()}
 * call.
 *
 * <p>Kept distinct from the legacy
 * {@code org.javai.punit.verdict.PunitVerdict} used by the existing
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
     * Project a two-dimensional verdict (functional + latency) onto
     * a single value, as chosen by the spec's
     * {@link VerdictDimension}.
     *
     * <p>Model-agnostic: the functional verdict argument can come
     * from any statistical model (Bernoulli, collision probability,
     * etc.) — this method only knows how to pick and combine.
     *
     * <ul>
     *   <li>{@link VerdictDimension#FUNCTIONAL} — returns the
     *       functional verdict unchanged.</li>
     *   <li>{@link VerdictDimension#LATENCY} — returns the latency
     *       verdict.</li>
     *   <li>{@link VerdictDimension#BOTH} — combines the two with
     *       three-valued logic: {@link #INCONCLUSIVE} if either is
     *       inconclusive, else {@link #FAIL} if either failed, else
     *       {@link #PASS}.</li>
     * </ul>
     */
    public static Verdict project(Verdict functional,
                                  LatencyVerdict latency,
                                  VerdictDimension dimension) {
        Objects.requireNonNull(functional, "functional");
        Objects.requireNonNull(latency, "latency");
        Objects.requireNonNull(dimension, "dimension");
        return switch (dimension) {
            case FUNCTIONAL -> functional;
            case LATENCY -> latency.verdict();
            case BOTH -> combineBoth(functional, latency.verdict());
        };
    }

    private static Verdict combineBoth(Verdict functional, Verdict latency) {
        if (functional == INCONCLUSIVE || latency == INCONCLUSIVE) {
            return INCONCLUSIVE;
        }
        if (functional == FAIL || latency == FAIL) {
            return FAIL;
        }
        return PASS;
    }

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
