# Getting Started with PUnit

*Experimentation and statistical regression testing for systems under uncertainty*

Get up and running with PUnit in minutes. This guide covers installation and your first steps with both **experimentation** and **testing**.

All attribution licensing is ARL.

---

## What is PUnit?

PUnit is a **dual-purpose platform** for systems characterized by uncertainty:

| Capability          | What It Does                                           | When to Use                            |
|---------------------|--------------------------------------------------------|----------------------------------------|
| **Experimentation** | Discover how your system behaves across configurations | Before you know what "good" looks like |
| **Testing**         | Verify behavior hasn't regressed                       | After you've established a baseline    |

The two are connected: experiments generate the empirical data that powers **spec-driven tests**—the most rigorous form of probabilistic testing.

---

## Prerequisites

- **Java 21+** or **Kotlin 1.9+**
- **Gradle 9.3.1+** or **Maven 3.9+**
- **JUnit 5.10+**

---

## Installation

PUnit is available on [Maven Central](https://central.sonatype.com/artifact/org.javai/punit). Add the dependency and the Gradle plugin to your project:

### Gradle (Kotlin DSL)

```kotlin
plugins {
    id("org.javai.punit") version "0.1.0"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.javai:punit:0.1.0")
}
```

### Gradle (Groovy DSL)

```groovy
plugins {
    id 'org.javai.punit' version '0.1.0'
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation 'org.javai:punit:0.1.0'
}
```

The `org.javai.punit` Gradle plugin automatically:
- Registers `experiment` and `exp` tasks for running experiments (see [Your First Experiment](#your-first-experiment))
- Configures the `test` task to exclude experiment-tagged tests from normal test runs
- Forwards `punit.*` system properties to the test JVM
- Supports `-Prun=` shorthand for filtering tests and experiments

### Maven

```xml
<dependency>
    <groupId>org.javai</groupId>
    <artifactId>punit</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>
```

Maven users need manual Surefire/Failsafe configuration for experiment tasks. See [MAVEN-CONFIGURATION.md](MAVEN-CONFIGURATION.md) for complete setup instructions.

---

## Your First Probabilistic Test

Create a test that passes if at least 80% of samples succeed. By default, PUnit assumes `intent = TestIntent.VERIFICATION`, which means it will enforce that your configuration is statistically sound. For quick early-warning checks, you can use `intent = TestIntent.SMOKE`.

```java
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.TestIntent;
import static org.assertj.core.api.Assertions.assertThat;

class MyFirstProbabilisticTest {

    @ProbabilisticTest(
        samples = 20, 
        minPassRate = 0.80,
        intent = TestIntent.SMOKE
    )
    void myServiceShouldUsuallyWork() {
        // Call your code characterized by uncertainty
        String result = myService.generateResponse("Hello");
        
        // Assert what "success" means
        assertThat(result).isNotBlank();
    }
}
```

Run with:

```bash
./gradlew test
```

PUnit will:
1. Execute your test 20 times
2. Track successes and failures
3. Pass if ≥80% of samples succeed
4. Terminate early if success is guaranteed or impossible

---

## Your First Experiment

Before writing tests, you often need to **discover** how your system behaves under different configurations before you
find a configuration which you can test against. PUnit's experiment modes help you explore the configuration space:

### EXPLORE: Compare Configurations

```java
@TestTemplate
@ExploreExperiment(
    useCase = MyUseCase.class,
    samplesPerConfig = 20,
    experimentId = "model-comparison-v1"
)
@FactorSource(value = "modelConfigs", factors = {"model", "temperature"})
void exploreModels(
        MyUseCase useCase,
        @Factor("model") String model,
        @Factor("temperature") Double temp,
        ResultCaptor captor) {

    useCase.configure(model, temp);
    captor.record(useCase.execute("test input"));
}

static List<FactorArguments> modelConfigs() {
    return FactorArguments.configurations()
        .names("model", "temperature")
        .values("gpt-4", 0.0)
        .values("gpt-4", 0.7)
        .values("gpt-3.5-turbo", 0.0)
        .stream().toList();
}
```

Run with:
```bash
./gradlew exp -Prun=MyExperiment.exploreModels
```

Output: One YAML file per configuration in `src/test/resources/punit/explorations/`

### OPTIMIZE: Tune a Factor

After exploring configurations, you may want to fine-tune a specific factor:

```java
@OptimizeExperiment(
    useCase = MyUseCase.class,
    controlFactor = "temperature",
    initialControlFactorSource = "startingTemperature",
    scorer = MySuccessRateScorer.class,
    mutator = TemperatureMutator.class,
    objective = OptimizationObjective.MAXIMIZE,
    samplesPerIteration = 20,
    maxIterations = 10
)
void optimizeTemperature(
        MyUseCase useCase,
        @ControlFactor("temperature") Double temperature,
        ResultCaptor captor) {
    captor.record(useCase.execute("test input"));
}

static Double startingTemperature() {
    return 1.0;  // Start high, optimize down
}
```

Run with:
```bash
./gradlew exp -Prun=MyExperiment.optimizeTemperature
```

Output: Optimization history at `src/test/resources/punit/optimizations/`

### MEASURE: Establish Baseline

Once you've chosen a configuration, measure it thoroughly:

```java
@MeasureExperiment(useCase = MyUseCase.class, samples = 1000)
void measureChosenConfig(MyUseCase useCase, ResultCaptor captor) {
    captor.record(useCase.execute("production input"));
}
```

Run with:
```bash
./gradlew exp -Prun=MyExperiment.measureChosenConfig
```

Output: A spec file at `src/test/resources/punit/specs/{UseCaseId}.yaml` — commit this to Git.

---

## The Complete Picture

```
EXPLORE (discover) → OPTIMIZE (tune) → MEASURE (baseline) → Test (verify)
```

Experimentation and testing are two halves of the same workflow. Specs bridge them: experiments generate specs, tests consume them.

---

## Running with Real LLMs

By default, PUnit examples use mock LLMs for fast, free, deterministic results. To run experiments with actual LLM providers:

### Configuration

```bash
# Enable real mode
export PUNIT_LLM_MODE=real

# Provide API keys for the providers you'll use
export OPENAI_API_KEY=sk-...           # For gpt-* models
export ANTHROPIC_API_KEY=sk-ant-...    # For claude-* models

# Run experiments
./gradlew exp -Prun=ShoppingBasketExplore.compareModels
```

### Model Routing

In real mode, the model name determines which provider handles the request:

| Model Pattern           | Provider  | Examples                                                  |
|-------------------------|-----------|-----------------------------------------------------------|
| `gpt-*`, `o1-*`, `o3-*` | OpenAI    | `gpt-4o-mini`, `gpt-4o`, `o1-preview`                     |
| `claude-*`              | Anthropic | `claude-haiku-4-5-20251001`, `claude-sonnet-4-5-20250929` |

### Cost Awareness

> **Warning**: Real LLM APIs cost money. A MEASURE experiment with 1000 samples can cost several dollars. Start with mock mode or small sample sizes to verify your setup before running large experiments.
>
> Use `tokenBudget` or `timeBudgetMs` constraints to cap costs.

---

## Next Steps

| Document                                          | Description                                                     |
|---------------------------------------------------|-----------------------------------------------------------------|
| [README](../README.md)                            | Project overview, philosophy, and quick reference               |
| [USER-GUIDE](USER-GUIDE.md)                       | Complete guide: experimentation, testing, and advanced features |
| [STATISTICAL-COMPANION](STATISTICAL-COMPANION.md) | Mathematical foundations for the curious                        |

---

## Quick Examples

### With Token Budget

```java
@ProbabilisticTest(
    samples = 100,
    minPassRate = 0.90,
    tokenBudget = 50000  // Stop if tokens exceed 50k
)
void llmTest(TokenChargeRecorder recorder) {
    LlmResponse response = llmClient.complete("Generate JSON");
    recorder.recordTokens(response.getTokensUsed());
    
    assertThat(response.getContent()).contains("{");
}
```

### With Rate Limiting

```java
@ProbabilisticTest(samples = 60, minPassRate = 0.85)
@Pacing(maxRequestsPerMinute = 60)  // Respect API rate limits
void rateLimitedApiTest() {
    // PUnit spaces requests ~1 second apart
    var result = externalApi.call();
    assertThat(result.isValid()).isTrue();
}
```

### Spec-Driven (After Running Experiments)

```java
@ProbabilisticTest(
    useCase = MyUseCase.class,  // Threshold derived from spec
    samples = 50
)
void specDrivenTest() {
    // minPassRate automatically derived from empirical baseline
    var result = myService.process();
    assertThat(result.isSuccess()).isTrue();
}
```

---

## Getting Help

- **Issues**: [GitHub Issues](https://github.com/javai-org/punit/issues)
- **Discussions**: [GitHub Discussions](https://github.com/javai-org/punit/discussions)

---

## Project Status

PUnit is in **early development** (v0.1.0). The API may evolve based on community feedback. We welcome:

- Bug reports
- Feature requests
- Use case descriptions
- Pull requests

Your feedback shapes the project's direction.

