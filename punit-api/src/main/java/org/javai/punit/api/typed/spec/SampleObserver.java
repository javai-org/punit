package org.javai.punit.api.typed.spec;

import java.time.Duration;

import org.javai.punit.api.typed.UseCaseOutcome;

/**
 * Synchronous sink for individual sample observations. The
 * {@link SampleExecutor} pushes one event per completed sample; the
 * engine's sample-loop aggregator implements this interface.
 *
 * <p>Two event channels: {@link #onSample} fires when the use case
 * returns normally (with {@link org.javai.outcome.Outcome.Ok Ok} or
 * {@link org.javai.outcome.Outcome.Fail Fail}); {@link #onDefect}
 * fires when the use case throws. The default {@code onDefect}
 * rethrows — preserving the {@code ABORT_TEST} semantics — but the
 * engine's aggregator overrides it for the spec's exception policy.
 *
 * <p>Implementations must be thread-safe: a future concurrent
 * {@link SampleExecutor} will invoke these methods from worker
 * threads. The shipped serial executor always invokes from the
 * calling thread, but the contract is specified in the strongest
 * form now so that replacement is a drop-in.
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
     * Invoked when {@code useCase.apply(input)} throws. The default
     * rethrows — aborting the run, which is the spec-level default
     * under the typed authoring model. The engine's aggregator
     * overrides this to implement
     * {@link ExceptionPolicy#FAIL_SAMPLE} when the spec opts in.
     *
     * <p>{@link java.lang.Error} subtypes must always propagate,
     * regardless of policy — overrides should re-check and rethrow
     * {@code Error}s.
     *
     * @param index the 0-based sample index
     * @param throwable the thrown defect
     * @param elapsed the wall-clock time the invocation took before
     *                throwing
     */
    default void onDefect(int index, Throwable throwable, Duration elapsed) {
        if (throwable instanceof RuntimeException re) {
            throw re;
        }
        if (throwable instanceof Error e) {
            throw e;
        }
        throw new RuntimeException(throwable);
    }
}
