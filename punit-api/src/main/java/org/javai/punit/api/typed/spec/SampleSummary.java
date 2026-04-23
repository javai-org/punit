package org.javai.punit.api.typed.spec;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.javai.punit.api.typed.UseCaseOutcome;

/**
 * Per-configuration aggregate of observed samples. Produced by the
 * engine, consumed by the spec via {@link Spec#consume(Configuration, SampleSummary)}.
 *
 * <p>The {@code outcomes} list holds every sample in execution order.
 * Each entry's inner {@link org.javai.outcome.Outcome} is either an
 * {@code Ok} (counted as a success) or a {@code Fail} (counted as a
 * failure). The success / failure counts and pass rate are derived
 * from this list — single source of truth.
 *
 * @param outcomes the per-sample outcomes, in execution order
 * @param elapsed wall-clock time spent sampling this configuration
 * @param <OT> the outcome value type
 */
public record SampleSummary<OT>(List<UseCaseOutcome<OT>> outcomes, Duration elapsed) {

    public SampleSummary {
        Objects.requireNonNull(outcomes, "outcomes");
        Objects.requireNonNull(elapsed, "elapsed");
        outcomes = List.copyOf(outcomes);
    }

    public int total() {
        return outcomes.size();
    }

    public int successes() {
        int n = 0;
        for (UseCaseOutcome<OT> o : outcomes) {
            if (o.value().isOk()) n++;
        }
        return n;
    }

    public int failures() {
        return total() - successes();
    }

    /** Observed pass rate; {@code NaN} when no samples executed. */
    public double passRate() {
        int t = total();
        return t == 0 ? Double.NaN : (double) successes() / (double) t;
    }
}
