# PUnit Development Plan

This document is the source of truth for PUnit's phased development. It consolidates the core probabilistic testing framework and the experiment extension into a single comprehensive plan.

---

## Overview

PUnit development is organized into three major tracks:

| Track | Description | Phases |
|-------|-------------|--------|
| **Core Framework** | Foundational `@ProbabilisticTest` infrastructure | C1–C8 |
| **Experiment Extension** | `@Experiment`, baselines, specifications | E1–E8 |
| **Enhancements** | Statistical improvements, prompt refinement | A, B, C |

---

## Track 1: Core Probabilistic Testing Framework (C1–C8)

These phases establish the foundational `@ProbabilisticTest` annotation and supporting infrastructure.

### Phase C1: Core Framework (MVP)

**Status**: ✅ Complete

**Deliverables**:
1. `@ProbabilisticTest` annotation with `samples` and `minPassRate`
2. `ProbabilisticTestExtension` implementing `TestTemplateInvocationContextProvider`
3. `SampleResultAggregator` with basic pass/fail counting
4. `InvocationInterceptor` to catch and record assertion failures
5. `FinalVerdictDecider` with simple pass rate comparison
6. Basic failure message with statistics
7. `TestReporter` integration for structured output

**Estimated Effort**: 2-3 days

---

### Phase C2: Early Termination

**Status**: ✅ Complete

**Deliverables**:
1. `EarlyTerminationEvaluator` with impossibility check
2. Stream short-circuiting in invocation provider
3. `TerminationReason` enum and reporting integration
4. Update failure message to include termination cause

**Estimated Effort**: 1-2 days

---

### Phase C3: Configuration System

**Status**: ✅ Complete

**Deliverables**:
1. `ConfigurationResolver` with precedence logic
2. System property support (`punit.samples`, `punit.minPassRate`)
3. Environment variable support
4. `punit.samplesMultiplier` for scaling
5. Validation and error messages for invalid configurations

**Estimated Effort**: 1 day

---

### Phase C4: Method-Level Cost Budget & Time Limits

**Status**: ✅ Complete

**Deliverables**:
1. `CostBudgetMonitor` implementation (wall-clock time + token accumulation)
2. `timeBudgetMs` annotation parameter
3. `tokenCharge` and `tokenBudget` annotation parameters
4. `TokenChargeRecorder` interface and `DefaultTokenChargeRecorder` implementation
5. JUnit 5 `ParameterResolver` for injecting `TokenChargeRecorder` into test methods
6. `onBudgetExhausted` behavior options
7. Static mode: Pre-sample budget checks integrated into sample loop
8. Dynamic mode: Post-sample token accumulation with recorder
9. Mode detection logic (check for TokenChargeRecorder parameter)
10. Reporting integration for budget-related termination

**Estimated Effort**: 3-4 days

---

### Phase C5: Budget Scopes (Class and Suite Levels)

**Status**: ✅ Complete

**Deliverables**:
1. `@ProbabilisticTestBudget` class-level annotation
2. `ProbabilisticTestBudgetExtension` for class-level budget management
3. `SharedBudgetMonitor` thread-safe implementation for shared budgets
4. `SuiteBudgetManager` singleton for suite-level state
5. Suite-level system property parsing (`punit.suite.*`)
6. Budget scope precedence logic (suite → class → method)
7. Consumption propagation to all active scopes
8. Extended `TerminationReason` enum with scoped values
9. Reporting integration for scoped budgets
10. Thread-safe budget tracking for parallel test execution

**Estimated Effort**: 3-4 days

---

### Phase C6: Enhanced Reporting & Diagnostics

**Status**: ✅ Complete

**Deliverables**:
1. `maxExampleFailures` parameter
2. Example failure collection in aggregator
3. Suppressed exceptions on final `AssertionError`
4. Enhanced failure message with example failures
5. Per-sample display names (e.g., "Sample 3/100")

**Estimated Effort**: 1 day

---

### Phase C7: Exception Handling & Edge Cases

**Status**: ✅ Complete

**Deliverables**:
1. `onException` parameter and handling logic
2. Parameter validation at discovery time
3. Edge case handling (samples=1, minPassRate=1.0, etc.)
4. Documentation of rounding behavior

**Estimated Effort**: 1 day

---

### Phase C8: Documentation & Polish

**Status**: ✅ Complete

**Deliverables**:
1. Javadoc for all public API
2. README with quick start guide
3. Examples in test sources
4. Migration guide (for teams adopting PUnit)

**Estimated Effort**: 1-2 days

---

**Track 1 Total**: 13-21 days (Complete)

---

## Track 2: Experiment Extension (E1–E8)

These phases extend PUnit with experiment support, baselines, and specifications.

### Phase E1: Core Use Case and Result Abstractions

**Status**: ✅ Complete

**Goals**: Establish foundational abstractions for use cases and results

**Deliverables**:
- `@UseCase` annotation
- `UseCaseResult` class with builder pattern
- `UseCaseContext` interface
- `UseCaseRegistry` for discovery and caching

**Dependencies**: None (greenfield)

**Estimated Effort**: 2-3 days

---

### Phase E2: Single-Config Experiment Mode

**Status**: ✅ Complete

**Goals**: Enable exploratory execution via `@Experiment`

**Deliverables**:
- `@Experiment` and `@ExperimentContext` annotations
- `ExperimentExtension` (JUnit 5 extension)
- `ExperimentResultAggregator`
- `EmpiricalBaselineGenerator`
- YAML serialization/deserialization

**Dependencies**: Phase E1

**Estimated Effort**: 4-5 days

---

### Phase E2b: Multi-Config Experiments

**Status**: ✅ Complete

**Goals**: Enable experiments with explicit `ExperimentConfig` lists

**Deliverables**:
- `@ExperimentDesign`, `@Config`, `@ExperimentGoal` annotations
- Sequential config execution with shared budget
- Goal-based early termination
- Aggregated `SUMMARY.yaml` report generation

**Dependencies**: Phase E2

**Estimated Effort**: 4-5 days

---

### Phase E2c: Adaptive Experiments

**Status**: ✅ Complete

**Goals**: Enable dynamic level generation for adaptive factors

**Deliverables**:
- `AdaptiveFactor<T>` interface
- `AdaptiveLevels` builder with `Supplier` support
- `RefinementStrategy<T>` SPI interface
- `IterationFeedback` and `FailureObservation` models
- Adaptive baseline generation with iteration history

**Dependencies**: Phase E2b

**Estimated Effort**: 5-6 days

---

### Phase E3: Specification Representation and Registry

**Status**: ✅ Complete

**Goals**: Define specification data model and resolution

**Deliverables**:
- `ExecutionSpecification` model class
- `SpecificationRegistry` for loading and resolving specs
- YAML format for specifications
- `SuccessCriteria` expression parsing

**Dependencies**: Phase E1

**Estimated Effort**: 3-4 days

---

### Phase E4: Spec-Driven Probabilistic Tests

**Status**: ✅ Complete

**Goals**: Extend `@ProbabilisticTest` to consume specifications

**Deliverables**:
- Add `spec` and `useCase` attributes
- `SuccessCriteria` evaluation integration
- Baseline-spec drift detection
- Provenance reporting

**Dependencies**: Phases E1, E2, E3

**Estimated Effort**: 4-5 days

---

### Phase E5: Pluggable Backend Infrastructure

**Status**: ✅ Complete

**Goals**: Establish SPI for experiment backends

**Deliverables**:
- `ExperimentBackend` SPI interface
- `ExperimentBackendRegistry` with ServiceLoader discovery
- Generic backend implementation

**Dependencies**: Phase E2

**Estimated Effort**: 2-3 days

---

### Phase E6: LLM Backend Extension (llmx)

**Status**: ✅ Complete

**Goals**: Implement LLM-specific backend as reference implementation

**Deliverables**:
- `org.javai.punit.llmx` package
- `LlmExperimentBackend`, `LlmUseCaseContext`
- Common model/temperature presets
- `LlmPromptRefinementStrategy` for adaptive experiments

**Dependencies**: Phases E5, E2c

**Estimated Effort**: 4-5 days

---

### Phase E7: Canonical Flow Examples

**Status**: ✅ Complete

**Goals**: Demonstrate complete canonical flow through working examples

**Deliverables**:
- End-to-end examples in test suite
- Single-config, multi-config, and adaptive experiment examples
- Mock LLM client for testing

**Dependencies**: Phases E4, E6, E2c

**Estimated Effort**: 3-4 days

---

### Phase E8: Documentation, Migration, and Guardrails

**Status**: ✅ Complete

**Goals**: Complete documentation and governance mechanisms

**Deliverables**:
- Javadoc for all public APIs
- User guide and migration guide
- Complete governance warnings/errors
- Edge case testing

**Dependencies**: Phases E1-E7

**Estimated Effort**: 3-4 days

---

**Track 2 Total**: 35-44 days (Complete)

---

## Track 3: Enhancements (Phases A, B, C)

These phases introduce advanced capabilities for cost tracking, prompt refinement, and statistical threshold derivation.

### Phase A: Core Statistical and Cost Enhancements

**Status**: 📋 Planned

**Priority**: P1

**Goals**: Improve cost tracking and statistical guidance

**Deliverables**:

#### A.1 Input/Output Token Split

Enhanced `CostSummary` record:

```java
public record CostSummary(
    long totalTimeMs,
    long avgTimePerSampleMs,
    long totalTokens,
    long avgTokensPerSample,
    long totalInputTokens,          // NEW
    long totalOutputTokens,         // NEW
    long avgInputTokensPerSample,   // NEW
    long avgOutputTokensPerSample,  // NEW
    long apiCallCount               // NEW
) {}
```

#### A.2 Token Estimation

```java
public interface TokenEstimator {
    long estimateInputTokens(String text);
    long estimateOutputTokens(String text);
    
    static TokenEstimator forModel(String modelName) { ... }
}
```

With `BasicCostEstimator` fallback using word-count heuristics (~1.3 tokens per word).

#### A.3 Sample Size Advisory

Baseline output includes guidance on samples needed for various precision levels.

#### A.4 Stability-Based Early Termination

```java
@Experiment(
    useCase = "usecase.json.generation",
    samples = 1000,
    stabilityThreshold = 0.02  // Stop when CI width < 2%
)
```

**Dependencies**: Phase E2

**Estimated Effort**: 3-4 days

---

### Phase B: Adaptive Prompt Refinement

**Status**: 📋 Planned

**Priority**: P2

**Goals**: Enable automated prompt improvement through LLM-assisted refinement

**Deliverables**:

#### B.1 PromptContributor Interface

```java
public interface PromptContributor {
    String getSystemMessage();
    default List<Example> getExamples() { return List.of(); }
    default Optional<String> getUserMessageTemplate() { return Optional.empty(); }
    
    record Example(String userMessage, String assistantResponse) {}
}
```

#### B.2 Failure Categorization SPI

```java
@FunctionalInterface
public interface FailureCategorizer {
    String categorize(UseCaseResult result);
}
```

#### B.3 @AdaptivePromptExperiment Annotation

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdaptivePromptExperiment {
    Class<? extends PromptContributor> promptContributor();
    int samplesPerIteration() default 50;
    int maxIterations() default 10;
    double targetSuccessRate() default Double.NaN;
    Class<? extends FailureCategorizer> failureCategorizer();
    String refinementModel() default "";
    String refinementPromptTemplate() default "";
    String[] refinableComponents() default {};
}
```

#### B.4 Refinement Loop Orchestration

1. Call PromptContributor for initial components
2. Run N samples, collect results
3. Apply failure categorization
4. Check goal; if not met, refine via LLM
5. Loop until goal met or limit reached

**Dependencies**: Phases E2c, E6, A

**Estimated Effort**: 5-6 days

---

### Phase C: Pass Rate Threshold Derivation

**Status**: 📋 Planned

**Priority**: P1

**Goals**: Enable statistically-derived pass rate thresholds for probabilistic tests

**Deliverables**:

#### C.1 Problem Statement

An experiment runs 1000 samples and observes 95.1% success rate. If the regression test runs only 100 samples with a 95.1% threshold, normal sampling variance will cause false failures.

**Solution**: Derive a one-sided lower confidence bound that accounts for increased variance in smaller test samples.

#### C.2 punit-statistics Module

Per Design Principle 1.6, an isolated module with:
- No dependencies on punit-core or punit-experiment
- Only Java standard library
- Comprehensive unit tests with worked examples
- Code readable by professional statisticians

#### C.3 Wilson Score Lower Bound

```
p_lower = (p̂ + z²/2n - z√(p̂(1-p̂)/n + z²/4n²)) / (1 + z²/n)
```

Preferred for small samples or extreme success rates.

#### C.4 RegressionThreshold and Calculator

```java
public record RegressionThreshold(
    ExperimentalBasis basis,
    TestConfiguration testConfig,
    double minPassRate,
    DerivationMetadata derivation
) {}

public class RegressionThresholdCalculator {
    public RegressionThreshold calculate(
            int experimentSamples,
            int experimentSuccesses,
            int testSamples,
            double confidenceLevel) { ... }
}
```

#### C.5 Three Operational Approaches

| Approach | Given | Framework Computes |
|----------|-------|-------------------|
| **Sample-Size-First** (Cost-Driven) | Samples + Confidence | Threshold |
| **Confidence-First** (Quality-Driven) | Confidence + Effect + Power | Sample Size |
| **Threshold-First** (Baseline-Anchored) | Samples + Explicit Threshold | Implied Confidence |

#### C.6 Enhanced @ProbabilisticTest

```java
@ProbabilisticTest(
    spec = "my-spec:v1",
    thresholdConfidence = 0.95,
    derivationPolicy = ThresholdDerivationPolicy.DERIVE
)
```

#### C.7 Qualified Failure Reporting

Reports include:
- Observed pass rate vs. threshold
- Confidence level and statistical method used
- Probability of false positive

**Dependencies**: Phases E2, E3, E4

**Estimated Effort**: 6-8 days

---

**Track 3 Total**: 14-18 days (Planned)

---

## Phase Summary

| Track | Phase | Description | Status | Est. Days |
|-------|-------|-------------|--------|-----------|
| Core | C1 | Core Framework (MVP) | ✅ | 2-3 |
| Core | C2 | Early Termination | ✅ | 1-2 |
| Core | C3 | Configuration System | ✅ | 1 |
| Core | C4 | Method-Level Cost Budget | ✅ | 3-4 |
| Core | C5 | Budget Scopes | ✅ | 3-4 |
| Core | C6 | Enhanced Reporting | ✅ | 1 |
| Core | C7 | Exception Handling | ✅ | 1 |
| Core | C8 | Documentation | ✅ | 1-2 |
| Experiment | E1 | Use Case Abstractions | ✅ | 2-3 |
| Experiment | E2 | Single-Config Experiments | ✅ | 4-5 |
| Experiment | E2b | Multi-Config Experiments | ✅ | 4-5 |
| Experiment | E2c | Adaptive Experiments | ✅ | 5-6 |
| Experiment | E3 | Specification Registry | ✅ | 3-4 |
| Experiment | E4 | Spec-Driven Tests | ✅ | 4-5 |
| Experiment | E5 | Backend Infrastructure | ✅ | 2-3 |
| Experiment | E6 | LLM Backend (llmx) | ✅ | 4-5 |
| Experiment | E7 | Canonical Flow Examples | ✅ | 3-4 |
| Experiment | E8 | Documentation | ✅ | 3-4 |
| Enhancement | A | Cost/Statistical Enhancements | 📋 | 3-4 |
| Enhancement | B | Adaptive Prompt Refinement | 📋 | 5-6 |
| Enhancement | C | Threshold Derivation | 📋 | 6-8 |

**Total Estimated Effort**: 62-83 days

---

## Recommended Execution Order

1. ✅ Complete Phases C1-C8 (Core Framework)
2. ✅ Complete Phases E1-E8 (Experiment Extension)
3. 📋 Phase C (isolated statistics module + threshold derivation)
4. 📋 Phase A (cost/statistical enhancements)
5. 📋 Phase B (prompt refinement)

---

## Explicit Exclusions

| Feature | Reason |
|---------|--------|
| Dollar cost calculations | Framework provides tokens/time only |
| Automated config optimization | Decisions remain with humans |
| Cross-experiment state management | Each experiment is independent |
| Visualization/dashboards | Results via JUnit only |
| Retry orchestration | Application's responsibility |
| Iteration history in baselines | Available as runtime output only |

---

## v1 Scope vs v2 Enhancements

### v1 Scope (Complete)

✅ `@ProbabilisticTest` annotation  
✅ `samples` and `minPassRate` parameters  
✅ Sequential sample execution  
✅ Early termination by impossibility  
✅ Wall-clock time budget (`timeBudgetMs`)  
✅ Static token budget (`tokenCharge`, `tokenBudget`)  
✅ Dynamic token charging via injectable `TokenChargeRecorder`  
✅ Budget scopes: method, class (`@ProbabilisticTestBudget`), and suite levels  
✅ System property / env var overrides (including suite-level budgets)  
✅ Structured reporting via `TestReporter`  
✅ Clear failure messages with statistics  
✅ Example failure capture  
✅ Basic exception handling policy  
✅ `@Experiment` and experiment infrastructure  
✅ `@UseCase` and use case abstractions  
✅ Empirical baselines and specifications  
✅ Backend SPI and `llmx` extension

### v2 Enhancements (Future)

⏳ **Parallel sample execution** (`parallelSamples` parameter)  
⏳ **Custom cost models** (SPI for money, API calls, dynamic token measurement)  
⏳ **Warm-up samples** (excluded from statistics)  
⏳ **Per-invocation timeout** (distinct from overall budget)  
⏳ **Retry policy** (retry failed samples N times before counting as failure)  
⏳ **Combined with @ParameterizedTest** (run probabilistic test for each parameter set)  
⏳ **Statistical confidence intervals** (report confidence bounds, not just point estimate)  
⏳ **Adaptive sampling** (stop early when statistically confident of pass/fail)  
⏳ **JUnit XML/HTML report integration** (custom reporter for richer output)  
⏳ **Flakiness tracking** (track pass rates over time across runs)  
⏳ **Gradle/Maven plugin** (configure defaults in build file)  
⏳ **Automatic token measurement** (SPI for intercepting LLM client calls)

---

*See [PLAN-99-DEVELOPMENT-STATUS.md](./PLAN-99-DEVELOPMENT-STATUS.md) for current implementation status.*

*[Back to Table of Contents](./DOC-00-TOC.md)*

