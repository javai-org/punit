# PLAN-COV: Implementation Plan for Covariate Support

This document provides a phased implementation plan for DES-COV (Covariate Support).

---

## Overview

| Aspect           | Value                              |
|------------------|------------------------------------|
| Estimated effort | 8-12 days                          |
| Risk level       | Medium (touches core abstractions) |
| Dependencies     | None (can proceed independently)   |
| Prerequisite     | DES-COV approval                   |

---

## Phase 1: Foundation — Data Model and API

**Goal**: Establish the core data structures and API surface.

**Duration**: 2-3 days

### Task 1.1: Create StandardCovariate Enum

**File**: `src/main/java/org/javai/punit/api/StandardCovariate.java`

```
- Create enum with WEEKDAY_VERSUS_WEEKEND, TIME_OF_DAY, TIMEZONE
- Add key() method returning stable string key
- Add comprehensive Javadoc with resolution and matching descriptions
```

**Tests**: `StandardCovariateTest.java`
- Verify key() returns expected values
- Verify enum values are stable (for serialization compatibility)

### Task 1.2: Extend @UseCase Annotation

**File**: `src/main/java/org/javai/punit/api/UseCase.java`

```
- Add covariates() attribute (StandardCovariate[] default {})
- Add customCovariates() attribute (String[] default {})
- Add Javadoc explaining declaration order significance
```

**Tests**: None (annotation-only change, tested via integration)

### Task 1.3: Create CovariateValue Sealed Interface

**File**: `src/main/java/org/javai/punit/model/CovariateValue.java`

```
- Create sealed interface with toCanonicalString() method
- Create StringValue record
- Create TimeWindowValue record (LocalTime start, LocalTime end, ZoneId timezone)
```

**Tests**: `CovariateValueTest.java`
- StringValue.toCanonicalString() returns value
- TimeWindowValue.toCanonicalString() formats correctly
- Equality and hashCode work correctly

### Task 1.4: Create CovariateProfile Class

**File**: `src/main/java/org/javai/punit/model/CovariateProfile.java`

```
- Immutable class with ordered key-value storage
- NOT_SET constant
- computeHash() method (SHA-256, truncated)
- computeValueHashes() method
- Builder pattern
```

**Tests**: `CovariateProfileTest.java`
- Hash is stable across JVM restarts
- Hash changes when values change
- Ordering is preserved
- NOT_SET constant is accessible

### Task 1.5: Create CovariateDeclaration Record

**File**: `src/main/java/org/javai/punit/model/CovariateDeclaration.java`

```
- Record with standardCovariates and customCovariates lists
- allKeys() method returning combined, ordered list
- computeDeclarationHash() method
- isEmpty() method
```

**Tests**: `CovariateDeclarationTest.java`
- allKeys() returns standard then custom, in order
- Hash is stable
- isEmpty() returns true when both lists empty

---

## Phase 2: Resolution Infrastructure

**Goal**: Build the covariate resolution system.

**Duration**: 2 days

### Task 2.1: Create CovariateResolutionContext Interface

**File**: `src/main/java/org/javai/punit/engine/covariate/CovariateResolutionContext.java`

```
- now() method
- experimentStartTime() / experimentEndTime() methods
- systemTimezone() method
- getSystemProperty() / getEnvironmentVariable() / getPunitEnvironment() methods
```

**Tests**: None (interface only)

### Task 2.2: Create DefaultCovariateResolutionContext

**File**: `src/main/java/org/javai/punit/engine/covariate/DefaultCovariateResolutionContext.java`

```
- Implement interface with real system access
- Support injected experiment timing
- Support injected PUnit environment map
```

**Tests**: `DefaultCovariateResolutionContextTest.java`
- Returns current time
- Returns system properties
- Returns environment variables
- Returns injected experiment timing

### Task 2.3: Create CovariateResolver Interface

**File**: `src/main/java/org/javai/punit/engine/covariate/CovariateResolver.java`

```
- Single resolve(CovariateResolutionContext) method
```

**Tests**: None (interface only)

### Task 2.4: Create Standard Covariate Resolvers

**Files**:
- `src/main/java/org/javai/punit/engine/covariate/WeekdayVsWeekendResolver.java`
- `src/main/java/org/javai/punit/engine/covariate/TimeOfDayResolver.java`
- `src/main/java/org/javai/punit/engine/covariate/TimezoneResolver.java`
- `src/main/java/org/javai/punit/engine/covariate/CustomCovariateResolver.java`

**Tests**: `*ResolverTest.java` for each
- WeekdayVsWeekend: Monday→Mo-Fr, Saturday→Sa-So
- TimeOfDay: Returns window from context timing
- Timezone: Returns system timezone
- Custom: Checks sys prop, env var, punit env in order; returns NOT_SET if missing

### Task 2.5: Create CovariateResolverRegistry

**File**: `src/main/java/org/javai/punit/engine/covariate/CovariateResolverRegistry.java`

```
- Maps covariate keys to resolvers
- Registers standard resolvers automatically
- Supports custom resolver registration
- getResolver(String key) method
```

**Tests**: `CovariateResolverRegistryTest.java`
- Returns correct resolver for standard covariates
- Returns CustomCovariateResolver for unknown keys
- Custom registrations override defaults

---

## Phase 3: Matching Infrastructure

**Goal**: Build the covariate matching system.

**Duration**: 1-2 days

### Task 3.1: Create CovariateMatcher Interface

**File**: `src/main/java/org/javai/punit/engine/covariate/CovariateMatcher.java`

```
- match(CovariateValue baseline, CovariateValue test) method
- MatchResult enum: CONFORMS, PARTIALLY_CONFORMS, DOES_NOT_CONFORM
```

**Tests**: None (interface only)

### Task 3.2: Create Standard Covariate Matchers

**Files**:
- `src/main/java/org/javai/punit/engine/covariate/WeekdayVsWeekendMatcher.java`
- `src/main/java/org/javai/punit/engine/covariate/TimeOfDayMatcher.java`
- `src/main/java/org/javai/punit/engine/covariate/ExactStringMatcher.java`

**Tests**: `*MatcherTest.java` for each
- WeekdayVsWeekend: Mo-Fr matches Mo-Fr, Mo-Fr doesn't match Sa-So
- TimeOfDay: Time within window matches, outside doesn't
- ExactString: Exact match works, NOT_SET never matches

### Task 3.3: Create CovariateMatcherRegistry

**File**: `src/main/java/org/javai/punit/engine/covariate/CovariateMatcherRegistry.java`

```
- Maps covariate keys to matchers
- Registers standard matchers automatically
- getMatcher(String key) method
```

**Tests**: `CovariateMatcherRegistryTest.java`
- Returns correct matcher for standard covariates
- Returns ExactStringMatcher for custom covariates

---

## Phase 4: Footprint and Baseline Selection

**Goal**: Implement footprint computation and baseline selection.

**Duration**: 2 days

### Task 4.1: Create FootprintComputer

**File**: `src/main/java/org/javai/punit/engine/covariate/FootprintComputer.java`

```
- computeFootprint(useCaseId, factors, covariateDeclaration) method
- Stable hash generation (SHA-256, truncated to 8 chars)
- Factors sorted for stability
- Covariate names in declaration order
```

**Tests**: `FootprintComputerTest.java`
- Same inputs produce same hash
- Different use case IDs produce different hashes
- Different factors produce different hashes
- Different covariate declarations produce different hashes
- Factor order doesn't matter (sorted internally)
- Covariate order matters

### Task 4.2: Create BaselineFileNamer

**File**: `src/main/java/org/javai/punit/engine/covariate/BaselineFileNamer.java`

```
- generateFilename(useCaseName, footprintHash, covariateProfile) method
- parse(filename) method returning ParsedFilename record
- Sanitization of use case name
- Hash truncation to 4 chars
```

**Tests**: `BaselineFileNamerTest.java`
- Generates expected filename format
- Parses generated filenames correctly
- Sanitizes special characters
- Handles empty covariate profiles

### Task 4.3: Create BaselineCandidate and SelectionResult Records

**File**: `src/main/java/org/javai/punit/engine/covariate/BaselineSelectionTypes.java`

```
- BaselineCandidate record
- SelectionResult record with factory methods
- ConformanceDetail record
- ScoredCandidate internal record
- CovariateScore internal record
```

**Tests**: None (data classes, tested via BaselineSelector)

### Task 4.4: Create BaselineSelector

**File**: `src/main/java/org/javai/punit/engine/covariate/BaselineSelector.java`

```
- select(candidates, testProfile) method
- Scoring algorithm (match count, declaration order, recency)
- Ambiguity detection
- Empty candidates handling
```

**Tests**: `BaselineSelectorTest.java`
- Selects best match by covariate conformance
- Prioritizes earlier covariates in ties
- Detects ambiguous selections
- Returns noMatch for empty candidates
- Uses recency as final tie-breaker

---

## Phase 5: Spec Integration

**Goal**: Extend ExecutionSpecification and YAML handling.

**Duration**: 1-2 days

### Task 5.1: Extend ExecutionSpecification

**File**: `src/main/java/org/javai/punit/spec/model/ExecutionSpecification.java`

```
- Add covariateProfile field
- Add footprint field
- Add getCovariateProfile() and getFootprint() methods
- Extend Builder with covariateProfile() and footprint() methods
```

**Tests**: Update `ExecutionSpecificationTest.java`
- Builder sets covariate profile correctly
- Builder sets footprint correctly
- Null handling for backward compatibility

### Task 5.2: Extend SpecificationSerializer

**File**: `src/main/java/org/javai/punit/spec/registry/SpecificationSerializer.java` (or create new)

```
- Add writeCovariates(yaml, profile) method
- Add writeFootprint(yaml, footprint) method
- Convert CovariateProfile to YAML map
```

**Tests**: `SpecificationSerializerTest.java`
- Serializes covariate profile correctly
- Serializes footprint correctly
- Handles empty profiles

### Task 5.3: Extend SpecificationLoader

**File**: `src/main/java/org/javai/punit/spec/registry/SpecificationLoader.java`

```
- Add loadCovariates(yaml) method
- Add loadFootprint(yaml) method
- Parse covariate values back to CovariateProfile
```

**Tests**: `SpecificationLoaderTest.java`
- Loads covariate profile correctly
- Loads footprint correctly
- Handles missing sections (backward compatibility)

### Task 5.4: Update Schema Version

```
- Bump schema to punit-spec-3
- Document new fields in schema documentation
```

---

## Phase 6: Experiment Extension Integration

**Goal**: Integrate covariate capture into MEASURE experiments.

**Duration**: 1-2 days

### Task 6.1: Extract CovariateDeclaration from UseCase

**File**: `src/main/java/org/javai/punit/experiment/engine/UseCaseCovariateExtractor.java`

```
- extractDeclaration(Class<?> useCaseClass) method
- Read @UseCase annotation
- Build CovariateDeclaration from covariates and customCovariates
```

**Tests**: `UseCaseCovariateExtractorTest.java`
- Extracts standard covariates
- Extracts custom covariates
- Returns empty declaration for use cases without covariates

### Task 6.2: Add Experiment Timing Tracking

**File**: `src/main/java/org/javai/punit/experiment/engine/ExperimentResultsAggregator.java` (or similar)

```
- Track experiment start time
- Track last sample completion time (end time)
- Expose getStartTime() and getEndTime()
```

**Tests**: Update existing tests
- Start time is captured
- End time updates with each sample

### Task 6.3: Integrate into ExperimentExtension

**File**: `src/main/java/org/javai/punit/experiment/engine/ExperimentExtension.java`

```
- In MEASURE mode completion:
  1. Extract CovariateDeclaration
  2. Create resolution context with experiment timing
  3. Resolve all covariates
  4. Compute footprint
  5. Generate filename
  6. Store in ExecutionSpecification
```

**Tests**: `ExperimentExtensionCovariateTest.java` (integration)
- MEASURE captures covariate profile
- MEASURE generates correct filename
- MEASURE stores footprint in spec

---

## Phase 7: Probabilistic Test Extension Integration

**Goal**: Integrate covariate matching into probabilistic tests.

**Duration**: 2 days

### Task 7.1: Create BaselineRepository

**File**: `src/main/java/org/javai/punit/engine/covariate/BaselineRepository.java`

```
- findCandidates(useCaseId, footprint) method
- Scan spec directory for matching files
- Load and filter by footprint
- Return list of BaselineCandidate
```

**Tests**: `BaselineRepositoryTest.java`
- Finds baselines with matching footprint
- Excludes baselines with different footprint
- Returns empty list when no matches

### Task 7.2: Create CovariateProfileResolver

**File**: `src/main/java/org/javai/punit/engine/covariate/CovariateProfileResolver.java`

```
- resolve(CovariateDeclaration, CovariateResolutionContext) method
- Iterate declared covariates
- Resolve each using appropriate resolver
- Build and return CovariateProfile
```

**Tests**: `CovariateProfileResolverTest.java`
- Resolves all declared covariates
- Preserves declaration order
- Handles resolution failures gracefully

### Task 7.3: Create NoCompatibleBaselineException

**File**: `src/main/java/org/javai/punit/engine/covariate/NoCompatibleBaselineException.java`

```
- Extend PunitConfigurationException
- Include useCaseId, expectedFootprint, availableFootprints
- Clear error message with remediation guidance
```

**Tests**: None (exception class)

### Task 7.4: Integrate into ProbabilisticTestExtension

**File**: `src/main/java/org/javai/punit/ptest/engine/ProbabilisticTestExtension.java`

```
- When use case is specified:
  1. Extract CovariateDeclaration
  2. Compute test's footprint
  3. Find candidate baselines
  4. If none: throw NoCompatibleBaselineException
  5. Resolve test's current profile
  6. Select best baseline
  7. Store selection result for reporting
```

**Tests**: `ProbabilisticTestExtensionCovariateTest.java` (integration)
- Selects correct baseline
- Throws on footprint mismatch
- Records selection for reporting

---

## Phase 8: Reporting Integration

**Goal**: Add covariate information to test reports.

**Duration**: 1 day

### Task 8.1: Create BaselineProvenance Record

**File**: `src/main/java/org/javai/punit/model/BaselineProvenance.java`

```
- Record with filename, footprint, generatedAt, samples, covariateProfile
- toHumanReadable() method
- toReportProperties() method
```

**Tests**: `BaselineProvenanceTest.java`
- Formats human-readable output correctly
- Generates expected report properties

### Task 8.2: Create CovariateWarningRenderer

**File**: `src/main/java/org/javai/punit/engine/covariate/CovariateWarningRenderer.java`

```
- render(List<ConformanceDetail> nonConforming, boolean ambiguous) method
- Format non-conforming covariates with baseline vs test values
- Add ambiguity warning if applicable
```

**Tests**: `CovariateWarningRendererTest.java`
- Renders single non-conformance
- Renders multiple non-conformances
- Renders ambiguity warning
- Returns empty for full conformance

### Task 8.3: Integrate into Report Builder

**File**: `src/main/java/org/javai/punit/ptest/engine/ProbabilisticTestReportBuilder.java` (or similar)

```
- Add baseline provenance to verdict
- Add covariate warnings when non-conformance exists
- Publish machine-readable properties via TestReporter
```

**Tests**: Integration tests
- Verdict includes baseline provenance
- Non-conformance warning appears
- JUnit report properties are published

### Task 8.4: Update Transparent Statistics Output

**File**: Update `StatisticalExplanationBuilder.java` and related

```
- Add BASELINE REFERENCE section with covariate profile
- Include conformance status
- Display warnings in appropriate section
```

**Tests**: Update existing transparent stats tests

---

## Phase 9: Testing and Documentation

**Goal**: Comprehensive testing and documentation updates.

**Duration**: 1 day

### Task 9.1: End-to-End Integration Tests

**File**: `src/test/java/org/javai/punit/integration/CovariateIntegrationTest.java`

```
- Full MEASURE → TEST flow with covariates
- Test covariate matching scenarios
- Test non-conformance warning generation
- Test footprint mismatch handling
- Test multiple baseline selection
```

### Task 9.2: Update User Guide

**File**: `docs/USER-GUIDE.md`

```
- Add "Covariates" section
- Document standard covariates
- Document custom covariates
- Provide usage examples
- Explain matching and warnings
```

### Task 9.3: Update Glossary

**File**: `docs/GLOSSARY.md`

```
- Add covariate definition
- Add footprint definition
- Add covariate profile definition
- Add conformance definition
```

---

## Dependency Graph

```
Phase 1 (Foundation)
    ├── Task 1.1: StandardCovariate
    ├── Task 1.2: @UseCase extension
    ├── Task 1.3: CovariateValue
    ├── Task 1.4: CovariateProfile (depends on 1.3)
    └── Task 1.5: CovariateDeclaration (depends on 1.1)

Phase 2 (Resolution) — depends on Phase 1
    ├── Task 2.1: CovariateResolutionContext
    ├── Task 2.2: DefaultCovariateResolutionContext (depends on 2.1)
    ├── Task 2.3: CovariateResolver
    ├── Task 2.4: Standard Resolvers (depends on 2.1, 2.3, 1.3)
    └── Task 2.5: CovariateResolverRegistry (depends on 2.4)

Phase 3 (Matching) — depends on Phase 1
    ├── Task 3.1: CovariateMatcher
    ├── Task 3.2: Standard Matchers (depends on 3.1, 1.3)
    └── Task 3.3: CovariateMatcherRegistry (depends on 3.2)

Phase 4 (Selection) — depends on Phases 2, 3
    ├── Task 4.1: FootprintComputer (depends on 1.5)
    ├── Task 4.2: BaselineFileNamer (depends on 1.4)
    ├── Task 4.3: Selection Types (depends on 1.4)
    └── Task 4.4: BaselineSelector (depends on 3.3, 4.3)

Phase 5 (Spec) — depends on Phase 1
    ├── Task 5.1: ExecutionSpecification extension
    ├── Task 5.2: Serializer (depends on 5.1, 1.4)
    ├── Task 5.3: Loader (depends on 5.1, 1.4)
    └── Task 5.4: Schema version

Phase 6 (Experiment) — depends on Phases 2, 4, 5
    ├── Task 6.1: UseCaseCovariateExtractor (depends on 1.2, 1.5)
    ├── Task 6.2: Timing tracking
    └── Task 6.3: ExperimentExtension integration

Phase 7 (Test) — depends on Phases 4, 5, 6
    ├── Task 7.1: BaselineRepository
    ├── Task 7.2: CovariateProfileResolver (depends on 2.5)
    ├── Task 7.3: NoCompatibleBaselineException
    └── Task 7.4: ProbabilisticTestExtension integration

Phase 8 (Reporting) — depends on Phase 7
    ├── Task 8.1: BaselineProvenance
    ├── Task 8.2: CovariateWarningRenderer
    ├── Task 8.3: Report builder integration
    └── Task 8.4: Transparent stats update

Phase 9 (Testing/Docs) — depends on all phases
    ├── Task 9.1: Integration tests
    ├── Task 9.2: User guide
    └── Task 9.3: Glossary
```

---

## Risk Mitigation

| Risk                             | Mitigation                                        |
|----------------------------------|---------------------------------------------------|
| Footprint hash instability       | Extensive unit tests, document hash algorithm     |
| Backward compatibility           | Empty covariates produce same footprint as before |
| Performance (scanning baselines) | Cache baseline list, index by use case ID         |
| Complex matching logic           | Keep algorithm simple, document clearly           |
| TIME_OF_DAY spanning midnight    | Document limitation, consider future enhancement  |

---

## Success Criteria

- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] MEASURE experiments capture covariate profiles
- [ ] Probabilistic tests select correct baselines
- [ ] Non-conformance warnings display correctly
- [ ] Footprint mismatch produces clear error
- [ ] Documentation is complete
- [ ] Backward compatibility verified with existing specs

