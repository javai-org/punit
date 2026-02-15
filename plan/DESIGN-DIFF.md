# DESIGN-DIFF: Diff-Anchor Lines in Explore Experiment Output

## Overview

This document describes the detailed design for implementing REQ-DIFF — deterministic anchor comment lines between sample blocks in explore experiment YAML output. The design touches five areas: anchor generation, output format changes, removal of fixed-width normalization, YamlBuilder enhancement, and `@UseCase` annotation cleanup.

## 1. Anchor Generation

### 1.1 New Class: `DiffAnchorGenerator`

**Package:** `org.javai.punit.experiment.engine.output`

This package already contains `OutputUtilities` and `DescriptiveStatistics` — shared output concerns used by both explore and measure writers. Anchor generation is another output concern, so it belongs here.

```java
package org.javai.punit.experiment.engine.output;

import java.util.Random;

/**
 * Generates deterministic anchor values for diff-aligned sample blocks.
 *
 * <p>Anchors are 8-character lowercase hexadecimal strings derived from a
 * fixed seed and a sample index. The same seed and index always produce
 * the same anchor, guaranteeing that two exploration files from the same
 * experiment run contain identical anchors at identical positions.
 *
 * <p>The seed is hard-coded (not time-based), so anchors are also stable
 * across reruns of the same experiment with the same sample count.
 */
public final class DiffAnchorGenerator {

    private static final long SEED = 42L;

    private DiffAnchorGenerator() {}

    /**
     * Returns the anchor string for the given sample index.
     *
     * <p>Uses {@link java.util.Random} seeded at {@code 42L}, advanced to
     * position {@code sampleIndex} by generating and discarding intermediate
     * values. The anchor is the lower 32 bits of the {@code nextLong()} call,
     * formatted as 8-character zero-padded lowercase hex.
     *
     * @param sampleIndex the zero-based sample index
     * @return 8-character lowercase hex string (e.g. "a3b1799d")
     * @throws IllegalArgumentException if sampleIndex is negative
     */
    public static String anchorFor(int sampleIndex) {
        if (sampleIndex < 0) {
            throw new IllegalArgumentException("sampleIndex must be non-negative");
        }
        Random rng = new Random(SEED);
        // Advance to position sampleIndex
        for (int i = 0; i < sampleIndex; i++) {
            rng.nextLong();
        }
        long value = rng.nextLong();
        return String.format("%08x", (int) value);
    }

    /**
     * Returns the full anchor comment line for a sample.
     *
     * <p>Format: {@code # ──── sample[N] ──── anchor:XXXXXXXX ────}
     *
     * @param sampleIndex the zero-based sample index
     * @return the complete anchor comment line (no trailing newline)
     */
    public static String anchorLine(int sampleIndex) {
        return "# ──── sample[" + sampleIndex + "] ──── anchor:" + anchorFor(sampleIndex) + " ────";
    }
}
```

**Design decisions:**

- **`java.util.Random` over `SecureRandom`**: Cryptographic strength is unnecessary. We need determinism and speed. `Random` with a fixed seed satisfies both. (REQ-DIFF-3, REQ-DIFF-4)
- **Seed = 42**: A well-known constant. Hard-coded, not configurable, because stability across reruns is a requirement. (REQ-DIFF-4)
- **Recreate-and-advance strategy**: For each call, a new `Random(SEED)` is created and advanced to position N. This is simple, stateless, and correct. With realistic sample counts (~20–1000), the cost of advancing is negligible. An alternative (single `Random` instance advancing sequentially) would require the caller to manage state and call in order — fragile for no benefit.
- **Lower 32 bits of `nextLong()`**: Using `nextInt()` would also work, but `nextLong()` → cast to `int` produces the same distribution and keeps the door open for wider anchors in the future.
- **Static utility class**: No instance state needed. Matches the pattern of `OutputUtilities`.

### 1.2 Test Class: `DiffAnchorGeneratorTest`

**Package:** `org.javai.punit.experiment.engine.output` (test source set)

Tests:
1. **Determinism**: `anchorFor(0)` returns the same value on repeated calls.
2. **Stability across indices**: `anchorFor(0)` ≠ `anchorFor(1)` (different indices produce different anchors).
3. **Format**: Result is exactly 8 lowercase hex characters matching `[0-9a-f]{8}`.
4. **Anchor line format**: `anchorLine(0)` matches the expected decorated format.
5. **Negative index rejected**: `anchorFor(-1)` throws `IllegalArgumentException`.
6. **Known values**: Assert specific anchor values for indices 0, 1, 2 to catch accidental algorithm changes. These values will be determined by running the implementation once and recording the output.

## 2. Output Format Changes in `ExploreOutputWriter`

### 2.1 Replace `diffableContent` List with `content` Block Scalar

The current output format uses a YAML list under `diffableContent`:

```yaml
  sample[0]:
    input: Add some apples
    postconditions:
      Contains valid actions: passed
    executionTimeMs: 0
    diffableContent:
      - "result: ADD MILK"
      - <absent>
```

The new format replaces this with a `content` block scalar and optionally a `failureDetail` field:

```yaml
# ──── sample[0] ──── anchor:a3b1799d ────
  input: Add some apples
  postconditions:
    Contains valid actions: passed
  executionTimeMs: 0
  content: |
    {"actions": [{"context": "SHOP", "name": "add", ...}]}
```

### 2.2 Modified Method: `writeResultProjections`

The `resultProjection` object wrapper remains, and anchor comment lines are injected before each sample block. The `sample[N]:` YAML key is **retained** — it provides necessary YAML structure (each sample's fields must live in their own nested map to avoid key collisions). The anchor line provides diff sync while the key provides data structure.

```java
private void writeResultProjections(YamlBuilder builder, EmpiricalBaseline baseline) {
    if (!baseline.hasResultProjections()) {
        return;
    }
    builder.startObject("resultProjection");
    for (ResultProjection projection : baseline.getResultProjections()) {
        String anchorLine = DiffAnchorGenerator.anchorLine(projection.sampleIndex());
        builder.rawLine(anchorLine);  // New YamlBuilder method — see §3
        writeResultProjection(builder, projection);
    }
    builder.endObject();
}
```

### 2.3 Modified Method: `writeResultProjection`

Each sample retains its `sample[N]:` YAML object wrapper (required so that repeated field names like `input`, `postconditions` don't collide across samples in the same map). The `diffableContent` list is replaced by a `content` block scalar containing the full, untruncated content, and an optional `failureDetail` field.

```java
private void writeResultProjection(YamlBuilder builder, ResultProjection projection) {
    String sampleKey = "sample[" + projection.sampleIndex() + "]";
    builder.startObject(sampleKey);

    if (projection.input() != null) {
        builder.field("input", projection.input());
    }

    if (!projection.postconditions().isEmpty()) {
        builder.startObject("postconditions");
        for (var entry : projection.postconditions().entrySet()) {
            builder.field(entry.getKey(), entry.getValue());
        }
        builder.endObject();
    }

    builder.field("executionTimeMs", projection.executionTimeMs());

    if (projection.content() != null && !projection.content().isEmpty()) {
        builder.blockScalar("content", projection.content());
    }

    if (projection.failureDetail() != null) {
        builder.field("failureDetail", projection.failureDetail());
    }

    builder.endObject();
}
```

**Key changes:**
- `diffableContent` (list of truncated/padded strings) → `content` (block scalar with full text). (REQ-DIFF-6)
- `<absent>` padding removed. (REQ-DIFF-7)
- Anchor comment line emitted before each `sample[N]:` key for diff sync. (REQ-DIFF-1, REQ-DIFF-2)
- `sample[N]:` key retained for YAML structural integrity (fields need their own namespace).
- `failureDetail` is a new optional field for error information that was previously embedded in `diffableContent`.

### 2.4 Impact on `ResultProjection` Record

The `ResultProjection` record changes from:

```java
public record ResultProjection(
    int sampleIndex,
    String input,
    Map<String, String> postconditions,
    long executionTimeMs,
    List<String> diffableLines
)
```

To:

```java
public record ResultProjection(
    int sampleIndex,
    String input,
    Map<String, String> postconditions,
    long executionTimeMs,
    String content,
    String failureDetail
)
```

**Changes:**
- `diffableLines` (`List<String>`) → `content` (`String`): Full untruncated content as a single string. May be null if no content is available.
- New `failureDetail` (`String`): Optional error/failure explanation. Null when the sample succeeded.
- Remove `ABSENT` constant — no longer needed.
- Keep `PASSED`, `FAILED`, `SKIPPED` constants for postcondition status values.
- The compact constructor validates `sampleIndex >= 0`, defensively copies `postconditions`, and keeps `content` and `failureDetail` as-is (they're immutable strings).
- The `success()` method is unchanged.
- Update the Javadoc to remove references to fixed-width normalization and `<absent>` padding.

### 2.5 Impact on `ResultProjectionBuilder`

The builder changes significantly because it no longer performs line-count normalization or line-length truncation.

**Constructor changes:**
- Remove `maxDiffableLines` parameter — no longer needed.
- Remove `maxLineLength` parameter — no longer needed (content is emitted at full length per REQ-DIFF-6).
- Remove `customProvider` parameter — see §2.6.

New constructor:

```java
public ResultProjectionBuilder() {}
```

**`build()` method changes:**

```java
public ResultProjection build(int sampleIndex, UseCaseOutcome<?> outcome) {
    Objects.requireNonNull(outcome, "outcome must not be null");

    String input = extractInput(outcome.metadata());
    Map<String, String> postconditions = extractPostconditions(outcome);
    String content = extractContent(outcome);

    return new ResultProjection(
        sampleIndex,
        input,
        postconditions,
        outcome.executionTime().toMillis(),
        content,
        null  // no failure detail for successful outcomes
    );
}
```

**`buildError()` method changes:**

```java
public ResultProjection buildError(int sampleIndex, String input,
                                    long executionTimeMs, Throwable error) {
    Objects.requireNonNull(error, "error must not be null");

    String failureDetail = error.getClass().getSimpleName() + ": " + firstLine(error.getMessage());
    Map<String, String> postconditions = Map.of("Execution completed", ResultProjection.FAILED);

    return new ResultProjection(
        sampleIndex,
        input,
        postconditions,
        executionTimeMs,
        null,  // no content for error outcomes
        failureDetail
    );
}
```

**Removed methods:**
- `normalizeLineCount()` — no padding or truncation.
- `truncate(String)` — no line-length truncation.
- `truncationNotice(int)` — no truncation notices.
- `normalizeValue()` — newline escaping was needed for single-line diffable output; block scalars handle multi-line content natively.

**New method — `extractContent()`:**

```java
private String extractContent(UseCaseOutcome<?> outcome) {
    Object result = outcome.result();
    if (result == null) {
        return null;
    }
    return result.toString();
}
```

This replaces the elaborate record-reflection and line-truncation logic. With full-length block scalars, `toString()` is sufficient. Use cases that need custom formatting can override `toString()` on their result types, which is idiomatic Java.

### 2.6 Impact on `DiffableContentProvider`

The `DiffableContentProvider` interface exists to let use cases customize the per-line diffable content. With the move from fixed-width line lists to full-content block scalars, this interface's purpose changes.

**Option A — Deprecate and remove.** The new design emits `result.toString()` as the content. Use cases customize output by implementing `toString()` on their result types. This is simpler and more idiomatic.

**Option B — Evolve to `ContentProjectionProvider`.** Rename and change the contract:

```java
@FunctionalInterface
public interface ContentProjectionProvider {
    String getContent(UseCaseOutcome<?> outcome);
}
```

**Recommended: Option A.** The `DiffableContentProvider` was designed for a world of fixed-width line-based diffing. In the new anchor-based world, content is emitted verbatim. `toString()` is the natural customization point. We deprecate `DiffableContentProvider` in this release and remove it in the next.

If deprecation is chosen:
- Mark `DiffableContentProvider` with `@Deprecated(forRemoval = true)`.
- `ResultProjectionBuilder` ignores it (no longer accepts it in its constructor).
- The `@UseCase` annotation attributes `maxDiffableLines` and `diffableContentMaxLineLength` are deprecated.

### 2.7 Impact on `@UseCase` Annotation

Two attributes become obsolete:

```java
/** @deprecated Anchors provide alignment; fixed-width padding is no longer needed. */
@Deprecated(forRemoval = true)
int maxDiffableLines() default 5;

/** @deprecated Content is emitted at full length; line truncation is no longer needed. */
@Deprecated(forRemoval = true)
int diffableContentMaxLineLength() default 60;
```

These attributes are retained (with defaults) for binary compatibility, but annotated as deprecated. The `ExploreStrategy.createProjectionBuilder()` method stops reading them.

### 2.8 Impact on `ExploreStrategy`

The `createProjectionBuilder()` method simplifies:

```java
private ResultProjectionBuilder createProjectionBuilder() {
    return new ResultProjectionBuilder();
}
```

No longer reads `@UseCase.maxDiffableLines()`, `@UseCase.diffableContentMaxLineLength()`, or checks for `DiffableContentProvider`. The method signature drops the `useCaseClass` and `provider` parameters.

Call sites in `intercept()` change from:

```java
ResultProjectionBuilder projectionBuilder = createProjectionBuilder(useCaseClass, providerOpt.orElse(null));
```

To:

```java
ResultProjectionBuilder projectionBuilder = createProjectionBuilder();
```

## 3. YamlBuilder Enhancement

### 3.1 New Method: `rawLine(String)`

The `YamlBuilder` needs to emit raw text (the anchor comment line) at the current position in the YAML document. Currently, `YamlBuilder` builds a tree of maps and lists, then serializes them. A raw line cannot be expressed in this data model.

**Approach: Post-processing marker.**

Introduce a sentinel value type that `serializeMap` recognizes and emits as a raw line:

```java
private record RawLine(String text) {}
```

The `rawLine()` method inserts this sentinel into the current map context using a unique synthetic key that won't collide with real keys:

```java
private int rawLineCounter = 0;

/**
 * Inserts a raw text line at the current position in the YAML output.
 *
 * <p>The line is emitted exactly as provided, with no indentation or
 * formatting applied. This is used for YAML comments that must appear
 * at specific positions (e.g. diff anchor lines).
 *
 * @param text the raw line to emit (typically a YAML comment)
 * @return this builder
 */
public YamlBuilder rawLine(String text) {
    String syntheticKey = "\0raw:" + (rawLineCounter++);
    currentMap().put(syntheticKey, new RawLine(text));
    return this;
}
```

In `serializeMap`, the RawLine is detected and emitted:

```java
private String serializeMap(Map<String, Object> map, int indent) {
    StringBuilder sb = new StringBuilder();
    String prefix = "  ".repeat(indent);

    for (Map.Entry<String, Object> entry : map.entrySet()) {
        String key = entry.getKey();
        Object value = entry.getValue();

        // Raw lines are emitted verbatim, bypassing normal key: value formatting
        if (value instanceof RawLine rl) {
            sb.append(rl.text()).append("\n");
            continue;
        }

        sb.append(prefix).append(key).append(":");
        appendValue(sb, value, indent, key);
    }

    return sb.toString();
}
```

**Why a synthetic key with `\0` prefix:** The null character cannot appear in a YAML key, so there's zero risk of collision with real data keys. The counter ensures uniqueness when multiple raw lines are added to the same map context.

**Why not a separate `comments` list:** The existing `comments` field in `YamlBuilder` prepends comments to the document header. Anchor lines must appear at specific positions within the serialized map — interleaved with real keys. A marker in the map's iteration order is the simplest way to achieve positional control.

### 3.2 Indentation Behavior

The raw line is emitted **without indentation**. The anchor format from REQ-DIFF-1 starts at column 0:

```yaml
resultProjection:
# ──── sample[0] ──── anchor:a3b1799d ────
  input: Add some apples
```

This is valid YAML — comments are ignored by parsers regardless of indentation. Starting at column 0 makes anchors visually prominent as section dividers. (REQ-DIFF-5)

## 4. Test Plan

### 4.1 `DiffAnchorGeneratorTest`

As described in §1.2. Pure unit test, no dependencies.

### 4.2 `ResultProjectionBuilderTest` Updates

Existing tests must be updated to reflect the new `ResultProjection` shape:

- **Remove tests for `<absent>` padding** — no longer applies.
- **Remove tests for line-count normalization** — no longer applies.
- **Remove tests for truncation notices** — no longer applies.
- **Update `build()` tests**: Assert `content` is a string (not a list), assert `failureDetail` is null for success cases.
- **Update `buildError()` tests**: Assert `failureDetail` contains error class and message, assert `content` is null.
- **Remove custom provider tests** — `DiffableContentProvider` is deprecated.
- **Add test**: `build()` with a record result returns `toString()` as content.
- **Add test**: `build()` with null result returns null content.

### 4.3 `ExploreOutputWriterTest` Updates

Existing tests remain valid (they test sections unrelated to result projections). New tests:

- **Anchor lines present**: Given a baseline with 3 result projections, the YAML output contains `# ──── sample[0] ──── anchor:XXXXXXXX ────`, `# ──── sample[1] ──── anchor:XXXXXXXX ────`, `# ──── sample[2] ──── anchor:XXXXXXXX ────`.
- **Anchor values are deterministic**: Two calls to `toYaml()` with the same baseline produce identical anchor values.
- **No `diffableContent` key**: Output does not contain `diffableContent:`.
- **Content as block scalar**: Output contains `content: |` followed by indented content lines.
- **No `<absent>` placeholders**: Output does not contain `<absent>`.
- **No `sample[N]:` YAML keys**: Output does not contain `sample[0]:` as a YAML key (the sample identifier is in the anchor comment only).
- **Variable-length blocks**: Two projections with different content lengths both appear correctly, with anchors re-synchronising.
- **YAML parsability**: The output (minus the fingerprint line) is parseable by a YAML parser. Anchor comment lines are invisible to the parser. (REQ-DIFF-5)
- **failureDetail field**: When a projection has a non-null `failureDetail`, it appears in the output.

### 4.4 `YamlBuilderTest` Updates

- **`rawLine()` emits verbatim text**: A raw line inserted into a map appears at the correct position in the serialized output with no indentation.
- **Multiple raw lines preserve order**: Two raw lines added consecutively appear in insertion order.
- **Raw lines don't affect YAML structure**: The map surrounding a raw line still serializes correctly.

### 4.5 Integration Verification

Run existing explore experiment test subjects (via TestKit) and verify:
- Output files contain anchor lines.
- Output files are valid YAML (comments are ignored by parser).
- Diffing two output files from the same experiment shows clean, sample-aligned hunks.

## 5. Migration and Compatibility

### 5.1 Schema Version

The `schemaVersion` field remains `punit-spec-1`. Anchor lines are YAML comments and do not change the parsed structure. The content format change (`diffableContent` list → `content` block scalar) is a structural change within the `resultProjection` section, but explore output files are transient comparison artifacts, not consumed by the framework's spec-loading pipeline. No schema version bump is required.

### 5.2 Existing Exploration Files

Existing exploration files in `src/test/resources/punit/explorations/` will be overwritten on the next experiment run. No migration tooling is needed.

### 5.3 Deprecation Timeline

| Item | This Release | Next Release |
|------|-------------|-------------|
| `DiffableContentProvider` | `@Deprecated(forRemoval = true)` | Remove |
| `@UseCase.maxDiffableLines` | `@Deprecated(forRemoval = true)` | Remove |
| `@UseCase.diffableContentMaxLineLength` | `@Deprecated(forRemoval = true)` | Remove |

## 6. File Change Summary

| File | Change Type | Description |
|------|------------|-------------|
| `experiment/engine/output/DiffAnchorGenerator.java` | **New** | Deterministic anchor value generation |
| `experiment/engine/output/DiffAnchorGeneratorTest.java` | **New** | Tests for anchor generation |
| `experiment/model/ResultProjection.java` | **Modify** | `diffableLines` → `content` + `failureDetail`; remove `ABSENT` |
| `experiment/engine/ResultProjectionBuilder.java` | **Modify** | Remove normalization/truncation; simplify to `toString()`-based content |
| `experiment/engine/ResultProjectionBuilderTest.java` | **Modify** | Update tests for new `ResultProjection` shape |
| `experiment/explore/ExploreOutputWriter.java` | **Modify** | Emit anchor lines; write `content` as block scalar; remove `sample[N]:` keys |
| `experiment/explore/ExploreOutputWriterTest.java` | **Modify** | Add anchor and format tests |
| `experiment/engine/YamlBuilder.java` | **Modify** | Add `rawLine()` method and `RawLine` sentinel |
| `experiment/engine/YamlBuilderTest.java` | **Modify** | Add `rawLine()` tests |
| `experiment/explore/ExploreStrategy.java` | **Modify** | Simplify `createProjectionBuilder()` — no more params |
| `api/UseCase.java` | **Modify** | Deprecate `maxDiffableLines` and `diffableContentMaxLineLength` |
| `api/DiffableContentProvider.java` | **Modify** | Deprecate entire interface |

## 7. Scope Boundaries

### In Scope
- Explore experiment output (`ExploreOutputWriter`) — REQ-DIFF-9 "must".
- Removal of fixed-width normalization and truncation — REQ-DIFF-6, REQ-DIFF-7.
- Deprecation of obsolete API surface.

### Out of Scope
- Measure experiment output (`MeasureOutputWriter`) — REQ-DIFF-9 says "may", not "must". Measure output does not have a `resultProjection` section today.
- Optimize experiment output — same rationale.
- Schema version bump — not warranted (see §5.1).
- Any changes to spec-loading pipeline — explore output files are not loaded as specs.

## 8. Sequencing

The implementation should proceed in this order, each step building on the previous:

1. **`DiffAnchorGenerator`** + tests — standalone, no dependencies.
2. **`YamlBuilder.rawLine()`** + tests — standalone, no dependencies.
3. **`ResultProjection` record** — change fields. This will cause compile errors in dependent code.
4. **`ResultProjectionBuilder`** — simplify to match new `ResultProjection`. Update tests.
5. **`ExploreOutputWriter`** — emit anchors and new format. Update tests.
6. **`ExploreStrategy`** — simplify `createProjectionBuilder()`.
7. **`@UseCase` and `DiffableContentProvider`** — deprecation annotations.
8. **Integration verification** — run explore experiment test subjects, verify output.
