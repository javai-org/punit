# Design: punit experiment DX

> **⚠ Superseded.** The second stage of this design (factor-record
> restructuring: `@FactorName`, typed `register(Class, Class, Function)`,
> `Stream<F>` semantics on `@FactorSource`, removal of `@ConfigSource` /
> `NamedConfig` / `@Factor` / `FactorArguments` / `@ControlFactor` /
> `FactorValues`) will **not** be implemented as planned here. A more
> impactful API redesign is being conducted from the
> **javai-orchestrator** project and will span the javai product family
> rather than punit in isolation. Work from that redesign will land in
> new design documents under the orchestrator repo.
>
> This document is preserved for historical context: it records the
> principles that shaped the first-stage cleanup (PR #1 below) and the
> thinking that fed into the broader cross-project redesign.

## Status

What landed from this design:

- **PR #1 — deprecated surface removal.** Merged. Deleted `@FactorSetter`,
  `@FactorGetter`, `registerAutoWired`,
  `OptimizeExperiment.initialControlFactorValue`,
  `ExploreExperiment.expiresInDays`. Renamed
  `initialControlFactorSource` → `initialFactor`. Dropped the
  `@FactorGetter` fallback in `OptimizeStrategy`. `punitexamples` was
  aligned in a companion PR.

What was **planned** but will not land from this design:

- **PR #2 — factor-record restructuring.** Abandoned in favour of a
  javai-orchestrator-driven redesign. The original planned scope was:
  `@FactorName`, the typed `register(Class, Class, Function)` overload,
  new `@FactorSource` semantics over `Stream<F>`, and deletion of the
  remaining pre-IUC factor machinery (`@ConfigSource`, `NamedConfig`,
  `@Factor`, `FactorArguments`, `@ControlFactor`, `FactorValues`).

The sections below describe the full originally-intended target DX. §5
Migration plan is likewise a record of the plan that was in flight;
it no longer describes scheduled work.

## Scope

This design targets punit's three experiment surfaces (`@MeasureExperiment`,
`@ExploreExperiment`, `@OptimizeExperiment`) and the machinery that sits
behind them (`UseCaseProvider`, factor / config sources, parameter
injection for factor values).

It does **not** touch `@ProbabilisticTest`, spec/baseline file formats,
budget enforcement, early-termination evaluation, or the reporting path.
Those are orthogonal and should be preserved.

## Framing

The purpose of this work is to design a clean, focussed, intuitive DX
for punit experiments — centred on the **immutable use case principle**
and shedding the accumulated baggage of earlier iterations that
preceded it.

Feotest's post-refactor shape is a useful source of principles
(explicit identity, framework-owned instance lifecycle, first-class
factor types, cross-experiment consistency), but it is not the target.
PUnit is Java inside JUnit 5 and should land on its own idiomatic shape.

The immutable use case principle is stated informally across the
codebase (deprecation notices on `@FactorSetter` / `@FactorGetter`,
`plan/REQ-DOE.md`, CHANGELOG entries for 0.6.0) but not codified in one
place. For the purposes of this design it is:

> A use case is an immutable object. Every factor value the experiment
> varies is supplied at construction time and is frozen thereafter for
> the lifetime of the instance. Each sample executes against a freshly
> constructed instance (or a reused instance whose internal state does
> not depend on prior samples). There is no setter-based configuration,
> no between-sample mutation of use case state, no `@BeforeEach`
> patching of an instance between runs.

Everything below follows from that principle.

## Terminology — factor is first-class

In the javai product family, **factor** is a first-class concept
drawn from the Design of Experiments (DoE) tradition. A factor is an
aspect of an experiment's configuration — a dimension along which a
trial can vary. Factors have levels; a specific tuple of levels is a
treatment combination; a stream of treatment combinations is the
design matrix for an experiment.

This design uses "factor" wherever DoE would use it, in preference to
generic names like "config", "setting", or "variant". Surface the
domain vocabulary where the domain vocabulary applies:

- A user-defined type representing the variation an experiment sweeps
  over is a **factor type** (`ModelFactor`, `Temperature`), not a
  "config".
- The annotation referencing a method that supplies factor values is
  `@FactorSource`, not `@ConfigSource`.
- The override for a field-derived factor-combination name is
  `@FactorName`, not `@ConfigName`.
- An output filename like `model-gpt-4o-mini_temperature-0.0.yaml`
  carries a **factor-combination name** — not a "config name".

The rule is applied where Factor is *what we are talking about*.
Existing attribute names where "config" reads as natural English for
"configuration run" (e.g. `samplesPerConfig`) are not reflexively
renamed; see §3.Q9 for the per-attribute reasoning.

This terminology choice is not cosmetic. The javai family's
methodology — statistical rigour for stochastic services — is rooted
in DoE. Losing that vocabulary hides the methodology behind generic
software-configuration language.

## Constraint — API only, outputs unchanged

This initiative changes the **API surface** punit presents to users
and the internal wiring that supports it. It does **not** change any
produced artifact. Specifically, the following are stable boundaries
that no proposal below may touch:

- The structure, field names, and values of produced YAML spec files
  — `specs/` (baselines), `explorations/`, `optimizations/`.
- The naming convention for spec files and their containing
  directories.
- The RP07 verdict XML interchange output. Its schema is owned by
  the orchestrator catalog
  (`javai-orchestrator/inventory/catalog/reporting/RP07-verdict-xml-interchange/`)
  and cannot be unilaterally altered from punit.
- The content of any report, log, or telemetry emission.

Every output produced by a migrated experiment under the new API
must be byte-identical to the output of the equivalent experiment
under the old API. Users who version-control baselines, or who read
specs from downstream tooling, must observe no change.

Where a proposal below would otherwise imply an output change, it
must be re-expressed as an API refactor behind the existing output
contract. The byte-identical-output requirement is a test in §5.4.

## TL;DR — the target DX

1. Identity comes from the `@UseCase` annotation on the class. The
   experiment annotation does not repeat `useCase = Foo.class`.
2. Instance supply is via `UseCaseProvider` with a single registration
   shape per experiment type: a `Supplier<T>` for measure, a
   `Function<F, T>` for explore and optimize (where `F` is a
   user-defined factor type).
3. Factors are first-class user-defined types — typically a Java
   `record`. No stringly-typed `FactorArguments`, no `@Factor("name")`
   parameter injection.
4. Explore takes `Stream<F>`. Optimize takes an initial `F` and an
   `F -> F` mutator. Measure has no factor.
5. The trial method signature is uniform across all three experiments:
   `(T useCase, InputType input, OutcomeCaptor captor)`. The use case
   instance is always parameter-injected; nothing is captured.

What goes away: `@FactorSource`, `@Factor`, `FactorArguments`,
`@ConfigSource`, `NamedConfig`, `@FactorSetter`, `@FactorGetter`,
`@ControlFactor`, `registerWithFactors`, `registerAutoWired`,
`controlFactor = "…"`, `initialControlFactorValue = "…"`.
See §6 for the complete deletion list.

---

## 1. Inventory of the current experiment DX

### 1.1 Annotation attributes

#### `@MeasureExperiment` — `punit-core/…/api/MeasureExperiment.java`

| Attribute | Type | Default | Role |
|---|---|---|---|
| `useCase` | `Class<?>` | `Void.class` | names the use case class |
| `samples` | `int` | `1000` | sample count |
| `timeBudgetMs` | `long` | `0` | wall-clock budget |
| `tokenBudget` | `long` | `0` | token budget |
| `experimentId` | `String` | `""` | id for output naming |
| `expiresInDays` | `int` | `0` | baseline validity window |

#### `@ExploreExperiment` — `punit-core/…/api/ExploreExperiment.java`

| Attribute | Type | Default | Role |
|---|---|---|---|
| `useCase` | `Class<?>` | `Void.class` | names the use case class |
| `samplesPerConfig` | `int` | `1` | per-config sample count |
| `timeBudgetMs` | `long` | `0` | wall-clock budget (total) |
| `tokenBudget` | `long` | `0` | token budget (total) |
| `experimentId` | `String` | `""` | id for output naming |
| `expiresInDays` | `int` | `0` | baseline validity window |
| `skipWarmup` | `boolean` | `false` | suppress use-case-declared warmup |

Requires exactly one of `@FactorSource` or `@ConfigSource` as a
sibling annotation.

#### `@OptimizeExperiment` — `punit-core/…/api/OptimizeExperiment.java`

| Attribute | Type | Default | Role |
|---|---|---|---|
| `useCase` | `Class<?>` | `Void.class` | names the use case class |
| `controlFactor` | `String` | *required* | factor name (string) |
| `initialControlFactorValue` | `String` | `""` | inline initial value |
| `initialControlFactorSource` | `String` | `""` | method-ref initial value |
| `scorer` | `Class<? extends Scorer<…>>` | *required* | scorer class |
| `mutator` | `Class<? extends FactorMutator<?>>` | *required* | mutator class |
| `objective` | `OptimizationObjective` | `MAXIMIZE` | min/max |
| `samplesPerIteration` | `int` | `20` | per-iteration sample count |
| `maxIterations` | `int` | `20` | hard iteration cap |
| `noImprovementWindow` | `int` | `5` | early stop window |
| `timeBudgetMs` | `long` | `0` | wall-clock budget (total) |
| `tokenBudget` | `long` | `0` | token budget (total) |
| `experimentId` | `String` | `""` | id for output naming |
| `skipWarmup` | `boolean` | `false` | suppress warmup |

### 1.2 `UseCaseProvider` — `punit-junit5/…/api/UseCaseProvider.java`

Three registration paths (priority order at `getInstance(...)`):

1. **Per-config prebuilt instance** (set by `ExploreStrategy` when
   using `@ConfigSource`): `setCurrentConfigInstance(Object)`.
2. **Factor-driven factory** for explore/optimize:
   `registerWithFactors(Class<T>, Function<FactorValues, T>)`, paired
   with per-trial `setCurrentFactorValues(Object[], List<String>)`.
3. **Plain factory**: `register(Class<T>, Supplier<T>)`.
4. **Deprecated**: `registerAutoWired(Class<T>, Supplier<T>)` — applies
   `@FactorSetter` reflection to a constructed instance; marked
   `@Deprecated(since = "0.6.0")`.

Parameter-injected via JUnit's `ParameterResolver`. Scoped per test
class; per-config and per-factor-combination state set/cleared by
the corresponding strategy before and after each trial.

### 1.3 Two parallel mechanisms for supplying variation in EXPLORE

| | `@FactorSource` | `@ConfigSource` |
|---|---|---|
| Annotation target | method | method |
| Referenced method returns | `Stream<FactorArguments>` | `Stream<NamedConfig<T>>` |
| Factor naming | via `names(...)` builder or `@FactorSource(factors = …)` | implicit (config name is the carrier) |
| Instance construction | via `provider.registerWithFactors(...)` in `@BeforeEach` | caller constructs inline inside the stream |
| Factor values reach trial | via `@Factor("name") String x` parameters | not applicable — instance is prebuilt |
| Era | pre-IUC (retained and bridged via `registerWithFactors`) | post-IUC (instances are already-immutable at stream time) |

Both mechanisms end up at the same runtime shape (a fresh `T` per
trial invocation) but via different user-facing surfaces. The
`FactorValues` type is stringly-typed (`getString`, `getDouble`).

### 1.4 Optimize-specific surface

- `@ControlFactor String x` — parameter injection of the current
  mutated factor value into the trial body.
- `@FactorGetter` / `@FactorSetter` on the use case — **deprecated
  since 0.6.0**; still a fallback path inside
  `OptimizeStrategy.getControlFactorFromUseCase` when
  `initialControlFactorValue` and `initialControlFactorSource` are both
  unset.
- `FactorMutator<F>` / `Scorer<OptimizationIterationAggregate>` — typed
  SPIs, but the annotation-level `Class<? extends FactorMutator<?>>`
  erases `F` at the declaration site.

### 1.5 Representative real-world usage (from `../punitexamples`)

```
// Measure
@MeasureExperiment(useCase = ShoppingBasketUseCase.class,
                   experimentId = "baseline-v1",
                   samples = 1000)
@InputSource("basketInstructions")
void measureBaseline(ShoppingBasketUseCase useCase,
                     String instruction,
                     OutcomeCaptor captor)

// Explore (ConfigSource path)
@ExploreExperiment(useCase = ShoppingBasketUseCase.class,
                   samplesPerConfig = 20, ...)
@ConfigSource("modelConfigurations")
@InputSource(file = "fixtures/shopping-instructions.json")
void compareModels(ShoppingBasketUseCase useCase,
                   @Input ShoppingInstructionInput inputData,
                   OutcomeCaptor captor)

// Optimize
@OptimizeExperiment(useCase = ShoppingBasketUseCase.class,
                    controlFactor = "temperature",
                    initialControlFactorSource = "naiveStartingTemperature",
                    scorer = ShoppingBasketSuccessRateScorer.class,
                    mutator = TemperatureMutator.class, ...)
void optimizeTemperature(ShoppingBasketUseCase useCase,
                         @ControlFactor("temperature") Double temperature,
                         OutcomeCaptor captor)
```

`punitexamples` contains a `ShoppingBasketExplore` that uses
`@ConfigSource` + `NamedConfig<ShoppingBasketUseCase>`; no
`@FactorSource` example is currently in the examples repo, though
the mechanism is still exercised by tests inside `punit/`.

---

## 2. Principles for the target DX

These are the non-negotiables the design honours.

1. **Immutable use case.** No setter on a use case is ever called by
   the framework. No reflective patching. A use case, once constructed,
   is used as-is for the samples it serves.
2. **One factory per experiment run.** The framework owns the
   construction closure for the duration of the run. Measure uses a
   zero-arg supplier; explore and optimize use a factor-parameterised
   factory. The closure type is the same across explore and optimize.
3. **Factor is a user-defined type.** Typically a Java record, with
   strongly typed fields. No map-like `FactorValues`, no
   `FactorArguments`, no `@Factor("name")` string keys.
4. **Trial receives the instance as a parameter.** This is already how
   punit works via JUnit parameter injection; preserve it. The trial
   body never calls `provider.getInstance(...)`; that's the plumbing's
   job.
5. **Identity is declared once.** The use case class carries
   `@UseCase(...)` — that's the identity. The experiment annotation
   does not repeat it.
6. **Cross-experiment consistency.** Where the three experiments share
   a concept (experiment id, sample count, time/token budget, warmup
   suppression, expiration), the attribute name is identical. Where
   they differ (how variation is supplied, how results are aggregated),
   the difference is domain-driven.

---

## 3. Answers to the review questions

### Identity and instance lifecycle

**Q1. How is the use case identity communicated to each experiment?**

Two places. The experiment annotation's `useCase = Foo.class` attribute
names the class; the identity string is derived from `@UseCase("…")` on
that class (or the simple class name). The rule is the same across
Measure / Explore / Optimize — all three have a `useCase()` attribute
on the annotation.

*Proposed:* remove `useCase()` from all three annotations. The identity
comes from `@UseCase` on the class whose type appears as the
instance-receiving parameter on the trial. This removes a repetition
and a failure mode (annotation says one class, parameter says another).
An optional `useCase()` override can stay if a use case needs to be
disambiguated (rare), but it should not be the normal path.

**Q2. Who owns the instance during the run?**

The `UseCaseProvider` owns it. The provider is itself owned per test
class (its lifecycle is a JUnit `Extension` registered per test). The
strategy (`Measure`/`Explore`/`Optimize`) sets and clears per-trial
state on the provider (e.g. `setCurrentConfigInstance`,
`setCurrentFactorValues`). "Owns" here means: the provider is what
hands the instance to JUnit parameter resolution; no other code
possesses a reference to the live instance after the trial.

*Proposed:* preserve. The provider-as-owner pattern already matches
the feotest principle of "framework owns instance lifecycle via a
factory." It is the JUnit-idiomatic analogue.

**Q3. Can "framework owns the instance via a factory" be expressed
inside JUnit without fighting it?**

Yes — it already is. `UseCaseProvider.register(Class<T>, Supplier<T>)`
and `registerWithFactors(Class<T>, Function<FactorValues, T>)` are
factory closures in the exact feotest sense, called by the framework
per sample. The JUnit-specific piece is that the provider is the
`ParameterResolver` that delivers the result to the trial. No friction.

*Proposed:* keep. Simplify the registration surface (see §4.5).

### Factors and configurations

**Q4. When do `@FactorSource` and `@ConfigSource` differ?**

Observationally, they converge at the runtime shape: both produce a
fresh (immutable) `T` per sample via a factory held by the provider.
They differ in where the user expresses the mapping from variation to
instance:

- `@FactorSource` — the stream carries stringly-typed tuples
  (`FactorArguments`); the user supplies a mapping function via
  `provider.registerWithFactors(...)` in `@BeforeEach`.
- `@ConfigSource` — the stream carries already-constructed instances
  (`NamedConfig<T>`); no mapping function is needed because the stream
  source did the construction.

The duplication is not justified. `@FactorSource` predates the
immutable use case principle (hence the need for a bridging
`registerWithFactors`); `@ConfigSource` is the post-IUC design.

*Proposed:* collapse to a single mechanism — see §4.3.

**Q5. Could a single mechanism subsume both?**

Yes. The cleanest target is: the source method returns `Stream<F>`
where `F` is a user-defined factor type (typically a record); the
provider is registered with `register(Class<T>, Class<F>, Function<F, T>)`.
The hand-picked-configurations case that `@ConfigSource` serves today
is expressed as a `Stream<F>` where `F` enumerates the variations the
user cares about.

`NamedConfig<T>` survives only if there is genuine value in the
"stream of prebuilt instances, framework does not need a factory"
shortcut. Under the immutable use case principle, a factor record plus
a one-line factory (`f -> f.instance()`) expresses the same thing with
negligible overhead. The design removes `NamedConfig` and
`@ConfigSource` (see §6).

**Q6. What does YAML output look like for a factor today?**

Under `@ConfigSource`, the config name comes from `NamedConfig::name()`;
YAML contains that label alongside the spec. Under `@FactorSource`,
factor names are threaded through `factors = {…}` on the annotation or
`FactorArguments.names(...)` inside the builder, and the values appear
in the spec as string key/value pairs derived from the tuple.

Multi-field factors work today but clumsily: the user enumerates
`NamedConfig` instances, or threads several `@Factor(…) T x` parameters
through the test signature.

*Proposed:* factor is a user-defined record. The YAML field names
and values rendered into produced spec files remain exactly what
they are today — the `@FactorSource` path's existing serialisation
shape is preserved; the record's fields map to the same YAML keys
the current API emits. Factor-combination filenames follow the
existing convention (`model-gpt-4_temperature-0.7`). An explicit
friendly label is attached by annotating a single field with
`@FactorName`; that field's value becomes the combination name,
equivalent to today's `@ConfigSource` / `NamedConfig` shortcut. The
typing improvement is on the API side only; byte-identical output is
a hard constraint (see Constraint above).

### Trial access to the instance

**Q7. How does the test method body reach the configured use case?**

Via a JUnit-injected method parameter whose type is the use case
class. `UseCaseProvider` is the `ParameterResolver` and resolves that
parameter. This is uniform across all three experiments.

*Proposed:* preserve unchanged.

**Q8. Is there a "closure capture" analogue in Java?**

There is no `ThreadLocal` or captured-field workaround in user code
today. The provider holds per-trial state (current factor values,
current config instance), but that is framework-internal. Users do not
need to capture anything. No DX gap here.

### Cross-experiment consistency

**Q9. Where do setter/attribute names differ?**

Shared concepts that are already consistent: `timeBudgetMs`,
`tokenBudget`, `experimentId`, `useCase`.

Inconsistencies:
- `samples` vs `samplesPerConfig` vs `samplesPerIteration` — the
  difference is real (measure counts across the whole run; explore and
  optimize count per-unit), so these names are legitimately distinct.
  Retain.
- `expiresInDays` is on Measure and Explore but not Optimize. On
  examination this is a miscarriage: expiration tracks the validity
  window of a **baseline spec**, and baseline specs are a
  measure-only artefact (they are consumed by `@ProbabilisticTest`
  for threshold derivation). Explore produces exploration specs —
  informational, not consumed by probabilistic tests — and Optimize
  produces an optimization history. Neither needs a validity window.
  `expiresInDays` belongs on Measure alone.
- `skipWarmup` is on Explore and Optimize but not Measure. Retain —
  Measure is the one experiment where warmup ought to be respected.

*Proposed:* no renames. The shared-concept attributes are already
aligned.

**Q10. How much DX transfers from `@MeasureExperiment` to the others?**

Under the proposed target DX:
- Identity resolution: identical across all three (from `@UseCase` on
  the class).
- Instance supply: identical pattern (register a factory on the
  provider in `@BeforeEach`), differing only in arity (zero-arg for
  measure, `F -> T` for explore/optimize).
- Trial signature: identical shape `(T, input, captor)`.
- Output paths: same directory layout rules.

Everything that does not transfer is domain-driven: *what is being
varied* (nothing / a set / an iterative search).

### Validation and precondition handling

**Q11. Where does punit validate experiment setup?**

At test template expansion time (inside the strategy's
`TestTemplateInvocationContextProvider.provideTestTemplateInvocationContexts`
path). Missing `@FactorSource` / `@ConfigSource` on `@ExploreExperiment`
is currently diagnosed late (at invocation time). Annotation-level
errors (missing `controlFactor` on `@OptimizeExperiment`) are
compile-time via the annotation declaration.

*Proposed:* move all runtime validation to a single point early in
the extension's lifecycle (before the first invocation), with messages
that name the missing piece and the setter/annotation to supply it.
Modelled on feotest's `.build()` panics ("`use_case_id` must be set
via `.use_case_id(…)`"). Concretely: a `validateExperimentSetup(...)`
call in the strategy that runs once per experiment and throws a
`ExperimentSetupException` with a structured error code (e.g.
`MISSING_FACTOR_SOURCE`).

**Q12. Is the "forgot to supply factors" case a first-class concern?**

It is not surfaced cleanly today. The failure mode is an empty stream
of invocation contexts or a confusing JUnit parameter resolution
error.

*Proposed:* first-class. Explicit error ("`@ExploreExperiment` requires
`@FactorSource`; declare a static method returning `Stream<F>` and
reference it by name").

### Optimize specifics

**Q13. How does the user supply the initial factor and mutator?**

Today: three paths for the initial factor (inline string attribute,
method reference, `@FactorGetter` reflection fallback); mutator is a
class attribute on the annotation. The string-typed inline initial
value cannot express typed factors; `@FactorGetter` relies on mutable
use case access.

*Proposed:* one path. The initial factor is a typed method reference
returning `F`. The mutator is a class whose generic parameter is
inferred from `F`. `initialControlFactorValue` (inline string) and
`@FactorGetter` (reflective fallback) are removed.

**Q14. Is iteration history exposed in a typed form?**

Partially. `FactorMutator<F>::mutate` already takes a typed current
value and a history object; the cost is the wildcard in
`Class<? extends FactorMutator<?>>` on the annotation, which erases
`F` at the declaration site.

*Proposed:* keep `FactorMutator<F>` as-is. Express `F` on the
annotation via either the return type of the initial-factor method or
a dedicated `factor = Temperature.class` attribute (see §4.4).

### JUnit affordances

**Q15. Which JUnit affordances make Rust-style builders inappropriate,
and which are orthogonal?**

Inappropriate in punit (because JUnit already handles them):
- Parameter injection (JUnit does this; builders would duplicate).
- Test discovery and reporting (JUnit runners + `@TestTemplate`).
- Lifecycle hooks (`@BeforeEach` / `@AfterEach`).

Orthogonal — nothing prevents punit from having a builder surface too:
- Required-field validation patterns (JUnit does nothing here).
- Typed construction of factor / mutator / scorer instances (JUnit
  doesn't help).

*Proposed:* keep the annotation-driven surface. Do not introduce a
builder DSL. The JUnit idiom is annotation + parameter injection;
fighting it costs more than it saves.

**Q16. Metadata-as-annotation vs setter — where is the line?**

Metadata-as-annotation fits when: the value is a literal known at
compile time (sample count, objective enum, time budget), the
attribute is read by the extension before the trial runs, and there
is no per-instance customisation needed.

Setter-style (via `@BeforeEach` on the provider) fits when: the value
involves runtime construction (a factory closure, an LLM client
reference, secrets read from env), or the value is a reference to a
user-defined type the annotation cannot hold.

*Proposed:* maintain the split as today. Sample counts, budgets,
objectives, experiment id, expiration days, warmup suppression stay
on the annotation. Factory closures, mutator/scorer *instances* (if
we ever want injection over `new`-via-`Class`) stay at call time.

---

## 4. Target DX

### 4.1 Shared shape across all three experiments

```java
@UseCase("shopping.basket")
public record ShoppingBasketUseCase(LlmClient llm,
                                    String model,
                                    double temperature) {
    public UseCaseOutcome<BasketResult> process(String instruction) { … }
}
```

Identity derives from `@UseCase("shopping.basket")`. The class is a
record — immutability is structural. All factor values are
constructor arguments.

Trial method signature, uniform across experiments:

```java
void trial(ShoppingBasketUseCase useCase,
           String instruction,
           OutcomeCaptor captor)
```

`@InputSource` continues to supply the `instruction` parameter
(unchanged — this design does not touch inputs).

### 4.2 Measure

```java
@MeasureExperiment(samples = 1000,
                   experimentId = "baseline-v1",
                   expiresInDays = 30)
@InputSource("basketInstructions")
void measureBaseline(ShoppingBasketUseCase useCase,
                     String instruction,
                     OutcomeCaptor captor) {
    captor.record(useCase.process(instruction));
}

@BeforeEach
void setUp(UseCaseProvider provider) {
    provider.register(ShoppingBasketUseCase.class,
        () -> new ShoppingBasketUseCase(llmClient, "gpt-4o", 0.0));
}
```

Changes vs today:
- `useCase = …` attribute removed (derived from the `useCase` parameter
  type).
- `provider.register(Class, Supplier)` is the only registration path
  used by measure.

### 4.3 Explore

```java
public record ModelFactor(String model, double temperature) {}

@ExploreExperiment(samplesPerConfig = 20,
                   experimentId = "model-comparison-v1",
                   skipWarmup = true)
@FactorSource("modelFactors")
@InputSource(file = "fixtures/shopping-instructions.json")
void compareModels(ShoppingBasketUseCase useCase,
                   @Input ShoppingInstructionInput inputData,
                   OutcomeCaptor captor) {
    captor.record(useCase.process(inputData.text()));
}

static Stream<ModelFactor> modelFactors() {
    return Stream.of(
        new ModelFactor("gpt-4o-mini", 0.0),
        new ModelFactor("gpt-4o-mini", 0.7),
        new ModelFactor("gpt-4o", 0.0)
    );
}

@BeforeEach
void setUp(UseCaseProvider provider) {
    provider.register(ShoppingBasketUseCase.class, ModelFactor.class,
        f -> new ShoppingBasketUseCase(llmClient, f.model(), f.temperature()));
}
```

Changes vs today:
- `@FactorSource` now references a method returning `Stream<F>` where
  `F` is any user-defined factor type. No more
  `Stream<FactorArguments>`, `factors = {…}`, or
  `FactorArguments.configurations().names(…)`.
- `@ConfigSource` / `NamedConfig` removed. The hand-picked-factors
  pattern is expressed by enumerating `F` values directly.
- `@Factor("model") String model` parameter injection removed. The
  trial body never needs the factor value — it needs the configured
  instance.
- `provider.registerWithFactors(Class, Function<FactorValues, T>)`
  replaced by `provider.register(Class<T>, Class<F>, Function<F, T>)`.
  Typed.

**Factor-combination naming in reports/specs:** preserves the
existing convention exactly. A record without `@FactorName` on any
field derives the filename from factor fields using the same format
as today's `@FactorSource` path
(`model-gpt-4o-mini_temperature-0.0.yaml`). A record with
`@FactorName` on exactly one field uses that field's value as the
combination name — equivalent to today's `@ConfigSource` /
`NamedConfig` shortcut. Produced files — both their paths and their
YAML content — are byte-identical to the old API's output for the
equivalent experiment; see Constraint.

### 4.4 Optimize

```java
public record Temperature(double value) {}

@OptimizeExperiment(factor = Temperature.class,
                    initialFactor = "startingTemperature",
                    scorer = ShoppingBasketSuccessRateScorer.class,
                    mutator = TemperatureMutator.class,
                    objective = OptimizationObjective.MAXIMIZE,
                    samplesPerIteration = 20,
                    maxIterations = 11,
                    experimentId = "temperature-optimization-v1",
                    skipWarmup = true)
@InputSource("basketInstructions")
void optimizeTemperature(ShoppingBasketUseCase useCase,
                         String instruction,
                         OutcomeCaptor captor) {
    captor.record(useCase.process(instruction));
}

static Temperature startingTemperature() {
    return new Temperature(0.0);
}

@BeforeEach
void setUp(UseCaseProvider provider) {
    provider.register(ShoppingBasketUseCase.class, Temperature.class,
        t -> new ShoppingBasketUseCase(llmClient, "gpt-4o", t.value()));
}
```

Changes vs today:
- `controlFactor = "…"` string attribute removed. The factor type
  replaces it (`factor = Temperature.class`). The name "control" was
  carried over from the mutable-factor-setter era, where exactly one
  factor was singled out for mutation among several setters; under
  IUC there is just "the factor the optimizer varies."
- `initialControlFactorValue` inline-string attribute removed —
  stringly typed and cannot express typed factors.
- `initialControlFactorSource` renamed to `initialFactor`; its
  method must return the `F` declared on `factor = …`.
- `@ControlFactor("…") Double t` parameter injection removed. The
  trial body accesses the varied value via the instance
  (`useCase.temperature()`), or — if truly needed — via an optional
  `@Factor Temperature t` typed injection (see §4.5 on whether this
  survives).
- `@FactorGetter` reflective-fallback removed (finalise the 0.6.0
  deprecation).
- `mutator` attribute type becomes
  `Class<? extends FactorMutator<F>>` with `F` inferred from the
  `factor` attribute — still a wildcard at the Java level, but now
  validated at experiment setup time against the declared `F`.

### 4.5 `UseCaseProvider` — simplified surface

Public methods after the change:

```java
<T> void register(Class<T> useCaseType, Supplier<T> factory);

<T, F> void register(Class<T> useCaseType,
                     Class<F> factorType,
                     Function<F, T> factory);

<T> T getInstance(Class<T> useCaseType);  // existing, unchanged
```

Removed:
- `registerWithFactors(Class<T>, Function<FactorValues, T>)` —
  untyped factor tuple.
- `registerAutoWired(Class<T>, Supplier<T>)` — `@FactorSetter`-based;
  already deprecated.
- `setCurrentConfigInstance(Object)` / `clearCurrentConfigInstance()`
  / `getCurrentInstance(Class<T>)` / `setCurrentFactorValues(...)` —
  these become package-private (called only by the strategies).

Factor injection — should the trial ever receive `F` directly? A
case can be made for an `@Factor Temperature t` parameter, typed.
Proposal: **no**, because (a) the use case already carries the factor
(by IUC, every factor value survives into the instance), so the trial
can read it off the instance; and (b) removing the annotation removes
a second identity surface for the factor. If a real use case emerges
later, `@Factor F t` can be added non-breakingly.

### 4.6 What goes away

| Item | Status | Reason |
|---|---|---|
| `@FactorSource.factors = {…}` | Removed | Factor names come from the record type |
| `FactorArguments` | Removed | Replaced by user-defined record |
| `FactorArguments.ConfigurationBuilder` | Removed | Same |
| `@Factor("name") …` parameter annotation | Removed | Trial body uses the instance |
| `@ConfigSource` | Removed | Subsumed by `@FactorSource` + record |
| `NamedConfig<T>` | Removed | Subsumed by user-defined record |
| `@FactorSetter` | Removed (was deprecated) | Mutable pattern; IUC violation |
| `@FactorGetter` | Removed (was deprecated) | Reflective read of mutable state |
| `FactorValues` type | Removed | Replaced by typed `F` |
| `@ControlFactor` parameter annotation | Removed | Trial reads factor off instance |
| `OptimizeExperiment.controlFactor` (String) | Removed | Replaced by `factor = F.class` |
| `OptimizeExperiment.initialControlFactorValue` | Removed | String-typed; can't express typed factors |
| `OptimizeExperiment.initialControlFactorSource` | Renamed | → `initialFactor` |
| `UseCaseProvider.registerWithFactors` | Removed | Replaced by typed overload |
| `UseCaseProvider.registerAutoWired` | Removed (was deprecated) | `@FactorSetter`-based |
| `Experiment.useCase()` attribute | Removed (all three) | Derived from trial parameter type |
| `ExploreExperiment.expiresInDays` | Removed | Expiration applies to baseline specs only; explore produces exploration specs, not baselines |

### 4.7 Reference matrix

| Concept | Measure | Explore | Optimize |
|---|---|---|---|
| Identity | `@UseCase` on class | `@UseCase` on class | `@UseCase` on class |
| Factor | *(none)* | `Stream<F>` via `@FactorSource` | `F`-typed initial + mutator |
| Instance supply | `provider.register(T, Supplier<T>)` | `provider.register(T, F, Function<F,T>)` | `provider.register(T, F, Function<F,T>)` |
| Trial | `(T, input, captor)` | `(T, input, captor)` | `(T, input, captor)` |
| Per-unit samples | `samples` | `samplesPerConfig` | `samplesPerIteration` |
| Experiment id | `experimentId` | `experimentId` | `experimentId` |
| Time budget | `timeBudgetMs` | `timeBudgetMs` | `timeBudgetMs` |
| Token budget | `tokenBudget` | `tokenBudget` | `tokenBudget` |
| Warmup suppression | *(warmup respected)* | `skipWarmup` | `skipWarmup` |
| Expiration | `expiresInDays` | *(no baseline spec)* | *(no baseline spec)* |

The rows that differ do so for domain reasons; the rows that match
match by name and semantics.

---

## 5. Migration plan

### 5.1 Change classification

- **Rename (mechanical, breaking):** `initialControlFactorSource` →
  `initialFactor`.
- **Type change (breaking):** `Class<? extends FactorMutator<?>>`
  wildcard validated against declared `F`.
- **Structural (breaking, no shim):** `@FactorSource` returns
  `Stream<F>` not `Stream<FactorArguments>`. `UseCaseProvider.register`
  gains a typed three-arg overload; `registerWithFactors` disappears.
- **Removal (breaking, already deprecated):** `@FactorSetter`,
  `@FactorGetter`, `registerAutoWired`.
- **Removal (breaking, not previously deprecated):** `@ConfigSource`,
  `NamedConfig`, `@Factor` parameter, `@ControlFactor` parameter,
  `FactorArguments`, `FactorValues`, `Experiment.useCase()` attribute,
  `controlFactor` string, `initialControlFactorValue` inline,
  `ExploreExperiment.expiresInDays`.

`ExploreExperiment.expiresInDays` is removed unconditionally. If the
attribute currently emits expiration metadata into exploration YAML,
that emission is removed too — exploration specs are informational
and are not consumed as baselines, so their expiration data was
never load-bearing. This is the one sanctioned deviation from
byte-identical output.

No feature flag; no compatibility shims. The changes land together
under a single major-bump-era release (0.x → 0.y). The rationale: the
legacy paths entangle with each other (`@FactorSource` relies on
`registerWithFactors`, which produces `FactorValues`, which is
consumed by `@Factor` parameters), so partial removal leaves the DX
worse than before. One breaking release is the lowest-cost path for
users.

### 5.2 Known callers

- `punit/src/test/java/**` — tests of the framework itself. Several
  test subjects exercise `@FactorSource` / `@Factor` / `@ControlFactor`
  directly; these rewrite to the new surface in the same commit as
  the API change. In scope for this PR.
- `punitexamples` — three experiment classes identified
  (`ShoppingBasketMeasure`, `ShoppingBasketExplore`,
  `ShoppingBasketOptimizeTemperature`). **Out of scope for this PR.**
  Migrated in a separate iteration after this refactor ships.
  Sketches in §5.3 are for that follow-up.
- External users: unknown. The release notes must flag this as a
  breaking release and link to a migration section in the user guide.

### 5.3 Per-example migration sketch (for follow-up punitexamples PR)

`ShoppingBasketMeasure.measureBaseline` — trivial:
- drop `useCase = ShoppingBasketUseCase.class` from the annotation.
- no other change.

`ShoppingBasketExplore.compareModels` — structural:
- define `record ModelFactor(String model, double temperature)`.
- rename `modelConfigurations()` to `modelFactors()` and rewrite it
  to return `Stream<ModelFactor>`.
- replace `@ConfigSource("modelConfigurations")` with
  `@FactorSource("modelFactors")`.
- in `@BeforeEach`, replace the inline `NamedConfig.of(...)` pattern
  with `provider.register(ShoppingBasketUseCase.class,
  ModelFactor.class, f -> new ShoppingBasketUseCase(llm, f.model(),
  f.temperature()))`.

`ShoppingBasketOptimizeTemperature.optimizeTemperature` — structural:
- define `record Temperature(double value)`.
- replace `controlFactor = "temperature"` with
  `factor = Temperature.class`.
- rename `initialControlFactorSource = "naiveStartingTemperature"` to
  `initialFactor = "naiveStartingTemperature"`; change the method's
  return type from `Double` to `Temperature`.
- drop `@ControlFactor("temperature") Double temperature` from the
  trial signature.
- update `TemperatureMutator implements FactorMutator<Double>` to
  `FactorMutator<Temperature>`.

### 5.4 Sequencing

The refactor lands as a single change on branch
`refactor/experiment-dx`, committed and opened as one PR against
`main`. There is no alongside period, no deprecation-phased rollout,
and no compatibility shim. The framework is pre-1.0; users have been
warned by `@Deprecated` markers on the most load-bearing removals
since 0.6.0.

1. **Capture an output corpus** on the current API — YAML specs for
   at least one measure, one explore via `@FactorSource`, one explore
   via `@ConfigSource`, and one optimize, plus the corresponding
   verdict XML. This corpus is the byte-identical reference for the
   rest of the work (see Constraint). Exploration-spec expiration
   metadata, if present, is expected to change under the new API —
   that is the one sanctioned deviation (§5.1).
2. **Implement the refactor in place.** Add the new API surface,
   rewire the strategies, delete the removed types, rename/drop
   annotation attributes. Port `punit`'s own tests to the new API in
   the same commit (or a small adjacent commit on the same branch).
3. **Diff output** against the corpus from step 1. Every artefact
   must match modulo the `expiresInDays` carve-out. Any other diff
   is a bug.
4. **Update docs.** `docs/USER-GUIDE.md` experiment sections rewritten
   to the new API. `CHANGELOG.md` entry explicitly notes that produced
   artefacts are unchanged (except exploration spec expiration
   metadata) and that baselines recorded under the old API remain
   valid.
5. **Open PR.** Body summarises the break, links to
   `plan/DES-EXPERIMENT-DX.md`, and flags the follow-up work
   (`punitexamples` migration is out of scope for this PR and happens
   in a separate iteration).

No shim library, no `@Deprecated` period for the already-deprecated
items (they are already `@Deprecated(since = "0.6.0")`). For items
newly removed (`@ConfigSource`, `NamedConfig`, etc.), the release
itself is the break; they are not long-for-the-codebase items that
merit a two-release phase-out given the small known call site.

---

## Appendix: feotest reference shape

Drawn from `feotest/src/experiment/{measure,explore,optimize}.rs` and
`feotest/docs/USER-GUIDE.md`. Used here only as a reference for what a
coherent same-family design looks like; not a target.

| Concept | measure | explore | optimize |
|---|---|---|---|
| Entry | `::builder()` | `::builder()` | `::builder()` |
| Terminal | `.build().run()` | `.build().run()` | `.build().run()` |
| Id | `.use_case_id(str)` | `.use_case_id(str)` | `.use_case_id(str)` |
| Factor | *(none)* | `.factors(Vec<F>)` | `.initial_factor(F)` + `.mutator(impl FactorMutator<F>)` |
| Instance | `.use_case(Fn() -> T)` | `.use_case(Fn(&F) -> T)` | `.use_case(Fn(&F) -> T)` |
| Trial | `.trial(Fn(&T, &str) -> Outcome)` | `.trial(Fn(&T, &str) -> Outcome)` | `.trial(Fn(&T, &str) -> Outcome)` |
| Samples | `.samples(n)` | `.samples_per_config(n)` | `.samples_per_iteration(n)` |

Factor types in feotest require `Clone + Display` (explore) or
`Clone + Serialize + Display` (optimize); `Display` yields the config
name in reports, `Serialize` yields the YAML. The punit analogue is a
Java record — Jackson/SnakeYAML handles serialisation reflectively,
and config naming is derived from field values (with a `name` field
override).

The Java-specific differences that shape the punit target DX:
- Annotations replace builder setters for compile-time-known metadata
  (samples, budgets, objectives, experiment id).
- `UseCaseProvider` registration in `@BeforeEach` replaces
  `.use_case(…)` and `.trial(…)` builder calls — the factory closure
  is passed to the provider rather than to a builder.
- Parameter injection replaces the trial closure's `(&T, &str)` pair
  with JUnit-resolved method parameters.
