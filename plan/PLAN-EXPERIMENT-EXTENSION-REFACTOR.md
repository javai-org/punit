# ExperimentExtension Refactoring Plan

## Current State

| Metric | Value |
|--------|-------|
| Lines of Code | 1,510 |
| Implemented Interfaces | `TestTemplateInvocationContextProvider`, `InvocationInterceptor` |
| Nested Records/Classes | 8 |
| Public Methods | 3 |
| Private Methods | ~35 |

## Problem

`ExperimentExtension` has grown unwieldy, spanning too many responsibilities:
- Factor resolution and naming
- Pacing control
- Result recording and success determination
- Spec/baseline generation
- Path resolution for output files
- Progress reporting
- Invocation context creation
- Parameter resolution
- Covariate extraction

This violates the Single Responsibility Principle and makes the class difficult to test, understand, and maintain.

## Goal

Reduce `ExperimentExtension` to ~600-700 lines by extracting cohesive responsibilities into dedicated, package-private helper classes. Each helper will receive its own unit test suite.

## Identified Responsibilities

| # | Responsibility | Lines (est.) | Candidate Class |
|---|----------------|--------------|-----------------|
| 1 | Factor Resolution | ~130 | `FactorResolver` |
| 2 | Spec Generation | ~200 | `SpecGenerator` |
| 3 | Result Recording | ~50 | `ResultRecorder` |
| 4 | Pacing Orchestration | ~40 | `ExperimentPacingOrchestrator` |
| 5 | Invocation Context Classes | ~150 | Move to own files |
| 6 | Parameter Resolvers | ~120 | Move to own files |
| 7 | Path Resolution | ~60 | `ExperimentOutputPathResolver` |
| 8 | Report Publishing | ~30 | `ExperimentReporter` |

---

## Phase 1: Extract FactorResolver

**Target Lines**: ~130 lines extracted

### Responsibilities to Extract

- `extractFactorInfos(Method, FactorSource, List<FactorArguments>)`
- `extractFactorInfosFromArguments(Method, FactorArguments)`
- `extractFactorValues(FactorArguments, List<FactorInfo>)`
- `getFactorInfos(Method)` (static helper)
- `buildConfigName(List<FactorInfo>, Object[])`
- `formatFactorValue(Object)`
- `resolveClass(String, Class<?>)`
- `getFactorArguments(Method, String)`
- `FactorInfo` record (move to own file)

### New Classes

```
src/main/java/org/javai/punit/experiment/engine/
├── FactorResolver.java       (~100 lines)
├── FactorInfo.java           (~10 lines, record)
└── ...
```

### Test File

```
src/test/java/org/javai/punit/experiment/engine/
└── FactorResolverTest.java   (~150 lines, 15-20 tests)
```

### Test Cases

1. Extract factor infos from FactorArguments with embedded names
2. Extract factor infos from @FactorSource.factors()
3. Extract factor infos from @Factor annotations
4. Build config name from factor values (single factor)
5. Build config name from factor values (multiple factors)
6. Format factor value (null, string, number)
7. Format factor value with special characters
8. Resolve class in same package
9. Resolve class in sibling package (usecase, model, etc.)
10. Resolve fully qualified class name
11. Get factor arguments from method returning Stream
12. Get factor arguments from method returning Collection
13. Error on invalid factor source

### Estimated Effort

- Implementation: ~1 hour
- Tests: ~1 hour

---

## Phase 2: Extract SpecGenerator

**Target Lines**: ~200 lines extracted

### Responsibilities to Extract

- `generateSpec(ExtensionContext, Store)`
- `generateExploreSpec(ExtensionContext, Store, String, ExperimentResultAggregator)`
- `generateSpecOnce(ExtensionContext, Store)`
- `checkAndGenerateSpec(ExtensionContext, Store)`
- Covariate extraction logic (currently inline in `generateSpec`)

### New Classes

```
src/main/java/org/javai/punit/experiment/engine/
├── SpecGenerator.java        (~180 lines)
└── ...
```

### Test File

```
src/test/java/org/javai/punit/experiment/engine/
└── SpecGeneratorTest.java    (~200 lines, 15-20 tests)
```

### Test Cases

1. Generate spec for completed MEASURE experiment
2. Generate spec for terminated MEASURE experiment (time budget)
3. Generate spec for terminated MEASURE experiment (token budget)
4. Generate spec with covariates
5. Generate spec without covariates
6. Generate spec exactly once (thread-safe guard)
7. Generate EXPLORE spec for single config
8. Generate EXPLORE specs for multiple configs
9. Include result projections in EXPLORE spec
10. Handle experiment ID in path
11. Use footprint in filename when present
12. Emit info note when expiresInDays == 0

### Estimated Effort

- Implementation: ~1.5 hours
- Tests: ~1.5 hours

---

## Phase 3: Extract ExperimentOutputPathResolver

**Target Lines**: ~60 lines extracted

### Responsibilities to Extract

- `resolveMeasureOutputPath(String, String, CovariateProfile)`
- `resolveExploreOutputPath(Experiment, String, String)`
- Constants: `DEFAULT_SPECS_DIR`, `DEFAULT_EXPLORATIONS_DIR`

### New Classes

```
src/main/java/org/javai/punit/experiment/engine/
├── ExperimentOutputPathResolver.java  (~50 lines)
└── ...
```

### Test File

```
src/test/java/org/javai/punit/experiment/engine/
└── ExperimentOutputPathResolverTest.java  (~100 lines, 12-15 tests)
```

### Test Cases

1. MEASURE path with no footprint (legacy naming)
2. MEASURE path with footprint
3. MEASURE path with system property override
4. EXPLORE path creates use case subdirectory
5. EXPLORE path with system property override
6. EXPLORE path sanitizes config name for filesystem
7. Creates parent directories if missing
8. Handles use case ID with dots

### Estimated Effort

- Implementation: ~0.5 hours
- Tests: ~0.5 hours

---

## Phase 4: Extract ResultRecorder

**Target Lines**: ~50 lines extracted

### Responsibilities to Extract

- `recordResult(ResultCaptor, ExperimentResultAggregator)`
- `determineSuccess(UseCaseResult)`
- `determineFailureCategory(UseCaseResult, UseCaseCriteria)`

### New Classes

```
src/main/java/org/javai/punit/experiment/engine/
├── ResultRecorder.java       (~40 lines)
└── ...
```

### Test File

```
src/test/java/org/javai/punit/experiment/engine/
└── ResultRecorderTest.java   (~120 lines, 15-18 tests)
```

### Test Cases

1. Record success when criteria.allPassed() is true
2. Record failure when criteria.allPassed() is false
3. Record success from "success" boolean field
4. Record success from "isValid" boolean field
5. Record failure from "error" field presence
6. Default to success when no indicators present
7. Extract failure category from first failed criterion
8. Extract failure category from "failureCategory" field
9. Extract failure category from "errorType" field
10. Handle exception recording
11. Handle null captor gracefully
12. Handle empty result gracefully

### Estimated Effort

- Implementation: ~0.5 hours
- Tests: ~1 hour

---

## Phase 5: Extract ExperimentPacingOrchestrator

**Target Lines**: ~40 lines extracted

### Responsibilities to Extract

- `resolvePacing(Method, int, Experiment)`
- `applyPacingDelay(Store)`
- `computeTotalSamples(Experiment, Method)`
- Pacing reporting (currently inline)

### New Classes

```
src/main/java/org/javai/punit/experiment/engine/
├── ExperimentPacingOrchestrator.java  (~60 lines)
└── ...
```

### Test File

```
src/test/java/org/javai/punit/experiment/engine/
└── ExperimentPacingOrchestratorTest.java  (~100 lines, 12-15 tests)
```

### Test Cases

1. Resolve pacing from @Pacing annotation
2. Resolve pacing with no annotation (defaults)
3. Compute total samples for MEASURE mode
4. Compute total samples for EXPLORE mode
5. Apply pacing delay (skip first sample)
6. Apply pacing delay (subsequent samples)
7. Handle null pacing configuration
8. Handle missing global sample counter
9. Report pacing configuration when enabled
10. Report feasibility warning when pacing exceeds time budget

### Estimated Effort

- Implementation: ~0.5 hours
- Tests: ~1 hour

---

## Phase 6: Extract ExperimentReporter

**Target Lines**: ~30 lines extracted

### Responsibilities to Extract

- `reportProgress(ExtensionContext, ExperimentResultAggregator, int, int)`
- `publishFinalReport(ExtensionContext, ExperimentResultAggregator)`

### New Classes

```
src/main/java/org/javai/punit/experiment/engine/
├── ExperimentReporter.java   (~40 lines)
└── ...
```

### Test File

```
src/test/java/org/javai/punit/experiment/engine/
└── ExperimentReporterTest.java  (~80 lines, 10-12 tests)
```

### Test Cases

1. Report progress with current sample and total
2. Report progress with success rate
3. Publish final report with all metrics
4. Include termination reason in final report
5. Include elapsed time in final report
6. Include token usage in final report

### Estimated Effort

- Implementation: ~0.5 hours
- Tests: ~0.5 hours

---

## Phase 7: Move Invocation Context Records to Own Files

**Target Lines**: ~150 lines moved

### Classes to Extract

Move from `ExperimentExtension` to their own files:

```
src/main/java/org/javai/punit/experiment/engine/
├── MeasureInvocationContext.java            (~30 lines)
├── MeasureWithFactorsInvocationContext.java (~40 lines)
├── ExploreInvocationContext.java            (~40 lines)
└── ...
```

### Notes

- These are currently `private record` declarations
- Move to package-private files
- No new tests needed (tested via integration)

### Estimated Effort

- Implementation: ~0.5 hours
- No additional tests (covered by integration)

---

## Phase 8: Move Parameter Resolvers to Own Files

**Target Lines**: ~120 lines moved

### Classes to Extract

Move from `ExperimentExtension` to their own files:

```
src/main/java/org/javai/punit/experiment/engine/
├── CaptorParameterResolver.java     (~50 lines)
├── FactorParameterResolver.java     (~40 lines)
├── FactorValuesResolver.java        (~30 lines)
├── FactorValuesInitializer.java     (~50 lines)
└── ...
```

### Notes

- These are currently `private static class` declarations
- Move to package-private files
- No new tests needed (tested via integration)

### Estimated Effort

- Implementation: ~0.5 hours
- No additional tests (covered by integration)

---

## Summary

| Phase | Description | Lines Extracted | New Test Count |
|-------|-------------|-----------------|----------------|
| 1 | FactorResolver | ~130 | 15-20 |
| 2 | SpecGenerator | ~200 | 15-20 |
| 3 | ExperimentOutputPathResolver | ~60 | 12-15 |
| 4 | ResultRecorder | ~50 | 15-18 |
| 5 | ExperimentPacingOrchestrator | ~40 | 12-15 |
| 6 | ExperimentReporter | ~30 | 10-12 |
| 7 | Invocation Context Records | ~150 | 0 |
| 8 | Parameter Resolvers | ~120 | 0 |
| **Total** | | **~780** | **~80** |

### Expected Final State

| Metric | Before | After |
|--------|--------|-------|
| Lines in ExperimentExtension | 1,510 | ~650-750 |
| Nested Records/Classes | 8 | 0 |
| Helper Classes | 0 | 10+ |
| Unit Test Coverage | Low | High |

---

## Execution Strategy

1. **One phase per feature branch**: `refactor/extract-factor-resolver`, etc.
2. **Test before extracting**: Write helper class tests first
3. **Delegate, don't duplicate**: Replace inline code with delegation calls
4. **Verify all existing tests pass**: Run full test suite after each phase
5. **Commit, push, create PR**: After each phase passes tests

---

## Dependencies

- Phase 2 (SpecGenerator) depends on Phase 3 (OutputPathResolver)
- Phase 2 (SpecGenerator) may use Phase 1 (FactorResolver) for covariate naming
- All other phases are independent

**Recommended order**: 1 → 3 → 2 → 4 → 5 → 6 → 7 → 8

---

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Breaking existing experiment behavior | Run full test suite after each extraction |
| Store access patterns change | Document store keys in each helper |
| Thread safety issues | Use same atomic patterns as original |
| Parameter resolver ordering | Maintain `getAdditionalExtensions()` order |

