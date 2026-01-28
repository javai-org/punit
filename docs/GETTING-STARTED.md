# Getting Started with PUnit

*Experimentation and statistical regression testing for non-deterministic systems*

Get up and running with PUnit in minutes. This guide covers installation and your first steps with both **experimentation** and **testing**.

---

## What is PUnit?

PUnit is a **dual-purpose platform** for non-deterministic systems:

| Capability | What It Does | When to Use |
|------------|--------------|-------------|
| **Experimentation** | Discover how your system behaves across configurations | Before you know what "good" looks like |
| **Testing** | Verify behavior hasn't regressed | After you've established a baseline |

The two are connected: experiments generate the empirical data that powers **spec-driven tests**—the most rigorous form of probabilistic testing.

---

## Prerequisites

- **Java 17+** or **Kotlin 1.8+**
- **Gradle 8.x** or **Maven 3.8+**
- **JUnit 5.10+**

---

## Installation

PUnit is available via [JitPack](https://jitpack.io). Add it to your project:

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    testImplementation("com.github.javai-org:punit:0.1.0")
}
```

### Gradle (Groovy DSL)

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    testImplementation 'com.github.javai-org:punit:0.1.0'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.javai-org</groupId>
        <artifactId>punit</artifactId>
        <version>0.1.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## Your First Probabilistic Test

Create a test that passes if at least 80% of samples succeed:

```java
import org.javai.punit.api.ProbabilisticTest;
import static org.assertj.core.api.Assertions.assertThat;

class MyFirstProbabilisticTest {

    @ProbabilisticTest(samples = 20, minPassRate = 0.80)
    void myServiceShouldUsuallyWork() {
        // Call your non-deterministic code
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

Before writing tests, you often need to **discover** how your system behaves. PUnit's experiment modes help you explore and measure:

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

## Next Steps

| Document | Description |
|----------|-------------|
| [README](../README.md) | Project overview, philosophy, and quick reference |
| [USER-GUIDE](USER-GUIDE.md) | Complete guide: experimentation, testing, and advanced features |
| [STATISTICAL-COMPANION](STATISTICAL-COMPANION.md) | Mathematical foundations for the curious |

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

