package org.javai.punit.contract;

import org.javai.punit.model.CriterionOutcome;
import org.javai.punit.model.UseCaseCriteria;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Adapts a contract-based postcondition evaluator to the {@link UseCaseCriteria} interface.
 *
 * <p>This adapter enables the new Design by Contract system to integrate with the
 * existing PUnit experiment infrastructure. It wraps a {@link PostconditionEvaluator}
 * and a result value, presenting them as a {@link UseCaseCriteria} for compatibility
 * with {@link org.javai.punit.api.ResultCaptor} and aggregators.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * UseCaseOutcome<String> outcome = ...; // contract-based outcome
 * UseCaseCriteria criteria = ContractCriteriaAdapter.from(outcome);
 * captor.recordCriteria(criteria);
 * }</pre>
 *
 * @param <R> the result type
 * @see PostconditionEvaluator
 * @see UseCaseCriteria
 */
public final class ContractCriteriaAdapter<R> implements UseCaseCriteria {

    private final PostconditionEvaluator<R> evaluator;
    private final R result;
    private volatile List<CriterionOutcome> cachedOutcomes;

    private ContractCriteriaAdapter(PostconditionEvaluator<R> evaluator, R result) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator must not be null");
        this.result = result;
    }

    /**
     * Creates a criteria adapter from a contract-based use case outcome.
     *
     * @param outcome the contract-based outcome
     * @param <R> the result type
     * @return a UseCaseCriteria adapter
     * @throws NullPointerException if outcome is null
     */
    public static <R> UseCaseCriteria from(UseCaseOutcome<R> outcome) {
        Objects.requireNonNull(outcome, "outcome must not be null");
        return new ContractCriteriaAdapter<>(
                outcome.postconditionEvaluator(),
                outcome.result()
        );
    }

    /**
     * Creates a criteria adapter from an evaluator and result.
     *
     * @param evaluator the postcondition evaluator
     * @param result the result to evaluate against
     * @param <R> the result type
     * @return a UseCaseCriteria adapter
     * @throws NullPointerException if evaluator is null
     */
    public static <R> UseCaseCriteria from(PostconditionEvaluator<R> evaluator, R result) {
        return new ContractCriteriaAdapter<>(evaluator, result);
    }

    @Override
    public List<Map.Entry<String, Supplier<Boolean>>> entries() {
        // This method is not typically used directly; evaluate() is preferred.
        // We provide a compatible implementation by converting postconditions to entries.
        return evaluator.evaluate(result).stream()
                .map(pc -> Map.entry(pc.description(), (Supplier<Boolean>) pc::passed))
                .toList();
    }

    @Override
    public List<CriterionOutcome> evaluate() {
        // Cache the evaluation results for consistency
        if (cachedOutcomes == null) {
            List<PostconditionResult> postconditionResults = evaluator.evaluate(result);
            cachedOutcomes = PostconditionResultAdapter.toCriterionOutcomes(postconditionResults);
        }
        return cachedOutcomes;
    }

    @Override
    public boolean allPassed() {
        // Use the original postcondition results for accurate evaluation
        // Skipped postconditions don't count as failures
        return evaluator.evaluate(result).stream()
                .noneMatch(PostconditionResult::failed);
    }
}
