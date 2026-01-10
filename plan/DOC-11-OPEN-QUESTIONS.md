# Open Questions and Recommendations

| Question                                                        | Recommendation                                                   |
|-----------------------------------------------------------------|------------------------------------------------------------------|
| **Should specs support inheritance/composition?**               | **No, not in v1.** Keep specs simple and flat.                   |
| **Should baselines auto-expire?**                               | **No.** Baselines are historical records.                        |
| **What if use case method throws an exception?**                | **Record as a failure type in UseCaseResult.**                   |
| **Can one spec reference multiple use cases?**                  | **No.** One spec = one use case.                                 |
| **How to handle flaky use case implementations?**               | **That's the point.** The framework measures flakiness.          |
| **Should success criteria support custom functions?**           | **Not in v1.** Start with simple expression language.            |
| **Where should baseline/spec files live?**                      | **`src/test/resources/punit/`** by default.                      |
| **What file format for baselines/specs?**                       | **YAML is the default.** JSON as optional alternative.           |
| **Should the number of ExperimentConfigs be limited?**          | **No.** Budget constraints naturally limit execution.            |
| **Can multi-config experiments be resumed after interruption?** | **No, not in v1.**                                               |
| **Can ExperimentConfigs run in parallel?**                      | **No, not initially.** Sequential execution only.                |
| **Can you filter/re-run specific ExperimentConfigs?**           | **Not in v1.**                                                   |
| **How do you create a spec from a multi-config experiment?**    | **Human selection.** Review SUMMARY.yaml and select best config. |

---

*Previous: [Out-of-Scope Clarifications](./DOC-10-OUT-OF-SCOPE.md)*

*Next: [Glossary](../docs/GLOSSARY.md)*

*[Back to Table of Contents](./DOC-00-TOC.md)*
