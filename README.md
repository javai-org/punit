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

| Feature                       | Description                                                        |
|-------------------------------|--------------------------------------------------------------------|
| **Normative thresholds**      | Test against SLA/SLO/policy thresholds with provenance tracking    |
| **Spec-driven thresholds**    | Derive pass/fail thresholds from empirical data, not guesswork     |
| **Early termination**         | Stop early when failure is inevitable or success is guaranteed     |
| **Budget control**            | Time and token budgets at method, class, or suite level            |
| **Pacing constraints**        | Declare API rate limits; framework computes optimal execution pace |
| **Latency assertions**        | Per-percentile latency thresholds (p50, p90, p95, p99)            |
| **HTML report**               | Standalone report with per-test statistical detail                 |
| **Sentinel**                  | JUnit-free engine for probabilistic testing in deployed environments |

## Quick Start

### 1. Add Dependency

**Gradle (Kotlin DSL):**

```kotlin
plugins {
    id("org.javai.punit") version "0.5.0"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.javai:punit:0.5.0")
}
```

**Maven:**

```xml
<dependency>
    <groupId>org.javai</groupId>
    <artifactId>punit</artifactId>
    <version>0.5.0</version>
    <scope>test</scope>
</dependency>
```

Maven users need manual Surefire/Failsafe configuration — see [MAVEN-CONFIGURATION.md](docs/MAVEN-CONFIGURATION.md).

### 2. Write a Probabilistic Test

```java
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.ThresholdOrigin;
import static org.assertj.core.api.Assertions.assertThat;

class MyServiceTest {

    @ProbabilisticTest(
        samples = 100,
        minPassRate = 0.90,
        thresholdOrigin = ThresholdOrigin.SLA,
        contractRef = "Service Agreement §4.2"
    )
    void serviceReturnsValidResponse() {
        Response response = myService.call();
        assertThat(response.isValid()).isTrue();
    }
}
```

This test runs 100 samples, requires 90% success, and documents that the threshold comes from an SLA. PUnit will terminate early if success is guaranteed or failure becomes inevitable.

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
