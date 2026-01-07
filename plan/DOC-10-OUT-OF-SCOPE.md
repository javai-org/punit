# Out-of-Scope Clarifications

The following are explicitly **not** part of this extension:

## 11.1 Auto-Approval of Specifications

Specifications require explicit human approval. There is no mechanism for automatically promoting baselines to specifications. This is intentional: the approval step forces deliberation about what behavior is acceptable.

## 11.2 AutoML / Automatic Configuration Optimization

The framework does not:
- Automatically search for optimal model parameters
- Suggest configuration changes based on experiment results
- Tune thresholds to make tests pass

These would undermine the purpose of empirical specification.

## 11.3 Runtime Routing via Use Case IDs

Production code does **not** use use case IDs for routing, feature flags, or configuration. Use case IDs are test/experiment metadata only.

## 11.4 Production-Time Specification Validation

The framework does not validate production behavior against specifications at runtime. Specifications inform production configuration through the build/deploy process, but enforcement happens in tests, not production.

## 11.5 Distributed Experiment Execution

Experiments run within a single JVM. Distributed experiment coordination (across nodes, cloud, etc.) is out of scope.

## 11.6 Real-Time Baseline Updates

Baselines are generated at experiment completion, not continuously updated during execution.

## 11.7 Experiment Scheduling and Orchestration

Experiments are run on-demand via JUnit. There is no built-in scheduler for periodic experiment execution.

## 11.8 Visual Dashboard / UI

There is no web UI or dashboard. Results are reported via JUnit's standard mechanisms.

## 11.9 IDE-Specific Integration

IDE plugins for baseline editing, spec creation, or result visualization are not in scope.

---

*Previous: [Extensibility Model](./DOC-09-EXTENSIBILITY-MODEL.md)*

*Next: [Open Questions and Recommendations](./DOC-11-OPEN-QUESTIONS.md)*

*[Back to Table of Contents](./DOC-00-TOC.md)*
