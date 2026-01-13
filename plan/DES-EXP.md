# DES-EXP: Baseline Expiration — Detailed Design

This document provides the detailed design for implementing baseline expiration in PUnit, as specified in REQ-EXP.

---

## 1. Overview

Baseline expiration provides a simple mechanism to surface stale baselines, alerting operators when empirical data may no longer be representative of current system behavior.

### Design Goals

1. **Minimal API surface** — One new annotation attribute
2. **Opt-in by default** — No expiration unless explicitly configured
3. **Proportional warnings** — Pre-expiration alerts scale with validity period
4. **Non-blocking** — Warnings qualify verdicts; they don't fail tests
5. **Machine-readable** — Expiration data in JUnit report properties

---

## 2. API Design

### 2.1 Extended @Experiment Annotation

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(ExperimentExtension.class)
public @interface Experiment {

    // ... existing attributes ...

    /**
     * Number of days for which the baseline remains valid.
     *
     * <p>When set to a positive value, probabilistic tests using this baseline
     * will display warnings as the expiration date approaches, and prominent
     * warnings after expiration.
     *
     * <p><b>Default: 0 (no expiration)</b>
     *
     * <p>When set to 0, the baseline does not expire. During MEASURE execution,
     * PUnit emits a one-time informational note suggesting the experimenter
     * consider setting an expiration policy.
     *
     * <p>Typical values:
     * <ul>
     *   <li>7-14 days: Rapidly evolving systems (LLM APIs, A/B tests)</li>
     *   <li>30 days: Standard recommendation for most use cases</li>
     *   <li>90 days: Stable internal systems</li>
     *   <li>0: Algorithms with no expected drift</li>
     * </ul>
     *
     * @return validity period in days, or 0 for no expiration
     */
    int expiresInDays() default 0;
}
```

### 2.2 Usage Example

```java
@Experiment(
    mode = ExperimentMode.MEASURE,
    useCase = ShoppingUseCase.class,
    samples = 1000,
    expiresInDays = 30  // Baseline valid for 30 days
)
void measureProductSearchBaseline(ShoppingUseCase useCase, ResultCaptor captor) {
    captor.record(useCase.searchProducts("headphones", context));
}
```

---

## 3. Data Model

### 3.1 ExpirationPolicy

```java
package org.javai.punit.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Immutable expiration policy for a baseline.
 */
public record ExpirationPolicy(
    int expiresInDays,
    Instant baselineEndTime
) {
    /** Sentinel for no expiration */
    public static final int NO_EXPIRATION = 0;

    /**
     * Returns true if this baseline has an expiration policy.
     */
    public boolean hasExpiration() {
        return expiresInDays > NO_EXPIRATION;
    }

    /**
     * Computes the expiration instant.
     *
     * @return the expiration instant, or empty if no expiration
     */
    public Optional<Instant> expirationTime() {
        if (!hasExpiration()) {
            return Optional.empty();
        }
        return Optional.of(baselineEndTime.plus(Duration.ofDays(expiresInDays)));
    }

    /**
     * Evaluates the expiration status at the given time.
     */
    public ExpirationStatus evaluateAt(Instant currentTime) {
        if (!hasExpiration()) {
            return ExpirationStatus.noExpiration();
        }

        var expiration = expirationTime().orElseThrow();
        var remaining = Duration.between(currentTime, expiration);

        if (remaining.isNegative()) {
            return ExpirationStatus.expired(remaining.negated());
        }

        var totalDuration = Duration.ofDays(expiresInDays);
        var remainingPercent = (double) remaining.toMillis() / totalDuration.toMillis();

        if (remainingPercent <= 0.10) {
            return ExpirationStatus.expiringImminently(remaining, remainingPercent);
        } else if (remainingPercent <= 0.25) {
            return ExpirationStatus.expiringSoon(remaining, remainingPercent);
        }

        return ExpirationStatus.valid(remaining);
    }
}
```

### 3.2 ExpirationStatus

```java
package org.javai.punit.model;

import java.time.Duration;

/**
 * The expiration status of a baseline at a point in time.
 */
public sealed interface ExpirationStatus {

    /**
     * No expiration policy defined.
     */
    record NoExpiration() implements ExpirationStatus {}

    /**
     * Baseline is valid with time remaining.
     */
    record Valid(Duration remaining) implements ExpirationStatus {}

    /**
     * Baseline expires soon (≤25% remaining).
     */
    record ExpiringSoon(
        Duration remaining,
        double remainingPercent
    ) implements ExpirationStatus {}

    /**
     * Baseline expiring imminently (≤10% remaining).
     */
    record ExpiringImminently(
        Duration remaining,
        double remainingPercent
    ) implements ExpirationStatus {}

    /**
     * Baseline has expired.
     */
    record Expired(Duration expiredAgo) implements ExpirationStatus {}

    // Factory methods
    static ExpirationStatus noExpiration() {
        return new NoExpiration();
    }

    static ExpirationStatus valid(Duration remaining) {
        return new Valid(remaining);
    }

    static ExpirationStatus expiringSoon(Duration remaining, double percent) {
        return new ExpiringSoon(remaining, percent);
    }

    static ExpirationStatus expiringImminently(Duration remaining, double percent) {
        return new ExpiringImminently(remaining, percent);
    }

    static ExpirationStatus expired(Duration expiredAgo) {
        return new Expired(expiredAgo);
    }

    /**
     * Returns true if this status requires a warning.
     */
    default boolean requiresWarning() {
        return this instanceof ExpiringSoon 
            || this instanceof ExpiringImminently 
            || this instanceof Expired;
    }

    /**
     * Returns true if the baseline has expired.
     */
    default boolean isExpired() {
        return this instanceof Expired;
    }
}
```

---

## 4. Extended ExecutionSpecification

### 4.1 New Fields

```java
public final class ExecutionSpecification {
    // ... existing fields ...
    
    private final ExpirationPolicy expirationPolicy;
    
    // Computed from empiricalBasis.generatedAt() for backward compatibility,
    // but preferring explicit experimentEndTime when available
    
    public ExpirationPolicy getExpirationPolicy() {
        return expirationPolicy;
    }
    
    public ExpirationStatus evaluateExpiration(Instant currentTime) {
        if (expirationPolicy == null) {
            return ExpirationStatus.noExpiration();
        }
        return expirationPolicy.evaluateAt(currentTime);
    }
    
    // ... in Builder ...
    
    public Builder expirationPolicy(int expiresInDays, Instant baselineEndTime) {
        this.expirationPolicy = new ExpirationPolicy(expiresInDays, baselineEndTime);
        return this;
    }
}
```

### 4.2 YAML Schema Extension

```yaml
# punit-spec-3 schema
schema: punit-spec-3
useCaseId: shopping.product.search
version: 1

expiration:
  expiresInDays: 30
  baselineEndTime: "2026-01-10T14:45:00Z"
  expirationDate: "2026-02-09T14:45:00Z"  # Computed, for human convenience

empiricalBasis:
  samples: 1000
  successes: 973
  generatedAt: "2026-01-10T14:30:00Z"

# ... rest of spec ...
```

### 4.3 Backward Compatibility

For specs without expiration data:
- `expirationPolicy` is null
- `evaluateExpiration()` returns `NoExpiration`
- No warnings are generated

---

## 5. Experiment Extension Changes

### 5.1 Capturing Expiration Policy

```java
public class ExperimentExtension implements TestTemplateInvocationContextProvider {

    // In MEASURE mode completion:
    
    private void generateSpec(ExperimentContext context, AggregatedResults results) {
        var annotation = context.getExperimentAnnotation();
        var expiresInDays = annotation.expiresInDays();
        
        // Emit one-time note if no expiration set
        if (expiresInDays == ExpirationPolicy.NO_EXPIRATION) {
            context.getReporter().publishEntry(
                "punit.info.expiration",
                "Consider setting expiresInDays to track baseline freshness"
            );
        }
        
        var spec = ExecutionSpecification.builder()
            .useCaseId(context.getUseCaseId())
            // ... other fields ...
            .expirationPolicy(expiresInDays, results.getEndTime())
            .build();
        
        specWriter.write(spec);
    }
}
```

### 5.2 Timestamp Capture

The experiment execution must track:

```java
public class ExperimentResults {
    private final Instant startTime;
    private Instant endTime;
    
    public void recordSampleCompletion(Instant timestamp) {
        this.endTime = timestamp;  // Last sample completion = end time
    }
    
    public Instant getEndTime() {
        return endTime != null ? endTime : Instant.now();
    }
}
```

---

## 6. Probabilistic Test Extension Changes

### 6.1 Expiration Evaluation

```java
public class ProbabilisticTestExtension implements TestTemplateInvocationContextProvider {

    private void evaluateAndReport(
            ExecutionSpecification spec,
            TestExecutionResult result,
            TestReporter reporter) {
        
        var expirationStatus = spec.evaluateExpiration(Instant.now());
        
        // Add expiration warning to report if needed
        if (expirationStatus.requiresWarning()) {
            var warning = renderExpirationWarning(spec, expirationStatus);
            result.addWarning(warning);
        }
        
        // Publish machine-readable properties
        publishExpirationProperties(reporter, spec, expirationStatus);
    }
}
```

### 6.2 Warning Rendering

```java
public class ExpirationWarningRenderer {

    public String render(ExecutionSpecification spec, ExpirationStatus status) {
        return switch (status) {
            case ExpirationStatus.Expired(var expiredAgo) -> renderExpired(spec, expiredAgo);
            case ExpirationStatus.ExpiringImminently(var remaining, var percent) -> 
                renderImminentExpiration(spec, remaining);
            case ExpirationStatus.ExpiringSoon(var remaining, var percent) -> 
                renderExpiringSoon(spec, remaining);
            default -> "";
        };
    }

    private String renderExpired(ExecutionSpecification spec, Duration expiredAgo) {
        var policy = spec.getExpirationPolicy();
        return String.format("""
            ════════════════════════════════════════════════════════════
            ⚠️  BASELINE EXPIRED
            ════════════════════════════════════════════════════════════
            
            The baseline used for statistical inference has expired.
            
              Baseline created:   %s
              Validity period:    %d days
              Expiration date:    %s
              Expired:            %s ago
            
            Statistical inference is based on potentially stale empirical data.
            Consider running a fresh MEASURE experiment to update the baseline.
            
            ════════════════════════════════════════════════════════════
            """,
            formatInstant(policy.baselineEndTime()),
            policy.expiresInDays(),
            formatInstant(policy.expirationTime().orElseThrow()),
            formatDuration(expiredAgo)
        );
    }

    private String renderImminentExpiration(ExecutionSpecification spec, Duration remaining) {
        var policy = spec.getExpirationPolicy();
        return String.format("""
            ⚠️  BASELINE EXPIRING IMMINENTLY
            
            Baseline expires in %s (on %s).
            Schedule a MEASURE experiment to refresh the baseline.
            """,
            formatDuration(remaining),
            formatInstant(policy.expirationTime().orElseThrow())
        );
    }

    private String renderExpiringSoon(ExecutionSpecification spec, Duration remaining) {
        var policy = spec.getExpirationPolicy();
        return String.format("""
            ℹ️  Baseline expires soon
            
            Baseline expires in %s (on %s).
            """,
            formatDuration(remaining),
            formatInstant(policy.expirationTime().orElseThrow())
        );
    }

    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        if (days > 0) {
            return days + " day" + (days == 1 ? "" : "s");
        }
        long hours = duration.toHours();
        if (hours > 0) {
            return hours + " hour" + (hours == 1 ? "" : "s");
        }
        return duration.toMinutes() + " minutes";
    }

    private String formatInstant(Instant instant) {
        return instant.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z"));
    }
}
```

---

## 7. Report Integration

### 7.1 JUnit Report Properties

```java
public class ExpirationReportPublisher {

    public void publish(TestReporter reporter, ExecutionSpecification spec, ExpirationStatus status) {
        var policy = spec.getExpirationPolicy();
        
        if (policy != null && policy.hasExpiration()) {
            reporter.publishEntry("punit.baseline.expiresInDays", 
                String.valueOf(policy.expiresInDays()));
            reporter.publishEntry("punit.baseline.endTime", 
                policy.baselineEndTime().toString());
            reporter.publishEntry("punit.baseline.expirationDate",
                policy.expirationTime().map(Instant::toString).orElse("none"));
        }
        
        reporter.publishEntry("punit.baseline.expirationStatus", 
            getStatusName(status));
        
        if (status instanceof ExpirationStatus.Expired expired) {
            reporter.publishEntry("punit.baseline.expiredAgoDays",
                String.valueOf(expired.expiredAgo().toDays()));
        }
    }

    private String getStatusName(ExpirationStatus status) {
        return switch (status) {
            case ExpirationStatus.NoExpiration() -> "NO_EXPIRATION";
            case ExpirationStatus.Valid v -> "VALID";
            case ExpirationStatus.ExpiringSoon s -> "EXPIRING_SOON";
            case ExpirationStatus.ExpiringImminently i -> "EXPIRING_IMMINENTLY";
            case ExpirationStatus.Expired e -> "EXPIRED";
        };
    }
}
```

### 7.2 Console Output

Expiration warnings are rendered:
- **Always** for expired baselines (regardless of verbosity)
- At normal verbosity for imminent expiration
- At verbose level for "expiring soon"

```java
public enum WarningLevel {
    ALWAYS,      // Expired - always shown
    NORMAL,      // Imminent - shown at normal and verbose
    VERBOSE      // Soon - only at verbose
}

public WarningLevel getWarningLevel(ExpirationStatus status) {
    return switch (status) {
        case ExpirationStatus.Expired e -> WarningLevel.ALWAYS;
        case ExpirationStatus.ExpiringImminently i -> WarningLevel.NORMAL;
        case ExpirationStatus.ExpiringSoon s -> WarningLevel.VERBOSE;
        default -> null;
    };
}
```

---

## 8. Interaction with REQ-COV (Covariates)

### 8.1 Independent Evaluation

Expiration and covariate conformance are evaluated independently:

```java
public class BaselineValidityEvaluator {

    public ValidityReport evaluate(
            ExecutionSpecification spec,
            CovariateProfile testProfile,
            Instant currentTime) {
        
        // Evaluate expiration
        var expirationStatus = spec.evaluateExpiration(currentTime);
        
        // Evaluate covariate conformance
        var covariateConformance = covariateEvaluator.evaluate(
            spec.getCovariateProfile(), testProfile);
        
        return new ValidityReport(expirationStatus, covariateConformance);
    }
}

public record ValidityReport(
    ExpirationStatus expirationStatus,
    CovariateConformanceReport covariateConformance
) {
    public boolean hasAnyWarnings() {
        return expirationStatus.requiresWarning() 
            || covariateConformance.hasNonConformance();
    }
    
    public String renderWarnings() {
        var sb = new StringBuilder();
        
        if (expirationStatus.requiresWarning()) {
            sb.append(expirationRenderer.render(expirationStatus));
        }
        
        if (covariateConformance.hasNonConformance()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(covariateRenderer.render(covariateConformance));
        }
        
        return sb.toString();
    }
}
```

### 8.2 Combined Warning Output

When both conditions apply:

```
════════════════════════════════════════════════════════════
⚠️  BASELINE EXPIRED
════════════════════════════════════════════════════════════

The baseline used for statistical inference has expired.

  Baseline created:   2025-12-10 14:45 GMT
  Validity period:    30 days
  Expiration date:    2026-01-09 14:45 GMT
  Expired:            4 days ago

Statistical inference is based on potentially stale empirical data.
Consider running a fresh MEASURE experiment to update the baseline.

════════════════════════════════════════════════════════════

⚠️ COVARIATE NON-CONFORMANCE
Statistical inference may be less reliable.

  • weekday_vs_weekend: baseline=Mo-Fr, test=Sa-So
  • region: baseline=EU, test=US

════════════════════════════════════════════════════════════
```

---

## 9. Spec Loading and Serialization

### 9.1 YAML Serialization

```java
public class SpecificationSerializer {

    public void writeExpiration(Map<String, Object> yaml, ExpirationPolicy policy) {
        if (policy == null || !policy.hasExpiration()) {
            return;
        }
        
        var expiration = new LinkedHashMap<String, Object>();
        expiration.put("expiresInDays", policy.expiresInDays());
        expiration.put("baselineEndTime", policy.baselineEndTime().toString());
        policy.expirationTime().ifPresent(exp -> 
            expiration.put("expirationDate", exp.toString()));
        
        yaml.put("expiration", expiration);
    }
}
```

### 9.2 YAML Deserialization

```java
public class SpecificationLoader {

    private ExpirationPolicy loadExpiration(Map<String, Object> yaml) {
        var expiration = (Map<String, Object>) yaml.get("expiration");
        if (expiration == null) {
            return null;
        }
        
        var expiresInDays = ((Number) expiration.get("expiresInDays")).intValue();
        var baselineEndTime = Instant.parse((String) expiration.get("baselineEndTime"));
        
        return new ExpirationPolicy(expiresInDays, baselineEndTime);
    }
}
```

---

## 10. Error Handling

### 10.1 Invalid Expiration Value

```java
public class ExperimentAnnotationValidator {

    public void validate(Experiment annotation) {
        if (annotation.expiresInDays() < 0) {
            throw new PunitConfigurationException(
                "expiresInDays must be non-negative, got: " + annotation.expiresInDays());
        }
    }
}
```

### 10.2 Missing Timestamp

If `baselineEndTime` is missing from an old spec:

```java
public ExpirationPolicy loadExpiration(Map<String, Object> yaml, EmpiricalBasis basis) {
    var expiration = (Map<String, Object>) yaml.get("expiration");
    if (expiration == null) {
        return null;
    }
    
    var expiresInDays = ((Number) expiration.get("expiresInDays")).intValue();
    
    // Fallback to generatedAt if baselineEndTime not present
    Instant baselineEndTime;
    if (expiration.containsKey("baselineEndTime")) {
        baselineEndTime = Instant.parse((String) expiration.get("baselineEndTime"));
    } else if (basis != null && basis.generatedAt() != null) {
        baselineEndTime = basis.generatedAt();
    } else {
        // Cannot compute expiration without a timestamp
        return null;
    }
    
    return new ExpirationPolicy(expiresInDays, baselineEndTime);
}
```

---

## 11. Migration Path

### 11.1 Backward Compatibility

- Existing specs without expiration data continue to work
- No expiration warnings for legacy specs
- New specs with `expiresInDays = 0` behave identically

### 11.2 Adding Expiration to Existing Use Cases

1. Add `expiresInDays` to `@Experiment` annotation
2. Re-run MEASURE experiment
3. New spec includes expiration policy
4. Probabilistic tests automatically use new policy

---

## 12. Testing Strategy

### 12.1 Unit Tests

```java
class ExpirationPolicyTest {
    
    @Test
    void noExpiration_returnsNoExpirationStatus() {
        var policy = new ExpirationPolicy(0, Instant.now());
        assertThat(policy.hasExpiration()).isFalse();
        assertThat(policy.evaluateAt(Instant.now()))
            .isInstanceOf(ExpirationStatus.NoExpiration.class);
    }
    
    @Test
    void expired_returnsExpiredStatus() {
        var endTime = Instant.now().minus(Duration.ofDays(35));
        var policy = new ExpirationPolicy(30, endTime);
        
        var status = policy.evaluateAt(Instant.now());
        
        assertThat(status).isInstanceOf(ExpirationStatus.Expired.class);
        var expired = (ExpirationStatus.Expired) status;
        assertThat(expired.expiredAgo().toDays()).isEqualTo(5);
    }
    
    @Test
    void expiringSoon_at25PercentRemaining() {
        var endTime = Instant.now().minus(Duration.ofDays(22)); // 8 days left of 30
        var policy = new ExpirationPolicy(30, endTime);
        
        var status = policy.evaluateAt(Instant.now());
        
        assertThat(status).isInstanceOf(ExpirationStatus.ExpiringSoon.class);
    }
    
    @Test
    void expiringImminently_at10PercentRemaining() {
        var endTime = Instant.now().minus(Duration.ofDays(28)); // 2 days left of 30
        var policy = new ExpirationPolicy(30, endTime);
        
        var status = policy.evaluateAt(Instant.now());
        
        assertThat(status).isInstanceOf(ExpirationStatus.ExpiringImminently.class);
    }
}
```

### 12.2 Integration Tests

```java
class ExpirationIntegrationTest {

    @Test
    void measureExperiment_capturesExpirationPolicy() {
        // Run MEASURE experiment with expiresInDays = 30
        // Verify spec file contains expiration section
    }
    
    @Test
    void probabilisticTest_showsExpiredWarning() {
        // Given a spec with expired baseline
        // When test runs
        // Then warning is shown regardless of verbosity
    }
    
    @Test
    void probabilisticTest_showsExpirationAndCovariateWarnings() {
        // Given expired baseline with covariate non-conformance
        // When test runs
        // Then both warnings are shown
    }
}
```

---

## 13. Summary

The expiration design provides:

| Aspect | Implementation |
|--------|----------------|
| Declaration | `@Experiment(expiresInDays = 30)` |
| Storage | `expiration` section in spec YAML |
| Evaluation | `ExpirationPolicy.evaluateAt(Instant)` |
| Proportional warnings | ≤25% → soon, ≤10% → imminent, <0 → expired |
| Reporting | Console warnings + JUnit report properties |
| COV integration | Independent evaluation, combined rendering |
| Default | No expiration (opt-in) |

