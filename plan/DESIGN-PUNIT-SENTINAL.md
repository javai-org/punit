# DESIGN-PUNIT-SENTINAL: Phased Design Document

This document describes the implementation plan for the PUnit Sentinel initiative in chronological phases. Each phase produces a fully integral and usable PUnit framework. No phase leaves the system in a broken or transitional state.

The scope extends beyond the `punit-sentinel` runtime artifact. It encompasses module decomposition, the two-dimension stochasticity model, the spec resolution redesign, and architectural guidance — all of which are prerequisites for, or consequences of, the Sentinel concept.

**Reference:** Requirements and design decisions are defined in `REQ-PUNIT-SENTINAL.md`.

---

## Dependency Integrity

PUnit already uses ArchUnit to enforce strict dependency rules between packages (e.g., the `statistics` package must not depend on `api`, `ptest.engine`, `experiment`, or `spec`). This discipline is non-negotiable and must be extended to cover the multi-module structure introduced by this initiative.

### Module-Level Constraints

Each module's ArchUnit test suite must enforce the following:

**`punit-core`:**
- `junit-jupiter-api` is `compileOnly` only — used for annotation meta-annotations (`@TestTemplate`, `@ExtendWith`), not for any runtime behaviour. No imports from `junit-jupiter-engine`.
- Zero imports from `org.javai.punit.sentinel.*` — core must not know about the Sentinel.
- Zero imports from `org.javai.punit.junit5.*` — core must not know about the JUnit integration layer.
- All existing intra-package dependency rules (e.g., `statistics` isolation) continue to apply.

**`punit-junit5`:**
- May depend on `punit-core` and `org.junit.*` (including `junit-jupiter-engine` for test execution).
- Must not depend on `org.javai.punit.sentinel.*`.

**`punit-sentinel`:**
- May depend on `punit-core` and `junit-jupiter-api` (for reading annotation metadata — see DD-05).
- Zero imports from `junit-jupiter-engine` — the Sentinel provides its own execution engine.
- Must not depend on `org.javai.punit.junit5.*`.

**`punit-reporting`** *(when implemented)*:
- May depend on `punit-core`.
- Zero imports from `junit-jupiter-engine`, `org.javai.punit.sentinel.*`, and `org.javai.punit.junit5.*`.

### Enforcement

ArchUnit tests are the enforcement mechanism — not convention, not code review. Every module-level dependency constraint listed above must have a corresponding ArchUnit test that fails the build if violated. These tests are introduced in Phase 1 (Module Decomposition) and extended as new modules are added.

The existing ArchUnit tests for intra-package dependencies are preserved and, where necessary, adapted to the multi-module structure.

---

## Artifact Strategy

### Current State

Today, PUnit publishes a single artifact to Maven Central:

- `org.javai:punit:<version>` — contains everything: statistical engine, JUnit 5 extensions, annotations, spec loading, contracts, budgets, reporting.

The Gradle plugin is published separately as `org.javai.punit:org.javai.punit.gradle.plugin`.

### Proposed Artifacts

The module decomposition introduces three consumer profiles with distinct dependency needs. Each profile must be able to depend on exactly what it requires, without pulling in unnecessary transitive dependencies:

| Consumer | Role | Needs | Must Not Have |
|---|---|---|---|
| **Use case author** (`app-usecases`) | Defines use cases and service contracts | `UseCaseOutcome`, `ServiceContract`, contract API, annotations | JUnit extensions, JUnit engine, Sentinel runner |
| **Test suite developer** | Writes `@ProbabilisticTest` methods | JUnit 5 extensions, annotations, statistical engine | Sentinel runner |
| **Sentinel deployer** | Runs experiments and tests in target environments | `SentinelRunner`, verdict dispatch, statistical engine, annotations | JUnit engine (`junit-jupiter-engine`) |

This yields four published artifacts, plus one reserved for future work:

**`org.javai:punit-core`** — The statistical engine, spec loading, contracts, budgets, reporting primitives, all PUnit annotations (`@ProbabilisticTest`, `@MeasureExperiment`, etc.), `UseCaseFactory`, `VerdictSink` interface. Annotations carry JUnit meta-annotations compiled against `compileOnly` `junit-jupiter-api` (DD-05). This is the foundation that both JUnit-based testing and the Sentinel build upon. It is also the direct dependency for use case authors who define service contracts and use case outcomes.

**`org.javai:punit-junit5`** — The JUnit 5 integration layer: extensions (`ProbabilisticTestExtension`, `ExperimentExtension`), `UseCaseProvider` as `ParameterResolver`. Depends on `punit-core` and `junit-jupiter-api` transitively. This is what test suite developers depend on.

**`org.javai:punit-sentinel`** — The Sentinel runner, `WebhookVerdictSink`, `EnvironmentMetadata`, `SentinelConfiguration`. Depends on `punit-core` and `junit-jupiter-api` (for reading annotation metadata). Zero `junit-jupiter-engine` dependency — the Sentinel provides its own execution engine. This is what Sentinel deployers depend on.

**`org.javai:punit`** — Backward-compatibility meta-artifact. An empty module that transitively depends on both `punit-core` and `punit-junit5`. Existing consumers who depend on `org.javai:punit` see no change in their dependency graph upon upgrade. New consumers are directed to `punit-junit5` instead.

**`org.javai:punit-reporting`** *(reserved, not part of the initial phases)* — Post-execution report generation: PUnit-aware HTML reports (analogous to JaCoCo's coverage reports) and JUnit XML post-processing (collapsing N samples into a single PUnit verdict entry for CI tools). Depends on `punit-core` only (for `VerdictEvent` and verdict data types). Carries its own rendering dependencies (templating engine, etc.). No JUnit dependency, no Sentinel dependency. Consumable by both the Gradle plugin (as a post-test report task) and the Sentinel (as a post-run report). This module is not implemented in the current initiative but the slot is reserved to ensure the artifact namespace and dependency graph are designed to accommodate it.

The Gradle plugin artifact (`org.javai.punit:org.javai.punit.gradle.plugin`) continues unchanged.

### Critical Evaluation

**Why not two artifacts (core + junit5), merging Sentinel into core?**

This would force every use case author and every JUnit test suite to pull in the Sentinel runner, HTTP client dependencies, and deployment-oriented classes they will never use. The Sentinel is an operational deployment concern, not a development-time concern. Keeping it separate respects the principle that production deployment machinery should not pollute development dependencies.

**Why not two artifacts (junit5 + sentinel), without a separate core?**

This is the option that would minimise the artifact count. However, use case authors (the `app-usecases` module) would be forced to depend on either `punit-junit5` (pulling JUnit extensions and engine onto their compile classpath, even though they never use them) or `punit-sentinel` (pulling the Sentinel runner onto their classpath, even though they only need contracts). Neither is acceptable. The use case module is a shared dependency of both the test suite and the Sentinel — it needs an artifact that carries neither JUnit extensions nor Sentinel concerns. That artifact is `punit-core`.

**Is the backward-compatibility meta-artifact worth the overhead?**

For the initial release following the module split, yes. It eliminates upgrade friction for existing consumers. It can be deprecated in a subsequent release and removed in a future major version, once consumers have had time to migrate their dependency declarations.

**Could `punit-core` be further split?**

Conceivably, the contract API (`UseCaseOutcome`, `ServiceContract`) could be a separate artifact from the statistical engine. However, the contract API depends on types like `DurationConstraint` and `PostconditionResult` which are already intertwined with the statistical model. Splitting further would create a proliferation of small artifacts with complex interdependencies. Three real artifacts (core, junit5, sentinel) plus one meta-artifact is the right granularity — each corresponds to a distinct consumer need, and no consumer is forced to accept unwanted transitive dependencies.

### Dependency Graph

```
org.javai:punit-core
    ↑            ↑               ↑
punit-junit5   punit-sentinel   punit-reporting (reserved)
    ↑
punit (meta-artifact, backward compat)
```

### Consumer Dependency Declarations

```kotlin
// Use case author (app-usecases module)
dependencies {
    api("org.javai:punit-core:0.4.0")
}

// Test suite developer
dependencies {
    testImplementation("org.javai:punit-junit5:0.4.0") // transitively includes punit-core
}

// Sentinel deployer
dependencies {
    implementation("org.javai:punit-sentinel:0.7.0") // transitively includes punit-core
}

// Existing consumer (unchanged, backward compatible)
dependencies {
    testImplementation("org.javai:punit:0.4.0") // transitively includes punit-core + punit-junit5
}
```

---

## Phase 1: Module Decomposition

**Addresses:** REQ-S01, REQ-S07, REQ-S06

**Objective:** Split the single `punit` module into `punit-core` (annotations, statistical engine, contracts — `compileOnly` on `junit-jupiter-api` per DD-05) and `punit-junit5` (JUnit 5 extensions and engine). Extract `UseCaseFactory` from `UseCaseProvider`. Validate budget configuration externalisation. This phase draws the module boundary that enables the Sentinel and provides structural clarity for all subsequent phases — the two-dimension model, the spec resolution redesign, and the assertion API all land more cleanly when it is already clear which module owns each concept.

### 1.1 Multi-Module Gradle Structure

**Current state:** Single-module project with `build.gradle.kts` at the root. JUnit 5 Jupiter API is declared as an `api` dependency (compile + transitive).

**Changes:**

Convert to a multi-module Gradle project:

```
punit/
├── settings.gradle.kts              # includes punit-core, punit-junit5, punit-sentinel
├── build.gradle.kts                 # shared configuration (Java 21, -parameters, etc.)
├── punit-core/
│   ├── build.gradle.kts             # compileOnly on junit-jupiter-api (DD-05)
│   └── src/main/java/               # annotations, statistical engine, specs, contracts, budgets, reporting
├── punit-junit5/
│   ├── build.gradle.kts             # depends on punit-core + JUnit 5 Jupiter (API + engine)
│   └── src/main/java/               # extensions, parameter resolvers
├── punit-sentinel/
│   ├── build.gradle.kts             # depends on punit-core + junit-jupiter-api (no engine)
│   └── src/main/java/               # Sentinel runner, verdict dispatch, environment metadata
├── punit-gradle-plugin/             # unchanged (standalone build)
└── ...
```

The root `build.gradle.kts` becomes a parent project with shared configuration (Java version, compiler flags, common dependency versions). Each submodule has its own `build.gradle.kts` declaring module-specific dependencies.

All three modules are established in Phase 1. `punit-sentinel` begins as a minimal module — its internal classes are populated in Phase 4 — but its existence from the outset ensures that module boundary decisions (what belongs in `punit-core` vs `punit-junit5`) are made with the Sentinel's needs in view. In particular, any class that the Sentinel will need must live in `punit-core`, not `punit-junit5`.

### 1.2 Module Boundary: What Goes Where

**`punit-core`** (`compileOnly` on `junit-jupiter-api` for annotation meta-annotations — see DD-05):

| Package             | Contents                                                                                                                                                                                        |
|---------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `api`               | All PUnit annotations: `@ProbabilisticTest`, `@MeasureExperiment`, `@ExploreExperiment`, `@OptimizeExperiment`, `@UseCase`, `@Factor`, `@FactorSource`, `@Input`, `@InputSource`, `@Latency`, `@Covariate`, `@CovariateSource`, `@Pacing`, `@Config`. Also: `TestIntent`, `ThresholdOrigin`, `BudgetExhaustedBehavior`, `ExceptionHandling`, `UseCaseContext`, `OutcomeCaptor`, `TokenChargeRecorder` |
| `contract`          | `UseCaseOutcome`, `ServiceContract`, `PostconditionEvaluator`, `DurationConstraint`, `DurationResult`, `MatchResult`, matchers                                                                  |
| `ptest.bernoulli`   | `SampleResultAggregator`, `EarlyTerminationEvaluator`, `FinalVerdictDecider`, `BernoulliTrialsStrategy`, `BernoulliTrialsConfig`                                                                |
| `ptest.strategy`    | `ProbabilisticTestStrategy`, `InterceptResult`, `SampleExecutionContext`                                                                                                                        |
| `spec`              | `SpecificationRegistry`, `SpecificationLoader`, `ExecutionSpecification`, `BaselineRepository`, `BaselineSelector`, covariate classes                                                           |
| `statistics`        | All statistical classes: `ThresholdDeriver`, `LatencyThresholdDeriver`, `StatisticalExplanation`, `StatisticalExplanationBuilder`, `BinomialProportionEstimator`, etc.                          |
| `controls.budget`   | `CostBudgetMonitor`, `SharedBudgetMonitor`, `BudgetOrchestrator`, `SuiteBudgetManager`, `ClassBudgetManager`                                                                                    |
| `controls.pacing`   | `PacingConfiguration`, `PacingResolver`, `PacingScheduler`                                                                                                                                      |
| `reporting`         | `PUnitReporter`, `RateFormat`, `LatencySummaryRenderer`                                                                                                                                         |
| `model`             | `TerminationReason`, `CovariateProfile`, `ExpirationStatus`, `BaselineProvenance`                                                                                                               |
| `verdict`           | `VerdictSink` interface (REQ-S05), `LogVerdictSink`                                                                                                                                             |
| `usecase`           | `UseCaseFactory` (REQ-S07)                                                                                                                                                                      |
| `util`              | `Lazy`, utility classes                                                                                                                                                                         |

**`punit-junit5`** (depends on `punit-core` + JUnit 5 Jupiter API + Engine):

| Package | Contents |
|---|---|
| `ptest.engine` | `ProbabilisticTestExtension`, `ConfigurationResolver`, `ResultPublisher`, `SampleExecutor`, `ProbabilisticTestValidator` |
| `experiment.engine` | `ExperimentExtension`, `ExperimentModeStrategy` |
| `experiment.measure` | `MeasureStrategy`, `MeasureConfig`, invocation contexts |
| `experiment.explore` | `ExploreStrategy`, `ExploreConfig`, invocation contexts |
| `experiment.optimize` | `OptimizeStrategy`, `OptimizeConfig`, invocation contexts |
| `experiment.engine.shared` | `ResultRecorder`, `FactorResolver`, parameter resolvers |
| `api` | `UseCaseProvider` (delegates to `UseCaseFactory` from punit-core) |
| `controls.budget` | `ProbabilisticTestBudgetExtension` (JUnit extension for suite/class budgets) |

### 1.3 Dependency Declarations

**`punit-core/build.gradle.kts`:**

```kotlin
dependencies {
    api("org.apache.commons:commons-statistics-distribution:1.2")
    api("org.yaml:snakeyaml:2.5")
    api("com.fasterxml.jackson.core:jackson-databind:2.21.0")
    api("org.javai:outcome:0.1.0")
    implementation("org.apache.logging.log4j:log4j-api:2.25.3")
    implementation("org.apache.logging.log4j:log4j-core:2.25.3")
    compileOnly("com.flipkart.zjsonpatch:zjsonpatch:0.4.16")
    // DD-05: annotations carry JUnit meta-annotations, compiled but not forced onto consumers
    compileOnly("org.junit.jupiter:junit-jupiter-api")
}
```

**`punit-junit5/build.gradle.kts`:**

```kotlin
dependencies {
    api(project(":punit-core"))
    api("org.junit.jupiter:junit-jupiter-api")   // runtime: activates annotation meta-annotations
    implementation("org.junit.jupiter:junit-jupiter-engine")
    // JUnit BOM for version management
    implementation(platform("org.junit:junit-bom:5.14.2"))
}
```

**`punit-sentinel/build.gradle.kts`:**

```kotlin
dependencies {
    api(project(":punit-core"))
    implementation("org.junit.jupiter:junit-jupiter-api")  // DD-05: for reading annotation metadata
    // NO junit-jupiter-engine — Sentinel provides its own execution engine
}
```

### 1.4 `UseCaseFactory` Extraction (REQ-S07)

**Current state:** `UseCaseProvider` implements JUnit's `ParameterResolver` and contains the instantiation logic (`getInstance(Class<T>)`), factory registration (`register()`, `registerWithFactors()`, `registerAutoWired()`), and singleton management.

**Changes:**

Extract the instantiation and factory logic into `UseCaseFactory` in `punit-core`:

```java
public class UseCaseFactory {
    public <T> void register(Class<T> type, Supplier<T> factory);
    public <T> void registerWithFactors(Class<T> type, Function<FactorValues, T> factory);
    public <T> void registerAutoWired(Class<T> type, Supplier<T> factory);
    public <T> T getInstance(Class<T> type);
    // Internal: factor values, singleton management
}
```

`UseCaseProvider` in `punit-junit5` becomes a thin adapter:

```java
public class UseCaseProvider implements ParameterResolver {
    private final UseCaseFactory factory = new UseCaseFactory();

    // ParameterResolver methods delegate to factory
    // register/getInstance methods delegate to factory
}
```

The Sentinel (Phase 4) uses `UseCaseFactory` directly, without any JUnit dependency.

### 1.5 Budget Configuration Validation (REQ-S06)

**Current state:** `ConfigurationResolver` resolves budget parameters via system property → environment variable → annotation → default. Suite-level budgets use `PUNIT_SUITE_TIME_BUDGET_MS` and `PUNIT_SUITE_TOKEN_BUDGET` environment variables.

**Validation:** Confirm that method-level budget parameters (`punit.timeBudgetMs`, `punit.tokenBudget`) are resolvable from environment variables (`PUNIT_TIME_BUDGET_MS`, `PUNIT_TOKEN_BUDGET`) without code changes. If the existing `ConfigurationResolver` does not support environment variable resolution for these parameters, add it.

**Deliverable:** Validation test confirming environment variable override works. Document the full set of environment variables in a configuration reference.

### 1.6 Published Artifact Strategy

To preserve backward compatibility for existing consumers who depend on the `punit` artifact:

- Publish `punit-core` and `punit-junit5` as separate artifacts.
- The existing `punit` artifact ID becomes a meta-artifact (empty module) that transitively depends on both `punit-core` and `punit-junit5`. Consumers who upgrade see no change in their dependency graph.
- New consumers are encouraged to depend on `punit-junit5` directly (which transitively includes `punit-core`).

### 1.7 Test Source Restructuring

Test sources split to match the module structure:

- `punit-core/src/test/java/` — Unit tests for all core classes (statistics, specs, contracts, budgets, aggregators, etc.).
- `punit-junit5/src/test/java/` — Tests that use JUnit TestKit, test subjects, extension tests, ArchUnit tests for package dependencies.

The ArchUnit test that enforces `statistics` package independence is updated to validate the module boundary: `punit-core` must have zero imports from `org.junit.*`.

### 1.8 Gradle Plugin Update

The `punit-gradle-plugin` must be updated to work with the multi-module structure:

- The plugin's dependency on PUnit (if any) should reference `punit-junit5` (or the meta-artifact).
- No user-facing changes to plugin DSL or task behaviour.

### 1.9 Backward Compatibility

- The meta-artifact ensures existing `punit` dependency declarations continue to work.
- No public API is removed or renamed — the split is purely structural.
- Annotations remain in `org.javai.punit.api` within `punit-core`. Package paths do not change. Existing code that imports `@ProbabilisticTest` continues to work without modification (DD-05).

### 1.10 Verification Criteria

- `punit-core` compiles with `junit-jupiter-api` as `compileOnly` (annotations use JUnit meta-annotations). All its unit tests pass.
- `punit-junit5` compiles against `punit-core` and all its tests pass.
- `punit-sentinel` compiles against `punit-core` + `junit-jupiter-api` with zero `junit-jupiter-engine` on the classpath.
- The meta-artifact resolves correctly — a consumer depending on `punit` transitively gets both `punit-core` and `punit-junit5`.
- `UseCaseFactory` unit tests pass in `punit-core`.
- `UseCaseProvider` delegation tests pass in `punit-junit5`.
- Budget environment variable override test passes.
- Existing consumer projects (e.g., `punitexamples`) build and test successfully against the new multi-module artifacts.
- ArchUnit test confirms `punit-core` has zero `junit-jupiter-engine` imports in production code.
- ArchUnit test confirms `punit-sentinel` has zero `junit-jupiter-engine` imports.

---

## Phase 2: Dimension-Scoped Assertion API

**Addresses:** REQ-S03, DD-01, DD-02

**Objective:** Introduce first-class support for the two dimensions of nondeterminism (functional and latency) at the assertion level, giving test authors explicit control over which dimensions a probabilistic test exercises. With the module boundary established in Phase 1, these additions land cleanly in `punit-core` (`UseCaseOutcome`, `SampleResultAggregator`) and `punit-junit5` (`ResultPublisher`, verdict rendering).

### 2.1 Changes to `UseCaseOutcome` (`punit-core`)

**Current state:** `UseCaseOutcome` has a single `assertAll()` method that throws `AssertionError` if any criterion fails (postconditions, expected value matching, duration constraint). The framework treats the outcome as a single pass/fail signal.

**Changes:**

Add two new assertion methods:

- **`assertContract()`** — Evaluates only functional postconditions and expected value matching. Throws `AssertionError` if any postcondition fails. If no service contract is configured on the outcome (i.e., no `PostconditionEvaluator` and no expected value), throws a misconfiguration error (`IllegalStateException` or similar) — the developer has asked to assert a contract that does not exist.

- **`assertLatency()`** — Evaluates only the duration constraint. Throws `AssertionError` if execution time exceeds the configured limit. If no `DurationConstraint` is configured (i.e., `durationResult` is null), throws a misconfiguration error — the developer has asked to assert latency without configuring a duration constraint.

**Modify `assertAll()`** to become adaptive:

- If both a service contract and a duration constraint are configured: assert both (conjunctive pass, current behaviour).
- If only a service contract is configured: assert contract only.
- If only a duration constraint is configured: assert latency only.
- If neither is configured: throw a misconfiguration error — there is nothing to assert.

The existing `fullySatisfied()` method retains its current semantics (checks all three criteria: postconditions, expected value, duration). The new assertion methods are the primary API for probabilistic test methods; `fullySatisfied()` remains available for non-PUnit use.

### 2.2 Dimension Signalling

The framework needs to know which dimensions a test method exercised so that the verdict includes only relevant statistical detail (DD-02). The assertion methods on `UseCaseOutcome` must communicate the asserted dimensions back to the framework.

**Approach:** Each assertion method records the dimensions it covers on a thread-scoped or sample-scoped context. The `SampleResultAggregator` (or a companion object) accumulates the set of dimensions asserted across the first sample. Subsequent samples are expected to assert the same dimensions (a consistency check may warn if they diverge).

Concrete mechanism:

- `UseCaseOutcome` accepts an `AssertionContext` (or uses a thread-local) that records which `assertXxx()` method was called.
- The framework reads the asserted dimensions after each sample and uses them to:
  - Record functional pass/fail in the aggregator (if contract was asserted).
  - Record latency measurement in the aggregator (if latency was asserted).
  - Ignore dimensions that were not asserted.

### 2.3 Changes to `SampleResultAggregator` (`punit-core`)

**Current state:** Tracks overall pass/fail count and collects `successfulLatenciesMs`. A sample either succeeds or fails as a whole.

**Changes:**

- Add per-dimension tracking using `Optional` to distinguish "not asserted" from "asserted with zero counts":
  - `Optional<Integer> functionalSuccesses()`, `Optional<Integer> functionalFailures()` — present only when the functional dimension is asserted.
  - `Optional<Integer> latencySuccesses()`, `Optional<Integer> latencyFailures()` — present only when the latency dimension is asserted.
- The existing `successes` / `failures` / `observedPassRate` fields become the composite (conjunctive) result — a sample is an overall success only if all asserted dimensions pass.
- Add `isFunctionalAsserted()` and `isLatencyAsserted()` — two explicit boolean queries reflecting the two-dimension model (DD-01). No generic "dimensions" collection.
- Latency measurements (`successfulLatenciesMs`) are already collected; extend to collect all latency measurements (not just successful ones) when latency is an asserted dimension, since the latency verdict needs the full distribution.

### 2.4 Changes to Verdict Rendering (`punit-junit5`)

**Current state:** `ResultPublisher` renders a single verdict with optional latency summary. `StatisticalExplanationBuilder` produces a single `StatisticalExplanation`.

**Changes:**

- `ResultPublisher.printConsoleSummary()` and `buildReportEntries()` receive the asserted dimension flags from the aggregator.
- If only contract is asserted: verdict body contains functional statistical detail only.
- If only latency is asserted: verdict body contains latency statistical detail only.
- If both are asserted: verdict body contains per-dimension sections, each with its own baseline, threshold, observed rate, and individual pass/fail. The top-level verdict is the conjunction.
- In all cases, raw failure counts are prominently displayed (DD-04).
- Each verdict includes a correlation ID (see Phase 4, section 4.2.1) in the console output and, if the verdict is FAIL, in the `AssertionError` message. This enables operators to cross-reference JUnit report line items with full verdict events in observability systems.

### 2.5 Backward Compatibility

- Existing tests that call `assertAll()` continue to work without modification. The adaptive behaviour preserves current semantics when both dimensions are configured, and silently omits unconfigured dimensions.
- Tests that do not call any assertion method on `UseCaseOutcome` (relying on the framework to detect assertion errors) continue to work — the framework's existing interception of `AssertionError` is unchanged.
- The `assertContract()` and `assertLatency()` methods are pure additions. No existing API is removed or renamed.

### 2.6 Verification Criteria

- Unit tests for `assertContract()`, `assertLatency()`, and adaptive `assertAll()` on `UseCaseOutcome` (in `punit-core`).
- Unit tests for misconfiguration detection (missing contract, missing duration constraint, neither configured).
- Unit tests for `SampleResultAggregator` per-dimension tracking (in `punit-core`).
- Integration tests (via TestKit, in `punit-junit5`) demonstrating:
  - A `@ProbabilisticTest` that calls `assertContract()` produces a verdict with functional detail only.
  - A `@ProbabilisticTest` that calls `assertLatency()` produces a verdict with latency detail only.
  - A `@ProbabilisticTest` that calls `assertAll()` with both dimensions configured produces a combined verdict.
  - A `@ProbabilisticTest` that calls `assertLatency()` without a duration constraint fails with a clear misconfiguration error.
- Existing test suite passes without modification.

---

## Phase 3: Per-Dimension Baselines and Spec Resolution

**Addresses:** DD-01 (per-dimension specs), REQ-S02

**Objective:** Separate functional and latency baselines into independent spec files. Introduce the `SpecRepository` abstraction and layered fallback resolution. With the module boundary in place (Phase 1), the `SpecRepository` interface and `LayeredSpecRepository` land in `punit-core`. With the assertion API in place (Phase 2), the framework knows which dimensions to resolve specs for.

### 3.1 Per-Dimension Baseline Specs

**Current state:** `MeasureStrategy` produces a single `ExecutionSpecification` per use case, written to `specs/{UseCaseId}.yaml`. The spec contains both functional statistics (`empiricalBasis`) and latency statistics (`latencyBaseline`). `SpecificationRegistry` resolves a single spec per use case ID.

**Changes:**

Introduce a dimension-qualified spec naming convention:

- `specs/{UseCaseId}.yaml` — Functional baseline spec. Contains `empiricalBasis` (pass/fail statistics) and related fields. No latency data.
- `specs/{UseCaseId}.latency.yaml` — Latency baseline spec. Contains `latencyBaseline` (percentiles, mean, standard deviation) and related fields. No functional data.

Each spec file is self-contained and independently versioned.

**Changes to `MeasureStrategy` / `MeasureSpecGenerator` (`punit-junit5`):**

- When an experiment completes, produce two spec files instead of one (if both dimensions have data):
  - Functional spec: populated from the aggregator's pass/fail statistics.
  - Latency spec: populated from the aggregator's latency measurements.
- If the use case has no duration constraint (no latency data collected), produce only the functional spec.
- If the use case has no service contract (latency-only use case), produce only the latency spec.

**Changes to `ExecutionSpecification` (`punit-core`):**

- Add a `dimension` field (or equivalent) indicating whether the spec covers functional or latency data. This is metadata within the YAML, not a separate class.
- Existing fields remain; each spec simply omits the fields irrelevant to its dimension.

**Backward compatibility for existing single-file specs:**

- The spec resolver must handle legacy single-file specs that contain both dimensions. When a legacy spec is loaded, the resolver extracts the relevant dimension's data. This ensures existing checked-in specs continue to work without manual migration.
- A one-time migration utility (or Gradle task) may be provided to split legacy specs into per-dimension files, but this is optional — the framework handles both formats transparently.

### 3.2 `SpecRepository` Interface (`punit-core`)

**Current state:** `SpecificationRegistry` is instantiated directly by `ConfigurationResolver.loadSpec()` with a hardcoded default path. `BaselineRepository` is similarly filesystem-bound.

**Changes:**

Introduce the `SpecRepository` interface in the `spec` package:

```java
public interface SpecRepository {
    Optional<ExecutionSpecification> resolve(String specId);
}
```

`SpecificationRegistry` implements `SpecRepository`. Its constructor continues to accept a `Path` for the specs root directory.

`ConfigurationResolver.loadSpec()` is refactored to accept an injected `SpecRepository` rather than instantiating its own `SpecificationRegistry`. The existing default behaviour (auto-detecting `src/test/resources/punit/specs/`) is preserved when no `SpecRepository` is explicitly provided.

### 3.3 Layered Fallback Resolution (`punit-core`)

Introduce `LayeredSpecRepository` implementing `SpecRepository`:

```java
public class LayeredSpecRepository implements SpecRepository {
    private final List<SpecRepository> layers; // ordered: first match wins

    public Optional<ExecutionSpecification> resolve(String specId) {
        for (SpecRepository layer : layers) {
            Optional<ExecutionSpecification> result = layer.resolve(specId);
            if (result.isPresent()) return result;
        }
        return Optional.empty();
    }
}
```

Default construction (used by `ConfigurationResolver` when no explicit repository is provided):

1. **Environment-local layer:** A `SpecificationRegistry` rooted at the path specified by system property `punit.spec.dir` or environment variable `PUNIT_SPEC_DIR`. Skipped if neither is set.
2. **Classpath layer:** A `SpecificationRegistry` rooted at the existing default path (`punit/specs/` on the classpath).

### 3.4 Per-Dimension Spec Resolution

With per-dimension spec files, the spec ID must be dimension-qualified. The framework resolves specs as follows:

- When loading a functional baseline: resolve `{UseCaseId}` (the existing convention). If a legacy single-file spec is found, extract functional data from it.
- When loading a latency baseline: resolve `{UseCaseId}.latency`. If not found and a legacy single-file spec exists with a `latencyBaseline` section, extract latency data from it.

This resolution is internal to `SpecificationRegistry`. The `SpecRepository` interface operates on string spec IDs; the dimension qualification is part of the ID.

### 3.5 Changes to `ConfigurationResolver` (`punit-junit5`)

- `loadSpec()` delegates to the injected `SpecRepository` (or the default `LayeredSpecRepository`).
- When the probabilistic test has asserted both dimensions (known from Phase 2's dimension signalling), two spec lookups occur: one for functional, one for latency. Each may resolve from a different layer (e.g., latency from environment-local, functional from classpath).
- `ResolvedConfiguration` is extended to carry per-dimension spec references (functional spec, latency spec), each of which may be present or absent.

### 3.6 Changes to `ThresholdDeriver` and `LatencyThresholdDeriver` (`punit-core`)

- `ThresholdDeriver` continues to derive the functional pass rate threshold from the functional spec's `empiricalBasis`. No change to its API, but it now receives only the functional spec.
- `LatencyThresholdDeriver` derives latency thresholds from the latency spec's `latencyBaseline`. It now receives only the latency spec.
- Each deriver operates on its own spec independently.

### 3.7 Backward Compatibility

- Legacy single-file specs (containing both functional and latency data) are handled transparently by the resolution mechanism. No existing spec files need to be modified.
- Existing `@ProbabilisticTest` methods that reference a `spec` by use case ID continue to work. The framework resolves the functional spec from the existing file and the latency spec from either a `.latency.yaml` file or the legacy file's `latencyBaseline` section.
- The `SpecRepository` interface is additive. `SpecificationRegistry` gains a new interface but retains its existing behaviour.
- If `PUNIT_SPEC_DIR` / `punit.spec.dir` is not set, the environment-local layer is absent and resolution behaves identically to the current system.

### 3.8 Verification Criteria

- Unit tests for `SpecRepository` interface and `LayeredSpecRepository` (in `punit-core`).
- Unit tests for `SpecificationRegistry` resolving dimension-qualified spec IDs (`{id}` and `{id}.latency`) (in `punit-core`).
- Unit tests for legacy spec compatibility (single-file spec with both dimensions).
- Unit tests for `ConfigurationResolver` with layered resolution, including environment-local override (in `punit-junit5`).
- Integration tests (via TestKit, in `punit-junit5`) demonstrating:
  - An experiment producing separate functional and latency spec files.
  - A probabilistic test consuming per-dimension specs from different sources (one environment-local, one classpath).
  - A probabilistic test consuming a legacy single-file spec — both dimensions extracted correctly.
- Existing test suite passes without modification.

---

## Phase 4: Sentinel Runtime

**Addresses:** REQ-S04, REQ-S05, REQ-S08, REQ-S09, REQ-S10

**Objective:** Introduce the `punit-sentinel` module — a non-JUnit runtime that executes probabilistic tests and experiments against a live environment, dispatching verdicts to configurable sinks. This phase builds on all prior phases: the module boundary (Phase 1), the dimension-scoped assertion API (Phase 2), and the spec resolution abstraction (Phase 3).

### 4.1 Module Structure

```
punit/
├── punit-core/
├── punit-junit5/
├── punit-sentinel/
│   ├── build.gradle.kts             # depends on punit-core only
│   └── src/main/java/
│       └── org/javai/punit/sentinel/
│           ├── SentinelRunner.java
│           ├── SentinelConfiguration.java
│           ├── SentinelResult.java
│           ├── EnvironmentMetadata.java
│           └── verdict/
│               ├── WebhookVerdictSink.java
│               └── CompositeVerdictSink.java
└── ...
```

**`punit-sentinel/build.gradle.kts`:**

```kotlin
dependencies {
    api(project(":punit-core"))
    // HTTP client for WebhookVerdictSink (e.g., java.net.http — zero additional deps)
    // NO JUnit dependency
}
```

### 4.2 `VerdictSink` Interface (REQ-S05)

Defined in `punit-core` (so both `punit-junit5` and `punit-sentinel` can use it):

```java
public interface VerdictSink {
    void accept(VerdictEvent event);
}
```

`VerdictEvent` is a record containing:

- `String correlationId` — a short, unique, grep-able token (e.g., 6 hex characters from a UUID) generated at verdict time. See section 4.2.1.
- `String testName` — the probabilistic test method identifier.
- `String useCaseId` — the use case being exercised.
- `boolean passed` — the composite verdict.
- `Map<String, String> reportEntries` — the structured data from `ResultPublisher.buildReportEntries()`.
- `Map<String, String> environmentMetadata` — environment context (REQ-S09), empty in JUnit context.
- `Instant timestamp` — when the verdict was produced.

#### 4.2.1 Verdict Correlation ID

Operators need to navigate between JUnit test reports and PUnit verdict events. JUnit reports show pass/fail line items in CI dashboards and IDE test runners. PUnit verdict events carry the full statistical detail and are dispatched to observability systems via `VerdictSink`. A correlation ID bridges the two.

Each verdict execution generates a short, unique correlation ID (e.g., `v:a3f8c2`). This ID appears in:

1. **The PUnit console verdict** — captured in JUnit XML `<system-out>`:
   ```
   ═ VERDICT: PASS ══════════════════════════ PUnit ═
   [v:a3f8c2] PaymentGatewayTest.processPayment
   Observed pass rate: 95.0% (95/100) >= required: 90.0%
   ```
2. **The JUnit failure message** — if the PUnit verdict is FAIL, the correlation ID is included in the `AssertionError` message, making it visible in every CI dashboard and IDE test runner.
3. **The `VerdictEvent`** — dispatched to all `VerdictSink` instances, carrying the same ID.

An operator seeing `[v:a3f8c2]` in their CI report can search their observability system for the same ID to retrieve the full statistical detail. The ID does not encode verdict content — it is a random per-execution token whose sole purpose is cross-referencing.

The correlation ID is generated in `punit-core` (since it is part of the `VerdictEvent` record) and consumed by both `punit-junit5` (verdict rendering in `ResultPublisher`) and `punit-sentinel` (verdict dispatch).

**Implementations:**

- **`LogVerdictSink`** (in `punit-core`) — Delegates to `PUnitReporter`. Default sink.
- **`WebhookVerdictSink`** (in `punit-sentinel`) — Posts `VerdictEvent` as JSON to a configurable HTTP endpoint using `java.net.http.HttpClient`. Configurable: URL, timeout, headers (for API keys), retry policy.
- **`CompositeVerdictSink`** (in `punit-sentinel`) — Dispatches to multiple sinks. Constructed with a list of `VerdictSink` instances.

### 4.3 `SentinelRunner` (REQ-S04)

The core execution engine for the Sentinel. Lives in `punit-sentinel`.

```java
public class SentinelRunner {

    private final SentinelConfiguration configuration;

    public SentinelRunner(SentinelConfiguration configuration);

    /** Execute all registered experiments, producing/refreshing baseline specs. */
    public SentinelResult runExperiments();

    /** Execute all registered probabilistic tests against current baselines. */
    public SentinelResult runTests();

    /** Execute a specific use case's probabilistic tests. */
    public SentinelResult runTests(String useCaseId);
}
```

**`SentinelConfiguration`** (builder pattern):

- `useCaseClasses` — List of use case classes to exercise (explicit registration).
- `specRepository` — The `SpecRepository` to use for spec resolution (defaults to `LayeredSpecRepository`).
- `useCaseFactory` — The `UseCaseFactory` for instantiation (defaults to reflection-based construction).
- `verdictSinks` — List of `VerdictSink` instances (defaults to `LogVerdictSink`).
- `environmentMetadata` — `EnvironmentMetadata` instance (REQ-S09).
- Budget overrides (time, tokens) — applied via system properties or environment variables through the existing `ConfigurationResolver` chain.

**Execution flow (test mode):**

For each registered use case class:
1. Scan for methods annotated with `@ProbabilisticTest`.
2. For each method:
   a. Resolve configuration via `ConfigurationResolver`.
   b. Load per-dimension specs via `SpecRepository` (Phase 3's layered resolution).
   c. Derive thresholds via `ThresholdDeriver` / `LatencyThresholdDeriver`.
   d. Instantiate the use case via `UseCaseFactory` (Phase 1's extracted factory).
   e. Execute the N-sample loop: invoke the method, record pass/fail in `SampleResultAggregator` (with Phase 2's per-dimension tracking), check `EarlyTerminationEvaluator` and `BudgetOrchestrator` after each sample.
   f. Compute verdict via `FinalVerdictDecider`.
   g. Dispatch verdict (with environment metadata) to all `VerdictSink` instances.

**Execution flow (experiment mode, REQ-S08):**

For each registered use case class:
1. Scan for methods annotated with `@MeasureExperiment`.
2. For each method:
   a. Parse experiment configuration.
   b. Instantiate the use case.
   c. Execute the N-sample loop, recording outcomes.
   d. Generate per-dimension spec files (Phase 3's spec model).
   e. Write specs to the environment-local spec directory (`punit.spec.dir` / `PUNIT_SPEC_DIR`).
   f. Report experiment completion via `VerdictSink`.

Only `@MeasureExperiment` is supported in the Sentinel context. `@ExploreExperiment` and `@OptimizeExperiment` are inherently interactive — they involve comparing configurations, examining diffs, and adjusting parameters iteratively. This is developer-at-the-keyboard work, not automated watchdog work. The Sentinel's experiment mode exists to establish and refresh baselines, which is precisely what measure experiments do.

### 4.4 `EnvironmentMetadata` (REQ-S09)

```java
public record EnvironmentMetadata(
    String environmentId,     // e.g., "prod", "staging", "us-east-1"
    String instanceId         // for multi-instance deployments
) {
    public static EnvironmentMetadata fromEnvironment() {
        return new EnvironmentMetadata(
            resolve("PUNIT_ENVIRONMENT", "punit.environment", "unknown"),
            resolve("PUNIT_INSTANCE_ID", "punit.instanceId", hostname())
        );
    }
}
```

Environment metadata is injected into every `VerdictEvent` produced by the Sentinel. In the JUnit context (where `VerdictSink` may also be used), the metadata is empty or default-valued.

### 4.5 `SentinelResult` (REQ-S10)

```java
public record SentinelResult(
    int totalTests,
    int passed,
    int failed,
    int skipped,                      // e.g., missing spec
    List<VerdictEvent> verdicts,      // individual verdict details
    Duration totalDuration
) {
    public boolean allPassed() { return failed == 0 && skipped == 0; }
}
```

The caller (scheduler, HTTP handler, operator script) uses `SentinelResult` to determine next steps (alerting, circuit-breaking, etc.). The Sentinel itself takes no action beyond reporting.

### 4.6 Annotation Scanning

The `SentinelRunner` must read annotation metadata from `@ProbabilisticTest` and `@MeasureExperiment` without JUnit's extension lifecycle.

**Decision (DD-05):** Annotations remain exactly as they are — single definitions in `punit-core` with their JUnit meta-annotations (`@TestTemplate`, `@ExtendWith`) intact. The Sentinel's classpath includes `junit-jupiter-api` (the annotation/interface JAR) but not `junit-jupiter-engine` (the test runner). The JUnit meta-annotations are inert metadata without the engine — they cause no JUnit behaviour.

The `SentinelRunner` reads the PUnit annotation attributes (`samples`, `minPassRate`, `spec`, `confidence`, etc.) reflectively to determine execution parameters. It ignores the JUnit meta-annotations entirely. No annotation splitting, wrapping, or restructuring is required.

### 4.7 Backward Compatibility

- `punit-sentinel` is a new module. No existing code is affected.
- The `VerdictSink` interface in `punit-core` is additive. Existing `ResultPublisher` behaviour is unchanged; `VerdictSink` is an additional dispatch channel.
- Annotations are unchanged (DD-05) — existing consumers see no change in annotation attributes, package paths, or import statements.

### 4.8 Verification Criteria

- Unit tests for `SentinelRunner` executing a test loop against a mock use case.
- Unit tests for `WebhookVerdictSink` (mock HTTP endpoint).
- Unit tests for `CompositeVerdictSink`.
- Unit tests for `EnvironmentMetadata.fromEnvironment()`.
- Integration test: `SentinelRunner.runExperiments()` produces spec files in a configured directory, followed by `SentinelRunner.runTests()` consuming those specs and producing verdicts.
- Integration test: `SentinelRunner.runTests()` with layered spec resolution — environment-local spec overrides classpath spec.
- `SentinelResult` correctly aggregates per-test verdicts.
- Existing `punit-junit5` test suite passes — Sentinel introduction does not affect JUnit-based testing.

---

## Phase 5: Architectural Guidance and Documentation

**Addresses:** REQ-S11

**Objective:** Produce documentation guiding PUnit adopters on application module layout for projects that use probabilistic testing and deploy a Sentinel.

### 5.1 `docs/ARCHITECTURE-GUIDE.md`

**Contents:**

1. **Introduction** — Why application architecture matters for probabilistic testing and environment-aware monitoring.

2. **Reference Module Layout:**
   ```
   app-stochastic          punit-core
     ↑         ↑              ↑
   app-main    app-usecases ──┘
                 ↑        ↑
            test suite    app-sentinel
   ```
   - Detailed description of each module's role and dependencies.
   - Example `build.gradle.kts` for each module.

3. **Why Use Cases Are Not Test Code:**
   - Use cases define the contract between the application and its stochastic dependencies.
   - They are consumed by both the JUnit test suite and the Sentinel.
   - Placing them in a test source set makes them unavailable to the Sentinel.
   - The `app-usecases` module is the bridge — it depends on `app-stochastic` (to invoke stochastic services) and `punit-core` (for `UseCaseOutcome`, `ServiceContract`, etc.).

4. **Why Stochastic Services Should Be Isolable:**
   - The Sentinel binary should be lightweight.
   - Separating stochastic integrations from the rest of the application keeps the Sentinel's dependency footprint minimal.
   - This also forces a clean API boundary around non-deterministic behaviour, which improves testability regardless of PUnit.

5. **The Sentinel Deployment Workflow:**
   - Run experiments in the target environment → environment-local specs produced.
   - Run tests → specs consumed via layered fallback.
   - Verdicts dispatched to observability infrastructure.
   - Scheduling is external (cron, K8s CronJob, cloud scheduler).

6. **Worked Example:**
   - A concrete example application (e.g., the existing shopping basket / payment gateway / LLM examples from `punitexamples`) structured according to the reference layout.
   - Gradle build files, use case module, Sentinel bootstrap code.

### 5.2 Configuration Reference

Update or create `docs/CONFIGURATION.md` documenting:

- All system properties (`punit.*`) with their environment variable equivalents.
- `PUNIT_SPEC_DIR` / `punit.spec.dir` — environment-local spec directory.
- `PUNIT_ENVIRONMENT` / `punit.environment` — environment identifier for Sentinel verdicts.
- `PUNIT_INSTANCE_ID` / `punit.instanceId` — Sentinel instance identifier.
- Budget variables: `PUNIT_SUITE_TIME_BUDGET_MS`, `PUNIT_SUITE_TOKEN_BUDGET`, `PUNIT_TIME_BUDGET_MS`, `PUNIT_TOKEN_BUDGET`.

### 5.3 Verification Criteria

- Documentation reviewed for accuracy against implemented behaviour.
- Worked example builds and runs (tests + Sentinel) successfully.
- All configuration variables documented and tested.

---

## Phase 6: User Documentation

**Objective:** Update all user-facing documentation to reflect the changes introduced in Phases 1–4. Documentation must be accurate against the implemented behaviour and must not lag behind the codebase.

### 6.1 User Guide

Update the PUnit user guide to cover:

- **Module decomposition:** Which artifact to depend on and when (`punit-core` for use case authors, `punit-junit5` for test developers, `punit-sentinel` for Sentinel deployers). Migration guidance for consumers upgrading from the single `punit` artifact.
- **Dimension-scoped assertions:** How to use `assertContract()`, `assertLatency()`, and `assertAll()`. When to use each. What misconfiguration errors look like and how to fix them.
- **Per-dimension baselines:** How experiments now produce separate functional and latency spec files. How the framework handles legacy single-file specs. The spec naming convention (`{UseCaseId}.yaml` and `{UseCaseId}.latency.yaml`).
- **Layered spec resolution:** How `PUNIT_SPEC_DIR` / `punit.spec.dir` works. The fallback order. How this supports the experiment-first Sentinel workflow.
- **Sentinel usage:** How to configure and run the Sentinel. `SentinelConfiguration`, `VerdictSink` implementations, environment metadata, lifecycle API.
- **Verdicts as triage signals (DD-04):** Reinforce that a PUnit PASS does not mean "ignore failures." Guidance on how operators should interpret verdicts.

### 6.2 Statistical Companion

Review the statistical companion document for any sections that require updates:

- The two-dimension model may affect how statistical concepts are explained (functional pass rate vs. latency distribution are distinct statistical profiles).
- Per-dimension threshold derivation: the statistical companion should explain that functional and latency thresholds are derived independently from their respective baselines.
- If the feasibility gate or Wilson score calculations are affected by the per-dimension model, update accordingly.

If no changes are needed, document the review outcome (i.e., confirm that the statistical companion remains accurate as-is).

### 6.3 Configuration Reference

Ensure `docs/CONFIGURATION.md` (introduced in Phase 5) is complete and cross-referenced from the user guide.

### 6.4 Verification Criteria

- All user guide sections reviewed for accuracy against implemented behaviour.
- Statistical companion reviewed; changes made if needed, or explicitly confirmed as accurate.
- No undocumented features — every user-facing API and configuration option introduced in Phases 1–4 has corresponding documentation.

---

## Phase 7: PUnit Examples Update

**Objective:** Update the companion project `punitexamples` to demonstrate the features introduced in this initiative. Examples are the primary learning tool for adopters — they must reflect current best practice.

### 7.1 Latency Experiments and Assertions

Add examples demonstrating:

- A use case with a `DurationConstraint` on its `ServiceContract`.
- A `@MeasureExperiment` that produces a latency baseline spec (`{UseCaseId}.latency.yaml`).
- A `@ProbabilisticTest` that calls `assertLatency()` — asserting only the latency dimension.
- A `@ProbabilisticTest` that calls `assertAll()` — asserting both functional and latency dimensions, producing a combined verdict.
- A `@ProbabilisticTest` that calls `assertContract()` — asserting only functional correctness, demonstrating that latency information is absent from the verdict.

These examples should use a realistic scenario (e.g., the existing LLM or payment gateway examples) where latency nondeterminism is a genuine concern.

### 7.2 Sentinel Example

Add at least one complete Sentinel implementation example, structured according to the reference module layout from the architectural guidance (Phase 5):

```
example-sentinel/
├── app-stochastic/          # Stochastic service integration (e.g., LLM client wrapper)
├── app-usecases/            # Use case definitions and service contracts
├── app-main/                # Main application (depends on app-stochastic only)
├── app-sentinel/            # Sentinel deployment
│   └── src/main/java/       # SentinelRunner bootstrap, configuration
└── test-suite/              # JUnit probabilistic tests (depends on app-usecases + punit-junit5)
```

The example must demonstrate:

- The `app-usecases` module as a shared dependency between the test suite and the Sentinel.
- `SentinelRunner` configuration with at least `LogVerdictSink`.
- Running experiments to produce environment-local specs.
- Running tests that consume those specs via layered fallback.
- Environment metadata in verdict output.
- The fact that the main application has no PUnit dependency.

### 7.3 Existing Examples Update

Review and update existing examples (shopping basket, payment gateway, LLM integration) to:

- Use the new `punit-junit5` artifact dependency (instead of `punit`).
- Use `assertContract()` where appropriate (most existing examples assert functional correctness only).
- Confirm they build and pass against the new multi-module artifacts.

### 7.4 Verification Criteria

- All new examples build and run successfully.
- Latency examples produce latency spec files and latency-specific verdicts.
- Sentinel example runs the full experiment → test workflow and produces verdicts.
- Existing examples build and pass without regression.

---

## Phase Summary

| Phase | Focus | Key Deliverables | Breaking Changes |
|-------|-------|------------------|-----------------|
| 1 | Module decomposition | `punit-core` + `punit-junit5` + `punit-sentinel` (skeleton) modules, `UseCaseFactory`, budget validation, meta-artifact, ArchUnit module constraints | None (meta-artifact preserves compatibility) |
| 2 | Dimension-scoped assertions | `assertContract()`, `assertLatency()`, adaptive `assertAll()`, per-dimension verdict rendering | None |
| 3 | Per-dimension baselines and spec resolution | Per-dimension spec files, `SpecRepository` interface, `LayeredSpecRepository`, `PUNIT_SPEC_DIR` | None (legacy specs handled) |
| 4 | Sentinel runtime | `SentinelRunner`, `VerdictSink`, `WebhookVerdictSink`, `EnvironmentMetadata`, lifecycle API | None (new module) |
| 5 | Architectural guidance | `docs/ARCHITECTURE-GUIDE.md`, configuration reference | None (documentation only) |
| 6 | User documentation | User guide updates, statistical companion review, configuration reference | None (documentation only) |
| 7 | Examples | Latency examples, Sentinel example, existing examples updated | None (companion project) |

Each phase is independently releasable. The version sequence could be:

- Phase 1 → **0.4.0** (module split)
- Phase 2 → **0.5.0** (dimension-scoped assertions)
- Phase 3 → **0.6.0** (spec model evolution)
- Phase 4 → **0.7.0** (Sentinel)
- Phases 5–7 → included in **0.7.0** or as a **0.7.1** patch
