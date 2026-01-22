package org.javai.punit.experiment.engine.shared;

import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.contract.PostconditionResult;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.punit.experiment.engine.ExperimentResultAggregator;
import org.javai.punit.model.CriterionOutcome;
import org.javai.punit.model.UseCaseCriteria;
import org.javai.punit.model.UseCaseResult;

import java.util.List;

/**
 * Records outcomes from an OutcomeCaptor into an ExperimentResultAggregator.
 *
 * <p>Handles success/failure determination and failure categorization.
 */
public final class ResultRecorder {

    private ResultRecorder() {
        // Utility class
    }

    /**
     * Records the outcome from the captor into the aggregator.
     *
     * <p>Success is determined in priority order:
     * <ol>
     *   <li>If a contract outcome is recorded, use {@code allPostconditionsPassed()}</li>
     *   <li>If legacy criteria are recorded, use {@code criteria.allPassed()}</li>
     *   <li>Otherwise, fall back to legacy heuristics</li>
     * </ol>
     */
    @SuppressWarnings("deprecation")
    public static void recordResult(OutcomeCaptor captor, ExperimentResultAggregator aggregator) {
        if (captor != null && captor.hasResult()) {
            // Prefer contract outcomes - use directly without conversion
            if (captor.hasContractOutcome()) {
                UseCaseOutcome<?> outcome = captor.getContractOutcome();
                boolean success = outcome.allPostconditionsSatisfied();

                if (success) {
                    aggregator.recordSuccess(outcome);
                } else {
                    List<PostconditionResult> postconditions = outcome.evaluatePostconditions();
                    String failureCategory = determineFailureCategoryFromPostconditions(postconditions);
                    aggregator.recordFailure(outcome, failureCategory);
                }
            } else if (captor.hasCriteria()) {
                // Legacy path: use criteria (requires UseCaseResult)
                UseCaseResult result = captor.getResult();
                boolean success = captor.getCriteria().allPassed();
                recordCriteriaLegacy(aggregator, captor.getCriteria());

                if (success) {
                    aggregator.recordSuccess(result);
                } else {
                    String failureCategory = determineFailureCategory(result, captor.getCriteria());
                    aggregator.recordFailure(result, failureCategory);
                }
            } else {
                // No criteria: use heuristics (requires UseCaseResult)
                UseCaseResult result = captor.getResult();
                boolean success = determineSuccess(result);
                if (success) {
                    aggregator.recordSuccess(result);
                } else {
                    String failureCategory = determineFailureCategory(result, null);
                    aggregator.recordFailure(result, failureCategory);
                }
            }
        } else if (captor != null && captor.hasException()) {
            aggregator.recordException(captor.getException());
        } else {
            aggregator.recordSuccess(UseCaseResult.builder().value("recorded", false).build());
        }
    }

    /**
     * Determines success from common result indicators.
     */
    private static boolean determineSuccess(UseCaseResult result) {
        if (result.hasValue("success")) {
            return result.getBoolean("success", false);
        }
        if (result.hasValue("isSuccess")) {
            return result.getBoolean("isSuccess", false);
        }
        if (result.hasValue("passed")) {
            return result.getBoolean("passed", false);
        }
        if (result.hasValue("isValid")) {
            return result.getBoolean("isValid", false);
        }
        if (result.hasValue("isValidJson")) {
            return result.getBoolean("isValidJson", false);
        }
        if (result.hasValue("error")) {
            return result.getValue("error", Object.class).isEmpty();
        }

        // Default to success if no failure indicators
        return true;
    }

    /**
     * Records criteria using the deprecated method (suppresses warning).
     */
    @SuppressWarnings("deprecation")
    private static void recordCriteriaLegacy(ExperimentResultAggregator aggregator, UseCaseCriteria criteria) {
        aggregator.recordCriteria(criteria);
    }

    /**
     * Determines the failure category from postcondition results.
     */
    private static String determineFailureCategoryFromPostconditions(List<PostconditionResult> postconditions) {
        if (postconditions != null) {
            for (PostconditionResult result : postconditions) {
                if (result.failed()) {
                    return result.description();
                }
            }
        }
        return "unknown";
    }

    /**
     * Determines the failure category from criteria or result.
     */
    private static String determineFailureCategory(UseCaseResult result, UseCaseCriteria criteria) {
        // If criteria are available, derive failure category from first failed criterion
        if (criteria != null) {
            for (CriterionOutcome outcome : criteria.evaluate()) {
                if (!outcome.passed()) {
                    return outcome.description();
                }
            }
        }

        // Legacy: check for common failure category indicators in result
        if (result.hasValue("failureCategory")) {
            return result.getString("failureCategory", "unknown");
        }
        if (result.hasValue("errorType")) {
            return result.getString("errorType", "unknown");
        }
        if (result.hasValue("errorCode")) {
            return result.getString("errorCode", "unknown");
        }
        return "unknown";
    }
}
