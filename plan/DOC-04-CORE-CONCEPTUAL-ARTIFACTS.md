# Core Conceptual Artifacts

This section defines the fundamental abstractions in the punit experiment extension.

## 3.1 Use Case (Test/Experiment Harness)

A **use case** is the fundamental unit of observation and testing. It is:

- A **function** that invokes production code
- Identified by a **use case ID** (string identifier, e.g., `usecase.email.validation`)
- Returns a `UseCaseResult` containing observed outcomes
- **Never** called by production code
- Defined in test/experiment space only

### 3.1.1 Use Case Representation

Use cases are represented as annotated methods:

```java
@UseCase("usecase.email.validation")
UseCaseResult validateEmailFormat(String email, UseCaseContext context) {
    // Invoke production code
    ValidationResult result = emailValidator.validate(email);
    
    // Capture observations (not assertions)
    return UseCaseResult.builder()
        .value("isValid", result.isValid())
        .value("errorCode", result.getErrorCode())
        .value("processingTimeMs", result.getProcessingTimeMs())
        .build();
}
```

**Key Design Decisions**:

1. **Use cases are methods, not classes**: Keeps them lightweight and composable.
2. **Use case ID is declared via annotation**: The ID is metadata, not part of the method signature.
3. **UseCaseContext is injected**: Context provides backend-specific configuration.
4. **Return type is always UseCaseResult**: Enforces the pattern that use cases produce observations, not verdicts.

### 3.1.2 Use Case Discovery

The framework discovers use cases via:
1. Classpath scanning for `@UseCase`-annotated methods
2. Explicit registration in test configuration
3. Reference from `@Experiment` or `@ProbabilisticTest(spec=...)` annotations

## 3.2 UseCaseResult

`UseCaseResult` is a neutral container for observed outcomes with:
- `Map<String, Object> values` - primary outputs
- `Map<String, Object> metadata` - contextual information
- `Instant timestamp` and `Duration executionTime`

**Design Principles**:
1. **Neutral and descriptive**: Contains key-value data, not judgments
2. **Flexible schema**: `Map<String, Object>` allows domain-specific values
3. **Immutable**: Once constructed, results cannot be modified
4. **Separates values from metadata**

## 3.3 Experiment

An experiment repeatedly executes a use case in **exploratory mode** to gather empirical data.

### Experiment Modes

1. **Single-config experiments**: One `ExperimentConfig`, one baseline output
2. **Multi-config experiments**: Multiple `ExperimentConfig`s, one baseline per config
3. **Adaptive experiments**: Configs discovered incrementally via feedback-driven refinement

### Experiment Vocabulary

| Term                       | Definition                                                                     |
|----------------------------|--------------------------------------------------------------------------------|
| **ExperimentDesign**       | Declarative description of what is explored (factors + levels)                 |
| **ExperimentFactor**       | One independently varied dimension (e.g., `model`, `temperature`)              |
| **ExperimentLevel**        | One setting of a factor (categorical or numeric)                               |
| **StaticFactor**           | Factor with levels enumerated up front                                         |
| **AdaptiveFactor**         | Factor with levels generated dynamically through iterative refinement          |
| **ExperimentConfig**       | One concrete combination of levels—the unit of execution                       |
| **ExperimentGoal**         | Optional criteria for early termination                                        |
| **RefinementStrategy**     | SPI for generating refined levels in adaptive experiments                      |

### Key Characteristics

- **No pass/fail**: Experiments never fail (except for infrastructure errors)
- **Produces empirical baseline**: One baseline file per `ExperimentConfig`
- **Never gates CI**: Experiment results are informational only

*For complete details on multi-config experiments, adaptive experiments, and baseline structure, see the full plan document.*

## 3.4 Empirical Baseline

The machine-readable output of an experiment containing:
- Use case ID and generation timestamp
- Execution context (backend-specific parameters)
- Statistical observations (success rate, variance, failure distribution)
- Cost metrics (tokens consumed, time elapsed)
- Sample size and confidence metadata

**Properties**: Immutable, Auditable, Descriptive (not normative), YAML by default

## 3.5 Execution Specification

A **human-reviewed and approved contract** derived from empirical baselines:

```yaml
specId: usecase.json.generation:v3
useCaseId: usecase.json.generation
version: 3

approvedAt: 2026-01-04T16:00:00Z
approvedBy: jane.engineer@example.com
approvalNotes: >
  Baseline shows 93.5% success rate. Setting threshold at 90%.

sourceBaselines:
  - usecase.json.generation/2026-01-04T15:30:00Z.yaml

executionContext:
  backend: llm
  model: gpt-4
  temperature: 0.7

requirements:
  minPassRate: 0.90
  successCriteria: "isValidJson == true && hasRequiredFields == true"
```

**Properties**: Normative, Versioned, Traceable, Approved, YAML by default

## 3.6 Probabilistic Conformance Test

A conformance test validates that system behavior matches an approved specification:

```java
@ProbabilisticTest(spec = "usecase.json.generation:v3")
void jsonGenerationMeetsSpec() {
    // Framework loads spec, resolves use case, runs samples,
    // evaluates against successCriteria, determines pass/fail
}
```

**Key Design Decisions**:
1. Spec reference is preferred over inline thresholds
2. Inline thresholds remain supported for backward compatibility
3. Specs override inline parameters (with warning)

---

*Previous: [Architecture Overview](./DOC-03-ARCHITECTURE-OVERVIEW.md)*

*Next: [Annotation & API Design](./DOC-05-ANNOTATION-API-DESIGN.md)*

*[Back to Table of Contents](./DOC-00-TOC.md)*
