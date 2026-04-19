# PUnit — Verdict Console Output Restructure

## 1. Purpose

Restructure the verdict console output so that a developer reading it can
**diagnose the situation without consulting any other source** — no spec
files, no test source, no framework internals.

Today the output identifies *that* a problem occurred but omits the
information needed to understand *why*. This document addresses two
specific deficiencies observed in the INCONCLUSIVE (covariate misalignment)
verdict, though the principles apply to all verdict categories.

---

## 2. Problem Statement

### 2.1 Header contradicts body

The verdict header currently reads:

```
═ VERDICT: INCONCLUSIVE (VERIFICATION) ═
```

The parenthetical `(VERIFICATION)` is the `TestIntent` — the test's
evidential category — but it reads as the *reason* for the verdict.
A user scanning the header gets "inconclusive because of verification"
and must reconcile that with the body:

```
Verdict:             Inconclusive — covariate misalignment
```

The header and body disagree on what the parenthetical means.

**Origin:** `ResultPublisher.printConsoleSummary()` constructs the header
as `"VERDICT: " + verdictLabel + " (" + intentLabel + ")"`. The intent
label (`VERIFICATION` / `SMOKE`) occupies the most prominent explanatory
position but carries no diagnostic value for the reader at that moment.

### 2.2 Covariate misalignment asserted but not shown

The output states *"covariate misalignment"* but never shows:

- Which covariates differ
- What the baseline values are
- What the observed (test-time) values are

The information exists: `ProbabilisticTestVerdict.covariateStatus()`
contains a list of `Misalignment` records, each with `covariateKey`,
`baselineValue`, and `testValue`. It is computed, stored, and then
discarded at render time.

A developer seeing "covariate misalignment" must:

1. Open the spec YAML and find the `covariates:` block
2. Read their test setup to determine the runtime covariate values
3. Manually compare the two

This defeats the purpose of the framework detecting the misalignment.

---

## 3. Requirements

### R1 — Header must carry the verdict reason, not the test intent

The parenthetical in the header line must communicate the *reason* for the
verdict — the piece of information the developer needs most urgently.

**Current:**
```
═ VERDICT: INCONCLUSIVE (VERIFICATION) ═
```

**Required:**
```
═ VERDICT: INCONCLUSIVE (covariate misalignment) ═
```

The test intent (`VERIFICATION`, `SMOKE`) may appear as a labelled field
in the body if it has diagnostic value, but it must not occupy the header's
explanatory position.

For PASS and FAIL verdicts, the parenthetical should carry appropriate
summary information (e.g. the observed vs required pass rate, or the
termination reason). The principle is: **the header alone should tell the
developer what happened**.

### R2 — INCONCLUSIVE verdicts must display the covariate comparison

When the verdict is INCONCLUSIVE due to covariate misalignment, the output
must show the full covariate profiles and highlight the differences.

**Required output (indicative format):**

```
Covariate misalignment:
  Baseline:  temperature=0.3, llm_model=gpt-4o-mini, day_of_week=WEEKDAY
  Observed:  temperature=0.7, llm_model=gpt-4o-mini, day_of_week=WEEKDAY
  Differs:   temperature (baseline: 0.3, observed: 0.7)
```

Constraints:

- All declared covariates must be shown, not just the mismatched ones.
  Seeing the full profile lets the developer verify the aligned covariates
  are correct, not just that the misaligned ones are wrong.
- Mismatched covariates must be visually distinguished (a "Differs" line,
  or inline markers — the exact mechanism is an implementation choice).
- Covariate keys must appear in declaration order (matching the spec YAML)
  so the comparison is easy to read alongside the spec file.

### R3 — Body fields must be consistent with the header

The `Verdict:` field in the body must use the same terminology as the
header. Today:

- Header: `INCONCLUSIVE (VERIFICATION)`
- Body: `Inconclusive — covariate misalignment`

After R1, both will carry "covariate misalignment" — but the formatting
(case, punctuation) must also be consistent. The body's `Verdict:` field
should not introduce phrasing that the header doesn't use.

### R4 — Baseline provenance must be visible

When a verdict references a baseline (all non-experiment verdicts), the
output should identify which baseline was used:

```
Baseline:            ShoppingBasketUseCase-8e72-7070-dd8e-5bad-a769.yaml
                     (measured 2026-04-16, 1000 samples, minPassRate=0.7471)
```

This is especially important for covariate misalignment: the developer
needs to see *which* baseline was selected (or fell back to) and when it
was measured, to understand why the covariates don't match.

---

## 4. Scope

### In scope

- Console verdict output rendered by `ResultPublisher.printConsoleSummary()`
- Header construction (`PUnitReporter.headerDivider()`)
- Body rendering of covariate status
- All verdict categories (PASS, FAIL, INCONCLUSIVE)

### Out of scope

- HTML report rendering (separate module, separate rendering pipeline)
- Verdict XML serialization (already captures `CovariateStatus`)
- Changes to verdict computation logic (`ProbabilisticTestVerdictBuilder`)
- Changes to covariate detection logic

---

## 5. Implementation Notes

### Existing data available at render time

`ProbabilisticTestVerdict` already contains everything needed:

| Data | Source | Currently rendered? |
|------|--------|---------------------|
| Verdict (PASS/FAIL/INCONCLUSIVE) | `punitVerdict()` | Yes (header + body) |
| Test intent (VERIFICATION/SMOKE) | `ExecutionContext.intent()` | Yes (header only) |
| Covariate misalignments | `covariateStatus().misalignments()` | No |
| Baseline covariate profile | `covariateStatus().misalignments()` (baseline values) | No |
| Observed covariate profile | `covariateStatus().misalignments()` (test values) | No |
| Spec filename / ID | Available via execution context | No |
| Spec generatedAt | Available via loaded spec | No |

No new data collection is required. The change is purely in the rendering
layer.

### Affected files

- `ResultPublisher.java` — header construction, body rendering
- `PUnitReporter.java` — header formatting utilities

---

## 6. Acceptance Criteria

- The verdict header parenthetical communicates the verdict reason, not
  the test intent.
- INCONCLUSIVE verdicts due to covariate misalignment display the full
  baseline and observed covariate profiles with differences highlighted.
- The `Verdict:` body field and the header use consistent terminology.
- Baseline provenance (filename, date, sample count) is visible in the
  verdict output.
- PASS and FAIL verdicts are not degraded by these changes.
- Existing tests that assert on verdict output are updated to match.

---

## 7. Cross-references

- `ResultPublisher.printConsoleSummary()` — primary render site
- `ProbabilisticTestVerdictBuilder` — verdict construction, covariate
  detection
- `ProbabilisticTestVerdict.CovariateStatus` — misalignment data model
- `TestIntent` — VERIFICATION / SMOKE enum
- `PunitVerdict` — PASS / FAIL / INCONCLUSIVE enum
- Observed in: `punitexamples` →
  `ShoppingBasketCovariateTest$HighTemperatureConfiguration.testWithHighTemperature`
  (baseline at `temperature=0.3`, test at `temperature=0.7`)
