package org.javai.punit.api.typed.spec;

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
    INCONCLUSIVE
}
