# Covariate Warning Requirements

## Overview
When a use case declares non-informational covariates (TEMPORAL, CONFIGURATION,
OPERATIONAL, etc.) but the probabilistic test does not reference a baseline spec,
PUnit should emit a warning. This helps users understand that their covariate
declarations have no effect without a baseline to match against.

## Background
Covariates are factors that may influence system behavior (e.g., time of day,
model configuration, infrastructure region). PUnit uses covariates to select
appropriate baselines during test execution. However, if no baseline exists
(non-spec'd test) or no baseline document is present, the covariate declarations
serve no purpose and may mislead users into thinking their tests are
covariate-aware when they are not.

## Goals
- Warn users when covariates are declared but cannot affect test behavior.
- Help users understand the relationship between covariates and baselines.
- Guide users toward running MEASURE experiments to establish baselines.

## Non-Goals
- Failing tests that have this configuration (warning only).
- Automatically running MEASURE experiments.
- Validating covariate values or profiles.

## Definitions
- **Non-informational covariate**: Any covariate with a category other than
  INFORMATIONAL. These categories participate in baseline matching:
  - TEMPORAL (soft match)
  - CONFIGURATION (hard gate)
  - EXTERNAL_DEPENDENCY (soft match)
  - INFRASTRUCTURE (soft match)
  - OPERATIONAL (soft match)
  - DATA_STATE (soft match)
- **Informational covariate**: A covariate with category INFORMATIONAL. These
  are metadata for audit trails only and do not participate in baseline selection.
- **Non-spec'd test**: A `@ProbabilisticTest` that does not reference a use case
  class or does not have a corresponding baseline spec file.

## User Stories
- As a user, I want to be warned if I declare covariates that have no effect,
  so I understand my test is not covariate-aware.
- As a user, I want the warning to tell me how to fix the issue (run MEASURE
  or add spec reference).

## Functional Requirements

### FR-1: Detection of Warning Condition
The warning condition is met when ALL of the following are true:
1. The `@ProbabilisticTest` annotation specifies a `useCase` class.
2. The use case class has non-informational covariates declared via `@UseCase`.
3. Either:
   a. No baseline spec file exists for the use case, OR
   b. The test is running in legacy mode (no spec reference).

### FR-2: Warning Content
The warning message must include:
- The use case class name.
- The count and categories of non-informational covariates.
- Guidance: suggest running a MEASURE experiment or checking spec configuration.

Example:
```
WARNING: Use case 'ShoppingBasketUseCase' declares 4 non-informational covariates
(TEMPORAL, CONFIGURATION) but no baseline spec is available. Covariate declarations
have no effect without a baseline. Consider running a MEASURE experiment to
establish a baseline.
```

### FR-3: Warning Timing
The warning should be emitted once per test method, before any samples execute.
This ensures the user sees the warning early in the test run.

### FR-4: Warning Mechanism
The warning should be emitted via:
- JUnit 5's `ExtensionContext.publishReportEntry()` for structured reporting.
- SLF4J logger at WARN level for console visibility.

### FR-5: No Test Failure
The warning must NOT cause the test to fail. It is informational only.

## Technical Approach

### Detection Logic
```
1. In ProbabilisticTestExtension.provideTestTemplateInvocationContexts():
2. Get @ProbabilisticTest annotation
3. If annotation.useCase() is Void.class → no warning (legacy mode, intentional)
4. Extract CovariateDeclaration from use case class
5. Filter to non-informational covariates:
   - declaration.allKeys().stream()
     .filter(key -> !declaration.getCategory(key).isIgnoredInMatching())
6. If no non-informational covariates → no warning
7. Check if baseline spec exists for the use case
8. If spec exists → no warning
9. Emit warning with covariate details
```

### Key Classes Involved
| Class | Role |
|-------|------|
| `ProbabilisticTestExtension` | Entry point; emits warning in `provideTestTemplateInvocationContexts()` |
| `UseCaseCovariateExtractor` | Extracts `CovariateDeclaration` from `@UseCase` annotation |
| `CovariateDeclaration` | Provides `allKeys()` and `getCategory(key)` methods |
| `CovariateCategory` | Provides `isIgnoredInMatching()` to identify INFORMATIONAL |
| `BaselineSelectionOrchestrator` | Determines if spec exists |

### Implementation Location
Primary location: `ProbabilisticTestExtension.provideTestTemplateInvocationContexts()`
(around line 176), after configuration resolution but before sample generation.

## Configuration Requirements
No additional configuration is required. The warning is always enabled.

Future consideration: A system property to suppress the warning:
```
-Dpunit.warnings.covariateSpec=false
```

## Compatibility
- This change is additive and does not affect existing test behavior.
- Tests that currently run without warnings will continue to pass.
- Only new warnings are emitted for the specified condition.

## Observability
The warning should be captured in:
- JUnit test reports via `publishReportEntry()`.
- Console output via SLF4J WARN.
- Any test listeners that subscribe to report entries.

## Open Questions
1. Should the warning include the specific covariate keys, or just categories?
2. Should there be a way to acknowledge/suppress the warning per test method?
3. Should the warning differentiate between "no spec file found" and "spec file
   exists but covariate profile doesn't match"?
