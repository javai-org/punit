# Data Flow

This section describes how data flows through the punit experiment extension.

## 5.0 Canonical Flow Overview

```
┌───────────────┐
│   Use Case    │  Test/experiment-only function that calls production
└───────┬───────┘
        ↓
┌───────────────────────────────────────┐
│   ExperimentDesign (Factors + Levels) │  Declarative description
└───────┬───────────────────────────────┘
        ↓
┌───────────────────────────────────────┐
│   ExperimentConfig (1..N)             │  Concrete combinations to execute
└───────┬───────────────────────────────┘
        ↓
┌───────────────────────────────────────┐
│   Empirical Baselines                 │  Machine-generated records
└───────┬───────────────────────────────┘
        │ (human reviews and approves)
        ↓
┌───────────────────────────────────────┐
│   Execution Specification             │  Human-approved contract
└───────┬───────────────────────────────┘
        ↓
┌───────────────────────────────────────┐
│   Probabilistic Conformance Tests     │  CI-gated validation
│   → PASS / FAIL                       │
└───────────────────────────────────────┘
```

## 5.1 Experiment Flow: ExperimentDesign → Empirical Baselines

1. **Resolve use case** by ID
2. **Parse ExperimentDesign** (factors/levels)
3. **For each ExperimentConfig**:
   - Build context from config (factor→level)
   - Execute use case N times
   - Aggregate results and compute statistics
   - Generate per-config baseline
   - Check goal for early termination
4. **Generate SUMMARY** (aggregated report)
5. **Publish via TestReporter**

## 5.2 Specification Creation Flow (Manual)

1. **Empirical Baseline** (generated file)
2. Developer reviews results
3. **Decision Point**: Is the success rate acceptable? What threshold?
4. **Create Spec File**: Set minPassRate, budgets, version, approval
5. **Commit specification** to VCS

## 5.3 Probabilistic Test Flow: Spec → Verdict

1. `@ProbabilisticTest(spec = "usecase.x:v3")`
2. `SpecificationRegistry.resolve("usecase.x:v3")`
3. Load `ExecutionSpecification` from file
4. Resolve `@UseCase` by useCaseId
5. Apply execution context from spec
6. Build `SuccessCriteria` from spec expression
7. **For each sample (1..N)**:
   - Invoke use case
   - Get `UseCaseResult`
   - Evaluate criteria → success/fail recorded
8. `FinalVerdictDecider.isPassing()` → **PASS** or **FAIL**

## 5.4 Specification → Production Configuration Flow

Specifications can inform production configuration **without** involving use cases:

1. **Execution Specification** (file)
2. Build/deploy process reads and extracts configuration
3. **Production Configuration** (no use case involvement)

This flow is **out of scope** for punit itself but enabled by the specification format.

---

*Previous: [Annotation & API Design](./DOC-05-ANNOTATION-API-DESIGN.md)*

*Next: [Governance & Safety Mechanisms](./DOC-07-GOVERNANCE-SAFETY.md)*

*[Back to Table of Contents](./DOC-00-TOC.md)*
