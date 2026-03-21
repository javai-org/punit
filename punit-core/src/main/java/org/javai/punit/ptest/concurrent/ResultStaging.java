package org.javai.punit.ptest.concurrent;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe staging area for sample results awaiting ordered consumption.
 *
 * <p>Workers deposit results at arbitrary times (keyed by sequence index).
 * The consumer calls {@link #drainNext()} to retrieve results in strict
 * sequence order (1, 2, 3, ...). If the next expected result is not yet
 * available, {@link #drainNext()} returns empty.
 *
 * <p>An optional {@link ProgressObserver} is notified at deposit time,
 * in arrival order, for real-time IDE feedback. This is independent of
 * the ordered aggregation path.
 *
 * <p>For blocking drain semantics, use {@link #awaitNext(long)} which
 * polls with a timeout.
 */
public class ResultStaging {

    private final ConcurrentHashMap<Integer, StagedResult> results = new ConcurrentHashMap<>();
    private final AtomicInteger nextExpected = new AtomicInteger(1);
    private final ProgressObserver observer;

    /**
     * Creates a staging area without progress observation.
     */
    public ResultStaging() {
        this(null);
    }

    /**
     * Creates a staging area with an optional progress observer.
     *
     * @param observer called on the worker thread at deposit time (may be null)
     */
    public ResultStaging(ProgressObserver observer) {
        this.observer = observer;
    }

    /**
     * Deposits a completed result. Called by worker threads.
     *
     * <p>If a {@link ProgressObserver} is configured, it is notified
     * synchronously on the calling (worker) thread.
     *
     * @param result the staged result to deposit
     */
    public void deposit(StagedResult result) {
        results.put(result.sequenceIndex(), result);
        if (observer != null) {
            observer.onSampleCompleted(result);
        }
    }

    /**
     * Attempts to drain the next result in sequence order.
     *
     * @return the next sequential result, or empty if not yet available
     */
    public Optional<StagedResult> drainNext() {
        int expected = nextExpected.get();
        StagedResult result = results.remove(expected);
        if (result != null) {
            nextExpected.incrementAndGet();
            return Optional.of(result);
        }
        return Optional.empty();
    }

    /**
     * Blocks until the next sequential result is available or timeout expires.
     *
     * @param timeoutMs maximum time to wait in milliseconds
     * @return the next sequential result, or empty if timeout expired or interrupted
     */
    public Optional<StagedResult> awaitNext(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Optional<StagedResult> result = drainNext();
            if (result.isPresent()) {
                return result;
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** Returns the next expected sequence index. */
    public int nextExpectedIndex() {
        return nextExpected.get();
    }

    /** Returns the number of results currently staged but not yet drained. */
    public int pendingCount() {
        return results.size();
    }
}
