# REQ-DIFF: Diff-Anchor Lines in Explore Experiment Output

## Summary

Add deterministic anchor comment lines between sample blocks in explore experiment YAML output files. These anchors act as sync points for diff tools, guaranteeing correct sample-for-sample alignment when comparing two exploration files side by side.

## Motivation

### The problem

Explore experiments produce one YAML output file per configuration (e.g. one per model). A key use case is comparing these files in a diff viewer to understand how configurations differ at the sample level. The `resultProjection` section was designed for this — every sample has the same structural keys, the same input, and the same sample indices.

However, because the content *within* each sample varies (different pass/fail outcomes, different response text), line-based diff algorithms (LCS) can latch onto the wrong matches when the inner content is highly repetitive. In practice this causes the diff viewer to misalign from one sample block onward, producing an unreadable diff.

### Example of misalignment (without anchors)

Given two exploration files for `model-claude-haiku` and `model-claude-sonnet`, the diff around `sample[4]` produces a 13-line insertion/deletion block:

```diff
79,91d78
<       Contains valid actions: passed
<       Response has content: passed
<       Valid shopping action: passed
<     executionTimeMs: 0
<     diffableContent:
<       - "completionTokens: 20"
<       - "content: {\"actions\": [{\"context\": \"SHOP\", \"name\": \"add\", \"p…"
<       - "promptTokens: 170"
<       - <absent>
<       - <absent>
<   sample[5]:
<     input: Add some apples
<     postconditions:
```

The diff tool has matched haiku's `sample[4]` (passed) against sonnet's `sample[4]` (failed) by jumping across sample boundaries. Every subsequent sample is misaligned.

### The solution

A YAML comment line containing a deterministic, unique anchor value is emitted before each `sample[N]:` block. Because the anchor sequence is derived from a fixed seed and the sample index alone, the same anchor appears at the same structural position in every exploration file produced by the same experiment run — regardless of which configuration generated it.

The diff tool sees these anchors as exact matches and locks onto them, forcing correct alignment at every sample boundary.

### Example of correct alignment (with anchors)

The same two files, with anchors inserted:

```yaml
# ──── sample[4] ──── anchor:6c031199 ────
  input: Add some apples
  postconditions:
    Contains valid actions: passed       # haiku: passed
    ...
```

```yaml
# ──── sample[4] ──── anchor:6c031199 ────
  input: Add some apples
  postconditions:
    Contains valid actions: failed       # sonnet: failed
    ...
```

The diff now produces clean, sample-aligned hunks:

```diff
84c84
<       Contains valid actions: passed
---
>       Contains valid actions: failed
86c86
<       Valid shopping action: passed
---
>       Valid shopping action: failed
89,90c89,90
<       - "completionTokens: 20"
<       - "content: {\"actions\": [{\"context\": \"SHOP\", \"name\": \"add\", \"p…"
---
>       - "completionTokens: 30"
>       - "content: I'd be happy to help! Here's the JSON:\\n\\n{\"action…"
```

Every hunk stays within its sample boundary. No insertions or deletions span sample blocks.

## Requirements

### REQ-DIFF-1: Anchor line format

Each anchor line **must** be a YAML comment with the following format:

```
# ──── sample[N] ──── anchor:XXXXXXXX ────
```

Where `N` is the zero-based sample index and `XXXXXXXX` is an 8-character lowercase hexadecimal value. The sample identifier and anchor value are combined into a single line that serves as both a section heading and a diff sync point.

**Rationale:** A YAML comment is invisible to any tooling that parses the file as YAML. The decorative `────` borders make the purpose self-documenting. Eight hex characters provide 4 billion unique values — more than sufficient for any realistic sample count. Combining the sample title with the anchor avoids a separate `sample[N]:` YAML key and reduces visual noise.

### REQ-DIFF-2: Anchor placement

An anchor line **must** be emitted at the start of each sample block in the `resultProjection` section of an explore experiment's YAML output. The sample's data follows indented beneath it.

Example:

```yaml
resultProjection:
# ──── sample[0] ──── anchor:a3b1799d ────
  input: Add some apples
  postconditions:
    Contains valid actions: passed
    Response has content: passed
    Valid shopping action: passed
  executionTimeMs: 0
  content: |
    {"actions": [{"context": "SHOP", "name": "add", "parameters": [{"name": "item", "value": "apples"}, {"name": "quantity", "value": "1"}]}]}
# ──── sample[1] ──── anchor:46685257 ────
  input: Add some apples
  postconditions:
    Contains valid actions: failed
    Response has content: passed
    Valid shopping action: failed
  executionTimeMs: 0
  content: |
    I'd be happy to help! Here's the JSON:

    {"actions": [{"context": "SHOP", "name": "add", "parameters": [{"name": "item", "value": "apples"}, {"name": "quantity", "value": "1"}]}]}
  failureDetail: "Response contains preamble text before JSON"
# ──── sample[2] ──── anchor:392456de ────
  ...
```

### REQ-DIFF-3: Deterministic anchor sequence

The anchor value for `sample[N]` **must** be determined solely by:

1. A **fixed seed** that is the same for all configurations within a single experiment run.
2. The **sample index** `N`.

This guarantees that two files produced by the same explore experiment (e.g. `model-gpt-4o-mini.yaml` and `model-claude-haiku.yaml`) contain identical anchor values at identical sample positions.

**Implementation note:** Any deterministic PRNG seeded with a fixed value is acceptable. The seed should be constant across all configurations within an experiment run. One approach is to use a well-known seed (e.g. `42`) and advance the PRNG to position `N` to produce the anchor for `sample[N]`.

### REQ-DIFF-4: Anchor stability across reruns

The anchor sequence **should** be stable across experiment reruns when the sample count is the same. This means using a hard-coded seed rather than a time-based or run-specific seed.

**Rationale:** Stable anchors allow diffing exploration files from *different* runs of the same experiment (e.g. comparing today's results against last week's baseline).

### REQ-DIFF-5: Anchors must not affect YAML parsing

Anchor lines **must** be YAML comments (prefixed with `#`). Any tooling that loads the YAML file as structured data **must** see no difference from a file without anchors.

### REQ-DIFF-6: Content truncation no longer required

Without anchors, `diffableContent` values are truncated to a fixed width to keep every sample block the same number of lines. With anchors providing alignment, this truncation is **no longer required**. Content values — including full JSON responses — **may** be emitted at their natural length.

This is a significant improvement for usability: developers can now diff actual response content side by side, seeing exactly which JSON fields differ, which values changed, and how response structure varies across configurations.

### REQ-DIFF-7: Variable-length sample blocks permitted

Without anchors, every sample block must contain the same number of lines (padded with `<absent>` placeholders where necessary). With anchors providing alignment, sample blocks **may** vary in line count. The `<absent>` padding is **no longer required**.

A sample that produces a longer error message, a multi-line pretty-printed JSON response, or additional metadata lines will not cause misalignment of subsequent samples.

### REQ-DIFF-8: Anchor line incorporates the sample title

The anchor line **should** incorporate the sample identifier, combining the sync point and the section heading into a single line. This reduces visual noise and avoids a separate `sample[N]:` YAML key.

Proposed format:

```
# ──── sample[0] ──── anchor:a3b1799d ────
```

Example with full, untruncated content and variable-length blocks:

```yaml
resultProjection:
# ──── sample[0] ──── anchor:a3b1799d ────
  input: Add some apples
  postconditions:
    Contains valid actions: passed
    Response has content: passed
    Valid shopping action: passed
  executionTimeMs: 0
  content: |
    {"actions": [{"context": "SHOP", "name": "add", "parameters": [{"name": "item", "value": "apples"}, {"name": "quantity", "value": "1"}]}]}
# ──── sample[1] ──── anchor:46685257 ────
  input: Add some apples
  postconditions:
    Contains valid actions: failed
    Response has content: passed
    Valid shopping action: failed
  executionTimeMs: 0
  content: |
    I'd be happy to help! Here's the JSON:

    {"actions": [{"context": "SHOP", "name": "add", "parameters": [{"name": "item", "value": "apples"}, {"name": "quantity", "value": "1"}]}]}
  failureDetail: "Response contains preamble text before JSON"
# ──── sample[2] ──── anchor:392456de ────
  ...
```

Note that `sample[1]` has more lines than `sample[0]` (multi-line content, extra `failureDetail`). The anchor on `sample[2]` re-synchronises the diff regardless.

### REQ-DIFF-9: Scope

Anchor lines **must** be emitted in explore experiment output files. They **may** be emitted in other experiment types (measure, optimize) if those types produce `resultProjection` sections with sample blocks.
