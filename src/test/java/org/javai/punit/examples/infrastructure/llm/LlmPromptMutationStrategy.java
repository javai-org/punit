package org.javai.punit.examples.infrastructure.llm;

import java.util.Optional;
import org.javai.punit.experiment.optimize.MutationException;
import org.javai.punit.experiment.optimize.OptimizationRecord;
import org.javai.punit.experiment.optimize.OptimizeHistory;
import org.javai.punit.experiment.optimize.OptimizeStatistics;

/**
 * LLM-powered prompt mutation strategy.
 *
 * <p>Uses an LLM to analyze failure patterns and generate improved prompts.
 * This demonstrates genuine AI-assisted prompt engineering, where the model
 * analyzes the current prompt's performance and suggests targeted improvements.
 *
 * <h2>How It Works</h2>
 * <ol>
 *   <li>Extracts performance data from optimization history (success rate, iteration count)</li>
 *   <li>Constructs a meta-prompt asking the LLM to improve the current prompt</li>
 *   <li>Sends the meta-prompt to the configured mutation model</li>
 *   <li>Returns the improved prompt for the next iteration</li>
 * </ol>
 *
 * <h2>Configuration</h2>
 * <p>The model used for mutations can be configured separately from experiment models:
 * <ul>
 *   <li>System property: {@code punit.llm.mutation.model}</li>
 *   <li>Environment variable: {@code PUNIT_LLM_MUTATION_MODEL}</li>
 *   <li>Default: {@code gpt-4o-mini} (cost-effective for meta-prompting)</li>
 * </ul>
 *
 * @see DeterministicPromptMutationStrategy
 */
public final class LlmPromptMutationStrategy implements PromptMutationStrategy {

    private static final String MODEL_PROPERTY = "punit.llm.mutation.model";
    private static final String MODEL_ENV_VAR = "PUNIT_LLM_MUTATION_MODEL";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    private static final double MUTATION_TEMPERATURE = 0.7; // Higher for creative improvements

    private static final String META_PROMPT_SYSTEM = """
        You are an expert prompt engineer. Your task is to improve prompts that instruct
        an LLM to convert natural language into structured JSON.

        Analyze the given prompt and its performance metrics, then produce an improved version.
        Focus on:
        - Clarity and specificity of instructions
        - Explicit format requirements
        - Examples that demonstrate correct output
        - Constraints that prevent common errors

        Output ONLY the improved prompt text. No explanations, no markdown, just the prompt.""";

    private static final String META_PROMPT_TEMPLATE = """
        CURRENT PROMPT:
        %s

        PERFORMANCE METRICS:
        - Iteration: %d
        - Success rate: %.1f%% (%d/%d samples)
        - Previous best: %.1f%%

        The prompt is being used to convert shopping instructions like "Add 2 apples"
        into JSON format: {"context": "SHOP", "name": "add", "parameters": [...]}

        Common failure modes to address:
        - Invalid JSON (missing brackets, quotes)
        - Wrong field names or structure
        - Invalid action names (should be: add, remove, clear)
        - Missing required fields

        Produce an improved prompt that achieves higher success rate.""";

    private final ChatLlm llm;

    /**
     * Creates an LLM mutation strategy using the default routing LLM.
     */
    public LlmPromptMutationStrategy() {
        this(ChatLlmProvider.resolve());
    }

    /**
     * Creates an LLM mutation strategy with a specific LLM instance.
     *
     * @param llm the LLM to use for generating mutations
     */
    public LlmPromptMutationStrategy(ChatLlm llm) {
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

        // Build the meta-prompt
        String userMessage = META_PROMPT_TEMPLATE.formatted(
                currentPrompt,
                iteration,
                successRate * 100,
                successes,
                samples,
                bestRate * 100
        );

        try {
            String improvedPrompt = llm.chat(META_PROMPT_SYSTEM, userMessage, model, MUTATION_TEMPERATURE);
            return improvedPrompt.trim();
        } catch (LlmApiException e) {
            throw new MutationException("LLM mutation failed: " + e.getMessage(), e);
        }
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
    public static String resolveMutationModel() {
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
