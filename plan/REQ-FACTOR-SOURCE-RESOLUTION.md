# Factor Source Resolution Requirements

## Overview
When a `@FactorSource` annotation references a method by name, PUnit must resolve
that reference to an actual method. This document specifies the resolution algorithm.

## Current Behavior
The current implementation only searches the test class for simple names, requiring
users to use the `Class#method` syntax for methods defined elsewhere. This is
inconvenient because factor provider methods are commonly defined in the use case
class, not the experiment class.

## Proposed Resolution Algorithm

### Resolution Forms

| Form | Example | Description |
|------|---------|-------------|
| Simple name | `multipleBasketInstructions` | Method name only |
| Class#method | `MyUseCase#factorMethod` | Class name with method |
| Fully qualified | `com.example.MyUseCase#factorMethod` | Full package path |

### Resolution Order by Form

#### 1. Simple Name (`methodName`)

Search order:
1. **Current class** - The experiment/test class where `@FactorSource` is declared
2. **Use case class** - The class specified in `@MeasureExperiment(useCase = ...)`
   or equivalent annotation

If found in current class, use it (no further search). If both classes define
a method with the same name, the current class wins (shadowing).

**Example:**
```java
@MeasureExperiment(useCase = ShoppingBasketUseCase.class)
@FactorSource(value = "multipleBasketInstructions", factors = {"instruction"})
void measureBaseline(...) { }
```

Resolution:
1. Look for `ShoppingBasketMeasure.multipleBasketInstructions()` → not found
2. Look for `ShoppingBasketUseCase.multipleBasketInstructions()` → found ✓

#### 2. Class#method (`ClassName#methodName`)

Search order:
1. **Current package** - Look for `ClassName` in the experiment class's package
2. **Use case's package** - Look for `ClassName` in the use case class's package
3. **Fail with hint** - Suggest using fully qualified form

**Example:**
```java
// Experiment in: org.example.experiments
// Use case in: org.example.usecases

@FactorSource(value = "PaymentUseCase#standardAmounts", factors = {"amount"})
```

Resolution:
1. Look for `org.example.experiments.PaymentUseCase` → not found
2. Look for `org.example.usecases.PaymentUseCase` → found ✓

#### 3. Fully Qualified (`pkg.ClassName#methodName`)

Direct lookup - no search, unambiguous.

**Example:**
```java
@FactorSource(value = "org.example.usecases.PaymentUseCase#standardAmounts")
```

Resolution:
1. Load `org.example.usecases.PaymentUseCase` directly
2. Get method `standardAmounts`

### Distinguishing Forms

The resolution form is determined by parsing the `@FactorSource.value()`:

| Pattern | Form |
|---------|------|
| No `#` character | Simple name |
| Contains `#` but class part has no `.` | Class#method |
| Contains `#` and class part has `.` | Fully qualified |

## Functional Requirements

### FR-1: Simple Name Resolution
When `@FactorSource.value()` contains no `#` character:
1. Search for a static method with that name in the current class
2. If not found, search in the use case class (if specified)
3. If not found in either, throw `ExtensionConfigurationException`

### FR-2: Class#method Resolution
When `@FactorSource.value()` contains `#` and the class part has no `.`:
1. Extract class name and method name by splitting on `#`
2. Search for the class in the current class's package
3. If not found, search in the use case class's package
4. If found, get the specified method
5. If not found, throw `ExtensionConfigurationException` with hint to use
   fully qualified form

### FR-3: Fully Qualified Resolution
When `@FactorSource.value()` contains `#` and the class part has `.`:
1. Extract fully qualified class name and method name
2. Load the class directly via `Class.forName()`
3. Get the specified method
4. If class or method not found, throw `ExtensionConfigurationException`

### FR-4: Method Requirements
The resolved method must:
- Be static
- Be accessible (public or package-private in same package)
- Return `Stream<FactorArguments>`, `List<FactorArguments>`, or `Collection<FactorArguments>`
- Take no parameters

### FR-5: Error Messages
Error messages must include:
- The original reference that failed to resolve
- The locations that were searched
- A suggestion to use the fully qualified form if ambiguous

**Example error:**
```
Cannot resolve factor source 'multipleBasketInstructions'.
Searched in:
  - org.example.experiments.ShoppingBasketMeasure
  - org.example.usecases.ShoppingBasketUseCase
Hint: Use fully qualified form 'org.example.MyClass#methodName' for explicit resolution.
```

### FR-6: Shadowing Behavior
If the same method name exists in both the current class and the use case class,
the current class takes precedence. No warning is issued (consistent with
standard lexical scoping).

## Implementation Notes

### Affected Classes
- `FactorResolver.resolveFactorArguments()` - Main resolution logic
- `FactorResolver.resolveClass()` - Class resolution helper (may need updates)

### Use Case Class Access
The resolution algorithm requires access to the use case class. This is available
from the experiment annotation:
- `@MeasureExperiment(useCase = ...)`
- `@ExploreExperiment(useCase = ...)`
- `@OptimizeExperiment(useCase = ...)`

The `FactorResolver.resolveFactorArguments()` method signature may need to accept
an additional `Class<?> useCaseClass` parameter.

## Examples

### Example 1: Simple Name in Use Case
```java
// ShoppingBasketUseCase.java
@FactorProvider
public static List<FactorArguments> multipleBasketInstructions() { ... }

// ShoppingBasketMeasure.java
@MeasureExperiment(useCase = ShoppingBasketUseCase.class)
@FactorSource(value = "multipleBasketInstructions", factors = {"instruction"})
void measureBaseline(...) { }
```
→ Resolves to `ShoppingBasketUseCase.multipleBasketInstructions()`

### Example 2: Simple Name in Current Class
```java
// ShoppingBasketMeasure.java
@FactorProvider
public static List<FactorArguments> customInstructions() { ... }

@MeasureExperiment(useCase = ShoppingBasketUseCase.class)
@FactorSource(value = "customInstructions", factors = {"instruction"})
void measureWithCustom(...) { }
```
→ Resolves to `ShoppingBasketMeasure.customInstructions()`

### Example 3: Cross-Package Reference
```java
// In org.example.experiments.PaymentMeasure.java
@MeasureExperiment(useCase = PaymentUseCase.class)  // in org.example.usecases
@FactorSource(value = "PaymentUseCase#standardAmounts", factors = {"amount"})
void measurePayments(...) { }
```
→ Resolves to `org.example.usecases.PaymentUseCase.standardAmounts()`

### Example 4: Fully Qualified
```java
@FactorSource(value = "org.example.shared.CommonFactors#allRegions", factors = {"region"})
void measureAcrossRegions(...) { }
```
→ Resolves to `org.example.shared.CommonFactors.allRegions()`

## Compatibility
This change is backward compatible:
- Existing `Class#method` syntax continues to work
- Existing simple names in current class continue to work
- New behavior only adds fallback to use case class

## Open Questions
1. Should we also search superclasses of the current class and use case class?
2. Should we emit a debug/trace log entry showing the resolution path?
