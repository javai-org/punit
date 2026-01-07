# PUnit Documentation

## Table of Contents

This folder contains key documentation for the PUnit project: design documents, architectural decisions, and reference material.

---

## Project Documentation

| #  | Document                                                            | Description                                      |
|----|---------------------------------------------------------------------|--------------------------------------------------|
| 01 | [Executive Summary](./DOC-01-EXECUTIVE-SUMMARY.md)                  | High-level overview of the experiment extension  |
| 02 | [Design Principles](./DOC-02-DESIGN-PRINCIPLES.md)                  | Core principles guiding implementation           |
| 03 | [Architecture Overview](./DOC-03-ARCHITECTURE-OVERVIEW.md)          | System architecture and component structure      |
| 04 | [Core Conceptual Artifacts](./DOC-04-CORE-CONCEPTUAL-ARTIFACTS.md)  | Use cases, results, configs, baselines, specs    |
| 05 | [Annotation/API Design](./DOC-05-ANNOTATION-API-DESIGN.md)          | `@Experiment`, `@ProbabilisticTest`, etc.        |
| 06 | [Data Flow](./DOC-06-DATA-FLOW.md)                                  | Experiment → baseline → spec → test flow         |
| 07 | [Governance and Safety](./DOC-07-GOVERNANCE-SAFETY.md)              | Budgeting, approval workflow, isolation          |
| 08 | [Execution and Reporting](./DOC-08-EXECUTION-REPORTING.md)          | Aggregation and baseline generation              |
| 09 | [Extensibility Model](./DOC-09-EXTENSIBILITY-MODEL.md)              | Backend SPI and `llmx` module                    |
| 10 | [Out of Scope Clarifications](./DOC-10-OUT-OF-SCOPE.md)             | Explicit exclusions                              |
| 11 | [Open Questions](./DOC-11-OPEN-QUESTIONS.md)                        | Design decisions and recommendations             |
| 12 | [Glossary](./DOC-12-GLOSSARY.md)                                    | Term definitions                                 |
| 13 | [Appendix: Class Sketches](./DOC-13-APPENDIX-CLASS-SKETCHES.md)     | Reference API sketches                           |
| 14 | [Conclusion](./DOC-14-CONCLUSION.md)                                | Summary                                          |

---

## Development Plan

| Document                                                       | Description                                       |
|----------------------------------------------------------------|---------------------------------------------------|
| [Development Plan](./PLAN-99-DEVELOPMENT-PLAN.md)              | Phased development plan (Tracks C, E, A/B/C)      |
| [Development Status](./PLAN-99-DEVELOPMENT-STATUS.md)          | Current implementation status                     |

---

## Related Documentation

| Document                                                       | Description                                       |
|----------------------------------------------------------------|---------------------------------------------------|
| [Operational Flow](../docs/OPERATIONAL-FLOW.md)                | End-to-end workflow for organizations             |
| [Statistical Companion](../docs/STATISTICAL-COMPANION.md)      | Rigorous statistical treatment for auditors       |

---

*Last updated: January 2026*
