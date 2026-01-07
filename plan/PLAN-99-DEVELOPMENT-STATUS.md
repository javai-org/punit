# PUnit Development Status

This document tracks the current implementation status of PUnit's development phases.

*Last updated: January 2026*

---

## Status Legend

| Symbol | Status        | Description                                   |
|--------|---------------|-----------------------------------------------|
| ✅      | **Complete**  | Fully implemented and tested                  |
| 🔄     | **Partial**   | Partially implemented; some elements remain   |
| 📋     | **Planned**   | Design complete; implementation pending       |
| ⏳     | **Future**    | Deferred to v2 or later                       |

---

## Track 1: Core Probabilistic Testing Framework

| Phase | Description                      | Status | Key Artifacts                                             |
|-------|----------------------------------|--------|-----------------------------------------------------------|
| C1    | Core Framework (MVP)             | ✅      | `@ProbabilisticTest`, `ProbabilisticTestExtension`        |
| C2    | Early Termination                | ✅      | `EarlyTerminationEvaluator`, `TerminationReason`          |
| C3    | Configuration System             | ✅      | `ConfigurationResolver`, system props, env vars           |
| C4    | Method-Level Cost Budget         | ✅      | `CostBudgetMonitor`, `TokenChargeRecorder`                |
| C5    | Budget Scopes                    | ✅      | `@ProbabilisticTestBudget`, `SharedBudgetMonitor`         |
| C6    | Enhanced Reporting               | ✅      | `maxExampleFailures`, suppressed exceptions               |
| C7    | Exception Handling               | ✅      | `onException`, parameter validation                       |
| C8    | Documentation                    | ✅      | Javadoc, README, examples                                 |

**Track 1 Status**: ✅ Complete

---

## Track 2: Experiment Extension

| Phase | Description                      | Status | Key Artifacts                                             |
|-------|----------------------------------|--------|-----------------------------------------------------------|
| E1    | Use Case Abstractions            | ✅      | `@UseCase`, `UseCaseResult`, `UseCaseContext`             |
| E2    | Single-Config Experiments        | ✅      | `@Experiment`, `ExperimentResultAggregator`               |
| E2b   | Multi-Config Experiments         | ✅      | `@ExperimentDesign`, `@Config`, `@ExperimentGoal`         |
| E2c   | Adaptive Experiments             | ✅      | `AdaptiveFactor`, `RefinementStrategy`                    |
| E3    | Specification Registry           | ✅      | `ExecutionSpecification`, `SpecificationRegistry`         |
| E4    | Spec-Driven Tests                | ✅      | `@ProbabilisticTest(spec=...)`, `SuccessCriteria`         |
| E5    | Backend Infrastructure           | ✅      | `ExperimentBackend` SPI, `ExperimentBackendRegistry`      |
| E6    | LLM Backend (llmx)               | ✅      | `org.javai.punit.llmx` package                            |
| E7    | Canonical Flow Examples          | ✅      | End-to-end examples, mock LLM client                      |
| E8    | Documentation                    | ✅      | Javadoc, user guide, migration guide                      |

**Track 2 Status**: ✅ Complete

---

## Track 3: Enhancements

| Phase | Description                      | Status | Key Artifacts                                             |
|-------|----------------------------------|--------|-----------------------------------------------------------|
| A     | Cost/Statistical Enhancements    | 📋     | See detailed status below                                 |
| B     | Adaptive Prompt Refinement       | 📋     | See detailed status below                                 |
| C     | Threshold Derivation             | 📋     | See detailed status below                                 |

**Track 3 Status**: 📋 Planned

---

## Phase A: Core Statistical and Cost Enhancements (Detailed)

| Component                                      | Status |
|------------------------------------------------|--------|
| Input/output token split in `CostSummary`      | ❌      |
| `TokenEstimator` interface                     | ❌      |
| Built-in model-specific estimators             | ❌      |
| `BasicCostEstimator` fallback                  | ❌      |
| Sample size advisory in baselines              | ❌      |
| Stability-based early termination              | ❌      |

---

## Phase B: Adaptive Prompt Refinement (Detailed)

| Component                                      | Status |
|------------------------------------------------|--------|
| `PromptContributor` interface                  | ❌      |
| `PromptConfiguration` interface                | ❌      |
| `PromptComponent` interface                    | ❌      |
| `@AdaptivePromptExperiment` annotation         | ❌      |
| `FailureCategorizer` interface                 | ❌      |
| Refinement loop orchestration                  | ❌      |

---

## Phase C: Pass Rate Threshold Derivation (Detailed)

| Component                                      | Status |
|------------------------------------------------|--------|
| `punit-statistics` isolated module             | ❌      |
| `RegressionThreshold` record                   | ❌      |
| `RegressionThresholdCalculator`                | ❌      |
| Wilson score bound implementation              | ❌      |
| Normal approximation implementation            | ❌      |
| Three operational approaches                   | ❌      |
| `ThresholdDerivationPolicy` enum               | ❌      |
| Enhanced `@ProbabilisticTest` attributes       | ❌      |
| Qualified failure reporting                    | ❌      |

---

## Quick Reference: Current vs. Planned

### CostSummary Record

**Current fields**:
- `totalTimeMs`
- `avgTimePerSampleMs`
- `totalTokens`
- `avgTokensPerSample`

**Planned additions** (Phase A):
- `totalInputTokens`
- `totalOutputTokens`
- `avgInputTokensPerSample`
- `avgOutputTokensPerSample`
- `apiCallCount`

### @ProbabilisticTest Annotation

**Current attributes**:
- `samples`
- `minPassRate`
- `timeBudgetMs`
- `tokenCharge`
- `tokenBudget`
- `onBudgetExhausted`
- `onException`
- `maxExampleFailures`
- `spec`
- `useCase`

**Planned additions** (Phase C):
- `thresholdConfidence`
- `derivationPolicy`
- `confidence`
- `minDetectableEffect`
- `power`

---

## Summary

| Track        | Complete | Planned | Total |
|--------------|----------|---------|-------|
| Core (C)     | 8        | 0       | 8     |
| Experiment (E) | 10     | 0       | 10    |
| Enhancement  | 0        | 3       | 3     |
| **Total**    | **18**   | **3**   | **21** |

---

*See [PLAN-99-DEVELOPMENT-PLAN.md](./PLAN-99-DEVELOPMENT-PLAN.md) for full phase descriptions.*

*[Back to Table of Contents](./DOC-00-TOC.md)*

