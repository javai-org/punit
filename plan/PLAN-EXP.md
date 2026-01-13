# PLAN-EXP: Implementation Plan for Baseline Expiration

This document provides a phased implementation plan for DES-EXP (Baseline Expiration).

---

## Overview

| Aspect           | Value                                        |
|------------------|----------------------------------------------|
| Estimated effort | 4-5 days                                     |
| Risk level       | Low (isolated feature, minimal core changes) |
| Dependencies     | Can be implemented before or after PLAN-COV  |
| Prerequisite     | DES-EXP approval                             |

---

## Phase 1: Foundation — Data Model and API

**Goal**: Establish the core data structures and API surface.

**Duration**: 1 day

### Task 1.1: Extend @Experiment Annotation

**File**: `src/main/java/org/javai/punit/api/Experiment.java`

```
- Add expiresInDays() attribute (int default 0)
- Add comprehensive Javadoc with typical values guidance
- Document that 0 means no expiration
```

**Tests**: None (annotation-only change, tested via integration)

### Task 1.2: Create ExpirationPolicy Record

**File**: `src/main/java/org/javai/punit/model/ExpirationPolicy.java`

```
- Record with expiresInDays (int) and baselineEndTime (Instant)
- NO_EXPIRATION constant (0)
- hasExpiration() method
- expirationTime() method returning Optional<Instant>
- evaluateAt(Instant) method returning ExpirationStatus
```

**Tests**: `ExpirationPolicyTest.java`
- hasExpiration() returns false for 0
- hasExpiration() returns true for positive values
- expirationTime() returns empty for 0
- expirationTime() computes correctly for positive values
- evaluateAt() returns correct status for various times

### Task 1.3: Create ExpirationStatus Sealed Interface

**File**: `src/main/java/org/javai/punit/model/ExpirationStatus.java`

```
- Sealed interface with permits for all status types
- NoExpiration record
- Valid record (Duration remaining)
- ExpiringSoon record (Duration remaining, double remainingPercent)
- ExpiringImminently record (Duration remaining, double remainingPercent)
- Expired record (Duration expiredAgo)
- Factory methods: noExpiration(), valid(), expiringSoon(), expiringImminently(), expired()
- Default methods: requiresWarning(), isExpired()
```

**Tests**: `ExpirationStatusTest.java`
- Each status type is correctly identified
- requiresWarning() returns correct values
- isExpired() returns correct values

### Task 1.4: Create ExperimentAnnotationValidator

**File**: `src/main/java/org/javai/punit/experiment/engine/ExperimentAnnotationValidator.java`

```
- validate(Experiment annotation) method
- Check expiresInDays >= 0
- Throw PunitConfigurationException for negative values
```

**Tests**: `ExperimentAnnotationValidatorTest.java`
- Accepts 0
- Accepts positive values
- Rejects negative values with clear error

---

## Phase 2: Spec Integration

**Goal**: Extend ExecutionSpecification and YAML handling for expiration.

**Duration**: 1 day

### Task 2.1: Extend ExecutionSpecification

**File**: `src/main/java/org/javai/punit/spec/model/ExecutionSpecification.java`

```
- Add expirationPolicy field (ExpirationPolicy)
- Add getExpirationPolicy() method
- Add evaluateExpiration(Instant) convenience method
- Extend Builder with expirationPolicy(int, Instant) method
```

**Tests**: Update `ExecutionSpecificationTest.java`
- Builder sets expiration policy correctly
- evaluateExpiration() delegates to policy
- Null policy returns NoExpiration status

### Task 2.2: Extend SpecificationSerializer

**File**: `src/main/java/org/javai/punit/spec/registry/SpecificationSerializer.java` (or create)

```
- Add writeExpiration(yaml, policy) method
- Only write if hasExpiration() is true
- Include expiresInDays, baselineEndTime, computed expirationDate
```

**Tests**: `SpecificationSerializerExpirationTest.java`
- Writes expiration section for policies with expiration
- Omits section for no-expiration policies
- Formats timestamps in ISO-8601

### Task 2.3: Extend SpecificationLoader

**File**: `src/main/java/org/javai/punit/spec/registry/SpecificationLoader.java`

```
- Add loadExpiration(yaml, empiricalBasis) method
- Parse expiration section if present
- Fallback to empiricalBasis.generatedAt() if baselineEndTime missing
- Return null if section missing (backward compatibility)
```

**Tests**: `SpecificationLoaderExpirationTest.java`
- Loads expiration section correctly
- Falls back to generatedAt when baselineEndTime missing
- Returns null for specs without expiration
- Handles malformed expiration data gracefully

---

## Phase 3: Experiment Extension Integration

**Goal**: Capture expiration policy during MEASURE experiments.

**Duration**: 1 day

### Task 3.1: Add Experiment End Time Tracking

**File**: `src/main/java/org/javai/punit/experiment/engine/ExperimentResultsAggregator.java` (or similar)

```
- Ensure endTime is tracked (last sample completion)
- Expose getEndTime() method
- Initialize with current time, update on each sample
```

**Tests**: Update existing tests
- End time is captured correctly
- End time updates with last sample

### Task 3.2: Integrate Expiration into ExperimentExtension

**File**: `src/main/java/org/javai/punit/experiment/engine/ExperimentExtension.java`

```
- In MEASURE mode completion:
  1. Read expiresInDays from annotation
  2. Get experiment end time from results
  3. Create ExpirationPolicy
  4. Add to ExecutionSpecification
  5. If expiresInDays == 0, emit informational note
```

**Tests**: `ExperimentExtensionExpirationTest.java` (integration)
- MEASURE captures expiration policy
- Zero expiration triggers informational note
- Spec file contains expiration section

### Task 3.3: Emit Informational Note for No Expiration

```
- Use TestReporter to publish entry
- Key: "punit.info.expiration"
- Value: "Consider setting expiresInDays to track baseline freshness"
- Only emit once per experiment
```

**Tests**: Verify note is emitted (integration test)

---

## Phase 4: Warning Rendering

**Goal**: Create the warning rendering infrastructure.

**Duration**: 0.5 days

### Task 4.1: Create ExpirationWarningRenderer

**File**: `src/main/java/org/javai/punit/engine/expiration/ExpirationWarningRenderer.java`

```
- render(ExecutionSpecification spec, ExpirationStatus status) method
- renderExpired() with prominent box format
- renderExpiringImminently() with warning format
- renderExpiringSoon() with informational format
- formatDuration(Duration) helper
- formatInstant(Instant) helper
```

**Tests**: `ExpirationWarningRendererTest.java`
- Renders expired warning with correct format
- Renders imminent warning with correct format
- Renders soon warning with correct format
- Returns empty string for Valid/NoExpiration
- Duration formatting is human-readable

### Task 4.2: Create WarningLevel Enum

**File**: `src/main/java/org/javai/punit/engine/expiration/WarningLevel.java`

```
- ALWAYS (expired - always shown)
- NORMAL (imminent - shown at normal/verbose)
- VERBOSE (soon - only at verbose)
- getWarningLevel(ExpirationStatus) static method
```

**Tests**: `WarningLevelTest.java`
- Expired → ALWAYS
- ExpiringImminently → NORMAL
- ExpiringSoon → VERBOSE
- Valid/NoExpiration → null

---

## Phase 5: Probabilistic Test Extension Integration

**Goal**: Evaluate and report expiration during test execution.

**Duration**: 1 day

### Task 5.1: Create ExpirationEvaluator

**File**: `src/main/java/org/javai/punit/engine/expiration/ExpirationEvaluator.java`

```
- evaluate(ExecutionSpecification spec) method
- Uses Instant.now() for current time
- Returns ExpirationStatus
- Handles null policy gracefully
```

**Tests**: `ExpirationEvaluatorTest.java`
- Returns NoExpiration for null policy
- Returns correct status for various policies
- Uses current time correctly

### Task 5.2: Create ExpirationReportPublisher

**File**: `src/main/java/org/javai/punit/engine/expiration/ExpirationReportPublisher.java`

```
- publish(TestReporter, ExecutionSpecification, ExpirationStatus) method
- Publish machine-readable properties:
  - punit.baseline.expiresInDays
  - punit.baseline.endTime
  - punit.baseline.expirationDate
  - punit.baseline.expirationStatus
  - punit.baseline.expiredAgoDays (if expired)
```

**Tests**: `ExpirationReportPublisherTest.java`
- Publishes all properties for policies with expiration
- Publishes only status for no-expiration policies
- Formats values correctly

### Task 5.3: Integrate into ProbabilisticTestExtension

**File**: `src/main/java/org/javai/punit/ptest/engine/ProbabilisticTestExtension.java`

```
- After loading spec:
  1. Evaluate expiration status
  2. If requiresWarning():
     a. Render warning
     b. Add to verdict based on WarningLevel
  3. Publish report properties
```

**Tests**: `ProbabilisticTestExtensionExpirationTest.java` (integration)
- Expired baseline shows prominent warning
- Imminent expiration shows warning at normal verbosity
- Soon expiration shows warning at verbose only
- Properties are published correctly

### Task 5.4: Update Report Builder

**File**: `src/main/java/org/javai/punit/ptest/engine/ProbabilisticTestReportBuilder.java` (or similar)

```
- Add expiration warning to verdict output
- Respect WarningLevel for verbosity
- Expired warnings always shown (ignore verbosity)
```

**Tests**: Integration tests

---

## Phase 6: Integration with Covariates (Optional)

**Goal**: Combine expiration and covariate warnings when both apply.

**Duration**: 0.5 days

**Note**: This phase is optional and can be deferred if PLAN-COV is not yet implemented.

### Task 6.1: Create ValidityReport Record

**File**: `src/main/java/org/javai/punit/engine/ValidityReport.java`

```
- Record with expirationStatus and covariateConformance (if COV implemented)
- hasAnyWarnings() method
- renderWarnings() method combining both
```

**Tests**: `ValidityReportTest.java`
- Combines warnings correctly
- Orders expiration before covariate warnings

### Task 6.2: Create BaselineValidityEvaluator

**File**: `src/main/java/org/javai/punit/engine/BaselineValidityEvaluator.java`

```
- evaluate(spec, testProfile, currentTime) method
- Evaluate both expiration and covariates
- Return combined ValidityReport
```

**Tests**: `BaselineValidityEvaluatorTest.java`
- Evaluates both conditions
- Works when only one is applicable

---

## Phase 7: Testing and Documentation

**Goal**: Comprehensive testing and documentation updates.

**Duration**: 0.5 days

### Task 7.1: End-to-End Integration Tests

**File**: `src/test/java/org/javai/punit/integration/ExpirationIntegrationTest.java`

```
- MEASURE experiment captures expiration
- Test with valid baseline (no warning)
- Test with expiring-soon baseline
- Test with expiring-imminently baseline
- Test with expired baseline
- Verify warnings appear at correct verbosity levels
- Verify JUnit report properties
```

### Task 7.2: Update User Guide

**File**: `docs/USER-GUIDE.md`

```
- Add "Baseline Expiration" section
- Document expiresInDays attribute
- Provide guidance on typical values
- Explain warning levels
- Show example output
```

### Task 7.3: Update Glossary

**File**: `docs/GLOSSARY.md`

```
- Add expiration policy definition
- Add baseline staleness definition
```

---

## Dependency Graph

```
Phase 1 (Foundation)
    ├── Task 1.1: @Experiment extension
    ├── Task 1.2: ExpirationPolicy (depends on 1.3)
    ├── Task 1.3: ExpirationStatus
    └── Task 1.4: ExperimentAnnotationValidator

Phase 2 (Spec) — depends on Phase 1
    ├── Task 2.1: ExecutionSpecification extension (depends on 1.2)
    ├── Task 2.2: Serializer (depends on 2.1)
    └── Task 2.3: Loader (depends on 2.1)

Phase 3 (Experiment) — depends on Phase 2
    ├── Task 3.1: End time tracking
    ├── Task 3.2: ExperimentExtension integration (depends on 3.1, 2.1)
    └── Task 3.3: Informational note

Phase 4 (Rendering) — depends on Phase 1
    ├── Task 4.1: ExpirationWarningRenderer (depends on 1.3)
    └── Task 4.2: WarningLevel (depends on 1.3)

Phase 5 (Test) — depends on Phases 2, 4
    ├── Task 5.1: ExpirationEvaluator (depends on 1.2, 2.1)
    ├── Task 5.2: ExpirationReportPublisher (depends on 1.3)
    ├── Task 5.3: ProbabilisticTestExtension integration
    └── Task 5.4: Report builder update

Phase 6 (Integration) — optional, depends on Phase 5 + PLAN-COV
    ├── Task 6.1: ValidityReport
    └── Task 6.2: BaselineValidityEvaluator

Phase 7 (Testing/Docs) — depends on all phases
    ├── Task 7.1: Integration tests
    ├── Task 7.2: User guide
    └── Task 7.3: Glossary
```

---

## Parallel Execution with PLAN-COV

If implementing both PLAN-COV and PLAN-EXP:

| Phase | COV | EXP | Notes                              |
|-------|-----|-----|------------------------------------|
| 1     | ✓   | ✓   | Can be done in parallel            |
| 2     | ✓   | ✓   | Can be done in parallel            |
| 3     | ✓   | ✓   | Can be done in parallel            |
| 4     | ✓   | ✓   | Can be done in parallel            |
| 5     | ✓   | ✓   | Coordinate on spec extension       |
| 6     | —   | ✓   | EXP Phase 6 depends on COV Phase 7 |
| 7     | —   | ✓   | COV Phase 7 completes              |
| 8     | —   | ✓   | Combined testing                   |
| 9     | ✓   | —   |                                    |

**Recommended approach**:
1. Complete COV Phases 1-5 and EXP Phases 1-5 in parallel
2. Merge spec extension changes carefully
3. Complete COV Phases 6-8
4. Complete EXP Phase 6 (integration with COV)
5. Combined Phase 9 for both features

---

## Risk Mitigation

| Risk                              | Mitigation                                    |
|-----------------------------------|-----------------------------------------------|
| Timestamp precision               | Use Instant for all timestamps                |
| Timezone handling                 | Store in UTC, convert to local for display    |
| Proportional threshold edge cases | Extensive unit tests for boundary conditions  |
| Warning fatigue                   | Opt-in by default (0 = no expiration)         |
| CI noise from warnings            | Document that warnings don't affect pass/fail |

---

## Success Criteria

- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] @Experiment(expiresInDays) is recognized
- [ ] MEASURE experiments capture expiration policy
- [ ] Specs contain expiration section when configured
- [ ] Expiration is evaluated correctly at test time
- [ ] Warnings appear at correct verbosity levels
- [ ] Expired warnings always appear (regardless of verbosity)
- [ ] JUnit report properties are published
- [ ] Documentation is complete
- [ ] Backward compatibility verified (specs without expiration work)

---

## Estimated Timeline

| Week  | Activities                                              |
|-------|---------------------------------------------------------|
| Day 1 | Phase 1 (Foundation)                                    |
| Day 2 | Phase 2 (Spec) + Phase 3 (Experiment)                   |
| Day 3 | Phase 4 (Rendering) + Phase 5 (Test)                    |
| Day 4 | Phase 6 (Integration, if COV ready) + Phase 7 (Testing) |
| Day 5 | Buffer / polish / documentation                         |

