package org.javai.punit.llmx;

import java.util.Optional;
import org.javai.punit.experiment.model.IterationFeedback;
import org.javai.punit.experiment.spi.RefinementStrategy;

/**
 * LLM-based strategy for refining prompts in adaptive experiments.
 *
 * <p>This strategy analyzes failures from the previous iteration and uses an LLM
 * to generate a refined prompt that addresses the observed issues.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AdaptiveLevels.<String>builder()
 *     .name("systemPrompt")
 *     .startingFrom("You are a helpful assistant...")
 *     .refinedBy(new LlmPromptRefinementStrategy(llmClient, "gpt-4"))
 *     .maxIterations(10)
 *     .build();
 * }</pre>
 *
 * <h2>How It Works</h2>
 * <ol>
 *   <li>Analyzes failures using {@link FailureAnalyzer}</li>
 *   <li>Constructs a meta-prompt asking the LLM to improve the original</li>
 *   <li>Returns the refined prompt for the next iteration</li>
 * </ol>
 */
public class LlmPromptRefinementStrategy implements RefinementStrategy<String> {

	private final LlmClient llmClient;
	private final String model;
	private final FailureAnalyzer failureAnalyzer;
	private final int maxRetries;

	/**
	 * Creates a strategy with default settings.
	 *
	 * @param llmClient the LLM client for prompt refinement
	 * @param model the model to use for refinement (e.g., "gpt-4")
	 */
	public LlmPromptRefinementStrategy(LlmClient llmClient, String model) {
		this(llmClient, model, new FailureAnalyzer(), 3);
	}

	/**
	 * Creates a strategy with custom settings.
	 *
	 * @param llmClient the LLM client for prompt refinement
	 * @param model the model to use for refinement
	 * @param failureAnalyzer the failure analyzer
	 * @param maxRetries maximum retries for LLM calls
	 */
	public LlmPromptRefinementStrategy(LlmClient llmClient, String model,
									   FailureAnalyzer failureAnalyzer, int maxRetries) {
		this.llmClient = llmClient;
		this.model = model;
		this.failureAnalyzer = failureAnalyzer;
		this.maxRetries = maxRetries;
	}

	@Override
	public Optional<String> refine(String currentPrompt, IterationFeedback feedback) {
		// Check if we should stop refining
		if (shouldStopRefining(feedback)) {
			return Optional.empty();
		}

		// Analyze failures
		FailureAnalyzer.FailureAnalysis analysis = failureAnalyzer.analyze(feedback.failures());

		// Build the meta-prompt for refinement
		String metaPrompt = buildRefinementPrompt(currentPrompt, feedback, analysis);

		// Call LLM to generate refined prompt
		try {
			String refinedPrompt = callLlmWithRetry(metaPrompt);
			return Optional.of(refinedPrompt);
		} catch (Exception e) {
			// If LLM call fails, return empty to stop iteration
			return Optional.empty();
		}
	}

	@Override
	public String description() {
		return "LLM-based prompt refinement using " + model;
	}

	private boolean shouldStopRefining(IterationFeedback feedback) {
		// Stop if no failures to learn from
		if (feedback.failures().isEmpty()) {
			return true;
		}

		// Stop if all failures are transient errors (not prompt-related)
		return feedback.failures().stream()
				.allMatch(f -> isTransientError(f.category()));
	}

	private boolean isTransientError(String category) {
		return "RATE_LIMIT".equals(category) ||
				"TIMEOUT".equals(category) ||
				"SERVICE_UNAVAILABLE".equals(category);
	}

	private String buildRefinementPrompt(String currentPrompt, IterationFeedback feedback,
										 FailureAnalyzer.FailureAnalysis analysis) {
		StringBuilder sb = new StringBuilder();

		sb.append("You are an expert at improving LLM system prompts.\n\n");
		sb.append("## Current Prompt\n");
		sb.append("```\n").append(currentPrompt).append("\n```\n\n");

		sb.append("## Results from Testing\n");
		sb.append("- Success rate: ").append(String.format("%.1f%%", feedback.summary().successRate() * 100)).append("\n");
		sb.append("- Samples: ").append(feedback.summary().samplesExecuted()).append("\n");
		sb.append("- Iteration: ").append(feedback.iteration() + 1).append("\n\n");

		sb.append("## Failure Analysis\n");
		sb.append(analysis.getSummary()).append("\n\n");

		if (!analysis.getPatterns().isEmpty()) {
			sb.append("## Identified Patterns\n");
			for (FailureAnalyzer.FailurePattern pattern : analysis.getPatterns()) {
				sb.append("- ").append(pattern.description())
						.append(" (").append(pattern.occurrences()).append(" occurrences)\n");
			}
			sb.append("\n");
		}

		sb.append("## Task\n");
		sb.append("Improve the prompt to address the identified failure patterns.\n");
		sb.append("Return ONLY the improved prompt, with no additional explanation.\n");

		return sb.toString();
	}

	private String callLlmWithRetry(String prompt) throws Exception {
		Exception lastException = null;

		for (int i = 0; i < maxRetries; i++) {
			try {
				return llmClient.complete(prompt, model);
			} catch (Exception e) {
				lastException = e;
				// Wait before retry
				Thread.sleep((long) (Math.pow(2, i) * 1000));
			}
		}

		throw lastException != null ? lastException : new RuntimeException("LLM call failed");
	}

	/**
	 * Interface for LLM completion calls.
	 *
	 * <p>Implement this interface to integrate with your LLM provider.
	 */
	public interface LlmClient {

		/**
		 * Completes a prompt using the specified model.
		 *
		 * @param prompt the prompt to complete
		 * @param model the model to use
		 * @return the completion text
		 * @throws Exception if the call fails
		 */
		String complete(String prompt, String model) throws Exception;
	}
}

