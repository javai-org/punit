# REQ-PUNIT-SENTINAL: PUnit Sentinel Requirements

## 1. Overview

PUnit Sentinel is a deployable companion module that runs experiments and probabilistic tests within a target operating environment. It acts as a watchdog over the stochastic features of an application, detecting performance degradation and behavioural drift in non-deterministic systems.

### 1.1 Motivation

Baseline specs captured during development may not reflect production reality. Environment-dependent concerns — latency, throughput, external service behaviour — can differ enormously between a developer laptop and a production server. PUnit currently has no recipe for establishing and validating baselines in situ.

### 1.2 Operational Model

The Sentinel operates in two modes:

- **Experiment mode** (infrequent): Runs experiments against the live environment to establish or refresh environment-specific baselines.
- **Test mode** (frequent): Executes probabilistic tests against the current baseline, reporting verdicts to an external observability system.

The Sentinel has no scheduling responsibility. Scheduling is delegated to the host environment (cron, Kubernetes CronJobs, cloud schedulers, operator action).

---

## 2. Gap Analysis

The following analysis identifies what exists, what can be reused, and what must be built or changed.

### 2.1 Reusable Without Modification

These components have zero JUnit dependency and can be used directly by the Sentinel:

| Component                   | Package           | Notes                                                  |
|-----------------------------|-------------------|--------------------------------------------------------|
| `SampleResultAggregator`    | `ptest.engine`    | Tracks pass/fail outcomes                              |
| `EarlyTerminationEvaluator` | `ptest.engine`    | Detects impossibility/guaranteed success               |
| `FinalVerdictDecider`       | `ptest.engine`    | Determines final pass/fail                             |
| `ThresholdDeriver`          | `statistics`      | Derives thresholds from baseline data                  |
| `CostBudgetMonitor`         | `controls.budget` | Per-method budget tracking                             |
| `SharedBudgetMonitor`       | `controls.budget` | Class/suite-level budget tracking                      |
| `BudgetOrchestrator`        | `controls.budget` | Orchestrates pre/post-sample budget checks             |
| `SuiteBudgetManager`        | `controls.budget` | Suite-level budget, reads system properties/env vars   |
| `ConfigurationResolver`     | `ptest.engine`    | System property -> env var -> annotation -> default    |
| `SpecificationLoader`       | `spec.registry`   | Parses YAML specs; accepts raw `String` content        |
| `BernoulliTrialsStrategy`   | `ptest.bernoulli` | Core sample execution strategy                         |
| All statistics classes      | `statistics`      | Confidence intervals, distributions, etc.              |
| `PUnitReporter`             | `reporting`       | Log4j-based reporting (standard in server deployments) |

### 2.2 JUnit-Coupled Components (Cannot Be Reused Directly)

| Component                    | JUnit Coupling                                                                                                                  | Sentinel Impact                                                          |
|------------------------------|---------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------|
| `ProbabilisticTestExtension` | Implements `TestTemplateInvocationContextProvider` + `InvocationInterceptor`; uses `ExtensionContext`, `ExtensionContext.Store` | The N-sample execution loop must be extracted or replicated              |
| `ExperimentExtension`        | Same dual-interface coupling                                                                                                    | Experiment execution loop must be extracted or replicated                |
| `UseCaseProvider`            | Implements JUnit `ParameterResolver`                                                                                            | Use case instantiation must work without JUnit parameter injection       |
| `ResultPublisher`            | Package-private; caller passes results to JUnit `TestReporter` via `context.publishReportEntry()`                               | Result dispatch must target a Sentinel-specific sink                     |
| `@ProbabilisticTest`         | Meta-annotated with `@TestTemplate`, `@ExtendWith`                                                                              | Annotation stays as-is (DD-05). Sentinel reads PUnit attributes directly; JUnit meta-annotations are inert without the engine |

---

## 3. Design Decisions

The following decisions were reached during requirements analysis and constrain the design.

### DD-01: Two Dimensions of Nondeterminism

The model recognises exactly two independent dimensions of stochastic behaviour:

- **Functional nondeterminism** — Did the SUT produce an acceptable result? Binary per sample (pass/fail).
- **Latency nondeterminism** — How long did the SUT take? Continuous measurement per sample (duration).

These dimensions are independent. A use case may exhibit nondeterminism in either or both. No other verdict dimensions are modelled — concerns such as token cost are budget constraints, not statistical assertions about SUT behaviour.

Because the dimensions are independent, each must have its own baseline spec. A single use case may produce two baseline files — one for functional nondeterminism, one for latency nondeterminism — each with its own sample size, statistical profile, and potentially different environment sensitivity. Experiments establish these baselines independently; probabilistic tests consume them independently. The spec resolution mechanism (REQ-S02) applies per dimension.

### DD-02: Single Verdict per Probabilistic Test Method

Each `@ProbabilisticTest` method execution produces a single verdict event. If the test asserts on both dimensions, the verdict is a conjunctive pass (both must pass for the verdict to pass). The verdict rationale provides per-dimension statistical detail — baseline, threshold, observed rate, confidence interval, and individual pass/fail — so that the source of any failure is unambiguous.

A use case may be exercised by any number of probabilistic test methods, each producing its own independent verdict. The use case itself has no aggregate verdict.

A verdict includes only the dimensions exercised by the test. If a `@ProbabilisticTest` makes no latency assertions, the verdict contains no latency information — and vice versa. The verdict's structure is shaped by what the test actually asserts, not by the full set of dimensions the model supports.

### DD-03: Layered Spec Resolution (No Source Declaration)

Spec sourcing is orthogonal to both the use case and the assertion dimension. Any dimension's baseline may be environment-dependent. Rather than requiring the test author to declare a spec source, the framework resolves specs via a layered fallback: environment-local directory first, classpath (checked-in) second. The existing covariate mechanism flags environmental mismatch. See REQ-S02.

An earlier proposal placed a `specSource` attribute on `@UseCase` to distinguish repository-sealed from environment-local specs. This was rejected because: (1) spec sourcing is orthogonal to both use case and assertion dimension — any dimension may be environment-dependent; (2) a layered resolution strategy achieves the same goal transparently; (3) the existing covariate mechanism already signals environmental mismatch. The REQ-S03 identifier has been reassigned to the dimension-scoped assertion API.

### DD-04: Verdicts as Triage Signals, Not Failure Suppression

A PUnit PASS verdict does not mean "ignore the failures." It means the observed failure rate falls within the expected statistical envelope — operators should still investigate, but with proportionate urgency. A PUnit FAIL means the failure rate exceeds what the baseline predicts and warrants immediate attention.

PUnit is a prioritisation tool: it helps operators allocate investigative effort in proportion to statistical confidence that a genuine problem exists. Individual sample failures are never hidden or suppressed. All verdict presentations — whether in JUnit XML, HTML reports, console output, or verdict sink payloads — must prominently display the raw failure count and rate alongside the verdict. The verdict contextualises the failures; it does not replace them.

This principle constrains the design of all reporting features (REQ-S05, and any future JUnit XML post-processor or HTML report generator).

### DD-05: JUnit API on Sentinel Classpath

The `junit-jupiter-api` JAR (annotations and interfaces) is acceptable on the Sentinel's production classpath. The `junit-jupiter-engine` (the test runner) is not.

This decision eliminates the need for annotation restructuring. `@ProbabilisticTest`, `@MeasureExperiment`, and all other PUnit annotations are defined once, in `punit-core`, with their JUnit meta-annotations (`@TestTemplate`, `@ExtendWith`) intact. `punit-core` declares `junit-jupiter-api` as a `compileOnly` dependency — sufficient for compilation, not forced onto consumers. `punit-junit5` brings `junit-jupiter-api` at runtime (via its own dependency). `punit-sentinel` brings `junit-jupiter-api` at runtime explicitly.

The JUnit meta-annotations are inert without the JUnit engine. The Sentinel's own execution engine reads the PUnit annotation attributes (`samples`, `minPassRate`, `spec`, etc.) reflectively and ignores the JUnit activation metadata. One annotation, two engines.

**Rationale:** The discomfort with JUnit on a production classpath is a hangover from deterministic testing culture, where tests have no reason to exist outside dev/CI. PUnit addresses a fundamentally different circumstance: stochastic behaviour that is environment-dependent. Baselines must be established and validated in the target environment. The annotations are metadata — they describe *what* to test, not *how* to run it. The engine determines the "how."

---

## 4. Requirements

### REQ-S01: Module Decomposition

**Rationale:** The framework is currently a single module with JUnit 5 Jupiter API declared as an `api` (compile + transitive) dependency. The Sentinel needs the statistical engine and annotation metadata but not the JUnit extensions or test engine.

**Requirement:** Decompose the project into:

- `punit-core` — Statistical engine, spec loading, configuration, budget management, reporting primitives, and all PUnit annotations. Annotations retain their JUnit meta-annotations (`@TestTemplate`, `@ExtendWith`); `junit-jupiter-api` is a `compileOnly` dependency (see DD-05).
- `punit-junit5` — JUnit 5 integration layer: extensions (`ProbabilisticTestExtension`, `ExperimentExtension`), `UseCaseProvider` as `ParameterResolver`. Depends on `punit-core` and `junit-jupiter-api` (runtime).
- `punit-sentinel` — Sentinel runner, verdict dispatch, deployment support. Depends on `punit-core` and `junit-jupiter-api` (runtime, for annotation reading). Zero `junit-jupiter-engine` dependency.

The existing single-module `punit` artifact should become a meta-artifact that transitively includes both `punit-core` and `punit-junit5` to preserve backward compatibility for current consumers.

### REQ-S02: Spec Resolution with Layered Fallback

**Rationale:** `SpecificationRegistry` and `BaselineRepository` are filesystem-only. `ConfigurationResolver.loadSpec()` instantiates `new SpecificationRegistry()` with hardcoded default paths (`src/test/resources/punit/specs`). A Sentinel in production needs specs from environment-specific locations. However, the test author should not need to declare where a spec comes from — the resolution mechanism should handle this transparently.

Whether a baseline is environment-dependent is not predictable by dimension (functional vs. latency). Functional baselines may be just as environment-sensitive as latency baselines when the SUT involves external services. Therefore, spec sourcing is not a declaration on the use case or assertion — it is a resolution strategy applied uniformly.

The existing covariate mechanism already reports when a baseline's context does not match the current execution environment, providing a natural safety net when a checked-in spec is used in a mismatched environment.

**Requirement:**

- Introduce a `SpecRepository` interface with method `ExecutionSpecification resolve(String specId)`.
- `SpecificationRegistry` becomes one implementation (filesystem-backed).
- `ConfigurationResolver.loadSpec()` accepts an injected `SpecRepository` rather than instantiating its own.
- A new environment variable `PUNIT_SPEC_DIR` / system property `punit.spec.dir` specifies an environment-local spec directory.
- Spec resolution follows a layered fallback strategy:
  1. **Environment-local directory** (`punit.spec.dir` / `PUNIT_SPEC_DIR`): If a spec exists here, it takes precedence. These are produced by running experiments in the target environment.
  2. **Classpath (checked-in specs)**: If no environment-local spec is found, the resolver falls back to the repository-checked spec on the classpath (the existing default path `punit/specs/`).
- No annotation-level `specSource` declaration is required. The resolution order is the mechanism; the test author simply references a spec by ID.
- Functional and latency baselines are separate specs (see DD-01). Each dimension's spec is resolved independently through the layered fallback. A use case may have an environment-local latency baseline while its functional baseline falls back to the checked-in version, or vice versa.
- The Sentinel workflow follows naturally: run experiments in the target environment to produce environment-local specs; subsequent test runs pick them up automatically. If no experiments have been run, checked-in baselines apply, and the covariate mechanism flags any environmental mismatch in the verdict.

### REQ-S03: Dimension-Scoped Assertion API

**Rationale:** `UseCaseOutcome` currently provides `assertAll()`, which checks all criteria (postconditions, expected value matching, duration constraints) as a single pass/fail. With functional and latency nondeterminism recognised as independent dimensions (DD-01), the test author needs to assert on each dimension independently. This determines which dimensions appear in the verdict (DD-02) and which baseline specs are consumed.

**Requirement:**

- Add the following methods to `UseCaseOutcome`:
  - `assertContract()` — Asserts only functional postconditions (the use case's service contract). Throws `AssertionError` if any postcondition fails. If no service contract is configured, this is a **misconfiguration** — the framework fails fast with a clear error rather than silently passing.
  - `assertLatency()` — Asserts only the duration constraint. Throws `AssertionError` if the execution time exceeds the limit. If no latency attribute is specified on the `@ProbabilisticTest` annotation, this is a **misconfiguration** — the framework fails fast with a clear error.
  - `assertAll()` — Adaptive: asserts whichever dimensions are configured. If a latency attribute is present, latency is asserted. If a service contract is present, the contract is asserted. If both are present, both are asserted (conjunctive pass). If neither is configured, this is a **misconfiguration** — there is nothing to assert.
- The assertion method invoked by the test determines which dimensions are exercised. The framework uses this to:
  - Include only exercised dimensions in the verdict rationale.
  - Resolve and consume only the relevant baseline specs.
  - Apply the conjunctive pass rule (DD-02) only across exercised dimensions.
- A `@ProbabilisticTest` method that calls `assertContract()` produces a verdict with functional detail only. One that calls `assertLatency()` produces a verdict with latency detail only. One that calls `assertAll()` produces a verdict covering whichever dimensions are configured.

### REQ-S04: Sentinel Runner

**Rationale:** There is no non-JUnit entry point for executing the N-sample probabilistic test loop. `ProbabilisticTestExtension` is tightly bound to JUnit's `TestTemplateInvocationContextProvider` lifecycle.

**Requirement:**

- Provide a `SentinelRunner` that executes probabilistic tests and experiments without requiring JUnit Platform.
- The runner must:
  - Discover use case classes (by explicit registration, classpath scanning, or configuration).
  - Instantiate use cases (directly or via a DI-aware factory).
  - Resolve configuration using the existing `ConfigurationResolver` priority chain.
  - Load specs via the `SpecRepository` abstraction (REQ-S02).
  - Execute the N-sample loop using `BernoulliTrialsStrategy`, `SampleResultAggregator`, and `EarlyTerminationEvaluator`.
  - Compute the final verdict using `FinalVerdictDecider`.
  - Dispatch verdicts via the `VerdictSink` abstraction (REQ-S05).
- The runner is invoked programmatically. Scheduling is not its concern.

### REQ-S05: Verdict Dispatch Abstraction

**Rationale:** Results are currently dispatched via two channels: Log4j (`PUnitReporter`) and JUnit `TestReporter` (`context.publishReportEntry()`). Neither is suitable as the sole output mechanism for a production Sentinel. Production environments need verdicts routed to metrics stores, alerting systems, dashboards, or webhooks.

**Requirement:**

- Introduce a `VerdictSink` interface (or equivalent) that receives structured verdict data.
- The structured data is the `Map<String, String>` already produced by `ResultPublisher.buildReportEntries()`, potentially enriched with environment metadata.
- Provide at least two implementations:
  - `LogVerdictSink` — Writes verdicts via the existing `PUnitReporter` (Log4j). Default for local/development use.
  - `WebhookVerdictSink` — Posts verdict data to a configurable HTTP endpoint. Suitable for integration with observability platforms.
- The `SentinelRunner` accepts one or more `VerdictSink` instances (composite pattern).
- The verdict response (alert, circuit-break, dashboard update) is the responsibility of the receiving system, not the Sentinel.

### REQ-S06: Externalised Budget Configuration

**Rationale:** Suite-level budgets are already configurable via `PUNIT_SUITE_TIME_BUDGET_MS` and `PUNIT_SUITE_TOKEN_BUDGET` environment variables. Method-level budgets are resolved from annotations, with system property overrides. A deployer must be able to override budgets without modifying source code.

**Requirement:**

- Ensure method-level budget parameters (`punit.timeBudgetMs`, `punit.tokenBudget`) are fully resolvable from environment variables, matching the existing `ConfigurationResolver` priority chain.
- The Sentinel's deployment descriptor (environment variables, properties file, or equivalent) is the canonical source for production budget values.
- No additional code changes are required if the existing resolution chain already supports this. Validate and document.

### REQ-S07: Use Case Instantiation Without JUnit

**Rationale:** `UseCaseProvider` implements JUnit's `ParameterResolver` to inject use case instances into test methods. In a Sentinel context, there is no JUnit parameter injection lifecycle.

**Requirement:**

- Extract the use case instantiation logic from `UseCaseProvider` into a standalone factory (`UseCaseFactory` or equivalent).
- The factory must:
  - Instantiate use case classes using their default constructor, or via a pluggable instantiation strategy (for DI framework integration).
  - Support the existing `UseCaseProvider.getInstance(Class)` behaviour.
- `UseCaseProvider` delegates to this factory internally (no duplication).
- The Sentinel uses the factory directly.

### REQ-S08: Experiment Execution in Sentinel Context

**Rationale:** The Sentinel must run experiments to establish environment-specific baselines. `ExperimentExtension` delegates to mode-specific strategies (`MeasureStrategy`, `ExploreStrategy`, `OptimizeStrategy`) but is coupled to JUnit's extension lifecycle.

**Requirement:**

- The `SentinelRunner` (REQ-S04) must support experiment execution in addition to probabilistic test execution.
- Experiment results (baseline specs) must be written to the environment-local spec store, making them available for subsequent probabilistic test runs.
- The spec output location is configurable via environment variables (`PUNIT_SPEC_DIR` or `punit.spec.dir`).

### REQ-S09: Environment Metadata in Verdicts

**Rationale:** A Sentinel verdict must be traceable to the environment in which it was produced. The current verdict output has no environment identification.

**Requirement:**

- Verdicts emitted by the Sentinel include environment metadata:
  - Environment identifier (e.g., `prod`, `staging`, `us-east-1`) — sourced from an environment variable or configuration.
  - Timestamp of execution.
  - Sentinel instance identifier (for multi-instance deployments).
- This metadata is appended to the structured verdict data (`Map<String, String>`) produced by `buildReportEntries()`.

### REQ-S10: Sentinel Lifecycle API

**Rationale:** The Sentinel is embedded in a host application or deployed as a sidecar. It needs a clean lifecycle API for integration.

**Requirement:**

- The Sentinel exposes a programmatic API:
  - `runExperiments()` — Executes all registered experiments, producing/refreshing baseline specs.
  - `runTests()` — Executes all registered probabilistic tests against current baselines.
  - `runTests(String useCaseId)` — Executes a specific use case's probabilistic test.
- Each method returns a structured result summary (aggregate pass/fail count, individual verdict details).
- The API is synchronous. The caller (scheduler, HTTP handler, operator script) manages threading and scheduling.

### REQ-S11: Architectural Guidance Documentation

**Rationale:** A PUnit Sentinel deployment requires the application's stochastic service integrations to be separable from the main application. The Sentinel must import and execute the same stochastic code paths directly, but should not depend on the entire application. Use cases — the bridge between stochastic services and PUnit's contract/assertion API — need a home that is accessible to both the JUnit test suite and the Sentinel, without leaking PUnit onto the main application's production classpath.

These concerns are not PUnit framework requirements, but adopters will face them immediately. Without guidance, teams will either couple the Sentinel to the full application or place use cases in the test source set (making them unavailable to the Sentinel).

**Requirement:**

- Produce architectural guidance documentation for PUnit adopters, covering:
  - **Reference module layout** for applications that use both probabilistic testing and a Sentinel deployment:
    ```
    app-stochastic          punit-core
      ↑         ↑              ↑
    app-main    app-usecases ──┘
                  ↑        ↑
             test suite    app-sentinel
    ```
    - `app-stochastic` — The application's stochastic service integrations (LLM clients, ML inference, external API wrappers). Contains the code that exhibits non-deterministic behaviour. No PUnit dependency.
    - `app-main` — The main application. Depends on `app-stochastic`. No PUnit dependency.
    - `app-usecases` — Use case definitions and service contracts. Depends on `app-stochastic` (to invoke stochastic services) and `punit-core` (for `UseCaseOutcome`, `ServiceContract`, etc.). This module defines *how to exercise and evaluate* the application's stochastic boundaries.
    - Test suite — Depends on `app-usecases` + `punit-junit5`. Developer-time probabilistic tests and experiments.
    - `app-sentinel` — The Sentinel deployment. Depends on `app-usecases` + `punit-sentinel`. Runs the same use cases and experiments in the target environment.
  - **Why use cases are not test code:** Use cases are consumed by both the test suite and the Sentinel. They define the contract between the application and its stochastic dependencies. They belong in a shared module, not in a test source set.
  - **Why stochastic services should be isolable:** The Sentinel binary should be lightweight. Separating stochastic integrations from the rest of the application keeps the Sentinel's dependency footprint minimal and forces a clean API boundary around non-deterministic behaviour.
- The documentation should be published as part of the PUnit project (e.g., `docs/SENTINEL-DEPLOYMENT-GUIDE.md`).

---

## 5. Out of Scope

The following concerns are explicitly deferred:

| Concern                               | Rationale                                                                                                                         |
|---------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| Scheduling                            | Delegated to the host environment (cron, K8s CronJobs, etc.)                                                                      |
| Alerting logic                        | The Sentinel reports verdicts; the response (alerting, circuit-breaking) is the receiving system's responsibility                 |
| Remote spec repository implementation | REQ-S02 defines the abstraction; specific remote implementations (S3, HTTP, database) are future work beyond the initial Sentinel |
| UI / Dashboard                        | The Sentinel is headless; dashboard integration is via the `VerdictSink` abstraction                                              |
| Authentication / authorisation        | Deployment-environment concern, not a framework concern                                                                           |

---

## 6. Dependency Summary

```
REQ-S01 (Module Decomposition)
  ├── REQ-S02 (Spec Resolution)            — must live in punit-core
  ├── REQ-S03 (Dimension-Scoped Assertions)— must live in punit-core (UseCaseOutcome)
  ├── REQ-S05 (Verdict Dispatch)           — must live in punit-core (interface) + punit-sentinel (impls)
  ├── REQ-S06 (Budget Configuration)       — validate existing support in punit-core
  └── REQ-S07 (Use Case Factory)           — must live in punit-core

REQ-S03 (Dimension-Scoped Assertions)
  └── REQ-S02 (Spec Resolution)            — determines which per-dimension specs are consumed

REQ-S04 (Sentinel Runner)
  ├── depends on REQ-S01, REQ-S02, REQ-S03, REQ-S05, REQ-S07
  ├── REQ-S08 (Experiment Execution)       — extends the runner
  ├── REQ-S09 (Environment Metadata)       — enriches verdict output
  └── REQ-S10 (Lifecycle API)              — public surface of the runner

REQ-S11 (Architectural Guidance)           — documents reference module layout for adopters
  └── depends on REQ-S01                   — the PUnit module structure it references
```

## 7. Naming

The module is named **PUnit Sentinel**. The artifact ID is `punit-sentinel`. The concept communicates watchdog/guardian semantics without implying "tests in production."
