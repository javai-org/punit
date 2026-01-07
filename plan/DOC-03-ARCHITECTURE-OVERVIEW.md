# Architecture Overview

## 2.1 Component Diagram

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

## 2.2 Ownership Boundaries

| Layer                  | Responsibility                                                        | Package Location                                                                          |
|------------------------|-----------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| **punit-core**         | Execution engine, aggregation, budgeting, reporting, base annotations | `org.javai.punit.api`, `org.javai.punit.engine`, `org.javai.punit.model`                  |
| **punit-experiment**   | Experiment annotation, baseline generation, spec resolution           | `org.javai.punit.experiment.api`, `org.javai.punit.experiment.engine`                     |
| **punit-spec**         | Specification model, registry, versioning, conflict resolution        | `org.javai.punit.spec.api`, `org.javai.punit.spec.model`, `org.javai.punit.spec.registry` |
| **punit-backends-spi** | Backend SPI interface, generic backend, registry                      | `org.javai.punit.experiment.spi`, `org.javai.punit.experiment.backend`                    |
| **llmx** (extension)   | LLM-specific backend, context, presets                                | `org.javai.punit.llmx`                                                                    |

### Dependency Constraints

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

## 2.3 Registry & Storage Concepts

### Empirical Baseline Storage

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

### Execution Specification Storage

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

### Specification Registry

The `SpecificationRegistry` is responsible for:
- Loading specifications from the filesystem
- Resolving spec references (e.g., `usecase.json.generation:v2`)
- Caching loaded specifications
- Validating specification integrity

## 2.4 File Format Support

### Default Format: YAML

YAML is the default file format for baselines and specifications because:

1. **Comments are supported**: Approval notes, rationale, and documentation can be embedded directly in spec files
2. **Human-readable**: Nested structures are easier to read and edit than JSON
3. **Multiline strings**: Approval notes and descriptions flow naturally
4. **Version control friendly**: YAML diffs are cleaner than JSON diffs

### Optional Format: JSON

JSON is supported as an alternative for:
- Tooling compatibility (some CI/CD tools prefer JSON)
- Programmatic generation (JSON is simpler to emit)
- Systems with existing JSON infrastructure

### Format Detection

The framework auto-detects format based on file extension:
- `.yaml` or `.yml` → YAML parser
- `.json` → JSON parser

When generating new files (baselines), YAML is used by default. This can be overridden via:
- Annotation parameter: `@Experiment(outputFormat = "json")`
- System property: `-Dpunit.outputFormat=json`
- Environment variable: `PUNIT_OUTPUT_FORMAT=json`

### Format Consistency

Within a project, consistency is recommended but not enforced. A specification can reference a YAML baseline even if the spec itself is in JSON format (or vice versa). The framework handles format translation transparently.

---

*Previous: [Design Principles](./DOC-02-DESIGN-PRINCIPLES.md)*

*Next: [Core Conceptual Artifacts](./DOC-04-CORE-CONCEPTUAL-ARTIFACTS.md)*

*[Back to Table of Contents](./DOC-00-TOC.md)*

