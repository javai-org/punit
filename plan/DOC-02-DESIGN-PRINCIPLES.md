# Design Principles (Invariants)

The following principles are **non-negotiable** and must be preserved throughout implementation:

## 1.1 Experiments Are First-Class and Domain-Neutral

- Experiments are **not** LLM-specific abstractions.
- The punit core must remain free of AI-specific concepts (prompts, models, tokens-as-LLM-tokens).
- LLM experimentation is an optional, pluggable backend—one of many possible experiment contexts.

## 1.2 Same Engine, Different Modes

- Experiments and probabilistic tests share the same execution, aggregation, budgeting, and reporting machinery.
- They differ in **intent** (exploratory vs. conformance) and **semantics** (data-only vs. pass/fail), not infrastructure.
- This reuse minimizes complexity, reduces bugs, and ensures consistent behavior.

## 1.3 Clear Semantic Separation

| Aspect        | Experiment Mode            | Probabilistic Test Mode                      |
|---------------|----------------------------|----------------------------------------------|
| Intent        | Exploratory, empirical     | Conformance, gatekeeping                     |
| Produces      | Empirical data             | Binary pass/fail verdict                     |
| Gates CI?     | **Never**                  | Yes                                          |
| Assertions    | Observations, not failures | Failures gate outcomes                       |
| Specification | None required              | Spec-driven (preferred) or inline thresholds |

## 1.4 Discovery Precedes Specification Precedes Testing

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

| Stage   | Artifact                        | Purpose                            |
|---------|---------------------------------|------------------------------------|
| Define  | Use Case                        | The behavior to observe/test       |
| Design  | ExperimentDesign                | What factors and levels to explore |
| Execute | ExperimentConfig                | Concrete configuration to run      |
| Record  | Empirical Baselines             | Observed behavior per config       |
| Approve | Execution Specification         | Human-approved contract            |
| Enforce | Probabilistic Conformance Tests | CI-gated validation                |

Hard-coded thresholds in `@ProbabilisticTest` remain supported but are explicitly a **transitional pattern**. The framework will encourage spec-driven testing and discourage arbitrary thresholds.

## 1.5 Production Code Must Remain Uncontaminated

Production application code must **never** depend on:
- Use case IDs
- Use case classes or functions
- Experiment APIs
- `UseCaseResult` or any experiment/test result types
- Specification references

All these abstractions exist strictly in **test/experiment space**. Production code is the *subject* of testing, not a participant in the test framework.

## 1.6 Statistical Foundations Must Be Isolated and Auditable

The statistical calculations that underpin punit's operational integrity guarantees are **critical infrastructure**. They must be:

- **Isolated**: Statistical modules have essentially no dependencies on other parts of the framework. A statistician reviewing the code should not need to understand experiments, use cases, or JUnit integration.

- **Independently testable**: Each statistical concept resides in a dedicated artifact (module/package), sharing that artifact only with inextricably connected concepts. This enables focused, rigorous unit testing.

- **Externally auditable**: The code must be readable by a professional statistician who may not be a Java expert. Variable names, method names, and comments should use standard statistical terminology.

- **Rigorously tested**: Unit tests include worked examples with real-world variable names (e.g., `experimentPassRate`, `testSamples`, `confidenceLevel`), even when values are hard-coded. These tests serve as executable documentation of the statistical methods.

This principle ensures that:
1. Organizations can validate the statistical integrity of punit independently
2. Professional statisticians can review the calculations without framework expertise
3. Statistical bugs are caught early through comprehensive, focused testing
4. The statistical foundation can evolve without destabilizing other framework components

---

*Previous: [Executive Summary](./DOC-01-EXECUTIVE-SUMMARY.md)*

*Next: [Architecture Overview](./DOC-03-ARCHITECTURE-OVERVIEW.md)*

*[Back to Table of Contents](./DOC-00-TOC.md)*

