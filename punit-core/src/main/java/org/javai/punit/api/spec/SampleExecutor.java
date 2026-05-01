package org.javai.punit.api.spec;

import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import org.javai.punit.api.UseCase;
import org.javai.punit.api.ValueMatcher;

/**
 * How samples are dispatched to the use case's {@code apply()} method.
 *
 * <p>The engine delegates all sample invocation through this interface.
 * The shipped implementation — {@code SerialSampleExecutor} in
 * punit-core — invokes samples one at a time on the calling thread.
 * A future concurrent executor will replace it in one place without
 * editing the engine.
 *
 * <p>An executor does not make policy decisions (pass / fail / retry /
 * abort). It reports outcomes and errors via a
 * {@link SampleObserver}; the observer (the engine's aggregator)
 * decides what they mean. The executor does honour a caller-supplied
 * early-stop predicate — the engine uses this to enforce wall-clock
 * and token budgets.
 */
public interface SampleExecutor {

    /**
     * Runs up to {@code sampleCount} samples of {@code useCase}.
     * Inputs are walked round-robin, advancing the cycle even across
     * successive invocations (so a call with {@code cycleStart = k}
     * reads input {@code inputs.get((k) % inputs.size())} first).
     *
     * <p>When {@code expected} is non-empty and {@code matcher} is
     * present, the executor dispatches to the use case's matching
     * {@code apply} form (form 3) per sample, pairing each input
     * with its expected value. Otherwise it dispatches to the
     * input-only form (form 1).
     *
     * <p>Before each sample, the executor consults {@code stopRequested}
     * and halts when it returns true.
     *
     * @param useCase the use case instance
     * @param inputs the inputs to cycle through (non-empty)
     * @param expected the expected values paired with inputs by
     *                 round-robin index; empty when no
     *                 instance-conformance matching is configured.
     *                 When non-empty, must be the same length as
     *                 {@code inputs}
     * @param matcher the matcher to use against {@code expected};
     *                must be present iff {@code expected} is non-empty
     * @param sampleCount the upper bound on samples to run; must be
     *                    non-negative
     * @param cycleStart the starting cycle index (so warmup can set this
     *                   to the count of warmup samples already run)
     * @param observer the sink for per-sample observations
     * @param stopRequested early-stop predicate consulted between
     *                      samples. Never null.
     * @param <FT> the factor record type (unused at invocation time)
     * @param <IT> the input type
     * @param <OT> the outcome value type
     */
    <FT, IT, OT> void runSamples(
            UseCase<FT, IT, OT> useCase,
            List<IT> inputs,
            List<OT> expected,
            Optional<ValueMatcher<OT>> matcher,
            int sampleCount,
            int cycleStart,
            SampleObserver<OT> observer,
            BooleanSupplier stopRequested);
}
