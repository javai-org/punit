package org.javai.punit.contract;

import org.javai.punit.model.CriterionOutcome;

import java.util.List;
import java.util.Objects;

/**
 * Adapts the new Design by Contract postcondition results to the existing PUnit
 * criterion outcome model.
 *
 * <p>This adapter enables the new {@link PostconditionResult} types to integrate
 * with the existing experiment infrastructure (aggregators, spec generation, etc.)
 * by converting them to {@link CriterionOutcome} instances.
 *
 * <h2>Mapping</h2>
 * <table border="1">
 *   <tr><th>PostconditionResult</th><th>CriterionOutcome</th></tr>
 *   <tr><td>{@link PostconditionResult.Passed}</td><td>{@link CriterionOutcome.Passed}</td></tr>
 *   <tr><td>{@link PostconditionResult.Failed}</td><td>{@link CriterionOutcome.Failed}</td></tr>
 *   <tr><td>{@link PostconditionResult.Skipped}</td><td>{@link CriterionOutcome.NotEvaluated}</td></tr>
 * </table>
 *
 * @see PostconditionResult
 * @see CriterionOutcome
 */
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
        return switch (result) {
            case PostconditionResult.Passed p -> new CriterionOutcome.Passed(p.description());
            case PostconditionResult.Failed f -> new CriterionOutcome.Failed(
                    f.description(),
                    f.reason() != null ? f.reason() : "Postcondition not satisfied"
            );
            case PostconditionResult.Skipped s -> new CriterionOutcome.NotEvaluated(s.description());
        };
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
