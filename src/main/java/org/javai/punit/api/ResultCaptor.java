package org.javai.punit.api;

import org.javai.punit.contract.ContractCriteriaAdapter;
import org.javai.punit.model.UseCaseCriteria;
import org.javai.punit.model.UseCaseOutcome;
import org.javai.punit.model.UseCaseResult;

import java.time.Instant;

/**
 * Captures use case execution results during experiments.
 *
 * <p>The {@code ResultCaptor} is injected into experiment methods and provides
 * a way to record execution results for aggregation. Each experiment invocation
 * gets a fresh captor instance.
 *
 * <h2>Usage with UseCaseOutcome</h2>
 * <p>The recommended pattern is to record a {@link UseCaseOutcome} which bundles
 * both the result and criteria:
 * <pre>{@code
 * @Experiment(useCase = ShoppingUseCase.class, samples = 1000)
 * void measureSearchBaseline(ShoppingUseCase useCase, ResultCaptor captor) {
 *     UseCaseOutcome outcome = useCase.searchProducts("wireless headphones");
 *     captor.record(outcome);  // Records both result and criteria
 * }
 * }</pre>
 *
 * <h2>Legacy Usage with Separate Result and Criteria</h2>
 * <p>The captor can also record result and criteria separately for backward
 * compatibility:
 * <pre>{@code
 * @Experiment(useCase = ShoppingUseCase.class, samples = 1000)
 * void measureSearchBaseline(ShoppingUseCase useCase, ResultCaptor captor) {
 *     UseCaseResult result = useCase.searchProducts("headphones");
 *     captor.record(result);
 *     captor.recordCriteria(useCase.criteria(result));
 * }
 * }</pre>
 *
 * <h2>How It Works</h2>
 * <ol>
 *   <li>The extension creates a new captor for each sample</li>
 *   <li>The captor is injected as a method parameter</li>
 *   <li>The experiment method calls {@code captor.record(outcome)} or {@code captor.record(result)}</li>
 *   <li>After method execution, the extension reads the result and aggregates it</li>
 * </ol>
 *
 * @see org.javai.punit.api.Experiment
 * @see UseCaseOutcome
 * @see UseCaseCriteria
 */
public class ResultCaptor {

    private volatile UseCaseResult result;
    private volatile UseCaseCriteria criteria;
    private volatile Throwable exception;
    private volatile boolean recorded = false;

    /**
     * Records a use case outcome (result + criteria together).
     *
     * <p>This is the recommended method for recording use case executions.
     * It captures both the result and its associated criteria in one call.
     *
     * @param outcome the use case outcome to record
     * @return the outcome (for fluent chaining)
     */
    public UseCaseOutcome record(UseCaseOutcome outcome) {
        if (!recorded) {
            this.result = outcome.result();
            this.criteria = outcome.criteria();
            this.recorded = true;
        }
        return outcome;
    }

    /**
     * Records a contract-based use case outcome.
     *
     * <p>This method supports the new Design by Contract system. It converts
     * the typed result and postcondition evaluator to the existing model types
     * for compatibility with the experiment infrastructure.
     *
     * <h2>Example Usage</h2>
     * <pre>{@code
     * @Experiment(useCase = MyUseCase.class, samples = 1000)
     * void measureBaseline(MyUseCase useCase, ResultCaptor captor) {
     *     UseCaseOutcome<String> outcome = useCase.performAction(input);
     *     captor.recordContract(outcome);
     * }
     * }</pre>
     *
     * @param outcome the contract-based outcome to record
     * @param <R> the result type
     * @return the outcome (for fluent chaining)
     */
    public <R> org.javai.punit.contract.UseCaseOutcome<R> recordContract(
            org.javai.punit.contract.UseCaseOutcome<R> outcome) {
        if (!recorded) {
            // Convert contract outcome to model types
            UseCaseResult.Builder builder = UseCaseResult.builder()
                    .executionTime(outcome.executionTime())
                    .timestamp(Instant.now());

            // Copy metadata
            outcome.metadata().forEach(builder::meta);

            // Store the raw result as a value (if not null)
            if (outcome.result() != null) {
                builder.value("result", outcome.result());
            }

            this.result = builder.build();
            this.criteria = ContractCriteriaAdapter.from(outcome);
            this.recorded = true;
        }
        return outcome;
    }

    /**
     * Records a use case execution result.
     *
     * <p>Call this method with the result of your use case invocation.
     * Only the first recorded result is kept; subsequent calls are ignored.
     *
     * @param result the use case result to record
     * @return the result (for fluent chaining)
     */
    public UseCaseResult record(UseCaseResult result) {
        if (!recorded) {
            this.result = result;
            this.recorded = true;
        }
        return result;
    }

    /**
     * Records success criteria for the use case result.
     *
     * <p>Call this method with the criteria obtained from the use case's
     * {@code criteria()} method. The criteria are evaluated lazily when
     * the extension processes the result.
     *
     * @param criteria the success criteria to record
     * @return the criteria (for fluent chaining)
     */
    public UseCaseCriteria recordCriteria(UseCaseCriteria criteria) {
        this.criteria = criteria;
        return criteria;
    }

    /**
     * Records an exception that occurred during use case execution.
     *
     * @param e the exception to record
     */
    public void recordException(Throwable e) {
        if (!recorded) {
            this.exception = e;
            this.recorded = true;
        }
    }

    /**
     * Checks if a result has been recorded.
     *
     * @return true if a result or exception was recorded
     */
    public boolean hasResult() {
        return recorded;
    }

    /**
     * Gets the recorded result.
     *
     * @return the recorded result, or null if none was recorded
     */
    public UseCaseResult getResult() {
        return result;
    }

    /**
     * Gets the recorded exception.
     *
     * @return the recorded exception, or null if none was recorded
     */
    public Throwable getException() {
        return exception;
    }

    /**
     * Checks if an exception was recorded.
     *
     * @return true if an exception was recorded
     */
    public boolean hasException() {
        return exception != null;
    }

    /**
     * Gets the recorded success criteria.
     *
     * @return the recorded criteria, or null if none was recorded
     */
    public UseCaseCriteria getCriteria() {
        return criteria;
    }

    /**
     * Checks if criteria have been recorded.
     *
     * @return true if criteria were recorded
     */
    public boolean hasCriteria() {
        return criteria != null;
    }

    /**
     * Resets the captor for reuse.
     */
    void reset() {
        this.result = null;
        this.criteria = null;
        this.exception = null;
        this.recorded = false;
    }
}
