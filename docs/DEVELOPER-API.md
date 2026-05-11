# punit Developer API

The surface a Java developer types when authoring tests, experiments,
sentinel reliability specifications, and consumers against punit.
This document is the operational counterpart to
[`DOMAIN-ONTOLOGY.md`](DOMAIN-ONTOLOGY.md): the ontology says *what
kinds of things live in the codebase*; this document says *what does
it look like to use them*.

This document is not the user guide. The
[`USER-GUIDE.md`](USER-GUIDE.md) is the tutorial; this is the spec.
A new contributor joining punit reads this to learn what the surface
is, what is public, what is internal, what discipline keeps the
surface honest, and what each architecture test enforces. An agent
generating or modifying punit code reads this to know which packages
to touch and which to never touch.

The architectural maxims in this document — particularly the
**statistics isolation rule** and the **api / runtime / engine
boundary** — are not stylistic preferences. They are enforced by
ArchUnit-style tests that fail the build on violation. The tests are
named in §[Architecture-test catalogue](#architecture-test-catalogue);
when modifying punit, satisfying these tests is the minimum bar for
the change being a change at all.

---

## Table of contents

- [Audience and scope](#audience-and-scope)
- [The two authoring annotations](#the-two-authoring-annotations)
- [The PUnit entry point](#the-punit-entry-point)
- [The Sampling primitive](#the-sampling-primitive)
- [Use Case and Contract](#use-case-and-contract)
- [Postconditions: the ContractBuilder surface](#postconditions-the-contractbuilder-surface)
- [Criteria](#criteria)
- [The empirical pair pattern](#the-empirical-pair-pattern)
- [Spec terminals and what each one returns](#spec-terminals-and-what-each-one-returns)
- [Public package contract](#public-package-contract)
- [Architecture-test catalogue](#architecture-test-catalogue)
- [Statistics isolation rule](#statistics-isolation-rule)
- [Sentinel deployability](#sentinel-deployability)
- [Outcome vs exception convention](#outcome-vs-exception-convention)
- [Verdict XML wire format](#verdict-xml-wire-format)
- [Versioning and binary compatibility](#versioning-and-binary-compatibility)

---

## Audience and scope

This document targets:

- **Authors** writing `@ProbabilisticTest` and `@Experiment`
  methods in their own codebases against the published artefact.
- **Sentinel deployers** authoring reliability specifications
  and configuring the sentinel binary.
- **punit contributors** modifying or extending the framework
  itself.
- **Code-generation agents** producing or modifying any of the
  above, who need a stable reference for *what is public, what is
  enforced, and what is internal*.

It does *not* cover:

- The [USER-GUIDE.md](USER-GUIDE.md) tutorial path.
- The Maven / Gradle / IntelliJ configuration (see
  [MAVEN-CONFIGURATION.md](MAVEN-CONFIGURATION.md) and the project
  `README.md`).
- The 0.6 → 0.7 migration story (see
  [MIGRATION-0.6-to-0.7.md](MIGRATION-0.6-to-0.7.md)).
- The statistical methodology (see
  [STATISTICAL-COMPANION.md](STATISTICAL-COMPANION.md)).

---

## The two authoring annotations

The author-facing surface is two annotations, both attribute-free.

```java
@ProbabilisticTest
void shoppingMeetsBaseline() {
    PUnit.testing(this::shoppingBaseline)
            .samples(100)
            .criterion(PassRate.empirical())
            .assertPasses();
}

@Experiment
void shoppingBaseline() {
    PUnit.measuring(shoppingSampling(1000), shoppingFactors()).run();
}
```

Each annotation is a JUnit `@Test` meta-tagged `@Tag("punit")`. Every
parameter — sample count, intent, criterion, threshold, baseline
binding — lives in the method body, on the typed builder. The
annotations carry no attributes by design: configuration must be
typed, must compile-check against the use case, and must be
expressible in the language's normal flow. Annotation attributes are
a strictly weaker channel — strings, integers, class literals — and
historically forced reflection-driven attribute reading the framework
no longer wants.

The annotations live at:

- `org.javai.punit.api.ProbabilisticTest`
- `org.javai.punit.api.Experiment`

Their JUnit meta-tags are inherited; they are not authored separately
on each test method.

---

## The PUnit entry point

`org.javai.punit.runtime.PUnit` is the one entry point an author
imports. Everything else flows from a static call on `PUnit`.

```java
PUnit.testing(sampling, factors)         // probabilistic test
PUnit.testing(sampling)                  // probabilistic test, NoFactors
PUnit.measuring(sampling, factors)       // measure experiment
PUnit.exploring(sampling)                // explore experiment
PUnit.optimizing(sampling)               // optimize experiment
```

Each factory returns a typed builder. The four builder families
(`TestBuilder`, `MeasureBuilder`, `ExploreBuilder`, `OptimizeBuilder`)
all live as static-nested classes on `PUnit`. They wrap the
corresponding spec-builder in `org.javai.punit.api.spec` and add the
test-harness-aware terminal:

| Builder            | Terminal(s)                                      | What the terminal does                           |
|--------------------|--------------------------------------------------|--------------------------------------------------|
| `TestBuilder`      | `.assertPasses()`, `.build()`                    | Drives the spec; translates Verdict to JUnit.    |
| `MeasureBuilder`   | `.run()`, `.build()`                             | Drives + emits baseline YAML.                    |
| `ExploreBuilder`   | `.run()`, `.build()`                             | Drives + emits per-config exploration YAML.      |
| `OptimizeBuilder`  | `.run()`, `.build()`                             | Drives + emits optimization history YAML.        |

`assertPasses()` translates the resulting Verdict:

- **PASS** → JUnit pass.
- **FAIL** → `org.opentest4j.AssertionFailedError`, carrying the
  verdict's explanation and per-criterion detail.
- **INCONCLUSIVE** → `org.opentest4j.TestAbortedException` —
  configuration / environment problem (no baseline, identity
  mismatch, …), not a service degradation.

`run()` returns normally on success; engine-level *defects*
propagate as runtime exceptions.

`build()` is used in the empirical pair pattern (see
[The empirical pair pattern](#the-empirical-pair-pattern)) to expose
an `Experiment` value to `PassRate.empiricalFrom(...)`.

The `@ProbabilisticTest`-annotated method always uses `assertPasses()`
or `build()`. The `@Experiment`-annotated method always uses `run()`
or `build()`. JUnit always reports pass for the test method itself
because punit owns the verdict; what the framework computed lives in
the Verdict, not in the JUnit pass/fail signal.

---

## The Sampling primitive

`org.javai.punit.api.Sampling<FT, IT, OT>` describes *how to produce
samples* — the use case factory, the input cycle, the sample count,
and the sample-loop governors (budgets, exception policy,
failure-retention cap).

```java
Sampling<F, I, O> sampling = Sampling.of(
        f -> new MyUseCase(f),       // factory bound to factors
        samples,                     // sample count
        input1, input2, input3);     // round-robin input cycle
```

A `Sampling` does not carry a factor instance. Factors are supplied
at the spec entry point (`testing`, `measuring`, …). This split is
load-bearing: the *same Sampling* is shared between a measure
baseline and the probabilistic test paired against it; the factor
instances at each call site are also the same; together they
guarantee that the test and the baseline draw from the same sampling
population (see [The empirical pair pattern](#the-empirical-pair-pattern)).

Sampling carries the per-test-loop governors:

- `BudgetExhaustionPolicy` — FAIL or EVALUATE_PARTIAL on budget
  exhaustion.
- `ExceptionPolicy` — FAIL_SAMPLE or ABORT_TEST on an unexpected
  thrown exception (this is *unexpected* — see
  [Outcome vs exception convention](#outcome-vs-exception-convention)).
- A failure-retention cap (RC14: how many failure exemplars the
  engine keeps for diagnostic purposes).

---

## Use Case and Contract

A use case is one Java class implementing `UseCase<FT, IT, OT>`:

```java
public class ShoppingBasketUseCase
        implements UseCase<Factors, Instruction, BasketTranslation> {

    @Override
    public Outcome<BasketTranslation> invoke(Instruction input, TokenTracker tracker) {
        // …call the service, record cost via tracker.recordTokens(n)…
        return Outcome.ok(translation);
        // or Outcome.fail("invalid-action", "actions list was empty");
    }

    @Override
    public void postconditions(ContractBuilder<BasketTranslation> b) {
        b.ensure("Has actions",
                t -> t.actions().isEmpty()
                        ? Outcome.fail("empty-actions", "actions list was empty")
                        : Outcome.ok())
         .ensure("All actions known", ShoppingBasketUseCase::allKnown);
    }
}
```

`UseCase<FT, IT, OT>` extends `Contract<IT, OT>`. The author writes
one `implements` clause and overrides three methods minimum (`invoke`
and `postconditions`, plus optional metadata methods).

### What the framework reads from a Use Case

| Method                                | Purpose                                              | Default                       |
|---------------------------------------|------------------------------------------------------|-------------------------------|
| `invoke(IT, TokenTracker)`            | The service call. Returns Outcome.                   | abstract — author overrides   |
| `postconditions(ContractBuilder<OT>)` | Declares the contract clauses.                       | abstract — author overrides   |
| `id()`                                | Stable identifier for filenames and logs.            | kebab-cased simple class name |
| `description()`                       | Human-readable description.                          | empty                         |
| `warmup()`                            | Discarded warmup invocations before counted samples. | 0                             |
| `pacing()`                            | Rate / concurrency limits the engine respects.       | unlimited                     |
| `covariates()`                        | Covariates the framework records on every sample.    | empty                         |
| `inputSource()` / `inputSupplier()`   | The input cycle.                                     | none — supplied via Sampling  |
| `maxLatency()`                        | Per-sample wall-clock bound.                         | empty                         |

### Lifecycle and state

The framework constructs **one Use Case instance per factor
configuration** (via the factory declared in Sampling) and reuses it
across every sample for that configuration. Implementations may carry
internal state — caches, connection handles, long-lived clients — but
must not mutate observable behaviour during a configuration's run.
Pre-existing caches and pools are fine; live reconfiguration in
response to sample outcomes is not.

### What goes in `invoke` vs `postconditions`

Keep `invoke` primitive:

- `invoke` does the service call. It returns `Outcome.ok(value)` for
  a successful response, `Outcome.fail(name, message)` for an
  *expected* business-level failure (a contract violation, a
  validation error, a service-returned error code).
- A thrown exception aborts the run — that is the framework's
  treatment of a *defect*, not of a sample that didn't meet
  contract.
- Contract judgement — "the response had this property" or "the
  response did not have this property" — belongs in
  `postconditions(ContractBuilder)`. That is where each clause gets
  a name and surfaces clause-named diagnostics on failure.
- Service-level errors (the service returned a structured error
  code) belong in `invoke` returning `Outcome.fail`.

This is a recorded discipline — see
[Outcome vs exception convention](#outcome-vs-exception-convention).

---

## Postconditions: the ContractBuilder surface

`org.javai.punit.api.ContractBuilder<O>` is the authoring surface for
the contract clauses. Authors never construct one directly — the
framework supplies a fresh builder to `postconditions(ContractBuilder<O>)`
and collects the result.

Two methods, both fluent:

```java
public final class ContractBuilder<O> {
    public ContractBuilder<O> ensure(String name, Function<O, Outcome<Void>> predicate);
    public <D> ContractBuilder<O> deriving(
            String name,
            Function<O, D> transform,
            Consumer<ContractBuilder<D>> sub);
}
```

- **`ensure(name, predicate)`** — leaf clause. Predicate returns
  `Outcome.ok()` on pass or `Outcome.fail(name, message)` on fail.
  The name is the clause's stable identifier in the verdict.
- **`deriving(name, transform, sub)`** — adds a derivation step that
  transforms the response mid-chain, then evaluates a sub-builder
  against the derived value. Used when one clause needs a derived
  view of the response that several other clauses depend on.

A `Postcondition`, `PostconditionEvaluator`, etc. exist as
public-but-internal types in `org.javai.punit.api`. Authors compose
through `ContractBuilder`; do not subclass these types directly.

A *parallel* and *unused* `org.javai.punit.contract` package contains
a duplicate `ServiceContract`, `Postcondition`, matcher family, etc.
That package is **dead code** scheduled for deletion under
`DIR-CONTRACT-PACKAGE-REMOVAL-punit`. Authors and contributors must
**not** reference it. Treat it as not-there; the linter and reviewers
will agree.

---

## Criteria

A criterion is a spec-level claim evaluated against the observed
sample aggregate. Criteria live in
`org.javai.punit.api.spec.Criterion<OT, S extends BaselineStatistics>`
as the abstract surface; concrete criteria with statistical
machinery live in `org.javai.punit.internal.engine.criteria` (so the
api package stays free of statistical dependencies — see
[Statistics isolation rule](#statistics-isolation-rule)).

### PassRate (Bernoulli pass-rate)

Three factory forms:

```java
PassRate.meeting(0.95, ThresholdOrigin.SLA);        // contractual
PassRate.empirical();                                // closest-match baseline
PassRate.empiricalFrom(this::shoppingBaseline);     // pinned baseline
```

- **`meeting(τ, origin)`** — declared threshold, NORMATIVE origin
  (SLA / SLO / Policy). With Verification intent the Feasibility
  Gate enforces sample-size adequacy.
- **`empirical()`** — closest-match baseline lookup; threshold
  derived at evaluation time from the resolved baseline's pass
  rate, via the one-sided Wilson lower bound at the test sample
  size (Statistical Companion §3.4 / §4.3.2).
- **`empiricalFrom(supplier)`** — pinned baseline; the supplier
  returns an `Experiment` value built via
  `Experiment.measuring(...).build()`. See
  [The empirical pair pattern](#the-empirical-pair-pattern).

### PercentileLatency

Asserts a percentile threshold on the latency-given-success
distribution. Counterpart to `@Latency` (annotation form, in
`api`). Reads `LatencyStatistics` from the resolved baseline.

### Composing criteria

```java
PUnit.testing(sampling, factors)
        .criterion(PassRate.empirical())
        .criterion(PercentileLatency.atP95(...))
        .reportOnly(SomeDiagnosticCriterion.of(...))
        .assertPasses();
```

`.criterion(c)` contributes to the combined verdict; `.reportOnly(c)`
is evaluated and attached but excluded from composition. The
combined verdict is the conjunction over `.criterion(...)` calls
(every contributing criterion must PASS for the verdict to PASS).

---

## The empirical pair pattern

The framework's structural guarantee that an empirical comparison is
statistically meaningful. The author publishes a `Sampling<F, I, O>`
helper used by both a measure baseline and the probabilistic test
paired against it:

```java
private Sampling<F, I, O> shoppingSampling(int samples) {
    return Sampling.of(f -> new ShoppingBasketUseCase(f), samples, ...inputs);
}

@Experiment
Experiment shoppingBaseline() {
    return Experiment.measuring(shoppingSampling(1000), shoppingFactors()).build();
}

@ProbabilisticTest
void shoppingMeetsBaseline() {
    PUnit.testing(shoppingSampling(100), shoppingFactors())
            .criterion(PassRate.empiricalFrom(this::shoppingBaseline))
            .assertPasses();
}
```

The shared `Sampling` reference enforces — at compile time — that the
test and the baseline carry the same use-case factory, same input
cycle, same loop governors. The shared factors call site enforces
the same factor record. The framework's empirical-baseline resolver
matches the test's factors against the measure's stored baseline.

Why this matters: an empirical probabilistic test asks *"has the
service's pass rate degraded from the rate the baseline measured?"*
The question is statistically coherent only when the test and
baseline draw from the same sampling population. By passing the same
`Sampling` value into both, Java's reference semantics enforce that
sameness. The pairing is structural, not a brittle prose convention.

---

## Spec terminals and what each one returns

| Builder method               | Returns                                         | Throws                                                                 |
|------------------------------|-------------------------------------------------|------------------------------------------------------------------------|
| `TestBuilder.assertPasses()` | `void`                                          | `AssertionFailedError` on FAIL; `TestAbortedException` on INCONCLUSIVE |
| `TestBuilder.build()`        | `ProbabilisticTest`                             | engine-level defects propagate                                         |
| `MeasureBuilder.run()`       | `void` (artefact written to disk)               | engine-level defects propagate                                         |
| `MeasureBuilder.build()`     | `Experiment` (used by `PassRate.empiricalFrom`) | none                                                                   |
| `ExploreBuilder.run()`       | `void` (artefacts written per configuration)    | engine-level defects propagate                                         |
| `OptimizeBuilder.run()`      | `void` (history file written)                   | engine-level defects propagate                                         |

`assertPasses` is the only path that translates a Verdict into a
JUnit signal; the other terminals leave the Verdict in the Verdict
sink chain.

---

## Public package contract

The packages an author imports and what each one carries. The same
public/internal split is **structurally enforced** as of 0.7.x by
the JPMS `module-info.java` declarations under each module's
`src/main/java/`: the `exports` clauses are the authoritative
public-surface list, and an external **modular** consumer cannot
import a non-exported package — the compiler refuses, and the
runtime throws `IllegalAccessError`. **Unnamed-module** consumers
(plain-classpath builds without a `module-info.java` of their own)
still see all classes on the classpath as a legacy concession;
they are guarded by the `org.javai.punit.internal.*` namespace
prefix and the ArchUnit regression rules.

| Package                                                                               | Contains                                                                                                                                      | Author may import?                                            |
|---------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------|
| `org.javai.punit.api`                                                                 | Annotations, UseCase, Contract, ContractBuilder, Sampling, Factor support, ValueMatcher, TestIntent, ThresholdOrigin, Pacing, PacingConfiguration, TokenTracker, … | yes                                                           |
| `org.javai.punit.api.spec`                                                            | Spec types (Spec, Experiment, ProbabilisticTest, Criterion, EvaluationContext, FactorsStepper, NextFactor, BaselineProvider, …)               | yes                                                           |
| `org.javai.punit.api.covariate`                                                       | Covariate (interface) and built-in covariate categories                                                                                       | yes                                                           |
| `org.javai.punit.runtime`                                                                      | `PUnit` entry point only — emitters live under `internal.runtime`                                                                             | yes — for `PUnit` only                                        |
| `org.javai.punit.verdict`                                                                      | Verdict types, sinks, RunMetadata, `TokenMode` enum                                                                                            | yes — for sink registration                                   |
| `org.javai.punit.statistics`                                                                   | Wilson, percentile, threshold derivation, feasibility evaluation                                                                              | yes — but rarely needed; the criteria already wrap statistics |
| `org.javai.punit.internal.engine.*` (criteria, baseline, explore, optimize, covariate, spec, …) | Engine internals and concrete criterion impls                                                                                                 | **no** — internal                                             |
| `org.javai.punit.internal.reporting`                                                           | Internal rendering helpers                                                                                                                    | **no** — internal                                             |
| `org.javai.punit.internal.runtime`                                                             | Emitters (`BaselineEmitter`, `ExploreEmitter`, `OptimizeEmitter`), resolvers, composer — driven by `PUnit`                                    | **no** — internal                                             |
| `org.javai.punit.internal.util`                                                                | Internal utilities                                                                                                                            | **no** — internal                                             |
| `org.javai.punit.junit5.*` (in punit-junit5)                                          | JUnit 5 extensions; one author-facing type (`UseCaseProvider`); annotations live in `punit-core`                                              | yes — for `UseCaseProvider` only                              |
| `org.javai.punit.report.*` (in punit-report)                                          | Verdict XML reader / writer; HTML report                                                                                                      | yes — for sink consumption                                    |
| `org.javai.punit.sentinel.*` (in punit-sentinel)                                      | Sentinel runtime + CLI                                                                                                                        | author-side: for reliability spec authoring; no JUnit deps    |

---

## Architecture-test catalogue

These tests fail the build on violation. Each protects a specific
invariant from regression.

| Test                                                            | Module         | Enforces                                                                                                                                                                                                             |
|-----------------------------------------------------------------|----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `CoreArchitectureTest`                                          | punit-core     | Package-level rules in punit-core. No `org.junit` deps in non-api packages.                                                                                                                                          |
| `CoreArchitectureTest.apiPackageMustNotDependOnJUnitExtensions` | punit-core     | The api package may reference JUnit annotation types as meta-annotations only — never extension, engine, or platform types.                                                                                          |
| `CoreArchitectureTest.statisticsModuleMustBeIsolated`           | punit-core     | `org.javai.punit.statistics` has zero dependencies on other punit packages.                                                                                                                                          |
| `RuntimeArchitectureTest`                                       | punit-core     | `org.javai.punit.runtime` has zero `org.junit` deps. Sentinel-deployable code reaches PUnit here without a JUnit classpath.                                                                                          |
| `Junit5ApiPackageContentsTest`                                  | punit-junit5   | The junit5 api package contains JUnit-specific public types only (e.g. `UseCaseProvider`); no annotation declarations (those belong in punit-core/api).                                                              |
| `ArchitectureTest`                                              | punit-junit5   | Abstraction-level discipline (evaluators don't depend on reporting; renderers don't depend on statistics).                                                                                                           |
| `SentinelArchitectureTest`                                      | punit-sentinel | Zero `org.junit` deps in punit-sentinel.                                                                                                                                                                             |
| `RequirementCodeIsolationTest`                                  | all modules    | Orchestrator-internal codes (CT/EX/LT/PT/RC/RP/SC/SN/TH/UC/XM/DG) MUST NOT appear anywhere in src/main/java or src/test/java — including @DisplayName strings, test-class names, test-method names, string literals. |
| `ArtefactEmissionRegressionTest`                                | punit-core     | Every `Experiment.Kind` must emit at least one artefact when `.run()` succeeds. Exhaustive switch over the enum — adding a new Kind without wiring an emitter is a compile-time fail.                                |
| `PackageStructureArchitectureTest`                              | punit-core     | The **target** package structure punit is moving toward under the cleanup directives `DIR-PACKAGE-DRIFT-FIX-punit` and `DIR-INTERNAL-NAMESPACE-punit`. Each rule names the directive and step that closes it. Current violations are captured in `archunit_store/` via `FreezingArchRule` so the build stays green during the cleanup; each refactor commit shrinks the store. When every store file is empty, the cleanup arc is complete and the rules become permanent regression guards. See the test class javadoc for the full workflow. |

To run all architecture-style guards:

```bash
./gradlew test --tests "*ArchitectureTest" --tests "*Junit5ApiPackageContentsTest" --tests "*RequirementCodeIsolationTest" --tests "*ArtefactEmissionRegressionTest"
```

To run only the package-structure rules (useful while working through the cleanup arc):

```bash
./gradlew test --tests "*PackageStructureArchitectureTest"
```

To re-baseline a frozen rule after refining its expression:

```bash
./gradlew test --tests "*PackageStructureArchitectureTest" -Darchunit.freeze.refreeze=true
```

---

## Statistics isolation rule

**Statistical calculations live only in `org.javai.punit.statistics`.
Period.**

This is the architectural maxim that protects punit's statistical
core. The package has no dependencies on any other punit package — a
reader of the framework can validate the statistical core in
isolation, against published formulae (the Statistical Companion),
without tracing through the rest of the codebase. Enforced by
`CoreArchitectureTest.statisticsModuleMustBeIsolated`.

### Consequences for code outside the statistics package

- **Never reimplement** a calculation that already lives in
  `BinomialProportionEstimator`, `LatencyDistribution`, or any other
  member of the statistics package. Reuse the existing class.
- **Never introduce** a new statistical-arithmetic helper outside
  `org.javai.punit.statistics`. If you find yourself reaching for
  `Math.sqrt`, an inverse-normal-CDF approximation, or
  `MessageDigest`-of-canonical-form in any other package, stop —
  the calculation belongs in the statistics package, with its own
  tests, against the formulation in the Statistical Companion.
- **The api package must not depend on Apache Commons.** When an
  api-side type needs a statistical calculation, the implementation
  belongs in the engine layer, not in the api package. Concrete
  `Criterion` implementations whose `evaluate()` consumes statistics
  live in `org.javai.punit.internal.engine.criteria` (e.g. `PassRate`)
  — the `Criterion` interface stays in `org.javai.punit.api.spec`, the
  statistical machinery stays in `org.javai.punit.statistics`, and
  the criterion bridges the two.

A breach of this rule — typically a duplicated Wilson-score
calculation, a self-contained inverse-normal-CDF approximation, or
similar — must be removed and replaced with a call into the
existing statistics-package class. There is **no exception** for
performance, convenience, or "the architecture would otherwise be
inconvenient": those are signals that the type doing the
calculation belongs in a different package, not that the rule
should bend.

This rule is the *enforcement* (in punit) of the family ontology's
**Statistical isolation** invariant.

---

## Sentinel deployability

`punit-sentinel` is the JUnit-free runtime engine. It evaluates the
same Service Contract as the test suite, but against a live system,
on a schedule, without a JUnit / cargo-test harness.

The architectural consequence is that **Verdict construction, Wilson
statistics, and Verdict XML emission must be core concerns, free of
test-harness dependencies**. The Sentinel reaches the engine through
`PUnit` / `runtime`, not through any JUnit extension. Enforced by:

- `RuntimeArchitectureTest` — zero `org.junit` deps in
  `org.javai.punit.runtime` and `org.javai.punit.internal.runtime`.
- `SentinelArchitectureTest` — zero `org.junit` deps in
  `punit-sentinel`.

Two family invariants the Sentinel surfaces specifically:

- **Build-time baseline embedding.** Every EMPIRICAL-origin test
  must carry its embedded default baseline at sentinel build time.
  Sentinel runtime cannot access the source tree at sample time;
  missing a default baseline is a build failure, not a runtime
  warning. Normative-origin tests are exempt (the threshold is
  declared, not derived).
- **Emits, never installs.** Measurement output and test input are
  independently addressable. The Sentinel may emit a baseline
  measurement to its measurement output but never overwrites the
  test input automatically. Promotion of a measurement to a
  committed baseline is operator-owned.

Authoring a reliability specification: see
[`SENTINEL-DEPLOYMENT-GUIDE.md`](SENTINEL-DEPLOYMENT-GUIDE.md).

---

## Outcome vs exception convention

Business-level failures travel as data, not as exceptions. Use
`org.javai.outcome.Outcome<T>` (from the `org.javai:outcome`
library): return `Outcome.ok(value)` for success,
`Outcome.fail(name, message)` for an expected business-level failure.
In the typed authoring surface this surfaces as `UseCaseOutcome<OT>`
whose `value` is an `Outcome<OT>`; authors write
`UseCaseOutcome.ok(...)` or `UseCaseOutcome.fail(...)` indirectly
through the Contract `apply` dispatch.

Reserve `throw` for genuine *defects*: programming mistakes
(`NullPointerException`, `IllegalStateException`,
misuse-signalling `IllegalArgumentException`), misconfiguration, and
catastrophe (`OutOfMemoryError`, `StackOverflowError`). A thrown
exception from a `Contract#invoke` aborts the run — that is the
correct response to a bug, not to a sample that happened not to meet
its contract.

The `ExceptionPolicy` (RC12 / RC13) covers genuinely *unexpected*
exceptions. An author who wants the engine to convert an unexpected
exception into a counted failed sample opts in via
`ExceptionPolicy.FAIL_SAMPLE`; the default is `ABORT_TEST`, which
preserves the defect signal.

This convention is the family invariant **Outcome vs exception
discipline**, applied to Java idiomatically. Per-language note: in
feotest the equivalent is idiomatic `Result<T, E>`.

---

## Verdict XML wire format

punit serialises every Verdict to XML using the family's
**verdict-XML interchange standard (RP07)**:

- **Canonical specification:** `inventory/catalog/reporting/RP07-verdict-xml-interchange/README.md`
  (in javai-orchestrator).
- **Canonical XSD schema:** `inventory/catalog/reporting/RP07-verdict-xml-interchange/verdict-1.0.xsd`.
- **Namespace:** `http://javai.org/verdict/1.0`.
- **Root element:** `<verdict-record>`.
- **punit's local XSD copy:** `punit-report/src/main/resources/org/javai/punit/report/verdict-1.0.xsd`
  — must diff clean against the canonical.

The RP07 standard includes pacing, environment metadata, baseline
expiration, and correlation ID. The only punit-specific field not
serialised to XML is `junitPassed` (JUnit always passes in punit
because punit owns the verdict; meaningless outside the JUnit
context).

When modifying the XML format:

- **Consult the RP07 specification in the orchestrator first.**
- Changes that affect schema semantics should be proposed in the
  orchestrator, not made unilaterally in punit.
- `<statistics>` carries `wilson-lower` only — the one-sided Wilson
  lower bound at the verdict's `confidence-level`. The upper bound
  carries no operational meaning under a left-tailed test and is
  not emitted. The local XSD is diff-clean against the canonical
  XSD; round-trip and schema-validation tests live in
  `punit-report`.

---

## Versioning and binary compatibility

punit is pre-1.0. Breaking API changes are explicit and announced:

- The minor version under 0.x signals breaking
  (`0.6 → 0.7` carried the typed-builder migration).
- Each release carries a `MIGRATION-X.Y-to-X.Z.md` guide for any
  break.
- The migration guide carries a coding-assistant prompt the user
  pastes into Claude Code / Cursor; the prompt walks the user's
  codebase and applies the migration mechanically.

There is no deprecation cycle owed pre-1.0. A 0.x.0 release that
breaks 0.x-1 callers is in policy.

After 1.0:

- Major version bumps signal breaking — and require the same
  migration-guide treatment.
- Module coordinates (`org.javai:punit`, `org.javai:punit-core`, …)
  stay constant. Renames are a tool of last resort.

The release process itself is documented in
[`punit/CLAUDE.md`](../CLAUDE.md) under "Release Process".

---

## What this document does not do

- It does not enumerate every public method on every type. The
  Javadoc + IDE auto-complete is authoritative on signatures; this
  document is authoritative on shape, contract, and discipline.
- It does not duplicate the user guide. New users start at
  [`USER-GUIDE.md`](USER-GUIDE.md); this document is for
  contributors and code-generation agents.
- It does not document the methodology. Statistical formulae and
  derivations live in [`STATISTICAL-COMPANION.md`](STATISTICAL-COMPANION.md);
  this document references the companion via the family ontology.
- It does not invent abstractions. Concepts are owned by the family
  ontology ([`javai-orchestrator/inventory/DOMAIN-ONTOLOGY.md`](../../inventory/DOMAIN-ONTOLOGY.md));
  punit's ontology ([`DOMAIN-ONTOLOGY.md`](DOMAIN-ONTOLOGY.md)) maps
  them onto Java idioms; this document maps them onto a developer's
  fingertips.
