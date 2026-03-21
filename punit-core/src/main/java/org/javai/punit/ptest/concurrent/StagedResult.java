package org.javai.punit.ptest.concurrent;

/**
 * A completed sample result keyed by its sequence index for ordered aggregation.
 *
 * <p>This record carries everything the consumer needs to feed into the aggregator
 * and budget monitors, eliminating any need for the consumer to reach back into
 * thread-local state.
 *
 * @param sequenceIndex     the 1-based sequence index from the original SampleTask
 * @param passed            true if the sample passed
 * @param failure           the exception if sample failed, null otherwise
 * @param shouldAbort       true if test should abort immediately (ABORT_TEST policy triggered)
 * @param abortException    the exception to rethrow if aborting
 * @param latencyMs         wall-clock execution time in milliseconds
 * @param functionalPassed  functional dimension result (null if not asserted)
 * @param latencyPassed     latency dimension result (null if not asserted)
 * @param dynamicTokens     tokens consumed in dynamic token mode (0 if not applicable)
 */
public record StagedResult(
        int sequenceIndex,
        boolean passed,
        Throwable failure,
        boolean shouldAbort,
        Throwable abortException,
        long latencyMs,
        Boolean functionalPassed,
        Boolean latencyPassed,
        long dynamicTokens
) {

    /** Creates a staged result for a successful sample. */
    public static StagedResult ofSuccess(int sequenceIndex, long latencyMs,
                                         Boolean functionalPassed, Boolean latencyPassed,
                                         long dynamicTokens) {
        return new StagedResult(sequenceIndex, true, null, false, null,
                latencyMs, functionalPassed, latencyPassed, dynamicTokens);
    }

    /** Creates a staged result for a failed sample. */
    public static StagedResult ofFailure(int sequenceIndex, Throwable failure,
                                         Boolean functionalPassed, Boolean latencyPassed,
                                         long dynamicTokens) {
        return new StagedResult(sequenceIndex, false, failure, false, null,
                0, functionalPassed, latencyPassed, dynamicTokens);
    }

    /** Creates a staged result for an aborted sample. */
    public static StagedResult ofAbort(int sequenceIndex, Throwable exception) {
        return new StagedResult(sequenceIndex, false, exception, true, exception,
                0, null, null, 0);
    }
}
