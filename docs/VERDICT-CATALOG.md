# Verdict Catalog

A curated collection of archetypal PUnit verdicts, organized from simplest to most complex.

PUnit verdicts are available at two detail levels:

- **Summary** (default) — compact verdict with pass rate comparison, termination reason, and caveats
- **Verbose** (`-Dpunit.stats.detail=VERBOSE`) — full statistical analysis including hypothesis test formulation, confidence intervals, and inference workings

The numerical values shown below come from actual test runs and will vary between executions due to the probabilistic nature of the tests.

---

## 1. Pass

The simplest passing verdict. PUnit detects that the required number of successes has been reached and terminates early, skipping the remaining samples.

### Summary

```
═ TEST CONFIGURATION FOR: servicePassesComfortably ═══════════════════ PUnit ═

  Mode:             EXPLICIT THRESHOLD
  Use Case:         ShoppingBasketUseCase
  Threshold:        0.5000
  Samples:          50
  Intent:           VERIFICATION

```

```
═ VERDICT: PASS (VERIFICATION) ═══════════════════════════════════════ PUnit ═

  servicePassesComfortably(ShoppingBasketUseCase, String)
  Observed pass rate: 0.8333 (25/30) >= min pass rate: 0.5000
  Termination: Required pass rate already achieved
  Details: After 30 samples with 25 successes (0.8333), required min pass rate (25 successes) already met. Skipping 20 remaining samples.
  Elapsed: 11ms

```

### Verbose

```
═ STATISTICAL ANALYSIS FOR: servicePassesComfortablyTransparent(ShoppingB... ═

  HYPOTHESIS TEST
    H₀ (null):        True success rate π ≥ 0.5000 (success rate meets threshold)
    H₁ (alternative): True success rate π < 0.5000 (success rate below threshold)
    Test type:        One-sided binomial proportion test
  
  OBSERVED DATA
    Sample size (n):     35
    Successes (k):       25
    Observed rate (p̂):  0.7143
  
  BASELINE REFERENCE
    Source:              (inline threshold)
    Threshold:           0.5000 (Threshold specified directly in @ProbabilisticTest annotation)
  
  STATISTICAL INFERENCE
    Standard error:      SE = √(p̂(1-p̂)/n) = √(0.71 × 0.29 / 35) = 0.0764
    95% Confidence interval: [0.549, 0.837]
    
    Test statistic:      z = (p̂ - π₀) / √(π₀(1-π₀)/n)
                         z = (0.71 - 0.50) / √(0.50 × 0.50 / 35)
                         z = 2.54
    
    p-value:             P(Z > 2.54) = 0.006
  
  VERDICT
    Result:              PASS
    Interpretation:      The observed success rate of 0.7143 meets the required
                         threshold of 0.5000. The test passes.
                         
    Caveat:              With n=35 samples, subtle performance changes may not
                         be detectable. For higher sensitivity, consider
                         increasing sample size.
    Caveat:              Using inline threshold (no baseline spec). For
                         statistically-derived thresholds with confidence
                         intervals, run a MEASURE experiment first.

```

---

## 2. Fail with early termination

When PUnit determines that the threshold can no longer be reached — even if every remaining sample succeeds — it terminates early and reports the impossibility analysis.

### Summary

```
═ TEST CONFIGURATION FOR: failsEarlyWhenThresholdUnreachable ═════════ PUnit ═

  Mode:             EXPLICIT THRESHOLD
  Use Case:         ShoppingBasketUseCase
  Threshold:        0.9500
  Samples:          30
  Intent:           SMOKE

```

```
═ VERDICT: FAIL (SMOKE) ══════════════════════════════════════════════ PUnit ═

  failsEarlyWhenThresholdUnreachable(ShoppingBasketUseCase, String)
  Observed pass rate: 0.0000 (0/2) < min pass rate: 0.9500
  Termination: Cannot reach required pass rate
  Details: After 2 samples with 0 successes, maximum possible successes (0 + 28 = 28) is less than required (29)
  Analysis: Needed 29 successes, maximum possible is 28
  Elapsed: 1ms

```

### Verbose

```
═ STATISTICAL ANALYSIS FOR: failsEarlyWhenThresholdUnreachableTransparent... ═

  HYPOTHESIS TEST
    H₀ (null):        True success rate π ≥ 0.9500 (observed rate meets threshold)
    H₁ (alternative): True success rate π < 0.9500 (observed rate below threshold)
    Test type:        One-sided binomial proportion test
  
  OBSERVED DATA
    Sample size (n):     2
    Successes (k):       0
    Observed rate (p̂):  0.0000
  
  BASELINE REFERENCE
    Source:              (inline threshold)
    Threshold:           0.9500 (Threshold specified directly in @ProbabilisticTest annotation)
  
  STATISTICAL INFERENCE
    Standard error:      SE = √(p̂(1-p̂)/n) = √(0.00 × 1.00 / 2) = 0.0000
    95% Confidence interval: [0.000, 0.658]
    
    Test statistic:      z = (p̂ - π₀) / √(π₀(1-π₀)/n)
                         z = (0.00 - 0.95) / √(0.95 × 0.05 / 2)
                         z = -6.16
    
    p-value:             P(Z > -6.16) = 1.000
  
  VERDICT
    Result:              FAIL
    Interpretation:      The observed success rate of 0.0000 falls below the
                         required threshold of 0.9500. The observed rate does
                         not meet the threshold.
                         
    Caveat:              Small sample size (n=2). Statistical conclusions
                         should be interpreted with caution. Consider
                         increasing sample size for more reliable results.
    Caveat:              Zero success rate observed. This indicates a
                         fundamental failure that may warrant investigation
                         before further testing.
    Caveat:              Using inline threshold (no baseline spec). For
                         statistically-derived thresholds with confidence
                         intervals, run a MEASURE experiment first.

```

---

## 3. Fail

A failing verdict. The verbose variant adds the HYPOTHESIS TEST and STATISTICAL INFERENCE workings.

### Summary

```
═ TEST CONFIGURATION FOR: serviceFailsNarrowlyTransparent ════════════ PUnit ═

  Mode:             EXPLICIT THRESHOLD
  Use Case:         ShoppingBasketUseCase
  Threshold:        0.9500
  Samples:          50
  Intent:           SMOKE

```

```
═ STATISTICAL ANALYSIS FOR: serviceFailsNarrowlyTransparent(ShoppingBaske... ═

  OBSERVED DATA
    Sample size (n):     3
    Successes (k):       0
    Observed rate (p̂):  0.0000
  
  BASELINE REFERENCE
    Source:              (inline threshold)
    Threshold:           0.9500 (Threshold specified directly in @ProbabilisticTest annotation)
  
  VERDICT
    Result:              FAIL
    Interpretation:      The observed success rate of 0.0000 falls below the
                         required threshold of 0.9500. The observed rate does
                         not meet the threshold.
                         
    Caveat:              Small sample size (n=3). Statistical conclusions
                         should be interpreted with caution. Consider
                         increasing sample size for more reliable results.
    Caveat:              Zero success rate observed. This indicates a
                         fundamental failure that may warrant investigation
                         before further testing.
    Caveat:              Using inline threshold (no baseline spec). For
                         statistically-derived thresholds with confidence
                         intervals, run a MEASURE experiment first.

```

### Verbose

```
═ STATISTICAL ANALYSIS FOR: serviceFailsNarrowlyTransparent(ShoppingBaske... ═

  HYPOTHESIS TEST
    H₀ (null):        True success rate π ≥ 0.9500 (observed rate meets threshold)
    H₁ (alternative): True success rate π < 0.9500 (observed rate below threshold)
    Test type:        One-sided binomial proportion test
  
  OBSERVED DATA
    Sample size (n):     5
    Successes (k):       2
    Observed rate (p̂):  0.4000
  
  BASELINE REFERENCE
    Source:              (inline threshold)
    Threshold:           0.9500 (Threshold specified directly in @ProbabilisticTest annotation)
  
  STATISTICAL INFERENCE
    Standard error:      SE = √(p̂(1-p̂)/n) = √(0.40 × 0.60 / 5) = 0.2191
    95% Confidence interval: [0.118, 0.769]
    
    Test statistic:      z = (p̂ - π₀) / √(π₀(1-π₀)/n)
                         z = (0.40 - 0.95) / √(0.95 × 0.05 / 5)
                         z = -5.64
    
    p-value:             P(Z > -5.64) = 1.000
  
  VERDICT
    Result:              FAIL
    Interpretation:      The observed success rate of 0.4000 falls below the
                         required threshold of 0.9500. The observed rate does
                         not meet the threshold.
                         
    Caveat:              Small sample size (n=5). Statistical conclusions
                         should be interpreted with caution. Consider
                         increasing sample size for more reliable results.
    Caveat:              Using inline threshold (no baseline spec). For
                         statistically-derived thresholds with confidence
                         intervals, run a MEASURE experiment first.

```

---

## 4. Budget exhaustion

When a cost budget (time or tokens) runs out before all samples complete, PUnit reports how many samples were executed and the pass rate at termination. The default budget-exhaustion behaviour is FAIL — the test fails regardless of the partial results.

### Summary

```
═ TEST CONFIGURATION FOR: failsWhenBudgetRunsOut ═════════════════════ PUnit ═

  Mode:             EXPLICIT THRESHOLD
  Use Case:         ShoppingBasketUseCase
  Threshold:        0.5000
  Samples:          50
  Intent:           VERIFICATION

```

```
═ VERDICT: FAIL (VERIFICATION) ═══════════════════════════════════════ PUnit ═

  failsWhenBudgetRunsOut(ShoppingBasketUseCase, String)
  Samples executed: 5 of 50 (budget exhausted before completion)
  Pass rate at termination: 1.0000 (5/5), required: 0.5000
  Termination: Method token budget exhausted
  Details: Method token budget exhausted: 1000 tokens >= 1000 budget
  Elapsed: 4ms

```

An alternative budget behaviour, `EVALUATE_PARTIAL`, evaluates the partial results against the threshold as if the test had completed normally. This can produce either a pass or fail depending on the partial results.

```
═ VERDICT: PASS (VERIFICATION) ═══════════════════════════════════════ PUnit ═

  evaluatesPartialResultsOnBudgetPass(ShoppingBasketUseCase, String)
  Observed pass rate: 0.6000 (3/5) >= min pass rate: 0.5000
  Termination: Method token budget exhausted
  Details: Method token budget exhausted: 1000 tokens >= 1000 budget
  Elapsed: 4ms

```

### Verbose

```
═ STATISTICAL ANALYSIS FOR: failsWhenBudgetRunsOutTransparent(ShoppingBas... ═

  HYPOTHESIS TEST
    H₀ (null):        True success rate π ≥ 0.5000 (success rate meets threshold)
    H₁ (alternative): True success rate π < 0.5000 (success rate below threshold)
    Test type:        One-sided binomial proportion test
  
  OBSERVED DATA
    Sample size (n):     5
    Successes (k):       4
    Observed rate (p̂):  0.8000
  
  BASELINE REFERENCE
    Source:              (inline threshold)
    Threshold:           0.5000 (Threshold specified directly in @ProbabilisticTest annotation)
  
  STATISTICAL INFERENCE
    Standard error:      SE = √(p̂(1-p̂)/n) = √(0.80 × 0.20 / 5) = 0.1789
    95% Confidence interval: [0.376, 0.964]
    
    Test statistic:      z = (p̂ - π₀) / √(π₀(1-π₀)/n)
                         z = (0.80 - 0.50) / √(0.50 × 0.50 / 5)
                         z = 1.34
    
    p-value:             P(Z > 1.34) = 0.090
  
  VERDICT
    Result:              FAIL
    Interpretation:      The observed success rate of 0.8000 falls below the
                         required threshold of 0.5000. This suggests the system
                         is not meeting its expected performance level.
                         
    Caveat:              Small sample size (n=5). Statistical conclusions
                         should be interpreted with caution. Consider
                         increasing sample size for more reliable results.
    Caveat:              Using inline threshold (no baseline spec). For
                         statistically-derived thresholds with confidence
                         intervals, run a MEASURE experiment first.

```

---

## 5. SLA compliance with contract provenance

When a threshold originates from an SLA, SLO, or policy, PUnit frames the hypothesis test accordingly and includes a THRESHOLD PROVENANCE section tracing the threshold back to its source. The hypothesis text adapts to the threshold origin (e.g. "system meets SLA requirement" vs "system meets SLO target" vs "system meets policy requirement").

### Summary

```
═ TEST CONFIGURATION FOR: slaPassShowsComplianceHypothesis ═══════════ PUnit ═

  Mode:             SLA-DRIVEN
  Use Case:         ShoppingBasketUseCase
  Threshold:        0.5000 (SLA)
  Contract:         Acme Payment SLA v3.2 §4.1
  Samples:          50
  Intent:           VERIFICATION

```

```
═ STATISTICAL ANALYSIS FOR: slaPassShowsComplianceHypothesis(ShoppingBask... ═

  OBSERVED DATA
    Sample size (n):     35
    Successes (k):       25
    Observed rate (p̂):  0.7143
  
  BASELINE REFERENCE
    Source:              (inline threshold)
    Threshold:           0.5000 (Threshold specified directly in @ProbabilisticTest annotation)
  
  VERDICT
    Result:              PASS
    Interpretation:      The observed success rate of 0.7143 meets the required
                         threshold of 0.5000. The system meets its SLA
                         requirement.
                         
    Caveat:              With n=35 samples, subtle performance changes may not
                         be detectable. For higher sensitivity, consider
                         increasing sample size.
  
  THRESHOLD PROVENANCE
    Threshold origin:    SLA
    Contract ref:        Acme Payment SLA v3.2 §4.1

```

### Verbose

```
═ STATISTICAL ANALYSIS FOR: slaPassShowsComplianceHypothesis(ShoppingBask... ═

  HYPOTHESIS TEST
    H₀ (null):        True success rate π ≥ 0.5000 (system meets SLA requirement)
    H₁ (alternative): True success rate π < 0.5000 (system violates SLA)
    Test type:        One-sided binomial proportion test
  
  OBSERVED DATA
    Sample size (n):     32
    Successes (k):       25
    Observed rate (p̂):  0.7813
  
  BASELINE REFERENCE
    Source:              (inline threshold)
    Threshold:           0.5000 (Threshold specified directly in @ProbabilisticTest annotation)
  
  STATISTICAL INFERENCE
    Standard error:      SE = √(p̂(1-p̂)/n) = √(0.78 × 0.22 / 32) = 0.0731
    95% Confidence interval: [0.612, 0.890]
    
    Test statistic:      z = (p̂ - π₀) / √(π₀(1-π₀)/n)
                         z = (0.78 - 0.50) / √(0.50 × 0.50 / 32)
                         z = 3.18
    
    p-value:             P(Z > 3.18) = 0.001
  
  VERDICT
    Result:              PASS
    Interpretation:      The observed success rate of 0.7813 meets the required
                         threshold of 0.5000. The system meets its SLA
                         requirement.
                         
    Caveat:              With n=32 samples, subtle performance changes may not
                         be detectable. For higher sensitivity, consider
                         increasing sample size.
  
  THRESHOLD PROVENANCE
    Threshold origin:    SLA
    Contract ref:        Acme Payment SLA v3.2 §4.1

```

---

## 6. Compliance undersized

When the sample size is too small to provide meaningful statistical evidence of compliance with a high-reliability SLA target, PUnit warns that a passing result is only a smoke-test-level observation. A failing result remains a reliable indication of non-conformance.

### Summary

```
═ TEST CONFIGURATION FOR: complianceUndersizedSmokeTestOnly ══════════ PUnit ═

  Mode:             SLA-DRIVEN
  Use Case:         ShoppingBasketUseCase
  Threshold:        0.9999 (SLA)
  Contract:         Acme Payment SLA v3.2 §4.1
  Samples:          50
  Intent:           SMOKE

```

```
═ STATISTICAL ANALYSIS FOR: complianceUndersizedSmokeTestOnly(ShoppingBas... ═

  OBSERVED DATA
    Sample size (n):     1
    Successes (k):       0
    Observed rate (p̂):  0.0000
  
  BASELINE REFERENCE
    Source:              (inline threshold)
    Threshold:           0.9999 (Threshold specified directly in @ProbabilisticTest annotation)
  
  VERDICT
    Result:              FAIL
    Interpretation:      The observed success rate of 0.0000 falls below the
                         required threshold of 0.9999. The observed rate is
                         inconsistent with the target.
                         
    Caveat:              Small sample size (n=1). Statistical conclusions
                         should be interpreted with caution. Consider
                         increasing sample size for more reliable results.
    Caveat:              Zero success rate observed. This indicates a
                         fundamental failure that may warrant investigation
                         before further testing.
    Caveat:              Warning: sample not sized for compliance verification.
                         With n=1 and target of 0.9999, even zero failures
                         would not provide sufficient statistical evidence of
                         compliance (α=0.050). A PASS at this sample size is a
                         smoke-test-level observation, not a compliance
                         determination. Note: a FAIL verdict remains a reliable
                         indication of non-conformance.
    Caveat:              Sample not sized for verification (N=1, need 27053).
                         This is a smoke-test-level observation, not a
                         compliance determination.
  
  THRESHOLD PROVENANCE
    Threshold origin:    SLA
    Contract ref:        Acme Payment SLA v3.2 §4.1

```

### Verbose

```
═ STATISTICAL ANALYSIS FOR: complianceUndersizedSmokeTestOnly(ShoppingBas... ═

  HYPOTHESIS TEST
    H₀ (null):        True success rate π ≥ 0.9999 (observed rate consistent with target)
    H₁ (alternative): True success rate π < 0.9999 (observed rate inconsistent with target)
    Test type:        One-sided binomial proportion test
  
  OBSERVED DATA
    Sample size (n):     4
    Successes (k):       3
    Observed rate (p̂):  0.7500
  
  BASELINE REFERENCE
    Source:              (inline threshold)
    Threshold:           0.9999 (Threshold specified directly in @ProbabilisticTest annotation)
  
  STATISTICAL INFERENCE
    Standard error:      SE = √(p̂(1-p̂)/n) = √(0.75 × 0.25 / 4) = 0.2165
    95% Confidence interval: [0.301, 0.954]
    
    Test statistic:      z = (p̂ - π₀) / √(π₀(1-π₀)/n)
                         z = (0.75 - 1.00) / √(1.00 × 0.00 / 4)
                         z = -49.98
    
    p-value:             P(Z > -49.98) = 1.000
  
  VERDICT
    Result:              FAIL
    Interpretation:      The observed success rate of 0.7500 falls below the
                         required threshold of 0.9999. The observed rate is
                         inconsistent with the target.
                         
    Caveat:              Small sample size (n=4). Statistical conclusions
                         should be interpreted with caution. Consider
                         increasing sample size for more reliable results.
    Caveat:              Warning: sample not sized for compliance verification.
                         With n=4 and target of 0.9999, even zero failures
                         would not provide sufficient statistical evidence of
                         compliance (α=0.050). A PASS at this sample size is a
                         smoke-test-level observation, not a compliance
                         determination. Note: a FAIL verdict remains a reliable
                         indication of non-conformance.
    Caveat:              Sample not sized for verification (N=4, need 27053).
                         This is a smoke-test-level observation, not a
                         compliance determination.
  
  THRESHOLD PROVENANCE
    Threshold origin:    SLA
    Contract ref:        Acme Payment SLA v3.2 §4.1

```

---

## 7. Covariate misalignment

When the test runs under conditions that differ from the baseline (e.g. different time of day, weekday vs weekend), PUnit emits a BASELINE FOUND banner listing the misaligned covariates before the test runs. This also appears as a caveat in the verdict.

### Summary

```
═ BASELINE FOUND FOR USE CASE: ShoppingBasketUseCase ═════════════════ PUnit ═

  Baseline file:    ShoppingBasketUseCase-9be9-a7ef-06d4-0aa5-a769.yaml
  
  Please note, the following covariates do not match the baseline:
    - time_of_day: baseline=06:13-06:13 Europe/London, test=07:06-07:06 Europe/Zurich
  
  Statistical comparison may be less reliable.
  Consider running a new MEASURE experiment under current conditions.

```

```
═ TEST CONFIGURATION FOR: temporalMismatchShowsCaveatTransparent ═════ PUnit ═

  Mode:             SPEC-DRIVEN
  Spec:             ShoppingBasketUseCase
  Threshold:        0.5656 (derived from baseline)
  Samples:          50
  Intent:           VERIFICATION

```

```
═ STATISTICAL ANALYSIS FOR: temporalMismatchShowsCaveatTransparent(Shoppi... ═

  OBSERVED DATA
    Sample size (n):     33
    Successes (k):       11
    Observed rate (p̂):  0.3333
  
  BASELINE REFERENCE
    Source:              ShoppingBasketUseCase-9be9-a7ef-06d4-0aa5-a769.yaml (generated 2026-01-23)
    Empirical basis:     1000 samples, 596 successes (0.5960)
    Threshold derivation:Lower bound of 95% CI = 0.5703, min pass rate = 0.5656
  
  VERDICT
    Result:              FAIL
    Interpretation:      The observed success rate of 0.3333 falls below the
                         required threshold of 0.5656. This suggests the system
                         is not meeting its expected performance level.
                         
    Caveat:              Covariate misalignment detected: the test conditions
                         differ from the baseline. Misaligned covariates:
                         time_of_day (baseline=06:13-06:13 Europe/London,
                         test=07:06-07:06 Europe/Zurich). Statistical
                         comparison may be less reliable.
    Caveat:              With n=33 samples, subtle performance changes may not
                         be detectable. For higher sensitivity, consider
                         increasing sample size.

```

### Verbose

```
═ STATISTICAL ANALYSIS FOR: temporalMismatchShowsCaveatTransparent(Shoppi... ═

  HYPOTHESIS TEST
    H₀ (null):        True success rate π ≥ 0.5656 (success rate meets threshold)
    H₁ (alternative): True success rate π < 0.5656 (success rate below threshold)
    Test type:        One-sided binomial proportion test
  
  OBSERVED DATA
    Sample size (n):     33
    Successes (k):       11
    Observed rate (p̂):  0.3333
  
  BASELINE REFERENCE
    Source:              ShoppingBasketUseCase-9be9-a7ef-06d4-0aa5-a769.yaml (generated 2026-01-23)
    Empirical basis:     1000 samples, 596 successes (0.5960)
    Threshold derivation:Lower bound of 95% CI = 0.5703, min pass rate = 0.5656
  
  STATISTICAL INFERENCE
    Standard error:      SE = √(p̂(1-p̂)/n) = √(0.33 × 0.67 / 33) = 0.0821
    95% Confidence interval: [0.198, 0.504]
    
    Test statistic:      z = (p̂ - π₀) / √(π₀(1-π₀)/n)
                         z = (0.33 - 0.57) / √(0.57 × 0.43 / 33)
                         z = -2.69
    
    p-value:             P(Z > -2.69) = 0.996
  
  VERDICT
    Result:              FAIL
    Interpretation:      The observed success rate of 0.3333 falls below the
                         required threshold of 0.5656. This suggests the system
                         is not meeting its expected performance level.
                         
    Caveat:              Covariate misalignment detected: the test conditions
                         differ from the baseline. Misaligned covariates:
                         time_of_day (baseline=06:13-06:13 Europe/London,
                         test=07:06-07:06 Europe/Zurich). Statistical
                         comparison may be less reliable.
    Caveat:              With n=33 samples, subtle performance changes may not
                         be detectable. For higher sensitivity, consider
                         increasing sample size.

```

---

## 8. Intent-governed verdicts (VERIFICATION vs SMOKE)

PUnit supports two test intents: **VERIFICATION** (evidential — enforces a statistical feasibility gate before any samples execute) and **SMOKE** (sentinel — permits undersized configurations and uses softened language). The intent appears in the verdict banner and influences hypothesis framing and caveats.

A VERIFICATION test with insufficient samples will fail at configuration time (not shown here — see the user guide for the infeasibility message). The examples below show how intent affects the verdict for tests that *do* execute.

### Summary

### Verification (sized, normative)

```
═ TEST CONFIGURATION FOR: verificationPassSized ══════════════════════ PUnit ═

  Mode:             SLA-DRIVEN
  Use Case:         ShoppingBasketUseCase
  Threshold:        0.5000 (SLA)
  Contract:         Acme Payment SLA v3.2 §4.1
  Samples:          50
  Intent:           VERIFICATION

```

```
═ STATISTICAL ANALYSIS FOR: verificationPassSized(ShoppingBasketUseCase, ... ═

  OBSERVED DATA
    Sample size (n):     44
    Successes (k):       25
    Observed rate (p̂):  0.5682
  
  BASELINE REFERENCE
    Source:              (inline threshold)
    Threshold:           0.5000 (Threshold specified directly in @ProbabilisticTest annotation)
  
  VERDICT
    Result:              PASS
    Interpretation:      The observed success rate of 0.5682 meets the required
                         threshold of 0.5000. The system meets its SLA
                         requirement.
                         
    Caveat:              With n=44 samples, subtle performance changes may not
                         be detectable. For higher sensitivity, consider
                         increasing sample size.
  
  THRESHOLD PROVENANCE
    Threshold origin:    SLA
    Contract ref:        Acme Payment SLA v3.2 §4.1

```

When a test is configured with `intent = SMOKE` and a normative threshold origin (SLA, SLO, or POLICY), PUnit uses softened language — "consistent with the target" rather than "meets SLA requirement". If the sample is undersized for verification, a sizing note appears.

### Smoke undersized (normative)

```
═ TEST CONFIGURATION FOR: smokeUndersizedNormative ═══════════════════ PUnit ═

  Mode:             SLA-DRIVEN
  Use Case:         ShoppingBasketUseCase
  Threshold:        0.9500 (SLA)
  Samples:          30
  Intent:           SMOKE

```

```
═ STATISTICAL ANALYSIS FOR: smokeUndersizedNormative(ShoppingBasketUseCas... ═

  OBSERVED DATA
    Sample size (n):     12
    Successes (k):       10
    Observed rate (p̂):  0.8333
  
  BASELINE REFERENCE
    Source:              (inline threshold)
    Threshold:           0.9500 (Threshold specified directly in @ProbabilisticTest annotation)
  
  VERDICT
    Result:              FAIL
    Interpretation:      The observed success rate of 0.8333 falls below the
                         required threshold of 0.9500. The observed rate is
                         inconsistent with the target.
                         
    Caveat:              Small sample size (n=12). Statistical conclusions
                         should be interpreted with caution. Consider
                         increasing sample size for more reliable results.
    Caveat:              Warning: sample not sized for compliance verification.
                         With n=12 and target of 0.9500, even zero failures
                         would not provide sufficient statistical evidence of
                         compliance (α=0.050). A PASS at this sample size is a
                         smoke-test-level observation, not a compliance
                         determination. Note: a FAIL verdict remains a reliable
                         indication of non-conformance.
    Caveat:              Sample not sized for verification (N=12, need 52).
                         This is a smoke-test-level observation, not a
                         compliance determination.
  
  THRESHOLD PROVENANCE
    Threshold origin:    SLA

```

If the SMOKE sample is already large enough for verification, PUnit suggests upgrading to `intent = VERIFICATION` for evidential strength.

### Smoke sized with hint (normative)

```
═ STATISTICAL ANALYSIS FOR: smokeSizedNormativeHint(ShoppingBasketUseCase... ═

  OBSERVED DATA
    Sample size (n):     33
    Successes (k):       25
    Observed rate (p̂):  0.7576
  
  BASELINE REFERENCE
    Source:              (inline threshold)
    Threshold:           0.5000 (Threshold specified directly in @ProbabilisticTest annotation)
  
  VERDICT
    Result:              PASS
    Interpretation:      The observed success rate of 0.7576 meets the required
                         threshold of 0.5000. The observed rate is consistent
                         with the target.
                         
    Caveat:              With n=33 samples, subtle performance changes may not
                         be detectable. For higher sensitivity, consider
                         increasing sample size.
    Caveat:              Sample is sized for verification. Consider setting
                         intent = VERIFICATION for stronger statistical
                         guarantees.
  
  THRESHOLD PROVENANCE
    Threshold origin:    SLA

```

### Verbose

```
═ STATISTICAL ANALYSIS FOR: verificationPassSized(ShoppingBasketUseCase, ... ═

  HYPOTHESIS TEST
    H₀ (null):        True success rate π ≥ 0.5000 (system meets SLA requirement)
    H₁ (alternative): True success rate π < 0.5000 (system violates SLA)
    Test type:        One-sided binomial proportion test
  
  OBSERVED DATA
    Sample size (n):     40
    Successes (k):       25
    Observed rate (p̂):  0.6250
  
  BASELINE REFERENCE
    Source:              (inline threshold)
    Threshold:           0.5000 (Threshold specified directly in @ProbabilisticTest annotation)
  
  STATISTICAL INFERENCE
    Standard error:      SE = √(p̂(1-p̂)/n) = √(0.63 × 0.38 / 40) = 0.0765
    95% Confidence interval: [0.470, 0.758]
    
    Test statistic:      z = (p̂ - π₀) / √(π₀(1-π₀)/n)
                         z = (0.63 - 0.50) / √(0.50 × 0.50 / 40)
                         z = 1.58
    
    p-value:             P(Z > 1.58) = 0.057
  
  VERDICT
    Result:              PASS
    Interpretation:      The observed success rate of 0.6250 meets the required
                         threshold of 0.5000. The system meets its SLA
                         requirement.
                         
    Caveat:              With n=40 samples, subtle performance changes may not
                         be detectable. For higher sensitivity, consider
                         increasing sample size.
  
  THRESHOLD PROVENANCE
    Threshold origin:    SLA
    Contract ref:        Acme Payment SLA v3.2 §4.1

```

```
═ STATISTICAL ANALYSIS FOR: smokeUndersizedNormative(ShoppingBasketUseCas... ═

  HYPOTHESIS TEST
    H₀ (null):        True success rate π ≥ 0.9500 (observed rate consistent with target)
    H₁ (alternative): True success rate π < 0.9500 (observed rate inconsistent with target)
    Test type:        One-sided binomial proportion test
  
  OBSERVED DATA
    Sample size (n):     9
    Successes (k):       7
    Observed rate (p̂):  0.7778
  
  BASELINE REFERENCE
    Source:              (inline threshold)
    Threshold:           0.9500 (Threshold specified directly in @ProbabilisticTest annotation)
  
  STATISTICAL INFERENCE
    Standard error:      SE = √(p̂(1-p̂)/n) = √(0.78 × 0.22 / 9) = 0.1386
    95% Confidence interval: [0.453, 0.937]
    
    Test statistic:      z = (p̂ - π₀) / √(π₀(1-π₀)/n)
                         z = (0.78 - 0.95) / √(0.95 × 0.05 / 9)
                         z = -2.37
    
    p-value:             P(Z > -2.37) = 0.991
  
  VERDICT
    Result:              FAIL
    Interpretation:      The observed success rate of 0.7778 falls below the
                         required threshold of 0.9500. The observed rate is
                         inconsistent with the target.
                         
    Caveat:              Small sample size (n=9). Statistical conclusions
                         should be interpreted with caution. Consider
                         increasing sample size for more reliable results.
    Caveat:              Warning: sample not sized for compliance verification.
                         With n=9 and target of 0.9500, even zero failures
                         would not provide sufficient statistical evidence of
                         compliance (α=0.050). A PASS at this sample size is a
                         smoke-test-level observation, not a compliance
                         determination. Note: a FAIL verdict remains a reliable
                         indication of non-conformance.
    Caveat:              Sample not sized for verification (N=9, need 52). This
                         is a smoke-test-level observation, not a compliance
                         determination.
  
  THRESHOLD PROVENANCE
    Threshold origin:    SLA

```

