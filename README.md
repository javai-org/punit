# PUNIT: Probabilistic Unit Testing Framework

[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.org/)
[![JUnit 5](https://img.shields.io/badge/JUnit-5.10%2B-green.svg)](https://junit.org/junit5/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

PUNIT is a JUnit 5 extension for testing non-deterministic systems. It runs tests multiple times and determines pass/fail based on statistical thresholds, making it ideal for testing LLMs, ML models, randomized algorithms, and other stochastic components.

## Features

- ✅ **Statistical Pass/Fail** - Test passes if observed success rate ≥ minimum threshold
- ✅ **Early Termination** - Stops early when success becomes mathematically impossible
- ✅ **Budget Control** - Time and token budgets at method, class, or suite level
- ✅ **Dynamic Token Tracking** - Record actual API consumption per invocation
- ✅ **Configuration Overrides** - System properties and environment variables
- ✅ **Thread-Safe** - Safe for parallel test execution
- ✅ **Rich Reporting** - Detailed statistics via JUnit TestReporter

## Quick Start

### 1. Add Dependency

```gradle
testImplementation("org.javai:punit:1.0.0")
```

### 2. Write Your First Probabilistic Test

```java
import org.javai.punit.api.ProbabilisticTest;
import static org.assertj.core.api.Assertions.assertThat;

class MyServiceTest {

    @ProbabilisticTest(samples = 100, minPassRate = 0.95)
    void serviceReturnsValidResponse() {
        Response response = myService.call();
        assertThat(response.isValid()).isTrue();
    }
}
```

This test runs 100 times and passes if at least 95% of invocations succeed.

## Usage Examples

### Basic Probabilistic Test

```java
@ProbabilisticTest(samples = 50, minPassRate = 0.90)
void llmRespondsWithValidJson() {
    String response = llmClient.complete("Generate a JSON object");
    assertThat(response).satisfies(JsonValidator::isValidJson);
}
```

### With Time Budget

```java
@ProbabilisticTest(samples = 100, minPassRate = 0.90, timeBudgetMs = 30000)
void completesWithinTimeLimit() {
    // Test will terminate if 30 seconds elapsed, even if samples remain
    Response response = slowService.call();
    assertThat(response).isNotNull();
}
```

### With Token Budget (Static Mode)

```java
@ProbabilisticTest(
    samples = 100, 
    minPassRate = 0.90,
    tokenCharge = 500,      // Each sample uses ~500 tokens
    tokenBudget = 10000     // Stop after 10,000 tokens consumed
)
void llmTestWithStaticTokenBudget() {
    String response = llmClient.complete("Summarize this text...");
    assertThat(response.length()).isGreaterThan(50);
}
```

### With Dynamic Token Tracking

```java
@ProbabilisticTest(samples = 50, minPassRate = 0.90, tokenBudget = 50000)
void llmTestWithDynamicTokenTracking(TokenChargeRecorder tokenRecorder) {
    LlmResponse response = llmClient.complete("Generate code for...");
    
    // Record actual tokens from API response
    tokenRecorder.recordTokens(response.getUsage().getTotalTokens());
    
    assertThat(response.getContent()).contains("function");
}
```

### Class-Level Shared Budget

```java
@ProbabilisticTestBudget(timeBudgetMs = 60000, tokenBudget = 100000)
class LlmIntegrationTests {

    @ProbabilisticTest(samples = 20, minPassRate = 0.90)
    void testJsonGeneration(TokenChargeRecorder recorder) {
        // Shares budget with other methods in this class
        LlmResponse response = llmClient.complete("Generate JSON...");
        recorder.recordTokens(response.getUsage().getTotalTokens());
        assertThat(response.getContent()).isValidJson();
    }

    @ProbabilisticTest(samples = 20, minPassRate = 0.85)
    void testCodeGeneration(TokenChargeRecorder recorder) {
        // Uses remaining budget after testJsonGeneration
        LlmResponse response = llmClient.complete("Generate code...");
        recorder.recordTokens(response.getUsage().getTotalTokens());
        assertThat(response.getContent()).contains("def ");
    }
}
```

### Budget Exhaustion Behavior

```java
@ProbabilisticTest(
    samples = 100,
    minPassRate = 0.80,
    tokenBudget = 5000,
    onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL
)
void evaluatePartialResults(TokenChargeRecorder recorder) {
    // If budget exhausted, evaluate pass rate from completed samples
    // instead of failing immediately
    LlmResponse response = llmClient.complete("...");
    recorder.recordTokens(response.getUsage().getTotalTokens());
    assertThat(response).isNotNull();
}
```

## Configuration

### Annotation Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `samples` | int | 100 | Number of test invocations |
| `minPassRate` | double | 0.95 | Minimum success rate (0.0 to 1.0) |
| `timeBudgetMs` | long | 0 | Max time in ms (0 = unlimited) |
| `tokenCharge` | int | 0 | Static tokens per sample |
| `tokenBudget` | long | 0 | Max tokens (0 = unlimited) |
| `onBudgetExhausted` | enum | FAIL | FAIL or EVALUATE_PARTIAL |
| `onException` | enum | FAIL_SAMPLE | FAIL_SAMPLE or ABORT_TEST |
| `maxExampleFailures` | int | 5 | Example failures to capture |

### System Property Overrides

Override annotation values at runtime:

```bash
# Override sample count
./gradlew test -Dpunit.samples=10

# Override minimum pass rate
./gradlew test -Dpunit.minPassRate=0.80

# Scale all sample counts by multiplier
./gradlew test -Dpunit.samplesMultiplier=0.1

# Suite-level budget (shared across all tests)
./gradlew test -Dpunit.suite.tokenBudget=500000
```

### Environment Variables

| Property | Environment Variable |
|----------|---------------------|
| `punit.samples` | `PUNIT_SAMPLES` |
| `punit.minPassRate` | `PUNIT_MIN_PASS_RATE` |
| `punit.samplesMultiplier` | `PUNIT_SAMPLES_MULTIPLIER` |
| `punit.timeBudgetMs` | `PUNIT_TIME_BUDGET_MS` |
| `punit.tokenCharge` | `PUNIT_TOKEN_CHARGE` |
| `punit.tokenBudget` | `PUNIT_TOKEN_BUDGET` |
| `punit.suite.timeBudgetMs` | `PUNIT_SUITE_TIME_BUDGET_MS` |
| `punit.suite.tokenBudget` | `PUNIT_SUITE_TOKEN_BUDGET` |

### Precedence (highest to lowest)

1. System property
2. Environment variable
3. Annotation value
4. Framework default

## How It Works

### Execution Flow

1. **Configuration Resolution** - Merge system props, env vars, and annotations
2. **Sample Generation** - Create N invocation contexts
3. **Sample Execution** - Run each sample, catch failures without throwing
4. **Early Termination Check** - Stop if success becomes impossible
5. **Budget Check** - Stop if time/token budget exhausted
6. **Final Verdict** - Pass if `observedPassRate >= minPassRate`
7. **Reporting** - Publish structured statistics

### Early Termination by Impossibility

PUNIT terminates early when it becomes mathematically impossible to reach the required pass rate:

```
Required successes = ceil(samples × minPassRate)
Max possible = current successes + remaining samples

If max possible < required successes → TERMINATE
```

Example: With 100 samples and 95% required (95 successes needed), if after 10 samples we have 4 failures, max possible = 6 + 90 = 96 ≥ 95, so we continue. After 6 failures, max possible = 4 + 90 = 94 < 95, so we terminate.

### Token Charging Modes

| Mode | Trigger | When Checked |
|------|---------|--------------|
| **Static** | `tokenCharge > 0`, no TokenChargeRecorder param | Before each sample |
| **Dynamic** | TokenChargeRecorder parameter present | After each sample |
| **None** | Neither configured | No token tracking |

### Budget Scope Precedence

Budgets are checked in order: **Suite → Class → Method**

The first exhausted budget triggers termination with the appropriate reason.

## Test Output

### Passing Test

```
Probabilistic test passed: 97.00% >= 95.00% (97/100 samples succeeded)
```

### Failing Test

```
Probabilistic test failed: observed pass rate 88.00% < required 95.00%

  Samples executed: 100 of 100
  Successes: 88
  Failures: 12
  Elapsed: 1523ms

  Example failures (showing 5 of 12):
    [Sample 1] Expected JSON but got: "Error: Rate limited"
    [Sample 2] Expected JSON but got: "Error: Rate limited"
    [Sample 3] Response was null
    [Sample 4] Expected JSON but got: "<html>..."
    [Sample 5] Timeout after 5000ms
```

### Structured Report Entries

Published via JUnit `TestReporter`:

```
punit.samples=100
punit.samplesExecuted=42
punit.successes=42
punit.failures=0
punit.minPassRate=0.9500
punit.observedPassRate=1.0000
punit.verdict=PASS
punit.terminationReason=CLASS_TOKEN_BUDGET_EXHAUSTED
punit.elapsedMs=15234
punit.tokenMode=DYNAMIC
punit.method.tokensConsumed=8500
punit.class.tokenBudget=50000
punit.class.tokensConsumed=48200
```

## Best Practices

### 1. Choose Appropriate Sample Sizes

- **Development**: 10-20 samples for fast feedback
- **CI (PR builds)**: 50-100 samples for confidence
- **Nightly/Release**: 500+ samples for statistical significance

Use `punit.samplesMultiplier` to scale per environment:

```bash
# Fast local development
./gradlew test -Dpunit.samplesMultiplier=0.1

# Thorough nightly run
./gradlew test -Dpunit.samplesMultiplier=5.0
```

### 2. Set Realistic Pass Rates

Don't aim for 100% on inherently non-deterministic tests. Typical targets:

- **LLM format compliance**: 85-95%
- **ML model accuracy**: 80-90%
- **Randomized algorithms**: 95-99%
- **Network-dependent tests**: 90-98%

### 3. Use Budgets for Cost Control

For expensive operations (API calls, LLM invocations):

```java
@ProbabilisticTestBudget(tokenBudget = 100000)  // Class limit
class ExpensiveTests {
    
    @ProbabilisticTest(samples = 50, tokenBudget = 10000)  // Method limit
    void expensiveOperation(TokenChargeRecorder recorder) {
        // Double protection: method and class budgets
    }
}
```

### 4. Capture Diagnostic Information

Use `maxExampleFailures` to capture representative failures:

```java
@ProbabilisticTest(samples = 100, minPassRate = 0.90, maxExampleFailures = 10)
void captureFailureExamples() {
    // Up to 10 failure examples will be included in the report
}
```

## Requirements

- Java 17+
- JUnit Jupiter 5.10+

## License

MIT License - see [LICENSE](LICENSE) for details.

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

