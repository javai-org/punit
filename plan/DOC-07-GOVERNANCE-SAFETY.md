# Governance & Safety Mechanisms

The framework actively guides developers toward empirical threshold selection and prevents misuse.

## 6.1 Discouraging Arbitrary Thresholds

### 6.1.1 Warning on Inline Thresholds Without Spec

```
⚠️ WARNING: @ProbabilisticTest for 'validateEmail' uses minPassRate=0.95 
   without an empirical spec. This threshold may be arbitrary.
   
   Recommendation: Run a MEASURE experiment first to establish empirical behavior,
   then reference the spec via useCase = MyUseCase.class.
```

This is a **warning**, not an error. The test still executes.

### 6.1.2 Spec-First Guidance in Documentation

Documentation and examples consistently demonstrate the spec-driven pattern as primary.

## 6.2 Surfacing Insufficient Empirical Data

```
⚠️ WARNING: Specification 'MyUseCase' is based on only 50 samples.
   Confidence interval for success rate is wide: [0.82, 0.98].
   
   Recommendation: Run a MEASURE experiment with 1000+ samples.
```

## 6.3 Git-Based Approval

Approval is now Git-based. The spec is "approved" when committed:

```bash
# 1. Generate spec
./gradlew measure --tests "MyExperiment"

# 2. Review
git diff src/test/resources/punit/specs/

# 3. Approve (commit)
git add src/test/resources/punit/specs/
git commit -m "Add spec for MyUseCase"
```

**For teams requiring review**:
- Use Pull Requests for spec changes
- PR approval = spec approval
- Merge = spec becomes active

## 6.4 Spec-Reality Drift Detection

```
⚠️ WARNING: Observed success rate (0.78) is significantly lower than
   spec's empirical basis (0.935) for 'MyUseCase'.
   
   This may indicate:
   - System regression
   - Environment differences
   - Spec that no longer reflects current behavior
```

## 6.5 Warnings vs Errors Summary

| Condition                                  | Severity | Behavior                             |
|--------------------------------------------|----------|--------------------------------------|
| Inline threshold without spec              | Warning  | Test executes with warning           |
| Spec with insufficient samples (<100)      | Warning  | Spec loads with warning              |
| Spec missing empiricalBasis                | Error    | Test fails                           |
| Observed rate significantly below spec     | Warning  | Test may still pass if threshold met |
| Use case ID not found                      | Error    | Test fails                           |
| Spec file not found (when expected)        | Error    | Test fails                           |

## 6.6 Experiment Workflow

### 6.6.1 Gradle Tasks

| Task | Command | Description |
|------|---------|-------------|
| **MEASURE** | `./gradlew measure --tests "..."` | Generate specs in `specs/` |
| **EXPLORE** | `./gradlew explore --tests "..."` | Generate specs in `explorations/` |

### 6.6.2 MEASURE Mode Output

Specs are written directly to version-controlled location:

```
src/test/resources/punit/specs/MyUseCase.yaml
```

Example output:

```yaml
schemaVersion: punit-spec-2
specId: MyUseCase
useCaseId: MyUseCase
generatedAt: 2026-01-09T15:30:00Z

empiricalBasis:
  samples: 1000
  successes: 945
  generatedAt: 2026-01-09T15:30:00Z

extendedStatistics:
  standardError: 0.0072
  confidenceInterval:
    lower: 0.930
    upper: 0.958
  failureDistribution:
    invalid_json: 35
    missing_fields: 20
  totalTimeMs: 450000
  avgTimePerSampleMs: 450

contentFingerprint: sha256:abc123...
```

### 6.6.3 EXPLORE Mode Output

Exploration specs are written to a separate location:

```
src/test/resources/punit/explorations/MyUseCase/
├── model-gpt-4_temp-0.0.yaml
├── model-gpt-4_temp-0.7.yaml
└── model-gpt-3.5_temp-0.0.yaml
```

These are for analysis and comparison, not for powering probabilistic tests.

### 6.6.4 Workflow Summary

```
┌─────────────────────────────────────────────────────────────────┐
│  1. EXPLORE (optional)                                          │
│     ./gradlew explore --tests "MyExperiment.exploreConfigs"     │
│     → Compare configs, choose optimal one                       │
└─────────────────────────────────┬───────────────────────────────┘
                                  ↓
┌─────────────────────────────────────────────────────────────────┐
│  2. MEASURE (required)                                          │
│     ./gradlew measure --tests "MyExperiment.measureSomething"   │
│     → Generate spec in specs/                                   │
└─────────────────────────────────┬───────────────────────────────┘
                                  ↓
┌─────────────────────────────────────────────────────────────────┐
│  3. REVIEW & COMMIT                                             │
│     git diff src/test/resources/punit/specs/                    │
│     git add . && git commit -m "Add spec for MyUseCase"         │
│     → Approval = commit                                         │
└─────────────────────────────────┬───────────────────────────────┘
                                  ↓
┌─────────────────────────────────────────────────────────────────┐
│  4. PROBABILISTIC TEST                                          │
│     @ProbabilisticTest(useCase = MyUseCase.class)               │
│     → Threshold derived from spec at runtime                    │
└─────────────────────────────────────────────────────────────────┘
```

## 6.7 Runtime Threshold Derivation

Specifications store **raw empirical data**, not computed thresholds. Thresholds are computed at test runtime.

**Rationale**: This separation of concerns means:
- **Spec = empirical truth** (what we observed)
- **Test = operational preferences** (samples, confidence level)
- Changing test parameters doesn't require regenerating specs

**At test runtime**, the framework:
1. Reads spec → `empiricalBasis.samples: 1000`, `empiricalBasis.successes: 945`
2. Reads annotation → `samples: 100`, `thresholdConfidence: 0.95` (defaults)
3. Computes → `minPassRate ≈ 0.930` (Wilson lower bound at 95% confidence)

---

*Previous: [Data Flow](./DOC-06-DATA-FLOW.md)*

*Next: [Execution & Reporting Semantics](./DOC-08-EXECUTION-REPORTING.md)*

*[Back to Table of Contents](./DOC-00-TOC.md)*
