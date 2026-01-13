# User Guide Findings and Requirements

*Document created: 2026-01-13*
*Purpose: Capture requirements for revisions to USER-GUIDE.md*

---

## 1. Developer Responsibilities for Trial Independence

### Finding

The STATISTICAL-COMPANION.md (Section 1.3) now documents that developers share responsibility for ensuring trial independence. This guidance needs to be surfaced in the USER-GUIDE.md in a practical, actionable form.

### Required Content

Add a new section covering developer responsibilities, including:

1. **Why trial independence matters**: Brief explanation that PUnit's statistical guarantees depend on trials being independent.

2. **Common features that can compromise independence**:
   - Cached system prompts (e.g., LLM provider caching features)
   - Conversation context carrying over between trials
   - Request batching
   - Fixed seed parameters

3. **Practical mitigation checklist**:
   - Disable caching during experiments (with example configuration)
   - Use fresh sessions for each trial
   - Submit requests individually
   - Use random or rotating seeds

4. **Documentation expectations**: Encourage developers to document their assumptions about trial independence in test annotations or comments.

### Suggested Location

Consider placing this section:
- After the "Statistical Foundations" section, or
- As part of a new "Best Practices" section, or
- Within each paradigm section (SLA-driven / Spec-driven) as a shared concern

### Cross-Reference

Link to STATISTICAL-COMPANION.md Section 1.3 for the formal treatment.

---

## Future Requirements

*(Additional USER-GUIDE.md requirements will be added below as they are identified)*

---

