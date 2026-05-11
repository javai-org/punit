# punit Domain Ontology

This document maps the **family domain ontology**
([`javai-orchestrator/inventory/DOMAIN-ONTOLOGY.md`](../../inventory/DOMAIN-ONTOLOGY.md))
onto punit's Java idioms, packages, and types. It is the conceptual
counterpart to [`DEVELOPER-API.md`](DEVELOPER-API.md) — together they
form punit's spec for "what the framework looks like to a Java
developer." This document answers *what kinds of things live in this
codebase and how they relate*; the API doc answers *what does it look
like to use them*.

This document is **downstream** of the family ontology. When this
document and the family ontology disagree, the family ontology wins
and this document is corrected. Do not introduce a concept here that
the family ontology does not sanction; raise a directive first to add
it at the family level.

---

## How to read

For each family-level concept this document carries:

- **family**: the family ontology section name.
- **java type**: the Java identifier in punit (interface, class,
  record, annotation, …).
- **package**: where it lives.
- **role notes**: idiom-level notes — what's a record vs sealed
  interface vs enum vs annotation, what's framework-internal vs
  author-facing, what's overridable vs final.
- **gotchas / drift to fix**: punit-specific points an agent must
  preserve when modifying or generating code.

Family-level invariants and policies are not restated here; the
family ontology is authoritative on those. Where punit *enforces* a
family-level invariant via a specific test or architectural rule,
the enforcement mechanism is named (typically the relevant
ArchUnit-style architecture test).

---

## Subject under test

```yaml
- family: Use Case
  java_type: UseCase<FT, IT, OT>          # interface (sealed by extension to Contract)
  package: org.javai.punit.api
  role_notes: |
    Sealed-by-convention: extends Contract<IT, OT>. An author writes
    one `class X implements UseCase<F, I, O>` and overrides three
    methods — `invoke`, `postconditions(ContractBuilder)`, plus
    whichever metadata methods diverge from defaults
    (`id`, `pacing`, `warmup`, `covariates`, `inputSource`, …).
    `FT` is the typed factor record; `IT` the per-sample input;
    `OT` the per-sample output value.
  gotchas:
    - `id()` defaults to a kebab-cased simple class name. Override
      when the default would collide or when a stable id is needed
      across renames.
    - The framework caches and reuses one instance per factor
      configuration. Implementations may carry per-configuration
      state but must not mutate it in ways that change observable
      `invoke` behaviour.
    - Factor and Covariate names must be disjoint (family invariant).
    - `ServiceContract` rename is pending under
      `DIR-USECASE-RENAME` (deferred past 0.7.x). The Java type
      stays `UseCase` for now.

- family: Use Case ID
  java_type: String (returned by UseCase#id())
  package: org.javai.punit.api
  role_notes: |
    Filename-safe, stable across runs. Path component for emitted
    artefacts (baselines, explorations, optimizations).
  gotchas:
    - Renaming an id without baseline migration breaks every
      committed baseline addressed by that id. Treat as schema
      change.

- family: Factor
  java_type: typed factor record (author-defined) + Factor / FactorValue / FactorBundle support types
  package: org.javai.punit.api
  role_notes: |
    The factor type is a Java record the author declares. The
    framework reads its components reflectively for filename
    composition and covariate alignment. `NoFactors` is a
    canonical empty factor type — use it, do not invent your own
    empty record.
  gotchas:
    - Drop the `with_` prefix on builder/fluent factor methods
      (project-wide rule — see family ontology assistant guidance).
    - **Term to avoid: "factor level".** The canonical javai term
      is "factor value". "Factor level" survives only in
      `javai-R/docs/GLOSSARY.md` as a Design-of-Experiments nod.
      Two javadoc surfaces in `Factor.java` and `Config.java` still
      use "factor levels" — sweep when next editing those files.

- family: Covariate
  java_type: Covariate (interface) + CovariateProfile + per-category implementations
  package: org.javai.punit.api.covariate
  role_notes: |
    Covariates are first-class members of baseline identity, not
    metadata. Built-in categories: `DayOfWeekCovariate`,
    `RegionCovariate`, `TimeOfDayCovariate`, `TimezoneCovariate`,
    `CustomCovariate`. Authors most often declare covariates by
    overriding `UseCase#covariates()` to return a list of these.
  gotchas:
    - When a Use Case declares Covariates, every artefact path
      that emits or consumes a baseline for that Use Case MUST
      carry the covariate profile (family invariant —
      "Covariate-as-identity").
    - The duplicate `model.CovariateProfile` was removed under
      DIR-PACKAGE-DRIFT-FIX-punit step 2; `api.covariate.CovariateProfile`
      is the sole canonical home.

- family: Input Source
  java_type: InputSource<IT> + InputSupplier<IT>
  package: org.javai.punit.api
  role_notes: |
    Backed by an in-memory list, a JSON file fixture
    (`InputSource.fromJsonFile(...)`), or a custom
    `InputSupplier`. The sample loop traverses round-robin.
```

---

## Judgement under test

```yaml
- family: Service Contract
  java_type: Contract<IT, OT>             # interface
  package: org.javai.punit.api
  role_notes: |
    The operational layer. Carries `invoke` and
    `postconditions(ContractBuilder<O>)`. UseCase extends Contract;
    the abstract / concrete split is preserved by inheritance.
  gotchas:
    - The "Service Contract" *concept* is owned by `Contract` here.
      Do not confuse with the planned `UseCase → ServiceContract`
      *type rename*; that rename moves the *subject* concept onto a
      different identifier and is deferred (see DIR-USECASE-RENAME).
    - A parallel `org.javai.punit.contract.*` package exists with a
      duplicate `ServiceContract` and friends. **Dead code,
      scheduled for deletion under DIR-CONTRACT-PACKAGE-REMOVAL-punit
      (in 0.7.x scope).** Do not reference, extend, or re-import —
      treat it as not-there.

- family: Postcondition
  java_type: ContractBuilder<O>::ensure / ::deriving
  package: org.javai.punit.api
  role_notes: |
    Authors declare clauses by populating the supplied
    `ContractBuilder<O>` in `postconditions(ContractBuilder)`. Two
    methods: `ensure(name, predicate)` for leaf clauses;
    `deriving(name, transform, sub)` for derivations whose nested
    clauses are populated through a sub-builder lambda. Predicates
    return `Outcome<Void>`: `Outcome.ok()` on pass,
    `Outcome.fail(name, message)` on fail.
  gotchas:
    - Do not move contract judgement out of `postconditions` into
      `invoke`. Failure detail (clause-named diagnostic) only
      arises when the clause is registered through the builder
      (memory: `keep_invoke_primitive`).
    - The standalone form in the dead `org.javai.punit.contract`
      package (Postcondition / PostconditionEvaluator / etc.) is
      gone after DIR-CONTRACT-PACKAGE-REMOVAL-punit. The inline
      form on `ContractBuilder` is the only canonical surface.

- family: Duration Constraint
  java_type: UseCase#maxLatency() returning Optional<Duration>
  package: org.javai.punit.api
  role_notes: |
    Per-sample wall-clock bound. Independent of postconditions: a
    duration violation is recorded even if every postcondition
    passes (family invariant — "Service Contract aspect
    independence").
  gotchas:
    - Do not conflate with PercentileLatency criterion. Duration
      Constraint is per-sample, contractual, fail-fast. Latency
      Assertion is per-test, distributional, percentile-based.

- family: Expected-Output Match
  java_type: ValueMatcher<OT> + Expectation<OT>
  package: org.javai.punit.api
  role_notes: |
    Golden-value comparison. Built-in matchers: exact,
    case-insensitive, JSON-structural; custom matchers compose.
    Author-facing API exposes the matcher composition surface and
    the `Expectation` carrier; engine-side dispatch is internal.

- family: Conformance
  java_type: UseCaseOutcome<OT> (the per-sample state) + criterion-level evaluation
  package: org.javai.punit.api (UseCaseOutcome) + org.javai.punit.api.spec (criterion path)
  role_notes: |
    `UseCaseOutcome` carries the per-sample data: timestamped
    wrapper of the `Outcome<OT>`, duration, token charge, clause
    results. The criterion-level conformance pass-rate is
    aggregated by the engine and read by `PassRate`.
  gotchas:
    - `api.UseCaseOutcome` is the canonical version. A duplicate
      `contract.UseCaseOutcome` exists in the dead package and is
      scheduled for deletion. Always import from `api`.
```

---

## The act of testing

```yaml
- family: Sample
  java_type: per-sample tuple (Outcome<OT> + duration + token diff)
              materialised inside Engine; surfaced via SampleSummary
  package: org.javai.punit.internal.engine + org.javai.punit.api.spec
  role_notes: |
    Authors do not write Sample — the framework produces it.
    `SampleSummary<OT>` is the aggregated form a criterion sees.
    `SampleClassification` and `Trial` are the fine-grained shapes
    inside `api/spec`.

- family: Outcome
  java_type: org.javai.outcome.Outcome<T>     # sealed interface (Ok | Fail)
  package: org.javai.outcome (external library)
  role_notes: |
    Imported from the `org.javai:outcome` library; not punit's
    invention. punit also publishes `UseCaseOutcome<OT>` as the
    per-sample wrapper carrying the `Outcome<OT>` value plus
    cost / timing / clauses. Authors return `Outcome.ok(value)`
    or `Outcome.fail(name, message)` from `invoke`.
  gotchas:
    - **Family invariant — Outcome vs exception discipline.**
      Expected failures travel via Outcome.fail; defects throw.
      The framework does not catch exceptions from invoke; a
      thrown exception aborts the run (defect signal).
    - Do not subclass `Outcome<T>` — sealed by design. Per-project
      additions belong on `UseCaseOutcome`, not on Outcome.

- family: Probabilistic Test
  java_type: ProbabilisticTest (api.spec interface) + @ProbabilisticTest annotation
  package: org.javai.punit.api.spec (spec) + org.javai.punit.api (annotation)
  role_notes: |
    The annotation `@ProbabilisticTest` is attribute-free. Every
    parameter lives on the spec built inside the method body via
    `PUnit.testing(...)`. The annotation is meta-tagged
    `@Test @Tag("punit")`.
  gotchas:
    - The api.spec `ProbabilisticTest` (the spec type) and the
      api `ProbabilisticTest` (the annotation) share a name and
      different roles. In code, fully qualify when both are in
      scope; in prose, "the test annotation" vs "the test spec".

- family: Verdict
  java_type: PUnitVerdict (sealed interface) + ProbabilisticTestVerdict (record)
  package: org.javai.punit.verdict
  role_notes: |
    `PUnitVerdict` is the sealed common surface; the concrete
    record `ProbabilisticTestVerdict` is what every test produces.
    The terminal builder methods on PUnit (`assertPasses`, `run`,
    `build`) translate the Verdict into the test-harness
    convention: PASS → JUnit pass; FAIL → AssertionFailedError;
    INCONCLUSIVE → TestAbortedException (configuration / drift,
    not service degradation).
  gotchas:
    - JUnit always reports pass for punit tests because punit
      owns the verdict. The framework's own Verdict is the
      meaningful artefact (family ontology — Verdict).
```

---

## Methodology and statistics

```yaml
- family: Parameter Triangle
  java_type: surfaced via TestBuilder#samples / criterion shapes
  package: org.javai.punit.runtime (builder) + org.javai.punit.api.spec (spec)
  role_notes: |
    Three approaches: sample-size-first (samples + criterion;
    framework derives implied claim), confidence-first (criterion
    declares confidence + power + MDE; framework computes
    samples), threshold-first (criterion declares threshold
    explicitly).

- family: Threshold (pass-rate)
  java_type: PassRate (criterion factory family)
  package: org.javai.punit.internal.engine.criteria
  role_notes: |
    Three forms: `PassRate.meeting(threshold, ThresholdOrigin)`
    (NORMATIVE, declared); `PassRate.empirical()` (closest-match
    baseline lookup); `PassRate.empiricalFrom(supplier)` (pinned
    baseline). The criterion lives under `engine.criteria` because
    its evaluate() consumes `BinomialProportionEstimator` —
    `Criterion` itself is in `api.spec`, but concrete criteria
    that touch statistics live in `engine.criteria`.
  gotchas:
    - **Family invariant — Statistical isolation.** A criterion
      may *call* statistical machinery; it must not contain
      statistical *arithmetic*. PassRate delegates to
      `BinomialProportionEstimator` and `ThresholdDeriver`. If
      ever tempted to inline a Wilson here, stop — the calculation
      belongs in `org.javai.punit.statistics`.

- family: Threshold (latency)
  java_type: PercentileLatency (criterion family) + LatencySpec / Latency (annotation form)
  package: org.javai.punit.internal.engine.criteria + org.javai.punit.api
  role_notes: |
    The `@Latency` annotation form sits in `api`; the criterion
    factory and evaluation in `engine.criteria`.

- family: Threshold Origin
  java_type: ThresholdOrigin enum
  package: org.javai.punit.api
  role_notes: |
    SLA / SLO / Policy → NORMATIVE (collectively). EMPIRICAL is
    distinct. Origin × Test Intent decides Feasibility Gate
    behaviour.

- family: Threshold Provenance
  java_type: fields on ProbabilisticTestVerdict + RunMetadata
  package: org.javai.punit.verdict
  role_notes: |
    Carried through to verdict XML emission. RP07 makes the
    binding (per family invariant — "Sample-time provenance
    must accompany the verdict").

- family: Feasibility Gate
  java_type: VerificationFeasibilityEvaluator + Feasibility (engine criterion adapter)
  package: org.javai.punit.statistics (evaluator) + org.javai.punit.internal.engine.criteria (adapter)
  role_notes: |
    The pre-execution gate. Verification + NORMATIVE → reject;
    Smoke + NORMATIVE → run with caveat; any + EMPIRICAL → run.
    SMOKE intent does not produce a warning by design (family
    invariant referenced via memory: `smoke_intent_silent_feasibility`).

- family: Wilson Score Bound
  java_type: BinomialProportionEstimator (two-sided + one-sided)
  package: org.javai.punit.statistics
  role_notes: |
    Both bounds live in the estimator. The verdict path uses
    only the one-sided lower bound (family invariant —
    one-sided Wilson in the verdict path). The two-sided form
    is preserved for SC01 (catalog) + diagnostics.
  gotchas:
    - Verdict XML emits `wilson-lower` only; the legacy
      `ci-lower`/`ci-upper` pair was retired with the 0.7.x cleanup.

- family: Latency Population
  java_type: LatencyDistribution (statistics) + LatencyPercentileComputer (engine)
  package: org.javai.punit.statistics + org.javai.punit.internal.engine
  role_notes: |
    **Family invariant — Latency Population purity.** Only
    successful Samples contribute. The `LatencyPercentileComputer`
    filters at aggregation time; the statistics module operates
    on the filtered sequence.

- family: Empirical Percentile
  java_type: nearest-rank computation in LatencyDistribution
  package: org.javai.punit.statistics
  role_notes: |
    Non-parametric, distribution-free. Latency thresholds derive
    from the binomial order-statistic upper confidence bound.
```

---

## Cost, pacing, and budget

```yaml
- family: Token
  java_type: TokenTracker (interface) + InMemoryTokenTracker (engine impl) + TokenChargeRecorder (annotation-driven static charge)
  package: org.javai.punit.api (tracker, recorder) + org.javai.punit.internal.engine (impl)
  role_notes: |
    Generic unit of cost — not LLM-specific (family ambiguous
    term: `Token`). Authors call `tracker.recordTokens(n)` inside
    `invoke` for dynamic charges; static charges declare via
    `@TokenChargeRecorder` or builder.

- family: Budget
  java_type: ResourceControls + ResourceControlsBuilder + BudgetTracker (engine)
  package: org.javai.punit.api.spec + org.javai.punit.internal.engine + org.javai.punit.internal.engine.budget
  role_notes: |
    Time / token / sample-count flavours. Suite / class / method
    scopes; first exhausted budget triggers termination.
    Exhaustion behaviour configurable: FAIL or EVALUATE_PARTIAL.
  gotchas:
    - feotest token-budget pre-sample check has a known
      double-count bug for static charges (memory:
      `feotest_token_budget_double_count`). Punit's path is sound;
      do not port the flaw if cross-pollinating.

- family: Pacing
  java_type: Pacing record + per-pacing-mode strategies
  package: org.javai.punit.api + org.javai.punit.internal.engine.pacing
  role_notes: |
    Pacing belongs on the Use Case (`UseCase#pacing()`), not on
    the spec — every test of the same service should respect the
    same rate limit. Spec builders deliberately do not expose
    pacing knobs.
```

---

## Experimentation

```yaml
- family: Experiment
  java_type: Experiment (api.spec interface) + @Experiment annotation
  package: org.javai.punit.api.spec (spec) + org.javai.punit.api (annotation)
  role_notes: |
    Three modes via the spec's `Experiment.Kind`: MEASURE,
    EXPLORE, OPTIMIZE. The annotation carries no attributes; the
    builder body decides the mode by entry point
    (`PUnit.measuring`, `PUnit.exploring`, `PUnit.optimizing`).
    The `ArtefactEmissionRegressionTest` enforces that every
    Kind has an emit path (compile-time via exhaustive switch).

- family: Stepper (Factors)
  java_type: FactorsStepper<FT> (interface) + NextFactor<FT> (sealed return)
  package: org.javai.punit.api.spec
  role_notes: |
    `next` returns `NextFactor.next(factors)` or
    `NextFactor.stop()`. The pre-0.7 `null`-as-stop pattern is
    retired; do not reintroduce.

- family: Empirical Baseline
  java_type: BaselineProvider + BaselineLookup (api) + BaselineEmitter (runtime)
  package: org.javai.punit.api.spec + org.javai.punit.runtime + org.javai.punit.internal.engine.baseline
  role_notes: |
    Emitted by MeasureBuilder.run() via BaselineEmitter; loaded
    via BaselineProvider; selected via covariate-aware resolver
    in engine.covariate.

- family: Footprint
  java_type: footprint hash inside engine.baseline + serialised as the EX09 field
  package: org.javai.punit.internal.engine.baseline
  role_notes: |
    Internal to the engine; surfaces in baseline YAML. Authors
    do not compute footprints by hand.

- family: Content Fingerprint
  java_type: contentFingerprint field on baseline YAML (EX10)
  package: org.javai.punit.internal.engine.baseline
  role_notes: |
    Soft-warning on mismatch (per 2026-05 catalog amendments),
    not hard abort.
```

---

## Test intent and policy

```yaml
- family: Test Intent
  java_type: TestIntent enum (VERIFICATION, SMOKE)
  package: org.javai.punit.api
  role_notes: |
    Surfaced on the test builder via `.intent(TestIntent.SMOKE)`.
    Default is VERIFICATION.

- family: Compliance Testing
  java_type: usage pattern: PassRate.meeting(τ, NORMATIVE) + Verification intent
  package: pattern, not a type
  role_notes: |
    No dedicated class — emerges from criterion + intent
    composition.

- family: Conformance Testing
  java_type: usage pattern: PassRate.empirical() / empiricalFrom(...)
  package: pattern, not a type
  role_notes: |
    Same — pattern, not a type.
```

---

## Reporting

```yaml
- family: Verdict XML (RP07)
  java_type: VerdictXmlWriter + VerdictXmlReader
  package: org.javai.punit.report
  role_notes: |
    `punit-report` module. Local XSD copy at
    `punit-report/src/main/resources/.../verdict-1.0.xsd`. Must
    diff clean against the canonical at
    `inventory/catalog/reporting/RP07-verdict-xml-interchange/verdict-1.0.xsd`.
  gotchas:
    - Emits `wilson-lower` only on `<statistics>`; the upper bound
      is not emitted (the verdict path is left-tailed). Aligned to
      the canonical XSD as of the 0.7.x cleanup.

- family: Verdict Sink
  java_type: VerdictSink (interface, public) + VerdictSinkBus (dispatcher, internal)
  package: org.javai.punit.verdict (interface) +
           org.javai.punit.internal.reporting (dispatcher)
  role_notes: |
    ServiceLoader-discovered. With `punit-report` on the
    classpath, the XML sink registers automatically. Without
    it, no default sink — explicit registration required.
```

---

## Sentinel

```yaml
- family: Sentinel
  java_type: SentinelMain + SentinelExecutor + SentinelOrchestrator
  package: org.javai.punit.sentinel (in punit-sentinel module)
  role_notes: |
    Zero `org.junit` dependencies. The Sentinel reaches the engine
    through `PUnit` / `runtime`, not through the JUnit extension
    surface. Enforced by `SentinelArchitectureTest`.
  gotchas:
    - **Family invariant — Sentinel build-time baseline embedding.**
      Every EMPIRICAL-origin test must carry its embedded default
      baseline at sentinel build time. Missing → build failure.
      Normative-origin tests are exempt.
    - **Family invariant — Sentinel emits, never installs.** The
      Sentinel may emit a measurement to its measurement output
      but never overwrites the test input automatically.

- family: Reliability Specification
  java_type: SentinelConfiguration + EnvironmentMetadata
  package: org.javai.punit.sentinel
  role_notes: |
    Bundle authored alongside the test suite; consumable by the
    suite via test adapter (SN05) and by the Sentinel via CLI.
```

---

## Punit-only concepts (not at the family level)

These exist because the JVM / JUnit / Java idiom requires them.
They are not in the family ontology because they have no
cross-language analogue (or different languages express them
differently).

```yaml
- name: Annotation marker
  description: |
    `@ProbabilisticTest` and `@Experiment` are bare markers
    (attribute-free, meta-tagged `@Test @Tag("punit")`). All
    configuration lives in the method body via the typed-builder
    surface.
  rationale: |
    The 0.6 → 0.7 redesign moved configuration off annotations
    onto types so it can carry typed factor records, typed
    initial values, typed expected outputs, and so on. The
    annotation survives as the JUnit hook only.

- name: Builder terminal
  description: |
    Each PUnit-entry-point builder ends in one of: `.assertPasses()`
    (probabilistic-test side), `.run()` (experiment side), or
    `.build()` (pair-pattern supplier — an Experiment value handed to
    PassRate.empiricalFrom).
  rationale: |
    The terminal selects the test-harness translation. Authors
    rarely call `.build()` directly except in the empirical pair
    pattern; the framework would be content with two terminals
    (assertPasses / run) but the supplier-pattern has earned the
    third.

- name: api / api.spec / engine boundary
  description: |
    `org.javai.punit.api` carries types an author touches when
    *authoring* (UseCase, Contract, ContractBuilder, Sampling,
    Factor support, annotations, intent, origin, value matchers,
    pacing, postcondition primitives that survive). `api.spec`
    carries the typed spec surface (Experiment, ProbabilisticTest,
    Criterion, EvaluationContext, FactorsStepper, …). `engine.*`
    is internal — engine.criteria, engine.baseline,
    engine.explore, engine.optimize, engine.covariate,
    engine.emit, engine.budget, engine.pacing, engine.spec.
    Authors should never import from `engine.*`.
  enforcement: |
    `CoreArchitectureTest` (package-level rules in punit-core) +
    `Junit5ApiPackageContentsTest` (junit5 abstraction-level
    discipline). See DEVELOPER-API.md §Architecture-test catalogue.

- name: Empirical pair pattern
  description: |
    Author publishes a `Sampling<F, I, O>` helper used by both a
    measure baseline (`@Experiment` returning `Experiment` via
    `Experiment.measuring(...).build()`) and a probabilistic test
    (`@ProbabilisticTest` with `PassRate.empiricalFrom(::baseline)`).
    Java reference semantics enforce that the test and the
    baseline draw from the same sampling population — same use
    case, same inputs, same factors, same governors.
  rationale: |
    Statistical coherence of "has the service degraded since the
    baseline?" requires the test and baseline to be drawn from
    the same sampling population. By passing the same `Sampling`
    value to both, the language enforces it.
```

---

## Lifecycles in punit terms

```yaml
- concept: probabilistic test execution (Java)
  states: [annotation_dispatched, builder_constructed, spec_built,
           feasibility_checked, sampling, terminated, verdict_built,
           sink_dispatched, junit_translated]
  notes: |
    The JUnit extension `ProbabilisticTestExtension` dispatches
    the @ProbabilisticTest method. The method body builds and
    drives the spec via PUnit. The verdict is dispatched to all
    registered VerdictSinks (XML if punit-report is present, log
    if Sentinel is configured, etc.) and then translated to JUnit
    pass/AssertionFailedError/TestAbortedException at the terminal.

- concept: empirical baseline (punit)
  states: [in_memory_after_measure, written_to_explorations_outputDir
           (test build), committed_under_src_test_resources_punit_specs,
           loaded_via_BaselineProvider, expired_per_expiresInDays]
  emit_path: org.javai.punit.runtime.BaselineEmitter
  load_path: org.javai.punit.api.spec.BaselineProvider via
             ProfileBoundBaselineProvider
```

---

## Punit-specific invariants and enforcement

The family ontology owns the invariants that hold across every
implementation. punit *enforces* them via specific tests; the table
names the test for each.

```yaml
- family_invariant: Statistical isolation
  punit_enforcement: |
    `org.javai.punit.statistics` has no `org.javai.punit.*`
    imports outside the package. Enforced by
    `CoreArchitectureTest.statisticsModuleMustBeIsolated`. Test
    sources must also keep statistical arithmetic out — see also
    "no Math.sqrt outside statistics" rule in punit's CLAUDE.md.

- family_invariant: Requirement-code isolation
  punit_enforcement: |
    Codes (CT, EX, LT, PT, RC, RP, SC, SN, TH, UC, XM, DG) MUST NOT
    appear anywhere in punit-core / punit-junit5 / punit-report /
    punit-sentinel — production OR test source, including
    `@DisplayName` strings, test-class and test-method names,
    string literals. Enforced by `RequirementCodeIsolationTest`.

- family_invariant: One-sided Wilson in the verdict path
  punit_enforcement: |
    `BinomialProportionEstimator.lowerBound(...)` is the verdict
    path's entry. Two-sided remains for SC01 / diagnostic uses.
    XSD alignment to `wilson-lower` in flight under
    DIR-RP07-WILSON-LOWER-VERDICT-XML.

- family_invariant: Latency Population purity
  punit_enforcement: |
    `LatencyPercentileComputer` filters to successful samples
    before delegating to `LatencyDistribution`. A code change to
    latency aggregation must preserve the success-only filter
    end-to-end.

- family_invariant: Outcome vs exception discipline
  punit_enforcement: |
    The engine does not catch exceptions thrown from
    `Contract#invoke`. Per-test exception policy
    (`ExceptionPolicy` / `RC12 / RC13`) covers *unexpected*
    exceptions only — converting one to a counted failed sample
    is opt-in via the policy, not a default.

- family_invariant: Sentinel deployability (zero JUnit deps)
  punit_enforcement: |
    `SentinelArchitectureTest` enforces no `org.junit` imports in
    `punit-sentinel`. `RuntimeArchitectureTest` enforces the same
    in `org.javai.punit.runtime`.

- family_invariant: Service Contract aspect independence
  punit_enforcement: |
    Postconditions, duration constraint, and expected-output
    match are evaluated independently in the engine. No
    cross-aspect short-circuit.

- family_invariant: api package must not depend on JUnit extensions
  punit_enforcement: |
    `CoreArchitectureTest.apiPackageMustNotDependOnJUnitExtensions`.
    Annotation meta-tags (`@Test`, `@Tag`) are permitted; engine,
    extension, and platform types are not.
```

---

## Drift to fix (punit-side)

Items below are punit-specific cleanup that this ontology surfaces
but does not itself fix. They are candidates for in-flight or
future cleanup directives.

The **formal, executable specification** of the target package
structure lives in
`punit-core/src/test/java/org/javai/punit/architecture/PackageStructureArchitectureTest.java`.
That test class encodes each architectural decision below as an
ArchUnit rule against the *post-cleanup* state. Until the cleanup
lands, current violations are captured in `archunit_store/` via
`FreezingArchRule`; each refactor commit shrinks the store.

```yaml
- item: "factor levels" terminology in javadoc
  locations:
    - punit-core/.../api/Factor.java:13
    - punit-core/.../api/Config.java:8
  fix: |
    Replace with "factor values". The canonical javai term is
    "factor value" (family ambiguous-terms entry "Factor Level").
  scope: editorial; sweep when next editing those files.

- item: model.CovariateProfile vs api.covariate.CovariateProfile
  status: resolved
  fix: |
    Resolved under DIR-PACKAGE-DRIFT-FIX-punit step 2.
    `model.CovariateProfile` was deleted; the engine consumers
    (`spec.registry.SpecificationLoader`, `spec.model.ExecutionSpecification`,
    `model.BaselineProvenance`) migrated to the canonical
    `api.covariate.CovariateProfile`. The `Builder` and the
    typed-`CovariateValue` capability were dropped (sole production
    use was String-wrapping; the in-flight `parseCovariateValue`
    output is now coerced to String at the put site via
    `toCanonicalString()`).

- item: org.javai.punit.contract.* (dead parallel stack)
  fix: |
    Owned by DIR-CONTRACT-PACKAGE-REMOVAL-punit (in 0.7.x scope).
    Until that lands, treat the package as not-there: do not
    reference, extend, or re-import.

- item: Verdict XML attribute names (ci-lower / ci-upper)
  status: resolved
  fix: |
    Resolved in the 0.7.x cleanup window. `<statistics>` now
    emits `wilson-lower` only; the legacy `ci-lower` / `ci-upper`
    pair was retired and the local XSD is diff-clean against
    canonical RP07.

- item: Statistical early termination (PT09 / PT10) regression
  fix: |
    The 0.6.x sample loop terminated early on impossibility /
    guaranteed success. The spec-engine refactor lost the
    per-sample evaluator and the `disableEarlyTermination()`
    builder method. Verdicts are correct (every test runs to
    its configured sample count); the optimisation is missing.
    Restoration is owed; see family inventory PT09 / PT10
    rows ("partial" for punit) and the user memory entry
    `punit_pt09_pt10_regression`.
```

---

## What this document does not do

- **It does not restate the family ontology's invariants.** Those
  are owned upstream. This document records *how punit enforces*
  each invariant, not what each invariant says.
- **It does not duplicate the glossary.** Concept definitions live
  in `javai-R/docs/GLOSSARY.md`. This document maps each
  glossary-defined concept onto a Java type / package / idiom.
- **It does not restate methodology.** Statistical formulae live
  in the Statistical Companion; this document references the
  Companion only via the family ontology.
- **It does not document the public API surface in detail.** That
  is `DEVELOPER-API.md`'s job. This document maps concepts to
  packages so that an agent reading the API doc has the
  conceptual scaffolding; the API doc carries the signatures and
  the developer-facing prose.
