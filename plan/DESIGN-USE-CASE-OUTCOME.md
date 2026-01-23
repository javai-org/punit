# Design: UseCaseOutcome Fluent API

This document captures the proposed redesign of `UseCaseOutcome` to improve developer experience when expressing use case contracts.

## Motivation

The current `translateInstruction` method in `ShoppingBasketUseCase` obscures the contract (postconditions). The reader must wade through execution mechanics, validation logic, and result building before reaching the criteria definition. Even then, the criteria are indirect—lambdas that retrieve pre-computed booleans rather than expressing the conditions themselves.

A use case must address two requirements simultaneously:

1. **Express the contract unambiguously** — a conjunction of conditions that constitutes a mathematically flawless expression of what the service guarantees
2. **Make results freely available** — experiment writers need access to all data; test writers need to assert all conditions are met

## Foundational Concepts

### The Contract Belongs to the Service

The contract (preconditions and postconditions) belongs to the **service** being invoked, not the use case. However, since Java lacks Eiffel's built-in design-by-contract support, the service cannot formally declare its own contract. The use case serves as the proxy through which we formalize what the service requires and guarantees.

| Concept        | Belongs To | Expressed In |
|----------------|------------|--------------|
| Preconditions  | Service    | Use case     |
| Postconditions | Service    | Use case     |
| Execution      | Use case   | Use case     |

The service is passive—it doesn't execute itself. The use case:

1. **Formalizes** the service's contract (preconditions + postconditions)
2. **Executes** the service
3. **Evaluates** whether the contract was satisfied

### Design-by-Contract Vocabulary

Borrowing from Eiffel:

| Clause    | Purpose                           | Evaluation | On Failure                                   |
|-----------|-----------------------------------|------------|----------------------------------------------|
| `require` | Preconditions (inputs valid?)     | **Eager**  | Throws `UseCasePreconditionException`        |
| `ensure`  | Postconditions (outputs correct?) | **Lazy**   | Recorded in outcome for statistical analysis |

### Precondition Failure Is a Programming Error

A broken precondition signals that the caller (test writer or experiment writer) wrote bad code. This is not an "outcome" to be handled gracefully—it demands a fix. Therefore, precondition violations throw an unchecked exception.

### Postconditions May Require Different Perspectives

Different postconditions may need to scrutinize the result through different lenses (e.g., structural JSON validity vs. PII detection). The design supports multiple parallel derivations from the raw result, each with its own group of ensures.

Deep structure navigation (nested JSON, XML) should use domain-appropriate tools (JSONPath, XPath) within the lambdas, not framework-level chaining of derivations.

## The ServiceContract

The contract is a first-class concept, declared separately from execution. It captures both preconditions (what the service requires) and postconditions (what the service guarantees).

### Type Parameters

`ServiceContract<I, R>` is parameterized by:
- `I` — the **input type** to the service (internal to the use case)
- `R` — the **result type** from the service (externally visible)

The input type `I` is typically a private record declared within the use case class. It bundles method parameters and configuration into a single type for contract evaluation. This is an internal detail—callers never see it.

### Contract Definition

The contract is declared as a `static final` field at the top of the use case class—a pure specification with no free variables. A private record for the input type is declared immediately before:

```java
public class ShoppingBasketUseCase implements UseCase<String> {

    // ═══════════════════════════════════════════════════════════════════════════
    // THE SERVICE CONTRACT
    // ═══════════════════════════════════════════════════════════════════════════

    private record ServiceInput(String prompt, String instruction, double temperature) {}

    private static final ServiceContract<ServiceInput, String> CONTRACT = ServiceContract.define()

        // What the service requires (preconditions)
        .require("Prompt not null", in -> in.prompt() != null)
        .require("Instruction not blank", in -> !in.instruction().isBlank())
        .require("Temperature in range", in -> in.temperature() >= 0 && in.temperature() <= 1)

        // What the service guarantees (postconditions)
        .ensure("Response not empty", response -> !response.isEmpty())

        .deriving("Valid JSON", ShoppingBasketUseCase::parseJson)
            .ensure("Has operations array", json -> json.has("operations"))
            .ensure("All operations valid", json -> allOpsValid(json.path("operations")))
            .ensure("All actions valid", json -> allActionsValid(json.path("operations")))
            .ensure("All quantities positive", json -> allQuantitiesPositive(json.path("operations")))

        .deriving("Content analyzed", ShoppingBasketUseCase::analyzeContent)
            .ensure("No PII detected", analysis -> !analysis.containsPii());

    // ...
}
```

The private record sits directly above the contract that uses it—unobtrusive, localized, and readable.

### Purity

- **`static final`** — The contract is immutable, defined once
- **No free variables** — Every lambda receives its input as a parameter
- **Static method references** — `ShoppingBasketUseCase::parseJson` captures nothing
- **Mathematical** — The contract is a pure specification

The contract section reads as documentation. A reader can see exactly what the service requires and guarantees without wading through execution details.

## The Use Case Class

### Type Parameters

The use case class implements `UseCase<R>`, binding it to a single service result type:

```java
public class ShoppingBasketUseCase implements UseCase<String> {
```

This provides compile-time safety: all use case methods must produce `UseCaseOutcome<String>`. The input type (used internally by the contract) is not part of the public signature.

### Multiple Use Case Methods

A use case class may contain multiple methods that exercise the service in different ways—representing distinct **logical operations**, not configuration variants:

- A **simple** operation (e.g., single instruction translation)
- A **contextual** operation (e.g., translation with basket state)
- A **batch** operation (e.g., multiple instructions at once)

All methods share the same contract and return the same result type.

> **Note:** Configuration variation (e.g., different prompts, temperatures, models) should be handled via PUnit's `@FactorSetter` mechanism, not by creating separate methods. The factor system allows experiments to explore configuration space systematically.

```java
public class ShoppingBasketUseCase implements UseCase<String> {

    // ═══════════════════════════════════════════════════════════════════════════
    // THE SERVICE CONTRACT
    // ═══════════════════════════════════════════════════════════════════════════

    private record ServiceInput(String prompt, String instruction, double temperature) {}

    private static final ServiceContract<ServiceInput, String> CONTRACT = /* ... */;

    // ═══════════════════════════════════════════════════════════════════════════
    // INSTANCE STATE
    // ═══════════════════════════════════════════════════════════════════════════

    private final ChatLlm llm;
    private String systemPrompt = "...";
    private double temperature = 0.3;

    // ═══════════════════════════════════════════════════════════════════════════
    // USE CASE METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    // Simple instruction translation
    public UseCaseOutcome<String> translateInstruction(String instruction) {
        return UseCaseOutcome
            .withContract(CONTRACT)
            .input(new ServiceInput(systemPrompt, instruction, temperature))
            .execute(in -> llm.chat(in.prompt(), in.instruction(), in.temperature()))
            .meta("tokensUsed", llm.getLastTokensUsed())
            .build();
    }

    // Translation with current basket context
    public UseCaseOutcome<String> translateWithContext(String instruction, BasketState basket) {
        return UseCaseOutcome
            .withContract(CONTRACT)
            .input(new ServiceInput(contextualPrompt(basket), instruction, temperature))
            .execute(in -> llm.chat(in.prompt(), in.instruction(), in.temperature()))
            .meta("tokensUsed", llm.getLastTokensUsed())
            .meta("basketSize", basket.itemCount())
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONTRACT SUPPORT (pure functions)
    // ═══════════════════════════════════════════════════════════════════════════

    private static Outcome<JsonNode> parseJson(String response) { /* ... */ }
    private static Outcome<ContentAnalysis> analyzeContent(String response) { /* ... */ }
    private static boolean allOpsValid(JsonNode operations) { /* ... */ }
    private static boolean allActionsValid(JsonNode operations) { /* ... */ }
    private static boolean allQuantitiesPositive(JsonNode operations) { /* ... */ }
}
```

### Shared Contract, Varying Execution

| Concern          | Where It Lives                              |
|------------------|---------------------------------------------|
| Preconditions    | Contract (shared)                           |
| Postconditions   | Contract (shared)                           |
| Input mapping    | Per-method (parameters → `ServiceInput`)    |
| Execution        | Per-method                                  |
| Metadata         | Per-method                                  |

The contract is the invariant. Input mapping and execution vary by method.

### Public vs Internal Types

| Concept                 | Visibility | Example                                 |
|-------------------------|------------|-----------------------------------------|
| `UseCase<R>`            | Public     | `UseCase<String>`                       |
| `UseCaseOutcome<R>`     | Public     | `UseCaseOutcome<String>`                |
| `ServiceContract<I, R>` | Internal   | `ServiceContract<ServiceInput, String>` |
| Input record            | Internal   | `private record ServiceInput(...)`      |

Callers interact with use case methods (e.g., `translateInstruction(String)`) and receive `UseCaseOutcome<String>`. They never see the internal `ServiceInput` type.

## The Fluent API

### Complete Example

```java
public UseCaseOutcome<String> translateInstruction(String instruction) {
    return UseCaseOutcome
        .withContract(CONTRACT)
        .input(new LlmInput(systemPrompt, instruction, temperature))
        .execute(in -> llm.chat(in.prompt(), in.instruction(), in.temperature()))
        .meta("tokensUsed", llm.getLastTokensUsed())
        .meta("model", model)
        .build();
}
```

### API Components

#### `withContract(contract)`

Binds the service contract to this outcome. The contract's preconditions will be checked against the input; its postconditions will be evaluated against the result.

```java
.withContract(CONTRACT)
```

#### `input(value)`

Provides the input to the service. Preconditions from the contract are evaluated eagerly against this input. Throws `UseCasePreconditionException` if any precondition fails.

```java
.input(new LlmInput(systemPrompt, instruction, temperature))
```

#### `execute(function)`

Runs the service. The framework captures execution time automatically (no manual `Instant.now()` required). Returns the raw result that postconditions will scrutinize.

```java
.execute(in -> llm.chat(in.prompt(), in.instruction(), in.temperature()))
```

#### `meta(key, value)`

Adds metadata to the outcome. Used for service-specific data like token counts that cannot be standardized by the framework.

```java
.meta("tokensUsed", llm.getLastTokensUsed())
.meta("model", model)
```

#### `build()`

Terminal operation. Returns the `UseCaseOutcome<R>` containing:
- The raw result
- Captured execution time
- Metadata
- Lazy postconditions from the contract

```java
.build()
```

### ServiceContract API Components

#### `require(description, predicate)`

Defines a precondition on the service input. Evaluated eagerly when `input()` is called.

```java
.require("Instruction not blank", input -> !input.instruction().isBlank())
```

#### `ensure(description, predicate)` — direct postcondition

Defines a postcondition directly on the raw result. Use this for simple checks that don't require parsing or transforming the result.

```java
.ensure("Response not empty", response -> !response.isEmpty())
.ensure("Reasonable length", response -> response.length() < 10000)
```

Direct postconditions are evaluated before any derivations. They are simple predicates on the raw result—no gate semantics, no skipping.

#### `deriving(description, function)`

Transforms the raw result into a derived perspective. The function always returns `Outcome<D>`. The description names this derivation as an ensure in its own right.

```java
.deriving("Valid JSON", ShoppingBasketUseCase::parseJson)  // String -> Outcome<JsonNode>
    .ensure("Has operations array", json -> json.has("operations"))
```

**Derivation failure semantics:**
- If derivation succeeds → "Valid JSON" passes, nested ensures are evaluated
- If derivation fails → "Valid JSON" fails, nested ensures are **skipped** (not evaluated, not counted)

This prevents artificial inflation of failure counts when the real failure is the derivation itself.

For trivial transformations that cannot fail, use the `Outcomes.lift()` helper to wrap pure functions:

```java
.deriving("Lowercase", Outcomes.lift(String::toLowerCase))
    .ensure("Contains keyword", s -> s.contains("operations"))
```

Multiple `deriving` clauses branch from the raw result (parallel perspectives), not from each other (no chaining).

#### `ensure(description, predicate)` — nested postcondition

Within a `deriving` block, defines a postcondition on the derived value. Evaluated lazily.

```java
.deriving("Valid JSON", ShoppingBasketUseCase::parseJson)
    .ensure("Has operations array", json -> json.has("operations"))
```

## Structural Overview

```
ServiceContract<I, R>
    │
    ├─► require(...)                        // preconditions on I (eager)
    ├─► require(...)
    │
    ├─► ensure("Not empty", pred)           // direct postcondition on R
    ├─► ensure("Reasonable length", pred)   // direct postcondition on R
    │
    ├─► deriving("Valid JSON", parseJson)   // derivation (acts as gate)
    │       │
    │       ├─► SUCCESS: "Valid JSON" passes
    │       │       ├─► ensure("Has ops", pred)      // evaluated
    │       │       └─► ensure("All valid", pred)    // evaluated
    │       │
    │       └─► FAILURE: "Valid JSON" fails
    │               ├─► ensure("Has ops", pred)      // skipped
    │               └─► ensure("All valid", pred)    // skipped
    │
    └─► deriving("Lowercase", Outcomes.lift(...))
            └─► ensure("...", pred)         // evaluated if derivation succeeds


UseCaseOutcome Builder
    │
    ├─► withContract(CONTRACT)    // bind the service contract
    │
    ├─► input(I)                  // provide input, check preconditions (throws)
    │
    ├─► execute(I -> R)           // run service, capture timing
    │
    ├─► meta(key, value)          // add metadata
    │
    └─► build()                   // produce UseCaseOutcome<R>
```

The derivation acts as a **gate**: if it fails, nested ensures are skipped. Only the derivation itself (named by its description) counts as the failed postcondition.

## Key Properties

### Execution Time Captured Automatically

The `execute` clause wraps the function, capturing start and end instants. No `Instant.now()` boilerplate in use case code.

### Token Tracking via Metadata

Token capture cannot be standardized—every service does this differently. The developer must explicitly inject token counts via the `meta()` clause:

```java
.meta("tokensUsed", llm.getLastTokensUsed())
```

### Type-Safe Throughout

- `UseCase<R>` declares the result type (public API)
- `ServiceContract<I, R>` uses an internal input type `I` and the result type `R`
- `require` predicates are `Predicate<I>` (checked at `input()` call)
- `execute` function is `Function<I, R>`
- `deriving` produces `Outcome<D>`
- `ensure` predicates match the derived type
- `UseCaseOutcome<R>` is parameterized by the result type

### Lazy Postcondition Evaluation

Postconditions are stored as lambdas and evaluated only when needed. This supports:
- Early termination strategies
- Parallel evaluation
- Detailed failure reporting

### Direct Postconditions vs Derivations

The contract supports two ways to define postconditions:

1. **Direct postconditions** — Simple predicates on the raw result. No transformation, no gate semantics. Always evaluated.

2. **Derivations** — Transform the result and act as a gate for nested postconditions. If the derivation fails, nested ensures are skipped.

Use direct postconditions for simple checks (not empty, reasonable length). Use derivations when you need to parse or transform the result before checking conditions.

### Derivation as a Gate

A `deriving()` clause is an ensure in its own right. The description names the postcondition ("Valid JSON"). If the derivation:

- **Succeeds** → The named ensure passes, nested ensures are evaluated
- **Fails** → The named ensure fails, nested ensures are **skipped** (not evaluated, not counted)

This prevents artificial inflation of failure counts. When JSON parsing fails, we count one failure ("Valid JSON"), not five (parsing + all the structural checks that couldn't run).

## Comparison: Before and After

### Before (Current Implementation)

```java
public UseCaseOutcome translateInstruction(String instruction) {
    Instant start = Instant.now();

    ChatResponse chatResponse = llm.chatWithMetadata(systemPrompt, instruction, temperature);
    String response = chatResponse.content();
    lastTokensUsed = chatResponse.totalTokens();

    Duration executionTime = Duration.between(start, Instant.now());

    ValidationResult validation = validateResponse(response);

    UseCaseResult result = UseCaseResult.builder()
            .value("isValidJson", validation.isValidJson)
            .value("hasOperationsArray", validation.hasOperationsArray)
            // ... more values ...
            .executionTime(executionTime)
            .build();

    UseCaseCriteria criteria = UseCaseCriteria.ordered()
            .criterion("Valid JSON",
                    () -> result.getBoolean("isValidJson", false))
            .criterion("Has operations array",
                    () -> result.getBoolean("hasOperationsArray", false))
            // ... more criteria ...
            .build();

    return new UseCaseOutcome(result, criteria);
}
```

**Problems:**
- Contract buried mid-method
- Criteria are indirect (retrieve pre-computed booleans)
- Manual timing boilerplate
- Double encoding (validation → result → criteria)
- Contract not reusable across methods

### After (Proposed Design)

```java
public class ShoppingBasketUseCase implements UseCase<String> {

    private record ServiceInput(String prompt, String instruction, double temperature) {}

    private static final ServiceContract<ServiceInput, String> CONTRACT = ServiceContract.define()
        .require("Prompt not null", in -> in.prompt() != null)
        .require("Instruction not blank", in -> !in.instruction().isBlank())
        .require("Temperature in range", in -> in.temperature() >= 0 && in.temperature() <= 1)

        .ensure("Response not empty", response -> !response.isEmpty())

        .deriving("Valid JSON", ShoppingBasketUseCase::parseJson)
            .ensure("Has operations array", json -> json.has("operations"))
            .ensure("All operations valid", json -> allOpsValid(json.path("operations")))
            .ensure("All actions valid", json -> allActionsValid(json.path("operations")))
            .ensure("All quantities positive", json -> allQuantitiesPositive(json.path("operations")))

        .deriving("Content analyzed", ShoppingBasketUseCase::analyzeContent)
            .ensure("No PII detected", analysis -> !analysis.containsPii());

    public UseCaseOutcome<String> translateInstruction(String instruction) {
        return UseCaseOutcome
            .withContract(CONTRACT)
            .input(new ServiceInput(systemPrompt, instruction, temperature))
            .execute(in -> llm.chat(in.prompt(), in.instruction(), in.temperature()))
            .meta("tokensUsed", llm.getLastTokensUsed())
            .build();
    }
}
```

**Improvements:**
- Contract declared at top of class as pure specification
- Preconditions and postconditions together in one place
- No free variables in contract definition
- Contract reusable across multiple use case methods
- Timing captured automatically
- Postconditions are direct predicates
- Method body is minimal: input → execute → metadata → build

## Builder Implementation

The construct comprises two builders:

1. **ContractBuilder** — Builds `ServiceContract<I, R>`, handling `require`, `deriving`, and `ensure`
2. **OutcomeBuilder** — Builds `UseCaseOutcome<R>`, handling `withContract`, `input`, `execute`, `meta`, and `build`

The `input()` call is where preconditions are checked (eagerly). The `build()` call is where the outcome is assembled with lazy postconditions.

---

*Related: [Design Principles](./DOC-02-DESIGN-PRINCIPLES.md)*
