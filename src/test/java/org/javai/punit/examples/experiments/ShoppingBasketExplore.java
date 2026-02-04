package org.javai.punit.examples.experiments;

import java.util.stream.Stream;
import org.javai.punit.api.ExploreExperiment;
import org.javai.punit.api.Factor;
import org.javai.punit.api.FactorArguments;
import org.javai.punit.api.FactorSource;
import org.javai.punit.api.Input;
import org.javai.punit.api.InputSource;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.examples.usecases.ShoppingInstructionInput;
import org.javai.punit.examples.usecases.ShoppingBasketUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * EXPLORE experiments for finding the best model and temperature configuration.
 *
 * <p>Before establishing a production baseline, you need to decide which LLM model
 * and temperature setting to use. These experiments help you compare configurations.
 *
 * <h2>Typical Workflow</h2>
 * <ol>
 *   <li><b>Explore</b> - Run these experiments to compare models and temperatures</li>
 *   <li><b>Choose</b> - Select the best configuration based on results</li>
 *   <li><b>Measure</b> - Run {@link ShoppingBasketMeasure} to establish baseline</li>
 *   <li><b>Test</b> - Use the baseline in probabilistic regression tests</li>
 * </ol>
 *
 * <h2>Running</h2>
 * <pre>{@code
 * ./gradlew exp -Prun=ShoppingBasketExplore
 * ./gradlew exp -Prun=ShoppingBasketExplore.compareModels
 * }</pre>
 *
 * @see ShoppingBasketUseCase
 * @see ShoppingBasketMeasure
 */
@Disabled("Example experiment - run with ./gradlew exp -Prun=ShoppingBasketExplore")
public class ShoppingBasketExplore {

    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    @BeforeEach
    void setUp() {
        provider.register(ShoppingBasketUseCase.class, ShoppingBasketUseCase::new);
    }

    // Representative instruction for configuration comparison
    private static final String TEST_INSTRUCTION = "Add some apples";

    /**
     * Compares different LLM models.
     *
     * <p>This experiment answers: "Which model handles this task most reliably?"
     * Temperature is fixed (0.1), so the only variable is the model itself.
     *
     * @param useCase the use case instance
     * @param model the model identifier to test
     * @param captor records outcomes for comparison
     */
    @ExploreExperiment(
            useCase = ShoppingBasketUseCase.class,
            samplesPerConfig = 20,
            experimentId = "model-comparison-v1"
    )
    @FactorSource(value = "modelConfigurations", factors = {"model"})
    void compareModels(
            ShoppingBasketUseCase useCase,
            @Factor("model") String model,
            OutcomeCaptor captor
    ) {
        useCase.setModel(model);
        useCase.setTemperature(0.1);  // Fixed temperature for fair comparison
        captor.record(useCase.translateInstruction(TEST_INSTRUCTION));
    }

    /**
     * Explores performance across varied inputs.
     *
     * <p>This experiment uses a curated set of instructions to understand how the
     * LLM performs across different instruction types. Each input becomes a separate
     * configuration, generating separate spec files for comparison.
     *
     * <p>The {@code @Input} annotation explicitly marks which parameter receives
     * the input value, distinguishing it from the use case parameter.
     *
     * @param useCase the use case instance
     * @param inputData the input data containing instruction and expected output
     * @param captor records outcomes for comparison
     */
    @ExploreExperiment(
            useCase = ShoppingBasketUseCase.class,
            samplesPerConfig = 10,
            experimentId = "input-exploration-v1"
    )
    @InputSource(file = "fixtures/shopping-instructions.json")
    void exploreInputVariations(
            ShoppingBasketUseCase useCase,
            @Input ShoppingInstructionInput inputData,
            OutcomeCaptor captor
    ) {
        useCase.setModel("gpt-4o-mini");
        useCase.setTemperature(0.1);
        captor.record(useCase.translateInstruction(inputData.instruction()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FACTOR PROVIDERS - Configuration options to explore
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Models to compare.
     *
     * <p>These are actual model identifiers recognized by the routing LLM.
     * In mock mode, any model name works. In real mode, these route to
     * the appropriate provider (OpenAI or Anthropic).
     */
    public static Stream<FactorArguments> modelConfigurations() {
        return FactorArguments.configurations()
                .names("model")
                .values("gpt-4o-mini")
                .values("gpt-4o")
                .values("claude-haiku-4-5-20251001")
                .values("claude-sonnet-4-5-20250929")
                .stream();
    }
}
