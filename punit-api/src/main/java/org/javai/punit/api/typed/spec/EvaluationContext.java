package org.javai.punit.api.typed.spec;

import java.util.Optional;

import org.javai.punit.api.typed.FactorBundle;

/**
 * The typed view a {@link Criterion} receives at evaluate time.
 *
 * <p>The type parameter {@code S} pins the baseline statistics kind
 * the criterion declared via {@link Criterion#statisticsType()}. The
 * {@link #baseline()} accessor returns exactly that kind, or empty
 * when no matching baseline was resolvable.
 *
 * @param <OT> the outcome value type the sampling loop produced
 * @param <S>  the baseline statistics kind the criterion reads
 */
public interface EvaluationContext<OT, S extends BaselineStatistics> {

    /** The observed sample aggregate for this spec run. */
    SampleSummary<OT> summary();

    /**
     * The baseline statistics of the criterion's declared kind, when
     * one was resolvable. Empty when the framework could not resolve
     * a matching baseline. Always empty when {@code S = NoStatistics}.
     */
    Optional<S> baseline();

    /** The bound factor bundle the spec was evaluated against. */
    FactorBundle factors();

    /**
     * The test's inputs identity — the
     * {@code Sampling.inputsIdentity()} of the {@code Sampling} this
     * spec was built from. Used by
     * {@link EmpiricalChecks#inputsIdentityMatch(String, String, String, java.util.Map)}
     * to verify cross-process that the test and its baseline drew
     * from the same input population.
     */
    String testInputsIdentity();

    /**
     * The inputs identity recorded by the resolved baseline, when
     * one was resolvable. Empty when the framework could not resolve
     * a matching baseline (in which case {@link #baseline()} is also
     * empty by construction).
     */
    Optional<String> baselineInputsIdentity();
}
