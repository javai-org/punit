package org.javai.punit.api.typed.spec;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.javai.punit.api.typed.UseCaseOutcome;

/**
 * Per-configuration aggregate of observed samples. Produced by the
 * engine, consumed by the spec via {@link Spec#consume(Configuration, SampleSummary)}.
 *
 * <p>{@link #outcomes()} holds every sample in execution order. The
 * success / failure counts and token totals are cached at
 * construction time: {@link UseCaseOutcome#value()} is lazy, so
 * evaluating it once per sample here spares downstream consumers
 * from repeated re-evaluation.
 *
 * <p>Construct via {@link #from(List, Duration)} — the canonical
 * constructor is exposed but requires pre-computed counts.
 *
 * @param outcomes the per-sample outcomes, in execution order
 * @param elapsed wall-clock time spent sampling this configuration
 * @param successes pre-computed count of outcomes whose value is Ok
 * @param failures pre-computed count of outcomes whose value is Fail
 * @param tokensConsumed pre-computed sum of per-sample tokens
 * @param <OT> the outcome value type
 */
public record SampleSummary<OT>(
        List<UseCaseOutcome<OT>> outcomes,
        Duration elapsed,
        int successes,
        int failures,
        long tokensConsumed) {

    public SampleSummary {
        Objects.requireNonNull(outcomes, "outcomes");
        Objects.requireNonNull(elapsed, "elapsed");
        if (successes < 0 || failures < 0) {
            throw new IllegalArgumentException("counts must be non-negative");
        }
        if (tokensConsumed < 0) {
            throw new IllegalArgumentException("tokensConsumed must be non-negative");
        }
        if (successes + failures != outcomes.size()) {
            throw new IllegalArgumentException(
                    "successes + failures (" + successes + " + " + failures
                            + ") must equal outcomes count (" + outcomes.size() + ")");
        }
        outcomes = List.copyOf(outcomes);
    }

    /**
     * Eagerly evaluates each outcome once to tally successes, failures,
     * and tokens. The single evaluation per sample is the payoff of
     * the lazy-evaluator design: consumers of the summary read
     * pre-computed counts without re-running postconditions.
     */
    public static <OT> SampleSummary<OT> from(List<UseCaseOutcome<OT>> outcomes, Duration elapsed) {
        int s = 0, f = 0;
        long tokens = 0;
        for (UseCaseOutcome<OT> o : outcomes) {
            if (o.value().isOk()) s++;
            else f++;
            tokens += o.tokens();
        }
        return new SampleSummary<>(outcomes, elapsed, s, f, tokens);
    }

    public int total() {
        return successes + failures;
    }

    /** Observed pass rate; {@code NaN} when no samples executed. */
    public double passRate() {
        int t = total();
        return t == 0 ? Double.NaN : (double) successes / (double) t;
    }
}
