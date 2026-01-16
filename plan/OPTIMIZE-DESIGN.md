# OPTIMIZE Mode Detailed Design

## Document Status
**Status**: Draft
**Version**: 0.4
**Last Updated**: 2026-01-16
**Related Documents**: [OPTIMIZE-REQ.md](./OPTIMIZE-REQ.md)

### Revision History
| Version | Date       | Changes                                                                                                                      |
|---------|------------|------------------------------------------------------------------------------------------------------------------------------|
| 0.4     | 2026-01-16 | Removed @FixedFactors; introduced @TreatmentValue + @TreatmentValueSource pattern; updated to @OptimizeExperiment annotation |
| 0.3     | 2026-01-16 | Initial design with @Experiment(mode=OPTIMIZE)                                                                               |

---

## Executive Summary

OPTIMIZE is a new experiment mode that iteratively refines **one treatment factor** of a use case while holding the other factors constant. It is conceptually a MEASURE experiment in a loop, where the treatment factor is mutated between iterations based on evaluation of aggregated outcomes.

**Key insight**: The parameter being optimized is not a separate concept—it is simply a factor that we choose to vary via mutation rather than holding constant.

### What Makes OPTIMIZE Distinct

| Mode         | Execution                                     | Output                             |
|--------------|-----------------------------------------------|------------------------------------|
| **MEASURE**  | N samples, 1 factor suit                      | Baseline document (statistics)     |
| **EXPLORE**  | N samples × M factor suits                    | Specifications comparable via diff |
| **OPTIMIZE** | N samples × K iterations, mutating one factor | The optimized factor value         |

OPTIMIZE's output is fundamentally different: not a baseline, not a diff-comparable spec, but **the best value found for the treatment factor**.

### Workflow Context

```
EXPLORE → Select winning config → OPTIMIZE one factor → MEASURE (establish baseline)
```

---

## 1. Terminology

This design uses terminology from **Design of Experiments (DoE)**.

| Term                 | Definition                                                                                        | Example                                                                              |
|----------------------|---------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| **Factor**           | A parameter that influences use case behaviour. Can be any type.                                  | `model` (string), `temperature` (double), `systemPrompt` (string), `image` (binary)  |
| **FactorSuit**       | A complete set of factor values for a use case instance. Replaces the vague term "configuration". | `{model: "gpt-4", temperature: 0.7, systemPrompt: "..."}`                            |
| **Treatment Factor** | The factor being optimized. Mutated between iterations. (Taguchi terminology)                     | `systemPrompt`                                                                       |
| **Fixed Factors**    | All other factors. Held constant throughout optimization.                                         | `model`, `temperature`                                                               |
| **UseCaseOutcome**   | The result of a single use case execution, including success/failure judgment.                    | One LLM response with pass/fail evaluation                                           |
| **Aggregate**        | Statistics derived from N outcomes sharing the same factor suit.                                  | `{successRate: 0.94, samples: 50}`                                                   |

### Factors Can Be Anything

A factor is not limited to strings or numbers. It can be:
- A system prompt (string)
- An image (for vision models)
- Audio (for speech models)
- A structured template (object)
- Any type the use case accepts

The mutator must be compatible with the factor's type.

---

## 2. Conceptual Model

### 2.1 OPTIMIZE = MEASURE in a Loop

```
┌─────────────────────────────────────────────────────────────────┐
│  OPTIMIZE Iteration                                             │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  MEASURE-like execution:                                  │  │
│  │    - All factors fixed (including treatment factor)      │  │
│  │    - Execute use case N times                             │  │
│  │    - Collect N outcomes                                   │  │
│  │    - Aggregate into statistics                            │  │
│  └───────────────────────────────────────────────────────────┘  │
│                           │                                     │
│                           ▼                                     │
│                    ┌─────────────┐                              │
│                    │  Evaluate   │ (Scorer)                     │
│                    └─────────────┘                              │
│                           │                                     │
│                           ▼                                     │
│                    ┌─────────────┐                              │
│                    │   Mutate    │ (Mutator changes treatment    │
│                    │             │  factor for next iteration)  │
│                    └─────────────┘                              │
│                           │                                     │
│                           ▼                                     │
│                   Continue or terminate                         │
└─────────────────────────────────────────────────────────────────┘
```

Each iteration:
1. Execute the use case N times with current factor suit (like MEASURE)
2. Aggregate outcomes into statistics
3. Evaluate the aggregate (Scorer produces a score)
4. Record the iteration
5. Check termination conditions
6. Mutate the treatment factor for the next iteration

### 2.2 What Changes Between Iterations

- **Fixed factors**: unchanged (e.g., `model`, `temperature`)
- **Treatment factor**: mutated by the Mutator (e.g., `systemPrompt`)

All factors are part of the same factor suit. The only distinction is which one the Mutator is allowed to change.

---

## 3. Architecture

### 3.1 High-Level Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    @OptimizeExperiment                          │
│                                                                 │
│  Specifies:                                                     │
│    - useCase: the use case class                                │
│    - treatmentFactor: which factor to optimize                   │
│    - scorer: how to evaluate aggregates                         │
│    - mutator: how to generate new factor values                 │
│    - samplesPerIteration: N samples per iteration               │
│    - termination criteria (maxIterations, noImprovementWindow)  │
│                                                                 │
│  Initial treatment value from @TreatmentValueSource on use case │
│  Fixed factors remain constant (implicit, no annotation needed) │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│              OptimizationOrchestrator                           │
│                                                                 │
│  Loop:                                                          │
│    1. Build factor suit (fixed factors + current treatment)     │
│    2. Execute use case N times → N outcomes                     │
│    3. Aggregate outcomes → statistics                           │
│    4. Score aggregate → iteration score                         │
│    5. Record iteration in history                               │
│    6. Check termination → stop or continue                      │
│    7. Mutate treatment factor → new value for next iteration   │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│              OptimizationHistory                                │
│                                                                 │
│  Contains:                                                      │
│    - All iterations (factor values, statistics, scores)         │
│    - Best iteration identified                                  │
│    - Termination reason                                         │
│                                                                 │
│  Primary output: the best value of the treatment factor        │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. Core Interfaces

### 4.1 Scorer Interface

**Role**: Evaluates an iteration's aggregate and produces a scalar score. The optimizer uses scores to compare iterations and identify the best factor value.

```java
package org.javai.punit.experiment.optimize;

/**
 * Evaluates an iteration's aggregate and produces a scalar score.
 *
 * The scorer is the automated equivalent of the human evaluator in EXPLORE mode.
 * Where EXPLORE produces specs for human comparison via diff, OPTIMIZE uses
 * the scorer to programmatically determine which factor value performs best.
 *
 * Scores must be comparable:
 *   - For MAXIMIZE objective: higher score = better
 *   - For MINIMIZE objective: lower score = better
 *
 * @param <A> The aggregate type (typically OptimizationIterationAggregate)
 */
@FunctionalInterface
public interface Scorer<A> {

    /**
     * Compute a scalar score for the given aggregate.
     *
     * The aggregate contains:
     *   - All factor values (fixed + treatment) for context
     *   - Statistics aggregated from N outcomes
     *
     * @param aggregate The iteration's aggregate result
     * @return A scalar score for ranking this iteration
     * @throws ScoringException if scoring fails
     */
    double score(A aggregate) throws ScoringException;

    /**
     * Human-readable description for the optimization history.
     */
    default String description() {
        return this.getClass().getSimpleName();
    }
}

/**
 * Thrown when scoring fails. The iteration is marked as failed.
 */
public class ScoringException extends Exception {
    public ScoringException(String message) { super(message); }
    public ScoringException(String message, Throwable cause) { super(message, cause); }
}
```

### 4.2 Standard Scorer Implementations

```java
/**
 * Scores by success rate.
 *
 * The simplest and most common scorer. Use with MAXIMIZE objective.
 * A success rate of 0.95 scores higher than 0.90.
 */
public class SuccessRateScorer implements Scorer<OptimizationIterationAggregate> {

    @Override
    public double score(OptimizationIterationAggregate aggregate) {
        return aggregate.statistics().successRate();
    }

    @Override
    public String description() {
        return "Success rate (higher is better)";
    }
}

/**
 * Scores by cost efficiency: success rate per 1000 tokens.
 *
 * Balances accuracy against token consumption. Useful when optimizing
 * for both quality and cost. Use with MAXIMIZE objective.
 */
public class CostEfficiencyScorer implements Scorer<OptimizationIterationAggregate> {

    @Override
    public double score(OptimizationIterationAggregate aggregate) {
        double successRate = aggregate.statistics().successRate();
        long tokens = aggregate.statistics().totalTokens();
        if (tokens == 0) return 0.0;
        return (successRate * 1000.0) / tokens;
    }

    @Override
    public String description() {
        return "Cost efficiency: success per 1k tokens";
    }
}

/**
 * Combines multiple scorers with configurable weights.
 *
 * Example: 70% success rate + 30% cost efficiency
 *
 * Use when optimizing for multiple objectives simultaneously.
 */
public class WeightedScorer implements Scorer<OptimizationIterationAggregate> {

    private final List<WeightedComponent> components;

    public WeightedScorer(WeightedComponent... components) {
        this.components = List.of(components);
    }

    @Override
    public double score(OptimizationIterationAggregate aggregate) throws ScoringException {
        double totalScore = 0.0;
        double totalWeight = 0.0;

        for (WeightedComponent c : components) {
            totalScore += c.scorer().score(aggregate) * c.weight();
            totalWeight += c.weight();
        }

        return totalWeight > 0 ? totalScore / totalWeight : 0.0;
    }

    @Override
    public String description() {
        return components.stream()
            .map(c -> String.format("%.0f%% %s", c.weight() * 100, c.scorer().description()))
            .collect(Collectors.joining(" + "));
    }

    public record WeightedComponent(Scorer<OptimizationIterationAggregate> scorer, double weight) {}
}
```

### 4.3 FactorMutator Interface

**Role**: Generates a new value for the treatment factor based on history. The mutator implements the search strategy for exploring the factor's value space.

```java
package org.javai.punit.experiment.optimize;

/**
 * Generates new values for the treatment factor.
 *
 * The mutator implements the search strategy—how to explore the space
 * of possible values for the factor being optimized. Strategies range
 * from simple (random perturbation) to sophisticated (LLM-based rewriting).
 *
 * The mutator only changes the DESIGNATED factor. All other factors
 * remain constant throughout optimization.
 *
 * @param <F> The type of the factor being optimized (String, Image, etc.)
 */
@FunctionalInterface
public interface FactorMutator<F> {

    /**
     * Generate a new value for the treatment factor.
     *
     * @param currentValue The current value of the treatment factor
     * @param history Read-only access to optimization history
     * @return A new value to try in the next iteration
     * @throws MutationException if mutation fails (optimization terminates)
     */
    F mutate(F currentValue, OptimizationHistory history) throws MutationException;

    /**
     * Human-readable description for the optimization history.
     */
    default String description() {
        return this.getClass().getSimpleName();
    }

    /**
     * Validate a factor value before use.
     * Override to add constraints (e.g., max length, content filters).
     *
     * @throws MutationException if the value violates constraints
     */
    default void validate(F value) throws MutationException {
        // No validation by default
    }
}

/**
 * Thrown when mutation fails. Optimization terminates with partial results.
 */
public class MutationException extends Exception {
    public MutationException(String message) { super(message); }
    public MutationException(String message, Throwable cause) { super(message, cause); }
}
```

### 4.4 Standard FactorMutator Implementations

```java
/**
 * LLM-based mutator for string factors (e.g., system prompts).
 *
 * Uses an LLM to suggest improved versions of the factor value based on:
 *   - The current value
 *   - Recent iteration results (scores, failure patterns)
 *
 * This is the primary mutator for system prompt optimization.
 */
public class LLMStringFactorMutator implements FactorMutator<String> {

    private final LLMClient llmClient;
    private final String mutationPromptTemplate;
    private final int maxLength;

    /**
     * @param llmClient Client for the LLM that generates mutations
     * @param mutationPromptTemplate Template with placeholders for current value and feedback
     * @param maxLength Maximum allowed length for the factor value
     */
    public LLMStringMutator(LLMClient llmClient, String mutationPromptTemplate, int maxLength) {
        this.llmClient = llmClient;
        this.mutationPromptTemplate = mutationPromptTemplate;
        this.maxLength = maxLength;
    }

    @Override
    public String mutate(String currentValue, OptimizationHistory history) throws MutationException {
        // Build feedback from recent iterations
        String feedback = formatRecentIterations(history.lastNIterations(3));

        // Ask LLM to suggest an improved value
        String prompt = String.format(mutationPromptTemplate, currentValue, feedback);

        try {
            return llmClient.complete(prompt);
        } catch (Exception e) {
            throw new MutationException("LLM mutation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void validate(String value) throws MutationException {
        if (value == null || value.isBlank()) {
            throw new MutationException("Factor value cannot be empty");
        }
        if (value.length() > maxLength) {
            throw new MutationException(
                String.format("Factor value exceeds max length %d (was %d)", maxLength, value.length())
            );
        }
    }

    private String formatRecentIterations(List<OptimizationRecord> iterations) {
        StringBuilder sb = new StringBuilder();
        for (OptimizationRecord iter : iterations) {
            sb.append(String.format("Iteration %d: score=%.3f, successRate=%.2f%%\n",
                iter.iterationNumber(),
                iter.score(),
                iter.aggregate().statistics().successRate() * 100
            ));
        }
        return sb.toString();
    }

    @Override
    public String description() {
        return "LLM-based string mutator (max length: " + maxLength + ")";
    }
}

/**
 * No-op mutator for testing. Returns the value unchanged.
 */
public class NoOpFactorMutator<F> implements FactorMutator<F> {

    @Override
    public F mutate(F currentValue, OptimizationHistory history) {
        return currentValue;
    }

    @Override
    public String description() {
        return "No-op (value unchanged)";
    }
}
```

### 4.5 OptimizationTerminationPolicy Interface

**Role**: Determines when the optimization loop should stop.

```java
package org.javai.punit.experiment.optimize;

/**
 * Determines when optimization should terminate.
 *
 * Common termination conditions:
 *   - Maximum iterations reached
 *   - No improvement for N consecutive iterations
 *   - Budget exhausted (time, tokens, cost)
 *   - Score threshold achieved
 *
 * Policies are composable via OptimizationCompositeTerminationPolicy.
 */
@FunctionalInterface
public interface OptimizationTerminationPolicy {

    /**
     * Check if optimization should terminate.
     *
     * @param history Current optimization history
     * @return Termination reason if should stop, empty to continue
     */
    Optional<OptimizationTerminationReason> shouldTerminate(OptimizationHistory history);

    /**
     * Human-readable description for the optimization history.
     */
    default String description() {
        return this.getClass().getSimpleName();
    }
}

/**
 * Reason for termination, included in final history.
 */
public record OptimizationTerminationReason(
    TerminationReason cause,
    String message
) {
    // TerminationReason is an enum defined in org.javai.punit.model
}

/**
 * Combines multiple policies. Terminates when ANY policy triggers.
 */
public class OptimizationCompositeTerminationPolicy implements OptimizationTerminationPolicy {

    private final List<OptimizationTerminationPolicy> policies;

    public OptimizationCompositeTerminationPolicy(OptimizationTerminationPolicy... policies) {
        this.policies = List.of(policies);
    }

    @Override
    public Optional<OptimizationTerminationReason> shouldTerminate(OptimizationHistory history) {
        for (OptimizationTerminationPolicy policy : policies) {
            Optional<OptimizationTerminationReason> reason = policy.shouldTerminate(history);
            if (reason.isPresent()) {
                return reason;
            }
        }
        return Optional.empty();
    }

    @Override
    public String description() {
        return policies.stream()
            .map(OptimizationTerminationPolicy::description)
            .collect(Collectors.joining(" OR "));
    }
}
```

### 4.6 Standard OptimizationTerminationPolicy Implementations

```java
/**
 * Terminates after a fixed number of iterations.
 */
public class OptimizationMaxIterationsPolicy implements OptimizationTerminationPolicy {

    private final int maxIterations;

    public OptimizationMaxIterationsPolicy(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    @Override
    public Optional<OptimizationTerminationReason> shouldTerminate(OptimizationHistory history) {
        if (history.iterationCount() >= maxIterations) {
            return Optional.of(OptimizationTerminationReason.maxIterations(maxIterations));
        }
        return Optional.empty();
    }

    @Override
    public String description() {
        return "Max " + maxIterations + " iterations";
    }
}

/**
 * Terminates if no improvement in the last N iterations.
 */
public class OptimizationNoImprovementPolicy implements OptimizationTerminationPolicy {

    private final int windowSize;

    public OptimizationNoImprovementPolicy(int windowSize) {
        this.windowSize = windowSize;
    }

    @Override
    public Optional<OptimizationTerminationReason> shouldTerminate(OptimizationHistory history) {
        if (history.iterationCount() <= windowSize) {
            return Optional.empty();
        }

        int iterationsSinceBest = history.iterationCount() -
                                  history.bestIteration().get().iterationNumber() - 1;

        if (iterationsSinceBest >= windowSize) {
            return Optional.of(OptimizationTerminationReason.noImprovement(windowSize));
        }
        return Optional.empty();
    }

    @Override
    public String description() {
        return "No improvement for " + windowSize + " iterations";
    }
}

/**
 * Terminates when time budget is exhausted.
 */
public class OptimizationTimeBudgetPolicy implements OptimizationTerminationPolicy {

    private final Duration maxDuration;

    public OptimizationTimeBudgetPolicy(Duration maxDuration) {
        this.maxDuration = maxDuration;
    }

    @Override
    public Optional<OptimizationTerminationReason> shouldTerminate(OptimizationHistory history) {
        if (history.totalDuration().compareTo(maxDuration) >= 0) {
            return Optional.of(OptimizationTerminationReason.timeBudgetExhausted(maxDuration));
        }
        return Optional.empty();
    }

    @Override
    public String description() {
        return "Time budget: " + maxDuration;
    }
}
```

---

## 5. Data Models

### 5.1 FactorSuit

**Role**: A complete set of factor values for a use case instance. Replaces the vague term "configuration".

```java
package org.javai.punit.experiment.optimize;

/**
 * A complete set of factor values for a use case.
 *
 * The term "FactorSuit" (like a suit of cards or suit of armor) captures the
 * concept of a complete matching set of factors. It replaces the vague term
 * "configuration" which could mean many things.
 *
 * FactorSuit is immutable. Use `with()` to create a modified copy.
 */
public record FactorSuit(Map<String, Object> values) {

    public FactorSuit {
        values = Map.copyOf(values);  // Defensive copy, immutable
    }

    /**
     * Get a factor value by name.
     */
    @SuppressWarnings("unchecked")
    public <F> F get(String factorName) {
        return (F) values.get(factorName);
    }

    /**
     * Create a new FactorSuit with one factor value changed.
     */
    public FactorSuit with(String factorName, Object value) {
        Map<String, Object> newValues = new HashMap<>(values);
        newValues.put(factorName, value);
        return new FactorSuit(newValues);
    }

    /**
     * Create a FactorSuit from a map.
     */
    public static FactorSuit of(Map<String, Object> values) {
        return new FactorSuit(values);
    }

    /**
     * Create a FactorSuit from key-value pairs.
     */
    public static FactorSuit of(Object... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Must provide key-value pairs");
        }
        Map<String, Object> values = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            values.put((String) keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return new FactorSuit(values);
    }
}
```

### 5.2 OptimizationIterationAggregate

**Role**: The result of one MEASURE-like execution within the optimization loop. Contains the complete factor suit plus aggregated statistics.

```java
package org.javai.punit.experiment.optimize;

/**
 * Aggregate result of one optimization iteration.
 *
 * This is analogous to what a single MEASURE experiment produces:
 * statistics aggregated from N outcomes with one factor suit.
 *
 * The aggregate includes ALL factor values (fixed + treatment) so that:
 *   1. The scorer has full context for evaluation
 *   2. The history is self-describing and auditable
 *   3. Each iteration can be understood in isolation
 */
public record OptimizationIterationAggregate(

    /** 0-indexed iteration number */
    int iterationNumber,

    /**
     * The complete factor suit for this iteration.
     *
     * Includes both fixed factors (constant across iterations) and the
     * treatment factor (which changes each iteration).
     */
    FactorSuit factorSuit,

    /**
     * The name of the factor being optimized.
     *
     * This factor's value in factorValues was set by the FactorMutator.
     * All other factors were held constant.
     */
    String treatmentFactorName,

    /**
     * Statistics aggregated from N outcomes.
     */
    OptimizationStatistics statistics,

    /** When this iteration started */
    Instant startTime,

    /** When this iteration completed */
    Instant endTime
) {
    /**
     * Convenience method to get the current value of the treatment factor.
     */
    @SuppressWarnings("unchecked")
    public <F> F treatmentFactorValue() {
        return (F) factorSuit.get(treatmentFactorName);
    }

    /**
     * Duration of this iteration.
     */
    public Duration duration() {
        return Duration.between(startTime, endTime);
    }
}

/**
 * Statistics aggregated from N outcomes.
 *
 * These are the metrics available to the Scorer for evaluation.
 */
public record OptimizationStatistics(
    /** Number of outcomes aggregated */
    int sampleCount,

    /** Proportion of successful outcomes (0.0 to 1.0) */
    double successRate,

    /** Total tokens consumed across all outcomes */
    long totalTokens,

    /** Mean latency in milliseconds */
    double meanLatencyMs,

    /** Count of successful outcomes */
    int successCount,

    /** Count of failed outcomes */
    int failureCount
) {}
```

### 5.3 OptimizationRecord

**Role**: A scored iteration for the optimization history.

```java
package org.javai.punit.experiment.optimize;

/**
 * A scored iteration record stored in the optimization history.
 *
 * Combines the aggregate (what was evaluated) with the score (how good it was).
 */
public record OptimizationRecord(
    /** The iteration's aggregate result */
    OptimizationIterationAggregate aggregate,

    /** Score computed by the Scorer */
    double score,

    /** Whether this iteration succeeded or failed */
    OptimizationStatus status,

    /** Failure reason if status != SUCCESS */
    Optional<String> failureReason
) {
    public int iterationNumber() {
        return aggregate.iterationNumber();
    }

    public boolean isSuccessful() {
        return status == OptimizationStatus.SUCCESS;
    }
}

public enum OptimizationStatus {
    SUCCESS,
    EXECUTION_FAILED,
    SCORING_FAILED
}
```

### 5.4 OptimizationHistory

**Role**: Complete audit trail and primary output of OPTIMIZE mode.

```java
package org.javai.punit.experiment.optimize;

/**
 * Complete history of an optimization run.
 *
 * This is the primary output of OPTIMIZE mode. Unlike:
 *   - MEASURE: produces a baseline document (statistics)
 *   - EXPLORE: produces specs for human comparison via diff
 *
 * OPTIMIZE produces:
 *   - The best value found for the treatment factor (primary output)
 *   - Full iteration history (for auditability and analysis)
 *   - Termination reason (why optimization stopped)
 *
 * The history is written to YAML and can be used to:
 *   - Extract the best factor value for production use
 *   - Analyse the optimization trajectory
 *   - Debug why certain mutations helped or hurt
 */
public final class OptimizationHistory {

    // === Identification ===

    private final String useCaseId;
    private final String experimentId;

    // === Factor Suit ===

    /** Name of the factor being optimized */
    private final String treatmentFactorName;

    /** Type of the treatment factor (for documentation) */
    private final String treatmentFactorType;

    /** Fixed factor suit: factors held constant throughout optimization */
    private final FactorSuit fixedFactors;

    /** Optimization objective */
    private final OptimizationObjective objective;

    /** Descriptions of scorer, mutator, termination policy */
    private final String scorerDescription;
    private final String mutatorDescription;
    private final String terminationPolicyDescription;

    // === Timing ===

    private final Instant startTime;
    private final Instant endTime;
    private final Duration totalDuration;

    // === Iterations ===

    private final List<OptimizationRecord> iterations;

    // === Results ===

    /** The iteration with the best score */
    private final OptimizationRecord bestIteration;

    /** Why optimization terminated */
    private final OptimizationTerminationReason terminationReason;

    // === Query Methods ===

    public int iterationCount() {
        return iterations.size();
    }

    public List<OptimizationRecord> lastNIterations(int n) {
        int start = Math.max(0, iterations.size() - n);
        return iterations.subList(start, iterations.size());
    }

    /**
     * The primary output: the best value found for the treatment factor.
     */
    @SuppressWarnings("unchecked")
    public <F> F bestFactorValue() {
        return (F) bestIteration.aggregate().factorSuit().get(treatmentFactorName);
    }

    /**
     * Score improvement from first to best iteration.
     */
    public double scoreImprovement() {
        if (iterations.isEmpty()) return 0.0;
        double initial = iterations.get(0).score();
        double best = bestIteration.score();
        return best - initial;
    }

    /**
     * Score improvement as a percentage.
     */
    public double scoreImprovementPercent() {
        if (iterations.isEmpty()) return 0.0;
        double initial = iterations.get(0).score();
        if (initial == 0.0) return 0.0;
        return (scoreImprovement() / Math.abs(initial)) * 100.0;
    }

    // Builder pattern for construction...
}

public enum OptimizationObjective {
    MAXIMIZE,
    MINIMIZE
}
```

---

## 6. Orchestration

### 6.1 OptimizationOrchestrator

**Role**: Executes the optimization loop.

```java
package org.javai.punit.experiment.optimize;

/**
 * Executes the OPTIMIZE experiment loop.
 *
 * The loop is conceptually simple:
 *   1. Execute use case N times (like MEASURE)
 *   2. Aggregate outcomes
 *   3. Score the aggregate
 *   4. Record in history
 *   5. Check termination
 *   6. Mutate the treatment factor
 *   7. Repeat
 *
 * The orchestrator coordinates these steps but delegates the actual
 * work to the Scorer, Mutator, and TerminationPolicy.
 */
public class OptimizationOrchestrator<F> {

    private final OptimizationConfig<F> config;
    private final UseCaseExecutor executor;
    private final OptimizationOutcomeAggregator aggregator;

    /**
     * Execute the full optimization loop.
     *
     * @return Complete optimization history including best factor value
     */
    public OptimizationHistory run() {

        OptimizationHistory.Builder historyBuilder = OptimizationHistory.builder()
            .useCaseId(config.useCaseId())
            .experimentId(config.experimentId())
            .treatmentFactorName(config.treatmentFactorName())
            .fixedFactors(config.fixedFactors())
            .objective(config.objective())
            .scorerDescription(config.scorer().description())
            .mutatorDescription(config.mutator().description())
            .terminationPolicyDescription(config.terminationPolicy().description())
            .startTime(Instant.now());

        F currentFactorValue = config.initialFactorValue();
        int iteration = 0;

        while (true) {
            Instant iterStart = Instant.now();

            // 1. Build complete factor suit for this iteration
            FactorSuit factorSuit = config.fixedFactors()
                .with(config.treatmentFactorName(), currentFactorValue);

            // 2. Execute use case N times (like MEASURE)
            List<UseCaseOutcome> outcomes = executor.execute(
                factorSuit,
                config.samplesPerIteration()
            );

            // 3. Aggregate outcomes
            OptimizationStatistics statistics = aggregator.aggregate(outcomes);

            OptimizationIterationAggregate aggregate = new OptimizationIterationAggregate(
                iteration,
                factorSuit,
                config.treatmentFactorName(),
                statistics,
                iterStart,
                Instant.now()
            );

            // 4. Score the aggregate
            double score;
            try {
                score = config.scorer().score(aggregate);
            } catch (ScoringException e) {
                OptimizationRecord failed = OptimizationRecord.scoringFailed(
                    aggregate, e.getMessage()
                );
                historyBuilder.addIteration(failed);
                return historyBuilder
                    .endTime(Instant.now())
                    .terminationReason(OptimizationTerminationReason.scoringFailure(e.getMessage()))
                    .build();
            }

            // 5. Record in history
            OptimizationRecord record = OptimizationRecord.success(aggregate, score);
            historyBuilder.addIteration(record);

            // 6. Check termination
            OptimizationHistory currentHistory = historyBuilder.buildPartial();
            Optional<OptimizationTerminationReason> termination =
                config.terminationPolicy().shouldTerminate(currentHistory);

            if (termination.isPresent()) {
                return historyBuilder
                    .endTime(Instant.now())
                    .terminationReason(termination.get())
                    .build();
            }

            // 7. Mutate treatment factor for next iteration
            try {
                currentFactorValue = config.mutator().mutate(currentFactorValue, currentHistory);
                config.mutator().validate(currentFactorValue);
            } catch (MutationException e) {
                return historyBuilder
                    .endTime(Instant.now())
                    .terminationReason(OptimizationTerminationReason.mutationFailure(e.getMessage()))
                    .build();
            }

            iteration++;
        }
    }
}
```

---

## 7. JUnit Integration

### 7.1 Annotation Design Decision: Separate Annotations

We chose to implement **three separate annotations** rather than one unified `@Experiment`:

- `@MeasureExperiment`
- `@ExploreExperiment`
- `@OptimizeExperiment`

**Rationale:**
- OPTIMIZE has 7 unique attributes (50% of total), making a unified annotation cluttered
- Separate annotations provide compile-time validation for required fields
- Immediate clarity of experiment purpose from the annotation name
- Each mode has distinct semantics and outputs

### 7.2 @OptimizeExperiment Annotation

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@TestTemplate
@ExtendWith(ExperimentExtension.class)
public @interface OptimizeExperiment {

    /** The use case class to execute. */
    Class<?> useCase() default Void.class;

    /** Name of the factor to optimize (treatment factor). Required. */
    String treatmentFactor();

    /** Scorer class for evaluating iteration aggregates. Required. */
    Class<? extends Scorer<OptimizationIterationAggregate>> scorer();

    /** FactorMutator class for generating new treatment factor values. Required. */
    Class<? extends FactorMutator<?>> mutator();

    /** Optimization objective: MAXIMIZE or MINIMIZE. Default: MAXIMIZE. */
    OptimizationObjective objective() default OptimizationObjective.MAXIMIZE;

    /** Number of samples per iteration. Default: 20. */
    int samplesPerIteration() default 20;

    /** Maximum number of iterations before termination. Default: 20. */
    int maxIterations() default 20;

    /** Consecutive iterations without improvement before termination. Default: 5. */
    int noImprovementWindow() default 5;

    /** Maximum wall-clock time budget in milliseconds. 0 = unlimited. */
    long timeBudgetMs() default 0;

    /** Maximum token budget across all iterations. 0 = unlimited. */
    long tokenBudget() default 0;

    /** Unique identifier for this experiment. */
    String experimentId() default "";
}
```

### 7.3 Design Decision: No @FixedFactors Annotation

An earlier design included a `@FixedFactors` annotation to explicitly specify which factors should remain constant during optimization. **This was removed.**

**Rationale:**
- **Superfluous ceremony**: The developer already specifies the treatment factor via `treatmentFactor`. Everything else implicitly stays constant.
- **Developer simplicity**: Less annotations = less cognitive load. The principle is to give developers as little to do as possible.
- **Trust the developer**: Yes, a developer *could* inadvertently change other factors, undermining the experiment. But developers can do many things to undermine an experiment—explicit guards for this one scenario add complexity without proportionate benefit.

**Original (discarded):**
```java
@OptimizeExperiment(treatmentFactor = "systemPrompt", ...)
@FixedFactors("shoppingFactors")  // <-- Removed
void optimize(...) { }
```

**Current (simpler):**
```java
@OptimizeExperiment(treatmentFactor = "systemPrompt", ...)
void optimize(...) { }
// Fixed factors come from the use case instance itself
```

### 7.4 Initial Treatment Value: @TreatmentValue + @TreatmentValueSource

The initial value for the treatment factor is provided via a two-part pattern:

1. **@TreatmentValueSource** - Marks a method on the use case class as the source of the initial value
2. **@TreatmentValue** - Marks a parameter on the experiment method to receive the injected value

**Why this pattern?**

1. **Programmatic flexibility**: Initial values may need to be computed, loaded from files, or derived from use case state. A static annotation value (`initialValue = "..."`) cannot accommodate this.

2. **Type safety**: The value flows through Java methods, preserving type information. Works for any factor type (String, double, Image, etc.).

3. **Use case encapsulation**: The use case owns its factors. It's natural for the use case to also provide the initial treatment value.

4. **Instance vs static**: The `@TreatmentValueSource` method is an instance method on the use case, allowing access to use case state.

**Use Case Definition:**
```java
@UseCase
public class ShoppingUseCase {

    private String systemPrompt;

    @TreatmentValueSource("systemPrompt")
    public String getSystemPrompt() {
        return this.systemPrompt;
    }

    @FactorSetter("systemPrompt")
    public void setSystemPrompt(String prompt) {
        this.systemPrompt = prompt;
    }
}
```

**Experiment Method:**
```java
@OptimizeExperiment(
    useCase = ShoppingUseCase.class,
    treatmentFactor = "systemPrompt",
    scorer = SuccessRateScorer.class,
    mutator = LLMStringFactorMutator.class
)
void optimizeSystemPrompt(
    ShoppingUseCase useCase,
    @TreatmentValue String initialPrompt,  // Injected from use case
    ResultCaptor captor
) {
    captor.record(useCase.searchProducts("wireless headphones"));
}
```

### 7.5 Future-Proofing: Multi-Factor Optimization

The `@TreatmentValue` annotation includes an optional `value()` parameter for future multi-factor support:

**Current (single factor - common case):**
```java
@TreatmentValue String initialPrompt  // Factor name inferred from treatmentFactor
```

**Future (multiple factors):**
```java
@TreatmentValue("systemPrompt") String initialPrompt,
@TreatmentValue("temperature") double initialTemp
```

This allows us to add multi-factor optimization without breaking the existing single-factor API.

---

## 8. Output and Reporting

### 8.1 Reporting via PUnitReporter

OPTIMIZE mode outputs its results via `PUnitReporter`, consistent with MEASURE and EXPLORE modes. This ensures uniform formatting and logging style across the framework.

**Verbosity Levels:**

| Level       | Output                                                                |
|-------------|-----------------------------------------------------------------------|
| **MINIMAL** | Treatment factor name + best value only                               |
| **SUMMARY** | Best value + score improvement + iteration count + termination reason |
| **FULL**    | Complete optimization history with all iterations                     |

**Example: MINIMAL verbosity**
```
╔══════════════════════════════════════════════════════════════════╗
║  OPTIMIZE: shopping-assistant / optimize-system-prompt           ║
╠══════════════════════════════════════════════════════════════════╣
║  Treatment factor: systemPrompt                                  ║
║  Best value:                                                     ║
║    You are a knowledgeable shopping assistant. When helping      ║
║    users:                                                        ║
║    1. Consider their stated budget                               ║
║    2. Prioritize highly-rated products                           ║
║    3. Mention key specifications relevant to their query         ║
╚══════════════════════════════════════════════════════════════════╝
```

**Example: SUMMARY verbosity**
```
╔══════════════════════════════════════════════════════════════════╗
║  OPTIMIZE: shopping-assistant / optimize-system-prompt           ║
╠══════════════════════════════════════════════════════════════════╣
║  Treatment factor: systemPrompt                                  ║
║  Fixed factors:    model=gpt-4, temperature=0.7                  ║
║  Objective:        MAXIMIZE success rate                         ║
╠══════════════════════════════════════════════════════════════════╣
║  Iterations:       12                                            ║
║  Best iteration:   7                                             ║
║  Score:            0.85 → 0.95 (+11.76%)                         ║
║  Termination:      No improvement in last 5 iterations           ║
╠══════════════════════════════════════════════════════════════════╣
║  Best value:                                                     ║
║    You are a knowledgeable shopping assistant. When helping      ║
║    users:                                                        ║
║    1. Consider their stated budget                               ║
║    2. Prioritize highly-rated products                           ║
║    3. Mention key specifications relevant to their query         ║
╚══════════════════════════════════════════════════════════════════╝
```

### 8.2 Progress Reporting

For long-running optimizations, intermediate progress is reported after each iteration:

```
OPTIMIZE [shopping-assistant] Starting: treatmentFactor=systemPrompt, objective=MAXIMIZE
OPTIMIZE [shopping-assistant] Iteration 0: score=0.85 (best=0.85)
OPTIMIZE [shopping-assistant] Iteration 1: score=0.88 (best=0.88) ↑ NEW BEST
OPTIMIZE [shopping-assistant] Iteration 2: score=0.86 (best=0.88)
OPTIMIZE [shopping-assistant] Iteration 3: score=0.90 (best=0.90) ↑ NEW BEST
OPTIMIZE [shopping-assistant] Iteration 4: score=0.89 (best=0.90)
...
OPTIMIZE [shopping-assistant] Iteration 12: score=0.92 (best=0.95)
OPTIMIZE [shopping-assistant] Terminated: No improvement in last 5 iterations
```

This allows an interested party to monitor progress without waiting for completion.

### 8.3 File Persistence

In addition to log output, the full optimization history is persisted to YAML for auditability and later analysis.

**Directory Structure:**
```
src/test/resources/punit/
├── specs/                              # MEASURE outputs
│   └── {useCaseId}.yaml
├── explorations/                       # EXPLORE outputs
│   └── {useCaseId}/
│       └── {configName}.yaml
└── optimizations/                      # OPTIMIZE outputs
    └── {useCaseId}/
        └── {experimentId}_YYYYMMDD_HHMMSS.yaml
```

### 8.4 YAML Output Example

```yaml
# Optimization History
# Primary output: the best value for the treatment factor

useCaseId: "shopping-assistant"
experimentId: "optimize-system-prompt"

# What was optimized
treatmentFactorName: "systemPrompt"
treatmentFactorType: "String"

# Fixed factor suit (selected during EXPLORE phase)
fixedFactors:
  model: "gpt-4"
  temperature: 0.7

# Optimization settings
objective: MAXIMIZE
scorerDescription: "Success rate (higher is better)"
mutatorDescription: "LLM-based string mutator (max length: 2000)"
terminationPolicyDescription: "Max 20 iterations OR No improvement for 5 iterations"

# Timing
startTime: "2026-01-16T10:30:00Z"
endTime: "2026-01-16T10:45:30Z"
totalDurationMs: 930000

# Iterations
iterations:
  - iterationNumber: 0
    factorSuit:
      model: "gpt-4"
      temperature: 0.7
      systemPrompt: "You are a helpful shopping assistant."
    statistics:
      sampleCount: 20
      successRate: 0.85
      totalTokens: 9000
      meanLatencyMs: 1200
    score: 0.85
    status: SUCCESS

  - iterationNumber: 1
    factorSuit:
      model: "gpt-4"
      temperature: 0.7
      systemPrompt: |
        You are a helpful shopping assistant. Consider the user's
        budget and preferences when making recommendations.
    statistics:
      sampleCount: 20
      successRate: 0.90
      totalTokens: 9500
      meanLatencyMs: 1250
    score: 0.90
    status: SUCCESS

  # ... more iterations ...

# Primary result: best iteration found
bestIteration:
  iterationNumber: 7
  factorValues:
    model: "gpt-4"
    temperature: 0.7
    systemPrompt: |
      You are a knowledgeable shopping assistant. When helping users:
      1. Consider their stated budget
      2. Prioritize highly-rated products
      3. Mention key specifications relevant to their query
  statistics:
    sampleCount: 20
    successRate: 0.95
    totalTokens: 10200
    meanLatencyMs: 1180
  score: 0.95

# Why optimization stopped
terminationReason:
  cause: NO_IMPROVEMENT
  message: "No improvement in last 5 iterations"

# Summary
scoreImprovement: 0.10
scoreImprovementPercent: 11.76
totalIterations: 12
```

### 8.5 Auditability and Decision Replay

**Clarification on Deterministic Replay** (see OPTIMIZE-REQ.md requirement #8)

Given that PUnit operates on non-deterministic systems (LLMs), "deterministic replay" cannot mean re-executing experiments and expecting identical outcomes. Instead, OPTIMIZE mode provides **decision replay**: the ability to verify that the optimization logic (scoring, mutation selection, termination decisions) was correctly applied given the recorded outcomes.

**What is achievable:**
- Replay the scoring logic on recorded aggregates to verify scores
- Replay the termination policy on recorded history to verify it would have made the same decisions
- Verify the optimization path was correct given the recorded data

**What is not achievable:**
- Re-executing the actual use case and expecting identical outcomes
- Reproducing the exact same LLM responses

The persisted YAML history (Section 8.4) contains all data necessary for decision replay:
- Complete factor suits for each iteration
- Aggregated statistics from outcomes
- Computed scores
- Termination reason

This enables:
1. **Audit**: Verify the scorer and termination policy behaved correctly
2. **Analysis**: Understand why certain mutations improved or degraded performance
3. **Comparison**: Compare optimization runs with different scorers or mutators (on new runs, not replayed outcomes)

---

## 9. Usage Example

### 9.1 Use Case Definition

The use case encapsulates the factors and provides the initial treatment value:

```java
@UseCase
public class ShoppingUseCase {

    // Fixed factors (set during construction, constant throughout optimization)
    private final String model;
    private final double temperature;

    // Treatment factor (will be mutated during optimization)
    private String systemPrompt;

    public ShoppingUseCase() {
        // Fixed factors selected during EXPLORE phase
        this.model = "gpt-4";
        this.temperature = 0.7;
        // Initial value for treatment factor
        this.systemPrompt = "You are a helpful shopping assistant.";
    }

    /**
     * Provides the initial value for the treatment factor.
     * Called once at the start of optimization.
     */
    @TreatmentValueSource("systemPrompt")
    public String getSystemPrompt() {
        return this.systemPrompt;
    }

    /**
     * Sets the treatment factor value.
     * Called by the extension before each iteration with the mutated value.
     */
    @FactorSetter("systemPrompt")
    public void setSystemPrompt(String prompt) {
        this.systemPrompt = prompt;
    }

    public UseCaseOutcome searchProducts(String query) {
        // Use case implementation using model, temperature, systemPrompt
        // ...
    }
}
```

### 9.2 Experiment Definition

```java
public class ShoppingOptimizationExperiment {

    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    @BeforeEach
    void setUp() {
        provider.register(ShoppingUseCase.class, () -> new ShoppingUseCase());
    }

    /**
     * Optimize the systemPrompt factor for the shopping assistant.
     *
     * Prerequisites:
     *   - EXPLORE completed: selected gpt-4 @ temperature 0.7
     *   - Initial systemPrompt provided by use case via @TreatmentValueSource
     *
     * This will:
     *   1. Run 20 samples per iteration (like MEASURE)
     *   2. Score by success rate
     *   3. Use LLM to suggest improved prompts
     *   4. Repeat until no improvement for 5 iterations
     */
    @OptimizeExperiment(
        useCase = ShoppingUseCase.class,
        experimentId = "optimize-system-prompt",

        // Which factor to optimize
        treatmentFactor = "systemPrompt",

        // How to evaluate
        scorer = SuccessRateScorer.class,
        objective = OptimizationObjective.MAXIMIZE,

        // How to mutate
        mutator = LLMStringFactorMutator.class,

        // Execution parameters
        samplesPerIteration = 20,
        maxIterations = 20,
        noImprovementWindow = 5,

        // Budgets
        timeBudgetMs = 3600000,
        tokenBudget = 500000
    )
    void optimizeSystemPrompt(
        ShoppingUseCase useCase,
        @TreatmentValue String initialPrompt,  // Injected from use case
        ResultCaptor captor
    ) {
        // This method body executes once per sample within each iteration.
        // Factor values (including the treatment factor) are already applied.
        UseCaseOutcome outcome = useCase.searchProducts("wireless headphones");
        captor.record(outcome);
    }
}
```

---

## 10. Future Enhancements

### 10.1 Multiple Treatment Factors

The current design supports a single treatment factor. A natural extension would be to allow multiple treatment factors, enabling N-dimensional optimization.

**Current (single treatment factor):**
```java
@OptimizeExperiment(
    treatmentFactor = "systemPrompt",
    ...
)
void optimize(
    ShoppingUseCase useCase,
    @TreatmentValue String initialPrompt,
    ResultCaptor captor
) { ... }
```

**Future (multiple treatment factors):**
```java
@OptimizeExperiment(
    treatmentFactors = {"systemPrompt", "temperature"},
    ...
)
void optimize(
    ShoppingUseCase useCase,
    @TreatmentValue("systemPrompt") String initialPrompt,
    @TreatmentValue("temperature") double initialTemp,
    ResultCaptor captor
) { ... }
```

Note: The `@TreatmentValue` annotation already supports the optional `value()` parameter for this purpose, providing forward compatibility.

**Implications:**
- The mutator interface would change from `FactorMutator<F>` (typed to factor type) to `FactorMutator` operating on `FactorSuit`
- Search becomes N-dimensional, requiring different strategies (grid search, Bayesian optimization, genetic algorithms)
- Single treatment factor is an edge case of this general model
- Captures interaction effects between factors (e.g., prompt style × temperature)

**Deferred because:**
- Single treatment factor covers the primary use case (system prompt optimization)
- Multi-dimensional optimization adds significant complexity
- Can be added later without breaking changes to the single-factor API (annotation already supports it)

---

## 11. Summary

OPTIMIZE mode is:

1. **Conceptually**: MEASURE in a loop, mutating one treatment factor between iterations

2. **Distinct from MEASURE/EXPLORE**: Different output (optimized factor value, not baseline or diff-comparable specs)

3. **Single treatment factor**: Fixed factors constant, only the treatment factor changes (multi-factor is a future enhancement)

4. **Factor-agnostic**: The treatment factor can be any type (string, image, audio, etc.)

5. **Automated evaluation**: Scorer replaces the human evaluator from EXPLORE

The key insight: the parameter being optimized is just another factor—one we choose to vary via mutation rather than holding constant.

### Key Design Decisions

| Decision                | Choice                                      | Rationale                                                                                            |
|-------------------------|---------------------------------------------|------------------------------------------------------------------------------------------------------|
| Annotation style        | Separate `@OptimizeExperiment`              | OPTIMIZE has 50% unique attributes; separate annotation provides clarity and compile-time validation |
| Fixed factors           | Implicit (no annotation)                    | Developer specifies treatment factor; everything else stays constant by default. Minimizes ceremony. |
| Initial value provision | `@TreatmentValueSource` + `@TreatmentValue` | Programmatic flexibility, type safety, use case encapsulation. Works with any factor type.           |
| Multi-factor support    | Deferred but designed for                   | `@TreatmentValue(factorName)` pattern already supports it when needed                                |