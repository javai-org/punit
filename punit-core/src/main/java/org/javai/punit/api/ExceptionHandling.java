package org.javai.punit.api;

/**
 * Defines how non-{@link AssertionError} exceptions thrown by the test method
 * should be handled.
 */
public enum ExceptionHandling {
    
    /**
     * Treat the exception as a failed sample. The exception is captured
     * for reporting but execution continues with the next sample.
     * This is the default behavior.
     */
    FAIL_SAMPLE,
    
    /**
     * Immediately abort the test with the exception. The test fails
     * and no further samples are executed. Use this when exceptions
     * indicate environmental issues that would cause all further
     * samples to fail.
     */
    ABORT_TEST
}

