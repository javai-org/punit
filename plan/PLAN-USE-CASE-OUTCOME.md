# Implementation Plan: UseCaseOutcome Fluent API

This document defines a phased implementation plan for the UseCaseOutcome redesign described in [DESIGN-USE-CASE-OUTCOME.md](./DESIGN-USE-CASE-OUTCOME.md).

## Overview

The implementation is divided into phases that build upon each other. Each phase delivers working, tested functionality that can be reviewed before proceeding.

## Phase 1: Core Types

**Goal:** Establish the foundational types without breaking existing functionality.

### Deliverables

1. **`Outcome<T>`** — Result type for fallible operations
   - `Outcome.success(T value)`
   - `Outcome.failure(String reason)`
   - `isSuccess()`, `isFailure()`
   - `value()`, `failureReason()`
   - `Outcome.lift(Function<A, B>)` — wraps pure functions

2. **`UseCase<R>`** — Interface for use case classes
   - Type parameter `R` for result type
   - Marker interface (no methods required initially)

3. **`Postcondition<T>`** — Represents a single ensure clause
   - `String description`
   - `Predicate<T> predicate`
   - `evaluate(T value)` → `PostconditionResult`

4. **`PostconditionResult`** — Evaluation result
   - `PASSED`, `FAILED`, `SKIPPED`
   - Failure reason when applicable

### Tests

- Unit tests for `Outcome` operations
- Unit tests for `Postcondition` evaluation
- Tests for `Outcome.lift()` behavior

---

## Phase 2: ServiceContract Builder

**Goal:** Implement the fluent API for defining service contracts.

### Deliverables

1. **`ServiceContract<I, R>`** — Immutable contract definition
   - List of preconditions (`Predicate<I>` with descriptions)
   - List of derivation groups (each with postconditions)

2. **`ServiceContract.define()`** — Entry point for builder

3. **`ContractBuilder<I, R>`** — Fluent builder
   - `.require(String description, Predicate<I> predicate)`
   - `.deriving(String description, Function<R, Outcome<D>> fn)` — fallible
   - `.deriving(Function<R, Outcome<D>> fn)` — infallible
   - Returns `DerivingBuilder<I, R, D>` after deriving

4. **`DerivingBuilder<I, R, D>`** — Builder for postconditions within a derivation
   - `.ensure(String description, Predicate<D> predicate)`
   - `.deriving(...)` — starts new derivation group (returns to raw result)
   - Implicit build on terminal operations

### Tests

- Builder produces correct `ServiceContract` structure
- Preconditions captured with descriptions
- Multiple derivation groups supported
- Derivation descriptions captured correctly

---

## Phase 3: Contract Evaluation Engine

**Goal:** Implement the logic that evaluates contracts against inputs and results.

### Deliverables

1. **`ContractEvaluator<I, R>`** — Evaluates a contract
   - `checkPreconditions(I input)` — throws `UseCasePreconditionException` on failure
   - `evaluatePostconditions(R result)` → `List<PostconditionResult>`

2. **`UseCasePreconditionException`** — Unchecked exception
   - Contains failed precondition description
   - Contains input value (for debugging)

3. **Derivation gate logic**
   - If derivation succeeds → evaluate nested ensures
   - If derivation fails → mark derivation ensure as FAILED, nested ensures as SKIPPED

4. **Lazy evaluation support**
   - Postconditions stored as lambdas
   - Evaluated only when requested

### Tests

- Precondition failure throws with correct message
- Postcondition evaluation returns correct results
- Derivation failure skips nested ensures
- Skipped ensures not counted in statistics

---

## Phase 4: UseCaseOutcome Builder

**Goal:** Implement the fluent API for building outcomes in use case methods.

### Deliverables

1. **`UseCaseOutcome<R>`** — The outcome type
   - `R result()` — the raw result
   - `Duration executionTime()`
   - `Map<String, Object> metadata()`
   - `List<PostconditionResult> evaluatePostconditions()`
   - `boolean allPostconditionsSatisfied()`

2. **`UseCaseOutcome.withContract(ServiceContract<I, R>)`** — Entry point

3. **`OutcomeBuilder<I, R>`** — Fluent builder
   - `.input(I input)` — provides input, checks preconditions (eager)
   - `.execute(Function<I, R> fn)` — runs service, captures timing
   - `.meta(String key, Object value)` — adds metadata
   - `.build()` — produces `UseCaseOutcome<R>`

4. **Automatic timing capture**
   - Start instant captured before `execute` function runs
   - End instant captured after function completes
   - Duration stored in outcome

### Tests

- Builder produces correct `UseCaseOutcome`
- Preconditions checked at `input()` call
- Timing captured accurately
- Metadata accessible
- Postconditions evaluate correctly through outcome

---

## Phase 5: Integration with PUnit Infrastructure

**Goal:** Connect the new types to PUnit's experiment and test execution.

### Deliverables

1. **Replace existing `UseCaseResult` and `UseCaseCriteria`**
   - Remove old types entirely
   - Update all internal references to use new types

2. **Integration with `SampleResultAggregator`**
   - Map postcondition results to pass/fail for aggregation
   - Handle SKIPPED postconditions appropriately

3. **Integration with early termination**
   - Lazy evaluation supports early termination strategies
   - Contract evaluation can be interrupted

4. **Integration with specification generation**
   - MEASURE experiments can derive specs from new outcome format
   - Baseline derivation works with postcondition results

### Tests

- End-to-end test with `@ProbabilisticTest`
- End-to-end test with `@MeasureExperiment`
- Aggregation produces correct statistics
- Early termination works correctly

---

## Phase 6: Migration of ShoppingBasketUseCase

**Goal:** Migrate the example use case to validate the design.

### Deliverables

1. **Migrated `ShoppingBasketUseCase`**
   - Implements `UseCase<String>`
   - Private `ServiceInput` record
   - Static `ServiceContract` definition
   - Refactored `translateInstruction` method

2. **Updated experiments**
   - `ShoppingBasketMeasure` works with new outcome
   - `ShoppingBasketExplore` works with new outcome

3. **Updated tests**
   - `ShoppingBasketTest` works with new outcome

4. **Validation**
   - All existing tests pass
   - Experiment output unchanged (or improved)

### Tests

- Existing `ShoppingBasketTest` passes
- Experiments produce valid specifications
- No regression in functionality

---

## Phase 7: Documentation and Cleanup

**Goal:** Finalize documentation and remove redundant code.

### Deliverables

1. **Updated Javadoc**
   - All new public types documented
   - Examples in Javadoc

2. **Updated User Guide**
   - Section on defining service contracts
   - Section on writing use case methods

3. **Code removal**
   - Delete old `UseCaseCriteria`, `UseCaseResult`, and related types
   - Remove all redundant code paths

4. **Final cleanup**
   - Ensure consistent style
   - Verify no dead code remains

---

## Dependencies Between Phases

```
Phase 1: Core Types
    │
    ▼
Phase 2: ServiceContract Builder
    │
    ▼
Phase 3: Contract Evaluation Engine
    │
    ▼
Phase 4: UseCaseOutcome Builder
    │
    ▼
Phase 5: Integration ◄─────────────┐
    │                              │
    ▼                              │
Phase 6: Migration ────────────────┘
    │
    ▼
Phase 7: Documentation
```

Phases 1-4 are strictly sequential. Phase 5 may reveal issues that require revisiting earlier phases. Phase 6 validates the entire design. Phase 7 can begin in parallel with Phase 6.

---

## Risk Mitigation

| Risk               | Mitigation                                    |
|--------------------|-----------------------------------------------|
| Type complexity    | Review generics with fresh eyes after Phase 2 |
| Builder ergonomics | Validate with real use cases in Phase 6       |

---

## Success Criteria

1. **ShoppingBasketUseCase** is cleaner and more readable
2. **Contract is visible** at top of use case class
3. **No timing boilerplate** in use case methods
4. **All tests pass** after migration
5. **Postcondition failure semantics** are correct (derivation gates work)
6. **Statistical analysis** produces correct results
7. **No redundant code** remains in codebase

---

*Related: [DESIGN-USE-CASE-OUTCOME.md](./DESIGN-USE-CASE-OUTCOME.md)*
