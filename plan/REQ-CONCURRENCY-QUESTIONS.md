# REQ-CONCURRENCY: Open Questions

## Q1. Default value: `0` vs `1`

The annotation currently has `default 0` but REQ-01 specifies `default 1`. With `default 0`, the framework's own default is illegal under REQ-03 (`< 1` rejected) without a special case. With `default 1`, the value directly means "sequential" with no sentinel-value decoding.

Recommendation: `default 1` — no current use for a sentinel value given NR-02 (adaptive concurrency deferred) and REQ-05 (no overridability).

**Decision:**

default 1

---

## Q2. Exception handling within concurrent batches

What happens when a sample throws an unexpected exception (not a test assertion failure, but `OutOfMemoryError`, `InterruptedException`, or an unchecked exception from user code)?

- Does one sample's exception poison the whole batch?
- Are other in-flight samples in the batch allowed to complete?
- Is the exception recorded as a sample failure, or does it abort the test?

**Decision:**
Subclasses of `Error` and `RuntimeException` are allowed to propagate. Errors indicate that something's up that only the Java runtime in concert with the OS can handle. Runtime exceptions indicate another defect of some kind. The system should emulate the behaviour of JUnit when one of these exception types is thrown from the body of a test method.  

---

## Q3. `@InputSource` interaction with early termination

With batched execution, if early termination fires after batch 2, you've consumed inputs 0-19 (with `maxConcurrent=10`). In sequential mode, early termination at sample 15 would consume inputs 0-14. The set of inputs exercised differs between sequential and concurrent runs for the same early-termination point. If inputs are not uniformly representative, this could introduce bias.

Is this acceptable? Should it be explicitly documented?

**Decision:**
The early termination must first and foremost be made clear in the statistical verdict. I believe this is already the case.
We will therefore not introduce any complexity to the codebase to handle this situation because of this. 
Furthermore, once a usecase is declared with maxConcurrent > 1 this will be recorded as a covariarte in the baseline. There will therefore never be any confusion between experiments and tests which are run with different values of maxConcurrent. 

---

## Q4. `maxConcurrent` as a recorded value in specs and output

REQ-27 says `maxConcurrent` is a covariate for baseline identification. But there's no requirement that it appears in console verdict output, spec YAML files, or experiment output. If it invalidates baselines, shouldn't the spec file record what `maxConcurrent` was used during measurement?

**Decision:**
This must appear in the console verdict as well as the verdict emitted and ultimately reported in punit-report. It is subject tot the same rules and emphasis as the other covariates in this sense.

---

## Q5. Interaction with JUnit 5 parallel test execution

If a user enables JUnit-level parallelism (`junit.jupiter.execution.parallel.enabled`) and sets `maxConcurrent > 1`, multiple test methods could each spawn concurrent batches simultaneously. If two test methods reference the same `@UseCase` with `maxConcurrent=10`, can 20 concurrent requests hit the service?

REQ-04 says "applies uniformly" but is ambiguous: does each test get its own limit of 10, or is the use case globally capped at 10 across all concurrent tests? Given the rationale (service concurrency tolerance), the latter seems intended but is not stated.

**Decision:**

I suggest the two options are mutually exclusive. If an experiment or a test method is invoked with `maxConcurrent > 1` AND the property `junit.jupiter.execution.parallel.enabled` is set, the framework should throw an exception and declare misconfiguration with a suitably siccinct and clear error message.

---

## Q6. Structured concurrency vs. raw virtual threads

REQ-09 says "virtual threads" but doesn't specify the concurrency primitive. JDK 21 offers `StructuredTaskScope` (preview in 21, finalized in 25) and `ExecutorService.newVirtualThreadPerTaskExecutor()`. The choice affects error propagation, cancellation, and lifecycle. `StructuredTaskScope` requires `--enable-preview` on JDK 21.

Which approach? Any constraint on preview API usage?

**Decision:**
We are targeting JDK21 without previews enabled. I think this precludes the use of `StructuredTaskScope` and `ExecutorService.newVirtualThreadPerTaskExecutor()`.

---

## Q7. Pacing overhead vs. budget

REQ-17 describes stagger-dispatch within a batch. REQ-16 says pre-check budget before dispatching. If pacing staggers dispatch at 100ms intervals, the batch has an inherent duration of `(maxConcurrent - 1) * interval + execution_time`. Should the pre-batch budget check account for pacing overhead when evaluating remaining time budget?

**Decision:**
We've decided to adopt my dispatching approach in a discussion outside this document.

---

## Q8. Graceful shutdown on test cancellation/timeout

JUnit 5 supports `@Timeout` on test methods. If a timeout fires mid-batch, virtual threads may be interrupted. Should the framework register a shutdown hook or lifecycle callback to clean up in-flight virtual threads? Are partial batch results included or discarded on forced termination?

**Decision:**
I think we should simply check for the presence of this @Timeout annotation on a Probabilistic test. If present, we throw an exception indicating a misconfiguraiton. The @Timeout annotation competes with PUnits budgeting mechanism and should not be used in conjunction with it.
