# PUnit User Guide

*Probabilistic testing for systems characterized by uncertainty*

All attribution licensing is ARL.

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
- [Part 2: The MEASURE Experiment](#part-2-the-measure-experiment)
  - [When No Normative Threshold Exists](#when-no-normative-threshold-exists)
  - [Running a MEASURE Experiment](#running-a-measure-experiment)
  - [The Spec: A Baseline for Regression Testing](#the-spec-a-baseline-for-regression-testing)
  - [Conformance Testing with Specs](#conformance-testing-with-specs)
  - [Baseline Expiration](#baseline-expiration)
- [Part 3: The Use Case](#part-3-the-use-case)
  - [Why Experiments and Tests Must Share the Same Target](#why-experiments-and-tests-must-share-the-same-target)
  - [The Service Contract](#the-service-contract)
  - [The UseCaseOutcome](#the-usecaseoutcome)
  - [Instance Conformance](#instance-conformance)
  - [Duration Constraints](#duration-constraints)
  - [The UseCaseProvider Pattern](#the-usecaseprovider-pattern)
  - [Input Sources](#input-sources)
  - [The Use Case in Full](#the-use-case-in-full)
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
- [Part 8: The Statistical Core](#part-8-the-statistical-core)
  - [Bernoulli Trials](#bernoulli-trials)
  - [Transparent Statistics Mode](#transparent-statistics-mode)
  - [Further Reading](#further-reading)
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

But assuming a feature can behave non-deterministically, instead of asking "Did it work?" PUnit asks "Does it work reliably enough?"

PUnit provides:

1. **Probabilistic tests** — Run a test method many times, count successes, and evaluate the observed success rate against a threshold
2. **Experiments** — Measure, explore, and optimize system behaviour to discover empirical thresholds
3. **Statistical rigour** — Proper confidence intervals, power analysis, and qualified verdicts

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
git clone <punitexamples-repo-url>
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
═══════════════════════════════════════════════════════════════
PUnit PASSED (SMOKE): apiMeetsSla
  Observed: 97.0% (97/100) >= min pass rate: 95.0%
  Threshold origin: SLA
  Contract ref: API SLA §3.1
═══════════════════════════════════════════════════════════════
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

To detect that a system is at 99.97% when the SLA requires 99.99%, you need enough samples to distinguish a 0.02% difference. With 1,000 samples, this gap is statistically invisible.

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

Three operational modes for `@ProbabilisticTest` that work without baselines. Each approach fixes two parameters and lets PUnit compute the third.

**You cannot specify all three parameters.** Attempting to fix sample size, threshold, *and* confidence simultaneously is statistically nonsensical — the parameter triangle means these values are interdependent.

**1. Threshold-First**

*"I know the pass rate must be ≥90%. Run 100 samples to verify."*

- **You specify**: Sample size + Threshold
- **PUnit computes**: The implied confidence level

```java
@ProbabilisticTest(
    samples = 100,
    minPassRate = 0.90
)
void thresholdFirst(ShoppingBasketUseCase useCase, @Factor("instruction") String instruction) {
    useCase.translateInstruction(instruction).assertAll();
}
```

**2. Sample-Size-First**

*"I have budget for 100 samples. What threshold can I verify with 95% confidence?"*

- **You specify**: Sample size + Confidence level
- **PUnit computes**: The achievable threshold

```java
@ProbabilisticTest(
    samples = 100,
    thresholdConfidence = 0.95
)
void sampleSizeFirst(ShoppingBasketUseCase useCase, @Factor("instruction") String instruction) {
    useCase.translateInstruction(instruction).assertAll();
}
```

**3. Confidence-First**

*"I need to detect a 5% degradation with 95% confidence and 80% power."*

- **You specify**: Confidence level + Power + Minimum detectable effect
- **PUnit computes**: The required sample size

The `minDetectableEffect` parameter is essential here. Without it, the question "how many samples do I need?" has no finite answer — detecting arbitrarily small degradations requires arbitrarily many samples.

```java
@ProbabilisticTest(
    confidence = 0.95,
    minDetectableEffect = 0.05,
    power = 0.80
)
void confidenceFirst(ShoppingBasketUseCase useCase, @Factor("instruction") String instruction) {
    useCase.translateInstruction(instruction).assertAll();
}
```

*Source: `org.javai.punit.examples.probabilistictests.ShoppingBasketThresholdApproachesTest`*

### Understanding Test Results

**How PUnit surfaces probabilistic results in JUnit**

A probabilistic test has two layers of feedback:

- **Sample-level failures**: individual sample failures are surfaced so you can inspect real examples of where the system failed.
- **Statistical verdict**: the overall PASS/FAIL is computed from the observed pass rate and the configured threshold.

In other words: sample failures are expected and informative; the statistical verdict is the actual decision.

**Reading the statistical report**

Every probabilistic test produces a verdict with statistical context:

```
═══════════════════════════════════════════════════════════════
PUnit PASSED: testInstructionTranslation
  Observed: 94.0% (94/100) >= min pass rate: 91.9%
  Baseline: 93.5% @ N=1000 (spec: ShoppingBasketUseCase.yaml)
  Confidence: 95%
═══════════════════════════════════════════════════════════════
```

**Responding to results**

| Verdict    | What it means                             | Recommended response                  |
|------------|-------------------------------------------|---------------------------------------|
| **PASSED** | Observed rate is consistent with baseline | Low priority; likely no action needed |
| **FAILED** | Observed rate is below expected threshold | Investigate; possible regression      |

The report provides the evidence; operators provide the judgment.

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
═══════════════════════════════════════════════════════════════
PUnit PASSED: testInstructionTranslation
  Observed: 94.0% (94/100) >= min pass rate: 91.9%
  Baseline: 93.5% @ N=1000 (spec: ShoppingBasketUseCase.yaml)
  Confidence: 95%

  ⚠️  BASELINE EXPIRED
  The baseline was generated on 2025-11-15 and expired on 2025-12-15.
  System behavior may have drifted. Consider re-running MEASURE to
  establish a fresh baseline.
═══════════════════════════════════════════════════════════════
```

---

## Part 3: The Use Case

### Why Experiments and Tests Must Share the Same Target

In the examples so far, we have seen probabilistic tests with inline assertions and MEASURE experiments that record outcomes. But there is a critical constraint that binds them together: **the experiment that establishes a baseline and the test that verifies against it must evaluate the same definition of success**.

If your MEASURE experiment measures "valid JSON with correct fields" but your probabilistic test only checks "non-empty response," you have learned nothing about whether the system meets its actual requirements. You would be measuring one thing and testing another.

PUnit therefore encourages the creation of an artifact called a **Use Case**. A Use Case wraps your application's functionality and defines, via a **Service Contract**, what "success" means. Both experiments and tests exercise the same Use Case, ensuring that what you measure is exactly what you test.

This is the shared expression of correctness in PUnit:
- **Experiments** use the Use Case to observe and record behaviour
- **Probabilistic Tests** use the same Use Case to verify that behaviour meets the threshold

### The Service Contract

At the heart of every PUnit Use Case is a **Service Contract** — a formal specification of what the service must do. The contract defines postconditions that must be satisfied for an invocation to be considered successful.

Here is a contract for a shopping basket use case that translates natural language instructions into structured JSON actions:

```java
private static final ServiceContract<ServiceInput, ChatResponse> CONTRACT =
        ServiceContract.<ServiceInput, ChatResponse>define()
                .ensure("Response has content", response ->
                        response.content() != null && !response.content().isBlank()
                                ? Outcome.ok()
                                : Outcome.fail("check", "content was null or blank"))
                .derive("Valid shopping action", ShoppingActionValidator::validate)
                .ensure("Contains valid actions", result -> {
                    if (result.actions().isEmpty()) {
                        return Outcome.fail("check", "No actions in result");
                    }
                    for (ShoppingAction action : result.actions()) {
                        if (!action.context().isValidAction(action.name())) {
                            return Outcome.fail("check",
                                    "Invalid action '%s' for context %s"
                                            .formatted(action.name(), action.context()));
                        }
                    }
                    return Outcome.ok();
                })
                .build();
```

The contract has three types of clauses:

- **`ensure`** — A postcondition that must hold. Returns `Outcome.ok()` on success or `Outcome.fail(...)` with a reason.
- **`derive`** — Transforms the result (e.g., parsing JSON into domain objects) and can define nested postconditions on the derived value.
- **`ensureDurationBelow`** — A timing constraint specifying the maximum allowed execution duration.

Postconditions are evaluated in order. If any fails, subsequent ones are skipped. This creates a **fail-fast hierarchy**:

1. "Response has content" — Is there a response at all?
2. "Valid shopping action" — Can it be parsed into domain objects?
3. "Contains valid actions" — Are the parsed actions semantically valid?

**Why not just use assertions?** In traditional TDD, validation logic lives inside tests. This works for deterministic systems where we expect 100% success. But for non-deterministic systems, we need validation logic that is **non-judgmental** — logic that observes and records outcomes without immediately aborting. The Service Contract provides exactly this. One might call it **Contract-Driven Development**.

### The UseCaseOutcome

The `UseCaseOutcome` is a statement detailing how well a service performed against the postconditions defined in its contract. It captures the result, evaluates each postcondition, and records what passed, what failed, and why.

```java
public UseCaseOutcome<ChatResponse> translateInstruction(String instruction) {
    return UseCaseOutcome
            .withContract(CONTRACT)
            .input(new ServiceInput(systemPrompt, instruction, model, temperature))
            .execute(in -> llm.chat(in.prompt(), in.instruction(), in.model(), in.temperature()))
            .build();
}
```

This single artifact serves both experiments and tests:

**In experiments**, the outcome is used to:
- Establish **baseline specifications** that later power probabilistic tests (MEASURE)
- Create **diffable documents** comparing configurations (EXPLORE)
- Provide the **basis for optimization runs** where the mutator learns from failures (OPTIMIZE)

**In probabilistic tests**, the outcome is used in the simplest way possible: to assert that the contract's postconditions were met, and to fail the sample if they were not. PUnit then counts successes across many samples to determine whether the observed rate meets the required threshold.

### Instance Conformance

Beyond postconditions, use cases can validate against **expected values** — specific instances the result should match. This enables instance conformance testing:

```java
public UseCaseOutcome<ChatResponse> translateInstruction(String instruction, String expectedJson) {
    return UseCaseOutcome
            .withContract(CONTRACT)
            .input(new ServiceInput(systemPrompt, instruction, model, temperature))
            .execute(in -> llm.chat(in.prompt(), in.instruction(), in.model(), in.temperature()))
            .expecting(expectedJson, ChatResponse::content, JsonMatcher.semanticEquality())
            .build();
}
```

The outcome tracks multiple dimensions:
- `allPostconditionsSatisfied()` — Did the result meet all contract postconditions?
- `matchesExpected()` — Did the result match the expected value?
- `withinDurationLimit()` — Did execution complete within the time constraint?
- `fullySatisfied()` — All of the above

### Duration Constraints

Contracts can include timing requirements via `ensureDurationBelow`. Unlike postconditions, duration constraints are evaluated **independently** — you always learn both "was it correct?" and "was it fast enough?" regardless of which (if either) fails.

```java
private static final ServiceContract<ServiceInput, ChatResponse> CONTRACT =
        ServiceContract.<ServiceInput, ChatResponse>define()
                .ensure("Response has content", response -> ...)
                .derive("Valid JSON", JsonParser::parse)
                    .ensure("Has required fields", json -> ...)
                .ensureDurationBelow(Duration.ofMillis(500))
                .build();
```

This independence is deliberate. A slow-but-correct response and a fast-but-wrong response fail for different reasons — diagnostics should show both dimensions:

```
Sample 47: FAIL
  ✓ Response has content
  ✓ Valid JSON
  ✓ Has required fields
  ✗ Duration: 847ms exceeded limit of 500ms
```

### The UseCaseProvider Pattern

Use cases are registered and injected via `UseCaseProvider`:

```java
public class ShoppingBasketTest {

    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    @BeforeEach
    void setUp() {
        provider.register(ShoppingBasketUseCase.class, ShoppingBasketUseCase::new);
    }

    @ProbabilisticTest(useCase = ShoppingBasketUseCase.class, samples = 100)
    void testInstructionTranslation(ShoppingBasketUseCase useCase, ...) {
        // useCase is automatically injected
    }
}
```

### Input Sources

The `@InputSource` annotation provides test inputs for experiments and probabilistic tests. Inputs are cycled across samples, ensuring coverage of the input space.

**Method Source:**

```java
@ProbabilisticTest(samples = 100)
@InputSource("testInstructions")
void myTest(ShoppingBasketUseCase useCase, String instruction) {
    useCase.translateInstruction(instruction).assertAll();
}

static Stream<String> testInstructions() {
    return Stream.of("Add milk", "Remove bread", "Clear cart");
}
```

**File Source (JSON):**

```java
record TestInput(String instruction, String expected) {}

@ProbabilisticTest(samples = 100)
@InputSource(file = "fixtures/inputs.json")
void myTest(ShoppingBasketUseCase useCase, TestInput input) {
    useCase.translateInstruction(input.instruction(), input.expected()).assertAll();
}
```

The JSON file contains an array matching the record structure:
```json
[
  {"instruction": "Add 2 apples", "expected": "{...}"},
  {"instruction": "Clear the basket", "expected": "{...}"}
]
```

**Explicit Input Parameter with `@Input`:**

When a method has multiple parameters that could receive the input value, use `@Input` to explicitly mark the target parameter:

```java
@ExploreExperiment(useCase = ShoppingBasketUseCase.class, samplesPerConfig = 10)
@InputSource(file = "fixtures/shopping-instructions.json")
void exploreInputVariations(
        ShoppingBasketUseCase useCase,
        @Input InputData inputData,    // Explicitly marked as input target
        OutcomeCaptor captor
) {
    captor.record(useCase.translateInstruction(inputData.instruction()));
}
```

Without `@Input`, the framework auto-detects the input parameter by excluding framework types (UseCase, OutcomeCaptor, TokenChargeRecorder) and `@Factor`-annotated parameters. Use `@Input` when:
- The method has multiple candidate parameters
- You want to be explicit for clarity
- Auto-detection picks the wrong parameter

**Sample Distribution:**

Samples are distributed evenly across inputs:
- 100 samples with 10 inputs = 10 samples per input
- Each input is tested the same number of times (remainders go to early inputs)

**Choosing Method vs File Source:**

| Use Case                     | Recommendation                             |
|------------------------------|--------------------------------------------|
| Simple string inputs         | Method source (inline, version-controlled) |
| Dataset with expected values | File source (easier to maintain, share)    |
| Generated/computed inputs    | Method source (programmatic)               |
| Large input sets             | File source (cleaner code)                 |

### The Use Case in Full

The full implementation demonstrates how all PUnit concepts come together in a single class:

```java
@UseCase(
    description = "Translate natural language shopping instructions to structured actions",
    covariateDayOfWeek = {@DayGroup({SATURDAY, SUNDAY})},
    covariateTimeOfDay = {"08:00/4h", "16:00/4h"},
    covariates = {
        @Covariate(key = "llm_model", category = CovariateCategory.CONFIGURATION),
        @Covariate(key = "temperature", category = CovariateCategory.CONFIGURATION)
    }
)
public class ShoppingBasketUseCase {

    @FactorGetter
    @CovariateSource("llm_model")
    public String getModel() { return model; }

    @FactorSetter("llm_model")
    public void setModel(String model) { this.model = model; }

    public UseCaseOutcome translateInstruction(String instruction) {
        // Call LLM, validate response, return outcome with criteria
    }
}
```

Key elements:

- **`@UseCase`** — Declares the use case and its covariates (environmental factors that may affect behaviour)
- **`@FactorGetter` / `@FactorSetter`** — Allow experiments to read and manipulate configuration
- **`@CovariateSource`** — Links factors to covariate tracking for baseline selection
- **`UseCaseOutcome`** — Bundles result data with success criteria

*Source: `org.javai.punit.examples.usecases.ShoppingBasketUseCase`*

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

```java
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
    useCase.setModel(model);
    useCase.setTemperature(0.3);  // Fixed for fair comparison
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
src/test/resources/punit/explorations/ShoppingBasketUseCase/
├── model-gpt-4o_temp-0.0.yaml
├── model-gpt-4o_temp-0.5.yaml
├── model-gpt-4o_temp-1.0.yaml
└── ...
```

Compare with standard diff tools to identify the preferred configuration.

```bash
./gradlew exp -Prun=ShoppingBasketExplore.compareModels
```

*Source: `org.javai.punit.examples.experiments.ShoppingBasketExplore`*

### Multi-Factor Exploration

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
    useCase.setModel(model);
    useCase.setTemperature(temperature);
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

**Note:** When using `@InputSource`, each optimization iteration tests the control factor against ALL inputs in the source. Don't specify `samplesPerIteration` when using `@InputSource` — the effective samples per iteration equals the number of inputs.

### Scorers and Mutators

- **Scorer** — Evaluates each iteration's aggregate results and returns a score
- **Mutator** — Generates new control factor values based on history

**Termination conditions:**

- `maxIterations` — Hard stop after N iterations
- `noImprovementWindow` — Stop if no improvement for N consecutive iterations

**Output:**

```
src/test/resources/punit/optimizations/ShoppingBasketUseCase/
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

By default, experiments use mock LLMs for fast, free, deterministic results. To run with real LLM providers, set the mode and provide API keys:

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

### Covariate-Aware Baseline Selection

Covariates are environmental factors that may affect system behaviour.

**What are covariates?**

- **Temporal**: Time of day, weekday vs weekend, season
- **Infrastructure**: Region, instance type, API version
- **Configuration**: Model, temperature, prompt variant

**Why they matter:** An LLM's behaviour may differ between weekdays and weekends (different load patterns), or between models. Testing against the wrong baseline produces misleading results.

**Intelligent baseline selection:** When a probabilistic test runs, PUnit examines the current execution context (covariates) and selects the most appropriate baseline. If one or more covariates don't match, PUnit qualifies the verdict with a warning:

```
═══════════════════════════════════════════════════════════════
PUnit PASSED: testInstructionTranslation
  Observed: 91.0% (91/100) >= min pass rate: 91.9%
  Baseline: 93.5% @ N=1000 (spec: ShoppingBasketUseCase.yaml)
  Confidence: 95%

  ⚠️  COVARIATE NON-CONFORMANCE
  The test is running under different conditions than the baseline:
    • day_of_week: baseline=WEEKDAY, current=WEEKEND
    • time_of_day: baseline=MORNING, current=EVENING

  Statistical inference may be less reliable. Consider whether
  these differences affect the validity of the comparison.
═══════════════════════════════════════════════════════════════
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
| **Suite**  | All tests in run   | *Planned for future release*             |

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
| `FAIL`             | Immediately fail the test when budget is exhausted. This is the **default** and most conservative option — you asked for N samples but couldn't afford them.                                                  |
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

## Part 8: The Statistical Core

A brief look at the statistical engine that powers PUnit.

### Bernoulli Trials

At its heart, PUnit models each sample as a **Bernoulli trial** — an experiment with exactly two outcomes: success or failure, with failure being defined as non-conformance to the use case's contract. When you run a probabilistic test:

1. Each sample execution is a Bernoulli trial with unknown success probability *p*
2. The baseline spec provides an estimate of *p* from prior measurement
3. PUnit uses the binomial distribution to determine whether the observed success count is consistent with the baseline

This statistical machinery runs automatically when a regression test executes. Users don't interact with it directly — they simply write tests, and PUnit applies rigorous statistical inference under the hood.

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

```
══════════════════════════════════════════════════════════════════════════════
STATISTICAL ANALYSIS: shouldReturnValidJson
══════════════════════════════════════════════════════════════════════════════

HYPOTHESIS TEST
  H₀ (null):        True success rate π ≤ 0.85
  H₁ (alternative): True success rate π > 0.85
  Test type:        One-sided binomial proportion test

OBSERVED DATA
  Sample size (n):     100
  Successes (k):       87
  Observed rate (p̂):   0.870

STATISTICAL INFERENCE
  Standard error:      SE = 0.0336
  95% Confidence interval: [0.804, 0.936]
  p-value:             0.288

VERDICT
  Result:              PASS

══════════════════════════════════════════════════════════════════════════════
```

### Further Reading

For the mathematical foundations — confidence interval calculations, power analysis, threshold derivation formulas — see [STATISTICAL-COMPANION.md](STATISTICAL-COMPANION.md).

---

## Appendices

### A: Configuration Reference

PUnit configuration follows this resolution order: System property → Environment variable → Annotation value → Framework default.

| Property                        | Environment Variable             | Description                   |
|---------------------------------|----------------------------------|-------------------------------|
| `punit.samples`                 | `PUNIT_SAMPLES`                  | Override sample count         |
| `punit.stats.transparent`       | `PUNIT_STATS_TRANSPARENT`        | Enable transparent statistics |
| `punit.specs.outputDir`         | `PUNIT_SPECS_OUTPUT_DIR`         | Spec output directory         |
| `punit.explorations.outputDir`  | `PUNIT_EXPLORATIONS_OUTPUT_DIR`  | Exploration output directory  |
| `punit.optimizations.outputDir` | `PUNIT_OPTIMIZATIONS_OUTPUT_DIR` | Optimization output directory |

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

Each experiment type produces YAML files in different directories with different structures:

#### MEASURE Output

Location: `src/test/resources/punit/specs/{UseCaseId}.yaml`

MEASURE produces baseline specs used by probabilistic tests:

```yaml
schemaVersion: punit-spec-2
specId: ShoppingBasketUseCase
useCaseId: ShoppingBasketUseCase
generatedAt: 2026-01-15T10:30:00Z

empiricalBasis:
  samples: 1000
  successes: 935
  generatedAt: 2026-01-15T10:30:00Z

covariates:
  llm_model: gpt-4o
  temperature: 0.3
  day_of_week: WEEKDAY

extendedStatistics:
  standardError: 0.0078
  confidenceInterval:
    lower: 0.919
    upper: 0.949

contentFingerprint: sha256:abc123...
```

#### EXPLORE Output

Location: `src/test/resources/punit/explorations/{UseCaseId}/{configName}.yaml`

EXPLORE produces one file per configuration tested, enabling comparison:

```yaml
schemaVersion: punit-exploration-1
useCaseId: ShoppingBasketUseCase
configurationId: model-gpt-4o_temp-0.3
generatedAt: 2026-01-15T09:15:00Z

configuration:
  model: gpt-4o
  temperature: 0.3

results:
  samples: 20
  successes: 19
  successRate: 0.95

covariates:
  day_of_week: WEEKDAY
```

#### OPTIMIZE Output

Location: `src/test/resources/punit/optimizations/{UseCaseId}/{experimentId}_{timestamp}.yaml`

OPTIMIZE produces a history of iterations showing the optimization trajectory:

```yaml
schemaVersion: punit-optimization-1
useCaseId: ShoppingBasketUseCase
experimentId: temperature-optimization-v1
generatedAt: 2026-01-15T11:45:00Z

controlFactor: temperature
objective: MAXIMIZE
terminationReason: NO_IMPROVEMENT_WINDOW

iterations:
  - iteration: 1
    controlValue: 1.0
    samples: 20
    successes: 15
    score: 0.75
  - iteration: 2
    controlValue: 0.7
    samples: 20
    successes: 17
    score: 0.85
  - iteration: 3
    controlValue: 0.4
    samples: 20
    successes: 19
    score: 0.95
  # ... more iterations

bestIteration:
  iteration: 5
  controlValue: 0.3
  score: 0.97
```

### C: Glossary

For a complete list of terms and definitions used in PUnit, see the [PUnit Glossary](GLOSSARY.md).

---

## Next Steps

- Explore the examples in the `punitexamples` project
- See [OPERATIONAL-FLOW.md](OPERATIONAL-FLOW.md) for the end-to-end workflow in detail
- See [STATISTICAL-COMPANION.md](STATISTICAL-COMPANION.md) for mathematical foundations
- See [GLOSSARY.md](GLOSSARY.md) for complete term definitions
