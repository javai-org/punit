package org.javai.punit.contract;

import org.javai.punit.model.CriterionOutcome;

import java.util.List;
import java.util.Objects;

/**
 * Adapts the Design by Contract postcondition results to the legacy PUnit
 * criterion outcome model.
 *
 * <p>This adapter bridges the new postcondition system with legacy infrastructure.
 * It will be removed when the legacy infrastructure is fully migrated.
 *
 * @deprecated Use {@link PostconditionResult} directly instead of converting
 *             to legacy criterion outcomes.
 * @see PostconditionResult
 * @see CriterionOutcome
 */
@Deprecated(forRemoval = true)
public final class PostconditionResultAdapter {

    private PostconditionResultAdapter() {
        // Utility class
    }

    /**
     * Converts a postcondition result to a criterion outcome.
     *
     * @param result the postcondition result to convert
     * @return the equivalent criterion outcome
     * @throws NullPointerException if result is null
     */
    public static CriterionOutcome toCriterionOutcome(PostconditionResult result) {
        Objects.requireNonNull(result, "result must not be null");
        if (result.passed()) {
            return new CriterionOutcome.Passed(result.description());
        } else {
            String reason = result.failureReason();
            // Check if this is a "skipped" failure (starts with "Skipped:")
            if (reason != null && reason.startsWith("Skipped:")) {
                return new CriterionOutcome.NotEvaluated(result.description());
            }
            return new CriterionOutcome.Failed(
                    result.description(),
                    reason != null ? reason : "Postcondition not satisfied"
            );
        }
    }

    /**
     * Converts a list of postcondition results to criterion outcomes.
     *
     * @param results the postcondition results to convert
     * @return list of equivalent criterion outcomes
     * @throws NullPointerException if results is null
     */
    public static List<CriterionOutcome> toCriterionOutcomes(List<PostconditionResult> results) {
        Objects.requireNonNull(results, "results must not be null");
        return results.stream()
                .map(PostconditionResultAdapter::toCriterionOutcome)
                .toList();
    }
}
