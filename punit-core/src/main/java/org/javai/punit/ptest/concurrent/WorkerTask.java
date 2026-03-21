package org.javai.punit.ptest.concurrent;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runnable executed by each worker virtual thread.
 *
 * <p>Each worker pulls tasks from the shared work queue, executes the sample
 * via a provided {@link SampleInvoker}, and deposits the result into the
 * staging area. Workers continue until they receive a poison pill, the stop
 * signal is set, or the queue is exhausted.
 *
 * <p>Workers are decoupled from aggregation policy — they know nothing about
 * pass rates, early termination, or budgets. They execute and deposit.
 */
public class WorkerTask implements Runnable {

    private final SampleWorkQueue workQueue;
    private final ResultStaging staging;
    private final SampleInvoker invoker;
    private final AtomicBoolean stopSignal;

    /**
     * @param workQueue  shared queue to pull tasks from
     * @param staging    staging area to deposit results into
     * @param invoker    callback that executes a single sample
     * @param stopSignal shared flag set by the consumer to stop workers
     */
    public WorkerTask(SampleWorkQueue workQueue,
                      ResultStaging staging,
                      SampleInvoker invoker,
                      AtomicBoolean stopSignal) {
        this.workQueue = workQueue;
        this.staging = staging;
        this.invoker = invoker;
        this.stopSignal = stopSignal;
    }

    @Override
    public void run() {
        while (!stopSignal.get()) {
            Optional<SampleTask> taskOutcome = workQueue.take();
            if (taskOutcome.isEmpty()) {
                break;
            }
            SampleTask task = taskOutcome.get();
            if (task.isPoison()) {
                break;
            }
            if (stopSignal.get()) {
                break;
            }

            StagedResult result = invoker.execute(task);
            staging.deposit(result);

            if (result.shouldAbort()) {
                stopSignal.set(true);
                break;
            }
        }
    }
}
