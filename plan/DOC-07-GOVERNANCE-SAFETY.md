# Governance & Safety Mechanisms

The framework actively guides developers toward empirical threshold selection and prevents misuse.

## 6.1 Discouraging Arbitrary Thresholds

### 6.1.1 Warning on Inline Thresholds Without Prior Baseline

```
⚠️ WARNING: @ProbabilisticTest for 'validateEmail' uses minPassRate=0.95 
   without an empirical baseline. This threshold may be arbitrary.
   
   Recommendation: Run an @Experiment first to establish empirical behavior,
   then create a specification based on observed results.
```

This is a **warning**, not an error. The test still executes.

### 6.1.2 Spec-First Guidance in Documentation

Documentation and examples consistently demonstrate the spec-driven pattern as primary.

## 6.2 Surfacing Insufficient Empirical Data

```
⚠️ WARNING: Specification 'usecase.json.gen:v3' is based on baseline with
   only 50 samples. Confidence interval for success rate is wide: [0.82, 0.98].
   
   Recommendation: Run additional experiments to narrow the confidence interval.
```

## 6.3 Preventing Blind Acceptance of Poor Results

Specifications **require** explicit approval metadata:

```yaml
approvedAt: 2026-01-04T16:00:00Z
approvedBy: jane.engineer@example.com
approvalNotes: "Approved after review of Jan 4 experiment results"
```

Missing approval metadata causes an **error** that fails the test.

## 6.4 Baseline-Spec Drift Detection

```
⚠️ WARNING: Observed success rate (0.78) is significantly lower than
   baseline (0.935) for specification 'usecase.json.gen:v3'.
   
   This may indicate:
   - System regression
   - Environment differences
   - Baseline that no longer reflects current behavior
```

## 6.5 Warnings vs Errors Summary

| Condition                                  | Severity | Behavior                             |
|--------------------------------------------|----------|--------------------------------------|
| Inline threshold without baseline          | Warning  | Test executes with warning           |
| Baseline with insufficient samples         | Warning  | Spec loads with warning              |
| Spec without approval metadata             | Error    | Test fails                           |
| Spec references missing baseline           | Error    | Test fails                           |
| Observed rate significantly below baseline | Warning  | Test may still pass if threshold met |
| Use case ID not found                      | Error    | Test fails                           |

---

*Previous: [Data Flow](./DOC-06-DATA-FLOW.md)*

*Next: [Execution & Reporting Semantics](./DOC-08-EXECUTION-REPORTING.md)*

*[Back to Table of Contents](./DOC-00-TOC.md)*
