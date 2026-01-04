/**
 * LLM-specific extensions for punit experiments (llmx).
 *
 * <p>This package provides:
 * <ul>
 *   <li>{@link org.javai.punit.llmx.LlmExperimentBackend} - Backend for LLM experiments</li>
 *   <li>{@link org.javai.punit.llmx.LlmUseCaseContext} - Typed context for LLM parameters</li>
 *   <li>{@link org.javai.punit.llmx.LlmResultValues} - Common value keys for LLM results</li>
 *   <li>{@link org.javai.punit.llmx.LlmPromptRefinementStrategy} - Adaptive prompt refinement</li>
 *   <li>{@link org.javai.punit.llmx.FailureAnalyzer} - Failure pattern analysis</li>
 * </ul>
 *
 * <h2>Package Independence</h2>
 * <p>The llmx package depends on punit core, but punit core does NOT depend on llmx.
 * This ensures the core framework remains domain-neutral.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * @Experiment(useCase = "usecase.json.generation", samples = 100)
 * @ExperimentContext(backend = "llm", parameters = {
 *     "model = gpt-4",
 *     "temperature = 0.7"
 * })
 * void measureJsonGeneration(UseCaseContext context) {
 *     LlmUseCaseContext llmContext = (LlmUseCaseContext) context;
 *     // Use llmContext.getModel(), llmContext.getTemperature(), etc.
 * }
 * }</pre>
 */
package org.javai.punit.llmx;

