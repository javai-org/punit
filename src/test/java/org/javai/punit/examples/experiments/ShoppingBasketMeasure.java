package org.javai.punit.examples.experiments;

import org.javai.punit.api.Factor;
import org.javai.punit.api.FactorSource;
import org.javai.punit.api.MeasureExperiment;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.examples.usecases.ShoppingBasketUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * MEASURE experiment for establishing the production baseline.
 *
 * <p>This experiment runs many samples (1000 by default) to establish a reliable
 * statistical baseline that probabilistic tests use to derive thresholds.
 *
 * <h2>One Baseline Per Use Case</h2>
 * <p>A use case should have ONE measure experiment that represents production traffic.
 * The factor provider determines which inputs are cycled through during measurement.
 * The probabilistic test then uses the <b>same factor provider</b>, ensuring the
 * test exercises the same input distribution as the baseline.
 *
 * <h2>How Cycling Works</h2>
 * <p>The factor provider {@link ShoppingBasketUseCase#multipleBasketInstructions()}
 * returns 10 instructions. With 1000 samples, each instruction is tested 100 times:
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
 * <p>The probabilistic test uses the same factor provider but with fewer samples
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
 * @see ShoppingBasketUseCase#multipleBasketInstructions()
 * @see org.javai.punit.examples.tests.ShoppingBasketTest
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
    @TestTemplate
    @MeasureExperiment(
            useCase = ShoppingBasketUseCase.class,
            experimentId = "baseline-v1"
    )
    @FactorSource(value = "multipleBasketInstructions", factors = {"instruction"})
    void measureBaseline(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction,
            OutcomeCaptor captor
    ) {
        captor.record(useCase.translateInstruction(instruction));
    }
}
