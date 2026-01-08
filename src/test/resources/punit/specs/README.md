# Execution Specifications

This directory contains approved execution specifications derived from empirical baselines.

## What is a Specification?

A specification is a **human-approved contract** that defines:

- **Baseline data**: Raw empirical observations (samples, successes) from experiments
- **Approval metadata**: Who approved, when, and why
- **Requirements**: Minimum pass rate, success criteria
- **Cost envelope**: Resource limits (time, tokens)

## File Format

Specifications are YAML files with the following structure:

```yaml
specId: usecase.shopping.search:v1
useCaseId: usecase.shopping.search
version: 1

# Approval metadata (required for validity)
approvedAt: 2026-01-08T15:30:00Z
approvedBy: mike.mannion
approvalNotes: "Reviewed baseline from 1000-sample experiment"

# Source baseline(s)
sourceBaselines:
  - shopping-search-realistic-v1

# Raw baseline data for threshold derivation
baselineData:
  samples: 1000
  successes: 951
  generatedAt: 2026-01-08T14:00:00Z

# Requirements
requirements:
  minPassRate: 0.95
  successCriteria: "isValidJson == true"

# Cost limits
costEnvelope:
  maxTimePerSampleMs: 100
  maxTokensPerSample: 500
  totalTokenBudget: 15000
```

## Using Specifications in Tests

Reference a spec in your `@ProbabilisticTest` annotation:

```java
@ProbabilisticTest(
    spec = "usecase.shopping.search:v1",
    samples = 30,
    confidence = 0.95
)
void shouldReturnValidJson() {
    // Test implementation
}
```

## Lifecycle

1. **Experiment** → Generates baseline in `build/punit/baselines/`
2. **Promote** → Moves to `punit/pending-approval/` for review
3. **Approve** → Creates spec here in `src/test/resources/punit/specs/`
4. **Test** → References spec for regression detection

