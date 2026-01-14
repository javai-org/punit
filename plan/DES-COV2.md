# DES-COV2: Covariate Categories and Category-Aware Baseline Selection

## Status

| Attribute | Value |
|-----------|-------|
| Status | PROPOSED |
| Created | 2026-01-14 |
| Depends on | DES-COV (Covariate Support) |

---

## 1. Motivation

DES-COV introduced covariates as contextual factors that drive variance in system behavior. Implementation revealed that **not all covariates are equal**:

- Changing an LLM model is fundamentally different from running at a different time of day
- A mismatch in `llm_model` **explains** a test failure; a mismatch in `time_of_day` suggests **caution** about comparison
- The current algorithm treats all covariates identically, leading to misleading warnings

This design introduces **Covariate Categories** to distinguish between types of covariates and implements **category-aware baseline selection** with appropriate matching semantics.

---

## 2. The Problem

### 2.1 Scenario: LLM Model Change

A developer:
1. Runs MEASURE with `llm_model = gpt-4.1-mini`
2. Later changes to `llm_model = gpt-3.5`
3. Runs probabilistic tests

**Current behavior:**
- Test runs against gpt-4.1-mini baseline
- Test fails (lower pass rate)
- Warning: "Covariate 'llm_model' differs — statistical comparison may be less reliable"

**Problem:** This downplays the failure. The model change IS the cause. The warning suggests the comparison might be unreliable when in fact it's precisely reliable — it correctly detected the regression caused by the model change.

### 2.2 Scenario: Time Window Precision

A developer:
1. Runs a fast MEASURE experiment (completes in 1 second)
2. Baseline records `time_of_day = 10:30-10:30`
3. Runs probabilistic tests at 14:45

**Current behavior:**
- Time mismatch detected
- Warning displayed about non-conformance

**Acceptable:** This is genuinely environmental variation where approximate matching is appropriate.

### 2.3 The Insight

Covariates serve fundamentally different purposes:

| Purpose | Nature | Mismatch Meaning |
|---------|--------|------------------|
| Track environmental conditions | Cannot control | "Conditions differ — be cautious" |
| Record deliberate configuration | Developer controls | "You changed this — this explains the result" |

---

## 3. Covariate Categories

### 3.1 Category Definitions

```java
public enum CovariateCategory {
    
    /**
     * Temporal and cyclical factors affecting system behavior.
     * Examples: time_of_day, weekday_vs_weekend
     * Mismatch: Warn — "Environmental condition differs"
     */
    TEMPORAL,
    
    /**
     * Deliberate system configuration choices.
     * Examples: llm_model, prompt_version, temperature
     * Mismatch: Hard fail — require matching baseline
     */
    CONFIGURATION,
    
    /**
     * External services and dependencies outside our control.
     * Examples: third_party_api_version, upstream_service
     * Mismatch: Warn — "External dependency differs"
     */
    EXTERNAL_DEPENDENCY,
    
    /**
     * Execution environment characteristics.
     * Examples: cloud_provider, instance_type, region
     * Mismatch: Warn — "Infrastructure differs"
     */
    INFRASTRUCTURE,
    
    /**
     * Data state affecting behavior.
     * Examples: cache_state, index_version, training_data_version
     * Mismatch: Warn — "Data context differs"
     */
    DATA_STATE,
    
    /**
     * For traceability without interpretation impact.
     * Examples: run_id, operator_tag, experiment_label
     * Mismatch: Ignored — not considered in matching
     */
    INFORMATIONAL
}
```

### 3.2 Standard Covariate Categories

```java
public enum StandardCovariate {
    WEEKDAY_VERSUS_WEEKEND("weekday_vs_weekend", CovariateCategory.TEMPORAL),
    TIME_OF_DAY("time_of_day", CovariateCategory.TEMPORAL),
    TIMEZONE("timezone", CovariateCategory.INFRASTRUCTURE),
    REGION("region", CovariateCategory.INFRASTRUCTURE);
}
```

### 3.3 Custom Covariate Declaration

```java
@UseCase(
    value = "ProductSearch",
    covariates = { StandardCovariate.TIME_OF_DAY },
    customCovariates = {
        @Covariate(key = "llm_model", category = CovariateCategory.CONFIGURATION),
        @Covariate(key = "prompt_version", category = CovariateCategory.CONFIGURATION),
        @Covariate(key = "cache_warm", category = CovariateCategory.DATA_STATE)
    }
)
public class ProductSearchUseCase { ... }
```

---

## 4. Category-Aware Baseline Selection Algorithm

### 4.1 Two-Phase Filtering

```
Phase 1: Hard Gates
├── Filter by footprint (exact match required)
└── Filter by CONFIGURATION covariates (exact match required)
    └── If no candidates remain → fail with actionable error

Phase 2: Soft Matching (remaining categories)
├── Score candidates by covariate conformance
│   ├── TEMPORAL covariates
│   ├── EXTERNAL_DEPENDENCY covariates
│   ├── INFRASTRUCTURE covariates
│   └── DATA_STATE covariates
│   (INFORMATIONAL covariates ignored)
├── Rank by: match count → category priority → declaration order → recency
└── Select best candidate
```

### 4.2 Matching Behavior by Category

| Category | Matching | On Mismatch | Rationale |
|----------|----------|-------------|-----------|
| CONFIGURATION | Hard gate | Fail test setup | Wrong tool for comparison; guide to EXPLORE/MEASURE |
| TEMPORAL | Soft match | Warn | Environmental; approximate comparison acceptable |
| EXTERNAL_DEPENDENCY | Soft match | Warn | External factors change; warn but proceed |
| INFRASTRUCTURE | Soft match | Warn | Environment differs; performance affected |
| DATA_STATE | Soft match | Warn | Data context varies; results may vary |
| INFORMATIONAL | Ignored | Nothing | For traceability only |

### 4.3 Scoring for Soft-Match Categories

```java
int score = 0;
for (Covariate cov : softMatchCovariates) {
    MatchResult result = matcher.match(baseline.get(cov), test.get(cov));
    if (result == CONFORMS) {
        score += 3;  // Full match
    } else if (result == PARTIALLY_CONFORMS) {
        score += 1;  // Partial match (e.g., overlapping time windows)
    }
    // DOES_NOT_CONFORM adds 0
}
```

### 4.4 Tie-Breaking

When candidates have equal scores:
1. **Category priority**: TEMPORAL matches valued over INFRASTRUCTURE over EXTERNAL_DEPENDENCY over DATA_STATE
2. **Declaration order**: Earlier-declared covariates prioritized
3. **Recency**: More recent baseline preferred

---

## 5. Error Messages

### 5.1 Configuration Mismatch (Hard Fail)

```
┌─ CONFIGURATION MISMATCH ─────────────────────────────────────────────────────┐
│ Cannot run probabilistic test: no baseline exists for current configuration │
├──────────────────────────────────────────────────────────────────────────────┤
│ Covariate 'llm_model' is configured as: gpt-3.5                              │
│ Available baselines have: gpt-4.1-mini                                       │
├──────────────────────────────────────────────────────────────────────────────┤
│ What to do:                                                                  │
│                                                                              │
│ • Comparing models? Use EXPLORE mode to evaluate configurations:             │
│     @Experiment(mode = EXPLORE)                                              │
│     void compareModels(@FactorSource("models") String model) { ... }         │
│                                                                              │
│ • Committed to gpt-3.5? Run MEASURE to establish a new baseline:             │
│     ./gradlew measure --tests "YourExperiment.measureWithNewConfig"          │
│                                                                              │
│ • Wrong configuration? Check your covariate value for 'llm_model'            │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 Soft-Match Warnings (Category-Specific)

**TEMPORAL:**
```
⚠️ Environmental condition 'time_of_day' differs from baseline.
   Baseline: 10:30-11:00 Europe/London
   Test:     14:45-14:45 Europe/London
   Statistical comparison may be affected by time-of-day factors.
```

**INFRASTRUCTURE:**
```
⚠️ Infrastructure 'region' differs from baseline.
   Baseline: eu-west-1
   Test:     us-east-1
   Performance characteristics may vary by region.
```

**EXTERNAL_DEPENDENCY:**
```
⚠️ External dependency 'payment_gateway_version' differs from baseline.
   Baseline: v2.3
   Test:     v2.4
   External service behavior may have changed.
```

---

## 6. Design Rationale

### 6.1 Why Hard Fail for CONFIGURATION?

**Principle:** Use the right tool for the job.

PUnit provides purpose-built tooling:
- **EXPLORE mode**: Compare configurations side-by-side with statistical rigor
- **MEASURE mode**: Establish baselines under known conditions
- **Probabilistic tests**: Validate behavior against matching baselines

Running a probabilistic test with a mismatched CONFIGURATION covariate is using the wrong tool. The test would:
- Produce a "failure" that's expected (different model = different behavior)
- Generate misleading statistics (comparing apples to oranges)
- Provide no actionable insight (you already know the models differ)

**Better workflow:**
1. Want to compare gpt-3.5 vs gpt-4.1-mini? → Use EXPLORE
2. Decided to switch to gpt-3.5? → Run MEASURE to create baseline
3. Running regression tests? → Probabilistic test matches configuration

### 6.2 Why Soft Match for TEMPORAL?

Environmental conditions vary legitimately:
- Tests run at different times of day
- CI runs on different days of week
- Exact reproduction of conditions is impractical

Soft matching allows:
- Test to proceed with appropriate warning
- Developer to understand comparison context
- Gradual baseline staleness without hard failures

### 6.3 Why Ignore INFORMATIONAL?

Some covariates exist purely for:
- Audit trails (`run_id`, `operator`)
- Debugging (`experiment_tag`, `branch_name`)
- Correlation with external systems (`ticket_id`)

These should never affect baseline selection or generate warnings.

---

## 7. Implementation Plan

### 7.1 New Components

| Component | Description |
|-----------|-------------|
| `CovariateCategory` enum | Six categories as defined above |
| `@Covariate` annotation | For declaring custom covariates with category |

### 7.2 Modified Components

| Component | Changes |
|-----------|---------|
| `StandardCovariate` | Add `category()` method |
| `CovariateDeclaration` | Track category for each covariate |
| `BaselineSelector` | Two-phase algorithm with category-aware logic |
| `NoCompatibleBaselineException` | Distinguish footprint vs configuration mismatch |
| `CovariateWarningRenderer` | Category-specific warning messages |

### 7.3 Test Coverage

- CONFIGURATION mismatch causes hard fail
- Soft-match categories generate appropriate warnings
- INFORMATIONAL covariates are ignored
- Two-phase algorithm selects correct baseline
- Error messages include actionable guidance

---

## 8. Future Considerations

### 8.1 Custom Matching Strategies

Allow developers to specify matching behavior per covariate:
```java
@Covariate(
    key = "api_version", 
    category = EXTERNAL_DEPENDENCY,
    matching = MatchingStrategy.SEMVER_COMPATIBLE
)
```

### 8.2 Configuration Tolerance

For some CONFIGURATION covariates, minor variations might be acceptable:
```java
@Covariate(
    key = "temperature",
    category = CONFIGURATION,
    tolerance = 0.1  // Accept 0.7 baseline for 0.8 test
)
```

### 8.3 Baseline Recommendation

When CONFIGURATION mismatch occurs, suggest the closest available baseline:
```
No exact match for llm_model=gpt-3.5.
Closest available: llm_model=gpt-3.5-turbo (1 version difference)
```

---

## 9. Glossary Additions

| Term | Definition |
|------|------------|
| **Covariate Category** | Classification of a covariate by its nature and matching semantics |
| **Hard Gate** | A matching requirement that must be satisfied exactly; failure excludes the candidate |
| **Soft Match** | A matching approach that allows partial conformance with warnings |
| **Configuration Mismatch** | When a CONFIGURATION-category covariate differs between test and all available baselines |

---

## 10. Acceptance Criteria

- [ ] `CovariateCategory` enum implemented with six categories
- [ ] `StandardCovariate` extended with category assignment
- [ ] `@Covariate` annotation supports custom covariates with categories
- [ ] `BaselineSelector` implements two-phase algorithm
- [ ] CONFIGURATION mismatch produces hard fail with guidance
- [ ] Soft-match categories produce category-specific warnings
- [ ] INFORMATIONAL covariates are ignored in selection
- [ ] All existing tests continue to pass
- [ ] New tests cover category-aware behavior

