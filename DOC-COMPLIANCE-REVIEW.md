# Documentation Review: Compliance vs Conformance

## The Problem

The current documentation treats "compliance testing" and "conformance testing" as two distinct *categories* of test, distinguished by threshold origin:

- **Compliance testing** = threshold from SLA/SLO/policy
- **Conformance testing** = threshold from empirical baseline

This framing is misleading. It implies that annotating a test with `thresholdOrigin = SLA` makes it a compliance test. It does not. A 200-sample test against a 99.99% SLA target cannot provide compliance-grade evidence regardless of its annotation — it is a conformance test (smoke test) with SLA provenance metadata.

## The Correct Framing

**All PUnit probabilistic tests are conformance tests.** Every test answers the same question: "Does the system's observed behavior conform to its service contract?" The threshold origin (SLA, SLO, EMPIRICAL, etc.) is provenance metadata — it documents *where the number came from*, not *what kind of test this is*.

A conformance test becomes capable of contributing to a **compliance determination** only when the sample size is sufficient to provide verification-grade statistical evidence. This is a property of the test's statistical power, not of its annotation.

The distinction that matters is not SLA-vs-empirical, but rather:
- **Conformance**: Does the observed behavior meet the service contract? (All tests.)
- **Compliance-grade evidence**: Is N large enough that a PASS constitutes meaningful evidence of contract fulfilment? (Subset of tests with sufficient statistical power.)

---

## Affected Documents

### 1. USER-GUIDE.md

#### Section: "Two Testing Scenarios: Compliance and Conformance" (lines 106–128)

**Problem:** Presents compliance and conformance as two parallel categories distinguished by threshold source. The table (lines 110–113) equates "compliance testing" with "mandated standard" and "conformance testing" with "baseline regression." This is the central misframing.

**Current text:**
```
### Two Testing Scenarios: Compliance and Conformance

PUnit supports two distinct testing scenarios. These are not alternative approaches—they address different situations:

| Scenario                | Question                                   | Threshold Source                   |
|-------------------------|--------------------------------------------|------------------------------------|
| **Compliance Testing**  | Does the service meet a mandated standard? | SLA, SLO, or policy (prescribed)   |
| **Conformance Testing** | Has performance dropped below baseline?    | Empirical measurement (discovered) |

**Compliance Testing**: You have an external mandate—a contractual SLA, an internal SLO, or a quality
policy—that defines the required success rate. You verify the system meets it.

**Conformance Testing**: No external mandate exists. You measure the system's actual behavior to establish
a baseline, then detect when performance regresses from that baseline.
```

**Proposed revised text:**
```
### What PUnit Tests Are

Every PUnit probabilistic test is a **conformance test**: it checks whether the system's observed
behavior conforms to its service contract. The two things that vary are where the threshold comes
from and how many samples you run.

| Threshold Source             | Origin                                       | Example                                      |
|------------------------------|----------------------------------------------|----------------------------------------------|
| **Prescribed (SLA/SLO/Policy)** | Contractual or organizational mandate    | "The SLA requires 99.99% success rate"       |
| **Empirical (Baseline)**     | Derived from a MEASURE experiment            | "Baseline measured 95.1% over 1000 samples"  |

Both produce conformance tests. The threshold origin is **provenance metadata** — it documents where
the number came from, not what kind of test this is.

**When does a test contribute to a compliance determination?** Only when the sample size is sufficient
to provide verification-grade statistical evidence at the declared target. A 200-sample test against
a 99.99% SLA is a conformance test with SLA provenance — a useful smoke test, but not compliance
evidence. PUnit will flag this explicitly when it detects that the sample size is undersized for
compliance-grade evidence.
```

#### Section: "Part 2: Compliance Testing" (lines 452–529)

**Problem:** The entire section heading and framing implies that setting `thresholdOrigin = SLA` constitutes compliance testing. The example uses `samples = 10000` which gives the impression of compliance-grade evidence, but even 10,000 samples is undersized for p₀=0.9999 at α=0.001.

**Current text (heading and opening):**
```
## Part 2: Compliance Testing

When you have a mandated threshold, you're verifying **compliance**—not detecting regression.

### When to Use Compliance Testing

Use compliance testing when:
- A business stipulation defines the required success rate
- An SLA with a customer specifies reliability targets
...
```

**Proposed revised text:**
```
## Part 2: Testing Against Prescribed Thresholds

When you have a mandated threshold (SLA, SLO, policy), you test conformance against it directly —
no baseline experiment is needed.

### When to Use Prescribed Thresholds

Use a prescribed threshold when:
- A business stipulation defines the required success rate
- An SLA with a customer specifies reliability targets
- An internal SLO sets performance expectations
- A quality policy mandates minimum thresholds

You don't need to run experiments to discover the threshold — it's given to you. The `thresholdOrigin`
annotation documents where the number came from, providing an audit trail.

**Important:** Setting `thresholdOrigin = SLA` does not make the test a compliance test. It records
that the threshold originates from an SLA. Whether the test provides compliance-grade evidence depends
on the sample size. PUnit will warn you when the sample size is insufficient for the declared target.
```

#### Sample sizing table context (lines 517–527)

**Problem:** The sample sizing discussion is good but lacks the framing that connects it to the compliance/conformance distinction.

**Proposed addition** (after the existing table):
```
This is exactly the distinction between conformance testing and compliance evidence. A 100-sample
test against a 99.99% SLA is a valid conformance test — it will reliably catch catastrophic
regressions — but it cannot provide compliance-grade evidence because the sample size is too small
to distinguish 99.99% from, say, 99.5%. PUnit detects this automatically and annotates the verdict
accordingly.
```

---

### 2. GLOSSARY.md

#### Entries: "Compliance Testing" and "Conformance Testing" (lines 10–11)

**Problem:** The glossary definitions encode the incorrect dichotomy directly.

**Current text:**
```
| **Compliance Testing**  | Verifying a system meets a mandated threshold (SLA, SLO, policy).         |
| **Conformance Testing** | Detecting when performance regresses below an empirically established baseline. |
```

**Proposed revised text:**
```
| **Conformance Testing**        | Testing whether the system's observed behavior conforms to its service contract. All PUnit probabilistic tests are conformance tests, regardless of threshold origin. |
| **Compliance Evidence**        | A conformance test result that carries sufficient statistical power to support a compliance determination. Requires a sample size large enough that even a perfect observation would produce a confidence bound exceeding the target threshold. PUnit flags when this condition is not met. |
| **Prescribed Threshold**       | A threshold sourced from an external mandate (SLA, SLO, policy) rather than derived from empirical measurement. |
| **Empirical Threshold**        | A threshold derived from a MEASURE experiment baseline.                    |
```

---

### 3. STATISTICAL-COMPANION.md

#### Section: "Reference Scenarios" — "Scenario A: Compliance Testing" (lines 64–76)

**Problem:** Labels the scenario as "Compliance Testing" when it is a conformance test against a prescribed threshold.

**Current text:**
```
### Scenario A: Compliance Testing with a Payment Processing API

**Application**: A third-party payment processing API with a contractual uptime guarantee.
...
**Key parameters**:
- $p_{\text{SLA}} = 0.995$ (given by contract)
- No baseline experiment required
- Statistical question: "Does the system meet its contractual obligation?"
```

**Proposed revised text:**
```
### Scenario A: Conformance Testing Against a Prescribed Threshold

**Application**: A third-party payment processing API with a contractual uptime guarantee.
...
**Key parameters**:
- $p_{\text{SLA}} = 0.995$ (given by contract)
- No baseline experiment required
- Statistical question: "Does the system conform to its contractual requirement?"
- Whether this constitutes **compliance evidence** depends on sample size (see §3.6)
```

#### Section: "Two Testing Scenarios" table (lines 95–124)

**Problem:** The table and subsection headings present "Compliance Testing" and "Regression Testing" as two distinct paradigms.

**Current text:**
```
| Scenario               | Threshold Source                   | Statistical Question                                  |
|------------------------|------------------------------------|-------------------------------------------------------|
| **Compliance Testing** | Contract, SLA, SLO, policy         | "Does the system meet the mandated requirement?"      |
| **Regression Testing** | Empirical estimate from experiment | "Has the system degraded from its measured baseline?" |
```

**Proposed revised text:**
```
| Threshold Source            | Origin                             | Statistical Question                                  |
|-----------------------------|------------------------------------|-------------------------------------------------------|
| **Prescribed (SLA/SLO/Policy)** | Contract, SLA, SLO, policy    | "Does the system conform to the mandated requirement?"|
| **Empirical (Baseline)**    | Empirical estimate from experiment | "Has the system regressed from its measured baseline?" |

Both are conformance tests — they check whether observed behavior meets the service contract.
The difference is where the threshold originates, not what kind of test is being performed.
Whether a prescribed-threshold test provides **compliance-grade evidence** depends on whether
the sample size is sufficient for the declared target (see §3.6).
```

#### Subsection heading: "Compliance Testing" (line 104)

**Current:** `### Compliance Testing`
**Proposed:** `### Prescribed Threshold (SLA / SLO / Policy)`

With updated bullet:
- Current: "The test verifies conformance to an external standard"
- Proposed: "The test checks conformance to a prescribed threshold"

#### Section 3.6: "Testing Against a Given Threshold (Compliance)" (lines 414–457)

**Problem:** The heading labels this as "Compliance." The content is actually excellent — it correctly discusses the statistical challenges and sample sizing requirements. But the heading implies that using a given threshold *is* compliance testing.

**Current heading:** `### 3.6 Testing Against a Given Threshold (Compliance)`
**Proposed heading:** `### 3.6 Testing Against a Prescribed Threshold`

**Proposed addition** after the sample size table (line 457):
```
**Compliance vs. conformance**: All of these are conformance tests. A test reaches
compliance-grade evidentiary strength only when the sample size is sufficient for
the target threshold at the desired confidence level. PUnit detects undersized samples
automatically and annotates the verdict with a warning when the test cannot contribute
to a compliance determination.
```

#### Line 874: THRESHOLD PROVENANCE purpose

**Current:** `Auditability for compliance tests`
**Proposed:** `Auditability and threshold traceability`

---

### 4. OPERATIONAL-FLOW.md

#### Section: "The Two Testing Paradigms" (lines 20–54)

**Problem:** Frames "SLA-Driven Testing" and "Spec-Driven Testing" as two paradigms. This is less problematic than the other docs since it doesn't use the word "compliance," but it still implies a categorical distinction that doesn't exist.

**Current text:**
```
### SLA-Driven Testing

The threshold comes from an **external requirement**—a contract, policy, or SLO:
...
**Workflow:** Requirement → Test → Verify
```

**Proposed revised text:**
```
### Prescribed-Threshold Testing

The threshold comes from an **external requirement**—a contract, policy, or SLO:
...
**Workflow:** Requirement → Test → Conformance Verdict

Note: the `thresholdOrigin` annotation records where the threshold came from. Whether the
test provides compliance-grade evidence depends on the sample size relative to the target.
```

---

### 5. README.md

#### Quick Start example (lines 36–57)

**Problem:** The example uses `samples = 100` with `minPassRate = 0.995` and `thresholdOrigin = SLA`. This is presented without qualification. The verdict output shows "PASSED" with provenance, implying this constitutes compliance verification.

**Current commentary (line 50):**
```
This test runs 100 samples, requires 99.5% success, and documents where that threshold came
from—useful for audits and traceability. When it passes, the verdict includes the provenance:
```

**Proposed revised commentary:**
```
This test runs 100 samples, requires 99.5% success, and documents where that threshold came
from — useful for audits and traceability. This is a conformance test: it checks whether
observed behavior meets the service contract. The `thresholdOrigin` and `contractRef` record
the threshold's provenance. Whether this sample size is sufficient for compliance-grade evidence
depends on the target — PUnit will warn you when it is not.
```

---

### 6. ThresholdOrigin.java (API Javadoc)

#### Enum-level Javadoc (lines 4–42)

**Problem:** The Javadoc is mostly fine — it correctly describes the enum as documenting threshold *origin*. However, the `POLICY` description (line 67) says "Policies may include security requirements, compliance mandates" — using "compliance" in a way that conflates the regulatory meaning with the testing meaning.

**Current (line 67):**
```java
 * Policies may include security requirements, compliance mandates, etc.
```

**Proposed:**
```java
 * Policies may include security requirements, organizational mandates, etc.
```

---

### 7. ProbabilisticTest.java (API Javadoc)

#### Line 304

**Current:**
```java
 *   <li>Regulators requiring compliance documentation</li>
```

**Proposed:**
```java
 *   <li>Regulators requiring test methodology documentation</li>
```

---

### 8. Code: SlaVerificationSizer.java and references

**Problem:** The class name `SlaVerificationSizer` and constant `"sample not sized for SLA verification"` tie the concept to SLA specifically, when the insight is about compliance evidence in general. A test with `thresholdOrigin = POLICY` and insufficient N gets a message about "SLA verification," which is incorrect.

**Action:** Rename to `ComplianceEvidenceEvaluator` (or similar). Update `SIZING_NOTE` to `"sample not sized for compliance verification"`. Rename `isSlaAnchored` to `hasComplianceContext`. Update all references.

This is a code change, not a documentation change, but is included here because the naming directly affects what users see in test output.

---

## Summary

The fundamental error across all documents is treating threshold origin as the defining characteristic of a test category. The corrected framing:

1. **All PUnit tests are conformance tests** — they check service contract conformance
2. **Threshold origin is metadata** — it records where the number came from (SLA, empirical, etc.)
3. **Compliance evidence is emergent** — it arises from sufficient statistical power, not from annotation
4. **The two things that vary** are threshold source (prescribed vs. empirical) and sample sizing (smoke-test vs. compliance-grade)
