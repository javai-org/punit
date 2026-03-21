package org.javai.punit.ptest.concurrent;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Shared work queue from which worker threads pull sample tasks.
 *
 * <p>Pre-populated with all sample tasks at construction time. Workers
 * call {@link #take()} to pull the next task. When execution should stop
 * (early termination, budget exhaustion), {@link #shutdown(int)} injects
 * poison pills to unblock waiting workers.
 */
public class SampleWorkQueue {

    private final BlockingQueue<SampleTask> queue;
    private final int totalSamples;

    /**
     * Creates a work queue populated with sample tasks.
     *
     * @param totalSamples number of samples to execute
     * @param inputCount   number of inputs for cycling (0 if no @InputSource)
     */
    public SampleWorkQueue(int totalSamples, int inputCount) {
        this.totalSamples = totalSamples;
        this.queue = new LinkedBlockingQueue<>();
        for (int i = 1; i <= totalSamples; i++) {
            int inputIndex = inputCount > 0 ? (i - 1) % inputCount : -1;
            queue.add(new SampleTask(i, inputIndex));
        }
    }

    /**
     * Pulls the next task, blocking if the queue is empty.
     *
     * @return the next sample task (or poison pill), or empty if interrupted
     */
    public Optional<SampleTask> take() {
        try {
            return Optional.of(queue.take());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /**
     * Injects poison pills to unblock and terminate all workers.
     *
     * @param workerCount the number of workers to signal
     */
    public void shutdown(int workerCount) {
        queue.clear();
        for (int i = 0; i < workerCount; i++) {
            queue.add(SampleTask.POISON);
        }
    }

    /** Returns the number of tasks remaining (including not-yet-pulled). */
    public int remaining() {
        return queue.size();
    }

    /** Returns the total number of samples this queue was created for. */
    public int totalSamples() {
        return totalSamples;
    }
}
