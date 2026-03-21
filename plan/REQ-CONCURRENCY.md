# REQ-CONCURRENCY: Concurrent Sample Execution

## 1. Summary

PUnit executes sample invocations sequentially. When sample sizes are high and invocations are I/O-bound (e.g., LLM APIs, payment gateways), this results in long execution times. This feature introduces controlled concurrent execution of samples within a single test or experiment, governed by a `maxConcurrent` attribute on `@UseCase`.

## 2. Motivation

A probabilistic test with 1,000 samples against an LLM API averaging 2 seconds per call takes over 33 minutes sequentially. With `maxConcurrent = 10`, the same test completes in approximately 3.5 minutes (assuming the API can sustain the load). For experiments — especially explore and optimise modes, which may run thousands of invocations across multiple configurations — the time savings are even more significant.

The bottleneck is I/O wait, not CPU. Virtual threads (JDK 21+) are the natural fit: they provide concurrency without heavyweight thread pool management, and they align with the I/O-bound nature of the workload.

## 3. Requirements

### 3.1. Configuration

| #      | Requirement                                                                                                                                                                                                                                                                                                           |
|--------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-01 | `@UseCase` gains an `int maxConcurrent() default 1` attribute.                                                                                                                                                                                                                                                        |
| REQ-02 | `maxConcurrent = 1` (the default) preserves current sequential execution behaviour. No behavioural change for existing users.                                                                                                                                                                                         |
| REQ-03 | `maxConcurrent` must be `>= 1`. Values `< 1` are rejected at configuration resolution time with `IllegalArgumentException`.                                                                                                                                                                                           |
| REQ-04 | `maxConcurrent` is a property of the use case, not of the test or experiment. It applies uniformly to all tests and experiments that reference the use case.                                                                                                                                                          |
| REQ-05 | `maxConcurrent` is not overridable by experiments, system properties, or environment variables. The use case author sets it; it is law. Rationale: it represents the concurrency the underlying service is prepared to handle. Overriding it risks throttling, rate-limit violations, or corrupted experimental data. |
| REQ-06 | `maxConcurrent > 1` and JUnit 5 parallel test execution (`junit.jupiter.execution.parallel.enabled = true`) are **mutually exclusive**. If both are active, the framework throws a configuration exception with a clear error message. Rationale: JUnit-level parallelism could cause multiple test methods to each spawn concurrent workers against the same use case, exceeding the service's concurrency tolerance in ways the `maxConcurrent` limit cannot control. |

### 3.2. Execution Model

| #      | Requirement                                                                                                                                                                                 |
|--------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-07 | Execution follows a **shared work queue with persistent workers** model. All samples are placed in a single shared queue. Up to `maxConcurrent` worker threads pull samples from the queue, execute them, and deposit results into a thread-safe result staging structure. Workers continue pulling until the queue is empty or a stop signal is received. |
| REQ-08 | Each sample is assigned a **sequence index** (1, 2, 3, ...) when placed in the work queue. Results are keyed by sequence index in the staging structure and fed to the aggregator in sequence order. |
| REQ-09 | The aggregated result sequence is deterministic and identical in ordering to what sequential execution would produce, regardless of completion order across workers.                        |
| REQ-10 | Concurrent samples are executed on **virtual threads**. The implementation uses `Executors.newVirtualThreadPerTaskExecutor()` or `Thread.ofVirtual()` — both are final APIs in JDK 21. `StructuredTaskScope` is not used (preview API in JDK 21, and the project does not enable preview features). |

### 3.3. Exception Handling

| #      | Requirement                                                                                                                                                                                 |
|--------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-11 | Subclasses of `Error` (e.g., `OutOfMemoryError`) and `RuntimeException` thrown from sample execution are **allowed to propagate**. The framework emulates JUnit's behaviour when these exception types are thrown from a test method body. |
| REQ-12 | `Error` subclasses indicate conditions that only the Java runtime in concert with the OS can handle. `RuntimeException` subclasses indicate defects. Neither is treated as a sample failure — they abort execution. |

### 3.4. Early Termination

| #      | Requirement                                                                                                                                                                                 |
|--------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-13 | Early termination is evaluated by the **single consumer** after each result is fed to the aggregator. When the consumer determines that early termination is warranted, it signals all worker threads to stop. |
| REQ-14 | If early termination is triggered, workers complete their current in-flight sample (already executing) but do not pull further samples from the queue. |
| REQ-15 | In-flight samples that complete after the stop signal are included in the final aggregation. Their results are deposited in the staging structure and drained by the consumer as normal. |
| REQ-16 | Samples remaining in the work queue after early termination are not executed. Early termination and its impact on the sample count are already reported in the statistical verdict. No additional complexity is introduced to handle the difference in input coverage between sequential and concurrent runs — `maxConcurrent` is recorded as a covariate, so baselines gathered under different concurrency levels are never conflated. |

### 3.5. Budget Tracking

| #      | Requirement                                                                                                                                                                                                                        |
|--------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-17 | Budget checks are performed by the single consumer thread after each result is aggregated, consistent with the early termination evaluation point. If budget is exhausted, the consumer signals workers to stop.                   |
| REQ-18 | Method-level budget tracking (`CostBudgetMonitor`) remains single-threaded — it is accessed only by the consumer thread. No thread-safety changes are required.                                                                    |
| REQ-19 | Class-level and suite-level budget monitors (`SharedBudgetMonitor`, `SuiteBudgetManager`) already use `AtomicLong` and require no changes.                                                                                         |
| REQ-20 | Workers check remaining budget before pulling the next sample from the queue. If the remaining budget is insufficient, the worker stops. This provides an early exit without waiting for the consumer to signal.                    |

### 3.6. Pacing

| #      | Requirement                                                                                                                                                                                                                                                                |
|--------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-21 | Each worker thread independently paces itself: after completing a sample execution, it waits for a duration dictated by the pacing strategy before pulling the next sample from the queue. The aggregate dispatch rate across all workers is `maxConcurrent / pacingInterval`. |
| REQ-22 | Pacing applies during warmup identically to counted samples.                                                                                                                                                                                                               |

### 3.7. Warmup

| #      | Requirement                                                                                                                                                                                                                  |
|--------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-23 | Warmup invocations execute **sequentially**, regardless of `maxConcurrent`. Rationale: warmup conditions the system to steady state (cache warming, connection pool initialisation). Concurrent warmup may not achieve this. |
| REQ-24 | Concurrent execution begins only after warmup completes.                                                                                                                                                                     |

### 3.8. Result Aggregation

| #      | Requirement                                                                                                                                                                                                                 |
|--------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-25 | `SampleResultAggregator` remains single-threaded. Worker threads deposit results into a thread-safe staging structure (e.g., `ConcurrentHashMap<Integer, Result>`) keyed by sequence index. A single consumer thread drains the staging structure in sequence order into the aggregator. |
| REQ-26 | The consumer drains **eagerly** — as results arrive, not after all workers finish. This allows early termination and budget checks to fire as soon as the condition is met, rather than waiting for all workers to complete. |
| REQ-27 | No changes to the statistical model. The binomial proportion estimator assumes i.i.d. trials; this assumption is the user's responsibility and is documented (see REQ-36).                                                  |

### 3.9. Serialised Output

| #      | Requirement                                                                                                                                                                                                                                                                                                                        |
|--------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-28 | All framework output (progress reporting, verdict rendering, diagnostic messages) is serialised through a **singleton output queue** scoped to the test run. A dedicated consumer thread reads from the queue and writes sequentially. No framework component writes directly to a shared output sink during concurrent execution. This follows the same concurrent-producers / single-threaded-consumer pattern as the result staging structure (REQ-25). |
| REQ-29 | The output queue and consumer thread are lifecycle-managed: created at suite start, drained and shut down at suite end.                                                                                                                                                                                                            |
| REQ-30 | The execution infrastructure (shared work queue, worker threads, result staging, single consumer) is **unified** for all values of `maxConcurrent`, including `maxConcurrent = 1`. There is no separate sequential code path. Rationale: (1) every existing test implicitly exercises the concurrent infrastructure, providing broad integration coverage for free; (2) a single code path eliminates branching in the engine and ensures behavioural equivalence; (3) the overhead of a queue with one worker and one consumer on virtual threads is negligible for I/O-bound workloads. |
| REQ-31 | User code executing within samples that writes to shared resources (log files, databases, external services) is the user's responsibility to manage. Documentation must call this out explicitly (see REQ-38).                                                                                                                     |

### 3.10. Covariate and Baseline Scoping

| #      | Requirement                                                                                                                                                                                      |
|--------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-32 | `maxConcurrent` is a **covariate** for baseline identification. A baseline established at `maxConcurrent = 5` is only valid for evaluating results gathered at `maxConcurrent = 5`.              |
| REQ-33 | `maxConcurrent` must be **recorded** in spec YAML files, console verdict output, and report output. It is subject to the same rules and emphasis as other covariates — it appears wherever covariates appear. |
| REQ-34 | `maxConcurrent` may also serve as an experimental **factor** — the two roles are not mutually exclusive. An experiment may vary concurrency to characterise its impact on the system under test. |

### 3.11. Documentation

| #      | Requirement                                                                                                                                                                                                                                                                                                                                     |
|--------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-35 | Documentation must clearly state that `maxConcurrent > 1` changes the execution profile of the system under test. Baselines and thresholds established at one concurrency level may not transfer to another. Concurrent requests may interact with shared state (caches, connection pools, rate limiters) differently than sequential requests. |
| REQ-36 | Documentation must explain that `maxConcurrent` is not overridable and the rationale: it represents a constraint of the underlying service, not a performance tuning parameter.                                                                                                                                                                 |
| REQ-37 | Documentation must state that `maxConcurrent > 1` is mutually exclusive with JUnit 5 parallel test execution, and the rationale: the framework cannot enforce the use case's concurrency limit when JUnit itself is parallelising test methods. |
| REQ-38 | Documentation must state that user code executing within concurrent samples is responsible for its own thread safety. The framework serialises its own output but cannot protect user-managed shared resources (log files, databases, diagnostic collections).                                                                                  |

## 4. Non-Requirements

| #     | Exclusion                                                                                                                                                                                                                                               |
|-------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| NR-01 | **Experiment-level override of `maxConcurrent`** — rejected. The use case author sets concurrency based on what the service can handle. Overriding it risks throttling and produces data gathered under conditions the service was not designed for.    |
| NR-02 | **Adaptive concurrency** (auto-scaling based on observed latency or error rates) — deferred. Static configuration is sufficient for the initial implementation.                                                                                         |
| NR-03 | **Cancelling in-flight samples on early termination** — rejected. Workers complete their current sample. The cost is bounded by `maxConcurrent` in-flight invocations, and cancellation introduces complexity around interrupted I/O and partial charges. |
| NR-04 | **Thread-safe `SampleResultAggregator`** — not required. The shared-queue model feeds results to the aggregator via a single consumer thread, avoiding the need for concurrent writes.                                                                  |
| NR-05 | **`StructuredTaskScope`** — not used. It is a preview API in JDK 21 and the project does not enable preview features.                                                                                                                                   |

## 5. User-Facing API

```java
@UseCase(value = "shopping.product.search", warmup = 5, maxConcurrent = 10)
public class ShoppingUseCase {
    // Up to 10 worker threads pull samples from a shared queue
    // Warmup runs sequentially before concurrent execution begins
}
```

No changes to test or experiment method bodies. No changes to `@ProbabilisticTest`, `@MeasureExperiment`, `@ExploreExperiment`, or `@OptimizeExperiment` annotations.

## 6. Execution Architecture

```
                    ┌─────────────────────┐
                    │  Shared Work Queue   │
                    │  [1, 2, 3, ..., N]   │
                    └────┬────┬────┬───────┘
                         │    │    │
                    pull │    │    │ pull
                         ▼    ▼    ▼
                    ┌────┐ ┌────┐ ┌────┐
                    │ W1 │ │ W2 │ │ W3 │   (virtual threads)
                    └──┬─┘ └──┬─┘ └──┬─┘
                       │      │      │
                deposit│      │      │ deposit
                       ▼      ▼      ▼
                    ┌─────────────────────┐
                    │  Result Staging      │
                    │  (thread-safe, keyed │
                    │   by sequence index) │
                    └──────────┬──────────┘
                               │
                    drain in   │ sequence order
                               ▼
                    ┌─────────────────────┐
                    │  Single Consumer     │
                    │  ┌─────────────────┐ │
                    │  │ Aggregator      │ │
                    │  │ (single-thread) │ │
                    │  └─────────────────┘ │
                    │  ┌─────────────────┐ │
                    │  │ Early term check│ │
                    │  └─────────────────┘ │
                    │  ┌─────────────────┐ │
                    │  │ Budget check    │ │
                    │  └─────────────────┘ │
                    └──────────┬──────────┘
                               │
                    stop signal│ (if triggered)
                               ▼
                         Workers stop
                         pulling from queue
```

Workers are decoupled from aggregation policy. They execute samples, deposit results, and pull the next item. All policy decisions (early termination, budget enforcement) live on the consumer side, single-threaded, exactly as in sequential mode.

## 7. Interactions with Existing Features

| Feature           | Interaction                                                                                                                                    |
|-------------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| Early termination | Evaluated by the single consumer after each result. Workers complete in-flight samples but stop pulling. Bounded overshoot of `maxConcurrent` in-flight invocations. |
| Budget tracking   | Checked by the single consumer after each result, and by workers before pulling the next sample.                                               |
| Pacing            | Each worker independently paces itself after completing a sample. Aggregate rate is `maxConcurrent / pacingInterval`.                          |
| Warmup            | Always sequential, completes before concurrent execution begins.                                                                               |
| Covariates        | `maxConcurrent` is a covariate for baseline scoping. Recorded in specs, verdicts, and reports. Changing it invalidates baselines.              |
| Experiments       | `maxConcurrent` may serve as a factor. Explore and optimise experiments use the same concurrency as tests — no override.                       |
| `@InputSource`    | Input cycling respects sequence indices. Sample N uses input `(N % inputCount)`, same as sequential.                                           |
| Token recording   | Dynamic token charges from concurrent samples are deposited in the staging structure and aggregated by the consumer.                           |
| Framework output  | All progress reporting, verdict rendering, and diagnostics are serialised through a singleton output queue. Same infrastructure for all values of `maxConcurrent`. |
| JUnit 5 parallel  | Mutually exclusive with `maxConcurrent > 1`. Framework throws configuration exception if both are active.                                      |
| JUnit `@Timeout`  | Detect-and-warn. If `@Timeout` is present on the test method or its enclosing class, log a warning at test start. Does not block execution. Global timeout defaults (`junit.jupiter.execution.timeout.default`) are not detected — documentation covers this case. |

## 8. Risks

| Risk                                                       | Mitigation                                                                                                                            |
|------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| Service cannot handle declared concurrency                 | `maxConcurrent` is set by the use case author who understands the service's limits. Framework enforces, not overrides.                |
| Early termination overshoot (up to `maxConcurrent` extra)  | Bounded cost. For expensive APIs, users can keep `maxConcurrent` moderate. Document the trade-off.                                    |
| Non-i.i.d. trials due to concurrent execution              | Document that concurrency changes the execution profile. Treat `maxConcurrent` as a baseline-scoping covariate to prevent mismatches. |
| Temporal clustering (concurrent samples hit same transient)| Per-worker pacing partially mitigates. Sequential execution (default) avoids entirely.                                                |
| Interleaved output from concurrent samples                 | Singleton output queue with dedicated consumer thread serialises all framework output. User-side output is the user's responsibility. |
| JUnit parallel + PUnit concurrent overwhelms service       | Mutually exclusive — framework rejects the combination at startup.                                                                    |

### 3.12. JUnit `@Timeout` Interaction

| #      | Requirement                                                                                                                                                                                                                                                                                                           |
|--------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-39 | If `@Timeout` is detected on a `@ProbabilisticTest` method or its enclosing class, the framework logs a warning at test start: JUnit `@Timeout` is active on this probabilistic test — if the timeout fires, the statistical verdict will be incomplete. PUnit's budget mechanism is the recommended alternative.     |
| REQ-40 | The presence of `@Timeout` does **not** prevent execution. The test proceeds normally. If the timeout fires, JUnit's standard timeout failure applies — no special handling by the framework.                                                                                                                         |
| REQ-41 | Global timeout defaults set via `junit.jupiter.execution.timeout.default` in `junit-platform.properties` are **not** detected by the framework. This interaction is covered by documentation only (see REQ-42). Rationale: a large application may have global timeouts that are appropriate for conventional tests but not for probabilistic tests. Detecting this requires reading JUnit platform configuration, which is disproportionate complexity for a documentation-solvable problem. |
| REQ-42 | Documentation must state that JUnit's `@Timeout` and global timeout defaults may produce incomplete verdicts on probabilistic tests, and that PUnit's budget mechanism is the correct way to bound execution time.                                                                                                    |
