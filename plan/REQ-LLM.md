# PUnit LLM — Extension Module for LLM Evaluation

## 1. Purpose

Provide a **domain-specific extension** of `punit-core` for statistically sound evaluation of LLM-backed systems.

This module enables:

- Prompt-driven experimentation
- Counterfactual testing
- Bias and invariance analysis
- LLM-specific evaluation and reporting

> **[REVIEW — Scope and module placement]** PUnit's multi-module structure is: `punit-core` (JUnit-free), `punit-junit5` (JUnit extensions), `punit-sentinel` (standalone runtime). A `punit-llm` module needs to declare its dependencies:
>
> 1. Does it depend on `punit-core` only (JUnit-free, usable from Sentinel)?
> 2. Or on `punit-junit5` (requires JUnit, experiment annotations)?
> 3. Or does it have a split (e.g., `punit-llm-core` + `punit-llm-junit5`)?
>
> The answer determines whether LLM evaluation is available in the Sentinel runtime (continuous monitoring without JUnit) or only in the experiment workflow.

---

## 2. Design Principles

### 2.1 Built on Core

- Must reuse:
    - Factor
    - DesignMatrix
    - ExecutionProfile
- Must NOT duplicate statistical logic

> **[REVIEW — Nonexistent references]** `DesignMatrix` and `ExecutionProfile` do not exist in PUnit today — they are proposals from REQ-DOE.md which themselves need reconciliation with PUnit's existing APIs (see REQ-DOE.md review). This document has a hard dependency on REQ-DOE.md. The actual existing constructs this module should build on are:
>
> - `@Factor`, `@FactorSource`, `FactorArguments`, `FactorValues` — factor system
> - `@ExploreExperiment`, `@MeasureExperiment` — experiment annotations
> - `PacingConfiguration` — execution control
> - `BinomialProportionEstimator`, `ThresholdDeriver` — statistical model
> - `UseCaseOutcome`, `OutcomeCaptor` — outcome capture
> - `@UseCase`, `UseCaseProvider`/`UseCaseFactory` — use case abstraction
>
> Restate "Built on Core" in terms of what actually exists, or explicitly note which items depend on REQ-DOE.md being implemented first.

---

### 2.2 Domain-Specific Layer

Adds LLM-specific abstractions:

- prompts
- prompt variation
- model configuration
- response evaluation

---

### 2.3 Statistical Integrity

- Must respect:
    - independence assumptions (explicitly caveated)
    - stationarity
- Must surface violations clearly

> **[REVIEW]** PUnit already surfaces stationarity caveats in `VerdictTextRenderer` and captures temporal covariates. What LLM-specific violations are anticipated beyond what PUnit already handles? For example:
>
> - **Model drift**: LLM providers update models silently — this is a stationarity violation. Should the module detect or warn about this? (Model version as a covariate?)
> - **Context window effects**: Prior prompts may influence subsequent responses in session-based APIs — this is an independence violation. Should the module enforce stateless API calls?
> - **Rate limiting / throttling**: Provider rate limits may introduce temporal bias (early samples vs late samples). PUnit's pacing already addresses this, but is it sufficient?
>
> These are concrete violations worth enumerating.

---

## 3. Core Concepts

### 3.1 Prompt

A structured input to an LLM.

```java
Prompt prompt = Prompt.template(
    "Summarise the following text: {{text}}"
);
```

> **[REVIEW — Relationship to PUnit's input model]** PUnit already has `@InputSource` for providing test data to experiments. A prompt template could be:
>
> 1. **A factor** — if the experiment varies the prompt (e.g., comparing "concise" vs "verbose" styles). This maps to `@Factor("prompt")`.
> 2. **An input** — if the prompt is test data fed via `@InputSource`. Different prompts are different test inputs, not experimental variations.
> 3. **Part of the use case** — if the prompt is internal to the `@UseCase` implementation.
>
> The `Prompt.template()` API conflates these. Clarify which role prompts play in the PUnit model. If prompts are a new first-class concept distinct from factors and inputs, justify why the existing abstractions are insufficient.

### 3.2 Prompt Suite

```java
PromptSuite suite = PromptSuite.of(
    "summarisation",
    prompts
);
```

> **[REVIEW]** What does a `PromptSuite` give you that `@InputSource` returning `Stream<Prompt>` doesn't? If it's just a named collection of prompts, it's an `@InputSource`. If it carries metadata (expected outputs, evaluation criteria, difficulty levels), that's new and worth specifying.

### 3.3 LLM Factors

Specialised factors for experiment design:

```java
Factor<Double> temperature = Factor.of("temperature", List.of(0.0, 0.7));

Factor<String> model = Factor.of("model", List.of("gpt-4", "local-llm"));

Factor<String> systemPrompt = Factor.of("systemPrompt", List.of("strict", "creative"));
```

> **[REVIEW — Same nomenclature issue as REQ-DOE.md]** PUnit uses `@Factor` annotations and `@FactorSource`/`FactorArguments`, not `Factor.of()`. These examples would look like:
>
> ```java
> @FactorSource("llmConfigs")
> static Stream<FactorArguments> llmConfigs() {
>     return FactorArguments.configurations()
>         .names("temperature", "model", "systemPrompt")
>         .values(0.0, "gpt-4", "strict")
>         .values(0.7, "local-llm", "creative")
>         .stream();
> }
> ```
>
> These are **standard PUnit factors** — there is nothing LLM-specific about them. Temperature, model, and system prompt are just factor names with string/double levels. The document should clarify what makes these "specialised" beyond naming convention. If `punit-llm` provides pre-built factor definitions or validation (e.g., "temperature must be in [0, 2]"), state that explicitly.

### 3.4 Counterfactual Set

Defines semantically equivalent prompts differing in one attribute.

```java
CounterfactualSet genderSwap = CounterfactualSet.of(
    basePrompt,
    List.of(
        promptMale,
        promptFemale
    )
);
```

Used for:
- bias detection
- invariance testing

> **[REVIEW — This is the most novel concept in this document]** Counterfactual testing implies **paired statistical comparison**: running the same experiment with controlled variation and testing whether outcomes differ significantly. This is fundamentally different from PUnit's current model in two ways:
>
> 1. **PUnit tests single proportions** (is p̂ ≥ threshold?). Counterfactual testing requires **two-proportion comparison** (is p̂₁ - p̂₂ ≥ δ?). This needs new statistical machinery — e.g., a two-proportion z-test or Fisher's exact test. The `BinomialProportionEstimator` does not support this today.
> 2. **Pairing integrity**: If prompts are paired (male/female variant of the same base prompt), the samples should be paired too (same random seed, same context). PUnit's current execution model runs samples independently. How is pairing maintained?
>
> This section needs significantly more detail:
> - What statistical test is used for counterfactual comparison?
> - How is pairing implemented at the execution level?
> - Is a counterfactual set a special kind of `@FactorSource`? Or a distinct execution mode?
> - What does the verdict look like? (Currently PUnit produces a single pass/fail verdict per test. A counterfactual test would produce a comparison verdict.)

### 3.5 Evaluator

Determines success/failure from LLM output.

```java
Evaluator evaluator = Evaluators.semanticMatch(expectedAnswer);
```

> **[REVIEW — Relationship to existing outcome model]** PUnit already has a well-defined outcome model:
>
> - `UseCaseOutcome` with `assertContract()` (functional postcondition), `assertLatency()` (duration), `assertAll()` (adaptive)
> - `OutcomeCaptor` for recording outcomes in experiments
> - Success/failure is determined by assertion pass/fail within the use case
>
> The proposed `Evaluator` appears to be an alternative way to determine success/failure from LLM output. Key questions:
>
> 1. Does `Evaluator` replace `UseCaseOutcome.assertContract()`? Or does it produce a `UseCaseOutcome`?
> 2. `Evaluators.semanticMatch(expectedAnswer)` implies comparing LLM output against an expected answer — is this a fuzzy match? Using what algorithm? (Embedding similarity? Another LLM as judge? Substring match?)
> 3. "Support composability" — does this mean `Evaluator.and(other)` or a pipeline of evaluators? How does a composite evaluator map to PUnit's binary pass/fail model?
> 4. If evaluators are composable and potentially soft (semantic match with a threshold), where does the Bernoulli trial boundary fall? Is each evaluator a separate dimension (like functional vs latency), or is the composite result a single pass/fail?

### 3.6 Bias Metrics

Measures disparity across groups.

Examples:
- pass-rate difference
- refusal-rate difference
- disagreement rate

> **[REVIEW — Significant statistical extension]** These metrics require statistical tests that PUnit does not currently implement:
>
> - **Pass-rate difference**: Two-proportion z-test or chi-squared test. Needs sample size, effect size, and confidence level.
> - **Refusal-rate difference**: Same as above but for a specific failure mode. PUnit tracks `failureDistribution` in `SampleResultAggregator` — this could be leveraged.
> - **Disagreement rate**: For paired samples, this is McNemar's test. Requires paired execution.
>
> These are new statistical capabilities. The document should:
> 1. Specify which tests are used and their assumptions
> 2. Define how thresholds are set (absolute difference? Relative? Configurable?)
> 3. Clarify whether these extend `BinomialProportionEstimator` or live in a separate statistical module
> 4. Address multiple comparison correction if testing multiple group pairs simultaneously

## 4. Functional Requirements

### 4.1 Prompt Variation

- Support templated prompts
- Generate variants programmatically
- Integrate with Factor/DesignMatrix

> **[REVIEW]** "Integrate with Factor/DesignMatrix" — again referencing REQ-DOE.md proposals. In current PUnit terms: prompt variants would be factor levels provided via `@FactorSource`, or inputs provided via `@InputSource`. Which is it? Prompt *structure* variation (template with different fill-ins) is an input concern. Prompt *style* variation (concise vs verbose) is a factor concern. Distinguish these.

---

### 4.2 Counterfactual Experiments

- Must support paired/grouped execution
- Maintain pairing integrity
- Enable comparison across variants

> **[REVIEW]** "Paired/grouped execution" and "pairing integrity" are critical but undefined. Concretely:
>
> - Does "paired" mean each counterfactual variant is executed with the same random seed / same input / same context?
> - Is pairing enforced at the framework level (PUnit guarantees paired execution) or at the use case level (user ensures pairing)?
> - How does pairing interact with PUnit's sample-level independence assumption? Paired samples are by definition not independent — this fundamentally changes the statistical model.
> - "Enable comparison across variants" — this requires a comparison verdict. PUnit's current verdict is per-test (single proportion). A counterfactual verdict compares proportions across groups. This is a new verdict type.

---

### 4.3 Evaluation Pipeline

- Evaluators must:
    - map outputs → success/failure
    - support composability
- Must integrate with Bernoulli model

> **[REVIEW]** "Must integrate with Bernoulli model" — in PUnit terms, this means the evaluator ultimately produces a binary pass/fail that feeds into `SampleResultAggregator`. This is already how `UseCaseOutcome.assertContract()` works. Clarify whether `Evaluator` is a new abstraction that wraps this, or an alternative path. If it's a pipeline (multiple evaluators in sequence), does each step produce its own dimension result, or is only the final result recorded?

---

### 4.4 Bias & Invariance Testing

Must support:

- group-level success rate comparison
- paired disagreement measurement
- threshold-based assertions

> **[REVIEW]** "Threshold-based assertions" — what kind of threshold?
>
> - Maximum acceptable difference in pass rates between groups (e.g., |p̂_male - p̂_female| ≤ 0.05)?
> - Minimum p-value from a statistical test?
> - Both?
>
> PUnit's current threshold model derives from baselines via `ThresholdDeriver`. Bias thresholds are fundamentally different — they are **comparative** (between groups) not **absolute** (against a baseline). The document needs to specify how bias thresholds are configured, derived, and reported.

---

### 4.5 Execution Metadata

Capture and report:

- model name/version
- temperature/top-p
- system prompt
- timestamp
- environment (local vs hosted)

> **[REVIEW — Map to existing PUnit concepts]** PUnit already captures environmental context via covariates on `@UseCase`:
>
> - `covariateTimezone`, `covariateTimeOfDay`, `covariateDayOfWeek`, `covariateRegion` — temporal/operational
> - Custom covariates via `@Covariate` annotations
>
> Model name, temperature, etc. could be:
>
> 1. **Factors** (if varied experimentally) — captured via `@Factor`/`FactorSuit`
> 2. **Covariates** (if fixed but environmentally determined) — captured via `@Covariate`
> 3. **Use case attributes** (if intrinsic to the use case) — captured in `@UseCase` metadata
>
> The document should map each metadata item to the appropriate PUnit concept rather than proposing a generic "capture and report" mechanism. Timestamp and environment are already covered by PUnit's covariate system.

---

### 4.6 Reporting

Reports must include:

- prompt definitions
- factor breakdown
- evaluator definitions
- model configuration
- bias metrics
- execution profile
- statistical caveats

Example:

```txt
Model: local-llm-v1
Temperature: 0.7

Bias Analysis:
  Male pass rate: 0.91
  Female pass rate: 0.83
  Difference: -0.08 (threshold: 0.05) → FAIL
```

> **[REVIEW]** PUnit has three reporting surfaces:
>
> 1. **Console summary** (`ResultPublisher.printConsoleSummary`) — compact per-test verdict
> 2. **Verbose console** (`VerdictTextRenderer.renderForReporter()`) — full statistical detail
> 3. **HTML report** (`VerdictTextRenderer.renderSummary()`/`renderStatisticalAnalysis()`)
>
> Where does bias reporting appear? All three? The example above looks like console output. Also:
>
> - "Statistical caveats" — PUnit already generates caveats in `VerdictTextRenderer`. LLM-specific caveats (e.g., "model version may have changed between baseline and test") should integrate with this existing mechanism.
> - The bias analysis example shows a comparison verdict (FAIL based on difference threshold). This is a new verdict structure — currently PUnit verdicts are `PASS`/`FAIL` based on single-proportion tests. A bias verdict needs to render the comparison, the threshold, the statistical test used, and the p-value.
>
> Also, PUnit's experiment output goes to YAML files. Where do bias metrics land in the YAML structure?

## 5. Guardrails

Warn when:

- comparing across different model versions
- insufficient samples per group
- counterfactual imbalance
- incompatible prompt sets

> **[REVIEW]** Good guardrails, but need specifics:
>
> 1. "Comparing across model versions" — how is model version detected? Is it a covariate that must match between baseline and test? Or a factor that varies within the experiment?
> 2. "Insufficient samples per group" — what is the minimum? For a two-proportion z-test, power analysis gives the required N. Should PUnit compute this (analogous to the feasibility gate's N_min for single proportions)?
> 3. "Counterfactual imbalance" — unequal group sizes? Or unequal pairing? Both?
> 4. "Incompatible prompt sets" — what makes prompt sets incompatible? Different template structures? Different numbers of variants?

---

## 6. Non-Goals

- No training/fine-tuning
- No full fairness framework
- No heavy statistical modelling

> **[REVIEW]** "No heavy statistical modelling" conflicts with the bias metrics requirements in §3.6 and §4.4, which require two-proportion tests, McNemar's test, and potentially multiple comparison correction. These ARE statistical modelling. Reword to clarify what is excluded — perhaps "no regression modelling, ANOVA, or effect-size estimation beyond proportion comparisons."

---

## 7. Deliverables

- punit-llm module
- Prompt + Counterfactual APIs
- Evaluator framework
- Bias metric utilities
- Reporting extensions
- Documentation

> **[REVIEW]** Missing from deliverables:
>
> - **Statistical extensions** to `punit-core` for two-proportion testing (these cannot live in `punit-llm` alone if they are general-purpose statistics)
> - **New verdict type** for comparative/bias results
> - **Integration tests** demonstrating the EXPLORE → MEASURE → TEST workflow with LLM use cases
> - Clarify whether `punit-llm` is a separate Gradle module (new `build.gradle.kts`), or a package within an existing module

---

## 8. Success Criteria

- Users can run statistically sound LLM experiments
- Bias/invariance can be tested rigorously
- Results are reproducible and interpretable
- Core remains clean and domain-agnostic

> **[REVIEW]** "Core remains clean and domain-agnostic" is the most important criterion here. Several proposals in this document risk violating it:
>
> - Two-proportion statistical tests are domain-agnostic — they should live in `punit-core/statistics`, not `punit-llm`
> - Comparative verdicts are domain-agnostic — any experiment might compare groups
> - Only prompt-specific abstractions (`Prompt`, `PromptSuite`, `CounterfactualSet`, LLM-specific evaluators) belong in `punit-llm`
>
> Make success criteria testable (same feedback as REQ-DOE.md §9).

---

> **[REVIEW — Overall structural gaps]**
>
> 1. **Dependency ordering**: This document depends heavily on REQ-DOE.md (Factor API, DesignMatrix, ExecutionProfile). REQ-DOE.md itself needs reconciliation with PUnit's existing APIs. Implementation of `punit-llm` is blocked on REQ-DOE.md resolution. State this dependency explicitly.
>
> 2. **The core statistical gap**: PUnit's entire statistical model is built on **single binomial proportion testing** (is p̂ ≥ threshold?). Counterfactual and bias testing require **two-sample proportion comparison** (is |p̂₁ - p̂₂| ≤ δ?). This is the single largest architectural addition implied by this document and it's barely mentioned. It affects:
>    - `BinomialProportionEstimator` (or a new `ProportionComparisonEstimator`)
>    - `ThresholdDeriver` (comparative thresholds are different from absolute thresholds)
>    - `SampleResultAggregator` (needs per-group tracking)
>    - `EarlyTerminationEvaluator` (comparative tests have different impossibility conditions)
>    - `ProbabilisticTestVerdict` (new verdict structure for comparisons)
>    - `VerdictTextRenderer` (rendering comparative results)
>
> 3. **What is genuinely LLM-specific vs domain-agnostic?**
>    - LLM-specific: `Prompt`, `PromptSuite`, `CounterfactualSet`, LLM evaluators, model metadata
>    - Domain-agnostic (belongs in core): two-proportion tests, comparative verdicts, group-level aggregation, paired execution
>    - The document should separate these explicitly. Much of the real work is in core, not in `punit-llm`.
>
> 4. **Missing: end-to-end workflow example.** Show a complete LLM experiment from use case definition through `@ExploreExperiment` to bias analysis verdict. Use PUnit's actual annotation-based API. This would ground the abstract concepts and reveal integration gaps.
>
> 5. **Missing: relationship to Sentinel.** If LLM evaluation is available in `punit-sentinel` (continuous monitoring), bias metrics become ongoing SLA checks. Is this in scope?
