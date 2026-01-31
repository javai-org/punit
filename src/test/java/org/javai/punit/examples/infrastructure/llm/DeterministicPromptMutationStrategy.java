package org.javai.punit.examples.infrastructure.llm;

import org.javai.punit.experiment.optimize.MutationException;
import org.javai.punit.experiment.optimize.OptimizeHistory;

/**
 * Deterministic prompt mutation strategy using a scripted progression.
 *
 * <p>This strategy produces a predefined sequence of prompts, each improving on
 * the previous by adding specific instructions that address common LLM failure modes.
 * It's useful for:
 * <ul>
 *   <li>Fast, free demonstrations (no API calls)</li>
 *   <li>Reproducible experiments</li>
 *   <li>Teaching prompt engineering principles</li>
 * </ul>
 *
 * <h2>Prompt Evolution</h2>
 * <pre>
 * Iteration 0 (weak): Initial prompt from experiment
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
 * @see LlmPromptMutationStrategy
 */
public final class DeterministicPromptMutationStrategy implements PromptMutationStrategy {

    /**
     * Sequence of prompts representing progressive improvement.
     * Each prompt builds on the previous one, addressing a specific failure mode.
     */
    private static final String[] PROMPT_PROGRESSION = {
        // Iteration 1: Add JSON-only format requirement
        """
        You are a shopping assistant. Convert the user's request into
        a JSON list of shopping basket operations.

        IMPORTANT: Respond with ONLY valid JSON. No explanations, no markdown, just JSON.""",

        // Iteration 2: Add explicit schema structure
        """
        You are a shopping assistant. Convert the user's request into
        JSON shopping basket operations.

        Respond with ONLY valid JSON in this structure:
        {"operations": [...]}

        The "operations" array contains the list of basket changes.""",

        // Iteration 3: Add required fields specification
        """
        You are a shopping assistant. Convert the user's request into
        JSON shopping basket operations.

        Respond with ONLY valid JSON in this structure:
        {"operations": [{"action": "...", "item": "...", "quantity": N}, ...]}

        Each operation MUST have exactly three fields:
        - "action": the operation type
        - "item": the product name
        - "quantity": the number of items""",

        // Iteration 4: Add valid action enumeration
        """
        You are a shopping assistant. Convert the user's request into
        JSON shopping basket operations.

        Respond with ONLY valid JSON:
        {"operations": [{"action": "...", "item": "...", "quantity": N}, ...]}

        Rules:
        - "action" MUST be one of: "add", "remove", or "clear"
        - "item" is the product name as a string
        - "quantity" is the number of items""",

        // Iteration 5: Add quantity constraints
        """
        You are a shopping assistant that converts natural language into JSON.

        OUTPUT FORMAT (no other text, just this JSON):
        {"operations": [{"action": "...", "item": "...", "quantity": N}, ...]}

        RULES:
        1. "action" must be exactly one of: "add", "remove", "clear"
        2. "item" is the product name (string)
        3. "quantity" must be a positive integer (1 or greater)
        4. For "clear" action, quantity should be 0 and item can be empty""",

        // Iteration 6+: Final refined prompt
        """
        You are a shopping basket assistant. Convert natural language instructions
        into structured JSON operations.

        RESPOND WITH ONLY THIS JSON FORMAT:
        {
          "operations": [
            {"action": "add"|"remove"|"clear", "item": "product name", "quantity": N}
          ]
        }

        STRICT RULES:
        1. action: ONLY "add", "remove", or "clear" (lowercase, no variations)
        2. item: product name as a non-empty string
        3. quantity: positive integer >= 1 (except "clear" which uses 0)
        4. NO explanations, NO markdown, NO extra fields

        Example: "Add 2 apples" -> {"operations": [{"action": "add", "item": "apples", "quantity": 2}]}"""
    };

    @Override
    public String mutate(String currentPrompt, OptimizeHistory history) throws MutationException {
        // iterationCount() returns the number of completed iterations.
        // After iteration 0 (weak prompt), we want PROMPT_PROGRESSION[0] for iteration 1.
        // So the index is iterationCount - 1.
        int index = history.iterationCount() - 1;

        // Return the next prompt in the progression
        if (index >= 0 && index < PROMPT_PROGRESSION.length) {
            return PROMPT_PROGRESSION[index];
        }

        // Beyond our progression - return the final refined prompt
        return PROMPT_PROGRESSION[PROMPT_PROGRESSION.length - 1];
    }

    @Override
    public String description() {
        return "Deterministic prompt progression - scripted sequence of improvements";
    }
}
