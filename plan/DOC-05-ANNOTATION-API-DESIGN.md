# Annotation & API Design

This section defines the annotations and API classes introduced by the experiment extension.

## 4.1 New Annotations

### @UseCase

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UseCase {
    /** Unique identifier for this use case. */
    String value();
    
    /** Human-readable description. */
    String description() default "";
}
```

### @Experiment

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(ExperimentExtension.class)
public @interface Experiment {
    String useCase();
    int samples() default 100;
    long timeBudgetMs() default 0;
    long tokenBudget() default 0;
    String baselineOutputDir() default "punit/baselines";
    boolean overwriteBaseline() default false;
}
```

### @ExperimentContext

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ExperimentContexts.class)
public @interface ExperimentContext {
    String backend();
    String[] template() default {};
    @Deprecated String[] parameters() default {};
}
```

## 4.2 Extended @ProbabilisticTest

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(ProbabilisticTestExtension.class)
public @interface ProbabilisticTest {
    int samples() default 100;
    double minPassRate() default 1.0;
    long timeBudgetMs() default 0;
    long tokenBudget() default 0;
    
    /** Reference to an execution specification. */
    String spec() default "";
    
    /** The use case ID to execute. */
    String useCase() default "";
}
```

## 4.3 API Classes

### UseCaseResult

```java
public final class UseCaseResult {
    private final Map<String, Object> values;
    private final Instant timestamp;
    private final Duration executionTime;
    private final Map<String, Object> metadata;
    
    public static Builder builder() { ... }
    public <T> Optional<T> getValue(String key, Class<T> type) { ... }
    public boolean getBoolean(String key, boolean defaultValue) { ... }
    public int getInt(String key, int defaultValue) { ... }
    public double getDouble(String key, double defaultValue) { ... }
    public String getString(String key, String defaultValue) { ... }
    public Map<String, Object> getAllValues() { ... }
    public Map<String, Object> getAllMetadata() { ... }
}
```

### UseCaseContext

```java
public interface UseCaseContext {
    String getBackend();
    <T> Optional<T> getParameter(String key, Class<T> type);
    <T> T getParameter(String key, Class<T> type, T defaultValue);
    Map<String, Object> getAllParameters();
    
    default boolean hasBackend(String backend) {
        return backend.equals(getBackend());
    }
}
```

### SuccessCriteria

```java
public interface SuccessCriteria {
    boolean isSuccess(UseCaseResult result);
    String getDescription();
    
    static SuccessCriteria parse(String expression) { ... }
}
```

Expression syntax supports:
- `"isValid == true"`
- `"score >= 0.8"`
- `"isValid == true && errorCount == 0"`

---

*Previous: [Core Conceptual Artifacts](./DOC-04-CORE-CONCEPTUAL-ARTIFACTS.md)*

*Next: [Data Flow](./DOC-06-DATA-FLOW.md)*

*[Back to Table of Contents](./DOC-00-TOC.md)*
