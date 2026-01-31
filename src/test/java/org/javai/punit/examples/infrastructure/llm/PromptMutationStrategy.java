package org.javai.punit.examples.infrastructure.llm;

import org.javai.punit.experiment.optimize.MutationException;
import org.javai.punit.experiment.optimize.OptimizeHistory;

/**
 * Strategy interface for prompt mutation in optimization experiments.
 *
 * <p>Different strategies can be used depending on the LLM mode:
 * <ul>
 *   <li>{@link DeterministicPromptMutationStrategy} - Scripted prompt progression (mock mode)</li>
 *   <li>{@link LlmPromptMutationStrategy} - LLM-powered prompt improvement (real mode)</li>
 * </ul>
 *
 * <p>This follows the Strategy pattern, allowing the mutator to switch between
 * deterministic and LLM-powered approaches based on configuration.
 *
 * @see org.javai.punit.experiment.optimize.FactorMutator
 */
public interface PromptMutationStrategy {

    /**
     * Generate an improved prompt based on the current prompt and optimization history.
     *
     * @param currentPrompt the current system prompt
     * @param history read-only access to optimization history
     * @return an improved prompt
     * @throws MutationException if mutation fails
     */
    String mutate(String currentPrompt, OptimizeHistory history) throws MutationException;

    /**
     * Human-readable description of this strategy.
     *
     * @return description for logging and reports
     */
    String description();
}
