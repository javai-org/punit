# PUNIT Experiment Extension — Design & Implementation Plan

## Executive Summary

This document describes the planned extension to the **punit** framework that introduces **experiment support** and an **empirical specification flow**. The extension enables a disciplined workflow following the **canonical flow**:

```
Use Case → ExperimentDesign → ExperimentConfig → Empirical Baselines → Execution Specification → Probabilistic Conformance Tests
```

This progression moves from **discovery** (experiments) through **codification** (specifications) to **enforcement** (tests).

The extension is **domain-neutral**. While LLM-based systems are a motivating use case, the abstractions introduced here apply to any non-deterministic system: stochastic algorithms, distributed systems, sensor-based hardware, or any component exhibiting probabilistic behavior.

---

## 1. Design Principles (Invariants)

The following principles are **non-negotiable** and must be preserved throughout implementation:

### 1.1 Experiments Are First-Class and Domain-Neutral

- Experiments are **not** LLM-specific abstractions.
- The punit core must remain free of AI-specific concepts (prompts, models, tokens-as-LLM-tokens).
- LLM experimentation is an optional, pluggable backend—one of many possible experiment contexts.

### 1.2 Same Engine, Different Modes

- Experiments and probabilistic tests share the same execution, aggregation, budgeting, and reporting machinery.
- They differ in **intent** (exploratory vs. conformance) and **semantics** (data-only vs. pass/fail), not infrastructure.
- This reuse minimizes complexity, reduces bugs, and ensures consistent behavior.

### 1.3 Clear Semantic Separation

| Aspect | Experiment Mode | Probabilistic Test Mode |
|--------|-----------------|-------------------------|
| Intent | Exploratory, empirical | Conformance, gatekeeping |
| Produces | Empirical data | Binary pass/fail verdict |
| Gates CI? | **Never** | Yes |
| Assertions | Observations, not failures | Failures gate outcomes |
| Specification | None required | Spec-driven (preferred) or inline thresholds |

### 1.4 Discovery Precedes Specification Precedes Testing

The canonical flow is:

```
  Use Case
       ↓
  ExperimentDesign (Factors + Levels)
       ↓
  ExperimentConfig
       ↓
  Empirical Baselines
       ↓
  Execution Specification
       ↓
  Probabilistic Conformance Tests
```

This flow reflects the disciplined progression from **discovery** (experiments) through **codification** (specifications) to **enforcement** (tests). Each artifact builds on the previous:

| Stage | Artifact | Purpose |
|-------|----------|---------|
| Define | Use Case | The behavior to observe/test |
| Design | ExperimentDesign | What factors and levels to explore |
| Execute | ExperimentConfig | Concrete configuration to run |
| Record | Empirical Baselines | Observed behavior per config |
| Approve | Execution Specification | Human-approved contract |
| Enforce | Probabilistic Conformance Tests | CI-gated validation |

Hard-coded thresholds in `@ProbabilisticTest` remain supported but are explicitly a **transitional pattern**. The framework will encourage spec-driven testing and discourage arbitrary thresholds.

### 1.5 Production Code Must Remain Uncontaminated

Production application code must **never** depend on:
- Use case IDs
- Use case classes or functions
- Experiment APIs
- `UseCaseResult` or any experiment/test result types
- Specification references

All these abstractions exist strictly in **test/experiment space**. Production code is the *subject* of testing, not a participant in the test framework.

---

## 2. Architecture Overview

### 2.1 Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              Production Code                                     │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │  Application Services, Domain Logic, External Integrations              │    │
│  │  (No knowledge of punit, experiments, use cases, or specifications)     │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        │ (invoked by)
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         Test / Experiment Space                                  │
│                                                                                  │
│  ┌──────────────────────────────┐   ┌────────────────────────────────────────┐  │
│  │    Use Case Functions        │   │     Result Interpretation              │  │
│  │                              │   │                                        │  │
│  │  - Invoke production code    │──▶│  UseCaseResult                        │  │
│  │  - Capture observations      │   │  - Map<String, Object> values         │  │
│  │  - Return UseCaseResult      │   │  - Neutral, descriptive data          │  │
│  │  - Never called by prod      │   │  - No assertions embedded             │  │
│  └──────────────────────────────┘   └────────────────────────────────────────┘  │
│              │                                        │                          │
│              ▼                                        ▼                          │
│  ┌──────────────────────────────────────────────────────────────────────────┐   │
│  │                      Execution Modes                                      │   │
│  │                                                                           │   │
│  │  ┌─────────────────────────┐     ┌─────────────────────────────────┐     │   │
│  │  │   @Experiment           │     │  @ProbabilisticTest             │     │   │
│  │  │                         │     │                                 │     │   │
│  │  │ - Exploratory           │     │ - Conformance testing           │     │   │
│  │  │ - No pass/fail          │     │ - Binary pass/fail              │     │   │
│  │  │ - Produces baseline     │     │ - Consumes specification        │     │   │
│  │  │ - Never gates CI        │     │ - Gates CI                      │     │   │
│  │  │ - Varies context        │     │ - Fixed context (from spec)     │     │   │
│  │  └─────────────────────────┘     └─────────────────────────────────┘     │   │
│  │              │                               │                            │   │
│  │              ▼                               ▼                            │   │
│  │  ┌──────────────────────────────────────────────────────────────────┐    │   │
│  │  │              Shared Execution Engine                              │    │   │
│  │  │                                                                   │    │   │
│  │  │  - Sample generation & invocation                                 │    │   │
│  │  │  - Result aggregation (SampleResultAggregator)                    │    │   │
│  │  │  - Budget monitoring (time, tokens, cost)                         │    │   │
│  │  │  - Early termination evaluation                                   │    │   │
│  │  │  - Structured reporting via JUnit TestReporter                    │    │   │
│  │  └──────────────────────────────────────────────────────────────────┘    │   │
│  └───────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
│  ┌───────────────────────────────┐   ┌────────────────────────────────────┐     │
│  │   Empirical Baseline          │   │   Execution Specification          │     │
│  │   (Machine-generated)         │──▶│   (Human-approved)                 │     │
│  │                               │   │                                    │     │
│  │   - Observed success rates    │   │   - useCaseId:version              │     │
│  │   - Variance, failure modes   │   │   - minPassRate (approved)         │     │
│  │   - Cost metrics              │   │   - Execution context              │     │
│  │   - Context metadata          │   │   - Cost envelopes                 │     │
│  │   - Descriptive only          │   │   - Normative contract             │     │
│  └───────────────────────────────┘   └────────────────────────────────────┘     │
│                                                                                  │
│  ┌──────────────────────────────────────────────────────────────────────────┐   │
│  │                    Backend Extensions (via SPI)                           │   │
│  │                                                                           │   │
│  │  ┌───────────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────┐ │   │
│  │  │ llmx              │  │ Randomized   │  │ Distributed  │  │ Hardware │ │   │
│  │  │ (reference impl)  │  │ Algorithm    │  │ System       │  │ Sensor   │ │   │
│  │  │                   │  │ Backend      │  │ Backend      │  │ Backend  │ │   │
│  │  │ model, temperature│  │ (future)     │  │ (future)     │  │ (future) │ │   │
│  │  │ provider, tokens  │  │              │  │              │  │          │ │   │
│  │  └───────────────────┘  └──────────────┘  └──────────────┘  └──────────┘ │   │
│  │        ↑                                                                  │   │
│  │        │ org.javai.punit.llmx (does NOT pollute core)                    │   │
│  └──────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Ownership Boundaries

| Layer | Responsibility | Package Location |
|-------|---------------|------------------|
| **punit-core** | Execution engine, aggregation, budgeting, reporting, base annotations | `org.javai.punit.api`, `org.javai.punit.engine`, `org.javai.punit.model` |
| **punit-experiment** | Experiment annotation, baseline generation, spec resolution | `org.javai.punit.experiment.api`, `org.javai.punit.experiment.engine` |
| **punit-spec** | Specification model, registry, versioning, conflict resolution | `org.javai.punit.spec.api`, `org.javai.punit.spec.model`, `org.javai.punit.spec.registry` |
| **punit-backends-spi** | Backend SPI interface, generic backend, registry | `org.javai.punit.experiment.spi`, `org.javai.punit.experiment.backend` |
| **llmx** (extension) | LLM-specific backend, context, presets | `org.javai.punit.llmx` |

#### Dependency Constraints

```
                    ┌─────────────────────┐
                    │       llmx          │  ← Extension (LLM-specific)
                    │ org.javai.punit.llmx│
                    └──────────┬──────────┘
                               │ depends on
                               ▼
┌───────────────────────────────────────────────────────────────────┐
│                         punit (core)                               │
│                                                                    │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌───────────┐ │
│  │ punit-core  │  │  punit-     │  │ punit-spec  │  │ punit-    │ │
│  │             │←─│  experiment │←─│             │  │ backends- │ │
│  │             │  │             │  │             │  │ spi       │ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └───────────┘ │
└───────────────────────────────────────────────────────────────────┘
```

> **Critical Constraint**: The core punit packages (`org.javai.punit.*`) **MUST NOT** import from `org.javai.punit.llmx`. Dependencies flow **one direction only**: `llmx → punit-core`.

**Enforcement**:
- ArchUnit tests verify no reverse dependencies
- Build-time checks prevent accidental coupling
- Future: llmx may be extracted to a separate JAR/module

**Decision Point**: Whether these are separate modules/JARs or packages within a single module is an implementation detail. For simplicity, we recommend starting with packages in a single module, with module separation as a future enhancement if needed.

### 2.3 Registry & Storage Concepts

#### Empirical Baseline Storage

Baselines are persisted as machine-readable files in a designated location. **YAML is the default format** due to its human-readability and support for comments; JSON is supported as an optional alternative.

```
src/test/resources/punit/baselines/
├── usecase.email.validation.yaml
├── usecase.json.generation.yaml
└── usecase.sentiment.analysis.yaml
```

Each baseline file contains:
- Use case ID
- Timestamp of generation
- Execution context (backend-specific parameters)
- Statistical observations (success rate, variance, failure distribution)
- Cost metrics (tokens consumed, time elapsed)
- Sample size and confidence metadata

#### Execution Specification Storage

Specifications are versioned contracts stored in a similar structure:

```
src/test/resources/punit/specs/
├── usecase.email.validation/
│   ├── v1.yaml
│   ├── v2.yaml
│   └── v3.yaml  (current)
├── usecase.json.generation/
│   └── v1.yaml
└── ...
```

Each specification file contains:
- Use case ID and version
- Approved minimum pass rate
- Execution context configuration
- Cost envelopes (max time, max tokens)
- Reference to source baseline(s)
- Approval metadata (who, when, why)

#### Specification Registry

The `SpecificationRegistry` is responsible for:
- Loading specifications from the filesystem
- Resolving spec references (e.g., `usecase.json.generation:v2`)
- Caching loaded specifications
- Validating specification integrity

### 2.4 File Format Support

#### Default Format: YAML

YAML is the default file format for baselines and specifications because:

1. **Comments are supported**: Approval notes, rationale, and documentation can be embedded directly in spec files
2. **Human-readable**: Nested structures are easier to read and edit than JSON
3. **Multiline strings**: Approval notes and descriptions flow naturally
4. **Version control friendly**: YAML diffs are cleaner than JSON diffs

#### Optional Format: JSON

JSON is supported as an alternative for:
- Tooling compatibility (some CI/CD tools prefer JSON)
- Programmatic generation (JSON is simpler to emit)
- Systems with existing JSON infrastructure

#### Format Detection

The framework auto-detects format based on file extension:
- `.yaml` or `.yml` → YAML parser
- `.json` → JSON parser

When generating new files (baselines), YAML is used by default. This can be overridden via:
- Annotation parameter: `@Experiment(outputFormat = "json")`
- System property: `-Dpunit.outputFormat=json`
- Environment variable: `PUNIT_OUTPUT_FORMAT=json`

#### Format Consistency

Within a project, consistency is recommended but not enforced. A specification can reference a YAML baseline even if the spec itself is in JSON format (or vice versa). The framework handles format translation transparently.

---

## 3. Core Conceptual Artifacts

### 3.1 Use Case (Test/Experiment Harness)

A **use case** is the fundamental unit of observation and testing. It is:

- A **function** that invokes production code
- Identified by a **use case ID** (string identifier, e.g., `usecase.email.validation`)
- Returns a `UseCaseResult` containing observed outcomes
- **Never** called by production code
- Defined in test/experiment space only

#### 3.1.1 Use Case Representation

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

1. **Use cases are methods, not classes**: Keeps them lightweight and composable. A single test class can define multiple related use cases.

2. **Use case ID is declared via annotation**: The ID is metadata, not part of the method signature. This allows the same use case implementation to be tested under different configurations.

3. **UseCaseContext is injected**: Context provides backend-specific configuration (e.g., model name for LLM, retry policy for distributed systems). The use case implementation may use or ignore context as appropriate.

4. **Return type is always UseCaseResult**: Enforces the pattern that use cases produce observations, not verdicts.

#### 3.1.2 Use Case Discovery

The framework discovers use cases via:
1. Classpath scanning for `@UseCase`-annotated methods
2. Explicit registration in test configuration
3. Reference from `@Experiment` or `@ProbabilisticTest(spec=...)` annotations

### 3.2 UseCaseResult

`UseCaseResult` is a neutral container for observed outcomes:

```java
public final class UseCaseResult {
    
    private final Map<String, Object> values;
    private final Instant timestamp;
    private final Duration executionTime;
    private final Map<String, Object> metadata;
    
    // Builder pattern for construction
    public static Builder builder() { ... }
    
    // Type-safe accessors with defaults
    public <T> T getValue(String key, Class<T> type) { ... }
    public <T> T getValue(String key, Class<T> type, T defaultValue) { ... }
    
    // Convenience accessors for common types
    public boolean getBoolean(String key, boolean defaultValue) { ... }
    public int getInt(String key, int defaultValue) { ... }
    public double getDouble(String key, double defaultValue) { ... }
    public String getString(String key, String defaultValue) { ... }
    
    // All values as immutable map
    public Map<String, Object> getAllValues() { ... }
    
    // Builder
    public static class Builder {
        public Builder value(String key, Object value) { ... }
        public Builder meta(String key, Object value) { ... }
        public UseCaseResult build() { ... }
    }
}
```

**Design Principles for UseCaseResult**:

1. **Neutral and descriptive**: Contains key-value data, not judgments. Whether a value represents "success" or "failure" is determined by the interpreter (experiment or test), not the result itself.

2. **Flexible schema**: The `Map<String, Object>` allows domain-specific values without requiring framework changes. Common patterns (boolean success, numeric scores) are supported via convenience methods.

3. **Immutable**: Once constructed, results cannot be modified. This ensures clean data flow and prevents corruption.

4. **Separates values from metadata**: Values are the primary outputs of use case execution. Metadata captures contextual information (e.g., which backend was used, request IDs).

### 3.3 Experiment

An experiment repeatedly executes a use case in **exploratory mode** to gather empirical data. Experiments support two modes:

1. **Single-config experiments**: One `ExperimentConfig`, one baseline output
2. **Multi-config experiments**: Multiple `ExperimentConfig`s, one baseline per config
3. **Adaptive experiments**: Configs discovered incrementally via feedback-driven refinement

#### Experiment Vocabulary

| Term | Definition |
|------|------------|
| **ExperimentDesign** | Declarative description of what is explored. Composed of `ExperimentFactor`s, each with `ExperimentLevel`s. May include `AdaptiveFactor`s. |
| **ExperimentFactor** | One independently varied dimension (e.g., `model`, `temperature`, `retryPolicy`). Domain-neutral. |
| **ExperimentLevel** | One deliberately chosen setting of a factor. May be categorical (`gpt-4o`) or numeric (`0.2`). |
| **StaticFactor** | An `ExperimentFactor` with levels enumerated up front. Cardinality is known and finite. |
| **AdaptiveFactor** | An `ExperimentFactor` with levels generated dynamically through iterative refinement. Initial level is provided (statically or via `Supplier`); subsequent levels are discovered based on execution feedback. |
| **Initial Level Supplier** | A `Supplier<T>` that provides the initial level for an adaptive factor. Enables sourcing initial levels from production code (e.g., prompt construction logic). |
| **ExperimentConfig** | One concrete combination of `ExperimentLevel`s. Fully specified, executable. The unit of execution and observation. |
| **ExperimentGoal** | Optional criteria for early termination. When any config achieves the goal, remaining configs/iterations are skipped. |
| **RefinementStrategy** | SPI for generating refined levels in adaptive experiments. Backend-specific (e.g., llmx provides LLM-based strategies). |

#### 3.3.0 Single-Config Experiment (Simple Case)

```java
@Experiment(
    useCase = "usecase.json.generation",
    samples = 200,
    timeBudgetMs = 120_000,
    tokenBudget = 100_000
)
@ExperimentContext(backend = "llm", model = "gpt-4", temperature = 0.7)
void measureJsonGenerationPerformance() {
    // Method body is optional—execution is driven by the use case
    // If present, can provide additional setup/teardown
}
```

**Key Characteristics**:

- **No pass/fail**: Experiments never fail (except for infrastructure errors). All outcomes are data.
- **Produces empirical baseline**: After execution, the framework generates a baseline file per `ExperimentConfig`.
- **May explore multiple configs**: Multi-config experiments sweep across an `ExperimentDesign` (e.g., multiple models, temperatures).
- **Never gates CI**: Experiment results are informational only.

#### 3.3.1 @Experiment Annotation Design

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(ExperimentExtension.class)
public @interface Experiment {
    
    /**
     * The use case ID to execute.
     * Must reference a method annotated with @UseCase.
     */
    String useCase();
    
    /**
     * Number of sample invocations to execute (single-config experiments).
     * For multi-config experiments, use samplesPerConfig() instead.
     * Must be >= 1. Default: 100.
     */
    int samples() default 100;
    
    /**
     * Number of samples per ExperimentConfig (multi-config experiments).
     * When @ExperimentDesign is present, this is used instead of samples().
     * Must be >= 1. Default: 100.
     */
    int samplesPerConfig() default 100;
    
    /**
     * Maximum wall-clock time budget in milliseconds.
     * For multi-config experiments, this applies across ALL configs combined.
     * 0 = unlimited. Default: 0.
     */
    long timeBudgetMs() default 0;
    
    /**
     * Maximum token budget for all samples.
     * For multi-config experiments, this applies across ALL configs combined.
     * 0 = unlimited. Default: 0.
     */
    long tokenBudget() default 0;
    
    /**
     * Directory for storing generated baselines.
     * For multi-config experiments, a subdirectory is created per experiment.
     * Relative to test resources root.
     * Default: "punit/baselines"
     */
    String baselineOutputDir() default "punit/baselines";
    
    /**
     * Unique identifier for this experiment.
     * Required for multi-config experiments; optional for single-config.
     * Used as directory name for output files.
     */
    String experimentId() default "";
    
    /**
     * Reference to a YAML ExperimentDesign file (for complex multi-config experiments).
     * When provided, overrides annotation-based configuration.
     * Path relative to test resources root.
     */
    String designFile() default "";
}
```

#### 3.3.1.1 ExperimentDesign Annotations

```java
/**
 * Defines the ExperimentDesign: an explicit list of ExperimentConfigs.
 * Configs execute in listed order.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExperimentDesign {
    Config[] value();
}

/**
 * Defines a single ExperimentConfig.
 * Each config is a concrete combination of ExperimentLevels for each ExperimentFactor.
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface Config {
    /**
     * Key-value pairs defining this config's factor→level mappings.
     * Format: "factor = level"
     * Example: @Config(params = {"model = gpt-4", "temperature = 0.2"})
     */
    String[] params() default {};
    
    // Convenience attributes for common LLM factors
    // (backend-specific; framework ignores unknown attributes)
    String model() default "";
    double temperature() default Double.NaN;
}

/**
 * Defines an optional early termination goal for the experiment.
 * When any ExperimentConfig achieves this goal, remaining configs are skipped.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExperimentGoal {
    
    /** Minimum success rate to consider goal achieved. */
    double successRate() default Double.NaN;
    
    /** Maximum average latency (ms) to consider goal achieved. */
    long maxLatencyMs() default Long.MAX_VALUE;
    
    /** Maximum average tokens per sample to consider goal achieved. */
    long maxTokensPerSample() default Long.MAX_VALUE;
    
    /**
     * Custom goal expression (for complex criteria).
     * Example: "successRate >= 0.90 && avgLatencyMs <= 500"
     */
    String when() default "";
}
```

#### 3.3.2 Single-Config Experiment Execution Semantics

1. **Use case resolution**: The framework locates the `@UseCase` method by ID.
2. **Context injection**: The `@ExperimentContext` (if present) configures the execution environment.
3. **Repeated execution**: The use case is invoked `samples` times.
4. **Result collection**: All `UseCaseResult` instances are collected.
5. **Baseline generation**: After all samples complete, the framework generates an empirical baseline file.
6. **Reporting**: Results are published via JUnit's `TestReporter` for visibility.

#### 3.3.3 Multi-Config Experiments

Multi-config experiments execute a use case across **multiple `ExperimentConfig`s**, producing one baseline per config. This enables systematic exploration of parameter spaces.

> See [Experiment Vocabulary](#experiment-vocabulary) above for definitions of `ExperimentDesign`, `ExperimentFactor`, `ExperimentLevel`, and `ExperimentConfig`.

##### Motivation

When evaluating non-deterministic systems, a single configuration may not reveal the full picture:
- Which LLM model performs best for this task?
- How does success rate vary with temperature?
- What's the cost/quality tradeoff across configurations?

Multi-config experiments answer these questions by executing across multiple `ExperimentConfig`s.

##### Explicit Config Lists

Rather than computing a Cartesian product of factors, experiments define an **explicit list of configs**. This provides:

1. **Transparency**: Every config is visible; no hidden combinations
2. **Control**: Developer chooses exactly which configurations to test
3. **Ordering**: Configs execute top-to-bottom; developer controls sequence
4. **Dependency handling**: Invalid factor/level combinations simply aren't listed

##### Annotation-Based Config List

```java
@Experiment(
    useCase = "usecase.json.generation",
    samplesPerConfig = 100,
    timeBudgetMs = 600_000,
    tokenBudget = 500_000
)
@ExperimentGoal(successRate = 0.90)  // Optional: stop early when goal is met
@ExperimentDesign({
    // Ordered cheapest-first to find most economical acceptable option
    @Config(model = "gpt-3.5-turbo", temperature = 0.0),
    @Config(model = "gpt-3.5-turbo", temperature = 0.2),
    @Config(model = "gpt-4.1-mini", temperature = 0.0),
    @Config(model = "gpt-4.1-mini", temperature = 0.2),
    @Config(model = "gpt-4", temperature = 0.0),
    @Config(model = "gpt-4", temperature = 0.2),
    // Fixed-temperature model (only valid at 1.0)
    @Config(model = "gpt-5-mini", temperature = 1.0)
})
void findCheapestAcceptableModel() {
    // 7 configs, executed in order
    // Stops early if any config achieves 90% success rate
}
```

##### YAML-Based Experiment Design (Recommended for Complex Experiments)

```yaml
# experiments/find-cheapest-acceptable.yaml
experimentId: find-cheapest-acceptable
useCaseId: usecase.json.generation
samplesPerConfig: 100

# Budget applies across ALL configs
budgets:
  timeBudgetMs: 600000      # 10 minutes total
  tokenBudget: 500000       # 500k tokens total

# Optional: Early termination when goal is achieved
goal:
  successRate: ">= 0.90"

# Fixed context values (apply to all configs)
context:
  backend: llm
  maxTokens: 1000

# Factors being explored (documentation/metadata)
factors:
  - name: model
    description: "LLM model to use"
  - name: temperature
    description: "Sampling temperature"

# Explicit config list (executed in order)
# Developer has ordered cheapest-first
configs:
  - model: gpt-3.5-turbo
    temperature: 0.0
    
  - model: gpt-3.5-turbo
    temperature: 0.2
    
  - model: gpt-4.1-mini
    temperature: 0.0
    
  - model: gpt-4.1-mini
    temperature: 0.2
    
  - model: gpt-4
    temperature: 0.0
    
  - model: gpt-4
    temperature: 0.2
    
  # This model only supports temperature = 1.0
  - model: gpt-5-mini
    temperature: 1.0
```

##### Why Explicit Configs Over Cartesian Products

A Cartesian product approach (`3 models × 6 temperatures = 18 configs`) has drawbacks:

1. **Hidden combinations**: Harder to see exactly what will run
2. **Invalid combinations**: Some factor/level combinations are invalid (e.g., `gpt-5-mini` only works at `temperature: 1.0`)
3. **No ordering control**: Framework must impose arbitrary order
4. **Exhaustive by default**: Encourages testing everything rather than what matters

Explicit config lists solve all of these:
- **What you see is what runs**
- **Invalid combinations simply aren't listed**
- **Developer controls order for strategic early termination**
- **Encourages curated, purposeful experiments**

##### Config Execution Semantics

1. **Sequential execution**: Configs execute in listed order
2. **Per-config sampling**: Each config runs `samplesPerConfig` invocations
3. **Budget enforcement**: Time and token budgets apply across **all configs combined**
   - If budget is exhausted mid-experiment, execution stops
   - Completed configs produce baselines; incomplete configs do not
4. **Goal-based early termination**: If goal criteria are satisfied, skip remaining configs
5. **Per-config baseline**: Each completed config generates its own baseline file
6. **Aggregated report**: Summary includes completed, skipped, and reason for termination

##### Config Ordering and Early Termination

When an experiment defines an early termination goal (e.g., "stop when successRate ≥ 0.90"), **configs execute in listed order** until the goal is met.

**The framework does not impose any ordering strategy.** The developer controls the order and should consider their objective:

| Objective | Recommended Ordering | Consequence if Goal Met Early |
|-----------|---------------------|------------------------------|
| Find *cheapest acceptable* option | Cheapest configs first | Expensive options never tested (intended) |
| Find *highest quality* option | Highest quality first | Cheaper alternatives never tested |
| Explore tradeoff space | Interleave quality/cost | Partial exploration before stopping |

**Example: Finding the cheapest model that achieves 90% success rate**

```yaml
goal:
  successRate: ">= 0.90"

# Ordered cheapest-first: stop as soon as an acceptable config is found
configs:
  - {model: gpt-3.5-turbo, temperature: 0.0}    # Cheapest, try first
  - {model: gpt-3.5-turbo, temperature: 0.2}
  - {model: gpt-4.1-mini, temperature: 0.0}     # Mid-tier
  - {model: gpt-4.1-mini, temperature: 0.2}
  - {model: gpt-4, temperature: 0.0}            # Most expensive, try last
  - {model: gpt-4, temperature: 0.2}
```

If `gpt-3.5-turbo` at `temperature: 0.2` achieves 92% success rate, the experiment terminates. The more expensive models are never tested—which is exactly what the developer intended.

**Contrast: Quality-first ordering (different objective)**

```yaml
# Ordered quality-first: find highest quality quickly, even if expensive
configs:
  - {model: gpt-4, temperature: 0.0}            # Best quality, try first
  - {model: gpt-4, temperature: 0.2}
  - {model: gpt-4.1-mini, temperature: 0.0}
  - {model: gpt-3.5-turbo, temperature: 0.0}    # Cheapest, try last
```

With this ordering, if `gpt-4` achieves the goal, cheaper alternatives are never tested. The developer might miss discovering that `gpt-3.5-turbo` could also satisfy the goal at lower cost. **This is a deliberate tradeoff chosen by the developer.**

**Key principle**: The framework is ordering-agnostic. It executes configs top-to-bottom and stops when the goal is met. The developer must order configs according to their priorities.

##### Budget Behavior in Multi-Config Experiments

There is **no limit** on the number of `ExperimentConfig`s. Budget constraints (time, tokens) naturally limit execution:

```
ExperimentDesign: 3 models × 6 temperatures = 18 configs
Samples per config: 100
Token budget: 100,000

If each sample uses ~1,000 tokens:
  - 100 samples × 1,000 tokens = 100,000 tokens per config
  - Budget exhausted after ~1 config

The framework will:
  1. Complete as many configs as budget allows
  2. Generate baselines for completed configs
  3. Report which configs were not executed
  4. Generate aggregated report for completed configs
```

##### Multi-Config Output Structure

```
baselines/
└── find-cheapest-acceptable/                     # Experiment directory
    ├── DESIGN.yaml                               # ExperimentDesign (factors, levels, configs)
    ├── SUMMARY.yaml                              # Aggregated results & analysis
    ├── config-000.yaml                           # Per-config baselines
    ├── config-001.yaml                           # (numbered by execution order)
    ├── config-002.yaml                           # (goal achieved here → later configs skipped)
    └── ...
```

Alternatively, configs can be named by their factor levels:

```
baselines/
└── find-cheapest-acceptable/
    ├── DESIGN.yaml
    ├── SUMMARY.yaml
    ├── model=gpt-3.5-turbo,temperature=0.0.yaml
    ├── model=gpt-3.5-turbo,temperature=0.2.yaml
    ├── model=gpt-4.1-mini,temperature=0.0.yaml   # Goal achieved
    └── ...                                        # Later configs not present (skipped)
```

##### Per-Config Baseline

Each `ExperimentConfig` produces a baseline with full traceability:

```yaml
# baselines/find-cheapest-acceptable/config-002.yaml
# (or named by levels: model=gpt-4.1-mini,temperature=0.0.yaml)

useCaseId: usecase.json.generation
experimentId: find-cheapest-acceptable
configIndex: 2  # 0-indexed position in config list

# The ExperimentConfig (factor → level mappings)
config:
  model: gpt-4.1-mini       # factor: level
  temperature: 0.0          # factor: level

# Full context (fixed values + config levels)
context:
  backend: llm
  model: gpt-4.1-mini
  temperature: 0.0
  maxTokens: 1000

generatedAt: 2026-01-04T15:32:00Z

statistics:
  samplesExecuted: 100
  successRate:
    observed: 0.92
    standardError: 0.027
    confidenceInterval95: [0.87, 0.97]
  failureDistribution:
    invalidJson: 5
    missingField: 3

cost:
  totalTimeMs: 42000
  avgTimePerSampleMs: 420
  totalTokens: 45000
  avgTokensPerSample: 450

# Goal evaluation (if experiment has a goal)
goalEvaluation:
  criteria: "successRate >= 0.90"
  met: true
  triggeredEarlyTermination: true
```

##### Aggregated Experiment Report

The `SUMMARY.yaml` provides a human-readable overview for analysis:

```yaml
# baselines/find-cheapest-acceptable/SUMMARY.yaml
experimentId: find-cheapest-acceptable
useCaseId: usecase.json.generation
generatedAt: 2026-01-04T16:00:00Z

# ExperimentDesign summary
design:
  factors:
    - name: model
      levels: [gpt-3.5-turbo, gpt-4.1-mini, gpt-4]
    - name: temperature
      levels: [0.0, 0.2]
  configsDefined: 6
  samplesPerConfig: 100

# Goal-based early termination
goal:
  criteria:
    successRate: ">= 0.90"
  achieved: true
  achievedAtConfig: 2  # 0-indexed

# Execution summary
execution:
  configsCompleted: 3
  configsSkipped: 3
  totalSamplesExecuted: 300
  totalTimeMs: 127000
  totalTokensConsumed: 61000
  terminationReason: GOAL_ACHIEVED

# Results for each ExperimentConfig (in execution order)
results:
  - config: {model: gpt-3.5-turbo, temperature: 0.0}
    successRate: 0.84
    avgLatencyMs: 312
    avgTokensPerSample: 198
    status: COMPLETED
    goalMet: false
    
  - config: {model: gpt-3.5-turbo, temperature: 0.2}
    successRate: 0.87
    avgLatencyMs: 325
    avgTokensPerSample: 205
    status: COMPLETED
    goalMet: false
    
  - config: {model: gpt-4.1-mini, temperature: 0.0}
    successRate: 0.92  # ✓ Meets goal
    avgLatencyMs: 380
    avgTokensPerSample: 245
    status: COMPLETED
    goalMet: true      # Triggered early termination
    
  - config: {model: gpt-4.1-mini, temperature: 0.2}
    status: SKIPPED
    reason: "Goal achieved by previous config"
    
  - config: {model: gpt-4, temperature: 0.0}
    status: SKIPPED
    reason: "Goal achieved by previous config"
    
  - config: {model: gpt-4, temperature: 0.2}
    status: SKIPPED
    reason: "Goal achieved by previous config"

# Analysis to aid human decision-making
analysis:
  goalAchievedBy:
    config: {model: gpt-4.1-mini, temperature: 0.0}
    successRate: 0.92
    avgTokensPerSample: 245
    note: "First config to meet goal (successRate >= 0.90)"
    
  configsTested:
    - config: {model: gpt-3.5-turbo, temperature: 0.0}
      successRate: 0.84
      verdict: "Below goal threshold"
    - config: {model: gpt-3.5-turbo, temperature: 0.2}
      successRate: 0.87
      verdict: "Below goal threshold"
      
  configsSkipped:
    - {model: gpt-4.1-mini, temperature: 0.2}
    - {model: gpt-4, temperature: 0.0}
    - {model: gpt-4, temperature: 0.2}
    note: "Not tested because goal was already achieved by an earlier config"

  costSavings:
    configsSkipped: 3
    estimatedSamplesSaved: 300
    estimatedTokensSaved: ~90000
    note: "Early termination avoided testing more expensive models"

# Note for human review
notes: |
  This experiment was ordered cheapest-first to find the most economical
  configuration that meets the 90% success rate goal.
  
  Result: gpt-4.1-mini at temperature 0.0 achieves 92% success rate.
  Cheaper options (gpt-3.5-turbo) were tested but did not meet the goal.
  More expensive options (gpt-4) were not tested.
  
  If you need to verify whether gpt-4 would perform better, re-run
  the experiment with a different ordering or without early termination.
```

##### Creating a Spec from Experiment Results

The framework generates human-readable reports; **humans select the best `ExperimentConfig`** and create specs:

1. Run experiment
2. Review `SUMMARY.yaml` analysis
3. Select optimal config based on requirements (quality, cost, latency)
4. Create specification referencing that config's baseline

```yaml
# specs/usecase.json.generation/v1.yaml
specId: usecase.json.generation:v1
useCaseId: usecase.json.generation
version: 1

approvedAt: 2026-01-04T17:00:00Z
approvedBy: jane.engineer@example.com
approvalNotes: |
  Selected gpt-4 at temperature 0.2 based on json-generation-sweep experiment.
  Observed 95% success rate; setting threshold at 90% to allow for variance.
  This balances quality (95%) with reasonable cost (242 tokens/sample avg).

# Reference to source baseline
sourceBaselines:
  - json-generation-sweep/model=gpt-4,temperature=0.2.yaml

# Execution context (from selected ExperimentConfig)
executionContext:
  backend: llm
  model: gpt-4
  temperature: 0.2
  maxTokens: 1000

requirements:
  minPassRate: 0.90
  successCriteria: "isValidJson == true && hasRequiredFields == true"
```

#### 3.3.4 Adaptive Experiments

An **adaptive experiment** extends the experiment model by allowing **one or more factors to generate their levels dynamically**, based on feedback from previous executions, rather than being fully enumerated up front.

##### Motivation

Static experiments require all levels to be known before execution. This works well for discrete, enumerable dimensions like model selection or temperature ranges. However, some factors are inherently open-ended:

- **System prompts**: The space of possible prompts is effectively infinite
- **Retry strategies**: May need refinement based on observed failure patterns
- **Input preprocessing**: Transformations that depend on observed failure modes

For these factors, the "right" level cannot be enumerated in advance—it must be **discovered through empirical feedback**.

##### Key Concept: Static vs. Adaptive Factors

| Aspect | Static Factor | Adaptive Factor |
|--------|--------------|-----------------|
| **Levels** | Enumerated up front | Generated iteratively |
| **Source** | Developer-specified list | Initial value + refinement strategy |
| **Cardinality** | Known, finite | Unknown, bounded by iteration limits |
| **Example** | `model: [gpt-3.5, gpt-4]` | `systemPrompt: startingFrom(...).refinedBy(...)` |

Both factor types coexist in the same `ExperimentDesign`. Static factors provide the fixed dimensions; adaptive factors provide the discovery dimension.

##### One-Sentence Mental Model

> A static experiment enumerates configs up front; an adaptive experiment **discovers configs incrementally** using empirical feedback—but both use exactly the same punit machinery.

##### Adaptive Factor API

```java
/**
 * An adaptive factor generates levels dynamically based on execution feedback.
 * Each iteration produces a new level; iteration continues until:
 * - Acceptable results are achieved (goal met)
 * - Iteration budget is exhausted
 * - Refinement strategy signals no further improvement possible
 */
public interface AdaptiveFactor<T> {
    
    /** The factor name (e.g., "systemPrompt"). */
    String name();
    
    /** 
     * The initial level to start iteration.
     * May be sourced from production code via a Supplier.
     */
    T initialLevel();
    
    /** Generate the next level based on feedback from previous execution. */
    Optional<T> refine(IterationFeedback feedback);
    
    /** Maximum iterations allowed (safety bound). */
    int maxIterations();
}

/**
 * Builder for adaptive factors with flexible initial level sourcing.
 */
public class AdaptiveLevels<T> {
    
    /**
     * Provide a static initial level value.
     * Use when the initial level is a fixed string or value.
     */
    public Builder<T> startingFrom(T initialValue);
    
    /**
     * Provide a Supplier that produces the initial level.
     * The Supplier is invoked once at experiment start.
     * 
     * Use this when:
     * - The initial level is constructed by production code
     * - The initial level depends on application context
     * - You want to test the actual prompt construction logic
     */
    public Builder<T> startingFrom(Supplier<T> initialValueSupplier);
    
    // ... builder methods
}

/**
 * Feedback from one iteration, used to guide refinement.
 */
public interface IterationFeedback {
    
    /** The level used in this iteration. */
    Object level();
    
    /** Aggregated results from this iteration's samples. */
    EmpiricalSummary summary();
    
    /** Structured failure information for analysis. */
    List<FailureObservation> failures();
    
    /** Iteration number (0-indexed). */
    int iteration();
}

/**
 * Observation of a single failure, for refinement analysis.
 */
public interface FailureObservation {
    
    /** The UseCaseResult from the failed sample. */
    UseCaseResult result();
    
    /** Which success criteria were not met. */
    List<String> unmetCriteria();
    
    /** Optional: exception if failure was due to error. */
    Optional<Throwable> exception();
}
```

##### Refinement Strategies

Refinement strategies are pluggable. The framework provides an SPI; backends (like llmx) provide implementations.

```java
/**
 * SPI for level refinement strategies.
 * Implementations are typically backend-specific.
 */
public interface RefinementStrategy<T> {
    
    /**
     * Generate a refined level based on feedback.
     * Returns empty if no further refinement is possible.
     */
    Optional<T> refine(T currentLevel, IterationFeedback feedback);
    
    /** Human-readable description of the strategy. */
    String description();
}
```

The llmx extension provides LLM-specific strategies:

```java
// In org.javai.punit.llmx.refinement

/**
 * Uses an LLM to refine prompts based on failure analysis.
 */
public class LlmPromptRefinementStrategy implements RefinementStrategy<String> {
    
    public static LlmPromptRefinementStrategy llmBased() {
        return new LlmPromptRefinementStrategy(defaultConfig());
    }
    
    public static LlmPromptRefinementStrategy llmBased(LlmRefinementConfig config) {
        return new LlmPromptRefinementStrategy(config);
    }
    
    @Override
    public Optional<String> refine(String currentPrompt, IterationFeedback feedback) {
        // Analyze failures
        // Generate refinement prompt
        // Call LLM to produce improved prompt
        // Return new prompt variant
    }
}
```

##### Adaptive Experiment Declaration

**Java API (programmatic)**:

```java
@Experiment(useCase = "usecase.summarization", samplesPerConfig = 50)
@ExperimentGoal(successRate = 0.90)
ExperimentDesign design = ExperimentDesign.builder()
    // Static factors: enumerated up front
    .factor("model", levels("gpt-4o"))
    .factor("temperature", levels(0.2))
    
    // Adaptive factor with static initial value
    .adaptiveFactor(
        "systemPrompt",
        AdaptiveLevels.<String>builder()
            .startingFrom("""
                You are a helpful assistant that summarizes text accurately.
                Keep summaries concise and preserve key information.
            """)
            .refinedBy(LlmPromptRefinementStrategy.llmBased())
            .maxIterations(10)
            .build()
    )
    .build();
```

**Sourcing Initial Level from Production Code**:

In real-world applications, system prompts are rarely static text. They are often constructed by production code—using templates, configuration, or even dynamic content. The adaptive factor framework supports this via a `Supplier`:

```java
// Production code constructs prompts (in main application)
public class PromptFactory {
    public String buildSummarizationPrompt(SummarizationConfig config) {
        return promptTemplate
            .withMaxLength(config.getMaxLength())
            .withTone(config.getTone())
            .withExclusions(config.getExclusions())
            .render();
    }
}

// Test/experiment code sources the initial level from production
@Experiment(useCase = "usecase.summarization", samplesPerConfig = 50)
@ExperimentGoal(successRate = 0.90)
ExperimentDesign design = ExperimentDesign.builder()
    .factor("model", levels("gpt-4o"))
    .factor("temperature", levels(0.2))
    
    // Adaptive factor: initial level sourced from production code
    .adaptiveFactor(
        "systemPrompt",
        AdaptiveLevels.<String>builder()
            // Supplier invokes production prompt construction
            .startingFrom(() -> promptFactory.buildSummarizationPrompt(defaultConfig))
            .refinedBy(LlmPromptRefinementStrategy.llmBased())
            .maxIterations(10)
            .build()
    )
    .build();
```

This approach:

1. **Tests the real prompt**: The experiment starts with the actual prompt your production code generates, not a hand-crafted test version.

2. **Maintains separation**: Production code (`PromptFactory`) has no knowledge of experiments. The `Supplier` is defined in test/experiment space.

3. **Enables realistic refinement**: The adaptive refinement improves upon what your production code actually produces, making the resulting spec directly applicable.

4. **Supports context-dependent prompts**: If your prompt varies based on user type, tenant configuration, or feature flags, the `Supplier` can capture the specific context being tested.

**Example: Context-Dependent Initial Level**:

```java
// Testing prompts for different user contexts
@Experiment(useCase = "usecase.customer-support", samplesPerConfig = 50)
@ExperimentGoal(successRate = 0.85)
ExperimentDesign design = ExperimentDesign.builder()
    .factor("model", levels("gpt-4o"))
    
    .adaptiveFactor(
        "systemPrompt",
        AdaptiveLevels.<String>builder()
            .startingFrom(() -> {
                // Production code constructs prompt based on context
                UserContext ctx = UserContext.builder()
                    .tier(CustomerTier.ENTERPRISE)
                    .region(Region.EU)
                    .features(Set.of("advanced-analytics"))
                    .build();
                return customerSupportPromptBuilder.build(ctx);
            })
            .refinedBy(LlmPromptRefinementStrategy.llmBased())
            .maxIterations(8)
            .build()
    )
    .build();
```

**YAML (declarative, for complex experiments)**:

```yaml
# experiments/adaptive-prompt-discovery.yaml
experimentId: adaptive-prompt-discovery
useCaseId: usecase.summarization
samplesPerConfig: 50

goal:
  successRate: ">= 0.90"

budgets:
  timeBudgetMs: 1800000    # 30 minutes
  tokenBudget: 1000000     # 1M tokens (includes refinement calls)

# Static factors
factors:
  - name: model
    levels: [gpt-4o]
  - name: temperature
    levels: [0.2]

# Adaptive factor with inline initial level
adaptiveFactors:
  - name: systemPrompt
    initialLevel: |
      You are a helpful assistant that summarizes text accurately.
      Keep summaries concise and preserve key information.
    refinementStrategy: llm-based    # Registered strategy ID
    maxIterations: 10
    
    # Optional: strategy-specific configuration
    strategyConfig:
      analysisPrompt: |
        Analyze these failures and suggest prompt improvements:
        {failures}
      model: gpt-4o    # Model for refinement (can differ from experiment model)
```

**YAML with Supplier Reference (production-sourced initial level)**:

When initial levels should be sourced from production code, YAML can reference a registered `Supplier`:

```yaml
# experiments/adaptive-enterprise-support.yaml
experimentId: adaptive-enterprise-support
useCaseId: usecase.customer-support
samplesPerConfig: 50

goal:
  successRate: ">= 0.85"

factors:
  - name: model
    levels: [gpt-4o]

adaptiveFactors:
  - name: systemPrompt
    # Reference a registered Supplier by ID (defined in Java)
    initialLevelSupplier: "enterprise-support-prompt-supplier"
    refinementStrategy: llm-based
    maxIterations: 8
```

The `Supplier` is registered in Java:

```java
// In test setup or configuration
AdaptiveFactorRegistry.registerSupplier(
    "enterprise-support-prompt-supplier",
    () -> {
        UserContext ctx = UserContext.builder()
            .tier(CustomerTier.ENTERPRISE)
            .region(Region.EU)
            .build();
        return customerSupportPromptBuilder.build(ctx);
    }
);
```

This pattern enables:
- **YAML-based experiment design** (declarative, versionable)
- **Production-sourced initial levels** (realistic starting points)
- **Separation of concerns** (YAML defines structure; Java provides content)

##### Adaptive Experiment Execution

At runtime, the experiment proceeds iteratively:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Adaptive Experiment Execution                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────────┐                                                        │
│  │ ExperimentDesign │                                                        │
│  │ (static + adapt.)│                                                        │
│  └────────┬─────────┘                                                        │
│           │                                                                  │
│           ▼                                                                  │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                         ITERATION LOOP                                │   │
│  │                                                                       │   │
│  │    ┌─────────────────────────────────────────────────────────────┐   │   │
│  │    │  1. Compose ExperimentConfig                                 │   │   │
│  │    │     • Static factors: fixed levels                          │   │   │
│  │    │     • Adaptive factors: current iteration's level           │   │   │
│  │    └────────────────────────┬────────────────────────────────────┘   │   │
│  │                             ▼                                        │   │
│  │    ┌─────────────────────────────────────────────────────────────┐   │   │
│  │    │  2. Execute Use Case (N samples)                            │   │   │
│  │    │     • Same execution engine as static experiments           │   │   │
│  │    │     • Same budgeting, reporting                             │   │   │
│  │    └────────────────────────┬────────────────────────────────────┘   │   │
│  │                             ▼                                        │   │
│  │    ┌─────────────────────────────────────────────────────────────┐   │   │
│  │    │  3. Aggregate Results → EmpiricalSummary                    │   │   │
│  │    │     • Success rate, failure distribution                    │   │   │
│  │    │     • Cost metrics                                          │   │   │
│  │    └────────────────────────┬────────────────────────────────────┘   │   │
│  │                             ▼                                        │   │
│  │    ┌─────────────────────────────────────────────────────────────┐   │   │
│  │    │  4. Check Termination                                        │   │   │
│  │    │     • Goal met? → EXIT (success)                            │   │   │
│  │    │     • Budget exhausted? → EXIT (budget)                     │   │   │
│  │    │     • Max iterations? → EXIT (limit)                        │   │   │
│  │    └────────────────────────┬────────────────────────────────────┘   │   │
│  │                             │ (not terminated)                       │   │
│  │                             ▼                                        │   │
│  │    ┌─────────────────────────────────────────────────────────────┐   │   │
│  │    │  5. Extract Feedback                                         │   │   │
│  │    │     • Failure observations                                   │   │   │
│  │    │     • Success patterns                                       │   │   │
│  │    └────────────────────────┬────────────────────────────────────┘   │   │
│  │                             ▼                                        │   │
│  │    ┌─────────────────────────────────────────────────────────────┐   │   │
│  │    │  6. Refine Adaptive Factor(s)                               │   │   │
│  │    │     • RefinementStrategy.refine(currentLevel, feedback)     │   │   │
│  │    │     • Produces next level (or signals no improvement)       │   │   │
│  │    └────────────────────────┬────────────────────────────────────┘   │   │
│  │                             │                                        │   │
│  │                             └──────── (loop) ───────────────────────┘   │
│  │                                                                       │   │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  ┌──────────────────┐                                                        │
│  │ Iteration History│ ← All levels and results recorded                     │
│  │ (for provenance) │                                                        │
│  └──────────────────┘                                                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

Pseudocode:

```java
// Adaptive experiment execution loop
int iteration = 0;
while (!terminationReached()) {
    // 1. Compose config from static + current adaptive levels
    ExperimentConfig config = composeConfig(staticFactors, adaptiveFactors);
    
    // 2. Execute use case repeatedly (same as static experiments)
    List<UseCaseResult> results = experimentEngine.runUseCase(useCase, config);
    
    // 3. Aggregate results
    EmpiricalSummary summary = aggregator.aggregate(results);
    
    // 4. Record this iteration
    iterationHistory.record(iteration, config, summary);
    
    // 5. Check termination conditions
    if (summary.meetsGoal(experimentGoal)) {
        terminate(GOAL_ACHIEVED);
        break;
    }
    if (budgetExhausted()) {
        terminate(BUDGET_EXHAUSTED);
        break;
    }
    if (iteration >= maxIterations) {
        terminate(ITERATION_LIMIT);
        break;
    }
    
    // 6. Extract feedback and refine adaptive factors
    IterationFeedback feedback = feedbackExtractor.from(summary, results);
    for (AdaptiveFactor<?> factor : adaptiveFactors) {
        Optional<?> refinedLevel = factor.refine(feedback);
        if (refinedLevel.isEmpty()) {
            terminate(NO_FURTHER_IMPROVEMENT);
            break;
        }
        factor.setCurrentLevel(refinedLevel.get());
    }
    
    iteration++;
}

// Generate baseline with full iteration history
generateAdaptiveBaseline(iterationHistory);
```

##### What Doesn't Change

Adaptive experiments reuse the existing punit machinery:

| Component | Behavior |
|-----------|----------|
| **Use Case** | Unchanged—same function, same `UseCaseResult` |
| **Execution Engine** | Unchanged—same sample execution, same budgeting |
| **Aggregation** | Unchanged—same `SampleResultAggregator` |
| **Budgeting** | Unchanged—time and token budgets apply |
| **Reporting** | Unchanged—JUnit TestReporter integration |
| **Goal Evaluation** | Unchanged—same criteria, applied per iteration |

An adaptive experiment is *not* a new execution model—it's a different way of **producing ExperimentConfigs**.

##### Adaptive Baseline Structure

Adaptive experiments produce baselines that include the full iteration history:

```yaml
# baselines/adaptive-prompt-discovery/BASELINE.yaml
experimentId: adaptive-prompt-discovery
useCaseId: usecase.summarization
experimentType: ADAPTIVE
generatedAt: 2026-01-04T18:30:00Z

# Static factor levels (fixed throughout)
staticConfig:
  model: gpt-4o
  temperature: 0.2

# Adaptive factor summary
adaptiveFactors:
  - name: systemPrompt
    refinementStrategy: llm-based
    totalIterations: 4
    finalLevel: |
      You are a text summarization expert. Your task is to:
      1. Identify the main thesis or argument
      2. Extract key supporting points (max 3)
      3. Preserve critical numbers, dates, and names
      4. Use active voice and concise language
      5. Keep summary under 100 words
      
      Avoid: vague generalizations, subjective interpretations, 
      adding information not in the source.

# Termination
termination:
  reason: GOAL_ACHIEVED
  atIteration: 3  # 0-indexed

# Goal evaluation
goal:
  criteria:
    successRate: ">= 0.90"
  achieved: true

# Full iteration history (for provenance and analysis)
iterations:
  - iteration: 0
    config:
      model: gpt-4o
      temperature: 0.2
      systemPrompt: |
        You are a helpful assistant that summarizes text accurately.
        Keep summaries concise and preserve key information.
    statistics:
      samplesExecuted: 50
      successRate: 0.72
      failureDistribution:
        missingKeyInfo: 8
        tooVerbose: 6
    goalMet: false
    
  - iteration: 1
    config:
      model: gpt-4o
      temperature: 0.2
      systemPrompt: |
        You are a text summarization assistant. Focus on:
        - Capturing the main point
        - Including key facts and figures
        - Keeping output under 100 words
    refinementReason: "Added specificity about key facts and length limit"
    statistics:
      samplesExecuted: 50
      successRate: 0.82
      failureDistribution:
        missingKeyInfo: 5
        structureIssues: 4
    goalMet: false
    
  - iteration: 2
    config:
      model: gpt-4o
      temperature: 0.2
      systemPrompt: |
        You are a text summarization expert. Your task is to:
        1. Identify the main thesis
        2. Extract key supporting points (max 3)
        3. Preserve critical numbers and names
        4. Keep summary under 100 words
    refinementReason: "Added structured instructions based on structure failures"
    statistics:
      samplesExecuted: 50
      successRate: 0.86
      failureDistribution:
        missingKeyInfo: 4
        vagueness: 3
    goalMet: false
    
  - iteration: 3
    config:
      model: gpt-4o
      temperature: 0.2
      systemPrompt: |
        You are a text summarization expert. Your task is to:
        1. Identify the main thesis or argument
        2. Extract key supporting points (max 3)
        3. Preserve critical numbers, dates, and names
        4. Use active voice and concise language
        5. Keep summary under 100 words
        
        Avoid: vague generalizations, subjective interpretations, 
        adding information not in the source.
    refinementReason: "Added explicit anti-patterns based on vagueness failures"
    statistics:
      samplesExecuted: 50
      successRate: 0.92  # ✓ Goal met
      failureDistribution:
        missingKeyInfo: 2
        other: 2
    goalMet: true

# Aggregate statistics
aggregateStatistics:
  totalSamples: 200
  totalIterations: 4
  successRateProgression: [0.72, 0.82, 0.86, 0.92]
  totalTimeMs: 180000
  totalTokensConsumed: 125000

# Cost breakdown
cost:
  experimentExecution:
    tokens: 100000
    calls: 200
  refinementCalls:
    tokens: 25000
    calls: 3
  total:
    tokens: 125000
```

##### Serialization Challenges for Adaptive Factor Levels

Recording adaptive factor levels in baseline files presents practical challenges:

| Challenge | Description |
|-----------|-------------|
| **Multi-line content** | System prompts often span dozens of lines, making baselines unwieldy |
| **Special characters** | Prompts may contain characters that require YAML/JSON escaping (quotes, colons, brackets) |
| **Structured objects** | Some adaptive factors may produce non-string values (nested structures, code templates) |
| **Large iteration history** | Many iterations × large levels = bloated baseline files |
| **Readability** | Escaped or inlined content is hard to review; diffs between iterations are obscured |

**Mitigation Strategies**:

The framework provides multiple strategies for handling complex adaptive factor levels:

**1. Inline with YAML Literal Blocks (default for short content)**

```yaml
iterations:
  - iteration: 0
    config:
      systemPrompt: |
        You are a helpful assistant that summarizes text.
        Keep summaries concise.
```

Works well for content under ~20 lines without problematic characters.

**2. External File References (for large or complex content)**

When levels exceed a configurable threshold (lines, bytes, or complexity score), the framework writes them to separate files and records references:

```yaml
iterations:
  - iteration: 0
    config:
      systemPrompt:
        $ref: "./levels/iteration-0-systemPrompt.txt"
        contentHash: "sha256:a1b2c3..."
        lineCount: 47
        
  - iteration: 1
    config:
      systemPrompt:
        $ref: "./levels/iteration-1-systemPrompt.txt"
        contentHash: "sha256:d4e5f6..."
        lineCount: 52
```

Output structure:

```
baselines/
└── adaptive-prompt-discovery/
    ├── BASELINE.yaml           # Main baseline with references
    └── levels/                 # Externalized level content
        ├── iteration-0-systemPrompt.txt
        ├── iteration-1-systemPrompt.txt
        ├── iteration-2-systemPrompt.txt
        └── iteration-3-systemPrompt.txt
```

**3. Configurable Serialization**

Adaptive factors can specify a serialization strategy:

```java
.adaptiveFactor(
    "systemPrompt",
    AdaptiveLevels.<String>builder()
        .startingFrom(initialPrompt)
        .refinedBy(strategy)
        .maxIterations(10)
        // Serialization configuration
        .serialization(LevelSerialization.builder()
            .externalizeWhen(level -> level.length() > 500)  // Size threshold
            .fileExtension(".txt")
            .includeContentHash(true)
            .build())
        .build()
)
```

**4. Structured Object Serialization**

For non-string adaptive factors (e.g., configuration objects, templates), the framework uses pluggable serializers:

```java
.adaptiveFactor(
    "retryConfig",
    AdaptiveLevels.<RetryConfig>builder()
        .startingFrom(RetryConfig.defaults())
        .refinedBy(retryConfigRefinementStrategy)
        .maxIterations(5)
        .serializer(RetryConfigSerializer.INSTANCE)  // Custom serializer
        .build()
)
```

The serializer converts the object to a YAML/JSON-compatible representation.

**5. Diff-Friendly Output (optional)**

For human review, the framework can generate diff-friendly reports alongside baselines:

```
baselines/
└── adaptive-prompt-discovery/
    ├── BASELINE.yaml
    ├── levels/
    └── analysis/
        ├── level-progression.diff    # Unified diff between iterations
        └── level-summary.md          # Human-readable summary of changes
```

**Configuration**:

```java
@Experiment(...)
@AdaptiveOutputConfig(
    externalizeLevelsOver = 500,        // Externalize levels > 500 chars
    generateDiffReport = true,          // Create diff analysis
    generateSummaryReport = true        // Create human-readable summary
)
```

**Baseline Provenance**:

Regardless of storage strategy, baselines always record:
- The source of the initial level (static value, `Supplier` ID, or `$ref`)
- Content hash for verification
- Serialization strategy used

This ensures reproducibility and auditability even when content is externalized.

##### What Adaptive Experiments Are NOT

**This is NOT prompt optimization or AutoML.**

| This IS | This is NOT |
|---------|-------------|
| Empirical refinement toward *acceptable* behavior | Search for globally *optimal* configuration |
| Iteration stops when goal is met | Iteration continues to find "best" |
| All iterations recorded for human review | Black-box optimization |
| Produces a spec candidate for approval | Produces "tuned" production configuration |
| Discovery phase before testing | Replacement for testing |

Adaptive experiments remain part of the **discovery → specification → testing** flow. The output is a baseline (with iteration history) that a human reviews to create a specification.

##### Governance and Safety

Adaptive experiments have additional governance considerations:

1. **Iteration bounds are mandatory**: `maxIterations` must be specified
2. **Budgets apply to refinement**: Token consumption for refinement calls counts against budget
3. **Full provenance**: Every level tried is recorded; no hidden exploration
4. **Human approval required**: Adaptive baselines still require human review before becoming specs
5. **Refinement strategy is explicit**: The strategy used is recorded in the baseline

```java
// Mandatory iteration limit
.adaptiveFactor(
    "systemPrompt",
    AdaptiveLevels.<String>builder()
        .startingFrom(initialPrompt)
        .refinedBy(strategy)
        .maxIterations(10)  // Required; no default
        .build()
)
```

### 3.4 Empirical Baseline

The empirical baseline is the machine-readable output of an experiment:

```yaml
# Empirical Baseline for usecase.json.generation
# Generated automatically by punit experiment runner
# DO NOT EDIT - create a specification based on this baseline instead

useCaseId: usecase.json.generation
generatedAt: 2026-01-04T15:30:00Z
experimentClass: com.example.JsonExperiments
experimentMethod: measureJsonGenerationPerformance

context:
  backend: llm
  model: gpt-4
  temperature: 0.7

execution:
  samplesPlanned: 200
  samplesExecuted: 200
  terminationReason: COMPLETED

statistics:
  successRate:
    observed: 0.935
    standardError: 0.017
    confidenceInterval95: [0.901, 0.969]
  failureDistribution:
    invalidJson: 8
    missingRequiredField: 3
    timeout: 2

cost:
  totalTimeMs: 87432
  avgTimePerSampleMs: 437
  totalTokens: 45230
  avgTokensPerSample: 226

successCriteria:
  definition: "isValidJson == true && hasRequiredFields == true"
  measuredBy:
    - isValidJson
    - hasRequiredFields
```

**Baseline Properties**:

- **Immutable**: Once generated, a baseline is never modified.
- **Auditable**: Contains enough information to reproduce or understand the experiment.
- **Descriptive, not normative**: Does not declare what *should* happen, only what *did* happen.
- **Versioned by timestamp**: Multiple baselines for the same use case can coexist.
- **YAML by default**: YAML format supports comments and is more readable; JSON is available for tooling compatibility.

### 3.5 Execution Specification

An execution specification is a **human-reviewed and approved contract** derived from empirical baselines:

```yaml
# Execution Specification: usecase.json.generation v3
# Approved contract for probabilistic testing

specId: usecase.json.generation:v3
useCaseId: usecase.json.generation
version: 3

# Approval metadata (required)
approvedAt: 2026-01-04T16:00:00Z
approvedBy: jane.engineer@example.com
approvalNotes: >
  Baseline from Jan 4 experiment shows 93.5% success rate.
  Setting threshold at 90% to allow for variance.

# Source baselines this spec is derived from
sourceBaselines:
  - usecase.json.generation/2026-01-04T15:30:00Z.yaml

# Execution context (applied during test runs)
executionContext:
  backend: llm
  model: gpt-4
  temperature: 0.7

# Pass/fail requirements
requirements:
  minPassRate: 0.90
  successCriteria: "isValidJson == true && hasRequiredFields == true"

# Resource limits
costEnvelope:
  maxTimePerSampleMs: 2000
  maxTokensPerSample: 500
  totalTokenBudget: 50000
```

**Specification Properties**:

- **Normative**: Declares what *should* happen, based on what *did* happen.
- **Versioned**: Each specification has an explicit version number.
- **Traceable**: References the source baseline(s) that informed the spec.
- **Approved**: Contains explicit approval metadata.
- **Consumable**: Used by probabilistic tests and production configuration.
- **YAML by default**: YAML format enables inline comments for approval notes and rationale; JSON is supported as an alternative.

#### 3.5.1 Specification Creation Workflow

Specifications are **not auto-generated**. The workflow is:

1. **Run experiment** → generates empirical baseline
2. **Review baseline** → human examines results
3. **Create specification** → human writes spec file (possibly using a CLI tool)
4. **Commit specification** → spec is version-controlled with code

This explicit approval step is critical. It prevents blind acceptance of observed behavior and forces deliberate threshold selection.

### 3.6 Probabilistic Conformance Test

A conformance test validates that the system behavior matches an approved specification:

```java
@ProbabilisticTest(spec = "usecase.json.generation:v3")
void jsonGenerationMeetsSpec() {
    // The framework:
    // 1. Loads the specification
    // 2. Resolves the use case
    // 3. Applies the execution context from the spec
    // 4. Runs samples per the spec's configuration
    // 5. Interprets UseCaseResult according to spec's successCriteria
    // 6. Determines pass/fail based on spec's minPassRate
}
```

**Key Design Decisions**:

1. **Spec reference is preferred over inline thresholds**: When `spec` is provided, the specification controls all test parameters. Inline parameters (samples, minPassRate) are ignored.

2. **Inline thresholds remain supported**: For backward compatibility and simple cases:
   ```java
   @ProbabilisticTest(samples = 100, minPassRate = 0.95)
   void simpleTest() { ... }
   ```

3. **Conflict resolution**: If both `spec` and inline parameters are provided, the framework issues a warning and uses the spec. This is explicit: specs override inline.

4. **Success criteria interpretation**: The specification's `successCriteria` expression is evaluated against each `UseCaseResult` to determine per-sample success.

---

## 4. Annotation & API Design

### 4.1 New Annotations

#### @UseCase

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UseCase {
    /**
     * Unique identifier for this use case.
     * Convention: dot-separated namespace (e.g., "usecase.email.validation").
     */
    String value();
    
    /**
     * Human-readable description of what this use case tests.
     */
    String description() default "";
}
```

#### @Experiment

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(ExperimentExtension.class)
public @interface Experiment {
    
    /** The use case ID to execute. */
    String useCase();
    
    /** Number of samples to execute. Default: 100. */
    int samples() default 100;
    
    /** Time budget in milliseconds. 0 = unlimited. */
    long timeBudgetMs() default 0;
    
    /** Token budget. 0 = unlimited. */
    long tokenBudget() default 0;
    
    /** Baseline output directory (relative to test resources). */
    String baselineOutputDir() default "punit/baselines";
    
    /** Whether to overwrite existing baselines. */
    boolean overwriteBaseline() default false;
}
```

#### @ExperimentContext

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ExperimentContexts.class)
public @interface ExperimentContext {
    
    /** Backend identifier (e.g., "llm", "sensor", "distributed"). */
    String backend();
    
    /** 
     * Key-value pairs for backend-specific configuration.
     * Format: "key = value" or "key = ${variableName}" for multi-config experiments.
     * 
     * Examples:
     *   "model = gpt-4"              // fixed value
     *   "model = ${model}"           // variable (substituted per config)
     *   "temperature = ${temp}"      // variable
     *   "maxTokens = 1000"           // fixed value
     */
    String[] template() default {};
    
    /**
     * @deprecated Use template() instead for clarity.
     * Retained for backward compatibility with single-config experiments.
     */
    @Deprecated
    String[] parameters() default {};
}
```

#### @ExperimentContexts (Container)

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExperimentContexts {
    ExperimentContext[] value();
}
```

### 4.2 Extended @ProbabilisticTest

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(ProbabilisticTestExtension.class)
public @interface ProbabilisticTest {
    
    // Existing parameters...
    int samples() default 100;
    double minPassRate() default 1.0;
    long timeBudgetMs() default 0;
    long tokenBudget() default 0;
    // ...
    
    // NEW: Specification reference
    /**
     * Reference to an execution specification.
     * Format: "useCaseId:version" (e.g., "usecase.json.generation:v3")
     * 
     * When provided:
     * - The specification controls samples, minPassRate, budgets, and context
     * - Inline parameter values are ignored (with a warning)
     * - Success criteria from the spec are used to evaluate each sample
     * 
     * When empty (default):
     * - Test behaves as before (inline parameters control execution)
     * - Success is determined by absence of AssertionError
     */
    String spec() default "";
    
    /**
     * The use case ID to execute (alternative to spec).
     * When provided without spec, the use case is executed with inline parameters.
     */
    String useCase() default "";
}
```

### 4.3 API Classes

#### UseCaseResult

```java
public final class UseCaseResult {
    private final Map<String, Object> values;
    private final Instant timestamp;
    private final Duration executionTime;
    private final Map<String, Object> metadata;
    
    private UseCaseResult(Builder builder) { ... }
    
    public static Builder builder() { return new Builder(); }
    
    public <T> Optional<T> getValue(String key, Class<T> type) { ... }
    public <T> T getValue(String key, Class<T> type, T defaultValue) { ... }
    
    public boolean getBoolean(String key) { return getBoolean(key, false); }
    public boolean getBoolean(String key, boolean defaultValue) { ... }
    public int getInt(String key) { return getInt(key, 0); }
    public int getInt(String key, int defaultValue) { ... }
    public double getDouble(String key) { return getDouble(key, 0.0); }
    public double getDouble(String key, double defaultValue) { ... }
    public String getString(String key) { return getString(key, ""); }
    public String getString(String key, String defaultValue) { ... }
    
    public Map<String, Object> getAllValues() { 
        return Collections.unmodifiableMap(values); 
    }
    public Map<String, Object> getAllMetadata() { 
        return Collections.unmodifiableMap(metadata); 
    }
    
    public Instant getTimestamp() { return timestamp; }
    public Duration getExecutionTime() { return executionTime; }
    
    public static final class Builder {
        private final Map<String, Object> values = new LinkedHashMap<>();
        private final Map<String, Object> metadata = new LinkedHashMap<>();
        private Instant timestamp = Instant.now();
        private Duration executionTime = Duration.ZERO;
        
        public Builder value(String key, Object val) { 
            values.put(key, val); 
            return this; 
        }
        public Builder meta(String key, Object val) { 
            metadata.put(key, val); 
            return this; 
        }
        public Builder executionTime(Duration duration) { 
            this.executionTime = duration; 
            return this; 
        }
        public UseCaseResult build() { return new UseCaseResult(this); }
    }
}
```

#### UseCaseContext

```java
public interface UseCaseContext {
    
    /** Returns the backend identifier. */
    String getBackend();
    
    /** Returns a parameter value, or empty if not present. */
    <T> Optional<T> getParameter(String key, Class<T> type);
    
    /** Returns a parameter value with a default. */
    <T> T getParameter(String key, Class<T> type, T defaultValue);
    
    /** Returns all parameters as an immutable map. */
    Map<String, Object> getAllParameters();
    
    /** Returns true if this context has the given backend. */
    default boolean hasBackend(String backend) {
        return backend.equals(getBackend());
    }
}
```

#### SuccessCriteria (Evaluation)

```java
public interface SuccessCriteria {
    
    /**
     * Evaluates whether a UseCaseResult represents success.
     * 
     * @param result the result to evaluate
     * @return true if the result meets success criteria
     */
    boolean isSuccess(UseCaseResult result);
    
    /**
     * Returns a human-readable description of the criteria.
     */
    String getDescription();
    
    /**
     * Parses a criteria expression from a specification.
     * Expression syntax is a simple boolean expression over value keys.
     * Examples:
     *   "isValid == true"
     *   "score >= 0.8"
     *   "isValid == true && errorCount == 0"
     */
    static SuccessCriteria parse(String expression) { ... }
}
```

---

## 5. Data Flow

### 5.0 Canonical Flow Overview

The complete data flow from use case definition to conformance testing:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            CANONICAL FLOW                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌───────────────┐                                                           │
│  │   Use Case    │  Test/experiment-only function that calls production      │
│  │               │  code and returns UseCaseResult                           │
│  └───────┬───────┘                                                           │
│          │                                                                   │
│          ▼                                                                   │
│  ┌───────────────────────────────────────┐                                   │
│  │   ExperimentDesign                    │  Declarative description of       │
│  │   (Factors + Levels)                  │  what to explore                  │
│  │                                       │                                   │
│  │   model: [gpt-3.5, gpt-4, gpt-4o]    │  ← ExperimentFactors              │
│  │   temperature: [0.0, 0.2, 0.5]       │  ← ExperimentLevels               │
│  └───────┬───────────────────────────────┘                                   │
│          │                                                                   │
│          ▼                                                                   │
│  ┌───────────────────────────────────────┐                                   │
│  │   ExperimentConfig (1..N)             │  Concrete combinations to         │
│  │                                       │  execute                          │
│  │   Config 1: {gpt-3.5, 0.0}           │                                   │
│  │   Config 2: {gpt-3.5, 0.2}           │  ← Each is fully specified        │
│  │   Config 3: {gpt-4, 0.0}             │  ← Each produces a baseline       │
│  │   ...                                 │                                   │
│  └───────┬───────────────────────────────┘                                   │
│          │                                                                   │
│          ▼                                                                   │
│  ┌───────────────────────────────────────┐                                   │
│  │   Empirical Baselines                 │  Machine-generated records        │
│  │                                       │  of observed behavior             │
│  │   config-001.yaml (93.5% success)     │                                   │
│  │   config-002.yaml (88.2% success)     │  ← One per ExperimentConfig       │
│  │   SUMMARY.yaml (aggregated)           │                                   │
│  └───────┬───────────────────────────────┘                                   │
│          │                                                                   │
│          │ (human reviews and approves)                                      │
│          ▼                                                                   │
│  ┌───────────────────────────────────────┐                                   │
│  │   Execution Specification             │  Human-approved contract          │
│  │                                       │                                   │
│  │   useCaseId: json.generation:v1       │                                   │
│  │   minPassRate: 0.90                   │  ← Derived from baselines        │
│  │   approvedBy: jane@example.com        │  ← Explicit approval             │
│  └───────┬───────────────────────────────┘                                   │
│          │                                                                   │
│          ▼                                                                   │
│  ┌───────────────────────────────────────┐                                   │
│  │   Probabilistic Conformance Tests     │  CI-gated validation              │
│  │                                       │                                   │
│  │   @ProbabilisticTest(spec = "...")    │                                   │
│  │   → PASS / FAIL                       │  ← Binary verdict                │
│  └───────────────────────────────────────┘                                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.1 Experiment Flow: ExperimentDesign → Empirical Baselines

```
┌─────────────────┐
│  @Experiment    │
│  annotation     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐     ┌─────────────────┐
│  Resolve        │────▶│  @UseCase       │
│  use case ID    │     │  method         │
└────────┬────────┘     └────────┬────────┘
         │                       │
         ▼                       │
┌─────────────────┐              │
│  Parse          │              │
│ ExperimentDesign│              │
│ (Factors/Levels)│              │
└────────┬────────┘              │
         │                       │
         ▼                       │
┌─────────────────────────────────────────────────────────────────┐
│                For each ExperimentConfig:                        │
│                                                                  │
│  ┌─────────────────┐                                             │
│  │ Build context   │                                             │
│  │ from config     │                                             │
│  │ (factor→level)  │                                             │
│  └────────┬────────┘                                             │
│           │                                                      │
│           ▼                                                      │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │              For each sample (1..N):                        │ │
│  │                                                             │ │
│  │  ┌─────────────┐   ┌─────────────┐   ┌───────────────────┐ │ │
│  │  │ Invoke      │──▶│ Production  │──▶│ UseCaseResult     │ │ │
│  │  │ use case    │   │ code        │   │ collected         │ │ │
│  │  └─────────────┘   └─────────────┘   └───────────────────┘ │ │
│  └─────────────────────────────────────────────────────────────┘ │
│           │                                                      │
│           ▼                                                      │
│  ┌─────────────────┐     ┌─────────────────┐                     │
│  │ Aggregate       │────▶│ Compute         │                     │
│  │ results         │     │ statistics      │                     │
│  └─────────────────┘     └────────┬────────┘                     │
│                                   │                              │
│                                   ▼                              │
│                         ┌─────────────────┐                      │
│                         │ Generate        │                      │
│                         │ per-config      │                      │
│                         │ baseline        │                      │
│                         └────────┬────────┘                      │
│                                  │                               │
│  ┌───────────────────────────────┼─────────────────────────────┐ │
│  │ Goal check: achieved? ────────┼──────▶ Yes: terminate early │ │
│  │                               │        No: continue         │ │
│  └───────────────────────────────┼─────────────────────────────┘ │
└──────────────────────────────────┼───────────────────────────────┘
                                   │
                                   ▼
                        ┌─────────────────────┐
                        │ Generate SUMMARY    │
                        │ (aggregated report) │
                        └──────────┬──────────┘
                                   │
                                   ▼
                        ┌─────────────────────┐
                        │ Publish via         │
                        │ TestReporter        │
                        └─────────────────────┘
```

### 5.2 Specification Creation Flow (Manual)

```
┌─────────────────────┐
│  Empirical Baseline │
│  (generated file)   │
└──────────┬──────────┘
           │
           │ (developer reviews)
           ▼
┌─────────────────────┐
│  Decision Point     │
│                     │
│  "Is 93.5% success  │
│   rate acceptable?" │
│                     │
│  "What threshold    │
│   should we set?"   │
└──────────┬──────────┘
           │
           │ (developer decides)
           ▼
┌─────────────────────┐
│  Create Spec File   │
│                     │
│  - Set minPassRate  │
│  - Set budgets      │
│  - Set version      │
│  - Add approval     │
└──────────┬──────────┘
           │
           │ (commit to VCS)
           ▼
┌─────────────────────┐
│  Execution Spec     │
│  (in repository)    │
└─────────────────────┘
```

### 5.3 Probabilistic Test Flow: Spec → Verdict

```
┌─────────────────────────────┐
│  @ProbabilisticTest         │
│  (spec = "usecase.x:v3")    │
└─────────────┬───────────────┘
              │
              ▼
┌─────────────────────────────┐
│  SpecificationRegistry      │
│  .resolve("usecase.x:v3")   │
└─────────────┬───────────────┘
              │
              ▼
┌─────────────────────────────┐     ┌─────────────────────────────┐
│  ExecutionSpecification     │────▶│  Resolve @UseCase           │
│  loaded from file           │     │  by useCaseId               │
└─────────────┬───────────────┘     └─────────────┬───────────────┘
              │                                   │
              ▼                                   ▼
┌─────────────────────────────┐     ┌─────────────────────────────┐
│  Apply execution context    │     │  Build SuccessCriteria      │
│  from spec                  │     │  from spec expression       │
└─────────────┬───────────────┘     └─────────────┬───────────────┘
              │                                   │
              └─────────────────┬─────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    For each sample (1..N):                       │
│                                                                  │
│  ┌─────────────────┐     ┌─────────────────┐     ┌───────────┐  │
│  │  Invoke         │────▶│  UseCaseResult  │────▶│  Evaluate │  │
│  │  use case       │     │  returned       │     │  criteria │  │
│  └─────────────────┘     └─────────────────┘     └─────┬─────┘  │
│                                                        │        │
│  ┌─────────────────┐                                   │        │
│  │  success/fail   │◀──────────────────────────────────┘        │
│  │  recorded       │                                            │
│  └─────────────────┘                                            │
│                                                                  │
│  (Early termination checks as in existing punit)                 │
└───────────────────────────────────────────────────────┬─────────┘
                                                        │
                                                        ▼
                                             ┌─────────────────────┐
                                             │  FinalVerdictDecider│
                                             │  .isPassing()       │
                                             └──────────┬──────────┘
                                                        │
                                           ┌────────────┴────────────┐
                                           │                         │
                                           ▼                         ▼
                                    ┌─────────────┐          ┌─────────────┐
                                    │  PASS       │          │  FAIL       │
                                    │  (gates CI) │          │  (gates CI) │
                                    └─────────────┘          └─────────────┘
```

### 5.4 Specification → Production Configuration Flow

Specifications can also inform production configuration **without** involving use cases:

```
┌─────────────────────────────┐
│  Execution Specification    │
│  (usecase.json.gen:v3)      │
└─────────────┬───────────────┘
              │
              │ (build/deploy process reads)
              ▼
┌─────────────────────────────┐
│  Extract configuration:     │
│                             │
│  - model: gpt-4             │
│  - temperature: 0.7         │
│  - timeout: 2000ms          │
└─────────────┬───────────────┘
              │
              │ (inject into production)
              ▼
┌─────────────────────────────┐
│  Production Configuration   │
│  (no use case involvement)  │
└─────────────────────────────┘
```

This flow is **out of scope** for the punit framework itself but is enabled by the specification format. Production tooling (build plugins, config generators) can consume specification files.

---

## 6. Governance & Safety Mechanisms

### 6.1 Discouraging Arbitrary Thresholds

The framework must actively guide developers toward empirical threshold selection:

#### 6.1.1 Warning on Inline Thresholds Without Prior Baseline

When `@ProbabilisticTest` uses inline `minPassRate` and no empirical baseline exists for the use case:

```
⚠️ WARNING: @ProbabilisticTest for 'validateEmail' uses minPassRate=0.95 
   without an empirical baseline. This threshold may be arbitrary.
   
   Recommendation: Run an @Experiment first to establish empirical behavior,
   then create a specification based on observed results.
```

This is a **warning**, not an error. The test still executes.

#### 6.1.2 Spec-First Guidance in Documentation

Documentation and examples should consistently demonstrate the spec-driven pattern as the primary approach.

### 6.2 Surfacing Insufficient Empirical Data

When a specification references a baseline with insufficient samples:

```
⚠️ WARNING: Specification 'usecase.json.gen:v3' is based on baseline with
   only 50 samples. Confidence interval for success rate is wide: [0.82, 0.98].
   
   Recommendation: Run additional experiments to narrow the confidence interval
   before relying on this specification in CI.
```

The framework computes confidence intervals and warns when they are too wide (configurable threshold).

### 6.3 Preventing Blind Acceptance of Poor Results

Specifications require explicit approval metadata:

```yaml
# Specification file MUST contain approval information
approvedAt: 2026-01-04T16:00:00Z
approvedBy: jane.engineer@example.com
approvalNotes: "Approved after review of Jan 4 experiment results"
```

When loading a specification without approval metadata:

```
❌ ERROR: Specification 'usecase.json.gen:v3' lacks approval metadata.
   Specifications must be explicitly approved before use in @ProbabilisticTest.
   
   Add 'approvedAt', 'approvedBy', and 'approvalNotes' to the specification file.
```

This is an **error** that fails the test.

### 6.4 Baseline-Spec Drift Detection

When running a probabilistic test, if the observed behavior significantly differs from the baseline that informed the spec:

```
⚠️ WARNING: Observed success rate (0.78) is significantly lower than
   baseline (0.935) for specification 'usecase.json.gen:v3'.
   
   This may indicate:
   - System regression
   - Environment differences
   - Baseline that no longer reflects current behavior
   
   Consider re-running experiments and updating the specification.
```

### 6.5 Warnings vs Errors Summary

| Condition | Severity | Behavior |
|-----------|----------|----------|
| Inline threshold without baseline | Warning | Test executes with warning |
| Baseline with insufficient samples | Warning | Spec loads with warning |
| Spec without approval metadata | Error | Test fails |
| Spec references missing baseline | Error | Test fails |
| Observed rate significantly below baseline | Warning | Test may still pass if threshold met |
| Use case ID not found | Error | Test fails |

---

## 7. Execution & Reporting Semantics

### 7.1 Experiment Reporting (No Verdict)

Experiments publish results without verdicts:

```
┌─────────────────────────────────────────────────────────────────┐
│ EXPERIMENT: JsonExperiments.measureJsonGenerationPerformance    │
├─────────────────────────────────────────────────────────────────┤
│ Use Case: usecase.json.generation                               │
│ Context: llm (model=gpt-4, temperature=0.7)                     │
│                                                                 │
│ Samples Executed: 200/200                                       │
│ Termination: COMPLETED                                          │
│ Elapsed: 87432ms                                                │
│ Tokens Consumed: 45230                                          │
│                                                                 │
│ Observations:                                                   │
│   Success Rate: 93.5% ± 1.7% (95% CI: [90.1%, 96.9%])           │
│                                                                 │
│   Failure Distribution:                                         │
│     invalidJson: 8 (4.0%)                                       │
│     missingRequiredField: 3 (1.5%)                              │
│     timeout: 2 (1.0%)                                           │
│                                                                 │
│ Baseline Generated: punit/baselines/usecase.json.generation/    │
│                     2026-01-04T15:30:00Z.yaml                   │
│                                                                 │
│ NOTE: This is an experiment. No pass/fail verdict is produced.  │
│       Use the generated baseline to create a specification.     │
└─────────────────────────────────────────────────────────────────┘
```

Key aspects:
- No "PASS" or "FAIL" status
- Statistical summaries with confidence intervals
- Clear note that this is informational only
- Pointer to generated baseline file

### 7.2 Probabilistic Test Reporting (With Verdict)

Conformance tests produce standard punit reports with spec provenance:

```
┌─────────────────────────────────────────────────────────────────┐
│ TEST: JsonConformanceTests.jsonGenerationMeetsSpec              │
├─────────────────────────────────────────────────────────────────┤
│ Status: PASSED                                                  │
│                                                                 │
│ Specification: usecase.json.generation:v3                       │
│ Use Case: usecase.json.generation                               │
│ Context: llm (model=gpt-4, temperature=0.7)                     │
│                                                                 │
│ Samples Executed: 100/100                                       │
│ Successes: 94                                                   │
│ Failures: 6                                                     │
│ Observed Pass Rate: 94.00%                                      │
│ Required Pass Rate: 90.00% (from spec)                          │
│ Termination: COMPLETED                                          │
│ Elapsed: 43216ms                                                │
│ Tokens: 22615/50000                                             │
│                                                                 │
│ Provenance:                                                     │
│   Spec: usecase.json.generation:v3                              │
│   Based on baseline: 2026-01-04T15:30:00Z.yaml                  │
│   Approved: 2026-01-04 by jane.engineer@example.com             │
└─────────────────────────────────────────────────────────────────┘
```

Key aspects:
- Clear PASS/FAIL verdict
- Provenance chain (Use Case → ExperimentDesign → Config → Baseline → Spec → Test)
- Threshold source clearly marked "(from spec)"

### 7.3 TestReporter Entry Keys

New keys for experiment/spec-aware reporting:

| Key | Description |
|-----|-------------|
| `punit.mode` | `EXPERIMENT` or `CONFORMANCE` |
| `punit.useCaseId` | The use case identifier |
| `punit.specId` | Specification ID (if spec-driven) |
| `punit.specVersion` | Specification version |
| `punit.baselineSource` | Source baseline file path |
| `punit.successCriteria` | Success criteria expression |
| `punit.context.backend` | Backend identifier |
| `punit.context.*` | Backend-specific context parameters |
| `punit.stats.successRate` | Observed success rate |
| `punit.stats.confidenceInterval` | 95% confidence interval (experiments) |
| `punit.stats.failureDistribution` | JSON map of failure modes (experiments) |
| `punit.baseline.outputPath` | Path to generated baseline (experiments) |

---

## 8. Extensibility Model

### 8.1 Pluggable Backends

Backends provide domain-specific execution context. They are plugged in via SPI:

```java
public interface ExperimentBackend {
    
    /** Returns the backend identifier (e.g., "llm", "sensor"). */
    String getId();
    
    /** Builds a UseCaseContext from annotation parameters. */
    UseCaseContext buildContext(Map<String, String> parameters);
    
    /** Validates that required parameters are present. */
    void validateParameters(Map<String, String> parameters) 
        throws IllegalArgumentException;
    
    /** Returns parameter documentation for this backend. */
    Map<String, ParameterDoc> getParameterDocumentation();
}
```

#### 8.1.1 Example: LLM Backend

```java
public class LlmExperimentBackend implements ExperimentBackend {
    
    @Override
    public String getId() { return "llm"; }
    
    @Override
    public UseCaseContext buildContext(Map<String, String> parameters) {
        String model = parameters.getOrDefault("model", "gpt-4");
        double temperature = Double.parseDouble(
            parameters.getOrDefault("temperature", "0.7"));
        String provider = parameters.getOrDefault("provider", "openai");
        
        return new LlmUseCaseContext(model, temperature, provider);
    }
    
    @Override
    public void validateParameters(Map<String, String> parameters) {
        // Validate model is supported, temperature in range, etc.
    }
    
    @Override
    public Map<String, ParameterDoc> getParameterDocumentation() {
        return Map.of(
            "model", new ParameterDoc("LLM model name", "gpt-4"),
            "temperature", new ParameterDoc("Sampling temperature", "0.7"),
            "provider", new ParameterDoc("LLM provider", "openai")
        );
    }
}
```

### 8.2 Backend Registration

Backends are discovered via ServiceLoader:

```
META-INF/services/org.javai.punit.experiment.spi.ExperimentBackend
```

Contents:
```
org.javai.punit.backend.llm.LlmExperimentBackend
org.javai.punit.backend.sensor.SensorExperimentBackend
```

### 8.3 Core Remains Clean

The punit core:
- Defines the `ExperimentBackend` SPI
- Provides a default "generic" backend that passes parameters through unchanged
- Has no compile-time dependency on any specific backend

LLM-specific code lives entirely in `punit-backend-llm`:
- `LlmExperimentBackend`
- `LlmUseCaseContext`
- LLM-specific utilities and helpers

### 8.4 Supporting Future Stochastic Domains

The abstraction supports any domain where:
1. Execution produces variable outcomes
2. Statistical aggregation is meaningful
3. Empirical thresholds can be established

Examples:
- **Distributed systems**: Network partitions, leader election, consensus
- **Hardware sensors**: Noise tolerance, calibration drift
- **Randomized algorithms**: Probabilistic data structures, Monte Carlo methods
- **External APIs**: Rate limits, transient failures, SLA compliance

---

## 9. Phased Implementation Plan

### Phase 1: Core Use Case and Result Abstractions

**Goals**:
- Establish the foundational abstractions for use cases and results
- Define the data model without execution machinery

**Scope**:
- `@UseCase` annotation
- `UseCaseResult` class with builder pattern
- `UseCaseContext` interface
- `UseCaseRegistry` for discovery and caching
- Unit tests for all new classes

**Non-Goals**:
- No execution engine changes
- No experiment annotation
- No baseline generation
- No spec resolution

**Deliverables**:
1. `org.javai.punit.experiment.api.UseCase` annotation
2. `org.javai.punit.experiment.model.UseCaseResult` class
3. `org.javai.punit.experiment.model.UseCaseContext` interface
4. `org.javai.punit.experiment.engine.UseCaseRegistry` class
5. Comprehensive unit tests

**Dependencies**: None (greenfield)

**Estimated Effort**: 2-3 days

---

### Phase 2: Single-Config Experiment Mode

**Goals**:
- Enable exploratory execution via `@Experiment`
- Generate empirical baselines from experiment runs
- Support single-config experiments (one `ExperimentConfig`)

**Scope**:
- `@Experiment` annotation (single-config attributes)
- `@ExperimentContext` annotation
- `ExperimentExtension` (JUnit 5 extension)
- `ExperimentResultAggregator` (extends/parallels `SampleResultAggregator`)
- `EmpiricalBaselineGenerator` for producing baseline files
- `EmpiricalBaseline` model class
- YAML serialization/deserialization for baselines (JSON as optional alternative)
- Integration with existing budget monitoring

**Non-Goals**:
- No multi-config experiments (Phase 2b)
- No spec resolution
- No conformance testing changes
- No pluggable backends (use generic backend)

**Deliverables**:
1. `@Experiment` and `@ExperimentContext` annotations
2. `ExperimentExtension` implementing `TestTemplateInvocationContextProvider`
3. `ExperimentResultAggregator` for collecting results
4. `EmpiricalBaseline` model and `EmpiricalBaselineGenerator`
5. YAML baseline file format and I/O (with JSON fallback support)
6. Integration tests demonstrating single-config experiment execution

**Dependencies**: Phase 1

**Estimated Effort**: 4-5 days

---

### Phase 2b: Multi-Config Experiments

**Goals**:
- Enable experiments with explicit `ExperimentConfig` lists
- Generate per-config baselines and aggregated reports
- Support goal-based early termination

**Scope**:
- `@ExperimentDesign`, `@Config`, `@ExperimentGoal` annotations
- Extended `@Experiment` annotation (`samplesPerConfig`, `experimentId`)
- Explicit config list parsing (annotation and YAML)
- Sequential config execution with shared budget
- Goal evaluation after each config (early termination)
- Per-config baseline generation
- Aggregated `SUMMARY.yaml` report generation with analysis
- YAML experiment design file support (for complex experiments)

**Non-Goals**:
- No parallel config execution (future enhancement)
- No partial execution/resume
- No config filtering/re-running
- No Cartesian product generation (configs are explicitly listed)

**Deliverables**:
1. `@ExperimentDesign`, `@Config`, `@ExperimentGoal` annotations
2. `ExperimentDesignParser` for annotation and YAML config lists
3. `GoalEvaluator` for checking early termination criteria
4. `MultiConfigExperimentExecutor` for sequential config execution
5. Per-config baseline output in experiment subdirectory
6. `ExperimentSummaryGenerator` for aggregated reports with analysis
7. YAML experiment design file parser
8. Integration tests demonstrating early termination on goal achievement

**Dependencies**: Phase 2

**Estimated Effort**: 4-5 days

---

### Phase 2c: Adaptive Experiments

**Goals**:
- Enable dynamic level generation for adaptive factors
- Implement the iteration loop with feedback-driven refinement
- Provide the SPI for refinement strategies
- Support sourcing initial levels from production code via `Supplier`

**Scope**:
- `AdaptiveFactor<T>` interface for dynamic level generation
- `AdaptiveLevels` builder for fluent adaptive factor definition
  - `startingFrom(T value)` for static initial levels
  - `startingFrom(Supplier<T> supplier)` for production-sourced initial levels
- `AdaptiveFactorRegistry` for registering named `Supplier`s (for YAML reference)
- `RefinementStrategy<T>` SPI interface
- `IterationFeedback` and `FailureObservation` feedback model
- Iteration loop in `ExperimentExtension` for adaptive experiments
- Adaptive baseline generation with full iteration history
- `maxIterations` as mandatory parameter (governance)
- Token budget accounting for refinement calls
- YAML adaptive factor definition support (including `initialLevelSupplier` reference)

**Non-Goals**:
- No built-in refinement strategies (backend-specific; llmx provides LLM strategies)
- No parallel iteration (inherently sequential)
- No automatic strategy selection

**Deliverables**:
1. `org.javai.punit.experiment.api.AdaptiveFactor<T>` interface
2. `org.javai.punit.experiment.api.AdaptiveLevels` builder (with `Supplier` support)
3. `org.javai.punit.experiment.api.AdaptiveFactorRegistry` for named `Supplier` registration
4. `org.javai.punit.experiment.spi.RefinementStrategy<T>` SPI
5. `org.javai.punit.experiment.model.IterationFeedback` feedback model
6. `org.javai.punit.experiment.model.FailureObservation` for failure analysis
7. `AdaptiveExperimentExecutor` with iteration loop
8. Adaptive baseline format with iteration history (records initial level source)
9. `LevelSerialization` configuration for handling complex/large levels
10. External file reference support (`$ref`) for large adaptive factor levels
11. `LevelSerializer<T>` SPI for custom object serialization
12. YAML adaptive factor parsing (including `initialLevelSupplier` resolution)
13. Integration tests with mock refinement strategy
14. Documentation for refinement strategy development, `Supplier` registration, and level serialization

**Dependencies**: Phase 2b (multi-config experiments provide the config execution foundation)

**Estimated Effort**: 5-6 days

---

### Phase 3: Specification Representation and Registry

**Goals**:
- Define the specification data model
- Implement spec loading and resolution

**Scope**:
- `ExecutionSpecification` model class
- `SpecificationRegistry` for loading and resolving specs
- YAML format for specification files (JSON as optional alternative)
- Spec validation (required fields, approval metadata)
- `SuccessCriteria` expression parsing and evaluation
- Warnings and errors for governance (missing approval, insufficient samples)

**Non-Goals**:
- No integration with `@ProbabilisticTest` yet
- No baseline-spec drift detection

**Deliverables**:
1. `org.javai.punit.spec.model.ExecutionSpecification` class
2. `org.javai.punit.spec.model.SuccessCriteria` interface and parser
3. `org.javai.punit.spec.registry.SpecificationRegistry`
4. YAML spec format and I/O (with JSON fallback support)
5. Validation logic with clear error messages
6. Unit tests for all spec handling

**Dependencies**: Phase 1 (for use case ID references)

**Estimated Effort**: 3-4 days

---

### Phase 4: Spec-Driven Probabilistic Tests

**Goals**:
- Extend `@ProbabilisticTest` to consume specifications
- Integrate use case execution with probabilistic testing

**Scope**:
- Add `spec` and `useCase` attributes to `@ProbabilisticTest`
- Modify `ProbabilisticTestExtension` to resolve specs
- Integrate `SuccessCriteria` evaluation into sample aggregation
- Handle spec vs inline parameter conflicts
- Implement baseline-spec drift detection (warnings)
- Implement provenance reporting

**Non-Goals**:
- No pluggable backends yet
- No CLI tooling

**Deliverables**:
1. Extended `@ProbabilisticTest` annotation
2. Modified `ProbabilisticTestExtension` with spec resolution
3. `SuccessCriteria`-based sample evaluation
4. Conflict resolution logic (spec overrides inline)
5. Drift detection and warning mechanism
6. Provenance fields in test reports
7. Integration tests demonstrating spec-driven tests

**Dependencies**: Phase 1, Phase 2, Phase 3

**Estimated Effort**: 4-5 days

---

### Phase 5: Pluggable Backend Infrastructure

**Goals**:
- Establish the SPI for experiment backends
- Implement the generic (passthrough) backend

**Scope**:
- `ExperimentBackend` SPI interface
- `ExperimentBackendRegistry` for backend discovery (ServiceLoader)
- Generic backend implementation (default, passthrough)
- Backend parameter validation
- Documentation for backend development

**Non-Goals**:
- No domain-specific backends in this phase (those are extensions)

**Deliverables**:
1. `org.javai.punit.experiment.spi.ExperimentBackend` interface
2. `org.javai.punit.experiment.spi.ExperimentBackendRegistry` with ServiceLoader discovery
3. `org.javai.punit.experiment.backend.GenericExperimentBackend` (default)
4. Backend developer documentation (SPI guide)

**Dependencies**: Phase 2

**Estimated Effort**: 2-3 days

---

### Phase 6: LLM Backend Extension (llmx)

**Goals**:
- Implement an LLM-specific backend extension as a reference implementation
- Demonstrate the extensibility model in practice
- Provide a production-ready backend for LLM-based experiments

**Scope**:
- `org.javai.punit.llmx` package (separate from core)
- `LlmExperimentBackend` implementing `ExperimentBackend` SPI
- `LlmUseCaseContext` with LLM-specific configuration (model, temperature, provider, maxTokens, etc.)
- LLM-specific `ExperimentFactor` presets (common model IDs, temperature ranges)
- Token consumption tracking and reporting
- Provider abstraction (OpenAI, Anthropic, etc.)
- Error classification (rate limits, context length, invalid responses)
- **Adaptive experiment refinement strategies** (LLM-based prompt refinement)

**Non-Goals**:
- No actual LLM API implementations (users bring their own client)
- No prompt engineering utilities beyond adaptive refinement (out of scope)

**Package Structure**:
```
org.javai.punit.llmx/
├── LlmExperimentBackend.java       # SPI implementation
├── LlmUseCaseContext.java          # LLM-specific context
├── LlmResultValues.java            # Common value keys (tokens, latency)
├── model/
│   ├── LlmProvider.java            # Provider enum/abstraction
│   ├── LlmModel.java               # Model identifier
│   └── LlmResponse.java            # Standardized response wrapper
├── factors/
│   ├── CommonModels.java           # Preset ExperimentLevels for models
│   └── TemperatureRange.java       # Preset ExperimentLevels for temperature
├── refinement/                     # Adaptive experiment support
│   ├── LlmPromptRefinementStrategy.java  # RefinementStrategy<String> impl
│   ├── LlmRefinementConfig.java          # Configuration for refinement
│   └── FailureAnalyzer.java              # Extracts patterns from failures
└── spi/
    └── LlmClient.java              # User-provided client interface
```

**Dependency Constraint** ⚠️:
> **The core punit framework MUST NOT depend on llmx.**
> 
> Dependencies flow one direction only: `llmx → punit-core`
> 
> This constraint ensures:
> - Core framework remains domain-neutral
> - llmx can be packaged separately in the future
> - Users without LLM needs don't pull unnecessary dependencies

**Verification**:
- Build must fail if any class in `org.javai.punit` (core) imports from `org.javai.punit.llmx`
- Gradle/Maven module structure should enforce this (or ArchUnit tests)

**Deliverables**:
1. `org.javai.punit.llmx.LlmExperimentBackend` (SPI implementation)
2. `org.javai.punit.llmx.LlmUseCaseContext` with model, temperature, provider, maxTokens
3. `org.javai.punit.llmx.LlmResultValues` constants for common value keys
4. `org.javai.punit.llmx.spi.LlmClient` interface for user-provided clients
5. Common model/temperature presets for ExperimentDesign
6. `org.javai.punit.llmx.refinement.LlmPromptRefinementStrategy` - LLM-based prompt refinement for adaptive experiments
7. `org.javai.punit.llmx.refinement.FailureAnalyzer` - extracts patterns from failure observations
8. Unit tests for llmx components (including refinement strategies)
9. ServiceLoader registration (`META-INF/services/`)

**Dependencies**: Phase 5, Phase 2c (adaptive experiments)

**Estimated Effort**: 4-5 days

---

### Phase 7: Canonical Flow Examples

**Goals**:
- Demonstrate the complete canonical flow through working examples
- Provide reference implementations for users to learn from
- Validate the framework design through realistic use cases

**Scope**:
- Examples in the project's test suite (`src/test/java`)
- End-to-end demonstration of the canonical flow:
  1. Use Case definition
  2. ExperimentDesign (Factors + Levels)
  3. ExperimentConfig execution
  4. Empirical Baseline generation
  5. Execution Specification creation
  6. Probabilistic Conformance Test execution
- Single-config, multi-config, and adaptive experiment examples
- Examples using the generic backend
- Examples using the llmx backend (with mock LLM client)

**Example Scenarios**:

1. **JSON Generation (llmx)**
   - Use Case: Generate valid JSON from natural language
   - ExperimentDesign: `model` × `temperature` factors
   - Success Criteria: `isValidJson == true && hasRequiredFields == true`
   - Full flow from experiment to conformance test

2. **Stochastic Algorithm (generic)**
   - Use Case: Probabilistic sorting with random pivots
   - ExperimentDesign: `arraySize` × `pivotStrategy` factors
   - Success Criteria: `isSorted == true && comparisons < threshold`
   - Demonstrates domain-neutral application

3. **Goal-Based Early Termination (llmx)**
   - Use Case: Find cheapest acceptable model
   - ExperimentDesign: Multiple configs ordered by cost
   - ExperimentGoal: `successRate >= 0.90`
   - Demonstrates early termination on goal achievement

4. **Adaptive Prompt Discovery (llmx)**
   - Use Case: Text summarization
   - AdaptiveFactor: `systemPrompt` with LLM-based refinement
   - ExperimentGoal: `successRate >= 0.90`
   - Demonstrates iterative level discovery with feedback-driven refinement

**Example Structure**:
```
src/test/java/org/javai/punit/examples/
├── canonicalflow/
│   ├── JsonGenerationCanonicalFlowTest.java
│   ├── StochasticAlgorithmCanonicalFlowTest.java
│   ├── EarlyTerminationCanonicalFlowTest.java
│   └── AdaptivePromptDiscoveryTest.java
├── usecases/
│   ├── JsonGenerationUseCase.java
│   ├── StochasticSortUseCase.java
│   ├── SummarizationUseCase.java
│   └── ...
└── mock/
    ├── MockLlmClient.java
    └── MockRefinementStrategy.java
```

**Non-Goals**:
- Not a tutorial (that's documentation)
- Not exhaustive coverage of all features

**Deliverables**:
1. `JsonGenerationCanonicalFlowTest` - complete llmx example
2. `StochasticAlgorithmCanonicalFlowTest` - generic backend example
3. `EarlyTerminationCanonicalFlowTest` - goal-based termination example
4. `AdaptivePromptDiscoveryTest` - adaptive experiment example with LLM refinement
5. Supporting use case implementations
6. Mock LLM client and mock refinement strategy for testing
7. Sample baseline and spec files in `src/test/resources/punit/`

**Dependencies**: Phase 6 (llmx), Phase 4 (spec-driven tests), Phase 2c (adaptive experiments)

**Estimated Effort**: 3-4 days

---

### Phase 8: Documentation, Migration, and Guardrails

**Goals**:
- Complete documentation for all new features
- Provide migration guidance for existing tests
- Implement all governance warnings/errors
- Polish and edge case handling

**Scope**:
- Javadoc for all public APIs
- User guide with examples
- Migration guide from inline-threshold tests to spec-driven tests
- llmx extension documentation
- Complete governance mechanism implementation
- Edge case testing and bug fixes
- Performance verification

**Non-Goals**:
- No CLI tooling (future enhancement)
- No IDE plugin support (future enhancement)

**Deliverables**:
1. Comprehensive Javadoc
2. User guide (README or separate documentation)
3. Migration guide
4. llmx extension guide
5. Complete governance warnings/errors
6. Edge case test coverage
7. Performance benchmarks

**Dependencies**: Phase 1-7

**Estimated Effort**: 3-4 days

---

### Phase Summary

| Phase | Description | Dependencies | Est. Days |
|-------|-------------|--------------|-----------|
| 1 | Core use case and result abstractions | None | 2-3 |
| 2 | Single-config experiment mode | Phase 1 | 4-5 |
| 2b | Multi-config experiments (ExperimentDesign) | Phase 2 | 4-5 |
| 2c | Adaptive experiments (AdaptiveFactor, RefinementStrategy) | Phase 2b | 5-6 |
| 3 | Specification representation and registry | Phase 1 | 3-4 |
| 4 | Spec-driven probabilistic tests | Phase 1, 2, 3 | 4-5 |
| 5 | Pluggable backend infrastructure | Phase 2 | 2-3 |
| 6 | LLM backend extension (llmx) | Phase 5, 2c | 4-5 |
| 7 | Canonical flow examples | Phase 4, 6, 2c | 3-4 |
| 8 | Documentation, migration, guardrails | Phase 1-7 | 3-4 |

**Total Estimated Effort**: 34-44 days

---

## 10. Out-of-Scope Clarifications

The following are explicitly **not** part of this extension:

### 10.1 Auto-Approval of Specifications

Specifications require explicit human approval. There is no mechanism for automatically promoting baselines to specifications. This is intentional: the approval step forces deliberation about what behavior is acceptable.

### 10.2 AutoML / Automatic Configuration Optimization

The framework does not:
- Automatically search for optimal model parameters
- Suggest configuration changes based on experiment results
- Tune thresholds to make tests pass

These would undermine the purpose of empirical specification.

### 10.3 Runtime Routing via Use Case IDs

Production code does **not** use use case IDs for routing, feature flags, or configuration. Use case IDs are test/experiment metadata only.

### 10.4 Production-Time Specification Validation

The framework does not validate production behavior against specifications at runtime. Specifications inform production configuration through the build/deploy process, but enforcement happens in tests, not production.

### 10.5 Distributed Experiment Execution

Experiments run within a single JVM. Distributed experiment coordination (across nodes, cloud, etc.) is out of scope.

### 10.6 Real-Time Baseline Updates

Baselines are generated at experiment completion, not continuously updated during execution.

### 10.7 Experiment Scheduling and Orchestration

Experiments are run on-demand via JUnit. There is no built-in scheduler for periodic experiment execution.

### 10.8 Visual Dashboard / UI

There is no web UI or dashboard. Results are reported via JUnit's standard mechanisms.

### 10.9 IDE-Specific Integration

IDE plugins for baseline editing, spec creation, or result visualization are not in scope.

---

## 11. Open Questions and Recommendations

| Question | Recommendation |
|----------|----------------|
| **Should specs support inheritance/composition?** | **No, not in v1.** Keep specs simple and flat. Composition can be added later if needed. |
| **Should baselines auto-expire?** | **No.** Baselines are historical records. Specs reference them; if a spec becomes stale, re-run experiments and create a new spec version. |
| **What if use case method throws an exception?** | **Record as a failure type in UseCaseResult.** Use case methods should catch expected exceptions and record them as values. Unexpected exceptions bubble up as test infrastructure failures. |
| **Can one spec reference multiple use cases?** | **No.** One spec = one use case. If you need to test multiple use cases together, create a composite use case. |
| **How to handle flaky use case implementations?** | **That's the point.** The framework measures flakiness. If a use case is too flaky, that's surfaced in the baseline, and the spec must accommodate it or the implementation must improve. |
| **Should success criteria support custom functions?** | **Not in v1.** Start with a simple expression language. Custom evaluators can be added via SPI in v2. |
| **Where should baseline/spec files live?** | **`src/test/resources/punit/`** by default. Configurable via annotation parameters and system properties. |
| **What file format for baselines/specs?** | **YAML is the default.** YAML supports comments (useful for approval notes and documentation), is more human-readable for nested structures, and version-controls well. JSON is supported as an optional alternative for tooling compatibility. The framework auto-detects format based on file extension (`.yaml`/`.yml` vs `.json`). |
| **Should the number of ExperimentConfigs be limited?** | **No.** The framework has built-in cost thresholds (time budget, token budget) which naturally limit execution. If budget is exhausted, execution stops and completed configs produce baselines. |
| **Can multi-config experiments be resumed after interruption?** | **No, not in v1.** Partial execution is not supported initially. Re-run the entire experiment if interrupted. |
| **Can ExperimentConfigs run in parallel?** | **No, not initially.** Configs execute sequentially. Parallel config execution may be added in a future version. |
| **Can you filter/re-run specific ExperimentConfigs?** | **Not in v1.** This capability may be added later. For now, re-run the entire experiment or create a smaller design. |
| **How do you create a spec from a multi-config experiment?** | **Human selection.** The framework generates a human-readable `SUMMARY.yaml` report with analysis. Humans review results, select the best config, and manually create a specification referencing that config's baseline. |

---

## 12. Glossary

| Term | Definition |
|------|------------|
| **Use Case** | A test/experiment-only function that invokes production code and returns a `UseCaseResult`. Reused by experiments and probabilistic tests. Identified by a use case ID. |
| **Use Case ID** | A unique string identifier for a use case (e.g., `usecase.json.generation`). |
| **UseCaseResult** | A neutral container of key-value observations (`Map<String, Object>` of values) produced by a use case invocation. Interpreted as data for measurement by experiments, as input for assertions by tests. |
| **Experiment** | Executes a use case across one or more `ExperimentConfig`s in experiment mode (no pass/fail, no CI gating). Produces empirical baselines. |
| **ExperimentDesign** | Declarative description of what is explored. Composed of `ExperimentFactor`s, each with one or more `ExperimentLevel`s. Defines the space of exploration. Static, reviewable, versionable. |
| **ExperimentFactor** | One independently varied dimension in an experiment (e.g., `model`, `temperature`, `retryPolicy`). Domain-neutral. |
| **ExperimentLevel** | One deliberately chosen setting of a factor. May be categorical (`gpt-4o`) or numeric (`0.2`). Finite and intentional. |
| **ExperimentConfig** | One concrete combination of `ExperimentLevel`s across all factors. Fully specified, executable. The unit of execution and observation. |
| **ExperimentGoal** | Optional criteria for early termination (e.g., `successRate >= 0.90`). When a config achieves the goal, remaining configs are skipped. |
| **Early Termination** | Stopping an experiment before all configs are tested, either due to goal achievement or budget exhaustion. |
| **Static Factor** | An `ExperimentFactor` with levels enumerated up front. Cardinality is known and finite. |
| **Adaptive Factor** | An `ExperimentFactor` with levels generated dynamically through iterative refinement. Initial level is provided; subsequent levels are discovered. |
| **Adaptive Experiment** | An experiment containing at least one `AdaptiveFactor`. Configs are discovered incrementally using empirical feedback. |
| **RefinementStrategy** | SPI for generating refined levels based on iteration feedback. Backend-specific (e.g., llmx provides LLM-based prompt refinement). |
| **IterationFeedback** | Structured feedback from one adaptive iteration, including aggregated results and failure observations. Used by `RefinementStrategy`. |
| **Iteration History** | Complete record of all iterations in an adaptive experiment, capturing each level tried and its results. Recorded in baselines for provenance. |
| **Level Serialization** | Configuration for how adaptive factor levels are recorded in baselines. Supports inline YAML, external file references, and custom serializers for complex objects. |
| **External Level Reference** | A `$ref` pointer in a baseline that references an externalized level stored in a separate file. Used for large or complex content. |
| **Empirical Baseline** | Machine-generated record of observed behavior from executing an `ExperimentConfig` (or adaptive iteration). |
| **Aggregated Report** | Summary of results across all configs in an experiment (`SUMMARY.yaml`). |
| **Execution Specification** | Human-approved contract derived from baselines, defining expected behavior for conformance testing. |
| **Conformance Test** | A probabilistic test that validates behavior against a specification. |
| **Backend** | A pluggable component that provides domain-specific execution context. Registered via SPI (`ExperimentBackend`). |
| **llmx** | The LLM-specific backend extension (`org.javai.punit.llmx`). Reference implementation of the backend SPI. Does not pollute the core framework. |
| **Success Criteria** | An expression evaluated against `UseCaseResult` to determine per-sample success. |
| **Provenance** | The chain of artifacts from definition to enforcement: Use Case → ExperimentDesign → ExperimentConfig → Baseline → Specification → Conformance Test. |

---

## 13. Appendix: Sketch of Key Class Implementations

The following sketches illustrate the intended structure of key classes. These are **not** executable code but serve as design guidance.

### 13.1 UseCaseResult

```java
package org.javai.punit.experiment.model;

public final class UseCaseResult {
    
    private final Map<String, Object> values;
    private final Map<String, Object> metadata;
    private final Instant timestamp;
    private final Duration executionTime;
    
    private UseCaseResult(Builder builder) {
        this.values = Map.copyOf(builder.values);
        this.metadata = Map.copyOf(builder.metadata);
        this.timestamp = builder.timestamp;
        this.executionTime = builder.executionTime;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getValue(String key, Class<T> type) {
        Object val = values.get(key);
        if (val == null) return Optional.empty();
        if (!type.isInstance(val)) {
            throw new ClassCastException("Value '" + key + "' is " + 
                val.getClass().getName() + ", not " + type.getName());
        }
        return Optional.of((T) val);
    }
    
    public <T> T getValue(String key, Class<T> type, T defaultValue) {
        return getValue(key, type).orElse(defaultValue);
    }
    
    public boolean getBoolean(String key, boolean defaultValue) {
        return getValue(key, Boolean.class, defaultValue);
    }
    
    public int getInt(String key, int defaultValue) {
        Number n = getValue(key, Number.class, defaultValue);
        return n.intValue();
    }
    
    public double getDouble(String key, double defaultValue) {
        Number n = getValue(key, Number.class, defaultValue);
        return n.doubleValue();
    }
    
    public String getString(String key, String defaultValue) {
        return getValue(key, String.class, defaultValue);
    }
    
    public Map<String, Object> getAllValues() {
        return values;
    }
    
    public Map<String, Object> getAllMetadata() {
        return metadata;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public Duration getExecutionTime() {
        return executionTime;
    }
    
    public static final class Builder {
        private final Map<String, Object> values = new LinkedHashMap<>();
        private final Map<String, Object> metadata = new LinkedHashMap<>();
        private Instant timestamp = Instant.now();
        private Duration executionTime = Duration.ZERO;
        
        public Builder value(String key, Object val) {
            Objects.requireNonNull(key, "key");
            values.put(key, val);
            return this;
        }
        
        public Builder meta(String key, Object val) {
            Objects.requireNonNull(key, "key");
            metadata.put(key, val);
            return this;
        }
        
        public Builder executionTime(Duration duration) {
            this.executionTime = Objects.requireNonNull(duration);
            return this;
        }
        
        public UseCaseResult build() {
            return new UseCaseResult(this);
        }
    }
}
```

### 13.2 ExperimentExtension

```java
package org.javai.punit.experiment.engine;

public class ExperimentExtension 
        implements TestTemplateInvocationContextProvider, 
                   InvocationInterceptor {
    
    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(ExperimentExtension.class);
    
    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod()
                .map(m -> m.isAnnotationPresent(Experiment.class))
                .orElse(false);
    }
    
    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
            ExtensionContext context) {
        
        Method testMethod = context.getRequiredTestMethod();
        Experiment annotation = testMethod.getAnnotation(Experiment.class);
        
        // Resolve use case
        String useCaseId = annotation.useCase();
        UseCaseRegistry useCaseRegistry = getUseCaseRegistry(context);
        UseCaseDefinition useCase = useCaseRegistry.resolve(useCaseId)
            .orElseThrow(() -> new ExtensionConfigurationException(
                "Use case not found: " + useCaseId));
        
        // Build context from @ExperimentContext annotations
        UseCaseContext useCaseContext = buildContext(testMethod);
        
        // Create aggregator for experiment results
        ExperimentResultAggregator aggregator = new ExperimentResultAggregator(
            useCaseId, annotation.samples());
        
        // Create budget monitors (reuse existing punit machinery)
        CostBudgetMonitor budgetMonitor = new CostBudgetMonitor(
            annotation.timeBudgetMs(),
            annotation.tokenBudget(),
            /* ... */);
        
        // Store in extension context
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        store.put("aggregator", aggregator);
        store.put("useCase", useCase);
        store.put("useCaseContext", useCaseContext);
        store.put("budgetMonitor", budgetMonitor);
        store.put("annotation", annotation);
        
        // Generate sample stream
        AtomicBoolean terminated = new AtomicBoolean(false);
        store.put("terminated", terminated);
        
        return Stream.iterate(1, i -> i + 1)
                .limit(annotation.samples())
                .takeWhile(i -> !terminated.get())
                .map(i -> new ExperimentInvocationContext(i, annotation.samples()));
    }
    
    @Override
    public void interceptTestTemplateMethod(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) throws Throwable {
        
        ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
        // ... get components from store ...
        
        // Execute use case and collect result
        UseCaseResult result;
        try {
            result = invokeUseCase(useCase, useCaseContext);
        } catch (Throwable t) {
            result = UseCaseResult.builder()
                .value("exception", t.getClass().getName())
                .value("exceptionMessage", t.getMessage())
                .build();
        }
        
        aggregator.recordResult(result);
        
        // Check budgets (reuse punit logic)
        // ...
        
        // If last sample or terminated, generate baseline
        if (aggregator.getSamplesExecuted() >= samples || terminated.get()) {
            generateBaseline(extensionContext, aggregator, annotation);
            publishExperimentReport(extensionContext, aggregator);
        }
        
        // Skip the actual test method body (experiment is use-case driven)
        invocation.skip();
    }
    
    private void generateBaseline(
            ExtensionContext context,
            ExperimentResultAggregator aggregator,
            Experiment annotation) {
        
        EmpiricalBaselineGenerator generator = new EmpiricalBaselineGenerator();
        EmpiricalBaseline baseline = generator.generate(
            aggregator, 
            context.getTestClass().orElse(null),
            context.getTestMethod().orElse(null));
        
        Path outputPath = resolveBaselineOutputPath(annotation, aggregator.getUseCaseId());
        baseline.writeTo(outputPath);
    }
}
```

### 13.3 ExecutionSpecification

```java
package org.javai.punit.spec.model;

public final class ExecutionSpecification {
    
    private final String specId;
    private final String useCaseId;
    private final int version;
    private final Instant approvedAt;
    private final String approvedBy;
    private final String approvalNotes;
    private final List<String> sourceBaselines;
    private final Map<String, Object> executionContext;
    private final SpecRequirements requirements;
    private final CostEnvelope costEnvelope;
    
    // Constructor, getters, builder...
    
    public String getSpecId() {
        return specId;
    }
    
    public String getUseCaseId() {
        return useCaseId;
    }
    
    public int getVersion() {
        return version;
    }
    
    public boolean isApproved() {
        return approvedAt != null && approvedBy != null;
    }
    
    public double getMinPassRate() {
        return requirements.minPassRate();
    }
    
    public SuccessCriteria getSuccessCriteria() {
        return SuccessCriteria.parse(requirements.successCriteria());
    }
    
    public static ExecutionSpecification loadFrom(Path path) throws IOException {
        // JSON deserialization
    }
    
    public void validate() throws SpecificationValidationException {
        if (!isApproved()) {
            throw new SpecificationValidationException(
                "Specification lacks approval metadata");
        }
        // Additional validation...
    }
    
    public record SpecRequirements(
        double minPassRate,
        String successCriteria
    ) {}
    
    public record CostEnvelope(
        long maxTimePerSampleMs,
        long maxTokensPerSample,
        long totalTokenBudget
    ) {}
}
```

### 13.4 SpecificationRegistry

```java
package org.javai.punit.spec.registry;

public class SpecificationRegistry {
    
    private final Path specsRoot;
    private final Map<String, ExecutionSpecification> cache = new ConcurrentHashMap<>();
    
    public SpecificationRegistry(Path specsRoot) {
        this.specsRoot = specsRoot;
    }
    
    public SpecificationRegistry() {
        // Default: src/test/resources/punit/specs
        this(detectDefaultSpecsRoot());
    }
    
    /**
     * Resolves a specification by ID.
     * 
     * @param specId format: "useCaseId:vN" (e.g., "usecase.json.gen:v3")
     * @return the loaded specification
     * @throws SpecificationNotFoundException if not found
     * @throws SpecificationValidationException if invalid
     */
    public ExecutionSpecification resolve(String specId) {
        return cache.computeIfAbsent(specId, this::loadSpec);
    }
    
    private ExecutionSpecification loadSpec(String specId) {
        ParsedSpecId parsed = ParsedSpecId.parse(specId);
        Path specDir = specsRoot.resolve(parsed.useCaseId());
        
        // Try YAML first (default), then JSON as fallback
        Path specPath = resolveSpecPath(specDir, parsed.version());
        
        if (specPath == null) {
            throw new SpecificationNotFoundException(
                "Specification not found: " + specId + 
                " (tried .yaml and .json in " + specDir + ")");
        }
        
        try {
            ExecutionSpecification spec = ExecutionSpecification.loadFrom(specPath);
            spec.validate();
            return spec;
        } catch (IOException e) {
            throw new SpecificationLoadException(
                "Failed to load specification: " + specId, e);
        }
    }
    
    private Path resolveSpecPath(Path specDir, int version) {
        // YAML is preferred (default format)
        Path yamlPath = specDir.resolve("v" + version + ".yaml");
        if (Files.exists(yamlPath)) return yamlPath;
        
        Path ymlPath = specDir.resolve("v" + version + ".yml");
        if (Files.exists(ymlPath)) return ymlPath;
        
        // JSON as fallback
        Path jsonPath = specDir.resolve("v" + version + ".json");
        if (Files.exists(jsonPath)) return jsonPath;
        
        return null;
    }
    
    private record ParsedSpecId(String useCaseId, int version) {
        static ParsedSpecId parse(String specId) {
            int colonIndex = specId.lastIndexOf(':');
            if (colonIndex < 0 || !specId.substring(colonIndex + 1).startsWith("v")) {
                throw new IllegalArgumentException(
                    "Invalid spec ID format: " + specId + 
                    ". Expected: useCaseId:vN");
            }
            String useCaseId = specId.substring(0, colonIndex);
            int version = Integer.parseInt(specId.substring(colonIndex + 2));
            return new ParsedSpecId(useCaseId, version);
        }
    }
}
```

### 13.5 SuccessCriteria

```java
package org.javai.punit.spec.model;

public interface SuccessCriteria {
    
    /**
     * Evaluates whether a result meets success criteria.
     */
    boolean isSuccess(UseCaseResult result);
    
    /**
     * Returns human-readable description.
     */
    String getDescription();
    
    /**
     * Parses a criteria expression.
     * 
     * Supported syntax:
     *   - "key == value" (equality)
     *   - "key != value" (inequality)
     *   - "key > value", "key >= value", "key < value", "key <= value"
     *   - "expr1 && expr2" (and)
     *   - "expr1 || expr2" (or)
     *   - "(expr)" (grouping)
     * 
     * Examples:
     *   - "isValid == true"
     *   - "score >= 0.8"
     *   - "isValid == true && errorCount == 0"
     */
    static SuccessCriteria parse(String expression) {
        return new ExpressionSuccessCriteria(expression);
    }
}

// Implementation sketch
class ExpressionSuccessCriteria implements SuccessCriteria {
    
    private final String expression;
    private final CompiledExpression compiled;
    
    ExpressionSuccessCriteria(String expression) {
        this.expression = expression;
        this.compiled = ExpressionParser.parse(expression);
    }
    
    @Override
    public boolean isSuccess(UseCaseResult result) {
        return compiled.evaluate(result.getAllValues());
    }
    
    @Override
    public String getDescription() {
        return expression;
    }
}
```

---

## 14. Conclusion

This plan establishes a disciplined extension to punit that:

1. **Introduces experiments as first-class citizens** — enabling empirical discovery before specification.

2. **Maintains domain neutrality** — the core abstractions (use cases, results, baselines, specs) apply to any stochastic system.

3. **Reuses existing infrastructure** — execution, aggregation, budgeting, and reporting are shared between experiments and tests.

4. **Enforces governance** — specifications require explicit approval; arbitrary thresholds are discouraged.

5. **Supports extensibility** — pluggable backends allow domain-specific context without polluting the core.

6. **Preserves production isolation** — all experiment/test abstractions live strictly in test space.

The phased implementation plan provides a clear path from foundational abstractions to complete functionality, with each phase building on the previous while delivering standalone value.

---

*End of Plan*

