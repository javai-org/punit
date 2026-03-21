package org.javai.punit.ptest.concurrent;

import org.javai.punit.controls.budget.BudgetOrchestrator;
import org.javai.punit.ptest.bernoulli.SampleResultAggregator;

/**
 * Handles budget exhaustion by recording termination and determining
 * whether to force failure or evaluate partial results.
 */
@FunctionalInterface
public interface BudgetExhaustionHandler {

    /**
     * Processes budget exhaustion for the given aggregator.
     *
     * @param checkResult the budget check result that triggered exhaustion
     * @param aggregator  the aggregator to record termination into
     */
    void handle(BudgetOrchestrator.BudgetCheckResult checkResult, SampleResultAggregator aggregator);
}
