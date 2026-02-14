package org.javai.punit.examples.probabilistictests;

import java.util.stream.Stream;
import org.javai.punit.api.InputSource;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.examples.usecases.ShoppingBasketUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Demonstrates the three operational approaches for threshold determination.
 *
 * <p>PUnit supports three ways to configure probabilistic tests, each suited
 * to different scenarios:
 *
 * <h2>Approach 1: Sample-Size-First</h2>
 * <p><b>Specify:</b> {@code samples} + {@code thresholdConfidence}<br>
 * <b>Framework computes:</b> {@code minPassRate}<br>
 * <b>Use when:</b> You have a fixed compute budget (e.g., 100 API calls)
 * and want the most statistically rigorous threshold possible.
 *
 * <h2>Approach 2: Confidence-First</h2>
 * <p><b>Specify:</b> {@code confidence} + {@code minDetectableEffect} + {@code power}<br>
 * <b>Framework computes:</b> {@code samples}<br>
 * <b>Use when:</b> You need to detect specific degradation levels with
 * guaranteed statistical power. Common in SLA monitoring.
 *
 * <h2>Approach 3: Threshold-First</h2>
 * <p><b>Specify:</b> {@code samples} + {@code minPassRate}<br>
 * <b>Framework computes:</b> implied confidence<br>
 * <b>Use when:</b> You have a specific pass rate requirement from
 * external sources (SLA, policy) and want to verify conformance.
 *
 * <h2>Running</h2>
 * <pre>{@code
 * ./gradlew test --tests "ShoppingBasketThresholdApproachesTest"
 * }</pre>
 *
 * @see ShoppingBasketUseCase
 */
@Disabled("Example test - run manually after generating baseline")
public class ShoppingBasketThresholdApproachesTest {

    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    @BeforeEach
    void setUp() {
        provider.register(ShoppingBasketUseCase.class, ShoppingBasketUseCase::new);
    }

    /**
     * Approach 1: Sample-Size-First.
     *
     * <p>"I have budget for 100 samples. Give me the threshold with 95% confidence."
     *
     * <p>This approach is ideal when:
     * <ul>
     *   <li>You have a fixed compute/cost budget</li>
     *   <li>You want maximum statistical rigor within that budget</li>
     *   <li>You're willing to accept whatever threshold the statistics dictate</li>
     * </ul>
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     */

    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 100,
            thresholdConfidence = 0.95
    )
    @InputSource("standardInstructions")
    void sampleSizeFirst(
            ShoppingBasketUseCase useCase,
            String instruction
    ) {
        useCase.translateInstruction(instruction).assertAll();
    }

    /**
     * Approach 2: Confidence-First.
     *
     * <p>"I need to detect a 5% degradation with 95% confidence and 80% power."
     *
     * <p>This approach is ideal when:
     * <ul>
     *   <li>You need to detect specific degradation levels</li>
     *   <li>You have statistical power requirements</li>
     *   <li>Sample count is flexible based on what statistics require</li>
     * </ul>
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     */

    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            confidence = 0.95,
            minDetectableEffect = 0.05,
            power = 0.80
    )
    @InputSource("standardInstructions")
    void confidenceFirst(
            ShoppingBasketUseCase useCase,
            String instruction
    ) {
        useCase.translateInstruction(instruction).assertAll();
    }

    /**
     * Approach 3: Threshold-First.
     *
     * <p>"I know the pass rate must be at least 90%. Run 100 samples to verify."
     *
     * <p>This approach is ideal when:
     * <ul>
     *   <li>You have a known threshold from SLA or policy</li>
     *   <li>You want to verify conformance with that threshold</li>
     *   <li>Statistical confidence is informational, not the primary driver</li>
     * </ul>
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     */

    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 100,
            minPassRate = 0.90
    )
    @InputSource("standardInstructions")
    void thresholdFirst(
            ShoppingBasketUseCase useCase,
            String instruction
    ) {
        useCase.translateInstruction(instruction).assertAll();
    }

    static Stream<String> standardInstructions() {
        return Stream.of(
                "Add 2 apples",
                "Remove the milk",
                "Add 1 loaf of bread",
                "Add 3 oranges and 2 bananas",
                "Clear the basket"
        );
    }
}
