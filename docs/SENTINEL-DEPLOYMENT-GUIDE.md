# Sentinel Deployment Guide

This guide covers project structure and operational deployment for PUnit Sentinel — the execution engine for running probabilistic tests and experiments in deployed environments. For authoring reliability specifications and the Sentinel programming model, see [Part 10 of the User Guide](USER-GUIDE.md#part-10-the-sentinel).

---

## Why the Sentinel Exists

The stochastic behaviour of certain use cases — LLM integrations, ML model inference, calls to external services with variable latency — can depend heavily on environmental factors: model versions deployed in a specific region, API provider performance characteristics, infrastructure configuration, network conditions.

PUnit already models these environmental influences through its **covariate** system. Covariates are declared environmental factors — temporal (time of day, weekday vs weekend), infrastructural (region, instance type, API version), or configurational (model, temperature, prompt variant) — that PUnit tracks so it can select the most appropriate baseline for a given execution context. When covariates don't match, PUnit qualifies the verdict with a non-conformance warning, because testing against the wrong baseline produces misleading results.

However, covariate-aware baseline selection works best when baselines are available for the relevant covariate values. For use cases whose behaviour is shaped by the deployed environment, probabilistic testing must be based on baselines obtained from within that environment. A baseline measured on a developer workstation or in a CI container may not reflect what the system experiences in staging or production.

Because environmental factors change over time — model updates, infrastructure migrations, provider degradation — a one-off baseline is insufficient. We require a mechanism that continuously guards against regression by re-measuring baselines and re-verifying reliability within the target environment.

This is the PUnit Sentinel: an execution engine that runs the same probabilistic tests and experiments defined for development-time testing, but in the deployed environment, producing environment-specific baselines and dispatching verdicts to observability infrastructure.

---

## Reference Module Layout

```
app-stochastic          punit-core
  ↑         ↑              ↑
app-main    app-usecases ──┘
              ↑        ↑
         test suite    app-sentinel
```

### Module Responsibilities

#### `app-stochastic` — Stochastic Service Integrations

Contains the application's non-deterministic service integrations: LLM client wrappers, ML model interfaces, external API clients with variable latency. These are **plain Java objects** constructable via `new` — no DI framework, no PUnit dependency.

```kotlin
// app-stochastic/build.gradle.kts
dependencies {
    // Only the service's own dependencies (HTTP client, SDK, etc.)
    implementation("com.openai:openai-java:1.0.0")
}
```

```java
// Plain Java — no PUnit, no Spring, no Guice
public class OpenAiClient {
    private final String apiKey;

    public OpenAiClient(String apiKey) {
        this.apiKey = apiKey;
    }

    public String chat(String prompt) {
        // Call OpenAI API
    }
}
```

#### `app-usecases` — Reliability Specifications

Contains `@Sentinel`-annotated reliability specification classes, use case implementations, service contracts, and `@InputSource` methods. This is the bridge between the application's stochastic services and PUnit's monitoring framework.

```kotlin
// app-usecases/build.gradle.kts
dependencies {
    api(project(":app-stochastic"))
    api("org.javai:punit-core:0.7.0")  // Production dependency
}
```

This module depends on `punit-core` via `api()` — not `testImplementation`. This is the defining characteristic of the Sentinel authoring model: PUnit types (`@Sentinel`, `UseCaseFactory`, `UseCaseOutcome`, `ServiceContract`, etc.) are production dependencies in this module because the reliability specification is a production artifact.

The module must **not** depend on `punit-junit5` or `junit-jupiter-api`. The reliability specification is JUnit-free. For how to author a `@Sentinel` class and the reliability-specification-first model, see [Part 10 of the User Guide](USER-GUIDE.md#part-10-the-sentinel).

#### `app-main` — Main Application

The main application module. Uses whatever DI framework is appropriate (Spring Boot, Guice, Micronaut, or none). Depends on `app-stochastic` to access stochastic services. **Has no PUnit dependency whatsoever.**

```kotlin
// app-main/build.gradle.kts
dependencies {
    implementation(project(":app-stochastic"))
    implementation("org.springframework.boot:spring-boot-starter-web:3.4.0")
    // No PUnit dependency
}
```

The DI layer simply instantiates the stochastic service classes from `app-stochastic` as beans — no code duplication, no PUnit awareness.

#### Test Suite — JUnit Probabilistic Tests

The JUnit test source set. Contains thin subclasses of `@Sentinel` reliability specifications, standalone `@ProbabilisticTest` classes, experiments, and test utilities.

```kotlin
// In app-usecases/build.gradle.kts or a dedicated app-tests module
dependencies {
    testImplementation(project(":app-usecases"))
    testImplementation("org.javai:punit-junit5:0.7.0")  // Transitively includes punit-core
    testImplementation("org.junit.jupiter:junit-jupiter")
}
```

#### `app-sentinel` — Sentinel Deployment

The Sentinel deployment module. Contains `SentinelRunner` bootstrap code and deployment configuration. This is the binary that runs in production/staging environments.

```kotlin
// app-sentinel/build.gradle.kts
dependencies {
    implementation(project(":app-usecases"))
    implementation("org.javai:punit-sentinel:0.7.0")  // Transitively includes punit-core
    // No JUnit dependency
}
```

```java
public class SentinelMain {
    public static void main(String[] args) {
        SentinelConfiguration config = SentinelConfiguration.builder()
            .sentinelClass(ShoppingBasketReliability.class)
            .verdictSink(new WebhookVerdictSink("https://alerts.example.com/punit"))
            .environmentMetadata(EnvironmentMetadata.fromEnvironment())
            .build();

        SentinelRunner runner = new SentinelRunner(config);

        // Run probabilistic tests against current baselines
        SentinelResult result = runner.runTests();

        System.exit(result.allPassed() ? 0 : 1);
    }
}
```

---

## Why Reliability Specifications Are Not Test Code

Reliability specifications define **what to measure** and **what to verify** about stochastic behaviour. They are consumed by both the JUnit test suite (via inheritance) and the Sentinel (directly).

Placing them in a test source set makes them unavailable to the Sentinel — code in `src/test/java` is never packaged into a JAR. The `app-usecases` module is the bridge: it depends on `app-stochastic` (to invoke stochastic services) and `punit-core` (for `UseCaseFactory`, `UseCaseOutcome`, `ServiceContract`, annotations, etc.), and it produces a production artifact consumable by both engines.

This also applies to `@InputSource` methods and their data. The Sentinel engine needs the same inputs as the JUnit engine. If input data lives in the test source set, the Sentinel cannot reach it.

---

## Why Stochastic Services Must Be Plain Java

The Sentinel runtime has no DI container. Stochastic services must be constructable without one.

This is not a limitation — it's a **forcing function for clean API boundaries** around non-deterministic behaviour. Applications using Spring, Guice, or other DI frameworks isolate their stochastic integrations in `app-stochastic` as plain Java objects. The main application's DI layer wraps them as beans. No code duplication, no PUnit dependency in the main application.

No framework-specific Sentinel variants are needed or provided. There is no `SpringSentinel` or `GuiceSentinel`. The `@Sentinel` class constructs its stochastic dependencies as plain Java objects, and the Sentinel runtime knows nothing about DI frameworks.

---

## The Sentinel Deployment Workflow

### 1. Establish Baselines (Experiment Mode)

Run measure experiments in the target environment to produce environment-local baseline specs:

```bash
# In the target environment (staging, production, etc.)
export PUNIT_SPEC_DIR=/opt/sentinel/specs
export PUNIT_ENVIRONMENT=staging

java -jar app-sentinel.jar --experiments
```

This invokes `SentinelRunner.runExperiments()`, which scans `@MeasureExperiment` methods in each `@Sentinel` class, executes the sample loops, and writes per-dimension spec files to `PUNIT_SPEC_DIR`.

### 2. Verify Against Baselines (Test Mode)

Run probabilistic tests against the current baselines:

```bash
java -jar app-sentinel.jar --tests
```

This invokes `SentinelRunner.runTests()`, which scans `@ProbabilisticTest` methods, loads specs via the layered `SpecRepository` (environment-local first, classpath fallback), derives thresholds, and executes the sample loops.

### 3. Verdict Dispatch

Verdicts are dispatched to all configured `VerdictSink` instances. Each verdict carries:

- A correlation ID (e.g., `v:a3f8c2`) for cross-referencing with JUnit reports and observability systems
- Environment metadata (`PUNIT_ENVIRONMENT`, `PUNIT_INSTANCE_ID`)
- Full statistical detail (observed pass rate, threshold, confidence interval, per-dimension breakdown)

---

## Scheduling Is the Deployer's Responsibility

The Sentinel is a library, not a daemon. It does not schedule its own execution, manage cron expressions, or run a background loop. The `SentinelRunner` executes when called, produces a `SentinelResult`, and returns. How and when it is called is entirely up to the deployer.

This is a deliberate design choice. Scheduling infrastructure varies widely across deployment environments, and PUnit has no reason to reinvent or constrain it. The deployer selects the mechanism that fits their operational context:

- **Cron or systemd timer** for simple periodic execution on a VM
- **Kubernetes CronJob** for containerised deployments
- **Cloud schedulers** such as AWS EventBridge, GCP Cloud Scheduler, or Azure Timer Triggers
- **CI/CD pipelines** as a post-deployment verification step
- **Application-embedded scheduling** via Quartz, Spring `@Scheduled`, or similar, if the Sentinel runs within an existing application process

The Sentinel's exit code (`SentinelResult.allPassed()`) and verdict dispatch (`VerdictSink`) provide the integration points. The deployer wires these into their alerting, dashboarding, or circuit-breaking infrastructure as appropriate.

---

## Dependency Summary

| Module           | PUnit Dependency              | JUnit Dependency       | Purpose                         |
|------------------|-------------------------------|------------------------|---------------------------------|
| `app-stochastic` | None                          | None                   | Stochastic service integrations |
| `app-main`       | None                          | None                   | Main application                |
| `app-usecases`   | `punit-core` (production)     | None                   | Reliability specifications      |
| Test suite       | `punit-junit5` (test)         | `junit-jupiter` (test) | JUnit probabilistic tests       |
| `app-sentinel`   | `punit-sentinel` (production) | None                   | Sentinel deployment             |

---

## PUnit Artifact Selection

| Consumer                      | Artifact                   | Scope                                                    |
|-------------------------------|----------------------------|----------------------------------------------------------|
| Reliability spec author       | `org.javai:punit-core`     | `api` (production)                                       |
| JUnit test developer          | `org.javai:punit-junit5`   | `testImplementation`                                     |
| Sentinel deployer             | `org.javai:punit-sentinel` | `implementation` (production)                            |
| Existing consumer (pre-split) | `org.javai:punit`          | `testImplementation` (backward-compatible meta-artifact) |
