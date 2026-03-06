package org.javai.punit.ptest.strategy;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.javai.punit.controls.budget.CostBudgetMonitor;
import org.javai.punit.controls.budget.DefaultTokenChargeRecorder;
import org.javai.punit.controls.budget.SharedBudgetMonitor;
import org.javai.punit.ptest.bernoulli.EarlyTerminationEvaluator;
import org.javai.punit.ptest.bernoulli.SampleResultAggregator;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Context provided to strategy intercept methods for sample execution.
 *
 * <p>This record encapsulates all the state and configuration needed by a
 * strategy to execute a single sample, including:
 * <ul>
 *   <li>The test configuration</li>
 *   <li>The result aggregator for recording outcomes</li>
 *   <li>Budget monitors at method, class, and suite levels</li>
 *   <li>The early termination evaluator</li>
 *   <li>The token recorder (for dynamic token mode)</li>
 *   <li>The termination flag (shared between samples)</li>
 * </ul>
 *
 * @param config the probabilistic test configuration
 * @param aggregator the result aggregator for recording sample outcomes
 * @param evaluator the early termination evaluator
 * @param methodBudget the method-level budget monitor
 * @param classBudget the class-level budget monitor (may be null)
 * @param suiteBudget the suite-level budget monitor (may be null)
 * @param tokenRecorder the token recorder for dynamic token mode (may be null)
 * @param terminated the shared termination flag
 * @param extensionContext the JUnit extension context
 */
public record SampleExecutionContext(
        ProbabilisticTestConfig config,
        SampleResultAggregator aggregator,
        EarlyTerminationEvaluator evaluator,
        CostBudgetMonitor methodBudget,
        SharedBudgetMonitor classBudget,
        SharedBudgetMonitor suiteBudget,
        DefaultTokenChargeRecorder tokenRecorder,
        AtomicBoolean terminated,
        ExtensionContext extensionContext
) {
    public SampleExecutionContext {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(aggregator, "aggregator must not be null");
        Objects.requireNonNull(evaluator, "evaluator must not be null");
        Objects.requireNonNull(methodBudget, "methodBudget must not be null");
        Objects.requireNonNull(terminated, "terminated must not be null");
        Objects.requireNonNull(extensionContext, "extensionContext must not be null");
    }

    /**
     * Returns true if dynamic token recording is enabled.
     */
    public boolean hasDynamicTokenRecording() {
        return tokenRecorder != null;
    }

    /**
     * Returns true if class-level budget monitoring is enabled.
     */
    public boolean hasClassBudget() {
        return classBudget != null;
    }

    /**
     * Returns true if suite-level budget monitoring is enabled.
     */
    public boolean hasSuiteBudget() {
        return suiteBudget != null;
    }
}
