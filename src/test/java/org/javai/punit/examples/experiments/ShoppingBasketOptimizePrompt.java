package org.javai.punit.examples.experiments;

import org.javai.punit.api.ControlFactor;
import org.javai.punit.api.OptimizeExperiment;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.api.Pacing;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.examples.usecases.ShoppingBasketUseCase;
import org.javai.punit.experiment.optimize.OptimizationObjective;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * OPTIMIZE experiment for iteratively refining the system prompt.
 *
 * <p>This experiment demonstrates how the optimization framework can automatically
 * improve a poorly-formulated prompt through iterative refinement. It starts with
 * a deliberately weak prompt (see {@link #weakStartingPrompt()}) that lacks:
 * <ul>
 *   <li>Explicit JSON schema</li>
 *   <li>Valid action values</li>
 *   <li>Quantity constraints</li>
 *   <li>Format examples</li>
 * </ul>
 *
 * <p>The optimizer then iteratively improves this prompt by:
 * <ol>
 *   <li>Running samples to measure success rate</li>
 *   <li>Identifying failure patterns</li>
 *   <li>Mutating the prompt to address failures</li>
 *   <li>Repeating until convergence</li>
 * </ol>
 *
 * <h2>Expected Progression</h2>
 * <pre>
 * Iteration 1: ~30% success (weak prompt, many JSON/format errors)
 * Iteration 2: ~50% success (added JSON structure)
 * Iteration 3: ~70% success (added valid actions)
 * Iteration 4: ~85% success (added quantity constraints)
 * Iteration 5: ~95% success (refined formatting)
 * </pre>
 *
 * <h2>What This Demonstrates</h2>
 * <ul>
 *   <li>{@code initialControlFactorSource} for specifying a weak starting point</li>
 *   <li>{@code @ControlFactor} for receiving the current prompt value</li>
 *   <li>Custom {@link ShoppingBasketPromptMutator} for targeted improvements</li>
 *   <li>{@link ShoppingBasketSuccessRateScorer} for iteration scoring</li>
 * </ul>
 *
 * <h2>Output</h2>
 * <p>Generates: {@code src/test/resources/punit/optimizations/ShoppingBasketUseCase/}
 *
 * <h2>Running</h2>
 * <pre>{@code
 * ./gradlew exp -Prun=ShoppingBasketOptimizePrompt
 * }</pre>
 *
 * @see ShoppingBasketUseCase
 * @see ShoppingBasketPromptMutator
 * @see ShoppingBasketSuccessRateScorer
 */
@Disabled("Example experiment - run manually with ./gradlew exp -Prun=ShoppingBasketOptimizePrompt")
public class ShoppingBasketOptimizePrompt {

    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    @BeforeEach
    void setUp() {
        provider.register(ShoppingBasketUseCase.class, ShoppingBasketUseCase::new);
    }

    /**
     * Provides a deliberately weak starting prompt for optimization.
     *
     * <p>This prompt is plausible (a developer might write it as a first attempt)
     * but missing critical details that lead to frequent failures:
     * <ul>
     *   <li>No explicit JSON schema - LLM may return wrong structure</li>
     *   <li>No valid action values - may invent actions like "purchase"</li>
     *   <li>No quantity constraints - may use zero or negative quantities</li>
     *   <li>No response format - may include explanations with JSON</li>
     * </ul>
     *
     * <p>Starting from this weak prompt makes the optimization journey more
     * compelling, as it demonstrates clear improvement over iterations.
     *
     * @return a weak but plausible starting prompt
     */
    static String weakStartingPrompt() {
        return """
            You are a shopping assistant. Convert the user's request into
            a JSON list of shopping basket operations.
            """;
    }

    /**
     * Optimizes the system prompt to maximize success rate.
     *
     * <p>Starts from {@link #weakStartingPrompt()} which has ~30% success rate,
     * and iteratively improves to ~95% by adding:
     * <ul>
     *   <li>Explicit JSON schema</li>
     *   <li>Valid action enumeration (ADD, REMOVE, CLEAR)</li>
     *   <li>Quantity constraints (positive integers)</li>
     *   <li>Response format requirements (JSON only, no explanations)</li>
     * </ul>
     *
     * @param useCase the use case instance
     * @param systemPrompt the current system prompt (updated each iteration)
     * @param captor records outcomes for scoring
     */
    @TestTemplate
    @OptimizeExperiment(
            useCase = ShoppingBasketUseCase.class,
            controlFactor = "systemPrompt",
            initialControlFactorSource = "weakStartingPrompt",
            scorer = ShoppingBasketSuccessRateScorer.class,
            mutator = ShoppingBasketPromptMutator.class,
            objective = OptimizationObjective.MAXIMIZE,
            samplesPerIteration = 5,
            maxIterations = 10,
            noImprovementWindow = 3,
            experimentId = "prompt-optimization-v1"
    )
    @Pacing(maxRequestsPerSecond = 5)
    void optimizeSystemPrompt(
            ShoppingBasketUseCase useCase,
            @ControlFactor("systemPrompt") String systemPrompt,
            OutcomeCaptor captor
    ) {
        // The systemPrompt is automatically injected and set via @FactorSetter, but just to prove it is:
        assert systemPrompt.equals(useCase.getSystemPrompt()) : "System prompt automatically set by PUnit";
        captor.record(useCase.translateInstruction("Add 2 apples and remove the bread"));
    }
}
