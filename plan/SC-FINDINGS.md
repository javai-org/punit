# Statistical Companion: SLA Paradigm Findings

## Executive Summary

The STATISTICAL-COMPANION document is rigorously correct for the **spec-driven paradigm** (empirical baseline) but requires significant enhancement to properly cover the **SLA-driven paradigm**. The current framing assumes thresholds are always derived from experimental measurement, overlooking the equally valid case where thresholds are given by contract, policy, or SLA.

This document identifies specific gaps and proposes changes to make the Statistical Companion serve both paradigms with equal statistical rigor.

### Terminology Convention

Throughout PUnit documentation, we use these terms consistently:

| Term         | Context                        | Examples                                             |
|--------------|--------------------------------|------------------------------------------------------|
| **Paradigm** | Source of threshold            | SLA-driven, Spec-driven                              |
| **Mode**     | Experiment type                | EXPLORE, MEASURE                                     |
| **Approach** | Test parameterization strategy | Sample-size-first, Confidence-first, Threshold-first |

### Ordering Principle

When presenting the two paradigms, **SLA-driven should come first** because:
- It involves fewer concepts (no experiments, no baseline estimation)
- It provides a gentler on-ramp for developers new to probabilistic testing
- The threshold is simply *given*, which is conceptually simpler than *derived*

The spec-driven paradigm builds on SLA-driven by adding experimentation, baseline estimation, and threshold derivation.

---

## 1. Fundamental Framing Issue

### Current State

The document is titled "Formal Statistical Foundations for PUnit" but is implicitly framed around a single workflow:

```
Experiment → Estimate p̂ → Derive threshold → Test
```

This is evident in:
- Section 2: "Baseline Estimation (Experiment Phase)"
- Section 3: "Threshold Derivation for Regression Testing"
- The running example assumes we first run 1000 experimental trials

### What's Missing

An equally valid workflow exists:

```
SLA/Contract → Given threshold p₀ → Test → Evaluate conformance
```

In this case:
- The threshold is a **normative claim**, not an empirical estimate
- There is no "baseline estimation" phase
- The statistical question changes from "Has it degraded?" to "Does it meet the requirement?"

### Recommendation

**Add Section 1.5: "Two Testing Paradigms"** that clearly distinguishes:

| Paradigm        | Threshold Source                   | Statistical Question                                  | Hypothesis Structure              |
|-----------------|------------------------------------|-------------------------------------------------------|-----------------------------------|
| **SLA-Driven**  | Contract, SLA, SLO, policy         | "Does the system meet the contractual requirement?"   | H₀: p ≥ p_SLA (meets requirement) |
| **Spec-Driven** | Empirical estimate from experiment | "Has the system degraded from its measured baseline?" | H₀: p ≥ p̂_exp (no degradation)   |

Both are legitimate applications of binomial proportion testing; they differ in the source and interpretation of the threshold.

---

## 2. The Experimental Basis Assumption

### Current State

Section 3.1 states:

> "The experiment established p̂_exp = 0.951 from n_exp = 1000 samples."

All formulas in Section 3 use `p̂_exp` and `n_exp`, implying an experimental basis always exists.

### The SLA-Driven Reality

In SLA-driven testing:
- There is no `p̂_exp` — the threshold comes from a contract
- There is no `n_exp` — no baseline experiment was conducted
- The threshold `p_SLA` is a **given**, not an estimate with associated uncertainty

### Statistical Implications

When the threshold is given (not estimated):

1. **No baseline uncertainty to propagate**: The threshold is assumed exact. We're testing whether the system meets a fixed target, not whether it differs from a noisy estimate.

2. **The hypothesis test simplifies**: We're conducting a one-sample proportion test against a known standard, not a two-sample comparison.

3. **Threshold derivation is unnecessary**: The SLA *is* the threshold. The question becomes: "How many samples do we need to verify conformance with acceptable confidence?"

### Recommendation

**Add Section 3.7: "Testing Against a Given Threshold (SLA-Driven)"**

Cover:
- The simplified hypothesis structure
- Sample size determination for SLA verification
- Confidence and power calculations when the null value is fixed
- Practical guidance for choosing sample sizes

---

## 3. The Hypothesis Testing Framework

### Current Formulation (Section 3.2)

$$H_0: p \geq p_{\text{exp}} \quad \text{(no degradation)}$$
$$H_1: p < p_{\text{exp}} \quad \text{(degradation has occurred)}$$

This frames the test as **detecting change from a baseline**.

### SLA-Driven Formulation

$$H_0: p \geq p_{\text{SLA}} \quad \text{(system meets SLA)}$$
$$H_1: p < p_{\text{SLA}} \quad \text{(system violates SLA)}$$

This frames the test as **verifying conformance to a requirement**.

### Why This Distinction Matters

1. **Burden of evidence**: In spec-driven testing, we're trying to detect a deviation from what was observed. In SLA-driven testing, we're trying to verify a claim.

2. **Risk allocation**: A spec-driven test asks "Can we reject the hypothesis that nothing changed?" An SLA-driven test asks "Can we reject the hypothesis that the system is compliant?"

3. **Interpretation of failure**: 
   - Spec-driven failure: "Evidence of degradation from baseline"
   - SLA-driven failure: "Evidence that SLA is not being met"

### Mathematical Structure is Identical

Importantly, the **mathematical structure** of the hypothesis test is the same for both paradigms:

$$H_0: p \geq p_{\text{threshold}}$$
$$H_1: p < p_{\text{threshold}}$$

What differs is the **textual interpretation** of what that threshold represents and what rejection means.

### Implementation Consequence: `thresholdOrigin` Should Influence Hypothesis Text

**Action Required**: PUnit's transparent statistics output should use the `thresholdOrigin` attribute to select appropriate hypothesis wording:

| `thresholdOrigin` | H₀ Text                           | H₁ Text                        |
|-------------------|-----------------------------------|--------------------------------|
| `SLA`             | "System meets SLA requirement"    | "System violates SLA"          |
| `SLO`             | "System meets SLO target"         | "System falls short of SLO"    |
| `POLICY`          | "System meets policy requirement" | "System violates policy"       |
| `EMPIRICAL`       | "No degradation from baseline"    | "Degradation from baseline"    |
| `UNSPECIFIED`     | "Success rate meets threshold"    | "Success rate below threshold" |

This makes the statistical output self-documenting: the hypothesis text reflects the *meaning* of the threshold, not just its numerical value.

### Recommendation

1. **Expand Section 3.2** to explicitly present both paradigms' interpretations
2. **Update PUnit implementation**: Modify `ConsoleExplanationRenderer` (or `StatisticalExplanationBuilder`) to use `thresholdOrigin` when constructing hypothesis text

---

## 4. Sample Size Determination for SLA Testing

### Current Coverage

Section 2.4 covers sample size for precision (estimation) and Section 5.4 covers sample size for power (spec-driven testing). Both assume an experimental context.

### What's Missing

For SLA-driven testing, the practitioner needs to answer:

> "I have a 99.5% uptime SLA. How many samples do I need to verify compliance with 95% confidence?"

This requires different framing:
- The threshold is fixed at 0.995
- We want to detect if the true rate is below this
- We need to specify: What degradation are we trying to detect? (minDetectableEffect)

### The Key Formula for SLA Testing

To detect with power (1-β) whether p < p_SLA by at least δ:

$$n = \left(\frac{z_\alpha \sqrt{p_{\text{SLA}}(1-p_{\text{SLA}})} + z_\beta \sqrt{(p_{\text{SLA}}-\delta)(1-(p_{\text{SLA}}-\delta))}}{\delta}\right)^2$$

### Reference Table Needed

| SLA Target | minDetectableEffect | Power 80% | Power 90% | Power 95% |
|------------|---------------------|-----------|-----------|-----------|
| 99.5%      | 0.5% (to 99.0%)     | 3,066     | 4,105     | 5,179     |
| 99.5%      | 1.0% (to 98.5%)     | 770       | 1,030     | 1,300     |
| 99.0%      | 1.0% (to 98.0%)     | 783       | 1,048     | 1,322     |
| 95.0%      | 2.0% (to 93.0%)     | 456       | 611       | 771       |
| 95.0%      | 5.0% (to 90.0%)     | 73        | 98        | 124       |

**Key insight**: High SLAs (99%+) with small detectable effects require very large samples. This is mathematically unavoidable.

### Recommendation

**Add Section 5.5: "Sample Size for SLA Verification"** with:
- Clear problem statement
- Worked examples
- Reference tables
- Discussion of the cost/sensitivity trade-off

---

## 5. The "minDetectableEffect" Concept

### Current State

The document doesn't explicitly discuss `minDetectableEffect` as a parameter, though the concept appears implicitly in power calculations (Section 5.3-5.4) as the "effect size δ".

### Why This Matters for SLA Testing

In SLA-driven testing, `minDetectableEffect` is **essential** for finite sample size calculation. Without it:

> "How many samples to verify p ≥ 99.5% with 95% confidence?"

has **no finite answer**. We must specify:

> "How many samples to verify p ≥ 99.5%, with 95% confidence that we'd detect a drop to 99.0%?"

### Recommendation

**Add Section 5.6: "The Role of Minimum Detectable Effect"**

Cover:
- Why minDetectableEffect is necessary for confidence-first testing
- How to choose an appropriate value (business-driven)
- The trade-off between detection sensitivity and sample cost
- Examples showing how different minDetectableEffect values impact required samples

---

## 6. The Three Operational Approaches (Section 6)

### Current State

Section 6 presents three approaches but frames them in terms of "experimental basis":

- Sample-size-first approach uses $\hat{p}_{\text{exp}}$
- Confidence-first approach uses $\hat{p}_{\text{exp}}$
- Threshold-first approach uses $\hat{p}_{\text{exp}}$

### Required Generalization

The three approaches apply to **both paradigms**. They should be presented with variants for each:

**For Spec-Driven Paradigm:**
- Use $\hat{p}_{\text{exp}}$ (estimated baseline)
- Account for baseline uncertainty

**For SLA-Driven Paradigm:**
- Use $p_{\text{SLA}}$ (given threshold)
- No baseline uncertainty to propagate

### Mathematical Formulation for SLA-Driven Paradigm

**Sample-Size-First Approach (Cost-Driven):**
- Given: $n_{\text{test}}$, $p_{\text{SLA}}$
- The test uses $p_{\text{threshold}} = p_{\text{SLA}}$ directly
- Implied confidence: depends on true p and sample size

**Confidence-First Approach (Risk-Driven):**
- Given: $p_{\text{SLA}}$, $\alpha$, $(1-\beta)$, $\delta$
- Compute: $n_{\text{test}}$ using power formula

**Threshold-First Approach (Direct Threshold):**
- Given: $n_{\text{test}}$, $p_{\text{threshold}} = p_{\text{SLA}}$
- Compute: Implied $\alpha$ (often high for small n)

### Recommendation

**Restructure Section 6** to present the SLA-driven paradigm first (simpler), then show how the spec-driven paradigm extends these formulas with baseline uncertainty.

---

## 7. The Running Example

### Current State

The running example (JSON generation use case) assumes an experimental workflow:
- 1000 experimental trials
- Baseline estimation
- Threshold derivation for regression testing

### Recommendation

**Add an SLA-driven running example first**, then present the spec-driven example as an extension:

**SLA Running Example**: A third-party payment API with a contractual 99.9% success rate SLA.

**Spec-Driven Running Example**: LLM JSON generation where no external threshold exists.

| Section          | SLA-Driven Example            | Spec-Driven Example                   |
|------------------|-------------------------------|---------------------------------------|
| Setup            | Payment API (contractual SLA) | LLM JSON generation (stochastic)      |
| Threshold source | Given: 99.9% per contract     | Estimated from 1000-sample experiment |
| Question         | "Does API meet SLA?"          | "Has quality degraded?"               |
| Samples          | 1000 verification samples     | 100 regression test samples           |

Following the ordering principle, present the SLA-driven example first as it requires fewer concepts to explain.

---

## 8. Interpretation of Results

### Current State

Section 7 covers reporting and interpretation, but the language assumes spec-driven context:

> "statistically significant degradation"

### SLA-Driven Language

For SLA testing, appropriate language includes:
- "statistically significant non-compliance"
- "evidence that SLA is not being met"
- "insufficient evidence to confirm SLA compliance"

### Recommendation

**Expand Section 7** with SLA-specific interpretation templates:

**SLA Test Pass:**
> "Based on n samples with an observed success rate of X%, we have Y% confidence that the system meets the Z% SLA requirement."

**SLA Test Fail:**
> "Based on n samples with an observed success rate of X%, we have statistically significant evidence (p-value = ...) that the system is not meeting the Z% SLA requirement."

---

## 9. Provenance and Auditability

### Current State

The document does not discuss documenting the source of thresholds.

### What's Missing

For SLA-driven testing, provenance is critical for audit:
- Which contract/SLA/policy defines this threshold?
- What is the document reference?
- When was the SLA established?

PUnit now supports `thresholdOrigin` and `contractRef` attributes. The Statistical Companion should explain how these integrate with the statistical framework.

### Recommendation

**Add Section 7.4: "Threshold Provenance"**

Cover:
- Why documenting threshold source matters
- The distinction between normative (SLA) and empirical (baseline) thresholds
- How PUnit's provenance attributes (`thresholdOrigin`, `contractRef`) support auditability

---

## 10. Transparent Statistics Mode

### Current State

Section 10 shows transparent output for a spec-driven test, referencing:
- "Baseline Reference" with spec source and empirical basis
- "Threshold derivation: Lower bound of 95% CI"

### What's Missing

For SLA-driven tests, the transparent output should show:
- Threshold source: SLA/SLO/Policy
- Contract reference
- No "baseline derivation" — the threshold is stated, not computed

### Recommendation

**Expand Section 10** with an SLA-driven example output:

```
HYPOTHESIS TEST
  H₀ (null):        True success rate π ≤ 0.995 (system does not meet SLA)
  H₁ (alternative): True success rate π > 0.995 (system meets SLA)
  Test type:        One-sided binomial proportion test

THRESHOLD SOURCE
  Type:             Service Level Agreement
  Reference:        Customer API SLA v2.1 §3.1
  Threshold:        99.5% (given, not derived)

OBSERVED DATA
  Sample size (n):     200
  Successes (k):       199
  Observed rate (p̂):   0.995

STATISTICAL INFERENCE
  Standard error:      SE = √(p̂(1-p̂)/n) = 0.005
  95% Confidence interval: [0.985, 1.000]
  
  Test statistic:      z = (p̂ - π₀) / √(π₀(1-π₀)/n)
                       z = (0.995 - 0.995) / 0.005
                       z = 0.00
  
  p-value:             P(Z > 0.00) = 0.500

VERDICT
  Result:              PASS
  Interpretation:      The observed success rate of 99.5% is consistent with 
                       the SLA requirement of 99.5%. The evidence neither 
                       confirms nor contradicts SLA compliance at this sample size.

  Caveat:              With n=200 samples at a 99.5% threshold, detection of 
                       small degradations is limited. Consider larger sample 
                       sizes for definitive SLA verification.
```

---

## Summary of Required Changes

| Section         | Change Type                                          | Priority | Notes                                         |
|-----------------|------------------------------------------------------|----------|-----------------------------------------------|
| 1.5 (new)       | Add "Two Testing Paradigms"                          | High     | SLA-driven first                              |
| 3.2             | Expand hypothesis framework for both paradigms       | High     | Same math, different interpretation           |
| 3.7 (new)       | Add "Testing Against a Given Threshold (SLA-Driven)" | High     | Before spec-driven derivation                 |
| 5.5 (new)       | Add "Sample Size for SLA Verification"               | High     |                                               |
| 5.6 (new)       | Add "Role of Minimum Detectable Effect"              | Medium   | Applies to both paradigms                     |
| 6               | Restructure for both paradigms                       | High     | SLA-driven first, then spec-driven extensions |
| 7               | Add SLA-specific interpretation language             | Medium   |                                               |
| 7.4 (new)       | Add "Threshold Provenance"                           | Medium   | thresholdOrigin, contractRef                  |
| 10              | Add SLA-driven transparent output example            | Medium   | Present before spec-driven example            |
| Running example | Add SLA-driven example                               | High     | Present first, simpler concepts               |

### Implementation Changes Required

| Component                                                       | Change                                                                                  | Priority |
|-----------------------------------------------------------------|-----------------------------------------------------------------------------------------|----------|
| `ConsoleExplanationRenderer` or `StatisticalExplanationBuilder` | Use `thresholdOrigin` to select appropriate hypothesis text in transparent stats output | High     |

### Terminology Checklist

When revising, ensure consistent usage:
- ✓ **Paradigm** for SLA-driven vs Spec-driven
- ✓ **Mode** for EXPLORE vs MEASURE experiments
- ✓ **Approach** for Sample-size-first, Confidence-first, Threshold-first

---

## Technical Note: GitHub Math Rendering

GitHub's markdown renderer uses MathJax but has specific quirks that cause ~10% of formulas to render incorrectly. When rewriting the STATISTICAL-COMPANION, apply these guidelines:

### Block Math (Display Equations)

**Correct**: Place `$$` on its own line with blank lines before and after:

```markdown
The sample proportion is:

$$
\hat{p} = \frac{k}{n}
$$

This is an unbiased estimator.
```

**Incorrect**: Inline `$$` without line breaks:
```markdown
The sample proportion is: $$\hat{p} = \frac{k}{n}$$
```

### Subscripts and Superscripts

**Correct**: Always use braces for multi-character subscripts:
```markdown
$p_{\text{SLA}}$, $n_{\text{test}}$, $\hat{p}_{\text{exp}}$
```

**Incorrect**: Unbraced multi-character subscripts:
```markdown
$p_SLA$, $n_test$  <!-- Renders incorrectly -->
```

### The `\text{}` Command

GitHub supports `\text{}` but be cautious:
- ✓ `$p_{\text{threshold}}$` — works
- ✓ `$\text{SE}(\hat{p})$` — works
- ✗ `$\text{some long text with spaces}$` — may fail; use outside math instead

### Underscores in Variable Names

Underscores trigger markdown italic formatting. Escape or use braces:
- ✓ `$p_{0}$` — uses braces
- ✓ `$p\_0$` — escaped underscore (less preferred)
- ✗ `$p_0$` — usually works but fragile in complex expressions

### Inline Math

Use single `$` with no spaces after opening or before closing:
- ✓ `where $n$ is the sample size`
- ✗ `where $ n $ is the sample size` — spaces cause issues

### Symbols That Need Escaping

| Symbol        | LaTeX                    | Notes                              |
|---------------|--------------------------|------------------------------------|
| Hat           | `\hat{p}`                | Works reliably                     |
| Overline      | `\bar{x}`                | Works reliably                     |
| Greek         | `\alpha`, `\beta`, `\pi` | Work reliably                      |
| Inequalities  | `\leq`, `\geq`           | Work; avoid `<=` `>=` in math mode |
| Approximately | `\approx`                | Works                              |
| Square root   | `\sqrt{}`                | Works                              |

### Test Before Committing

Preview the document on GitHub (or use a local MathJax-enabled viewer) before finalizing. Pay special attention to:
1. Complex fractions with subscripts
2. Equations with multiple levels of nesting
3. Inline math adjacent to punctuation

---

## Conclusion

The STATISTICAL-COMPANION is mathematically sound but narrowly focused on the spec-driven paradigm. With the additions outlined above, it will properly serve both paradigms:

1. **SLA-Driven Paradigm**: Organizations with contractual requirements who want to verify SLA/SLO compliance
2. **Spec-Driven Paradigm**: Organizations with empirical baselines who want to detect regression from measured performance

Both are legitimate, statistically rigorous use cases. The document should:
- Present the SLA-driven paradigm first (fewer concepts, gentler on-ramp)
- Show how the spec-driven paradigm extends the same statistical foundations with experimentation
- Use consistent terminology: "paradigm" for threshold source, "mode" for experiment types, "approach" for parameterization strategy

---

*Document prepared: 2026-01-13*
*Purpose: Guide revisions to STATISTICAL-COMPANION.md to support SLA-driven testing*

