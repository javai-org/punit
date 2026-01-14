# PLAN-COV2: Implementation Plan for Covariate Categories

## Overview

This plan implements the design specified in `DES-COV2.md`. The work is divided into four phases, each building on the previous.

---

## Status Summary

| Phase | Description                    | Status      |
|-------|--------------------------------|-------------|
| 1     | Covariate Categories           | NOT STARTED |
| 2     | Use Case Instance Provisioning | NOT STARTED |
| 3     | File Naming & Reporting        | NOT STARTED |
| 4     | Shopping Example Update        | NOT STARTED |

---

## Phase 1: Covariate Categories

**Goal:** Implement the `CovariateCategory` enum and integrate it into the covariate infrastructure.

### Tasks

| #    | Task                                                              | Status |
|------|-------------------------------------------------------------------|--------|
| 1.1  | Create `CovariateCategory` enum with six categories               | ⬜      |
| 1.2  | Add `category()` method to `StandardCovariate`                    | ⬜      |
| 1.3  | Create `@Covariate` annotation for custom covariates              | ⬜      |
| 1.4  | Update `CovariateDeclaration` to track category per covariate     | ⬜      |
| 1.5  | Update `UseCaseCovariateExtractor` to parse categories            | ⬜      |
| 1.6  | Implement two-phase filtering in `BaselineSelector`               | ⬜      |
| 1.7  | Update `NoCompatibleBaselineException` for CONFIGURATION mismatch | ⬜      |
| 1.8  | Update `CovariateWarningRenderer` for category-specific messages  | ⬜      |
| 1.9  | Implement temporal matching strategy (closest fit)                | ⬜      |
| 1.10 | Tests for category-aware baseline selection                       | ⬜      |
| 1.11 | Tests for CONFIGURATION hard fail with actionable message         | ⬜      |
| 1.12 | Tests for INFORMATIONAL covariates ignored                        | ⬜      |

### Acceptance Criteria (Phase 1)

- [ ] `CovariateCategory` enum implemented with six categories
- [ ] `StandardCovariate` extended with category assignment
- [ ] `@Covariate` annotation supports custom covariates with categories
- [ ] `BaselineSelector` implements two-phase algorithm
- [ ] CONFIGURATION mismatch produces hard fail with guidance
- [ ] Soft-match categories produce category-specific warnings
- [ ] INFORMATIONAL covariates are ignored in selection
- [ ] Temporal matching uses closest-fit algorithm (±30 min tolerance)

---

## Phase 2: Use Case Instance Provisioning

**Goal:** Enable PUnit to obtain use case instances for `@CovariateSource` method invocation.

### Tasks

| #    | Task                                                                     | Status |
|------|--------------------------------------------------------------------------|--------|
| 2.1  | Create `UseCaseProvider` interface                                       | ⬜      |
| 2.2  | Create `UseCaseRegistry` class                                           | ⬜      |
| 2.3  | Implement `ReflectiveUseCaseProvider` (default fallback)                 | ⬜      |
| 2.4  | Implement `FactoryUseCaseProvider` (manual registration)                 | ⬜      |
| 2.5  | Add `PUnit.setUseCaseRegistry()` / `getUseCaseRegistry()`                | ⬜      |
| 2.6  | Create `UseCaseRegistryExtension` for per-class override                 | ⬜      |
| 2.7  | Create `@CovariateSource` annotation                                     | ⬜      |
| 2.8  | Update `CovariateProfileResolver` to call `@CovariateSource` methods     | ⬜      |
| 2.9  | Update `ExperimentExtension` to resolve use case via registry            | ⬜      |
| 2.10 | Update `ProbabilisticTestExtension` to resolve use case via registry     | ⬜      |
| 2.11 | Implement use case injection as test method parameter                    | ⬜      |
| 2.12 | Tests for registry provider chain                                        | ⬜      |
| 2.13 | Tests for `@CovariateSource` method discovery and invocation             | ⬜      |
| 2.14 | Tests for resolution hierarchy (instance → sys prop → env var → default) | ⬜      |

### Acceptance Criteria (Phase 2)

- [ ] `UseCaseProvider` interface implemented
- [ ] `UseCaseRegistry` implemented with provider chain
- [ ] `ReflectiveUseCaseProvider` implemented (default fallback)
- [ ] `FactoryUseCaseProvider` implemented (manual registration)
- [ ] `PUnit.setUseCaseRegistry()` / `getUseCaseRegistry()` static methods
- [ ] `@CovariateSource` annotation implemented
- [ ] Instance methods are discovered and invoked for covariate resolution
- [ ] System property fallback works: `org.javai.punit.covariate.<key>`
- [ ] Environment variable fallback works: `ORG_JAVAI_PUNIT_COVARIATE_<KEY>`
- [ ] Use case instance injected as test method parameter

---

## Phase 3: File Naming & Reporting

**Goal:** Implement the new baseline filename format and statistical reporting language.

### Tasks

| #    | Task                                                                  | Status |
|------|-----------------------------------------------------------------------|--------|
| 3.1  | Update `BaselineFileNamer` to include experiment method name          | ⬜      |
| 3.2  | Update `BaselineFileNamer` to include timestamp (YYYYMMDD-HHMM)       | ⬜      |
| 3.3  | Update `BaselineFileNamer` to exclude INFORMATIONAL from hash         | ⬜      |
| 3.4  | Update `ExperimentExtension` to pass experiment method name           | ⬜      |
| 3.5  | Implement file-based matching (filename scan, no YAML parse)          | ⬜      |
| 3.6  | Implement statistical reporting language (Section 7 of DES-COV2)      | ⬜      |
| 3.7  | Update `CovariateWarningRenderer` with category-specific phrasing     | ⬜      |
| 3.8  | Update `ConsoleExplanationRenderer` for covariate conformance section | ⬜      |
| 3.9  | Tests for filename format                                             | ⬜      |
| 3.10 | Tests for INFORMATIONAL exclusion from hash                           | ⬜      |
| 3.11 | Tests for overwrite behavior (same filename)                          | ⬜      |
| 3.12 | Tests for statistical report language                                 | ⬜      |

### Acceptance Criteria (Phase 3)

- [ ] Filename format: `<UseCaseId>.<MethodName>-<YYYYMMDD-HHMM>-<FootprintHash>-<CovHashes>.yaml`
- [ ] INFORMATIONAL covariates excluded from filename hash
- [ ] Same filename → overwrite (newer baseline replaces older)
- [ ] Multiple experiment methods produce distinct filenames
- [ ] File-based matching scans filenames without opening YAML
- [ ] Statistical reports use category-sensitive, neutral language
- [ ] Full match reports "equivalent conditions"
- [ ] Partial match reports "for evaluation" without prejudging

---

## Phase 4: Shopping Example Update

**Goal:** Update the existing shopping experiment and probabilistic test examples to demonstrate `UseCaseRegistry` integration.

### Tasks

| #    | Task                                                                       | Status |
|------|----------------------------------------------------------------------------|--------|
| 4.1  | Update `ShoppingUseCase` with `@CovariateSource` methods                   | ⬜      |
| 4.2  | Add `CovariateCategory.CONFIGURATION` covariate for LLM model              | ⬜      |
| 4.3  | Create `ShoppingUseCaseProvider` (factory-based)                           | ⬜      |
| 4.4  | Update `ShoppingExperiment` to register provider with `UseCaseRegistry`    | ⬜      |
| 4.5  | Update `ShoppingExperiment` to receive use case as method parameter        | ⬜      |
| 4.6  | Update probabilistic test to use same registry pattern                     | ⬜      |
| 4.7  | Verify baseline filename includes experiment method name                   | ⬜      |
| 4.8  | Verify covariate conformance report displays correctly                     | ⬜      |
| 4.9  | Run full experiment → test cycle to validate end-to-end                    | ⬜      |
| 4.10 | Update shopping example documentation in USER-GUIDE.md                     | ⬜      |

### Acceptance Criteria (Phase 4)

- [ ] `ShoppingUseCase` has `@CovariateSource` methods for custom covariates
- [ ] `ShoppingExperiment` uses `UseCaseRegistry` to obtain use case instance
- [ ] Use case is injected as experiment method parameter
- [ ] Probabilistic test uses same pattern
- [ ] Baseline files use new naming format with experiment method name
- [ ] End-to-end cycle (MEASURE → test) works correctly
- [ ] USER-GUIDE.md updated with registry usage example

---

## Dependencies

```
Phase 1 ──────────────────────────────────────────────────▶ Phase 2
  │                                                            │
  │ (categories must exist                                     │
  │  before @CovariateSource                                   │
  │  can use them)                                             │
  │                                                            │
  └──────────────────────▶ Phase 3 ◀───────────────────────────┘
                              │
                              │ (file naming needs categories
                              │  for INFORMATIONAL exclusion;
                              │  reporting needs use case instance
                              │  for covariate values)
                              │
                              ▼
                          Phase 4
                              │
                              │ (example update requires all
                              │  infrastructure to be in place)
```

---

## Notes

- **Temporal tolerance:** ±30 minutes from window edge for `PARTIALLY_CONFORMS`
- **No DI dependency:** PUnit core has no Spring/Guice dependency
- **Framework guides:** See `docs/USERGUIDE-SPRING.md` and `docs/USERGUIDE-GUICE.md`

---

## Changelog

| Date       | Change               |
|------------|----------------------|
| 2026-01-14 | Initial plan created |


