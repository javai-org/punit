# Execution & Reporting Semantics

## 7.1 Experiment Reporting (No Verdict)

Experiments publish results without verdicts:

```
┌─────────────────────────────────────────────────────────────────┐
│ EXPERIMENT: JsonExperiments.measureJsonGenerationPerformance    │
├─────────────────────────────────────────────────────────────────┤
│ Use Case: usecase.json.generation                               │
│ Context: llm (model=gpt-4, temperature=0.7)                     │
│                                                                 │
│ Samples Executed: 200/200                                       │
│ Termination: COMPLETED                                          │
│ Elapsed: 87432ms                                                │
│ Tokens Consumed: 45230                                          │
│                                                                 │
│ Observations:                                                   │
│   Success Rate: 93.5% ± 1.7% (95% CI: [90.1%, 96.9%])           │
│                                                                 │
│   Failure Distribution:                                         │
│     invalidJson: 8 (4.0%)                                       │
│     missingRequiredField: 3 (1.5%)                              │
│     timeout: 2 (1.0%)                                           │
│                                                                 │
│ Baseline Generated: punit/baselines/usecase.json.generation/    │
│                                                                 │
│ NOTE: This is an experiment. No pass/fail verdict is produced.  │
└─────────────────────────────────────────────────────────────────┘
```

Key aspects:
- No "PASS" or "FAIL" status
- Statistical summaries with confidence intervals
- Clear note that this is informational only
- Pointer to generated baseline file

## 7.2 Probabilistic Test Reporting (With Verdict)

Conformance tests produce standard punit reports with spec provenance:

```
┌─────────────────────────────────────────────────────────────────┐
│ TEST: JsonConformanceTests.jsonGenerationMeetsSpec              │
├─────────────────────────────────────────────────────────────────┤
│ Status: PASSED                                                  │
│                                                                 │
│ Specification: usecase.json.generation:v3                       │
│ Use Case: usecase.json.generation                               │
│ Context: llm (model=gpt-4, temperature=0.7)                     │
│                                                                 │
│ Samples Executed: 100/100                                       │
│ Successes: 94                                                   │
│ Failures: 6                                                     │
│ Observed Pass Rate: 94.00%                                      │
│ Required Pass Rate: 90.00% (from spec)                          │
│ Termination: COMPLETED                                          │
│                                                                 │
│ Provenance:                                                     │
│   Spec: usecase.json.generation:v3                              │
│   Based on baseline: 2026-01-04T15:30:00Z.yaml                  │
│   Approved: 2026-01-04 by jane.engineer@example.com             │
└─────────────────────────────────────────────────────────────────┘
```

Key aspects:
- Clear PASS/FAIL verdict
- Provenance chain
- Threshold source clearly marked "(from spec)"

## 7.3 TestReporter Entry Keys

| Key                               | Description                              |
|-----------------------------------|------------------------------------------|
| `punit.mode`                      | `EXPERIMENT` or `CONFORMANCE`            |
| `punit.useCaseId`                 | The use case identifier                  |
| `punit.specId`                    | Specification ID (if spec-driven)        |
| `punit.specVersion`               | Specification version                    |
| `punit.baselineSource`            | Source baseline file path                |
| `punit.successCriteria`           | Success criteria expression              |
| `punit.context.backend`           | Backend identifier                       |
| `punit.context.*`                 | Backend-specific context parameters      |
| `punit.stats.successRate`         | Observed success rate                    |
| `punit.stats.confidenceInterval`  | 95% confidence interval (experiments)    |
| `punit.stats.failureDistribution` | JSON map of failure modes (experiments)  |
| `punit.baseline.outputPath`       | Path to generated baseline (experiments) |

---

*Previous: [Governance & Safety Mechanisms](./DOC-07-GOVERNANCE-SAFETY.md)*

*Next: [Extensibility Model](./DOC-09-EXTENSIBILITY-MODEL.md)*

*[Back to Table of Contents](./DOC-00-TOC.md)*
