package org.javai.punit.examples2.experiments;

import org.javai.punit.experiment.optimize.FactorMutator;
import org.javai.punit.experiment.optimize.MutationException;
import org.javai.punit.experiment.optimize.OptimizeHistory;

import java.util.List;

/**
 * Mutator that iteratively improves the system prompt for ShoppingBasketUseCase.
 *
 * <p>This mutator implements a deterministic prompt engineering strategy:
 * it checks which improvements haven't been applied yet and adds relevant
 * instructions to reduce specific failure modes.
 *
 * <h2>Improvement Strategy</h2>
 * <p>Instructions are added in priority order:
 * <ol>
 *   <li><b>JSON format</b> - "Always respond with valid JSON"</li>
 *   <li><b>Array structure</b> - "The response must have an 'operations' array"</li>
 *   <li><b>Required fields</b> - "Each operation needs action, item, quantity"</li>
 *   <li><b>Valid actions</b> - "Actions must be: add, remove, or clear"</li>
 *   <li><b>Positive quantities</b> - "Quantities must be positive integers"</li>
 * </ol>
 *
 * <h2>Why Deterministic?</h2>
 * <p>Unlike a real LLM-based mutator that might use Claude/GPT to generate
 * improved prompts, this implementation is deterministic. This makes the
 * demonstration predictable and easy to understand.
 *
 * @see org.javai.punit.experiment.optimize.FactorMutator
 */
public class ShoppingBasketPromptMutator implements FactorMutator<String> {

    /**
     * Improvements to apply, in priority order.
     * Each entry: [keyword to check if present, instruction to add]
     */
    private static final List<String[]> IMPROVEMENTS = List.of(
            new String[]{"valid json", """

IMPORTANT: Always respond with valid, well-formed JSON. No explanations, just JSON."""},

            new String[]{"operations", """

The response MUST have an "operations" array containing the basket operations."""},

            new String[]{"action", """

Each operation MUST have exactly these fields: "action", "item", "quantity"."""},

            new String[]{"add", """

Valid actions are ONLY: "add", "remove", or "clear". No other action values are allowed."""},

            new String[]{"positive", """

Quantity must be a positive integer (1 or greater), never zero, negative, or a string."""},

            new String[]{"example", """

Example valid response:
{"operations": [{"action": "add", "item": "apples", "quantity": 2}]}"""}
    );

    @Override
    public String mutate(String currentPrompt, OptimizeHistory history) throws MutationException {
        if (currentPrompt == null) {
            currentPrompt = "";
        }

        String lowerPrompt = currentPrompt.toLowerCase();

        // Find the first improvement that hasn't been applied
        for (String[] improvement : IMPROVEMENTS) {
            String keyword = improvement[0];
            String instruction = improvement[1];

            if (!lowerPrompt.contains(keyword)) {
                return currentPrompt + instruction;
            }
        }

        // All standard improvements applied - add iteration-specific refinements
        int iteration = history.iterationCount();

        if (!lowerPrompt.contains("double-check")) {
            return currentPrompt + """

Before responding, double-check that your JSON is syntactically correct.""";
        }

        if (!lowerPrompt.contains("critical")) {
            return currentPrompt + """

CRITICAL: A malformed response is worse than no response. Validate your output.""";
        }

        // Minor variation to prevent getting stuck
        return currentPrompt + "\n[Iteration " + iteration + " refinement]";
    }

    @Override
    public String description() {
        return "Iterative prompt improvement - adds specific instructions to address common failure modes";
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
