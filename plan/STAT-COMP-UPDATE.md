# STAT-COMP-UPDATE: Proposed Updates to Statistical Companion

This document proposes additions to the Statistical Companion (STATISTICAL-COMPANION.md) that highlight how PUnit actively encourages good statistical practice through its design. These proposals introduce **no new statistical models**—they describe how existing features address common pitfalls that would otherwise invalidate the statistical assumptions underlying probabilistic testing.

---

## Summary of Proposed Changes

| Location       | Type           | Description                                                              |
|----------------|----------------|--------------------------------------------------------------------------|
| Section 1.3    | Extension      | Extend "Assumptions and Limitations" with new subsection on stationarity |
| Section 8      | New subsection | Add "8.4 PUnit's Guardrails for Assumption Validity"                     |
| Section 8.3    | Extension      | Expand non-stationarity discussion with covariate support                |
| New Section 11 | New            | "Statistical Discipline Through Design"                                  |
| Global         | Terminology    | Replace "statistically significant" with acceptance-rule language        |

---

## Proposal 1: Extend Section 1.3 (Assumptions and Limitations)

### Current Text (Line 175-180)

```markdown
2. **Stationarity**: The success probability *p* is constant across trials. This may be violated if:
   - The LLM provider updates the model during the experiment
   - System load affects response quality
   - Input distribution changes during execution
```

### Proposed Replacement

```markdown
2. **Stationarity**: The success probability *p* is constant across trials. This may be violated if:
   - The LLM provider updates the model during the experiment
   - System load affects response quality
   - Input distribution changes during execution
   - Contextual factors differ between baseline creation and test execution (time of day, day of week, deployment region, etc.)

   **PUnit addresses stationarity through two complementary mechanisms**:
   
   - **Covariates** (see Section 8.4.1): Explicit declaration and tracking of contextual factors that may influence success rates, with warnings when baseline and test contexts differ.
   
   - **Baseline expiration** (see Section 8.4.2): Time-based validity tracking that alerts operators when baselines may no longer represent current system behavior.
   
   These features do not *guarantee* stationarity—that is impossible in practice—but they make non-stationarity **visible and auditable** rather than silently undermining inference.
```

---

## Proposal 2: Add New Section 8.4

### Proposed New Section

```markdown
### 8.4 PUnit's Guardrails for Assumption Validity

The statistical validity of probabilistic testing depends on the assumptions outlined in Section 1.3. While no framework can guarantee these assumptions hold, PUnit provides **guardrails**—features that surface violations, qualify results, and encourage practices that preserve statistical validity.

These guardrails embody a key principle: **statistical honesty over silent convenience**. Rather than producing clean verdicts that hide uncertainty, PUnit makes the conditions of inference explicit and auditable.

#### 8.4.1 Covariate-Aware Baseline Matching

**The problem**: A baseline represents the empirical behavior of a system under specific conditions. If a probabilistic test runs under different conditions—different time of day, different deployment region, different feature flags—the comparison may be invalid. The samples are drawn from **different populations**.

This is a violation of the **stationarity assumption**: the success probability $p$ is not constant between baseline creation and test execution.

**Example**: A customer service LLM performs differently during peak hours (high load, queue delays) versus off-peak hours. A baseline measured at 2 AM may not represent behavior at 2 PM.

**PUnit's solution**: Developers declare **covariates**—exogenous factors that may influence success rates:

```java
@UseCase(
    value = "shopping.product.search",
    covariates = { StandardCovariate.WEEKDAY_VERSUS_WEEKEND, StandardCovariate.TIME_OF_DAY },
    customCovariates = { "region" }
)
public class ShoppingUseCase { ... }
```

This declaration is an explicit statement: "These factors may affect performance. Track them."

**How it works**:

1. During MEASURE experiments, PUnit records the covariate values as part of the baseline specification.

2. During probabilistic tests, PUnit resolves the current covariate values and compares them against the baseline.

3. If values differ (non-conformance), PUnit issues a **warning** that qualifies the verdict:

```
⚠️ COVARIATE NON-CONFORMANCE
Statistical inference may be less reliable.

  • weekday_vs_weekend: baseline=Mo-Fr, test=Sa-So
  • time_of_day: baseline=09:03-09:25, test=14:30-14:45

The baseline was created under different conditions than the current test.
Consider whether this affects the validity of the comparison.
```

**Statistical interpretation**: Non-conformance does not change the pass/fail verdict. Instead, it **qualifies** the inference:

> "Under the assumption that success rates are comparable across these conditions, the test passes. However, this assumption may not hold—the baseline was created on a weekday, but the test is running on a weekend."

This transforms a hidden assumption into an explicit, auditable caveat.

**Why this matters**:

| Without Covariates                             | With Covariates                              |
|------------------------------------------------|----------------------------------------------|
| Silent assumption that conditions don't matter | Explicit declaration of relevant conditions  |
| Population mismatch is invisible               | Population mismatch triggers warning         |
| False confidence in verdicts                   | Qualified confidence with documented caveats |
| Statistical validity unknowable                | Statistical validity auditable               |

#### 8.4.2 Baseline Expiration

**The problem**: Systems change over time. Dependencies update, models are retrained, infrastructure drifts. A baseline from six months ago may no longer represent current behavior—even if all declared covariates match.

This is **temporal non-stationarity**: the success probability $p$ changes over calendar time in ways that cannot be captured by discrete covariates.

**PUnit's solution**: Developers declare a **validity period** for baselines:

```java
@Experiment(
    mode = ExperimentMode.MEASURE,
    useCase = ShoppingUseCase.class,
    expiresInDays = 30
)
void measureProductSearchBaseline(...) { ... }
```

This declaration is an explicit statement: "I believe this baseline remains representative for 30 days."

**How it works**:

1. The `expiresInDays` value is recorded in the baseline specification along with the experiment end timestamp.

2. During probabilistic tests, PUnit computes whether the baseline has expired.

3. As expiration approaches, PUnit issues **graduated warnings**:

| Time Remaining           | Warning Level | Message                        |
|--------------------------|---------------|--------------------------------|
| > 25% of validity period | None          | —                              |
| ≤ 25%                    | Informational | "Baseline expires soon"        |
| ≤ 10%                    | Warning       | "Baseline expiring imminently" |
| Expired                  | Prominent     | "BASELINE EXPIRED"             |

**Statistical interpretation**: Expiration does not change the pass/fail verdict. Instead, it signals:

> "This baseline is old. The system may have changed in ways not captured by covariate tracking. Interpret results with appropriate caution."

**Complementary to covariates**: Covariates catch **known, observable** context changes (weekday vs weekend, region). Expiration catches **unknown, gradual** drift (model updates, dependency changes, infrastructure evolution).

| Guardrail  | What It Catches        | Mechanism                                |
|------------|------------------------|------------------------------------------|
| Covariates | Known context mismatch | Explicit factor declaration and matching |
| Expiration | Unknown temporal drift | Calendar-based validity period           |

Together, they provide **defense in depth** against non-stationarity.

#### 8.4.3 Baseline Provenance

**The problem**: A statistical verdict is only meaningful if its empirical foundation is known. "The test passed" means little without knowing: against what baseline? Under what conditions? With what caveats?

**PUnit's solution**: Every test verdict includes explicit **baseline provenance**:

```
BASELINE REFERENCE
  File:        ShoppingUseCase-ax43-dsf2.yaml
  Generated:   2026-01-10 14:45 UTC
  Samples:     1000
  Observed rate: 95.1%
  Covariates:  weekday_vs_weekend=Mo-Fr, time_of_day=09:03-09:25, region=EU
  Expiration:  2026-02-09 (27 days remaining)
```

This ensures that **no inference result is ever detached from its empirical foundation**.

**Why this matters for statistical validity**:

- Auditors can verify that comparisons are appropriate
- Operators can investigate unexpected results by examining baseline conditions
- Historical analysis can account for which baseline was in effect when
- Reproducibility is supported by explicit documentation

#### 8.4.4 Explicit Warnings Over Silent Failures

A consistent design principle across PUnit's guardrails:

> **Warnings qualify verdicts; they do not suppress them.**

| Condition                   | Verdict Impact | Warning |
|-----------------------------|----------------|---------|
| Covariate non-conformance   | None           | Yes     |
| Expired baseline            | None           | Yes     |
| Multiple suitable baselines | None           | Yes     |

This principle reflects a statistical philosophy:

1. **The developer asked a statistical question** ("Does my system meet this threshold?"). PUnit answers that question.

2. **The answer may have caveats** ("...but the baseline is old" or "...but the conditions differ"). PUnit surfaces those caveats.

3. **The decision about whether to trust the answer remains with the human**. PUnit provides information, not absolution.

This approach preserves statistical honesty without creating operational paralysis. Tests don't mysteriously skip or fail due to metadata issues—they run, and their limitations are documented.
```

---

## Proposal 3: Expand Section 8.3 (Non-Stationarity)

### Current Text (Lines 1027-1035)

```markdown
### 8.3 Non-Stationarity

If *p* changes during the experiment:

- Point estimate $\hat{p}$ reflects time-averaged behavior
- Confidence intervals may understate true uncertainty
- Consider time-series analysis or segmented estimation

**Detection**: Plot success rate over time; test for trend using Cochran-Armitage or similar.
```

### Proposed Replacement

### 8.3 Non-Stationarity

Non-stationarity—when the success probability $p$ is not constant—is perhaps the most insidious threat to probabilistic testing. Unlike independence violations, which can sometimes be detected through autocorrelation, non-stationarity may be invisible in aggregate statistics while fundamentally invalidating comparisons.

#### 8.3.1 Forms of Non-Stationarity

| Form                         | Example                                    | Detection Difficulty                    |
|------------------------------|--------------------------------------------|-----------------------------------------|
| **Within-experiment drift**  | Model updates during a long MEASURE run    | Moderate (time-series analysis)         |
| **Between-experiment drift** | System changes between baseline and test   | Hard (requires external knowledge)      |
| **Contextual variation**     | Different behavior on weekdays vs weekends | Easy (if factors are known and tracked) |
| **Gradual degradation**      | Slow performance decay over months         | Hard (no single detectable event)       |

#### 8.3.2 Statistical Consequences

If $p$ changes during the experiment:

- Point estimate $\hat{p}$ reflects time-averaged behavior, not current behavior
- Confidence intervals may understate true uncertainty
- Threshold derivations may be based on stale data
- Verdicts may systematically mislead in one direction

If $p$ differs between baseline and test:

- The comparison is between **different populations**
- The hypothesis test answers the wrong question
- Type I and Type II error rates are no longer controlled
- Verdicts are statistically meaningless (though they appear valid)

#### 8.3.3 Why This Is Hard

Non-stationarity is difficult because:

1. **It's often invisible in aggregate data**: A 95% pass rate could arise from stable 95% behavior, or from 99% for half the samples and 91% for the other half.

2. **It can occur between experiments**: The system that generated the baseline may literally not exist anymore (different model version, different infrastructure).

3. **It can be caused by external factors**: Changes to dependencies, APIs, or infrastructure that the developer doesn't control or even know about.

4. **The statistical machinery assumes it away**: All the formulas in this document assume $p$ is constant. When it isn't, the formulas still produce numbers—they're just wrong.

#### 8.3.4 PUnit's Approach

PUnit cannot guarantee stationarity. No framework can. Instead, PUnit provides **guardrails** that:

1. **Make context explicit**: Covariate declarations force developers to think about what factors might matter.

2. **Make drift visible**: Covariate non-conformance and expiration warnings surface potential violations.

3. **Preserve auditability**: Baseline provenance ensures the conditions of inference are always documented.

4. **Qualify rather than suppress**: Warnings accompany verdicts rather than replacing them.

See Section 8.4 for detailed descriptions of these guardrails.

#### 8.3.5 Developer Responsibilities

PUnit provides tools; developers must use them wisely:

| Responsibility               | How PUnit Helps                             | What Developers Must Do                     |
|------------------------------|---------------------------------------------|---------------------------------------------|
| Identify relevant factors    | Standard covariates for common cases        | Declare covariates in use case annotations  |
| Track contextual changes     | Automatic covariate resolution and matching | Ensure custom covariates are in environment |
| Recognize baseline staleness | Expiration warnings                         | Set appropriate `expiresInDays` values      |
| Investigate warnings         | Clear warning messages with specifics       | Don't ignore non-conformance warnings       |
| Refresh stale baselines      | Prominent expiration alerts                 | Run MEASURE experiments when prompted       |

**Detection techniques** (for additional rigor):

- Plot success rate over time within long experiments; test for trend using Cochran-Armitage or Mann-Kendall
- Compare success rates across different time windows or contexts
- Monitor for structural breaks using CUSUM or similar methods
- Track baseline age and set conservative expiration policies
```

---

## Proposal 4: New Section 11

### Proposed New Section

```markdown
## 11. Statistical Discipline Through Design

PUnit is designed not just to *perform* statistical tests, but to *encourage* statistical discipline. This section summarizes how PUnit's features embody principles of good statistical practice.

### 11.1 The Problem with "Just Run More Tests"

A naive approach to non-deterministic testing is: "Run the test many times and see if it mostly passes." This approach fails because:

- **No principled sample size**: How many is "many"? Without power analysis, sample sizes are guesses.
- **No controlled error rates**: What confidence do we have in verdicts? Without hypothesis testing, confidence is undefined.
- **No threshold derivation**: What pass rate is acceptable? Without baselines, thresholds are arbitrary.
- **No assumption checking**: Are comparisons valid? Without guardrails, violations are invisible.

PUnit addresses each of these failures with specific features.

### 11.2 How PUnit Encourages Good Practice

| Statistical Principle | Common Violation | PUnit's Guardrail |
|-----------------------|------------------|-------------------|
| **Principled sample sizes** | Arbitrary numbers (10, 100, 1000) | Power analysis, confidence-first approach |
| **Controlled error rates** | Unknown false positive rates | Threshold derivation with specified α |
| **Empirically-grounded thresholds** | Hardcoded guesses | MEASURE experiments, spec-driven testing |
| **Assumption validity** | Silent violations | Covariate tracking, expiration warnings |
| **Reproducibility** | Undocumented conditions | Baseline provenance, machine-readable metadata |
| **Transparency** | Black-box verdicts | Transparent statistics mode |

### 11.3 Explicit Over Implicit

PUnit's design favors explicitness:

| Aspect | Implicit (Hidden) | Explicit (PUnit) |
|--------|-------------------|------------------|
| Threshold origin | Hardcoded number | `thresholdOrigin`, `contractRef` |
| Baseline conditions | Unmarked file | Covariate profile in spec |
| Baseline age | Check file timestamp | `expiresInDays`, expiration status |
| Comparison validity | Assumed | Non-conformance warnings |
| Statistical reasoning | Hidden in code | Transparent statistics output |

This explicitness serves multiple audiences:

- **Developers**: Understand what they're testing and why
- **Reviewers**: Verify that test configuration is appropriate
- **Auditors**: Confirm that methodology is sound
- **Operators**: Investigate unexpected results with full context

### 11.4 Warnings as Statistical Honesty

PUnit's warning system reflects a commitment to statistical honesty:

> The purpose of statistical analysis is not to produce clean answers, but to quantify uncertainty and surface limitations.

When PUnit issues a warning about covariate non-conformance or baseline expiration, it is saying:

> "I performed the calculation you requested. Here is the answer. However, you should know that the conditions underlying this calculation may not be ideal. Here's specifically what concerns me."

This is more useful than either:
- **Silent acceptance**: Producing a verdict without surfacing limitations
- **Silent rejection**: Refusing to produce a verdict due to imperfect conditions

In practice, conditions are rarely perfect. Statistical discipline means quantifying and documenting imperfection, not pretending it doesn't exist.

### 11.5 Stationarity as a Managed Condition

Traditional statistical frameworks treat stationarity as an **assumption**—something that must be true for the analysis to be valid, but that is typically asserted rather than verified.

PUnit treats stationarity as a **managed condition**:

| Traditional Approach | PUnit Approach |
|----------------------|----------------|
| Assume stationarity holds | Declare relevant factors (covariates) |
| Hope baselines are still valid | Set explicit validity periods (expiration) |
| Silently violate assumptions | Surface violations as warnings |
| Binary: valid or invalid | Graduated: valid, cautionary, expired |

This shift—from hidden assumption to managed condition—is at the heart of PUnit's approach to statistical validity.

### 11.6 The Audit Trail

Every PUnit test verdict can be traced to its foundations:

```
Verdict: PASS (97/100 = 97% ≥ threshold 90.4%)
    ↓
Threshold: 90.4% (derived from baseline at 95% confidence)
    ↓
Baseline: ShoppingUseCase-ax43-dsf2.yaml
    ↓
Empirical Basis: 951/1000 = 95.1% (measured 2026-01-10)
    ↓
Conditions: weekday=Mo-Fr, time=09:03-09:25, region=EU
    ↓
Validity: 27 days remaining (expires 2026-02-09)
    ↓
Conformance: ⚠️ time_of_day non-conforming (test ran at 14:30)
```

This audit trail enables:

- **Post-hoc investigation**: Why did this test fail last Tuesday?
- **Compliance documentation**: Proof that testing methodology is sound
- **Historical analysis**: How have pass rates changed over time?
- **Debugging**: Is this a real regression or a baseline mismatch?

### 11.7 Summary: Statistics in Service of Engineering

PUnit's statistical machinery serves a practical goal: **reliable, reproducible verdicts about non-deterministic systems**.

The features described in this section—covariate tracking, baseline expiration, provenance, transparent statistics—are not academic exercises. They address real problems that arise when testing systems like LLMs:

- "Why does this test pass on Tuesday and fail on Saturday?"
- "Is this failure real or just variance?"
- "Can I trust a baseline from three months ago?"
- "What assumptions am I making, and are they valid?"

By surfacing these questions—and providing frameworks for answering them—PUnit brings statistical discipline to probabilistic testing without requiring developers to be statisticians.
```

---

## Proposal 5: Tighten Statistical Terminology

### Feedback from Statistician Review

**Issue**: The framework uses "statistically significant" and "statistically insignificant" language, but this terminology implies p-values, hypothesis tests, and explicit α control—which the framework does not currently implement.

**Recommendation**: Replace "statistically significant / insignificant" with more accurate terminology:

| Avoid                     | Use Instead                                    |
|---------------------------|------------------------------------------------|
| statistically significant | acceptance decision, threshold met             |
| statistically insignificant | threshold not met, acceptance criterion failed |
| significant result        | evidence strength, decision confidence         |

**Justification**: The framework is currently performing an **acceptance rule** (observed pass rate vs threshold). Using inferential language ("statistically significant") implies:

- p-values
- Hypothesis tests
- Explicit α control

This invites justified criticism and erodes trust with statistically literate users who will correctly note that the terminology doesn't match the methodology.

**Scope**: Audit the following for terminology changes:

1. STATISTICAL-COMPANION.md
2. Console output messages (transparent statistics mode)
3. Report templates
4. API documentation and Javadoc
5. User-facing annotations

**Example Corrections**:

| Before | After |
|--------|-------|
| "The result is statistically significant" | "The observed rate meets the acceptance threshold" |
| "Not statistically significant" | "Evidence insufficient to conclude threshold met" |
| "Significant improvement detected" | "Observed rate exceeds threshold with confidence X%" |

**Principle**: Use language that accurately describes what the framework actually does (acceptance testing against empirically-derived thresholds) rather than language that implies more sophisticated inference (hypothesis testing with p-values).

---

## Integration Notes

### Placement

1. **Proposal 1** (Section 1.3 extension): Replace existing stationarity bullet point with expanded version.

2. **Proposal 2** (Section 8.4): Add as new subsection after existing Section 8.3.

3. **Proposal 3** (Section 8.3 expansion): Replace existing Section 8.3 with expanded version.

4. **Proposal 4** (Section 11): Add as new section before References.

### Cross-References

The following cross-references should be added:

- Section 1.3 → Section 8.4
- Section 8.3 → Section 8.4
- Section 8.4 → Section 11
- Section 11 → Section 8.4

### Table of Contents Update

Add to TOC:

```markdown
- [8.4 PUnit's Guardrails for Assumption Validity](#84-punits-guardrails-for-assumption-validity)
  - [8.4.1 Covariate-Aware Baseline Matching](#841-covariate-aware-baseline-matching)
  - [8.4.2 Baseline Expiration](#842-baseline-expiration)
  - [8.4.3 Baseline Provenance](#843-baseline-provenance)
  - [8.4.4 Explicit Warnings Over Silent Failures](#844-explicit-warnings-over-silent-failures)
- [11. Statistical Discipline Through Design](#11-statistical-discipline-through-design)
  - [11.1 The Problem with "Just Run More Tests"](#111-the-problem-with-just-run-more-tests)
  - [11.2 How PUnit Encourages Good Practice](#112-how-punit-encourages-good-practice)
  - [11.3 Explicit Over Implicit](#113-explicit-over-implicit)
  - [11.4 Warnings as Statistical Honesty](#114-warnings-as-statistical-honesty)
  - [11.5 Stationarity as a Managed Condition](#115-stationarity-as-a-managed-condition)
  - [11.6 The Audit Trail](#116-the-audit-trail)
  - [11.7 Summary: Statistics in Service of Engineering](#117-summary-statistics-in-service-of-engineering)
```

---

## Review Checklist

- [ ] Proposals introduce no new statistical models
- [ ] Proposals accurately describe REQ-COV and REQ-EXP features
- [ ] Language is appropriate for statistician audience
- [ ] Cross-references are consistent
- [ ] Examples are realistic and illustrative
- [ ] Tone is consistent with existing document
- [ ] Terminology audit complete: no "statistically significant/insignificant" unless implementing hypothesis testing

