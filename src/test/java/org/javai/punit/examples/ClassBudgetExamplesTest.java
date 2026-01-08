package org.javai.punit.examples;

import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.ProbabilisticTestBudget;
import org.javai.punit.api.TokenChargeRecorder;
import org.junit.jupiter.api.Disabled;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Example demonstrating class-level shared budget.
 * 
 * <p><b>NOTE:</b> These tests contain failing samples BY DESIGN. They use random
 * behavior to simulate real-world probabilistic scenarios.
 * 
 * <p>All probabilistic tests in this class share a common token budget.
 * When the class budget is exhausted, remaining tests will fail or
 * evaluate partial results based on the configured behavior.
 */
@Disabled("Examples - run individually. Contains failing samples by design.")
@ProbabilisticTestBudget(
    tokenBudget = 5000,     // 5,000 tokens shared across all methods
    timeBudgetMs = 10000,   // 10 seconds max for entire class
    onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL
)
class ClassBudgetExamplesTest {

    private final Random random = new Random();

    /**
     * First test method - consumes some of the shared budget.
     */
    @ProbabilisticTest(samples = 20, minPassRate = 0.90)
    void firstTest(TokenChargeRecorder tokenRecorder) {
        int tokens = 100 + random.nextInt(50);
        tokenRecorder.recordTokens(tokens);
        
        assertThat(true).isTrue();
    }

    /**
     * Second test method - uses remaining budget after firstTest.
     * If firstTest consumed 3000 tokens, this test only has 2000 left.
     */
    @ProbabilisticTest(samples = 20, minPassRate = 0.85)
    void secondTest(TokenChargeRecorder tokenRecorder) {
        int tokens = 80 + random.nextInt(40);
        tokenRecorder.recordTokens(tokens);
        
        assertThat(true).isTrue();
    }

    /**
     * Third test method - might be cut short if budget exhausted.
     * With EVALUATE_PARTIAL, it will pass if completed samples meet threshold.
     */
    @ProbabilisticTest(samples = 30, minPassRate = 0.80)
    void thirdTest(TokenChargeRecorder tokenRecorder) {
        int tokens = 150 + random.nextInt(100);
        tokenRecorder.recordTokens(tokens);
        
        // Check remaining budget
        long remaining = tokenRecorder.getRemainingBudget();
        // Note: This shows the method-level view; class budget is tracked separately
        
        assertThat(true).isTrue();
    }
}

