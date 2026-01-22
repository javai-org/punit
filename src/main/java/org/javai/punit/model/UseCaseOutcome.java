package org.javai.punit.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A compound return type that bundles a use case result with its success criteria.
 *
 * @deprecated Use {@link org.javai.punit.contract.UseCaseOutcome} instead, which provides:
 *             <ul>
 *               <li>Type-safe result access (no map lookups)</li>
 *               <li>Postconditions via Design by Contract</li>
 *               <li>Built-in execution timing and timestamps</li>
 *             </ul>
 *
 * @see org.javai.punit.contract.UseCaseOutcome
 * @see org.javai.punit.contract.ServiceContract
 */
@Deprecated(forRemoval = true)
public record UseCaseOutcome(
    UseCaseResult result,
    UseCaseCriteria criteria
) {
    
    /**
     * Compact constructor for validation.
     */
    public UseCaseOutcome {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(criteria, "criteria must not be null");
    }
    
    // ========== Convenience Delegations to Criteria ==========
    
    /**
     * Evaluates all criteria and returns their outcomes.
     *
     * <p>This is a convenience delegation to {@link UseCaseCriteria#evaluate()}.
     *
     * @return list of criterion outcomes in order
     */
    public List<CriterionOutcome> evaluate() {
        return criteria.evaluate();
    }
    
    /**
     * Returns true if all criteria pass.
     *
     * <p>This is a convenience delegation to {@link UseCaseCriteria#allPassed()}.
     *
     * @return true if all criteria evaluate to passed
     */
    public boolean allPassed() {
        return criteria.allPassed();
    }
    
    /**
     * Asserts that all criteria pass.
     *
     * <p>This is a convenience delegation to {@link UseCaseCriteria#assertAll()}.
     *
     * @throws AssertionError if any criterion fails
     */
    public void assertAll() {
        criteria.assertAll();
    }
    
    /**
     * Evaluates criteria and returns results as a map of description to pass/fail.
     *
     * @return ordered map of criterion description to pass status
     */
    public Map<String, Boolean> evaluateAsMap() {
        java.util.LinkedHashMap<String, Boolean> map = new java.util.LinkedHashMap<>();
        for (CriterionOutcome outcome : evaluate()) {
            map.put(outcome.description(), outcome.passed());
        }
        return map;
    }
    
    // ========== Convenience Delegations to Result ==========
    
    /**
     * Returns the timestamp of when this use case was executed.
     *
     * <p>This is a convenience delegation to {@link UseCaseResult#timestamp()}.
     *
     * @return the execution timestamp
     */
    public Instant timestamp() {
        return result.timestamp();
    }
    
    /**
     * Returns the execution time of this use case.
     *
     * <p>This is a convenience delegation to {@link UseCaseResult#executionTime()}.
     *
     * @return the execution duration
     */
    public Duration executionTime() {
        return result.executionTime();
    }
    
    // ========== Factory Methods ==========
    
    /**
     * Creates an outcome representing an invocation failure.
     *
     * <p>Use this when the use case method itself throws an exception.
     * The result is empty and the criteria contains a single errored criterion.
     *
     * @param cause the exception that occurred during invocation
     * @return an outcome representing the failure
     */
    public static UseCaseOutcome invocationFailed(Throwable cause) {
        Objects.requireNonNull(cause, "cause must not be null");
        UseCaseResult emptyResult = UseCaseResult.builder()
            .value("error", cause.getMessage())
            .value("errorType", cause.getClass().getName())
            .build();
        return new UseCaseOutcome(emptyResult, UseCaseCriteria.constructionFailed(cause));
    }
    
    /**
     * Creates an outcome from a result with default (trivial) criteria.
     *
     * <p>This is a migration helper for use cases that still return only
     * {@link UseCaseResult}. The framework wraps these results with the
     * trivial postcondition (empty criteria that always passes).
     *
     * <p>See {@link UseCaseCriteria#defaultCriteria()} for the Design by Contract
     * rationale behind using empty criteria as the default.
     *
     * @param result the use case result
     * @return an outcome with trivial (empty) criteria
     */
    public static UseCaseOutcome withDefaultCriteria(UseCaseResult result) {
        Objects.requireNonNull(result, "result must not be null");
        return new UseCaseOutcome(result, UseCaseCriteria.defaultCriteria());
    }
}

