package org.javai.punit.engine;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import org.javai.punit.api.typed.Pacing;
import org.javai.punit.api.typed.TokenTracker;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.javai.punit.api.typed.ValueMatcher;
import org.javai.punit.api.typed.spec.SampleExecutor;
import org.javai.punit.api.typed.spec.SampleObserver;

/**
 * One-at-a-time sample executor. Invokes
 * {@code useCase.apply(input, tracker)} on the calling thread and
 * forwards outcomes along with the wall-clock time taken. Consults
 * the caller's early-stop predicate between samples so the engine
 * can enforce wall-clock and token budgets, and honours the use
 * case's declared {@link Pacing} between samples.
 *
 * <p>Constructs one {@link TokenTracker} per {@code runSamples} call
 * — that tracker spans every sample in the call, accumulating cost
 * across the whole run. The contract's {@code apply} default diffs
 * the tracker's total before and after each {@code invoke} to derive
 * the per-sample cost recorded on the outcome.
 *
 * <h2>Pacing behaviour</h2>
 *
 * <p>Pacing is read from {@link UseCase#pacing()} once per call —
 * pacing is a service-level property, so a single {@code runSamples}
 * call sees a consistent limit. The pacing delay is inserted
 * <em>between</em> samples, so the first sample dispatches immediately
 * and subsequent samples wait
 * {@link Pacing#effectiveMinDelayMillis()} millis after the previous
 * sample started before dispatching. The sleep occurs on the calling
 * thread and therefore counts against any wall-clock budget the
 * engine is enforcing.
 *
 * <p>{@code maxConcurrent} is informational here — a serial executor
 * always runs at concurrency 1, which is &le; any positive cap.
 *
 * <p>Business-level failures are signalled by the use case returning
 * a {@link UseCaseOutcome} whose inner {@link org.javai.outcome.Outcome}
 * is a {@code Fail}; the executor just forwards those. Defects —
 * exceptions thrown from {@code apply} — are routed through the
 * observer's {@code onDefect} channel, which the engine's aggregator
 * uses to apply the spec's
 * {@link org.javai.punit.api.typed.spec.ExceptionPolicy}. The
 * executor itself makes no policy decisions.
 */
public final class SerialSampleExecutor implements SampleExecutor {

    @Override
    public <FT, IT, OT> void runSamples(
            UseCase<FT, IT, OT> useCase,
            List<IT> inputs,
            List<OT> expected,
            Optional<ValueMatcher<OT>> matcher,
            int sampleCount,
            int cycleStart,
            SampleObserver<OT> observer,
            BooleanSupplier stopRequested) {

        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("inputs must be non-empty");
        }
        if (sampleCount < 0) {
            throw new IllegalArgumentException("sampleCount must be non-negative");
        }
        if (!expected.isEmpty() && matcher.isEmpty()) {
            throw new IllegalArgumentException(
                    "matcher is required when expected outputs are supplied");
        }

        Pacing pacing = useCase.pacing();
        long minDelayMillis = pacing.effectiveMinDelayMillis();
        TokenTracker tracker = new InMemoryTokenTracker();
        boolean matching = !expected.isEmpty();

        for (int i = 0; i < sampleCount; i++) {
            if (stopRequested.getAsBoolean()) {
                return;
            }
            if (i > 0 && minDelayMillis > 0) {
                sleep(minDelayMillis);
                if (stopRequested.getAsBoolean()) {
                    return;
                }
            }
            int inputIndex = (cycleStart + i) % inputs.size();
            IT input = inputs.get(inputIndex);
            long t0 = System.nanoTime();
            UseCaseOutcome<IT, OT> outcome;
            try {
                if (matching) {
                    OT expectedValue = expected.get(inputIndex % expected.size());
                    outcome = useCase.apply(input, expectedValue, matcher.get(), tracker);
                } else {
                    outcome = useCase.apply(input, tracker);
                }
            } catch (Throwable t) {
                Duration elapsed = Duration.ofNanos(System.nanoTime() - t0);
                observer.onDefect(i, t, elapsed);
                continue;
            }
            Duration elapsed = Duration.ofNanos(System.nanoTime() - t0);
            observer.onSample(i, outcome, elapsed);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("pacing sleep interrupted", ie);
        }
    }
}
