# Glossary

| Term | Definition |
|------|------------|
| **Use Case** | A test/experiment-only function that invokes production code and returns a `UseCaseResult`. |
| **Use Case ID** | A unique string identifier for a use case (e.g., `usecase.json.generation`). |
| **UseCaseResult** | A neutral container of key-value observations produced by a use case invocation. |
| **Experiment** | Executes a use case across one or more `ExperimentConfig`s in exploratory mode. |
| **ExperimentDesign** | Declarative description of what is explored (factors + levels). |
| **ExperimentFactor** | One independently varied dimension (e.g., `model`, `temperature`). |
| **ExperimentLevel** | One setting of a factor (categorical or numeric). |
| **ExperimentConfig** | One concrete combination of levels—the unit of execution. |
| **ExperimentGoal** | Optional criteria for early termination. |
| **StaticFactor** | Factor with levels enumerated up front. |
| **AdaptiveFactor** | Factor with levels generated dynamically through iterative refinement. |
| **RefinementStrategy** | SPI for generating refined levels in adaptive experiments. |
| **IterationFeedback** | Structured feedback from one adaptive iteration. |
| **Empirical Baseline** | Machine-generated record of observed behavior. |
| **Execution Specification** | Human-approved contract derived from baselines. |
| **Conformance Test** | A probabilistic test that validates behavior against a specification. |
| **Backend** | A pluggable component providing domain-specific execution context. |
| **llmx** | The LLM-specific backend extension. |
| **Success Criteria** | Expression evaluated against `UseCaseResult` to determine per-sample success. |
| **Provenance** | The chain of artifacts from definition to enforcement. |
| **PromptContributor** | Interface for extracting prompt components from production code. |
| **FailureCategorizer** | Function that classifies failed samples into categories. |
| **TokenEstimator** | Interface for estimating token counts when providers don't report them. |
| **RegressionThreshold** | Statistically-derived minimum pass rate for regression tests. |
| **One-Sided Lower Bound** | Statistical threshold below which true success rate is unlikely to fall. |
| **Wilson Score Bound** | Robust confidence bound for binomial proportions. |
| **Sample-Size-First Approach** | User specifies samples; framework computes threshold. |
| **Confidence-First Approach** | User specifies confidence; framework computes required samples. |
| **Threshold-First Approach** | User specifies threshold; framework computes implied confidence. |
| **False Positive (Type I Error)** | A test failure when the system has not degraded. |
| **False Negative (Type II Error)** | A test pass when the system has degraded. |
| **Effect Size** | The minimum degradation the test is designed to detect. |
| **Statistical Power** | The probability of correctly detecting a real degradation (1-β). |
| **punit-statistics Module** | Isolated module for all statistical calculations. |

---

*Previous: [Open Questions and Recommendations](./DOC-11-OPEN-QUESTIONS.md)*

*Next: [Appendix: Class Sketches](./DOC-13-APPENDIX-CLASS-SKETCHES.md)*

*[Back to Table of Contents](./DOC-00-TOC.md)*
