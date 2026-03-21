package org.javai.punit.ptest.concurrent;

/**
 * A unit of work representing a single sample to execute.
 *
 * @param sequenceIndex 1-based index matching the sequential execution order
 * @param inputIndex    the input to use for this sample (sequenceIndex - 1) % inputCount,
 *                      or -1 if no @InputSource is configured
 */
public record SampleTask(int sequenceIndex, int inputIndex) {

    /** Sentinel task placed in the queue to signal workers to shut down. */
    public static final SampleTask POISON = new SampleTask(-1, -1);

    public boolean isPoison() {
        return sequenceIndex == -1;
    }
}
