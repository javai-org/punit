# PUnit: The Probabilistic Unit Testing Framework
*Statistical regression testing for non-deterministic systems*

[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.org/)
[![JUnit 5](https://img.shields.io/badge/JUnit-5.13%2B-green.svg)](https://junit.org/junit5/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

PUnit is a JUnit 5 extension for testing non-deterministic systems. It runs tests multiple times and determines pass/fail based on statistical thresholds, making it ideal for testing LLMs, ML models, randomized algorithms, and other stochastic components.

## Table of Contents

- [Why PUnit?](#why-punit)
- [Features](#features)
- [Quick Start](#quick-start)
- [Core Concepts](#core-concepts)
- [Usage Examples](#usage-examples)
- [Configuration Reference](#configuration-reference)
- [How It Works](#how-it-works)
- [IDE Integration](#ide-integration)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)
- [Understanding Test Results](#understanding-test-results)

## Why PUnit?

Traditional unit tests follow a simple pass/fail model: if any assertion fails, the test fails. But what about systems that are inherently non-deterministic?

- **LLMs** don't always produce the same output for the same prompt
- **ML models** have accuracy rates, not guaranteed correctness
- **Randomized algorithms** may occasionally produce suboptimal results
- **Network-dependent tests** may experience transient failures

PUnit lets you express expectations statistically:

```java
// "This LLM should return valid JSON at least 90% of the time"
@ProbabilisticTest(samples = 100, minPassRate = 0.90)
void llmReturnsValidJson() {
    String response = llm.complete("Generate a JSON object with name and age");
    assertThat(response).satisfies(JsonValidator::isValidJson);
}
```

## Features

| Feature | Description |
|---------|-------------|
| ✅ **Statistical Pass/Fail** | Test passes if observed success rate ≥ minimum threshold |
| ✅ **Smart Early Termination** | Stops early when failure is inevitable OR success is guaranteed |
| ✅ **Budget Control** | Time and token budgets at method, class, or suite level |
| ✅ **Dynamic Token Tracking** | Record actual API consumption per invocation |
| ✅ **Configuration Overrides** | System properties and environment variables for CI/CD |
| ✅ **Accurate IDE Display** | Failed samples show as ❌, passed samples show as ✅ |
| ✅ **Thread-Safe** | Safe for parallel test execution |
| ✅ **Rich Reporting** | Detailed statistics via JUnit TestReporter |

## Quick Start

### Step 1: Add Dependency

**Gradle (Kotlin DSL):**
```kotlin
repositories {
    mavenLocal()  // If using locally published version
    mavenCentral()
}

dependencies {
    testImplementation("org.javai:punit:0.1.0")
}
```

**Gradle (Groovy DSL):**
```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    testImplementation 'org.javai:punit:0.1.0'
}
```

**Maven:**
```xml
<dependency>
    <groupId>org.javai</groupId>
    <artifactId>punit</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>
```

### Step 2: Write Your First Probabilistic Test

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

This test:
1. Runs the test body **100 times**
2. Counts how many invocations pass vs fail
3. **Passes** if at least 95% succeed (≥95 out of 100)
4. **Fails** if the success rate falls below 95%

### Step 3: Run It

```bash
./gradlew test
```

You'll see output like:

```
✅ MyServiceTest
    ✅ serviceReturnsValidResponse()
        ✅ Sample 1/100
        ✅ Sample 2/100
        ...
        ✅ Sample 95/100  ← SUCCESS_GUARANTEED, remaining samples skipped
```

### (Recommended) Spec-Driven Workflow

For production use, derive thresholds from empirical data:

```bash
# 1. Run a MEASURE experiment (1000 samples)
./gradlew measure --tests "MyExperiment.measureSomething"
# → Generates: src/test/resources/punit/specs/MyUseCase.yaml

# 2. Review and commit the spec
git add src/test/resources/punit/specs/
git commit -m "Add spec for MyUseCase"

# 3. Reference in your test (threshold derived from spec)
```

```java
@ProbabilisticTest(useCase = MyUseCase.class, samples = 50)
void serviceReturnsValidResponse() {
    // minPassRate derived from spec's empirical data
    Response response = myService.call();
    assertThat(response.isValid()).isTrue();
}
```

## Core Concepts

### Samples and Pass Rate

- **`samples`**: How many times to run the test body
- **`minPassRate`**: The minimum success rate required (0.0 to 1.0)

```java
@ProbabilisticTest(samples = 50, minPassRate = 0.80)
void testWith80PercentThreshold() {
    // Must pass at least 40/50 times (80%)
}
```

### Early Termination

PUnit optimizes test execution with two types of early termination:

#### 1. Impossibility Detection (Fail Fast)
When it becomes mathematically impossible to reach the required pass rate:

```
samples = 10, minPassRate = 0.8 → need 8 successes

After 3 failures with 0 successes:
  max possible = 0 + 7 remaining = 7 < 8 required
  → TERMINATE (impossible to pass)
```

#### 2. Success Guaranteed (Pass Fast) 
When enough samples have passed that the test will definitely succeed:

```
samples = 10, minPassRate = 0.8 → need 8 successes

After 8 consecutive successes:
  already have 8 ≥ 8 required
  → TERMINATE (success guaranteed, skip remaining 2 samples)
```

This optimization saves tokens and time when testing expensive operations like LLM calls.

### Budget Control

Control resource consumption with time and token budgets:

```java
@ProbabilisticTest(
    samples = 100,
    minPassRate = 0.90,
    timeBudgetMs = 30000,    // Stop after 30 seconds
    tokenBudget = 50000      // Stop after 50k tokens
)
void expensiveTest(TokenChargeRecorder recorder) {
    LlmResponse response = llm.complete("...");
    recorder.recordTokens(response.usage().totalTokens());
    assertThat(response.content()).isNotEmpty();
}
```

## Usage Examples

### Basic Test

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
    // Test terminates after 30 seconds, even if samples remain
    Response response = slowService.call();
    assertThat(response).isNotNull();
}
```

### Static Token Budget

When you know approximately how many tokens each sample uses:

```java
@ProbabilisticTest(
    samples = 100, 
    minPassRate = 0.90,
    tokenCharge = 500,      // Each sample uses ~500 tokens
    tokenBudget = 10000     // Stop after 10,000 tokens (20 samples max)
)
void llmTestWithStaticTokenBudget() {
    String response = llmClient.complete("Summarize this text...");
    assertThat(response.length()).isGreaterThan(50);
}
```

### Dynamic Token Tracking

For accurate token tracking from actual API responses:

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

Share a budget across multiple test methods:

```java
@ProbabilisticTestBudget(timeBudgetMs = 60000, tokenBudget = 100000)
class LlmIntegrationTests {

    @ProbabilisticTest(samples = 20, minPassRate = 0.90)
    void testJsonGeneration(TokenChargeRecorder recorder) {
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

### Evaluate Partial Results on Budget Exhaustion

```java
@ProbabilisticTest(
    samples = 100,
    minPassRate = 0.80,
    tokenBudget = 5000,
    onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL
)
void evaluateWhatWeHave(TokenChargeRecorder recorder) {
    // If budget runs out after 20 samples with 18 successes:
    // 18/20 = 90% >= 80% → PASS (instead of automatic FAIL)
    LlmResponse response = llmClient.complete("...");
    recorder.recordTokens(response.getUsage().getTotalTokens());
    assertThat(response).isNotNull();
}
```

## Configuration Reference

### @ProbabilisticTest Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `samples` | int | 100 | Number of test invocations |
| `minPassRate` | double | 0.95 | Minimum success rate (0.0 to 1.0) |
| `timeBudgetMs` | long | 0 | Max time in ms (0 = unlimited) |
| `tokenCharge` | int | 0 | Static tokens per sample |
| `tokenBudget` | long | 0 | Max tokens (0 = unlimited) |
| `onBudgetExhausted` | enum | FAIL | `FAIL` or `EVALUATE_PARTIAL` |
| `onException` | enum | FAIL_SAMPLE | `FAIL_SAMPLE` or `ABORT_TEST` |
| `maxExampleFailures` | int | 5 | Example failures to capture for reporting |

### @ProbabilisticTestBudget (Class-Level)

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `timeBudgetMs` | long | 0 | Shared time budget for all methods |
| `tokenBudget` | long | 0 | Shared token budget for all methods |
| `onBudgetExhausted` | enum | FAIL | Behavior when class budget exhausted |

### System Property Overrides

Override annotation values at runtime (useful for CI/CD):

```bash
# Override sample count
./gradlew test -Dpunit.samples=10

# Override minimum pass rate
./gradlew test -Dpunit.minPassRate=0.80

# Scale all sample counts by multiplier (0.1 = 10% of original)
./gradlew test -Dpunit.samplesMultiplier=0.1

# Suite-level budget (shared across ALL tests in the run)
./gradlew test -Dpunit.suite.tokenBudget=500000
```

### Environment Variables

| System Property | Environment Variable |
|-----------------|---------------------|
| `punit.samples` | `PUNIT_SAMPLES` |
| `punit.minPassRate` | `PUNIT_MIN_PASS_RATE` |
| `punit.samplesMultiplier` | `PUNIT_SAMPLES_MULTIPLIER` |
| `punit.timeBudgetMs` | `PUNIT_TIME_BUDGET_MS` |
| `punit.tokenCharge` | `PUNIT_TOKEN_CHARGE` |
| `punit.tokenBudget` | `PUNIT_TOKEN_BUDGET` |
| `punit.suite.timeBudgetMs` | `PUNIT_SUITE_TIME_BUDGET_MS` |
| `punit.suite.tokenBudget` | `PUNIT_SUITE_TOKEN_BUDGET` |

### Configuration Precedence

Values are resolved in this order (highest priority first):

1. **System property** (`-Dpunit.samples=10`)
2. **Environment variable** (`PUNIT_SAMPLES=10`)
3. **Annotation value** (`@ProbabilisticTest(samples = 100)`)
4. **Framework default** (100)

## How It Works

### Execution Flow

```
1. Configuration Resolution
   └─ Merge: system props → env vars → annotation → defaults

2. Sample Stream Generation
   └─ Create N invocation contexts (lazy stream)

3. For Each Sample:
   ├─ Execute test body
   ├─ Catch failures (record, don't throw)
   ├─ Check budgets (time, tokens)
   └─ Check early termination:
       ├─ IMPOSSIBILITY → stop, test fails
       └─ SUCCESS_GUARANTEED → stop, test passes

4. Final Verdict
   └─ PASS if observedPassRate >= minPassRate
```

### Token Charging Modes

| Mode | Trigger | When Checked |
|------|---------|--------------|
| **Static** | `tokenCharge > 0`, no TokenChargeRecorder param | Before each sample |
| **Dynamic** | TokenChargeRecorder parameter present | After each sample |
| **None** | Neither configured | No token tracking |

### Budget Scope Precedence

Budgets are checked in order: **Suite → Class → Method**

The first exhausted budget triggers termination.

## IDE Integration

PUnit provides accurate visual feedback in your IDE:

```
❌ evaluateReadinessTest()
    ❌ Sample 1/20  ← Assertion failed
    ❌ Sample 2/20  ← Assertion failed
    ❌ Sample 3/20  ← Assertion failed
    ❌ Sample 4/20  ← Assertion failed
    ❌ Sample 5/20  ← IMPOSSIBILITY: cannot reach 80% pass rate
```

```
✅ serviceReturnsValidResponse()
    ✅ Sample 1/10
    ✅ Sample 2/10
    ...
    ✅ Sample 8/10  ← SUCCESS_GUARANTEED: 80% achieved
```

### Icon Meanings

| Icon | Meaning |
|------|---------|
| ✅ | Sample passed its assertion |
| ❌ | Sample failed its assertion |

## Best Practices

### 1. Choose Appropriate Sample Sizes

| Environment | Samples | Purpose |
|-------------|---------|---------|
| Local dev | 10-20 | Fast feedback |
| PR builds | 50-100 | Reasonable confidence |
| Nightly/Release | 500+ | Statistical significance |

Use `samplesMultiplier` to scale per environment:

```bash
# Fast local development (10% of defined samples)
./gradlew test -Dpunit.samplesMultiplier=0.1

# Thorough nightly run (5x samples)
./gradlew test -Dpunit.samplesMultiplier=5.0
```

### 2. Set Realistic Pass Rates

Don't aim for 100% on inherently non-deterministic tests:

| Use Case | Typical Pass Rate |
|----------|-------------------|
| LLM format compliance | 85-95% |
| ML model accuracy | 80-90% |
| Randomized algorithms | 95-99% |
| Network-dependent tests | 90-98% |

### 3. Use Budgets for Cost Control

```java
// Double protection: method AND class budgets
@ProbabilisticTestBudget(tokenBudget = 100000)
class ExpensiveTests {
    
    @ProbabilisticTest(samples = 50, tokenBudget = 10000)
    void expensiveOperation(TokenChargeRecorder recorder) {
        // Limited by both budgets
    }
}
```

### 4. Capture Diagnostic Information

```java
@ProbabilisticTest(
    samples = 100, 
    minPassRate = 0.90, 
    maxExampleFailures = 10  // Capture up to 10 failure examples
)
void captureFailureExamples() {
    // Failure messages will be included in the test report
}
```

### 5. Use EVALUATE_PARTIAL for Expensive Tests

When you'd rather have partial results than nothing:

```java
@ProbabilisticTest(
    samples = 1000,
    minPassRate = 0.85,
    tokenBudget = 50000,
    onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL
)
void largeScaleTest(TokenChargeRecorder recorder) {
    // If budget exhausted after 200 samples with 180 successes:
    // 180/200 = 90% >= 85% → PASS
}
```

## Troubleshooting

### Test Only Runs Once

If your probabilistic test only executes one sample instead of the configured number:

1. **Check for system property overrides:**
   ```bash
   # Make sure these aren't set
   echo $PUNIT_SAMPLES
   echo $PUNIT_SAMPLES_MULTIPLIER
   ```

2. **Check for class-level budget annotations** that might be exhausting immediately

3. **Verify the annotation is correct:**
   ```java
   @ProbabilisticTest(samples = 10, minPassRate = 0.9)  // ✓ Correct
   @Test  // ✗ Wrong - this is a regular JUnit test
   ```

### Test Reports "IMPOSSIBILITY" Immediately

With high pass rates, a single failure can trigger impossibility:

```java
// With 100% pass rate, first failure → impossibility
@ProbabilisticTest(samples = 100, minPassRate = 1.0)  // Very strict!
```

Consider lowering the pass rate or using more samples.

### Token Budget Exhausted Too Quickly

1. Check that `tokenCharge` reflects actual usage
2. Use dynamic token tracking for variable consumption
3. Increase the budget or reduce samples

## Test Output Examples

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

### Early Termination (Success Guaranteed)

```
Probabilistic test passed: 100.00% >= 80.00%

  Samples executed: 8 of 10
  Successes: 8
  Failures: 0
  Termination: SUCCESS_GUARANTEED
  Reason: After 8 samples with 8 successes (100.0%), required threshold (8 successes) 
          already met. Skipping 2 remaining samples.
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
punit.terminationReason=SUCCESS_GUARANTEED
punit.elapsedMs=15234
punit.method.tokensConsumed=8500
```

## Understanding Test Results

### Reading PUnit Verdicts

When any sample fails, the IDE shows the test as ❌ FAILED. **This is correct behavior** - even statistically insignificant failures deserve attention. The key is knowing *how much* attention.

**Always read the console summary:**
```
═══════════════════════════════════════════════════════════════
PUnit PASSED: sample()
  Observed pass rate: 99.0% (97/98) >= threshold: 95.0%
  Termination: Required pass rate already achieved
═══════════════════════════════════════════════════════════════
```

**Interpreting Results:**

| IDE Shows | Console Says   | What To Do                                                                       |
|-----------|----------------|----------------------------------------------------------------------------------|
| ❌ FAILED  | `PUnit PASSED` | Take a quick look. Probably fine - rerun if concerned. Don't send investigators. |
| ❌ FAILED  | `PUnit FAILED` | Statistically significant. You have data-informed justification to investigate.  |
| ✅ PASSED  | `PUnit PASSED` | All samples passed. Move on.                                                     |

**Why it works this way:**

The red ❌(or whatever icon your IDE uses) ensures you don't ignore failures completely. The console verdict tells you
how to *prioritize*:
- **Statistically insignificant failure:** Glance at error messages, maybe rerun, stay cool
- **Statistically significant failure:** Dig in - the data says something is wrong

This is what PUnit is all about: **data-informed decisions about whether to act on test failure.** Without PUnit, you'd be flying blind, chasing false flags, or labeling failures as "flaky" and ignoring them.

A last word on statistically significant failure: We live in a universe in which rare things happen occasionally. So
even in the case where a statistically significant failure has occurred, it can still sometimes be a false positive.
But the power of the statistics behind PUnit means that this will itself occur very rarely, maybe one time in a hundred, depending on your test scenario. PUnit is powerful, but it's not magic!

## Requirements

- Java 17+
- JUnit Jupiter 5.13+

## License

MIT License - see [LICENSE](LICENSE) for details.

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.
