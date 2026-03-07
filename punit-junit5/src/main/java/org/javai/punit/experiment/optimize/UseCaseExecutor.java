package org.javai.punit.experiment.optimize;

import java.util.List;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.punit.experiment.model.FactorSuit;

/**
 * Executes a use case multiple times with a given factor suit.
 *
 * <p>This interface abstracts the actual use case execution from the
 * optimization orchestrator, allowing different execution strategies
 * (sequential, parallel, etc.) and enabling testing with mocks.
 *
 * <h2>Implementation Guidelines</h2>
 * <ul>
 *   <li>Apply the factor suit to configure the use case before each execution</li>
 *   <li>Capture all outcomes, including failures</li>
 *   <li>Respect any pacing or rate limiting requirements</li>
 * </ul>
 */
@FunctionalInterface
public interface UseCaseExecutor {

    /**
     * Execute the use case N times with the given factor suit.
     *
     * @param factorSuit the complete factor values to apply
     * @param sampleCount the number of times to execute
     * @return list of outcomes from each execution
     * @throws ExecutionException if execution fails catastrophically
     */
    List<UseCaseOutcome<?>> execute(FactorSuit factorSuit, int sampleCount) throws ExecutionException;

    /**
     * Exception thrown when use case execution fails catastrophically.
     *
     * <p>Individual sample failures should be captured in the returned outcomes,
     * not thrown as exceptions. This exception is for infrastructure failures
     * that prevent any execution from completing.
     */
    class ExecutionException extends Exception {

        public ExecutionException(String message) {
            super(message);
        }

        public ExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
