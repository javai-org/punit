package org.javai.punit.examples.experiments;

import org.javai.punit.examples.infrastructure.llm.ChatLlmProvider;
import org.javai.punit.examples.infrastructure.llm.DeterministicPromptMutationStrategy;
import org.javai.punit.examples.infrastructure.llm.LlmPromptMutationStrategy;
import org.javai.punit.examples.infrastructure.llm.PromptMutationStrategy;
import org.javai.punit.experiment.optimize.FactorMutator;
import org.javai.punit.experiment.optimize.MutationException;
import org.javai.punit.experiment.optimize.OptimizeHistory;

/**
 * Mutator that iteratively improves the system prompt for ShoppingBasketUseCase.
 *
 * <p>This mutator uses a strategy pattern to switch between:
 * <ul>
 *   <li><b>Mock mode</b> ({@link DeterministicPromptMutationStrategy}): A scripted progression
 *       of prompts that demonstrates prompt engineering principles without API calls.</li>
 *   <li><b>Real mode</b> ({@link LlmPromptMutationStrategy}): LLM-powered prompt improvement
 *       that analyzes failure patterns and generates targeted improvements.</li>
 * </ul>
 *
 * <h2>Mode Selection</h2>
 * <p>The strategy is selected based on {@code punit.llm.mode}:
 * <ul>
 *   <li>{@code mock} (default): Uses deterministic progression</li>
 *   <li>{@code real}: Uses LLM-powered improvement</li>
 * </ul>
 *
 * <h2>LLM Mutation Configuration</h2>
 * <p>In real mode, the model used for mutations can be configured separately:
 * <ul>
 *   <li>System property: {@code punit.llm.mutation.model}</li>
 *   <li>Environment variable: {@code PUNIT_LLM_MUTATION_MODEL}</li>
 *   <li>Default: {@code gpt-4o-mini}</li>
 * </ul>
 *
 * <h2>Prompt Evolution (Deterministic Mode)</h2>
 * <pre>
 * Iteration 0 (weak): "You are a shopping assistant..."
 *   → ~30% success: vague, no schema, LLM invents formats
 *
 * Iteration 1: + JSON-only response format
 *   → ~50% success: no more prose mixed with JSON
 *
 * Iteration 2: + explicit schema with operations array
 *   → ~65% success: correct structure, but field errors
 *
 * Iteration 3: + required fields (action, item, quantity)
 *   → ~80% success: correct fields, but invalid values
 *
 * Iteration 4: + valid action enumeration (add/remove/clear)
 *   → ~90% success: valid actions, occasional quantity issues
 *
 * Iteration 5: + quantity constraints (positive integers)
 *   → ~95% success: robust prompt, rare edge case failures
 * </pre>
 *
 * @see DeterministicPromptMutationStrategy
 * @see LlmPromptMutationStrategy
 * @see org.javai.punit.experiment.optimize.FactorMutator
 */
public class ShoppingBasketPromptMutator implements FactorMutator<String> {

    private final PromptMutationStrategy strategy;

    /**
     * Creates a mutator with the strategy selected based on LLM mode.
     *
     * <p>In mock mode, uses deterministic progression. In real mode, uses
     * LLM-powered improvement.
     */
    public ShoppingBasketPromptMutator() {
        this.strategy = ChatLlmProvider.isRealMode()
                ? new LlmPromptMutationStrategy()
                : new DeterministicPromptMutationStrategy();
    }

    /**
     * Creates a mutator with a specific strategy.
     *
     * @param strategy the mutation strategy to use
     */
    public ShoppingBasketPromptMutator(PromptMutationStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public String mutate(String currentPrompt, OptimizeHistory history) throws MutationException {
        return strategy.mutate(currentPrompt, history);
    }

    @Override
    public String description() {
        return strategy.description();
    }

    @Override
    public void validate(String prompt) throws MutationException {
        if (prompt == null) {
            throw new MutationException("Prompt cannot be null");
        }
        if (prompt.length() > 10000) {
            throw new MutationException("Prompt exceeds maximum length of 10000 characters");
        }
    }
}
