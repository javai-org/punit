# OPTIMIZE Mode Implementation Plan

## Document Status
**Status**: Draft
**Version**: 0.1
**Last Updated**: 2026-01-16
**Related Documents**: [OPTIMIZE-REQ.md](./OPTIMIZE-REQ.md), [OPTIMIZE-DESIGN.md](./OPTIMIZE-DESIGN.md)

---

## Overview

This document describes the phased implementation plan for OPTIMIZE mode. Each phase has clear deliverables, testing requirements, and acceptance criteria. Phases are designed to be incremental, with each building on the previous.

**Guiding Principles:**
- Each phase delivers working, tested code
- Integration tests validate cross-component behaviour
- Documentation updates are not deferred to the end
- LLM-specific support is isolated in the `llmx` package

---

## Phase 1: Core Data Structures

**Objective**: Establish the foundational types that all other components depend on.

### Deliverables

| Component               | Package                               | Description                                |
|-------------------------|---------------------------------------|--------------------------------------------|
| `FactorSuit`            | `org.javai.punit.experiment.optimize` | Immutable factor value container           |
| `OptimizationStatistics`          | `org.javai.punit.experiment.optimize` | Statistics from N outcomes                 |
| `OptimizationIterationAggregate`  | `org.javai.punit.experiment.optimize` | Factor suit + statistics for one iteration |
| `OptimizationRecord`              | `org.javai.punit.experiment.optimize` | Scored iteration with status               |
| `OptimizationObjective` | `org.javai.punit.experiment.optimize` | Enum: MAXIMIZE, MINIMIZE                   |

### Testing

- Unit tests for `FactorSuit` immutability and `with()` behaviour
- Unit tests for record equality and accessors
- Property-based tests for `FactorSuit.of()` varargs parsing

### Acceptance Criteria

- All types are immutable
- `FactorSuit.with()` returns a new instance without modifying the original
- Full Javadoc with examples

---

## Phase 2: Core Interfaces

**Objective**: Define the contracts for pluggable components.

### Deliverables

| Component           | Package                               | Description                             |
|---------------------|---------------------------------------|-----------------------------------------|
| `Scorer<A>`         | `org.javai.punit.experiment.optimize` | Evaluates aggregates, returns scalar    |
| `ScoringException`  | `org.javai.punit.experiment.optimize` | Checked exception for scoring failures  |
| `FactorMutator<F>`                | `org.javai.punit.experiment.optimize` | Generates new factor values             |
| `MutationException`               | `org.javai.punit.experiment.optimize` | Checked exception for mutation failures |
| `OptimizationTerminationPolicy`   | `org.javai.punit.experiment.optimize` | Decides when to stop                    |
| `OptimizationTerminationReason`   | `org.javai.punit.experiment.optimize` | Record with cause and message           |

### Testing

- Interface contract tests using mock implementations
- Verify exception handling paths

### Acceptance Criteria

- Interfaces are `@FunctionalInterface` where appropriate
- Default methods have sensible implementations
- Javadoc specifies behavioural contracts

---

## Phase 3: Standard Implementations

**Objective**: Provide ready-to-use implementations for common scenarios.

### Deliverables

**Scorers:**

| Component               | Description                                      |
|-------------------------|--------------------------------------------------|
| `SuccessRateScorer`     | Scores by success rate (most common)             |
| `CostEfficiencyScorer`  | Balances success rate against token consumption  |
| `WeightedScorer`        | Combines multiple scorers with weights           |

**Termination Policies:**

| Component                              | Description                                  |
|----------------------------------------|----------------------------------------------|
| `OptimizationMaxIterationsPolicy`      | Stops after N iterations                     |
| `OptimizationNoImprovementPolicy`      | Stops if no improvement for N iterations     |
| `OptimizationTimeBudgetPolicy`         | Stops when time budget exhausted             |
| `OptimizationCompositeTerminationPolicy` | Combines policies (any triggers termination) |

**Mutators:**

| Component              | Description                            |
|------------------------|----------------------------------------|
| `NoOpFactorMutator<F>` | Returns value unchanged (for testing)  |

### Testing

- Unit tests for each scorer with known inputs/outputs
- Unit tests for each termination policy with crafted histories
- Integration test: `CompositeTerminationPolicy` combines correctly

### Acceptance Criteria

- All implementations have `description()` returning human-readable text
- Edge cases handled (empty history, zero values, etc.)

---

## Phase 4: Optimization History

**Objective**: Implement the complete history tracking and query API.

### Deliverables

| Component                     | Package                               | Description                          |
|-------------------------------|---------------------------------------|--------------------------------------|
| `OptimizationHistory`         | `org.javai.punit.experiment.optimize` | Complete audit trail                 |
| `OptimizationHistory.Builder` | `org.javai.punit.experiment.optimize` | Builder for incremental construction |

### Key Behaviours

- `bestIteration()` returns the iteration with optimal score (respecting objective)
- `scoreImprovement()` and `scoreImprovementPercent()` calculate deltas
- `buildPartial()` allows querying mid-optimization
- `lastNIterations(n)` supports mutator feedback

### Testing

- Unit tests for best iteration tracking (MAXIMIZE vs MINIMIZE)
- Unit tests for improvement calculations
- Test partial build during optimization loop

### Acceptance Criteria

- History is immutable once built
- Builder allows partial queries for in-progress optimization
- All metadata captured (timing, descriptions, termination reason)

---

## Phase 5: Orchestrator

**Objective**: Implement the core optimization loop.

### Deliverables

| Component                       | Package                               | Description                            |
|---------------------------------|---------------------------------------|----------------------------------------|
| `OptimizationConfig<F>`         | `org.javai.punit.experiment.optimize` | Configuration for orchestrator         |
| `OptimizationOrchestrator<F>`   | `org.javai.punit.experiment.optimize` | Executes the optimization loop         |
| `OptimizationOutcomeAggregator` | `org.javai.punit.experiment.optimize` | Aggregates outcomes to statistics      |

### Key Behaviours

- Loop: execute → aggregate → score → record → check termination → mutate
- Handles scoring failures gracefully (terminates with partial history)
- Handles mutation failures gracefully (terminates with partial history)
- Respects budget limits (time, tokens)

### Testing

- Unit tests with mock executor, scorer, mutator, termination policy
- Test normal completion (termination policy triggers)
- Test scoring failure path
- Test mutation failure path
- Test budget exhaustion paths

### Acceptance Criteria

- Orchestrator is stateless (all state in history)
- Progress can be observed via callbacks (for reporting)
- Full history available regardless of termination cause

---

## Phase 6: JUnit Integration

**Objective**: Integrate OPTIMIZE mode into the existing experiment framework.

### Deliverables

| Component             | Change                                               |
|-----------------------|------------------------------------------------------|
| `ExperimentMode`      | Add `OPTIMIZE` enum value                            |
| `@Experiment`         | Add OPTIMIZE-specific attributes (see design §7.1)   |
| `ExperimentExtension` | Handle `mode = OPTIMIZE`                             |
| `@FixedFactors`       | New annotation for specifying fixed factor source    |
| `@InitialFactorValue` | New annotation for initial treatment factor value    |

### Key Behaviours

- `ExperimentMode.OPTIMIZE` has sensible defaults (20 samples/iteration, 20 max iterations)
- Extension validates OPTIMIZE-specific required fields
- Extension constructs `OptimizationOrchestrator` from annotation values

### Testing

- Integration tests using actual JUnit 5 test execution
- Test validation errors for missing required fields
- Test that experiment method executes correct number of times

### Acceptance Criteria

- OPTIMIZE experiments run via `./gradlew optimize`
- Annotation validation provides clear error messages
- Backward compatible: MEASURE and EXPLORE unchanged

---

## Phase 7: Output and Reporting

**Objective**: Implement reporting and persistence consistent with existing modes.

### Deliverables

| Component                | Description                        |
|--------------------------|------------------------------------|
| `OptimizationReporter`   | Formats output via `PUnitReporter` |
| `OptimizationYamlWriter` | Persists history to YAML           |
| Progress callbacks       | Real-time iteration progress       |

### Key Behaviours

- Three verbosity levels: MINIMAL, SUMMARY, FULL (see design §8.1)
- Progress reported after each iteration (see design §8.2)
- YAML persisted to `src/test/resources/punit/optimizations/`

### Testing

- Unit tests for each verbosity level output
- Test YAML round-trip (write then parse)
- Test progress callback invocation

### Acceptance Criteria

- Output style matches existing PUnit reporting
- YAML schema documented
- Full history only in FULL verbosity

---

## Phase 8: LLM-Specific Support

**Objective**: Provide LLM-specific mutator in the `llmx` package.

### Deliverables

| Component          | Package                | Description                      |
|--------------------|------------------------|----------------------------------|
| `LLMStringFactorMutator` | `org.javai.punit.llmx` | LLM-based string factor mutation |
| `PromptGuardrails` | `org.javai.punit.llmx` | Constraints for prompt mutation  |

### Key Behaviours

- Uses configurable LLM client
- Formats recent iteration feedback for context
- Respects max length and content constraints
- Validates mutated values before returning

### Testing

- Unit tests with mock LLM client
- Test constraint validation (max length, etc.)
- Test feedback formatting

### Acceptance Criteria

- `LLMStringFactorMutator` works with any `LlmClient` implementation
- Guardrails are configurable
- Errors produce clear `MutationException` messages

---

## Phase 9: Documentation

**Objective**: Update user-facing documentation.

### Deliverables

| Document                   | Updates                                   |
|----------------------------|-------------------------------------------|
| `USER-GUIDE.md`            | Add "Part 5: OPTIMIZE Mode" section       |
| `ExperimentMode` Javadoc   | Add OPTIMIZE to mode comparison table     |
| `@Experiment` Javadoc      | Document OPTIMIZE-specific attributes     |
| Example experiment         | `ShoppingOptimizationExperiment.java`     |

### USER-GUIDE.md Additions

- When to use OPTIMIZE (after EXPLORE, before MEASURE baseline)
- Workflow diagram: EXPLORE → OPTIMIZE → MEASURE
- Basic example with `SuccessRateScorer`
- Advanced example with `WeightedScorer` and custom mutator
- Interpreting optimization output
- Troubleshooting common issues

### Testing

- Documentation review for accuracy
- Example code compiles and runs
- Links verified

### Acceptance Criteria

- User can follow guide to run first OPTIMIZE experiment
- Workflow context is clear (relationship to EXPLORE/MEASURE)
- Examples are copy-paste runnable

---

## Phase Dependencies

```
Phase 1: Core Data Structures
    │
    ▼
Phase 2: Core Interfaces
    │
    ├──────────────────────┐
    ▼                      ▼
Phase 3: Standard      Phase 4: Optimization
    Implementations        History
    │                      │
    └──────────┬───────────┘
               ▼
         Phase 5: Orchestrator
               │
               ▼
         Phase 6: JUnit Integration
               │
               ├──────────────────────┐
               ▼                      ▼
         Phase 7: Output         Phase 8: LLM-Specific
             and Reporting           Support
               │                      │
               └──────────┬───────────┘
                          ▼
                    Phase 9: Documentation
```

---

## Milestones

| Milestone                | Phases | Description                                           |
|--------------------------|--------|-------------------------------------------------------|
| **M1: Foundation**       | 1-2    | Data structures and interfaces defined                |
| **M2: Core Engine**      | 3-5    | Optimization loop functional with standard components |
| **M3: Integrated**       | 6-7    | Full JUnit integration with reporting                 |
| **M4: Production Ready** | 8-9    | LLM support and documentation complete                |

---

## Risk Mitigation

| Risk                                    | Mitigation                                           |
|-----------------------------------------|------------------------------------------------------|
| Orchestrator complexity                 | Keep loop simple; delegate to pluggable components   |
| JUnit integration breaks existing modes | Extensive backward-compatibility tests               |
| YAML schema changes                     | Version the schema; migration path for old files     |
| LLM mutator unreliable                  | Retry logic; fallback to previous value on failure   |

---

## Open Questions

1. **Gradle task name**: Should OPTIMIZE experiments run via `./gradlew optimize` (new task) or `./gradlew experiment --mode=optimize` (flag)?

2. **Budget sharing**: Should OPTIMIZE share budget infrastructure with MEASURE/EXPLORE or have its own?

3. **Parallel evaluation**: Should we support evaluating multiple mutations in parallel within an iteration? (Deferred per design, but impacts orchestrator design.)
