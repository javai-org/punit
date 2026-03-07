package org.javai.punit.contract;

import java.util.List;

/**
 * Evaluates postconditions against a result.
 *
 * <p>This interface represents the postcondition evaluation capability,
 * decoupled from preconditions and contract definition. It's what
 * {@link UseCaseOutcome} needs to evaluate whether a result satisfies
 * the service's guarantees.
 *
 * @param <R> the result type
 * @see ServiceContract
 * @see UseCaseOutcome
 */
public interface PostconditionEvaluator<R> {

    /**
     * Evaluates all postconditions against a result.
     *
     * @param result the result to evaluate
     * @return list of postcondition results
     */
    List<PostconditionResult> evaluate(R result);

    /**
     * Returns the total number of postconditions.
     *
     * @return the postcondition count
     */
    int postconditionCount();
}
