package org.javai.punit.engine;

import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.javai.punit.api.typed.spec.SampleExecutor;
import org.javai.punit.api.typed.spec.SampleObserver;

/**
 * One-at-a-time sample executor. Invokes {@code useCase.apply(input)}
 * on the calling thread and forwards outcomes along with the
 * wall-clock time taken. Consults the caller's early-stop predicate
 * between samples so the engine can enforce wall-clock and token
 * budgets.
 *
 * <p>Business-level failures are signalled by the use case returning
 * a {@link UseCaseOutcome} whose inner {@link org.javai.outcome.Outcome}
 * is a {@code Fail}; the executor just forwards those. Defects —
 * exceptions thrown from {@code apply} — propagate naturally out of
 * this method and terminate the run. A future slice wires them through
 * the observer's {@code onDefect} channel.
 *
 * <p>Pacing, concurrency, and retry are not handled here yet — they
 * are wired in later slices.
 */
public final class SerialSampleExecutor implements SampleExecutor {

    @Override
    public <FT, IT, OT> void runSamples(
            UseCase<FT, IT, OT> useCase,
            List<IT> inputs,
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

        for (int i = 0; i < sampleCount; i++) {
            if (stopRequested.getAsBoolean()) {
                return;
            }
            IT input = inputs.get((cycleStart + i) % inputs.size());
            long t0 = System.nanoTime();
            UseCaseOutcome<OT> outcome = useCase.apply(input);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - t0);
            observer.onSample(i, outcome, elapsed);
        }
    }
}
