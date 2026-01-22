# Contract System Migration Plan

## Overview

This document outlines the plan to consolidate the duplicate type systems in PUnit:
- **Old system** (`org.javai.punit.model`): `CriterionOutcome`, `UseCaseCriteria`, `UseCaseOutcome`
- **New system** (`org.javai.punit.contract`): `PostconditionResult`, `PostconditionEvaluator<R>`, `UseCaseOutcome<R>`

The goal is to eliminate redundancy by adopting the new typed contract system as the canonical approach.

## Current State

### Old Types (model package) - TO BE REMOVED

```
CriterionOutcome (sealed interface)
├── Passed(description)
├── Failed(description, reason)
├── Errored(description, cause)
└── NotEvaluated(description)

UseCaseCriteria (interface)
├── entries(): List<Entry<String, Supplier<Boolean>>>
├── evaluate(): List<CriterionOutcome>
└── allPassed(): boolean

UseCaseOutcome (record)
├── result: UseCaseResult
└── criteria: UseCaseCriteria

UseCaseContract (interface) - DEAD, DELETE
```

### New Types (contract package) - CANONICAL

```
PostconditionResult (record) - SIMPLIFIED
├── description: String
└── outcome: Outcome<?>              ← leverages existing Outcome type

PostconditionEvaluator<R> (interface)
├── evaluate(R result): List<PostconditionResult>
└── postconditionCount(): int

UseCaseOutcome<R> (record)
├── result: R                        ← typed!
├── executionTime: Duration
├── metadata: Map<String, Object>
└── postconditionEvaluator: PostconditionEvaluator<R>

UseCaseResult (record) - MOVE FROM MODEL
├── values: Map<String, Object>
├── metadata: Map<String, Object>
├── timestamp: Instant
└── executionTime: Duration
```

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

### 2. UseCaseResult Location

**Decision**: Move `UseCaseResult` from `model` package to `contract` package.

`UseCaseResult` (Map-based result container) is useful for:
- Storing arbitrary named values when a typed result isn't appropriate
- Metadata storage
- Execution timing

It coexists with `UseCaseOutcome<R>` for different use cases:
- `UseCaseOutcome<R>` - when you have a typed result
- `UseCaseResult` - when you need a bag of named values

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

### Phase 10: Move UseCaseResult to Contract Package

**Goal**: Relocate `UseCaseResult` from model to contract package.

Tasks:
- [ ] Move `UseCaseResult.java` from `model` to `contract` package
- [ ] Update all import statements across the codebase
- [ ] Update package-info.java in both packages
- [ ] Run tests to verify

Files affected:
- `src/main/java/org/javai/punit/model/UseCaseResult.java` → `src/main/java/org/javai/punit/contract/UseCaseResult.java`
- All files that import `org.javai.punit.model.UseCaseResult`

### Phase 11: Delete Old Types

**Goal**: Remove the redundant old types from model package.

Tasks:
- [ ] Delete `CriterionOutcome.java`
- [ ] Delete `UseCaseCriteria.java`
- [ ] Delete `model/UseCaseOutcome.java`
- [ ] Delete adapter classes created during integration attempt:
  - `PostconditionResultAdapter.java`
  - `ContractCriteriaAdapter.java`
- [ ] Update package-info.java files
- [ ] Final test run to ensure nothing is broken

Files to delete:
- `src/main/java/org/javai/punit/model/CriterionOutcome.java`
- `src/main/java/org/javai/punit/model/UseCaseCriteria.java`
- `src/main/java/org/javai/punit/model/UseCaseOutcome.java`
- `src/main/java/org/javai/punit/contract/PostconditionResultAdapter.java`
- `src/main/java/org/javai/punit/contract/ContractCriteriaAdapter.java`
- Associated test files

### Phase 12: Documentation and Cleanup

**Goal**: Update documentation to reflect the new architecture.

Tasks:
- [ ] Update CLAUDE.md if needed
- [ ] Update package-info.java files
- [ ] Remove any remaining references to old types
- [ ] Final review and cleanup

## Risk Mitigation

### Backward Compatibility

The migration will break existing use case implementations that use the old types. Mitigation:
- Document the migration path clearly
- Provide examples of migrated use cases
- Consider a deprecation period if external users exist

### Testing Strategy

- Run full test suite after each phase
- Ensure each phase is independently committable
- Write new tests for migrated functionality before deleting old tests

## Success Criteria

1. All tests pass
2. No references to `CriterionOutcome`, `UseCaseCriteria`, or `model.UseCaseOutcome` remain
3. Example use cases demonstrate the new contract pattern
4. Code is cleaner with single source of truth for outcome types

## Resolved Questions

1. **~~Errored state~~**: No. Use `Outcome<?>` which captures failure reasons. Exception details go in the failure message.

2. **~~UseCaseResult~~**: Keep it. Move to contract package. Coexists with typed `UseCaseOutcome<R>`.

3. **~~UseCaseContract interface~~**: Dead. Delete it. `ServiceContract<I, R>` replaces it.

4. **~~Lazy evaluation~~**: Both systems are lazy (evaluate on demand). No design change needed. Memoization is a potential future optimization.

## Open Questions

None currently.
