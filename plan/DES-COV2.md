# DES-COV2: Covariate Categories and Category-Aware Baseline Selection

## Status

| Attribute  | Value                       |
|------------|-----------------------------|
| Status     | PROPOSED                    |
| Created    | 2026-01-14                  |
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

| Purpose                         | Nature             | Mismatch Meaning                              |
|---------------------------------|--------------------|-----------------------------------------------|
| Track environmental conditions  | Cannot control     | "Conditions differ — be cautious"             |
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

| Category            | Matching   | On Mismatch     | Rationale                                           |
|---------------------|------------|-----------------|-----------------------------------------------------|
| CONFIGURATION       | Hard gate  | Fail test setup | Wrong tool for comparison; guide to EXPLORE/MEASURE |
| TEMPORAL            | Soft match | Warn            | Environmental; approximate comparison acceptable    |
| EXTERNAL_DEPENDENCY | Soft match | Warn            | External factors change; warn but proceed           |
| INFRASTRUCTURE      | Soft match | Warn            | Environment differs; performance affected           |
| DATA_STATE          | Soft match | Warn            | Data context varies; results may vary               |
| INFORMATIONAL       | Ignored    | Nothing         | For traceability only                               |

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

### 4.5 Temporal Matching Strategy

For TEMPORAL covariates, the algorithm seeks the **closest fit** to the probabilistic test's runtime:

#### TIME_OF_DAY Matching

| Scenario                                                        | Match Result         | Score |
|-----------------------------------------------------------------|----------------------|-------|
| Test time falls **within** baseline window                      | `CONFORMS`           | 3     |
| Test time is **adjacent** to baseline window (within tolerance) | `PARTIALLY_CONFORMS` | 1     |
| Test time is **distant** from baseline window                   | `DOES_NOT_CONFORM`   | 0     |

**Distance calculation:**
```
distance = min(
    abs(test_time - baseline_window_start),
    abs(test_time - baseline_window_end)
)
```

When multiple baselines have `DOES_NOT_CONFORM`, select the one with **smallest temporal distance**.

**Example:**
```
Baseline A: 09:00-10:00 Europe/London
Baseline B: 14:00-15:00 Europe/London
Test runs:  11:30 Europe/London

Distance to A: 1h 30m (from 10:00)
Distance to B: 2h 30m (to 14:00)
→ Baseline A selected (closer)
```

#### WEEKDAY_VERSUS_WEEKEND Matching

| Scenario                                     | Match Result       | Score |
|----------------------------------------------|--------------------|-------|
| Same category (both weekday or both weekend) | `CONFORMS`         | 3     |
| Different category                           | `DOES_NOT_CONFORM` | 0     |

**Day proximity for tie-breaking:**

When multiple baselines share the same day category, prefer the baseline established on the **closest day** to the test's execution day.

**Example:**
```
Test runs: Wednesday

Baseline A: Established Monday (weekday)     → distance: 2 days
Baseline B: Established Tuesday (weekday)    → distance: 1 day
Baseline C: Established Saturday (weekend)   → category mismatch

→ Baseline B selected (same category, closest day)
```

#### Combined Temporal Scoring

When both `TIME_OF_DAY` and `WEEKDAY_VERSUS_WEEKEND` are declared:

1. **Filter** by day category match (weekday/weekend must match if possible)
2. **Rank** by time-of-day proximity among matching day-category candidates
3. If no day-category match exists, fall back to time-of-day proximity alone with warning

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

## 6. Baseline File Naming

### 6.1 Filename Format

```
<UseCaseId>.<MethodName>-<YYYYMMDD-HHMM>-<FootprintHash>-<CovHash1>-<CovHash2>.yaml
```

### 6.2 Component Breakdown

| Component              | Purpose                                    | Example           |
|------------------------|--------------------------------------------|-------------------|
| `UseCaseId`            | Group files by use case class              | `ShoppingUseCase` |
| `MethodName`           | Distinguish experiment methods             | `searchProducts`  |
| `YYYYMMDD-HHMM`        | Temporal ordering (sortable)               | `20260114-1030`   |
| `FootprintHash`        | Hash of factors + covariate declaration    | `a1b2`            |
| `CovHash1-CovHash2...` | Covariate value hashes (declaration order) | `c3d4-e5f6`       |

### 6.3 Examples

```
ShoppingUseCase.measureSearch-20260114-1030-a1b2-c3d4-e5f6.yaml
ShoppingUseCase.measureSearch-20260114-1445-a1b2-c3d4-e5f6.yaml  ← Same covariates, later run
ShoppingUseCase.measureSearch-20260114-1500-a1b2-7x8y-e5f6.yaml  ← Different TIME_OF_DAY
ShoppingUseCase.measureCart-20260114-1030-a1b2-c3d4-e5f6.yaml    ← Different method
```

### 6.4 Design Goals

| Goal                           | How Achieved                                                      |
|--------------------------------|-------------------------------------------------------------------|
| **Grouped by use case**        | `UseCaseId.` prefix ensures related files sort together           |
| **Multiple methods supported** | Method name included after use case ID                            |
| **Temporal ordering**          | Timestamp before hashes → alphabetical sort = chronological order |
| **Diffable**                   | Sort by name, compare adjacent files to see progression           |
| **Unique by covariates**       | Value hashes ensure distinct configurations create distinct files |

### 6.5 What Gets Hashed in Filename

| Category            | In Filename Hash? | Rationale                                                |
|---------------------|-------------------|----------------------------------------------------------|
| CONFIGURATION       | ✅ Yes             | Different config = different baseline                    |
| TEMPORAL            | ✅ Yes             | Different conditions = different baseline for selection  |
| INFRASTRUCTURE      | ✅ Yes             | Different environment = different baseline               |
| EXTERNAL_DEPENDENCY | ✅ Yes             | Different external state = different baseline            |
| DATA_STATE          | ✅ Yes             | Different data context = different baseline              |
| **INFORMATIONAL**   | ❌ **No**          | Traceability only; not used for baseline differentiation |

### 6.6 Overwrite Behavior

If two experiments produce files with **identical names** (same method, same footprint, same covariate values), the **newer file overwrites** the older. This is intentional:
- It's a re-run of the same configuration
- The newer baseline is more relevant
- Prevents accumulation of stale baselines

**INFORMATIONAL covariates do not affect filename:**
- Two runs differing only by `run_id` or `operator` → same filename → overwrite
- INFORMATIONAL data is recorded inside the file for traceability

### 6.7 Multiple Experiment Methods per Use Case

```java
@UseCase("ShoppingUseCase")
public class ShoppingUseCase {
    public UseCaseResult searchProducts(String query) { ... }
    public UseCaseResult addToCart(String productId) { ... }
}

// Experiment class
public class ShoppingExperiment {
    
    @Experiment(useCase = ShoppingUseCase.class)
    void measureSearch() { ... }  // → ShoppingUseCase.measureSearch-...
    
    @Experiment(useCase = ShoppingUseCase.class)
    void measureCart() { ... }    // → ShoppingUseCase.measureCart-...
}
```

The **experiment method name** (not the use case method) appears in the filename. This ensures:
- Different experiments targeting the same use case are distinguishable
- Factor variations within an experiment share a common prefix
- Temporal sorting works within each experiment method

### 6.8 File-Based Matching (Performance Optimization)

The probabilistic test engine computes the same footprint and covariate value hashes that the experiment did. Because these values are encoded directly in the filename, the engine can perform **rapid baseline selection by scanning filenames alone**—without opening or parsing any YAML files.

**Matching algorithm:**
1. List files in specs directory matching `<UseCaseId>.*`
2. Parse filenames to extract footprint hash and covariate hashes
3. Filter candidates where footprint matches (exact)
4. Filter candidates where CONFIGURATION covariate hashes match (hard gate)
5. Score remaining candidates by covariate hash matches (soft match)
6. Only open the best-matching file(s) to load baseline data

**Benefits:**
- **O(n) filename scans** instead of O(n) file reads + YAML parsing
- Scales well with large baseline repositories
- File contents only accessed for the selected baseline(s)

**Note:** The timestamp in the filename is used for tie-breaking (prefer most recent) but does not participate in the matching algorithm itself.

---

## 7. Statistical Reporting Language

### 7.1 Principles

The language used in statistical reports must:

1. **Be category-sensitive** — Different covariate categories warrant different phrasing
2. **Remain neutral on partial matches** — State facts without dictating interpretation
3. **Leave interpretation to the evaluator** — The human reader decides significance
4. **Be affirmative on full matches** — Confirm statistical validity when conditions align
5. **Use scientific precision** — Avoid colloquial or emotive language

### 7.2 Full Match (100% Covariate Conformance)

When all declared covariates match between test and baseline:

```
COVARIATE CONFORMANCE: FULL

All declared covariates match the selected baseline.
Statistical comparison is performed under equivalent conditions.

  ✓ llm_model: gpt-4.1-mini
  ✓ time_of_day: 14:00-15:00 Europe/London
  ✓ region: eu-west-1
```

**Language characteristics:**
- Affirmative but factual ("equivalent conditions")
- Does not claim the test result is therefore valid—only that conditions align
- Lists matched covariates for transparency

### 7.3 Partial Match (Some Covariates Differ)

When some covariates differ, the report states the facts neutrally:

```
COVARIATE CONFORMANCE: PARTIAL

The selected baseline was established under different conditions.
Covariate differences are listed below for evaluation.

  ✓ llm_model: gpt-4.1-mini
  ⚠ time_of_day
      Baseline: 14:00-15:00 Europe/London
      Test:     09:30-10:00 Europe/London
  ✓ region: eu-west-1
```

**Language characteristics:**
- Factual statement of difference ("different conditions")
- No judgment on whether this invalidates results
- Phrase "for evaluation" delegates interpretation to the reader

### 7.4 Category-Specific Language

Each category uses tailored phrasing that reflects its nature:

#### CONFIGURATION (Hard Gate — No Partial Match Possible)
If a CONFIGURATION mismatch occurs, the test does not proceed. The error message (see Section 5) applies, not reporting language.

#### TEMPORAL
```
⚠ time_of_day
    Baseline: 14:00-15:00 Europe/London
    Test:     09:30-10:00 Europe/London
    
    The test executed outside the baseline's temporal window.
    Temporal factors may influence system behavior.
```

#### INFRASTRUCTURE
```
⚠ hosting_environment
    Baseline: aws-production
    Test:     local-dev
    
    Infrastructure differs from baseline conditions.
    Resource availability and latency characteristics may vary.
```

#### EXTERNAL_DEPENDENCY
```
⚠ payment_gateway_version
    Baseline: v2.3
    Test:     v2.4
    
    External dependency version differs from baseline.
    Third-party service behavior may have changed.
```

#### DATA_STATE
```
⚠ catalog_size
    Baseline: 50000
    Test:     75000
    
    Data state differs from baseline conditions.
    Data volume or distribution may affect performance.
```

#### INFORMATIONAL
INFORMATIONAL covariates are **not reported** in the conformance section. They appear only in the baseline metadata section for traceability:

```
BASELINE METADATA
  Established: 2026-01-14 10:30 UTC
  Samples: 1000
  Run ID: exp-20260114-001
  Operator: ci-pipeline
```

### 7.5 Summary Table

| Match State | Header                           | Tone        | Interpretation Guidance   |
|-------------|----------------------------------|-------------|---------------------------|
| Full        | `COVARIATE CONFORMANCE: FULL`    | Affirmative | "equivalent conditions"   |
| Partial     | `COVARIATE CONFORMANCE: PARTIAL` | Neutral     | "for evaluation"          |
| None found  | `NO COMPATIBLE BASELINE`         | Directive   | Guides to EXPLORE/MEASURE |

### 7.6 Phrasing Patterns by Category

| Category            | Mismatch Phrase                                     | Factor Phrase                                                |
|---------------------|-----------------------------------------------------|--------------------------------------------------------------|
| TEMPORAL            | "executed outside the baseline's temporal window"   | "Temporal factors may influence system behavior"             |
| INFRASTRUCTURE      | "Infrastructure differs from baseline conditions"   | "Resource availability and latency characteristics may vary" |
| EXTERNAL_DEPENDENCY | "External dependency version differs from baseline" | "Third-party service behavior may have changed"              |
| DATA_STATE          | "Data state differs from baseline conditions"       | "Data volume or distribution may affect performance"         |

### 7.7 Language to Avoid

| Avoid                         | Why                      | Use Instead                                       |
|-------------------------------|--------------------------|---------------------------------------------------|
| "Test results may be invalid" | Prejudges interpretation | "Conditions differ"                               |
| "Warning: mismatch detected"  | Implies fault            | "Covariate difference noted"                      |
| "Baseline is stale"           | Value judgment           | "Baseline established under different conditions" |
| "Results are unreliable"      | Prejudges significance   | "Evaluate in context of differences"              |
| "You should re-run"           | Prescriptive             | (Leave to evaluator)                              |

### 7.8 Example: Complete Report Section

```
═══════════════════════════════════════════════════════════════════
                      COVARIATE CONFORMANCE: PARTIAL
═══════════════════════════════════════════════════════════════════

The selected baseline was established under different conditions.
Covariate differences are listed below for evaluation.

MATCHED COVARIATES
  ✓ llm_model: gpt-4.1-mini                           [CONFIGURATION]
  ✓ region: eu-west-1                                 [INFRASTRUCTURE]

DIFFERING COVARIATES
  ⚠ time_of_day                                       [TEMPORAL]
      Baseline: 14:00-15:00 Europe/London
      Test:     09:30-10:00 Europe/London
      
      The test executed outside the baseline's temporal window.
      Temporal factors may influence system behavior.

  ⚠ catalog_size                                      [DATA_STATE]
      Baseline: 50000
      Test:     75000
      
      Data state differs from baseline conditions.
      Data volume or distribution may affect performance.

───────────────────────────────────────────────────────────────────
BASELINE METADATA
  File: ShoppingUseCase.measureSearch-20260114-1030-a1b2-c3d4.yaml
  Established: 2026-01-14 10:30 UTC
  Samples: 1000
═══════════════════════════════════════════════════════════════════
```

---

## 8. Design Rationale

### 8.1 Why Hard Fail for CONFIGURATION?

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

### 8.2 Why Soft Match for TEMPORAL?

Environmental conditions vary legitimately:
- Tests run at different times of day
- CI runs on different days of week
- Exact reproduction of conditions is impractical

Soft matching allows:
- Test to proceed with appropriate warning
- Developer to understand comparison context
- Gradual baseline staleness without hard failures

### 8.3 Why Ignore INFORMATIONAL?

Some covariates exist purely for:
- Audit trails (`run_id`, `operator`)
- Debugging (`experiment_tag`, `branch_name`)
- Correlation with external systems (`ticket_id`)

These should never affect baseline selection or generate warnings.

---

## 9. Developer Experience: Covariate Value Resolution

### 9.1 Resolution Hierarchy

Covariate values are resolved in the following order:

```
┌─────────────────────────────────────────────────────────────────┐
│                    Covariate Resolution Order                    │
├─────────────────────────────────────────────────────────────────┤
│ 1. Instance-provided value (@CovariateSource method)            │
│    → Returns whatever the instance's configuration says         │
│    ↓ if method not present                                      │
│ 2. System property: org.javai.punit.covariate.<key>             │
│    → Fallback for covariates without resolver methods           │
│    ↓ if null                                                    │
│ 3. Environment variable: ORG_JAVAI_PUNIT_COVARIATE_<KEY>        │
│    → Alternative fallback                                       │
│    ↓ if null                                                    │
│ 4. Default resolver (for standard covariates)                   │
│    → TIME_OF_DAY, TIMEZONE use system values automatically      │
└─────────────────────────────────────────────────────────────────┘
```

### 9.2 Naming Convention for System/Environment Properties

| Covariate Key         | System Property                                 | Environment Variable                            |
|-----------------------|-------------------------------------------------|-------------------------------------------------|
| `llm_model`           | `org.javai.punit.covariate.llm_model`           | `ORG_JAVAI_PUNIT_COVARIATE_LLM_MODEL`           |
| `region`              | `org.javai.punit.covariate.region`              | `ORG_JAVAI_PUNIT_COVARIATE_REGION`              |
| `hosting_environment` | `org.javai.punit.covariate.hosting_environment` | `ORG_JAVAI_PUNIT_COVARIATE_HOSTING_ENVIRONMENT` |

### 9.3 The @CovariateSource Annotation

Developers provide covariate values via annotated instance methods:

```java
@UseCase(
    value = "ProductSearch",
    covariates = { StandardCovariate.TIME_OF_DAY },
    customCovariates = {
        @Covariate(key = "llm_model", category = CovariateCategory.CONFIGURATION),
        @Covariate(key = "region", category = CovariateCategory.INFRASTRUCTURE)
    }
)
@Component
public class ProductSearchUseCase {
    
    private final LlmClient llmClient;
    
    @Value("${app.region}")
    private String region;
    
    @CovariateSource("llm_model")
    public String getLlmModel() {
        return llmClient.getModelName();  // Reads from injected config
    }
    
    @CovariateSource("region")
    public String getRegion() {
        return region;  // Reads from Spring config (operator-controlled)
    }
    
    // TIME_OF_DAY: No resolver needed — default resolver handles it
}
```

**Key points:**

- **Instance method, not static**: Works naturally with dependency injection
- **Reads from configuration**: The method returns whatever the application's configuration says
- **Operator control via deployment config**: Operators control values by setting `application.yml`, environment variables, etc.
- **No override mechanism**: PUnit trusts the instance; operators control values at deployment level

### 9.4 Return Types

`@CovariateSource` methods may return:

| Return Type      | Conversion                                               |
|------------------|----------------------------------------------------------|
| `String`         | Wrapped as `CovariateValue.StringValue`                  |
| `CovariateValue` | Used directly (for complex types like `TimeWindowValue`) |

```java
// Simple string value
@CovariateSource("llm_model")
public String getLlmModel() {
    return "gpt-4.1-mini";
}

// Complex value (time window)
@CovariateSource("business_hours")
public CovariateValue getBusinessHours() {
    return new CovariateValue.TimeWindowValue(
        LocalTime.of(9, 0), LocalTime.of(17, 0), ZoneId.of("Europe/London"));
}
```

### 9.5 When Each Resolution Level Applies

| Covariate        | Typical Source                     | Rationale                           |
|------------------|------------------------------------|-------------------------------------|
| `llm_model`      | `@CovariateSource` method          | Application code knows the model    |
| `prompt_version` | `@CovariateSource` method          | Application code knows the version  |
| `region`         | `@CovariateSource` or sys/env prop | Deployment config determines region |
| `timezone`       | Default resolver                   | JVM knows system timezone           |
| `time_of_day`    | Default resolver                   | PUnit captures execution time       |
| `run_id`         | Sys/env prop                       | Operator sets for traceability      |

### 9.6 Design Rationale: No Override Mechanism

**The instance is the source of truth.** If the developer provides a `@CovariateSource` method, that value is used.

**Operators control values via deployment configuration:**
- In Spring: `application.yml`, `application-prod.yml`, environment-specific profiles
- The use case instance reads from this configuration
- The `@CovariateSource` method returns the operator-configured value

**This avoids complexity:**
- No "who can override whom" rules
- No category-specific override permissions
- Leverages existing configuration patterns (Spring profiles, env-specific config)

**If an operator needs control:**
1. Operator sets deployment configuration (`app.llm.model=gpt-4`)
2. Instance reads from config (`@Value("${app.llm.model}")`)
3. `@CovariateSource` returns the operator-set value

**If a developer hard-codes a value they shouldn't:**
- This is a developer bug, not a PUnit problem
- Peer review and code check gates should catch this
- Developer should read from configuration, not hard-code

---

## 10. Use Case Instance Provisioning

### 10.1 The Problem

To call `@CovariateSource` methods, PUnit needs a use case **instance**. But:
- PUnit currently references only the use case **class** (`@Experiment(useCase = X.class)`)
- The instance is created inside the experiment/test body
- Different applications instantiate use cases differently (DI, manual, etc.)

### 10.2 Design Principle

**PUnit has no dependency on Spring, Guice, or any DI framework.**

Integration with these frameworks is achieved through:
- A simple `UseCaseProvider` interface (in PUnit core)
- Framework-specific implementations (in user code)
- Separate documentation for each framework

### 10.3 UseCaseProvider Interface

```java
/**
 * Provides use case instances for experiments and tests.
 * Implementations vary by application architecture.
 */
public interface UseCaseProvider {
    
    /**
     * Returns an instance of the specified use case class.
     * The instance must be fully initialized (dependencies injected, etc.).
     */
    <T> T getInstance(Class<T> useCaseClass);
    
    /**
     * Returns true if this provider can supply the given use case class.
     */
    boolean supports(Class<?> useCaseClass);
}
```

### 10.4 UseCaseRegistry

```java
/**
 * Registry of use case providers.
 * PUnit consults this to obtain use case instances.
 */
public class UseCaseRegistry {
    
    private final List<UseCaseProvider> providers = new ArrayList<>();
    
    public UseCaseRegistry register(UseCaseProvider provider) {
        providers.add(0, provider);  // Most recently added takes priority
        return this;
    }
    
    public <T> T getInstance(Class<T> useCaseClass) {
        for (UseCaseProvider provider : providers) {
            if (provider.supports(useCaseClass)) {
                return provider.getInstance(useCaseClass);
            }
        }
        throw new NoUseCaseProviderException(useCaseClass);
    }
    
    /**
     * Creates a registry with the reflective fallback provider.
     */
    public static UseCaseRegistry withDefaults() {
        return new UseCaseRegistry()
            .register(new ReflectiveUseCaseProvider());
    }
}
```

### 10.5 Built-in Providers

#### ReflectiveUseCaseProvider (Default Fallback)

```java
public class ReflectiveUseCaseProvider implements UseCaseProvider {
    
    @Override
    public <T> T getInstance(Class<T> useCaseClass) {
        try {
            return useCaseClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new UseCaseInstantiationException(useCaseClass, e);
        }
    }
    
    @Override
    public boolean supports(Class<?> useCaseClass) {
        try {
            useCaseClass.getDeclaredConstructor();
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
```

#### FactoryUseCaseProvider (Manual Registration)

```java
public class FactoryUseCaseProvider implements UseCaseProvider {
    
    private final Map<Class<?>, Supplier<?>> factories = new HashMap<>();
    
    public <T> FactoryUseCaseProvider register(Class<T> useCaseClass, Supplier<T> factory) {
        factories.put(useCaseClass, factory);
        return this;
    }
    
    @Override
    public <T> T getInstance(Class<T> useCaseClass) {
        Supplier<?> factory = factories.get(useCaseClass);
        if (factory == null) {
            throw new NoUseCaseProviderException(useCaseClass);
        }
        return useCaseClass.cast(factory.get());
    }
    
    @Override
    public boolean supports(Class<?> useCaseClass) {
        return factories.containsKey(useCaseClass);
    }
}
```

### 10.6 Global Registry Configuration

The primary mechanism for configuring the registry is via a global static method:

```java
// In test setup, base test class, or @BeforeAll
PUnit.setUseCaseRegistry(
    UseCaseRegistry.withDefaults()
        .register(new FactoryUseCaseProvider()
            .register(ShoppingUseCase.class, () -> new ShoppingUseCase(mockLlm)))
);
```

**Provider priority:** Providers are consulted in reverse registration order (most recent first). The `ReflectiveUseCaseProvider` (from `withDefaults()`) serves as a fallback.

### 10.7 Per-Test Override (Optional)

For fine-grained control, a test class may declare a registry field:

```java
@ExtendWith(PUnitExtension.class)
public class ShoppingExperiment {
    
    @RegisterExtension
    static UseCaseRegistryExtension registry = UseCaseRegistryExtension.builder()
        .register(ShoppingUseCase.class, () -> new ShoppingUseCase(specialConfig))
        .build();
    
    @Experiment(useCase = ShoppingUseCase.class)
    void measureSearch(ShoppingUseCase uc, ResultCaptor captor) { ... }
}
```

**Resolution order:**
1. Check for `@RegisterExtension UseCaseRegistryExtension` in test class → use if present
2. Fall back to global `PUnit.getUseCaseRegistry()`

### 10.8 How PUnit Obtains the Instance

```
┌─────────────────────────────────────────────────────────────────────┐
│  @Experiment(useCase = ShoppingUseCase.class)                       │
│  void measureSearch(ShoppingUseCase uc, ResultCaptor captor)        │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
                    ┌─────────────────────────┐
                    │  PUnit resolves 'uc'    │
                    │  parameter              │
                    └────────────┬────────────┘
                                 │
                                 ▼
                    ┌─────────────────────────┐
                    │  UseCaseRegistry        │
                    │  .getInstance(          │
                    │    ShoppingUseCase)     │
                    └────────────┬────────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              ▼                  ▼                  ▼
       ┌────────────┐     ┌────────────┐     ┌────────────┐
       │  Factory   │     │   (DI)     │     │ Reflective │
       │  Provider  │     │  Provider  │     │  Provider  │
       └────────────┘     └────────────┘     └────────────┘
                                 │
                                 ▼
                    ┌─────────────────────────┐
                    │  Use case instance      │
                    │  (fully initialized)    │
                    ├─────────────────────────┤
                    │  → @CovariateSource     │
                    │    methods callable     │
                    │  → Functionality        │
                    │    invokable            │
                    └─────────────────────────┘
```

### 10.9 Framework Integration

PUnit provides the interfaces. Users provide framework-specific implementations.

| Framework | Implementation | Documentation |
|-----------|----------------|---------------|
| Spring Boot | `SpringUseCaseProvider` | `docs/USERGUIDE-SPRING.md` |
| Google Guice | `GuiceUseCaseProvider` | `docs/USERGUIDE-GUICE.md` |
| Plain Java | `FactoryUseCaseProvider` | (Core user guide) |

### 10.10 Instance Lifecycle

| Scope | Behavior | Use When |
|-------|----------|----------|
| Per-invocation | New instance for each sample | Stateless use cases, isolation needed |
| Cached | Same instance reused | Expensive initialization, stateless |

The `UseCaseProvider` implementation controls this. The `FactoryUseCaseProvider` creates per-invocation by default (calls supplier each time).

---

## 11. Implementation Plan

### 11.1 New Components

| Component                       | Description                                          |
|---------------------------------|------------------------------------------------------|
| `CovariateCategory` enum        | Six categories as defined above                      |
| `@Covariate` annotation         | For declaring custom covariates with category        |
| `@CovariateSource` annotation   | Marks instance methods that provide covariate values |
| `UseCaseProvider` interface     | Abstraction for obtaining use case instances         |
| `UseCaseRegistry` class         | Registry of providers, consulted by PUnit            |
| `ReflectiveUseCaseProvider`     | Default provider using no-arg constructor            |
| `FactoryUseCaseProvider`        | Provider with manual factory registration            |
| `UseCaseRegistryExtension`      | JUnit extension for per-class registry override      |
| `NoUseCaseProviderException`    | Thrown when no provider can supply a use case        |

### 11.2 Modified Components

| Component                       | Changes                                              |
|---------------------------------|------------------------------------------------------|
| `StandardCovariate`             | Add `category()` method                              |
| `CovariateDeclaration`          | Track category for each covariate                    |
| `CovariateResolverRegistry`     | Support `@CovariateSource` method discovery          |
| `BaselineSelector`              | Two-phase algorithm with category-aware logic        |
| `NoCompatibleBaselineException` | Distinguish footprint vs configuration mismatch      |
| `CovariateWarningRenderer`      | Category-specific warning messages                   |
| `PUnit` class                   | Add `setUseCaseRegistry()` / `getUseCaseRegistry()`  |
| `ExperimentExtension`           | Resolve use case instance via registry               |
| `ProbabilisticTestExtension`    | Resolve use case instance via registry               |
| `BaselineFileNamer`             | Include experiment method name in filename           |

### 11.3 Test Coverage

- CONFIGURATION mismatch causes hard fail
- Soft-match categories generate appropriate warnings
- INFORMATIONAL covariates are ignored
- Two-phase algorithm selects correct baseline
- Error messages include actionable guidance
- `@CovariateSource` methods are discovered and invoked
- Resolution hierarchy (instance → sys prop → env var → default) works correctly

---

## 12. Future Considerations

### 12.1 Custom Matching Strategies

Allow developers to specify matching behavior per covariate:
```java
@Covariate(
    key = "api_version", 
    category = EXTERNAL_DEPENDENCY,
    matching = MatchingStrategy.SEMVER_COMPATIBLE
)
```

### 12.2 Configuration Tolerance

For some CONFIGURATION covariates, minor variations might be acceptable:
```java
@Covariate(
    key = "temperature",
    category = CONFIGURATION,
    tolerance = 0.1  // Accept 0.7 baseline for 0.8 test
)
```

### 12.3 Baseline Recommendation

When CONFIGURATION mismatch occurs, suggest the closest available baseline:
```
No exact match for llm_model=gpt-3.5.
Closest available: llm_model=gpt-3.5-turbo (1 version difference)
```

---

## 13. Glossary Additions

| Term                       | Definition                                                                                |
|----------------------------|-------------------------------------------------------------------------------------------|
| **Covariate Category**     | Classification of a covariate by its nature and matching semantics                        |
| **Hard Gate**              | A matching requirement that must be satisfied exactly; failure excludes the candidate     |
| **Soft Match**             | A matching approach that allows partial conformance with warnings                         |
| **Configuration Mismatch** | When a CONFIGURATION-category covariate differs between test and all available baselines  |
| **@CovariateSource**       | Annotation marking an instance method that provides a covariate's value                   |
| **Resolution Hierarchy**   | The order in which covariate values are resolved: instance → sys prop → env var → default |
| **UseCaseProvider**        | Interface for supplying use case instances; implementations vary by app architecture      |
| **UseCaseRegistry**        | Central registry of providers; PUnit consults this to obtain use case instances           |

---

## 14. Acceptance Criteria

### Category-Aware Selection
- [ ] `CovariateCategory` enum implemented with six categories
- [ ] `StandardCovariate` extended with category assignment
- [ ] `@Covariate` annotation supports custom covariates with categories
- [ ] `BaselineSelector` implements two-phase algorithm
- [ ] CONFIGURATION mismatch produces hard fail with guidance
- [ ] Soft-match categories produce category-specific warnings
- [ ] INFORMATIONAL covariates are ignored in selection

### Covariate Value Resolution
- [ ] `@CovariateSource` annotation implemented
- [ ] Instance methods are discovered and invoked for covariate resolution
- [ ] System property fallback works: `org.javai.punit.covariate.<key>`
- [ ] Environment variable fallback works: `ORG_JAVAI_PUNIT_COVARIATE_<KEY>`
- [ ] Resolution hierarchy is respected (instance → sys prop → env var → default)
- [ ] Both `String` and `CovariateValue` return types are supported

### Baseline File Naming
- [ ] Filename format: `<UseCaseId>.<MethodName>-<YYYYMMDD-HHMM>-<FootprintHash>-<CovHashes>.yaml`
- [ ] INFORMATIONAL covariates excluded from filename hash
- [ ] Same filename → overwrite (newer baseline replaces older)
- [ ] Multiple experiment methods produce distinct filenames

### Use Case Instance Provisioning
- [ ] `UseCaseProvider` interface implemented
- [ ] `UseCaseRegistry` implemented with provider chain
- [ ] `ReflectiveUseCaseProvider` implemented (default fallback)
- [ ] `FactoryUseCaseProvider` implemented (manual registration)
- [ ] `PUnit.setUseCaseRegistry()` / `getUseCaseRegistry()` static methods
- [ ] `UseCaseRegistryExtension` for per-class override
- [ ] `ExperimentExtension` obtains use case via registry
- [ ] `ProbabilisticTestExtension` obtains use case via registry
- [ ] Use case instance injected as test method parameter

### General
- [ ] All existing tests continue to pass
- [ ] New tests cover category-aware behavior
- [ ] New tests cover resolution hierarchy
- [ ] New tests cover baseline file naming

