# PUnit User Guide

*Probabilistic testing for systems characterized by uncertainty*

All attribution licensing is ARL.

---

## Table of Contents

- [Introduction](#introduction)
  - [What is PUnit?](#what-is-punit)
  - [The PUnit Philosophy](#the-punit-philosophy)
  - [Core Concepts in PUnit](#core-concepts-in-punit)
  - [Two Testing Scenarios: Compliance and Conformance](#two-testing-scenarios-compliance-and-conformance)
  - [Quick Start](#quick-start)
  - [Running the Examples](#running-the-examples)
- [Part 1: The Shopping Basket Domain](#part-1-the-shopping-basket-domain)
  - [Domain Overview](#domain-overview)
  - [Why Service Contracts?](#why-service-contracts)
  - [The Service Contract](#the-service-contract)
  - [The UseCaseOutcome](#the-usecaseoutcome)
  - [Instance Conformance](#instance-conformance)
  - [Duration Constraints](#duration-constraints)
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
  - [Understanding Test Results](#understanding-test-results)
  - [The UseCaseProvider Pattern](#the-usecaseprovider-pattern)
  - [Input Sources](#input-sources)
  - [Conformance Testing with Specs](#conformance-testing-with-specs)
  - [Baseline Expiration](#baseline-expiration)
  - [Covariate-Aware Baseline Selection](#covariate-aware-baseline-selection)
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
  - [B: Experiment Output Formats](#b-experiment-output-formats)
  - [C: Glossary](#c-glossary)

---

## Introduction

### What is PUnit?

PUnit is a JUnit 5 extension framework for **probabilistic testing** of systems characterized by uncertainty. It addresses a fundamental challenge: how do you write reliable tests for systems that don't produce the same output every time?

Traditional unit tests expect deterministic behavior—call a function, assert the result. But many modern systems are characterized by inherent uncertainty:

- **LLM integrations** — Model outputs vary with temperature, prompt phrasing, and even API load
- **ML model inference** — Predictions may have confidence thresholds that occasionally miss
- **Distributed systems** — Network conditions, timing, and race conditions introduce variability
- **Randomized algorithms** — By design, outputs differ across executions

PUnit runs tests multiple times and determines pass/fail based on **statistical thresholds** rather than binary success/failure. Instead of asking "Did it work?" PUnit asks "Does it work reliably enough?"

### The PUnit Philosophy

PUnit recognizes that **experiments and tests must refer to the same objects**.

In traditional testing, we articulate correctness through a series of test assertions. This works for deterministic systems where we expect 100% success. However, for systems where uncertainty is a factor (like LLM integrations), a test assertion that aborts on failure is of zero use when we want to collect data about the service's behavior. We need to know *how often* it fails, not just that it *did* fail.

PUnit therefore encourages the creation of an artifact called a **Use Case**. A Use Case defines, among other things, a **Service Contract**.

The Service Contract is the shared expression of correctness in PUnit-land:
- **Experiments** use it as a source of correctness data (to measure behavior).
- **Probabilistic Tests** use it as a correctness enforcer (to verify performance against a threshold).

By using the same Use Case and Service Contract for both measurement and testing, you ensure that what you are testing is exactly what you have measured.

### Core Concepts in PUnit

PUnit has a few first-class artifacts. Understanding them up front makes the rest of the guide much easier to follow.

**Sample**
A single execution of the system under test. A probabilistic test runs many samples of the same test method and counts how many succeed.

**ServiceContract**
A formal definition of what "success" means for a use case. It contains postconditions (and optional timing constraints) that determine whether a sample should count as a success.

**UseCaseOutcome**
The object that combines the input, result, and contract evaluation for a single sample. Experiments and tests both use `UseCaseOutcome` so they measure and test the same criteria.

**Spec**
A baseline YAML file produced by a MEASURE experiment. It records observed success rate, sample count, covariates, and statistical metadata. Conformance tests use specs to derive thresholds.

**Covariate and Factor**
A covariate is an environmental condition that can affect behavior (model, time of day, region). A factor is an input or configuration you vary during experiments and tests.

### Two Testing Scenarios: Compliance and Conformance

PUnit supports two distinct testing scenarios. These are not alternative approaches—they address different situations:

| Scenario                | Question                                   | Threshold Source                   |
|-------------------------|--------------------------------------------|------------------------------------|
| **Compliance Testing**  | Does the service meet a mandated standard? | SLA, SLO, or policy (prescribed)   |
| **Conformance Testing** | Has performance dropped below baseline?    | Empirical measurement (discovered) |

**Compliance Testing**: You have an external mandate—a contractual SLA, an internal SLO, or a quality policy—that defines the required success rate. You verify the system meets it.

```
"The payment gateway SLA requires 99.99% success rate"
    → Run samples, verify compliance with the mandate
```

**Conformance Testing**: No external mandate exists. You measure the system's actual behavior to establish a baseline, then detect when performance regresses from that baseline.

```
"What success rate should we expect from our LLM integration?"
    → Run experiments to measure baseline
    → Run tests to detect regression from baseline
```

### Quick Start

Before diving into the code, remember the PUnit philosophy: we define a **Use Case** and its **Service Contract** once, then use it for both experiments and tests.

**Dependency setup:**

```kotlin
// build.gradle.kts
dependencies {
    testImplementation("org.javai:punit:0.1.0")
}
```

**1) Define a contract for your use case:**

```java
private static final ServiceContract<Request, Response> CONTRACT =
        ServiceContract.<Request, Response>define()
                .ensure("Response has content", response ->
                        response.content() != null && !response.content().isBlank()
                                ? Outcome.ok()
                                : Outcome.fail("check", "content was null or blank"))
                .build();
```

**2) Return a UseCaseOutcome from your use case method:**

```java
public UseCaseOutcome<Response> callService(Request request) {
    return UseCaseOutcome
            .withContract(CONTRACT)
            .input(request)
            .execute(req -> client.call(req))
            .build();
}
```

**3) Register the use case and write a probabilistic test (compliance):**

```java
@RegisterExtension
UseCaseProvider provider = new UseCaseProvider();

@BeforeEach
void setUp() {
    provider.register(MyUseCase.class, MyUseCase::new);
}

@ProbabilisticTest(
    useCase = MyUseCase.class,
    samples = 100,
    minPassRate = 0.95,
    thresholdOrigin = ThresholdOrigin.SLA,
    contractRef = "API SLA §3.1"
)
void apiMeetsSla(MyUseCase useCase) {
    useCase.callService(new Request(...)).assertAll();
}
```

This test runs 100 samples. Each sample calls the use case, evaluates the contract, and counts toward the pass rate. The test passes when the observed rate meets the required threshold.

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

### Domain Overview

The ShoppingBasketUseCase translates natural language shopping instructions into structured JSON actions:

**Input:**
```
"Add 2 apples and remove the bread"
```

**Expected Output:**
```json
{
  "actions": [
    {
      "context": "SHOP",
      "name": "add",
      "parameters": [
        {"name": "item", "value": "apples"},
        {"name": "quantity", "value": "2"}
      ]
    },
    {
      "context": "SHOP",
      "name": "remove",
      "parameters": [
        {"name": "item", "value": "bread"}
      ]
    }
  ]
}
```

This task is inherently non-deterministic because it relies on an LLM. The model might:

- Return invalid JSON
- Omit required fields
- Invent invalid actions like "purchase" instead of "add"
- Use the wrong context or misspell action names

The success criteria hierarchy catches these failures in order:

1. Valid JSON (parseable)
2. Has "actions" array with at least one action
3. Each action has context, name, and parameters
4. Actions are valid for the given context ("add", "remove", "clear" for SHOP)

### Why Service Contracts?

In traditional Java development, **Test-Driven Development (TDD)** places validation logic inside tests. You write a test that asserts expected behavior, and the test passes or fails. This works well for deterministic systems.

But with non-deterministic systems—LLMs, ML models, distributed services—this approach breaks down. Consider what happens when you test an LLM integration the traditional way:

```java
@Test
void shouldReturnValidJson() {
    String result = llmClient.translate("Add 2 apples");
    assertThat(result).matches(JSON_PATTERN);  // Sometimes fails!
}
```

This test will fail occasionally. And what do we learn from this failure? **Nothing useful.** It merely confirms what we already know: we're dealing with non-determinism. The test teaches us nothing about the *rate* of failure or whether that rate is acceptable.

The fundamental question isn't "Does it work?" but rather **"How often does it work, and is that often enough?"**

To answer this question, we need validation logic that is **non-judgmental**—logic that observes and records outcomes without immediately passing or failing. This is where the `ServiceContract` comes in.

**The key insight**: We must separate the *specification of correctness* from the *judgment of acceptability*.

- The **ServiceContract** defines what "correct" means—the postconditions that should hold
- **Experiments** use this contract to *measure* how often it's satisfied
- **Tests** use the same contract to *verify* the measured rate meets a threshold

Using the same validation logic for both measurement and testing isn't just convenient—it's **essential**. To measure one thing and then test another would be nonsensical. If your experiment measures "valid JSON with correct fields" but your test checks "non-empty response," you've learned nothing about whether your system meets its actual requirements.

This is why PUnit encourages declaring a `ServiceContract` for non-deterministic use cases from the outset. One might call it **Contract-Driven Development**.

For deterministic services, this approach adds an artifact without much value—traditional tests work fine. But when dealing with non-deterministic behavior that must be measured over time, the service contract is **non-negotiable**. It's the only way to get from "the test failed" to "the system fails 3% of the time, which is within our 5% tolerance."

### The Service Contract

At the heart of every PUnit use case is a **ServiceContract**—a formal specification of what the service must do. The contract defines postconditions that must be satisfied for an invocation to be considered successful.

Here's the contract from `ShoppingBasketUseCase`, defined at the top of the class:

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

Postconditions (`ensure` and `derive`) are evaluated in order. If any fails, subsequent ones are skipped. This creates a **fail-fast hierarchy**:

1. "Response has content" — Is there a response at all?
2. "Valid shopping action" — Can it be parsed into domain objects?
3. "Contains valid actions" — Are the parsed actions semantically valid?

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
- Create **diffable documents** comparing configurations (EXPLORE)
- Provide the **basis for optimization runs** where the mutator learns from failures (OPTIMIZE)
- Establish **baseline specifications** that later power probabilistic tests (MEASURE)

**In probabilistic tests**, the outcome is used in the simplest way possible: to assert that the contract's postconditions were met, and to fail the sample if they were not. PUnit then counts successes across many samples to determine whether the observed rate meets the required threshold.

### Instance Conformance

Beyond postconditions, use cases can validate against **expected values**—specific instances the result should match. This enables instance conformance testing:

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

Note a key difference between an experiment and a test: An experiment *observes* how a use case's result compares to the contract, while a test *checks* that the result meets the contract (and signals a fail if it does not).

### Duration Constraints

Contracts can include timing requirements via `ensureDurationBelow`. Unlike postconditions, duration constraints are evaluated **independently**—you always learn both "was it correct?" and "was it fast enough?" regardless of which (if either) fails.

```java
private static final ServiceContract<ServiceInput, ChatResponse> CONTRACT =
        ServiceContract.<ServiceInput, ChatResponse>define()
                .ensure("Response has content", response -> ...)
                .derive("Valid JSON", JsonParser::parse)
                    .ensure("Has required fields", json -> ...)
                .ensureDurationBelow(Duration.ofMillis(500))
                .build();
```

This independence is deliberate. A slow-but-correct response and a fast-but-wrong response fail for different reasons—diagnostics should show both dimensions:

```
Sample 47: FAIL
  ✓ Response has content
  ✓ Valid JSON
  ✓ Has required fields
  ✗ Duration: 847ms exceeded limit of 500ms
```

Duration constraints are useful when response time is part of the service contract (SLAs, user experience requirements) and you want timing tracked alongside correctness through experiments and tests.

### The ShoppingBasketUseCase Implementation

The full implementation demonstrates key PUnit concepts:

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

- **`@UseCase`** — Declares covariates that may affect behavior. Covariates are discussed later in this guide
- **`@FactorGetter` / `@FactorSetter`** — Allow experiments to manipulate configuration
- **`@CovariateSource`** — Links factors to covariate tracking
- **`UseCaseOutcome`** — Bundles result data with success criteria

*Source: `org.javai.punit.examples.usecases.ShoppingBasketUseCase`*

---

## Part 2: Compliance vs. Smoke Testing

When you have a mandated threshold, you're verifying **compliance**—not detecting regression. However, it is critical to distinguish between a full compliance audit and a lightweight smoke test.

### When to Use Compliance Testing

Use compliance testing when:

- A business stipulation defines the required success rate
- An SLA with a customer specifies reliability targets
- An internal SLO sets performance expectations
- A quality policy mandates minimum thresholds

### Compliance, Smoke, and Sample Size

For services that are inherently highly reliable (e.g., payment gateways with 99.99% targets), a true compliance test requires a **very large sample size** to be statistically valid.

PUnit enforces this through the `intent` attribute of `@ProbabilisticTest`:

1.  **`TestIntent.VERIFICATION`** (Default): PUnit treats the test as an evidential claim. If the `thresholdOrigin` is normative (SLA, SLO, or POLICY) and the sample size is too small to support the threshold, PUnit will **reject the test out of hand** with a configuration error.
2.  **`TestIntent.SMOKE`**: PUnit treats the test as a lightweight early-warning check. It accepts undersized configurations but labels the result as a SMOKE verdict, indicating it is not a full verification.

### Testing Against SLAs, SLOs, and Policies

The `PaymentGatewaySlaTest` demonstrates these concepts:

```java
@ProbabilisticTest(
    useCase = PaymentGatewayUseCase.class,
    samples = 100,              // Small sample size
    minPassRate = 0.9999,
    thresholdOrigin = ThresholdOrigin.SLA,
    intent = TestIntent.SMOKE,  // Must be SMOKE because 100 samples cannot verify 99.99%
    contractRef = "Payment Provider SLA v2.3, Section 4.1"
)
void testSlaSmokeCheck(...) { ... }
```

### Threshold Provenance (Normative Origins)

The `thresholdOrigin` attribute documents where the threshold came from. Origins marked as **normative** (SLA, SLO, POLICY) trigger PUnit's strict sample-size enforcement for VERIFICATION tests.

```
═══════════════════════════════════════════════════════════════
PUnit PASSED: testSlaCompliance
  Observed: 99.99% (9999/10000) >= min pass rate: 99.99%
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
Compare    Tune one   Establish  Conformance
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

### EXPLORE: Compare Configurations

**When to use EXPLORE:**

- You have multiple configurations to compare (models, temperatures, prompts)
- You want rapid feedback before committing to expensive measurements
- You're discovering what works, not yet measuring reliability

**Example: Comparing Models**

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

**Example: Multi-Factor Exploration**

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

*Source: `org.javai.punit.examples.experiments.ShoppingBasketExplore`*

### OPTIMIZE: Tune a Single Factor

**When to use OPTIMIZE:**

- After EXPLORE has identified a promising configuration
- You want to automatically tune a single factor (temperature, prompt)
- Manual iteration would be too slow or expensive

OPTIMIZE iteratively refines a **control factor** through mutation and evaluation.

**Example: Optimizing Temperature**

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

**Example: Optimizing Prompts**

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

**Note:** When using `@InputSource`, each optimization iteration tests the control factor against ALL inputs in the source. Don't specify `samplesPerIteration` when using `@InputSource`—the effective samples per iteration equals the number of inputs.

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

The JSON file contains an array of input objects:
```json
[
  {"instruction": "Add 2 apples", "expected": "{\"context\":\"SHOP\",\"name\":\"add\",...}"},
  {"instruction": "Clear the basket", "expected": "{\"context\":\"SHOP\",\"name\":\"clear\",...}"}
]
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

### Understanding Test Results

**How PUnit surfaces probabilistic results in JUnit**

A probabilistic test has two layers of feedback:

- **Sample-level failures**: individual sample failures are surfaced so you can inspect real examples of where the use case failed.
- **Statistical verdict**: the overall PASS/FAIL is computed from the observed pass rate and the configured threshold.

In other words: sample failures are expected and informative; the statistical verdict is the actual decision. PUnit's report is the authoritative view of the probabilistic outcome.

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

The statistical verdict informs how to respond:

| Verdict    | What it means                             | Recommended response                  |
|------------|-------------------------------------------|---------------------------------------|
| **PASSED** | Observed rate is consistent with baseline | Low priority; likely no action needed |
| **FAILED** | Observed rate is below expected threshold | Investigate; possible regression      |

- **PASSED**: The system is behaving as expected based on historical data. Operators should probably not invest significant time investigating.
- **FAILED**: The system may have regressed. A deeper investigation is worth considering—check recent changes, environmental factors, or upstream dependencies.

The report provides the evidence; operators provide the judgment.

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

### Conformance Testing with Specs

Once you understand the parameter triangle, you can let **specs** provide **`minPassRate`**. The spec contains the empirical success rate from a prior MEASURE experiment; PUnit derives `minPassRate` from this baseline. This enables **conformance testing**—detecting when performance regresses below an empirically established baseline.

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
  day_of_week: WEEKDAY
extendedStatistics:
  confidenceInterval: { lower: 0.919, upper: 0.949 }
```

**When to use conformance testing:**

- No external mandate defines the threshold—you need to discover what "normal" is
- You want to detect regression from established performance
- The system evolves and you need to catch regressions early

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

PUnit loads the matching spec and derives the threshold from the baseline's empirical success rate plus a statistical margin.

*Source: `org.javai.punit.examples.tests.ShoppingBasketTest`*

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

*Source: `org.javai.punit.examples.tests.ShoppingBasketCovariateTest`*

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

| Behavior           | Description                                                                                                                                                                                                 |
|--------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `FAIL`             | Immediately fail the test when budget is exhausted. This is the **default** and most conservative option—you asked for N samples but couldn't afford them.                                                  |
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

*Source: `org.javai.punit.examples.tests.ShoppingBasketPacingTest`*

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

*Source: `org.javai.punit.examples.tests.ShoppingBasketExceptionTest`*

---

## Part 6: The Statistical Core

A brief look at the statistical engine that powers PUnit.

### Bernoulli Trials

At its heart, PUnit models each sample as a **Bernoulli trial**—an experiment with exactly two outcomes: success or failure, with failure being defined as non-conformance to the use case's contract. When you run a probabilistic test:

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

- Explore the examples in `src/test/java/org/javai/punit/examples/`
- See [STATISTICAL-COMPANION.md](STATISTICAL-COMPANION.md) for mathematical foundations
- See [GLOSSARY.md](GLOSSARY.md) for complete term definitions
