# Plan: Diff-Friendly EXPLORE Specs

## Overview

Enhance EXPLORE phase spec generation to include a diff-optimized text representation of use case results. This enables meaningful side-by-side comparison of exploration specs using standard diff tools (git diff, vimdiff, etc.).

## Motivation

The EXPLORE phase generates one spec per configuration:
```
src/test/resources/punit/explorations/
└── ShoppingUseCase/
    ├── model-gpt-4_temp-0.0.yaml
    ├── model-gpt-4_temp-0.7.yaml
    └── model-gpt-4_temp-1.0.yaml
```

Currently, diffs show metric differences but not the actual use case outputs. By including a structured, fixed-format result projection, developers can immediately see *what* differed between configurations.

## Requirements

### Result Projection Structure

Each sample's `UseCaseResult` produces a fixed-format text block with:

1. **Diffable Content**: One line per key-value pair, sorted alphabetically by key
2. **Timestamp**: Execution timestamp (will always differ, but provides context)
3. **Execution Time**: Duration in milliseconds

### Fixed Line Count

All specs must have the same number of result lines regardless of actual output:
- Missing values → `<absent>`
- Extra values beyond limit → `<truncated: +N more>`

### Configuration

Add attributes to `@UseCase` annotation:

```java
@UseCase(
    value = "shopping.search",
    maxDiffableLines = 5,
    diffableContentMaxLineLength = 60
)
public class ShoppingUseCase {
    // ...
}
```

- **`maxDiffableLines`**: Maximum number of lines to include in diff projection (default: 5)
- **`diffableContentMaxLineLength`**: Maximum characters per line (default: 60)

### Custom Projection

Use case classes can implement `DiffableContentProvider` to customize projection:

```java
@UseCase("shopping.search")
public class ShoppingUseCase implements DiffableContentProvider {
    
    @Override
    public List<String> getDiffableContent(UseCaseResult result, int maxLineLength) {
        // Custom projection logic
        return List.of(
            truncate("productCount: " + result.getInt("productCount", 0), maxLineLength),
            truncate("hasErrors: " + result.hasValue("error"), maxLineLength)
        );
    }
}
```

## Example Output

### Spec with `maxDiffableLines = 5`

```yaml
# In model-gpt-4_temp-0.7.yaml

resultProjection:
  sample[0]:
    timestamp: "2026-01-10T17:30:45.123Z"
    executionTimeMs: 245
    diffableContent:
      - "category: Electronics"
      - "isValid: true"
      - "productCount: 5"
      - "responseType: json"
      - "<absent>"
```

### Diff View (temp-0.0 vs temp-0.7)

```diff
  resultProjection:
    sample[0]:
-     timestamp: "2026-01-10T17:30:42.001Z"
+     timestamp: "2026-01-10T17:30:45.123Z"
-     executionTimeMs: 180
+     executionTimeMs: 245
      diffableContent:
        - "category: Electronics"
        - "isValid: true"
-       - "productCount: 3"
+       - "productCount: 5"
        - "responseType: json"
        - "<absent>"
```

### Truncation Example

If a value exceeds `diffableContentMaxLineLength`:

```yaml
diffableContent:
  - "description: This is a very long product description t…"
  - "productName: Wireless Noise-Canceling Headphones"
```

Note: Uses ellipsis character (…) as visual truncation indicator.

### Line Count Truncation

If result has 8 values but `maxDiffableLines = 5`:

```yaml
diffableContent:
  - "fieldA: value1"
  - "fieldB: value2"
  - "fieldC: value3"
  - "fieldD: value4"
  - "fieldE: value5"
  - "<truncated: +3 more>"
```

Note: The truncation notice does not count toward `maxDiffableLines`. You always get up to `maxDiffableLines` actual content lines, plus a truncation notice if needed.

**Diff alignment implication**: This means truncated results have `maxDiffableLines + 1` lines while non-truncated results have exactly `maxDiffableLines` lines. If one config produces truncated output and another doesn't, lines after the truncation point will be offset by 1. Consider setting `maxDiffableLines` high enough to avoid truncation in typical cases.

### Absent Example

If result has only 2 values but `maxDiffableLines = 5`:

```yaml
diffableContent:
  - "fieldA: value1"
  - "fieldB: value2"
  - "<absent>"
  - "<absent>"
  - "<absent>"
```

## Line Formatting

### Value Lines

```
"{key}: {truncatedValue}"
```

- Key: as-is (alphabetically sorted)
- Value: truncated with ellipsis (…) if exceeds available space
- Total line length: ≤ `diffableContentMaxLineLength` characters

### Default Truncation (in UseCaseResult.getDiffableContent)

```java
private String formatLine(String key, Object value, int maxLineLength) {
    String valueStr = normalizeValue(value);
    String prefix = key + ": ";
    int maxValueLength = maxLineLength - prefix.length();
    
    if (maxValueLength <= 0) {
        return key.substring(0, Math.min(key.length(), maxLineLength - 1)) + "…";
    }
    
    if (valueStr.length() > maxValueLength) {
        valueStr = valueStr.substring(0, maxValueLength - 1) + "…";
    }
    
    return prefix + valueStr;
}
```

## Multi-Sample Handling

When `samplesPerConfig > 1`:

```yaml
resultProjection:
  sample[0]:
    timestamp: "..."
    executionTimeMs: 123
    diffableContent:
      - "key1: val1"
      - "<absent>"
  sample[1]:
    timestamp: "..."
    executionTimeMs: 145
    diffableContent:
      - "key1: val2"
      - "<absent>"
  sample[2]:
    timestamp: "..."
    executionTimeMs: 132
    diffableContent:
      - "key1: val1"
      - "<absent>"
```

## Implementation Phases

### Phase 1: UseCaseResult Refactoring
- [ ] Refactor `UseCaseResult` from class to Java record
- [ ] Add compact constructor with defensive copying (`Map.copyOf()`)
- [ ] Add `getDiffableContent(int maxLineLength)` method with default implementation
- [ ] Implement alphabetical key sorting via stream + `comparingByKey()`
- [ ] Implement value truncation with ellipsis (…)
- [ ] Implement newline escaping (`\n` → `\\n`)
- [ ] Preserve convenience methods (`getBoolean`, `getInt`, etc.)
- [ ] Preserve Builder pattern
- [ ] Add deprecated bridge methods for old accessors (`getAllValues()` → `values()`)

### Phase 2: DiffableContentProvider Interface
- [ ] Create `DiffableContentProvider` functional interface
- [ ] Define `List<String> getDiffableContent(UseCaseResult result, int maxLineLength)`
- [ ] Add Javadoc with usage examples

### Phase 3: UseCase Annotation Enhancement
- [ ] Add `maxDiffableLines` attribute (default: 5)
- [ ] Add `diffableContentMaxLineLength` attribute (default: 60)
- [ ] Update annotation Javadoc

### Phase 4: ResultProjectionBuilder
- [ ] Create `ResultProjection` record with `diffableLines`
- [ ] Create `ResultProjectionBuilder` class
- [ ] Accept optional `DiffableContentProvider` in constructor
- [ ] Implement line count normalization (`<absent>` padding, `<truncated>` notice)
- [ ] Implement error case projection

### Phase 5: Integration with BaselineWriter
- [ ] Extend `EmpiricalBaseline` to hold result projections
- [ ] Update `BaselineWriter.buildYamlContent()` to emit `resultProjection` section
- [ ] Rename YAML key from `values:` to `diffableContent:`
- [ ] Ensure projection only emitted in EXPLORE mode
- [ ] Handle multi-sample scenarios

### Phase 6: Experiment Extension Integration
- [ ] Detect if use case class implements `DiffableContentProvider`
- [ ] Resolve `maxDiffableLines` and `diffableContentMaxLineLength` from annotation
- [ ] Capture `UseCaseResult` during EXPLORE execution
- [ ] Build projections and store in aggregator
- [ ] Handle error cases

### Phase 7: Testing
- [ ] Unit tests for `UseCaseResult.getDiffableContent()`
  - [ ] Alphabetical ordering
  - [ ] Truncation with ellipsis
  - [ ] Newline escaping
  - [ ] Empty values map
  - [ ] Null values
- [ ] Unit tests for `ResultProjectionBuilder`
  - [ ] Absent padding
  - [ ] Truncation notice
  - [ ] Custom provider integration
  - [ ] Error case projection
- [ ] Unit tests for `DiffableContentProvider`
  - [ ] Custom provider output used
  - [ ] Fewer lines than max handled
- [ ] Integration tests
  - [ ] Generate two specs and verify diff alignment
  - [ ] Verify consistent line count across configs

### Phase 8: Documentation
- [ ] Document `maxDiffableLines` and `diffableContentMaxLineLength` in USER-GUIDE.md
- [ ] Document `DiffableContentProvider` interface with examples
- [ ] Add example of diff workflow to OPERATIONAL-FLOW.md
- [ ] Update README with EXPLORE diff feature
- [ ] Document migration from class to record accessors

## Design Decisions

### Why Refactor UseCaseResult to a Record?

`UseCaseResult` is an immutable data carrier—exactly what records are designed for. Benefits:
- Guaranteed immutability by language spec
- Auto-generated `equals()`, `hashCode()`, `toString()`
- Reduced boilerplate (~340 lines → ~100 lines)
- Clear intent: "This is pure data"

### Why DiffableContentProvider Interface?

Putting projection logic on the use case class (via interface) rather than subclassing `UseCaseResult`:
- Preserves `UseCaseResult` immutability (records can't be extended)
- Allows domain-specific customization without framework changes
- Framework auto-detects via `instanceof` check
- Follows composition over inheritance

### Why Alphabetical Order?

Consistent ordering ensures the same keys appear on the same lines across configs, enabling line-by-line diff alignment.

### Why Ellipsis (…) Instead of "..."?

- Single character (saves 2 chars of line space)
- Universally recognized visual cue for truncation
- Modern terminals and diff tools handle UTF-8 well

### Why Include Timestamp?

Even though it always differs, it provides temporal context. Users know *when* each config was evaluated, which matters if external factors (API versions, data state) might affect results.

### Why Fixed Line Count?

Diff tools align by line number. Variable line counts cause misalignment:

```
# BAD: Variable lines
Config A:        Config B:
key1: val1       key1: val1
key2: val2       key2: val2
                 key3: val3    ← new key pushes everything down
key4: val4       key4: val4    ← now misaligned
```

```
# GOOD: Fixed lines with <absent>
Config A:        Config B:
key1: val1       key1: val1
key2: val2       key2: val2
<absent>         key3: val3    ← difference visible, alignment preserved
key4: val4       key4: val4
```

### Why Default of 60 for maxLineLength?

Balances information density with readability in split-pane diff views:
- 40: Too aggressive, clips common content
- 60: Works well in most IDE split views
- 80: Full terminal width, may wrap in narrow panes

### Why Default of 5 for maxDiffableLines?

Provides reasonable visibility into results without bloating spec files:
- 1: Too minimal for most use cases
- 5: Shows key fields without overwhelming
- 10+: Starts to dominate spec file size

## Success Criteria

- [ ] `UseCaseResult` is a Java record with `getDiffableContent()` method
- [ ] `DiffableContentProvider` interface exists and is detected by framework
- [ ] EXPLORE specs include `resultProjection` section with `diffableContent`
- [ ] All specs for same use case have identical line structure
- [ ] `diff spec1.yaml spec2.yaml` shows meaningful, aligned differences
- [ ] Value ordering is deterministic (alphabetical)
- [ ] Long values are truncated with ellipsis (…)
- [ ] Missing/excess lines use consistent placeholders
- [ ] Custom providers work correctly when implemented

## References

- Design document: `DESIGN-DIFF-FRIENDLY-EXPLORE.md`
- UseCase annotation: `src/main/java/org/javai/punit/api/UseCase.java`
- UseCaseResult: `src/main/java/org/javai/punit/experiment/model/UseCaseResult.java`
