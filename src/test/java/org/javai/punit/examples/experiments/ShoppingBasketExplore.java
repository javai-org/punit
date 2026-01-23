package org.javai.punit.examples.experiments;

import java.util.stream.Stream;
import org.javai.punit.api.ExploreExperiment;
import org.javai.punit.api.Factor;
import org.javai.punit.api.FactorArguments;
import org.javai.punit.api.FactorSource;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.examples.usecases.ShoppingBasketUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * EXPLORE experiments for finding the best model and temperature configuration.
 *
 * <p>Before establishing a production baseline, you need to decide which LLM model
 * and temperature setting to use. These experiments help you compare options using
 * a simple instruction - just enough to see which configuration works best.
 *
 * <h2>Why Use a Simple Instruction?</h2>
 * <p>During exploration, you want to isolate the effect of model/temperature changes.
 * Using a simple instruction like "Add 2 apples" keeps the focus on configuration
 * comparison rather than instruction complexity. Once you've chosen a configuration,
 * you can measure its behavior across varied instructions.
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

    // Simple instruction used for all exploration - keeps focus on configuration comparison
    private static final String SIMPLE_INSTRUCTION = "Add 2 apples";

    /**
     * Compares different LLM models with a fixed simple instruction.
     *
     * <p>This experiment answers: "Which model handles this task most reliably?"
     * By keeping the instruction simple and temperature fixed (0.3), the only
     * variable is the model itself.
     *
     * <p>In a real application, you would replace the mock model names with actual
     * model identifiers like "gpt-4o", "claude-3-5-sonnet", etc.
     *
     * @param useCase the use case instance
     * @param model the model identifier to test
     * @param captor records outcomes for comparison
     */
    @TestTemplate
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
        useCase.setTemperature(0.3);  // Fixed temperature for fair comparison
        captor.record(useCase.translateInstruction(SIMPLE_INSTRUCTION));
    }

    /**
     * Compares models across different temperature settings.
     *
     * <p>This two-factor exploration reveals how each model responds to temperature
     * changes. Some models may be more stable across temperatures than others.
     * The instruction remains simple to isolate the model×temperature interaction.
     *
     * <p>Use this to answer questions like:
     * <ul>
     *   <li>Does model X work better at low or high temperature?</li>
     *   <li>Which model is most stable across temperature changes?</li>
     *   <li>Is there a clear winner for our use case?</li>
     * </ul>
     *
     * @param useCase the use case instance
     * @param model the model identifier
     * @param temperature the temperature setting
     * @param captor records outcomes
     */
    @TestTemplate
    @ExploreExperiment(
            useCase = ShoppingBasketUseCase.class,
            samplesPerConfig = 20,
            experimentId = "model-temperature-matrix-v1"
    )
    @FactorSource(value = "modelTemperatureMatrix", factors = {"model", "temperature"})
    void compareModelsAcrossTemperatures(
            ShoppingBasketUseCase useCase,
            @Factor("model") String model,
            @Factor("temperature") Double temperature,
            OutcomeCaptor captor
    ) {
        useCase.setModel(model);
        useCase.setTemperature(temperature);
        captor.record(useCase.translateInstruction(SIMPLE_INSTRUCTION));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FACTOR PROVIDERS - Configuration options to explore
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Models to compare.
     *
     * <p>In a real application, replace these with actual model identifiers.
     */
    public static Stream<FactorArguments> modelConfigurations() {
        return FactorArguments.configurations()
                .names("model")
                .values("gpt-4o-mini")
                .values("gpt-4o")
                .values("claude-3-5-haiku")
                .values("claude-3-5-sonnet")
                .stream();
    }

    /**
     * Model × temperature combinations to explore.
     *
     * <p>Creates a matrix to understand how each model behaves at different
     * temperature settings.
     */
    public static Stream<FactorArguments> modelTemperatureMatrix() {
        return FactorArguments.configurations()
                .names("model", "temperature")
                // GPT-4o-mini across temperatures
                .values("gpt-4o-mini", 0.0)
                .values("gpt-4o-mini", 0.5)
                .values("gpt-4o-mini", 1.0)
                // GPT-4o across temperatures
                .values("gpt-4o", 0.0)
                .values("gpt-4o", 0.5)
                .values("gpt-4o", 1.0)
                // Claude Haiku across temperatures
                .values("claude-3-5-haiku", 0.0)
                .values("claude-3-5-haiku", 0.5)
                .values("claude-3-5-haiku", 1.0)
                // Claude Sonnet across temperatures
                .values("claude-3-5-sonnet", 0.0)
                .values("claude-3-5-sonnet", 0.5)
                .values("claude-3-5-sonnet", 1.0)
                .stream();
    }
}
