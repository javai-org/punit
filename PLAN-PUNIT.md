# PUNIT: Probabilistic Unit Testing Framework — Implementation Plan

## Executive Summary

**PUNIT** is a JUnit 5 extension that enables binary pass/fail outcomes for inherently stochastic or non-deterministic tests. It executes a test method multiple times, aggregates results, and determines pass/fail based on whether the observed pass rate meets a configurable threshold—while always recording transparent statistical evidence.

---

## 1. High-Level Architecture

### 1.1 Component Diagram (Textual)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           JUnit 5 Platform                                  │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                     Jupiter Test Engine                               │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │  │
│  │  │                 @TestTemplate Mechanism                         │  │  │
│  │  └─────────────────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         PUNIT Extension Layer                               │
│                                                                             │
│  ┌─────────────────────┐   ┌─────────────────────┐   ┌──────────────────┐  │
│  │ @ProbabilisticTest  │   │ ProbabilisticTest   │   │ Configuration    │  │
│  │ (Meta-annotation    │──▶│ Extension           │◀──│ Resolver         │  │
│  │  wrapping           │   │ (TestTemplate       │   │ (annotation +    │  │
│  │  @TestTemplate)     │   │  InvocationContext  │   │  system props +  │  │
│  │                     │   │  Provider)          │   │  env vars)       │  │
│  └─────────────────────┘   └─────────────────────┘   └──────────────────┘  │
│                                    │                                        │
│                                    ▼                                        │
│  ┌─────────────────────┐   ┌─────────────────────┐   ┌──────────────────┐  │
│  │ Invocation          │   │ Sample Result       │   │ Early            │  │
│  │ Interceptor         │──▶│ Aggregator          │──▶│ Termination      │  │
│  │ (catches assertion  │   │ (tracks pass/fail   │   │ Evaluator        │  │
│  │  errors per sample) │   │  counts, stores     │   │ (impossibility + │  │
│  │                     │   │  example failures)  │   │  budget checks)  │  │
│  └─────────────────────┘   └─────────────────────┘   └──────────────────┘  │
│                                    │                                        │
│                                    ▼                                        │
│  ┌─────────────────────┐   ┌─────────────────────┐   ┌──────────────────┐  │
│  │ Cost Budget         │   │ Final Verdict       │   │ Report           │  │
│  │ Monitor             │   │ Decider             │   │ Publisher        │  │
│  │ (wall-clock time +  │   │ (compare observed   │   │ (TestReporter +  │  │
│  │  token accumulator, │   │  rate to min rate,  │   │  structured      │  │
│  │  extensible)        │   │  produce pass/fail) │   │  failure msg)    │  │
│  └─────────────────────┘   └─────────────────────┘   └──────────────────┘  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Component Responsibilities

| Component | Responsibility |
|-----------|----------------|
| **@ProbabilisticTest** | Meta-annotation combining `@TestTemplate` with PUNIT extension. Exposes `samples`, `minPassRate`, and operational parameters. |
| **ProbabilisticTestExtension** | Core JUnit 5 extension implementing `TestTemplateInvocationContextProvider`. Generates N invocation contexts and coordinates all other components. |
| **ConfigurationResolver** | Resolves effective configuration by merging annotation values, system properties, and environment variables with defined precedence. |
| **InvocationInterceptor** | Intercepts each sample execution, catches `AssertionError` (and optionally other exceptions), and records outcome without failing the overall test immediately. |
| **SampleResultAggregator** | Thread-safe accumulator for sample outcomes. Tracks successes, failures, example failure causes, and elapsed time. |
| **EarlyTerminationEvaluator** | After each sample, checks (a) mathematical impossibility of reaching `minPassRate`, and (b) cost budget exhaustion. Signals termination when triggered. |
| **CostBudgetMonitor** | Tracks resource consumption at method level (wall-clock time and token accumulation). Provides `shouldTerminate()` and `getRemainingBudget()` methods for each budget type. |
| **SharedBudgetMonitor** | Thread-safe budget tracker for class-level and suite-level shared budgets. Coordinates consumption across multiple test methods. |
| **SuiteBudgetManager** | Singleton managing suite-level budget state across the entire test run. Initialized from system properties/environment variables. |
| **TokenChargeRecorder** | Injectable interface for test methods to report dynamic token consumption. Implemented by `DefaultTokenChargeRecorder`, which integrates with all active budget monitors (method, class, suite). |
| **FinalVerdictDecider** | At end of all samples (or early termination), computes `observedPassRate`, compares to `minPassRate`, and produces final pass/fail. |
| **ReportPublisher** | Publishes structured statistics via `TestReporter.publishEntry()`. On failure, constructs a detailed failure message including all statistics and termination reason. |

### 1.3 Data Flow

```
1. JUnit discovers @ProbabilisticTest method
2. ProbabilisticTestExtension.provideTestTemplateInvocationContexts() called
3. ConfigurationResolver computes effective (samples, minPassRate, timeBudget, tokenBudget, tokenCharge, ...)
4. Extension creates SampleResultAggregator, CostBudgetMonitor, TokenChargeRecorder (if dynamic mode)
5. Extension yields N InvocationContext objects (lazy stream for early termination)
   │
   ├─► For each invocation context (sample k = 1..N):
   │     a. [Static mode only] Pre-sample budget check (time + token)
   │     b. JUnit triggers @BeforeEach → test method → @AfterEach
   │          - If dynamic mode: test calls tokenRecorder.recordTokens(n)
   │     c. InvocationInterceptor wraps execution:
   │          - Success: aggregator.recordSuccess()
   │          - AssertionError: aggregator.recordFailure(error)
   │          - Other exception: configurable (default: recordFailure)
   │     d. [Dynamic mode only] Post-sample token accumulation
   │     e. EarlyTerminationEvaluator.shouldTerminate(aggregator, budgetMonitor)
   │          - If yes: signal termination, break loop
   │
6. After loop (normal or early):
   a. FinalVerdictDecider.computeVerdict(aggregator, config)
   b. ReportPublisher.publish(aggregator, verdict, testReporter)
   c. If verdict == FAIL: throw AssertionError with detailed message
7. JUnit records single test result (PASS or FAIL)
```

---

## 2. Public API Surface

### 2.1 Core Annotation: `@ProbabilisticTest`

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(ProbabilisticTestExtension.class)
public @interface ProbabilisticTest {

    /**
     * Number of sample invocations to execute.
     * Must be ≥ 1. Default: 100.
     */
    int samples() default 100;

    /**
     * Minimum pass rate required for overall test success.
     * Value in range [0.0, 1.0]. Default: 0.95 (95%).
     * Overall test passes iff (successes / samples) ≥ minPassRate.
     */
    double minPassRate() default 0.95;

    /**
     * Maximum wall-clock time budget in milliseconds for all samples.
     * 0 = unlimited. Default: 0.
     * If budget exhausted before all samples complete, behavior is controlled
     * by onBudgetExhausted().
     */
    long timeBudgetMs() default 0;

    /**
     * Token charge per sample invocation. This value is accumulated after
     * each sample execution. Used in conjunction with tokenBudget() to
     * limit total token consumption across all samples.
     * Must be ≥ 0. Default: 0 (no token tracking).
     * 
     * Use case: When testing systems with per-call costs (API tokens, credits),
     * set this to the expected/maximum tokens consumed per invocation.
     */
    int tokenCharge() default 0;

    /**
     * Maximum total token budget for all samples combined.
     * 0 = unlimited. Default: 0.
     * After each sample, tokensConsumed += tokenCharge. If tokensConsumed
     * exceeds tokenBudget before all samples complete, behavior is controlled
     * by onBudgetExhausted().
     * 
     * The check occurs BEFORE starting a new sample: if the next sample's
     * tokenCharge would exceed the remaining budget, termination is triggered.
     */
    long tokenBudget() default 0;

    /**
     * Behavior when any budget (time or token) is exhausted before completing
     * all samples. Default: FAIL (test fails if budget exhausted).
     */
    BudgetExhaustedBehavior onBudgetExhausted() default BudgetExhaustedBehavior.FAIL;

    /**
     * How to treat non-AssertionError exceptions thrown by the test method.
     * Default: FAIL_SAMPLE (count as a failed sample, continue execution).
     */
    ExceptionHandling onException() default ExceptionHandling.FAIL_SAMPLE;

    /**
     * Maximum number of example failures to capture for diagnostic reporting.
     * Default: 5. Set to 0 to disable capture.
     */
    int maxExampleFailures() default 5;
}
```

### 2.2 Supporting Enums

```java
public enum BudgetExhaustedBehavior {
    /** Test fails immediately if any budget (time or token) is exhausted before all samples. */
    FAIL,
    /** Evaluate verdict based on samples completed so far when budget is exhausted. */
    EVALUATE_PARTIAL
}

public enum ExceptionHandling {
    /** Treat non-assertion exceptions as sample failure, continue. */
    FAIL_SAMPLE,
    /** Abort entire test immediately on any non-assertion exception. */
    ABORT
}
```

### 2.3 Dynamic Token Charging: `TokenChargeRecorder`

For scenarios where token consumption varies per sample (e.g., LLM calls with variable output lengths), PUNIT provides an injectable `TokenChargeRecorder` that test methods can use to report actual token usage.

```java
/**
 * Injectable component for reporting dynamic token consumption within a sample.
 * Inject via JUnit 5 parameter resolution in test methods.
 * 
 * When a test method has a TokenChargeRecorder parameter, dynamic token charging
 * is enabled and the static tokenCharge annotation value is ignored for that test.
 */
public interface TokenChargeRecorder {

    /**
     * Records tokens consumed during this sample invocation.
     * May be called multiple times within a single sample; values are summed.
     * 
     * @param tokens number of tokens to add to this sample's consumption (must be ≥ 0)
     * @throws IllegalArgumentException if tokens < 0
     */
    void recordTokens(int tokens);

    /**
     * Records tokens consumed during this sample invocation.
     * Convenience overload for long values (e.g., from API responses).
     * 
     * @param tokens number of tokens to add to this sample's consumption (must be ≥ 0)
     * @throws IllegalArgumentException if tokens < 0
     */
    void recordTokens(long tokens);

    /**
     * Returns the total tokens recorded so far for this sample.
     * Resets to 0 at the start of each sample.
     * 
     * @return tokens recorded in current sample
     */
    long getTokensForCurrentSample();

    /**
     * Returns the cumulative tokens consumed across all completed samples.
     * Does not include the current (in-progress) sample.
     * 
     * @return total tokens consumed in completed samples
     */
    long getTotalTokensConsumed();

    /**
     * Returns the remaining token budget, or Long.MAX_VALUE if unlimited.
     * 
     * @return remaining budget after completed samples
     */
    long getRemainingBudget();
}
```

#### 2.3.1 Usage Pattern

```java
@ProbabilisticTest(samples = 50, minPassRate = 0.90, tokenBudget = 100000)
void llmRespondsWithValidJson(TokenChargeRecorder tokenRecorder) {
    String prompt = "Generate a JSON object with fields: name, age, email";
    
    // Call LLM and capture usage from response
    LlmResponse response = llmClient.complete(prompt);
    
    // Report actual token consumption from LLM response metadata
    tokenRecorder.recordTokens(response.getUsage().getTotalTokens());
    
    // Perform assertions
    assertThat(response.getContent()).satisfies(JsonValidator::isValidJson);
}
```

#### 2.3.2 Static vs Dynamic Token Charging

| Mode | Triggered When | Behavior |
|------|----------------|----------|
| **Static** | `tokenCharge > 0` AND no `TokenChargeRecorder` parameter | Fixed `tokenCharge` added after each sample |
| **Dynamic** | Test method has `TokenChargeRecorder` parameter | Tokens recorded via `recordTokens()` calls are accumulated |
| **Mixed** | Both present | Dynamic takes precedence; static `tokenCharge` is ignored with a logged warning |
| **None** | `tokenCharge = 0` AND no `TokenChargeRecorder` | No token tracking; `tokenBudget` is effectively ignored |

#### 2.3.3 Budget Check Timing with Dynamic Tokens

With dynamic token charging, the budget check timing changes:

- **Pre-sample check (static)**: Cannot be done because consumption is unknown
- **Post-sample check (dynamic)**: After each sample completes, check if budget is exhausted

```
After completing sample k:
    tokensConsumed += tokenRecorder.getTokensForCurrentSample()
    if tokenBudget > 0 AND tokensConsumed > tokenBudget:
        match onBudgetExhausted:
            FAIL → TERMINATE with reason TOKEN_BUDGET_EXHAUSTED
            EVALUATE_PARTIAL → compute verdict on samples completed (including this one)
```

**Trade-off**: With dynamic charging, the budget may be exceeded by the final sample's consumption before termination occurs. This is acceptable because:
1. The exact consumption is unknown until the sample completes
2. The alternative (aborting mid-sample) would leave resources in an inconsistent state
3. Users who need strict limits should use conservative budgets or static charging

### 2.4 Budget Scope: Method, Class, and Suite Levels

Budgets can be applied at different levels of granularity, allowing test developers to control resource consumption across individual tests, entire test classes, or complete test suites.

#### 2.4.1 Budget Scope Levels

| Scope | Description | Configuration Mechanism |
|-------|-------------|------------------------|
| **Method** | Budget applies to a single `@ProbabilisticTest` method | `@ProbabilisticTest` annotation attributes |
| **Class** | Budget shared across all `@ProbabilisticTest` methods in a test class | `@ProbabilisticTestBudget` class-level annotation |
| **Suite** | Budget shared across all probabilistic tests in the JVM/test run | System properties or environment variables |

#### 2.4.2 Class-Level Budget Annotation: `@ProbabilisticTestBudget`

```java
/**
 * Defines shared budgets for all @ProbabilisticTest methods within a test class.
 * When present, class-level budgets are shared across all probabilistic test
 * methods in the annotated class.
 * 
 * Method-level budgets in @ProbabilisticTest act as additional constraints
 * (whichever is exhausted first triggers termination).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(ProbabilisticTestBudgetExtension.class)
public @interface ProbabilisticTestBudget {

    /**
     * Maximum wall-clock time budget in milliseconds shared across all
     * @ProbabilisticTest methods in this class.
     * 0 = unlimited. Default: 0.
     */
    long timeBudgetMs() default 0;

    /**
     * Maximum token budget shared across all @ProbabilisticTest methods
     * in this class. Tokens consumed by any method count against this budget.
     * 0 = unlimited. Default: 0.
     */
    long tokenBudget() default 0;

    /**
     * Behavior when the class-level budget is exhausted.
     * Affects all subsequent @ProbabilisticTest methods in the class.
     * Default: FAIL.
     */
    BudgetExhaustedBehavior onBudgetExhausted() default BudgetExhaustedBehavior.FAIL;
}
```

#### 2.4.3 Suite-Level Budget Configuration

Suite-level budgets are configured via system properties or environment variables:

| Property | Env Var | Description |
|----------|---------|-------------|
| `punit.suite.timeBudgetMs` | `PUNIT_SUITE_TIME_BUDGET_MS` | Max time across all probabilistic tests in JVM |
| `punit.suite.tokenBudget` | `PUNIT_SUITE_TOKEN_BUDGET` | Max tokens across all probabilistic tests in JVM |
| `punit.suite.onBudgetExhausted` | `PUNIT_SUITE_ON_BUDGET_EXHAUSTED` | `FAIL` or `EVALUATE_PARTIAL` |

#### 2.4.4 Budget Scope Precedence and Interaction

When multiple budget scopes are active, they interact as follows:

```
For each sample execution:
    1. Check suite-level budgets (if configured)
       → If exhausted: terminate all remaining tests in suite
    2. Check class-level budgets (if @ProbabilisticTestBudget present)
       → If exhausted: terminate all remaining tests in class
    3. Check method-level budgets (from @ProbabilisticTest)
       → If exhausted: terminate current test method
```

**Key semantics**:
- **All budgets are checked**: A sample may be blocked by any active budget
- **First exhausted wins**: The most restrictive budget determines when termination occurs
- **Consumption is cumulative**: Tokens/time consumed by a method count against all enclosing scopes
- **Independent tracking**: Each scope tracks its own consumption and remaining budget

#### 2.4.5 Usage Examples

**Method-level only (current default)**:
```java
class MyTest {
    @ProbabilisticTest(samples = 100, tokenBudget = 10000)
    void test1() { /* has its own 10K token budget */ }

    @ProbabilisticTest(samples = 100, tokenBudget = 10000)
    void test2() { /* has its own separate 10K token budget */ }
}
// Total possible: 20K tokens (10K per test)
```

**Class-level shared budget**:
```java
@ProbabilisticTestBudget(tokenBudget = 15000, timeBudgetMs = 60000)
class MyTest {
    @ProbabilisticTest(samples = 100)
    void test1() { /* shares class budget */ }

    @ProbabilisticTest(samples = 100)  
    void test2() { /* shares class budget */ }
}
// Total possible: 15K tokens shared across both tests
// If test1 uses 10K, test2 only has 5K remaining
```

**Mixed method and class budgets**:
```java
@ProbabilisticTestBudget(tokenBudget = 20000)
class MyTest {
    @ProbabilisticTest(samples = 100, tokenBudget = 8000)
    void test1() { /* limited to 8K tokens AND class budget */ }

    @ProbabilisticTest(samples = 100, tokenBudget = 15000)
    void test2() { /* limited to 15K tokens AND remaining class budget */ }
}
// test1 can use at most 8K (method limit)
// test2 can use at most min(15K, 20K - test1_usage)
```

**Suite-level via system property**:
```bash
# Limit entire test suite to 100K tokens and 5 minutes
./gradlew test -Dpunit.suite.tokenBudget=100000 -Dpunit.suite.timeBudgetMs=300000
```

#### 2.4.6 Shared Budget State Management

Class-level and suite-level budgets require shared state across test executions:

| Scope | State Storage | Lifecycle |
|-------|---------------|-----------|
| **Method** | `ExtensionContext.Store` (method namespace) | Created/destroyed per test method |
| **Class** | `ExtensionContext.Store` (class namespace) | Created at first test, destroyed after last test in class |
| **Suite** | Static singleton or JUnit `ExtensionContext.Root` store | Created on first access, destroyed at JVM shutdown |

**Thread safety**: Suite-level and class-level budget monitors must be thread-safe to support parallel test execution across different test methods/classes.

#### 2.4.7 Reporting with Budget Scopes

When budgets are scoped beyond method level, reports include scope information:

```
samplesExecuted=47
tokensConsumed=4700
tokenBudget=5000
tokenBudgetScope=CLASS
classTokensConsumed=12300
classTokenBudget=15000
suiteTokensConsumed=45600
suiteTokenBudget=100000
terminationReason=CLASS_TOKEN_BUDGET_EXHAUSTED
```

### 2.5 Package Structure

```
com.prompz.punit/
├── api/
│   ├── ProbabilisticTest.java          (method-level annotation)
│   ├── ProbabilisticTestBudget.java    (class-level budget annotation)
│   ├── BudgetExhaustedBehavior.java    (enum)
│   ├── ExceptionHandling.java          (enum)
│   ├── BudgetScope.java                (enum: METHOD, CLASS, SUITE)
│   └── TokenChargeRecorder.java        (interface for dynamic token reporting)
├── engine/
│   ├── ProbabilisticTestExtension.java (main extension for @ProbabilisticTest)
│   ├── ProbabilisticTestBudgetExtension.java (extension for @ProbabilisticTestBudget)
│   ├── ConfigurationResolver.java
│   ├── SampleResultAggregator.java
│   ├── EarlyTerminationEvaluator.java
│   ├── CostBudgetMonitor.java          (per-method budget tracking)
│   ├── SharedBudgetMonitor.java        (class/suite-level shared budget tracking, thread-safe)
│   ├── SuiteBudgetManager.java         (singleton for suite-level budget state)
│   ├── FinalVerdictDecider.java
│   ├── ReportPublisher.java
│   └── DefaultTokenChargeRecorder.java (implementation of TokenChargeRecorder)
├── model/
│   ├── ProbabilisticTestConfiguration.java
│   ├── SampleResult.java
│   ├── AggregatedResults.java
│   ├── TerminationReason.java          (enum: COMPLETED, IMPOSSIBILITY, METHOD_TIME_BUDGET_EXHAUSTED, 
│   │                                          METHOD_TOKEN_BUDGET_EXHAUSTED, CLASS_TIME_BUDGET_EXHAUSTED,
│   │                                          CLASS_TOKEN_BUDGET_EXHAUSTED, SUITE_TIME_BUDGET_EXHAUSTED,
│   │                                          SUITE_TOKEN_BUDGET_EXHAUSTED)
│   └── Verdict.java
└── spi/
    └── CostModel.java                  (v2: SPI for custom cost models)
```

---

## 3. Execution Semantics

### 3.1 Sample Counting and Pass Rate Calculation

- **Required successes**: `requiredSuccesses = ceil(samples × minPassRate)`
- **Observed pass rate**: `observedPassRate = successes / samplesExecuted`
- **Verdict**: `PASS if observedPassRate ≥ minPassRate, else FAIL`

**Rounding rule**: Use `Math.ceil()` for `requiredSuccesses` to ensure the threshold is met, not merely approached.

### 3.2 Early Termination: Fail-Fast by Impossibility

After each sample `k` (where `k` ranges from 1 to N):

```
successes_so_far = s
failures_so_far = f = k - s
remaining_samples = N - k
max_possible_successes = s + remaining_samples

if max_possible_successes < requiredSuccesses:
    TERMINATE with reason IMPOSSIBILITY
```

This is a **deterministic, lossless optimization**. It cannot change the outcome; it only accelerates failure detection.

### 3.3 Early Termination: Budget Exhaustion

PUNIT supports two independent budget mechanisms that are checked before each sample:

#### 3.3.1 Time Budget Check

```
if timeBudgetMs > 0 AND elapsed_ms ≥ timeBudgetMs:
    match onBudgetExhausted:
        FAIL → TERMINATE with reason TIME_BUDGET_EXHAUSTED, verdict FAIL
        EVALUATE_PARTIAL → compute verdict on samples completed so far
```

#### 3.3.2 Token Budget Check

PUNIT supports two token charging modes with different check timing:

##### Static Token Charging (annotation-based)

When using the `tokenCharge` annotation parameter (and no `TokenChargeRecorder` is injected):

```
Before starting sample k:
    projectedTokens = tokensConsumed + tokenCharge
    if tokenBudget > 0 AND projectedTokens > tokenBudget:
        match onBudgetExhausted:
            FAIL → TERMINATE with reason TOKEN_BUDGET_EXHAUSTED, verdict FAIL
            EVALUATE_PARTIAL → compute verdict on samples completed so far
    
After completing sample k:
    tokensConsumed += tokenCharge
```

**Rationale**: Pre-execution check prevents overspending when charge is known upfront.

##### Dynamic Token Charging (recorder-based)

When the test method injects a `TokenChargeRecorder`:

```
Before starting sample k:
    tokenRecorder.reset()  // Clear per-sample accumulator
    
During sample k:
    tokenRecorder.recordTokens(n)  // Test code reports actual consumption
    
After completing sample k:
    sampleTokens = tokenRecorder.getTokensForCurrentSample()
    tokensConsumed += sampleTokens
    if tokenBudget > 0 AND tokensConsumed > tokenBudget:
        match onBudgetExhausted:
            FAIL → TERMINATE with reason TOKEN_BUDGET_EXHAUSTED, verdict FAIL
            EVALUATE_PARTIAL → compute verdict on samples completed so far
```

**Rationale**: Post-execution check is necessary because consumption is unknown until the sample completes. The trade-off is that the budget may be exceeded by the final sample before termination.

##### Common Behavior

- **Use cases**: API rate limits, cost control for paid services, resource quotas, LLM token budgets.
- **Relationship to time budget**: Both budgets are independent. If either is exhausted, termination is triggered.
- **Mode selection**: Dynamic mode activates when test method has a `TokenChargeRecorder` parameter; otherwise static mode is used.

#### 3.3.3 Budget Scope and Priority

When multiple budget scopes are active, checks occur in order from broadest to narrowest:

```
1. Suite-level time budget     → SUITE_TIME_BUDGET_EXHAUSTED
2. Suite-level token budget    → SUITE_TOKEN_BUDGET_EXHAUSTED
3. Class-level time budget     → CLASS_TIME_BUDGET_EXHAUSTED
4. Class-level token budget    → CLASS_TOKEN_BUDGET_EXHAUSTED
5. Method-level time budget    → METHOD_TIME_BUDGET_EXHAUSTED
6. Method-level token budget   → METHOD_TOKEN_BUDGET_EXHAUSTED
```

**Rationale for check order**:
- Broader scopes are checked first because exhausting a suite/class budget affects multiple tests
- Within a scope, time is checked before tokens (time exhaustion is more urgent)
- The first triggered budget determines the `terminationReason`

**Consumption propagation**:
When a sample consumes resources, consumption is recorded at all active scopes:

```
After sample k completes:
    methodBudget.addTokens(sampleTokens)
    methodBudget.updateElapsedTime()
    
    if classBudget is active:
        classBudget.addTokens(sampleTokens)  // thread-safe
        classBudget.updateElapsedTime()
        
    if suiteBudget is active:
        suiteBudget.addTokens(sampleTokens)  // thread-safe
        suiteBudget.updateElapsedTime()
```

### 3.4 Lifecycle Hook Semantics

| JUnit Hook | Behavior with @ProbabilisticTest |
|------------|----------------------------------|
| `@BeforeAll` | Runs **once** before all samples |
| `@BeforeEach` | Runs **before each sample** |
| `@AfterEach` | Runs **after each sample** |
| `@AfterAll` | Runs **once** after all samples (and after verdict) |

This matches standard `@RepeatedTest` semantics and requires no special handling.

---

## 4. Reporting Specification

### 4.1 Structured Report Entries (Always Published)

Published via `TestReporter.publishEntry()` with key `punit.results`:

```
samples=100
samplesExecuted=100
successes=97
failures=3
minPassRate=0.95
observedPassRate=0.97
verdict=PASS
terminationReason=COMPLETED
elapsedMs=1523
tokenCharge=100
tokensConsumed=10000
tokenBudget=50000
```

**Notes on token fields**:
- `tokenCharge`: The configured static charge per sample (0 if not configured or using dynamic mode)
- `tokensConsumed`: Total tokens consumed (static: `samplesExecuted × tokenCharge`, dynamic: sum of recorded values)
- `tokenBudget`: The configured budget limit (0 if unlimited)
- `tokenMode`: Either `STATIC`, `DYNAMIC`, or `NONE` indicating which charging mode was used
- These fields are always included for consistency, even when token budgeting is not used

### 4.2 Failure Message Format

On failure, throw `AssertionError` with message:

```
Probabilistic test failed: observed pass rate 0.72 < required 0.95

  Samples executed: 50 of 100
  Successes: 36
  Failures: 14
  Termination: IMPOSSIBILITY (cannot reach required 95 successes)
  Elapsed: 823ms
  Tokens: 5000 of 50000 (100 per sample)

  Example failures (showing 3 of 14):
    [Sample 3] Expected <true> but was <false>
    [Sample 7] java.lang.AssertionError: Response did not contain expected keyword
    [Sample 12] Expected status 200 but was 500
```

**Token budget exhaustion message variant**:

```
Probabilistic test failed: token budget exhausted

  Samples executed: 47 of 100
  Successes: 45
  Failures: 2
  Termination: TOKEN_BUDGET_EXHAUSTED
  Elapsed: 2341ms
  Tokens: 4700 of 5000 (100 per sample) — next sample would exceed budget

  Note: 47 samples completed successfully would have passed (95.74% ≥ 95.00%)
        but budget exhaustion with FAIL policy forces failure.
```

### 4.3 Suppressed Exceptions

Attach up to `maxExampleFailures` captured exceptions as **suppressed exceptions** on the final `AssertionError`. This allows programmatic access to failure details.

---

## 5. Configuration Overrides and Precedence

### 5.1 Override Mechanisms

| Source | Example | Scope |
|--------|---------|-------|
| Annotation | `@ProbabilisticTest(samples = 50)` | Per-method |
| System property | `-Dpunit.samples=200` | JVM-wide |
| Environment variable | `PUNIT_SAMPLES=200` | Process-wide |

### 5.2 Precedence Rules (Highest to Lowest)

1. **System property** (e.g., `-Dpunit.samples=10`) — highest priority
2. **Environment variable** (e.g., `PUNIT_SAMPLES=10`)
3. **Annotation value** (e.g., `samples = 100`)
4. **Framework default** (if annotation uses default)

**Rationale**: Runtime overrides take precedence to enable different configurations per environment (PR builds, nightly, local dev) without code changes.

### 5.3 Supported Override Properties

#### Method-Level Overrides

| Property | Env Var | Annotation Attr | Default |
|----------|---------|-----------------|---------|
| `punit.samples` | `PUNIT_SAMPLES` | `samples` | 100 |
| `punit.minPassRate` | `PUNIT_MIN_PASS_RATE` | `minPassRate` | 0.95 |
| `punit.timeBudgetMs` | `PUNIT_TIME_BUDGET_MS` | `timeBudgetMs` | 0 |
| `punit.tokenCharge` | `PUNIT_TOKEN_CHARGE` | `tokenCharge` | 0 |
| `punit.tokenBudget` | `PUNIT_TOKEN_BUDGET` | `tokenBudget` | 0 |

#### Suite-Level Budgets (System Properties / Env Vars Only)

| Property | Env Var | Description | Default |
|----------|---------|-------------|---------|
| `punit.suite.timeBudgetMs` | `PUNIT_SUITE_TIME_BUDGET_MS` | Max time for all probabilistic tests in JVM | 0 (unlimited) |
| `punit.suite.tokenBudget` | `PUNIT_SUITE_TOKEN_BUDGET` | Max tokens for all probabilistic tests in JVM | 0 (unlimited) |
| `punit.suite.onBudgetExhausted` | `PUNIT_SUITE_ON_BUDGET_EXHAUSTED` | Behavior when suite budget exhausted | `FAIL` |

**Note**: Suite-level budgets can only be configured via system properties or environment variables, not annotations (since they span multiple test classes).

### 5.4 Multiplier Override (Convenience)

Support a **multiplier** for samples to scale all tests uniformly:

- `punit.samplesMultiplier` / `PUNIT_SAMPLES_MULTIPLIER`
- Effective samples = `annotation.samples × multiplier`
- Default multiplier: 1.0

**Use case**: PR builds use multiplier 0.1 (10% samples), nightly uses 2.0 (200% samples).

---

## 6. JUnit 5 Integration Details

### 6.1 Why `@TestTemplate` (Not Custom TestEngine)

- **Least invasive**: Uses existing Jupiter extension points
- **Tooling compatibility**: IDEs, build tools, and CI servers understand Jupiter tests
- **Lifecycle preservation**: BeforeEach/AfterEach work naturally
- **Single logical test**: Template-based tests appear as one test with N invocations

A custom `TestEngine` would be overkill and would require reimplementing lifecycle, reporting, and parameter resolution.

### 6.2 TestTemplateInvocationContextProvider Implementation

```java
public class ProbabilisticTestExtension 
    implements TestTemplateInvocationContextProvider, 
               InvocationInterceptor,
               ParameterResolver,
               AfterAllCallback {

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod()
            .map(m -> m.isAnnotationPresent(ProbabilisticTest.class))
            .orElse(false);
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
            ExtensionContext context) {
        // Returns a Stream that:
        // 1. Lazily generates invocation contexts
        // 2. Can be short-circuited when early termination is triggered
        // 3. Each context carries the sample index and shared aggregator reference
    }

    // ParameterResolver for TokenChargeRecorder injection
    @Override
    public boolean supportsParameter(ParameterContext paramContext, ExtensionContext extContext) {
        return paramContext.getParameter().getType() == TokenChargeRecorder.class;
    }

    @Override
    public Object resolveParameter(ParameterContext paramContext, ExtensionContext extContext) {
        // Return the current sample's TokenChargeRecorder instance
        // (stored in ExtensionContext store, reset for each sample)
    }
}
```

### 6.3 TokenChargeRecorder Parameter Resolution

The `ProbabilisticTestExtension` implements `ParameterResolver` to support `TokenChargeRecorder` injection:

1. **Detection**: At test discovery, check if any `@ProbabilisticTest` method has a `TokenChargeRecorder` parameter
2. **Mode selection**: If parameter present → dynamic mode; otherwise → static mode
3. **Instance management**: Create fresh `DefaultTokenChargeRecorder` for each sample, linked to the shared `CostBudgetMonitor`
4. **Reset**: At the start of each sample, reset the recorder's per-sample counter to 0

### 6.4 Handling Invocation-Level Failures

The `InvocationInterceptor` catches exceptions during test execution:

```java
@Override
public void interceptTestMethod(Invocation<Void> invocation,
                                 ReflectiveInvocationContext<Method> context,
                                 ExtensionContext extensionContext) {
    try {
        invocation.proceed();
        aggregator.recordSuccess();
    } catch (AssertionError e) {
        aggregator.recordFailure(e);
        // Do NOT rethrow — sample failed, but overall test continues
    } catch (Throwable t) {
        // Handle based on ExceptionHandling policy
    } finally {
        // In dynamic mode: accumulate tokens recorded during this sample
        // (regardless of pass/fail outcome)
        if (tokenRecorder != null) {
            budgetMonitor.addTokens(tokenRecorder.getTokensForCurrentSample());
        }
    }
}
```

**Critical**: By catching the exception and not rethrowing, individual sample failures don't abort the overall test.

**Token handling on failure**: Tokens recorded via `TokenChargeRecorder` before an assertion failure are still counted. This is intentional—token consumption reflects actual resource usage, regardless of whether the sample passed or failed.

### 6.5 Preventing Individual Sample Failures in Reports

To prevent JUnit from marking each sample as failed/passed individually:

1. The `InvocationContext` can provide a custom `DisplayName` (e.g., "Sample 1/100")
2. The interceptor swallows assertion errors
3. Final verdict is thrown at end via `AfterTestExecutionCallback` or by checking termination in the stream

### 6.6 Single Logical Test Appearance

In test reports, the structure will appear as:

```
MyTest
└── myProbabilisticTest()
    ├── Sample 1/100 ✓
    ├── Sample 2/100 ✓
    ├── Sample 3/100 ✗ (swallowed)
    ├── ...
    └── [Final Verdict: PASS (97/100 = 97% ≥ 95%)]
```

**Alternative approach**: If individual sample visibility is undesirable, use a single invocation context that internally loops and suppresses per-sample reporting. This is a UX decision to finalize.

---

## 7. Exception Handling Semantics

### 7.1 AssertionError

- **Always**: Count as sample failure, continue execution
- Record the error for diagnostic reporting (up to `maxExampleFailures`)

### 7.2 Other Throwables

Based on `onException` configuration:

| Policy | Behavior |
|--------|----------|
| `FAIL_SAMPLE` (default) | Count as sample failure, continue execution |
| `ABORT` | Immediately fail entire test with the exception as cause |

### 7.3 Exceptions in @BeforeEach / @AfterEach

- These are **not** caught by PUNIT
- JUnit's standard behavior applies: the sample fails, lifecycle continues based on JUnit semantics
- Recommendation: Treat such failures as test infrastructure problems, not sample failures

---

## 8. Parallel Execution Considerations

### 8.1 v1 Recommendation: Disable Intra-Test Parallelism

For v1, samples within a single `@ProbabilisticTest` execute **sequentially**.

**Rationale**:
- Simplifies aggregator implementation (no concurrent writes)
- Early termination is straightforward (no orphan samples)
- Cost budget tracking is accurate
- Most stochastic tests (e.g., testing external services) may have rate limits

### 8.2 Inter-Test Parallelism

Parallel execution of **different** probabilistic test methods is **supported**. Each test method has its own independent aggregator and state.

### 8.3 v2: Configurable Intra-Test Parallelism

Future enhancement: `@ProbabilisticTest(parallelSamples = 4)` to run samples in parallel. Would require:
- Thread-safe aggregator (use `AtomicInteger`, `ConcurrentLinkedQueue`)
- Adjusted early termination (check after batch completion)
- Budget tracking across threads

---

## 9. Edge Cases and Validation

### 9.1 Parameter Validation

| Condition | Behavior |
|-----------|----------|
| `samples ≤ 0` | Throw `IllegalArgumentException` at test discovery |
| `samples = 1` | Valid; behaves like regular test but with pass rate reporting |
| `minPassRate < 0.0` | Throw `IllegalArgumentException` |
| `minPassRate > 1.0` | Throw `IllegalArgumentException` |
| `minPassRate = 0.0` | Valid; test always passes (all samples can fail) |
| `minPassRate = 1.0` | Valid; all samples must pass (fail-fast on first failure) |
| `timeBudgetMs < 0` | Throw `IllegalArgumentException` |
| `tokenCharge < 0` | Throw `IllegalArgumentException` |
| `tokenBudget < 0` | Throw `IllegalArgumentException` |
| `tokenCharge > 0, tokenBudget = 0` | Valid; tokens are tracked but not limited |
| `tokenCharge = 0, tokenBudget > 0` | Valid but no-op; budget never consumed (log warning) |
| `tokenCharge > tokenBudget` (both > 0) | Throw `IllegalArgumentException` (cannot run even 1 sample) |

### 9.2 Rounding Edge Cases

For `minPassRate = 1.0` with `samples = 100`:
- `requiredSuccesses = ceil(100 × 1.0) = 100`
- Any single failure triggers impossibility termination

For `minPassRate = 0.95` with `samples = 10`:
- `requiredSuccesses = ceil(10 × 0.95) = ceil(9.5) = 10`
- Effectively requires 100% pass rate for small sample sizes
- **Document this clearly**: small sample sizes with high pass rates may be stricter than expected

### 9.3 Empty/No Samples Executed

If early termination happens before any samples run (e.g., time budget already exceeded):
- Observed pass rate: 0.0 (or undefined)
- Verdict: FAIL
- Message: "No samples executed: {reason}"

---

## 10. Motivating Examples

### 10.1 Network Service Availability

```java
@ProbabilisticTest(samples = 100, minPassRate = 0.99)
void externalServiceIsAvailable() {
    Response response = httpClient.get("https://api.example.com/health");
    assertThat(response.statusCode()).isEqualTo(200);
}
```

*Tests that a service is available 99% of the time, accounting for transient network issues.*

### 10.2 Randomized Algorithm Correctness

```java
@ProbabilisticTest(samples = 1000, minPassRate = 1.0)
void quicksortAlwaysSorts() {
    int[] input = randomArray(100);
    int[] sorted = quicksort(input.clone());
    assertThat(sorted).isSorted();
}
```

*Fuzz testing with random inputs; expects 100% correctness.*

### 10.3 LLM Response Quality (One Example Among Many)

```java
@ProbabilisticTest(samples = 50, minPassRate = 0.90, timeBudgetMs = 60000)
void llmRespondsWithValidJson() {
    String prompt = "Generate a JSON object with fields: name, age, email";
    String response = llmClient.complete(prompt);
    assertThat(response).satisfies(JsonValidator::isValidJson);
}
```

*LLM outputs are inherently variable; 90% validity rate is acceptable.*

### 10.6 API Cost Control with Static Token Budget

```java
@ProbabilisticTest(
    samples = 100, 
    minPassRate = 0.90, 
    tokenCharge = 500,      // Each API call consumes ~500 tokens (estimated)
    tokenBudget = 25000,    // Max 25K tokens for this test
    onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL
)
void paidApiReturnsValidResponse() {
    String response = expensiveApiClient.query("test input");
    assertThat(response).isNotEmpty();
    assertThat(response).doesNotContain("error");
}
```

*Controls test costs when using paid APIs with predictable per-call costs. With 500 tokens/sample and 25K budget, at most 50 samples will run. If 45+ of those pass, the test succeeds.*

### 10.7 LLM Testing with Dynamic Token Charging

```java
@ProbabilisticTest(
    samples = 100, 
    minPassRate = 0.90, 
    tokenBudget = 50000,    // Max 50K tokens for this test
    onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL
)
void llmGeneratesValidJsonWithDynamicTokens(TokenChargeRecorder tokenRecorder) {
    String prompt = "Generate a JSON object with fields: name, age, email";
    
    // Call LLM - token consumption varies based on response length
    LlmResponse response = llmClient.complete(prompt);
    
    // Report actual tokens from response metadata
    Usage usage = response.getUsage();
    tokenRecorder.recordTokens(usage.getPromptTokens());
    tokenRecorder.recordTokens(usage.getCompletionTokens());
    
    // Alternatively, report total in one call:
    // tokenRecorder.recordTokens(usage.getTotalTokens());
    
    assertThat(response.getContent()).satisfies(JsonValidator::isValidJson);
}
```

*LLM responses have variable token consumption. By injecting `TokenChargeRecorder`, the test reports actual usage from the LLM's response metadata. The framework accumulates these and terminates when the 50K budget is reached.*

### 10.8 Streaming LLM with Progressive Token Recording

```java
@ProbabilisticTest(
    samples = 50, 
    minPassRate = 0.85, 
    tokenBudget = 100000
)
void streamingLlmProducesCoherentOutput(TokenChargeRecorder tokenRecorder) {
    StringBuilder output = new StringBuilder();
    AtomicInteger tokenCount = new AtomicInteger();
    
    llmClient.streamCompletion("Write a short story", chunk -> {
        output.append(chunk.getText());
        // Record tokens as they stream in
        if (chunk.getTokenCount() > 0) {
            tokenRecorder.recordTokens(chunk.getTokenCount());
        }
    });
    
    assertThat(output.toString())
        .hasSizeGreaterThan(100)
        .contains(".");  // Has at least one sentence
}
```

*For streaming APIs, tokens can be recorded progressively as chunks arrive. The `recordTokens()` method can be called multiple times per sample; values are summed.*

### 10.4 Race Condition Detection

```java
@ProbabilisticTest(samples = 500, minPassRate = 1.0)
void concurrentCounterIsThreadSafe() {
    Counter counter = new Counter();
    runConcurrently(10, () -> counter.increment());
    assertThat(counter.get()).isEqualTo(10);
}
```

*High sample count to detect intermittent race conditions.*

### 10.5 Hardware Sensor Reliability

```java
@ProbabilisticTest(samples = 100, minPassRate = 0.95)
void temperatureSensorReturnsValidReading() {
    double temp = sensor.readTemperature();
    assertThat(temp).isBetween(-40.0, 85.0);
}
```

*Physical sensors may occasionally return noise; 95% valid readings is acceptable.*

---

## 11. Implementation Plan (Phased)

| Phase | Status | Description |
|-------|--------|-------------|
| Phase 1 | ✅ Complete | Core Framework (MVP) |
| Phase 2 | ✅ Complete | Early Termination |
| Phase 3 | ✅ Complete | Configuration System |
| Phase 4 | ✅ Complete | Method-Level Cost Budget & Time Limits |
| Phase 5 | ✅ Complete | Budget Scopes (Class and Suite Levels) |
| Phase 6 | ✅ Complete | Enhanced Reporting & Diagnostics |
| Phase 7 | ✅ Complete | Exception Handling & Edge Cases |
| Phase 8 | ✅ Complete | Documentation & Polish |

---

### Phase 1: Core Framework (MVP) ✅

**Deliverables:**
1. `@ProbabilisticTest` annotation with `samples` and `minPassRate`
2. `ProbabilisticTestExtension` implementing `TestTemplateInvocationContextProvider`
3. `SampleResultAggregator` with basic pass/fail counting
4. `InvocationInterceptor` to catch and record assertion failures
5. `FinalVerdictDecider` with simple pass rate comparison
6. Basic failure message with statistics
7. `TestReporter` integration for structured output

**Acceptance Criteria:**
- Test executes N times
- Each sample's assertion failure is recorded, not thrown
- Final verdict based on pass rate comparison
- Report includes all required statistics

**Estimated Effort:** 2-3 days

---

### Phase 2: Early Termination ✅

**Deliverables:**
1. `EarlyTerminationEvaluator` with impossibility check
2. Stream short-circuiting in invocation provider
3. `TerminationReason` enum and reporting integration
4. Update failure message to include termination cause

**Acceptance Criteria:**
- Test terminates early when success becomes impossible
- Termination reason is clearly reported
- No unnecessary samples execute after termination

**Estimated Effort:** 1-2 days

---

### Phase 3: Configuration System ✅

**Deliverables:**
1. `ConfigurationResolver` with precedence logic
2. System property support (`punit.samples`, `punit.minPassRate`)
3. Environment variable support
4. `punit.samplesMultiplier` for scaling
5. Validation and error messages for invalid configurations

**Acceptance Criteria:**
- Overrides work at all levels
- Precedence is correct
- Invalid values produce clear errors at discovery time

**Estimated Effort:** 1 day

---

### Phase 4: Method-Level Cost Budget & Time Limits ✅

**Deliverables:**
1. `CostBudgetMonitor` implementation (wall-clock time + token accumulation)
2. `timeBudgetMs` annotation parameter
3. `tokenCharge` and `tokenBudget` annotation parameters
4. `TokenChargeRecorder` interface and `DefaultTokenChargeRecorder` implementation
5. JUnit 5 `ParameterResolver` for injecting `TokenChargeRecorder` into test methods
6. `onBudgetExhausted` behavior options
7. Static mode: Pre-sample budget checks integrated into sample loop
8. Dynamic mode: Post-sample token accumulation with recorder
9. Mode detection logic (check for TokenChargeRecorder parameter)
10. Reporting integration for budget-related termination (both time and token)

**Acceptance Criteria:**
- Tests terminate when method-level time budget exceeded
- Static mode: Tests terminate when next sample would exceed token budget (pre-check)
- Dynamic mode: Tests terminate after sample causes budget to be exceeded (post-check)
- Token consumption is accurately tracked and reported in both modes
- `TokenChargeRecorder` is injectable and supports multiple `recordTokens()` calls per sample
- Behavior matches `onBudgetExhausted` setting for both budget types
- Budget exhaustion reason (time vs token) is clearly reported
- Mode is reported (`STATIC`, `DYNAMIC`, or `NONE`)

**Estimated Effort:** 3-4 days

---

### Phase 5: Budget Scopes (Class and Suite Levels) ✅

**Deliverables:**
1. `@ProbabilisticTestBudget` class-level annotation
2. `ProbabilisticTestBudgetExtension` for class-level budget management
3. `SharedBudgetMonitor` thread-safe implementation for shared budgets
4. `SuiteBudgetManager` singleton for suite-level state
5. Suite-level system property parsing (`punit.suite.*`)
6. Budget scope precedence logic (suite → class → method)
7. Consumption propagation to all active scopes
8. Extended `TerminationReason` enum with scoped values
9. Reporting integration for scoped budgets
10. Thread-safe budget tracking for parallel test execution

**Acceptance Criteria:**
- Class-level `@ProbabilisticTestBudget` correctly shares budget across methods
- Suite-level budgets from system properties are respected
- All scopes are checked in correct order (suite first, then class, then method)
- Consumption in any method is reflected in all enclosing scopes
- Termination reason correctly identifies which scope triggered it
- Thread-safe operation when tests run in parallel
- Reports include scope information when relevant

**Estimated Effort:** 3-4 days

---

### Phase 6: Enhanced Reporting & Diagnostics ✅

*Note: These features were implemented as part of earlier phases.*

**Deliverables:**
1. `maxExampleFailures` parameter
2. Example failure collection in aggregator
3. Suppressed exceptions on final `AssertionError`
4. Enhanced failure message with example failures
5. Per-sample display names (e.g., "Sample 3/100")

**Acceptance Criteria:**
- Example failures are captured and reported
- Failure message is human-readable and actionable
- Suppressed exceptions are programmatically accessible

**Estimated Effort:** 1 day

---

### Phase 7: Exception Handling & Edge Cases ✅

*Note: These features were implemented as part of earlier phases.*

**Deliverables:**
1. `onException` parameter and handling logic
2. Parameter validation at discovery time
3. Edge case handling (samples=1, minPassRate=1.0, etc.)
4. Documentation of rounding behavior

**Acceptance Criteria:**
- Non-assertion exceptions handled per policy
- Invalid parameters fail at discovery with clear messages
- Edge cases behave as documented

**Estimated Effort:** 1 day

---

### Phase 8: Documentation & Polish ✅

**Deliverables:**
1. Javadoc for all public API
2. README with quick start guide
3. Examples in test sources
4. Migration guide (for teams adopting PUNIT)

**Estimated Effort:** 1-2 days

---

### Total Estimated Effort: 13-21 days

---

## 12. Test Strategy

### 12.1 Unit Tests for Extension Components

| Component | Test Cases |
|-----------|------------|
| `ConfigurationResolver` | Precedence rules, default values, validation, multiplier math, token config validation, suite property parsing |
| `SampleResultAggregator` | Counting correctness, thread safety (v2), example failure capture, token accumulation |
| `EarlyTerminationEvaluator` | Impossibility math, boundary conditions, time budget checks, token budget checks (static pre-check, dynamic post-check), scope-aware budget checks |
| `TokenChargeRecorder` | Multiple recordTokens() calls accumulate, reset between samples, negative value rejection, getRemainingBudget accuracy |
| `SharedBudgetMonitor` | Thread safety, concurrent consumption updates, budget propagation, remaining budget calculation across methods |
| `SuiteBudgetManager` | Singleton behavior, system property initialization, thread-safe consumption tracking |
| `FinalVerdictDecider` | Pass rate calculation, rounding, threshold comparison |
| `ReportPublisher` | Output format, key names, value formatting, token statistics formatting, mode and scope reporting |

### 12.2 Integration Tests

**Passing Tests:**
```java
@ProbabilisticTest(samples = 10, minPassRate = 0.8)
void alwaysPasses() {
    assertThat(true).isTrue();
}
// Expected: 10/10 = 100% ≥ 80% → PASS
```

**Failing Tests (Below Threshold):**
```java
@ProbabilisticTest(samples = 10, minPassRate = 0.8)
void failsHalfTheTime() {
    assertThat(Math.random() > 0.5).isTrue();
}
// Expected: ~50% < 80% → FAIL
```

**Fail-Fast Tests:**
```java
@ProbabilisticTest(samples = 100, minPassRate = 0.95)
void alwaysFails() {
    fail("Always fails");
}
// Expected: Terminates after 6 samples (ceil(100 * 0.95) = 95 required, 
//           0 + 94 < 95 after 6 failures)
```

**Time Budget Exhaustion Tests:**
```java
@ProbabilisticTest(samples = 1000, minPassRate = 0.5, timeBudgetMs = 100)
void slowTest() {
    Thread.sleep(50);
    assertThat(true).isTrue();
}
// Expected: Time budget exhausted after ~2 samples, fails with TIME_BUDGET_EXHAUSTED reason
```

**Token Budget Exhaustion Tests:**
```java
@ProbabilisticTest(samples = 100, minPassRate = 0.8, tokenCharge = 100, tokenBudget = 500)
void tokenLimitedTest() {
    assertThat(true).isTrue();
}
// Expected: Runs 5 samples (5 × 100 = 500 tokens), 6th sample would exceed budget
//           With 5/5 = 100% pass rate ≥ 80%, but FAIL policy → fails with TOKEN_BUDGET_EXHAUSTED
```

**Token Budget with EVALUATE_PARTIAL:**
```java
@ProbabilisticTest(
    samples = 100, 
    minPassRate = 0.8, 
    tokenCharge = 100, 
    tokenBudget = 500,
    onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL
)
void tokenLimitedPartialTest() {
    assertThat(true).isTrue();
}
// Expected: Runs 5 samples, 100% pass rate ≥ 80% → PASS (verdict based on completed samples)
```

**Dynamic Token Charging Tests:**
```java
@ProbabilisticTest(samples = 10, minPassRate = 0.8, tokenBudget = 500)
void dynamicTokenTest(TokenChargeRecorder recorder) {
    recorder.recordTokens(100);  // Fixed 100 tokens per sample
    assertThat(true).isTrue();
}
// Expected: Runs 5 samples (500 tokens), then terminates on 6th attempt
//           With FAIL policy → fails with TOKEN_BUDGET_EXHAUSTED
```

**Dynamic Token with Variable Consumption:**
```java
@ProbabilisticTest(samples = 100, minPassRate = 0.9, tokenBudget = 1000)
void variableTokenTest(TokenChargeRecorder recorder) {
    int tokens = randomBetween(50, 200);  // Variable consumption
    recorder.recordTokens(tokens);
    assertThat(true).isTrue();
}
// Expected: Number of samples varies based on random consumption
//           Terminates when cumulative tokens exceed 1000
```

**Multiple recordTokens() Calls:**
```java
@ProbabilisticTest(samples = 10, minPassRate = 1.0, tokenBudget = 500)
void multipleRecordCallsTest(TokenChargeRecorder recorder) {
    recorder.recordTokens(30);  // Input tokens
    recorder.recordTokens(70);  // Output tokens
    // Total: 100 per sample
    assertThat(true).isTrue();
}
// Expected: Runs 5 samples (5 × 100 = 500), terminates after 5th
```

**Class-Level Budget Tests:**
```java
@ProbabilisticTestBudget(tokenBudget = 500)
class ClassBudgetTest {
    @ProbabilisticTest(samples = 10, minPassRate = 1.0, tokenCharge = 100)
    void test1() { assertThat(true).isTrue(); }
    // Consumes 1000 tokens if all 10 run, but class limit is 500
    // Expected: Runs 5 samples, then CLASS_TOKEN_BUDGET_EXHAUSTED

    @ProbabilisticTest(samples = 10, minPassRate = 1.0, tokenCharge = 100)  
    void test2() { assertThat(true).isTrue(); }
    // Expected: If test1 used 500, test2 has 0 budget remaining
    //           Terminates immediately with CLASS_TOKEN_BUDGET_EXHAUSTED
}
```

**Mixed Method and Class Budget Tests:**
```java
@ProbabilisticTestBudget(tokenBudget = 1000)
class MixedBudgetTest {
    @ProbabilisticTest(samples = 20, minPassRate = 1.0, tokenCharge = 100, tokenBudget = 500)
    void methodLimitedTest() { assertThat(true).isTrue(); }
    // Expected: Runs 5 samples (method limit 500 < class limit 1000)
    //           Terminates with METHOD_TOKEN_BUDGET_EXHAUSTED
    //           Class budget now has 500 remaining
    
    @ProbabilisticTest(samples = 20, minPassRate = 1.0, tokenCharge = 100)
    void classLimitedTest() { assertThat(true).isTrue(); }
    // Expected: Runs 5 samples (500 remaining from class budget)
    //           Terminates with CLASS_TOKEN_BUDGET_EXHAUSTED
}
```

**Suite-Level Budget Tests:**
```java
// Run with: -Dpunit.suite.tokenBudget=300
@ProbabilisticTest(samples = 10, minPassRate = 1.0, tokenCharge = 100)
void suiteLimitedTest() { assertThat(true).isTrue(); }
// Expected: Runs 3 samples (suite limit 300)
//           Terminates with SUITE_TOKEN_BUDGET_EXHAUSTED
```

### 12.3 Configuration Override Tests

- Test system property overrides annotation
- Test environment variable as fallback
- Test multiplier scaling
- Test invalid override values

### 12.4 Reporting Verification Tests

- Capture `TestReporter` output and verify structure
- Verify failure message format
- Verify suppressed exceptions are attached

### 12.5 Performance Tests

- Measure overhead per sample (should be < 1ms)
- Verify early termination actually saves time
- Test with high sample counts (10,000+)

---

## 13. Open Questions and Recommended Defaults

| Question | Recommendation |
|----------|----------------|
| **Should individual samples appear in test reports?** | **Yes, but collapsed by default**. Show "Sample N/M" for each invocation. IDEs can collapse these. Provides debugging visibility. |
| **What happens if @BeforeEach throws?** | **Let JUnit handle it normally**. PUNIT does not intercept lifecycle methods. Sample is marked as failed by JUnit, which PUNIT observes. |
| **Should there be a "warm-up" sample count?** | **No, not in v1**. Keep it simple. Users can handle warm-up in test setup. v2 could add `warmupSamples` parameter. |
| **How to handle @ParameterizedTest-style inputs?** | **Out of scope for v1**. Probabilistic tests focus on repeated execution of same logic. Combining with parameterized tests is a v2 feature. |
| **Should pass rate be "greater than" or "greater than or equal"?** | **Greater than or equal (≥)**. More intuitive: 95% pass rate means 95% is acceptable. |
| **Should observed pass rate round to N decimal places?** | **No rounding in comparison**. Use exact double arithmetic. Round only for display (2 decimal places). |
| **Maximum samples limit?** | **No hard limit, but document performance implications**. Recommend < 10,000 for CI. |
| **What if test has TokenChargeRecorder but never calls recordTokens()?** | **Treat as 0 tokens for that sample**. This is valid (test may conditionally consume tokens). No warning needed. |
| **Should TokenChargeRecorder be thread-safe?** | **No, for v1**. Samples execute sequentially; recorder is reset between samples. v2 parallel execution would require thread-safe implementation. |
| **What if recordTokens() is called after sample assertion fails?** | **Tokens are still counted**. The recorder captures all tokens consumed, regardless of assertion outcome. Token consumption is orthogonal to pass/fail. |
| **How is class-level budget time tracked?** | **Wall-clock time from first sample of first method to current sample**. Time includes gaps between methods. This reflects real elapsed time, not just test execution time. |
| **What if class-level and method-level budgets conflict?** | **Both are enforced independently**. The more restrictive budget wins. No error is raised for having both; they compose naturally. |
| **What happens to remaining tests when class budget is exhausted?** | **They are skipped with a clear message**. JUnit reports them as failed (or skipped, based on `onBudgetExhausted`) with reason indicating class budget exhaustion. |
| **Is suite budget reset between test classes?** | **No**. Suite budget spans the entire JVM execution. It is designed for CI scenarios where you want to limit total resource usage across all tests. |
| **How does parallel test execution interact with shared budgets?** | **Thread-safe monitors are used**. `SharedBudgetMonitor` and `SuiteBudgetManager` use atomic operations. Consumption from parallel tests is correctly accumulated. |

---

## 14. v1 Scope vs v2 Enhancements

### v1 Scope (MVP)

✅ `@ProbabilisticTest` annotation  
✅ `samples` and `minPassRate` parameters  
✅ Sequential sample execution  
✅ Early termination by impossibility  
✅ Wall-clock time budget (`timeBudgetMs`)  
✅ Static token budget (`tokenCharge`, `tokenBudget`)  
✅ Dynamic token charging via injectable `TokenChargeRecorder`  
✅ Budget scopes: method, class (`@ProbabilisticTestBudget`), and suite levels  
✅ System property / env var overrides (including suite-level budgets)  
✅ Structured reporting via `TestReporter`  
✅ Clear failure messages with statistics (including token consumption, mode, and scope)  
✅ Example failure capture  
✅ Basic exception handling policy  

### v2 Enhancements (Future)

⏳ **Parallel sample execution** (`parallelSamples` parameter)  
⏳ **Custom cost models** (SPI for money, API calls, dynamic token measurement)  
⏳ **Warm-up samples** (excluded from statistics)  
⏳ **Per-invocation timeout** (distinct from overall budget)  
⏳ **Retry policy** (retry failed samples N times before counting as failure)  
⏳ **Combined with @ParameterizedTest** (run probabilistic test for each parameter set)  
⏳ **Statistical confidence intervals** (report confidence bounds, not just point estimate)  
⏳ **Adaptive sampling** (stop early when statistically confident of pass/fail)  
⏳ **JUnit XML/HTML report integration** (custom reporter for richer output)  
⏳ **Flakiness tracking** (track pass rates over time across runs)  
⏳ **Gradle/Maven plugin** (configure defaults in build file)  
⏳ **Automatic token measurement** (SPI for intercepting LLM client calls to auto-capture tokens without explicit recordTokens() calls)  

---

## 15. Appendix: Terminology Glossary

| Term | Definition |
|------|------------|
| **Sample** | A single invocation of the test method |
| **Observed pass rate** | `successes / samplesExecuted` |
| **Minimum pass rate** | Threshold for overall test success |
| **Required successes** | `ceil(samples × minPassRate)` |
| **Impossibility termination** | Early stop when success is mathematically unreachable |
| **Cost budget** | Resource limit (v1: wall-clock time, token count) for all samples |
| **Token charge (static)** | Fixed number of tokens consumed per sample, declared in annotation |
| **Token charge (dynamic)** | Variable tokens reported per sample via `TokenChargeRecorder` |
| **Token budget** | Maximum total tokens allowed across all samples |
| **Tokens consumed** | Running total of tokens used (static: `samplesExecuted × tokenCharge`, dynamic: sum of recorded values) |
| **TokenChargeRecorder** | Injectable interface for test methods to report actual token consumption |
| **Budget scope** | The level at which a budget is applied: METHOD, CLASS, or SUITE |
| **Method-level budget** | Budget that applies only to a single `@ProbabilisticTest` method |
| **Class-level budget** | Shared budget across all `@ProbabilisticTest` methods in a test class |
| **Suite-level budget** | Shared budget across all probabilistic tests in the JVM/test run |
| **Verdict** | Final binary outcome: PASS or FAIL |

---

## 16. Appendix: Example Test Report Output

### Passing Test

```
┌─────────────────────────────────────────────────────────────────┐
│ TEST: MyServiceTest.serviceReturnsValidResponse                 │
├─────────────────────────────────────────────────────────────────┤
│ Status: PASSED                                                  │
│ Samples: 100/100 executed                                       │
│ Successes: 98                                                   │
│ Failures: 2                                                     │
│ Observed Pass Rate: 98.00%                                      │
│ Required Pass Rate: 95.00%                                      │
│ Elapsed: 2341ms                                                 │
│ Tokens: 10000/50000 (100 per sample)                            │
│ Termination: COMPLETED                                          │
└─────────────────────────────────────────────────────────────────┘
```

### Failing Test (Impossibility)

```
┌─────────────────────────────────────────────────────────────────┐
│ TEST: MyServiceTest.serviceReturnsValidResponse                 │
├─────────────────────────────────────────────────────────────────┤
│ Status: FAILED                                                  │
│ Samples: 23/100 executed                                        │
│ Successes: 12                                                   │
│ Failures: 11                                                    │
│ Observed Pass Rate: 52.17%                                      │
│ Required Pass Rate: 95.00%                                      │
│ Elapsed: 456ms                                                  │
│ Tokens: 2300/50000 (100 per sample)                             │
│ Termination: IMPOSSIBILITY                                      │
│                                                                 │
│ Reason: After 23 samples with 12 successes, maximum possible    │
│         successes (12 + 77 = 89) is less than required (95).    │
│                                                                 │
│ Example failures (3 of 11):                                     │
│   [Sample 2] AssertionError: Expected status 200 but was 503    │
│   [Sample 5] AssertionError: Response body was empty            │
│   [Sample 8] AssertionError: Expected JSON but got HTML         │
└─────────────────────────────────────────────────────────────────┘
```

### Failing Test (Method-Level Token Budget Exhausted)

```
┌─────────────────────────────────────────────────────────────────┐
│ TEST: ExpensiveApiTest.apiReturnsValidData                      │
├─────────────────────────────────────────────────────────────────┤
│ Status: FAILED                                                  │
│ Samples: 50/100 executed                                        │
│ Successes: 48                                                   │
│ Failures: 2                                                     │
│ Observed Pass Rate: 96.00%                                      │
│ Required Pass Rate: 90.00%                                      │
│ Elapsed: 12453ms                                                │
│ Tokens: 25000/25000 (500 per sample)                            │
│ Termination: METHOD_TOKEN_BUDGET_EXHAUSTED                      │
│                                                                 │
│ Reason: Token budget (25000) would be exceeded by next sample   │
│         (current: 25000, next charge: 500).                     │
│                                                                 │
│ Note: Observed pass rate (96.00%) meets requirement (90.00%),   │
│       but onBudgetExhausted=FAIL forces test failure.           │
└─────────────────────────────────────────────────────────────────┘
```

### Failing Test (Class-Level Budget Exhausted)

```
┌─────────────────────────────────────────────────────────────────┐
│ TEST: LlmTestSuite.secondTestMethod                             │
├─────────────────────────────────────────────────────────────────┤
│ Status: FAILED                                                  │
│ Samples: 12/50 executed                                         │
│ Successes: 11                                                   │
│ Failures: 1                                                     │
│ Observed Pass Rate: 91.67%                                      │
│ Required Pass Rate: 90.00%                                      │
│ Elapsed: 8234ms                                                 │
│                                                                 │
│ Budget Scope: CLASS                                             │
│ Method Tokens: 6000 (500 per sample)                            │
│ Class Tokens: 15000/15000 (shared across 2 methods)             │
│                                                                 │
│ Termination: CLASS_TOKEN_BUDGET_EXHAUSTED                       │
│                                                                 │
│ Reason: Class-level token budget (15000) exhausted.             │
│         Previous methods consumed: 9000 tokens                  │
│         This method consumed: 6000 tokens                       │
│         Next sample charge (500) would exceed remaining (0).    │
│                                                                 │
│ Note: Observed pass rate (91.67%) meets requirement (90.00%),   │
│       but onBudgetExhausted=FAIL forces test failure.           │
└─────────────────────────────────────────────────────────────────┘
```

### Suite-Level Budget Status (Informational)

```
┌─────────────────────────────────────────────────────────────────┐
│ SUITE BUDGET STATUS (after all tests)                           │
├─────────────────────────────────────────────────────────────────┤
│ Suite Time Budget: 45230ms / 300000ms (15.1% used)              │
│ Suite Token Budget: 87500 / 100000 (87.5% used)                 │
│                                                                 │
│ Tests terminated by suite budget: 0                             │
│ Remaining capacity: 12500 tokens, 254770ms                      │
└─────────────────────────────────────────────────────────────────┘
```

---

*End of Implementation Plan*

