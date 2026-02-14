package org.javai.punit.examples.experiments;

import java.util.stream.Stream;
import org.javai.punit.api.InputSource;
import org.javai.punit.api.MeasureExperiment;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.examples.usecases.ShoppingBasketUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * MEASURE experiment for establishing the production baseline.
 *
 * <p>This experiment runs many samples (1000 by default) to establish a reliable
 * statistical baseline that probabilistic tests use to derive thresholds.
 *
 * <h2>One Baseline Per Use Case</h2>
 * <p>A use case should have ONE measure experiment that represents production traffic.
 * The input source determines which inputs are cycled through during measurement.
 * The probabilistic test then uses the <b>same input source</b>, ensuring the
 * test exercises the same input distribution as the baseline.
 *
 * <h2>How Input Cycling Works</h2>
 * <p>The input source {@link #basketInstructions()} returns 10 instructions. With 1000
 * samples, each instruction is tested 100 times:
 * <pre>
 * Sample 1    → "Add 2 apples"
 * Sample 2    → "Remove the milk"
 * ...
 * Sample 10   → "I'd like to remove all the vegetables"
 * Sample 11   → "Add 2 apples"  (cycles back)
 * ...
 * Sample 1000 → (10th instruction)
 * </pre>
 *
 * <p>The probabilistic test uses the same input source but with fewer samples
 * (e.g., 100). It still cycles through all 10 instructions, just fewer times each.
 *
 * <h2>Output</h2>
 * <p>Generates: {@code src/test/resources/punit/specs/ShoppingBasketUseCase-{footprint}.yaml}
 *
 * <h2>Running</h2>
 * <pre>{@code
 * ./gradlew exp -Prun=ShoppingBasketMeasure
 * }</pre>
 *
 * @see ShoppingBasketUseCase
 * @see #basketInstructions()
 * @see org.javai.punit.examples.probabilistictests.ShoppingBasketTest
 */
@Disabled("Example experiment - run with ./gradlew exp -Prun=ShoppingBasketMeasure")
public class ShoppingBasketMeasure {

    /**
     * The use case provider handles construction and injection of use cases.
     */
    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    /**
     * Configures the use case provider before each experiment sample.
     */
    @BeforeEach
    void setUp() {
        provider.register(ShoppingBasketUseCase.class, ShoppingBasketUseCase::new);
    }

    /**
     * Establishes the production baseline for shopping basket instruction translation.
     *
     * <p>This experiment cycles through 10 representative instructions, measuring
     * how reliably the LLM translates each type. The resulting baseline reflects
     * aggregate behavior across:
     * <ul>
     *   <li>Simple single-item operations ("Add 2 apples")</li>
     *   <li>Multi-item operations ("Add 3 oranges and 2 bananas")</li>
     *   <li>Clear operations ("Clear the basket")</li>
     *   <li>Natural language variations ("I'd like to remove all the vegetables")</li>
     * </ul>
     *
     * <p>With 1000 samples and 10 instructions, each instruction is tested exactly
     * 100 times, providing reliable statistics for each input type.
     *
     * @param useCase the use case instance (injected by PUnit)
     * @param instruction the instruction (cycles through 10 variations)
     * @param captor records outcomes for aggregation
     */
    @MeasureExperiment(
            useCase = ShoppingBasketUseCase.class,
            experimentId = "baseline-v1"
    )
    @InputSource("basketInstructions")
    void measureBaseline(
            ShoppingBasketUseCase useCase,
            String instruction,
            OutcomeCaptor captor
    ) {
        captor.record(useCase.translateInstruction(instruction));
    }

    /**
     * Establishes a baseline with instance conformance checking.
     *
     * <p>This experiment demonstrates using a golden dataset (JSON file) that includes
     * expected responses. Each input contains both the instruction and the expected
     * JSON response, enabling instance conformance checking.
     *
     * <p>The {@link TranslationInput} record captures both fields, and the use case's
     * {@link ShoppingBasketUseCase#translateInstruction(String, String)} method
     * accepts the expected value for conformance checking.
     *
     * @param useCase the use case instance
     * @param input the input with instruction and expected response
     * @param captor records outcomes for aggregation
     */
    @MeasureExperiment(
            useCase = ShoppingBasketUseCase.class,
            experimentId = "baseline-with-golden-v1"
    )
    @InputSource(file = "golden/shopping-instructions.json")
    void measureBaselineWithGolden(
            ShoppingBasketUseCase useCase,
            TranslationInput input,
            OutcomeCaptor captor
    ) {
        captor.record(useCase.translateInstruction(input.instruction(), input.expected()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INPUT TYPES - Records for structured test inputs
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Input record for golden dataset testing with expected values.
     *
     * <p>This record is automatically deserialized from the JSON file by Jackson.
     * The field names must match the JSON keys.
     *
     * @param instruction the natural language instruction
     * @param expected the expected JSON response
     */
    public record TranslationInput(String instruction, String expected) {}

    // ═══════════════════════════════════════════════════════════════════════════
    // INPUT SOURCES - Test input data for experiments
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Representative basket instructions for baseline measurement.
     *
     * <p>These 10 instructions cover the main operation types:
     * <ul>
     *   <li>Single-item add/remove</li>
     *   <li>Multi-item operations</li>
     *   <li>Quantity specifications</li>
     *   <li>Clear operations</li>
     *   <li>Natural language variations</li>
     * </ul>
     *
     * @return stream of instruction strings
     */
    static Stream<String> basketInstructions() {
        return Stream.of(
                "Add 2 apples",
                "Remove the milk",
                "Add 1 loaf of bread",
                "Add 3 oranges and 2 bananas",
                "Add 5 tomatoes and remove the cheese",
                "Clear the basket",
                "Clear everything",
                "Remove 2 eggs from the basket",
                "Add a dozen eggs",
                "I'd like to remove all the vegetables"
        );
    }
}
