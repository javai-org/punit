# PUnit User Guide

*Probabilistic testing for systems characterized by uncertainty*

All attribution licensing is ARL.

---

> **A spec is a declarative description of a data-generating process.**
> **Running it realises a sample from that process.**
>
> Hold this framing in mind throughout the guide — it is the shape
> every probabilistic test and every experiment in PUnit takes, and
> it is what separates probabilistic testing from the single-execution
> premise of traditional unit testing.

---

## Table of Contents

- [Introduction](#introduction)
  - [The Problem: When Binary Testing Fails](#the-problem-when-binary-testing-fails)
  - [What is PUnit?](#what-is-punit)
  - [Quick Start](#quick-start)
  - [Running the Examples](#running-the-examples)
- [Part 1: The Probabilistic Test](#part-1-the-probabilistic-test)
  - [Testing Against a Known Threshold](#testing-against-a-known-threshold)
  - [Threshold Provenance](#threshold-provenance)
  - [Sample Sizing for Verification](#sample-sizing-for-verification)
  - [The Parameter Triangle](#the-parameter-triangle)
  - [Threshold Approaches](#threshold-approaches)
  - [Understanding Test Results](#understanding-test-results)
  - [The Latency Attribute](#the-latency-attribute)
- [Part 2: The MEASURE Experiment](#part-2-the-measure-experiment)
  - [When No Normative Threshold Exists](#when-no-normative-threshold-exists)
  - [Running a MEASURE Experiment](#running-a-measure-experiment)
  - [The Spec: A Baseline for Regression Testing](#the-spec-a-baseline-for-regression-testing)
  - [Conformance Testing with Specs](#conformance-testing-with-specs)
  - [Baseline Expiration](#baseline-expiration)
- [Part 3: The Use Case](#part-3-the-use-case)
  - [Why Experiments and Tests Must Share the Same Target](#why-experiments-and-tests-must-share-the-same-target)
  - [The UseCase Interface](#the-usecase-interface)
  - [Postconditions: Defining the Contract](#postconditions-defining-the-contract)
  - [TokenTracker: The Cost Channel](#tokentracker-the-cost-channel)
  - [UseCaseOutcome: What the Framework Assembles](#usecaseoutcome-what-the-framework-assembles)
  - [Use Case Metadata](#use-case-metadata)
  - [Per-Sample Latency Bounds](#per-sample-latency-bounds)
  - [Warmup: Achieving Statistical Stationarity](#warmup-achieving-statistical-stationarity)
  - [DI integration: Spring, Guice, and friends](#di-integration-spring-guice-and-friends)
- [Part 4: The EXPLORE Experiment](#part-4-the-explore-experiment)
  - [When to Use EXPLORE](#when-to-use-explore)
  - [Comparing Configurations](#comparing-configurations)
  - [Multi-Factor Exploration](#multi-factor-exploration)
- [Part 5: The OPTIMIZE Experiment](#part-5-the-optimize-experiment)
  - [When to Use OPTIMIZE](#when-to-use-optimize)
  - [Optimizing Temperature](#optimizing-temperature)
  - [Optimizing Prompts](#optimizing-prompts)
  - [Scorers and Mutators](#scorers-and-mutators)
- [Part 6: The Complete Workflow](#part-6-the-complete-workflow)
  - [From Discovery to Regression Protection](#from-discovery-to-regression-protection)
  - [Covariate-Aware Baseline Selection](#covariate-aware-baseline-selection)
    - [Automatic Complement Labels](#automatic-complement-labels)
- [Part 7: Resource Management](#part-7-resource-management)
  - [Budget Control](#budget-control)
  - [Pacing Constraints](#pacing-constraints)
  - [Exception Handling](#exception-handling)
- [Part 8: Latency](#part-8-latency)
  - [The Problem with Averages](#the-problem-with-averages)
  - [Latency Recording in Experiments](#latency-recording-in-experiments)
  - [Testing Latency with Contractual Thresholds](#testing-latency-with-contractual-thresholds)
  - [Baseline-Derived Thresholds](#baseline-derived-thresholds)
  - [The Three Latency Mechanisms at a Glance](#the-three-latency-mechanisms-at-a-glance)
  - [Sample Size and Percentile Reliability](#sample-size-and-percentile-reliability)
- [Part 9: The Statistical Core](#part-9-the-statistical-core)
  - [Bernoulli Trials](#bernoulli-trials)
  - [Latency: Empirical Percentiles with Confidence Bounds](#latency-empirical-percentiles-with-confidence-bounds)
  - [Transparent Statistics Mode](#transparent-statistics-mode)
  - [Further Reading](#further-reading)
- [Part 10: The Sentinel](#part-10-the-sentinel)
  - [Module Decomposition and Artifact Selection](#module-decomposition-and-artifact-selection)
  - [The Reliability-Specification-First Model](#the-reliability-specification-first-model)
  - [UseCaseFactory and UseCaseProvider](#usecasefactory-and-usecaseprovider)
  - [Configuring and Running the Sentinel](#configuring-and-running-the-sentinel)
  - [Verdicts as Triage Signals](#verdicts-as-triage-signals)
- [Part 11: The HTML Report](#part-11-the-html-report)
  - [Generating the Report](#generating-the-report)
  - [Report Location and Contents](#report-location-and-contents)
  - [Configuration](#report-configuration)
- [Appendices](#appendices)
  - [A: Configuration Reference](#a-configuration-reference)
  - [B: Experiment Output Formats](#b-experiment-output-formats)
  - [C: Glossary](#c-glossary)

---

## Introduction

### The Problem: When Binary Testing Fails

Traditional unit testing is built on a binary premise: call a function, assert the result, pass or fail. This works brilliantly for deterministic systems where the same input always produces the same output. But an entire class of modern systems does not behave this way:

- **LLM integrations** — Model outputs vary with temperature, prompt phrasing, or simply from call to call
- **ML model inference** — Predictions may have confidence thresholds that occasionally miss
- **Distributed systems** — Network conditions, timing, and race conditions introduce variability
- **Randomized algorithms** — By design, outputs differ across executions

Consider what happens when you test an LLM integration the traditional way:

```java
@Test
void shouldReturnValidJson() {
    String result = llmClient.translate("Add 2 apples");
    assertThat(result).matches(JSON_PATTERN);  // Sometimes fails!
}
```

This test will fail occasionally. And what do we learn from the failure? **Nothing useful.** It merely confirms what we already know: we are dealing with non-determinism. The test teaches us nothing about the *rate* of failure or whether that rate is acceptable.

The fundamental question is not "Does it work?" but rather **"How often does it work, and is that often enough?"**

The entire pass/fail culture and infrastructure of traditional unit testing — the assertion that aborts on first failure, the test runner that reports a red bar — is simply the wrong tool for stochastic behaviour. We need a different approach.

### What is PUnit?

PUnit is a JUnit 5 extension framework for **probabilistic testing**. It addresses the shortcomings of binary testing by running tests multiple times and determining pass/fail based on **statistical thresholds** rather than single-execution assertions. To be clear: PUnit is not a replacement for traditional unit testing. It is specifically designed for testing features which exhibit stochastic (aka random) behaviours. If a feature is fully deterministic, it is not a candidate for probabilistic testing.

Stochastic behaviour manifests in two dimensions:

- **Functional** — whether the system produces a correct result. An LLM may return valid JSON 93% of the time; a classifier may achieve 97% accuracy. The success rate varies across executions.
- **Temporal** — how long the system takes to respond. A service that averages 200ms may occasionally spike to 2 seconds. Latency is inherently variable, and a single measurement tells you very little about what to expect.

PUnit addresses both. Pass-rate assertions handle functional stochasticity; latency assertions (see [Part 8: Latency](#part-8-latency)) handle temporal stochasticity. The two are evaluated independently — a service can pass on reliability but fail on latency, or vice versa.

But assuming a feature can behave non-deterministically, instead of asking "Did it work?" PUnit asks "Does it work reliably enough — and fast enough?"

PUnit provides:

1. **Probabilistic tests** — Run a test method many times, count successes, and evaluate the observed success rate against a threshold
2. **Latency assertions** — Evaluate observed percentile latencies (p50, p90, p95, p99) against contractual or baseline-derived thresholds
3. **Experiments** — Measure, explore, and optimize system behaviour to discover empirical thresholds
4. **Statistical rigour** — Proper confidence intervals, power analysis, and qualified verdicts

### Quick Start

**Gradle setup:**

```kotlin
// build.gradle.kts
plugins {
    id("org.javai.punit") version "0.1.0"
}

dependencies {
    testImplementation("org.javai:punit:0.1.0")
}
```

The `org.javai.punit` plugin automatically registers `experiment` and `exp` tasks for running experiments, configures the `test` task to exclude experiment-tagged tests, and supports `-Prun=` shorthand for filtering. Maven users should configure Surefire/Failsafe manually — see [MAVEN-CONFIGURATION.md](MAVEN-CONFIGURATION.md).

**The simplest probabilistic test:**

```java
@ProbabilisticTest(
    samples = 100,
    minPassRate = 0.95
)
void apiMeetsReliabilityTarget() {
    String result = llmClient.translate("Add 2 apples");
    assertThat(result).matches(JSON_PATTERN);
}
```

This test runs 100 samples. Each sample calls the LLM and asserts the result. Instead of aborting on the first failure, PUnit counts successes across all samples. The test passes if at least 95% of samples succeed. Behind this simplicity lies a rigorous statistical engine — confidence intervals, power analysis, and qualified verdicts — that the following sections will progressively reveal.

### Running the Examples

This guide uses examples from the **punitexamples** project — a separate repository that demonstrates PUnit's capabilities using a shopping basket and payment gateway domain. Clone it alongside PUnit:

```bash
git clone https://github.com/javai-org/punitexamples.git
```

The punitexamples project uses a Gradle composite build to reference the local PUnit framework. Experiments and tests are ready to run without modification.

**Running experiments:**

```bash
./gradlew exp -Prun=ShoppingBasketMeasure
./gradlew exp -Prun=ShoppingBasketExplore.compareModels
./gradlew exp -Prun=ShoppingBasketOptimizeTemperature
```

The `exp` task includes only experiment-tagged tests, so experiments and regular tests are naturally separated — no need to disable or enable anything.

**Running tests:**

```bash
./gradlew test --tests "ShoppingBasketTest"
./gradlew test --tests "PaymentGatewaySlaTest"
```

---

## Part 1: The Probabilistic Test

The probabilistic test is PUnit's central concept. It is the natural starting point because, in its simplest form, it requires nothing beyond a threshold and a sample size — no experiments, no baselines, no supporting artifacts.

### Testing Against a Known Threshold

The simplest scenario: you have a **normative threshold** — a mandated standard that defines the required success rate. This might come from:

- A contractual SLA with a customer
- An internal SLO for a service
- A quality policy that mandates minimum reliability

In this case, the threshold is given to you. You do not need to discover it empirically. But before you can write the test, you must decide what kind of claim you are making about the service. PUnit distinguishes between two **test intents**:

- **`TestIntent.VERIFICATION`** (default): An evidential claim. PUnit requires the sample size to be large enough to verify the threshold at 95% confidence. If the sample size is too small, PUnit **rejects the test configuration** before any samples run. This protects you from drawing conclusions that the data cannot support.
- **`TestIntent.SMOKE`**: A lightweight early-warning check. PUnit accepts that the sample size may be too small for a statistically rigorous conclusion, but runs the test anyway as a quick sanity check. The verdict is labelled as SMOKE, making clear that it is not a full verification.

In practice, a true VERIFICATION of a high threshold (say 95% or above) requires a substantial number of samples. For many situations — CI pipelines, quick pre-deployment checks, rate-limited APIs — running thousands of samples is impractical. In these cases, a SMOKE test is the appropriate choice: it can catch catastrophic failures cheaply, even if it cannot provide the statistical power of a full verification.

Here is a smoke test against an SLA:

```java
@ProbabilisticTest(
    samples = 100,
    minPassRate = 0.95,
    intent = TestIntent.SMOKE,  // 100 samples is not enough to verify 95% at high confidence
    thresholdOrigin = ThresholdOrigin.SLA,
    contractRef = "API SLA §3.1"
)
void apiMeetsSla() {
    String result = apiClient.call(new Request(...));
    assertThat(result).isNotNull();
    assertThat(result.statusCode()).isEqualTo(200);
}
```

This test runs 100 samples. Each sample calls the API and asserts the result. PUnit counts successes and evaluates whether the observed rate meets the 95% threshold.

```
═ VERDICT: PASS (SMOKE) ══════════════════════════════════════════════ PUnit ═

  apiMeetsSla

  Observed pass rate:  0.9700 (97/100) >= required: 0.9500
  Threshold origin:    SLA
  Contract:            API SLA §3.1
  Elapsed:             1234ms

══════════════════════════════════════════════════════════════════════════════
```

The `thresholdOrigin` and `contractRef` attributes document where the threshold came from. This provenance is part of the test's output, so anyone reading the results can trace the threshold back to its source.

To run a full VERIFICATION — one that provides genuine statistical evidence that the service meets its SLA — you would omit the `intent` attribute (VERIFICATION is the default) and provide a sample size large enough for PUnit to accept the configuration. The required sample size depends on the threshold; higher thresholds demand more samples to verify.

*Source: `org.javai.punit.examples.probabilistictests.PaymentGatewaySlaTest`*

### Threshold Provenance

The `thresholdOrigin` attribute documents where the threshold came from. Origins marked as **normative** (SLA, SLO, POLICY) trigger PUnit's strict sample-size enforcement for VERIFICATION tests.

| Origin        | Normative? | Description                                   |
|---------------|------------|-----------------------------------------------|
| `SLA`         | Yes        | Contractual commitment to external customers  |
| `SLO`         | Yes        | Internal service quality target               |
| `POLICY`      | Yes        | Organisational quality mandate                |
| `EMPIRICAL`   | No         | Derived from measurement data                 |
| `UNSPECIFIED` | No         | No provenance declared                        |

### Sample Sizing for Verification

When testing high thresholds (99.9%+), sample size matters significantly:

| Samples | Can Detect Deviation Of |
|---------|-------------------------|
| 1,000   | ~1%                     |
| 10,000  | ~0.1%                   |
| 100,000 | ~0.03%                  |

To detect that a system is at 99.97% when the SLA requires 99.99%, you need enough samples to distinguish a 0.02% difference. Even with 1,000 samples, this gap is statistically invisible.

### The Parameter Triangle

**This is essential to grasp before proceeding.**

PUnit operates with three interdependent parameters. You control **two**; statistics determines the third:

```
        Sample Size (cost/time)
               /\
              /  \
             /    \
            /      \
    Confidence ──── Threshold
    (how sure)      (how strict)
```

| You Fix            | And Fix     | Statistics Determines |
|--------------------|-------------|-----------------------|
| Sample size        | Threshold   | Confidence level      |
| Sample size        | Confidence  | Achievable threshold  |
| Confidence + Power | Effect size | Required samples      |

This is not a PUnit limitation — it is fundamental to statistical inference. PUnit makes these trade-offs explicit and computable.

### Threshold Approaches

Three operational modes for a probabilistic test, distinguished by which knob the author fixes first. The first two use an **empirical** threshold (the baseline rate, resolved at runtime, with a Wilson lower bound applied to the run's observed rate at the configured confidence). The third uses a **contractual** threshold (an externally-fixed SLA / SLO / policy figure, with a deterministic `observed >= threshold` comparison — no Wilson margin).

**1. Sample-Size-First (Budget-Driven)**

*"I have a sample budget of 100. Run them against the recorded baseline."*

- **You specify**: sample size.
- **PUnit applies**: the Wilson lower bound at the default confidence (0.95) to the run's observed rate, and passes iff that lower bound clears the baseline rate read from the matched spec.

```java
@ProbabilisticTest
void sampleSizeFirst() {
    PUnit.testing(this::baseline)
            .samples(100)
            .criterion(BernoulliPassRate.empirical())
            .assertPasses();
}
```

**2. Confidence-First (Power-Driven)**

*"I need to detect a 5-percentage-point regression with 80% power at 95% confidence. How many samples?"*

- **You specify**: target confidence + power + minimum detectable effect (MDE).
- **PUnit derives**: the required sample size via `PowerAnalysis.sampleSize(baseline, mde, power)`. The verdict still applies the Wilson lower bound, but N is sized so a real regression of size MDE has the configured probability of failing the bound.

The MDE is essential — without it the question "how many samples do I need?" has no finite answer (detecting arbitrarily small degradations requires arbitrarily many samples).

```java
@ProbabilisticTest
void confidenceFirst() {
    int n = PowerAnalysis.sampleSize(this::baseline, 0.05, 0.80);

    PUnit.testing(this::baseline)
            .samples(n)
            .criterion(BernoulliPassRate.empirical().atConfidence(0.95))
            .assertPasses();
}
```

**3. Threshold-First (Externally-Dictated)**

*"The threshold is 0.90, dictated by SLA. Verify against it."*

- **You specify**: a contractual threshold and its provenance (`SLA`, `SLO`, or `POLICY`).
- **PUnit applies**: a deterministic `observed >= threshold` comparison. No baseline is involved; no Wilson margin. The provenance is recorded on the verdict for audit traceability.

```java
@ProbabilisticTest
void thresholdFirst() {
    PUnit.testing(ShoppingBasketUseCase.sampling(INSTRUCTIONS, 100), LlmTuning.DEFAULT)
            .criterion(BernoulliPassRate.meeting(0.90, ThresholdOrigin.SLA))
            .assertPasses();
}
```

> **Antipattern: pinning a contractual threshold to a baseline's observed rate.** Reading a baseline file by eye and pasting its observed rate into `BernoulliPassRate.meeting(0.935, ThresholdOrigin.EMPIRICAL)` looks like the empirical-pair pattern but isn't. The contractual path is deterministic — `observed >= 0.935` — and natural sampling variance puts the next run's observed rate below 0.935 roughly half the time even when the SUT is performing exactly at baseline. Result: a roughly coin-flip false-fail rate. The proper baseline-comparison path is `BernoulliPassRate.empirical()`, which resolves the baseline at runtime, applies the Wilson lower bound at the configured confidence, and gives the test the statistical buffer that the hardcoded contractual approach is missing.

*Source: `org.javai.punit.examples.probabilistictests.ShoppingBasketThresholdApproachesTest`.*

### Understanding Test Results

**How PUnit surfaces probabilistic results in JUnit**

A probabilistic test has two layers of feedback:

- **Sample-level failures**: individual sample failures are surfaced so you can inspect real examples of where the system failed.
- **Statistical verdict**: the overall PASS/FAIL is computed from the observed pass rate and the configured threshold.

In other words: sample failures are expected and informative; the statistical verdict is the actual decision.

**Reading the statistical report**

Every probabilistic test produces a verdict with statistical context:

```
═ VERDICT: PASS (VERIFICATION) ═══════════════════════════════════════ PUnit ═

  testInstructionTranslation

  Observed pass rate:  0.9400 (94/100) >= required: 0.9190
  Elapsed:             5765ms

══════════════════════════════════════════════════════════════════════════════
```

**Responding to results**

| Verdict    | What it means                             | Recommended response                  |
|------------|-------------------------------------------|---------------------------------------|
| **PASSED** | Observed rate is consistent with baseline | Low priority; likely no action needed |
| **FAILED** | Observed rate is below expected threshold | Investigate; possible regression      |

The report provides the evidence; operators provide the judgment.

### The Latency Attribute

Every `@ProbabilisticTest` has an optional `latency` attribute that controls whether and how PUnit evaluates response-time percentiles alongside the pass-rate verdict. Three situations determine how the attribute should be used:

**1. Omit `latency` when using a baseline spec**

When the test references a use case whose baseline spec contains latency data, PUnit **automatically** derives latency thresholds — no annotation is needed:

```java
@ProbabilisticTest(
    useCase = PaymentGatewayUseCase.class,
    samples = 200
)
void paymentServiceConformsToBaseline() {
    paymentService.processPayment(testPayment()).assertAll();
}
```

PUnit derives both the pass-rate threshold and the latency thresholds from the spec. If the spec has no latency section, latency is simply not evaluated.

**2. Set explicit thresholds for contractual latency targets**

When you have a known SLA or SLO that prescribes response-time limits, declare them directly:

```java
@ProbabilisticTest(
    samples = 200,
    minPassRate = 0.95,
    latency = @Latency(p95Ms = 500, p99Ms = 1000)
)
void paymentServiceMeetsLatencySla() {
    PaymentResult result = paymentService.processPayment(testPayment());
    assertThat(result.isSuccessful()).isTrue();
}
```

The `@Latency` annotation supports four percentiles: `p50Ms`, `p90Ms`, `p95Ms`, and `p99Ms`. Declare only the ones you care about — unset percentiles (default `-1`) are not asserted.

Explicit thresholds are **enforced by default** — breaches fail the test without requiring any global flag. The annotation is the developer's declaration of intent; no second opt-in is needed.

> **Precedence rule:** When explicit `@Latency` thresholds are declared and the baseline spec also contains latency data, the explicit thresholds take precedence — the baseline latency data is ignored. This allows you to use a baseline for pass-rate derivation while asserting latency against your own contractual targets.

**3. Use `@Latency(disabled = true)` to opt out**

When a baseline spec contains latency data but you only want to assert pass-rate, disable latency evaluation explicitly:

```java
@ProbabilisticTest(
    useCase = PaymentGatewayUseCase.class,
    samples = 200,
    latency = @Latency(disabled = true)
)
void passRateOnlyTest() { ... }
```

Without this opt-out, PUnit would automatically evaluate latency thresholds derived from the baseline. The `disabled = true` flag does not prevent derivation — the thresholds already exist in the spec — it simply tells PUnit to ignore them.

**When none of the above applies** — inline threshold tests with no baseline and no explicit `@Latency` — the attribute can simply be omitted. PUnit evaluates pass-rate only.

For full details on latency mechanics, enforcement modes, and sample-size requirements, see [Part 8: Latency](#part-8-latency).

---

## Part 2: The MEASURE Experiment

### When No Normative Threshold Exists

Not every system comes with an SLA or a policy that prescribes its required success rate. For many systems — particularly LLM integrations, ML pipelines, and novel algorithms — nobody has declared what "good enough" looks like. The question is not "does it meet the SLA?" but rather "what success rate should we expect, and has it degraded?"

This is where the **MEASURE experiment** comes in. Its purpose is to run the system many times, observe its actual behaviour, and record the results as a **baseline**. That baseline then becomes the threshold for future probabilistic tests — not because someone mandated it, but because it reflects the system's empirically observed performance.

### Running a MEASURE Experiment

A MEASURE experiment typically runs many samples (1000+ recommended) to establish precise statistics.

**Running experiments:**

The `exp` task is provided by the PUnit Gradle plugin (see [Quick Start](#quick-start)). It includes only tests tagged with `punit-experiment`, deactivates `@Disabled` so experiments can run, and sets `ignoreFailures = true` since experiments are exploratory.

```bash
# Run all experiments in a class
./gradlew exp -Prun=ShoppingBasketMeasure

# Run a specific experiment method
./gradlew exp -Prun=ShoppingBasketMeasure.measureBaseline

# Traditional --tests syntax also works
./gradlew exp --tests "ShoppingBasketMeasure"
```

For Maven, use a dedicated profile with `<groups>punit-experiment</groups>` — see [MAVEN-CONFIGURATION.md](MAVEN-CONFIGURATION.md).

**Example with `@InputSource`:**

```java
@MeasureExperiment(
    useCase = ShoppingBasketUseCase.class,
    experimentId = "baseline-v1"
)
@InputSource("basketInstructions")
void measureBaseline(
    ShoppingBasketUseCase useCase,
    String instruction,
    OutcomeCaptor captor
) {
    captor.record(useCase.translateInstruction(instruction));
}

static Stream<String> basketInstructions() {
    return Stream.of(
        "Add 2 apples",
        "Remove the milk",
        "Clear the basket"
    );
}
```

**Using File-Based Input with Expected Values:**

For instance conformance testing, use a JSON file with expected values:

```java
record TranslationInput(String instruction, String expected) {}

@MeasureExperiment(
    useCase = ShoppingBasketUseCase.class,
    experimentId = "baseline-with-expected-v1"
)
@InputSource(file = "fixtures/shopping-instructions.json")
void measureBaselineWithExpected(
    ShoppingBasketUseCase useCase,
    TranslationInput input,
    OutcomeCaptor captor
) {
    captor.record(useCase.translateInstruction(input.instruction(), input.expected()));
}
```

**Input Cycling:**

Samples are distributed evenly across inputs. With 1000 samples and 10 inputs:

```
Sample 1    → "Add 2 apples"
Sample 2    → "Remove the milk"
...
Sample 10   → "Clear the basket"
Sample 11   → "Add 2 apples"  (cycles back)
...
Sample 1000 → (100th cycle completes)
```

Each instruction is tested exactly 100 times.

*Source: `org.javai.punit.examples.experiments.ShoppingBasketMeasure`*

### The Spec: A Baseline for Regression Testing

The output of a MEASURE experiment is a **spec** — a YAML file that captures everything about what was measured:

```
src/test/resources/punit/specs/ShoppingBasketUseCase.yaml
```

**A spec is more than a number.** It is a complete record of:

- **What** was measured (use case ID, success criteria)
- **When** it was measured (timestamp, expiration)
- **How many** samples were collected (empirical basis)
- **Under what conditions** (covariates: model, temperature, time of day, etc.)
- **Statistical confidence** (confidence intervals, standard error)

```yaml
# Example spec structure
specId: ShoppingBasketUseCase
generatedAt: 2026-01-15T10:30:00Z
empiricalBasis:
  samples: 1000
  successes: 935
covariates:
  llm_model: gpt-4o
  temperature: 0.3
  day_of_week: WEEKDAY
extendedStatistics:
  confidenceInterval: { lower: 0.919, upper: 0.949 }
```

**Committing Baselines:**

The developer is encouraged to commit baselines to the repository. By default, they are placed in the **test** folder (of the standard Gradle folder layout). This is because a probabilistic regression test uses the baseline as input. The test cannot be performed without it, and if it is not present in the CI environment, the test will alert operators to this by failing.

```bash
git add src/test/resources/punit/specs/
git commit -m "Add baseline for ShoppingBasket (93.5% @ N=1000)"
```

### Conformance Testing with Specs

Once a spec exists, you can write a probabilistic test that derives its threshold from the baseline. PUnit loads the matching spec and computes `minPassRate` from the empirical success rate plus a statistical margin. This is **conformance testing** — detecting when performance regresses below an empirically established baseline.

```java
@ProbabilisticTest(
    useCase = ShoppingBasketUseCase.class,
    samples = 100
)
@InputSource("standardInstructions")
void testInstructionTranslation(
    ShoppingBasketUseCase useCase,
    String instruction
) {
    useCase.translateInstruction(instruction).assertAll();
}

static Stream<String> standardInstructions() {
    return Stream.of(
        "Add 2 apples",
        "Remove the milk",
        "Clear the basket"
    );
}
```

Notice that no `minPassRate` is specified. PUnit derives it from the spec.

| Scenario                | Question                                   | Threshold Source                   |
|-------------------------|--------------------------------------------|------------------------------------|
| **Compliance Testing**  | Does the service meet a mandated standard? | SLA, SLO, or policy (prescribed)   |
| **Conformance Testing** | Has performance dropped below baseline?    | Empirical measurement (discovered) |

*Source: `org.javai.punit.examples.probabilistictests.ShoppingBasketTest`*

### Baseline Expiration

System usage and environmental changes mean that baseline data can become dated. To guard against this, PUnit allows you to specify an expiration date for the generated baseline.

```java
@MeasureExperiment(
    useCase = ShoppingBasketUseCase.class,
    samples = 1000,
    expiresInDays = 30  // Baseline valid for 30 days
)
```

An expired baseline can and will still be used by the probabilistic test, but the verdict will include a warning that the baseline may have drifted and the test's result should therefore be treated with caution.

```
═ VERDICT: PASS (VERIFICATION) ═══════════════════════════════════════ PUnit ═

  testInstructionTranslation

  Observed pass rate:  0.9400 (94/100) >= required: 0.9190
  Elapsed:             5765ms

══════════════════════════════════════════════════════════════════════════════
```

When the baseline has expired, PUnit prints a separate warning immediately after the verdict:

```
═ BASELINE EXPIRED ═══════════════════════════════════════════════════ PUnit ═

  The baseline used for statistical inference has expired.

  Baseline created:    2025-11-15 09:30 GMT
  Validity period:     30 days
  Expiration date:     2025-12-15 09:30 GMT
  Expired:             3 days ago

  Statistical inference is based on potentially stale empirical data.
  Consider running a fresh MEASURE experiment to update the baseline.

══════════════════════════════════════════════════════════════════════════════
```

---

## Part 3: The Use Case

### Why Experiments and Tests Must Share the Same Target

In the examples so far, we have seen probabilistic tests with inline assertions and MEASURE experiments that record outcomes. But there is a critical constraint that binds them together: **the experiment that establishes a baseline and the test that verifies against it must evaluate the same definition of success**.

If your MEASURE experiment measures "valid JSON with correct fields" but your probabilistic test only checks "non-empty response," you have learned nothing about whether the system meets its actual requirements. You would be measuring one thing and testing another.

PUnit therefore centres on an artefact called a **Use Case**. A use case wraps your application's functionality and declares — in one place — what to invoke, what counts as success, what the operational constraints are, and what environmental factors influence the outcome. Both experiments and tests exercise the same use case, ensuring that what you measure is exactly what you test.

This is the shared expression of correctness in PUnit:
- **Experiments** use the use case to observe and record behaviour.
- **Probabilistic Tests** use the same use case to verify that behaviour meets the threshold.

### The UseCase Interface

A use case is a Java class implementing `UseCase<FT, IT, OT>`. Three type parameters fix what the use case does:

| Parameter | Carries                                                                                       |
|-----------|-----------------------------------------------------------------------------------------------|
| `FT`      | The **factor record** — configuration the author has chosen to vary (model, temperature, …). Typically a `record`. |
| `IT`      | The **per-sample input** — data the engine cycles through across samples.                     |
| `OT`      | The **per-sample output value** — what the service returns when it succeeds.                  |

`UseCase<FT, IT, OT>` extends `Contract<IT, OT>`. The `Contract` half is operational — it carries the service call and the acceptance criteria. The `UseCase` half adds metadata — identity, covariate declarations, pacing, warmup. An author writing one use case implements one interface and overrides three methods (plus whichever metadata methods diverge from the framework defaults):

- **`invoke(IT input, TokenTracker tracker)`** — calls the service, records cost, returns the value wrapped in an `Outcome<OT>`. This is the only place the SUT is touched.
- **`postconditions(ContractBuilder<OT> b)`** — declares what counts as success. Called once per run; the resulting clause list is evaluated against every sample's output.
- Metadata methods (`id`, `description`, `warmup`, `pacing`, `covariates`, `customCovariateResolvers`, `maxLatency`) — describe the use case's identity and operational shape.

The framework constructs the use case once per factor configuration via the factory closure declared on the spec, then drives the sampling loop:

```java
public final class ShoppingBasketUseCase
        implements UseCase<ShoppingBasketUseCase.LlmTuning, String, String> {

    public record LlmTuning(String model, double temperature, String systemPrompt) {
        public static final LlmTuning DEFAULT = new LlmTuning("gpt-4o-mini", 0.3, "...");
    }

    private final ChatLlm llm;
    private final LlmTuning tuning;

    public ShoppingBasketUseCase(ChatLlm llm, LlmTuning tuning) {
        this.llm = llm;
        this.tuning = tuning;
    }

    @Override
    public Outcome<String> invoke(String instruction, TokenTracker tracker) {
        try {
            ChatResponse response = llm.chatWithMetadata(
                    tuning.systemPrompt(), instruction,
                    tuning.model(), tuning.temperature());
            tracker.recordTokens(response.totalTokens());
            return Outcome.ok(response.content());
        } catch (ChatLlmException e) {
            return Outcome.fail("llm-error", e.getMessage());
        }
    }

    @Override
    public void postconditions(ContractBuilder<String> b) {
        // see "Postconditions: Defining the Contract" below
    }

    @Override
    public String id() { return "shopping-basket"; }
}
```

The use case is immutable for the duration of sampling. Internal caches and connection handles are fine; live reconfiguration in response to sample outcomes is not. Mutating internal state mid-run violates the i.i.d. assumption that statistical inference depends on.

### Postconditions: Defining the Contract

`postconditions(ContractBuilder<OT> b)` declares what makes a sample a success. The framework calls it once per run, evaluates every clause it adds against each sample's output, and surfaces the per-clause pass/fail counts on the verdict.

A clause is one of two shapes:

- **Leaf** (`b.ensure`) — a check on the output value. The check returns `Outcome.ok()` or `Outcome.fail(name, reason)`.
- **Derived** (`b.deriving`) — a transformation of the output (e.g., parsing JSON) followed by nested clauses on the derived value. If the transformation itself fails, the nested clauses are skipped and recorded as such.

```java
@Override
public void postconditions(ContractBuilder<String> b) {
    b.ensure("Response not empty",
            response -> response == null || response.isBlank()
                    ? Outcome.fail("empty-response", "LLM returned no content")
                    : Outcome.ok());

    b.deriving("Valid JSON",
            ShoppingActionValidator::parse,
            sub -> sub.ensure("All actions valid for context", translation -> {
                for (ShoppingAction action : translation.actions()) {
                    if (!action.context().isValidAction(action.name())) {
                        return Outcome.fail("invalid-action",
                                "Invalid action '%s' for context %s"
                                        .formatted(action.name(), action.context()));
                    }
                }
                return Outcome.ok();
            }));
}
```

Every clause has a stable description (the first argument) — these strings are the keys in the per-clause failure histogram on `SampleSummary.failuresByPostcondition()`. Diagnostic output, the verdict XML's `<postcondition-failures>` block, and the OPTIMIZE meta-prompt all read those keys, so descriptions should be specific enough to act on.

**Why clauses, not assertions.** Traditional unit tests fail fast — the first assertion that doesn't hold aborts the test. That is exactly wrong for probabilistic testing. A single sample's failure is not a verdict; the verdict comes from the population. Clauses observe and record without aborting, so the framework can count successes across the whole sample population and apply the statistical test that decides PASS, FAIL, or INCONCLUSIVE.

**Why pass-failure-as-data.** The contract returns an `Outcome.Fail(name, reason)` for an anticipated failure rather than throwing. Anticipated failures (postcondition violations, validation errors, refusals) flow through the data channel; thrown exceptions are reserved for genuine defects (programming mistakes, misconfiguration, JVM-level catastrophe). The engine treats a thrown exception from `invoke` as a defect by default — see `ExceptionPolicy.ABORT_TEST` — because silently counting it as a sample failure would hide bugs.

### TokenTracker: The Cost Channel

Token cost — LLM provider tokens, payment-API call cost, or any per-sample cost — flows through the `TokenTracker` argument to `invoke`. The use case calls `tracker.recordTokens(n)` after each invocation; the framework accumulates those across samples, enforces token budgets when configured, and surfaces the total on the verdict.

```java
@Override
public Outcome<String> invoke(String instruction, TokenTracker tracker) {
    ChatResponse response = llm.chatWithMetadata(...);
    tracker.recordTokens(response.totalTokens());
    return Outcome.ok(response.content());
}
```

The token abstraction is open. LLM tokens are the canonical case, but the channel can carry any unit-of-cost: vendor-API credits, third-party service calls, an arbitrary "compute cost" your team has agreed on. Use cases that have no per-sample cost simply ignore the tracker.

### UseCaseOutcome: What the Framework Assembles

`UseCaseOutcome<IT, OT>` is the per-sample artefact the framework builds for every invocation. Authors do not construct it directly — `invoke` returns an `Outcome<OT>`, and the framework's `apply` dispatch wraps that with timing, cost diff, postcondition evaluation, and (where the test or experiment configures matching) instance comparison.

Recipients (the probabilistic test asserter, the baseline writer, the OPTIMIZE meta-prompt builder, the EXPLORE diff renderer) consume the outcome without reaching back to the use case for additional state. It carries:

- `result` — the outcome `invoke` returned (`Outcome.Ok<OT>` or `Outcome.Fail<OT>`).
- `contract` — the `Contract<IT, OT>` instance that judged this sample.
- `postconditionResults` — per-clause results from evaluating the contract; empty when `result` is `Outcome.Fail` (no value to evaluate).
- `match` — the optional instance-conformance result (when matching was configured on the spec).
- `tokens` — the per-sample token diff via `TokenTracker`.
- `duration` — wall-clock time of the `invoke` call.

The framework aggregates these into `SampleSummary` and `ProbabilisticTestResult` for the verdict pipeline; experiment-side consumers read the same shape.

### Use Case Metadata

Beyond the operational core, the `UseCase` interface exposes metadata that lives on the use case rather than on the spec — properties of the SUT itself, not of any one experiment or test:

| Method                      | Purpose                                                                                                                                              | Default                |
|-----------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------|
| `id()`                      | Stable identifier used in baseline filenames, logs, and diagnostics.                                                                                 | kebab-cased class name |
| `description()`             | Human-readable description for reports and logs.                                                                                                     | empty string           |
| `warmup()`                  | Number of warmup invocations to discard before counted samples begin (see [Warmup](#warmup-achieving-statistical-stationarity)).                     | `0`                    |
| `pacing()`                  | Rate and concurrency limits the engine must respect. Pacing belongs to the SUT — every test against the same service should respect the same limit. | `Pacing.unlimited()`   |
| `covariates()`              | Environmental variables outside developer control that influence outcomes (region, day-of-week, model version, …).                                   | empty list             |
| `customCovariateResolvers()`| Suppliers for any `CustomCovariate` declared by `covariates()`. Called once per run.                                                                 | empty map              |
| `maxLatency()`              | Optional per-sample wall-clock bound (see [Per-Sample Latency Bounds](#per-sample-latency-bounds)).                                                   | `Optional.empty()`     |

Each method is `default` on the interface — override only the ones whose default does not fit. A pure-Java use case with no factors, no covariates, and no rate limits implements just `invoke`, `postconditions`, and (optionally) `id`.

```java
@Override public String id() { return "shopping-basket"; }

@Override
public List<Covariate> covariates() {
    return List.of(
            Covariate.custom("llm_model", CovariateCategory.CONFIGURATION),
            Covariate.custom("temperature", CovariateCategory.CONFIGURATION));
}

@Override
public Map<String, Supplier<String>> customCovariateResolvers() {
    return Map.of(
            "llm_model", () -> tuning.model(),
            "temperature", () -> Double.toString(tuning.temperature()));
}

@Override public Pacing pacing() { return Pacing.unlimited(); }
```

Because metadata is a property of the SUT, it cannot be overridden per-test by system property or environment variable. Different SUTs have different operational characteristics; a global override would be incoherent.

### Per-Sample Latency Bounds

A use case may declare a per-sample wall-clock bound through `maxLatency()`. When set, the engine records a duration violation for any sample whose `invoke` call exceeds the bound. The sample's postcondition results are still collected — the violation is an additional facet, not a short-circuit — and the bound surfaces on the verdict alongside the postcondition histogram.

```java
@Override
public Optional<Duration> maxLatency() {
    return Optional.of(Duration.ofMillis(500));
}
```

Most use cases do *not* set a per-sample bound. Aggregate latency claims — "the 95th percentile must be under 200 ms" — belong on the test or experiment side via the `PercentileLatency` criterion (see Part 1). Per-sample bounds and percentile bounds answer two distinct questions:

- A **per-sample bound** says "every individual call must be fast enough." A use case where any single slow call is unacceptable (a payment authorisation that mustn't time out, a hard real-time path) declares it on the use case.
- A **percentile criterion** says "the population as a whole must be fast enough." This is the typical SLA shape — a few outliers are tolerable as long as the bulk of calls hit the target.

The two compose. A test exercising a use case with `maxLatency = 500ms` and a `PercentileLatency.empirical(P95)` criterion verifies both that no single call broke the per-sample ceiling *and* that the population's 95th percentile cleared the empirical baseline.

### Warmup: Achieving Statistical Stationarity

Statistical inference assumes that each sample is drawn from the same distribution. But for many systems under test, the first few invocations behave differently from steady state. Caches are cold, connection pools are empty, JIT compilation has not yet kicked in, and LLM provider rate-limit windows have not stabilised. These early invocations come from a *different* distribution — one characterised by higher latency, lower throughput, or different failure modes. Including them in the sample contaminates the data with a transient artefact that has nothing to do with the system's true reliability.

This is a **stationarity** problem. Probabilistic testing requires that the process generating outcomes is stationary — that is, its statistical properties do not change over the observation window. Cold-start effects violate this assumption.

The `warmup()` metadata method addresses this directly:

```java
public final class PaymentGatewayUseCase
        implements UseCase<Tier, Charge, PaymentResult> {

    @Override public int warmup() { return 5; }

    // First 5 invocations are discarded before counting begins.
}
```

When `warmup()` returns a value greater than zero, the framework executes that many additional invocations before the counted samples begin. Results from warmup invocations are silently discarded — they are not recorded to the aggregator and do not contribute to the observed pass rate, failure counts, latency percentiles, or any statistical analysis.

**Warmup is additive.** A test configured with 100 samples and `warmup = 5` executes 105 total invocations: 5 discarded, then 100 counted. The sample count in verdicts, specs, and reports reflects only the counted invocations.

#### Why warmup lives on the use case

Warmup is a property of the system under test, not the statistical method. A payment gateway that initialises a connection pool on first use needs warmup regardless of whether you are measuring, exploring, or running a regression test. Placing `warmup` on the use case ensures that:

1. **Spec coherence.** The MEASURE experiment and the probabilistic test that consumes its spec both discard the same number of invocations. If the experiment warms up but the test does not (or vice versa), the baseline and the test are observing different distributions, and the threshold is meaningless.

2. **Single source of truth.** There is one place to change the warmup count, and every experiment and test that references the use case inherits it automatically.

Because warmup reflects the SUT's architecture rather than a testing preference, there is deliberately no system property or environment variable override.

#### How warmup interacts with other features

| Feature                   | Behaviour during warmup                                                                                                                                                                                                           |
|---------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Budget (time, tokens)** | Warmup invocations consume budget normally. If the budget is exhausted during warmup, the test terminates before any counted samples execute.                                                                                     |
| **Early termination**     | Suppressed during warmup. Even if all warmup invocations fail, the framework proceeds to counted samples — warmup results carry no statistical weight.                                                                            |
| **Latency recording**     | Warmup latencies are not recorded. Only post-warmup invocations contribute to latency percentiles.                                                                                                                                |
| **Covariates**            | Warmup is itself an implicit CONFIGURATION covariate. A spec generated with `warmup = 5` will only match tests whose use case also declares `warmup = 5`, preventing accidental comparison of warmed-up and cold-start baselines. |
| **Pacing**                | Warmup invocations are subject to pacing constraints, ensuring they do not violate rate limits.                                                                                                                                   |

#### Choosing a warmup count

There is no universal formula. The right value depends on what the system needs to reach steady state:

- **Connection pool initialisation** — typically 1–3 invocations.
- **JIT compilation** — for JVM-based SUTs, 10–50+ invocations depending on the critical path.
- **LLM provider stabilisation** — usually 1–5 invocations to fill rate-limit windows and establish session routing.
- **Cache warming** — depends on cache topology; a single pass through the input space may suffice.

When in doubt, run a MEASURE experiment with `warmup = 0` and examine the latency profile. If the first few samples show conspicuously higher latency or lower pass rates, increase the warmup count until the distribution stabilises. The EXPLORE experiment can also be used to compare warmup values side by side.

The default is `warmup = 0` — no warmup invocations. Only add warmup when you have evidence of cold-start non-stationarity.

### DI integration: Spring, Guice, and friends

PUnit has no dependency on any DI framework. The integration is whatever produces a configured use case instance — and the integration point is the factory closure on `Sampling.Builder.useCaseFactory(...)`.

**Spring Boot:**

```java
@SpringBootTest
class ProductSearchTest {

    @Autowired
    ApplicationContext ctx;

    @ProbabilisticTest
    void searchMeetsBaseline() {
        PUnit.testing(
                Sampling.<Tuning, String, SearchResult>builder()
                        .useCaseFactory(tuning -> ctx.getBean(ProductSearchUseCase.class))
                        .inputs("phone", "laptop", "shoes")
                        .samples(100)
                        .build(),
                Tuning.DEFAULT)
            .criterion(BernoulliPassRate.empirical())
            .assertPasses();
    }
}
```

**Guice:**

```java
class ProductSearchTest {

    private static final Injector injector =
            Guice.createInjector(new AppModule(), new TestModule());

    @ProbabilisticTest
    void searchMeetsBaseline() {
        PUnit.testing(
                Sampling.<Tuning, String, SearchResult>builder()
                        .useCaseFactory(tuning -> injector.getInstance(ProductSearchUseCase.class))
                        .inputs("phone", "laptop", "shoes")
                        .samples(100)
                        .build(),
                Tuning.DEFAULT)
            .criterion(BernoulliPassRate.empirical())
            .assertPasses();
    }
}
```

The factory is called once per factor configuration. If your use case takes injected dependencies (an LLM client, a payment-gateway facade), let the container wire them — the factory just hands back the container-managed instance.

If the factor record needs to influence which bean is returned (e.g. a different LLM client per configuration), branch inside the factory:

```java
.useCaseFactory(tuning -> switch (tuning.provider()) {
    case OPENAI    -> ctx.getBean(OpenAiSearchUseCase.class);
    case ANTHROPIC -> ctx.getBean(AnthropicSearchUseCase.class);
})
```

There is nothing PUnit-specific about the integration. The factory closure is the contract; everything else is the framework you already use.

---

## Part 4: The EXPLORE Experiment

### When to Use EXPLORE

Before you can MEASURE a baseline, you need to know *what* to measure. If you have multiple candidate configurations — different models, temperatures, or prompts — you need a way to compare them and identify which one works best.

The EXPLORE experiment is designed for exactly this: **rapid, side-by-side comparison of configurations** with modest sample sizes. It is not meant to produce statistically rigorous baselines; it is meant to produce enough signal to make an informed choice.

Use EXPLORE when:

- You have multiple configurations to compare (models, temperatures, prompts)
- You want rapid feedback before committing to expensive measurements
- You are discovering what works, not yet measuring reliability

### Comparing Configurations

Register the use case with a factor-aware factory. PUnit constructs a fresh, immutable instance per configuration:

```java
@BeforeEach
void setUp() {
    provider.registerWithFactors(ShoppingBasketUseCase.class, factors -> {
        String model = factors.has("model") ? factors.getString("model") : "gpt-4o-mini";
        double temp = factors.has("temperature") ? factors.getDouble("temperature") : 0.1;
        return new ShoppingBasketUseCase(ChatLlmProvider.resolve(), model, temp,
                ShoppingBasketUseCase.DEFAULT_SYSTEM_PROMPT);
    });
}

@ExploreExperiment(
    useCase = ShoppingBasketUseCase.class,
    samplesPerConfig = 20,
    experimentId = "model-comparison-v1"
)
@FactorSource(value = "modelConfigurations", factors = {"model"})
void compareModels(
    ShoppingBasketUseCase useCase,
    @Factor("model") String model,
    OutcomeCaptor captor
) {
    // useCase already configured via registerWithFactors — no mutation
    captor.record(useCase.translateInstruction("Add 2 apples"));
}

public static Stream<FactorArguments> modelConfigurations() {
    return FactorArguments.configurations()
        .names("model")
        .values("gpt-4o-mini")
        .values("gpt-4o")
        .values("claude-3-5-haiku")
        .values("claude-3-5-sonnet")
        .stream();
}
```

**Output:**

EXPLORE produces one exploration file per configuration:

```
build/punit/explorations/ShoppingBasketUseCase/
├── model-gpt-4o-mini.yaml
├── model-gpt-4o.yaml
├── model-claude-3-5-haiku.yaml
├── model-claude-3-5-sonnet.yaml
└── ...
```

Compare with standard diff tools to identify the preferred configuration.

```bash
./gradlew exp -Prun=ShoppingBasketExplore.compareModels
```

*Source: `org.javai.punit.examples.experiments.ShoppingBasketExplore`*

### Multi-Factor Exploration

When the `registerWithFactors` factory handles multiple factors, the same factory serves both single-factor and multi-factor explorations:

```java
@ExploreExperiment(
    useCase = ShoppingBasketUseCase.class,
    samplesPerConfig = 20,
    experimentId = "model-temperature-matrix-v1"
)
@FactorSource(value = "modelTemperatureMatrix", factors = {"model", "temperature"})
void compareModelsAcrossTemperatures(
    ShoppingBasketUseCase useCase,
    @Factor("model") String model,
    @Factor("temperature") Double temperature,
    OutcomeCaptor captor
) {
    // useCase already configured with both model and temperature
    captor.record(useCase.translateInstruction("Add 2 apples"));
}

public static Stream<FactorArguments> modelTemperatureMatrix() {
    return FactorArguments.configurations()
        .names("model", "temperature")
        .values("gpt-4o", 0.0)
        .values("gpt-4o", 0.5)
        .values("gpt-4o", 1.0)
        .values("claude-3-5-sonnet", 0.0)
        .values("claude-3-5-sonnet", 0.5)
        .values("claude-3-5-sonnet", 1.0)
        .stream();
}
```

---

## Part 5: The OPTIMIZE Experiment

### When to Use OPTIMIZE

After EXPLORE has identified a promising configuration, you may want to fine-tune a specific parameter. Manual iteration — trying temperature 0.3, then 0.25, then 0.35, and so on — is slow and expensive.

OPTIMIZE automates this process. It iteratively refines a **control factor** through mutation and evaluation, converging on the value that maximises (or minimises) a scoring function.

Use OPTIMIZE when:

- EXPLORE has identified a promising configuration
- You want to automatically tune a single factor (temperature, prompt)
- Manual iteration would be too slow or expensive

### Optimizing Temperature

```java
@OptimizeExperiment(
    useCase = ShoppingBasketUseCase.class,
    controlFactor = "temperature",
    initialControlFactorSource = "naiveStartingTemperature",
    scorer = ShoppingBasketSuccessRateScorer.class,
    mutator = TemperatureMutator.class,
    objective = OptimizationObjective.MAXIMIZE,
    samplesPerIteration = 20,
    maxIterations = 11,
    noImprovementWindow = 5,
    experimentId = "temperature-optimization-v1"
)
void optimizeTemperature(
    ShoppingBasketUseCase useCase,
    @ControlFactor("temperature") Double temperature,
    OutcomeCaptor captor
) {
    captor.record(useCase.translateInstruction("Add 2 apples and remove the bread"));
}

static Double naiveStartingTemperature() {
    return 1.0;  // Start high, optimize down
}
```

### Optimizing Prompts

```java
@OptimizeExperiment(
    useCase = ShoppingBasketUseCase.class,
    controlFactor = "systemPrompt",
    initialControlFactorSource = "weakStartingPrompt",
    scorer = ShoppingBasketSuccessRateScorer.class,
    mutator = ShoppingBasketPromptMutator.class,
    objective = OptimizationObjective.MAXIMIZE,
    maxIterations = 10,
    noImprovementWindow = 3,
    experimentId = "prompt-optimization-v1"
)
@InputSource(file = "fixtures/shopping-instructions.json")
void optimizeSystemPrompt(
    ShoppingBasketUseCase useCase,
    @ControlFactor("systemPrompt") String systemPrompt,
    TranslationInput input,
    OutcomeCaptor captor
) {
    captor.record(useCase.translateInstruction(input.instruction(), input.expected()));
}

static String weakStartingPrompt() {
    return "You are a shopping assistant. Convert requests to JSON.";
}
```

**Note:** When using `@InputSource`, inputs are cycled via round-robin within each iteration. If `samplesPerIteration` is not specified, it defaults to the number of inputs (one pass through each input per iteration). If `samplesPerIteration` is explicitly set, the specified value is used and inputs cycle accordingly.

### Scorers and Mutators

- **Scorer** — Evaluates each iteration's aggregate results and returns a score
- **Mutator** — Generates new control factor values based on history

**Termination conditions:**

- `maxIterations` — Hard stop after N iterations
- `noImprovementWindow` — Stop if no improvement for N consecutive iterations

**Output:**

```
build/punit/optimizations/ShoppingBasketUseCase/
└── temperature-optimization-v1_20260119_103045.yaml
```

The output file contains the optimized configuration as well as the history of iterations and their results.

*Source: `org.javai.punit.examples.experiments.ShoppingBasketOptimizeTemperature`, `ShoppingBasketOptimizePrompt`*

---

## Part 6: The Complete Workflow

### From Discovery to Regression Protection

The preceding sections have introduced PUnit's concepts one at a time. In practice, they combine into a coherent workflow that takes a non-deterministic system from initial discovery to continuous regression protection:

```
EXPLORE → OPTIMIZE → MEASURE → TEST
   |          |          |        |
Compare    Tune one   Establish  Regression
configs    factor     baseline   testing
```

1. **EXPLORE** — Compare candidate configurations (models, temperatures, prompts) with small sample sizes to identify what works
2. **OPTIMIZE** — Fine-tune the winning configuration's key parameters automatically
3. **MEASURE** — Run the optimised configuration with a large sample (1000+) to establish a statistically reliable baseline
4. **TEST** — Write probabilistic tests that derive their thresholds from the baseline, catching regressions in CI

The Use Case is the thread that runs through every stage. It defines what "success" means via its Service Contract, and every experiment and test evaluates the same definition.

For a detailed treatment of this workflow — including production readiness phases and guidance on when to re-run each stage — see [OPERATIONAL-FLOW.md](OPERATIONAL-FLOW.md).

**Running with real LLMs:**

By default, the experiments in the punitexamoples project use mock LLMs for fast, free, deterministic results. To run with real LLM providers, set the mode and provide API keys:

```bash
export PUNIT_LLM_MODE=real
export OPENAI_API_KEY=sk-...
export ANTHROPIC_API_KEY=sk-ant-...
./gradlew exp -Prun=ShoppingBasketExplore.compareModels
```

See [Appendix A: Configuration Reference](#a-configuration-reference) for all LLM configuration options.

> **Cost Warning**: Running experiments with real LLMs incurs API costs. A typical EXPLORE experiment with 4 models × 20 samples = 80 API calls. A MEASURE experiment with 1000 samples can cost several dollars depending on the model. Approximate costs per 1M tokens (as of Jan 2025):
>
> | Model                        | Input | Output |
> |------------------------------|-------|--------|
> | `gpt-4o-mini`                | $0.15 | $0.60  |
> | `gpt-4o`                     | $2.50 | $10.00 |
> | `claude-haiku-4-5-20251001`  | $1.00 | $5.00  |
> | `claude-sonnet-4-5-20250929` | $3.00 | $15.00 |
>
> Use budget constraints (`tokenBudget`, `timeBudgetMs`) to cap costs. Start with mock mode or small sample sizes to verify your experiment works before running with real APIs.

### Baseline Spec Resolution

When a `@ProbabilisticTest` runs, PUnit needs to find the baseline specification that was produced by a prior `@MeasureExperiment`. This section describes where PUnit looks and in what order.

#### Resolution Algorithm

PUnit resolves specs through a **layered search** — it checks each location in priority order and uses the first match:

| Priority | Source                          | How to configure                                                                      |
|----------|---------------------------------|---------------------------------------------------------------------------------------|
| 1        | **Environment-local directory** | System property `punit.spec.dir` or environment variable `PUNIT_SPEC_DIR`             |
| 2        | **Classpath**                   | `punit/specs/` on the classpath (the default output location of `@MeasureExperiment`) |

This layering enables environment-specific overrides without modifying checked-in specs. A staging environment can have its own baseline directory with specs that reflect staging-grade latency, while the classpath retains the production baselines committed to version control.

**Example: overriding specs per environment**

```bash
# CI uses checked-in specs (classpath) — no configuration needed
./gradlew test

# Staging uses environment-specific baselines
PUNIT_SPEC_DIR=/opt/punit/staging-specs ./gradlew test

# Local development overrides via system property
./gradlew test -Dpunit.spec.dir=./local-specs
```

#### Dimension-Qualified Specs

A `@MeasureExperiment` may produce multiple spec files when the use case has both functional and latency dimensions:

| File                                 | Contents                                                                      |
|--------------------------------------|-------------------------------------------------------------------------------|
| `ShoppingBasketUseCase.yaml`         | Functional baseline (success rate) — always produced                          |
| `ShoppingBasketUseCase.latency.yaml` | Latency baseline (percentile timings) — produced when latency data is present |

When a `@ProbabilisticTest` uses `assertLatency()` or `assertAll()`, PUnit first looks for the dedicated latency spec (`{UseCaseId}.latency.yaml`). If not found, it falls back to the combined spec and extracts latency data from it. This allows latency baselines to be maintained independently — useful when latency characteristics are more environment-sensitive than functional correctness.

The layered search applies to each spec file independently. For example, an environment-local directory might override only the latency spec while the functional spec is resolved from the classpath.

#### What Happens When No Spec is Found

If no spec is found in any layer, the behaviour depends on the test configuration:

- **With explicit `minPassRate`**: The test runs using the declared threshold directly (no baseline needed)
- **Without `minPassRate`**: The test fails with a configuration error, since there is no basis for determining a threshold

### Covariate-Aware Baseline Selection

Covariates are environmental factors that may affect system behaviour.

**What are covariates?**

- **Temporal**: Time of day, weekday vs weekend, season
- **Infrastructure**: Region, instance type, API version
- **Configuration**: Model, temperature, prompt variant

**Why they matter:** An LLM's behaviour may differ between weekdays and weekends (different load patterns), or between models. Testing against the wrong baseline produces misleading results.

**Intelligent baseline selection:** When a probabilistic test runs, PUnit examines the current execution context (covariates) and selects the most appropriate baseline. If one or more covariates don't match, PUnit qualifies the verdict with a warning:

```
═ VERDICT: PASS (VERIFICATION) ═══════════════════════════════════════ PUnit ═

  testInstructionTranslation

  Observed pass rate:  0.9100 (91/100) >= required: 0.9190
  Elapsed:             5765ms

══════════════════════════════════════════════════════════════════════════════
```

When covariates don't match, PUnit prints a separate warning after the verdict:

```
═ COVARIATE NON-CONFORMANCE ══════════════════════════════════════════ PUnit ═

  Statistical inference may be less reliable.

  • day_of_week: baseline=WEEKDAY, test=WEEKEND
  • time_of_day: baseline=MORNING, test=EVENING

══════════════════════════════════════════════════════════════════════════════
```

**How PUnit selects baselines:**

1. Use case declares relevant covariates via `@UseCase` or `@Covariate`
2. MEASURE experiment records covariate values in the spec
3. At test time, PUnit captures current covariate values
4. Framework selects the baseline with matching (or closest) covariates

```java
@UseCase(
    covariateDayOfWeek = {@DayGroup({SATURDAY, SUNDAY})},
    covariates = {
        @Covariate(key = "llm_model", category = CovariateCategory.CONFIGURATION)
    }
)
public class ShoppingBasketUseCase { }
```

*Source: `org.javai.punit.examples.probabilistictests.ShoppingBasketCovariateTest`*

#### Automatic Complement Labels

When you declare day-of-week or time-of-day partitions, you typically only list the groups you care about distinguishing. PUnit automatically derives a descriptive label for the complement — the remaining days or time intervals not covered by any declared partition.

**Day of week:** Given `@DayGroup({SATURDAY, SUNDAY})`, PUnit computes the complement as the five remaining weekdays and labels it `WEEKDAY`. The labelling uses the same conventions as declared groups: `WEEKEND` and `WEEKDAY` for the well-known sets, or joined day names (e.g. `MONDAY_TUESDAY_WEDNESDAY`) for arbitrary subsets. A baseline recorded on a Wednesday will show:

```yaml
covariates:
  day_of_week: WEEKDAY
```

**Time of day:** Given `covariateTimeOfDay = {"08:00/4h", "16:00/4h"}`, PUnit computes the uncovered intervals — `[00:00, 08:00)`, `[12:00, 16:00)`, and `[20:00, 24:00)` — and joins them into a single remainder label. A baseline recorded at 14:00 will show:

```yaml
covariates:
  time_of_day: 00:00/8h, 12:00/4h, 20:00/4h
```

This means you only need to declare the partitions you want to distinguish. Everything else is captured automatically with a self-describing label, so spec files remain readable without referring back to the use case declaration.

---

## Part 7: Resource Management

### Budget Control

Traditional tests run once per execution. By contrast, experiments and probabilistic tests require multiple executions. This has the potential to rack up costs in terms of time and resources. PUnit addresses this first-class concern by providing safeguards against excessive resource consumption.

Budgets can be specified at different levels:

| Level      | Scope              | How to Set                               |
|------------|--------------------|------------------------------------------|
| **Method** | Single test method | `@ProbabilisticTest(timeBudgetMs = ...)` |
| **Class**  | All tests in class | `@CostBudget` on class                   |
| **Suite**  | All tests in run   | System property or environment variable  |

When budgets are set at multiple levels, PUnit enforces all of them — the first exhausted budget triggers termination.

**Time Budgets:**

```java
@ProbabilisticTest(
    samples = 500,
    minPassRate = 0.95,
    timeBudgetMs = 60000  // Stop after 60 seconds
)
void timeConstrainedTest() { ... }
```

**Token Budgets:**

```java
@ProbabilisticTest(
    samples = 500,
    minPassRate = 0.95,
    tokenBudget = 100000  // Stop after 100k tokens
)
void tokenConstrainedTest(TokenChargeRecorder recorder) {
    LlmResponse response = llmClient.complete("Generate JSON");
    recorder.recordTokens(response.getUsage().getTotalTokens());
    assertThat(response.getContent()).satisfies(JsonValidator::isValid);
}
```

**What happens when the budget runs out?** The `onBudgetExhausted` parameter controls this:

| Behavior           | Description                                                                                                                                                                                                 |
|--------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `FAIL`             | Immediately fail the test when budget is exhausted. This is the **default** and most conservative option — you asked for N samples but couldn't afford them.                                                |
| `EVALUATE_PARTIAL` | Evaluate results from the samples completed before budget exhaustion. The test passes if the observed pass rate meets `minPassRate`. Use with caution: a small sample may not be statistically significant. |

```java
// Default behavior: FAIL when budget exhausted
@ProbabilisticTest(
    samples = 1000,
    minPassRate = 0.90,
    tokenBudget = 50000
    // onBudgetExhausted = BudgetExhaustedBehavior.FAIL (implicit default)
)
void strictBudgetTest(TokenChargeRecorder recorder) {
    // If budget runs out after 200 samples → FAIL
    // Rationale: You requested 1000 samples for statistical confidence;
    // 200 samples may not provide reliable results
}

// Alternative: Evaluate whatever we managed to collect
@ProbabilisticTest(
    samples = 1000,
    minPassRate = 0.90,
    tokenBudget = 50000,
    onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL
)
void evaluateWhatWeHave(TokenChargeRecorder recorder) {
    // If budget runs out after 200 samples with 185 successes:
    // 185/200 = 92.5% >= 90% → PASS (instead of automatic FAIL)
    // Warning: 200 samples may not be statistically rigorous
}
```

*Source: `org.javai.punit.examples.probabilistictests.ShoppingBasketBudgetTest`*

### Pacing Constraints

Hitting APIs with tens, hundreds or thousands of calls must be done in a controlled manner. Many third-party APIs limit calls per minute/hour.

When testing rate-limited APIs, use `@Pacing` to stay within limits:

```java
@ProbabilisticTest(
    samples = 200,
    minPassRate = 0.95
)
@Pacing(maxRequestsPerMinute = 60)
void rateLimitedApiTest() {
    // PUnit automatically spaces samples ~1 second apart
}
```

**Pacing parameters:**

| Parameter               | Description              | Example                  |
|-------------------------|--------------------------|--------------------------|
| `maxRequestsPerSecond`  | Max RPS                  | `2.0` → 500ms delay      |
| `maxRequestsPerMinute`  | Max RPM                  | `60` → 1000ms delay      |
| `maxRequestsPerHour`    | Max RPH                  | `3600` → 1000ms delay    |
| `minMsPerSample`        | Explicit minimum delay   | `500` → 500ms delay      |
| `maxConcurrentRequests` | Parallel execution limit | `3` → up to 3 concurrent |

When multiple constraints are specified, the **most restrictive** wins.

*Source: `org.javai.punit.examples.probabilistictests.ShoppingBasketPacingTest`*

### Exception Handling

Configure how exceptions during sample execution are handled:

```java
@ProbabilisticTest(
    samples = 100,
    onException = ExceptionHandling.FAIL_SAMPLE
)
void exceptionsCountAsFailures() {
    // Exceptions count as sample failures, don't propagate
}
```

| Strategy      | Behavior                                            |
|---------------|-----------------------------------------------------|
| `FAIL_SAMPLE` | Exception counts as failed sample; continue testing |
| `ABORT_TEST`  | Exception immediately fails the test                |

*Source: `org.javai.punit.examples.probabilistictests.ShoppingBasketExceptionTest`*

---

## Part 8: Latency

### The Problem with Averages

A service responds in 200ms on average. Sounds fine — until you discover that one in a hundred requests takes 5 seconds, and one in a thousand takes 30.

The average is perhaps the most widely reported and most misleading latency metric. It collapses an entire distribution into a single number, hiding the experience of the users who matter most: those at the tail. A system that averages 200ms but occasionally stalls for seconds is a fundamentally different system from one that consistently responds in 180–220ms. The average cannot distinguish between them.

This is well understood in performance engineering. The solution is **percentile-based measurement**: instead of asking "what is the average?", ask "what latency does 95% of traffic experience?" and "what does 99% experience?" These questions reveal the shape of the distribution and expose the tail behaviour that averages conceal.

PUnit applies this principle to probabilistic testing. Rather than recording a single average latency, it captures the full latency distribution across all successful samples and evaluates percentile-based thresholds: p50 (median), p90, p95, and p99.

### Latency Recording in Experiments

PUnit automatically records per-sample wall-clock execution time during both MEASURE and EXPLORE experiments. No additional configuration is needed — latency data is captured as a natural byproduct of running samples.

Only **successful samples** contribute to the latency distribution. Failed samples are excluded because their execution times are often meaningless — a fast failure (an immediate validation rejection) or a slow failure (a timeout) would distort the distribution of the service's actual response behaviour.

After all samples complete, PUnit computes the latency distribution from the successful sample times and writes it to the experiment output file.

#### MEASURE experiment output

A MEASURE experiment's spec file includes a `latency` section alongside the existing pass-rate statistics:

```yaml
schemaVersion: punit-spec-1
useCaseId: PaymentGatewayUseCase
generatedAt: 2026-02-15T10:30:00Z
execution:
  samplesPlanned: 1000
  samplesExecuted: 1000
  terminationReason: COMPLETED
requirements:
  minPassRate: 0.9194
statistics:
  successRate:
    observed: 0.9350
    standardError: 0.0078
    confidenceInterval95: [0.9194, 0.9506]
  successes: 935
  failures: 65
latency:
  sampleCount: 935
  meanMs: 312
  maxMs: 1450
  sortedLatenciesMs: [98, 105, 112, ..., 580, ..., 920, ..., 1450]
cost:
  totalTimeMs: 315000
  avgTimePerSampleMs: 315
```

The `sampleCount` in the latency section equals the number of successes, not the total sample count. The full sorted vector of successful-response latencies is preserved in `sortedLatenciesMs` so that any percentile can be recovered exactly and so that baseline-derived thresholds can use the binomial order-statistic upper bound (see the Statistical Companion §12.4). `meanMs` and `maxMs` are reported for quick inspection; the sample standard deviation is deliberately omitted because latency distributions are not well-characterised by their second moment.

This data serves two purposes. First, it is descriptive — it tells you what the service's latency profile looked like during the experiment. Second, it becomes the **baseline** from which PUnit can derive latency thresholds for probabilistic tests (covered in [Baseline-Derived Thresholds](#baseline-derived-thresholds) below).

#### EXPLORE experiment output

EXPLORE experiments produce the same latency section per configuration, enabling side-by-side latency comparison across configurations:

```yaml
# model-gpt-4o_temp-0.3.yaml
statistics:
  observed: 0.9500
  successes: 19
  failures: 1
latency:
  sampleCount: 19
  meanMs: 450
  maxMs: 1400
  sortedLatenciesMs: [180, 220, 260, 310, 340, 380, 430, 480, 530, 580, 620, 660, 720, 750, 820, 910, 1030, 1100, 1400]

# model-claude-3-5-sonnet_temp-0.3.yaml
statistics:
  observed: 0.9000
  successes: 18
  failures: 2
latency:
  sampleCount: 18
  meanMs: 210
  maxMs: 510
  sortedLatenciesMs: [110, 135, 155, 170, 180, 190, 200, 210, 220, 230, 250, 270, 290, 320, 340, 380, 480, 510]
```

Here, model A has a higher pass rate but substantially worse tail latency. Model B responds more consistently, with a p99 less than half of model A's. This kind of trade-off — reliability versus responsiveness — is invisible to average-based metrics and difficult to spot without per-configuration latency profiles.

### Testing Latency with Contractual Thresholds

When you know the latency requirements upfront — from an SLA, an internal SLO, or a design target — you can declare them directly on the test:

```java
@ProbabilisticTest(
    samples = 200,
    minPassRate = 0.95,
    latency = @Latency(p95Ms = 500, p99Ms = 1000)
)
void paymentServiceMeetsLatencySla() {
    PaymentResult result = paymentService.processPayment(testPayment());
    assertThat(result.isSuccessful()).isTrue();
}
```

PUnit measures the wall-clock time of each successful sample, computes the observed percentile distribution after all samples complete, and compares each declared percentile against its threshold. A percentile **passes** if the observed value is at or below the threshold; it **fails** (breaches) if the observed value exceeds it.

The `@Latency` annotation supports four percentiles: `p50Ms`, `p90Ms`, `p95Ms`, and `p99Ms`. You need only declare the ones you care about — unset percentiles (default `-1`) are not asserted.

**The combined verdict.** Latency and pass rate are independent quality dimensions. Both must pass for the test to pass. A service that meets its reliability target but breaches its latency SLA still fails the test:

```
═ VERDICT: FAIL (VERIFICATION) ═══════════════════════════════════════ PUnit ═

  paymentServiceMeetsLatencySla

  Observed pass rate:  0.9600 (192/200) >= required: 0.9500
  Latency:             (n=192): p95 480ms <= 500ms, p99 1350ms > 1000ms ← BREACH
  Elapsed:             12340ms

══════════════════════════════════════════════════════════════════════════════
```

The pass rate is fine (96% >= 95%), but the observed p99 of 1350ms exceeds the 1000ms threshold. Because the thresholds are declared explicitly via `@Latency`, they are enforced by default — the test fails. No global flag is required.

### Baseline-Derived Thresholds

When a MEASURE experiment has been run and its spec contains latency data, PUnit **automatically** derives latency thresholds from the baseline — no explicit opt-in is required. This mirrors how PUnit derives pass-rate thresholds from baselines: the empirically observed performance becomes the expectation, with a statistical margin to account for natural variance.

```java
@ProbabilisticTest(
    useCase = PaymentGatewayUseCase.class,
    samples = 200
)
void paymentServiceLatencyConformsToBaseline() {
    PaymentResult result = paymentService.processPayment(testPayment());
    assertThat(result.isSuccessful()).isTrue();
}
```

If the baseline spec contains a latency section, PUnit derives upper-bound thresholds for all four percentiles using a confidence interval. The thresholds are slightly looser than the raw baseline values — they accommodate the natural variance you would expect when re-running the service with a different (typically smaller) sample size.

If you want a baseline-backed test that asserts pass-rate but **not** latency, opt out with `@Latency(disabled = true)`:

```java
@ProbabilisticTest(
    useCase = PaymentGatewayUseCase.class,
    samples = 200,
    latency = @Latency(disabled = true)
)
void passRateOnlyTest() { ... }
```

> **Precedence rule:** When explicit `@Latency` thresholds (e.g. `@Latency(p95Ms = 500)`) are declared and the baseline also contains latency data, the explicit thresholds take precedence — the baseline latency data is ignored. This lets you use the baseline for pass-rate while asserting latency against contractual targets. To suppress latency evaluation entirely, use `@Latency(disabled = true)`.

#### Latency enforcement mode

Latency enforcement is **context-aware** — it depends on where the thresholds came from.

**Explicit thresholds are enforced by default.** When you write `@Latency(p95Ms = 500)`, you are declaring intent. PUnit honours that intent — breaches fail the test without requiring any global flag. The annotation is the developer's declaration that latency matters for this test.

**Baseline-derived thresholds are advisory by default.** When latency thresholds are automatically derived from a baseline spec (no explicit `@Latency` values), breaches produce warnings but do not fail the test. This is because latency profiles are environment-dependent: a baseline generated on CI hardware may not match a developer laptop.

To promote baseline-derived thresholds to enforced (e.g. on CI with consistent hardware):

```bash
# System property
-Dpunit.latency.enforce=true

# Environment variable
PUNIT_LATENCY_ENFORCE=true
```

A typical setup is to enforce baseline-derived latency in CI while keeping advisory mode for local development:

```properties
# CI gradle.properties
systemProp.punit.latency.enforce=true
```

The global flag only affects baseline-derived thresholds. It does not override or disable enforcement of explicit thresholds.

| Threshold origin          | Global flag | Breach behaviour            | Feasibility gate           |
|---------------------------|-------------|-----------------------------|----------------------------|
| Explicit `@Latency`       | (any)       | Test fails                  | Active (VERIFICATION only) |
| Baseline-derived          | `false`     | Warn in output, test passes | Skipped                    |
| Baseline-derived          | `true`      | Test fails                  | Active (VERIFICATION only) |
| `@Latency(disabled=true)` | (any)       | N/A (not evaluated)         | Skipped                    |

To suppress latency evaluation entirely (not even advisory warnings), use `@Latency(disabled = true)` on individual tests.

### The Three Latency Mechanisms at a Glance

PUnit evaluates latency through three distinct mechanisms. They operate at different granularities, draw their thresholds from different sources, and fire at different points in the test lifecycle. Understanding how they relate — and where the real analytical power lies — is important for choosing the right approach.

#### Mechanism 1: Service Contract Duration Constraint

A `DurationConstraint` declared on the use case's `ServiceContract` via `ensureDurationBelow()`. When the test method calls `assertLatency()` or `assertAll()`, each sample's execution time is checked against the declared duration. A breach is a sample failure in the latency dimension.

This is the simplest of the three mechanisms: a fixed ceiling applied to each individual invocation in isolation. It can catch gross outliers — a single call that takes 30 seconds against a 5-second SLA — but it tells you nothing about the distribution. A service where 40% of calls narrowly miss the ceiling would pass every individual check while delivering a terrible user experience in aggregate. Per-sample duration constraints are a useful safety net, but they are not where the probabilistic approach adds value.

#### Mechanism 2: Explicit `@Latency` Annotation

Percentile thresholds declared directly on the `@ProbabilisticTest` via the `latency` attribute, e.g. `@Latency(p95Ms = 500, p99Ms = 1000)`.

This is fundamentally different from mechanism 1. Rather than checking each sample in isolation, PUnit collects the wall-clock time of every successful sample, computes the observed percentile distribution after all samples complete, and compares each declared percentile against its threshold. The thresholds come from a contractual source — an SLA, SLO, or design target known to the test author.

This aggregate evaluation is where the probabilistic framework delivers real insight. A per-sample check cannot distinguish between "occasionally slow" and "systematically slow". Percentile-based evaluation can: a p95 breach tells you the tail is too heavy; a p50 breach tells you the median is too slow. By evaluating the distribution rather than individual data points, mechanism 2 transforms latency testing from a blunt per-invocation gate into a statistically meaningful assessment of service behaviour.

#### Mechanism 3: Baseline-Derived Percentile Thresholds

Percentile thresholds automatically derived from a MEASURE experiment's baseline spec. When the spec contains latency data and no explicit `@Latency` annotation is present, PUnit computes upper-bound thresholds for each percentile using a confidence interval.

Mechanism 3 operates at the same aggregate granularity as mechanism 2 — the difference is provenance. Where mechanism 2 uses contractual thresholds chosen by the test author, mechanism 3 derives thresholds empirically from the baseline. The formula `threshold = baselinePercentile + z × (σ / √n)` accommodates natural variance when re-running with a different sample size. This makes mechanism 3 the natural choice for regression testing: the system's own measured performance becomes the expectation, with a statistical margin that tightens as the sample size grows.

#### Comparison

| Aspect                   | Duration Constraint (1)                                | Explicit `@Latency` (2)                 | Baseline-Derived (3)                                |
|--------------------------|--------------------------------------------------------|-----------------------------------------|-----------------------------------------------------|
| **Declared on**          | `ServiceContract` (use case)                           | `@ProbabilisticTest` (test)             | MEASURE spec (automatic)                            |
| **Granularity**          | Per-sample                                             | Aggregate (percentile)                  | Aggregate (percentile)                              |
| **Threshold provenance** | Fixed value in code                                    | Contractual (SLA/SLO)                   | Empirical (baseline + confidence interval)          |
| **Evaluation point**     | During each sample (`assertLatency()` / `assertAll()`) | After all samples complete              | After all samples complete                          |
| **Opt-in/opt-out**       | Implicit when contract has duration constraint         | Explicit `latency = @Latency(...)`      | Automatic; opt out with `@Latency(disabled = true)` |
| **Can coexist with**     | Either of the other two                                | Either (overrides baseline if present)  | Either (yields to explicit if present)              |

#### How the Mechanisms Interact

**Explicit thresholds (mechanism 2) take precedence over baseline-derived thresholds (mechanism 3).** Both mechanisms answer the same question — does the latency distribution conform? — but they draw thresholds from different sources. When both are available, the explicit thresholds win: PUnit uses the contractual values from the `@Latency` annotation and ignores the baseline-derived values. This allows a developer to use the baseline for pass-rate derivation while asserting latency against a known SLA or SLO. When no explicit thresholds are declared, baseline-derived thresholds apply automatically.

**Mechanism 1 is independent of mechanisms 2 and 3.** A service contract's duration constraint operates per-sample during execution; the aggregate percentile mechanisms operate after all samples complete. Both can be active simultaneously. A sample could pass the per-sample duration check but the overall distribution could still breach a percentile threshold, or vice versa.

**A test can exercise all applicable mechanisms.** When a test calls `assertAll()` on a use case that has a duration constraint, and the test also has percentile thresholds (either explicit or baseline-derived), all layers are active: per-sample duration checks during execution, and aggregate percentile checks after completion. In practice, the aggregate mechanisms (2 or 3) are the ones that provide the statistically grounded verdict — the per-sample constraint is a coarse-grained safety net that catches individual outliers but cannot characterise the distribution.

### Sample Size and Percentile Reliability

Percentile assertions are only as reliable as the sample size behind them. Computing a p99 from 10 samples is statistically dubious — with only 10 observations, the "99th percentile" is just the maximum value, and a single outlier dominates the result.

PUnit addresses this with minimum sample size requirements. The relevant count is the number of **successful samples** (since only successes contribute to the latency distribution), which depends on both the total sample count and the expected pass rate. See part 9 for more detail on the minimal sample size requirements for latency assertions.

For **VERIFICATION** intent, PUnit enforces these minimums as a feasibility gate before any samples execute. If the expected number of successful samples (total samples multiplied by the expected pass rate) is insufficient for any asserted percentile, the test is rejected with a configuration error:

```
Latency p99 assertion requires at least 100 successful samples,
but only 40 are expected (planned=50, expected success rate=0.80).
Increase sample size or remove the p99 assertion.
```

For **SMOKE** intent, the feasibility gate is bypassed. PUnit runs the test and reports whatever percentiles it can compute, but marks the results as **indicative** — a signal, not evidence. This follows the same philosophy as pass-rate smoke tests: useful for catching gross regressions cheaply, but not a substitute for proper verification.

---

## Part 9: The Statistical Core

A brief look at the statistical engine that powers PUnit.

### Bernoulli Trials

At its heart, PUnit models each sample as a **Bernoulli trial** — an experiment with exactly two outcomes: success or failure, with failure being defined as non-conformance to the use case's contract. When you run a probabilistic test:

1. Each sample execution is a Bernoulli trial with unknown success probability *p*
2. The baseline spec provides an estimate of *p* from prior measurement
3. PUnit uses the binomial distribution to determine whether the observed success count is consistent with the baseline

This statistical machinery runs automatically when a regression test executes. Users don't interact with it directly — they simply write tests, and PUnit applies rigorous statistical inference under the hood.

### Latency: Empirical Percentiles with Confidence Bounds

Pass-rate testing uses a parametric model (binomial distribution), but latency requires a different approach. Service latency distributions are often multimodal or heavily skewed — a fast path through a cache versus a slow path to the database, for example. Assuming normality would be inappropriate, so PUnit avoids fitting a parametric distribution to latency data entirely.

Instead, PUnit works with **empirical percentiles**. During a MEASURE experiment, it records the wall-clock duration of each successful sample, sorts the values, and reads off percentiles using nearest-rank interpolation:

```
index = ⌈p × n⌉ − 1    (clamped to [0, n−1])
percentile = sorted[index]
```

Only successful samples contribute — failed samples are excluded because their timing reflects validation failure or timeout, not the system's operational latency.

**Threshold derivation from baselines.** When a regression test references a baseline spec that contains latency data, PUnit derives a one-sided upper confidence bound for each percentile:

```
threshold = baselinePercentile + z × (σ / √n)
```

where *z* is the z-score for the configured confidence level (default 95%, one-sided), *σ* is the baseline's standard deviation, and *n* is the baseline's sample count. The term *σ / √n* is the standard error — it quantifies how much the observed percentile might vary across runs due to sampling alone. Larger baselines (higher *n*) yield tighter thresholds; noisier systems (higher *σ*) yield wider ones.

The derived threshold is clamped to be at least the baseline value, ensuring it never becomes tighter than what was actually observed.

**Minimum sample sizes.** Percentile estimates degrade rapidly with small samples. A "p99" computed from 10 observations is just the maximum — it tells you nothing about the 99th percentile of the true distribution. PUnit defines minimum sample counts for evidential results:

| Percentile | Minimum successful samples |
|------------|----------------------------|
| p50        | 5                          |
| p90        | 10                         |
| p95        | 20                         |
| p99        | 100                        |

When latency thresholds are effectively enforced (explicit `@Latency` thresholds, or baseline-derived with `-Dpunit.latency.enforce=true`) and the test has VERIFICATION intent, these minimums are enforced as a feasibility gate before any samples execute. The relevant count is the *expected* number of successful samples — total samples multiplied by the expected pass rate. If this falls below the minimum for any asserted percentile, the test is rejected with a configuration error rather than producing unreliable results.

When the sample count is below the minimum but the feasibility gate is inactive (baseline-derived advisory mode or SMOKE intent), PUnit still evaluates latency but marks the results as **indicative** — a signal that the numbers should be taken with a grain of salt.

### Transparent Statistics Mode

Enable `transparentStats = true` to see the statistical reasoning:

```java
@ProbabilisticTest(samples = 100, transparentStats = true)
void myTest() { ... }
```

Or via system property:

```bash
./gradlew test -Dpunit.stats.transparent=true
```

Output includes:

- Hypothesis formulation (H₀ and H₁)
- Observed data summary
- Confidence intervals
- p-values and verdict interpretation
- Latency analysis (when thresholds are available)

```
═ STATISTICAL ANALYSIS FOR: shouldReturnValidJson ════════════════════ PUnit ═

  HYPOTHESIS TEST
    H₀ (null):             True success rate π ≤ 0.8500 (system does not meet spec)
    H₁ (alternative):      True success rate π > 0.8500 (system meets spec)
    Test type:             One-sided binomial proportion test

  OBSERVED DATA
    Sample size (n):       100
    Successes (k):         87
    Observed rate (p̂):     0.8700

  BASELINE REFERENCE
    Source:                JsonValidationUseCase.yaml (generated 2026-01-10)
    Empirical basis:       1000 samples, 870 successes (0.8700)
    Threshold derivation:  Lower bound of 95% CI = 85.1%, min pass rate = 85%

  STATISTICAL INFERENCE
    Standard error:        SE = √(p̂(1-p̂)/n) = √(0.87 × 0.13 / 100) = 0.0336
    Confidence interval:   95% [0.804, 0.936]

    Test statistic:        z = (p̂ - π₀) / √(π₀(1-π₀)/n)
                           z = (0.87 - 0.85) / √(0.85 × 0.15 / 100)
                           z = 0.56

    p-value:               P(Z > 0.56) = 0.288

  LATENCY ANALYSIS
    Population:            Successful samples only (n=87 of 100)
    Observed distribution:
      p50:                 120ms
      p90:                 340ms
      p95:                 480ms
      p99:                 920ms
      max:                 1150ms

    Percentile thresholds (from baseline):
      p95:                 480ms <= 517ms                            PASS
      p99:                 920ms <= 1050ms                           PASS

    Baseline reference:    JsonValidationUseCase.yaml

  VERDICT
    Result:                PASS
    Interpretation:        The observed success rate of 87.00% meets the
                           derived threshold of 85.00%.

══════════════════════════════════════════════════════════════════════════════
```

### Further Reading

For the mathematical foundations — confidence interval calculations, power analysis, threshold derivation formulas — see [STATISTICAL-COMPANION.md](STATISTICAL-COMPANION.md).

---

## Part 10: The Sentinel

Parts 1–9 cover PUnit as a development-time testing framework integrated with JUnit 5. This part introduces the **Sentinel** — an execution engine that runs the same probabilistic tests and experiments in deployed environments, without JUnit.

If the notion of running a "test" in production feels uncomfortable, it is worth examining why. Traditional software engineering testing assumes largely deterministic behaviour: once a test passes in CI, the feature is safe to deploy. For a truly deterministic feature, this is perfectly reasonable. But for features whose behaviour is shaped by stochasticity — randomness, non-determinism — reliability often depends on the environment in which the feature operates. The Sentinel is PUnit's tool for this: a lightweight test runner, independent of JUnit and its dependency graph; purpose-built for deployed environments.

For project structure, module layout, and operational deployment, see the [Sentinel Deployment Guide](SENTINEL-DEPLOYMENT-GUIDE.md).

### Module Decomposition and Artifact Selection

PUnit is published as three artifacts, each serving a distinct consumer:

| Artifact                   | Consumer                                       | Dependency scope              |
|----------------------------|------------------------------------------------|-------------------------------|
| `org.javai:punit-core`     | Reliability spec authors (`@Sentinel` classes) | `api` (production)            |
| `org.javai:punit-junit5`   | JUnit test developers                          | `testImplementation`          |
| `org.javai:punit-sentinel` | Sentinel deployers                             | `implementation` (production) |

A backward-compatible meta-artifact (`org.javai:punit`) transitively includes `punit-core` and `punit-junit5`. Existing consumers who depend on `punit` see no change upon upgrade.

**Dependency declarations:**

```kotlin
// Reliability spec author (app-usecases module)
dependencies {
    api("org.javai:punit-core:0.7.0")
}

// JUnit test developer
dependencies {
    testImplementation("org.javai:punit-junit5:0.7.0")  // includes punit-core transitively
}

// Sentinel deployer
dependencies {
    implementation("org.javai:punit-sentinel:0.7.0")  // includes punit-core transitively
}

// Existing consumer (unchanged)
dependencies {
    testImplementation("org.javai:punit:0.7.0")  // includes punit-core + punit-junit5
}
```

**Migration from the single `punit` artifact:** If you only write JUnit probabilistic tests, replace `org.javai:punit` with `org.javai:punit-junit5`. No code changes are required — package paths and APIs are identical.

### The Reliability-Specification-First Model

A `@Sentinel`-annotated class is a **reliability specification** — a plain Java class that defines what to measure and what to verify about a stochastic use case. It contains no JUnit types and has no runtime JUnit dependency.

```java
@Sentinel
public class ShoppingBasketReliability {

    UseCaseFactory factory = new UseCaseFactory();
    {
        factory.register(ShoppingBasketUseCase.class,
            () -> new ShoppingBasketUseCase(new OpenAiClient(System.getenv("OPENAI_API_KEY"))));
    }

    @MeasureExperiment(useCase = ShoppingBasketUseCase.class, experimentId = "baseline-v1")
    @InputSource("instructions")
    void measureBaseline(ShoppingBasketUseCase useCase, String instruction, OutcomeCaptor captor) {
        captor.record(useCase.translateInstruction(instruction));
    }

    @ProbabilisticTest(useCase = ShoppingBasketUseCase.class, samples = 100)
    @InputSource("instructions")
    void testInstructionTranslation(ShoppingBasketUseCase useCase, String instruction) {
        useCase.translateInstruction(instruction).assertAll();
    }

    static Stream<String> instructions() {
        return Stream.of("Add 2 apples", "Remove the milk", "Add 1 loaf of bread");
    }
}
```

This class is the **single source of truth** for the reliability specification. It is consumed by both the JUnit engine (via inheritance) and the Sentinel engine (directly).

**JUnit consumption:** A one-line subclass in the test source set inherits all methods and input sources:

```java
public class ShoppingBasketReliabilityTest extends ShoppingBasketReliability {

    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    @BeforeEach
    void setUp() {
        provider.register(ShoppingBasketUseCase.class, ShoppingBasketUseCase::new);
    }
}
```

The JUnit subclass may add `@DisplayName`, `@Tag`, additional `@BeforeEach` setup, or extra test methods, but the reliability specification itself is untouched.

**Why the specification is not test code.** A reliability specification defines the stochastic contract between a use case and its environment. Because it is consumed by two independent engines, it must be packageable into a JAR — which means it belongs in a production source set (e.g. `src/main/java` of an `app-usecases` module), not in `src/test/java`. Code in a test source set is never packaged and would be invisible to the Sentinel.

### UseCaseFactory and UseCaseProvider

PUnit provides two classes for use case registration, each serving a different context:

| Class             | Module         | JUnit dependency          | Used by                                |
|-------------------|----------------|---------------------------|----------------------------------------|
| `UseCaseFactory`  | `punit-core`   | None                      | `@Sentinel` reliability specifications |
| `UseCaseProvider` | `punit-junit5` | Yes (`ParameterResolver`) | Traditional JUnit test classes         |

`UseCaseProvider` extends `UseCaseFactory`, inheriting all factory logic (registration, instance resolution, singleton management) and adding JUnit parameter injection. Both classes share the same `register()` and `getInstance()` API.

**In a `@Sentinel` class**, use `UseCaseFactory` directly — a field initialiser or instance initialiser populates the registry:

```java
UseCaseFactory factory = new UseCaseFactory();
{
    factory.register(ShoppingBasketUseCase.class, ShoppingBasketUseCase::new);
}
```

**In a JUnit test class**, use `UseCaseProvider` with `@RegisterExtension` — the JUnit engine injects use case instances into test method parameters:

```java
@RegisterExtension
UseCaseProvider provider = new UseCaseProvider();

@BeforeEach
void setUp() {
    provider.register(ShoppingBasketUseCase.class, ShoppingBasketUseCase::new);
}
```

When a JUnit test class extends a `@Sentinel` class, both the inherited `UseCaseFactory` field and the `@RegisterExtension UseCaseProvider` field are present. PUnit's field scanning finds any field assignable to `UseCaseFactory`, so both patterns work transparently.

### Building the Sentinel JAR

The PUnit Gradle plugin provides a `createSentinel` task that builds an executable fat JAR containing all `@Sentinel`-annotated classes, their dependencies, and the Sentinel runtime. The task scans the test classpath for `@Sentinel` classes and packages everything needed into a single JAR:

```bash
./gradlew createSentinel
```

The resulting JAR is written to `build/libs/<project>-sentinel.jar`. The task:

- Discovers all `@Sentinel`-annotated classes on the test classpath (including transitive project dependencies)
- Packages compiled test and main classes, runtime dependencies, and the `punit-sentinel` runtime
- Generates a `META-INF/punit/sentinel-classes` manifest listing discovered sentinel classes
- Sets `SentinelMain` as the JAR's main class

At least one `@Sentinel`-annotated class must exist on the classpath, or the task will fail with an error.

### Running the Sentinel

The Sentinel CLI provides commands and options for running tests and experiments:

```
Usage: java [-Dpunit.spec.dir=<dir>] -jar sentinel.jar <command> [options]

Commands:
  test         Run probabilistic tests against baseline specs
  exp          Run experiments to produce baseline specs

Options:
  --help       Show this help message and exit
  --list       List available use cases and exit
  --verbose    Show per-sample progress during execution
  --useCase <id>  Run only the specified use case

Configuration:
  -Dpunit.spec.dir=<dir>   Spec directory (JVM system property)
  PUNIT_SPEC_DIR=<dir>     Spec directory (environment variable)

Exit codes:
  0  All tests/experiments passed (or --help/--list)
  1  One or more tests/experiments failed, or no use cases matched
  2  Usage error
```

**Discovering available use cases.** Use `--list` to see all available tests and experiments with their use case IDs:

```bash
java -jar sentinel.jar --list
```

This produces a table showing the use case ID, type, method name, and sample count:

```
Use Case Id            Type        Name                                      Samples
──────────────────────────────────────────────────────────────────────────────────────
PaymentGatewayUseCase  experiment  PaymentGatewayReliability.measureBaseline  200
PaymentGatewayUseCase  test        PaymentGatewayReliability.testLatency      50
ShoppingBasketUseCase  experiment  ShoppingBasketReliability.measureBaseline  1000
ShoppingBasketUseCase  test        ShoppingBasketReliability.testBaseline     100
```

**Running experiments.** Experiments produce baseline spec files. A spec output directory is required:

```bash
java -Dpunit.spec.dir=/opt/sentinel/specs -jar sentinel.jar exp
```

To run experiments for a single use case, pass the use case ID from the `--list` output:

```bash
java -Dpunit.spec.dir=/opt/sentinel/specs -jar sentinel.jar exp --useCase ShoppingBasketUseCase
```

**Running tests.** Tests verify behaviour against existing baseline specs:

```bash
java -jar sentinel.jar test
java -jar sentinel.jar test --useCase PaymentGatewayUseCase
```

**Verbose mode.** Add `--verbose` to see per-sample progress during execution:

```bash
java -jar sentinel.jar test --verbose --useCase ShoppingBasketUseCase
```

```
ShoppingBasketReliability.testBaseline (100 samples)
  sample 1/100 pass
  sample 2/100 pass
  sample 3/100 FAIL
  ...
  -> PASS

Sentinel Test Summary
────────────────────────────────────────
Total:    1
Passed:   1
Failed:   0
Skipped:  0
Duration: 45230ms

Result: PASS
```

### Programmatic API

For custom deployment scenarios (embedded scheduling, CI/CD integration, custom verdict routing), the Sentinel can be configured and invoked programmatically:

```java
SentinelConfiguration config = SentinelConfiguration.builder()
    .sentinelClass(ShoppingBasketReliability.class)
    .verdictSink(new WebhookVerdictSink("https://alerts.example.com/punit"))
    .environmentMetadata(EnvironmentMetadata.fromEnvironment())
    .build();

SentinelRunner runner = new SentinelRunner(config);
SentinelResult result = runner.runTests();

if (!result.allPassed()) {
    System.exit(1);
}
```

**Configuration options:**

| Method                                      | Description                                                   | Default                                 |
|---------------------------------------------|---------------------------------------------------------------|-----------------------------------------|
| `.sentinelClass(Class<?>)`                  | Register a `@Sentinel` class (at least one required)          | —                                       |
| `.specRepository(SpecRepository)`           | Baseline spec resolution strategy                             | `LayeredSpecRepository`                 |
| `.verdictSink(VerdictSink)`                 | Add a verdict sink (multiple allowed, composed automatically) | `LogVerdictSink`                        |
| `.environmentMetadata(EnvironmentMetadata)` | Environment context attached to verdicts                      | `EnvironmentMetadata.fromEnvironment()` |

**Verdict sinks.** Every verdict produced by the Sentinel is dispatched to all configured `VerdictSink` instances:

- `LogVerdictSink` — logs verdicts via PUnit's standard reporting (default)
- `WebhookVerdictSink` — posts verdicts as JSON to an HTTP endpoint, configurable with URL, timeout, and headers
- `CompositeVerdictSink` — dispatches to multiple sinks (created automatically when multiple sinks are registered)

Each verdict carries a **correlation ID** (e.g., `v:a3f8c2`) that appears in both the console verdict and the `VerdictEvent`, enabling operators to cross-reference JUnit CI reports with full verdict events in observability systems.

**Environment metadata.** The Sentinel tags every verdict with environment context, resolved from system properties or environment variables:

| Property            | Environment Variable | Default   | Description                                        |
|---------------------|----------------------|-----------|----------------------------------------------------|
| `punit.environment` | `PUNIT_ENVIRONMENT`  | `unknown` | Environment identifier (e.g., `staging`, `prod`)   |
| `punit.instanceId`  | `PUNIT_INSTANCE_ID`  | hostname  | Instance identifier for multi-instance deployments |

### Verdicts as Triage Signals

A PUnit verdict of PASS does not mean "there were no failures." It means "the observed failure rate is within the expected statistical envelope for this system." A test with `minPassRate = 0.95` that observes 4 failures in 100 samples passes — but 4 failures still occurred.

Conversely, a verdict of FAIL does not necessarily mean "the system is broken." It means "the observed failure rate deviates from the baseline by a statistically significant margin." The deviation may be caused by a genuine regression, a transient environmental issue, or a baseline that no longer reflects current conditions.

**Raw failure counts matter.** PUnit always displays the raw counts (e.g., `91/100`) alongside the statistical verdict. Operators should pay attention to both:

- A PASS with 0 failures is qualitatively different from a PASS with 5 failures, even though both meet the threshold
- A FAIL with 89/100 against a threshold of 0.90 is a marginal miss; a FAIL with 50/100 is a catastrophic regression

**Proportionate response.** The verdict determines the urgency and nature of the investigation, not whether to investigate at all. When a stochastic system reports failures — even within threshold — those failures may warrant examination. PUnit provides the statistical context to determine whether the failure rate is expected or anomalous; the operator decides what to do about it.

---

## Part 11: The HTML Report

PUnit can generate a standalone HTML report that summarises every probabilistic test verdict from a test run. The report is a single `index.html` file with embedded CSS — no external dependencies — and is designed to be opened in a browser, shared with stakeholders, or archived alongside CI artefacts. This works in very much the same way as familiar reporting tools like JaCoCo.

### Generating the Report

When the PUnit Gradle plugin is applied, a `punitReport` task is registered automatically. The workflow is:

1. **Run your tests.** During execution, PUnit writes an XML verdict file for each probabilistic test into `build/reports/punit/xml/`.
2. **Generate the report.** Run the `punitReport` task to transform those XML files into an HTML report.

```bash
# Run tests, then generate the report
./gradlew test punitReport
```

The two tasks can also be run independently. For example, you can re-generate the report from existing XML verdicts without re-running the tests:

```bash
./gradlew punitReport
```

### Report Location and Contents

The HTML report is written to:

```
build/reports/punit/html/index.html
```

The report contains:

- **Summary statistics** — total test count with pass, fail, and inconclusive breakdowns.
- **Grouped results** — tests grouped by use-case ID (or class name when no use case is declared), presented in an expandable table.
- **Per-test detail** — each test row expands to show the verdict summary (observed pass rate, sample count, threshold, termination status) and a nested statistical analysis panel (confidence interval, z-score, p-value, baseline provenance).
- **Latency percentiles** — p50, p95, and p99 columns for tests that record latency.
- **Inconclusive guidance** — when covariate misalignment produces inconclusive verdicts, the report shows a banner and per-test guidance with the command to re-run the relevant experiment.

### Report Configuration

XML verdict collection is enabled by default. The following properties control report behaviour:

| Property               | Environment Variable | Default                   | Description                                           |
|------------------------|----------------------|---------------------------|-------------------------------------------------------|
| `punit.report.dir`     | `PUNIT_REPORT_DIR`   | `build/reports/punit/xml` | Directory for XML verdict files                       |
| `punit.report.enabled` | —                    | `true`                    | Set to `false` to disable XML verdict file generation |

**Example:** Disable XML verdict collection for a quick local run:

```bash
./gradlew test -Dpunit.report.enabled=false
```

### RP07 Verdict XML: Field Reference

Each test invocation produces one XML file named `{className}.{methodName}.xml`.

Test identity comes from the JVM stack: the nearest `@ProbabilisticTest`-annotated method. When `PUnit.testing(...)` is invoked outside JUnit (hand-driven integration runs, scripted measurement runs), the resolver falls back to the use case identifier in place of `className`/`methodName`. Emission still works; filenames are just less informative.

The table below describes what each field of an emitted verdict carries.

| RP07 element / attribute         | Behaviour                                                                                                                                   |
|----------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `<identity use-case-id>`         | The use case's `id()`. Falls back to `className` if `id()` returns blank.                                                                   |
| `<execution planned-samples>`    | From `Sampling.samples()`.                                                                                                                  |
| `<execution warmup>`             | Not surfaced; builder default (omitted attribute).                                                                                          |
| `<execution applied-multiplier>` | Not surfaced. `samplesMultiplier` is not applied.                                                                                           |
| `<provenance spec-filename>`     | Populated for empirical criteria that resolve a baseline. Absent when only contractual criteria run.                                        |
| `<provenance contract-ref>`      | From `.contractRef(...)` on the test builder when set; absent otherwise.                                                                    |
| `<cost total-tokens>`            | From the engine's per-sample token tracker.                                                                                                 |
| `<cost ...-budget>`              | Always `0` (= unlimited). Per-method budget configuration is not surfaced on the verdict.                                                   |
| `<pacing>`                       | Omitted. Pacing configuration is not surfaced on the verdict.                                                                               |
| `<latency>`                      | `<observed>` percentiles populated; `<evaluations>` empty until per-percentile latency assertions are surfaced as criteria.                 |
| `<postcondition-failures>`       | Populated from the per-clause failure histogram. Each clause shows its description, count, and up to three retained input/reason exemplars. |
| `<termination>`                  | One of `COMPLETED`, `METHOD_TIME_BUDGET_EXHAUSTED`, `METHOD_TOKEN_BUDGET_EXHAUSTED`.                                                        |
| `correlation-id` (on root)       | Auto-generated `v:xxxxxx` UUID fragment unless explicitly supplied via `RunMetadata.correlationId`.                                         |

Custom verdict sinks (Slack notifiers, observability webhooks, archive uploaders) can be plugged in via `VerdictSinkBus.register(VerdictSink)`. The framework's default `VerdictXmlSink` is installed automatically; `register(...)` adds your sink alongside it. Tests that need exclusive control can use `VerdictSinkBus.replaceAll(...)`.

---

## Appendices

### A: Configuration Reference

PUnit configuration follows a consistent resolution order across all properties:

**System property > Environment variable > Annotation value > Framework default**

This allows different configurations per environment (local dev, PR builds, nightly CI, staging, production) without code changes. System properties take highest precedence, enabling per-invocation overrides via `-D` flags or Gradle's `-P` forwarding.

#### General Test Configuration

These properties override the corresponding `@ProbabilisticTest` annotation attributes.

| Property                  | Environment Variable       | Default | Description                                    |
|---------------------------|----------------------------|---------|------------------------------------------------|
| `punit.samples`           | `PUNIT_SAMPLES`            | `100`   | Override sample count per test                 |
| `punit.minPassRate`       | `PUNIT_MIN_PASS_RATE`      | —       | Override minimum pass rate threshold (0.0–1.0) |
| `punit.samplesMultiplier` | `PUNIT_SAMPLES_MULTIPLIER` | `1.0`   | Multiplier applied to sample count             |

**Example:** Run a quick smoke check with 10 samples instead of the annotation-specified count:

```bash
./gradlew test -Dpunit.samples=10
```

**Example:** Double all sample counts for a nightly CI run:

```bash
export PUNIT_SAMPLES_MULTIPLIER=2.0
./gradlew test
```

#### Method-Level Budget

These override the corresponding `@ProbabilisticTest` annotation attributes. When both method-level and suite-level budgets are set, all are enforced — the first exhausted budget triggers termination.

| Property             | Environment Variable   | Default | Description                                               |
|----------------------|------------------------|---------|-----------------------------------------------------------|
| `punit.timeBudgetMs` | `PUNIT_TIME_BUDGET_MS` | `0`     | Time budget per test in milliseconds (0 = unlimited)      |
| `punit.tokenBudget`  | `PUNIT_TOKEN_BUDGET`   | `0`     | Token budget per test (0 = unlimited)                     |
| `punit.tokenCharge`  | `PUNIT_TOKEN_CHARGE`   | `0`     | Fixed token charge per sample (0 = use dynamic recording) |

**Example:** Cap each test at 30 seconds:

```bash
./gradlew test -Dpunit.timeBudgetMs=30000
```

#### Suite-Level Budget

Suite budgets apply across **all** probabilistic tests in the JVM. They are configured exclusively via system properties or environment variables — there is no annotation equivalent.

| Property                        | Environment Variable              | Default | Description                                   |
|---------------------------------|-----------------------------------|---------|-----------------------------------------------|
| `punit.suite.timeBudgetMs`      | `PUNIT_SUITE_TIME_BUDGET_MS`      | `0`     | Time budget for entire suite (0 = unlimited)  |
| `punit.suite.tokenBudget`       | `PUNIT_SUITE_TOKEN_BUDGET`        | `0`     | Token budget for entire suite (0 = unlimited) |
| `punit.suite.onBudgetExhausted` | `PUNIT_SUITE_ON_BUDGET_EXHAUSTED` | `FAIL`  | `FAIL` or `EVALUATE_PARTIAL`                  |

**Budget exhaustion behaviour:**

- `FAIL` — remaining tests are skipped and the suite fails.
- `EVALUATE_PARTIAL` — remaining tests are skipped, but completed tests are evaluated normally (partial results are valid).

**Budget hierarchy:** Suite > Class > Method. First exhausted budget triggers termination.

#### Pacing

Pacing controls request rates for rate-limited APIs (LLM providers, payment gateways). These override the corresponding `@Pacing` annotation attributes. When multiple constraints are specified, the most restrictive wins.

| Property                       | Environment Variable             | Default | Description                                |
|--------------------------------|----------------------------------|---------|--------------------------------------------|
| `punit.pacing.maxRps`          | `PUNIT_PACING_MAX_RPS`           | `0`     | Maximum requests per second (0 = no limit) |
| `punit.pacing.maxRpm`          | `PUNIT_PACING_MAX_RPM`           | `0`     | Maximum requests per minute (0 = no limit) |
| `punit.pacing.maxRph`          | `PUNIT_PACING_MAX_RPH`           | `0`     | Maximum requests per hour (0 = no limit)   |
| `punit.pacing.maxConcurrent`   | `PUNIT_PACING_MAX_CONCURRENT`    | `0`     | Maximum concurrent requests (0 = no limit) |
| `punit.pacing.minMsPerSample`  | `PUNIT_PACING_MIN_MS_PER_SAMPLE` | `0`     | Minimum delay per sample in ms (0 = none)  |

**Example:** Throttle to 60 requests per minute for a rate-limited API:

```bash
export PUNIT_PACING_MAX_RPM=60
./gradlew test
```

#### Specification Resolution

Specs resolve using **layered fallback**: environment-local directory first, classpath second. This enables environment-specific baselines without modifying checked-in specs.

| Property         | Environment Variable | Default | Description                                            |
|------------------|----------------------|---------|--------------------------------------------------------|
| `punit.spec.dir` | `PUNIT_SPEC_DIR`     | —       | Environment-local spec directory (overrides classpath) |

**Classpath fallback:** `punit/specs/` on the classpath (checked-in specs in `src/test/resources/punit/specs/`).

**Per-dimension spec naming convention:**

- `{UseCaseId}.yaml` — Functional baseline spec
- `{UseCaseId}.latency.yaml` — Latency baseline spec

Older single-file specs containing both dimensions are handled transparently — the framework extracts the relevant dimension's data.

#### Experiment Output Directories

| Property                        | Environment Variable             | Default                                      | Description                |
|---------------------------------|----------------------------------|----------------------------------------------|----------------------------|
| `punit.specs.outputDir`         | `PUNIT_SPECS_OUTPUT_DIR`         | `src/test/resources/punit/specs/`            | MEASURE experiment output  |
| `punit.explorations.outputDir`  | `PUNIT_EXPLORATIONS_OUTPUT_DIR`  | `build/punit/explorations/`                  | EXPLORE experiment output  |
| `punit.optimizations.outputDir` | `PUNIT_OPTIMIZATIONS_OUTPUT_DIR` | `build/punit/optimizations/`                 | OPTIMIZE experiment output |

The three defaults differ deliberately:

- **MEASURE specs are inputs to subsequent `@ProbabilisticTest` runs** (including in CI). They live alongside source and are intended to be committed to the repository so that the probabilistic test's baseline travels with the code.
- **EXPLORE and OPTIMIZE outputs are human-review artefacts** with no downstream programmatic consumer. They default to `build/` so that running an experiment doesn't produce uncommitted-file noise in the source tree. Commit them only when you want a historical record, not as a test requirement.

This split was introduced in 0.6.0. Prior releases wrote all three under `src/test/resources/punit/`. If you were relying on EXPLORE or OPTIMIZE outputs landing under `src/`, set `punit.explorations.outputDir` / `punit.optimizations.outputDir` (or `punit.explorationsDir` / `punit.optimizationsDir` in the Gradle plugin extension) explicitly.

#### Transparent Statistics

Transparent mode produces verbose statistical explanations of test verdicts, designed for auditors, regulators, and stakeholders who need to understand how PUnit reaches its verdicts.

| Property                  | Environment Variable       | Default   | Description                                  |
|---------------------------|----------------------------|-----------|----------------------------------------------|
| `punit.stats.transparent` | `PUNIT_STATS_TRANSPARENT`  | `false`   | Enable transparent statistics mode           |
| `punit.stats.detailLevel` | `PUNIT_STATS_DETAIL_LEVEL` | `VERBOSE` | Detail level: `SUMMARY` or `VERBOSE`         |
| `punit.stats.format`      | `PUNIT_STATS_FORMAT`       | `CONSOLE` | Output format: `CONSOLE`, `MARKDOWN`, `JSON` |

`punit.stats.transparent` has an additional precedence level: the `@ProbabilisticTest(transparentStats = true)` annotation attribute takes highest priority, above the system property.

**Detail levels:**

- `SUMMARY` — verdict and key numbers only (observed rate, threshold, sample count).
- `VERBOSE` — full explanation including hypothesis test, statistical inference (standard error, confidence interval, z-test, p-value).

**Example:** Generate markdown-formatted summaries for a compliance report:

```bash
./gradlew test -Dpunit.stats.transparent=true -Dpunit.stats.detailLevel=SUMMARY -Dpunit.stats.format=MARKDOWN
```

#### Latency Enforcement

| Property                | Environment Variable    | Default | Description                                                          |
|-------------------------|-------------------------|---------|----------------------------------------------------------------------|
| `punit.latency.enforce` | `PUNIT_LATENCY_ENFORCE` | `false` | Enforce baseline-derived latency assertions (default: advisory only) |

Explicit `@Latency` thresholds (at least one percentile value ≥ 0) are always enforced — this flag has no effect on them. This flag controls baseline-derived thresholds only: when disabled (default), baseline-derived latency breaches produce warnings but do not fail the test; when enabled, they are enforced like explicit thresholds.

#### Warmup

Warmup is configured exclusively via the `@UseCase` annotation — there is no system property or environment variable override. This is deliberate: warmup reflects the SUT's caching and initialisation characteristics, which are intrinsic to the system and do not change between environments.

Use warmup with services whose initialisation time is significant, such as those with complex startup logic or external dependencies, or caching. This will result in an experimental baseline, which skips over samples, which are not representative of the larger population.

| Attribute              | Default | Description                                                   |
|------------------------|---------|---------------------------------------------------------------|
| `@UseCase(warmup = N)` | `0`     | Number of invocations to discard before counted samples begin |

See [Warmup: Achieving Statistical Stationarity](#warmup-achieving-statistical-stationarity) for full documentation.

#### Sentinel Environment Metadata

These properties are used by the Sentinel runtime to tag verdicts with deployment context. They are resolved by `EnvironmentMetadata.fromEnvironment()`.

| Property            | Environment Variable | Default   | Description                                        |
|---------------------|----------------------|-----------|----------------------------------------------------|
| `punit.environment` | `PUNIT_ENVIRONMENT`  | `unknown` | Environment identifier (e.g., `prod`, `staging`)   |
| `punit.instanceId`  | `PUNIT_INSTANCE_ID`  | hostname  | Instance identifier for multi-instance deployments |

#### LLM Provider Configuration

The example infrastructure supports switching between mock and real LLM providers. This is useful for running experiments with actual LLM APIs.

| Property                      | Environment Variable       | Default                        | Description                                       |
|-------------------------------|----------------------------|--------------------------------|---------------------------------------------------|
| `punit.llm.mode`              | `PUNIT_LLM_MODE`           | `mock`                         | Mode: `mock` or `real`                            |
| `punit.llm.openai.key`        | `OPENAI_API_KEY`           | —                              | OpenAI API key (required for OpenAI models)       |
| `punit.llm.anthropic.key`     | `ANTHROPIC_API_KEY`        | —                              | Anthropic API key (required for Anthropic models) |
| `punit.llm.openai.baseUrl`    | `OPENAI_BASE_URL`          | `https://api.openai.com/v1`    | OpenAI API base URL                               |
| `punit.llm.anthropic.baseUrl` | `ANTHROPIC_BASE_URL`       | `https://api.anthropic.com/v1` | Anthropic API base URL                            |
| `punit.llm.timeout`           | `PUNIT_LLM_TIMEOUT`        | `30000`                        | Request timeout in milliseconds                   |
| `punit.llm.mutation.model`    | `PUNIT_LLM_MUTATION_MODEL` | `gpt-4o-mini`                  | Model used for LLM-powered prompt mutations       |

**Mode switching:**

- **`mock`** (default) — Uses `MockChatLlm` which returns deterministic responses. No API keys required. Safe for CI.
- **`real`** — Uses `RoutingChatLlm` which routes to the appropriate provider based on the model name specified in each call.

**Model → Provider routing:**

In `real` mode, the model name determines which provider handles the request:

| Model Pattern                                 | Provider  | Examples                                                  |
|-----------------------------------------------|-----------|-----------------------------------------------------------|
| `gpt-*`, `o1-*`, `o3-*`, `text-*`, `davinci*` | OpenAI    | `gpt-4o`, `gpt-4o-mini`, `o1-preview`                     |
| `claude-*`                                    | Anthropic | `claude-haiku-4-5-20251001`, `claude-sonnet-4-5-20250929` |

**Example: Running experiments with real LLMs:**

```bash
# Set mode and API keys
export PUNIT_LLM_MODE=real
export OPENAI_API_KEY=sk-...
export ANTHROPIC_API_KEY=sk-ant-...

# Run experiments - model names in factor providers determine which APIs are called
./gradlew exp -Prun=ShoppingBasketExplore.compareModels
```

You only need API keys for the providers you're actually using. If your experiment only uses OpenAI models, you don't need an Anthropic API key.

### B: Experiment Output Formats

Each experiment type produces YAML files in different directories with different structures. The extracts below are taken from real experiment outputs. Sections omitted for brevity are marked with `# ...`.

#### MEASURE Output

Location: `src/test/resources/punit/specs/{UseCaseId}.yaml`

MEASURE produces the baseline spec that probabilistic tests use to derive thresholds. Its distinguishing features are: **covariates** (the environmental conditions under which the baseline was recorded), **requirements** (the derived `minPassRate`), **extended statistics** (standard error, confidence interval), and optionally a **latency** section. When the use case declares covariates, the filename includes a **footprint** suffix that encodes the covariate combination.

```yaml
# Empirical Baseline for ShoppingBasketUseCase                          ← header comment
# Generated automatically by punit experiment runner
# DO NOT EDIT - create a specification based on this baseline instead

schemaVersion: punit-spec-1
useCaseId: ShoppingBasketUseCase
generatedAt: 2026-03-03T17:45:51.370476Z
experimentClass: org.javai.punit.examples.experiments.ShoppingBasketMeasure
experimentMethod: measureBaseline
footprint: 8e7245de                                                     ← covariate fingerprint
covariates:                                                             ← environmental conditions
  day_of_week: WEEKDAY
  time_of_day: 16:00/4h
  llm_model: gpt-4o-mini
  temperature: 0.3
execution:
  samplesPlanned: 1000
  samplesExecuted: 1000
  terminationReason: COMPLETED
requirements:
  minPassRate: 0.7418                                                   ← derived threshold
statistics:
  successRate:
    observed: 0.7680
    standardError: 0.0133
    confidenceInterval95: [0.7418, 0.7942]                              ← 95% CI
  successes: 768
  failures: 232
  failureDistribution:                                                  ← per-postcondition breakdown
    Valid shopping action: 232
latency:                                                                ← latency distribution
  sampleCount: 768
  meanMs: 0
  maxMs: 3
  sortedLatenciesMs: [0, 0, 0, ..., 1, 1, 2, 3]                         ← full sorted vector (elided)
cost:
  totalTimeMs: 557
  avgTimePerSampleMs: 0
  totalTokens: 197288
  avgTokensPerSample: 197
contentFingerprint: 90fc6a06ededa50e...                                 ← integrity hash
```

When latency data is present, MEASURE also produces a dedicated latency spec at `{UseCaseId}-latency.yaml` containing only the `execution` and `latency` sections.

#### EXPLORE Output

Location: `build/punit/explorations/{UseCaseId}/{configName}.yaml`

EXPLORE produces **one file per configuration**, enabling side-by-side comparison. Its distinguishing feature is the **result projection** — a per-sample record of inputs, postcondition outcomes, and raw content that lets you inspect exactly what the system produced under each configuration. The `useCaseId` includes the configuration name as a path suffix.

```yaml
# Empirical Baseline for ShoppingBasketUseCase/model-gpt-4o             ← config in ID
# Generated automatically by punit experiment runner
# DO NOT EDIT - create a specification based on this baseline instead

schemaVersion: punit-spec-1
useCaseId: ShoppingBasketUseCase/model-gpt-4o                           ← use case + config
generatedAt: 2026-03-03T17:41:25.667899Z
experimentClass: org.javai.punit.examples.experiments.ShoppingBasketExplore
experimentMethod: compareModels
execution:
  samplesPlanned: 20
  samplesExecuted: 20
  terminationReason: COMPLETED
statistics:
  observed: 0.9000
  successes: 18
  failures: 2
latency:
  sampleCount: 18
  # ...
cost:
  totalTimeMs: 194
  avgTimePerSampleMs: 9
  totalTokens: 3922
  avgTokensPerSample: 196
resultProjection:                                                       ← per-sample detail
# ────── anchor:0dfe8af7 ──────
  sample[0]:
    input: Add some apples
    postconditions:
      Contains valid actions: passed
      Response has content: passed
      Valid shopping action: passed
    executionTimeMs: 0
    content: |
      ChatResponse[content={"actions": [...]}, promptTokens=177, completionTokens=20]
# ────── anchor:0c45c028 ──────
  sample[1]:
    # ...
# ────── anchor:17610c9a ──────
  sample[4]:                                                            ← a failed sample
    input: Add some apples
    postconditions:
      Contains valid actions: failed                                    ← postcondition failure
      Response has content: passed
      Valid shopping action: failed
    executionTimeMs: 0
    content: |
      ChatResponse[content={"operations": [...]}, ...]                  ← wrong schema
  # ... remaining samples
contentFingerprint: b8c458e7da03edb9...
```

#### OPTIMIZE Output

Location: `build/punit/optimizations/{UseCaseId}/{experimentId}_{timestamp}.yaml`

OPTIMIZE produces a history of iterations showing the optimization trajectory. Its distinguishing features are: the **control factor** being varied, the **optimization policy** (objective, scorer, mutator, termination), a **best iteration** summary (when at least one iteration scored above the minimum threshold), and the full **iteration log** with per-iteration scores, statistics, and the control factor value tried.

```yaml
# Optimization History for ShoppingBasketUseCase
# Primary output: the best value for the control factor
# Generated automatically by punit @OptimizeExperiment

schemaVersion: punit-optimize-1
useCaseId: ShoppingBasketUseCase
experimentId: prompt-optimization-v1
generatedAt: 2026-03-03T17:43:11.308105Z
controlFactor:                                                          ← what is being optimized
  name: systemPrompt
  type: String
optimization:                                                           ← optimization policy
  objective: MAXIMIZE
  scorer: Success rate (higher is better) - measures % of samples where all criteria passed
  mutator: Mock (deterministic) prompt progression - scripted sequence of improvements
  terminationPolicy: Max 10 iterations OR No improvement for 3 iterations
timing:
  startTime: 2026-03-03T17:43:11.308105Z
  endTime: 2026-03-03T17:43:27.771902Z
  totalDurationMs: 16463
bestIteration:                                                          ← best result (when present)
  iterationNumber: 3
  score: 87.5%
  bestControlFactor: |
    You are a shopping assistant. Convert the user's request into JSON...
  statistics:
    sampleCount: 8
    observed: 0.8750
    successes: 7
    failures: 1
summary:
  totalIterations: 10
  totalTokens: 0
  initialScore: 0.0%
  bestScore: 87.5%
  scoreImprovement: 87.5%
terminationReason:
  cause: MAX_ITERATIONS
  message: "Reached maximum iterations: 10"
iterations:                                                             ← full iteration history
  - iterationNumber: 0
    status: BELOW_THRESHOLD
    score: 0.0%
    controlFactor: |                                                    ← value tried
      You are a shopping assistant. Convert the user's request into
      a JSON list of shopping basket operations.
    statistics:
      sampleCount: 8
      observed: 0.0000
      successes: 0
      failures: 8
    totalTokens: 0
    meanLatencyMs: 0.00
    failureReason: Score 0.00 is below minimum threshold 0.50           ← why this iteration failed
  - iterationNumber: 1
    status: BELOW_THRESHOLD
    score: 0.0%
    controlFactor: |
      You are a shopping assistant. Convert the user's request into
      a JSON list of shopping basket operations.

      IMPORTANT: Respond with ONLY valid JSON. No explanations, ...
    # ...
  # ... remaining iterations
contentFingerprint: b4f6de1a05ca9aff...
```

When no iteration scores above the minimum threshold, the `bestIteration` section is omitted and `scoreImprovement` is `0.0%`.

### C: Glossary

For a complete list of terms and definitions used in PUnit, see the [PUnit Glossary](GLOSSARY.md).

---

## Next Steps

- Explore the examples in the `punitexamples` project
- See [OPERATIONAL-FLOW.md](OPERATIONAL-FLOW.md) for the end-to-end workflow in detail
- See [STATISTICAL-COMPANION.md](STATISTICAL-COMPANION.md) for mathematical foundations
- See [GLOSSARY.md](GLOSSARY.md) for complete term definitions
