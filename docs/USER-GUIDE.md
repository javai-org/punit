# PUnit User Guide

*Probabilistic testing for non-deterministic systems*

---

## Table of Contents

- [Introduction](#introduction)
  - [What is PUnit?](#what-is-punit)
  - [Two Testing Scenarios: Compliance and Regression](#two-testing-scenarios-compliance-and-regression)
  - [Quick Start](#quick-start)
  - [Running the Examples](#running-the-examples)
- [Part 1: The Shopping Basket Domain](#part-1-the-shopping-basket-domain)
  - [The Use Case as a Contract](#the-use-case-as-a-contract)
  - [Domain Overview](#domain-overview)
  - [The ShoppingBasketUseCase Implementation](#the-shoppingbasketusecase-implementation)
- [Part 2: Compliance Testing](#part-2-compliance-testing)
  - [When to Use Compliance Testing](#when-to-use-compliance-testing)
  - [Testing Against SLAs, SLOs, and Policies](#testing-against-slas-slos-and-policies)
  - [Threshold Provenance](#threshold-provenance)
  - [Sample Sizing for High Thresholds](#sample-sizing-for-high-thresholds)
- [Part 3: The Experimentation Workflow](#part-3-the-experimentation-workflow)
  - [Running Experiments](#running-experiments)
  - [EXPLORE: Compare Configurations](#explore-compare-configurations)
  - [OPTIMIZE: Tune a Single Factor](#optimize-tune-a-single-factor)
  - [MEASURE: Establish Baseline](#measure-establish-baseline)
- [Part 4: Probabilistic Testing](#part-4-probabilistic-testing)
  - [The Parameter Triangle](#the-parameter-triangle)
  - [Threshold Approaches (No Baseline Required)](#threshold-approaches-no-baseline-required)
  - [The UseCaseProvider Pattern](#the-usecaseprovider-pattern)
  - [Regression Testing with Specs](#regression-testing-with-specs)
  - [Covariate-Aware Baseline Selection](#covariate-aware-baseline-selection)
  - [Understanding Test Results](#understanding-test-results)
- [Part 5: Resource Management](#part-5-resource-management)
  - [Budget Control](#budget-control)
  - [Pacing Constraints](#pacing-constraints)
  - [Exception Handling](#exception-handling)
- [Part 6: The Statistical Core](#part-6-the-statistical-core)
  - [Bernoulli Trials](#bernoulli-trials)
  - [Transparent Statistics Mode](#transparent-statistics-mode)
  - [Further Reading](#further-reading)
- [Appendices](#appendices)
  - [A: Configuration Reference](#a-configuration-reference)
  - [B: Spec File Format](#b-spec-file-format)
  - [C: Glossary](#c-glossary)

---

## Introduction

### What is PUnit?

PUnit is a JUnit 5 extension framework for **probabilistic testing** of non-deterministic systems. It addresses a fundamental challenge: how do you write reliable tests for systems that don't produce the same output every time?

Traditional unit tests expect deterministic behavior—call a function, assert the result. But many modern systems are inherently non-deterministic:

- **LLM integrations** — Model outputs vary with temperature, prompt phrasing, and even API load
- **ML model inference** — Predictions may have confidence thresholds that occasionally miss
- **Distributed systems** — Network conditions, timing, and race conditions introduce variability
- **Randomized algorithms** — By design, outputs differ across executions

PUnit runs tests multiple times and determines pass/fail based on **statistical thresholds** rather than binary success/failure. Instead of asking "Did it work?" PUnit asks "Does it work reliably enough?"

### Two Testing Scenarios: Compliance and Regression

PUnit supports two distinct testing scenarios. These are not alternative approaches—they address different situations:

| Scenario | Question | Threshold Source |
|----------|----------|------------------|
| **Compliance Testing** | Does the service meet a mandated standard? | SLA, SLO, or policy (prescribed) |
| **Regression Testing** | Has performance dropped below baseline? | Empirical measurement (discovered) |

**Compliance Testing**: You have an external mandate—a contractual SLA, an internal SLO, or a quality policy—that defines the required success rate. You verify the system meets it.

```
"The payment gateway SLA requires 99.99% success rate"
    → Run samples, verify compliance with the mandate
```

**Regression Testing**: No external mandate exists. You measure the system's actual behavior to establish a baseline, then detect when performance degrades from that baseline.

```
"What success rate should we expect from our LLM integration?"
    → Run experiments to measure baseline
    → Run tests to detect regression from baseline
```

### Quick Start

**Dependency setup:**

```kotlin
// build.gradle.kts
dependencies {
    testImplementation("org.javai:punit:0.1.0")
}
```

**Simplest possible test (compliance):**

```java
@ProbabilisticTest(
    samples = 100,
    minPassRate = 0.95,
    thresholdOrigin = ThresholdOrigin.SLA,
    contractRef = "API SLA §3.1"
)
void apiMeetsSla() {
    assertThat(apiClient.call().isSuccess()).isTrue();
}
```

### Running the Examples

This guide uses examples from `org.javai.punit.examples`. All examples are `@Disabled` by default to prevent accidental execution in CI.

**Running experiments:**

First, comment out the @Disabled in the experiment class. Then...

```bash
./gradlew exp -Prun=ShoppingBasketMeasure
./gradlew exp -Prun=ShoppingBasketExplore.compareModels
./gradlew exp -Prun=ShoppingBasketOptimizeTemperature
```

**Running tests:**

First, comment out the @Disabled in the test class. Then...

```bash
./gradlew test --tests "ShoppingBasketTest"
./gradlew test --tests "PaymentGatewaySlaTest"
```

---

## Part 1: The Shopping Basket Domain

This guide uses a running example: the **ShoppingBasketUseCase**. Understanding this domain will help you apply PUnit to your own systems.

### The Use Case as a Contract

A use case in PUnit represents a **behavioral contract**—a formal specification of:

- **What** the system does (the operation)
- **What success means** (postconditions/criteria)
- **What factors affect behavior** (inputs, configuration)

This follows the Design by Contract principle. Use cases define postconditions (success criteria) that must be satisfied for an invocation to be considered successful. PUnit then measures how reliably these postconditions are met.

```java
public class ShoppingBasketUseCase {

    public UseCaseOutcome translateInstruction(String instruction) {
        // ... execute use case logic ...

        UseCaseCriteria criteria = UseCaseCriteria.ordered()
            .criterion("Valid JSON", () -> result.getBoolean("isValidJson", false))
            .criterion("Has operations array", () -> result.getBoolean("hasOperationsArray", false))
            .criterion("Actions are valid", () -> result.getBoolean("allActionsValid", false))
            .build();

        return new UseCaseOutcome(result, criteria);
    }
}
```

The criteria define what "success" means. Each invocation either satisfies all criteria (success) or fails one (failure). PUnit counts successes across many invocations to determine reliability.

Note a key difference between an experiment and a test: An experiment *observes* how a use case's result compares to the contract, while a test *checks* that the result meets the contract (and signals a fail if it does not). 

### Domain Overview

The ShoppingBasketUseCase translates natural language shopping instructions into structured JSON operations:

**Input:**
```
"Add 2 apples and remove the bread"
```

**Expected Output:**
```json
{
  "operations": [
    {"action": "add", "item": "apples", "quantity": 2},
    {"action": "remove", "item": "bread", "quantity": 1}
  ]
}
```

This task is inherently non-deterministic because it relies on an LLM. The model might:

- Return invalid JSON
- Omit required fields
- Invent invalid actions like "purchase" instead of "add"
- Use zero or negative quantities

The success criteria hierarchy catches these failures in order:

1. Valid JSON (parseable)
2. Has "operations" array
3. Each operation has required fields (action, item, quantity)
4. Actions are valid ("add", "remove", "clear")
5. Quantities are positive integers

### The ShoppingBasketUseCase Implementation

The full implementation demonstrates key PUnit concepts:

```java
@UseCase(
    description = "Translate natural language shopping instructions to JSON basket operations",
    covariates = {StandardCovariate.WEEKDAY_VERSUS_WEEKEND, StandardCovariate.TIME_OF_DAY},
    categorizedCovariates = {
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

- **`@UseCase`** — Declares covariates that may affect behavior. Covariates are discussed later in this guide
- **`@FactorGetter` / `@FactorSetter`** — Allow experiments to manipulate configuration
- **`@CovariateSource`** — Links factors to covariate tracking
- **`UseCaseOutcome`** — Bundles result data with success criteria

*Source: `org.javai.punit.examples.usecases.ShoppingBasketUseCase`*

---

## Part 2: Compliance Testing

When you have a mandated threshold, you're verifying **compliance**—not detecting regression.

### When to Use Compliance Testing

Use compliance testing when:

- A business stipulation defines the required success rate
- An SLA with a customer specifies reliability targets
- An internal SLO sets performance expectations
- A quality policy mandates minimum thresholds

You don't need to run experiments to discover the threshold—it's given to you.

### Testing Against SLAs, SLOs, and Policies

The `PaymentGatewaySlaTest` demonstrates compliance testing:

```java
@TestTemplate
@ProbabilisticTest(
    useCase = PaymentGatewayUseCase.class,
    samples = 10000,
    minPassRate = 0.9999,
    thresholdOrigin = ThresholdOrigin.SLA,
    contractRef = "Payment Provider SLA v2.3, Section 4.1"
)
@FactorSource(value = "standardAmounts", factors = {"cardToken", "amountCents"})
void testSlaCompliance(
    PaymentGatewayUseCase useCase,
    @Factor("cardToken") String cardToken,
    @Factor("amountCents") Long amountCents
) {
    useCase.chargeCard(cardToken, amountCents).assertAll();
}
```

Key elements:

- **`minPassRate = 0.9999`** — The mandated 99.99% threshold
- **`thresholdOrigin = ThresholdOrigin.SLA`** — Documents where the threshold came from
- **`contractRef`** — Reference to the specific contract clause

### Threshold Provenance

The `thresholdOrigin` attribute documents where the threshold came from:

| Origin      | Use When                                               |
|-------------|--------------------------------------------------------|
| `SLA`       | External Service Level Agreement with customer         |
| `SLO`       | Internal Service Level Objective                       |
| `POLICY`    | Compliance or organizational policy                    |
| `EMPIRICAL` | Derived from baseline measurement (regression testing) |

This information appears in the verdict output, providing an audit trail:

```
═══════════════════════════════════════════════════════════════
PUnit PASSED: testSlaCompliance
  Observed: 99.97% (9997/10000) >= Threshold: 99.99%
  Threshold origin: SLA
  Contract ref: Payment Provider SLA v2.3, Section 4.1
═══════════════════════════════════════════════════════════════
```

### Sample Sizing for High Thresholds

When testing high thresholds (99.9%+), sample size matters significantly:

| Samples | Can Detect Deviation Of |
|---------|-------------------------|
| 1,000   | ~1%                     |
| 10,000  | ~0.1%                   |
| 100,000 | ~0.03%                  |

To detect that a system is at 99.97% when the SLA requires 99.99%, you need enough samples to distinguish a 0.02% difference. With 1,000 samples, this gap is statistically invisible.

*Source: `org.javai.punit.examples.tests.PaymentGatewaySlaTest`*

---

## Part 3: The Experimentation Workflow

When no external mandate defines your threshold, you need to **discover** what "normal" looks like through experimentation. This enables regression testing later.

The experimentation workflow:

```
EXPLORE → OPTIMIZE → MEASURE → TEST
   |          |          |        |
Compare    Tune one   Establish  Regression
configs    factor     baseline   testing
```

### Running Experiments

All experiments use the unified `exp` Gradle task:

```bash
# Run all experiments in a class
./gradlew exp -Prun=ShoppingBasketMeasure

# Run a specific experiment method
./gradlew exp -Prun=ShoppingBasketExplore.compareModels

# Traditional --tests syntax also works
./gradlew exp --tests "ShoppingBasketExplore"
```

Experiments are `@Disabled` by default to prevent accidental execution during normal test runs. The `exp` task deactivates this condition.

### EXPLORE: Compare Configurations

**When to use EXPLORE:**

- You have multiple configurations to compare (models, temperatures, prompts)
- You want rapid feedback before committing to expensive measurements
- You're discovering what works, not yet measuring reliability

**Example: Comparing Models**

```java
@TestTemplate
@ExploreExperiment(
    useCase = ShoppingBasketUseCase.class,
    samplesPerConfig = 20,
    experimentId = "model-comparison-v1"
)
@FactorSource(value = "modelConfigurations", factors = {"model"})
void compareModels(
    ShoppingBasketUseCase useCase,
    @Factor("model") String model,
    ResultCaptor captor
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

**Example: Multi-Factor Exploration**

```java
@TestTemplate
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
    ResultCaptor captor
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

**Output:**

EXPLORE produces one spec file per configuration:

```
src/test/resources/punit/explorations/ShoppingBasketUseCase/
├── model-gpt-4o_temp-0.0.yaml
├── model-gpt-4o_temp-0.5.yaml
├── model-gpt-4o_temp-1.0.yaml
└── ...
```

Compare with standard diff tools to identify the preferred configuration.

*Source: `org.javai.punit.examples.experiments.ShoppingBasketExplore`*

### OPTIMIZE: Tune a Single Factor

**When to use OPTIMIZE:**

- After EXPLORE has identified a promising configuration
- You want to automatically tune a single factor (temperature, prompt)
- Manual iteration would be too slow or expensive

OPTIMIZE iteratively refines a **control factor** through mutation and evaluation.

**Example: Optimizing Temperature**

```java
@TestTemplate
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
    ResultCaptor captor
) {
    captor.record(useCase.translateInstruction("Add 2 apples and remove the bread"));
}

static Double naiveStartingTemperature() {
    return 1.0;  // Start high, optimize down
}
```

**Example: Optimizing Prompts**

```java
@TestTemplate
@OptimizeExperiment(
    useCase = ShoppingBasketUseCase.class,
    controlFactor = "systemPrompt",
    initialControlFactorSource = "weakStartingPrompt",
    scorer = ShoppingBasketSuccessRateScorer.class,
    mutator = ShoppingBasketPromptMutator.class,
    objective = OptimizationObjective.MAXIMIZE,
    samplesPerIteration = 5,
    maxIterations = 10,
    noImprovementWindow = 3,
    experimentId = "prompt-optimization-v1"
)
void optimizeSystemPrompt(
    ShoppingBasketUseCase useCase,
    @ControlFactor("systemPrompt") String systemPrompt,
    ResultCaptor captor
) {
    captor.record(useCase.translateInstruction("Add 2 apples and remove the bread"));
}

static String weakStartingPrompt() {
    return "You are a shopping assistant. Convert requests to JSON.";
}
```

**Scorers and Mutators:**

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

### MEASURE: Establish Baseline

**When to use MEASURE:**

- After EXPLORE/OPTIMIZE has identified the winning configuration
- You need a statistically reliable baseline for regression testing
- You're preparing to commit a spec to version control

A MEASURE experiment typically runs many samples (1000+ recommended) to establish precise statistics.
**Example:**

```java
@TestTemplate
@MeasureExperiment(
    useCase = ShoppingBasketUseCase.class,
    samples = 1000,
    experimentId = "baseline-v1"
)
@FactorSource(value = "multipleBasketInstructions", factors = {"instruction"})
void measureBaseline(
    ShoppingBasketUseCase useCase,
    @Factor("instruction") String instruction,
    ResultCaptor captor
) {
    captor.record(useCase.translateInstruction(instruction));
}
```

**Factor Cycling:**

When a factor source returns multiple values, MEASURE cycles through them:

```
Sample 1    → "Add 2 apples"
Sample 2    → "Remove the milk"
...
Sample 10   → "Clear the basket"
Sample 11   → "Add 2 apples"  (cycles back)
Sample 12   → "Remove the milk"
...
Sample 1000 → (100th cycle completes)
```

With 1000 samples and 10 instructions, each instruction is tested exactly 100 times.

**Output:**

```
src/test/resources/punit/specs/ShoppingBasketUseCase.yaml
```

**Committing Baselines:**

The developer is encouraged to commit baselines to the repository. By default, they are placed in the **test** folder (of the standard gradle folder layout). This is because a probabilistic regression test uses the baseline as input. The test cannot be performed without it, and if it is not present in the CI environment, the test will alert operators to this by failing. 

```bash
git add src/test/resources/punit/specs/
git commit -m "Add baseline for ShoppingBasket (93.5% @ N=1000)"
```

*Source: `org.javai.punit.examples.experiments.ShoppingBasketMeasure`*


---

## Part 4: Probabilistic Testing

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

This isn't a PUnit limitation—it's fundamental to statistical inference. PUnit makes these trade-offs explicit and computable.

### Threshold Approaches (No Baseline Required)

Three operational modes for `@ProbabilisticTest` that work without baselines. Each approach fixes two parameters and lets PUnit compute the third.

**You cannot specify all three parameters.** Attempting to fix sample size, threshold, *and* confidence simultaneously is statistically nonsensical—the parameter triangle means these values are interdependent. If you try, you'll either over-constrain the problem (no valid solution exists) or create redundant specifications that may contradict each other.

**1. Sample-Size-First**

*"I have budget for 100 samples. What threshold can I verify with 95% confidence?"*

- **You specify**: Sample size + Confidence level
- **PUnit computes**: The achievable threshold (the strictest pass rate you can reliably verify with these constraints)

```java
@ProbabilisticTest(
    useCase = ShoppingBasketUseCase.class,
    samples = 100,
    thresholdConfidence = 0.95  // confidence level for deriving threshold (not the threshold itself)
)
void sampleSizeFirst(ShoppingBasketUseCase useCase, @Factor("instruction") String instruction) {
    useCase.translateInstruction(instruction).assertAll();
}
```

PUnit computes and reports the threshold (equivalent to `minPassRate`) based on the observed success rate and the specified confidence.

**2. Confidence-First**

*"I need to detect a 5% degradation with 95% confidence and 80% power."*

- **You specify**: Confidence level + Power + Minimum detectable effect
- **PUnit computes**: The required sample size (how many samples are needed to achieve this detection capability)

The `minDetectableEffect` parameter is essential here. Without it, the question "how many samples do I need?" has no finite answer—detecting arbitrarily small degradations requires arbitrarily many samples. By specifying the smallest drop worth detecting, you bound the problem and get a concrete sample size.

```java
@ProbabilisticTest(
    useCase = ShoppingBasketUseCase.class,
    confidence = 0.95,           // probability of correct verdict (1 - false positive rate)
    minDetectableEffect = 0.05,  // smallest drop from baseline worth detecting (5%)
    power = 0.80                 // probability of catching a real degradation (1 - false negative rate)
)
void confidenceFirst(ShoppingBasketUseCase useCase, @Factor("instruction") String instruction) {
    useCase.translateInstruction(instruction).assertAll();
}
```

**3. Threshold-First**

*"I know the pass rate must be ≥90%. Run 100 samples to verify."*

- **You specify**: Sample size + Threshold
- **PUnit computes**: The implied confidence level (how certain you can be about the verdict given these constraints)

```java
@ProbabilisticTest(
    useCase = ShoppingBasketUseCase.class,
    samples = 100,
    minPassRate = 0.90  // THE THRESHOLD: the pass rate the system must meet
)
void thresholdFirst(ShoppingBasketUseCase useCase, @Factor("instruction") String instruction) {
    useCase.translateInstruction(instruction).assertAll();
}
```

*Source: `org.javai.punit.examples.tests.ShoppingBasketThresholdApproachesTest`*

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

    @TestTemplate
    @ProbabilisticTest(useCase = ShoppingBasketUseCase.class, samples = 100)
    void testInstructionTranslation(ShoppingBasketUseCase useCase, ...) {
        // useCase is automatically injected
    }
}
```

### Regression Testing with Specs

Once you understand the parameter triangle, you can let **specs** provide **`minPassRate`**. The spec contains the empirical success rate from a prior MEASURE experiment; PUnit derives `minPassRate` from this baseline. This enables **regression testing**—detecting when performance drops below an empirically established baseline.

**A spec is more than a number.** It's a complete record of:

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
  weekday_vs_weekend: WEEKDAY
extendedStatistics:
  confidenceInterval: { lower: 0.919, upper: 0.949 }
```

**When to use regression testing:**

- No external mandate defines the threshold—you need to discover what "normal" is
- You want to detect degradation from established performance
- The system evolves and you need to catch regressions early

```java
@TestTemplate
@ProbabilisticTest(
    useCase = ShoppingBasketUseCase.class,
    samples = 100
)
@FactorSource(value = "multipleBasketInstructions", factors = {"instruction"})
void testInstructionTranslation(
    ShoppingBasketUseCase useCase,
    @Factor("instruction") String instruction
) {
    useCase.translateInstruction(instruction).assertAll();
}
```

PUnit loads the matching spec and derives the threshold from the baseline's empirical success rate plus a statistical margin.

*Source: `org.javai.punit.examples.tests.ShoppingBasketTest`*

### Baseline Expiration ###

System usage and environmental changes mean that baseline data can become dated. Do guard against this, PUnit allows you to specify an expiration date for the generated baseline. 

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
  Observed: 94.0% (94/100) >= Threshold: 91.9%
  Baseline: 93.5% @ N=1000 (spec: ShoppingBasketUseCase.yaml)
  Confidence: 95%

  ⚠️  BASELINE EXPIRED
  The baseline was generated on 2025-11-15 and expired on 2025-12-15.
  System behavior may have drifted. Consider re-running MEASURE to
  establish a fresh baseline.
═══════════════════════════════════════════════════════════════
```

### Covariate-Aware Baseline Selection

Covariates are environmental factors that may affect system behavior.

**What are covariates?**

- **Temporal**: Time of day, weekday vs weekend, season
- **Infrastructure**: Region, instance type, API version
- **Configuration**: Model, temperature, prompt variant

**Why they matter:** An LLM's behavior may differ between weekdays and weekends (different load patterns), or between models. Testing against the wrong baseline produces misleading results.

**Intelligent baseline selection:** When a probabilistic test runs, PUnit examines the current execution context (covariates) and selects the most appropriate baseline. If one or more covariates don't match, PUnit qualifies the verdict with a warning:

```
═══════════════════════════════════════════════════════════════
PUnit PASSED: testInstructionTranslation
  Observed: 91.0% (91/100) >= Threshold: 91.9%
  Baseline: 93.5% @ N=1000 (spec: ShoppingBasketUseCase.yaml)
  Confidence: 95%

  ⚠️  COVARIATE NON-CONFORMANCE
  The test is running under different conditions than the baseline:
    • weekday_vs_weekend: baseline=WEEKDAY, current=WEEKEND
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
    covariates = {StandardCovariate.WEEKDAY_VERSUS_WEEKEND},
    categorizedCovariates = {
        @Covariate(key = "llm_model", category = CovariateCategory.CONFIGURATION)
    }
)
public class ShoppingBasketUseCase { }
```

*Source: `org.javai.punit.examples.tests.ShoppingBasketCovariateTest`*

### Understanding Test Results

**Why PUnit fails tests in JUnit (even when PUnit's verdict is PASSED)**

A key design decision: PUnit marks all probabilistic tests as **failed** in JUnit terms, regardless of the statistical verdict. This is intentional.

Why? Because probabilistic test results must not be ignored. Unlike deterministic tests where PASS means "nothing to see here," a probabilistic PASS still carries information that operators should consider. By failing in JUnit:

- CI pipelines pause for human review
- The statistical report gets attention
- Operators make informed decisions rather than blindly proceeding

**Reading the statistical report**

Every probabilistic test produces a verdict with statistical context:

```
═══════════════════════════════════════════════════════════════
PUnit PASSED: testInstructionTranslation
  Observed: 94.0% (94/100) >= Threshold: 91.9%
  Baseline: 93.5% @ N=1000 (spec: ShoppingBasketUseCase.yaml)
  Confidence: 95%
═══════════════════════════════════════════════════════════════
```

**Responding to results**

The statistical verdict informs how to respond:

| Verdict    | What it means                             | Recommended response                  |
|------------|-------------------------------------------|---------------------------------------|
| **PASSED** | Observed rate is consistent with baseline | Low priority; likely no action needed |
| **FAILED** | Observed rate is below expected threshold | Investigate; possible regression      |

- **PASSED**: The system is behaving as expected based on historical data. Operators should probably not invest significant time investigating.
- **FAILED**: The system may have regressed. A deeper investigation is worth considering—check recent changes, environmental factors, or upstream dependencies.

The report provides the evidence; operators provide the judgment.

---

## Part 5: Resource Management

### Budget Control

Traditional tests run once per execution. By contrast, experiments and probabilistic tests require multiple executions. This has the potential to rack up costs in terms of time and resources. PUnit addresses this first-class concern by providing safeguards against excessive resource consumption.

Budgets can be specified at different levels:

| Level      | Scope              | How to Set                               |
|------------|--------------------|------------------------------------------|
| **Method** | Single test method | `@ProbabilisticTest(timeBudgetMs = ...)` |
| **Class**  | All tests in class | `@CostBudget` on class                   |
| **Suite**  | All tests in run   | *Planned for future release*             |

When budgets are set at multiple levels, PUnit enforces all of them—the first exhausted budget triggers termination.

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

| Behavior | Description |
|----------|-------------|
| `FAIL` | Immediately fail the test when budget is exhausted. This is the **default** and most conservative option—you asked for N samples but couldn't afford them. |
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

*Source: `org.javai.punit.examples.tests.ShoppingBasketBudgetTest`*

### Pacing Constraints

Hitting APIs with tens, hundreds or thousands of calls must be done in a controlled manner. Many 3rd-party APIs limit calls per minute/hour.

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

*Source: `org.javai.punit.examples.tests.ShoppingBasketPacingTest`*

### Exception Handling

Configure how exceptions during sample execution are handled:

```java
@ProbabilisticTest(
    samples = 100,
    exceptionHandling = ExceptionHandling.FAIL_SAMPLE
)
void exceptionsCountAsFailures() {
    // Exceptions count as sample failures, don't propagate
}
```

| Strategy      | Behavior                                            |
|---------------|-----------------------------------------------------|
| `FAIL_SAMPLE` | Exception counts as failed sample; continue testing |
| `PROPAGATE`   | Exception immediately fails the test                |
| `IGNORE`      | Exception is ignored; sample not counted            |

*Source: `org.javai.punit.examples.tests.ShoppingBasketExceptionTest`*

---

## Part 6: The Statistical Core

A brief look at the statistical engine that powers PUnit.

### Bernoulli Trials

At its heart, PUnit models each sample as a **Bernoulli trial**—an experiment with exactly two outcomes: success or failure., with failure being defined as conformance to the use case's contract. When you run a probabilistic test:

1. Each sample execution is a Bernoulli trial with unknown success probability *p*
2. The baseline spec provides an estimate of *p* from prior measurement
3. PUnit uses the binomial distribution to determine whether the observed success count is consistent with the baseline

This statistical machinery runs automatically when a regression test executes. Users don't interact with it directly—they simply write tests, and PUnit applies rigorous statistical inference under the hood.

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

For the mathematical foundations—confidence interval calculations, power analysis, threshold derivation formulas—see [STATISTICAL-COMPANION.md](STATISTICAL-COMPANION.md).

---

## Appendices

### A: Configuration Reference

PUnit configuration follows this resolution order: System property → Environment variable → Annotation value → Framework default.

| Property                       | Environment Variable            | Description                   |
|--------------------------------|---------------------------------|-------------------------------|
| `punit.samples`                | `PUNIT_SAMPLES`                 | Override sample count         |
| `punit.stats.transparent`      | `PUNIT_STATS_TRANSPARENT`       | Enable transparent statistics |
| `punit.specs.outputDir`        | `PUNIT_SPECS_OUTPUT_DIR`        | Spec output directory         |
| `punit.explorations.outputDir` | `PUNIT_EXPLORATIONS_OUTPUT_DIR` | Exploration output directory  |

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
  weekday_vs_weekend: WEEKDAY

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
  weekday_vs_weekend: WEEKDAY
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

| Term                    | Definition                                                                                                 |
|-------------------------|------------------------------------------------------------------------------------------------------------|
| **Baseline**            | Empirically measured success rate used as reference for regression testing                                 |
| **Compliance testing**  | Verifying a system meets a mandated threshold (SLA, SLO, policy)                                           |
| **confidence**          | Probability of a correct verdict; equals 1 minus the false positive rate. Part of the parameter triangle.  |
| **Covariate**           | Environmental factor that may affect system behavior                                                       |
| **Factor**              | Input or configuration that varies across test executions                                                  |
| **minDetectableEffect** | Smallest drop from baseline worth detecting; required for Confidence-First approach to compute sample size |
| **minPassRate**         | The threshold pass rate the system must meet to pass the test. Part of the parameter triangle.             |
| **power**               | Probability of catching a real degradation; equals 1 minus the false negative rate                         |
| **Regression testing**  | Detecting when performance drops below an established baseline                                             |
| **Sample**              | A single execution of the system under test                                                                |
| **samples**             | Number of test executions; controls cost and time. Part of the parameter triangle.                         |
| **Spec**                | YAML file containing baseline measurements and metadata                                                    |
| **thresholdConfidence** | Confidence level used for deriving `minPassRate` from observed results in Sample-Size-First approach       |
| **Use case**            | A behavioral contract defining an operation and its success criteria                                       |

---

## Next Steps

- Explore the examples in `src/test/java/org/javai/punit/examples/`
- See [STATISTICAL-COMPANION.md](STATISTICAL-COMPANION.md) for mathematical foundations
- See [GLOSSARY.md](GLOSSARY.md) for complete term definitions
