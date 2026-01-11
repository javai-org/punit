# Pacing Constraints Design

## 1. Overview

This document describes the design for **pacing constraints**—a mechanism that allows developers to declare API rate limits and throughput constraints, which the framework uses to compute an optimal sampling execution plan.

### 1.1 Motivation

Many use cases under experiment invoke external APIs (particularly LLM APIs) that impose rate limits:
- Requests per second/minute/hour
- Maximum concurrent requests
- Token throughput limits (TPM)

Without pacing, the framework would either:
1. Execute samples as fast as possible, hit rate limits, and fail unpredictably
2. Require developers to manually insert delays, cluttering test code

### 1.2 Design Philosophy

Pacing is fundamentally different from the existing **guardrails** (time/token budgets):

| Guardrails (Existing)           | Pacing Constraints (New)                   |
|---------------------------------|--------------------------------------------|
| Reactive: "Stop if we exceed X" | Proactive: "Use X to compute optimal pace" |
| Defensive circuit breakers      | Scheduling algorithm inputs                |
| Runtime enforcement             | Pre-execution planning                     |
| Triggers termination            | Prevents hitting limits                    |

**Key Insight**: Pacing constraints are not "abort conditions"—they are **inputs to a scheduling algorithm** that computes inter-request delays and concurrency levels to stay within limits while maximizing throughput.

---

## 2. Constraint Vocabulary

### 2.1 Rate-Based Constraints

These express limits in terms of requests per time unit. The framework computes the implied minimum delay between samples.

| Constraint             | Description | Implied Min Delay  |
|------------------------|-------------|--------------------|
| `maxRequestsPerSecond` | Maximum RPS | `1000 / RPS` ms    |
| `maxRequestsPerMinute` | Maximum RPM | `60000 / RPM` ms   |
| `maxRequestsPerHour`   | Maximum RPH | `3600000 / RPH` ms |

### 2.2 Concurrency Constraints

| Constraint              | Description                                     |
|-------------------------|-------------------------------------------------|
| `maxConcurrentRequests` | Maximum number of samples executing in parallel |

### 2.3 Direct Delay Constraint

For developers who prefer simplicity over derived values:

| Constraint       | Description                                 |
|------------------|---------------------------------------------|
| `minMsPerSample` | Explicit minimum delay between samples (ms) |

---

## 3. Constraint Composition

When multiple constraints are specified, the framework computes the **most restrictive** effective pacing.

### 3.1 Effective Delay Calculation

```
effectiveMinDelayMs = max(
    minMsPerSample,                           // Direct constraint
    1000 / maxRequestsPerSecond,              // If RPS specified
    60000 / maxRequestsPerMinute,             // If RPM specified
    3600000 / maxRequestsPerHour              // If RPH specified
)
```

### 3.2 Effective Concurrency Calculation

Concurrency must also respect rate limits. If a developer specifies `maxConcurrentRequests = 5` but `maxRequestsPerMinute = 60`, the framework must throttle concurrency or increase per-worker delay.

```
// With average latency L (ms) per sample:
maxSustainableConcurrency = (effectiveMinDelayMs * maxConcurrentRequests) / L

// Effective concurrency:
effectiveConcurrency = min(maxConcurrentRequests, maxSustainableConcurrency)
```

### 3.3 Example Computation

Given:
- `samples = 200`
- `maxRequestsPerMinute = 60` → implies 1000ms delay
- `maxConcurrentRequests = 5`
- Average latency ≈ 1500ms per sample

Computation:
1. `effectiveMinDelayMs = 1000ms` (from RPM)
2. At 60 RPM with 5 workers: each worker can do 12 requests/min
3. Workers dispatch with staggered delays to maintain aggregate 60 RPM
4. Estimated duration: `200 samples / 60 RPM = 3.33 minutes`

---

## 4. Annotation API Design

### 4.1 New `@Pacing` Annotation

A dedicated annotation keeps pacing concerns separate from test configuration:

```java
package org.javai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares pacing constraints for a probabilistic test or experiment.
 *
 * <p>Pacing constraints inform the framework how to schedule sample execution
 * to stay within API rate limits. Unlike budget guardrails (which terminate
 * execution when exceeded), pacing constraints proactively control execution
 * pace to prevent hitting limits.
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Simple delay-based pacing:</h3>
 * <pre>{@code
 * @ProbabilisticTest(samples = 100)
 * @Pacing(minMsPerSample = 500)
 * void testWithHalfSecondDelay() { ... }
 * }</pre>
 *
 * <h3>Rate-limited API:</h3>
 * <pre>{@code
 * @ProbabilisticTest(samples = 200)
 * @Pacing(maxRequestsPerMinute = 60, maxConcurrentRequests = 3)
 * void testWithRateLimits() { ... }
 * }</pre>
 *
 * <h3>Combined constraints (most restrictive wins):</h3>
 * <pre>{@code
 * @ProbabilisticTest(samples = 100)
 * @Pacing(
 *     maxRequestsPerMinute = 60,
 *     maxRequestsPerSecond = 2,  // More restrictive: 500ms vs 1000ms
 *     maxConcurrentRequests = 5
 * )
 * void testWithMultipleConstraints() { ... }
 * }</pre>
 *
 * @see ProbabilisticTest
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Pacing {

    // ═══════════════════════════════════════════════════════════════════════════
    // RATE-BASED CONSTRAINTS
    // Framework computes minimum delay from these
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Maximum requests per second.
     * 0 = unlimited (default).
     *
     * <p>Implies minimum delay: {@code 1000 / maxRequestsPerSecond} ms
     *
     * @return the maximum RPS, or 0 for unlimited
     */
    double maxRequestsPerSecond() default 0;

    /**
     * Maximum requests per minute.
     * 0 = unlimited (default).
     *
     * <p>Implies minimum delay: {@code 60000 / maxRequestsPerMinute} ms
     *
     * <p>This is the most common constraint for LLM APIs (OpenAI, Anthropic, etc.).
     *
     * @return the maximum RPM, or 0 for unlimited
     */
    double maxRequestsPerMinute() default 0;

    /**
     * Maximum requests per hour.
     * 0 = unlimited (default).
     *
     * <p>Implies minimum delay: {@code 3600000 / maxRequestsPerHour} ms
     *
     * @return the maximum RPH, or 0 for unlimited
     */
    double maxRequestsPerHour() default 0;

    // ═══════════════════════════════════════════════════════════════════════════
    // CONCURRENCY CONSTRAINTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Maximum number of concurrent sample executions.
     * 0 = sequential execution (default, current behavior).
     * 1 = sequential execution (explicit).
     * N > 1 = up to N samples execute in parallel.
     *
     * <p>Note: Concurrency interacts with rate constraints. If concurrency
     * would cause rate limits to be exceeded, the framework automatically
     * throttles.
     *
     * @return the maximum concurrent requests, or 0/1 for sequential
     */
    int maxConcurrentRequests() default 0;

    // ═══════════════════════════════════════════════════════════════════════════
    // DIRECT DELAY CONSTRAINT
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Minimum delay between sample executions in milliseconds.
     * 0 = no delay (default).
     *
     * <p>This is the simplest form of pacing—a direct delay specification
     * without requiring the developer to think in terms of rates.
     *
     * <p>When combined with rate-based constraints, the most restrictive
     * constraint wins (highest delay).
     *
     * @return the minimum delay in milliseconds, or 0 for no delay
     */
    long minMsPerSample() default 0;
}
```

### 4.2 Class-Level Pacing (Optional Extension)

For shared pacing across multiple tests:

```java
package org.javai.punit.api;

/**
 * Declares pacing constraints at the class level, applying to all
 * {@link ProbabilisticTest} methods in the class.
 *
 * <p>Method-level {@link Pacing} annotations override class-level settings.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PacingDefaults {
    double maxRequestsPerSecond() default 0;
    double maxRequestsPerMinute() default 0;
    double maxRequestsPerHour() default 0;
    int maxConcurrentRequests() default 0;
    long minMsPerSample() default 0;
}
```

---

## 5. Configuration Resolution

### 5.1 New Configuration Properties

Following the existing precedence pattern (system prop > env var > annotation > default):

| Property                      | Environment Variable             | Description               |
|-------------------------------|----------------------------------|---------------------------|
| `punit.pacing.maxRps`         | `PUNIT_PACING_MAX_RPS`           | Max requests per second   |
| `punit.pacing.maxRpm`         | `PUNIT_PACING_MAX_RPM`           | Max requests per minute   |
| `punit.pacing.maxRph`         | `PUNIT_PACING_MAX_RPH`           | Max requests per hour     |
| `punit.pacing.maxConcurrent`  | `PUNIT_PACING_MAX_CONCURRENT`    | Max concurrent requests   |
| `punit.pacing.minMsPerSample` | `PUNIT_PACING_MIN_MS_PER_SAMPLE` | Min delay between samples |

### 5.2 PacingConfiguration Record

```java
package org.javai.punit.engine;

/**
 * Resolved pacing configuration with computed execution plan.
 */
public record PacingConfiguration(
    // Raw constraints
    double maxRequestsPerSecond,
    double maxRequestsPerMinute,
    double maxRequestsPerHour,
    int maxConcurrentRequests,
    long minMsPerSample,
    
    // Computed execution plan
    long effectiveMinDelayMs,
    int effectiveConcurrency,
    long estimatedDurationMs,
    double effectiveRps
) {
    /**
     * Computes the estimated completion time based on start time and duration.
     *
     * @param startTime the execution start time
     * @return the estimated completion time
     */
    public java.time.Instant estimatedCompletionTime(java.time.Instant startTime) {
        return startTime.plusMillis(estimatedDurationMs);
    }

    /**
     * Formats the estimated duration as a human-readable string (e.g., "3m 20s").
     */
    public String formattedDuration() {
        long seconds = estimatedDurationMs / 1000;
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        if (minutes > 0) {
            return String.format("%dm %ds", minutes, remainingSeconds);
        }
        return String.format("%ds", seconds);
    }
    /**
     * Returns true if any pacing constraint is configured.
     */
    public boolean hasPacing() {
        return maxRequestsPerSecond > 0 
            || maxRequestsPerMinute > 0 
            || maxRequestsPerHour > 0 
            || maxConcurrentRequests > 1 
            || minMsPerSample > 0;
    }

    /**
     * Returns true if this configuration enables concurrent execution.
     */
    public boolean isConcurrent() {
        return effectiveConcurrency > 1;
    }
}
```

---

## 6. Execution Plan Computation

### 6.1 PacingCalculator

```java
package org.javai.punit.engine;

/**
 * Computes optimal execution plan from pacing constraints.
 */
public class PacingCalculator {

    /**
     * Computes the execution plan for a given sample count and constraints.
     *
     * @param samples the number of samples to execute
     * @param pacing the pacing annotation (may be null)
     * @param estimatedLatencyMs estimated average latency per sample (0 = unknown)
     * @return the computed pacing configuration
     */
    public PacingConfiguration compute(int samples, Pacing pacing, long estimatedLatencyMs) {
        if (pacing == null) {
            return noPacing(samples);
        }
        
        // Compute effective minimum delay from all rate constraints
        long effectiveMinDelayMs = computeEffectiveDelay(pacing);
        
        // Compute effective concurrency
        int effectiveConcurrency = computeEffectiveConcurrency(
            pacing.maxConcurrentRequests(),
            effectiveMinDelayMs,
            estimatedLatencyMs
        );
        
        // Compute effective RPS
        double effectiveRps = effectiveConcurrency > 0 && effectiveMinDelayMs > 0
            ? (1000.0 / effectiveMinDelayMs) * effectiveConcurrency
            : Double.MAX_VALUE;
        
        // Clamp to rate limits
        effectiveRps = clampToRateLimits(effectiveRps, pacing);
        
        // Compute estimated duration
        long estimatedDurationMs = samples > 0 && effectiveRps > 0
            ? (long) (samples / effectiveRps * 1000)
            : 0;
        
        return new PacingConfiguration(
            pacing.maxRequestsPerSecond(),
            pacing.maxRequestsPerMinute(),
            pacing.maxRequestsPerHour(),
            pacing.maxConcurrentRequests(),
            pacing.minMsPerSample(),
            effectiveMinDelayMs,
            effectiveConcurrency,
            estimatedDurationMs,
            effectiveRps
        );
    }

    private long computeEffectiveDelay(Pacing pacing) {
        long delay = pacing.minMsPerSample();
        
        if (pacing.maxRequestsPerSecond() > 0) {
            delay = Math.max(delay, (long) (1000 / pacing.maxRequestsPerSecond()));
        }
        if (pacing.maxRequestsPerMinute() > 0) {
            delay = Math.max(delay, (long) (60000 / pacing.maxRequestsPerMinute()));
        }
        if (pacing.maxRequestsPerHour() > 0) {
            delay = Math.max(delay, (long) (3600000 / pacing.maxRequestsPerHour()));
        }
        
        return delay;
    }

    private int computeEffectiveConcurrency(int maxConcurrent, long delayMs, long latencyMs) {
        if (maxConcurrent <= 1) {
            return 1; // Sequential
        }
        if (latencyMs <= 0 || delayMs <= 0) {
            return maxConcurrent; // No throttling needed if unknown
        }
        
        // Can we sustain maxConcurrent workers at the given delay?
        // Each worker fires every (delayMs * maxConcurrent) ms
        // Aggregate rate = maxConcurrent / delay
        return maxConcurrent;
    }

    private double clampToRateLimits(double rps, Pacing pacing) {
        if (pacing.maxRequestsPerSecond() > 0) {
            rps = Math.min(rps, pacing.maxRequestsPerSecond());
        }
        if (pacing.maxRequestsPerMinute() > 0) {
            rps = Math.min(rps, pacing.maxRequestsPerMinute() / 60.0);
        }
        if (pacing.maxRequestsPerHour() > 0) {
            rps = Math.min(rps, pacing.maxRequestsPerHour() / 3600.0);
        }
        return rps;
    }

    private PacingConfiguration noPacing(int samples) {
        return new PacingConfiguration(0, 0, 0, 0, 0, 0, 1, 0, Double.MAX_VALUE);
    }
}
```

---

## 7. Pre-Flight Report

Before execution begins, the framework outputs a summary of the execution plan:

### 7.1 Console Output Format

```
╔══════════════════════════════════════════════════════════════════╗
║ PUnit Experiment: ShoppingUseCase                                ║
╠══════════════════════════════════════════════════════════════════╣
║ Samples requested:     200                                       ║
║ Pacing constraints:                                              ║
║   • Max requests/min:  60 RPM                                    ║
║   • Max concurrent:    3                                         ║
║   • Min delay/sample:  (derived: 1000ms)                         ║
╠══════════════════════════════════════════════════════════════════╣
║ Computed execution plan:                                         ║
║   • Concurrency:       3 workers                                 ║
║   • Inter-request delay: 333ms per worker (staggered)            ║
║   • Effective throughput: 60 samples/min                         ║
║   • Estimated duration: 3m 20s                                   ║
║   • Estimated completion: 14:23:45                               ║
║   • Estimated tokens:  ~48,000 (based on avg usage)              ║
╠══════════════════════════════════════════════════════════════════╣
║ Started: 14:20:25                                                ║
║ Proceeding with execution...                                     ║
╚══════════════════════════════════════════════════════════════════╝
```

### 7.2 Feasibility Warnings

If pacing constraints conflict with budget constraints, the framework issues warnings:

```
⚠ WARNING: Pacing conflict detected
  • 1000 samples at 60 RPM would take ~16.7 minutes
  • Time budget is 10 minutes (timeBudgetMs = 600000)
  • Options:
    1. Reduce sample count to ~600
    2. Increase time budget to 17 minutes
    3. Remove/increase RPM limit
```

---

## 8. Integration with Existing Components

### 8.1 TestConfiguration Extension

Add pacing fields to the existing `TestConfiguration` record:

```java
private record TestConfiguration(
    // Existing fields...
    int samples,
    double minPassRate,
    // ...
    
    // New pacing fields
    PacingConfiguration pacing
) {
    boolean hasPacing() {
        return pacing != null && pacing.hasPacing();
    }
}
```

### 8.2 ConfigurationResolver Extension

```java
public class ConfigurationResolver {
    // Existing constants...
    
    // New pacing constants
    public static final String PROP_PACING_MAX_RPS = "punit.pacing.maxRps";
    public static final String PROP_PACING_MAX_RPM = "punit.pacing.maxRpm";
    public static final String PROP_PACING_MAX_RPH = "punit.pacing.maxRph";
    public static final String PROP_PACING_MAX_CONCURRENT = "punit.pacing.maxConcurrent";
    public static final String PROP_PACING_MIN_MS = "punit.pacing.minMsPerSample";
    
    public static final String ENV_PACING_MAX_RPS = "PUNIT_PACING_MAX_RPS";
    public static final String ENV_PACING_MAX_RPM = "PUNIT_PACING_MAX_RPM";
    public static final String ENV_PACING_MAX_RPH = "PUNIT_PACING_MAX_RPH";
    public static final String ENV_PACING_MAX_CONCURRENT = "PUNIT_PACING_MAX_CONCURRENT";
    public static final String ENV_PACING_MIN_MS = "PUNIT_PACING_MIN_MS_PER_SAMPLE";
    
    /**
     * Resolves pacing configuration from annotation and environment.
     */
    public PacingConfiguration resolvePacing(Method testMethod, int samples) {
        Pacing pacing = testMethod.getAnnotation(Pacing.class);
        PacingCalculator calculator = new PacingCalculator();
        
        // Resolve with precedence
        double maxRps = resolveDouble(PROP_PACING_MAX_RPS, ENV_PACING_MAX_RPS,
            pacing != null ? pacing.maxRequestsPerSecond() : 0, 0);
        double maxRpm = resolveDouble(PROP_PACING_MAX_RPM, ENV_PACING_MAX_RPM,
            pacing != null ? pacing.maxRequestsPerMinute() : 0, 0);
        // ... etc
        
        return calculator.compute(samples, /* resolved pacing */);
    }
}
```

### 8.3 Execution Loop Modification

The sample execution loop in `ProbabilisticTestExtension` must be modified to:

1. Apply inter-sample delays based on `effectiveMinDelayMs`
2. (Phase 2) Support concurrent execution if `effectiveConcurrency > 1`

```java
// Simplified illustration
private Stream<TestTemplateInvocationContext> createSampleStream(
        int samples, AtomicBoolean terminated, 
        DefaultTokenChargeRecorder tokenRecorder,
        PacingConfiguration pacing) {

    return Stream.iterate(1, i -> i + 1)
            .limit(samples)
            .takeWhile(i -> !terminated.get())
            .peek(i -> applyPacingDelay(pacing, i))  // NEW: Apply delay
            .map(sampleIndex -> new ProbabilisticTestInvocationContext(
                    sampleIndex, samples, tokenRecorder));
}

private void applyPacingDelay(PacingConfiguration pacing, int sampleIndex) {
    if (pacing != null && pacing.effectiveMinDelayMs() > 0 && sampleIndex > 1) {
        try {
            Thread.sleep(pacing.effectiveMinDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

---

## 9. Implementation Plan

**See**: [PLAN-PACING.md](PLAN-PACING.md) for implementation phases and progress tracking.

---

## 10. Budget Exhaustion: Projected vs Actual

### 10.1 Terminology

| Term | Definition |
|------|------------|
| **Budget Exhausted** | Hard limit actually crossed (time elapsed, tokens consumed) |
| **Projected Exhaustion** | Interpolated estimate predicts budget will be exhausted before completion |

### 10.2 Behavior Matrix

| Detection Point | Budget Mode | Behavior |
|-----------------|-------------|----------|
| **Pre-flight** | Any | Terminate by default (run cannot possibly complete) |
| **Runtime** | `FAIL` | Terminate on projected exhaustion |
| **Runtime** | `EVALUATE_PARTIAL` | Log warning, continue until actual exhaustion |
| **Actual exhaustion** | `FAIL` | Terminate, test fails |
| **Actual exhaustion** | `EVALUATE_PARTIAL` | Terminate, evaluate partial results |

### 10.3 Projection Parameters

- **Minimum samples before projection**: 10 (configurable constant)
- **Time projection**: Deterministic (pacing-based) — high confidence
- **Token projection**: Stochastic (average + standard deviation, recomputed per sample)
- **Latency projection**: Stochastic (average + standard deviation)

### 10.4 Unified Termination Evaluator

The termination evaluator combines multiple contributors behind a unified interface:

```java
public interface TerminationEvaluator {
    Optional<TerminationDecision> evaluate(RunState state);
}

public record TerminationDecision(
    TerminationReason reason,
    TerminationAction action,  // TERMINATE, WARN_AND_CONTINUE
    String explanation
) {}

public enum TerminationReason {
    // Pass-rate based (existing)
    SUCCESS_GUARANTEED,
    IMPOSSIBILITY,
    
    // Budget based (existing)
    TIME_BUDGET_EXHAUSTED,
    TOKEN_BUDGET_EXHAUSTED,
    
    // Projection based (new)
    TIME_BUDGET_PROJECTED_EXHAUSTION,
    TOKEN_BUDGET_PROJECTED_EXHAUSTION
}
```

### 10.5 Pre-Flight Termination

If pre-flight analysis determines the run cannot complete:
- **Default**: Terminate immediately (don't waste resources)
- **Future consideration**: Optional override flag for developers who want to run anyway

The correct solution is for the developer to reconsider pacing or cost ceilings, not to override the safeguard.

---

## 11. Open Questions

### 11.1 Input Consistency Between Experiments and Tests

**Status**: Design complete — see **[DESIGN-FACTOR-CONSISTENCY.md](DESIGN-FACTOR-CONSISTENCY.md)**

**Summary**: Factor consistency is ensured via incremental hashing:
- Experiments compute a rolling hash as factors are consumed
- Hash + count stored in the spec
- Probabilistic tests compute their own hash and compare
- Mismatch triggers a warning (not a failure)
- First-N prefix determinism ensures tests use a subset of experiment factors

---

### 11.2 Other Open Questions

1. **Should pacing apply to experiments as well as `@ProbabilisticTest`?**  
   Likely yes—experiments involve repeated sampling and often call LLM APIs.

2. **Should we support "warm-up" samples at reduced rate?**  
   Some APIs have burst limits separate from sustained rates.

3. **Global token monitoring architecture**  
   Token charges are a global concern spanning samples, tests, and experiments. The current `TokenChargeRecorder` (per-sample) and `SharedBudgetMonitor` (per-class/suite) may need unification into a single global monitoring singleton that:
   - Aggregates tokens across concurrent samples
   - Performs continuous budget checks (not just at sample boundaries)
   - Triggers cross-worker early termination when budgets exhausted
   
   This design should be addressed before implementing Phase 2 concurrent execution.

4. **Should we expose a programmatic API for pacing (e.g., in `UseCaseProvider`)?**  
   Could be useful for dynamic configuration.

5. **In-flight sample abandonment semantics**  
   When early termination abandons in-flight samples:
   - Should abandoned samples be logged/reported?
   - How do we handle cleanup (e.g., if test code holds resources)?
   - Should there be a configurable grace period before abandonment?

---

## 11. Summary

Pacing constraints enable developers to declare API rate limits declaratively. The framework computes an optimal execution plan—including inter-sample delays and concurrency levels—and provides upfront estimates of execution time. This proactive approach prevents hitting rate limits rather than reacting to them.

Key benefits:
- **Predictable execution time** — developers know upfront how long tests will take
- **Compliance by design** — never hits rate limits during execution
- **Separation of concerns** — pacing is orthogonal to test logic
- **Flexible configuration** — multiple constraint types, environment override support

---

*See [PLAN-PACING.md](PLAN-PACING.md) for implementation status and next steps.*

