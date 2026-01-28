# Operational Flow: From Requirement to Confidence

This document describes the end-to-end workflow for using **PUnit** to test non-deterministic systems—systems that don't always produce the same output for the same input.

---

## Overview

When a system behaves non-deterministically (LLMs, distributed systems, randomized algorithms), traditional pass/fail testing breaks down. A test might pass today and fail tomorrow—not because the system changed, but because of inherent randomness.

PUnit provides a disciplined workflow that:

1. **Expresses** requirements as statistical thresholds (from SLAs or empirical data)
2. **Executes** multiple samples to gather evidence
3. **Evaluates** results with proper statistical context
4. **Reports** qualified verdicts (not just PASS/FAIL, but with confidence)

---

## The Two Testing Paradigms

PUnit supports two complementary approaches to defining thresholds:

### SLA-Driven Testing

The threshold comes from an **external requirement**—a contract, policy, or SLO:

```java
@ProbabilisticTest(
    samples = 100,
    minPassRate = 0.999,        // From SLA: "99.9% uptime"
    thresholdOrigin = ThresholdOrigin.SLA,
    contractRef = "Customer API SLA §3.1"
)
void apiMeetsSla() { ... }
```

**Workflow:** Requirement → Test → Verify

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

Regardless of which paradigm you use, you must decide **how to parameterize** your test. There are three approaches:

> **See also:** [`ShoppingAssistantSlaExample.java`](../src/test/java/org/javai/punit/examples/ShoppingAssistantSlaExample.java) for a complete working example demonstrating all three approaches.

### Approach 1: Sample-Size-First (Cost-Driven)

*"We can afford 100 samples per run. Given our threshold, what confidence does that achieve?"*

```java
@ProbabilisticTest(
    minPassRate = 0.999,   // SLA or spec-derived threshold
    samples = 100          // Fixed by budget/time constraints
)
```

**What happens:**
- PUnit runs exactly 100 samples
- Computes implied confidence and detection power
- Reports what conclusions the evidence supports

**Trade-off:** You accept whatever statistical power 100 samples provides. Good for detecting large regressions; less sensitive to small degradations.

**Best for:** Continuous monitoring, CI pipelines, rate-limited APIs.

### Approach 2: Confidence-First (Risk-Driven)

*"We need 95% confidence that a pass is meaningful. How many samples?"*

```java
@ProbabilisticTest(
    minPassRate = 0.999,
    confidence = 0.95,           // How sure we need to be
    power = 0.80,                // Probability of detecting a real problem
    minDetectableEffect = 0.002  // Smallest degradation we care about
)
```

**What happens:**
- PUnit computes required sample size from statistical parameters
- Runs that many samples (may be large)
- Provides guaranteed confidence and power

**Trade-off:** Sample size is determined by statistics, not budget. May require many samples for tight thresholds.

**Best for:** Safety-critical systems, compliance audits, pre-release assurance.

### Approach 3: Threshold-First (Baseline-Anchored)

*"We want to test against the exact experimental rate."*

```java
@ProbabilisticTest(
    useCase = MyUseCase.class,   // Spec provides empirical data
    samples = 100,
    minPassRate = 0.935          // Raw experimental rate
)
```

**What happens:**
- PUnit uses the explicit threshold
- Computes implied confidence
- Warns if false positive rate is high

**Trade-off:** Using the raw experimental rate as threshold means ~50% of legitimate tests will fail due to sampling variance.

**Best for:** Organizations learning the trade-offs, or deliberately accepting strict thresholds.

### Choosing Your Approach

**In practice, choose ONE approach and use it consistently across your organization.**

| If Your Priority Is...              | Use...            | You're Saying...                          |
|-------------------------------------|-------------------|-------------------------------------------|
| Controlling costs (CI time, API)    | Sample-Size-First | "We can afford N samples. What do we get?"|
| Minimizing risk (safety, compliance)| Confidence-First  | "We need X% confidence. What does it cost?"|
| Learning the trade-offs             | Threshold-First   | "Show me exactly what's happening."       |

All three approaches work with both SLA-driven and spec-driven testing. The approach determines **how** you parameterize—not **where** the threshold comes from.

> **Working example:** See [`ShoppingAssistantSlaExample.java`](../src/test/java/org/javai/punit/examples/ShoppingAssistantSlaExample.java) for a complete demonstration of all three approaches with extensive commentary.

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

A **use case** is a reusable function that invokes your production code and captures observations:

```java
@UseCase("usecase.json.generation")
public UseCaseResult generateJson(String prompt, UseCaseContext context) {
    LlmResponse response = llmClient.complete(prompt);
    boolean isValid = JsonValidator.isValid(response.getContent());
    
    return UseCaseResult.builder()
        .value("isValidJson", isValid)
        .value("tokensUsed", response.getTokensUsed())
        .build();
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

| Step | Command | Output |
|------|---------|--------|
| Define use case | — | `@UseCase` class |
| Run experiment | `./gradlew measure` | Spec file |
| Commit spec | `git commit` | Version-controlled baseline |
| Run tests | `./gradlew test` | Qualified pass/fail verdicts |

**The key insight:** PUnit doesn't eliminate uncertainty—it quantifies it. Every verdict comes with statistical context, enabling informed decisions about whether to act on test results.
