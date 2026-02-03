package org.javai.punit.examples.tests;

import org.javai.punit.api.Factor;
import org.javai.punit.api.FactorSource;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.examples.usecases.ShoppingBasketUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;

import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Demonstrates diagnostic features in probabilistic testing.
 *
 * <p>When debugging probabilistic tests, you may need detailed information
 * about the statistical computations. PUnit provides diagnostic features
 * to make the statistics transparent.
 *
 * <h2>What This Demonstrates</h2>
 * <ul>
 *   <li>{@code transparentStats = true} - Detailed statistical explanations</li>
 *   <li>Early termination visibility (impossibility/success-guaranteed)</li>
 * </ul>
 *
 * <h2>Transparent Statistics Output</h2>
 * <p>When enabled, the test output includes:
 * <ul>
 *   <li>Baseline statistics used for threshold derivation</li>
 *   <li>Derived threshold and confidence interval</li>
 *   <li>Observed success rate and sample count</li>
 *   <li>Statistical test details (null hypothesis, p-value)</li>
 *   <li>Early termination reason (if applicable)</li>
 * </ul>
 *
 * <h2>Early Termination</h2>
 * <p>PUnit can terminate tests early in two scenarios:
 * <ul>
 *   <li><b>Impossibility</b>: Even if all remaining samples pass, the test cannot pass</li>
 *   <li><b>Success Guaranteed</b>: Even if all remaining samples fail, the test passes</li>
 * </ul>
 *
 * <h2>Running</h2>
 * <pre>{@code
 * ./gradlew test --tests "ShoppingBasketDiagnosticsTest"
 * }</pre>
 *
 * @see ShoppingBasketUseCase
 */
@Disabled("Example test - run manually after generating baseline")
public class ShoppingBasketDiagnosticsTest {

    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    @BeforeEach
    void setUp() {
        provider.register(ShoppingBasketUseCase.class, ShoppingBasketUseCase::new);
    }

    /**
     * Test with transparent statistics enabled.
     *
     * <p>When {@code transparentStats = true}, the test output includes
     * detailed explanations of all statistical computations. This is
     * invaluable for:
     * <ul>
     *   <li>Understanding why a test passed or failed</li>
     *   <li>Verifying threshold derivation is correct</li>
     *   <li>Debugging unexpected results</li>
     *   <li>Explaining results to stakeholders</li>
     * </ul>
     *
     * <p>Example output:
     * <pre>
     * === Probabilistic Test: testInstructionTranslation ===
     *
     * Baseline:
     *   Source: punit/specs/ShoppingBasketUseCase.yaml
     *   Baseline Success Rate: 0.947
     *   Sample Count: 1000
     *
     * Threshold Derivation:
     *   Confidence Level: 0.95
     *   Derived Min Pass Rate: 0.918
     *   Method: Wilson score interval lower bound
     *
     * Execution:
     *   Samples Completed: 100
     *   Successes: 94
     *   Failures: 6
     *   Observed Success Rate: 0.940
     *
     * Statistical Test:
     *   H0: True success rate >= 0.918
     *   H1: True success rate < 0.918
     *   Test Statistic: z = 0.802
     *   p-value: 0.789
     *   Verdict: PASS (observed rate 0.940 >= threshold 0.918)
     * </pre>
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     */

    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 100,
            transparentStats = true
    )
    @FactorSource(value = "standardInstructions", factors = {"instruction"})
    void testWithTransparentStats(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction
    ) {
        useCase.translateInstruction(instruction).assertAll();
    }

    /**
     * Test demonstrating early termination scenarios.
     *
     * <p>With a high sample count, early termination becomes more likely.
     * PUnit will stop testing early if:
     * <ul>
     *   <li>Success is mathematically guaranteed</li>
     *   <li>Failure is mathematically certain</li>
     * </ul>
     *
     * <p>Transparent stats shows when and why early termination occurred.
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     */

    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 200,
            transparentStats = true
    )
    @FactorSource(value = "standardInstructions", factors = {"instruction"})
    void testShowingEarlyTermination(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction
    ) {
        useCase.translateInstruction(instruction).assertAll();
    }

    /**
     * Test with explicit threshold showing stats comparison.
     *
     * <p>When using an explicit threshold (not derived from baseline),
     * transparent stats shows how the observed rate compares to the
     * specified minimum.
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     */

    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 100,
            minPassRate = 0.85,
            transparentStats = true
    )
    @FactorSource(value = "standardInstructions", factors = {"instruction"})
    void testWithExplicitThresholdAndStats(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction
    ) {
        useCase.translateInstruction(instruction).assertAll();
    }
}
