package org.javai.punit.experiment.engine.shared;

import java.time.Duration;
import java.util.List;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.contract.PostconditionResult;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.punit.experiment.engine.ExperimentResultAggregator;

/**
 * Records outcomes from an OutcomeCaptor into an ExperimentResultAggregator.
 *
 * <p>Handles success/failure determination and failure categorization based on
 * postcondition evaluation.
 */
public final class ResultRecorder {

    private ResultRecorder() {
        // Utility class
    }

    /**
     * Records the outcome from the captor into the aggregator, using wall-clock duration
     * measured by the framework around the sample invocation.
     *
     * <p>The wall-clock duration is preferred over {@code outcome.executionTime()} because
     * the latter depends on how the user constructs the outcome and may not reflect the
     * actual time spent executing the sample.
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
     * Records the outcome from the captor into the aggregator.
     *
     * <p>Uses the outcome's own {@code executionTime()} for duration tracking.
     * Prefer {@link #recordResult(OutcomeCaptor, ExperimentResultAggregator, Duration)}
     * when wall-clock timing is available.
     *
     * @param captor the outcome captor containing the recorded outcome
     * @param aggregator the aggregator to record results into
     */
    public static void recordResult(OutcomeCaptor captor, ExperimentResultAggregator aggregator) {
        if (captor != null && captor.hasResult()) {
            UseCaseOutcome<?> outcome = captor.getContractOutcome();
            boolean success = outcome.allPostconditionsSatisfied();

            if (success) {
                aggregator.recordSuccess(outcome);
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
