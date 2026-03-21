package org.javai.punit.ptest.concurrent;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.javai.punit.controls.budget.BudgetOrchestrator;
import org.javai.punit.controls.budget.CostBudgetMonitor;
import org.javai.punit.controls.budget.SharedBudgetMonitor;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.ptest.bernoulli.EarlyTerminationEvaluator;
import org.javai.punit.ptest.bernoulli.SampleResultAggregator;

/**
 * Single-threaded consumer that drains staged results in sequence order
 * into the aggregator, evaluating early termination and budget constraints
 * after each result.
 *
 * <p>This class is the policy enforcement point for the concurrent execution
 * model. All decisions that affect whether execution continues — early
 * termination, budget exhaustion, abort signals — are made here, in a
 * single thread, exactly as they were in the sequential model.
 *
 * <p>The consumer runs on its own thread (or the coordinating thread) and
 * blocks waiting for results via {@link ResultStaging#awaitNext(long)}.
 */
public class ResultConsumer {

    private final ResultStaging staging;
    private final SampleResultAggregator aggregator;
    private final EarlyTerminationEvaluator earlyTerminationEvaluator;
    private final BudgetOrchestrator budgetOrchestrator;
    private final CostBudgetMonitor methodBudget;
    private final SharedBudgetMonitor classBudget;
    private final SharedBudgetMonitor suiteBudget;
    private final AtomicBoolean stopSignal;
    private final SampleWorkQueue workQueue;
    private final int workerCount;
    private final BudgetExhaustionHandler budgetExhaustionHandler;

    private int resultsConsumed = 0;
    private StagedResult abortResult = null;

    public ResultConsumer(ResultStaging staging,
                          SampleResultAggregator aggregator,
                          EarlyTerminationEvaluator earlyTerminationEvaluator,
                          BudgetOrchestrator budgetOrchestrator,
                          CostBudgetMonitor methodBudget,
                          SharedBudgetMonitor classBudget,
                          SharedBudgetMonitor suiteBudget,
                          AtomicBoolean stopSignal,
                          SampleWorkQueue workQueue,
                          int workerCount,
                          BudgetExhaustionHandler budgetExhaustionHandler) {
        this.staging = staging;
        this.aggregator = aggregator;
        this.earlyTerminationEvaluator = earlyTerminationEvaluator;
        this.budgetOrchestrator = budgetOrchestrator;
        this.methodBudget = methodBudget;
        this.classBudget = classBudget;
        this.suiteBudget = suiteBudget;
        this.stopSignal = stopSignal;
        this.workQueue = workQueue;
        this.workerCount = workerCount;
        this.budgetExhaustionHandler = budgetExhaustionHandler;
    }

    /**
     * Consumes all results until execution is complete.
     *
     * <p>Blocks on the staging area, draining results in sequence order.
     * After each result is fed to the aggregator, evaluates:
     * <ol>
     *   <li>Abort condition (Error/RuntimeException from sample)</li>
     *   <li>Early termination (impossibility or success guaranteed)</li>
     *   <li>Budget exhaustion (time or token budget exceeded)</li>
     *   <li>Completion (all samples consumed)</li>
     * </ol>
     *
     * @param totalSamples the total number of samples expected
     */
    public void consumeAll(int totalSamples) {
        while (resultsConsumed < totalSamples && !stopSignal.get()) {
            Optional<StagedResult> next = staging.awaitNext(100);
            if (next.isEmpty()) {
                continue;
            }

            StagedResult result = next.get();
            resultsConsumed++;

            feedToAggregator(result);

            // 1. Abort check
            if (result.shouldAbort()) {
                abortResult = result;
                signalStop();
                return;
            }

            // 2. Early termination check
            Optional<TerminationReason> earlyTermination = earlyTerminationEvaluator.shouldTerminate(
                    aggregator.getSuccesses(), aggregator.getSamplesExecuted());
            if (earlyTermination.isPresent()) {
                aggregator.setTerminated(earlyTermination.get(),
                        buildTerminationDetails(earlyTermination.get()));
                signalStop();
                drainInFlight();
                return;
            }

            // 3. Budget check
            BudgetOrchestrator.BudgetCheckResult budgetCheck = budgetOrchestrator.checkAfterSample(
                    suiteBudget, classBudget, methodBudget);
            if (budgetCheck.shouldTerminate()) {
                budgetExhaustionHandler.handle(budgetCheck, aggregator);
                signalStop();
                drainInFlight();
                return;
            }
        }

        // All samples consumed — mark complete
        if (!stopSignal.get()) {
            aggregator.setCompleted();
        }
    }

    /**
     * Drains any in-flight results that were deposited after the stop signal.
     * These results are included in the final aggregation per REQ-15.
     */
    private void drainInFlight() {
        long drainDeadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < drainDeadline) {
            Optional<StagedResult> result = staging.drainNext();
            if (result.isEmpty()) {
                if (staging.pendingCount() == 0) {
                    break;
                }
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }
            resultsConsumed++;
            feedToAggregator(result.get());
        }
    }

    /**
     * Feeds a single staged result into the aggregator and budget monitors.
     */
    private void feedToAggregator(StagedResult result) {
        if (result.passed()) {
            aggregator.recordSuccess(result.latencyMs());
        } else {
            aggregator.recordFailure(result.failure());
        }

        // Per-dimension results
        if (result.functionalPassed() != null) {
            aggregator.recordFunctionalResult(result.functionalPassed());
        }
        if (result.latencyPassed() != null) {
            aggregator.recordLatencyResult(result.latencyPassed());
        }

        // Token recording
        if (result.dynamicTokens() > 0) {
            methodBudget.recordDynamicTokens(result.dynamicTokens());
        }
    }

    private void signalStop() {
        stopSignal.set(true);
        workQueue.shutdown(workerCount);
    }

    private String buildTerminationDetails(TerminationReason reason) {
        return reason.name() + " after " + aggregator.getSamplesExecuted() + " samples"
                + " (" + aggregator.getSuccesses() + " successes)";
    }

    /** Returns the result that triggered an abort, or null if no abort occurred. */
    public StagedResult abortResult() {
        return abortResult;
    }

    /** Returns the number of results consumed so far. */
    public int resultsConsumed() {
        return resultsConsumed;
    }
}
