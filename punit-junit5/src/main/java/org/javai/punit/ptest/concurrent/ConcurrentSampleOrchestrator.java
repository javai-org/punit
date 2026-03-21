package org.javai.punit.ptest.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.javai.punit.controls.budget.BudgetOrchestrator;
import org.javai.punit.controls.budget.CostBudgetMonitor;
import org.javai.punit.controls.budget.SharedBudgetMonitor;
import org.javai.punit.ptest.bernoulli.EarlyTerminationEvaluator;
import org.javai.punit.ptest.bernoulli.SampleResultAggregator;

/**
 * Orchestrates concurrent sample execution using virtual threads.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Populate work queue with sample tasks</li>
 *   <li>Start N worker virtual threads</li>
 *   <li>Consumer drains results, evaluates policy, signals stop</li>
 *   <li>Join all workers</li>
 *   <li>Return execution summary for verdict computation</li>
 * </ol>
 *
 * <p>This class lives in punit-junit5 because it bridges the JUnit 5
 * invocation model with the JUnit-free concurrent engine in punit-core.
 */
public class ConcurrentSampleOrchestrator {

    private final int maxConcurrent;
    private final SampleResultAggregator aggregator;
    private final EarlyTerminationEvaluator earlyTerminationEvaluator;
    private final BudgetOrchestrator budgetOrchestrator;
    private final CostBudgetMonitor methodBudget;
    private final SharedBudgetMonitor classBudget;
    private final SharedBudgetMonitor suiteBudget;
    private final SampleInvoker invoker;
    private final BudgetExhaustionHandler budgetExhaustionHandler;
    private final ProgressObserver progressObserver;

    public ConcurrentSampleOrchestrator(
            int maxConcurrent,
            SampleResultAggregator aggregator,
            EarlyTerminationEvaluator earlyTerminationEvaluator,
            BudgetOrchestrator budgetOrchestrator,
            CostBudgetMonitor methodBudget,
            SharedBudgetMonitor classBudget,
            SharedBudgetMonitor suiteBudget,
            SampleInvoker invoker,
            BudgetExhaustionHandler budgetExhaustionHandler,
            ProgressObserver progressObserver) {
        this.maxConcurrent = maxConcurrent;
        this.aggregator = aggregator;
        this.earlyTerminationEvaluator = earlyTerminationEvaluator;
        this.budgetOrchestrator = budgetOrchestrator;
        this.methodBudget = methodBudget;
        this.classBudget = classBudget;
        this.suiteBudget = suiteBudget;
        this.invoker = invoker;
        this.budgetExhaustionHandler = budgetExhaustionHandler;
        this.progressObserver = progressObserver;
    }

    /**
     * Executes all samples concurrently and blocks until completion.
     *
     * @param totalSamples the number of samples to execute
     * @param inputCount   number of inputs for cycling (0 if no @InputSource)
     * @return the execution result summary
     */
    public ExecutionResult execute(int totalSamples, int inputCount) {
        SampleWorkQueue workQueue = new SampleWorkQueue(totalSamples, inputCount);
        ResultStaging staging = new ResultStaging(progressObserver);
        AtomicBoolean stopSignal = new AtomicBoolean(false);

        int effectiveWorkers = Math.min(maxConcurrent, totalSamples);

        // Start workers on virtual threads
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> workers = new ArrayList<>();
            for (int i = 0; i < effectiveWorkers; i++) {
                WorkerTask worker = new WorkerTask(
                        workQueue, staging, invoker, stopSignal);
                workers.add(executor.submit(worker));
            }

            // Consumer runs on the orchestrator's thread (the calling thread)
            ResultConsumer consumer = new ResultConsumer(
                    staging, aggregator, earlyTerminationEvaluator,
                    budgetOrchestrator, methodBudget, classBudget, suiteBudget,
                    stopSignal, workQueue, effectiveWorkers, budgetExhaustionHandler);

            consumer.consumeAll(totalSamples);

            // Ensure all workers have completed
            for (Future<?> worker : workers) {
                try {
                    worker.get();
                } catch (Exception e) {
                    // Workers handle their own exceptions;
                    // abort results are captured via staging
                }
            }

            return new ExecutionResult(consumer.abortResult(), consumer.resultsConsumed());
        }
    }

    /**
     * Summary of the concurrent execution run.
     *
     * @param abortResult   the result that triggered an abort, or null
     * @param totalConsumed the total number of results consumed
     */
    public record ExecutionResult(StagedResult abortResult, int totalConsumed) {

        public boolean wasAborted() {
            return abortResult != null;
        }
    }
}
