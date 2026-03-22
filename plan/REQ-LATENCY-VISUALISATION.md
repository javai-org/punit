# REQ-LATENCY-VISUALISATION: Always Show Latency with Colour-Coded Assertion Status

## 1. Summary

The PUnit HTML report should always display observed latency distribution (p50, p95, p99) for every probabilistic test, regardless of whether `@Latency` thresholds are configured. When thresholds exist, individual percentile cells are colour-coded green (passed) or red (exceeded). When no threshold is configured, the cell renders in the default text colour — informational only.

## 2. Motivation

PUnit already measures wall-clock latency for every sample (`SampleExecutor` records `latencyMs` on every `recordSuccess` call). However, the latency distribution is only included in the verdict XML and HTML report when `@Latency` thresholds are explicitly configured or derived from a baseline. This means useful observational data is silently discarded.

### Finding in punitexamples

The PaymentGateway tests include four test methods against the same use case. Only `testReliabilityWithLatencySla` declares `@Latency(p95Ms=500, p99Ms=1000)`. The other three (`testFunctionalCorrectness`, `testLatency`, `testCombinedReliability`) show dashes in the p50/p95/p99 columns, even though PUnit measured latency for every sample. The data exists but is not surfaced.

## 3. Visual Design

Three visual states per latency cell, distinguished by colour:

| State                   | Colour                          | Meaning               |
|-------------------------|---------------------------------|-----------------------|
| Asserted, passed        | Green (`--pass-color: #2e7d32`) | Under threshold       |
| Asserted, failed        | Red (`--fail-color: #c62828`)   | Threshold exceeded    |
| Observed, no threshold  | Default text colour             | Informational only    |

Colours are applied at **per-cell granularity**. A test with `@Latency(p95Ms=500)` but no `p99Ms` threshold renders:
- p50: default (no threshold for p50)
- p95: green or red (threshold configured)
- p99: default (no threshold for p99)

No legends, footnotes, or symbols are required. The colour semantics are self-evident: red means exceeded, green means within bounds, neutral means no opinion.

### Example

| Test Name                     | p50   | p95                                      | p99                                      |
|-------------------------------|-------|------------------------------------------|------------------------------------------|
| testReliabilityWithLatencySla | 134ms | <span style="color:#2e7d32">195ms</span> | <span style="color:#2e7d32">202ms</span> |
| testCombinedReliability       | 127ms | 189ms                                    | 198ms                                    |
| testFunctionalCorrectness     | 131ms | 192ms                                    | 199ms                                    |

In the first row, p95 and p99 are green (asserted, passed). In the other rows, all values are default colour (observed, no threshold).

## 4. Requirements

### 4.1. Verdict Data

| #      | Requirement                                                                                                                                                                                                       |
|--------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-01 | The `LatencyDimension` is included in the `ProbabilisticTestVerdict` whenever at least one successful sample was recorded, regardless of whether `@Latency` thresholds are configured.                            |
| REQ-02 | When no `@Latency` thresholds are configured and no baseline-derived thresholds exist, the `LatencyDimension` is populated with the observed distribution (p50, p90, p95, p99, max) and an empty assertions list. |
| REQ-03 | The `LatencyDimension.skipped` field is `false` when latency data is present (even without thresholds). It is `true` only when zero successful samples exist.                                                     |
| REQ-04 | The verdict XML includes the `<latency>` element with `<distribution>` for all tests that have latency data. The `<assertions>` element is present but empty when no thresholds are configured.                   |

### 4.2. HTML Report

| #      | Requirement                                                                                                                                                        |
|--------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-05 | The p50, p95, and p99 table columns display observed values for every test that has latency data. Dashes are shown only when no successful samples were recorded.  |
| REQ-06 | When a percentile has an assertion (threshold configured), the cell is styled with `color: var(--pass-color)` if passed or `color: var(--fail-color)` if exceeded. |
| REQ-07 | When a percentile has no assertion, the cell is styled with the default text colour. No special class or styling is applied.                                       |
| REQ-08 | Colour is applied per cell, not per row. A single row may have a mix of green, red, and default cells depending on which percentiles have thresholds.              |
| REQ-09 | The existing CSS variables `--pass-color` and `--fail-color` are reused. No new colour variables are introduced.                                                   |

### 4.3. Consistency

| #      | Requirement                                                                                                                                                                                           |
|--------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-10 | The `VerdictTextRenderer` (console output and Level 2/3 in the HTML report) continues to render latency analysis as it does today. Changes are limited to the table cells and the verdict data model. |
| REQ-11 | The `VerdictXmlWriter` and `VerdictXmlReader` handle the new case (latency present, assertions empty) without error. Existing verdict XMLs with no `<latency>` element remain parseable.              |
| REQ-12 | The `punitVerify` task is unaffected. It evaluates pass/fail from the verdict element, not from latency assertions.                                                                                   |

## 5. Affected Code

| File                                                            | Change                                                                                                                                                                                                                                                   |
|-----------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `punit-junit5/.../ptest/engine/ProbabilisticTestExtension.java` | In `buildProbabilisticTestVerdict`: build `LatencyDimension` from raw aggregator timing data when `latencyResult.wasEvaluated()` is false but successful samples exist.                                                                                  |
| `punit-report/.../report/HtmlReportWriter.java`                 | In `appendVerdictRow`: look up per-percentile assertion status from `LatencyDimension.assertions()` and apply CSS class (`latency-pass` / `latency-fail`) per cell. Render observed value in default style when no assertion exists for that percentile. |
| `punit-report/.../report/HtmlReportWriter.java` (CSS)           | Add `.latency-pass { color: var(--pass-color); font-weight: 600; }` and `.latency-fail { color: var(--fail-color); font-weight: 600; }`.                                                                                                                 |
| `punit-report/.../report/VerdictXmlWriter.java`                 | No structural change — already handles `LatencyDimension` with empty assertions list.                                                                                                                                                                    |
| `punit-report/.../report/VerdictXmlReader.java`                 | No structural change — already handles optional assertions element.                                                                                                                                                                                      |

## 6. Non-Requirements

| #     | Exclusion                                                                                                                                                                               |
|-------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| NR-01 | **Legend or footnote** — the colour semantics (green = passed, red = exceeded, default = no threshold) are self-evident. No explanatory text is added to the report.                     |
| NR-02 | **Threshold value in the cell** — the cell shows only the observed value. The threshold is available in the expandable statistical analysis section.                                      |
| NR-03 | **Colour-coding the VerdictTextRenderer console output** — console output uses ANSI-free text. Colour-coding is limited to the HTML report.                                              |
| NR-04 | **Configuration to disable latency display** — latency data is always shown when available. There is no opt-out.                                                                         |

## 7. Verification

1. Run punitexamples: `./gradlew :app-tests:experiment :app-tests:test --continue && ./gradlew :app-tests:punitReport`.
2. Open `app-tests/build/reports/punit/html/index.html`.
3. Verify: `testReliabilityWithLatencySla` shows green p95 and p99 values (thresholds 500ms and 1000ms, observed ~195ms and ~202ms).
4. Verify: `testFunctionalCorrectness`, `testLatency`, `testCombinedReliability` show latency values in default text colour (no dashes).
5. Verify: tests with no successful samples (e.g., budget-exhausted tests) show dashes.
6. Verify: a test where a latency threshold is exceeded shows the value in red.
