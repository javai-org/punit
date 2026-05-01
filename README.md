# PUnit: The Experimentation and Probabilistic Testing Framework

---

## Governance and Sponsorship Transparency

<p align="center">
  <a href="https://karakun.com">
    <img src="media/karakun-logo.PNG" alt="Karakun" width="220"/>
  </a>
</p>

PUnit is proudly sponsored by [Karakun](https://karakun.com), a Swiss software engineering consultancy specialising in scalable systems, AI, and enterprise-grade solutions.

## Independence

All technical decisions within PUnit are made independently of its sponsor.

This includes:
- Statistical models and assumptions
- Test semantics and verdict logic
- API design and framework behaviour

## Rationale

PUnit aims to provide a neutral and rigorous approach to testing stochastic systems. Maintaining independence from commercial influence is essential to its credibility, particularly in regulated environments such as finance and healthcare.

---


## Why PUnit?

PUnit brings statistical rigor to testing non-deterministic systems...

*Experimentation and unit testing at certainty's boundary*

[![Java 21+](https://img.shields.io/badge/Java-21%2B-blue.svg)](https://openjdk.org/)
[![JUnit 5](https://img.shields.io/badge/JUnit-5.13%2B-green.svg)](https://junit.org/junit5/)

Some systems do not yield the same outcome on every run — LLMs, ML models, randomized algorithms, network-dependent services. A conventional unit test pretends certainty: one run, one verdict. PUnit makes the boundary explicit: it turns repeated samples into statistical evidence, and reports how strong that evidence is.

## Features

### Experimentation

| Mode            | Description                                                                  |
|-----------------|------------------------------------------------------------------------------|
| **EXPLORE**     | Compare the impact of different configurations with minimal samples          |
| **OPTIMIZE**    | Auto-tune a factor iteratively to find the optimal value for production      |
| **MEASURE**     | Generate an empirical baseline spec to power probabilistic tests             |

### Testing

| Feature                    | Description                                                          |
|----------------------------|----------------------------------------------------------------------|
| **Normative thresholds**   | Test against SLA/SLO/policy thresholds with provenance tracking      |
| **Spec-driven thresholds** | Derive pass/fail thresholds from empirical data, not guesswork       |
| **Early termination**      | Stop early when failure is inevitable or success is guaranteed       |
| **Budget control**         | Time and token budgets at method, class, or suite level              |
| **Pacing constraints**     | Declare API rate limits; framework computes optimal execution pace   |
| **Latency assertions**     | Per-percentile latency thresholds (p50, p90, p95, p99)               |
| **HTML report**            | Standalone report with per-test statistical detail                   |
| **Sentinel**               | JUnit-free engine for probabilistic testing in deployed environments |

## Project structure

PUnit ships as four published artefacts plus a Gradle plugin:

| Artefact               | Coordinate                 | Purpose                                                                                                          |
|------------------------|----------------------------|------------------------------------------------------------------------------------------------------------------|
| **punit-core**         | `org.javai:punit-core`     | Foundational library: author-facing API (`UseCase`, `Contract`, `Sampling`, criteria), engine, statistics, baselines, runtime entry point. JUnit-free; sentinel-deployable directly. |
| **punit-junit5**       | `org.javai:punit-junit5`   | JUnit 5 integration. The artefact most consumers depend on; pulls `punit-core` transitively.                     |
| **punit-sentinel**     | `org.javai:punit-sentinel` | Sentinel runner for production/scheduled probabilistic checks without a test harness.                            |
| **punit-report**       | `org.javai:punit-report`   | HTML report generator and verdict-XML reader/writer. Pulled in by `punit-junit5`; also consumable on its own.    |
| **punit Gradle plugin**| `org.javai.punit` (plugin) | Auto-configures the `test` task, registers `experiment` / `exp` tasks, supports `-Prun=` filtering.              |

The four library artefacts share the `punit-` prefix; `punit-core` is the foundation, the others extend it.

## Quick Start

### 1. Add Dependency

Most users depend on `punit-junit5`, which pulls `punit-core` (and `punit-report`) transitively:

```kotlin
plugins {
    id("org.javai.punit") version "0.6.0"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.javai:punit-junit5:0.6.0")
}
```

**Maven:**

```xml
<dependency>
    <groupId>org.javai</groupId>
    <artifactId>punit-junit5</artifactId>
    <version>0.6.0</version>
    <scope>test</scope>
</dependency>
```

Maven users need to enable JUnit extension auto-detection and configure Surefire/Failsafe — see [MAVEN-CONFIGURATION.md](docs/MAVEN-CONFIGURATION.md).

For sentinel-deployable applications that run probabilistic checks without a test harness, depend on `punit-core` directly (and optionally `punit-sentinel` for the standalone runner). See [Part 9: The Sentinel](docs/USER-GUIDE.md#part-9-the-sentinel) in the user guide.

### 2. Write a Probabilistic Test

A use case wraps the service call and declares its acceptance contract:

```java
public class GreetingService implements UseCase<Void, String, String> {
    @Override
    public Outcome<String> invoke(String name, TokenTracker tracker) {
        return Outcome.ok(myService.greet(name));
    }

    @Override
    public void postconditions(ContractBuilder<String> b) {
        b.ensure("Greeting is non-empty", s ->
                s == null || s.isBlank()
                        ? Outcome.fail("empty", "no content")
                        : Outcome.ok());
    }
}
```

The probabilistic test exercises that use case with a `Sampling` (factory + inputs + sample count) and asserts a population-level criterion:

```java
class GreetingServiceTest {
    @ProbabilisticTest
    void serviceGreetsConsistently() {
        PUnit.testing(
                Sampling.of(v -> new GreetingService(), 100,
                        List.of("Alice", "Bob", "Charlie")),
                null)
            .criterion(BernoulliPassRate.meeting(0.95, ThresholdOrigin.SLA))
            .contractRef("Service Agreement §4.2")
            .assertPasses();
    }
}
```

This runs 100 samples, requires a 95% success rate at the configured confidence (default 0.95), and records the threshold's provenance as an SLA. The framework terminates early if success is guaranteed or failure becomes inevitable.

### 3. Run It

```bash
./gradlew test
```

## Documentation

The **[User Guide](docs/USER-GUIDE.md)** is the comprehensive reference for PUnit. It covers the full experimentation-to-testing workflow, the use case pattern, latency assertions, budget and pacing control, the statistical core, the Sentinel runtime, and the HTML report.

The **[Statistical Companion](docs/STATISTICAL-COMPANION.md)** covers the mathematical foundations for readers who want to understand the inference machinery.

## Requirements

- Java 21+
- JUnit Jupiter 5.13+

## License

Attribution Required License (ARL-1.0) — see [LICENSE](LICENSE) for details.

## Contributing

Contributions are welcome. Please open an issue or pull request on [GitHub](https://github.com/javai-org/punit).
