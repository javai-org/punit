package org.javai.punit.api.typed.spec;

import java.time.Duration;

import org.javai.punit.api.typed.UseCaseOutcome;

/**
 * Synchronous sink for individual sample observations. The
 * {@link SampleExecutor} pushes one event per completed sample; the
 * engine's sample-loop aggregator implements this interface.
 *
 * <p>There is one callback only. A successful invocation produces a
 * {@link UseCaseOutcome} whose inner {@link org.javai.outcome.Outcome}
 * is either an {@code Ok} (counted as a sample success) or a
 * {@code Fail} (counted as a sample failure). Defects — exceptions
 * thrown from {@code UseCase.apply} — are not reported here; they
 * bubble out of the executor and the engine, aborting the run.
 * A future exception-handling policy will let authors opt into
 * catching defects and treating them as sample failures; the default
 * stands for now.
 *
 * <p>Implementations must be thread-safe: a future concurrent
 * {@code SampleExecutor} will invoke this method from worker threads.
 * The shipped {@link SerialSampleExecutor} always invokes from the
 * calling thread, but the contract is specified in the strongest
 * form now so that replacement is a drop-in.
 *
 * @param <OT> the outcome value type
 */
public interface SampleObserver<OT> {

    /**
     * Invoked after the executor has invoked {@code useCase.apply(input)}
     * and the invocation returned normally (either with
     * {@link org.javai.outcome.Outcome.Ok Ok} or
     * {@link org.javai.outcome.Outcome.Fail Fail}).
     *
     * @param index the 0-based sample index
     * @param outcome the wrapped outcome
     * @param elapsed the wall-clock time the invocation took
     */
    void onSample(int index, UseCaseOutcome<OT> outcome, Duration elapsed);
}
