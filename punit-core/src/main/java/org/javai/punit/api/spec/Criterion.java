package org.javai.punit.api.spec;

/**
 * A spec-level claim evaluated against the observed sample aggregate.
 *
 * <p>A criterion is concerned with one statistical model and therefore
 * one kind of baseline statistics. {@code PassRate} reads
 * pass-rate statistics; {@code PercentileLatency} reads latency
 * percentiles. Future criterion kinds add new {@link BaselineStatistics}
 * implementations without touching existing types.
 *
 * <p>Criteria compose on a
 * {@link ProbabilisticTest.Builder} via {@code .criterion(c)} (required,
 * contributes to the combined verdict) or {@code .reportOnly(c)}
 * (evaluated, attached to the result, excluded from composition).
 *
 * @param <OT> the outcome value type the sample loop produced
 * @param <S>  the baseline statistics kind the criterion reads
 */
public interface Criterion<OT, S extends BaselineStatistics> {

    /** Stable identifier used in reports and the composed result. */
    String name();

    /**
     * The statistics kind this criterion reads. The framework uses
     * this to route the right slice of the resolved baseline into the
     * criterion's evaluation context.
     *
     * <p>Criteria that read no baseline return
     * {@code NoStatistics.class}.
     */
    Class<S> statisticsType();

    /**
     * Evaluate this criterion against the observed sample summary and
     * its typed context. Called once per spec run, after the engine
     * has finished sampling. Must be a pure function of the context —
     * no side effects.
     */
    CriterionResult evaluate(EvaluationContext<OT, S> ctx);

    /**
     * @return {@code true} if the criterion's evaluation depends on a
     *         resolved baseline (an empirical claim against measured
     *         data); {@code false} for contractual claims with no
     *         baseline dependency. Default: {@code false}.
     *
     * <p>The framework consults this at build time to gate the
     * inline-sampling form on probabilistic tests: an empirical
     * criterion requires a {@link Sampling} value shared with the
     * paired measure (the structural integrity guarantee for the
     * empirical comparison), so the inline form — which constructs
     * a fresh {@link Sampling} per spec — is rejected with a
     * diagnostic teaching the helper-extraction pattern.
     */
    default boolean isEmpirical() {
        return false;
    }
}
