# Glossary

| Term                               | Definition                                                                                  |
|------------------------------------|---------------------------------------------------------------------------------------------|
| **Use Case**                       | A test/experiment-only function that invokes production code and returns a `UseCaseResult`. |
| **Use Case ID**                    | A unique string identifier for a use case (e.g., `usecase.json.generation`).                |
| **UseCaseResult**                  | A neutral container of key-value observations produced by a use case invocation.            |
| **Experiment**                     | Executes a use case across one or more `ExperimentConfig`s in exploratory mode.             |
| **ExperimentDesign**               | Declarative description of what is explored (factors + levels).                             |
| **ExperimentFactor**               | One independently varied dimension (e.g., `model`, `temperature`).                          |
| **ExperimentLevel**                | One setting of a factor (categorical or numeric).                                           |
| **ExperimentConfig**               | One concrete combination of levels—the unit of execution.                                   |
| **ExperimentGoal**                 | Optional criteria for early termination.                                                    |
| **Factor**                         | One independently varied dimension in EXPLORE mode (e.g., `model`, `temperature`).          |
| **FactorSource**                   | JUnit-style source of factor combinations (e.g., `@MethodSource`, `@CsvFactorSource`).      |
| **BASELINE Mode**                  | Default experiment mode: precise estimation of one configuration with many samples.         |
| **EXPLORE Mode**                   | Experiment mode for comparing multiple configurations with fewer samples each.              |
| **Empirical Baseline**             | Machine-generated record of observed behavior.                                              |
| **Execution Specification**        | Human-approved contract derived from baselines.                                             |
| **Conformance Test**               | A probabilistic test that validates behavior against a specification.                       |
| **Backend**                        | A pluggable component providing domain-specific configuration.                              |
| **llmx**                           | The LLM-specific backend extension.                                                         |
| **Success Criteria**               | Expression evaluated against `UseCaseResult` to determine per-sample success.               |
| **Provenance**                     | The chain of artifacts from definition to enforcement.                                      |
| **Threshold Provenance**           | Optional metadata documenting where a test's threshold originated (SLA, SLO, policy, etc.). |
| **Threshold Origin**               | Enum indicating the origin of a probabilistic test's threshold (e.g., SLA, SLO, POLICY).    |
| **Contract Reference**             | Human-readable string identifying the document/clause defining a test threshold.            |
| **SLA (Service Level Agreement)**  | Contractual commitment to external customers defining minimum service quality.              |
| **SLO (Service Level Objective)**  | Internal target for service quality, often more stringent than SLAs.                        |
| **PromptContributor**              | Interface for extracting prompt components from production code.                            |
| **FailureCategorizer**             | Function that classifies failed samples into categories.                                    |
| **TokenEstimator**                 | Interface for estimating token counts when providers don't report them.                     |
| **RegressionThreshold**            | Statistically-derived minimum pass rate for regression tests.                               |
| **One-Sided Lower Bound**          | Statistical threshold below which true success rate is unlikely to fall.                    |
| **Wilson Score Bound**             | Robust confidence bound for binomial proportions.                                           |
| **Sample-Size-First Approach**     | User specifies samples; framework computes threshold.                                       |
| **Confidence-First Approach**      | User specifies confidence; framework computes required samples.                             |
| **Threshold-First Approach**       | User specifies threshold; framework computes implied confidence.                            |
| **False Positive (Type I Error)**  | A test failure when the system has not degraded.                                            |
| **False Negative (Type II Error)** | A test pass when the system has degraded.                                                   |
| **Effect Size**                    | The minimum degradation the test is designed to detect.                                     |
| **Statistical Power**              | The probability of correctly detecting a real degradation (1-β).                            |
| **punit-statistics Module**        | Isolated module for all statistical calculations.                                           |

