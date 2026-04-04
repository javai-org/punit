# PUnit Core — Experiment Design (DoE-light)

## 1. Purpose

Introduce a **lightweight, deterministic-first experiment design capability** into PUnit.

This enables users to:

- Define **factors** and **levels**
- Generate **structured configuration sets**
- Execute experiments with **statistical discipline**
- Maintain **reproducibility and auditability**

PUnit must **minimise avoidable randomness**. Where randomness is used, it must be **explicit, seeded, and reproducible**.

> **[REVIEW]** PUnit already has a rich factor system: `@Factor`, `@FactorSource`, `FactorArguments`, `FactorValues`, and `FactorSuit`. Today, users manually construct treatment combinations via `@FactorSource` returning `Stream<FactorArguments>`. Use cases are immutable: factors are set at construction time via `registerWithFactors`, not mutated during sampling (see [DES-IMMUTABLE-USECASE](DES-IMMUTABLE-USECASE.md)). This document proposes automating treatment combination construction via design strategies. The purpose section should explicitly frame this as **extending the existing factor infrastructure** with automated design generation, not introducing factors from scratch. What is the boundary between what already exists and what this proposal adds?
>
> Note: `@FactorSetter`, `@FactorGetter`, and `registerAutoWired` are deprecated as of 0.5.3 and scheduled for removal. DoE integration must use the immutable use case pattern exclusively — design matrices produce `FactorArguments` streams consumed by `registerWithFactors` factories, not setter-based injection.

---

## 2. Design Principles

### 2.1 Determinism First

- Configuration selection should be deterministic wherever feasible
- Observed variability should originate from the **system under test**, not the framework

---

### 2.2 Explicit Approximation

- Reduced designs must clearly communicate that they are approximations
- No strategy should silently omit parts of the factor space

---

### 2.3 Reproducibility is Mandatory

- All designs must be reproducible
- Any stochastic selection requires a **mandatory seed**

---

### 2.4 Statistical Integrity

- Must align with PUnit assumptions:
    - independence (as far as operationally achievable)
    - stationarity (explicitly surfaced)

> **[REVIEW]** These principles are sound and align with PUnit's philosophy. Two additions worth considering:
>
> 1. **Composability** — designs should compose with existing `@InputSource` (which distributes test data across samples) and the covariate system. State whether designs interact with or are orthogonal to these mechanisms.
> 2. **Immutability** — design-generated configurations must flow through `registerWithFactors`, producing immutable use case instances. Each configuration in the design matrix results in a fresh, fully-configured use case. This aligns with the i.i.d. assumption and the immutable use case principle (see [DES-IMMUTABLE-USECASE](DES-IMMUTABLE-USECASE.md)).

---

## 3. Core Concepts

### 3.1 Factor

A deliberately varied input variable.

```java
Factor<Double> temperature = Factor.of(
    "temperature",
    List.of(0.0, 0.7, 1.0)
);

Factor<String> promptStyle = Factor.of(
    "promptStyle",
    List.of("concise", "verbose")
);
```

> **[REVIEW — Nomenclature mismatch]** PUnit already defines factors via annotations, not a builder API:
>
> ```java
> @FactorSource("modelConfigs")
> static Stream<FactorArguments> modelConfigs() {
>     return FactorArguments.configurations()
>         .names("temperature", "promptStyle")
>         .values(0.0, "concise")
>         .values(0.7, "verbose")
>         .stream();
> }
> ```
>
> The proposed `Factor.of()` typed builder is a new programmatic API that would **generate** `FactorArguments` combinations rather than requiring manual enumeration. This is a valid addition, but the document needs to clarify:
>
> 1. Does `Factor<T>` replace `@Factor`/`@FactorSource` or complement them? (Presumably complement — producing `Stream<FactorArguments>` for consumption by `@FactorSource` and `registerWithFactors`.)
> 2. Where does `Factor<T>` live? In `punit-core` alongside `FactorArguments`?
> 3. The type parameter `<T>` is new — PUnit's current `FactorValues` uses `getString()`/`getDouble()` untyped getters. How does type safety flow through to `FactorSuit` and `FactorValues`?

### 3.2 Design Matrix

A collection of configurations derived from factors.

```java
DesignMatrix design = Design.fullFactorial(
    temperature,
    promptStyle
);
```

Each configuration is a mapping:
```txt
{ temperature=0.7, promptStyle="concise" }
```

> **[REVIEW — Integration point]** A `DesignMatrix` is effectively a generator of `Stream<FactorArguments>`. The document should make this explicit: `DesignMatrix` produces the same `FactorArguments` stream that `@FactorSource` consumes today. For example:
>
> ```java
> @FactorSource("design")
> static Stream<FactorArguments> design() {
>     return Design.fullFactorial(temperature, promptStyle).toFactorArguments();
> }
> ```
>
> This keeps the existing `@ExploreExperiment` + `@FactorSource` mechanism intact and positions design strategies as a convenience layer for generating treatment combinations. State this integration pattern explicitly.

### 3.3 Design Strategies

Supported Strategies (Initial)
FULL_FACTORIAL
- All combinations
- Maximum coverage
- May explode combinatorially

```java
Design.fullFactorial(factors...)
```

ONE_FACTOR_AT_A_TIME (OFAT)
- Vary one factor, others fixed
- Simple exploratory design

```java
Design.oneFactorAtATime(factors...)
```

> **[REVIEW]** OFAT requires a **baseline configuration** (the fixed values for non-varied factors). The document doesn't address how this baseline is specified. Is it the first level of each factor? A user-supplied default? This is essential for OFAT to be well-defined.

FRACTIONAL_FACTORIAL (limited)
- Reduced design with controlled structure
- Only basic support (e.g. 2-level factors initially)

```java
Design.fractionalFactorial(factors...)
```

> **[REVIEW]** Fractional factorial designs require choosing a **resolution level** (e.g., Resolution III, IV, V) that determines which interactions are aliased. "Basic support for 2-level factors" is a reasonable start, but the document should:
>
> 1. State which resolution is used (Resolution III is typical for screening)
> 2. Clarify whether confounding/aliasing is reported to the user
> 3. Consider whether this complexity is warranted for the initial release, or whether FULL_FACTORIAL + SEEDED_RANDOM_SUBSET covers the practical need

SEEDED_RANDOM_SUBSET
A reproducible reduced design generated via pseudo-random selection.
- Seed is mandatory
- Must be recorded and reported
- Used as a pragmatic approximation strategy

```java
Design.seededRandomSubset(
    factors,
    subsetSize = 20,
    seed = 42L
);
```

Important:
- Not guaranteed to be balanced
- Must warn if coverage is poor

> **[REVIEW]** "Coverage" needs a definition. Is it factor-level coverage (every level appears at least once), pairwise coverage (every pair of levels co-occurs), or something else? The guardrail "warn if coverage is poor" is unimplementable without this definition.

## 4. Execution Integration

### 4.1 Execution Profile

Defines how samples are executed.

```java
ExecutionProfile profile = ExecutionProfile.builder()
    .mode(SEQUENTIAL_PACED)
    .pacing(Duration.ofMillis(500))
    .randomisedOrder(true)
    .build();
```

> **[REVIEW — Overlap with existing API]** PUnit already has `PacingConfiguration` (with `maxRequestsPerSecond`, `maxConcurrentRequests`, `minMsPerSample`, etc.) and `@UseCase(maxConcurrent=)`. The proposed `ExecutionProfile` overlaps significantly. Key questions:
>
> 1. Is `ExecutionProfile` intended to **replace** `PacingConfiguration`, **wrap** it, or be a separate concept?
> 2. `randomisedOrder` — PUnit currently executes configurations in the order `@FactorSource` provides them. Randomising execution order is a valid idea for reducing temporal confounding, but introduces the same seed/reproducibility requirement from §2.3. How does this interact with the covariate system (which captures temporal context)?
> 3. `SEQUENTIAL_PACED` as a mode — PUnit already distinguishes sequential vs concurrent execution. Adding a separate "mode" enum risks competing with the existing pacing model.
>
> Recommendation: frame this as configuration that feeds into the existing `PacingConfiguration`, not as a parallel abstraction.

### 4.2 Allocation Strategy

Defines how samples are distributed across configurations.

```java
AllocationStrategy allocation = AllocationStrategy.equal();
```

Future:
- adaptive allocation (variance-aware, threshold-aware)

> **[REVIEW]** PUnit already distributes samples across inputs via `@InputSource` with even distribution, and `@ExploreExperiment(samplesPerConfig=N)` controls samples per configuration. The document should clarify:
>
> 1. Is `AllocationStrategy` about distributing a **total sample budget** across configurations (i.e., replacing `samplesPerConfig` with a total budget divided by strategy)? Or distributing **input data** across samples within each configuration?
> 2. Equal allocation is the current default. The interesting future case (adaptive/variance-aware) would require online reallocation — which has significant implications for PUnit's independence assumptions and early termination logic.
> 3. How does allocation interact with the existing budget hierarchy (Suite → Class → Method)?


## 5. Integration with Experiments

Design matrices must integrate with existing PUnit constructs:

```java
@ExploreExperiment
ExperimentDesign design() {
    return Design.fullFactorial(temperature, promptStyle);
}
```

> **[REVIEW — Does not match PUnit's extension model]** This integration example is problematic:
>
> 1. `@ExploreExperiment` is a **method-level annotation** on the experiment method itself (which receives `@Factor`-annotated parameters and an `OutcomeCaptor`). It is not placed on a separate design-provider method.
> 2. The existing integration point is `@FactorSource`, which provides `Stream<FactorArguments>` to `@ExploreExperiment`.
> 3. A realistic integration would look like:
>
> ```java
> // Registration: immutable use case constructed per configuration
> provider.registerWithFactors(MyUseCase.class, factors -> {
>     double temp = factors.getDouble("temperature");
>     String style = factors.getString("promptStyle");
>     return new MyUseCase(temp, style);
> });
>
> @ExploreExperiment(useCase = MyUseCase.class, samplesPerConfig = 5)
> @FactorSource("design")
> void explore(MyUseCase useCase,
>              @Factor("temperature") double temp,
>              @Factor("promptStyle") String style,
>              OutcomeCaptor captor) {
>     // useCase already configured — @Factor params are informational
>     captor.record(useCase.execute());
> }
>
> static Stream<FactorArguments> design() {
>     return Design.fullFactorial(
>         Factor.of("temperature", List.of(0.0, 0.7, 1.0)),
>         Factor.of("promptStyle", List.of("concise", "verbose"))
>     ).toFactorArguments();
> }
> ```
>
> This section needs to be rewritten to show how `Design`/`DesignMatrix` produces `FactorArguments` streams that plug into the existing `@FactorSource` mechanism. The use case is constructed immutably via `registerWithFactors` — the design matrix provides the factor combinations, and the factory builds a fresh instance per combination. Alternatively, if the proposal is for a **new** integration mechanism (e.g., `@DesignSource`), that needs to be stated and justified.

## 6. Reporting Requirements

- factors and levels
- design strategy
- number of configurations
- allocation strategy
- execution profile
- seed (if applicable)

Example:

```txt
Design Strategy: SEEDED_RANDOM_SUBSET
Seed: 42
Configurations: 20
Factors:
  - temperature: [0.0, 0.7, 1.0]
  - promptStyle: [concise, verbose]
```

> **[REVIEW]** PUnit already reports experiment results via `ExploreOutputWriter` into YAML files under `explorations/`. The design metadata (strategy, seed, coverage) should be captured in these YAML specs — likely as additional fields in the existing output structure. Specify where this data lives: in the YAML spec? Console output? HTML report? All three?
>
> Also, PUnit's existing exploration output records per-configuration results. The design strategy metadata is a natural addition to the file header. Show the proposed YAML extension rather than (or in addition to) the plain-text example.

## 7. Validation & Guardrails

The framework must warn when:
- factor levels are missing in the design
- configurations are poorly balanced
- sample size per configuration is too small
- combinatorial explosion is likely

> **[REVIEW]** These are reasonable guardrails but need thresholds or heuristics:
>
> 1. "Factor levels missing" — clear for SEEDED_RANDOM_SUBSET; define what "missing" means for FRACTIONAL_FACTORIAL (some levels are intentionally aliased).
> 2. "Poorly balanced" — needs a quantitative definition. Maximum imbalance ratio? Chi-squared test?
> 3. "Sample size per configuration is too small" — PUnit's `@ExploreExperiment` defaults to `samplesPerConfig=1`, which is by design for rapid screening. The guardrail should distinguish between EXPLORE (where 1 is fine) and MEASURE (where statistical power matters). Also consider: the feasibility gate (`N_min` from Wilson score) already handles this for `@ProbabilisticTest` with VERIFICATION intent.
> 4. "Combinatorial explosion" — at what threshold? 100 configs? 1000? This should be configurable.

## 8. Non-Goals

PUnit does NOT aim to provide:
- full DoE tooling
- regression modelling (ANOVA, etc.)
- automated optimisation

> **[REVIEW — Contradiction]** "Automated optimisation" is listed as a non-goal, but PUnit **already provides** `@OptimizeExperiment` with `Scorer`, `FactorMutator`, `OptimizationObjective`, and a full iteration history. This is explicitly automated optimisation of a control factor.
>
> Either:
> 1. This non-goal refers to something different (e.g., automated design optimisation / D-optimal designs) — in which case, reword to be precise.
> 2. The author was unaware of `@OptimizeExperiment` — in which case, the design strategies should acknowledge and integrate with the existing optimisation workflow (EXPLORE → select winner → OPTIMIZE → MEASURE).

## 9. Success Criteria

- Users can design experiments without combinatorial explosion
- Results remain reproducible and auditable
- Variability is attributable primarily to the system under test
- Core remains domain-agnostic

> **[REVIEW]** These criteria are aspirational but not testable. Consider making them concrete:
>
> - "Users can generate treatment combinations from factor definitions using at least two design strategies" (testable)
> - "Generated designs integrate with `@ExploreExperiment` via `@FactorSource`" (verifiable)
> - "Stochastic designs are reproducible given the same seed" (testable)
> - "Design metadata (strategy, seed, coverage) is captured in experiment output YAML" (verifiable)
>
> Also missing from success criteria: **backward compatibility**. Existing `@FactorSource` methods that manually enumerate `FactorArguments` must continue to work unchanged.

---

> **[REVIEW — Overall structural gaps]**
>
> The following topics are not addressed and need resolution before implementation:
>
> 1. **Relationship to `@OptimizeExperiment`**: Can design strategies feed into OPTIMIZE workflows? E.g., use EXPLORE with a fractional factorial to screen, then OPTIMIZE the winning factor.
> 2. **Interaction with `@InputSource`**: If an experiment has both a design matrix (factor combinations) and an input source (test data), how are they composed? Today these are independent — `@InputSource` distributes inputs across samples *within* each configuration. Confirm this remains the case.
> 3. **Budget interaction**: With automated design generation, the total sample count = configurations × samplesPerConfig. How does this interact with `timeBudgetMs` and `tokenBudget`? PUnit's existing `@ExperimentGoal` allows early termination when a configuration achieves a goal — does this still apply?
> 4. **Where does this code live?** `punit-core` (since factors are already there) or a new module? The module boundary matters for the JUnit-free constraint on `punit-core`.
> 5. **Covariate interaction**: Design factors are experimental variables; covariates are environmental context captured for baseline matching. The document should state that design factors are NOT covariates and explain the distinction for users who may confuse them.
> 6. **Immutable use case integration**: Design-generated configurations flow through `registerWithFactors` to produce immutable use case instances. The DoE layer must not rely on `@FactorSetter`, `@FactorGetter`, or `registerAutoWired` — all of which are deprecated (0.5.3) and scheduled for removal. The design matrix produces `Stream<FactorArguments>`, and the `registerWithFactors` factory consumes `FactorValues` to construct each instance. This is the only supported path.
