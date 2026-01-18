package org.javai.punit.examples.experiments;

import org.javai.punit.api.OptimizeExperiment;
import org.javai.punit.api.ResultCaptor;
import org.javai.punit.api.ControlFactor;
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
 * <p>This experiment uses the optimization framework to automatically
 * improve the system prompt by:
 * <ol>
 *   <li>Starting with the initial prompt from the use case</li>
 *   <li>Running samples to measure success rate</li>
 *   <li>Mutating the prompt to add targeted improvements</li>
 *   <li>Repeating until convergence or max iterations</li>
 * </ol>
 *
 * <h2>What This Demonstrates</h2>
 * <ul>
 *   <li>{@code @OptimizeExperiment} annotation with text treatment factor</li>
 *   <li>{@code @ControlFactor} for receiving the current prompt value</li>
 *   <li>{@code @FactorGetter} on use case for initial value</li>
 *   <li>Custom {@link ShoppingBasketPromptMutator} for prompt generation</li>
 *   <li>{@link SuccessRateScorer} for iteration scoring</li>
 * </ul>
 *
 * <h2>Output</h2>
 * <p>Generates: {@code src/test/resources/punit/optimizations/ShoppingBasketUseCase/}
 *
 * <h2>Running</h2>
 * <pre>{@code
 * ./gradlew test --tests "ShoppingBasketOptimizePrompt"
 * }</pre>
 *
 * @see ShoppingBasketUseCase
 * @see ShoppingBasketPromptMutator
 * @see SuccessRateScorer
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
     * Optimizes the system prompt to maximize success rate.
     *
     * <p>The mutator ({@link ShoppingBasketPromptMutator}) iteratively adds
     * instructions to address common failure modes like:
     * <ul>
     *   <li>Malformed JSON</li>
     *   <li>Missing required fields</li>
     *   <li>Invalid action values</li>
     *   <li>Non-positive quantities</li>
     * </ul>
     *
     * @param useCase the use case instance
     * @param systemPrompt the current system prompt (updated each iteration)
     * @param captor records outcomes for scoring
     */
    @TestTemplate
    @OptimizeExperiment(
            useCase = ShoppingBasketUseCase.class,
            treatmentFactor = "systemPrompt",
            scorer = SuccessRateScorer.class,
            mutator = ShoppingBasketPromptMutator.class,
            objective = OptimizationObjective.MAXIMIZE,
            samplesPerIteration = 20,
            maxIterations = 10,
            noImprovementWindow = 3,
            experimentId = "prompt-optimization-v1"
    )
    void optimizeSystemPrompt(
            ShoppingBasketUseCase useCase,
            @ControlFactor("systemPrompt") String systemPrompt,
            ResultCaptor captor
    ) {
        // The systemPrompt is automatically injected and set via @FactorSetter
        assert useCase.getSystemPrompt().equals(systemPrompt);
        // Run the use case with a fixed instruction
        captor.record(useCase.translateInstruction("Add 2 apples and remove the bread"));
    }

    // NOTE: @OptimizeExperiment does not currently support @FactorSource parameters.
    // A future enhancement could add this capability to vary inputs across iterations.
}
