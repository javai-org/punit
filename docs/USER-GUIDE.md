# PUnit User Guide

*Experimentation and statistical regression testing for non-deterministic systems*

---

## Table of Contents

- [Introduction](#introduction)
- [Quick Start](#quick-start)
- [Part 1: Statistical Foundations](#part-1-statistical-foundations)
  - [The Parameter Triangle](#the-parameter-triangle)
  - [Sample-Size-First vs Confidence-First](#sample-size-first-vs-confidence-first)
- [Part 2: SLA-Driven Testing](#part-2-sla-driven-testing)
  - [When to Use SLA-Driven Testing](#when-to-use-sla-driven-testing)
  - [Basic SLA-Driven Test](#basic-sla-driven-test)
  - [Threshold Provenance](#threshold-provenance)
  - [Budget Control](#budget-control)
  - [Pacing Constraints](#pacing-constraints)
- [Part 3: Spec-Driven Testing](#part-3-spec-driven-testing)
  - [When to Use Spec-Driven Testing](#when-to-use-spec-driven-testing)
  - [The Workflow](#the-workflow)
  - [Step 1: Create a Use Case](#step-1-create-a-use-case)
  - [Step 2: Run a MEASURE Experiment](#step-2-run-a-measure-experiment)
  - [Step 3: Commit the Spec](#step-3-commit-the-spec)
  - [Step 4: Create a Probabilistic Test](#step-4-create-a-probabilistic-test)
  - [Factor Sources](#factor-sources)
  - [Factor Consistency](#factor-consistency)
- [Part 4: EXPLORE Mode](#part-4-explore-mode)
  - [When to Use EXPLORE](#when-to-use-explore)
  - [Running Explorations](#running-explorations)
  - [Comparing Configurations](#comparing-configurations)
- [Transparent Statistics Mode](#transparent-statistics-mode)
- [Decision Flowchart](#decision-flowchart)
- [Next Steps](#next-steps)

---

## Introduction

PUnit is an **experimentation and testing platform** for non-deterministic systems. It provides two complementary approaches:

| Approach         | Threshold Source            | Use When                                    |
|------------------|-----------------------------|---------------------------------------------|
| **SLA-Driven**   | External contract or policy | You have a defined target (SLA, SLO, etc.)  |
| **Spec-Driven**  | Empirical baseline          | You need to discover acceptable performance |

Both approaches use the same `@ProbabilisticTest` annotation—the difference is where the `minPassRate` comes from.

This guide is organized in four parts:

1. **Statistical Foundations** — The concepts that underpin PUnit's approach
2. **SLA-Driven Testing** — Test against contractual thresholds (simpler, fewer moving parts)
3. **Spec-Driven Testing** — Derive thresholds from empirical experiments (more advanced)
4. **EXPLORE Mode** — Compare configurations to find optimal settings

---

## Quick Start

**Choose your path based on your scenario:**

```
┌─────────────────────────────────────────────────────────────────────────┐
│ Do you have a contractual threshold (SLA, SLO, policy)?                 │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   YES ────────────────────────────► Part 2: SLA-Driven Testing          │
│   "I have 99.5% uptime SLA"         (skip experiments, test directly)   │
│                                                                         │
│   NO ─────────────────────────────► Part 3: Spec-Driven Testing         │
│   "I need to discover what's        (run experiments, derive threshold) │
│    acceptable for this LLM"                                             │
│                                                                         │
│   MAYBE ──────────────────────────► Part 4: EXPLORE Mode                │
│   "I want to compare different      (compare configurations first)      │
│    models/prompts/configs"                                              │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

**Quick dependency setup:**

```kotlin
// build.gradle.kts
dependencies {
    testImplementation("org.javai:punit:0.1.0")
}
```

**Fastest possible test (SLA-driven):**

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

---

## Part 1: Statistical Foundations

Before diving into the two testing approaches, you need to understand **how PUnit's parameters interact**. This knowledge applies whether you're testing against an SLA or an empirical baseline.

### The Parameter Triangle

PUnit operates with three interdependent parameters. At any given time, you can control **two of the three**; the third is determined by statistics:

```
                    ┌─────────────────┐
                    │   Sample Size   │
                    │   (Cost/Time)   │
                    └────────┬────────┘
                             │
              Fix any two ───┼─── Third is computed
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│  Confidence   │    │   Threshold   │    │   Detection   │
│   (How sure)  │    │ (How strict)  │    │   Capability  │
└───────────────┘    └───────────────┘    └───────────────┘
```

| If You Fix...       | And You Fix...   | Then Statistics Determines... |
|---------------------|------------------|-------------------------------|
| Sample size (cost)  | Threshold        | Confidence level              |
| Sample size (cost)  | Confidence       | How strict threshold can be   |
| Confidence          | Threshold        | Required sample size          |

**This is not a limitation of PUnit**—it's a fundamental property of statistical inference. PUnit simply makes these trade-offs explicit and computable.

### Sample-Size-First vs Confidence-First

Based on the parameter triangle, there are two operational approaches:

#### Option A: Sample-Size-First (Cost-Driven)

*"We can afford 100 samples. Given a 99.5% threshold, what confidence does that achieve?"*

```java
@ProbabilisticTest(
    minPassRate = 0.995,    // SLA requirement
    samples = 100           // What we can afford
)
void slaTest() { ... }
```

**What PUnit does:**
- Runs 100 samples
- Counts successes
- Computes implied confidence and power
- Reports whether observed rate meets threshold

**Best for:** Continuous monitoring, CI pipelines with time constraints, API rate limits.

#### Option B: Confidence-First (Risk-Driven)

*"We need 95% confidence that we'd detect a drop from 99.5% to 99.0%. How many samples?"*

```java
@ProbabilisticTest(
    minPassRate = 0.995,         // SLA requirement
    confidence = 0.95,           // How sure we need to be
    power = 0.80,                // Detection probability
    minDetectableEffect = 0.005  // Smallest drop we care about
)
void assuranceTest() { ... }
```

**What PUnit does:**
- Computes required sample size (may be large)
- Runs that many samples
- Provides the specified statistical guarantees

**Best for:** Safety-critical systems, pre-release assurance, compliance audits.

#### Why `minDetectableEffect` Matters

Without `minDetectableEffect`, the question "How many samples to verify p ≥ 99.5% with 95% confidence?" has **no finite answer**. Statistics must know:

- Detect a drop to 99%? → Moderate N
- Detect a drop to 99.4%? → Large N
- Detect a drop to 99.4999%? → Infinite N

The `minDetectableEffect` defines the smallest violation worth detecting. Only with this can PUnit compute a finite, honest sample size.

---

## Part 2: SLA-Driven Testing

### When to Use SLA-Driven Testing

Use SLA-driven testing when:

- ✅ You have a contractual threshold (SLA, SLO, compliance requirement)
- ✅ The threshold is defined by business/legal requirements, not technical measurement
- ✅ You want to get testing running quickly with minimal setup
- ✅ The system under test is a "black box" you can't experiment with freely

**Examples:**
- Third-party API with 99.9% uptime SLA
- Internal service with 95% success rate SLO
- Compliance requirement: "Must succeed 90% of the time"

### Basic SLA-Driven Test

```java
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.ThresholdOrigin;
import static org.assertj.core.api.Assertions.assertThat;

class ApiSlaTest {

    private final ApiClient apiClient = new ApiClient();

    @ProbabilisticTest(
        samples = 100,
        minPassRate = 0.995,
        thresholdOrigin = ThresholdOrigin.SLA,
        contractRef = "Vendor API Agreement §2.3"
    )
    void vendorApiMeetsSla() {
        Response response = apiClient.call();
        assertThat(response.isSuccess()).isTrue();
    }
}
```

Run with:

```bash
./gradlew test
```

### Threshold Provenance

The `thresholdOrigin` and `contractRef` attributes document **where the threshold came from**. This information appears in the verdict:

```
═══════════════════════════════════════════════════════════════
PUnit PASSED: vendorApiMeetsSla
  Observed pass rate: 99.6% (996/1000) >= min pass rate: 99.5%
  Threshold origin: SLA
  Contract ref: Vendor API Agreement §2.3
  Elapsed: 45234ms
═══════════════════════════════════════════════════════════════
```

#### Available Target Sources

| Source        | Use When                                        |
|---------------|-------------------------------------------------|
| `UNSPECIFIED` | Default; threshold origin not documented        |
| `SLA`         | Threshold from external Service Level Agreement |
| `SLO`         | Threshold from internal Service Level Objective |
| `POLICY`      | Threshold from compliance or organizational policy |
| `EMPIRICAL`   | Threshold derived from baseline measurement     |

```java
// SLA from customer contract
@ProbabilisticTest(
    samples = 100, minPassRate = 0.999,
    thresholdOrigin = ThresholdOrigin.SLA,
    contractRef = "Acme Corp Contract #12345 §4.1"
)

// Internal SLO
@ProbabilisticTest(
    samples = 100, minPassRate = 0.95,
    thresholdOrigin = ThresholdOrigin.SLO,
    contractRef = "Platform Team SLO Dashboard"
)

// Compliance policy
@ProbabilisticTest(
    samples = 100, minPassRate = 0.99,
    thresholdOrigin = ThresholdOrigin.POLICY,
    contractRef = "SOC 2 Type II Control CC7.1"
)
```

### Budget Control

Control resource consumption with time and token budgets:

```java
@ProbabilisticTest(
    samples = 500,
    minPassRate = 0.95,
    timeBudgetMs = 60000,    // Stop after 60 seconds
    tokenBudget = 100000     // Stop after 100k tokens (LLM tests)
)
void expensiveTest(TokenChargeRecorder recorder) {
    LlmResponse response = llmClient.complete("Generate JSON");
    recorder.recordTokens(response.getUsage().getTotalTokens());
    assertThat(response.getContent()).satisfies(JsonValidator::isValid);
}
```

#### Budget Exhaustion Behavior

```java
@ProbabilisticTest(
    samples = 1000,
    minPassRate = 0.90,
    tokenBudget = 50000,
    onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL
)
void evaluateWhatWeHave(TokenChargeRecorder recorder) {
    // If budget runs out after 200 samples with 185 successes:
    // 185/200 = 92.5% >= 90% → PASS (instead of automatic FAIL)
}
```

### Pacing Constraints

When testing rate-limited APIs, use `@Pacing` to stay under limits:

```java
@ProbabilisticTest(
    samples = 200,
    minPassRate = 0.95,
    thresholdOrigin = ThresholdOrigin.SLA,
    contractRef = "OpenAI API Terms"
)
@Pacing(maxRequestsPerMinute = 60)
void llmApiTest() {
    // PUnit automatically spaces samples ~1 second apart
    String response = openAiClient.complete("Generate JSON");
    assertThat(response).satisfies(JsonValidator::isValid);
}
```

PUnit prints an execution plan before starting:

```
╔══════════════════════════════════════════════════════════════════╗
║ PUnit Test: llmApiTest                                           ║
╠══════════════════════════════════════════════════════════════════╣
║ Samples requested:     200                                       ║
║ Pacing: 60 RPM → 1000ms between samples                          ║
║ Estimated duration: 3m 20s                                       ║
╚══════════════════════════════════════════════════════════════════╝
```

#### Pacing Parameters

| Parameter               | Description                   | Example                  |
|-------------------------|-------------------------------|--------------------------|
| `maxRequestsPerSecond`  | Max RPS                       | `2.0` → 500ms delay      |
| `maxRequestsPerMinute`  | Max RPM                       | `60` → 1000ms delay      |
| `maxRequestsPerHour`    | Max RPH                       | `3600` → 1000ms delay    |
| `minMsPerSample`        | Explicit minimum delay        | `500` → 500ms delay      |
| `maxConcurrentRequests` | Parallel execution limit      | `3` → up to 3 concurrent |

When multiple constraints are specified, the **most restrictive** wins.

---

## Part 3: Spec-Driven Testing

### When to Use Spec-Driven Testing

Use spec-driven testing when:

- ✅ You don't have an external threshold
- ✅ You need to discover what "acceptable" means for your system
- ✅ You want thresholds derived from empirical data, not guesswork
- ✅ You're working with a system you can experiment with (e.g., your own LLM integration)

**Examples:**
- LLM-based feature with no predefined success rate
- ML model where you need to establish a performance baseline
- New randomized algorithm you're developing

### The Workflow

```
┌─────────────────────────────────────────────────────────────────────┐
│                        EXPERIMENTATION                              │
│                                                                     │
│   Use Case  ──▶  MEASURE  ──▶  Spec                                │
│                  (1000+ samples)  (commit to Git)                   │
└─────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────┐
│                           TESTING                                   │
│                                                                     │
│   Spec  ──▶  Probabilistic Test  ──▶  CI Pass/Fail                 │
│              (threshold derived from spec)                          │
└─────────────────────────────────────────────────────────────────────┘
```

1. **Use Case**: Define what behavior to observe
2. **MEASURE**: Run large-scale experiment to establish baseline
3. **Spec**: Machine-generated empirical baseline (commit to Git)
4. **Test**: CI-gated test with threshold derived from spec

### Step 1: Create a Use Case

A **Use Case** is a reusable method that invokes production code and captures observations:

```java
package com.example.usecases;

import org.javai.punit.experiment.api.UseCase;
import org.javai.punit.experiment.api.UseCaseContext;
import org.javai.punit.experiment.model.UseCaseResult;

public class JsonGenerationUseCase {

    private final LlmClient llmClient;

    public JsonGenerationUseCase(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @UseCase("usecase.json.generation")
    public UseCaseResult generateJson(String prompt, UseCaseContext context) {
        // Get configuration from context
        String model = context.getParameter("model", String.class, "gpt-4");
        
        // Invoke production code
        LlmResponse response = llmClient.complete(prompt, model);

        // Capture observations (not assertions!)
        boolean isValidJson = JsonValidator.isValid(response.getContent());
        
        return UseCaseResult.builder()
            .value("isValidJson", isValidJson)
            .value("content", response.getContent())
            .value("tokensUsed", response.getTokensUsed())
            .meta("model", model)
            .build();
    }
}
```

**Key points:**
- Use cases return **observations**, not pass/fail verdicts
- The `@UseCase` ID is used to locate the generated spec
- Use cases are reused across experiments AND tests

### Step 2: Run a MEASURE Experiment

```java
package com.example.experiments;

import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.experiment.api.Experiment;
import org.javai.punit.experiment.api.ExperimentMode;
import org.javai.punit.experiment.api.ResultCaptor;

public class JsonGenerationExperiment {

    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    @BeforeEach
    void setUp() {
        provider.register(JsonGenerationUseCase.class, () ->
            new JsonGenerationUseCase(new OpenAIClient())
        );
    }

    @Experiment(
        mode = ExperimentMode.MEASURE,
        useCase = JsonGenerationUseCase.class,
        samples = 1000
    )
    void measureJsonGeneration(JsonGenerationUseCase useCase, ResultCaptor captor) {
        captor.record(useCase.generateJson("Generate a user profile JSON"));
    }
}
```

Run with:

```bash
./gradlew measure --tests "JsonGenerationExperiment"
```

### Step 3: Commit the Spec

After running, PUnit generates a spec at:

```
src/test/resources/punit/specs/usecase.json.generation.yaml
```

Example content:

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
  standardError: 0.0078
  confidenceInterval:
    lower: 0.919
    upper: 0.949

contentFingerprint: sha256:abc123...
```

**Review and commit:**

```bash
git add src/test/resources/punit/specs/
git commit -m "Add baseline for JSON generation (93.5% @ N=1000)"
```

### Step 4: Create a Probabilistic Test

```java
package com.example.tests;

import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.ThresholdOrigin;
import static org.assertj.core.api.Assertions.assertThat;

class JsonGenerationTest {

    private final LlmClient llmClient = new LlmClient();

    @ProbabilisticTest(
        useCase = JsonGenerationUseCase.class,  // Spec looked up by use case ID
        samples = 100,
        thresholdOrigin = ThresholdOrigin.EMPIRICAL,
        contractRef = "usecase.json.generation.yaml"
    )
    void shouldGenerateValidJson() {
        LlmResponse response = llmClient.complete("Generate a user profile JSON");
        assertThat(JsonValidator.isValid(response.getContent())).isTrue();
    }
}
```

The test:
1. Loads the spec for `usecase.json.generation`
2. Derives threshold from empirical basis (lower bound of 95% CI)
3. Runs 100 samples
4. Compares observed rate to derived threshold

### Factor Sources

Factor sources provide **structured, reusable inputs** to experiments and tests:

```java
public class JsonGenerationUseCase {

    // Factor source for consistent inputs across experiments and tests
    public static List<FactorArguments> standardPrompts() {
        return List.of(
            FactorArguments.of("prompt", "Generate a user profile JSON"),
            FactorArguments.of("prompt", "Generate an order JSON"),
            FactorArguments.of("prompt", "Generate a product JSON")
        );
    }

    @UseCase("usecase.json.generation")
    public UseCaseResult generateJson(String prompt, UseCaseContext context) {
        // ...
    }
}
```

Reference in experiments:

```java
@Experiment(mode = ExperimentMode.MEASURE, samples = 1000)
@FactorSource("JsonGenerationUseCase#standardPrompts")
void measure(@Factor("prompt") String prompt, ResultCaptor captor) {
    captor.record(useCase.generateJson(prompt));
}
```

**Consumption behavior:**
- `List<FactorArguments>` → **Cycling**: samples rotate through entries
- `Stream<FactorArguments>` → **Sequential**: each sample gets next element

### Factor Consistency

PUnit validates that experiments and tests use the **same factor source**. A hash is stored in the spec and compared at test time:

```
✓ Factor sources match.
  Note: Experiment used 1000 samples; test uses 100.
```

or

```
⚠️ FACTOR CONSISTENCY WARNING
   Factor source mismatch detected.
   Statistical conclusions may be less reliable.
```

---

## Part 4: EXPLORE Mode

### When to Use EXPLORE

Use EXPLORE mode when:

- ✅ You have multiple configurations to compare (models, temperatures, prompts)
- ✅ You want rapid feedback before committing to a statistical baseline
- ✅ You're discovering what works, not yet measuring reliability

**Examples:**
- Comparing GPT-4 vs GPT-3.5 for a task
- Testing different temperature settings
- Evaluating prompt variations

### Running Explorations

```java
@Experiment(
    mode = ExperimentMode.EXPLORE,
    useCase = JsonGenerationUseCase.class,
    samplesPerConfig = 3  // Small samples for rapid comparison
)
@FactorSource("explorationConfigs")
void exploreModels(
        @Factor("model") String model,
        @Factor("temperature") double temperature,
        ResultCaptor captor) {
    
    useCase.configure(model, temperature);
    captor.record(useCase.generateJson("Generate a user profile"));
}

static List<FactorArguments> explorationConfigs() {
    return FactorArguments.configurations()
        .names("model", "temperature")
        .values("gpt-4", 0.0)
        .values("gpt-4", 0.7)
        .values("gpt-3.5-turbo", 0.0)
        .values("gpt-3.5-turbo", 0.7)
        .stream().toList();
}
```

Run with:

```bash
./gradlew explore --tests "JsonGenerationExperiment.exploreModels"
```

### Comparing Configurations

EXPLORE outputs go to:

```
src/test/resources/punit/explorations/{UseCaseId}/
├── model-gpt-4_temp-0.0.yaml
├── model-gpt-4_temp-0.7.yaml
├── model-gpt-3.5-turbo_temp-0.0.yaml
└── model-gpt-3.5-turbo_temp-0.7.yaml
```

Compare with standard diff tools:

```bash
diff explorations/*/model-gpt-4*.yaml explorations/*/model-gpt-3.5*.yaml
```

**EXPLORE vs MEASURE:**

| Aspect            | EXPLORE                      | MEASURE                  |
|-------------------|------------------------------|--------------------------|
| Goal              | Compare configurations       | Establish baseline       |
| Samples           | Small (1-3 per config)       | Large (1000+)            |
| Output            | One file per configuration   | Single spec per use case |
| Statistical rigor | Low (rapid feedback)         | High (reliable baseline) |

---

## Transparent Statistics Mode

Enable detailed statistical explanations for auditing, debugging, or education:

```bash
./gradlew test -Dpunit.stats.transparent=true
```

Or per-test:

```java
@ProbabilisticTest(samples = 100, transparentStats = true)
void myTest() { ... }
```

Example output:

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
  Interpretation:      The observed success rate of 87% is consistent with 
                       the baseline expectation.

══════════════════════════════════════════════════════════════════════════════
```

---

## Decision Flowchart

Use this flowchart to choose the right approach:

```
                         ┌─────────────────────────────┐
                         │  Do you have a contractual  │
                         │  threshold (SLA/SLO/policy)?│
                         └──────────────┬──────────────┘
                                        │
                        ┌───────────────┴───────────────┐
                        │                               │
                       YES                              NO
                        │                               │
                        ▼                               ▼
          ┌──────────────────────┐        ┌─────────────────────────┐
          │  SLA-DRIVEN TESTING  │        │ Do you have multiple    │
          │                      │        │ configurations to try?  │
          │  @ProbabilisticTest( │        └────────────┬────────────┘
          │    minPassRate=...,  │                     │
          │    thresholdOrigin=SLA  │         ┌───────────┴───────────┐
          │  )                   │         │                       │
          └──────────────────────┘        YES                      NO
                                           │                       │
                                           ▼                       ▼
                              ┌────────────────────┐   ┌───────────────────────┐
                              │   EXPLORE MODE     │   │   MEASURE + TEST      │
                              │                    │   │                       │
                              │   Compare configs  │   │   1. Run MEASURE      │
                              │   then MEASURE     │   │   2. Commit spec      │
                              │   the winner       │   │   3. Test against it  │
                              └────────────────────┘   └───────────────────────┘
```

**Summary table:**

| Your Situation                            | Approach             | Key Parameters                      |
|-------------------------------------------|----------------------|-------------------------------------|
| Have SLA/SLO threshold                    | SLA-Driven           | `minPassRate`, `thresholdOrigin`       |
| Need to discover acceptable rate          | MEASURE → Spec-Test  | `useCase`, spec file                |
| Want to compare models/prompts/configs    | EXPLORE first        | `samplesPerConfig`, `@FactorSource` |
| Need detailed statistical output          | Transparent mode     | `transparentStats = true`           |

---

## Next Steps

- See [README.md](../README.md) for quick reference and configuration options
- See [OPERATIONAL-FLOW.md](OPERATIONAL-FLOW.md) for detailed workflow documentation
- See [STATISTICAL-COMPANION.md](STATISTICAL-COMPANION.md) for mathematical foundations
- See [GLOSSARY.md](GLOSSARY.md) for term definitions
- Explore examples in `src/test/java/org/javai/punit/examples/`
