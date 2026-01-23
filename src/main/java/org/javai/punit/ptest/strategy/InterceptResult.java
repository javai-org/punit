package org.javai.punit.ptest.strategy;

import java.util.Optional;
import org.javai.punit.model.TerminationReason;

/**
 * Result of a strategy's intercept operation.
 *
 * <p>This record encapsulates the outcome of executing a single sample,
 * including whether the test should terminate early and any sample failure
 * that should be re-thrown.
 *
 * <p>The extension uses this result to:
 * <ul>
 *   <li>Determine if the test should terminate early</li>
 *   <li>Finalize the test if termination is requested</li>
 *   <li>Re-throw sample failures for IDE visibility</li>
 * </ul>
 *
 * @param shouldTerminate true if the test should terminate after this sample
 * @param terminationReason the reason for termination, if any
 * @param terminationDetails human-readable details about the termination
 * @param sampleFailure the sample failure to re-throw, if any (for IDE visibility)
 * @param shouldAbort true if the test should abort immediately (exception handling)
 * @param abortException the exception to throw when aborting
 */
public record InterceptResult(
        boolean shouldTerminate,
        TerminationReason terminationReason,
        String terminationDetails,
        Throwable sampleFailure,
        boolean shouldAbort,
        Throwable abortException
) {
    /**
     * Creates a result indicating the sample executed normally and the test should continue.
     */
    public static InterceptResult continueExecution() {
        return new InterceptResult(false, null, null, null, false, null);
    }

    /**
     * Creates a result indicating the sample executed normally but had a failure
     * that should be shown in the IDE.
     *
     * @param failure the sample failure to re-throw
     */
    public static InterceptResult continueWithFailure(Throwable failure) {
        return new InterceptResult(false, null, null, failure, false, null);
    }

    /**
     * Creates a result indicating the test should terminate.
     *
     * @param reason the termination reason
     * @param details human-readable details
     */
    public static InterceptResult terminate(TerminationReason reason, String details) {
        return new InterceptResult(true, reason, details, null, false, null);
    }

    /**
     * Creates a result indicating the test should terminate with a sample failure.
     *
     * @param reason the termination reason
     * @param details human-readable details
     * @param failure the sample failure to re-throw
     */
    public static InterceptResult terminateWithFailure(
            TerminationReason reason, String details, Throwable failure) {
        return new InterceptResult(true, reason, details, failure, false, null);
    }

    /**
     * Creates a result indicating the test should abort immediately.
     *
     * <p>Note: Abort uses null termination reason as this is an exceptional case
     * that bypasses normal termination handling.
     *
     * @param exception the exception to throw
     */
    public static InterceptResult abort(Throwable exception) {
        return new InterceptResult(true, null,
                "Test aborted due to exception", null, true, exception);
    }

    /**
     * Returns true if there is a sample failure to re-throw.
     */
    public boolean hasSampleFailure() {
        return sampleFailure != null;
    }

    /**
     * Returns the termination reason as an Optional.
     */
    public Optional<TerminationReason> getTerminationReason() {
        return Optional.ofNullable(terminationReason);
    }
}
