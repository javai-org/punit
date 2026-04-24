package org.javai.punit.api.typed.spec;

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

    /**
     * Project a functional-only verdict — for the case where no
     * latency verdict is available (spec declared no latency
     * thresholds, or the run produced no samples to compute them
     * from).
     *
     * <p>The latency side is treated as vacuously {@link #PASS}
     * under this overload: nothing was asserted about latency, so
     * nothing failed it.
     *
     * <ul>
     *   <li>{@link VerdictDimension#FUNCTIONAL} — returns the
     *       functional verdict unchanged.</li>
     *   <li>{@link VerdictDimension#LATENCY} — returns {@link #PASS}
     *       (nothing asserted on this axis).</li>
     *   <li>{@link VerdictDimension#BOTH} — returns the functional
     *       verdict (the latency side vacuously passes, so the
     *       combined verdict is determined by the functional side
     *       alone).</li>
     * </ul>
     */
    public static Verdict project(Verdict functional, VerdictDimension dimension) {
        Objects.requireNonNull(functional, "functional");
        Objects.requireNonNull(dimension, "dimension");
        return switch (dimension) {
            case FUNCTIONAL, BOTH -> functional;
            case LATENCY -> PASS;
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
}
