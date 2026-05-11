# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added (JPMS module declarations)

Three of punit's four library modules now ship a
`module-info.java` declaring exports, requires, and ServiceLoader
provides/uses. `module-info.class` is the published artifact-level
expression of the public-surface promise the
`org.javai.punit.internal.*` namespace started.

- **`punit-core`** declares module `org.javai.punit.core`. Exports
  every public package (`api`, `api.spec`, `api.covariate`,
  `api.match`, `runtime`, `verdict`, `statistics`,
  `statistics.transparent`). Targeted exports
  (`exports … to <module>`) grant sibling modules the minimum
  internal-package surface they need —
  `internal.engine.emit` to `punit-report`, `internal.reporting`
  to `punit-report` and `punit-sentinel`. `requires static
  org.junit.jupiter.api` keeps JUnit a compile-time-only
  dependency.
- **`punit-report`** declares module `org.javai.punit.report`.
  `provides org.javai.punit.verdict.VerdictSink with
  org.javai.punit.report.VerdictXmlSink` is the JPMS expression of
  the existing `META-INF/services/` ServiceLoader registration.
- **`punit-sentinel`** declares module
  `org.javai.punit.sentinel`. Explicitly omits `requires` for any
  JUnit module — the compiler now enforces the JUnit-free
  invariant as a property of the module declaration, complementing
  `SentinelArchitectureTest`.
- **`punit-junit5`** has been retired as a published artifact —
  see the *Removed* section below.

### Removed (`org.javai:punit-junit5` Maven coordinate)

`org.javai:punit-junit5` was a dependency-bundler with no
production code. The framework's user-facing annotations
(`@ProbabilisticTest`, `@Experiment`) live in `punit-core/api`;
they are meta-annotated with `@org.junit.jupiter.api.Test` (via
`requires static`) and carry no `@ExtendWith`, so a probabilistic
test reaches the framework by calling
`PUnit.testing(useCase).assertPasses()` inside the test body —
nothing in the JUnit lifecycle needs a punit-supplied extension.
The bundler module added nothing beyond a curated `api` group of
JUnit Jupiter + jackson + log4j that consumers can declare
directly, and its sole content was integration tests and test
subjects (now absorbed into `punit-core`'s test source).

**Migration.** Consumers previously declaring:

```kotlin
testImplementation("org.javai:punit-junit5:<version>")
```

replace it with the direct deps the bundler used to pull in. The
minimal set for an author who writes `@ProbabilisticTest` against
`PUnit.testing(...)` and wants verdict XML emission is:

```kotlin
implementation("org.javai:punit-core:<version>")
testImplementation("org.javai:punit-report:<version>")  // XML VerdictSink
testImplementation("org.junit.jupiter:junit-jupiter")    // JUnit Jupiter
```

Add `jackson-databind` / `jackson-dataformat-csv` only if the
test sources use `@InputSource` for JSON/CSV inputs; add
`log4j-core` (or another SLF4J backend) only if punit's runtime
logging needs to land somewhere.

### Consumer impact

- **Modular consumers** (own `module-info.java`, depend on punit
  via modulepath) — the `exports` clauses are now the
  authoritative public surface. Attempting to import a non-exported
  `internal.*` package fails to compile and at runtime throws
  `IllegalAccessError`. Existing modular consumers may need to
  add `requires org.javai.punit.core` (or
  `org.javai.punit.report` / `org.javai.punit.sentinel`) to their
  own `module-info.java`.
- **Unnamed-module consumers** (no `module-info.java`, classpath
  build) — no source changes required. JPMS export visibility is
  not gated on the classpath path; these consumers continue to see
  every class on punit's JARs and remain guarded by the
  `internal.*` namespace prefix and the ArchUnit rules.

### Build infrastructure

- `punit-core` applies `org.gradlex.extra-java-module-info` 1.13
  to wrap `org.javai:outcome` as an automatic module. Every other
  transitive dependency is already JPMS-aware (real `module-info`
  in jackson / snakeyaml / log4j / JUnit / opentest4j;
  `Automatic-Module-Name` in commons-statistics).

### Changed (public-surface promotion — breaking FQN change)

Authoring-time and verdict-shape types that previously lived under
`internal.*` packages have been promoted to their natural public
locations. The motivating principle: a type that appears in a
public method signature on a public class must itself be in a
public package, so a JPMS-modularised consumer can compile against
the verdict API without reaching into framework internals.

- `org.javai.punit.internal.engine.budget.CostBudgetMonitor.TokenMode`
  (enum) → `org.javai.punit.verdict.TokenMode` (top-level). The
  enum is a verdict-side label naming how token cost was measured
  (NONE / STATIC / DYNAMIC); it is a peer to `ThresholdOrigin` and
  `Verdict`, not a sub-concept of the budget monitor.
- `org.javai.punit.internal.engine.pacing.PacingConfiguration`
  (record) → `org.javai.punit.api.PacingConfiguration` (top-level,
  alongside the existing `Pacing` builder type). The record carries
  authoring-time pacing constraints + computed execution plan; it
  is consumed by the builder API and read off the verdict, both
  user-facing.

### Changed (package layout — internal relocations)

Renderers and adapters that had drifted into public packages have
been relocated to their natural internal homes. No external API
change (all are non-public classes); only their FQNs move.

- `org.javai.punit.verdict.VerdictTextRenderer` →
  `org.javai.punit.internal.reporting.VerdictTextRenderer`
- `org.javai.punit.verdict.VerdictAdapter` →
  `org.javai.punit.internal.runtime.VerdictAdapter`
- `org.javai.punit.verdict.VerdictSinkBus` →
  `org.javai.punit.internal.reporting.VerdictSinkBus`

The `VerdictSink` interface itself stays public at
`org.javai.punit.verdict.VerdictSink`; only the dispatcher moved.

### Migration

Consumers that referenced any of the four promoted types by FQN:

- `org.javai.punit.internal.engine.budget.CostBudgetMonitor.TokenMode`
  → `org.javai.punit.verdict.TokenMode`
- `org.javai.punit.internal.engine.pacing.PacingConfiguration`
  → `org.javai.punit.api.PacingConfiguration`
- The three renderer/adapter relocations affect only consumers that
  were already reaching into the framework's internal-shaped types —
  not a supported pattern, no migration documented.

## [0.7.0-alpha3] - 2026-05-10

> **🧪 Experimental release.** The 0.7.x structural-cleanup arc:
> dead-code excision, package-drift resolution, the
> wire-format alignment with the canonical RP07 schema, and the
> public/internal split made visible at every import statement
> via the new `org.javai.punit.internal.*` namespace. Four merged
> PRs (#140, #141, #142, #143), 25 commits, net ~−4,400 lines.
> Mechanical migration: most consumer changes are find-and-replace
> on import statements. No new framework behaviour; no oracle
> changes.

### Removed (breaking)

- **The `org.javai.punit.contract.*` parallel stack is gone.**
  The package was a stillborn second authoring surface (14
  production classes, 13 test classes) that had been deprecated
  but never excised. Replaced where authors actually used it by
  the new `org.javai.punit.api.match` subpackage carrying
  `ValueMatcher`, `MatchResult`, `StringMatcher`, `JsonMatcher`.
  If you were reaching into `contract.*`, the typed authoring
  surface (`Sampling.matching(...)`, `UseCase.postconditions(...)`)
  is the supported path; `api.match` covers the value-matcher
  use case directly.

### Changed (package layout — breaking FQN change)

- The public/internal split is now structural. Every framework-internal
  package moved under `org.javai.punit.internal.*`:
  - `org.javai.punit.engine.*` → `org.javai.punit.internal.engine.*`
    (criteria, baseline, explore, optimize, covariate, spec, budget,
    pacing, emit, …).
  - `org.javai.punit.reporting` → `org.javai.punit.internal.reporting`.
  - `org.javai.punit.util` → `org.javai.punit.internal.util`.
  - The non-`PUnit` residents of `runtime/` (the emitters
    `BaselineEmitter`, `ExploreEmitter`, `OptimizeEmitter`; the
    resolvers `BaselineProviderResolver`, `TestIdentityResolver`; the
    composer `EmpiricalTestComposer`) moved to
    `org.javai.punit.internal.runtime.*`. `PUnit` itself stays public
    at `org.javai.punit.runtime.PUnit`.
- The public packages — `api`, `api.spec`, `api.covariate`, `api.match`,
  `runtime` (for `PUnit`), `verdict`, `statistics`,
  `statistics.transparent` — keep their FQNs.
- Migration for code that imported any of the moved packages: prepend
  `internal.` to the package path. The migration is mechanical and a
  find-and-replace pattern resolves every affected import:
  - `org.javai.punit.engine` → `org.javai.punit.internal.engine`
  - `org.javai.punit.reporting` → `org.javai.punit.internal.reporting`
  - `org.javai.punit.util` → `org.javai.punit.internal.util`

### Changed (verdict XML wire format — breaking)

- `<statistics>` now carries `wilson-lower` (the one-sided Wilson
  lower bound at the verdict's `confidence-level`). The legacy
  `ci-lower` / `ci-upper` attribute pair has been retired.
  Consumers reading verdict XML must update their parsers; the
  reader rejects documents missing `wilson-lower` with a clear
  diagnostic.
- `ProbabilisticTestVerdict.StatisticalAnalysis.ciLower` /
  `ciUpper` record components renamed to `wilsonLower` (single
  one-sided lower bound). The two-sided
  `BinomialProportionEstimator.estimate(...)` is unchanged and
  still validated against the javai-R `wilson_ci` fixture; only
  the verdict path moves to one-sided.
- Verdict text rendering replaces the "CI lower bound:" label with
  "Wilson lower bound:" and drops the prose framing the value as a
  confidence interval.
- The local `verdict-1.0.xsd` is now diff-clean against the
  canonical RP07 schema in the orchestrator.

### Changed (package drift — breaking FQN change)

A coordinated sweep ahead of the internal-namespace move resolved
every long-standing package-drift case. Six steps, mostly mechanical
relocations; consumers that imported any of these specific FQNs need
to update.

- `org.javai.punit.power.PowerAnalysis` folded into
  `org.javai.punit.engine.baseline` (then relocated to
  `internal.engine.baseline` under the namespace move). The class is
  a baseline-resolution bridge; co-locating with its primary
  collaborators removed an awkward single-class package.
- The `org.javai.punit.model.*` catch-all is dispersed:
  - `CovariateProfile` consolidated on the typed
    `api.covariate.CovariateProfile` (the duplicate is gone).
  - Covariate-shaped types moved to `api.covariate.*`.
  - `UseCaseAttributes` moved to `api.*`.
  - Verdict-lifecycle types moved to `verdict.*`.
  - The `model/` directory is deleted.
- `org.javai.punit.controls.budget/pacing` collapsed into
  `org.javai.punit.engine.budget/pacing` (then under
  `internal.engine.*`); `controls/` is deleted.
- The top-level `org.javai.punit.spec.{registry,expiration,model,criteria}`
  runtime tree relocated under `engine.spec.*` (then under
  `internal.engine.spec.*`); `api.spec.*` is the public typed-spec
  surface and is unchanged.
- `org.javai.punit.engine.output` folded into
  `org.javai.punit.engine.emit` (then under
  `internal.engine.emit`); `engine/output/` is deleted.

### Added

- `org.javai.punit.api.match` subpackage with `ValueMatcher`,
  `MatchResult`, `StringMatcher`, `JsonMatcher`. Wired through
  `Sampling.matching(...)` for instance conformance.
- `PackageStructureArchitectureTest` carrying the cleanup-arc rules
  with `FreezingArchRule` violation stores under `archunit_store/`.
  Each rule's frozen exception list shrinks as cleanup commits land;
  the rules become permanent regression guards when the stores
  reach empty.
- `RuntimeArchitectureTest.internalRuntimeMustNotImportJUnit` —
  parallel JUnit-free rule for `internal.runtime`.

### Internal

- ArchUnit rule refresh: `CoreArchitectureTest.statisticsModuleMustBeIsolated`
  and `utilPackageMustBeSelfContained` updated to the post-namespace
  layout. `corePackagesMustNotDependOnJUnitExtensions` renamed to
  `statisticsPackageMustNotDependOnJUnitExtensions` (the rule body
  always covered only the statistics package; the old name was stale).
- Six static methods promoted from package-private to `public` to
  cross the new package boundary out of `runtime/` into
  `internal.runtime/`: `BaselineEmitter.emit`,
  `BaselineProviderResolver.resolveDir`/`resolve`,
  `EmpiricalTestComposer.compose`, `TestIdentityResolver.resolve`,
  the `*Emitter.emit` methods. All inside `internal.*` and so still
  off-limits to authors via `publicMustNotImportInternal`.

### Known follow-up

- The `publicMustNotImportInternal` ArchUnit rule's frozen store is
  not empty at release time (116 entries across three categories):
  `runtime.PUnit` driving internals by design; `verdict/*` types
  embedding `internal.engine.budget` / `internal.engine.pacing`
  configuration state for serialisation (package drift, candidate
  for follow-up); `api.covariate.CovariateDeclaration` reaching into
  `internal.util.HashUtils` (small util reach). The rule is a live
  regression guard via `freeze()`; the residue tracks the remaining
  cross-boundary cleanup. Resolving the verdict-embedding drift is
  the prerequisite for adopting JPMS modularisation.

## [0.7.0-alpha2] - 2026-05-10

> **🧪 Experimental release.** Patch over 0.7.0-alpha. Closes the
> wider feasibility-gate audit listed as a "Known gap" in the
> 0.7.0-alpha entry; corrects the empirical-threshold-derivation
> methodology against the javai-R oracle; tightens cost / UX on
> empirical runs that have no resolvable baseline; and removes
> orchestrator-internal requirement codes from public source. No
> breaking API changes vs 0.7.0-alpha.

### Fixed
- **Empirical threshold derivation now applies Wilson at the test
  sample size**, per Statistical Companion §3.4 / §4.3.2. The 0.7.0-alpha
  path applied Wilson at the baseline sample size and compared the
  test's Wilson lower bound against that; this could yield a
  threshold that the test's own confidence machinery couldn't
  underwrite. The fix matches javai-R's reference implementation;
  the conformance fixtures pass tolerance 1e-6.
- **PowerAnalysis.sampleSize now resolves covariate-stamped
  baselines.** A use case declaring covariates could not use the
  confidence-first authoring pattern — `PowerAnalysis.sampleSize`
  threw `IllegalStateException("no baseline found …")` even when a
  matching covariate-stamped baseline existed and was findable by
  the test pipeline's own resolver. Fixed by threading the use
  case's covariate profile + declarations through to the four-arg
  `BaselineResolver` overload.
- **PowerAnalysis admits perfect baselines.** A two-sided
  precondition rejected baselines whose recorded `observedPassRate`
  sat within `mde` of 1.0 — including the common case of a
  `rate = 1.0` baseline. Relaxed to one-sided so the threshold
  path's existing perfect-baseline two-step is reachable from the
  power path too.
- **INCONCLUSIVE verdicts emit a `[PUNIT-INCONCLUSIVE]` line to
  stderr** before the `TestAbortedException` is thrown. Earlier the
  IDE per-test detail pane showed the explanation but the build
  console saw only the four ⊘ icons; a developer watching the build
  log had no account of why a test was skipped.
- **Empirical runs with no resolvable baseline short-circuit
  pre-flight.** Earlier the framework drove the spec through the
  engine — taking *all configured samples* — before the criterion's
  `evaluate` returned INCONCLUSIVE. Each sample is an LLM API call
  in the typical use case; the framework now skips sampling when the
  verdict is structurally guaranteed to be INCONCLUSIVE-no-baseline.

### Added — feasibility-gate audit closes
- **Pre-flight feasibility now applies to contractual criteria too.**
  0.7.0-alpha's `Feasibility.check` silently early-returned for any
  criterion where `!isEmpirical()`; a contractual test with a 99.99%
  SLA at n=50 produced a verdict instead of aborting. Both
  contractual and empirical paths now go through the same gate.
- **Soundness floor enforced regardless of intent.** A configured
  confidence below `StatisticalDefaults.SOUNDNESS_FLOOR_CONFIDENCE`
  (= 0.80) aborts pre-flight in both VERIFICATION and SMOKE intents.
  This was listed as a known gap in 0.7.0-alpha; closes the wider
  feasibility-gate audit.
- **End-to-end audit tests** in `punit-junit5` cover each pre-flight
  invariant via TestKit, with abort-before-sampling verified by a
  counter probe on the use case. The original regression slipped
  past existing unit tests because the gate was wired only at the
  evaluator level — these guard against the same shape of regression
  recurring.

### Changed
- **Orchestrator-internal requirement codes** (the project family's
  internal feature-tracking shorthand) removed from public source —
  javadoc, inline comments, `@DisplayName` strings, assertion
  messages. These tracking codes are interpretable only via an
  internal catalog and were noise to an open-source reader. An
  ArchUnit-style regression guard scans both production and test
  source to keep them out.

### Known gaps still in this experimental release
- **Statistical early termination** (early termination on
  failure-inevitable / success-guaranteed) is still not wired. The
  supporting model is in place but the per-sample evaluator and the
  builder method that opts out have not yet been re-added. Tracked
  for restoration in a subsequent release.

## [0.7.0-alpha] - 2026-05-07

> **🧪 Experimental release.** The first cut of the 0.7.0 typed-builder API. Core authoring surface and statistical engine are ready for evaluation; some optimisations are deferred — see **Known gaps** below. v0.x means breaking changes are still possible: pin to this exact version if you depend on its surface today, and check the issue tracker before upgrading. Feedback at https://github.com/javai-org/punit/issues.

> **⚠️ Breaking changes** — punit 0.7.0 replaces the annotation-driven authoring style of 0.6.x with a typed, builder-based one. See [MIGRATION-0.6-to-0.7.md](docs/MIGRATION-0.6-to-0.7.md) — it carries a coding-assistant prompt that walks your codebase and applies the migration, plus per-change discussion and FAQ. There is no deprecation cycle and no 0.6.x-compatible shim. Maven coordinates are unchanged.

### Changed (breaking)
- **Annotation attributes move to a typed builder.** Configuration that used to live on `@ProbabilisticTest(...)`, `@MeasureExperiment(...)`, `@ExploreExperiment(...)`, `@OptimizeExperiment(...)` is now expressed via builder calls on `PUnit.testing(...)` / `.measuring(...)` / `.exploring(...)` / `.optimizing(...)` invoked from the method body. The annotations survive as bare markers with no attributes. See [MIGRATION §2.1](docs/MIGRATION-0.6-to-0.7.md#21-parameterised-annotation--unparameterised-annotation--builder).
- **Three experiment annotations consolidated to one.** `@MeasureExperiment`, `@ExploreExperiment`, `@OptimizeExperiment` → single `@Experiment` marker; experiment kind is selected by the body's `PUnit.*` factory call. See [MIGRATION §2.1](docs/MIGRATION-0.6-to-0.7.md#21-parameterised-annotation--unparameterised-annotation--builder).
- **`@UseCase` annotation replaced by the typed `UseCase<FT, I, O>` interface.** A use case is now a class implementing the interface; metadata (`description`, `warmup`, `pacing`, `covariates`, …) becomes method overrides; factor variation is expressed via a factor record passed at construction. See [MIGRATION §2.2](docs/MIGRATION-0.6-to-0.7.md#22-use-cases).
- **Covariate redesign.** `@Covariate`, `@CovariateSource`, `@DayGroup`, `@RegionGroup` annotations are gone. Covariates are now values from the sealed hierarchy under `org.javai.punit.api.covariate` (`DayOfWeekCovariate`, `RegionCovariate`, `TimeOfDayCovariate`, `TimezoneCovariate`, `Covariate.custom(...)`), declared on `UseCase.covariates()` and resolved on `UseCase.customCovariateResolvers()` for project-defined covariates. See [MIGRATION §2.3](docs/MIGRATION-0.6-to-0.7.md#23-covariates).
- **`org.javai.punit.contract` package gone.** `Postcondition`, `PostconditionCheck`, `PostconditionEvaluator`, `PostconditionResult`, `UseCaseOutcome` relocated to `org.javai.punit.api`; `ServiceContract` replaced by the typed `Contract<I, O>`, typically folded into the `UseCase` rather than kept as a separate type. See [MIGRATION §2.4](docs/MIGRATION-0.6-to-0.7.md#24-imports-moved-from-contract-to-api).
- **MEASURE baseline shape — aggregate signal only.** Baseline YAML no longer carries the per-sample `resultProjection:` block. The probabilistic test consumes only pass count, sample total, footprint, fingerprint, derived threshold, and the per-clause failure histogram; the per-sample shape is now carried by EXPLORE / OPTIMIZE outputs only. Baselines produced by 0.6.x are not consumed by 0.7.x — regenerate. File size becomes constant in sample count; the EX10 integrity fingerprint covers only content the resolver reads.
- **Failed contract evaluations emit to `System.err` during MEASURE and probabilistic-test runs.** One `[PUNIT-FAIL]` line per failed clause, written synchronously as the engine processes each sample. The line carries the sample index, the failure kind (`apply` / `match` / `postcondition` / `defect`), the clause name, and a single-line reason. Apply-level failures, match mismatches, postcondition failures, and defects all use the same line shape and channel. Stderr is not intercepted by the progress bridge, so failure detail and the `[PUNIT-PROGRESS]` counter on stdout coexist without interleaving.

### Removed (breaking)
- **`@Sentinel` annotation.** The `punit-sentinel` module scans typed `UseCase` implementations directly; no marker required.
- **`@FactorSetter`, `@FactorGetter`, `@FactorAnnotations`.** Reflection-based factor accessors retire entirely. Factors are typed records consumed at construction. (Deprecated in 0.6.0.)
- **`UseCaseProvider.registerAutoWired(...)`** and `UseCaseFactory.registerAutoWired(...)`. Use `registerWithFactors(...)`. (Deprecated in 0.6.0.)
- **Parameter-level annotations no longer wired by the framework**: `@Factor`, `@FactorSource`, `@Input`, `@InputSource`, `@Config`, `@ConfigSource`, `@ControlFactor`, `@DayGroup`, `@RegionGroup`, `@Latency`, `@ExperimentDesign`, `@ExperimentGoal`. Their `@interface` definitions linger in `api/` for binary compatibility, but the engine reads none of them. Use the typed authoring surface.
- **Types removed from `api/`**: `AdaptiveFactor`, `UseCaseContext`, `ProbabilisticTestBudget`. Subsumed by the typed pipeline (custom `FactorsStepper` for adaptive factors; covariate metadata + `TokenTracker` for context; builder-level budget calls + `BudgetExhaustionPolicy` for budget).
- **`OptimizeExperiment.initialControlFactorValue`** (inline string attribute, could not express typed initial values).
- **`OptimizeExperiment.initialControlFactorSource`** (renamed to **`initialFactor`** with identical semantics — a static no-arg method on the experiment class returning the typed initial value).
- **`@FactorGetter` fallback path** in `OptimizeStrategy`. Initial factor values come solely from the `initialFactor` method.
- **`ExploreExperiment.expiresInDays`**. Baseline expiration is a property of MEASURE-produced baseline specs only.

### Added
- **Typed-builder entry points** in `org.javai.punit.runtime.PUnit`: `testing(...)`, `measuring(...)`, `exploring(...)`, `optimizing(...)`.
- **Typed pipeline types in `org.javai.punit.api`**: `Contract<I, O>`, `ContractBuilder<O>`, `Sampling`, `MatchResult`, `ValueMatcher`, `Expectation`, `InputSupplier`, `FactorBundle`, `FactorValue`, `LatencyResult`, `LatencySpec`, plus value records (`BooleanValue`, `DecimalValue`, `DurationValue`, `EnumValue`, `InstantValue`, `IntegralValue`, `StringValue`, `UriValue`).
- **`org.javai.punit.api.spec` subpackage** with spec / verdict / runtime-result types: `Spec`, `TypedSpec`, `Verdict`, `EngineResult`, `EngineRunSummary`, `ProbabilisticTestResult`, `Trial`, `SampleClassification`, `SampleSummary`, `SampleObserver`, `SampleExecutor`, `FactorsStepper`, `NextFactor` (sealed `Continue` / `Stop`), `Scorer`, `ResourceControls` / `ResourceControlsBuilder`, `Criterion`, `EvaluatedCriterion`, `BaselineProvider`, `BudgetExhaustionPolicy`, `FailureCount`, `FailureExemplar`, `LatencyStatistics`, `PassRateStatistics`, `NoStatistics`, `PercentileLatency`, `PercentileKey`, `TerminationReason`, `DurationViolation`, `ExperimentResult`.
- **`org.javai.punit.api.covariate` subpackage** with the sealed `Covariate` hierarchy.
- **`OptimizeBuilder.disableEarlyTermination()`** — opts a single optimize run out of statistical early termination.
- **Postcondition failure histograms** on `SampleSummary`, `ProbabilisticTestResult`, verdict text, verdict XML (`<postcondition-failures>`), and the HTML report. Failures broken down by named clause with bounded exemplars.
- **`EngineRunSummary` on `ProbabilisticTestResult`** — run-level scalars (planned/executed samples, elapsed, tokens, latency, termination, confidence, matched baseline filename).
- **Covariate-aware best-match baseline selection.** Baseline filenames carry a covariate hash; the resolver picks the best-aligned baseline.
- **Pre-flight feasibility gate.** A VERIFICATION-intent test whose configured (samples, threshold, confidence) tuple cannot underwrite a verification claim aborts pre-flight with an `INFEASIBLE VERIFICATION` diagnostic — no samples execute, no misleading PASS / FAIL is produced. Applies to both contractual (`SLA` / `SLO` / `POLICY`) and empirical thresholds. SMOKE-intent runs are silent: the developer has explicitly opted into the sizing gap.
- **RP07 XML verdict on every test.** Each `@ProbabilisticTest` produces a `{className}.{methodName}.xml` file under `build/reports/punit/xml/` (configurable via `punit.report.dir` / `PUNIT_REPORT_DIR`, suppressible with `punit.report.enabled=false`). The HTML report (`./gradlew punitReport`) consumes these files. See Part 11 of the user guide for the field reference.
- **`BaselineLookup.sourceFile`.** Matched baseline filename threaded from `BaselineResolver` through `Spec.conclude` to the verdict XML's `<provenance spec-filename>` attribute.

### Known gaps in this experimental release

These are the rough edges that justify the `-alpha` qualifier. Each is tracked for restoration in a subsequent release.

- **Statistical early termination is not yet wired.** The 0.6.x sample loop stopped early when the outcome became impossible (failure inevitable) or guaranteed (success guaranteed); the spec-engine refactor preserved the supporting model (the `TerminationReason` enum, scenario fixtures, rendering paths) but the per-sample evaluator and the `disableEarlyTermination()` builder method on the probabilistic-test surface have not yet been re-added. Verdicts are correct — every test runs to its configured sample count and produces the right answer — but configurations that could have terminated after a few samples now run all of them.
- **Wider feasibility-gate audit.** The pre-flight gate (see *Added* above) is wired correctly for the failure shape that motivated this release, but the catalog defines a family of related invariants — soundness floor (implied confidence < 80%, cross-intent), parameter-bounds validation, configuration-coherence checks across the threshold-first / sample-size-first / confidence-first approaches. Some of these remain unchecked. Configurations whose implied confidence falls below 80% are not yet rejected across all intents.

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
