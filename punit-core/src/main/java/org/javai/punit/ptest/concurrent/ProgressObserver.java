package org.javai.punit.ptest.concurrent;

/**
 * Observes sample completion in arrival order for real-time progress reporting.
 *
 * <p>Called synchronously on the worker thread at deposit time. Implementations
 * must be lightweight — no long-running computations or network calls.
 *
 * <p>This is distinct from the ordered aggregation path ({@link ResultConsumer}),
 * which drains results in strict sequence order for correct statistical verdicts.
 * The observer sees results as they complete, regardless of sequence order.
 */
@FunctionalInterface
public interface ProgressObserver {

    /**
     * Called when a sample completes execution, before ordered aggregation.
     *
     * @param result the completed sample result
     */
    void onSampleCompleted(StagedResult result);
}
