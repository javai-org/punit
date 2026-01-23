package org.javai.punit.api;

import java.util.List;
import org.javai.punit.contract.PostconditionResult;
import org.javai.punit.contract.UseCaseOutcome;

/**
 * Captures use case execution outcomes during experiments.
 *
 * <p>The {@code OutcomeCaptor} is injected into experiment methods and provides
 * a way to record execution outcomes for aggregation. Each experiment invocation
 * gets a fresh captor instance.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @Experiment(useCase = ShoppingUseCase.class, samples = 1000)
 * void measureSearchBaseline(ShoppingUseCase useCase, OutcomeCaptor captor) {
 *     UseCaseOutcome<SearchResult> outcome = useCase.searchProducts("wireless headphones");
 *     captor.record(outcome);  // Records result and postconditions
 * }
 * }</pre>
 *
 * <h2>How It Works</h2>
 * <ol>
 *   <li>The extension creates a new captor for each sample</li>
 *   <li>The captor is injected as a method parameter</li>
 *   <li>The experiment method calls {@code captor.record(outcome)}</li>
 *   <li>After method execution, the extension reads the outcome and aggregates it</li>
 * </ol>
 *
 * @see org.javai.punit.api.Experiment
 * @see UseCaseOutcome
 * @see org.javai.punit.contract.ServiceContract
 */
public class OutcomeCaptor {

    private volatile UseCaseOutcome<?> contractOutcome;
    private volatile Throwable exception;
    private volatile boolean recorded = false;

    /**
     * Records a use case outcome.
     *
     * <p>This method captures the typed result and postconditions for aggregation.
     *
     * <h2>Example Usage</h2>
     * <pre>{@code
     * @Experiment(useCase = MyUseCase.class, samples = 1000)
     * void measureBaseline(MyUseCase useCase, OutcomeCaptor captor) {
     *     UseCaseOutcome<String> outcome = useCase.performAction(input);
     *     captor.record(outcome);
     * }
     * }</pre>
     *
     * @param outcome the outcome to record
     * @param <R> the result type
     * @return the outcome (for fluent chaining)
     */
    public <R> UseCaseOutcome<R> record(UseCaseOutcome<R> outcome) {
        if (!recorded) {
            this.contractOutcome = outcome;
            this.recorded = true;
        }
        return outcome;
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
     * <p>Returns true when an outcome was recorded, not when only an exception
     * was recorded. Use {@link #hasException()} to check for exceptions.
     *
     * @return true if an outcome was recorded
     */
    public boolean hasResult() {
        return contractOutcome != null;
    }

    /**
     * Checks if anything (outcome or exception) has been recorded.
     *
     * @return true if something was recorded
     */
    public boolean isRecorded() {
        return recorded;
    }

    /**
     * Gets the recorded outcome.
     *
     * @return the recorded outcome, or null if none was recorded
     */
    public UseCaseOutcome<?> getContractOutcome() {
        return contractOutcome;
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
     * Checks if an outcome was recorded.
     *
     * @return true if an outcome was recorded via {@link #record}
     */
    public boolean hasContractOutcome() {
        return contractOutcome != null;
    }

    /**
     * Gets postcondition results from the recorded outcome.
     *
     * <p>This method evaluates the postconditions and returns the results.
     * For contracts with derivations, the results include both derivation
     * outcomes and their nested postconditions.
     *
     * @return the evaluated postcondition results, or null if no outcome was recorded
     */
    public List<PostconditionResult> getPostconditionResults() {
        if (contractOutcome == null) {
            return null;
        }
        return contractOutcome.evaluatePostconditions();
    }

    /**
     * Checks if all postconditions passed.
     *
     * <p>This is a convenience method that evaluates the postconditions
     * and returns true if all passed.
     *
     * @return true if an outcome was recorded and all postconditions passed,
     *         false otherwise
     */
    public boolean allPostconditionsPassed() {
        if (contractOutcome == null) {
            return false;
        }
        return contractOutcome.allPostconditionsSatisfied();
    }

    /**
     * Resets the captor for reuse.
     */
    void reset() {
        this.contractOutcome = null;
        this.exception = null;
        this.recorded = false;
    }
}
