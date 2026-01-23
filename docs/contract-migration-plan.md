# Contract System Migration Plan

## Overview

This document outlines the plan to consolidate the duplicate type systems in PUnit:
- **Old system** (`org.javai.punit.model`): `CriterionOutcome`, `UseCaseCriteria`, `UseCaseOutcome`
- **New system** (`org.javai.punit.contract`): `PostconditionResult`, `PostconditionEvaluator<R>`, `UseCaseOutcome<R>`

The goal is to eliminate redundancy by adopting the new typed contract system as the canonical approach.

## Current State ✅ MIGRATION COMPLETE

### Canonical Types (contract package)

```
ServiceContract<I, R> (class)
├── preconditions: List<Precondition<I>>
├── postconditions: List<Postcondition<R>>
└── derivations: List<Derivation<I, R, ?>>

PostconditionResult (record)
├── description: String
└── outcome: Outcome<?>              ← leverages existing Outcome type

PostconditionEvaluator<R> (interface)
├── evaluate(R result): List<PostconditionResult>
└── postconditionCount(): int

UseCaseOutcome<R> (record)
├── result: R                        ← typed!
├── executionTime: Duration
├── timestamp: Instant
├── metadata: Map<String, Object>
└── postconditionEvaluator: PostconditionEvaluator<R>
```

### Deleted Types (model package) - REMOVED

All legacy types have been deleted:
- `CriterionOutcome` - replaced by `PostconditionResult`
- `UseCaseCriteria` - replaced by `PostconditionEvaluator<R>`
- `UseCaseOutcome` (model) - replaced by `UseCaseOutcome<R>` (contract)
- `UseCaseResult` - replaced by typed result objects
- `UseCaseContract` - replaced by `ServiceContract<I, R>`

## Design Decisions

### 1. PostconditionResult Simplification

**Decision**: Simplify `PostconditionResult` to use `Outcome<?>` internally.

```java
// Before: sealed interface with 3 variants
sealed interface PostconditionResult {
    record Passed(String description) ...
    record Failed(String description, String reason) ...
    record Skipped(String description, String reason) ...
}

// After: single record wrapping Outcome
record PostconditionResult(String description, Outcome<?> outcome) {
    boolean passed() { return outcome.isSuccess(); }
    boolean failed() { return outcome.isFailure(); }
}
```

**Rationale**: The `Outcome<?>` type already provides:
- Success/failure semantics
- Failure reason via `failureMessage()`
- Optional result value for derivations

The "Skipped" case becomes a failure with reason "Derivation 'X' failed" - no special handling needed.

### 2. UseCaseResult Deletion

**Decision**: Delete `UseCaseResult` entirely.

The original plan was to move `UseCaseResult` from `model` to `contract` package.
After analysis, `UseCaseResult` was found to be unnecessary:

- The new contract pattern uses **typed results** (e.g., `TranslationResult`, `PaymentResult`)
- Typed results provide compile-time safety and better documentation
- Metadata is stored in `UseCaseOutcome.metadata()` Map
- No use case required a generic "bag of values" container

**Result**: `UseCaseResult` was deleted. All use cases now define typed result records.

### 3. UseCaseCriteria Replacement

**Decision**: Replace `UseCaseCriteria` with `PostconditionEvaluator<R>`.

Key improvement: Postconditions are defined as `Predicate<R>` that receive the result as a parameter, rather than `Supplier<Boolean>` that close over external state.

```java
// Old: Criteria close over result via closure
UseCaseCriteria criteria = UseCaseCriteria.ordered()
    .criterion("Not empty", () -> result.getString("response", "").length() > 0)
    .build();

// New: Postconditions receive result as parameter
ServiceContract<Input, String> contract = ServiceContract.<Input, String>define()
    .ensure("Not empty", response -> !response.isEmpty())
    .build();
```

### 4. UseCaseContract Interface

**Decision**: Delete. The `ServiceContract<I, R>` class fully replaces it.

## Migration Phases

### Phase 1: Simplify PostconditionResult ✅ COMPLETE

**Goal**: Refactor `PostconditionResult` from sealed interface to record with `Outcome<?>`.

Tasks:
- [x] Replace sealed interface with `record PostconditionResult(String description, Outcome<?> outcome)`
- [x] Add convenience methods: `passed()`, `failed()`, `failureReason()`
- [x] Add factory methods: `passed(description)`, `passed(description, value)`, `failed(description, reason)`
- [x] Update all usages in contract package (Postcondition, Derivation, etc.)
- [x] Update tests
- [x] Added `Outcomes.okVoid()` for void success results

Files affected:
- `src/main/java/org/javai/punit/contract/PostconditionResult.java`
- `src/main/java/org/javai/punit/contract/Postcondition.java`
- `src/main/java/org/javai/punit/contract/Derivation.java`
- `src/main/java/org/javai/punit/contract/Outcomes.java`
- `src/test/java/org/javai/punit/contract/PostconditionResultTest.java`

### Phase 2: Migrate CriteriaOutcomeAggregator ✅ COMPLETE

**Goal**: Update aggregator to use `PostconditionResult` instead of `CriterionOutcome`.

Tasks:
- [x] Created `PostconditionAggregator` with `record(List<PostconditionResult>)` method
- [x] Created `PostconditionStats` inner class (renamed from `CriterionStats`)
- [x] Added deprecated bridge method for `UseCaseCriteria` compatibility
- [x] Updated `ExperimentResultAggregator` to use `PostconditionAggregator`
- [x] Updated `CriteriaReporter` with new methods for `PostconditionResult`
- [x] Created tests for `PostconditionAggregator`

Files affected:
- `src/main/java/org/javai/punit/spec/criteria/PostconditionAggregator.java` (NEW)
- `src/main/java/org/javai/punit/spec/criteria/CriteriaReporter.java`
- `src/main/java/org/javai/punit/experiment/engine/ExperimentResultAggregator.java`
- `src/test/java/org/javai/punit/spec/criteria/PostconditionAggregatorTest.java` (NEW)

### Phase 3: Migrate ExperimentResultAggregator ✅ COMPLETE

**Goal**: Update to use `PostconditionEvaluator<R>` instead of `UseCaseCriteria`.

Tasks:
- [x] Added `recordPostconditions(List<PostconditionResult>)` method
- [x] Added `getPostconditionAggregator()`, `hasPostconditionStats()`, `getPostconditionPassRates()`
- [x] Deprecated legacy methods: `recordCriteria()`, `getCriteriaAggregator()`, `hasCriteriaStats()`, `getCriteriaPassRates()`
- [x] Updated `ResultCaptor` with postcondition accessors
- [x] Updated `ResultRecorder` to prioritize postconditions over legacy criteria

Files affected:
- `src/main/java/org/javai/punit/experiment/engine/ExperimentResultAggregator.java`
- `src/main/java/org/javai/punit/api/ResultCaptor.java`
- `src/main/java/org/javai/punit/experiment/engine/shared/ResultRecorder.java`

### Phase 4: Migrate ResultCaptor ✅ COMPLETE

**Goal**: Update to work with contract types as primary, legacy types as secondary.

Tasks:
- [x] Created `OutcomeCaptor` as the new primary class
- [x] Made `record(contract.UseCaseOutcome<R>)` the primary method
- [x] Deprecated `ResultCaptor` (now extends `OutcomeCaptor` for backward compatibility)
- [x] Deprecated legacy methods: `recordContract()`, `record(model.UseCaseOutcome)`, `record(UseCaseResult)`, `recordCriteria()`
- [x] Added new methods: `hasContractOutcome()`, `getPostconditionResults()`, `allPostconditionsPassed()`
- [x] Updated tests with deprecation suppressions

Files affected:
- `src/main/java/org/javai/punit/api/OutcomeCaptor.java` (NEW)
- `src/main/java/org/javai/punit/api/ResultCaptor.java` (deprecated alias)
- `src/main/java/org/javai/punit/experiment/engine/shared/ResultRecorder.java`

### Phase 5: Migrate ResultRecorder ✅ COMPLETE

**Goal**: Update to determine success from `PostconditionResult` instead of `CriterionOutcome`.

Tasks:
- [x] Updated `recordResult()` to prioritize contract postconditions
- [x] Added `determineFailureCategoryFromPostconditions()` method
- [x] Uses `OutcomeCaptor` as parameter type
- [x] Created comprehensive `ResultRecorderTest`
- [x] Fixed `OutcomeCaptor.hasResult()` to distinguish results from exceptions

Files affected:
- `src/main/java/org/javai/punit/experiment/engine/shared/ResultRecorder.java`
- `src/main/java/org/javai/punit/api/OutcomeCaptor.java`
- `src/test/java/org/javai/punit/experiment/engine/shared/ResultRecorderTest.java` (NEW)

### Phase 6: Migrate Criteria Infrastructure ✅ COMPLETE

**Goal**: Update or remove criteria-related classes that depend on old types.

**Assessment**: All four classes were UNUSED in production code:
- `CriteriaInvoker` - designed for old `criteria()` method pattern, not used
- `CriteriaResolver` - designed to resolve `criteria()` methods, not used
- `CriteriaOutcomeAggregator` - superseded by `PostconditionAggregator`, not used
- `CriteriaReporter` - formatting utilities never instantiated

Tasks:
- [x] Evaluated `CriteriaInvoker` - DELETE (not used, old pattern)
- [x] Evaluated `CriteriaResolver` - DELETE (not used, old pattern)
- [x] Evaluated `CriteriaOutcomeAggregator` - DELETE (superseded by `PostconditionAggregator`)
- [x] Evaluated `CriteriaReporter` - DELETE (not used)
- [x] Deleted all four classes and their tests
- [x] Verified build and tests pass

Files deleted:
- `src/main/java/org/javai/punit/spec/criteria/CriteriaInvoker.java`
- `src/main/java/org/javai/punit/spec/criteria/CriteriaResolver.java`
- `src/main/java/org/javai/punit/spec/criteria/CriteriaOutcomeAggregator.java`
- `src/main/java/org/javai/punit/spec/criteria/CriteriaReporter.java`
- `src/test/java/org/javai/punit/spec/criteria/CriteriaInvokerTest.java`
- `src/test/java/org/javai/punit/spec/criteria/CriteriaResolverTest.java`
- `src/test/java/org/javai/punit/spec/criteria/CriteriaOutcomeAggregatorTest.java`

### Phase 7: Delete UseCaseContract Interface ✅ COMPLETE

**Goal**: Remove the dead interface.

Tasks:
- [x] Deleted `UseCaseContract.java`
- [x] Removed `implements UseCaseContract` from `ShoppingBasketUseCase`
- [x] Removed `implements UseCaseContract` from `PaymentGatewayUseCase`
- [x] Updated `package-info.java` in examples/usecases
- [x] Updated `USER-GUIDE.md` to remove `UseCaseContract` references
- [x] Verified build and tests pass

Files deleted:
- `src/main/java/org/javai/punit/api/UseCaseContract.java`

Files modified:
- `src/test/java/org/javai/punit/examples/usecases/ShoppingBasketUseCase.java`
- `src/test/java/org/javai/punit/examples/usecases/PaymentGatewayUseCase.java`
- `src/test/java/org/javai/punit/examples/usecases/package-info.java`
- `docs/USER-GUIDE.md`

### Phase 8: Migrate Optimize Strategy Classes ✅ COMPLETE (via bridge)

**Goal**: Update optimization infrastructure to use contract types.

**Assessment**: The optimize infrastructure already works with contract-based outcomes
through the `OutcomeCaptor` bridge:

1. When a use case returns `contract.UseCaseOutcome<R>`, it's recorded via `OutcomeCaptor.record()`
2. `OutcomeCaptor` converts the contract outcome to `UseCaseResult` + `UseCaseCriteria`
3. `OptimizeStrategy.intercept()` reads from the captor and constructs `model.UseCaseOutcome`
4. The rest of the optimize pipeline works unchanged

This bridge approach means:
- [x] Optimize experiments work with BOTH old and new use case patterns
- [x] No changes needed to optimize code for Phase 9 (migrate use cases)
- [ ] Full migration to contract types deferred to Phase 11 (when deleting old types)

The optimize code will be updated to directly use `contract.UseCaseOutcome<?>` when
we delete `model.UseCaseOutcome` in Phase 11, forcing the refactoring at that point.

### Phase 9: Migrate Example Use Cases ✅ COMPLETE

**Goal**: Update example use cases to demonstrate the new contract pattern.

Tasks:
- [x] Migrated `ShoppingBasketUseCase` to use `ServiceContract` and `contract.UseCaseOutcome<TranslationResult>`
  - Created `TranslationResult` record as typed result
  - Replaced `UseCaseCriteria.ordered()` with `ServiceContract.define()`
  - Uses method references for postconditions: `TranslationResult::isValidJson`
- [x] Migrated `PaymentGatewayUseCase` to use `ServiceContract` and `contract.UseCaseOutcome<PaymentResult>`
  - Uses existing `PaymentResult` record as typed result
  - Single postcondition: `PaymentResult::success`
- [x] Added `assertAll()` method to `contract.UseCaseOutcome` for test compatibility
- [x] All tests pass (experiments use captor bridge for backward compatibility)

Files modified:
- `src/test/java/org/javai/punit/examples/usecases/ShoppingBasketUseCase.java`
- `src/test/java/org/javai/punit/examples/usecases/PaymentGatewayUseCase.java`
- `src/main/java/org/javai/punit/contract/UseCaseOutcome.java`

### Phase 10: Delete UseCaseResult ✅ COMPLETE

**Goal**: Remove the deprecated `UseCaseResult` type.

**Assessment**: After migrating all infrastructure to use `contract.UseCaseOutcome<R>`,
the `UseCaseResult` type (map-based result container) was no longer needed.

Tasks:
- [x] Updated `FailureObservation` to return `UseCaseOutcome<?>` instead of `UseCaseResult`
- [x] Updated `SuccessCriteria` to accept `Map<String, Object>` instead of `UseCaseResult`
- [x] Updated `ResultProjection` Javadoc to reference `UseCaseOutcome`
- [x] Updated `UseCaseContext` Javadoc example
- [x] Updated `ResultProjectionBuilderTest` to use `UseCaseOutcome`
- [x] Updated `SuccessCriteriaTest` to use `Map` directly
- [x] Deleted `UseCaseResult.java`
- [x] Deleted `UseCaseResultTest.java`
- [x] Deleted unused `UseCaseRegistry.java` and `UseCaseDefinition.java`

Files deleted:
- `src/main/java/org/javai/punit/model/UseCaseResult.java`
- `src/test/java/org/javai/punit/model/UseCaseResultTest.java`
- `src/main/java/org/javai/punit/experiment/engine/UseCaseRegistry.java`
- `src/main/java/org/javai/punit/experiment/engine/UseCaseDefinition.java`

Files modified:
- `src/main/java/org/javai/punit/model/FailureObservation.java`
- `src/main/java/org/javai/punit/spec/model/SuccessCriteria.java`
- `src/main/java/org/javai/punit/experiment/model/ResultProjection.java`
- `src/main/java/org/javai/punit/api/UseCaseContext.java`
- `src/test/java/org/javai/punit/spec/model/SuccessCriteriaTest.java`
- `src/test/java/org/javai/punit/experiment/engine/ResultProjectionBuilderTest.java`

### Phase 11: Delete All Legacy Types ✅ COMPLETE

**Goal**: Remove all redundant old types from model package.

**Completed Deletions:**

| Type | Status |
|------|--------|
| `model.UseCaseOutcome` | ✅ Deleted |
| `model.UseCaseCriteria` | ✅ Deleted |
| `model.CriterionOutcome` | ✅ Deleted |
| `model.UseCaseResult` | ✅ Deleted |
| `ResultCaptor` | ✅ Deleted (replaced by `OutcomeCaptor`) |
| `PostconditionResultAdapter` | ✅ Deleted |
| `ContractCriteriaAdapter` | ✅ Deleted |
| `UseCaseRegistry` | ✅ Deleted (unused) |
| `UseCaseDefinition` | ✅ Deleted (unused) |

**Infrastructure Migration:**

All infrastructure now uses `contract.UseCaseOutcome<R>` directly:

1. **Experiment infrastructure:**
   - `OutcomeCaptor` stores `UseCaseOutcome<?>` directly
   - `ResultRecorder` uses `outcome.allPostconditionsSatisfied()` for success determination
   - `ExperimentResultAggregator` accepts `UseCaseOutcome<?>` directly

2. **Optimize infrastructure:**
   - `OptimizeState` uses `List<UseCaseOutcome<?>>`
   - `DefaultOptimizationOutcomeAggregator` uses `outcome.allPostconditionsSatisfied()`
   - All strategies work with contract outcomes

3. **EXPLORE mode:**
   - `ResultProjectionBuilder` builds projections from `UseCaseOutcome<?>`
   - `DiffableContentProvider` receives `UseCaseOutcome<?>`

Tasks:
- [x] Migrated all invocation contexts from `ResultCaptor` to `OutcomeCaptor`
- [x] Migrated all strategies (MeasureStrategy, ExploreStrategy, OptimizeStrategy)
- [x] Updated parameter resolvers
- [x] Updated `DiffableContentProvider` interface
- [x] Deleted all legacy types and adapters
- [x] Deleted `ResultCaptor` (replaced by `OutcomeCaptor`)
- [x] Updated all tests
- [x] Verified build and all tests pass

### Phase 12: Documentation and Cleanup ✅ COMPLETE

**Goal**: Update documentation to reflect the new architecture.

Tasks:
- [x] Updated migration plan with assessment of each phase
- [x] Documented bridge pattern and its purpose
- [x] Example use cases demonstrate new contract pattern (`ShoppingBasketUseCase`, `PaymentGatewayUseCase`)
- [x] USER-GUIDE.md updated (Phase 7)
- [x] CLAUDE.md unchanged (no updates needed)

## Risk Mitigation

### Migration Complete

The migration was completed without breaking changes:
- All use cases now return `contract.UseCaseOutcome<R>` with typed results
- All infrastructure uses the contract types directly
- No bridge pattern or adapters needed
- Full backward compatibility maintained during incremental migration

### Testing Strategy

- ✅ Full test suite passes after each phase
- ✅ Each phase was independently committable
- ✅ New tests written for migrated functionality
- ✅ All tests updated to use new types

## Success Criteria ✅ ALL COMPLETE

1. ✅ All tests pass
2. ✅ Example use cases demonstrate the new contract pattern
3. ✅ All legacy types removed - no bridge pattern needed
4. ✅ Single canonical type system: `contract.UseCaseOutcome<R>`

## Resolved Questions

1. **~~Errored state~~**: No. Use `Outcome<?>` which captures failure reasons. Exception details go in the failure message.

2. **~~UseCaseResult~~**: Keep in model package. It's internal infrastructure, not part of the contract API.

3. **~~UseCaseContract interface~~**: Deleted. `ServiceContract<I, R>` replaces it.

4. **~~Lazy evaluation~~**: Both systems are lazy (evaluate on demand). No design change needed.

5. **~~Delete old types~~**: Deferred. Bridge pattern provides backward compatibility. Full deletion requires optimize infrastructure refactoring.

## Migration Summary

| Phase | Status | Description |
|-------|--------|-------------|
| 1 | ✅ Complete | Simplify PostconditionResult to use Outcome |
| 2 | ✅ Complete | Migrate CriteriaOutcomeAggregator → PostconditionAggregator |
| 3 | ✅ Complete | Migrate ExperimentResultAggregator |
| 4 | ✅ Complete | Migrate ResultCaptor → OutcomeCaptor |
| 5 | ✅ Complete | Migrate ResultRecorder |
| 6 | ✅ Complete | Delete unused criteria infrastructure |
| 7 | ✅ Complete | Delete UseCaseContract interface |
| 8 | ✅ Complete | Optimize infrastructure uses contract types directly |
| 9 | ✅ Complete | Migrate example use cases |
| 10 | ✅ Complete | Delete UseCaseResult and related types |
| 11 | ✅ Complete | Delete all legacy types (model package cleanup) |
| 12 | ✅ Complete | Documentation and cleanup |

## Final State

The migration is complete. PUnit now uses a single, unified type system:

**Canonical Types (`org.javai.punit.contract`):**
- `ServiceContract<I, R>` - Defines preconditions and postconditions
- `UseCaseOutcome<R>` - Typed result with postcondition evaluation
- `PostconditionResult` - Result of evaluating a single postcondition
- `PostconditionEvaluator<R>` - Interface for postcondition evaluation

**Deleted Types:**
- `model.UseCaseOutcome` - Replaced by `contract.UseCaseOutcome<R>`
- `model.UseCaseResult` - Replaced by typed result objects
- `model.UseCaseCriteria` - Replaced by `PostconditionEvaluator<R>`
- `model.CriterionOutcome` - Replaced by `PostconditionResult`
- `api.ResultCaptor` - Replaced by `OutcomeCaptor`
- `contract.PostconditionResultAdapter` - No longer needed
- `contract.ContractCriteriaAdapter` - No longer needed

## Open Questions

None - migration complete.
