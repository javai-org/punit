# REQ-COV: Stationarity-Assuring Features and Guardrails

This document describes a coherent set of features whose purpose is to protect the statistical validity of probabilistic inference in the presence of non-stationarity, while remaining operationally simple, deterministic, and transparent to developers and operators.

The story unfolds from baseline creation, through baseline selection, to test execution and reporting, with explicit guardrails at each stage.

---

## 1. Declaring relevant covariates at use case definition time

A use case may declare a set of exogenous contextual covariates that are known or suspected to influence the data-generating process but are not part of the functional invocation of the use case.

These covariates represent the use case author's explicit statement of:

> "These are factors which may influence the performance of the use case."

Covariates are declared directly in the use case annotation and fall into two categories:

- **Standard covariates**  
  A predefined set supplied by PUnit (e.g., `WEEKDAY_VERSUS_WEEKEND`, `TIME_OF_DAY`, `TIMEZONE`), exposed via a type-safe API but ultimately backed by stable string keys.

- **Custom covariates**  
  User-defined keys that refer to values supplied via a general environment map (feature flags, deployment tier, tenant class, etc.).

The declaration is authoritative:

- Only declared covariates are captured during baseline creation.
- The **names** of declared covariates contribute to the invocation footprint (see Section 3).
- Only declared covariates participate in later matching and reporting.

This avoids accidental noise and ensures that "context relevance" is intentional rather than implicit.

---

## 2. Covariate resolution strategies

Each covariate type requires a unique strategy for obtaining its value from the environment. PUnit provides standard resolution strategies for built-in covariates and allows custom strategies for user-defined covariates.

### Standard covariate resolution

| Covariate                | Resolution Strategy                                                  | Example Values                      |
|--------------------------|----------------------------------------------------------------------|-------------------------------------|
| `WEEKDAY_VERSUS_WEEKEND` | Derived from current date                                            | `Mo-Fr`, `Sa-So`                    |
| `TIME_OF_DAY`            | Captured from experiment execution interval (start time to end time) | `09:03-09:25 Europe/London`         |
| `TIMEZONE`               | Derived from system timezone                                         | `Europe/London`, `America/New_York` |

Note that `TIME_OF_DAY` does not require an explicit value at declaration time—the value is implied by the experiment's actual execution interval.

### Custom covariate resolution

Custom covariates obtain their values from the environment at runtime:

| Source                | Example                          |
|-----------------------|----------------------------------|
| System property       | `-Dregion=EU`                    |
| Environment variable  | `REGION=EU`                      |
| PUnit environment map | Programmatically supplied values |

The resolved value is baked into the baseline specification as a factual record of the conditions under which samples were collected.

If a custom covariate is not present in the environment, its value must be presented as a standard value such as 'not set'.
When it comes to matching a test with a baseline, 'not set' values have 'null' semantics i.e. they are treated as not matching, even if both the baseline's declared 'region' and the test engine at the time of the test run have the value 'not set'. 

---

## 3. The invocation footprint

The **invocation footprint** is a stable identifier that determines which baselines are candidates for a given probabilistic test. It is derived from three components:

1. **Use case identity** — The fully qualified name or identifier of the use case.
2. **Functional parameters** — The values of any parameters used to invoke the use case (factors/levels).
3. **Covariate names** — The set of declared covariate keys (not their values).

The footprint acts as a **hard compatibility gate**: only baselines whose footprint exactly matches the test's footprint are ever considered candidates.

### Why covariate names are part of the footprint

If a developer changes a use case's covariate declaration (adding, removing, or renaming covariates), this must create a new group of experiments. The probabilistic test—linked to the use case and hence its covariates—depends on the baseline files and use case being in alignment.

Including covariate names in the footprint ensures that:

- Misalignment between test and baseline is detectable.
- Old baselines with different covariate structures are automatically excluded.
- Developers are prompted to regenerate baselines when covariate declarations change.

### Covariate values are stored separately

Within baselines that share the same footprint, different covariate **values** distinguish baselines from one another. The baseline specification contains a map of covariate name to covariate value:

```yaml
covariates:
  WEEKDAY_VERSUS_WEEKEND: "Mo-Fr"
  TIME_OF_DAY: "09:03-09:25 Europe/London"
  REGION: "EU"
```

This structure supports both readability and programmatic matching.

---

## 4. Baseline file naming

Baseline files are named to support both human recognition and deterministic lookup. The filename structure is:

```
{UseCaseName}-{footprintHash}-{covariateHash}.yaml
```

Where:

- **UseCaseName** — Human-readable identifier for the use case.
- **footprintHash** — Short hash derived from the invocation footprint.
- **covariateHash** — Short hash derived from the covariate values.

For use cases with multiple covariates, the covariate hash may be a composite of individual hashes, ordered according to declaration order:

```
ShoppingUseCase-ax43-dsf2-6571-ihj2.yaml
```

The four hashes after the footprint correspond to four declared covariates and their values. The order reflects declaration order, suggesting that earlier covariates are likely more influential—a loose convention that becomes relevant during matching.

---

## 5. Deterministic baseline selection at probabilistic test time

When a probabilistic test executes, PUnit performs a two-phase selection:

### Phase 1: Footprint matching

PUnit identifies all baselines whose invocation footprint exactly matches the test's footprint. This is a hard gate—baselines with different footprints are excluded entirely.

### Phase 2: Covariate value matching

From the candidate baselines, PUnit resolves the current covariate values using the same strategies as baseline creation, then evaluates how well each candidate's stored values match the current context.

**Matching strategies** are covariate-specific:

| Covariate | Matching Rule |
|-----------|---------------|
| `WEEKDAY_VERSUS_WEEKEND` | Current day of week falls within range (Monday → matches `Mo-Fr`; Sunday → matches `Sa-So`) |
| `TIME_OF_DAY` | Current time falls within baseline's recorded interval |
| `REGION` | Exact string match |

### Selection algorithm

The algorithm is deterministic and prioritizes covariates in declaration order (left to right):

1. Prefer baselines with the most matching covariates.
2. When comparing covariates, prioritize matches on earlier-declared covariates.
3. Break remaining ties using stable rules (e.g., recency, sample size).

Partial matches are permitted, but anything other than a perfect match results in the statistical verdict being qualified accordingly (see Section 7).

### No matching baseline

If no baseline matches the footprint at all, the test cannot proceed. PUnit should fail with a clear configuration error indicating that no compatible baseline exists for the current use case and covariate structure.

---

## 6. Explicit identification of the baseline used for inference

Every probabilistic test run must unambiguously state which baseline was used as the basis for statistical inference.

This identification includes:

- A human-recognizable baseline name or filename.
- A stable identifier (e.g., hash/fingerprint).
- Relevant summary metadata (timestamp, sample size, covariate values).

The baseline identity is surfaced:

- Prominently in the test verdict.
- In detailed diagnostic output.
- In machine-readable test metadata (e.g., JUnit report properties).

This ensures that no inference result is ever detached from its empirical foundation.

---

## 7. Detecting and reporting covariate non-conformance

After baseline selection, PUnit evaluates covariate conformance between:

- The selected baseline's covariate profile, and
- The test's current execution context.

For each declared covariate, PUnit determines whether:

- The test context **conforms** (e.g., current time lies within the baseline's time window).
- The test context **partially conforms** (where applicable).
- The test context **does not conform**.

If any non-conformance exists, PUnit issues a clear warning in the final report, stating:

- Which covariates do not conform.
- How they differ (baseline value vs. test value).
- That statistical inference may be less reliable under the observed conditions.

Crucially:

- Non-conformance **never** alters pass/fail semantics.
- It qualifies the inference rather than suppressing it.

This preserves statistical honesty without encouraging complacency.

---

## 8. Guarding against ambiguous inference foundations

If multiple baselines are equally suitable under the selection policy (e.g., identical covariate conformance), PUnit:

- Selects one deterministically (using tie-breaking rules).
- Emits a warning that multiple suitable baselines existed.

This highlights potential ambiguity in the empirical foundation and signals to operators that:

- Baseline coverage may be overlapping or insufficiently discriminated.
- Refinement of covariates or baseline strategy may be warranted.

---

## 9. Summary

Taken together, these features ensure that:

- Statistical inference is never implicitly assumed to be stationary.
- The conditions under which baselines were created are explicit, inspectable, and comparable.
- Probabilistic tests are transparent about which empirical reality they are testing against.
- Operators are informed whenever inference relies on partially mismatched conditions.
- Simplicity is preserved: warnings qualify results; they do not silence them.

In short, PUnit treats stationarity not as a hidden assumption, but as a managed, reported, and auditable condition of probabilistic testing.
