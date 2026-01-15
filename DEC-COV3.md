DEC-COV3: Lazy Baseline Selection for Covariate-Aware Tests
===========================================================

Purpose
-------
Remove the requirement that `UseCaseProvider` be static and registered in `@BeforeAll`
for covariate-aware baseline selection. Make baseline selection lazy so it occurs only
once a test instance (and therefore a use case instance) exists.

This document focuses exclusively on the technical details implied by this change.


Background: Current Flow and Constraint
---------------------------------------
JUnit Jupiter lifecycle (simplified):
1) Static initialization
2) `@BeforeAll`
3) `provideTestTemplateInvocationContexts()`  <-- baseline selection happens here
4) Test instance created
5) `@BeforeEach`
6) Test invocation(s)

Today, baseline selection occurs at step (3). At that moment:
- The test instance does not exist.
- The `UseCaseProvider` instance field is not available.
- `@CovariateSource` methods on the use case cannot be invoked because no use case
  instance exists.

This forces users to declare a static `UseCaseProvider` and register factories in
`@BeforeAll` so that the extension can construct a use case instance early.
This is correct but brittle and counter to the principle that the test can own
provider instantiation without static wiring.


Design Goal
-----------
Delay baseline selection until after a test instance exists, so:
- `@CovariateSource` methods can be invoked on an actual use case instance.
- Provider registration can remain in instance lifecycle methods (`@BeforeEach`).
- Baseline selection stays covariate-aware and deterministic.


Key Concept: Lazy Baseline Selection
------------------------------------
Baseline selection will be deferred from `provideTestTemplateInvocationContexts()` to
the first sample invocation in `interceptTestTemplateMethod()`.

The extension will:
1) In `provideTestTemplateInvocationContexts()`:
   - Compute only the data needed to select later (e.g., specId, declaration, footprint).
   - Store this in the extension context store as "pending selection".
   - Do NOT select a baseline yet.

2) In `interceptTestTemplateMethod()` for the first sample:
   - Resolve the use case instance (via `UseCaseProvider`).
   - Resolve covariate profile using that instance.
   - Select the baseline using the full covariate-aware algorithm.
   - Cache the selection for the remaining samples.

3) Subsequent samples reuse the cached selection.


Detailed Flow
-------------
### Phase 1: Pre-invocation (template context)
Location: `ProbabilisticTestExtension.provideTestTemplateInvocationContexts()`

Actions:
- Resolve `specId` (based on `@ProbabilisticTest.useCase()`).
- Extract `CovariateDeclaration` from the use case class.
- Compute footprint (using declaration, empty factors).
- Query baseline repository for candidates matching footprint.
- If no candidate baselines are found:
  - Record this state in `PendingBaselineSelection` (empty candidates list).
  - Do not fail during Phase 1.
  - Phase 2 will throw `NoCompatibleBaselineException` before the first sample
    executes. This exception already produces a readable explanation plus guidance
    (e.g., run MEASURE or use EXPLORE), and the test run halts immediately.
- Store a `PendingBaselineSelection` in the extension store:
  - `specId`
  - `useCaseClass`
  - `declaration`
  - `footprint`
  - `candidates`

Notes:
- Do not fail here if CONFIGURATION covariates mismatch, because no test instance
  and no `@CovariateSource` values are available.


### Phase 2: First sample invocation
Location: `ProbabilisticTestExtension.interceptTestTemplateMethod()`

Before executing the first sample:
- If no selection exists in the store, attempt lazy selection:
  - Obtain the `UseCaseProvider` from the test instance.
  - Create the use case instance using the provider.
  - Build `DefaultCovariateResolutionContext` with the use case instance.
  - Resolve `CovariateProfile`.
  - Execute `BaselineSelector.select(...)`.
  - Store:
    - Selected `ExecutionSpecification`
    - `SelectionResult` (including filename, conformance details)
  - Log selected baseline filename.
  - Emit non-conformance warnings if applicable.

If selection fails due to CONFIGURATION mismatch, throw `NoCompatibleBaselineException`
with guidance (MEASURE vs EXPLORE).


### Phase 3: Subsequent samples
Location: `ProbabilisticTestExtension.interceptTestTemplateMethod()`

- If selection already exists, do nothing.
- Continue sampling as usual.


Data Structures
---------------
### PendingBaselineSelection (new, internal)
Stored in extension context store:
- `specId: String`
- `useCaseClass: Class<?>`
- `declaration: CovariateDeclaration`
- `footprint: String`
- `candidates: List<BaselineCandidate>`

Optional additional fields:
- `hasSpec: boolean` (if no spec, skip selection entirely)
- `wasInlineThreshold: boolean` (if minPassRate set explicitly, skip selection)


Extension Store Keys
--------------------
New key:
- `PENDING_SELECTION_KEY`

Existing keys reused:
- `SPEC_KEY`
- `SELECTION_RESULT_KEY`


Selection Conditions
--------------------
Lazy selection is performed only if:
- The test is spec-driven (no explicit `minPassRate`).
- A `specId` is resolved from `@ProbabilisticTest.useCase()`.
- Covariate declaration is non-empty.
- Baseline candidates exist for the footprint.

If any of these conditions fail, the extension falls back to legacy behavior
without selection:
- For inline thresholds: no baseline is selected.
- For missing specs: fallback to default logic and messaging.


UseCaseProvider Requirements
----------------------------
The provider no longer needs to be static, but it must be available on the
test instance at the time of the first sample invocation. This means:
- Registration in `@BeforeEach` is acceptable.
- Registration in `@BeforeAll` remains valid.
- PUnit must locate the provider by scanning test instance fields.

If no provider is found:
- `@CovariateSource` resolution falls back to system properties and environment
  variables.
- CONFIGURATION covariates may be `UNDEFINED`, triggering a configuration mismatch
  with informative error guidance.


Logging Requirements
--------------------
When selection succeeds:
- Print baseline filename (once), before sampling begins.
- Log non-conformance warnings (if any).

When selection fails (CONFIGURATION mismatch):
- Throw `NoCompatibleBaselineException` with:
  - Current CONFIGURATION values
  - Available baseline values
  - Actionable steps (MEASURE vs EXPLORE)

When selection is skipped:
- If inline threshold mode, report inline threshold in statistical explanation.
- If spec not found, warn appropriately.


Thread Safety
-------------
Selection must be performed exactly once per test invocation sequence.
Implementation must use the extension context store as the single source of truth.
If tests are parallelized:
- Ensure selection uses atomic check-and-set or a store `getOrComputeIfAbsent()`.
- The selection process must be idempotent (same candidates + same test profile
  yields same result).


Impact on Existing Code
-----------------------
Expected changes include:
- `ProbabilisticTestExtension`:
  - Introduce `PendingBaselineSelection` and store it in `provideTestTemplateInvocationContexts()`.
  - Add lazy selection in `interceptTestTemplateMethod()` before first sample.
  - Remove early selection from `provideTestTemplateInvocationContexts()`.
  - Keep logging and selection result storage unchanged, but move to lazy path.
- `ConfigurationResolver`:
  - Ensure `specId` is retained in the resolved configuration (needed for later selection).
- `UseCaseProvider`:
  - No API changes required.
  - The provider is discovered at runtime from test instance fields.


Test Plan
---------
1) Spec-driven test with provider registered in `@BeforeEach`:
   - Should select baseline and log filename.
   - Should resolve covariates via `@CovariateSource`.

2) Spec-driven test with provider in `@BeforeAll`:
   - Should behave identically.

3) Inline-threshold test:
   - Should not attempt baseline selection.
   - Should not require provider.

4) CONFIGURATION mismatch (use case instance returns different values):
   - Should fail with `NoCompatibleBaselineException`.
   - Error message should include guidance (MEASURE vs EXPLORE).

5) No provider available:
   - Should fall back to system properties / env vars.
   - If still `UNDEFINED`, should surface mismatch message.


Open Questions
--------------
1) Should a cached use case instance created during selection be reused for
   sampling, or should sampling create its own instance via the provider?
   - Current plan: selection instance is only for covariate resolution.

2) Should selection be performed at class level (once per test method) or per
   invocation stream? Current plan: per test method, cached in store.

3) Should candidates be loaded during lazy selection instead of preloaded?
   - Preload in phase 1 to keep errors deterministic and avoid repeated scans.


Summary
-------
Lazy baseline selection removes the static-provider constraint by deferring
covariate resolution until a test instance exists. This preserves the core
principle that the use case instance owns its covariate values, while keeping
baseline selection covariate-aware and deterministic.


