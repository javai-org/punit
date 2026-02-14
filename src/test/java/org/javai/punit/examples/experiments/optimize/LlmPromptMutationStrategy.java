package org.javai.punit.examples.experiments.optimize;

import java.util.Optional;
import org.javai.punit.examples.app.llm.ChatLlm;
import org.javai.punit.examples.app.llm.ChatLlmProvider;
import org.javai.punit.examples.app.llm.LlmApiException;
import org.javai.punit.experiment.optimize.IterationFeedback;
import org.javai.punit.experiment.optimize.MutationException;
import org.javai.punit.experiment.optimize.OptimizationRecord;
import org.javai.punit.experiment.optimize.OptimizeHistory;
import org.javai.punit.experiment.optimize.OptimizeStatistics;

/**
 * LLM-powered prompt mutation strategy.
 *
 * <p>Uses an LLM to analyze failure patterns and generate improved prompts.
 * This demonstrates genuine AI-assisted prompt engineering, where the model
 * analyzes the current prompt's performance and actual failure feedback
 * to suggest targeted improvements.
 *
 * <h2>How It Works</h2>
 * <ol>
 *   <li>Extracts performance data and failure feedback from optimization history</li>
 *   <li>Constructs a meta-prompt with actual error messages from postcondition failures</li>
 *   <li>Sends the meta-prompt to the configured mutation model</li>
 *   <li>Returns the improved prompt for the next iteration</li>
 * </ol>
 *
 * <h2>Feedback-Driven Improvement</h2>
 * <p>Unlike approaches that hardcode the expected schema, this strategy learns from
 * actual feedback including:
 * <ul>
 *   <li>Postcondition failure messages (e.g., "Invalid JSON: unexpected token")</li>
 *   <li>Validation errors (e.g., "Invalid action 'purchase' for context SHOP")</li>
 *   <li>Instance conformance mismatches (expected vs actual results)</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>The model used for mutations can be configured separately from experiment models:
 * <ul>
 *   <li>System property: {@code punit.llm.mutation.model}</li>
 *   <li>Environment variable: {@code PUNIT_LLM_MUTATION_MODEL}</li>
 *   <li>Default: {@code gpt-4o-mini} (cost-effective for meta-prompting)</li>
 * </ul>
 *
 * @see MockLlmPromptMutationStrategy
 */
final class LlmPromptMutationStrategy implements PromptMutationStrategy {

    private static final String MODEL_PROPERTY = "punit.llm.mutation.model";
    private static final String MODEL_ENV_VAR = "PUNIT_LLM_MUTATION_MODEL";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    private static final double MUTATION_TEMPERATURE = 0.7; // Higher for creative improvements

    private static final String META_PROMPT_SYSTEM = """
        You are an expert prompt engineer. Your task is to improve prompts that instruct
        an LLM to produce structured output.

        You will receive:
        1. The current prompt being used
        2. Performance metrics (success rate, iteration count)
        3. ACTUAL FAILURE FEEDBACK from the system - this is the most important part!

        Analyze the failure feedback carefully to understand:
        - What postconditions are failing and why
        - What the actual error messages say
        - If there are expected vs actual mismatches, what the difference is

        Based on this analysis, produce an improved prompt that:
        - Addresses the specific failures shown in the feedback
        - Adds explicit instructions to prevent the observed errors
        - Includes examples if the feedback suggests format confusion
        - Is clear and unambiguous

        Output ONLY the improved prompt text. No explanations, no markdown, just the prompt.""";

    private static final String META_PROMPT_TEMPLATE = """
        CURRENT PROMPT:
        %s

        PERFORMANCE METRICS:
        - Iteration: %d
        - Success rate: %.1f%% (%d/%d samples)
        - Previous best: %.1f%%

        %s

        Based on the failure feedback above, produce an improved prompt that addresses these specific issues.""";

    private final ChatLlm llm;

    /**
     * Creates an LLM mutation strategy using the default routing LLM.
     */
    LlmPromptMutationStrategy() {
        this(ChatLlmProvider.resolve());
    }

    /**
     * Creates an LLM mutation strategy with a specific LLM instance.
     *
     * @param llm the LLM to use for generating mutations
     */
    LlmPromptMutationStrategy(ChatLlm llm) {
        this.llm = llm;
    }

    @Override
    public String mutate(String currentPrompt, OptimizeHistory history) throws MutationException {
        String model = resolveMutationModel();

        // Extract performance data from history
        int iteration = history.iterationCount();
        double successRate = getLastSuccessRate(history);
        int successes = getLastSuccesses(history);
        int samples = getLastSamples(history);
        double bestRate = getBestSuccessRate(history);

        // Extract actual failure feedback from the last iteration
        String feedbackSection = extractFeedbackSection(history);

        // Build the meta-prompt
        String userMessage = META_PROMPT_TEMPLATE.formatted(
                currentPrompt,
                iteration,
                successRate * 100,
                successes,
                samples,
                bestRate * 100,
                feedbackSection
        );

        try {
            String improvedPrompt = llm.chat(META_PROMPT_SYSTEM, userMessage, model, MUTATION_TEMPERATURE);
            return improvedPrompt.trim();
        } catch (LlmApiException e) {
            throw new MutationException("LLM mutation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts the failure feedback section from the optimization history.
     *
     * <p>This is the key method that provides real feedback to the mutator LLM
     * instead of hardcoded schema knowledge.
     */
    private String extractFeedbackSection(OptimizeHistory history) {
        if (history.iterationCount() == 0) {
            return "FAILURE FEEDBACK:\nNo feedback yet - this is the first iteration.";
        }

        OptimizationRecord lastIteration = history.iterations().get(history.iterationCount() - 1);
        OptimizeStatistics stats = lastIteration.aggregate().statistics();
        IterationFeedback feedback = stats.feedback();

        StringBuilder sb = new StringBuilder();
        sb.append("FAILURE FEEDBACK FROM LAST ITERATION:\n");

        if (feedback == null || !feedback.hasFailures()) {
            if (stats.failureCount() > 0) {
                sb.append("There were ").append(stats.failureCount())
                        .append(" failures but detailed feedback was not captured.\n");
            } else {
                sb.append("All samples passed! Looking for further optimization opportunities.\n");
            }
            return sb.toString();
        }

        // Include the formatted feedback from IterationFeedback
        sb.append(feedback.formatForMutator());

        return sb.toString();
    }

    @Override
    public String description() {
        return "LLM-powered prompt improvement using " + resolveMutationModel();
    }

    private double getLastSuccessRate(OptimizeHistory history) {
        if (history.iterationCount() == 0) {
            return 0.0;
        }
        OptimizationRecord last = history.iterations().get(history.iterationCount() - 1);
        return last.aggregate().statistics().successRate();
    }

    private int getLastSuccesses(OptimizeHistory history) {
        if (history.iterationCount() == 0) {
            return 0;
        }
        OptimizationRecord last = history.iterations().get(history.iterationCount() - 1);
        return last.aggregate().statistics().successCount();
    }

    private int getLastSamples(OptimizeHistory history) {
        if (history.iterationCount() == 0) {
            return 0;
        }
        OptimizationRecord last = history.iterations().get(history.iterationCount() - 1);
        return last.aggregate().statistics().sampleCount();
    }

    private double getBestSuccessRate(OptimizeHistory history) {
        Optional<Double> best = history.bestScore();
        return best.orElse(0.0);
    }

    /**
     * Resolves the model to use for mutations.
     *
     * @return the mutation model identifier
     */
    static String resolveMutationModel() {
        String value = System.getProperty(MODEL_PROPERTY);
        if (value != null && !value.isBlank()) {
            return value;
        }

        value = System.getenv(MODEL_ENV_VAR);
        if (value != null && !value.isBlank()) {
            return value;
        }

        return DEFAULT_MODEL;
    }
}
