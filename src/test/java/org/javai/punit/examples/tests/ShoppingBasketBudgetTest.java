package org.javai.punit.examples.tests;

import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.Factor;
import org.javai.punit.api.FactorSource;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.ProbabilisticTestBudget;
import org.javai.punit.api.TokenChargeRecorder;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.examples.usecases.ShoppingBasketUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Demonstrates budget management features in probabilistic testing.
 *
 * <p>Budget management allows you to control resource consumption (time, tokens)
 * across probabilistic tests. This is essential when testing against paid APIs
 * or when tests need to complete within time constraints.
 *
 * <h2>What This Demonstrates</h2>
 * <ul>
 *   <li>{@code @ProbabilisticTestBudget} - Class-level shared budget</li>
 *   <li>{@code timeBudgetMs} - Time limit per test method</li>
 *   <li>{@code tokenBudget} - Token limit per test method</li>
 *   <li>{@code tokenCharge} - Static token charge per sample</li>
 *   <li>{@code TokenChargeRecorder} - Dynamic token recording</li>
 *   <li>{@code BudgetExhaustedBehavior} - What to do when budget runs out</li>
 * </ul>
 *
 * <h2>Budget Hierarchy</h2>
 * <p>Budgets are checked in order: Suite → Class → Method.
 * The first exhausted budget triggers termination.
 *
 * <h2>Running</h2>
 * <pre>{@code
 * ./gradlew test --tests "ShoppingBasketBudgetTest"
 * }</pre>
 *
 * @see ShoppingBasketUseCase
 */
@Disabled("Example test - run manually")
@ProbabilisticTestBudget(
        timeBudgetMs = 60000,    // 60 second class budget
        tokenBudget = 100000     // 100K token class budget
)
public class ShoppingBasketBudgetTest {

    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    @BeforeEach
    void setUp() {
        provider.register(ShoppingBasketUseCase.class, ShoppingBasketUseCase::new);
    }

    /**
     * Test with time budget that fails on exhaustion.
     *
     * <p>If the test runs longer than 10 seconds, it will fail immediately
     * rather than continuing with partial results.
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     */
    @TestTemplate
    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 100,
            timeBudgetMs = 10000,  // 10 second method budget
            onBudgetExhausted = BudgetExhaustedBehavior.FAIL
    )
    @FactorSource(value = "standardInstructions", factors = {"instruction"})
    void testWithTimeBudgetFail(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction
    ) {
        useCase.translateInstruction(instruction).assertAll();
    }

    /**
     * Test with time budget that evaluates partial results.
     *
     * <p>If the test runs longer than 10 seconds, it will evaluate whatever
     * samples completed successfully. Use with caution - partial results
     * may not be statistically significant.
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     */
    @TestTemplate
    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 100,
            timeBudgetMs = 10000,
            onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL
    )
    @FactorSource(value = "standardInstructions", factors = {"instruction"})
    void testWithTimeBudgetPartial(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction
    ) {
        useCase.translateInstruction(instruction).assertAll();
    }

    /**
     * Test with static token charge per sample.
     *
     * <p>Use this when you know the approximate token cost per invocation.
     * The test will stop when total tokens would exceed the budget.
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     */
    @TestTemplate
    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 100,
            tokenCharge = 150,      // Estimated 150 tokens per sample
            tokenBudget = 10000,    // 10K token method budget
            onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL
    )
    @FactorSource(value = "standardInstructions", factors = {"instruction"})
    void testWithStaticTokenCharge(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction
    ) {
        useCase.translateInstruction(instruction).assertAll();
    }

    /**
     * Test with dynamic token recording.
     *
     * <p>Use this when token costs vary per invocation. Record actual tokens
     * used via the {@link TokenChargeRecorder} parameter.
     *
     * <p>The {@link ShoppingBasketUseCase} tracks tokens from each LLM call via
     * {@link ShoppingBasketUseCase#getLastTokensUsed()}, which we pass to the
     * token recorder.
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     * @param tokenRecorder records actual token usage per sample
     */
    @TestTemplate
    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 100,
            tokenBudget = 10000,
            onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL
    )
    @FactorSource(value = "standardInstructions", factors = {"instruction"})
    void testWithDynamicTokenRecording(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction,
            TokenChargeRecorder tokenRecorder
    ) {
        var outcome = useCase.translateInstruction(instruction);

        // Record actual tokens from the outcome metadata (extracted from response via withResult)
        outcome.getMetadataLong("tokensUsed").ifPresent(tokenRecorder::recordTokens);

        outcome.assertAll();
    }

    /**
     * Multiple tests sharing the class-level budget.
     *
     * <p>When {@code @ProbabilisticTestBudget} is at class level, all methods
     * share the budget. This test will contribute to class budget consumption.
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     */
    @TestTemplate
    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 50
    )
    @FactorSource(value = "standardInstructions", factors = {"instruction"})
    void testContributingToClassBudget(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction
    ) {
        useCase.translateInstruction(instruction).assertAll();
    }
}
