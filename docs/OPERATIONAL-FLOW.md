# Operational Flow: From Requirement to Certainty

This document describes the end-to-end workflow for using **PUnit** to test systems characterized by uncertainty—systems that don't always produce the same output for the same input.

All attribution licensing is ARL.

---

## Overview

When a system behaves with inherent uncertainty (LLMs, distributed systems, randomized algorithms), traditional pass/fail testing breaks down. A test might pass today and fail tomorrow—not because the system changed, but because of its nature.

PUnit provides a disciplined workflow that:

1. **Expresses** requirements as statistical thresholds (from normative origins like SLAs or empirical data)
2. **Executes** multiple samples to gather evidence
3. **Evaluates** results with proper statistical context and declared **intent**
4. **Reports** qualified verdicts (categorized as VERIFICATION or SMOKE)

---

## The Two Testing Paradigms

PUnit supports two complementary approaches to defining thresholds:

### SLA-Driven Testing (Compliance vs. Smoke)

The threshold comes from a **normative origin**—a contract, policy, or SLO. 

However, we must distinguish between **compliance** and a **smoke test**. A true compliance test requires a sample size large enough to support an evidential claim (VERIFICATION). For highly reliable services like payment gateways, this sample size can be very large. 

If you want an early-warning check without the cost of a full compliance audit, declare your intent as `SMOKE`.

```java
@ProbabilisticTest(
    samples = 100,
    minPassRate = 0.999,        // From SLA: "99.9% success rate"
    thresholdOrigin = ThresholdOrigin.SLA,
    intent = TestIntent.SMOKE,  // Acknowledge this is a smoke test, not a full compliance audit
    contractRef = "Customer API SLA §3.1"
)
void apiMeetsSla() { ... }
```

**Verification Enforcement:** If you use `intent = TestIntent.VERIFICATION` (the default) with a normative threshold origin like `SLA`, PUnit will reject the test configuration if the sample size is too low to provide a statistically sound result.

**Workflow:** Requirement → Declare Intent → Test → Verify

### Spec-Driven Testing

The threshold is **derived from empirical data** gathered through experiments:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ Use Case ──▶ EXPLORE ──▶ OPTIMIZE ──▶ MEASURE ──▶ Spec ──▶ Test             │
│              (compare)    (tune)       (1000+)     (commit)  (threshold      │
│                                                               derived)       │
└──────────────────────────────────────────────────────────────────────────────┘
```

**Workflow:** Explore → Optimize → Measure → Commit Spec → Test

Both paradigms use the same `@ProbabilisticTest` annotation. The difference is where `minPassRate` comes from.

---

## The Three Operational Approaches

Regardless of which paradigm you use, you must decide **how to parameterize** your test.

> **Where the threshold comes from, and how the comparison is decided.** PUnit's `PassRate` criterion has two modes:
>
> - **Empirical** (`PassRate.empirical()`) — the threshold is the observed pass rate read at runtime from the matched baseline spec (produced by a prior MEASURE experiment). The verdict applies a one-sided **Wilson score lower bound** to the run's observed rate at the configured confidence (default 0.95) and passes iff that lower bound clears the baseline rate. Approaches 1 and 2 below use this mode.
> - **Contractual** (`PassRate.meeting(threshold, origin)`) — the threshold is an externally-fixed number declared in code (an SLA, SLO, or policy figure). The verdict is a **deterministic** `observed >= threshold` comparison. No Wilson margin: an SLA is an external commitment to a specific number, not a statistical claim against a baseline. Approach 3 uses this mode.
>
> What the three approaches differ in is which knob the author fixes first.

### Approach 1: Sample-Size-First (Budget-Driven)

*"My budget allows 100 samples per run. Run them against the recorded baseline and let the framework decide."*

```java
@ProbabilisticTest
void sampleSizeFirst() {
    PUnit.testing(this::baseline)
            .samples(100)
            .criterion(PassRate.empirical())
            .assertPasses();
}
```

**What happens:**
- PUnit runs the chosen samples.
- `empirical()` resolves the matched baseline spec at runtime; its observed rate becomes the threshold for this run.
- The verdict applies the Wilson lower bound at the default confidence (0.95) to *this* run's observed rate, and passes iff that lower bound clears the baseline.
- With small N the Wilson margin is wide, so passing requires the observed rate to be clearly above the baseline.

**Trade-off:** You accept whatever statistical power 100 samples affords. Detects large regressions confidently; less sensitive to small ones.

**Best for:** Continuous monitoring, CI pipelines, rate-limited APIs.

### Approach 2: Confidence-First (Power-Driven)

*"I need to be able to detect a 5-percentage-point regression with 80% probability at 95% confidence. How many samples?"*

```java
@ProbabilisticTest
void confidenceFirst() {
    int n = PowerAnalysis.sampleSize(this::baseline, 0.05, 0.80);

    PUnit.testing(this::baseline)
            .samples(n)
            .criterion(PassRate.empirical().atConfidence(0.95))
            .assertPasses();
}
```

**What happens:**
- `PowerAnalysis.sampleSize(baseline, mde, power)` derives the required N from the baseline rate, the minimum detectable effect (MDE), and the target power.
- PUnit runs that many samples (typically larger than budget-driven runs).
- Verdict still applies the Wilson lower bound at the target confidence — but N is sized so a real regression of size MDE has the configured probability of failing the bound.

**Trade-off:** Sample size is determined by statistics, not budget. Tight MDEs and high power require many samples.

**Best for:** Safety-critical systems, compliance audits, pre-release assurance.

### Approach 3: Threshold-First (Externally-Dictated)

*"The threshold is fixed by an SLA, SLO, or policy. Verify against it."*

```java
@ProbabilisticTest
void thresholdFirst() {
    PUnit.testing(MyUseCase.sampling(INPUTS, 100), MyFactors.DEFAULT)
            .criterion(PassRate.meeting(0.90, ThresholdOrigin.SLA))
            .contractRef("Customer API SLA §3.1")
            .assertPasses();
}
```

**What happens:**
- The threshold and its provenance (`SLA`, `SLO`, or `POLICY`) are declared in code; no baseline is involved.
- PUnit runs the chosen samples and applies a **deterministic** `observed >= threshold` comparison — no Wilson margin.
- The threshold's provenance and the optional `contractRef` are recorded on the verdict for audit traceability.

**Trade-off:** No statistical buffer. With a small N relative to a strict threshold, declare `intent = TestIntent.SMOKE` to mark the run as a sentinel rather than a verification claim; otherwise PUnit's pre-flight feasibility gate may reject the configuration as undersized for verification.

**Best for:** SLA-style verification of services with externally-committed reliability targets.

> **Antipattern: pinning a contractual threshold to a baseline's observed rate.** Reading a baseline file by eye and pasting its observed rate into `PassRate.meeting(0.935, ThresholdOrigin.EMPIRICAL)` looks like the empirical-pair pattern but isn't. The contractual path is deterministic — `observed >= 0.935` — and natural sampling variance puts the next run's observed rate below 0.935 roughly half the time even when the SUT is performing exactly at baseline. Result: ~50% false-fail rate. The proper baseline-comparison path is `PassRate.empirical()`, which resolves the baseline at runtime, applies the Wilson lower bound at the configured confidence, and gives the test the statistical buffer that the hardcoded contractual approach is missing.

### Choosing Your Approach

| If Your Priority Is...               | Use...            | You're Saying...                                                       |
|--------------------------------------|-------------------|------------------------------------------------------------------------|
| Controlling costs (CI time, API)     | Sample-Size-First | "We can afford N samples against the baseline. Verdict at default confidence." |
| Minimizing risk (safety, compliance) | Confidence-First  | "We need to detect this regression at this power. How many samples?"   |
| Honouring a contractual target       | Threshold-First   | "The SLA says 0.99. Pass iff observed beats it."                       |

> **Working example:** See [`ShoppingBasketThresholdApproachesTest`](https://github.com/javai-org/punitexamples/blob/main/src/test/java/org/javai/punit/examples/probabilistictests/ShoppingBasketThresholdApproachesTest.java) in punitexamples for a complete demonstration of all three approaches.

---

## The Fundamental Trade-Off

You cannot simultaneously have:
- Low testing cost (few samples)
- High confidence (low false positive rate)
- Tight threshold (detect small degradations)

This is not a limitation of PUnit—it's a fundamental property of statistical inference.

| If You Fix...     | And You Fix...   | Then Statistics Determines... |
|-------------------|------------------|-------------------------------|
| Sample size       | Threshold        | Confidence level              |
| Sample size       | Confidence       | How tight threshold can be    |
| Confidence        | Threshold        | Required sample size          |

PUnit makes these trade-offs **explicit and computable** rather than leaving them implicit.

---

## The Spec-Driven Workflow in Detail

### Stage 1: Define a Use Case

PUnit recognizes that **experiments and tests must refer to the same objects**.

In traditional testing, we articulate correctness through a series of test assertions. This works for deterministic systems where we expect 100% success. However, for systems with inherent uncertainty, a test assertion that aborts on failure is of zero use when we want to collect data about the service's behavior. We need to know *how often* it fails, not just that it *did* fail.

We therefore define a **Use Case** and its associated **Service Contract**. The Service Contract is the shared expression of correctness:
- **Experiments** use it as a source of correctness data (to measure behavior).
- **Probabilistic Tests** use it as a correctness enforcer (to verify performance against a threshold).

A Use Case is a reusable class that invokes your production code and declares an acceptance contract:

```java
public final class JsonGenerationUseCase
        implements UseCase<Tuning, String, String> {

    @Override
    public Outcome<String> invoke(String prompt, TokenTracker tracker) {
        LlmResponse response = llmClient.complete(prompt);
        tracker.recordTokens(response.getTokensUsed());
        return Outcome.ok(response.getContent());
    }

    @Override
    public void postconditions(ContractBuilder<String> b) {
        b.ensure("Output is valid JSON",
                output -> JsonValidator.isValid(output)
                        ? Outcome.ok()
                        : Outcome.fail("invalid-json", "validator rejected output"));
    }

    @Override
    public String id() { return "json-generation"; }
}
```

The use case is **reused** across experiments AND tests. You define it once.

### Stage 2: Run a MEASURE Experiment

Run the use case many times (typically 1000+) to gather empirical data:

```java
@Experiment(
    mode = ExperimentMode.MEASURE,
    useCase = JsonGenerationUseCase.class,
    samples = 1000
)
void measureBaseline(JsonGenerationUseCase useCase, ResultCaptor captor) {
    captor.record(useCase.generateJson("Generate a user profile"));
}
```

```bash
./gradlew measure --tests "JsonGenerationExperiment"
```

### Stage 3: Commit the Spec

The experiment writes a spec file to `src/test/resources/punit/specs/`:

```yaml
schemaVersion: punit-spec-2
specId: usecase.json.generation
useCaseId: usecase.json.generation
generatedAt: 2026-01-12T10:30:00Z

empiricalBasis:
  samples: 1000
  successes: 935
  generatedAt: 2026-01-12T10:30:00Z

extendedStatistics:
  confidenceInterval:
    lower: 0.919
    upper: 0.949
```

**Review and commit:**

```bash
git add src/test/resources/punit/specs/
git commit -m "Add baseline for JSON generation (93.5% @ N=1000)"
```

The approval step IS the commit. If your organization uses pull requests, that's where review happens.

### Stage 4: Create a Probabilistic Test

The test references the spec. The threshold is derived at runtime:

```java
@ProbabilisticTest(
    useCase = JsonGenerationUseCase.class,
    samples = 100,
    confidence = 0.95
)
void jsonGenerationMeetsBaseline() {
    LlmResponse response = llmClient.complete("Generate a user profile");
    assertThat(JsonValidator.isValid(response.getContent())).isTrue();
}
```

**What happens at runtime:**
1. Load spec for `usecase.json.generation`
2. Read empirical basis (93.5% from 1000 samples)
3. Compute threshold for 100 samples at 95% confidence
4. Run 100 samples
5. Report pass/fail with statistical context

---

## Understanding Results

### When the Test Passes

```
═══════════════════════════════════════════════════════════════
PUnit PASSED: jsonGenerationMeetsBaseline
  Observed pass rate: 94.0% (94/100) >= min pass rate: 91.6%
  Threshold derived from: usecase.json.generation.yaml (93.5% baseline)
  Elapsed: 45234ms
═══════════════════════════════════════════════════════════════
```

**Interpretation:** The observed 94% is consistent with the 93.5% baseline, accounting for sampling variance. No evidence of degradation.

### When the Test Fails

```
═══════════════════════════════════════════════════════════════
PUnit FAILED: jsonGenerationMeetsBaseline
  Observed pass rate: 85.0% (85/100) < min pass rate: 91.6%
  Shortfall: 6.6% below threshold

  CONTEXT:
    Confidence: 95%
    Interpretation: Evidence suggests degradation from baseline.
    False positive probability: 5%

  SUGGESTED ACTIONS:
    1. Investigate recent changes
    2. Re-run experiment if change was intentional
    3. Increase sample size if false positive suspected
═══════════════════════════════════════════════════════════════
```

**Interpretation:** The observed 85% is statistically inconsistent with the 93.5% baseline. There's a 5% chance this is a false positive.

### The Critical Qualification

A "FAILED" result does not mean "definitely broken."

It means: "The observed behavior is statistically inconsistent with the baseline at the configured confidence level."

With 95% confidence, if there's no real degradation, there's still a 5% chance of seeing a failure (false alarm). This is the fundamental trade-off of statistical testing.

---

## When to Re-Run Experiments

Update your spec when:

- **Major model updates**: New LLM version, significant prompt changes
- **Intentional improvements**: System should perform better
- **Baseline staleness**: Every 3-6 months for actively changing systems
- **After production incidents**: To recalibrate expectations

```bash
# Re-run experiment (overwrites existing spec)
./gradlew measure --tests "JsonGenerationExperiment"

# Review changes
git diff src/test/resources/punit/specs/

# Commit updated spec
git commit -am "Update JSON generation baseline after prompt improvements"
```

---

## EXPLORE Mode: Configuration Discovery

When you have choices about how to configure a non-deterministic system (model, temperature, prompt), use EXPLORE mode to compare options:

```java
@TestTemplate
@ExploreExperiment(
    useCase = JsonGenerationUseCase.class,
    samplesPerConfig = 20,
    experimentId = "model-comparison-v1"
)
@FactorSource(value = "modelConfigs", factors = {"model"})
void explore(
        JsonGenerationUseCase useCase,
        @Factor("model") String model,
        ResultCaptor captor) {
    captor.record(useCase.generateJson("Generate a profile"));
}

static List<FactorArguments> modelConfigs() {
    return FactorArguments.configurations()
        .names("model")
        .values("gpt-4")
        .values("gpt-3.5-turbo")
        .stream().toList();
}
```

```bash
./gradlew exp -Prun=MyExperiment.explore
```

EXPLORE is for **rapid feedback** before committing to expensive measurements. Compare results, then OPTIMIZE or MEASURE the winner.

---

## OPTIMIZE Mode: Factor Tuning

After EXPLORE identifies a promising configuration, use OPTIMIZE to fine-tune a specific factor:

```java
@TestTemplate
@OptimizeExperiment(
    useCase = JsonGenerationUseCase.class,
    controlFactor = "temperature",
    initialControlFactorSource = "startingTemperature",
    scorer = SuccessRateScorer.class,
    mutator = TemperatureMutator.class,
    objective = OptimizationObjective.MAXIMIZE,
    samplesPerIteration = 20,
    maxIterations = 10,
    noImprovementWindow = 3
)
void optimizeTemperature(
        JsonGenerationUseCase useCase,
        @ControlFactor("temperature") Double temperature,
        ResultCaptor captor) {
    captor.record(useCase.generateJson("Generate a profile"));
}

static Double startingTemperature() {
    return 1.0;  // Start high, optimize down
}
```

```bash
./gradlew exp -Prun=MyExperiment.optimizeTemperature
```

OPTIMIZE iteratively refines a **control factor** through mutation and evaluation. Use it to find the optimal temperature, prompt phrasing, or other continuous parameters before establishing your baseline with MEASURE.

---

## Summary

| Step            | Command             | Output                                  |
|-----------------|---------------------|-----------------------------------------|
| Define use case | —                   | `UseCase<F, I, O>` class                |
| Run experiment  | `./gradlew measure` | Spec file                               |
| Commit spec     | `git commit`        | Version-controlled baseline             |
| Run tests       | `./gradlew test`    | Qualified verdicts (VERIFICATION/SMOKE) |

**The key insight:** PUnit doesn't eliminate uncertainty—it quantifies it. Every verdict comes with statistical context and a clear statement of intent, enabling informed decisions about whether to act on test results.

---

## Production Readiness

Taking a system characterized by uncertainty from prototype to production involves two distinct phases:

### Phase A: Prepartion

The goal is to find the best configuration and understand raw system behavior.

| Step | Activity                                 | Purpose                                          |
|------|------------------------------------------|--------------------------------------------------|
| 1    | **EXPLORE** factors (model, temperature) | Find the best configuration for your price-point |
| 2    | **OPTIMIZE** the system prompt           | Maximize reliability with iterative refinement   |
| 3    | **MEASURE** raw baseline                 | Quantify how well the optimized system performs  |

At the end of Phase A, you have an optimized configuration and a baseline that reflects the system's true reliability—warts and all.

### Phase B: Hardening

The goal is to improve user-facing reliability and establish regression protection.

| Step | Activity                      | Purpose                                                   |
|------|-------------------------------|-----------------------------------------------------------|
| 4    | Add **hardening mechanisms**  | Improve reliability without changing the underlying model |
| 5    | **MEASURE** hardened baseline | Quantify the improvement (and its cost)                   |
| 6    | Add **probabilistic tests**   | Protect against regression using both baselines           |

**Why measure before and after hardening?**

The pre-hardening baseline (Phase A) tells you how well the underlying system performs. The post-hardening baseline (Phase B) tells you what users actually experience. Keeping both baselines enables you to detect if the underlying model degrades—even when hardening masks it at the user level.

**What does hardening mean for LLMs?**

For LLM-based systems, hardening typically involves retrying failed invocations. A simple retry repeats the same request; a smarter approach includes the previous (invalid) response in the retry prompt, guiding the model toward a valid response. This improves user-facing reliability at the cost of additional tokens.

### When to Re-Run Each Phase

- **Re-run Phase A** when you change models, significantly modify prompts, or suspect the underlying system has changed.
- **Re-run Phase B** when you modify hardening logic or want to re-baseline user-facing reliability.
- **Run Phase B tests continuously** in CI to detect regressions.
