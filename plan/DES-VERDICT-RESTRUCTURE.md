# Design: Verdict Console Output Restructure

**Implements:** REQ-VERDICT-RESTRUCTURE (R1–R4)
**Inventory features:** RP01 (verdict record), RP05 (console renderer)
**Date:** 2026-04-17
**Status:** Proposal

---

## 1. Context

`REQ-VERDICT-RESTRUCTURE.md` identifies four deficiencies in the current
console verdict output. The orchestrator inventory has been updated to make
these normative across all javai frameworks (see `inventory/catalog/reporting/
RP01-verdict-record/README.md` and `RP05-console-renderer/README.md`).

This document specifies the punit changes required to satisfy those
requirements. The changes are confined to the verdict record data model and
the rendering layer — no changes to verdict computation, covariate detection,
or test execution.

### Current rendering pipeline

```
ProbabilisticTestVerdictBuilder  →  ProbabilisticTestVerdict
                                            ↓
                                    ResultPublisher.printConsoleSummary()
                                            ↓
                                    PUnitReporter.reportInfo(title, body)
```

The header is constructed in `ResultPublisher` line 161:

```java
String title = "VERDICT: " + verdictLabel + " (" + intentLabel + ")";
```

This puts the test intent (VERIFICATION/SMOKE) in the explanatory position.
R1 requires the verdict reason there instead.

---

## 2. Changes to `ProbabilisticTestVerdict`

### 2.1 Add `verdictReason` field

Add a new `String verdictReason` component to the record. This is the single
most important diagnostic context for the verdict, used by all renderers in
the header/title position.

```java
public record ProbabilisticTestVerdict(
        String correlationId,
        Instant timestamp,
        TestIdentity identity,
        ExecutionSummary execution,
        Optional<FunctionalDimension> functional,
        Optional<LatencyDimension> latency,
        StatisticalAnalysis statistics,
        CovariateStatus covariates,
        CostSummary cost,
        Optional<PacingSummary> pacing,
        Optional<SpecProvenance> provenance,
        Termination termination,
        Map<String, String> environmentMetadata,
        boolean junitPassed,
        PunitVerdict punitVerdict,
        String verdictReason               // NEW
) { ... }
```

The builder computes `verdictReason` from existing fields:

| Verdict | Condition | Reason string |
|---------|-----------|---------------|
| PASS | — | `"{observed} >= {threshold}"` (4dp) |
| FAIL | termination is COMPLETED | `"{observed} < {threshold}"` (4dp) |
| FAIL | termination is budget exhaustion | `"budget exhausted"` |
| INCONCLUSIVE | covariate misalignment | `"covariate misalignment"` |
| INCONCLUSIVE | budget exhaustion | `"budget exhausted"` |
| INCONCLUSIVE | other | `"insufficient evidence"` |

### 2.2 Expand `CovariateStatus` with full profiles

The current `CovariateStatus` carries only misaligned covariates. R2 requires
showing *all* declared covariates (aligned and misaligned) so the developer
can verify the aligned ones are correct. Add the full profiles:

```java
public record CovariateStatus(
        boolean aligned,
        List<Misalignment> misalignments,
        Map<String, String> baselineProfile,   // NEW — all covariates from baseline
        Map<String, String> observedProfile     // NEW — all covariates at test time
) {
    public CovariateStatus {
        misalignments = misalignments != null ? List.copyOf(misalignments) : List.of();
        baselineProfile = baselineProfile != null ? Map.copyOf(baselineProfile) : Map.of();
        observedProfile = observedProfile != null ? Map.copyOf(observedProfile) : Map.of();
    }

    public static CovariateStatus allAligned() {
        return new CovariateStatus(true, List.of(), Map.of(), Map.of());
    }
}
```

Both maps use `LinkedHashMap` in the builder to preserve declaration order
(covariate keys must appear in the order declared in the use case, matching
the spec YAML).

### 2.3 `BaselineSummary` — already exists

`StatisticalAnalysis.BaselineSummary` already carries `sourceFile`,
`generatedAt`, `baselineSamples`, `baselineSuccesses`, `baselineRate`, and
`derivedThreshold`. No structural change needed — these fields satisfy R4.
The change is in the *rendering* layer, which currently ignores them.

---

## 3. Changes to `ResultPublisher`

### 3.1 Header construction (R1)

Replace the current header:

```java
// BEFORE
String intentLabel = exec.intent() != null ? exec.intent().name() : "VERIFICATION";
String verdictLabel = verdict.punitVerdict().name();
String title = "VERDICT: " + verdictLabel + " (" + intentLabel + ")";
```

With:

```java
// AFTER
String verdictLabel = verdict.punitVerdict().name();
String title = "VERDICT: " + verdictLabel + " (" + verdict.verdictReason() + ")";
```

### 3.2 Covariate comparison block (R2)

Add a new private method `appendCovariateComparison` called when
`verdict.punitVerdict() == PunitVerdict.INCONCLUSIVE` and
`!verdict.covariates().aligned()`:

```java
void appendCovariateComparison(StringBuilder sb, ProbabilisticTestVerdict verdict) {
    CovariateStatus cov = verdict.covariates();
    if (cov.aligned() || cov.baselineProfile().isEmpty()) {
        return;
    }

    sb.append("\n");
    sb.append("Covariate misalignment:\n");
    sb.append("  Baseline:  ").append(formatProfile(cov.baselineProfile())).append("\n");
    sb.append("  Observed:  ").append(formatProfile(cov.observedProfile())).append("\n");

    List<Misalignment> diffs = cov.misalignments();
    if (!diffs.isEmpty()) {
        String diffStr = diffs.stream()
                .map(m -> m.covariateKey() + " (baseline: " + m.baselineValue()
                        + ", observed: " + m.testValue() + ")")
                .collect(Collectors.joining(", "));
        sb.append("  Differs:   ").append(diffStr).append("\n");
    }
}

private String formatProfile(Map<String, String> profile) {
    return profile.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(", "));
}
```

Insert the call in `printConsoleSummary` immediately after the existing
`appendDimensionBreakdown` call, and remove the current hard-coded
`"Inconclusive — covariate misalignment"` line:

```java
// REMOVE
if (verdict.punitVerdict() == PunitVerdict.INCONCLUSIVE) {
    sb.append(PUnitReporter.labelValueLn("Verdict:", "Inconclusive — covariate misalignment"));
}

// REPLACE WITH
appendCovariateComparison(sb, verdict);
```

### 3.3 Baseline provenance block (R4)

Add a new private method `appendBaselineProvenance` that renders provenance
from `StatisticalAnalysis.BaselineSummary`:

```java
void appendBaselineProvenance(StringBuilder sb, ProbabilisticTestVerdict verdict) {
    verdict.statistics().baseline().ifPresent(baseline -> {
        sb.append(PUnitReporter.labelValueLn("Baseline:", baseline.sourceFile()));

        String dateStr = baseline.generatedAt()
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate()
                .toString();
        sb.append(PUnitReporter.labelValueLn("",
                String.format("(measured %s, %d samples, minPassRate=%s)",
                        dateStr,
                        baseline.baselineSamples(),
                        RateFormat.format(baseline.derivedThreshold()))));
    });
}
```

Insert the call in `printConsoleSummary` after the covariate comparison
and before the existing provenance (threshold origin / contract ref) block.

### 3.4 Body consistency (R3)

The existing `appendProvenance` method renders threshold origin and contract
reference. After the header change, the body `Verdict:` line (currently only
for INCONCLUSIVE) is removed — the covariate comparison block replaces it
with a structured display. No separate `Verdict:` label line is needed
because the header carries the verdict reason.

---

## 4. Builder changes

### 4.1 `ProbabilisticTestVerdictBuilder`

The builder must:

1. Derive `verdictReason` from the verdict, execution summary, covariate
   status, and termination reason.
2. Populate `baselineProfile` and `observedProfile` on `CovariateStatus`.

For (1), add a private method:

```java
private String deriveVerdictReason() {
    if (punitVerdict == PunitVerdict.INCONCLUSIVE) {
        if (!covariateStatus.aligned()) {
            return "covariate misalignment";
        }
        if (terminationReason.isBudgetExhaustion()) {
            return "budget exhausted";
        }
        return "insufficient evidence";
    }
    if (terminationReason.isBudgetExhaustion()) {
        return "budget exhausted";
    }
    String comparator = observedPassRate >= minPassRate ? ">=" : "<";
    return String.format("%s %s %s",
            RateFormat.format(observedPassRate),
            comparator,
            RateFormat.format(minPassRate));
}
```

For (2), the builder receives covariate profiles from the test extension.
The covariate profiles are already available at verdict construction time:
the baseline spec's `covariates` block provides the baseline profile, and
the use case's `resolvedCovariates()` provides the observed profile. The
extension code that builds the `CovariateStatus` must pass these through.

---

## 5. Reporting reference tests

### 5.1 Fixture consumption

Create `ReportingReferenceTest.java` in
`punit-core/src/test/java/org/javai/punit/reporting/conformance/`:

```java
class ReportingReferenceTest {

    private static final Path FIXTURE_PATH = Path.of(
            "../../../javai-orchestrator/inventory/catalog/reporting/" +
            "RP05-console-renderer/fixtures/verdict_console_output.json");

    @ParameterizedTest
    @MethodSource("fixtureProvider")
    void verdictOutputMatchesFixture(String caseName, JsonNode inputs,
                                      JsonNode expected) {
        ProbabilisticTestVerdict verdict = buildVerdictFromInputs(inputs);
        ResultPublisher publisher = new ResultPublisher(captureReporter);
        publisher.printConsoleSummary(verdict, null, null);

        String output = captureReporter.captured();
        String headerContains = expected.get("headerContains").asText();
        assertThat(output).contains(headerContains);

        for (JsonNode line : expected.get("bodyLines")) {
            String pattern = expandPlaceholders(line.asText());
            assertThat(output).containsPattern(pattern);
        }
    }
}
```

### 5.2 Fixture path strategy

The fixture JSON lives in the orchestrator's inventory. punit references it
via relative path from the submodule root. If the orchestrator is not present
(e.g., standalone punit builds), the test is skipped with an assumption:

```java
@BeforeAll
static void checkFixtureAvailable() {
    assumeTrue(Files.exists(FIXTURE_PATH),
            "Orchestrator fixture not available — skipping reference tests");
}
```

Alternatively, the fixture can be copied into punit's test resources during
the build. The exact mechanism is an implementation choice; the requirement
is that the fixture is consumed, not hand-maintained in punit.

### 5.3 Placeholder expansion

A utility method expands `{{PLACEHOLDER}}` tokens to regex:

```java
private String expandPlaceholders(String line) {
    return line
            .replace("{{TIMESTAMP}}", "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*")
            .replace("{{ELAPSED}}", "\\d+")
            .replace("{{CORRELATION_ID}}", "[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}");
}
```

### 5.4 Verdict construction from fixture inputs

The `buildVerdictFromInputs(JsonNode)` method constructs a
`ProbabilisticTestVerdict` from the fixture's abstract `inputs` object,
using the builder:

```java
private ProbabilisticTestVerdict buildVerdictFromInputs(JsonNode inputs) {
    return ProbabilisticTestVerdict.builder()
            .className(inputs.at("/identity/className").asText())
            .methodName(inputs.at("/identity/methodName").asText())
            .verdict(PunitVerdict.valueOf(inputs.get("verdict").asText()))
            .successes(inputs.at("/execution/successes").asInt())
            .failures(inputs.at("/execution/failures").asInt())
            // ... remaining fields mapped from JSON paths
            .build();
}
```

---

## 6. Test plan

### Existing test updates

| Test class | Change |
|---|---|
| `ResultPublisherTest` | Update header assertions: `(VERIFICATION)` → `(reason)`. Add tests for covariate comparison block and baseline provenance block. |
| `VerdictTextRendererTest` | Update any header/verdict rendering assertions. Add covariate comparison assertions for transparent stats mode. |
| `PUnitReporterTest` | No changes (formatting primitives unchanged). |

### New tests

| Test | What it validates |
|---|---|
| `ReportingReferenceTest` | Fixture-driven: all 5 fixture cases produce correct output. |
| `VerdictReasonDerivationTest` | Unit test for `deriveVerdictReason()`: all verdict × termination × covariate combinations produce the correct reason string. |
| `CovariateComparisonRenderingTest` | Covariate comparison block: all covariates shown, differs line correct, declaration order preserved, empty when aligned. |
| `BaselineProvenanceRenderingTest` | Baseline provenance block: filename, date, sample count, threshold rendered. Absent when no baseline. |

---

## 7. Affected files

| File | Change type |
|---|---|
| `ProbabilisticTestVerdict.java` | Add `verdictReason` record component |
| `ProbabilisticTestVerdict.CovariateStatus` | Add `baselineProfile`, `observedProfile` maps |
| `ProbabilisticTestVerdictBuilder.java` | Derive verdict reason; pass covariate profiles |
| `ResultPublisher.java` | Header restructure; add `appendCovariateComparison`, `appendBaselineProvenance`; remove hard-coded inconclusive line |
| `VerdictTextRenderer.java` | Update `renderForReporter` verdict section to use `verdictReason()`; add covariate comparison to transparent stats |
| `ResultPublisherTest.java` | Update header assertions; add new test methods |
| `VerdictTextRendererTest.java` | Update verdict section assertions |
| `ReportingReferenceTest.java` | New — fixture-driven conformance tests |
| Extension code (verdict construction site) | Pass covariate profiles to CovariateStatus |

---

## 8. Implementation sequence

1. **`CovariateStatus` expansion** — add `baselineProfile` and
   `observedProfile` maps; update `allAligned()` factory; update all
   construction sites.
2. **`verdictReason` field** — add to `ProbabilisticTestVerdict`; implement
   `deriveVerdictReason()` in builder; update all construction sites.
3. **Header restructure** (R1) — change `ResultPublisher.printConsoleSummary`
   header construction to use `verdictReason()`.
4. **Body consistency** (R3) — remove hard-coded `"Inconclusive — covariate
   misalignment"` line.
5. **Covariate comparison** (R2) — implement `appendCovariateComparison`;
   wire into `printConsoleSummary`.
6. **Baseline provenance** (R4) — implement `appendBaselineProvenance`; wire
   into `printConsoleSummary`.
7. **Update existing tests** — fix all header assertions in
   `ResultPublisherTest` and `VerdictTextRendererTest`.
8. **Reporting reference tests** — implement `ReportingReferenceTest` with
   fixture consumption.
9. **VerdictTextRenderer updates** — update transparent stats mode to use
   `verdictReason()` and render covariate comparison.

Steps 1–2 are data model changes (compile, fix call sites).
Steps 3–6 are rendering changes (independent of each other after 1–2).
Steps 7–9 are test changes (after rendering is stable).

---

## 9. Out of scope

- HTML report rendering (RP04 — separate module, separate rendering pipeline).
- Verdict XML serialization (RP02/RP03 — `CovariateStatus` is already
  serialised; `verdictReason` should be added to custom XML but that is a
  separate change).
- Changes to verdict computation logic.
- Changes to covariate detection logic.
- VerdictTextRenderer HTML tooltip rendering (no header concept in HTML
  tooltips).
