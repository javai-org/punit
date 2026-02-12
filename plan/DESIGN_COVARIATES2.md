# Redesign covariates: partitioning model, flexible standard covariates, separate attributes

## Context

The covariate mechanism lets a developer declare that a use case's postconditions may be
affected by contextual factors. Both experiments (which measure baselines) and tests (which
verify against baselines) inherit covariate sensitivity from the use case they exercise.

Problems with the current design:

1. `StandardCovariate.WEEKDAY_VERSUS_WEEKEND` hard-codes a binary {Mon-Fri} vs {Sat-Sun}
   partition. The framework should support arbitrary day groupings.

2. `StandardCovariate.TIME_OF_DAY` assumes a single opaque time window. The developer
   should be able to specify named time-of-day periods and partition accordingly.

3. Three separate attributes on `@UseCase` (`covariates`, `customCovariates`,
   `categorizedCovariates`) when one would suffice.

4. `CovariateCategory.INFORMATIONAL` declares something as a covariate and then ignores it
   -- a contradiction. Traceability metadata belongs in code comments, not covariate
   declarations.

5. `BaselineSelector` uses direct enum comparison (`== CovariateCategory.CONFIGURATION`)
   instead of `CovariateCategory.isHardGate()`. The hard-gate behavior should flow through
   the category's API so that the matching semantics are transparent to the developer reading
   the category documentation.

No backward compatibility needed -- sole user, will re-measure baselines.

## Conceptual model

### What covariates declare

A covariate on a use case says: "The postconditions of this use case may be affected by
this factor." The framework uses this declaration to:
- Partition baselines during experiments
- Select the appropriate baseline partition at test time
- Report mismatches with appropriate severity

### Three covariate behaviors

**1. Partitioning** -- day-of-week, time-of-day, region

The developer declares named groups of values. Each group defines a partition in which the
use case's behavior is expected to be statistically homogeneous. Values not covered by any
declared group form an implicit remainder partition.

Rules:
- Groups must be mutually exclusive (a value cannot appear in more than one group)
- If declared groups exhaust all possible values, there is no remainder partition
- If declared groups do not exhaust all values, the uncovered values form one implicit
  remainder partition

Partitioning covariates fall into two sub-types based on their domain:

**Finite-domain partitioning** -- the set of possible values is known and bounded.
Day-of-week (7 values) and time-of-day (a bounded 24-hour cycle) are finite-domain. The
framework can validate completeness: it knows whether declared groups exhaust the space
and can reason about what the remainder contains. For example, declaring
`{SAT, SUN}` + `{MON, TUE, WED, THU, FRI}` covers all 7 days, so the framework can confirm
there is no remainder partition.

**Open-domain partitioning** -- the set of possible values is unbounded and unknown to the
framework. Region is open-domain: the framework cannot enumerate all possible regions, so
it cannot validate completeness. It accepts whatever labels the developer declares and
treats everything else as remainder. There is always an implicit remainder partition
(the framework cannot know whether it is empty in practice).

Examples:
- Day-of-week (finite): declaring `{SAT, SUN}` creates two partitions -- that group
  and the remaining five days
- Day-of-week (finite): declaring `{MON, TUE}` + `{THU, SAT}` creates three partitions --
  the two declared groups and `{WED, FRI, SUN}`
- Time-of-day (finite): declaring `08:00/2h` + `16:00/3h` creates three partitions -- the
  two periods and the remaining hours. Periods must not cross midnight; the developer
  splits a midnight-spanning concept into two within-day periods
- Region (open): declaring `{FR, DE}` + `{UK, IR}` creates three partitions -- the two
  declared groups and everything else (remainder always present since the framework can't
  know all possible regions). A single-element group like `{US}` is just a region on its own

**2. Identity** -- timezone

The developer declares sensitivity without specifying groups. The framework captures the
actual value from the environment and matches baselines by that value. No partitioning, no
remainder, no grouping -- just a label recorded and matched.

**3. Hard gate** -- configuration covariates (e.g. LLM model, prompt version)

A mismatch disqualifies the baseline entirely. The developer declares what configuration
factors matter; the `BaselineSelector` refuses to consider baselines measured under a
different configuration. This behavior is determined by `CovariateCategory.isHardGate()`.

### Custom covariates

Standard covariates handle each dimension independently. When a developer needs
cross-dimensional logic -- e.g. "08:00-11:00 on weekdays but 11:00-14:00 on Saturdays" --
a custom covariate provides the escape hatch. The developer supplies the resolution function
and assigns a category; the framework records and matches the resulting value according to
the category's semantics.

## API

### Annotation definitions

Each standard covariate gets a dedicated attribute on `@UseCase`. The attribute name carries
the `covariate` prefix, making the family visible at a glance and grouping them in IDE
autocomplete. Custom covariates use a `covariates` array of `@Covariate` annotations.

```java
// Day partition: one or more days forming a single partition
@Target({})
public @interface DayGroup {
    DayOfWeek[] value();
    String label() default "";  // auto-derived if empty
}

// Region partition: one or more ISO 3166-1 alpha-2 country codes forming a single partition
@Target({})
public @interface RegionGroup {
    String[] value();
    String label() default "";  // auto-derived if empty
}

// Custom covariate declaration
@Target({})
public @interface Covariate {
    String key();
    CovariateCategory category();
}
```

### `@UseCase` covariate attributes

```java
public @interface UseCase {
    String value() default "";
    String description() default "";

    // Standard covariates -- each attribute declares a covariate type
    DayGroup[]    covariateDayOfWeek() default {};
    String[]      covariateTimeOfDay() default {};   // e.g. "08:00/2h"
    RegionGroup[] covariateRegion()    default {};
    boolean       covariateTimezone()  default false;

    // Custom covariates
    Covariate[]   covariates() default {};

    // ... existing non-covariate attributes unchanged
}
```

### Full example

```java
@UseCase(
    value = "shopping.product.search",
    description = "Search products by query",

    // Day-of-week: weekends grouped, Monday distinct, Tue-Fri = remainder
    covariateDayOfWeek = {
        @DayGroup({SATURDAY, SUNDAY}),
        @DayGroup(MONDAY)
    },

    // Time-of-day: morning rush + evening rush, rest = remainder
    covariateTimeOfDay = { "08:00/2h", "16:00/3h" },

    // Region: FR/DE grouped, UK/IR grouped, rest = remainder
    covariateRegion = {
        @RegionGroup({"FR", "DE"}),
        @RegionGroup({"UK", "IR"})
    },

    // Timezone: identity covariate
    covariateTimezone = true,

    // Custom covariates
    covariates = {
        @Covariate(key = "llm_model", category = CONFIGURATION),
        @Covariate(key = "prompt_version", category = CONFIGURATION),
        @Covariate(key = "cache_state", category = DATA_STATE)
    }
)
public class ShoppingUseCase { }
```

Note: for single-element groups, Java annotation syntax allows dropping the array braces:
`@DayGroup(MONDAY)` instead of `@DayGroup({MONDAY})`, and `@RegionGroup("US")` instead of
`@RegionGroup({"US"})`.

## Standard covariate details

### Day-of-week

Finite-domain partitioning covariate (7 values). Each `@DayGroup` declares one or more
`java.time.DayOfWeek` values that form a single partition. Days within a group are
considered statistically equivalent.

```java
// Simplest: just "weekends behave differently"
// Two partitions: {SAT,SUN} and remainder {MON..FRI}
covariateDayOfWeek = { @DayGroup({SATURDAY, SUNDAY}) }

// Equivalent to old WEEKDAY_VERSUS_WEEKEND (all days covered, no remainder)
covariateDayOfWeek = { @DayGroup({SATURDAY, SUNDAY}), @DayGroup({MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY}) }

// Monday is special, weekends grouped, rest = remainder
// Three partitions: {SAT,SUN}, {MON}, remainder {TUE..FRI}
covariateDayOfWeek = { @DayGroup({SATURDAY, SUNDAY}), @DayGroup(MONDAY) }

// Arbitrary grouping: {MON,TUE} and {THU,SAT} behave equivalently within each group
// Three partitions: {MON,TUE}, {THU,SAT}, remainder {WED,FRI,SUN}
covariateDayOfWeek = { @DayGroup({MONDAY, TUESDAY}), @DayGroup({THURSDAY, SATURDAY}) }
```

Labels: if `label()` is empty, the framework auto-derives it. For well-known patterns
(`{SATURDAY, SUNDAY}` -> "WEEKEND", `{MONDAY..FRIDAY}` -> "WEEKDAY"), the framework uses
the conventional name. Otherwise it joins the day names (e.g. "MONDAY_TUESDAY").

Validation (at extraction time):
- A day cannot appear in more than one `@DayGroup` (mutual exclusivity)
- If all 7 days are covered, no remainder partition

Resolution (`DayOfWeekResolver`):
- Get current `DayOfWeek` from `context.now().atZone(context.systemTimezone())`
- If current day is in a declared group -> resolve to that group's label
- If undeclared (remainder) -> resolve to a stable remainder label (e.g. "OTHER")

Matching: `ExactStringMatcher` -- resolved values are plain strings.

### Time-of-day

Finite-domain partitioning covariate (24-hour cycle). Each period is defined as a
**start time + duration**, where `start + duration <= 24:00`. Periods must not cross
midnight.

Start times use HH:mm format where HH is 00-23 and mm is 00-59.

Periods use **half-open intervals** `[start, start+duration)` -- start is inclusive, end is
exclusive. This means adjacent periods tile cleanly without overlap: `[08:00, 10:00)` +
`[10:00, 12:00)` share the boundary point with no ambiguity.

```java
covariateTimeOfDay = { "08:00/2h", "16:00/3h" }
```

This creates three partitions: "08:00/2h" = [08:00, 10:00), "16:00/3h" = [16:00, 19:00),
and the remainder.

The `start+duration` format is preferred over `start-end` because `00:00` is ambiguous as
an endpoint (start of day? end of day?), whereas `23:00/1h` unambiguously means
[23:00, 24:00).

If a developer conceptually wants a period spanning midnight (e.g. 23:00 to 01:00), they
declare two periods: `23:00/1h` and `00:00/1h`. These become two separate partitions, which
is correct when combined with day-of-week partitioning -- the 23:00 hour belongs to one day
and the 00:00 hour belongs to the next. For developers who truly need a single partition
crossing midnight, a custom covariate is the escape hatch.

Validation (at extraction time):
- Start time HH:mm: HH in [00, 23], mm in [00, 59]
- `start + duration <= 24:00` (no midnight crossing)
- Duration must be > 0
- Periods must not overlap

Resolution (`TimeOfDayResolver`):
- Get current time from `context.now().atZone(context.systemTimezone())`
- If current time falls within a declared period -> resolve to that period's label
- Otherwise -> resolve to a stable remainder label (e.g. "OTHER")

The framework can validate completeness: if declared periods cover the full 24-hour cycle
[00:00, 24:00), there is no remainder partition.

### Region

Open-domain partitioning covariate. Each `@RegionGroup` declares one or more region labels
that form a single partition. Regions within a group are considered statistically equivalent.
The framework cannot enumerate all possible regions, so there is always an implicit
remainder partition.

Region values must be valid ISO 3166-1 alpha-2 country codes, validated against
`Locale.getISOCountries()` at extraction time.

```java
// Two groups: {FR, DE} and {UK, IR}. Three partitions total (including remainder).
covariateRegion = { @RegionGroup({"FR", "DE"}), @RegionGroup({"UK", "IR"}) }

// Single-element groups: each region is its own partition. Three partitions total.
covariateRegion = { @RegionGroup("US"), @RegionGroup("EU") }
```

Validation (at extraction time):
- Region labels must be valid ISO 3166-1 alpha-2 country codes
- A region label cannot appear in more than one group (mutual exclusivity)

Resolution: system property `punit.region` or environment variable `PUNIT_REGION`.
If the resolved value is in a declared group -> that group's label. Otherwise -> remainder.

No completeness validation is possible (unlike finite-domain covariates).

### Timezone

Identity covariate. No partitioning, no parameters. Declaring it says "timezone matters":

```java
covariateTimezone = true
```

Resolution: captures the system timezone. Matching: exact string match against baselines.

## Remove INFORMATIONAL category

Remove `CovariateCategory.INFORMATIONAL` and all associated functionality and documentation.
Remove `isIgnoredInMatching()`. Update `BaselineSelector`, `BaselineFileNamer`, and any
other references.

## Hard gate: use the category API

Replace direct enum comparisons (`== CovariateCategory.CONFIGURATION`) in `BaselineSelector`
with `CovariateCategory.isHardGate()` calls.

## New files

| File                                             | Purpose                                                                             |
|--------------------------------------------------|-------------------------------------------------------------------------------------|
| `api/DayGroup.java`                              | Annotation: `DayOfWeek[] value()`, `String label()`. `@Target({})`.                 |
| `api/RegionGroup.java`                           | Annotation: `String[] value()`, `String label()`. `@Target({})`.                    |
| `spec/baseline/covariate/DayOfWeekResolver.java` | Implements `CovariateResolver`. Constructor takes list of day group definitions.    |
| `spec/baseline/covariate/TimeOfDayResolver.java` | Implements `CovariateResolver`. Constructor takes list of time period definitions.  |
| `spec/baseline/covariate/RegionResolver.java`    | Implements `CovariateResolver`. Constructor takes list of region group definitions. |
| Tests for each new class                         | `DayOfWeekResolverTest`, `TimeOfDayResolverTest`, `RegionResolverTest`              |

## Modified files

| File                             | Change                                                                                                                                                                                                                                                                   |
|----------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Covariate.java`                 | Simplify to custom-only: `String key()`, `CovariateCategory category()`.                                                                                                                                                                                                 |
| `UseCase.java`                   | Replace `StandardCovariate[] covariates`, `String[] customCovariates`, `Covariate[] categorizedCovariates` with: `DayGroup[] covariateDayOfWeek`, `String[] covariateTimeOfDay`, `RegionGroup[] covariateRegion`, `boolean covariateTimezone`, `Covariate[] covariates`. |
| `CovariateCategory.java`         | Remove `INFORMATIONAL`. Remove `isIgnoredInMatching()`. Update Javadoc.                                                                                                                                                                                                  |
| `CovariateDeclaration.java`      | Rework to store day group definitions, time period definitions, region group definitions.                                                                                                                                                                                |
| `UseCaseCovariateExtractor.java` | Extract from separate `@UseCase` attributes. Build group definitions. Validate mutual exclusivity. Validate time period format. Validate region codes against ISO 3166-1.                                                                                                |
| `CovariateResolverRegistry.java` | Register `DayOfWeekResolver` for `"day_of_week"`, `TimeOfDayResolver` for `"time_of_day"`, `RegionResolver` for `"region"`. Remove `WeekdayVsWeekendResolver`.                                                                                                           |
| `CovariateMatcherRegistry.java`  | Register `ExactStringMatcher` for `"day_of_week"` and `"region"`. Remove `WeekdayVsWeekendMatcher`.                                                                                                                                                                      |
| `CovariateProfileResolver.java`  | Thread group/period definitions from declaration to resolvers.                                                                                                                                                                                                           |
| `BaselineSelector.java`          | Replace direct `== CovariateCategory.CONFIGURATION` with `isHardGate()`. Remove INFORMATIONAL handling.                                                                                                                                                                  |
| `BaselineFileNamer.java`         | Remove INFORMATIONAL exclusion logic (no longer needed).                                                                                                                                                                                                                 |

## Deleted files

| File                                | Reason                                                                          |
|-------------------------------------|---------------------------------------------------------------------------------|
| `StandardCovariate.java`            | No longer needed -- covariate types are expressed by `@UseCase` attribute names |
| `StandardCovariateTest.java`        | Enum removed                                                                    |
| `WeekdayVsWeekendResolver.java`     | Replaced by `DayOfWeekResolver`                                                 |
| `WeekdayVsWeekendMatcher.java`      | Replaced by `ExactStringMatcher` (already exists)                               |
| `WeekdayVsWeekendResolverTest.java` | Replaced by `DayOfWeekResolverTest`                                             |
| `WeekdayVsWeekendMatcherTest.java`  | No longer needed                                                                |

## Updated test/example files

All files referencing `WEEKDAY_VERSUS_WEEKEND`, `weekday_vs_weekend`, `INFORMATIONAL`,
the old covariate attributes, or `StandardCovariate`.

| File                                                  | Change                                                                                               |
|-------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| `CovariateDeclarationTest.java`                       | Update for new declaration model with group/period definitions                                       |
| `CovariateResolverRegistryTest.java`                  | `DayOfWeekResolver`, `TimeOfDayResolver`, `RegionResolver`                                           |
| `CovariateMatcherRegistryTest.java`                   | `ExactStringMatcher` for day_of_week and region                                                      |
| `UseCaseCovariateExtractorTest.java`                  | Rewrite for separate attributes, day group extraction, time period validation, region ISO validation |
| `CovariateProfileResolverTest.java`                   | Add group-aware and period-aware resolution tests                                                    |
| `CovariateProfileTest.java`                           | Update keys/values                                                                                   |
| `BaselineFileNamerTest.java`                          | Update keys/values, remove INFORMATIONAL tests                                                       |
| `BaselineRepositoryTest.java`                         | Update YAML                                                                                          |
| `ProbabilisticTestExtensionCovariateTest.java`        | Update YAML                                                                                          |
| `ShoppingBasketUseCase.java`                          | Migrate to `covariateDayOfWeek = { @DayGroup({SATURDAY, SUNDAY}) }` etc.                             |
| `ShoppingBasketCovariateTest.java`                    | Update refs                                                                                          |
| `BaselineSelectorTest.java`                           | Update for `isHardGate()` usage, remove INFORMATIONAL tests                                          |
| `CovariateCategoryTest.java`                          | Remove INFORMATIONAL tests                                                                           |
| `docs/USER-GUIDE.md`, `docs/STATISTICAL-COMPANION.md` | Update references                                                                                    |

## Implementation order

1. **New annotations** -- `@DayGroup`, `@RegionGroup`
2. **`@Covariate` annotation** -- simplify to custom-only (key + category)
3. **`@UseCase` annotation** -- replace covariate attributes with separate typed attributes
4. **`CovariateCategory`** -- remove INFORMATIONAL, remove `isIgnoredInMatching()`
5. **Delete `StandardCovariate`** -- remove enum and test
6. **New resolvers** -- `DayOfWeekResolver`, `TimeOfDayResolver`, `RegionResolver`
7. **`CovariateDeclaration`** -- rework for group/period definitions
8. **`UseCaseCovariateExtractor`** -- extract from separate attributes, validate
9. **Registries** -- swap registrations
10. **`CovariateProfileResolver`** -- thread definitions to resolvers
11. **`BaselineSelector`** -- use `isHardGate()`, remove INFORMATIONAL handling
12. **`BaselineFileNamer`** -- remove INFORMATIONAL exclusion
13. **Delete** old resolver/matcher files
14. **Tests** -- new + update all existing
15. **Documentation**

## Verification

1. `./gradlew test --rerun` -- all tests pass
2. Verify `ShoppingBasketCovariateTest` runs with new API
3. `./gradlew test jacocoTestReport` -- coverage on new classes
