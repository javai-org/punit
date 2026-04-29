# Review: punit experiment DX

## Goal of this review

PUnit's experiment-side developer experience has evolved across several
iterations, and the addition of the **immutable use case principle**
forced a rethink of how factors are expressed. The result is a surface
that works but carries legacy from earlier thinking — two partially
overlapping mechanisms (`@FactorSource` and `@ConfigSource`), an
identity/instance model that is somewhat implicit, and subtle
inconsistencies in naming between Measure, Explore, and Optimize.

A sibling framework in the javai family — **feotest** (Rust) — recently
went through a substantial DX refactor of its three experiment types.
That exercise surfaced a small set of principles that produced a
noticeably cleaner API. This review asks: **what does punit's
experiment DX look like through those principles, and where should it
change?**

The output of this review is a design document at
`punit/plan/DES-EXPERIMENT-DX.md` proposing the alignment path.

## Background: the feotest exercise and what it taught us

Feotest's three experiment builders (`MeasureExperiment`,
`ExploreExperiment`, `OptimizeExperiment`) were converged onto a single
idiomatic shape. The user-facing changes and the reasoning are
captured in:

- `/Users/mikemannion/workspace/javai-orchestrator/feotest/src/experiment/measure.rs`
- `/Users/mikemannion/workspace/javai-orchestrator/feotest/src/experiment/explore.rs`
- `/Users/mikemannion/workspace/javai-orchestrator/feotest/src/experiment/optimize.rs`
- `/Users/mikemannion/workspace/javai-orchestrator/feotest/docs/USER-GUIDE.md`

The principles that drove the refactor:

1. **Identity is explicit, not derived.** Each experiment takes
   `.use_case_id("...")` as a string; it is not inferred from a
   passed-in use case instance. This decouples the experiment's
   user-facing type from the framework's identity notion.

2. **The framework owns instance lifecycle via a factory.** Each
   experiment takes `.use_case(factory_closure)` where the closure
   produces the use case instance. The framework calls the factory at
   run time and owns the instance for the run (in explore/optimize,
   once per factor/iteration; in measure, once for the whole run).
   Users no longer pass prebuilt instance references.

3. **The trial receives the instance as a parameter.** All three
   experiments' trial closures take `(&UseCase, input) -> Outcome`.
   Users never capture the use case in the trial closure — it is
   handed to them. This removes the "closure capture gymnastics" that
   plagued the old API.

4. **Factors are first-class, user-defined types.** Explore takes
   `Vec<F>`; optimize takes `initial_factor: F` plus a
   `FactorMutator<F>`. Measure has no factor (single condition). The
   factor type is the user's own struct/enum, with `Serialize` giving
   natural YAML output (scalar factors render as scalars, struct
   factors render as mappings). There is no framework-imposed enum
   wrapper like the old `FactorValue`.

5. **"One factory → one use case type" is structural.** Because there
   is a single factory producing a single T, every configuration
   compared (in explore) or tuned (in optimize) is by construction a
   variant of the same use case. No runtime check is needed for this
   invariant; the type system enforces it.

6. **Consistency across the three experiments.** The builder entry
   (`::builder()`), the terminal chain (`.build().run()`), the
   required/optional field split, and the setter names (`.inputs(...)`,
   `.trial(...)`, `.experiment_id(...)`) are identical wherever the
   concept is shared. Differences appear only where the experiments
   genuinely differ (variation supply: none / mutator / list;
   output shape).

For a compact summary of the convergence, see the final alignment
matrix at the bottom of this file.

## What punit looks like today (starting inventory)

The per-project agent should inventory punit's current experiment DX
before proposing changes. Starting points:

- `punit/punit-core/src/main/java/org/javai/punit/api/MeasureExperiment.java`
- `punit/punit-core/src/main/java/org/javai/punit/api/ExploreExperiment.java`
- `punit/punit-core/src/main/java/org/javai/punit/api/OptimizeExperiment.java`
- `punit/punit-core/src/main/java/org/javai/punit/api/FactorSource.java`
- `punit/punit-core/src/main/java/org/javai/punit/api/ConfigSource.java`
- `punit/punit-core/src/main/java/org/javai/punit/api/Factor.java`
- `punit/punit-core/src/main/java/org/javai/punit/api/FactorArguments.java`
- `punit/punit-core/src/main/java/org/javai/punit/api/UseCase.java`
- The `UseCaseProvider` implementation (wherever it lives)
- Any example tests under `punit/` and under `punitexamples/`

Also useful: the corresponding user guide and any developer-facing
documentation under `punit/docs/`.

## The history to respect

PUnit's current DX is **not** the result of naive design. It
accumulated through several earlier iterations, and the **immutable
use case** principle arrived after the factor-arguments machinery was
already in place. That arrival triggered `@ConfigSource` as a second
mechanism for supplying variations, because the factor-value-to-
constructor-call path no longer fit cleanly.

The review should treat this history as load-bearing context. The
question is not "what is wrong with the old design?" but "given what
we now know about the DX target (from feotest), what is the minimum
coherent design that honours the immutable use case principle and
reflects the learnings above?"

## Review questions

The agent producing `DES-EXPERIMENT-DX.md` should answer these
concretely, each with a citation to the current punit code and a
proposed direction:

### Identity and instance lifecycle

1. How is the use case identity currently communicated to each
   experiment? Is it derived from the injected instance, from a
   string literal in an annotation attribute, or both? Is the rule
   the same across Measure, Explore, and Optimize?
2. Who owns the instance during the run? The test method (via JUnit
   parameter injection), the `UseCaseProvider`, or the experiment
   runner? What does "owns" mean in each case — who can mutate it,
   who can drop it?
3. Can the "framework owns the instance via a factory" principle be
   expressed inside the JUnit paradigm without fighting the
   framework? (A `UseCaseProvider` that registers a factory closure
   per test might already be the JUnit-idiomatic analogue of the
   feotest factory; verify.)

### Factors and configurations

4. When do `@FactorSource` and `@ConfigSource` produce materially
   different behaviour, and when do they produce the same behaviour
   via different syntax? Is the duplication justified, or is one of
   them a leftover?
5. Could a single mechanism subsume both? In feotest, explore
   expresses both "pre-built instances" and "factor combinations" as
   a `Vec<F>` plus a factory. The factory makes them into the same
   shape. Could punit's `ConfigSource` become the default with
   `FactorSource` as a particular factory pattern (the factor being
   a `FactorArguments` or a domain struct)?
6. What does the YAML / report output look like for a factor today?
   Can a user express multi-field factors (model + temperature +
   system prompt) in a way that round-trips cleanly? Or is the user
   forced to either enumerate `NamedConfig<T>` instances or thread
   individual `@Factor("model")`, `@Factor("temperature")` parameters
   through the test method signature?

### Trial access to the instance

7. How does the test method body reach the configured use case?
   Through a method parameter (JUnit injection) or through
   `provider.getInstance(…)`? Is the shape uniform across Measure,
   Explore, and Optimize?
8. Is there a "closure capture" analogue in Java — for example,
   using a `ThreadLocal` or a captured field to let the trial reach
   state not passed in as a parameter? If so, is that the right
   idiom, or does it indicate a DX gap?

### Cross-experiment consistency

9. List the setter/attribute names used by the three experiment
   annotations. Where the concept is the same (samples per unit,
   experiment id, time budget, token budget), are the names
   identical? Where they differ (what's being varied), is the
   difference domain-motivated or accidental?
10. If a user learns `@MeasureExperiment` first, how much of the DX
    transfers when they meet `@ExploreExperiment` and
    `@OptimizeExperiment`? What doesn't transfer, and why?

### Validation and precondition handling

11. Where does punit validate experiment setup — at annotation
    processing time, at test extension activation, at the first
    invocation, or not at all? Are missing-required-attribute errors
    clear and named?
12. Does punit express "a test template produces a meaningful
    diagnostic when the user forgets to supply a `@FactorSource` /
    `@ConfigSource`" as a first-class concern? Compare to feotest's
    `build()` panics that name the missing field and its setter.

### Optimize specifics

13. How does the user supply the initial factor and the mutator
    strategy in punit? Is there a single cohesive surface, or does
    the user assemble several annotations and beans?
14. Does punit's optimize expose the iteration history to the
    mutator in a typed form, or does it hand back a generic
    "arguments" object that the user re-parses each call? (In feotest,
    `FactorMutator<F>::mutate(&F, &[IterationRecord<F>]) -> F` gives
    the user their own type all the way through.)

### What punit has that feotest doesn't (and why)

15. JUnit gives punit things feotest has to build from scratch:
    parameterised tests, extension injection, the `@BeforeEach`
    lifecycle, reporters that CI systems already understand. Which
    of these make a Rust-style builder pattern **inappropriate** for
    punit, and which are orthogonal?
16. PUnit's proc-macro / annotation style makes it easy to express
    *metadata* about an experiment (time budget, expiration days,
    skip warmup). Feotest has to spell those as setter calls.
    When is metadata-as-annotation the right choice, and when does
    it obscure a decision better made at call time?

## Constraints the review should honour

- **Preserve the immutable use case principle.** It was a
  deliberately chosen design pillar; proposals that erode it are
  out of scope.
- **Don't over-index on feotest.** Feotest is Rust; punit is Java
  inside JUnit. Some feotest idioms (closures-as-factories,
  stackable builder setters) translate naturally; others
  (generic `<F, T>` parameters everywhere) do not. The goal is
  alignment on *principles* — identity is explicit, instance
  lifecycle is framework-owned, factors are first-class — not on
  mechanical API shapes.
- **Leave a migration path.** PUnit has real users (`punitexamples`
  at minimum). A proposal should note what each change breaks and
  what a deprecation / migration sequence would look like. "Big
  bang rewrite" is a legitimate answer but it has to be argued for.
- **Keep the three experiments consistent with each other.**
  Divergence between Measure/Explore/Optimize should be justified
  by domain difference, not by history. This is the single biggest
  lever.

## Deliverable

Produce `punit/plan/DES-EXPERIMENT-DX.md` with:

1. A concise inventory of the current experiment DX (one table per
   experiment: annotations, attributes, parameter injection points,
   lifecycle hooks).
2. A direct comparison against feotest's post-refactor shape, using
   the alignment matrix below as a template.
3. Answers to each of the review questions above.
4. A proposed target DX — annotated with which changes are pure
   renames, which are structural, and which break existing users.
5. A migration sketch: deprecation plan, compatibility shims if any,
   and a rough sequencing of the changes.

## Reference: feotest's post-refactor alignment matrix

| Concept | measure | optimize | explore |
|---|---|---|---|
| Entry | `::builder()` | `::builder()` | `::builder()` |
| Terminal | `.build().run()` | `.build().run()` | `.build().run()` |
| Id | `.use_case_id(str)` | `.use_case_id(str)` | `.use_case_id(str)` |
| Factor / variation | *(none)* | `.initial_factor(F)` + `.mutator(impl FactorMutator<F>)` | `.factors(Vec<F>)` |
| Instance production | `.use_case(Fn() -> T)` | `.use_case(Fn(&F) -> T)` | `.use_case(Fn(&F) -> T)` |
| Trial | `.trial(Fn(&T, &str) -> Outcome)` | `.trial(Fn(&T, &str) -> Outcome)` | `.trial(Fn(&T, &str) -> Outcome)` |
| Per-unit samples | `.samples(n)` | `.samples_per_iteration(n)` | `.samples_per_config(n)` |
| Experiment id | `.experiment_id(s)` | `.experiment_id(s)` | `.experiment_id(s)` |
| Required-field panics at build | ✓ | ✓ | ✓ |

The cells that match across all three experiments reflect the DX
principles; the cells that differ reflect the genuine domain
differences between the three experiment types. That is the kind of
outcome the punit review should aim for.
