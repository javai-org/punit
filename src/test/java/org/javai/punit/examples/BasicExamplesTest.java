package org.javai.punit.examples;

import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.TokenChargeRecorder;
import org.junit.jupiter.api.Disabled;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Example probabilistic tests demonstrating PUNIT features.
 * 
 * <p>These tests are disabled by default to avoid running in CI.
 * Enable them individually to see PUNIT in action.
 */
@Disabled("Examples - run individually to explore PUNIT features")
class BasicExamplesTest {

    private final Random random = new Random();

    // ========== Basic Usage ==========

    /**
     * Basic probabilistic test with default settings.
     * Runs 100 times, passes if 95% succeed.
     */
    @ProbabilisticTest
    void basicTest() {
        // Simulates a service that occasionally fails
        boolean success = random.nextDouble() < 0.98;
        assertThat(success).isTrue();
    }

    /**
     * Custom sample count and pass rate.
     * Runs 50 times, passes if 80% succeed.
     */
    @ProbabilisticTest(samples = 50, minPassRate = 0.80)
    void customThresholds() {
        boolean success = random.nextDouble() < 0.85;
        assertThat(success).isTrue();
    }

    // ========== Time Budget ==========

    /**
     * Test with time budget.
     * Stops after 1 second even if samples remain.
     */
    @ProbabilisticTest(samples = 100, minPassRate = 0.90, timeBudgetMs = 1000)
    void withTimeBudget() throws InterruptedException {
        // Simulate slow operation
        Thread.sleep(50);
        assertThat(true).isTrue();
    }

    // ========== Token Budget (Static Mode) ==========

    /**
     * Static token charging: each sample consumes fixed tokens.
     * Stops when next sample would exceed budget.
     */
    @ProbabilisticTest(
        samples = 100,
        minPassRate = 0.90,
        tokenCharge = 100,    // Each sample uses 100 tokens
        tokenBudget = 500     // Stop after 500 tokens (5 samples)
    )
    void staticTokenBudget() {
        assertThat(true).isTrue();
    }

    // ========== Token Budget (Dynamic Mode) ==========

    /**
     * Dynamic token charging: record actual tokens per invocation.
     * The TokenChargeRecorder parameter triggers dynamic mode.
     */
    @ProbabilisticTest(samples = 50, minPassRate = 0.90, tokenBudget = 1000)
    void dynamicTokenBudget(TokenChargeRecorder tokenRecorder) {
        // Simulate variable token consumption (like real LLM responses)
        int tokensUsed = 50 + random.nextInt(100);
        tokenRecorder.recordTokens(tokensUsed);
        
        assertThat(true).isTrue();
    }

    /**
     * Multiple token recordings per sample.
     * Useful when a test makes multiple API calls.
     */
    @ProbabilisticTest(samples = 20, minPassRate = 0.90, tokenBudget = 2000)
    void multipleTokenRecordings(TokenChargeRecorder tokenRecorder) {
        // First API call
        tokenRecorder.recordTokens(50);
        
        // Second API call
        tokenRecorder.recordTokens(75);
        
        // Third API call
        tokenRecorder.recordTokens(25);
        
        // Total for this sample: 150 tokens
        assertThat(tokenRecorder.getTokensForCurrentSample()).isEqualTo(150);
    }

    // ========== Budget Exhaustion Behavior ==========

    /**
     * EVALUATE_PARTIAL: if budget exhausted, evaluate partial results
     * instead of failing immediately.
     */
    @ProbabilisticTest(
        samples = 100,
        minPassRate = 0.50,  // Low threshold so partial results likely pass
        tokenBudget = 200,
        tokenCharge = 100,
        onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL
    )
    void evaluatePartialOnBudgetExhaustion() {
        assertThat(true).isTrue();
    }

    // ========== Realistic LLM Simulation ==========

    /**
     * Simulates testing an LLM with realistic behavior:
     * - Variable response quality (some invalid JSON)
     * - Variable token consumption
     * - Budget constraints
     */
    @ProbabilisticTest(
        samples = 50,
        minPassRate = 0.85,
        tokenBudget = 25000,
        maxExampleFailures = 3
    )
    void simulatedLlmTest(TokenChargeRecorder tokenRecorder) {
        // Simulate LLM response with variable quality
        SimulatedLlmResponse response = simulateLlmCall();
        
        // Record actual tokens
        tokenRecorder.recordTokens(response.tokensUsed);
        
        // Validate response (simulated 90% success rate)
        assertThat(response.isValidJson)
            .as("Expected valid JSON but got: %s", response.content)
            .isTrue();
    }

    private SimulatedLlmResponse simulateLlmCall() {
        int tokens = 200 + random.nextInt(300);  // 200-500 tokens
        boolean isValid = random.nextDouble() < 0.90;  // 90% valid
        String content = isValid ? "{\"valid\": true}" : "Error: " + random.nextInt(100);
        return new SimulatedLlmResponse(content, tokens, isValid);
    }

    private record SimulatedLlmResponse(String content, int tokensUsed, boolean isValidJson) {}
}

