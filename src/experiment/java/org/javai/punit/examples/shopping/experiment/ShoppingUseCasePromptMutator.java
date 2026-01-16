package org.javai.punit.examples.shopping.experiment;

import java.util.ArrayList;
import java.util.List;
import org.javai.punit.experiment.optimize.FactorMutator;
import org.javai.punit.experiment.optimize.MutationException;
import org.javai.punit.experiment.optimize.OptimizeHistory;

/**
 * Mutator that simulates LLM-based prompt improvement for ShoppingUseCase.
 *
 * <p>This mutator implements a simple but effective prompt engineering strategy:
 * it analyzes the optimization history to identify which improvements haven't
 * been tried yet, then adds relevant instructions to the system prompt.
 *
 * <h2>Improvement Strategy</h2>
 * <p>The mutator adds instructions in priority order, checking what's already
 * in the prompt to avoid duplication:
 * <ol>
 *   <li><b>JSON format</b> - "Always respond with valid, well-formed JSON."</li>
 *   <li><b>Field names</b> - "Use exact field names: products, query, totalResults."</li>
 *   <li><b>Type correctness</b> - "Use numeric values for prices, strings for names."</li>
 *   <li><b>Required fields</b> - "Always include all required fields."</li>
 * </ol>
 *
 * <p>This simulates how a human (or LLM) would iteratively refine a prompt
 * based on observed failures, adding specific instructions to address each
 * category of error.
 *
 * <h2>Deterministic for Demonstration</h2>
 * <p>Unlike a real LLM-based mutator that might use Claude/GPT to generate
 * improved prompts, this implementation is deterministic. This makes the
 * demonstration predictable and easy to understand.
 *
 * @see org.javai.punit.experiment.optimize.FactorMutator
 */
public class ShoppingUseCasePromptMutator implements FactorMutator<String> {

    /**
     * The improvement instructions, in priority order.
     * Each entry contains: [keyword to check, instruction to add]
     */
    private static final List<String[]> IMPROVEMENTS = List.of(
            new String[]{"valid json", """

                    IMPORTANT: Always respond with valid, well-formed JSON. Ensure all braces are closed and syntax is correct."""},

            new String[]{"products, query, totalresults", """

                    Use exact field names: products, query, totalResults. Do not use alternative names like "items" or "results"."""},

            new String[]{"numeric", """

                    Use correct types: numeric values for prices (not strings), strings for product names."""},

            new String[]{"required field", """

                    Always include all required fields: products (array), query (string), totalResults (number). Never omit any of these."""},

            new String[]{"each product must have", """

                    Each product must have: name (string), price (number), category (string). Never use null for required attributes."""}
    );

    @Override
    public String mutate(String currentPrompt, OptimizeHistory history) throws MutationException {
        if (currentPrompt == null) {
            currentPrompt = "";
        }

        String lowerPrompt = currentPrompt.toLowerCase();

        // Find the first improvement that hasn't been applied yet
        for (String[] improvement : IMPROVEMENTS) {
            String keyword = improvement[0];
            String instruction = improvement[1];

            if (!lowerPrompt.contains(keyword)) {
                // Apply this improvement
                return currentPrompt + instruction;
            }
        }

        // All standard improvements have been applied
        // Add iteration-specific refinement based on history
        int iteration = history.iterationCount();

        if (!lowerPrompt.contains("double-check")) {
            return currentPrompt + """

                    Before responding, double-check that your JSON is valid and contains all required fields with correct types.""";
        }

        if (!lowerPrompt.contains("critical")) {
            return currentPrompt + """

                    CRITICAL: JSON validity is essential. A malformed response is worse than no response.""";
        }

        // If we've exhausted all improvements, make minor variations
        // This prevents the optimizer from getting stuck
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
