package org.javai.punit.ptest.concurrent;

/**
 * Executes a single sample and returns the result.
 *
 * <p>This interface decouples the worker thread from the JUnit 5 invocation
 * mechanism. The punit-junit5 module provides an implementation that wraps
 * the existing {@code SampleExecutor} logic.
 */
@FunctionalInterface
public interface SampleInvoker {

    /**
     * Executes the sample identified by the given task.
     *
     * @param task the sample task containing sequence index and input index
     * @return the staged result of execution
     */
    StagedResult execute(SampleTask task);
}
