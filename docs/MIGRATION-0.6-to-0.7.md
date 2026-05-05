# Migrating from punit 0.6.x to 0.7.0

punit 0.7.0 replaces the annotation-driven authoring style of
0.6.x with a typed, builder-based one. The headline change:
configuration that used to live in annotation *attributes* moves
into builder calls inside the test method body, and a use case is
now an implementation of a typed `UseCase<FT, I, O>` interface
rather than an `@UseCase`-annotated class.

This guide is in three parts:

1. **A coding-assistant prompt** — paste it into Claude Code,
   Cursor, ChatGPT-with-tools, or any agent that can read and
   edit local files, and it will walk your codebase and apply the
   migration.
2. **Per-change discussion** — what changed, why, and what the
   new shape looks like. Read this if you want to understand the
   migration before letting an assistant run it, or if your
   codebase has patterns the assistant flags for human review.
3. **FAQ / edge cases** — short answers to questions that
   typically come up.

---

## 1. The coding-assistant prompt — "the fast path"

Paste the block below to your coding assistant. The prompt is
self-contained: it carries the rule set, one full worked
example, search instructions, and a verification step.

````
You are migrating a Java codebase from org.javai:punit 0.6.x to
org.javai:punit 0.7.0. Operate only on files under the user's
working directory; do not touch the punit framework itself.

# What changed

In 0.7.0, configuration that used to live in annotation
attributes lives in a typed builder invoked from the method body.
The `@ProbabilisticTest` and `@Experiment` annotations survive
but as bare markers with no attributes. Use cases are now typed
interfaces, not annotated classes.

# Rules to apply

1. `@ProbabilisticTest(...)` with attributes → `@ProbabilisticTest`
   (no attributes); the body invokes
   `org.javai.punit.runtime.PUnit.testing(...)`, each removed
   attribute becomes a builder call on the returned builder, and
   the chain ends with `.assertPasses()`. The first argument to
   `PUnit.testing(...)` is the use case's `sampling(...)` factory
   call; if the use case is parameterised by a factor record, a
   factor record instance is the second argument.

2. `@MeasureExperiment(...)`, `@ExploreExperiment(...)`,
   `@OptimizeExperiment(...)` → `@Experiment` (no attributes);
   each removed attribute becomes a builder call on
   `PUnit.measuring(...)`, `PUnit.exploring(...)`, or
   `PUnit.optimizing(...)` respectively, and the body ends with
   `.run()`.

3. `@UseCase(...)`-annotated class → class
   `implements UseCase<FT, I, O>`. The annotation attributes
   (`description`, `warmup`, `covariates`, `covariateDayOfWeek`,
   `covariateTimeOfDay`) become method overrides on the
   interface (`description()`, `warmup()`, `covariates()`, …).
   The factor record `FT` is the configuration the use case is
   instantiated with — declare it as a Java `record` and pass an
   instance at construction.

4. `@Factor`, `@FactorSource`, `@Input`, `@InputSource`,
   `@FactorGetter`, `@FactorSetter` parameter annotations →
   factor records and `Sampling`. The use case takes a factor
   record at construction; the test supplies inputs via
   `Sampling.from(input)` or `Sampling.matching(supplier, matcher)`.

5. `@Covariate(...)`, `@CovariateSource(...)` →
   `covariates()` returning typed covariate instances from
   `org.javai.punit.api.covariate` (`DayOfWeekCovariate`,
   `RegionCovariate`, `TimeOfDayCovariate`, `TimezoneCovariate`,
   `Covariate.custom(key, category)` for project-defined
   covariates). Custom covariates resolve their values via
   `customCovariateResolvers()`.

6. `@Sentinel` → delete. The sentinel module scans typed use
   cases automatically; no marker is required.

7. Imports moved from `org.javai.punit.contract.*` to
   `org.javai.punit.api.*`:
     - `org.javai.punit.contract.UseCaseOutcome`        → `org.javai.punit.api.UseCaseOutcome`
     - `org.javai.punit.contract.Postcondition`         → `org.javai.punit.api.Postcondition`
     - `org.javai.punit.contract.PostconditionCheck`    → `org.javai.punit.api.PostconditionCheck`
     - `org.javai.punit.contract.PostconditionEvaluator`→ `org.javai.punit.api.PostconditionEvaluator`
     - `org.javai.punit.contract.PostconditionResult`   → `org.javai.punit.api.PostconditionResult`
     - `org.javai.punit.contract.ServiceContract`       → `org.javai.punit.api.Contract`

# Worked example

The pre-0.7 form of a probabilistic test:

```java
@ProbabilisticTest(
    samples = 100,
    minPassRate = 0.95,
    intent = TestIntent.SMOKE,
    thresholdOrigin = ThresholdOrigin.SLA,
    contractRef = "API SLA §3.1"
)
void apiMeetsSla() {
    String result = apiClient.call(new Request(...));
    assertThat(result).isNotNull();
    assertThat(result.statusCode()).isEqualTo(200);
}
```

The equivalent 0.7 form:

```java
@ProbabilisticTest
void apiMeetsSla() {
    PUnit.testing(MyApiUseCase.sampling(REQUESTS, 100))
        .criterion(PassRate.meeting(0.95, ThresholdOrigin.SLA))
        .intent(TestIntent.SMOKE)
        .contractRef("API SLA §3.1")
        .assertPasses();
}
```

Each annotation attribute moves to a builder method:
`samples` becomes the second argument to the use case's
`sampling(...)` factory; `minPassRate` and `thresholdOrigin`
become a `PassRate.meeting(...)` criterion; `intent` and
`contractRef` are direct builder calls; the assertions inside
the body are subsumed by the use case's contract — its
`postconditions(ContractBuilder<O>)` method declares the per-
sample check, and the framework counts pass / fail across
samples.

# Search instructions

Identify candidate files with these greps (run from the user's
project root):

- `grep -rn '@ProbabilisticTest(' src` — every parameterised
  probabilistic test.
- `grep -rn '@MeasureExperiment\|@ExploreExperiment\|@OptimizeExperiment' src`
  — every parameterised experiment.
- `grep -rn '@UseCase' src` — every annotated use case class.
- `grep -rn '@CovariateSource\|@Covariate(' src` — every
  covariate declaration.
- `grep -rn '@Factor\|@FactorSource\|@FactorGetter\|@FactorSetter\|@Input\|@InputSource' src`
  — every factor / input parameter annotation.
- `grep -rn 'org.javai.punit.contract' src` — every import to
  relocate.

# What to flag, not auto-migrate

Surface the following for human review rather than guessing:

- A `@ProbabilisticTest` method whose 0.6.x form has no
  `useCase = …` attribute and performs the service call inline
  in the method body. Migration to 0.7 requires extracting a
  new `UseCase<FT, I, O>` implementation — that's a larger
  structural change than a method-body rearrangement, and the
  shape of the extracted use case (factor record fields,
  postcondition wording, input/output types) is a design
  decision the human should make. Do not invent a use case
  silently.
- A `@UseCase`-annotated class whose constructor takes anything
  other than a factor record's worth of fields (e.g. injected
  collaborators that should remain constructor parameters
  alongside the factor record).
- A custom covariate with a non-trivial resolution (e.g. reads
  from external state, depends on the input rather than the
  factor record). The shape of `customCovariateResolvers()` is
  `Map<String, Supplier<String>>` — anything that doesn't fit a
  simple `Supplier` needs human review.
- A test method that mixes `@ProbabilisticTest` with non-punit
  JUnit features in ways that the rule set above doesn't cover
  (e.g. parameterised JUnit `@MethodSource` driving the punit
  test).

# Verification step

After edits:

1. Run `./gradlew compileJava compileTestJava` (or the project's
   equivalent). Resolve any remaining errors.
2. Run the project's test suite.
3. Report files changed, files flagged for human review, and any
   unresolved patterns.
````

---

## 2. Per-change discussion

### 2.1 Parameterised annotation → unparameterised annotation + builder

This is the dominant change. Every test- or experiment-shaping
attribute moves from the annotation onto a builder call. The
annotation itself is reduced to a marker.

#### Before

```java
@ProbabilisticTest(
    samples = 100,
    minPassRate = 0.95
)
void apiMeetsReliabilityTarget() {
    String result = llmClient.translate("Add 2 apples");
    assertThat(result).matches(JSON_PATTERN);
}
```

```java
@MeasureExperiment(
    useCase = ShoppingBasketUseCase.class,
    experimentId = "baseline-v1"
)
@InputSource("basketInstructions")
void measureBaseline(
    ShoppingBasketUseCase useCase,
    String instruction,
    OutcomeCaptor captor
) {
    captor.record(useCase.translateInstruction(instruction));
}
```

#### After

```java
@ProbabilisticTest
void apiMeetsReliabilityTarget() {
    PUnit.testing(MyUseCase.sampling(INSTRUCTIONS, 100))
        .criterion(PassRate.meeting(0.95))
        .assertPasses();
}
```

```java
@Experiment
void measureBaseline() {
    PUnit.measuring(ShoppingBasketUseCase.sampling(BASKET_INSTRUCTIONS, 1000))
        .experimentId("baseline-v1")
        .run();
}
```

#### Why

Annotation attributes are *static metadata*. The configuration
of a probabilistic test or experiment is *operational logic* — it
references types (the use case class), depends on other values
(criteria derived from baselines), and benefits from the
compiler's type discipline (an integer where a boolean was
expected, or a wrong threshold-origin enum, becomes a compile
error rather than a misconfiguration). Carrying that logic in
annotation attributes was always a workaround for "Java doesn't
let me invoke a builder from an annotation". 0.7 puts it where
it belongs: in the method body, where it can be type-checked,
refactored, debugged, and composed.

A side effect of the move: the assertions that lived in the
0.6.x test body (`assertThat(...)`) disappear into the use
case's `postconditions(ContractBuilder<O>)` method. The
probabilistic test no longer makes per-sample assertions; it
runs the configured sampling and lets the framework count
contract pass / fail outcomes across samples. The
`.assertPasses()` terminal asserts the *aggregate* verdict.

### 2.2 Use cases

In 0.6.x a use case was a class annotated with `@UseCase`,
configured by attributes on the annotation, by `final` fields
populated at construction, and by `@CovariateSource` /
`@FactorGetter` / `@FactorSetter` accessors that the framework
read by reflection. Factor variation across an experiment was
expressed by annotating the test method's parameters with
`@Factor`, `@FactorSource`, `@Input`, and friends, and the
framework wired the values into the use case via reflection.

In 0.7 a use case is an implementation of the typed interface
`UseCase<FT, I, O>` (extending `Contract<I, O>`). The shape:

- **`FT` is the factor record type.** A Java `record` carrying the
  configuration the use case is sensitive to — model name,
  temperature, retry count, prompt template, anything that varies
  across an experiment. The use case takes the factor record at
  construction time and stores it in a `final` field. Different
  factor values mean different `UseCase` instances; the use case
  itself is immutable. A factor record allows us to supply a
  use case's construction using a single object, thereby making
  construction uniform for all use cases no matter how many
  factors the use case has.
- **`I` is the per-sample input type.** What changes between samples
  within a single configuration — the customer query, the JSON
  payload, the instruction string. Tests supply inputs via
  `Sampling.from(input)` for a fixed input or
  `Sampling.matching(supplier, matcher)` for input-output
  pairs.
- **`O` is the use case's raw output type.** What the service
  returns. Postconditions in the contract operate on `O`.

The framework no longer reads anything by reflection. Every
piece of metadata the framework used to harvest from annotations
or accessor methods now travels via a method on `UseCase` — `id()`,
`description()`, `warmup()`, `pacing()`, `covariates()`,
`customCovariateResolvers()`, `postconditions(ContractBuilder<O>)`,
`invoke(I, TokenTracker)` — each with a default the author
overrides only when the default doesn't fit. This is more lines
of code than a single `@UseCase(...)` annotation, but every
override is type-checked, statically discoverable, and has its
javadoc one click away in any IDE.

The factor / factor-source / factor-getter / factor-setter
annotation surface from 0.6.x is therefore subsumed into the
factor record `FT` and the use case's constructor. Code generation
and reflection both retire. Great news for type safety fans!

`@Sentinel` is also gone — the sentinel module discovers
sentinel-eligible use cases by scanning for typed
`UseCase` implementations and `@ProbabilisticTest` / `@Experiment`
methods. The marker annotation existed to guide the v0.6.x
reflection scanner; it has nothing to do once the scanner is
reading types.

### 2.3 Covariates

Covariates flag *which axes of the running environment matter
for baseline matching* — was the test run on a weekend, in
European business hours, in `eu-west-1`, with `gpt-4o-mini`,
under `temperature = 0.3`. Two runs whose covariates align can
share a baseline; two runs whose CONFIGURATION covariates differ
must not, regardless of whether the engine could compute a
result.

In 0.6.x covariates were declared via the `@UseCase` annotation
and a small zoo of supporting annotations:

```java
@UseCase(
    warmup = 3,
    covariateDayOfWeek = {@DayGroup({SATURDAY, SUNDAY})},
    covariateTimeOfDay = {"08:00/4h", "16:00/4h"},
    covariates = {
        @Covariate(key = "llm_model", category = CovariateCategory.CONFIGURATION),
        @Covariate(key = "temperature", category = CovariateCategory.CONFIGURATION)
    }
)
public class ShoppingBasketUseCase {
    @CovariateSource("llm_model")
    public String getModel() { return model; }

    @CovariateSource
    public double getTemperature() { return temperature; }
    ...
}
```

In 0.7 covariates are values from the sealed hierarchy under
`org.javai.punit.api.covariate` (`DayOfWeekCovariate`,
`RegionCovariate`, `TimeOfDayCovariate`, `TimezoneCovariate`)
plus `Covariate.custom(key, category)` for project-defined
covariates whose values come from the use case's own state.
Declarations live as a method override on the typed
`UseCase`, and resolution lives on a sibling override:

```java
public final class ShoppingBasketUseCase
        implements UseCase<LlmTuning, String, String> {

    @Override
    public List<Covariate> covariates() {
        return List.of(
                Covariate.custom("llm_model", CovariateCategory.CONFIGURATION),
                Covariate.custom("temperature", CovariateCategory.CONFIGURATION));
    }

    @Override
    public Map<String, Supplier<String>> customCovariateResolvers() {
        return Map.of(
                "llm_model", tuning::model,
                "temperature", () -> Double.toString(tuning.temperature()));
    }
    ...
}
```

The `@DayGroup`, `@RegionGroup`, `@CovariateSource`,
`@CovariateCategory` machinery from 0.6.x is replaced by typed
covariate instances declared on the `UseCase`. Built-in
covariates (day-of-week, time-of-day, region, timezone) carry
their own resolution logic; only `Covariate.custom(...)`
covariates need an entry in `customCovariateResolvers()`.

### 2.4 Imports moved from `contract/` to `api/`

The `org.javai.punit.contract` package is gone. The types that
lived there moved to `org.javai.punit.api`:

| 0.6.x import                                                     | 0.7.0 import                                                            |
|------------------------------------------------------------------|-------------------------------------------------------------------------|
| `org.javai.punit.contract.UseCaseOutcome`                        | `org.javai.punit.api.UseCaseOutcome`                                    |
| `org.javai.punit.contract.Postcondition`                         | `org.javai.punit.api.Postcondition`                                     |
| `org.javai.punit.contract.PostconditionCheck`                    | `org.javai.punit.api.PostconditionCheck`                                |
| `org.javai.punit.contract.PostconditionEvaluator`                | `org.javai.punit.api.PostconditionEvaluator`                            |
| `org.javai.punit.contract.PostconditionResult`                   | `org.javai.punit.api.PostconditionResult`                               |
| `org.javai.punit.contract.ServiceContract`                       | `org.javai.punit.api.Contract` (renamed)                                |
| `org.javai.punit.contract.match.*`                               | subsumed by `org.javai.punit.api.{Sampling, ValueMatcher, MatchResult}` |
| `org.javai.punit.contract.Derivation`                            | subsumed by `Contract` + `ContractBuilder`                              |
| `org.javai.punit.contract.AssertionScope`                        | subsumed by `Contract`                                                  |
| `org.javai.punit.contract.DurationConstraint` / `DurationResult` | subsumed by `LatencySpec` / `LatencyResult`                             |

Most usages need only an import update. `ServiceContract`
authors will additionally find that the new `Contract<I, O>` is
typed and that postconditions are declared in
`postconditions(ContractBuilder<O>)` rather than collected from a
separately-defined contract object — typically the migration is
to fold the contract into the `UseCase` rather than to keep
`Contract` as a separate type.

---

## 3. FAQ / edge cases

**My `@ProbabilisticTest`-annotated method body had `assertThat`
calls. Where do those go?**

Into the use case's `postconditions(ContractBuilder<O>)` method.
The 0.6.x style ran the body once per sample and counted
JUnit-style assertion failures; the 0.7 style runs the use
case's `invoke` method once per sample and lets the contract's
named postcondition clauses produce structured pass / fail
outcomes. The framework aggregates those across samples — the
test method body should contain only the builder chain ending
with `.assertPasses()`.

**My experiment used `@InputSource("methodName")` to pull
inputs from a `Stream<String>` factory method. What replaces it?**

The use case's static `sampling(...)` factory takes the input
collection directly, and the engine cycles through it across
samples. If your inputs are large or expensive, return them from
a method that builds the collection once and pass that to
`sampling(...)`. The 0.6.x parameter-injection model — where the
framework called your stream factory and bound elements into the
test method — has retired.

**What happened to `@Sentinel`?**

Deleted. The `punit-sentinel` module scans for typed `UseCase`
implementations on the classpath and runs sentinel-eligible
methods automatically; no marker is needed. See
`docs/SENTINEL-DEPLOYMENT-GUIDE.md` for the current mechanism.

**What happened to `@Latency(p95Ms = …)` on `@ProbabilisticTest`?**

The `latency` attribute is gone. Latency assertions move to a
separate `LatencySpec`-shaped criterion on the typed builder
(typically `.criterion(PercentileLatency.…)` alongside the
pass-rate criterion). See the USER-GUIDE's "Latency" section for
the current shape.

**What happened to `@UseCase(warmup = 3)`?**

The `warmup` attribute is now an override on `UseCase.warmup()`,
returning an `int`. The default is 0; override only if the use
case needs warmup invocations discarded (typical for
connection-pool fill, JIT, cold caches).

**Will my 0.6.x baseline files keep working?**

Baseline file naming changed under the covariate-aware best-match
selection introduced in 0.7. Existing baseline files are not
deleted on upgrade and won't crash a test run, but a covariate
mismatch will be reported as INCONCLUSIVE rather than silently
selected. The recommended flow is to re-run measure experiments
on 0.7 to regenerate baselines under the new naming; the cost is
a measure run, not a code change.

**Is there a deprecation cycle? Can I run 0.6.x and 0.7 side by
side?**

No. punit is pre-1.0 and the breaking changes ship in one minor
release. There is no 0.6.x shim that accepts the old API. Either
your codebase compiles against 0.7 or it stays on 0.6.x.

**Do my Maven coordinates change?**

No. Same `org.javai:punit-core`, `punit-junit5`, `punit-sentinel`,
`punit-report` artefacts at the same group. Bump the version
property and rebuild.
