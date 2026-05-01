package org.javai.punit.api.spec;

import java.time.Duration;
import java.util.Objects;

import org.javai.punit.api.UseCaseOutcome;

/**
 * One per-sample observation: the input that drove the call, the
 * outcome the use case returned (or that the engine synthesised
 * under {@link ExceptionPolicy#FAIL_SAMPLE}), and the wall-clock
 * duration of the invocation.
 *
 * <p>{@link SampleSummary#trials()} exposes the ordered sequence of
 * trials so history-sensitive criteria (collision rate, Shewhart
 * runs-rules, autocorrelation) can read the outcome stream rather
 * than only the aggregate counts a Bernoulli criterion is content
 * with.
 *
 * @param input    the per-sample input that drove this trial
 * @param outcome  the outcome the use case returned (success or fail)
 * @param duration wall-clock duration of the invocation
 * @param <IT>     the per-sample input type
 * @param <OT>     the per-sample outcome value type
 */
public record Trial<IT, OT>(IT input, UseCaseOutcome<IT, OT> outcome, Duration duration) {

    public Trial {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(duration, "duration");
    }
}
