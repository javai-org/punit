package org.javai.punit.api.typed.spec;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.javai.punit.api.typed.UseCaseOutcome;

/**
 * Per-configuration aggregate of observed samples. Produced by the
 * engine, consumed by the spec via {@link Spec#consume(Configuration, SampleSummary)}.
 *
 * <p>{@code outcomes} carries only the successful invocations (the
 * ones that returned a value); {@code errors} carries only the failing
 * ones (where the invocation threw). The two lists are therefore of
 * length {@code successes} and {@code failures} respectively.
 *
 * @param successes the number of samples counted as successes
 * @param failures the number of samples counted as failures
 * @param outcomes the successful samples' wrapped outcomes, in
 *                 execution order
 * @param errors the failing samples' exceptions, in execution order
 * @param elapsed wall-clock time spent sampling this configuration
 * @param <OT> the outcome value type
 */
public record SampleSummary<OT>(
        int successes,
        int failures,
        List<UseCaseOutcome<OT>> outcomes,
        List<Throwable> errors,
        Duration elapsed) {

    public SampleSummary {
        Objects.requireNonNull(outcomes, "outcomes");
        Objects.requireNonNull(errors, "errors");
        Objects.requireNonNull(elapsed, "elapsed");
        if (successes < 0 || failures < 0) {
            throw new IllegalArgumentException("successes and failures must be non-negative");
        }
        if (outcomes.size() != successes) {
            throw new IllegalArgumentException(
                    "outcomes size (" + outcomes.size() + ") must equal successes (" + successes + ")");
        }
        if (errors.size() != failures) {
            throw new IllegalArgumentException(
                    "errors size (" + errors.size() + ") must equal failures (" + failures + ")");
        }
        outcomes = List.copyOf(outcomes);
        errors = List.copyOf(errors);
    }

    /** Total samples observed. */
    public int total() {
        return successes + failures;
    }

    /** Observed pass rate; {@code NaN} when no samples executed. */
    public double passRate() {
        int t = total();
        return t == 0 ? Double.NaN : (double) successes / (double) t;
    }
}
