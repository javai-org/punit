# DES-COV: Covariate Support — Detailed Design

This document provides the detailed design for implementing covariate support in PUnit, as specified in REQ-COV.

---

## 1. Overview

Covariates enable PUnit to track contextual factors that may influence use case performance, ensuring that probabilistic tests compare against statistically appropriate baselines.

### Design Goals

1. **Minimal API surface** — Declare covariates via existing annotations
2. **Type-safe standard covariates** — Enum-based declaration for common cases
3. **Extensible custom covariates** — String keys for user-defined factors
4. **Deterministic matching** — Reproducible baseline selection
5. **Transparent reporting** — Clear warnings for non-conformance

---

## 2. API Design

### 2.1 Standard Covariate Enum

```java
package org.javai.punit.api;

/**
 * Standard covariates provided by PUnit for common contextual factors.
 *
 * <p>These covariates have built-in resolution and matching strategies.
 */
public enum StandardCovariate {
    
    /**
     * Weekday vs weekend classification.
     * 
     * <p>Resolution: Current date → "Mo-Fr" or "Sa-So"
     * <p>Matching: Current day falls within baseline's range
     */
    WEEKDAY_VERSUS_WEEKEND("weekday_vs_weekend"),
    
    /**
     * Time of day window.
     *
     * <p>Resolution: Experiment execution interval (start to end time with timezone)
     * <p>Matching: Current time falls within baseline's recorded interval
     */
    TIME_OF_DAY("time_of_day"),
    
    /**
     * System timezone.
     *
     * <p>Resolution: System default timezone
     * <p>Matching: Exact string match
     */
    TIMEZONE("timezone"),

	/**
	 * Region.
	 *
	 * <p>Resolution: System property or environment variable
	 * <p>Matching: Case-insensitive string match
	 */
	REGION("region");

	private final String key;
    
    StandardCovariate(String key) {
        this.key = key;
    }
    
    /**
     * Returns the stable string key used in baseline specs.
     */
    public String key() {
        return key;
    }
}
```

### 2.2 Extended @UseCase Annotation

```java
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UseCase {

    // ... existing attributes ...

    /**
     * Standard covariates that may influence this use case's performance.
     *
     * <p>Declared covariates:
     * <ul>
     *   <li>Are captured during MEASURE experiments</li>
     *   <li>Contribute to the invocation footprint (by name)</li>
     *   <li>Participate in baseline selection and conformance checking</li>
     * </ul>
     *
     * <p>Order matters: earlier covariates are prioritized during matching.
     *
     * @return array of standard covariates
     */
    StandardCovariate[] covariates() default {};

    /**
     * Custom covariate keys for user-defined contextual factors.
     *
     * <p>Custom covariates are resolved from:
     * <ol>
     *   <li>System property: {@code -D{key}=value}</li>
     *   <li>Environment variable: {@code KEY=value}</li>
     *   <li>PUnit environment map (programmatic)</li>
     * </ol>
     *
     * <p>If a custom covariate is not found in the environment, its value
     * is recorded as "not_set". Values of "not_set" never match, even if
     * both baseline and test have "not_set".
     *
     * @return array of custom covariate keys
     */
    String[] customCovariates() default {};
}
```

### 2.3 Usage Example

```java
@UseCase(
    value = "shopping.product.search",
    covariates = { 
        StandardCovariate.WEEKDAY_VERSUS_WEEKEND,
        StandardCovariate.TIME_OF_DAY 
    },
    customCovariates = { "hosting_environment", "feature_flag_new_ranking" }
)
public class ShoppingUseCase {
    // ...
}
```

---

## 3. Data Model

### 3.1 CovariateProfile

```java
package org.javai.punit.model;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable record of covariate values captured during baseline creation
 * or resolved at test execution time.
 */
public final class CovariateProfile {

    /** Sentinel value for unresolved custom covariates */
    public static final String NOT_SET = "not_set";

    private final Map<String, CovariateValue> values;
    private final List<String> orderedKeys;

    private CovariateProfile(Map<String, CovariateValue> values, List<String> orderedKeys) {
        this.values = Map.copyOf(values);
        this.orderedKeys = List.copyOf(orderedKeys);
    }

    /**
     * Returns covariate keys in declaration order.
     */
    public List<String> orderedKeys() {
        return orderedKeys;
    }

    /**
     * Returns the value for the given covariate key.
     */
    public CovariateValue get(String key) {
        return values.get(key);
    }

    /**
     * Returns all covariate values as an unmodifiable map.
     */
    public Map<String, CovariateValue> asMap() {
        return values;
    }

    /**
     * Computes a stable hash of this profile for use in filenames.
     */
    public String computeHash() {
        // Implementation: SHA-256 of canonical string representation, truncated
    }

    /**
     * Computes individual hashes for each covariate value, in declaration order.
     */
    public List<String> computeValueHashes() {
        // Implementation: One hash per covariate value
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<String, CovariateValue> values = new LinkedHashMap<>();
        private final List<String> orderedKeys = new java.util.ArrayList<>();

        public Builder put(String key, CovariateValue value) {
            if (!orderedKeys.contains(key)) {
                orderedKeys.add(key);
            }
            values.put(key, value);
            return this;
        }

        public CovariateProfile build() {
            return new CovariateProfile(values, orderedKeys);
        }
    }
}
```

### 3.2 CovariateValue

```java
package org.javai.punit.model;

import java.time.LocalTime;
import java.time.ZoneId;

/**
 * A covariate value, which may be atomic or structured.
 */
public sealed interface CovariateValue {

    /**
     * Returns the canonical string representation for storage and hashing.
     */
    String toCanonicalString();

    /**
     * Simple string value (e.g., "EU", "Mo-Fr").
     */
    record StringValue(String value) implements CovariateValue {
        @Override
        public String toCanonicalString() {
            return value;
        }
    }

    /**
     * Time window value with timezone.
     */
    record TimeWindowValue(
        LocalTime start,
        LocalTime end,
        ZoneId timezone
    ) implements CovariateValue {
        @Override
        public String toCanonicalString() {
            return String.format("%s-%s %s", start, end, timezone);
        }
    }
}
```

### 3.3 CovariateDeclaration

```java
package org.javai.punit.model;

import org.javai.punit.api.StandardCovariate;
import java.util.List;

/**
 * The set of covariates declared by a use case.
 * Used for footprint computation and resolution.
 */
public record CovariateDeclaration(
    List<StandardCovariate> standardCovariates,
    List<String> customCovariates
) {
    /**
     * Returns all covariate keys in declaration order.
     */
    public List<String> allKeys() {
        var keys = new java.util.ArrayList<String>();
        standardCovariates.forEach(sc -> keys.add(sc.key()));
        keys.addAll(customCovariates);
        return keys;
    }

    /**
     * Computes a stable hash of the covariate declaration (names only).
     * This hash contributes to the invocation footprint.
     */
    public String computeDeclarationHash() {
        // Implementation: SHA-256 of sorted covariate names
    }

    /**
     * Returns true if no covariates are declared.
     */
    public boolean isEmpty() {
        return standardCovariates.isEmpty() && customCovariates.isEmpty();
    }
}
```

---

## 4. Invocation Footprint

### 4.1 FootprintComputer

```java
package org.javai.punit.engine;

import org.javai.punit.model.CovariateDeclaration;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Computes the invocation footprint for baseline matching.
 *
 * <p>The footprint uniquely identifies the combination of:
 * <ol>
 *   <li>Use case identity</li>
 *   <li>Functional parameters (factors)</li>
 *   <li>Covariate declaration (names, not values)</li>
 * </ol>
 */
public class FootprintComputer {

    /**
     * Computes the invocation footprint.
     *
     * @param useCaseId the use case identifier
     * @param factors the functional parameters (may be empty)
     * @param covariateDeclaration the declared covariates
     * @return a stable hash representing the footprint
     */
    public String computeFootprint(
            String useCaseId,
            Map<String, Object> factors,
            CovariateDeclaration covariateDeclaration) {
        
        var sb = new StringBuilder();
        sb.append("usecase:").append(useCaseId).append("\n");
        
        // Factors in sorted order for stability
        factors.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> sb.append("factor:")
                .append(e.getKey()).append("=").append(e.getValue()).append("\n"));
        
        // Covariate names in declaration order
        covariateDeclaration.allKeys().forEach(key -> 
            sb.append("covariate:").append(key).append("\n"));
        
        return hash(sb.toString());
    }

    private String hash(String input) {
        // SHA-256, truncated to 8 hex chars for readability
    }
}
```

---

## 5. Covariate Resolution

### 5.1 CovariateResolver Interface

```java
package org.javai.punit.engine;

import org.javai.punit.model.CovariateValue;

/**
 * Strategy for resolving a covariate's value from the environment.
 */
public interface CovariateResolver {

    /**
     * Resolves the covariate value for the current execution context.
     *
     * @param context the resolution context (provides environment access, timestamps)
     * @return the resolved value
     */
    CovariateValue resolve(CovariateResolutionContext context);
}
```

### 5.2 CovariateResolutionContext

```java
package org.javai.punit.engine;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

/**
 * Context for covariate resolution, providing access to environment and timing.
 */
public interface CovariateResolutionContext {

    /**
     * Returns the current instant (for time-based covariates).
     */
    Instant now();

    /**
     * Returns the experiment start time (for TIME_OF_DAY resolution).
     */
    Optional<Instant> experimentStartTime();

    /**
     * Returns the experiment end time (for TIME_OF_DAY resolution).
     */
    Optional<Instant> experimentEndTime();

    /**
     * Returns the system timezone.
     */
    ZoneId systemTimezone();

    /**
     * Returns a system property value.
     */
    Optional<String> getSystemProperty(String key);

    /**
     * Returns an environment variable value.
     */
    Optional<String> getEnvironmentVariable(String key);

    /**
     * Returns a value from the PUnit environment map.
     */
    Optional<String> getPunitEnvironment(String key);
}
```

### 5.3 Standard Covariate Resolvers

```java
package org.javai.punit.engine.covariate;

/**
 * Resolver for WEEKDAY_VERSUS_WEEKEND.
 */
public class WeekdayVsWeekendResolver implements CovariateResolver {

    @Override
    public CovariateValue resolve(CovariateResolutionContext context) {
        var dayOfWeek = context.now()
            .atZone(context.systemTimezone())
            .getDayOfWeek();
        
        String value = switch (dayOfWeek) {
            case SATURDAY, SUNDAY -> "Sa-So";
            default -> "Mo-Fr";
        };
        
        return new CovariateValue.StringValue(value);
    }
}

/**
 * Resolver for TIME_OF_DAY (experiment interval).
 */
public class TimeOfDayResolver implements CovariateResolver {

    @Override
    public CovariateValue resolve(CovariateResolutionContext context) {
        var start = context.experimentStartTime()
            .orElseThrow(() -> new IllegalStateException(
                "TIME_OF_DAY requires experiment timing context"));
        var end = context.experimentEndTime()
            .orElseThrow(() -> new IllegalStateException(
                "TIME_OF_DAY requires experiment timing context"));
        
        var zone = context.systemTimezone();
        var startTime = start.atZone(zone).toLocalTime();
        var endTime = end.atZone(zone).toLocalTime();
        
        return new CovariateValue.TimeWindowValue(startTime, endTime, zone);
    }
}

/**
 * Resolver for custom (user-defined) covariates.
 */
public class CustomCovariateResolver implements CovariateResolver {

    private final String key;

    public CustomCovariateResolver(String key) {
        this.key = key;
    }

    @Override
    public CovariateValue resolve(CovariateResolutionContext context) {
        // Try sources in order: system property, env var, punit env
        var value = context.getSystemProperty(key)
            .or(() -> context.getEnvironmentVariable(key.toUpperCase()))
            .or(() -> context.getPunitEnvironment(key))
            .orElse(CovariateProfile.NOT_SET);
        
        return new CovariateValue.StringValue(value);
    }
}
```

---

## 6. Covariate Matching

### 6.1 CovariateMatcher Interface

```java
package org.javai.punit.engine;

import org.javai.punit.model.CovariateValue;

/**
 * Strategy for matching covariate values between baseline and test.
 */
public interface CovariateMatcher {

    /**
     * Determines whether the test value matches the baseline value.
     *
     * @param baselineValue the value recorded in the baseline
     * @param testValue the value resolved at test time
     * @return the match result
     */
    MatchResult match(CovariateValue baselineValue, CovariateValue testValue);

    /**
     * Result of a covariate match.
     */
    enum MatchResult {
        /** Perfect match */
        CONFORMS,
        /** Partial match (applicable for ranges) */
        PARTIALLY_CONFORMS,
        /** No match */
        DOES_NOT_CONFORM
    }
}
```

### 6.2 Standard Covariate Matchers

```java
package org.javai.punit.engine.covariate;

/**
 * Matcher for WEEKDAY_VERSUS_WEEKEND.
 */
public class WeekdayVsWeekendMatcher implements CovariateMatcher {

    @Override
    public MatchResult match(CovariateValue baselineValue, CovariateValue testValue) {
        if (!(baselineValue instanceof CovariateValue.StringValue baseline) ||
            !(testValue instanceof CovariateValue.StringValue test)) {
            return MatchResult.DOES_NOT_CONFORM;
        }
        
        return baseline.value().equals(test.value()) 
            ? MatchResult.CONFORMS 
            : MatchResult.DOES_NOT_CONFORM;
    }
}

/**
 * Matcher for TIME_OF_DAY.
 */
public class TimeOfDayMatcher implements CovariateMatcher {

    @Override
    public MatchResult match(CovariateValue baselineValue, CovariateValue testValue) {
        if (!(baselineValue instanceof CovariateValue.TimeWindowValue baseline)) {
            return MatchResult.DOES_NOT_CONFORM;
        }
        
        // For test time, we need the current time as a point, not a window
        LocalTime testTime;
        if (testValue instanceof CovariateValue.TimeWindowValue tw) {
            testTime = tw.start(); // Use start time for point-in-time check
        } else if (testValue instanceof CovariateValue.StringValue sv) {
            testTime = LocalTime.parse(sv.value().split(" ")[0].split("-")[0]);
        } else {
            return MatchResult.DOES_NOT_CONFORM;
        }
        
        // Check if test time falls within baseline window
        if (!testTime.isBefore(baseline.start()) && !testTime.isAfter(baseline.end())) {
            return MatchResult.CONFORMS;
        }
        
        return MatchResult.DOES_NOT_CONFORM;
    }
}

/**
 * Matcher for exact string match (custom covariates, REGION, TIMEZONE).
 */
public class ExactStringMatcher implements CovariateMatcher {

    @Override
    public MatchResult match(CovariateValue baselineValue, CovariateValue testValue) {
        // "not_set" never matches, even with itself
        if (CovariateProfile.NOT_SET.equals(baselineValue.toCanonicalString()) ||
            CovariateProfile.NOT_SET.equals(testValue.toCanonicalString())) {
            return MatchResult.DOES_NOT_CONFORM;
        }
        
        return baselineValue.toCanonicalString().equals(testValue.toCanonicalString())
            ? MatchResult.CONFORMS
            : MatchResult.DOES_NOT_CONFORM;
    }
}
```

---

## 7. Baseline Selection

### 7.1 BaselineSelector

```java
package org.javai.punit.engine;

import org.javai.punit.model.CovariateProfile;
import org.javai.punit.spec.model.ExecutionSpecification;
import java.util.List;
import java.util.Optional;

/**
 * Selects the best-matching baseline for a probabilistic test.
 */
public class BaselineSelector {

    private final CovariateMatcherRegistry matcherRegistry;

    /**
     * Selects the best baseline from candidates.
     *
     * @param candidates baselines with matching footprint
     * @param testProfile the test's current covariate profile
     * @return selection result including the chosen baseline and conformance info
     */
    public SelectionResult select(
            List<BaselineCandidate> candidates,
            CovariateProfile testProfile) {
        
        if (candidates.isEmpty()) {
            return SelectionResult.noMatch();
        }

        // Score each candidate
        var scored = candidates.stream()
            .map(c -> new ScoredCandidate(c, score(c.covariateProfile(), testProfile)))
            .sorted(this::compareScores)
            .toList();

        var best = scored.get(0);
        var ambiguous = scored.size() > 1 && 
            compareScores(scored.get(0), scored.get(1)) == 0;

        return new SelectionResult(
            best.candidate(),
            best.score().conformanceDetails(),
            ambiguous,
            scored.size()
        );
    }

    private CovariateScore score(CovariateProfile baseline, CovariateProfile test) {
        var details = new java.util.ArrayList<ConformanceDetail>();
        int matchCount = 0;
        
        for (String key : baseline.orderedKeys()) {
            var baselineValue = baseline.get(key);
            var testValue = test.get(key);
            var matcher = matcherRegistry.getMatcher(key);
            var result = matcher.match(baselineValue, testValue);
            
            details.add(new ConformanceDetail(key, baselineValue, testValue, result));
            if (result == CovariateMatcher.MatchResult.CONFORMS) {
                matchCount++;
            }
        }
        
        return new CovariateScore(matchCount, details);
    }

    private int compareScores(ScoredCandidate a, ScoredCandidate b) {
        // Primary: more matches is better
        int matchDiff = Integer.compare(b.score().matchCount(), a.score().matchCount());
        if (matchDiff != 0) return matchDiff;
        
        // Secondary: prioritize earlier covariates (left-to-right)
        for (int i = 0; i < a.score().conformanceDetails().size(); i++) {
            var aDetail = a.score().conformanceDetails().get(i);
            var bDetail = b.score().conformanceDetails().get(i);
            int cmp = compareMatchResult(bDetail.result(), aDetail.result());
            if (cmp != 0) return cmp;
        }
        
        // Tertiary: recency (more recent baseline preferred)
        return b.candidate().generatedAt().compareTo(a.candidate().generatedAt());
    }

    private int compareMatchResult(CovariateMatcher.MatchResult a, CovariateMatcher.MatchResult b) {
        return Integer.compare(a.ordinal(), b.ordinal());
    }
}
```

### 7.2 Selection Result Types

```java
package org.javai.punit.engine;

public record SelectionResult(
    BaselineCandidate selected,
    List<ConformanceDetail> conformanceDetails,
    boolean ambiguous,
    int candidateCount
) {
    public static SelectionResult noMatch() {
        return new SelectionResult(null, List.of(), false, 0);
    }
    
    public boolean hasSelection() {
        return selected != null;
    }
    
    public boolean hasNonConformance() {
        return conformanceDetails.stream()
            .anyMatch(d -> d.result() != CovariateMatcher.MatchResult.CONFORMS);
    }
}

public record ConformanceDetail(
    String covariateKey,
    CovariateValue baselineValue,
    CovariateValue testValue,
    CovariateMatcher.MatchResult result
) {}

public record BaselineCandidate(
    String filename,
    String footprint,
    CovariateProfile covariateProfile,
    Instant generatedAt,
    ExecutionSpecification spec
) {}
```

---

## 8. Baseline File Naming

### 8.1 BaselineFileNamer

```java
package org.javai.punit.engine;

/**
 * Generates and parses baseline filenames.
 *
 * Format: {UseCaseName}-{footprintHash}[-{covHash1}[-{covHash2}...]].yaml
 */
public class BaselineFileNamer {

    /**
     * Generates the filename for a baseline.
     */
    public String generateFilename(
            String useCaseName,
            String footprintHash,
            CovariateProfile covariateProfile) {
        
        var sb = new StringBuilder();
        sb.append(sanitize(useCaseName));
        sb.append("-").append(truncateHash(footprintHash));
        
        for (String hash : covariateProfile.computeValueHashes()) {
            sb.append("-").append(truncateHash(hash));
        }
        
        sb.append(".yaml");
        return sb.toString();
    }

    /**
     * Parses a baseline filename to extract components.
     */
    public ParsedFilename parse(String filename) {
        // Implementation
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String truncateHash(String hash) {
        return hash.substring(0, Math.min(4, hash.length()));
    }
}
```

---

## 9. Extended ExecutionSpecification

### 9.1 New Fields

The `ExecutionSpecification` class is extended with:

```java
public final class ExecutionSpecification {
    // ... existing fields ...
    
    private final CovariateProfile covariateProfile;
    private final String footprint;
    
    // ... in Builder ...
    
    public Builder covariateProfile(CovariateProfile profile) {
        this.covariateProfile = profile;
        return this;
    }
    
    public Builder footprint(String footprint) {
        this.footprint = footprint;
        return this;
    }
}
```

### 9.2 YAML Schema Extension

```yaml
# punit-spec-3 schema (extends punit-spec-2)
schema: punit-spec-3
useCaseId: shopping.product.search
version: 1

footprint: "ax43"  # Hash of use case + factors + covariate names

covariates:
  weekday_vs_weekend: "Mo-Fr"
  time_of_day: "14:30-14:45 Europe/London"
  region: "EU"

empiricalBasis:
  samples: 1000
  successes: 973
  experimentStartTime: "2026-01-10T14:30:00Z"
  experimentEndTime: "2026-01-10T14:45:00Z"

# ... rest of spec ...
```

---

## 10. Reporting Integration

### 10.1 Baseline Identification in Verdict

```java
public record BaselineProvenance(
    String filename,
    String footprint,
    Instant generatedAt,
    int samples,
    CovariateProfile covariateProfile
) {
    public String toHumanReadable() {
        return String.format("%s (generated %s, %d samples)",
            filename, generatedAt, samples);
    }
}
```

### 10.2 Non-Conformance Warning

```java
public record CovariateWarning(
    List<ConformanceDetail> nonConformingCovariates,
    boolean ambiguousSelection
) {
    public String render() {
        var sb = new StringBuilder();
        sb.append("⚠️ COVARIATE NON-CONFORMANCE\n");
        sb.append("Statistical inference may be less reliable.\n\n");
        
        for (var detail : nonConformingCovariates) {
            sb.append(String.format("  • %s: baseline=%s, test=%s\n",
                detail.covariateKey(),
                detail.baselineValue().toCanonicalString(),
                detail.testValue().toCanonicalString()));
        }
        
        if (ambiguousSelection) {
            sb.append("\n⚠️ Multiple equally-suitable baselines existed.\n");
        }
        
        return sb.toString();
    }
}
```

---

## 11. Integration Points

### 11.1 Experiment Extension

During MEASURE experiment execution:

1. Extract `CovariateDeclaration` from `@UseCase` annotation
2. Create `CovariateResolutionContext` with experiment timing
3. Resolve all covariates to create `CovariateProfile`
4. Compute footprint including covariate names
5. Generate baseline filename with covariate hashes
6. Store profile in `ExecutionSpecification`

### 11.2 Probabilistic Test Extension

During test execution:

1. Extract `CovariateDeclaration` from linked use case
2. Compute test's footprint
3. Load all baseline files for use case
4. Filter by footprint match (hard gate)
5. Resolve test's current `CovariateProfile`
6. Select best baseline using `BaselineSelector`
7. Evaluate conformance and generate warnings
8. Include `BaselineProvenance` and warnings in report

---

## 12. Error Handling

### 12.1 No Matching Footprint

```java
public class NoCompatibleBaselineException extends PunitConfigurationException {
    
    public NoCompatibleBaselineException(
            String useCaseId, 
            String expectedFootprint,
            List<String> availableFootprints) {
        super(String.format(
            "No baseline matches footprint '%s' for use case '%s'. " +
            "Available footprints: %s. " +
            "This may indicate covariate declarations have changed. " +
            "Run a MEASURE experiment to generate a compatible baseline.",
            expectedFootprint, useCaseId, availableFootprints));
    }
}
```

### 12.2 Missing Custom Covariate

When a custom covariate is not found in the environment, it is recorded as `"not_set"`. This is not an error at capture time, but will result in non-conformance at test time.

---

## 13. Migration Path

### 13.1 Backward Compatibility

- Existing baselines without covariates continue to work
- Empty covariate declaration (`covariates = {}`) produces same footprint as before
- Schema version upgrade (`punit-spec-2` → `punit-spec-3`) is backward compatible

### 13.2 Migration Steps

1. Add covariate declarations to use cases
2. Run MEASURE experiments to generate new baselines with covariate profiles
3. Old baselines are automatically excluded (different footprint)
4. No manual cleanup required (old files are simply not matched)

---

## 14. Testing Strategy

### 14.1 Unit Tests

- `CovariateProfileTest`: Hash computation, serialization
- `FootprintComputerTest`: Stability, ordering independence of factors
- `*ResolverTest`: Each resolver with various inputs
- `*MatcherTest`: Each matcher with conforming/non-conforming cases
- `BaselineSelectorTest`: Scoring, tie-breaking, ambiguity detection

### 14.2 Integration Tests

- End-to-end MEASURE → TEST flow with covariates
- Baseline selection with multiple candidates
- Non-conformance warning generation
- Footprint mismatch error handling

