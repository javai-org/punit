# DES-CONCURRENCY: Concurrent Sample Execution Design

## 1. Overview

This document describes the internal architecture for implementing concurrent sample execution as specified in [REQ-CONCURRENCY](REQ-CONCURRENCY.md). The design introduces a new `ptest.concurrent` package containing cohesive, independently testable components that replace the current sequential invocation model for all values of `maxConcurrent` (unified infrastructure per REQ-30).

## 2. Design Principles

1. **Single code path.** The concurrent infrastructure handles `maxConcurrent = 1` identically to `maxConcurrent = 10`. No branching between sequential and concurrent execution.
2. **Existing components remain untouched.** `SampleResultAggregator`, `EarlyTerminationEvaluator`, `BudgetOrchestrator`, and `CostBudgetMonitor` are consumed by the new infrastructure — not modified.
3. **Workers know nothing about policy.** Worker threads execute samples and deposit results. All decisions (early termination, budget enforcement, verdict computation) live on the single consumer thread.
4. **Testability by construction.** Each new class has a single responsibility, accepts its dependencies via constructor injection, and can be tested in isolation without JUnit 5's extension machinery.

## 3. Package Structure

```
punit-core/src/main/java/org/javai/punit/ptest/concurrent/
├── SampleWorkQueue.java           # Shared work queue of sequenced sample tasks
├── SampleTask.java                # Value object: sequence index + sample identity
├── StagedResult.java              # Value object: sequence index + execution outcome
├── ResultStaging.java             # Thread-safe staging structure for completed results
├── ResultConsumer.java            # Single-threaded consumer: staging → aggregator → policy checks
└── WorkerTask.java               # Runnable executed by each virtual thread worker
```

```
punit-junit5/src/main/java/org/javai/punit/ptest/concurrent/
└── ConcurrentSampleOrchestrator.java   # Top-level orchestrator, integrates with JUnit lifecycle
```

The `punit-core` classes are JUnit-free. The orchestrator in `punit-junit5` bridges between JUnit 5's extension model and the concurrent execution engine.

## 4. Component Design

### 4.1. SampleTask

A value object representing a unit of work in the queue.

```java
package org.javai.punit.ptest.concurrent;

/**
 * A unit of work representing a single sample to execute.
 *
 * @param sequenceIndex 1-based index matching the sequential execution order
 * @param inputIndex    the input to use for this sample (sequenceIndex - 1) % inputCount,
 *                      or -1 if no @InputSource is configured
 */
public record SampleTask(int sequenceIndex, int inputIndex) {

    /** Sentinel task placed in the queue to signal workers to shut down. */
    public static final SampleTask POISON = new SampleTask(-1, -1);

    public boolean isPoison() {
        return sequenceIndex == -1;
    }
}
```

**Module:** punit-core
**Tests:** Trivial value object — tested implicitly through `SampleWorkQueue` tests.

### 4.2. SampleWorkQueue

A pre-populated `BlockingQueue` of `SampleTask` instances. Populated at construction time with all sample tasks in sequence order.

```java
package org.javai.punit.ptest.concurrent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.javai.outcome.Outcome;
import org.javai.outcome.boundary.Boundary;

/**
 * Shared work queue from which worker threads pull sample tasks.
 *
 * <p>Pre-populated with all sample tasks at construction time. Workers
 * call {@link #take()} to pull the next task. When execution should stop
 * (early termination, budget exhaustion), {@link #shutdown(int)} injects
 * poison pills to unblock waiting workers.
 */
public class SampleWorkQueue {

    private final BlockingQueue<SampleTask> queue;
    private final int totalSamples;

    /**
     * Creates a work queue populated with sample tasks.
     *
     * @param totalSamples number of samples to execute
     * @param inputCount   number of inputs for cycling (0 if no @InputSource)
     */
    public SampleWorkQueue(int totalSamples, int inputCount) {
        this.totalSamples = totalSamples;
        this.queue = new LinkedBlockingQueue<>();
        for (int i = 1; i <= totalSamples; i++) {
            int inputIndex = inputCount > 0 ? (i - 1) % inputCount : -1;
            queue.add(new SampleTask(i, inputIndex));
        }
    }

    /**
     * Pulls the next task, blocking if the queue is empty.
     *
     * @return Ok containing the next sample task (or poison pill), or Fail if interrupted
     */
    public Outcome<SampleTask> take() {
        return Boundary.silent().call("SampleWorkQueue.take", () -> queue.take());
    }

    /**
     * Injects poison pills to unblock and terminate all workers.
     *
     * @param workerCount the number of workers to signal
     */
    public void shutdown(int workerCount) {
        queue.clear();
        for (int i = 0; i < workerCount; i++) {
            queue.add(SampleTask.POISON);
        }
    }

    /** Returns the number of tasks remaining (including not-yet-pulled). */
    public int remaining() {
        return queue.size();
    }

    /** Returns the total number of samples this queue was created for. */
    public int totalSamples() {
        return totalSamples;
    }
}
```

**Module:** punit-core
**Key test scenarios:**
- Queue contains exactly N tasks after construction
- Tasks are in sequence order 1..N
- Input indices cycle correctly for given inputCount
- `shutdown()` clears remaining tasks and injects poison pills
- `take()` returns `Outcome.Ok` containing poison after shutdown
- `take()` returns `Outcome.Fail` when the thread is interrupted

### 4.3. StagedResult

A value object carrying the outcome of a single sample execution.

```java
package org.javai.punit.ptest.concurrent;

import org.javai.punit.ptest.engine.SampleExecutor.SampleResult;

/**
 * A completed sample result keyed by its sequence index for ordered aggregation.
 *
 * @param sequenceIndex the 1-based sequence index from the original SampleTask
 * @param sampleResult  the execution outcome (pass/fail/abort)
 * @param latencyMs     wall-clock execution time in milliseconds
 * @param functionalPassed  functional dimension result (null if not asserted)
 * @param latencyPassed     latency dimension result (null if not asserted)
 * @param dynamicTokens     tokens consumed in dynamic token mode (0 if not applicable)
 */
public record StagedResult(
        int sequenceIndex,
        SampleResult sampleResult,
        long latencyMs,
        Boolean functionalPassed,
        Boolean latencyPassed,
        long dynamicTokens
) {}
```

**Module:** punit-core
**Note:** This record captures everything the consumer needs to feed into the aggregator and budget monitors, eliminating any need for the consumer to reach back into thread-local state.

### 4.4. ResultStaging

Thread-safe staging structure where workers deposit results and the consumer drains them in sequence order.

```java
package org.javai.punit.ptest.concurrent;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe staging area for sample results awaiting ordered consumption.
 *
 * <p>Workers deposit results at arbitrary times (keyed by sequence index).
 * The consumer calls {@link #drainNext()} to retrieve results in strict
 * sequence order (1, 2, 3, ...). If the next expected result is not yet
 * available, {@link #drainNext()} returns empty.
 *
 * <p>For blocking drain semantics, use {@link #awaitNext(long)} which
 * polls with a timeout.
 */
public class ResultStaging {

    private final ConcurrentHashMap<Integer, StagedResult> results = new ConcurrentHashMap<>();
    private final AtomicInteger nextExpected = new AtomicInteger(1);

    /**
     * Deposits a completed result. Called by worker threads.
     *
     * @param result the staged result to deposit
     */
    public void deposit(StagedResult result) {
        results.put(result.sequenceIndex(), result);
    }

    /**
     * Attempts to drain the next result in sequence order.
     *
     * @return the next sequential result, or empty if not yet available
     */
    public Optional<StagedResult> drainNext() {
        int expected = nextExpected.get();
        StagedResult result = results.remove(expected);
        if (result != null) {
            nextExpected.incrementAndGet();
            return Optional.of(result);
        }
        return Optional.empty();
    }

    /**
     * Blocks until the next sequential result is available or timeout expires.
     *
     * @param timeoutMs maximum time to wait in milliseconds
     * @return the next sequential result, or empty if timeout expired or interrupted
     */
    public Optional<StagedResult> awaitNext(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Optional<StagedResult> result = drainNext();
            if (result.isPresent()) {
                return result;
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** Returns the next expected sequence index. */
    public int nextExpectedIndex() {
        return nextExpected.get();
    }

    /** Returns the number of results currently staged but not yet drained. */
    public int pendingCount() {
        return results.size();
    }
}
```

**Module:** punit-core
**Key test scenarios:**
- Deposit and drain in order returns results sequentially
- Out-of-order deposits are held until their turn
- `drainNext()` returns empty when next result is not yet deposited
- `awaitNext()` blocks and returns when result becomes available
- `awaitNext()` returns empty on timeout
- Concurrent deposits from multiple threads do not corrupt state

### 4.5. WorkerTask

The `Runnable` executed by each virtual thread. Pulls tasks from the work queue, executes samples, deposits results into staging.

```java
package org.javai.punit.ptest.concurrent;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runnable executed by each worker virtual thread.
 *
 * <p>Each worker pulls tasks from the shared work queue, executes the sample
 * via a provided {@link SampleInvoker}, and deposits the result into the
 * staging area. Workers continue until they receive a poison pill, the stop
 * signal is set, or the queue is exhausted.
 *
 * <p>Workers are decoupled from aggregation policy — they know nothing about
 * pass rates, early termination, or budgets. They execute and deposit.
 */
public class WorkerTask implements Runnable {

    private final SampleWorkQueue workQueue;
    private final ResultStaging staging;
    private final SampleInvoker invoker;
    private final AtomicBoolean stopSignal;

    /**
     * @param workQueue  shared queue to pull tasks from
     * @param staging    staging area to deposit results into
     * @param invoker    callback that executes a single sample
     * @param stopSignal shared flag set by the consumer to stop workers
     */
    public WorkerTask(SampleWorkQueue workQueue,
                      ResultStaging staging,
                      SampleInvoker invoker,
                      AtomicBoolean stopSignal) {
        this.workQueue = workQueue;
        this.staging = staging;
        this.invoker = invoker;
        this.stopSignal = stopSignal;
    }

    @Override
    public void run() {
        while (!stopSignal.get()) {
            Outcome<SampleTask> taskOutcome = workQueue.take();
            if (taskOutcome.isFail()) {
                break;
            }
            SampleTask task = taskOutcome.getOrThrow();
            if (task.isPoison()) {
                break;
            }
            if (stopSignal.get()) {
                break;
            }

            StagedResult result = invoker.execute(task);
            staging.deposit(result);

            if (result.sampleResult().shouldAbort()) {
                stopSignal.set(true);
                break;
            }
        }
    }
}
```

**Module:** punit-core
**Key test scenarios:**
- Worker pulls tasks and deposits results until queue is empty
- Worker stops on poison pill
- Worker stops when stopSignal is set
- Worker stops on abort result (Error/RuntimeException from sample)
- Worker exits cleanly when `take()` returns `Outcome.Fail`

### 4.6. SampleInvoker (Functional Interface)

The bridge between the worker and the actual test invocation. Defined in punit-core as an interface; implemented in punit-junit5 to wrap JUnit's `Invocation<Void>` mechanism.

```java
package org.javai.punit.ptest.concurrent;

/**
 * Executes a single sample and returns the result.
 *
 * <p>This interface decouples the worker thread from the JUnit 5 invocation
 * mechanism. The punit-junit5 module provides an implementation that wraps
 * JUnit's {@code Invocation<Void>} and the existing {@code SampleExecutor}.
 */
@FunctionalInterface
public interface SampleInvoker {

    /**
     * Executes the sample identified by the given task.
     *
     * @param task the sample task containing sequence index and input index
     * @return the staged result of execution
     */
    StagedResult execute(SampleTask task);
}
```

**Module:** punit-core

### 4.7. ResultConsumer

The single-threaded consumer that drains the staging area in sequence order, feeds results to the aggregator, and evaluates early termination and budget constraints.

```java
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
            if (result.sampleResult().shouldAbort()) {
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
        // Give in-flight workers a brief window to deposit their results
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
        if (result.sampleResult().passed()) {
            aggregator.recordSuccess(result.latencyMs());
        } else {
            aggregator.recordFailure(result.sampleResult().failure());
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
```

**Module:** punit-core
**Key test scenarios:**
- Consumes all N results in sequence order, aggregator reflects correct counts
- Triggers early termination (impossibility) and signals stop
- Triggers early termination (success guaranteed) and signals stop
- Triggers budget exhaustion and signals stop, with correct forced-failure behaviour
- Drains in-flight results after stop signal
- Handles abort result (propagated Error/RuntimeException)
- Concurrent deposit from multiple workers, consumer drains correctly

### 4.8. BudgetExhaustionHandler (Functional Interface)

Extracts the budget exhaustion policy decision from the consumer to allow independent testing. The implementation in punit-junit5 delegates to `BudgetOrchestrator.determineBehavior()`.

```java
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
```

**Module:** punit-core

### 4.9. ConcurrentSampleOrchestrator

The top-level orchestrator in `punit-junit5` that wires all components together and manages the virtual thread lifecycle. This is the integration point between JUnit 5's extension model and the concurrent execution engine.

```java
package org.javai.punit.ptest.concurrent;

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
 *   <li>Warmup phase — sequential, using existing SampleExecutor</li>
 *   <li>Populate work queue with sample tasks</li>
 *   <li>Start N worker virtual threads</li>
 *   <li>Consumer drains results, evaluates policy, signals stop</li>
 *   <li>Join all workers</li>
 *   <li>Return execution summary for verdict computation</li>
 * </ol>
 *
 * <p>This class lives in punit-junit5 because it bridges the JUnit 5
 * invocation model (Invocation&lt;Void&gt;, ExtensionContext) with the
 * JUnit-free concurrent engine in punit-core.
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

    // ... constructor accepting all dependencies ...

    /**
     * Executes all samples concurrently and blocks until completion.
     *
     * @param totalSamples the number of samples to execute
     * @param inputCount   number of inputs for cycling (0 if no @InputSource)
     * @return the execution result summary
     */
    public ExecutionResult execute(int totalSamples, int inputCount) {
        SampleWorkQueue workQueue = new SampleWorkQueue(totalSamples, inputCount);
        ResultStaging staging = new ResultStaging();
        AtomicBoolean stopSignal = new AtomicBoolean(false);

        int effectiveWorkers = Math.min(maxConcurrent, totalSamples);

        // Start workers on virtual threads
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> workers = new java.util.ArrayList<>();
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
```

**Module:** punit-junit5
**Key test scenarios:**
- Executes N samples with maxConcurrent = 1 (degenerate case)
- Executes N samples with maxConcurrent > 1
- Worker count is capped at min(maxConcurrent, totalSamples)
- Early termination stops workers and drains in-flight results
- Budget exhaustion stops workers
- Abort result is captured and returned
- All workers complete before `execute()` returns

## 5. Integration with Existing Execution Flow

### 5.1. Current Flow (to be replaced)

```
ProbabilisticTestExtension.provideTestTemplateInvocationContexts()
  → generates Stream<TestTemplateInvocationContext>
  → JUnit calls interceptTestTemplateMethod() once per sample
    → BernoulliTrialsStrategy.intercept()
      → SampleExecutor.execute()
      → early termination / budget / completion checks
  → finalizeProbabilisticTest()
```

### 5.2. New Flow

```
ProbabilisticTestExtension.provideTestTemplateInvocationContexts()
  → generates a SINGLE TestTemplateInvocationContext
  → JUnit calls interceptTestTemplateMethod() ONCE
    → BernoulliTrialsStrategy.intercept() detects concurrent mode
      → warmup phase (sequential, existing SampleExecutor)
      → ConcurrentSampleOrchestrator.execute()
        → workers pull from SampleWorkQueue
        → workers invoke SampleInvoker.execute()
        → workers deposit into ResultStaging
        → ResultConsumer drains → aggregator → policy checks
      → returns ExecutionResult
    → returns InterceptResult based on ExecutionResult
  → finalizeProbabilisticTest() (unchanged)
```

### 5.3. Key Architectural Decision: Single Invocation Context

The current design generates one `TestTemplateInvocationContext` per sample. JUnit 5 then calls the interceptor once per context. This is fundamentally incompatible with our execution model where the framework owns the concurrency.

The new design generates a **single invocation context** that represents the entire concurrent execution. The interceptor is called once, orchestrates all samples internally, and returns a single `InterceptResult`. This eliminates JUnit's per-sample iteration loop entirely — the framework owns the execution lifecycle.

This means:
- `provideTestTemplateInvocationContexts()` returns a `Stream` of exactly one context (plus warmup contexts if needed — but see section 5.4)
- The interceptor for that single context runs the entire concurrent execution
- `finalizeProbabilisticTest()` is called once, as before

### 5.4. Warmup Integration

Warmup executions remain sequential (REQ-23). They are executed **within** the interceptor, before the concurrent orchestrator is started. The existing `SampleExecutor.executeWarmup()` method is reused unchanged.

The warmup invocations can no longer be modelled as separate `WarmupInvocationContext` instances in the stream — they are executed internally before `ConcurrentSampleOrchestrator.execute()` is called. This simplifies the warmup gate logic in `BernoulliTrialsStrategy` significantly.

### 5.5. SampleInvoker Implementation

The `SampleInvoker` implementation in punit-junit5 must bridge from `SampleTask` to the actual test method invocation. Since we are no longer using JUnit's per-sample `Invocation<Void>` mechanism, the invoker must create its own invocation of the test method.

```java
/**
 * SampleInvoker implementation that invokes the test method reflectively.
 *
 * <p>Wraps the existing SampleExecutor logic: assertion scope management,
 * latency measurement, exception handling, and dimension result capture.
 */
class ReflectiveSampleInvoker implements SampleInvoker {

    private final Object testInstance;
    private final Method testMethod;
    private final ExceptionHandling exceptionPolicy;
    private final List<Object> inputs;       // null if no @InputSource
    private final TokenChargeRecorder tokenRecorder;

    @Override
    public StagedResult execute(SampleTask task) {
        // 1. Resolve input parameter (if applicable)
        Object input = resolveInput(task);

        // 2. Reset token recorder
        if (tokenRecorder != null) {
            tokenRecorder.resetForNextSample();
        }

        // 3. Execute with assertion scope and latency measurement
        AssertionScope.begin();
        try {
            long startNanos = System.nanoTime();
            invokeTestMethod(testInstance, testMethod, input, tokenRecorder);
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

            return new StagedResult(
                    task.sequenceIndex(),
                    SampleResult.ofSuccess(),
                    latencyMs,
                    captureFunctionalResult(),
                    captureLatencyResult(),
                    captureTokens()
            );
        } catch (AssertionError e) {
            return new StagedResult(
                    task.sequenceIndex(),
                    SampleResult.ofFailure(e),
                    0,
                    captureFunctionalResult(),
                    captureLatencyResult(),
                    captureTokens()
            );
        } catch (Throwable t) {
            if (exceptionPolicy == ExceptionHandling.ABORT_TEST) {
                return new StagedResult(
                        task.sequenceIndex(),
                        SampleResult.ofAbort(t),
                        0, null, null, 0
                );
            }
            return new StagedResult(
                    task.sequenceIndex(),
                    SampleResult.ofFailure(t),
                    0,
                    captureFunctionalResult(),
                    captureLatencyResult(),
                    captureTokens()
            );
        } finally {
            AssertionScope.end();
        }
    }
}
```

**Module:** punit-junit5
**Note:** `AssertionScope` is thread-local, so each worker's assertion scope is isolated.

## 6. Configuration Validation

### 6.1. maxConcurrent Validation

Validation occurs in `UseCaseAttributes` (punit-core). The current validation allows `>= 0`; this must be updated to `>= 1` per REQ-03.

**Change:** `UseCaseAttributes` compact constructor:
```java
// Before:
if (maxConcurrent < 0) { throw ... }

// After:
if (maxConcurrent < 1) { throw ... }
```

The `DEFAULT` constant changes from `new UseCaseAttributes(0, 0)` to `new UseCaseAttributes(0, 1)`.

### 6.2. @UseCase Annotation

**Change:** `UseCase.maxConcurrent()` default from `0` to `1`. Javadoc updated per REQ-01.

### 6.3. JUnit 5 Parallel Execution Guard (REQ-06)

Checked in `ProbabilisticTestExtension.provideTestTemplateInvocationContexts()` when `maxConcurrent > 1`:

```java
if (maxConcurrent > 1) {
    String parallelEnabled = System.getProperty("junit.jupiter.execution.parallel.enabled");
    if ("true".equalsIgnoreCase(parallelEnabled)) {
        throw new ExtensionConfigurationException(
                "maxConcurrent > 1 is incompatible with JUnit 5 parallel test execution "
                + "(junit.jupiter.execution.parallel.enabled=true). "
                + "Disable JUnit parallelism or set maxConcurrent = 1.");
    }
}
```

### 6.4. Pacing Mutual Exclusivity Guard

Pacing (rate limiting) and concurrency are alternative mechanisms for avoiding API overload. Per-worker pacing cannot enforce a global dispatch rate when multiple workers are active (see design discussion). The two mechanisms are therefore mutually exclusive: if `maxConcurrent > 1`, pacing must not be configured.

Checked in `ProbabilisticTestExtension.provideTestTemplateInvocationContexts()`:

```java
if (maxConcurrent > 1 && pacing != null && pacing.hasConstraints()) {
    throw new ExtensionConfigurationException(
            "Pacing and maxConcurrent > 1 are mutually exclusive. "
            + "Use pacing to limit dispatch rate (sequential execution), "
            + "or use maxConcurrent to limit concurrent invocations, but not both.");
}
```

### 6.5. @Timeout Warning (REQ-39)

Checked in `ProbabilisticTestExtension.provideTestTemplateInvocationContexts()`:

```java
Method testMethod = context.getRequiredTestMethod();
Class<?> testClass = context.getRequiredTestClass();
if (AnnotationSupport.isAnnotated(testMethod, org.junit.jupiter.api.Timeout.class)
        || AnnotationSupport.isAnnotated(testClass, org.junit.jupiter.api.Timeout.class)) {
    logger.warn("JUnit @Timeout is active on this probabilistic test. "
            + "If the timeout fires, the statistical verdict will be incomplete. "
            + "PUnit's budget mechanism is the recommended alternative.");
}
```

## 7. Covariate Integration (REQ-32, REQ-33)

`maxConcurrent` must be recorded as a covariate wherever other covariates appear.

### 7.1. Affected Classes

| Class                             | Change                                                                      |
|-----------------------------------|-----------------------------------------------------------------------------|
| `UseCaseCovariateExtractor`       | Extract `maxConcurrent` from `@UseCase` annotation as a covariate entry     |
| `CovariateProfileResolver`        | Include `maxConcurrent` in the covariate profile used for baseline matching |
| `MeasureSpecGenerator`            | Write `maxConcurrent` into the spec YAML                                    |
| `ResultPublisher`                 | Include `maxConcurrent` in console verdict output                           |
| `VerdictTextRenderer`             | Include `maxConcurrent` in verbose verdict rendering                        |
| `ProbabilisticTestVerdictBuilder` | Carry `maxConcurrent` into the verdict model                                |

### 7.2. Spec YAML Format

```yaml
useCaseId: shopping.product.search
covariates:
  maxConcurrent: 10
  # ... other covariates
samples: 1000
observedPassRate: 0.97
```

## 8. Impact on Experiment Execution

The experiment strategies (`MeasureStrategy`, `ExploreStrategy`, `OptimizeStrategy`) execute samples via a similar interceptor pattern. The concurrent execution model applies to experiments identically — `maxConcurrent` is resolved from the `@UseCase` annotation and the same `ConcurrentSampleOrchestrator` is used.

### 8.1. MeasureStrategy

No change to the strategy itself. The concurrent orchestrator replaces the sequential sample loop. The spec generation code consumes `SampleResultAggregator` as before.

### 8.2. ExploreStrategy

Each factor combination is a separate execution run. Within each run, samples execute concurrently up to `maxConcurrent`. Between runs (different factor combinations), execution is sequential.

### 8.3. OptimizeStrategy

Each iteration is a separate execution run. Within each iteration, samples execute concurrently. Between iterations, execution is sequential (the optimizer needs the previous iteration's results to determine the next control value).

## 9. Classes Unchanged

The following classes require **no modifications**:

| Class                       | Reason                                       |
|-----------------------------|----------------------------------------------|
| `SampleResultAggregator`    | Fed by consumer thread only, single-threaded |
| `EarlyTerminationEvaluator` | Stateless, called by consumer thread only    |
| `BudgetOrchestrator`        | Called by consumer thread only               |
| `CostBudgetMonitor`         | Accessed by consumer thread only             |
| `SharedBudgetMonitor`       | Already uses `AtomicLong`                    |
| `SuiteBudgetManager`        | Already thread-safe                          |
| `FinalVerdictDecider`       | Consumes aggregator post-execution           |
| `ResultPublisher`           | Renders once after execution completes       |
| `VerdictTextRenderer`       | Renders once after execution completes       |
| `ProbabilisticTestVerdict`  | Value object, constructed post-execution     |

## 10. Classes Modified

| Class                             | Module       | Change                                                                                                                                                 |
|-----------------------------------|--------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| `UseCase`                         | punit-core   | `maxConcurrent` default `0` → `1`, javadoc update                                                                                                      |
| `UseCaseAttributes`               | punit-core   | Validation `>= 0` → `>= 1`, `DEFAULT` uses `maxConcurrent = 1`                                                                                         |
| `BernoulliTrialsStrategy`         | punit-junit5 | `intercept()` delegates to `ConcurrentSampleOrchestrator` instead of sequential `SampleExecutor`; `provideInvocationContexts()` returns single context |
| `ProbabilisticTestExtension`      | punit-junit5 | Parallel execution guard (REQ-06), `@Timeout` warning (REQ-39), single invocation context handling                                                     |
| `UseCaseCovariateExtractor`       | punit-core   | Extract `maxConcurrent` as covariate                                                                                                                   |
| `MeasureSpecGenerator`            | punit-junit5 | Write `maxConcurrent` into spec YAML                                                                                                                   |
| `ProbabilisticTestVerdictBuilder` | punit-core   | Include `maxConcurrent` in verdict model                                                                                                               |
| `ExperimentExtension`             | punit-junit5 | Concurrent execution for experiment sample loops                                                                                                       |

## 11. New Classes Summary

| Class                          | Module       | Package            | Responsibility                                                  |
|--------------------------------|--------------|--------------------|-----------------------------------------------------------------|
| `SampleTask`                   | punit-core   | `ptest.concurrent` | Value object: sequence index + input index                      |
| `SampleWorkQueue`              | punit-core   | `ptest.concurrent` | Pre-populated blocking queue of sample tasks                    |
| `StagedResult`                 | punit-core   | `ptest.concurrent` | Value object: sample outcome keyed by sequence index            |
| `ResultStaging`                | punit-core   | `ptest.concurrent` | Thread-safe deposit/drain structure for ordered consumption     |
| `WorkerTask`                   | punit-core   | `ptest.concurrent` | Runnable: pull task → invoke → deposit result → repeat          |
| `SampleInvoker`                | punit-core   | `ptest.concurrent` | Functional interface: executes a sample, returns `StagedResult` |
| `ResultConsumer`               | punit-core   | `ptest.concurrent` | Single-threaded: staging → aggregator → policy checks           |
| `BudgetExhaustionHandler`      | punit-core   | `ptest.concurrent` | Functional interface: budget exhaustion policy decision         |
| `ConcurrentSampleOrchestrator` | punit-junit5 | `ptest.concurrent` | Top-level lifecycle: warmup → workers → consumer → join         |
| `ReflectiveSampleInvoker`      | punit-junit5 | `ptest.concurrent` | `SampleInvoker` impl: reflective test method invocation         |

## 12. Test Strategy

### 12.1. Unit Tests (per component)

Each punit-core class is tested in isolation:

- `SampleWorkQueueTest` — population, ordering, shutdown, poison pills
- `ResultStagingTest` — concurrent deposit/drain, ordering guarantees, timeout behaviour
- `WorkerTaskTest` — pull/execute/deposit loop, stop signal, abort handling
- `ResultConsumerTest` — aggregation, early termination, budget exhaustion, drain-in-flight

### 12.2. Integration Tests

- `ConcurrentSampleOrchestratorTest` — end-to-end with mock `SampleInvoker`:
  - `maxConcurrent = 1` produces identical results to current sequential execution
  - `maxConcurrent > 1` produces identical aggregation to sequential (deterministic ordering)
  - Early termination stops all workers and drains in-flight
  - Budget exhaustion stops execution
  - Abort propagates correctly

### 12.3. Regression via Existing Tests

Per REQ-30, the unified infrastructure means all existing `@ProbabilisticTest` tests exercise the concurrent code path with `maxConcurrent = 1`. This provides broad regression coverage without writing new tests.

### 12.4. TestKit Tests

Test subjects in `testsubjects/` that exercise probabilistic tests via JUnit TestKit will implicitly validate the concurrent infrastructure. Additional test subjects with `maxConcurrent > 1` can be added to verify concurrent execution end-to-end.

## 13. Thread Safety Analysis

| Component                   | Thread Safety                                      | Accessed By                         |
|-----------------------------|----------------------------------------------------|-------------------------------------|
| `SampleWorkQueue`           | Thread-safe (`LinkedBlockingQueue`)                | Workers (take), consumer (shutdown) |
| `ResultStaging`             | Thread-safe (`ConcurrentHashMap`, `AtomicInteger`) | Workers (deposit), consumer (drain) |
| `SampleResultAggregator`    | Not thread-safe — **consumer thread only**         | Consumer                            |
| `EarlyTerminationEvaluator` | Stateless (immutable)                              | Consumer                            |
| `CostBudgetMonitor`         | Not thread-safe — **consumer thread only**         | Consumer                            |
| `SharedBudgetMonitor`       | Thread-safe (`AtomicLong`)                         | Consumer, other test methods        |
| `AtomicBoolean stopSignal`  | Thread-safe                                        | Workers (read), consumer (write)    |
| `AssertionScope`            | Thread-local                                       | Each worker independently           |
| `TokenChargeRecorder`       | Per-sample reset — **worker thread only**          | Workers                             |
