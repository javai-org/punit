package org.javai.punit.api.typed.spec;

import java.time.Duration;

import org.javai.punit.api.typed.UseCaseOutcome;

/**
 * Synchronous sink for individual sample observations. The
 * {@link SampleExecutor} pushes one event per completed sample; the
 * engine's sample-loop aggregator implements this interface.
 *
 * <p>Implementations must be thread-safe: a future concurrent
 * {@code SampleExecutor} will invoke these methods from worker
 * threads. The shipped {@link SerialSampleExecutor} always invokes
 * from the calling thread, but the contract is specified in the
 * strongest form now so that replacement is a drop-in.
 *
 * @param <OT> the outcome value type
 */
public interface SampleObserver<OT> {

    /**
     * Invoked after the executor has invoked {@code useCase.apply(input)}
     * and the invocation returned normally.
     *
     * @param index the 0-based sample index
     * @param outcome the wrapped outcome
     * @param elapsed the wall-clock time the invocation took
     */
    void onSample(int index, UseCaseOutcome<OT> outcome, Duration elapsed);

    /**
     * Invoked after the executor has invoked {@code useCase.apply(input)}
     * and the invocation threw.
     *
     * @param index the 0-based sample index
     * @param error the thrown {@link Throwable}
     * @param elapsed the wall-clock time the invocation took before
     *                throwing
     */
    void onError(int index, Throwable error, Duration elapsed);
}
