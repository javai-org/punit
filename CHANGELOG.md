# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Removed (breaking)
- **`@FactorSetter`, `@FactorGetter` annotations.** Previously deprecated in 0.6.0. Factor values for EXPLORE and OPTIMIZE experiments must be supplied via `UseCaseProvider.registerWithFactors(...)`; mutable-setter patterns are no longer supported.
- **`UseCaseProvider.registerAutoWired(...)`** (and the parallel `UseCaseFactory.registerAutoWired(...)`). Previously deprecated in 0.6.0. Use `registerWithFactors(...)` instead.
- **`OptimizeExperiment.initialControlFactorValue`** (inline string attribute). Could not express typed initial values. Use `initialFactor` with a static method returning the typed value.
- **`OptimizeExperiment.initialControlFactorSource`** (renamed). The attribute is now **`initialFactor`** with identical semantics — a static no-arg method on the experiment class that returns the starting control factor value. `initialFactor` is required.
- **`@FactorGetter` fallback path** in `OptimizeStrategy`. Initial factor values now come solely from the `initialFactor` method — the reflective fallback that read a `@FactorGetter`-annotated accessor off the use case is gone.
- **`ExploreExperiment.expiresInDays`**. Baseline expiration is a property of MEASURE-produced baseline specs, not exploration specs. `@MeasureExperiment` keeps the attribute. Exploration spec YAML no longer carries expiration metadata.

This is the first breaking release of the experiment DX refactor described in `plan/DES-EXPERIMENT-DX.md`. Deeper structural changes (factor-record typing, `@FactorSource` semantics, `@ConfigSource` / `NamedConfig` / `@Factor` / `@ControlFactor` removal) land in a follow-up PR.

## [0.6.0] - 2026-04-16

### Changed
- **Annotations consolidation (breaking).** All user-facing PUnit annotations — `@ProbabilisticTest`, `@MeasureExperiment`, `@ExploreExperiment`, `@OptimizeExperiment` — now live in `punit-core`. Previously, the three experiment annotations lived in `punit-junit5` because they carried `@ExtendWith(ExperimentExtension.class)`; this meta-annotation is now removed. JUnit wiring is handled entirely via ServiceLoader auto-registration (already in place). Users of the `org.javai.punit` Gradle plugin see no change. Maven users and plain-Gradle users without the plugin must set `junit.jupiter.extensions.autodetection.enabled=true` in `junit-platform.properties` — see `docs/MAVEN-CONFIGURATION.md`. The fully-qualified annotation names (`org.javai.punit.api.MeasureExperiment` etc.) are unchanged; only the publishing module differs. Sentinel specs in modules that depend on `punit-core` (and not `punit-junit5`) now compile without the reflection-based workaround that shipped in 0.5.x.
- **Immutable use case pattern.** `@FactorSetter`, `@FactorGetter`, and `UseCaseProvider.registerAutoWired` are deprecated and scheduled for removal. Mutable factor patterns violate the i.i.d. assumption required for valid statistical inference. Use `UseCaseProvider.registerWithFactors` to configure factors at construction time. See the Immutable Use Cases section in the user guide.
- **Statistics rework for contract-linked latency.** Latencies are now formally categorised by whether the associated service contract passed or failed. This addresses a prior weakness where latency statistics could be computed over a population that mixed successful and failed invocations, distorting percentile estimates. The change is of course synchronised with the Javai oracle project [javai-R](https://github.com/javai-org/javai-R).
- **Experiment output locations split by semantics.** MEASURE specs (inputs to subsequent `@ProbabilisticTest` runs) still default to `src/test/resources/punit/specs/` and are intended to be committed. EXPLORE and OPTIMIZE outputs (human-review artefacts only) now default to `build/punit/explorations/` and `build/punit/optimizations/` respectively. Downstream projects that were relying on the old default locations for EXPLORE/OPTIMIZE outputs must set `punit.explorations.outputDir` / `punit.optimizations.outputDir` explicitly to restore the prior behaviour. See the Experiment Output Directories section in the user guide.

### Added
- `@ConfigSource` and `@InputSource` can now be combined on the same EXPLORE experiment method, enabling exploration across both configuration and input axes simultaneously.
- Additional validation for `@Input` annotation targets, catching misconfiguration at setup time rather than during sample execution.
- Conformance test coverage extended to match the javai-R statistical oracle for additional edge cases.

### Removed
- **`zjsonpatch` dependency.** PUnit no longer depends on `zjsonpatch`. Downstream consumers that relied on the transitive presence of this library must declare it directly.

### Deprecated
- `@FactorSetter`, `@FactorGetter`, `UseCaseProvider.registerAutoWired` — see **Changed** above.

## [0.5.2] - 2026-03-22

### Added
- **Latency distribution always visible** — the HTML report now shows p50, p95, and p99 latency values for every test with successful samples, even when no `@Latency` thresholds are configured. Previously, tests without explicit latency thresholds showed dashes in these columns.
- **Per-cell latency colour coding** — latency cells in the HTML report are colour-coded per percentile: green when an asserted threshold is met, red when exceeded, and muted grey when no threshold is configured (observational only).
- **`punitVerify` Gradle task** — a post-execution verification task (wired into `check`) that reads verdict XMLs and fails the build only when a probabilistic test's statistical verdict is FAIL. Individual sample failures during test execution no longer break the build; they are reported as JUnit "aborted" (skipped) rather than "failed", deferring the pass/fail decision to the aggregate verdict.

### Changed
- Framework console output now routes to stdout/stderr instead of Log4j, ensuring visibility in all build environments without Log4j configuration.

## [0.5.1] - 2026-03-21

### Fixed
- Improved accuracy of baseline threshold derivation for high-reliability use cases. The confidence interval used to compute `minPassRate` in MEASURE experiment specs now uses the Wilson score method consistently, matching the statistical engine used elsewhere in PUnit. Previously generated specs with very high observed pass rates (near 100%) may produce slightly different `minPassRate` values when re-measured. Re-running affected MEASURE experiments is recommended.

## [0.5.0] - 2026-03-13

### Added
- **HTML test report** — standalone HTML report summarising all probabilistic test verdicts from a test run. Generated via `./gradlew punitReport` from XML verdict files produced during test execution. The report groups results by use case, provides expandable per-test detail (verdict summary, statistical analysis, baseline provenance), latency percentiles, and operator guidance for inconclusive verdicts caused by covariate misalignment.
- Statistical assumptions and limitations disclosure in the HTML report header (collapsed by default)
- `punit-report` module containing XML verdict serialisation, HTML report generation, and report configuration
- `punitReport` Gradle task registered automatically by the PUnit plugin

## [0.4.1] - 2026-03-10

### Changed
- Bumped `org.javai:outcome` transitive dependency from 0.1.0 to 0.2.0 — see [outcome 0.2.0 changelog](https://github.com/javai-org/outcome/releases/tag/v0.2.0) for breaking changes to `Failure` record fields (`exception`, `retryAfter`, `correlationId` now return `Optional`)

## [0.4.0] - 2026-03-10

### Added
- **PUnit Sentinel** — a JUnit-free execution engine for running probabilistic tests and experiments in deployed environments (staging, production). The Sentinel runs the same `@ProbabilisticTest` and `@MeasureExperiment` methods defined for development-time testing, producing environment-specific baselines and dispatching verdicts to observability infrastructure.
- `@Sentinel` annotation for marking reliability specification classes consumed by both JUnit and the Sentinel engine
- `SentinelRunner` API for programmatic test and experiment execution with use-case-level filtering
- `SentinelMain` CLI with commands (`test`, `exp`), options (`--list`, `--useCase <id>`, `--verbose`, `--help`), and structured exit codes
- `createSentinel` Gradle task (via the `org.javai.punit` plugin) that builds an executable fat JAR containing all `@Sentinel` classes, their dependencies, and the Sentinel runtime
- `WebhookVerdictSink` for dispatching verdicts as JSON to HTTP endpoints with configurable URL, timeout, and headers
- Environment metadata tagging on verdicts (`PUNIT_ENVIRONMENT`, `PUNIT_INSTANCE_ID`)
- Three-module decomposition: `punit-core` (JUnit-free engine), `punit-junit5` (JUnit 5 extensions), `punit-sentinel` (Sentinel runtime)
- `UseCaseFactory` in `punit-core` for JUnit-free use case registration, independent of `UseCaseProvider`
- `SpecRepository` interface with `LayeredSpecRepository` for environment-local-first spec resolution
- Dimension-scoped assertion API: `UseCaseOutcome.assertContract()`, `assertLatency()`, `assertAll()`
- Per-dimension baselines and spec resolution (functional and latency specs as separate files)
- ArchUnit-enforced module boundaries: `punit-core` and `punit-sentinel` have zero `org.junit` dependencies

### Changed
- Reliability specifications now use the spec-first model: `@Sentinel` classes in production source sets, consumed by both JUnit (via inheritance) and the Sentinel engine (directly)
- Explicit `@Latency` attribute on `@ProbabilisticTest` overrides baseline-derived thresholds without causing a misconfiguration error

## [0.3.1] - 2026-03-02

### Fixed
- Verdict header contradicting verdict body when using spec-derived thresholds — `BernoulliTrialsConfig` was not synced with `TestConfiguration` after baseline `minPassRate` derivation, causing the verdict summary to use stale values

## [0.3.0] - 2026-03-02

### Added
- Latency assertions via `@Latency` annotation with per-percentile thresholds (p50, p90, p95, p99)
- Automatic latency threshold derivation from baseline specs using confidence-interval upper bounds
- Advisory latency mode (default): breaches warn in output but do not fail the test
- Latency enforcement mode (`-Dpunit.latency.enforce=true`) for CI environments with consistent hardware
- Latency feasibility gate for VERIFICATION intent — rejects undersized sample configurations before execution
- Indicative marker for latency results when sample size is below the statistical minimum
- Latency data captured in MEASURE and EXPLORE experiment specs (percentiles, mean, standard deviation, sample count)
- Latency analysis section in transparent statistics output

### Changed
- Improved EXPLORE experiment output format with diff-anchor lines

## [0.2.0] - 2026-02-15

### Added
- Release lifecycle Gradle tasks (`release`, `tagRelease`)
- Centralised version in `gradle.properties`
- `CHANGELOG.md` with validation gate in the release task
- Test intent system: `VERIFICATION` (evidential) and `SMOKE` (sentinel) modes
- Feasibility gate using Wilson score lower bound for verification tests
- Diff-anchor lines in explore experiment output
- Infeasibility fail-fast for infeasible thresholds

## [0.1.0] - 2025-12-15

Initial release of PUnit — a JUnit 5 extension framework for probabilistic
unit testing of non-deterministic systems.

### Added
- `@ProbabilisticTest` annotation with configurable samples, minPassRate, and confidence
- `@MeasureExperiment` and `@ExploreExperiment` for baseline measurement and exploration
- `@OptimizeExperiment` for feedback-driven parameter optimization
- Spec-based workflow: measure baseline, generate YAML spec, derive thresholds statistically
- Early termination (impossibility and success-guaranteed detection)
- Time and token cost budgets (method, class, and suite scopes)
- Covariate-aware baseline selection with environmental, temporal, and operational categories
- Design by Contract support via `ServiceContract` and `UseCaseOutcome`
- `@InputSource` for parameterized test inputs (JSON, CSV)
- Instance conformance checking (JSON schema matching)
- Pacing support to avoid rate limits from LLM/API providers
- Statistical engine with Wilson score confidence intervals and threshold derivation
- Verbose statistical explanation output
- Gradle plugin (`org.javai.punit`) for test/experiment task configuration

[Unreleased]: https://github.com/javai-org/punit/compare/v0.6.0...HEAD
[0.6.0]: https://github.com/javai-org/punit/compare/v0.5.2...v0.6.0
[0.5.2]: https://github.com/javai-org/punit/compare/v0.5.1...v0.5.2
[0.5.1]: https://github.com/javai-org/punit/compare/v0.5.0...v0.5.1
[0.5.0]: https://github.com/javai-org/punit/compare/v0.4.1...v0.5.0
[0.4.1]: https://github.com/javai-org/punit/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/javai-org/punit/compare/v0.3.1...v0.4.0
[0.3.1]: https://github.com/javai-org/punit/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/javai-org/punit/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/javai-org/punit/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/javai-org/punit/releases/tag/v0.1.0
