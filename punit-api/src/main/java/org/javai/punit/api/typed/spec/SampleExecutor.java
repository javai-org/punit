package org.javai.punit.api.typed.spec;

import java.util.List;

import org.javai.punit.api.typed.UseCase;

/**
 * How samples are dispatched to the use case's {@code apply()} method.
 *
 * <p>The engine delegates all sample invocation through this interface.
 * The shipped implementation — {@link SerialSampleExecutor} — invokes
 * samples one at a time on the calling thread and honours any pacing
 * configured on the use case. A future concurrent executor will
 * replace it in one place without editing the engine.
 *
 * <p>An executor does not make policy decisions (pass / fail / retry /
 * abort). It reports outcomes and errors via a
 * {@link SampleObserver}; the observer (the engine's aggregator)
 * decides what they mean.
 */
public interface SampleExecutor {

    /**
     * Runs {@code sampleCount} samples of {@code useCase}. Inputs are
     * walked round-robin, advancing the cycle even across successive
     * invocations (so a call with {@code cycleStart = k} reads input
     * {@code inputs.get((k) % inputs.size())} first).
     *
     * @param useCase the use case instance
     * @param inputs the inputs to cycle through (non-empty)
     * @param sampleCount the number of samples to run; must be non-negative
     * @param cycleStart the starting cycle index (so warmup can set this
     *                   to the count of warmup samples already run)
     * @param observer the sink for per-sample observations
     * @param <FT> the factor record type (unused at invocation time)
     * @param <IT> the input type
     * @param <OT> the outcome value type
     */
    <FT, IT, OT> void runSamples(
            UseCase<FT, IT, OT> useCase,
            List<IT> inputs,
            int sampleCount,
            int cycleStart,
            SampleObserver<OT> observer);
}
