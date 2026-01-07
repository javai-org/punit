# PUNIT Quick Introduction

A step-by-step guide for getting started with probabilistic testing using PUNIT.

---

## Overview

PUNIT follows a disciplined workflow:

```
Use Case → Experiment → Empirical Baseline → Probabilistic Test
```

1. **Use Case**: Define *what* behavior you want to observe
2. **Experiment**: Run the use case repeatedly to gather empirical data  
3. **Empirical Baseline**: Machine-generated summary of observed behavior
4. **Probabilistic Test**: CI-gated test using the empirically derived pass rate

---

## Step 1: Create a Use Case

A **Use Case** is a reusable method that invokes production code and captures observations. It returns a neutral `UseCaseResult` containing key-value pairs.

### Example: JSON Validation Use Case

```java
package com.example.usecases;

import org.javai.punit.experiment.api.UseCase;
import org.javai.punit.experiment.api.UseCaseContext;
import org.javai.punit.experiment.model.UseCaseResult;

public class JsonGenerationUseCases {

    // Inject your production service
    private final LlmClient llmClient;

    public JsonGenerationUseCases(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @UseCase("usecase.json.generation")
    public UseCaseResult generateJson(String prompt, UseCaseContext context) {
        // 1. Get configuration from context (optional)
        String model = context.getParameter("model", String.class, "gpt-4");
        double temperature = context.getParameter("temperature", Double.class, 0.7);

        // 2. Invoke production code
        LlmResponse response = llmClient.complete(prompt, model, temperature);

        // 3. Capture observations as key-value pairs
        boolean isValidJson = JsonValidator.isValid(response.getContent());
        
        return UseCaseResult.builder()
            .value("isValidJson", isValidJson)
            .value("content", response.getContent())
            .value("tokensUsed", response.getTokensUsed())
            .meta("model", model)
            .meta("requestId", response.getRequestId())
            .build();
    }
}
```

### Key Points

- **Annotate with `@UseCase("id")`**: Use a dot-separated namespace (e.g., `usecase.json.generation`)
- **Accept `UseCaseContext`**: Provides backend-specific parameters
- **Return `UseCaseResult`**: Neutral observations, no assertions
- **Never called by production code**: Use cases exist only in test/experiment space

---

## Step 2: Create an Experiment

An **Experiment** executes a use case repeatedly to gather empirical data. Experiments are exploratory—they never produce pass/fail verdicts.

### Example: JSON Generation Experiment

```java
package com.example.experiments;

import org.javai.punit.experiment.api.Experiment;
import org.javai.punit.experiment.api.ExperimentContext;
import org.junit.jupiter.api.Disabled;

@Disabled("Run manually to generate baselines")
public class JsonGenerationExperiment {

    @Experiment(
        useCase = "usecase.json.generation",
        samples = 100,                    // Run 100 times
        tokenBudget = 50000,              // Stop if tokens exceed 50k
        timeBudgetMs = 120000             // Stop after 2 minutes
    )
    @ExperimentContext(
        backend = "llm",
        parameters = {
            "model = gpt-4",
            "temperature = 0.7"
        }
    )
    void measureJsonGenerationPerformance() {
        // Method body is optional—execution is driven by the use case
    }
}
```

### Run the Experiment

```bash
./gradlew test --tests "JsonGenerationExperiment" -Pexperiment=true
```

### Generated Baseline

After running, PUNIT generates a baseline file at:

```
src/test/resources/punit/baselines/usecase.json.generation.yaml
```

Example baseline content:

```yaml
# Empirical Baseline for usecase.json.generation
# Generated automatically by punit experiment runner

useCaseId: usecase.json.generation
generatedAt: 2026-01-04T15:30:00Z
experimentClass: com.example.experiments.JsonGenerationExperiment
experimentMethod: measureJsonGenerationPerformance

context:
  backend: llm
  model: gpt-4
  temperature: 0.7

execution:
  samplesPlanned: 100
  samplesExecuted: 100
  terminationReason: ALL_SAMPLES_COMPLETED

statistics:
  observedSuccessRate: 0.935    # 93.5% of samples produced valid JSON
  standardError: 0.025
  confidenceIntervalLower: 0.886
  confidenceIntervalUpper: 0.984
  successes: 93
  failures: 7
  failureDistribution:
    malformed_json: 4
    empty_response: 2
    timeout: 1

cost:
  totalTimeMs: 45000
  avgTimePerSampleMs: 450
  totalTokens: 42500
  avgTokensPerSample: 425
```

### Key Points

- **`samples`**: How many times to run the use case
- **`tokenBudget` / `timeBudgetMs`**: Resource limits
- **`@ExperimentContext`**: Specifies backend and configuration parameters
- **Baselines are descriptive**: They record what happened, not what should happen

---

## Step 3: Create a Probabilistic Test

A **Probabilistic Test** uses the empirically derived pass rate to gate CI. The test passes if the observed success rate meets or exceeds the minimum threshold.

### Option A: Inline Threshold (Simple)

Use the baseline's `observedSuccessRate` (93.5%) as a guide, with a small margin for variance:

```java
package com.example.tests;

import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.TokenChargeRecorder;
import static org.assertj.core.api.Assertions.assertThat;

class JsonGenerationTest {

    private final LlmClient llmClient = new LlmClient();

    @ProbabilisticTest(
        samples = 50,              // Run 50 times
        minPassRate = 0.90         // Based on baseline: 93.5% - margin
    )
    void shouldGenerateValidJson(TokenChargeRecorder tokenRecorder) {
        // 1. Call your production code
        LlmResponse response = llmClient.complete(
            "Generate a JSON object with name and age",
            "gpt-4",
            0.7
        );

        // 2. Record tokens (for budget tracking)
        tokenRecorder.recordTokens(response.getTokensUsed());

        // 3. Assert (failures count toward pass rate)
        assertThat(JsonValidator.isValid(response.getContent()))
            .as("Expected valid JSON but got: %s", response.getContent())
            .isTrue();
    }
}
```

### Option B: Spec-Driven Test (Recommended)

Create a specification from the baseline, then reference it in your test:

**1. Create Specification** (`src/test/resources/punit/specs/usecase.json.generation/v1.yaml`):

```yaml
# Execution Specification: usecase.json.generation v1
useCaseId: usecase.json.generation
version: v1

# Approved threshold (derived from baseline observation of 93.5%)
minPassRate: 0.90

# Execution parameters
samples: 50
timeBudgetMs: 60000
tokenBudget: 30000

# Context (what configuration to use)
context:
  backend: llm
  model: gpt-4
  temperature: 0.7

# Approval metadata
approval:
  approvedBy: jane.smith@example.com
  approvedAt: 2026-01-05T10:00:00Z
  rationale: |
    Baseline from Jan 4 experiment shows 93.5% success rate.
    Setting threshold at 90% to allow for variance.

# Traceability
sourceBaselines:
  - usecase.json.generation/2026-01-04T15:30:00Z.yaml
```

**2. Reference Specification in Test**:

```java
@ProbabilisticTest(spec = "usecase.json.generation:v1")
void shouldGenerateValidJson(TokenChargeRecorder tokenRecorder) {
    LlmResponse response = llmClient.complete(
        "Generate a JSON object with name and age",
        "gpt-4",
        0.7
    );
    tokenRecorder.recordTokens(response.getTokensUsed());
    
    assertThat(JsonValidator.isValid(response.getContent())).isTrue();
}
```

### Run Tests

```bash
./gradlew test
```

### Understanding Results

```
✅ shouldGenerateValidJson()
    ✅ Sample 1/50
    ✅ Sample 2/50
    ...
    ✅ Sample 45/50  ← SUCCESS_GUARANTEED, remaining samples skipped
    
Probabilistic test passed: 100.00% >= 90.00% (45/45 samples succeeded)
```

Or if it fails:

```
❌ shouldGenerateValidJson()
    ✅ Sample 1/50
    ❌ Sample 2/50  ← Assertion failed
    ...
    ❌ Sample 10/50  ← IMPOSSIBILITY: cannot reach 90% pass rate
    
Probabilistic test failed: observed pass rate 72.00% < required 90.00%
```

---

## Summary

| Step | Artifact | Purpose |
|------|----------|---------|
| 1. Use Case | `@UseCase` method | Define what to observe |
| 2. Experiment | `@Experiment` + `@ExperimentContext` | Gather empirical data |
| 3. Baseline | YAML file (auto-generated) | Record observations |
| 4. Test | `@ProbabilisticTest` | Gate CI with derived threshold |

---

## Quick Reference

### Choosing Pass Rates

| Baseline Observation | Recommended `minPassRate` |
|---------------------|---------------------------|
| 98%+ | 0.95 |
| 93-97% | 0.90 |
| 88-92% | 0.85 |
| 83-87% | 0.80 |

Always leave margin below the observed rate to account for variance.

### Budget Control

```java
@ProbabilisticTest(
    samples = 100,
    minPassRate = 0.90,
    timeBudgetMs = 30000,     // Stop after 30 seconds
    tokenBudget = 50000,      // Stop after 50k tokens
    onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL
)
```

### Class-Level Shared Budget

```java
@ProbabilisticTestBudget(tokenBudget = 100000, timeBudgetMs = 60000)
class LlmIntegrationTests {
    
    @ProbabilisticTest(samples = 30, minPassRate = 0.90)
    void test1(TokenChargeRecorder recorder) { /* ... */ }
    
    @ProbabilisticTest(samples = 30, minPassRate = 0.85)
    void test2(TokenChargeRecorder recorder) { /* ... */ }
}
```

---

## Next Steps

- See [README.md](../README.md) for full configuration reference
- See [PLAN-EXP.md](../PLAN-EXP.md) for detailed experiment design documentation
- Explore examples in `src/test/java/org/javai/punit/examples/`

