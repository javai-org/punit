package org.javai.punit.examples.tests;

import org.javai.punit.api.Factor;
import org.javai.punit.api.FactorSource;
import org.javai.punit.api.Pacing;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.examples.usecases.ShoppingBasketUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;

import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Demonstrates rate limiting with the {@code @Pacing} annotation.
 *
 * <p>When testing against rate-limited APIs (like LLMs), you need to control
 * request frequency to avoid hitting rate limits. PUnit's pacing system
 * provides several options:
 *
 * <h2>What This Demonstrates</h2>
 * <ul>
 *   <li>{@code maxRequestsPerSecond} - RPS limit</li>
 *   <li>{@code maxRequestsPerMinute} - RPM limit (common for LLM APIs)</li>
 *   <li>{@code minMsPerSample} - Direct delay between samples</li>
 * </ul>
 *
 * <h2>Common LLM API Limits</h2>
 * <ul>
 *   <li>OpenAI: 60-10000 RPM depending on tier</li>
 *   <li>Anthropic: Varies by model and tier</li>
 *   <li>Google: 60-1000 RPM depending on model</li>
 * </ul>
 *
 * <h2>Running</h2>
 * <pre>{@code
 * ./gradlew test --tests "ShoppingBasketPacingTest"
 * }</pre>
 *
 * @see ShoppingBasketUseCase
 */
@Disabled("Example test - run manually")
public class ShoppingBasketPacingTest {

    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    @BeforeEach
    void setUp() {
        provider.register(ShoppingBasketUseCase.class, ShoppingBasketUseCase::new);
    }

    /**
     * Test with requests-per-second limit.
     *
     * <p>Use RPS limits when:
     * <ul>
     *   <li>The API has burst rate limits</li>
     *   <li>You need fine-grained control over request timing</li>
     *   <li>Tests run for short durations</li>
     * </ul>
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     */

    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 50
    )
    @Pacing(maxRequestsPerSecond = 5)  // Max 5 requests per second
    @FactorSource(value = "standardInstructions", factors = {"instruction"})
    void testWithRpsLimit(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction
    ) {
        useCase.translateInstruction(instruction).assertAll();
    }

    /**
     * Test with requests-per-minute limit.
     *
     * <p>Use RPM limits when:
     * <ul>
     *   <li>The API has minute-based rate limits (common for LLM APIs)</li>
     *   <li>You want to match the API's documented limits</li>
     *   <li>Tests run for longer durations</li>
     * </ul>
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     */

    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 50
    )
    @Pacing(maxRequestsPerMinute = 60)  // Max 60 requests per minute (1/sec avg)
    @FactorSource(value = "standardInstructions", factors = {"instruction"})
    void testWithRpmLimit(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction
    ) {
        useCase.translateInstruction(instruction).assertAll();
    }

    /**
     * Test with direct delay between samples.
     *
     * <p>Use direct delays when:
     * <ul>
     *   <li>You want precise control over timing</li>
     *   <li>Rate limits aren't well-defined</li>
     *   <li>You want to add "breathing room" between requests</li>
     * </ul>
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     */

    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 50
    )
    @Pacing(minMsPerSample = 200)  // At least 200ms between samples
    @FactorSource(value = "standardInstructions", factors = {"instruction"})
    void testWithDirectDelay(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction
    ) {
        useCase.translateInstruction(instruction).assertAll();
    }

    /**
     * Test with multiple pacing constraints.
     *
     * <p>When multiple constraints are specified, the most restrictive wins.
     * This allows setting both burst limits (RPS) and sustained limits (RPM).
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     */

    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 50
    )
    @Pacing(
            maxRequestsPerSecond = 10,   // Burst limit
            maxRequestsPerMinute = 120   // Sustained limit
    )
    @FactorSource(value = "standardInstructions", factors = {"instruction"})
    void testWithMultipleConstraints(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction
    ) {
        useCase.translateInstruction(instruction).assertAll();
    }

    /**
     * Test with conservative LLM API pacing.
     *
     * <p>Example of pacing suitable for a typical LLM API with:
     * <ul>
     *   <li>60 RPM limit</li>
     *   <li>No burst above 2 RPS</li>
     * </ul>
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     */

    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 30
    )
    @Pacing(
            maxRequestsPerSecond = 2,
            maxRequestsPerMinute = 60
    )
    @FactorSource(value = "standardInstructions", factors = {"instruction"})
    void testWithConservativeLlmPacing(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction
    ) {
        useCase.translateInstruction(instruction).assertAll();
    }
}
