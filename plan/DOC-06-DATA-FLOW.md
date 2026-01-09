# Data Flow

This section describes how data flows through the punit experiment extension.

## 5.0 Canonical Flow Overview

```
┌───────────────────────────────────────────┐
│   Use Case                                │  Application code invoking
│   (invokes stochastic behavior)           │  non-deterministic behavior
└───────────────────┬───────────────────────┘
                    ↓
    ┌───────────────────────────────────────────────────────┐
    │  EXPLORE mode (optional)                              │
    │                                                       │
    │  Compare multiple configurations to find optimal one  │
    │  Output: explorations/{UseCaseId}/*.yaml              │
    │                                                       │
    │  Skip this step if configuration is already known     │
    └───────────────────────┬───────────────────────────────┘
                            ↓
    ┌───────────────────────────────────────────────────────┐
    │  MEASURE mode (required)                              │
    │                                                       │
    │  Run use case 1000+ times with fixed configuration    │
    │  to measure empirical pass/fail rate                  │
    │  Output: specs/{UseCaseId}.yaml                       │
    └───────────────────────┬───────────────────────────────┘
                            ↓
┌───────────────────────────────────────────┐
│   Execution Specification                 │  Commit to Git
│   (observed success rate, statistics)     │  (approval = commit)
└───────────────────┬───────────────────────┘
                    ↓
┌───────────────────────────────────────────┐
│   Probabilistic Conformance Tests         │  CI-gated validation
│   (threshold derived from spec at runtime)│
│   → PASS / FAIL (with confidence)         │
└───────────────────────────────────────────┘
```

## 5.1 Experiment Flow

Experiments operate in one of two modes:

| Mode | Command | Purpose | Output |
|------|---------|---------|--------|
| `MEASURE` | `./gradlew measure` | Establish reliable statistics | `specs/{UseCaseId}.yaml` |
| `EXPLORE` | `./gradlew explore` | Compare configurations | `explorations/{UseCaseId}/*.yaml` |

### 5.1.1 EXPLORE Mode (Optional)

Use this mode when the optimal configuration is unknown—for example, when choosing between LLM models, temperatures, or prompt variants.

1. **Resolve use case** by ID
2. **Parse FactorSource** (configurations to compare)
3. **For each configuration**:
   - Build context from factor values
   - Execute use case 1-10 times (fast pass)
   - Aggregate results and compute statistics
   - Generate per-config spec in `explorations/`
4. **Compare results** (via diff tools, IDE, or future PUnit tooling)
5. **Select optimal configuration** for MEASURE mode

**Output location**: `src/test/resources/punit/explorations/{UseCaseId}/`

### 5.1.2 MEASURE Mode (Required)

Use this mode with a fixed configuration to measure the true success rate. **This step generates the spec used by probabilistic tests.**

1. **Resolve use case** by ID
2. **Apply known configuration** (from exploration or predetermined)
3. **Execute use case 1000+ times** (default: 1000 samples)
4. **Aggregate results** and compute statistics:
   - Observed success rate
   - Standard error
   - Confidence interval
   - Failure distribution
   - Timing and token metrics
5. **Generate spec file** directly in `specs/`
6. **Publish via TestReporter**

**Output location**: `src/test/resources/punit/specs/{UseCaseId}.yaml`

### When to Skip EXPLORE

Skip EXPLORE (5.1.1) and proceed directly to MEASURE (5.1.2) when:

- The system is **given** (e.g., a third-party API with fixed behavior)
- The configuration is **mandated** (e.g., organizational policy dictates model choice)
- You're **re-measuring** an existing configuration after changes

## 5.2 Spec Approval Flow (Git-Based)

The approval workflow is now Git-based:

1. **Run MEASURE experiment** → generates spec in `src/test/resources/punit/specs/`
2. **Review the spec** via `git diff`, IDE, or PR
3. **Commit to version control** → this is the approval
4. (Optional) **Use Pull Request** for team review before merge

No separate `punitApprove` command is needed—approval is your commit.

```bash
# 1. Run experiment
./gradlew measure --tests "MyExperiment.measureSomething"

# 2. Review
git diff src/test/resources/punit/specs/

# 3. Approve (commit)
git add src/test/resources/punit/specs/
git commit -m "Add spec for MyUseCase (1000 samples, 94.5% success)"
```

## 5.3 Probabilistic Test Flow: Spec → Verdict

1. `@ProbabilisticTest(useCase = MyUseCase.class)`
2. `SpecificationRegistry.resolve("MyUseCase")`
3. Load `ExecutionSpecification` from `specs/MyUseCase.yaml`
4. **Derive threshold at runtime**:
   - Read `empiricalBasis.samples` and `empiricalBasis.successes`
   - Compute Wilson lower bound at configured confidence level
   - This becomes the effective `minPassRate`
5. **For each sample (1..N)**:
   - Invoke use case
   - Get result
   - Evaluate success/fail
6. `FinalVerdictDecider.isPassing()` → **PASS** or **FAIL**

## 5.4 Spec File Structure

```
src/test/resources/punit/
├── specs/                           # MEASURE output (powers tests)
│   └── MyUseCase.yaml               # Single file per use case
│
└── explorations/                    # EXPLORE output (for analysis)
    └── MyUseCase/                   # Directory per use case
        ├── model-gpt-4_temp-0.0.yaml
        ├── model-gpt-4_temp-0.7.yaml
        └── model-gpt-3.5_temp-0.0.yaml
```

## 5.5 Spec File Format (punit-spec-2)

```yaml
schemaVersion: punit-spec-2
specId: MyUseCase
useCaseId: MyUseCase
generatedAt: 2026-01-09T15:30:00Z

# Core empirical data (required for threshold derivation)
empiricalBasis:
  samples: 1000
  successes: 945
  generatedAt: 2026-01-09T15:30:00Z

# Extended statistics (optional, for analysis)
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

# Configuration context (optional)
configuration:
  model: gpt-4
  temperature: 0.2

# Cost envelope (optional)
costEnvelope:
  maxTimePerSampleMs: 100
  maxTokensPerSample: 500
  totalTokenBudget: 50000

# Integrity check
contentFingerprint: sha256:abc123...
```

**Key points:**
- **Threshold not stored**: Derived at test runtime from empiricalBasis
- **No approval metadata**: Approval = Git commit
- **Extended statistics optional**: For analysis, not threshold computation

---

*Previous: [Annotation & API Design](./DOC-05-ANNOTATION-API-DESIGN.md)*

*Next: [Governance & Safety Mechanisms](./DOC-07-GOVERNANCE-SAFETY.md)*

*[Back to Table of Contents](./DOC-00-TOC.md)*
