# `@ServiceLatencyTest` — Design Specification

> **See also:** [Implementation Plan](PLAN-SERVICE-LATENCY.md)

---

## 1. Purpose and Scope

### 1.1 Definition

`@ServiceLatencyTest` is a probabilistic regression detector for service latency. It answers a single, carefully framed question:

> *Is the observed latency behavior statistically inconsistent with previously observed, experimentally derived latency behavior, under comparable conditions?*

### 1.2 What It Is

- A statistical deviation detector from empirical baselines
- A tail-focused regression test using exceedance rate analysis
- A cousin annotation to `@ProbabilisticTest`, sharing infrastructure but measuring latency rather than pass/fail

### 1.3 What It Is Not

- Not a functional correctness test
- Not an absolute latency threshold assertion
- Not a trend forecaster or performance predictor
- Not a single-sample inference engine

### 1.4 Non-Goals (Explicit Limits)

`@ServiceLatencyTest` does not:
- Infer performance trends
- Forecast future latency
- Smooth seasonal effects
- Auto-adjust baselines
- Perform inference on single samples

It assumes:
- Baselines are representative
- Experiments are operationally valid
- Environment mismatches are handled explicitly, not statistically

---

## 2. Statistical Model

### 2.1 Foundational Principles

#### 2.1.1 Empirical Baselines Over Assumed Distributions

Real-world service latency distributions are:
- Heavy-tailed
- Skewed
- Often multi-modal
- Sensitive to caching, warm-up, and contention effects
- Often date- and time-dependent

Therefore:
- No parametric distribution is assumed
- All inference is grounded in empirical distributions captured during controlled experiments
- Latency behavior is only comparable within similar operating periods
- The baseline is the sole source of expectation

#### 2.1.2 Uniform Statistical Treatment

Latency observations are collected as an ordered sequence X₁, X₂, ..., Xₙ. However, statistical inference is only reliable when applied to sufficiently sized samples. A single observation cannot support meaningful probabilistic inference.

### 2.2 Tail Exceedance Regression

Latency regression is detected by converting continuous latency observations into Bernoulli trials:

**Procedure:**
1. Derive threshold **T** from baseline distribution (default: baseline p95)
2. For each current observation Xᵢ:
    - **Success:** Xᵢ ≤ T
    - **Failure (exceedance):** Xᵢ > T
3. Estimate the exceedance rate
4. **Decision:** Fail if observed exceedance rate exceeds baseline expectation by more than allowed margin, with confidence ≥ 1-α

**Properties:**
- Focuses on user-relevant tail behavior
- Robust to heavy tails
- Produces interpretable confidence statements
- Unifies with PUnit's existing binomial proportion statistics

### 2.3 Confidence Semantics

All failures are qualified:

> "Latency regression detected with 95% confidence relative to baseline."

Confidence refers to statistical evidence, not business impact or SLA violation.

---

## 3. Warm-up Handling

### 3.1 Design Principle

The first invocations of a service are often slower due to JIT compilation, connection establishment, cache population, and resource pool initialization. For complex code paths, JIT optimization may not fully stabilize until after several invocations. Similarly, connection pools and caches may require multiple calls to reach steady state.

**Therefore:** `@ServiceLatencyTest` allows users to specify a configurable number of warmup iterations that run before any data is collected. This ensures that all measured samples reflect steady-state behavior.

### 3.2 Warmup Sample Size

PUnit provides an optional `warmupSampleSize` parameter with a default of 0. When specified:

1. The framework first executes the use case `warmupSampleSize` times
2. No latency data is collected during warmup iterations
3. The framework then executes the main run of exactly `samples` invocations
4. All `samples` invocations are measured and analyzed

**Total invocations = `warmupSampleSize` + `samples`**

### 3.3 Behavior

| warmupSampleSize | Total Invocations | Samples Analyzed | Use When                                              |
|------------------|-------------------|------------------|-------------------------------------------------------|
| 0 (default)      | N                 | All N            | Cold-start matters; first impressions are user-visible|
| 5                | N + 5             | N                | Need JIT stabilization before measuring               |
| 10+              | N + 10+           | N                | Complex code paths requiring extended warm-up         |

### 3.4 Why Multiple Warmup Iterations?

A single warmup invocation may be insufficient for:

- **JIT compilation**: Complex methods may require several invocations before the JIT compiler fully optimizes the code path
- **Tiered compilation**: The JVM's tiered compilation (C1 → C2) may not reach peak optimization until after multiple calls
- **Connection pool warming**: Establishing a full pool of database or HTTP connections may require multiple concurrent-style invocations
- **Cache priming**: Multi-level caches (L1, L2, distributed) may need several access patterns before reaching steady state
- **Class loading**: Lazy-loaded classes along the code path may not all load on the first invocation

### 3.5 Cold-Start Testing

To test cold-start behavior (where the first invocation matters), simply use the default `warmupSampleSize=0`. The first invocation will be included in the measured samples.

### 3.6 Validation

- `warmupSampleSize` must be ≥ 0

### 3.7 Baseline Consistency

The same `warmupSampleSize` parameter applies during both baseline generation (experiment phase) and test execution. The baseline records the warmup configuration used during its generation, ensuring that baseline and test runs follow consistent warmup behavior.

---

## 4. Timeout Handling

### 4.1 Statistical Treatment

Timeouts are recorded as latency samples with duration equal to the elapsed time until timeout. They are **not** treated specially in statistical computation.

If timeout duration exceeds threshold T (baseline p95), the sample naturally counts as an exceedance.

### 4.2 Operational Visibility

- **Timeout count** is always included in the test report
- **`maxAllowedTimeouts`** parameter provides a circuit breaker

### 4.3 Circuit Breaker Rationale

As timeout frequency increases, the statistical value of timing data decreases. A test where most invocations timeout should fail on that basis before analyzing latency distribution.

If timeout count ≥ `maxAllowedTimeouts`, the test fails immediately without completing statistical analysis.

---

## 5. Baseline and Specification

### 5.1 Relationship to ProbabilisticTest

Baseline generation for `@ServiceLatencyTest` uses the same use case mechanism as `@ProbabilisticTest`. The key difference is that latency experiments may need temporal profiling to ensure comparable conditions.

### 5.2 Baseline (Experiment Output)

Baselines are produced by long-running experiments and are immutable records of observed behavior.

#### 5.2.1 Baseline Contents

**Core Statistical Data:**
- Total sample count N
- Effective sample count after warm-up N'
- Warm-up policy applied
- Empirical quantiles: p50, p90, p95, p99
- p99.9 (included if N ≥ 1000)
- Min / max / mean / standard deviation
- Tail-preserving distribution representation (t-digest, compression=100)

**Measurement Semantics:**
- Timing unit (nanoseconds)
- Clock type (monotonic)
- Timeout duration (if applicable)
- Timeout count during experiment
- Measurement scope (end-to-end vs measured block)

**Execution Metadata:**
- Environment fingerprint
- Timestamp and provenance (git SHA, CI build ID)
- Experiment class and method
- Period profile (if applicable)

#### 5.2.2 Baseline YAML Structure

```yaml
useCaseId: checkout.service
generatedAt: 2026-01-07T10:00:00Z
type: latency
profile: peak_weekday  # Optional, omitted if unprofiled

experiment:
  class: com.example.experiments.CheckoutLatencyExperiment
  method: measureCheckoutLatency

warmup:
  sampleSize: 5
  invocationsExecuted: 5

execution:
  samplesPlanned: 1000
  samplesExecuted: 1000
  timeouts: 2
  invocationTimeoutMs: 5000

latencyNanos:
  min: 8100000
  max: 234000000
  mean: 18700000
  stddev: 12300000
  p50: 12300000
  p90: 35600000
  p95: 45200000
  p99: 89400000
  p999: 156000000

distribution:
  format: tdigest
  compression: 100
  encoded: "base64-encoded-tdigest..."

environment:
  jvmVersion: "21.0.1+12"
  jvmVendor: "Eclipse Temurin"
  osName: "Linux"
  osArch: "amd64"
  availableProcessors: 8
  maxHeapBytes: 4294967296
  containerized: true

provenance:
  gitSha: "a1b2c3d4e5f6"
  ciBuildId: "build-12345"
  ciPipelineUrl: "https://ci.example.com/builds/12345"
```

### 5.3 Specification (Human-Approved)

Specifications reference baselines and add approval metadata:

```yaml
useCaseId: checkout.service
version: v1
type: latency
profile: peak_weekday  # Optional

approval:
  approvedAt: 2026-01-08T14:00:00Z
  approvedBy: jane.engineer@example.com
  notes: "Baseline from load test on 2026-01-07"

baseline:
  reference: checkout.service/baseline.peak_weekday.yaml
  warmupSampleSize: 5
  samplesExecuted: 1000
  p95Nanos: 45200000
```

---

## 6. Use Case Execution Time Recording

### 6.1 Motivation

Every experiment involves repeated execution of a use case. The wall-clock duration of each use case invocation is valuable operational data that should be captured and preserved, even for experiments that do not focus specifically on latency (e.g., `@ProbabilisticTest` experiments that measure pass/fail rates).

Recording execution time provides:
- **Performance visibility**: Teams can observe baseline execution durations without running separate latency experiments
- **Regression detection hints**: Significant changes in average execution time may correlate with behavioral changes
- **Capacity planning data**: Mean and standard deviation of execution time help estimate resource requirements
- **Debugging context**: When a test fails, knowing the execution time distribution helps diagnose root causes

### 6.2 Requirement

**REQ-LATENCY-01:** All experiments (including `@ProbabilisticTest` experiments) MUST record the following execution time metrics for every use case invocation:

1. **Mean execution time** (in nanoseconds): The arithmetic mean of all use case invocation durations
2. **Standard deviation** (in nanoseconds): The sample standard deviation of all use case invocation durations
3. **Minimum execution time** (in nanoseconds): The fastest invocation
4. **Maximum execution time** (in nanoseconds): The slowest invocation

### 6.3 Baseline YAML Extension

For all experiment types, the baseline YAML MUST include an `executionTime` section:

```yaml
executionTime:
  meanNanos: 18700000
  stddevNanos: 12300000
  minNanos: 8100000
  maxNanos: 234000000
```

### 6.4 Implementation Notes

- Execution time is measured using `System.nanoTime()` for monotonic, high-resolution timing
- Timing measurement wraps the entire use case invocation (entry to exit)
- Failed invocations (exceptions, timeouts) are included in timing statistics
- Warmup invocations (if applicable) are excluded from timing statistics

---

## 7. Period Profile Handling

### 7.1 Motivation

Latency behavior frequently varies as a function of date and time due to:
- Diurnal traffic patterns
- Scheduled batch jobs
- Cache churn and eviction cycles
- Dependency saturation during business hours
- Infrastructure sharing across teams

These effects are often larger than code-level performance regressions.

Profiles ensure that statistical comparisons are made only between latency distributions observed under comparable temporal conditions.

### 7.2 Mechanism

Profiles are a **CI/environment concern**, not a test annotation concern.

#### 7.2.1 Experiment Phase (Baseline Generation)

```
CI Schedule: "Run checkout latency experiment at 9am weekdays"
CI Environment: PUNIT_PERIOD_PROFILE=peak_weekday

Experiment runs → Baseline generated with profile tag
```

#### 7.2.2 Test Phase (Baseline Selection)

```
CI Environment: PUNIT_PERIOD_PROFILE=peak_weekday

PUnit resolution:
  1. Look for baseline matching (useCaseId + profile)
  2. If found: use profiled baseline
  3. If not found: fallback to unprofiled baseline + WARN
```

### 7.3 Resolution Precedence

| Condition                              | Behavior                                   |
|----------------------------------------|--------------------------------------------|
| Profile set, profiled baseline exists  | Use profiled baseline                      |
| Profile set, profiled baseline missing | Fallback to unprofiled baseline + **WARN** |
| No profile set                         | Use unprofiled baseline                    |
| No profile set, no unprofiled baseline | **FAIL** (no baseline available)           |

### 7.4 Environment Variables

| Variable               | Purpose                               |
|------------------------|---------------------------------------|
| `PUNIT_PERIOD_PROFILE` | Specifies the period/temporal profile |
| `punit.period.profile` | System property alternative           |

### 7.5 Baseline Storage Convention

```
src/test/resources/punit/baselines/
  {useCaseId}/
    baseline.yaml                    # Unprofiled
    baseline.{profile}.yaml          # Profiled (e.g., baseline.peak_weekday.yaml)
```

### 7.6 Warning Output

When fallback occurs:

```
⚠️ PROFILE MISMATCH WARNING

Requested profile: peak_weekday
No baseline found for: checkout.service + peak_weekday
Falling back to: checkout.service (unprofiled)

Results may not reflect expected operating conditions.
Consider running experiment with PUNIT_PERIOD_PROFILE=peak_weekday
```

---

## 8. Annotation API

### 8.1 Design Principle

Using `@ServiceLatencyTest` is a deliberate decision. The developer must explicitly specify a valid operational configuration. No defaults exist for core operational parameters.

### 8.2 Annotation Definition

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(ServiceLatencyTestExtension.class)
public @interface ServiceLatencyTest {
    
    // ═══════════════════════════════════════════════════════════════
    // REQUIRED — Must be explicitly specified
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Reference to a latency specification.
     * Format: "useCaseId:version" (e.g., "checkout.service:v1")
     */
    String spec();
    
    /**
     * Number of service invocations to execute.
     * Must be explicitly specified.
     */
    int samples();
    
    /**
     * Statistical confidence level (0.0 to 1.0).
     * Must be explicitly specified.
     */
    double confidence();
    
    /**
     * Maximum allowed increase in exceedance rate (absolute, 0.0 to 1.0).
     * Must be explicitly specified.
     * 
     * For thresholdQuantile=0.95, baseline exceedance is 5%.
     * If maxExceedanceIncrease=0.03, test fails when observed
     * exceedance exceeds 8%.
     */
    double maxExceedanceIncrease();
    
    // ═══════════════════════════════════════════════════════════════
    // OPTIONAL — Sensible defaults provided
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Quantile used to derive the exceedance threshold.
     * Default: 0.95 (p95)
     */
    double thresholdQuantile() default 0.95;
    
    /**
     * Number of warmup iterations to execute before data collection.
     * Warmup invocations are not measured or included in analysis.
     * Total invocations = warmupSampleSize + samples.
     * Default: 0
     */
    int warmupSampleSize() default 0;
    
    /**
     * Timeout for individual invocation (milliseconds).
     * Timed-out invocations are recorded with elapsed duration.
     * 0 = no timeout.
     * Default: 0
     */
    long invocationTimeoutMs() default 0;
    
    /**
     * Maximum allowed timeouts before test fails immediately.
     * 0 = no limit (timeouts only affect statistics).
     * Default: 0
     */
    int maxAllowedTimeouts() default 0;
    
    /**
     * Maximum wall-clock time for all samples (milliseconds).
     * 0 = unlimited.
     * Default: 0
     */
    long timeBudgetMs() default 0;
}
```

### 8.3 Validation

The framework rejects annotations missing required parameters:

```java
// INVALID — missing required parameters
@ServiceLatencyTest(spec = "checkout.service:v1")  // ❌

// INVALID — incomplete
@ServiceLatencyTest(
    spec = "checkout.service:v1",
    samples = 100
)  // ❌

// VALID — all required parameters specified
@ServiceLatencyTest(
    spec = "checkout.service:v1",
    samples = 100,
    confidence = 0.95,
    maxExceedanceIncrease = 0.03
)  // ✅
```

---

## 9. Usage Examples

### 9.1 Standard Configuration

```java
@ServiceLatencyTest(
    spec = "checkout.service:v1",
    samples = 100,
    confidence = 0.95,
    maxExceedanceIncrease = 0.03,
    warmupSampleSize = 5
)
void checkoutLatency() {
    checkoutService.process(order);
}
```

**Behavior:**
- 5 warmup invocations (not measured) + 100 measured invocations = 105 total
- All 100 measured samples are analyzed
- Threshold = baseline p95
- 95% confidence
- Fail if exceedance rate > 8% (5% baseline + 3% margin)

### 9.2 Strict Configuration

```java
@ServiceLatencyTest(
    spec = "checkout.service:v1",
    samples = 200,
    confidence = 0.99,
    maxExceedanceIncrease = 0.01,
    thresholdQuantile = 0.99,
    warmupSampleSize = 10,
    invocationTimeoutMs = 5000,
    maxAllowedTimeouts = 3
)
void checkoutLatencyStrict() {
    checkoutService.process(order);
}
```

**Behavior:**
- 10 warmup + 200 measured = 210 total invocations
- All 200 samples analyzed
- Threshold = baseline p99
- 99% confidence
- Fail if exceedance rate > 2% (1% baseline + 1% margin)
- Fail immediately if 3+ timeouts

### 9.3 Cold-Start Testing

```java
@ServiceLatencyTest(
    spec = "checkout.service:v1",
    samples = 100,
    confidence = 0.95,
    maxExceedanceIncrease = 0.03
    // warmupSampleSize defaults to 0
)
void checkoutLatencyIncludingColdStart() {
    checkoutService.process(order);
}
```

**Behavior:**
- 0 warmup + 100 measured = 100 total invocations
- First (cold) invocation is included in analysis
- Cold-start contributes to exceedance calculation

### 9.4 Time-Budgeted Test

```java
@ServiceLatencyTest(
    spec = "checkout.service:v1",
    samples = 500,
    confidence = 0.95,
    maxExceedanceIncrease = 0.02,
    timeBudgetMs = 60000,
    invocationTimeoutMs = 2000
)
void checkoutLatencyHighVolume() {
    checkoutService.process(order);
}
```

---

## 10. Test Execution

### 10.1 Execution Flow

1. Load specification and resolve baseline (applying profile if `PUNIT_PERIOD_PROFILE` is set)
2. Extract threshold T from baseline (quantile specified by `thresholdQuantile`)
3. Execute use case `warmupSampleSize` times without measuring (warmup phase)
4. Execute use case `samples` times, measuring latency with monotonic clock (`System.nanoTime()`)
5. For each measured sample, classify as success (≤ T) or exceedance (> T)
6. Compute exceedance rate and confidence interval
7. Compare against baseline exceedance rate + allowed margin
8. Report result with confidence qualification

### 10.2 Early Termination

The test terminates early if:
- Timeout count reaches `maxAllowedTimeouts`
- Time budget exhausted (`timeBudgetMs`)
- Statistical impossibility/guarantee detected (optional optimization)

---

## 11. Reporting

### 11.1 Pass

```
LATENCY TEST PASSED (95% confidence)

Spec: checkout.service:v1
Warmup: 5 iterations
Samples: 100 (measured)
Timeouts: 0

Threshold: 45.2 ms (baseline p95)
Baseline exceedance rate: 5.0%
Observed exceedance rate: 4.0% (4 of 100 samples)
Maximum allowed: 8.0%

Decision: PASS — exceedance rate within acceptable margin.
```

### 11.2 Regression Failure

```
LATENCY REGRESSION DETECTED (95% confidence)

Spec: checkout.service:v1
Warmup: 5 iterations
Samples: 100 (measured)
Timeouts: 1

Threshold: 45.2 ms (baseline p95)
Baseline exceedance rate: 5.0%
Observed exceedance rate: 12.0% (12 of 100 samples)
Maximum allowed: 8.0%

Decision: FAIL — exceedance rate 12.0% exceeds maximum 8.0% with 95% confidence.

Observed latency summary:
    p50:  14.2 ms  (baseline: 12.3 ms, +15.4%)
    p95:  62.8 ms  (baseline: 45.2 ms, +38.9%)
    p99:  98.4 ms  (baseline: 89.4 ms, +10.1%)
```

### 11.3 Timeout Threshold Exceeded

```
TIMEOUT THRESHOLD EXCEEDED

Spec: checkout.service:v1
Warmup: 5 iterations (completed)
Samples attempted: 47 of 100
Timeouts: 5 (threshold: 5)

Decision: FAIL — timeout count reached maximum allowed (5).

Test terminated early. Statistical analysis not performed.
```

### 11.4 Profile Mismatch Warning

```
LATENCY TEST PASSED (95% confidence)

⚠️ PROFILE MISMATCH WARNING
Requested profile: peak_weekday
No baseline found for: checkout.service + peak_weekday
Falling back to: checkout.service (unprofiled)

Spec: checkout.service:v1 (unprofiled fallback)
...
```

---

## 12. Data Models

### 12.1 LatencyBaseline

```java
public final class LatencyBaseline {
    
    private final String useCaseId;
    private final Instant generatedAt;
    private final String experimentClass;
    private final String experimentMethod;
    private final String profile;  // nullable
    
    private final WarmupSummary warmup;
    private final LatencyExecutionSummary execution;
    private final LatencyStatistics statistics;
    private final DistributionDigest distribution;
    private final EnvironmentFingerprint environment;
    private final Provenance provenance;
}

public record WarmupSummary(
    int sampleSize,
    int invocationsExecuted
) {}

public record LatencyExecutionSummary(
    int samplesPlanned,
    int samplesExecuted,
    int timeouts,
    long invocationTimeoutMs
) {}

public record LatencyStatistics(
    long minNanos,
    long maxNanos,
    long meanNanos,
    long stddevNanos,
    long p50Nanos,
    long p90Nanos,
    long p95Nanos,
    long p99Nanos,
    Long p999Nanos  // nullable, included if N >= 1000
) {}

public record DistributionDigest(
    String format,      // "tdigest"
    int compression,    // 100
    byte[] encoded
) {}

public record EnvironmentFingerprint(
    String jvmVersion,
    String jvmVendor,
    String osName,
    String osArch,
    int availableProcessors,
    long maxHeapBytes,
    boolean containerized
) {}

public record Provenance(
    String gitSha,
    String ciBuildId,
    String ciPipelineUrl
) {}
```

### 12.2 LatencySpec

```java
public final class LatencySpec {
    
    private final String useCaseId;
    private final String version;
    private final String profile;  // nullable
    
    private final Approval approval;
    private final BaselineReference baseline;
}

public record Approval(
    Instant approvedAt,
    String approvedBy,
    String notes
) {}

public record BaselineReference(
    String reference,
    int warmupSampleSize,
    int samplesExecuted,
    long p95Nanos
) {}
```

### 12.3 ExecutionTimeSummary

```java
/**
 * Execution time statistics for any experiment type.
 * Recorded for all use case invocations during experiment execution.
 */
public record ExecutionTimeSummary(
    long meanNanos,
    long stddevNanos,
    long minNanos,
    long maxNanos
) {}
```

---

## 13. Summary

| Aspect                   | Decision                                                           |
|--------------------------|--------------------------------------------------------------------|
| **Statistical model**    | Tail exceedance regression (binomial)                              |
| **Warm-up handling**     | `warmupSampleSize` parameter (default 0); runs before measurement  |
| **Threshold**            | Baseline quantile (default p95, configurable)                      |
| **Margin**               | Absolute exceedance increase (required, no default)                |
| **Timeouts**             | Recorded as samples; count reported; optional `maxAllowedTimeouts` |
| **Profiles**             | Environment-driven (`PUNIT_PERIOD_PROFILE`); fallback with warning |
| **Required params**      | `spec`, `samples`, `confidence`, `maxExceedanceIncrease`           |
| **Confidence**           | Required, explicitly specified                                     |
| **Distribution storage** | t-digest (compression=100)                                         |
| **Execution time**       | Mean + stddev + min/max recorded for all experiments               |

---

## 14. One-Sentence Summary

> `@ServiceLatencyTest` performs statistically principled regression detection on empirically derived latency distributions, using tail exceedance analysis with configurable warmup iterations, profile-aware baseline selection, and confidence-qualified deviation reporting.

