# Design: Unified Test Class with Tag-based Experiment/Test Separation

## Status

**Proposed** — Ready for implementation

## Problem

PUnit's current workflow requires **four artifacts** to test a non-deterministic system:

1. **Service under test** — the actual functionality (e.g., an LLM-powered shopping agent)
2. **Use case** — wraps the service, evaluates postconditions, reports outcomes
3. **Measure experiment class** (`ShoppingBasketMeasure`) — runs the use case at scale (1000+ samples) to produce a baseline YAML spec
4. **Probabilistic test class** (`ShoppingBasketTest`) — asserts against the baseline on an ongoing basis (100 samples)

By comparison, conventional testing has just **2 artifacts** (service + test).

The Measure and Test classes share significant ceremony:

| Duplicated element   | `ShoppingBasketMeasure`       | `ShoppingBasketTest`          |
|----------------------|-------------------------------|-------------------------------|
| `@RegisterExtension` | `UseCaseProvider provider`    | `UseCaseProvider provider`    |
| `@BeforeEach`        | `register(…, ::new)`          | `register(…, ::new)`          |
| Input source         | `basketInstructions()`        | `standardInstructions()`      |
| Method body          | `captor.record(useCase.…)`    | `useCase.….assertAll()`       |
| `useCase =`          | `ShoppingBasketUseCase.class` | `ShoppingBasketUseCase.class` |

The input sources are often near-identical or outright duplicated. The developer must maintain two classes, two sets of imports, and two sets of setup code that test the same use case.

## Design Decision: Keep UseCase, Merge Experiment + Test

The **use case** earns its place as a distinct artifact — its annotation carries covariate information fundamental to statistics, it provides the service contract, factor getters/setters, and creates a technical buffer enabling both experimentation and correctness assertions.

The consolidation target is the **Measure experiment** and the **Probabilistic test**: they can coexist as methods in a single class, distinguished by annotation, with shared setup and input sources.

### Before (4 artifacts)

```
ShoppingBasketUseCase.java     ← use case (keeps)
ShoppingBasketMeasure.java     ← experiment class (merges into ↓)
ShoppingBasketTest.java        ← test class (merges into ↓)
```

### After (3 artifacts)

```
ShoppingBasketUseCase.java             ← use case (unchanged)
ShoppingBasketReliabilityTest.java     ← unified class with both @MeasureExperiment and @ProbabilisticTest methods
```

## Technical Feasibility

Investigation confirmed **zero architectural barriers** to co-location:

- Both `@MeasureExperiment` and `@ProbabilisticTest` carry `@TestTemplate` and `@ExtendWith` for their respective extensions
- Extensions discover methods independently via `supportsTestTemplate()`, which checks annotation type
- `@InputSource` resolves per-method (not per-class)
- `UseCaseProvider` (`@RegisterExtension`) works with both extension types
- No shared mutable state between extensions

## Problem with Naive Co-location

Without additional infrastructure, running `./gradlew test` on a unified class executes **both** the measure experiment and the probabilistic test. JUnit 5 discovers all `@TestTemplate` methods in a class. Neither the Gradle `test` task nor the `exp` task filters by annotation type — the distinction relies entirely on JUnit 5's `TestTemplateInvocationContextProvider` extension mechanism, which doesn't prevent both extensions from running their respective methods.

This is undesirable because:
- Measure experiments are expensive (1000+ samples, often calling external APIs)
- They should only run on-demand, not in every CI build
- The `test` task should run only probabilistic tests

## Solution: JUnit 5 Tag-based Filtering

Two options were evaluated:

| Criterion              | Option A: JUnit 5 Tags (meta-annotation) | Option B: System property in extensions |
|------------------------|------------------------------------------|-----------------------------------------|
| Framework code changes | None (annotations only)                  | Extension logic changes required        |
| IDE compatibility      | Native support (IntelliJ, Eclipse)       | Requires run config setup               |
| JUnit idiom            | Standard mechanism                       | Custom mechanism                        |
| Gradle integration     | `includeTags` / `excludeTags`            | `systemProperty` checks                 |

**Option A (JUnit 5 Tags)** is chosen — it is idiomatic, requires zero extension code changes, and works natively with IDE test runners.

### Implementation

#### 1. Add `@Tag("punit-experiment")` to experiment annotations

JUnit 5 supports transitive tags via meta-annotations. Adding `@Tag` to the annotation definition means every method carrying that annotation automatically inherits the tag.

**Files to modify:**

- `src/main/java/org/javai/punit/api/MeasureExperiment.java`
- `src/main/java/org/javai/punit/api/ExploreExperiment.java`
- `src/main/java/org/javai/punit/api/OptimizeExperiment.java`

Each annotation's meta-annotations change from:

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(ExperimentExtension.class)
```

To:

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(ExperimentExtension.class)
@Tag("punit-experiment")
```

Import to add: `org.junit.jupiter.api.Tag`

#### 2. Exclude experiment tag from `test` task

**File:** `build.gradle.kts`

```kotlin
tasks.test {
    useJUnitPlatform {
        excludeTags("punit-experiment")
    }
    // ... rest unchanged
}
```

This ensures experiment methods never run during `./gradlew test`, even if the class is not `@Disabled`.

#### 3. Include only experiment tag in `exp` task

**File:** `build.gradle.kts` (inside `configureAsExperimentTask()`)

```kotlin
useJUnitPlatform {
    includeTags("punit-experiment")
}
```

This ensures `@ProbabilisticTest` methods are excluded during `./gradlew exp`, even when `@Disabled` is deactivated.

## Example: Unified Test Class

```java
@Disabled("Example - see class Javadoc for run instructions")
public class ShoppingBasketReliabilityTest {

    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    @BeforeEach
    void setUp() {
        provider.register(ShoppingBasketUseCase.class, ShoppingBasketUseCase::new);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MEASURE — establish baseline (run once / periodically)
    // ═══════════════════════════════════════════════════════════════════════════

    @MeasureExperiment(useCase = ShoppingBasketUseCase.class, experimentId = "baseline-v1")
    @InputSource("instructions")
    void measureBaseline(ShoppingBasketUseCase useCase, String instruction, OutcomeCaptor captor) {
        captor.record(useCase.translateInstruction(instruction));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST — verify against baseline (run in CI)
    // ═══════════════════════════════════════════════════════════════════════════

    @ProbabilisticTest(useCase = ShoppingBasketUseCase.class, samples = 100)
    @InputSource("instructions")
    void testInstructionTranslation(ShoppingBasketUseCase useCase, String instruction) {
        useCase.translateInstruction(instruction).assertAll();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SHARED INPUT SOURCE
    // ═══════════════════════════════════════════════════════════════════════════

    static Stream<String> instructions() {
        return Stream.of(
                "Add 2 apples",
                "Remove the milk",
                "Add 1 loaf of bread",
                "Add 3 oranges and 2 bananas",
                "Add 5 tomatoes and remove the cheese",
                "Clear the basket",
                "Clear everything",
                "Remove 2 eggs from the basket",
                "Add a dozen eggs",
                "I'd like to remove all the vegetables"
        );
    }
}
```

### Execution

```bash
# Phase 1: Establish baseline (run once / occasionally)
./gradlew exp -Prun=ShoppingBasketReliabilityTest.measureBaseline

# Phase 2: Verify against baseline (run in CI)
./gradlew test --tests "ShoppingBasketReliabilityTest.testInstructionTranslation"
```

Key benefits of the unified class:
- **Single `@BeforeEach`** — shared setup, no duplication
- **Shared input source** — `instructions()` used by both measure and test
- **Co-located documentation** — the relationship between measure and test is explicit
- **Same package/imports** — no cross-referencing between separate classes

## Backward Compatibility

| Scenario                                                             | Impact                                                                                                                                                                                                                                                    |
|----------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Existing separate experiment classes (e.g., `ShoppingBasketMeasure`) | **No change.** They have `@Disabled` (which `exp` deactivates) and their methods carry the experiment tag via the annotation. `includeTags("punit-experiment")` selects them correctly.                                                                   |
| Existing test-only classes (e.g., `ShoppingBasketTest`)              | **No change.** They have no experiment-tagged methods, so `excludeTags` has no effect.                                                                                                                                                                    |
| `exp` task's `@Disabled` deactivation                                | **Still needed** for standalone experiment classes that use `@Disabled`. The tag filter and the disabled deactivation work orthogonally — tags filter which methods are candidates, then conditions (like `@Disabled`) are evaluated on those candidates. |

## Files Summary

### Modify (framework annotations — add `@Tag("punit-experiment")`)

1. `src/main/java/org/javai/punit/api/MeasureExperiment.java`
2. `src/main/java/org/javai/punit/api/ExploreExperiment.java`
3. `src/main/java/org/javai/punit/api/OptimizeExperiment.java`

### Modify (build — tag-based filtering)

4. `build.gradle.kts` — `excludeTags("punit-experiment")` on `test` task; `includeTags("punit-experiment")` on `configureAsExperimentTask()`

### Create (example — unified pattern)

5. `src/test/java/org/javai/punit/examples/tests/ShoppingBasketReliabilityTest.java`

## Verification

1. `./gradlew compileTestJava` — compilation succeeds
2. `./gradlew test --rerun` — existing tests still pass; experiment methods are excluded by tag
3. Verify that experiment-tagged methods do NOT appear in `test` task output
