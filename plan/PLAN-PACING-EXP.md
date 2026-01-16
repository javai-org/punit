# Plan: Add @Pacing Support to Experiments

**Status**: PLANNING  
**Priority**: High  
**Rationale**: Experiments are often long-running (1000+ samples with LLM calls). Pacing is even more critical for experiments than tests to respect API rate limits.

---

## Problem Statement

The `@Pacing` annotation works with `@ProbabilisticTest` but is silently ignored when used with `@Experiment`. This is a significant gap because:

1. **Experiments are longer**: Typical experiments run 1000+ samples vs 100 for tests
2. **Rate limits matter more**: Long-running experiments are more likely to hit API rate limits
3. **Cost is higher**: Uncontrolled experiment execution can burn through API budgets quickly
4. **Developer expectation**: The annotation exists and appears to apply—silent failure is confusing

---

## Current Architecture

### How Pacing Works for @ProbabilisticTest

1. **Resolution**: `PacingResolver.resolve(Method, samples)` reads `@Pacing` annotation and system/env overrides
2. **Calculation**: `PacingCalculator.compute()` determines `effectiveMinDelayMs` from constraints
3. **Configuration**: Returns `PacingConfiguration` record with delay and concurrency settings
4. **Execution**: `SampleExecutor.applyPacingDelay()` sleeps between samples (skipping first sample)
5. **Reporting**: `PacingReporter` logs pacing info at test start

### Key Classes

| Class                 | Package        | Purpose                                        |
|-----------------------|----------------|------------------------------------------------|
| `Pacing`              | `api`          | Annotation defining rate constraints           |
| `PacingResolver`      | `ptest.engine` | Resolves config from annotation + system props |
| `PacingCalculator`    | `ptest.engine` | Computes effective delays from constraints     |
| `PacingConfiguration` | `ptest.engine` | Immutable config record                        |
| `PacingReporter`      | `ptest.engine` | Logs pacing summary                            |
| `SampleExecutor`      | `ptest.engine` | Applies delays during execution                |

### How ExperimentExtension Executes Samples

1. **MEASURE mode**: `provideMeasureInvocationContexts()` creates stream of N invocation contexts
2. **EXPLORE mode**: `provideExploreInvocationContexts()` creates stream of configs × samples contexts
3. **Interception**: `interceptTestTemplateMethod()` executes each sample via `invocation.proceed()`
4. **No pacing**: Currently no delay logic exists in experiment execution

---

## Proposed Solution

### Design Principles

1. **Reuse existing infrastructure**: Use `PacingResolver`, `PacingCalculator`, `PacingConfiguration`
2. **Consistent behavior**: Pacing should work identically for experiments and tests
3. **Package location**: Pacing classes remain in `ptest.engine` (shared infrastructure)
4. **Minimal changes**: Add pacing to `ExperimentExtension` without major refactoring

### Implementation Phases

#### Phase 1: Resolve Pacing Configuration

**File**: `ExperimentExtension.java`

Add pacing resolution in `provideTestTemplateInvocationContexts()`:

```java
// After annotation validation, resolve pacing
PacingResolver pacingResolver = new PacingResolver();
int samples = annotation.mode().getEffectiveSampleSize(
    annotation.mode() == ExperimentMode.EXPLORE 
        ? annotation.samplesPerConfig() 
        : annotation.samples()
);
PacingConfiguration pacing = pacingResolver.resolve(testMethod, samples);
store.put("pacing", pacing);
```

**Estimated effort**: Small (5-10 lines)

---

#### Phase 2: Apply Pacing Delays in Sample Execution

**File**: `ExperimentExtension.java`

Add delay logic in `interceptMeasureMethod()` and `interceptExploreMethod()`:

```java
// At start of interceptMeasureMethod/interceptExploreMethod:
PacingConfiguration pacing = store.get("pacing", PacingConfiguration.class);
AtomicInteger globalSampleCounter = store.get("globalSampleCounter", AtomicInteger.class);
int sample = globalSampleCounter.incrementAndGet();

// Apply pacing delay (skip first sample)
if (sample > 1 && pacing != null && pacing.hasPacing()) {
    long delayMs = pacing.effectiveMinDelayMs();
    if (delayMs > 0) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

**Key point**: For EXPLORE mode, use a **global** sample counter across all configs to ensure continuous pacing. This prevents rate limit violations when many configs have few samples each.

**Implementation detail**: Store `globalSampleCounter` in the parent context store, shared across all invocation contexts.

**Estimated effort**: Medium (20-30 lines across two methods)

---

#### Phase 3: Report Pacing Configuration

**File**: `ExperimentExtension.java`

Add pacing reporting at experiment start:

```java
// In provideTestTemplateInvocationContexts(), after resolving pacing:
if (pacing.hasPacing()) {
    PacingReporter reporter = new PacingReporter();
    reporter.report(pacing, samples);
}
```

**Alternative**: Create experiment-specific pacing reporter if output format differs.

**Estimated effort**: Small (5 lines)

---

#### Phase 4: Update @Pacing Javadoc

**File**: `Pacing.java`

Update annotation Javadoc to document experiment support:

```java
/**
 * Declares pacing constraints for a probabilistic test or experiment.
 *
 * <h2>Usage with @ProbabilisticTest</h2>
 * <pre>{@code
 * @ProbabilisticTest(samples = 100)
 * @Pacing(maxRequestsPerMinute = 60)
 * void testWithPacing() { ... }
 * }</pre>
 *
 * <h2>Usage with @Experiment</h2>
 * <pre>{@code
 * @Experiment(useCase = MyUseCase.class, samples = 1000)
 * @Pacing(maxRequestsPerMinute = 60)
 * void measureBaseline(MyUseCase useCase, ResultCaptor captor) { ... }
 * }</pre>
 */
```

**Estimated effort**: Small (documentation only)

---

#### Phase 5: Add Unit Tests

**New File**: `ExperimentPacingTest.java`

Test cases:
1. `pacingDelayAppliedBetweenSamples` - verify delays occur
2. `noPacingDelayOnFirstSample` - first sample has no delay
3. `pacingResolvedFromAnnotation` - annotation values used
4. `pacingResolvedFromSystemProperty` - system prop overrides annotation
5. `pacingReportedAtExperimentStart` - pacing info logged
6. `exploreModePacingContinuousAcrossConfigs` - pacing continues across config boundaries (no reset)
7. `pacingWarnsWhenExceedsTimeBudget` - warning logged if pacing × samples > timeBudget

**Estimated effort**: Medium (50-100 lines)

---

#### Phase 6: Integration Testing

Manual verification:
1. Run experiment with `@Pacing(minMsPerSample = 500)` - verify 500ms delays
2. Run experiment with `@Pacing(maxRequestsPerMinute = 60)` - verify 1s delays
3. Run EXPLORE mode experiment - verify pacing per config
4. Override with system property - verify override works

---

## Files to Modify

| File                        | Change Type | Description                           |
|-----------------------------|-------------|---------------------------------------|
| `ExperimentExtension.java`  | Modify      | Add pacing resolution and delay logic |
| `Pacing.java`               | Modify      | Update Javadoc for experiment support |
| `ExperimentPacingTest.java` | New         | Unit tests for experiment pacing      |

## Files to Reuse (No Changes)

| File                       | Purpose                     |
|----------------------------|-----------------------------|
| `PacingResolver.java`      | Resolves @Pacing annotation |
| `PacingCalculator.java`    | Computes effective delays   |
| `PacingConfiguration.java` | Configuration record        |
| `PacingReporter.java`      | Logs pacing summary         |

---

## Design Decisions

1. **EXPLORE mode pacing scope**: Pacing is **CONTINUOUS** across all samples, not reset per config.
   - **Rationale**: EXPLORE experiments often have many configs but only 1 sample each. Resetting per config would mean no delays, risking API rate limit violations.
   - **Implementation**: Track sample count globally, not per-config.

2. **Reuse delay logic**: Reuse `SampleExecutor.applyPacingDelay()` or extract to shared utility.
   - **Rationale**: DRY principle; consistent behavior between tests and experiments.

3. **Pacing + time budget warning**: Log a warning if `totalSamples × minDelayMs > timeBudgetMs`.
   - **Rationale**: Help developers catch configuration conflicts early.

---

## Estimated Effort

| Phase | Effort | Lines |
|-------|--------|-------|
| 1. Resolve pacing | Small | ~10 |
| 2. Apply delays | Medium | ~30 |
| 3. Report pacing | Small | ~5 |
| 4. Update Javadoc | Small | ~20 |
| 5. Unit tests | Medium | ~100 |
| 6. Integration test | Small | Manual |

**Total**: ~165 lines of code + manual testing

---

## Success Criteria

1. ✅ `@Pacing` annotation on experiment methods applies delays between samples
2. ✅ First sample of each config/experiment has no delay
3. ✅ System property overrides work (e.g., `-Dpunit.pacing.maxRpm=30`)
4. ✅ Pacing info logged at experiment start
5. ✅ Existing probabilistic test pacing behavior unchanged
6. ✅ All new and existing tests pass

---

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Breaking existing experiments | High | Feature is additive; no pacing = no change |
| Performance overhead from pacing check | Low | Single null check per sample |
| Thread interruption handling | Low | Standard interrupt handling pattern |

---

## Next Steps

1. Review and approve this plan
2. Create feature branch `feature/experiment-pacing`
3. Implement Phase 1-3 (core functionality)
4. Implement Phase 4-5 (docs and tests)
5. Manual integration testing
6. Create PR

