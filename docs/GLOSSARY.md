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
| **Footprint**                      | A stable hash identifying the combination of use case identity, functional parameters, and covariate declarations. Two baselines with the same footprint are candidates for covariate-based selection. |
| **BASELINE Mode**                  | Default experiment mode: precise estimation of one configuration with many samples.         |
| **Baseline Selection**             | The process of choosing the most appropriate baseline for a probabilistic test based on footprint match and covariate conformance. When multiple baselines exist, the one with the best covariate match is selected. |
| **EXPLORE Mode**                   | Experiment mode for comparing multiple configurations with fewer samples each.              |
| **Empirical Baseline**             | Machine-generated record of observed behavior.                                              |
| **Execution Specification**        | Human-approved contract derived from baselines.                                             |
| **Conformance Test**               | A probabilistic test that validates behavior against a specification.                       |
| **Covariate**                      | A contextual factor that drives variance in system behavior. Covariates are declared on a use case to indicate which environmental or configuration variables should be tracked for baseline matching and statistical comparison. Unlike functional inputs (Factors), covariates represent conditions that affect outcomes but are often outside direct control. |
| **Covariate Category**             | Classification of a covariate by its nature: TEMPORAL (time-based), CONFIGURATION (deliberate choices), EXTERNAL_DEPENDENCY (third-party services), INFRASTRUCTURE (execution environment), DATA_STATE (data context), or INFORMATIONAL (traceability only). |
| **Covariate Conformance**          | The degree to which a test's covariate values match those of the baseline. Full conformance means all covariates match; non-conformance indicates the test ran under different conditions than the baseline was established. |
| **Covariate Profile**              | An immutable record of covariate values captured at a specific point in time, used to characterize the conditions under which an experiment or test was executed. |
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

