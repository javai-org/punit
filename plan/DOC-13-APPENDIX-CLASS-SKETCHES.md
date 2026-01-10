# Appendix: Sketch of Key Class Implementations

The following sketches illustrate the intended structure of key classes. These are **not** executable code but serve as design guidance.

## 14.1 UseCaseResult

```java
public final class UseCaseResult {
    
    private final Map<String, Object> values;
    private final Map<String, Object> metadata;
    private final Instant timestamp;
    private final Duration executionTime;
    
    private UseCaseResult(Builder builder) { ... }
    
    public static Builder builder() { return new Builder(); }
    
    public <T> Optional<T> getValue(String key, Class<T> type) { ... }
    public <T> T getValue(String key, Class<T> type, T defaultValue) { ... }
    
    public boolean getBoolean(String key, boolean defaultValue) { ... }
    public int getInt(String key, int defaultValue) { ... }
    public double getDouble(String key, double defaultValue) { ... }
    public String getString(String key, String defaultValue) { ... }
    
    public Map<String, Object> getAllValues() { return values; }
    public Map<String, Object> getAllMetadata() { return metadata; }
    public Instant getTimestamp() { return timestamp; }
    public Duration getExecutionTime() { return executionTime; }
    
    public static final class Builder {
        public Builder value(String key, Object val) { ... }
        public Builder meta(String key, Object val) { ... }
        public Builder executionTime(Duration duration) { ... }
        public UseCaseResult build() { ... }
    }
}
```

## 14.2 ExperimentExtension

```java
public class ExperimentExtension 
        implements TestTemplateInvocationContextProvider, 
                   InvocationInterceptor {
    
    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod()
                .map(m -> m.isAnnotationPresent(Experiment.class))
                .orElse(false);
    }
    
    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
            ExtensionContext context) {
        // Resolve use case, build context, create aggregator
        // Generate sample stream
    }
    
    @Override
    public void interceptTestTemplateMethod(...) {
        // Execute use case, record result
        // Check budgets, generate baseline on completion
    }
}
```

## 14.3 ExecutionSpecification

```java
public final class ExecutionSpecification {
    
    private final String useCaseId;
    private final int version;
    
    // Approval metadata
    private final Instant approvedAt;
    private final String approvedBy;
    private final String approvalNotes;
    
    // Raw baseline data (for statistical derivation at test time)
    private final BaselineData baseline;
    
    // Configuration used during experiment
    private final Map<String, Object> configuration;
    
    // Success criteria expression
    private final String successCriteria;
    
    public boolean isApproved() { return approvedAt != null && approvedBy != null; }
    
    // Raw data accessors - thresholds computed at test time, not stored here
    public int getBaselineSamples() { return baseline.samples(); }
    public int getBaselineSuccesses() { return baseline.successes(); }
    public double getObservedRate() { return baseline.observedRate(); }
    
    public SuccessCriteria getSuccessCriteria() { ... }
    
    public static ExecutionSpecification loadFrom(Path path) { ... }
    public void validate() throws SpecificationValidationException { ... }
    
    public record BaselineData(
        Instant generatedAt,
        int samples,
        int successes,
        int failures,
        double observedRate
    ) {}
}
```

## 14.4 SpecificationRegistry

```java
public class SpecificationRegistry {
    
    private final Path specsRoot;
    private final Map<String, ExecutionSpecification> cache;
    
    public ExecutionSpecification resolve(String specId) {
        return cache.computeIfAbsent(specId, this::loadSpec);
    }
    
    private ExecutionSpecification loadSpec(String specId) {
        // Parse specId, find file (YAML preferred, JSON fallback)
        // Load and validate
    }
}
```

## 14.5 SuccessCriteria

```java
public interface SuccessCriteria {
    
    boolean isSuccess(UseCaseResult result);
    String getDescription();
    
    static SuccessCriteria parse(String expression) {
        return new ExpressionSuccessCriteria(expression);
    }
}
```

---

*Previous: [Glossary](../docs/GLOSSARY.md)*

*Next: [Conclusion](./DOC-14-CONCLUSION.md)*

*[Back to Table of Contents](./DOC-00-TOC.md)*
