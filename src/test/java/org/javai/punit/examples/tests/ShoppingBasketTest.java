package org.javai.punit.examples.tests;

import java.util.stream.Stream;
import org.javai.punit.api.InputSource;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.examples.usecases.ShoppingBasketUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;

import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Core probabilistic test for ShoppingBasketUseCase.
 *
 * <p>This test demonstrates the fundamental pattern of spec-driven probabilistic
 * testing, where thresholds are derived from baselines established by
 * {@link org.javai.punit.examples.experiments.ShoppingBasketMeasure}.
 *
 * <h2>What This Demonstrates</h2>
 * <ul>
 *   <li>{@code @ProbabilisticTest} annotation for probabilistic testing</li>
 *   <li>{@code useCase} parameter for automatic use case instantiation</li>
 *   <li>Spec-driven thresholds (loaded from baseline measurements)</li>
 *   <li>Statistical significance in pass/fail determination</li>
 * </ul>
 *
 * <h2>How It Works</h2>
 * <ol>
 *   <li>PUnit loads the spec from {@code punit/specs/ShoppingBasketUseCase.yaml}</li>
 *   <li>Derives the minimum pass rate threshold from baseline statistics</li>
 *   <li>Runs the configured number of samples</li>
 *   <li>Passes if observed rate meets/exceeds derived threshold</li>
 * </ol>
 *
 * <h2>Running</h2>
 * <pre>{@code
 * # First, generate the baseline (if not already done)
 * ./gradlew test --tests "ShoppingBasketMeasure"
 *
 * # Then run the test
 * ./gradlew test --tests "ShoppingBasketTest"
 * }</pre>
 *
 * @see ShoppingBasketUseCase
 * @see org.javai.punit.examples.experiments.ShoppingBasketMeasure
 */
@Disabled("Example test - run manually after generating baseline with ShoppingBasketMeasure")
public class ShoppingBasketTest {

    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    @BeforeEach
    void setUp() {
        provider.register(ShoppingBasketUseCase.class, ShoppingBasketUseCase::new);
    }

    /**
     * Tests shopping basket instruction translation with spec-derived threshold.
     *
     * <p>This test:
     * <ul>
     *   <li>Runs 100 samples with varied instructions</li>
     *   <li>Uses threshold derived from baseline measurement</li>
     *   <li>Passes if success rate meets statistical expectations</li>
     * </ul>
     *
     * @param useCase the use case instance (auto-wired by PUnit)
     * @param instruction the instruction to translate
     */

    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 100
    )
    @InputSource("standardInstructions")
    void testInstructionTranslation(
            ShoppingBasketUseCase useCase,
            String instruction
    ) {
        useCase.translateInstruction(instruction).assertAll();
    }

    /**
     * Tests with a controlled single instruction for focused verification.
     *
     * <p>Using a single instruction isolates the test from input variance,
     * making it easier to detect changes in LLM behavior.
     *
     * @param useCase the use case instance
     * @param instruction the instruction (always "Add 2 apples and remove the bread")
     */

    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 100
    )
    @InputSource("singleInstruction")
    void testControlledInstruction(
            ShoppingBasketUseCase useCase,
            String instruction
    ) {
        useCase.translateInstruction(instruction).assertAll();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INPUT SOURCES - Test input data
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Standard instructions for varied testing.
     */
    static Stream<String> standardInstructions() {
        return Stream.of(
                "Add 2 apples",
                "Remove the milk",
                "Add 1 loaf of bread",
                "Add 3 oranges and 2 bananas",
                "Clear the basket"
        );
    }

    /**
     * Single instruction for controlled testing.
     */
    static Stream<String> singleInstruction() {
        return Stream.of("Add 2 apples and remove the bread");
    }
}
