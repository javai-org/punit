# OPTIMIZE Mode Requirements

## Overview
OPTIMIZE is a new experiment mode that enables iterative, self-tuning runs of a
target experiment by applying a loss function and a tunable input mutation
between iterations. It is generic across domains, with optional LLM-specific
support available under the `llmx` package for system prompt optimization.

## Goals
- Provide a generic optimization loop that can be applied to any experiment.
- Allow user-defined scoring and input mutation strategies.
- Support termination based on improvement, time, tokens, or iteration caps.
- Preserve auditability and reproducibility of all iterations.
- Offer LLM-specific helpers without coupling core engine to LLMs.

## Non-Goals
- Implementing a specific optimizer algorithm (e.g., Bayesian optimization).
- Guaranteeing convergence or global optima.
- Modifying the semantics of existing EXPLORE/MEASURE modes.

## Definitions
- Target experiment: The experiment that produces results to be optimized.
- Scorer: A function that maps a result (and context) to a scalar score.
- Mutator: A function that takes the current inputs and history and returns
  modified inputs for the next iteration.
- Iteration: One full run of the target experiment under a specific input state.

## User Stories
- As a user, I can define a target experiment and let OPTIMIZE iterate it.
- As a user, I can plug in a custom loss function for scoring.
- As a user, I can cap iterations or stop when improvements stall.
- As a user, I can trace every iteration’s inputs, results, and scores.
- As a user, I can enable LLM-specific prompt tuning without using LLMs
  elsewhere in the system.

## Functional Requirements
1. OPTIMIZE mode must accept a reference to a target experiment.
2. OPTIMIZE mode must execute the target experiment repeatedly, serially.
3. OPTIMIZE mode must call a scorer after each iteration.
4. OPTIMIZE mode must call a mutator to produce inputs for the next iteration.
5. OPTIMIZE mode must terminate when any termination condition is met.
6. OPTIMIZE mode must return the best-scoring iteration and full history.
7. OPTIMIZE mode must enforce global budget limits (time, tokens, cost).
8. OPTIMIZE mode must provide deterministic replay for a recorded history.

## Configuration Requirements
Required fields:
- `mode: OPTIMIZE`
- `targetExperiment`: name or id of the target experiment
- `scorer`: reference to a scoring function or configuration
- `mutator`: reference to an input mutation function or configuration
- `termination`: termination policy definition

Optional fields:
- `objective`: maximize or minimize
- `seed`: deterministic seed for optimization
- `budgets`: time, token, and cost caps
- `historyRetention`: full or summary

## Execution Lifecycle
1. Initialize inputs from target experiment configuration.
2. Run target experiment with current inputs.
3. Score results.
4. Record iteration data and update best-so-far.
5. Check termination conditions.
6. Mutate inputs for the next iteration.
7. Repeat until termination.

## Termination Requirements
Termination policies must support:
- `maxIterations`
- `maxTime`
- `maxTokens` or `maxCost`
- `noImprovementWindow`
- `minDelta` improvement threshold

Termination evaluation must consider the entire history and budgets.

## Scoring Requirements
- Scoring must return a scalar value with explicit objective direction.
- Scorers must be able to access iteration inputs and results.
- Scores must be recorded per iteration.

## Mutation Requirements
- Mutators must accept the current input state and history.
- Mutators must be able to preserve immutable inputs unless explicitly changed.
- Mutators must support domain-specific logic without engine changes.

## Results and Auditability
OPTIMIZE results must include:
- Best iteration inputs, results, and score.
- Full iteration history with timestamps and budgets.
- References to scorer and mutator configurations used.

## Safety and Cost Controls
- All budget limits must be enforced across iterations.
- Failures in scorer or mutator must fail the iteration cleanly with diagnostics.
- The system must prevent unbounded input growth.

## LLM-Specific Support (llmx)
LLMX provides optional adapters for prompt tuning:
- `SystemPromptMutator`: modifies system prompts using a chosen model.
- `PromptScorer`: evaluates outputs using rubrics or eval models.
- `PromptGuardrails`: enforce max length, required anchors, and redaction.

LLMX adapters must implement the generic scorer and mutator interfaces.

## Compatibility
- OPTIMIZE must be additive and not change EXPLORE/MEASURE behavior.
- Target experiments must run without modification.

## Observability
Expose metrics per optimization run:
- Iteration count, time, token usage, score trends.
- Best score progression and termination reason.

## Open Questions
- Should OPTIMIZE support parallel candidate evaluation per iteration?
- How should history be persisted and replayed by default?
- Should there be a standard set of built-in scorers and mutators?

