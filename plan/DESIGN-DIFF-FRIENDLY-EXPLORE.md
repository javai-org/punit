# Diff-Friendly EXPLORE Specs Design

## 1. Problem Statement

When exploring LLM configurations, developers compare multiple spec files to understand how different settings affect behavior. Current specs show statistical summaries but not the actual outputs, making it difficult to answer questions like:

- "What did temperature 0.0 actually produce compared to 0.7?"
- "Did the model hallucinate more fields at higher temperatures?"
- "Was the JSON structure different between models?"

This design introduces a **result projection** mechanism that embeds a diff-optimized view of use case results directly in EXPLORE specs.

## 2. Design Goals

| Goal                | Description                                                |
|---------------------|------------------------------------------------------------|
| **Diff Alignment**  | Same keys appear on same lines across all config specs     |
| **Fixed Structure** | Identical line count regardless of actual result content   |
| **Readable**        | Human-scannable without tooling                            |
| **Truncated**       | Long values abbreviated for side-by-side viewing           |
| **Deterministic**   | Same input → same projection (no random ordering)          |
| **Extensible**      | Developers can customize projection logic for their domain |

## 3. Data Model

### 3.1 UseCaseResult as a Record

`UseCaseResult` is refactored from a class to a Java record, reflecting its nature as an immutable data carrier:

```java
/**
 * A neutral container for observed outcomes from a use case invocation.
 *
 * <p>{@code UseCaseResult} holds key-value pairs representing the observations
 * made during use case execution. It is:
 * <ul>
 *   <li><strong>Neutral and descriptive</strong>: Contains data, not judgments</li>
 *   <li><strong>Flexible</strong>: The {@code Map<String, Object>} allows domain-specific values</li>
 *   <li><strong>Immutable</strong>: Guaranteed by the record specification</li>
 * </ul>
 */
public record UseCaseResult(
    Map<String, Object> values,
    Map<String, Object> metadata,
    Instant timestamp,
    Duration executionTime
) {
    /**
     * Compact constructor for defensive copying and validation.
     */
    public UseCaseResult {
        values = values != null ? Map.copyOf(values) : Map.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(executionTime, "executionTime must not be null");
    }
    
    /**
     * Returns the default diffable content for this result.
     *
     * <p>The default implementation:
     * <ul>
     *   <li>Iterates over the values map</li>
     *   <li>Converts each value to a string via {@code toString()}</li>
     *   <li>Sorts entries alphabetically by key</li>
     *   <li>Formats as "key: value" lines</li>
     *   <li>Truncates values exceeding maxLineLength with ellipsis (…)</li>
     * </ul>
     *
     * @param maxLineLength maximum characters per line (including key and separator)
     * @return list of formatted key-value lines, alphabetically ordered
     */
    public List<String> getDiffableContent(int maxLineLength) {
        return values.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> formatLine(e.getKey(), e.getValue(), maxLineLength))
            .toList();
    }
    
    private String formatLine(String key, Object value, int maxLineLength) {
        String valueStr = normalizeValue(value);
        String prefix = key + ": ";
        int maxValueLength = maxLineLength - prefix.length();
        
        if (maxValueLength <= 0) {
            // Key is too long; show key with ellipsis
            return key.substring(0, Math.min(key.length(), maxLineLength - 1)) + "…";
        }
        
        if (valueStr.length() > maxValueLength) {
            valueStr = valueStr.substring(0, maxValueLength - 1) + "…";
        }
        
        return prefix + valueStr;
    }
    
    private String normalizeValue(Object value) {
        if (value == null) {
            return "null";
        }
        // Single-line representation: escape control characters
        return value.toString()
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
    
    // Convenience accessors (delegating to values map)
    
    public boolean getBoolean(String key, boolean defaultValue) {
        Object val = values.get(key);
        return val instanceof Boolean b ? b : defaultValue;
    }
    
    public int getInt(String key, int defaultValue) {
        Object val = values.get(key);
        return val instanceof Number n ? n.intValue() : defaultValue;
    }
    
    public long getLong(String key, long defaultValue) {
        Object val = values.get(key);
        return val instanceof Number n ? n.longValue() : defaultValue;
    }
    
    public double getDouble(String key, double defaultValue) {
        Object val = values.get(key);
        return val instanceof Number n ? n.doubleValue() : defaultValue;
    }
    
    public String getString(String key, String defaultValue) {
        Object val = values.get(key);
        return val instanceof String s ? s : defaultValue;
    }
    
    public boolean hasValue(String key) {
        return values.containsKey(key);
    }
    
    /**
     * Builder for constructing {@code UseCaseResult} instances.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private final Map<String, Object> values = new LinkedHashMap<>();
        private final Map<String, Object> metadata = new LinkedHashMap<>();
        private Instant timestamp = Instant.now();
        private Duration executionTime = Duration.ZERO;
        
        public Builder value(String key, Object val) {
            Objects.requireNonNull(key, "key must not be null");
            values.put(key, val);
            return this;
        }
        
        public Builder meta(String key, Object val) {
            Objects.requireNonNull(key, "key must not be null");
            metadata.put(key, val);
            return this;
        }
        
        public Builder timestamp(Instant ts) {
            this.timestamp = Objects.requireNonNull(ts);
            return this;
        }
        
        public Builder executionTime(Duration d) {
            this.executionTime = Objects.requireNonNull(d);
            return this;
        }
        
        public UseCaseResult build() {
            return new UseCaseResult(values, metadata, timestamp, executionTime);
        }
    }
}
```

### 3.2 DiffableContentProvider Interface

Use case classes can implement `DiffableContentProvider` to customize how their results are projected for diffing:

```java
/**
 * Interface for use case classes that want to customize diff projection.
 *
 * <p>When a use case class implements this interface, the framework uses
 * its {@link #getDiffableContent} method instead of the default algorithm
 * on {@link UseCaseResult}.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * @UseCase("shopping.product.search")
 * public class ShoppingUseCase implements DiffableContentProvider {
 *     
 *     @Override
 *     public List<String> getDiffableContent(UseCaseResult result, int maxLineLength) {
 *         // Custom projection: summarize instead of showing raw values
 *         int productCount = result.getInt("productCount", 0);
 *         boolean hasErrors = result.hasValue("error");
 *         
 *         return List.of(
 *             truncate("productCount: " + productCount, maxLineLength),
 *             truncate("hasErrors: " + hasErrors, maxLineLength)
 *         );
 *     }
 *     
 *     private String truncate(String s, int max) {
 *         return s.length() > max ? s.substring(0, max - 1) + "…" : s;
 *     }
 * }
 * }</pre>
 *
 * <h2>When to Use</h2>
 * <ul>
 *   <li>When default toString() representations are too verbose</li>
 *   <li>When you want to exclude noisy fields (timestamps, request IDs)</li>
 *   <li>When you want to show computed summaries instead of raw data</li>
 *   <li>When domain objects need special formatting</li>
 * </ul>
 */
@FunctionalInterface
public interface DiffableContentProvider {
    
    /**
     * Returns custom diffable content for a use case result.
     *
     * <p>The returned list should:
     * <ul>
     *   <li>Have a consistent number of lines for the same use case</li>
     *   <li>Be alphabetically ordered by conceptual key for diff alignment</li>
     *   <li>Respect the maxLineLength to avoid diff tool wrapping</li>
     * </ul>
     *
     * @param result the use case result to project
     * @param maxLineLength maximum characters per line
     * @return list of formatted lines for diff comparison
     */
    List<String> getDiffableContent(UseCaseResult result, int maxLineLength);
}
```

### 3.3 Result Projection Record

```java
/**
 * A diff-optimized projection of a UseCaseResult.
 *
 * <p>Designed for line-by-line comparison in diff tools. All projections
 * for the same use case have identical structure regardless of actual
 * content, using placeholders for missing or excess values.
 */
public record ResultProjection(
    int sampleIndex,
    Instant timestamp,
    long executionTimeMs,
    List<String> diffableLines
) {
    /**
     * Placeholder for values that don't exist in this result
     * but are expected based on maxDiffableLines configuration.
     */
    public static final String ABSENT = "<absent>";
    
    /**
     * Compact constructor for defensive copying.
     */
    public ResultProjection {
        diffableLines = List.copyOf(diffableLines);
    }
}
```

### 3.4 UseCase Annotation Enhancement

```java
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface UseCase {
    
    /**
     * The use case identifier.
     *
     * <p>If empty (default), the simple class name is used as the ID.
     *
     * @return the use case ID, or empty string to use class name
     */
    String value() default "";
    
    /**
     * Maximum number of content lines to include in result projections.
     *
     * <p>When a result has fewer lines than this limit, remaining lines
     * are filled with {@code <absent>}. When a result has more lines,
     * exactly {@code maxDiffableLines} content lines are shown, followed
     * by a {@code <truncated: +N more>} notice (which does not count
     * toward this limit).
     *
     * <p>For perfect diff alignment, set this high enough to avoid
     * truncation in typical cases.
     *
     * @return maximum content lines (default: 5)
     */
    int maxDiffableLines() default 5;
    
    /**
     * Maximum characters per line in diffable content.
     *
     * <p>Lines exceeding this length are truncated with an ellipsis (…).
     * This ensures content displays well in side-by-side diff tools.
     *
     * <p>Consider your diff tool's typical viewport when setting this:
     * <ul>
     *   <li>40: Conservative, works with narrow panes</li>
     *   <li>60: Balanced for most IDEs in split view</li>
     *   <li>80: Full terminal width</li>
     * </ul>
     *
     * @return maximum line length (default: 60)
     */
    int diffableContentMaxLineLength() default 60;
}
```

### 3.5 Extended EmpiricalBaseline

```java
public final class EmpiricalBaseline {
    
    // ... existing fields ...
    
    /**
     * Result projections for EXPLORE mode.
     * Empty for MEASURE mode.
     */
    private final List<ResultProjection> resultProjections;
    
    // ... existing methods ...
    
    public List<ResultProjection> getResultProjections() {
        return resultProjections;
    }
    
    public boolean hasResultProjections() {
        return resultProjections != null && !resultProjections.isEmpty();
    }
}
```

## 4. Result Projection Builder

### 4.1 Core Builder Class

```java
/**
 * Builds diff-optimized result projections from UseCaseResult instances.
 *
 * <p>Supports two projection modes:
 * <ul>
 *   <li><strong>Default</strong>: Uses {@link UseCaseResult#getDiffableContent}</li>
 *   <li><strong>Custom</strong>: Uses {@link DiffableContentProvider#getDiffableContent}
 *       when the use case class implements the interface</li>
 * </ul>
 *
 * <p>Line count behavior:
 * <ul>
 *   <li>Results with fewer lines than max: padded with {@code <absent>}</li>
 *   <li>Results with exactly max lines: no placeholders</li>
 *   <li>Results with more than max lines: max content lines + {@code <truncated: +N more>}</li>
 * </ul>
 *
 * <p>Note: The truncation notice does not count toward {@code maxDiffableLines}.
 */
public class ResultProjectionBuilder {
    
    private final int maxDiffableLines;
    private final int maxLineLength;
    private final DiffableContentProvider customProvider;
    
    /**
     * Creates a builder with default projection.
     */
    public ResultProjectionBuilder(int maxDiffableLines, int maxLineLength) {
        this(maxDiffableLines, maxLineLength, null);
    }
    
    /**
     * Creates a builder with optional custom projection.
     *
     * @param maxDiffableLines maximum lines to include
     * @param maxLineLength maximum characters per line
     * @param customProvider custom provider, or null for default behavior
     */
    public ResultProjectionBuilder(int maxDiffableLines, int maxLineLength,
                                    DiffableContentProvider customProvider) {
        if (maxDiffableLines < 1) {
            throw new IllegalArgumentException("maxDiffableLines must be at least 1");
        }
        if (maxLineLength < 10) {
            throw new IllegalArgumentException("maxLineLength must be at least 10");
        }
        this.maxDiffableLines = maxDiffableLines;
        this.maxLineLength = maxLineLength;
        this.customProvider = customProvider;
    }
    
    /**
     * Builds a projection from a use case result.
     *
     * @param sampleIndex the sample index (0-based)
     * @param result the use case result
     * @return the projection
     */
    public ResultProjection build(int sampleIndex, UseCaseResult result) {
        List<String> rawLines = getDiffableLines(result);
        List<String> normalizedLines = normalizeLineCount(rawLines);
        
        return new ResultProjection(
            sampleIndex,
            result.timestamp(),
            result.executionTime().toMillis(),
            normalizedLines
        );
    }
    
    /**
     * Builds a projection for an error case.
     */
    public ResultProjection buildError(int sampleIndex, Instant timestamp, 
                                        long executionTimeMs, Throwable error) {
        List<String> lines = new ArrayList<>();
        lines.add(truncate("error: " + error.getClass().getSimpleName()));
        lines.add(truncate("message: " + firstLine(error.getMessage())));
        
        List<String> normalizedLines = normalizeLineCount(lines);
        
        return new ResultProjection(sampleIndex, timestamp, executionTimeMs, normalizedLines);
    }
    
    private List<String> getDiffableLines(UseCaseResult result) {
        if (customProvider != null) {
            return customProvider.getDiffableContent(result, maxLineLength);
        }
        return result.getDiffableContent(maxLineLength);
    }
    
    private List<String> normalizeLineCount(List<String> lines) {
        List<String> normalized = new ArrayList<>();
        
        // Add up to maxDiffableLines of actual content
        int contentLinesToAdd = Math.min(lines.size(), maxDiffableLines);
        for (int i = 0; i < contentLinesToAdd; i++) {
            normalized.add(lines.get(i));
        }
        
        // Add truncation notice if there are more lines (does NOT count toward maxDiffableLines)
        int remaining = lines.size() - contentLinesToAdd;
        if (remaining > 0) {
            normalized.add(truncationNotice(remaining));
        }
        
        // Pad with <absent> if needed to reach maxDiffableLines
        // (only when no truncation occurred)
        while (normalized.size() < maxDiffableLines) {
            normalized.add(ResultProjection.ABSENT);
        }
        
        return List.copyOf(normalized);
    }
    
    private String truncate(String text) {
        if (text.length() <= maxLineLength) {
            return text;
        }
        return text.substring(0, maxLineLength - 1) + "…";
    }
    
    private String truncationNotice(int remaining) {
        return "<truncated: +" + remaining + " more>";
    }
    
    private String firstLine(String text) {
        if (text == null) return "null";
        int newline = text.indexOf('\n');
        return newline > 0 ? text.substring(0, newline) : text;
    }
}
```

### 4.2 Formatting Rules

#### Line Format

```
"{key}: {value}"
```

| Component | Rule |
|-----------|------|
| Key | As-is, alphabetically sorted |
| Separator | `: ` (colon + space) |
| Value | Truncated with ellipsis (…) if exceeds limit |
| Total | ≤ `diffableContentMaxLineLength` characters |

#### Truncation Examples

```java
// maxLineLength = 60
// Original: "description" → "This is a very long product description that goes on..."
// After truncation:
"description: This is a very long product description t…"

// Original: "x" → "short"  
// No truncation needed:
"x: short"
```

#### Placeholder Lines

| Scenario | Line Count | Behavior |
|----------|------------|----------|
| Fewer lines than `maxDiffableLines` | Exactly `maxDiffableLines` | Pad with `<absent>` |
| Exactly `maxDiffableLines` | Exactly `maxDiffableLines` | No placeholders needed |
| More lines than `maxDiffableLines` | `maxDiffableLines + 1` | Show max lines + `<truncated: +N more>` |

**Note**: The truncation notice does not count toward `maxDiffableLines`. This means truncated results have one additional line. Set `maxDiffableLines` high enough to avoid truncation in typical cases to maintain perfect diff alignment.

## 5. YAML Output Format

### 5.1 Section Structure

```yaml
# Added to EXPLORE specs only
resultProjection:
  sample[0]:
    timestamp: "2026-01-10T17:30:45.123456Z"
    executionTimeMs: 245
    diffableContent:
      - "category: Electronics"
      - "price: 29.99"
      - "productName: Wireless Mouse"
      - "<absent>"
      - "<absent>"
  sample[1]:
    timestamp: "2026-01-10T17:30:45.456789Z"
    executionTimeMs: 198
    diffableContent:
      - "category: Electronics"
      - "price: 49.99"
      - "productName: Mechanical Keyboard"
      - "<absent>"
      - "<absent>"
```

### 5.2 BaselineWriter Extension

```java
private String buildYamlContent(EmpiricalBaseline baseline) {
    StringBuilder sb = new StringBuilder();
    
    // ... existing content ...
    
    // Result projections (EXPLORE mode only)
    if (baseline.hasResultProjections()) {
        sb.append("\nresultProjection:\n");
        
        for (ResultProjection projection : baseline.getResultProjections()) {
            sb.append("  sample[").append(projection.sampleIndex()).append("]:\n");
            sb.append("    timestamp: \"")
              .append(ISO_FORMATTER.format(projection.timestamp()))
              .append("\"\n");
            sb.append("    executionTimeMs: ")
              .append(projection.executionTimeMs())
              .append("\n");
            sb.append("    diffableContent:\n");
            
            for (String line : projection.diffableLines()) {
                sb.append("      - \"")
                  .append(escapeYamlString(line))
                  .append("\"\n");
            }
        }
    }
    
    return sb.toString();
}
```

## 6. Value Ordering

### 6.1 Alphabetical Requirement

Values MUST appear in alphabetical key order to ensure diff alignment. The default `UseCaseResult.getDiffableContent()` implementation handles this automatically:

```java
// Default implementation sorts by key
return values.entrySet().stream()
    .sorted(Map.Entry.comparingByKey())
    .map(e -> formatLine(e.getKey(), e.getValue(), maxLineLength))
    .toList();
```

Custom `DiffableContentProvider` implementations should also maintain alphabetical order.

### 6.2 Cross-Config Alignment

Given two configs with different values:

| Config A | Config B |
|----------|----------|
| `alpha: 1` | `alpha: 2` |
| `beta: x` | `beta: y` |
| `gamma: true` | `<absent>` |

Because keys are alphabetically sorted, `alpha` is always line 1, `beta` is always line 2, etc. This enables meaningful diff output:

```diff
  diffableContent:
-   - "alpha: 1"
+   - "alpha: 2"
-   - "beta: x"
+   - "beta: y"
-   - "gamma: true"
+   - "<absent>"
```

## 7. Integration with Experiment Extension

### 7.1 Result Capture

```java
// In ExperimentExtension.interceptTestTemplateMethod()

// Resolve projection settings
UseCase annotation = useCaseClass.getAnnotation(UseCase.class);
int maxLines = annotation != null ? annotation.maxDiffableLines() : 5;
int maxLineLength = annotation != null ? annotation.diffableContentMaxLineLength() : 60;

// Check for custom provider
DiffableContentProvider customProvider = null;
if (useCaseInstance instanceof DiffableContentProvider provider) {
    customProvider = provider;
}

ResultProjectionBuilder projectionBuilder = 
    new ResultProjectionBuilder(maxLines, maxLineLength, customProvider);

// After successful execution
UseCaseResult result = captor.getResult();
if (result != null && mode == ExperimentMode.EXPLORE) {
    ResultProjection projection = projectionBuilder.build(sampleIndex, result);
    aggregator.addResultProjection(projection);
}

// After failed execution
if (mode == ExperimentMode.EXPLORE) {
    ResultProjection projection = projectionBuilder.buildError(
        sampleIndex, Instant.now(), executionTimeMs, exception);
    aggregator.addResultProjection(projection);
}
```

### 7.2 Configuration Resolution

```java
private ProjectionConfig getProjectionConfig(Class<?> useCaseClass, Object useCaseInstance) {
    UseCase annotation = useCaseClass.getAnnotation(UseCase.class);
    
    int maxLines = annotation != null ? annotation.maxDiffableLines() : 5;
    int maxLineLength = annotation != null ? annotation.diffableContentMaxLineLength() : 60;
    
    DiffableContentProvider provider = null;
    if (useCaseInstance instanceof DiffableContentProvider p) {
        provider = p;
    }
    
    return new ProjectionConfig(maxLines, maxLineLength, provider);
}

private record ProjectionConfig(
    int maxLines, 
    int maxLineLength, 
    DiffableContentProvider provider
) {}
```

## 8. Multi-Sample Considerations

### 8.1 Sample Indexing

Each sample gets a unique index within its config:

```yaml
resultProjection:
  sample[0]:
    # First sample for this config
  sample[1]:
    # Second sample for this config
  sample[2]:
    # Third sample for this config
```

### 8.2 Line Count Consistency

For `samplesPerConfig = 3` and `maxDiffableLines = 5`:

- Each config spec has exactly 3 samples
- Each sample has exactly 5 diffable lines (plus timestamp and executionTimeMs)
- Total structure lines: 3 × (3 + 5) = 24 lines

This consistency is maintained across ALL config specs.

## 9. Edge Cases

### 9.1 Empty Values Map

```yaml
diffableContent:
  - "<absent>"
  - "<absent>"
  - "<absent>"
  - "<absent>"
  - "<absent>"
```

### 9.2 Null Value

```java
values.put("key", null);
// Becomes:
"key: null"
```

### 9.3 Complex Object Value

```java
values.put("product", new Product("Widget", 29.99));
// Uses toString():
"product: Product{name=Widget, price=29.99}"
```

### 9.4 Very Long Key

Keys are not truncated separately (they're under developer control). If a key + separator approaches maxLineLength, the value portion shrinks or disappears:

```java
// Key: "extremelyLongKeyNameThatTakesUpMostOfTheLine" (44 chars)
// Separator: ": " (2 chars)
// maxLineLength: 60
// Remaining for value: 60 - 44 - 2 = 14 chars
"extremelyLongKeyNameThatTakesUpMostOfTheLine: This is tru…"
```

### 9.5 Error Results

Errors get a special two-line format:

```yaml
diffableContent:
  - "error: JsonParseException"
  - "message: Unexpected token at position 42"
  - "<absent>"
  - "<absent>"
  - "<absent>"
```

### 9.6 Custom Provider Returns Too Few Lines

The framework pads with `<absent>`:

```java
// Custom provider returns 2 lines, maxDiffableLines = 5
@Override
public List<String> getDiffableContent(UseCaseResult result, int maxLineLength) {
    return List.of("line1: value1", "line2: value2");
}
// Output includes 3 <absent> lines to reach 5
```

## 10. Schema Validation

### 10.1 SpecSchemaValidator Update

Add validation for `resultProjection` section:

```java
private static void validateResultProjection(String content, List<String> errors) {
    if (!content.contains("resultProjection:")) {
        return; // Optional section, valid to omit
    }
    
    // Validate structure if present
    // - Each sample[N] must have timestamp, executionTimeMs, diffableContent
    // - diffableContent must be a list
}
```

### 10.2 Fingerprint Handling

The `resultProjection` section is included in fingerprint computation, ensuring tampering is detected.

## 11. Performance Considerations

### 11.1 Memory

- Each `ResultProjection` is small (index, timestamp, long, list of strings)
- For `samplesPerConfig = 10` and `maxDiffableLines = 5`: ~50 strings per config
- Negligible compared to actual LLM responses

### 11.2 Execution Time

- Stream sorting: O(n log n) where n = number of values
- String truncation: O(1) per value
- Total overhead: microseconds per sample

### 11.3 Spec File Size

For `maxDiffableLines = 5` and `samplesPerConfig = 3`:
- ~15 diffable lines × ~60 chars = ~0.9 KB per spec
- Acceptable for version control

## 12. Migration Guide

### 12.1 UseCaseResult API Changes

The refactoring from class to record changes accessor method names:

| Old (Class) | New (Record) |
|-------------|--------------|
| `getAllValues()` | `values()` |
| `getAllMetadata()` | `metadata()` |
| `getTimestamp()` | `timestamp()` |
| `getExecutionTime()` | `executionTime()` |

Convenience methods remain unchanged:
- `getBoolean(String key, boolean defaultValue)`
- `getInt(String key, int defaultValue)`
- `getString(String key, String defaultValue)`
- etc.

### 12.2 Deprecation Bridge

During migration, the old accessors can be provided as deprecated methods:

```java
public record UseCaseResult(...) {
    
    @Deprecated(forRemoval = true)
    public Map<String, Object> getAllValues() {
        return values();
    }
    
    @Deprecated(forRemoval = true)
    public Instant getTimestamp() {
        return timestamp();
    }
    
    // etc.
}
```

## 13. Testing Strategy

### 13.1 Unit Tests

```java
@Nested
class UseCaseResultTest {
    
    @Test
    void getDiffableContentSortsKeysAlphabetically() { ... }
    
    @Test
    void getDiffableContentTruncatesLongValues() { ... }
    
    @Test
    void getDiffableContentEscapesNewlines() { ... }
    
    @Test
    void getDiffableContentHandlesNullValues() { ... }
    
    @Test
    void getDiffableContentHandlesEmptyValuesMap() { ... }
    
    @Test
    void getDiffableContentUsesEllipsisForTruncation() { ... }
}

@Nested
class ResultProjectionBuilderTest {
    
    @Test
    void padsWithAbsentForMissingLines() { ... }
    
    @Test
    void addsTruncationNoticeForExcessLines() { ... }
    
    @Test
    void usesCustomProviderWhenAvailable() { ... }
    
    @Test
    void fallsBackToDefaultWhenNoCustomProvider() { ... }
}

@Nested
class DiffableContentProviderTest {
    
    @Test
    void customProviderOutputIsUsed() { ... }
    
    @Test
    void customProviderCanReturnFewerLinesThanMax() { ... }
}
```

### 13.2 Integration Tests

```java
@Test
void exploreSpecsHaveConsistentLineCount() {
    // Run EXPLORE with 3 configs
    // Load all 3 spec files
    // Assert resultProjection sections have identical structure
}

@Test
void diffShowsMeaningfulDifferences() {
    // Generate two specs with known different values
    // Run diff programmatically
    // Assert diff output shows value differences, not structural changes
}

@Test
void customProviderIntegratesWithExperimentExtension() {
    // Use case class implements DiffableContentProvider
    // Run EXPLORE
    // Assert projections use custom format
}
```

## 14. References

- Implementation phases: See `PLAN-DIFF-FRIENDLY-EXPLORE.md`
- UseCase annotation: `src/main/java/org/javai/punit/api/UseCase.java`
- UseCaseResult: `src/main/java/org/javai/punit/experiment/model/UseCaseResult.java`
- BaselineWriter: `src/main/java/org/javai/punit/experiment/engine/BaselineWriter.java`
- Experiment extension: `src/main/java/org/javai/punit/experiment/engine/ExperimentExtension.java`
