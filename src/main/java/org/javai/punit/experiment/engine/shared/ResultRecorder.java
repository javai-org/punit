package org.javai.punit.experiment.engine.shared;

import java.time.Duration;
import java.util.List;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.contract.PostconditionResult;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.punit.experiment.engine.ExperimentResultAggregator;
import org.junit.jupiter.api.extension.InvocationInterceptor;

/**
 * Executes a sample invocation with wall-clock timing and records the outcome
 * into an {@link ExperimentResultAggregator}.
 *
 * <p>This class is the single point responsible for:
 * <ol>
 *   <li>Measuring wall-clock execution time around {@code invocation.proceed()}</li>
 *   <li>Extracting the outcome from the {@link OutcomeCaptor}</li>
 *   <li>Routing success/failure into the aggregator with accurate timing</li>
 * </ol>
 *
 * <p>Wall-clock timing is measured by the framework (via {@code System.nanoTime()})
 * rather than relying on {@code UseCaseOutcome.executionTime()}, which depends on
 * how the user constructs the outcome and may not reflect the actual sample duration.
 */
public final class ResultRecorder {

    private ResultRecorder() {
        // Utility class
    }

    /**
     * Executes the invocation, measures wall-clock time, and records the result.
     *
     * <p>On success, the wall-clock duration is stored alongside the outcome.
     * On exception from {@code invocation.proceed()}, the exception is recorded
     * as a failure with no duration.
     *
     * @param invocation the JUnit invocation to execute
     * @param captor the outcome captor that the test method populates
     * @param aggregator the aggregator to record the result into
     * @throws Throwable only if the exception should propagate (currently all are caught)
     */
    public static void executeAndRecord(InvocationInterceptor.Invocation<Void> invocation,
                                        OutcomeCaptor captor,
                                        ExperimentResultAggregator aggregator) throws Throwable {
        try {
            long startNanos = System.nanoTime();
            invocation.proceed();
            Duration wallClock = Duration.ofNanos(System.nanoTime() - startNanos);
            recordResult(captor, aggregator, wallClock);
        } catch (Throwable e) {
            aggregator.recordException(e);
        }
    }

    /**
     * Records the outcome from the captor into the aggregator with the given
     * wall-clock duration.
     *
     * <p>Use this when timing has been measured externally and additional
     * post-invocation logic (e.g., projection building) requires the timing
     * and recording to be separate from {@link #executeAndRecord}.
     *
     * @param captor the outcome captor containing the recorded outcome
     * @param aggregator the aggregator to record results into
     * @param wallClockDuration the wall-clock duration measured by the framework
     */
    public static void recordResult(OutcomeCaptor captor, ExperimentResultAggregator aggregator,
                                    Duration wallClockDuration) {
        if (captor != null && captor.hasResult()) {
            UseCaseOutcome<?> outcome = captor.getContractOutcome();
            boolean success = outcome.allPostconditionsSatisfied();

            if (success) {
                aggregator.recordSuccess(outcome, wallClockDuration);
            } else {
                List<PostconditionResult> postconditions = outcome.evaluatePostconditions();
                String failureCategory = determineFailureCategory(postconditions);
                aggregator.recordFailure(outcome, failureCategory);
            }
        } else if (captor != null && captor.hasException()) {
            aggregator.recordException(captor.getException());
        }
        // If nothing was recorded, don't add anything to the aggregator
    }

    /**
     * Determines the failure category from postcondition results.
     *
     * @param postconditions the postcondition results
     * @return the description of the first failed postcondition, or "unknown"
     */
    private static String determineFailureCategory(List<PostconditionResult> postconditions) {
        if (postconditions != null) {
            for (PostconditionResult result : postconditions) {
                if (result.failed()) {
                    return result.description();
                }
            }
        }
        return "unknown";
    }
}
