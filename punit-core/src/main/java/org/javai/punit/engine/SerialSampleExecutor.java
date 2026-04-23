package org.javai.punit.engine;

import java.time.Duration;
import java.util.List;

import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.javai.punit.api.typed.spec.SampleExecutor;
import org.javai.punit.api.typed.spec.SampleObserver;

/**
 * One-at-a-time sample executor. Invokes {@code useCase.apply(input)}
 * on the calling thread and forwards outcomes (or the {@link Throwable}
 * on failure) to the observer along with the wall-clock time taken.
 *
 * <p>Pacing, concurrency, and retry are not handled here — Stage 3
 * wires them through additional spec fields.
 */
public final class SerialSampleExecutor implements SampleExecutor {

    @Override
    public <FT, IT, OT> void runSamples(
            UseCase<FT, IT, OT> useCase,
            List<IT> inputs,
            int sampleCount,
            int cycleStart,
            SampleObserver<OT> observer) {

        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("inputs must be non-empty");
        }
        if (sampleCount < 0) {
            throw new IllegalArgumentException("sampleCount must be non-negative");
        }

        for (int i = 0; i < sampleCount; i++) {
            IT input = inputs.get((cycleStart + i) % inputs.size());
            long t0 = System.nanoTime();
            try {
                UseCaseOutcome<OT> outcome = useCase.apply(input);
                Duration elapsed = Duration.ofNanos(System.nanoTime() - t0);
                observer.onSample(i, outcome, elapsed);
            } catch (Throwable t) {
                Duration elapsed = Duration.ofNanos(System.nanoTime() - t0);
                observer.onError(i, t, elapsed);
            }
        }
    }
}
