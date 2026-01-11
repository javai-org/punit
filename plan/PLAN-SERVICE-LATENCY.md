# `@ServiceLatencyTest` — Implementation Plan

> **Design Specification:** [DESIGN-SERVICE-LATENCY.md](DESIGN-SERVICE-LATENCY.md)

---

## 1. Overview

This document tracks the implementation plan for `@ServiceLatencyTest` and related execution time recording functionality.

---

## 2. Implementation Milestones

### Phase 1: Execution Time Recording (All Experiments)

**Goal:** Record use case execution time (mean, stddev, min, max) for all experiment types.

| Task | Status | Description |
|------|--------|-------------|
| 2.1.1 | ⬜ Pending | Add `ExecutionTimeSummary` record to model package |
| 2.1.2 | ⬜ Pending | Instrument experiment runner to measure invocation durations |
| 2.1.3 | ⬜ Pending | Compute mean, stddev, min, max from collected durations |
| 2.1.4 | ⬜ Pending | Add `executionTime` section to baseline YAML output |
| 2.1.5 | ⬜ Pending | Update baseline parser to read `executionTime` section |
| 2.1.6 | ⬜ Pending | Include execution time summary in experiment reports |

**Acceptance Criteria:**
- All `@ProbabilisticTest` experiments record execution time statistics
- Baseline YAML files include `executionTime` section with mean, stddev, min, max
- Experiment reports display execution time summary

### Phase 2: ServiceLatencyTest Core

**Goal:** Implement the `@ServiceLatencyTest` annotation and extension.

| Task | Status | Description |
|------|--------|-------------|
| 2.2.1 | ⬜ Pending | Create `@ServiceLatencyTest` annotation |
| 2.2.2 | ⬜ Pending | Implement `ServiceLatencyTestExtension` |
| 2.2.3 | ⬜ Pending | Implement tail exceedance regression logic |
| 2.2.4 | ⬜ Pending | Add warmup sample handling |
| 2.2.5 | ⬜ Pending | Add timeout handling and circuit breaker |
| 2.2.6 | ⬜ Pending | Implement latency-specific baseline generation |

### Phase 3: Latency Baselines and Specs

**Goal:** Support latency-specific baselines and specifications.

| Task | Status | Description |
|------|--------|-------------|
| 2.3.1 | ⬜ Pending | Create `LatencyBaseline` model |
| 2.3.2 | ⬜ Pending | Create `LatencySpec` model |
| 2.3.3 | ⬜ Pending | Implement t-digest distribution storage |
| 2.3.4 | ⬜ Pending | Add latency baseline YAML serialization |
| 2.3.5 | ⬜ Pending | Add latency spec YAML serialization |

### Phase 4: Period Profile Support

**Goal:** Implement profile-aware baseline selection.

| Task | Status | Description |
|------|--------|-------------|
| 2.4.1 | ⬜ Pending | Read `PUNIT_PERIOD_PROFILE` environment variable |
| 2.4.2 | ⬜ Pending | Implement profiled baseline storage convention |
| 2.4.3 | ⬜ Pending | Implement profile resolution with fallback logic |
| 2.4.4 | ⬜ Pending | Add profile mismatch warning output |

### Phase 5: Reporting

**Goal:** Generate latency-specific test reports.

| Task | Status | Description |
|------|--------|-------------|
| 2.5.1 | ⬜ Pending | Implement pass report format |
| 2.5.2 | ⬜ Pending | Implement regression failure report format |
| 2.5.3 | ⬜ Pending | Implement timeout threshold exceeded report |
| 2.5.4 | ⬜ Pending | Add latency summary to failure reports |

---

## 3. Testing Requirements

### 3.1 Unit Tests

```java
// Exceedance calculation
@Test void shouldCalculateExceedanceRate()
@Test void shouldHandleZeroExceedances()
@Test void shouldHandleAllExceedances()

// Execution time recording
@Test void shouldComputeMeanExecutionTime()
@Test void shouldComputeStdDevExecutionTime()
@Test void shouldTrackMinMaxExecutionTime()
@Test void shouldExcludeWarmupFromExecutionTimeStats()

// Warm-up sample size
@Test void shouldExecuteWarmupIterationsWithoutMeasuring()
@Test void shouldMeasureAllSamplesAfterWarmup()
@Test void zeroWarmupShouldIncludeFirstSample()

// Threshold derivation
@Test void shouldUseBaselineP95AsDefaultThreshold()
@Test void shouldUseConfiguredQuantileAsThreshold()

// Timeout handling
@Test void shouldRecordTimeoutAsLatencySample()
@Test void shouldCountTimeoutsSeparately()
@Test void shouldFailWhenMaxTimeoutsExceeded()
@Test void shouldTerminateEarlyOnMaxTimeouts()

// Annotation validation
@Test void shouldRejectAnnotationMissingRequiredParams()
@Test void shouldAcceptAnnotationWithAllRequiredParams()
```

### 3.2 Integration Tests

```java
// Execution time recording
@Test void shouldIncludeExecutionTimeInProbabilisticTestBaseline()
@Test void shouldIncludeExecutionTimeInExperimentReport()

// Baseline generation
@Test void shouldGenerateBaselineWithCorrectStructure()
@Test void shouldExecuteWarmupBeforeBaselineMeasurement()
@Test void shouldStoreTDigestRepresentation()
@Test void shouldIncludeProfileInBaseline()

// Baseline selection
@Test void shouldSelectProfiledBaselineWhenProfileSet()
@Test void shouldFallbackToUnprofiledWhenProfiledMissing()
@Test void shouldWarnOnProfileMismatch()
@Test void shouldFailWhenNoBaselineExists()

// Regression detection
@Test void shouldPassWhenExceedanceWithinMargin()
@Test void shouldFailWhenExceedanceExceedsMargin()
@Test void shouldRespectConfidenceLevel()

// Reporting
@Test void shouldIncludeTimeoutCountInReport()
@Test void shouldShowLatencySummaryOnFailure()
@Test void shouldIncludeProfileMismatchWarning()
```

### 3.3 Edge Cases

```java
@Test void shouldHandleZeroVarianceLatencies()
@Test void shouldWarnOnInsufficientSamplesForP99()
@Test void shouldHandleBimodalDistribution()
@Test void shouldHandleAllSamplesAsTimeouts()
@Test void shouldHandleSingleMeasuredSample()
```

---

## 4. Dependencies

| Dependency | Purpose | Version |
|------------|---------|---------|
| t-digest | Distribution quantile estimation | 3.3+ |

---

## 5. Open Questions

1. Should execution time be displayed prominently in the standard test output, or only in detailed reports?
2. Should we warn when execution time variance is unusually high (coefficient of variation threshold)?
3. Should t-digest compression factor be configurable?

---

## 6. Change Log

| Date | Change |
|------|--------|
| 2026-01-10 | Initial plan created from PLAN-99-SERVICE-LATENCY.md |
| 2026-01-10 | Added Phase 1: Execution Time Recording for all experiments |

