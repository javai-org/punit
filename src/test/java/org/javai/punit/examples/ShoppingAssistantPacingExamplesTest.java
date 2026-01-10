package org.javai.punit.examples;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Random;
import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.Pacing;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.TokenChargeRecorder;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.examples.shopping.usecase.MockShoppingAssistant;
import org.javai.punit.examples.shopping.usecase.ShoppingUseCase;
import org.javai.punit.experiment.model.UseCaseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Examples demonstrating PUnit's pacing constraints for rate-limited APIs.
 *
 * <h2>What is Pacing?</h2>
 * <p>When testing rate-limited APIs (LLMs, third-party services, etc.), pacing lets you
 * declare rate limits and the framework computes optimal execution timing automatically.
 *
 * <h2>Post-EXPLORE Configuration</h2>
 * <p>These tests assume the EXPLORE phase has already determined optimal settings:
 * <ul>
 *   <li>Model: gpt-4</li>
 *   <li>Temperature: 0.7</li>
 * </ul>
 * <p>Each test sets these values in the {@code @BeforeEach} setup method.
 *
 * <h2>Pacing vs Guardrails</h2>
 * <table>
 *   <tr><th>Guardrails (Time/Token Budgets)</th><th>Pacing Constraints</th></tr>
 *   <tr><td>Reactive: "Stop if we exceed X"</td><td>Proactive: "Use X to compute optimal pace"</td></tr>
 *   <tr><td>Defensive circuit breakers</td><td>Scheduling algorithm inputs</td></tr>
 *   <tr><td>Runtime enforcement</td><td>Pre-execution planning</td></tr>
 * </table>
 *
 * <h2>Pre-Flight Report</h2>
 * <p>When pacing is configured, PUnit prints an execution plan before starting:
 * <pre>
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║ PUnit Test: shouldReturnValidJsonWithRateLimit                   ║
 * ╠══════════════════════════════════════════════════════════════════╣
 * ║ Samples requested:     50                                        ║
 * ║ Pacing constraints:                                              ║
 * ║   • Max requests/min:  60 RPM                                    ║
 * ║   • Min delay/sample:  1000ms (derived from 60 RPM)              ║
 * ╠══════════════════════════════════════════════════════════════════╣
 * ║ Computed execution plan:                                         ║
 * ║   • Concurrency:         sequential                              ║
 * ║   • Inter-request delay: 1000ms                                  ║
 * ║   • Effective throughput: 60 samples/min                         ║
 * ║   • Estimated duration:  50s                                     ║
 * ║   • Estimated completion: 14:23:45                               ║
 * ╠══════════════════════════════════════════════════════════════════╣
 * ║ Started: 14:22:55                                                ║
 * ║ Proceeding with execution...                                     ║
 * ╚══════════════════════════════════════════════════════════════════╝
 * </pre>
 *
 * <h2>Environment Overrides</h2>
 * <p>Override pacing at runtime for different environments:
 * <pre>
 * # More conservative rate for CI
 * ./gradlew test -Dpunit.pacing.maxRpm=30
 *
 * # Or via environment variable
 * export PUNIT_PACING_MAX_RPM=30
 * </pre>
 *
 * @see Pacing
 * @see ShoppingUseCase
 */
@Disabled("Example - demonstrates pacing constraints for rate-limited APIs")
@DisplayName("Shopping Assistant Pacing Examples")
class ShoppingAssistantPacingExamplesTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // USE CASE PROVIDER CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    @BeforeEach
    void setUp() {
        provider.register(ShoppingUseCase.class, () -> {
            ShoppingUseCase useCase = new ShoppingUseCase(
                new MockShoppingAssistant(
                    new Random(),
                    MockShoppingAssistant.MockConfiguration.experimentRealistic()
                )
            );
            // Configuration fixed post-EXPLORE (same as MEASURE experiment)
            useCase.setModel("gpt-4");
            useCase.setTemperature(0.7);
            return useCase;
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EXAMPLE 1: SIMPLE DELAY-BASED PACING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Demonstrates the simplest form of pacing: explicit delay between samples.
     *
     * <p>Use {@code minMsPerSample} when you want to specify delay directly without
     * thinking in terms of rates. This is useful when you know the API can handle
     * a request every N milliseconds.
     *
     * <p><b>Pre-flight report will show:</b>
     * <ul>
     *   <li>Min delay/sample: 500ms (explicit)</li>
     *   <li>Effective throughput: 120 samples/min</li>
     *   <li>Estimated duration: ~25s (for 50 samples)</li>
     * </ul>
     */
    @ProbabilisticTest(
        useCase = ShoppingUseCase.class,
        samples = 50,
        minPassRate = 0.90
    )
    @Pacing(minMsPerSample = 500)  // Wait 500ms between each sample
    @DisplayName("Simple delay-based pacing (500ms between samples)")
    void shouldReturnValidJsonWithSimpleDelay(
            ShoppingUseCase useCase,
            TokenChargeRecorder tokenRecorder) {
        UseCaseResult result = useCase.searchProducts("wireless headphones");

        tokenRecorder.recordTokens(result.getInt("tokensUsed", 0));

        assertThat(result.getBoolean("isValidJson", false))
            .as("Response should be valid JSON")
            .isTrue();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EXAMPLE 2: RATE-BASED PACING (RPM)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Demonstrates rate-based pacing using requests per minute (RPM).
     *
     * <p>This is the most common pacing pattern for LLM APIs like OpenAI and
     * Anthropic, which typically impose RPM limits.
     *
     * <p><b>How it works:</b>
     * <ul>
     *   <li>60 RPM → 1 request per second → 1000ms delay</li>
     *   <li>Framework computes delay automatically from rate limit</li>
     *   <li>Never hits rate limit during execution</li>
     * </ul>
     *
     * <p><b>Pre-flight report will show:</b>
     * <ul>
     *   <li>Max requests/min: 60 RPM</li>
     *   <li>Min delay/sample: 1000ms (derived from 60 RPM)</li>
     *   <li>Estimated duration: ~50s (for 50 samples)</li>
     * </ul>
     */
    @ProbabilisticTest(
        useCase = ShoppingUseCase.class,
        samples = 50,
        minPassRate = 0.90
    )
    @Pacing(maxRequestsPerMinute = 60)  // OpenAI tier 1 limit
    @DisplayName("Rate-based pacing (60 RPM - OpenAI style)")
    void shouldReturnValidJsonWithRateLimit(
            ShoppingUseCase useCase,
            TokenChargeRecorder tokenRecorder) {
        UseCaseResult result = useCase.searchProducts("laptop stand");

        tokenRecorder.recordTokens(result.getInt("tokensUsed", 0));

        assertThat(result.getBoolean("isValidJson", false))
            .as("Response should be valid JSON")
            .isTrue();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EXAMPLE 3: RATE-BASED PACING (RPS)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Demonstrates rate-based pacing using requests per second (RPS).
     *
     * <p>Some APIs specify limits in RPS rather than RPM. PUnit supports both.
     *
     * <p><b>How it works:</b>
     * <ul>
     *   <li>2 RPS → 500ms delay between requests</li>
     *   <li>Framework computes delay automatically</li>
     * </ul>
     */
    @ProbabilisticTest(
        useCase = ShoppingUseCase.class,
        samples = 30,
        minPassRate = 0.90
    )
    @Pacing(maxRequestsPerSecond = 2)  // 2 requests per second
    @DisplayName("Rate-based pacing (2 RPS)")
    void shouldReturnValidJsonWithRpsLimit(
            ShoppingUseCase useCase,
            TokenChargeRecorder tokenRecorder) {
        UseCaseResult result = useCase.searchProducts("USB-C hub");

        tokenRecorder.recordTokens(result.getInt("tokensUsed", 0));

        assertThat(result.getBoolean("isValidJson", false))
            .as("Response should be valid JSON")
            .isTrue();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EXAMPLE 4: COMBINED CONSTRAINTS (MOST RESTRICTIVE WINS)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Demonstrates how multiple pacing constraints compose.
     *
     * <p>When multiple constraints are specified, the <b>most restrictive wins</b>.
     * This allows you to specify multiple API limits and let the framework compute
     * the optimal pace that satisfies all of them.
     *
     * <p><b>Constraint composition:</b>
     * <ul>
     *   <li>60 RPM → 1000ms delay</li>
     *   <li>2 RPS → 500ms delay</li>
     *   <li>minMsPerSample = 250 → 250ms delay</li>
     *   <li><b>Effective delay: 1000ms</b> (RPM is most restrictive)</li>
     * </ul>
     */
    @ProbabilisticTest(
        useCase = ShoppingUseCase.class,
        samples = 30,
        minPassRate = 0.90
    )
    @Pacing(
        maxRequestsPerMinute = 60,   // Implies 1000ms (most restrictive)
        maxRequestsPerSecond = 2,    // Implies 500ms
        minMsPerSample = 250         // Explicit 250ms
    )
    @DisplayName("Combined constraints (most restrictive wins)")
    void shouldReturnValidJsonWithCombinedConstraints(
            ShoppingUseCase useCase,
            TokenChargeRecorder tokenRecorder) {
        UseCaseResult result = useCase.searchProducts("mechanical keyboard");

        tokenRecorder.recordTokens(result.getInt("tokensUsed", 0));

        assertThat(result.getBoolean("isValidJson", false))
            .as("Response should be valid JSON")
            .isTrue();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EXAMPLE 5: PACING WITH BUDGET GUARDRAILS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Demonstrates using pacing together with budget guardrails.
     *
     * <p>Pacing and budgets are complementary:
     * <ul>
     *   <li><b>Pacing</b>: Controls execution rate (proactive)</li>
     *   <li><b>Budgets</b>: Hard limits on time/tokens (reactive)</li>
     * </ul>
     *
     * <p><b>Feasibility warning:</b> If pacing would take longer than the time
     * budget allows, PUnit warns you before execution:
     * <pre>
     * ⚠ WARNING: Pacing conflict detected
     *   • 100 samples at 60 RPM would take ~1.7 minutes
     *   • Time budget is 1 minute (timeBudgetMs = 60000)
     *   • Options:
     *     1. Reduce sample count to ~60
     *     2. Increase time budget to 2 minutes
     *     3. Relax pacing constraints
     * </pre>
     */
    @ProbabilisticTest(
        useCase = ShoppingUseCase.class,
        samples = 50,
        minPassRate = 0.85,
        timeBudgetMs = 120000,  // 2 minute max
        tokenBudget = 25000,
        onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL
    )
    @Pacing(maxRequestsPerMinute = 60)
    @DisplayName("Pacing with budget guardrails")
    void shouldReturnValidJsonWithPacingAndBudgets(
            ShoppingUseCase useCase,
            TokenChargeRecorder tokenRecorder) {
        UseCaseResult result = useCase.searchProducts("webcam 4k");

        tokenRecorder.recordTokens(result.getInt("tokensUsed", 0));

        assertThat(result.getBoolean("isValidJson", false))
            .as("Response should be valid JSON")
            .isTrue();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EXAMPLE 6: HOURLY RATE LIMIT
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Demonstrates pacing for APIs with hourly rate limits.
     *
     * <p>Some APIs (especially free tiers) impose hourly limits. Use
     * {@code maxRequestsPerHour} to stay within these limits.
     *
     * <p><b>How it works:</b>
     * <ul>
     *   <li>3600 RPH → 1 request per second → 1000ms delay</li>
     *   <li>Framework computes delay: 3600000ms / 3600 = 1000ms</li>
     * </ul>
     */
    @ProbabilisticTest(
        useCase = ShoppingUseCase.class,
        samples = 20,
        minPassRate = 0.90
    )
    @Pacing(maxRequestsPerHour = 3600)  // 1 request per second (hourly limit)
    @DisplayName("Hourly rate limit (3600 RPH)")
    void shouldReturnValidJsonWithHourlyLimit(
            ShoppingUseCase useCase,
            TokenChargeRecorder tokenRecorder) {
        UseCaseResult result = useCase.searchProducts("bluetooth speaker waterproof");

        tokenRecorder.recordTokens(result.getInt("tokensUsed", 0));

        assertThat(result.getBoolean("isValidJson", false))
            .as("Response should be valid JSON")
            .isTrue();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EXAMPLE 7: VERY SLOW PACING (CONSERVATIVE API USAGE)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Demonstrates conservative pacing for expensive or strictly rate-limited APIs.
     *
     * <p>Sometimes you want to be extra conservative to avoid any chance of
     * hitting rate limits, especially with expensive APIs or during critical
     * production deployments.
     *
     * <p><b>Execution plan:</b>
     * <ul>
     *   <li>10 RPM → 6 seconds between requests</li>
     *   <li>20 samples → ~2 minutes total</li>
     * </ul>
     */
    @ProbabilisticTest(
        useCase = ShoppingUseCase.class,
        samples = 20,
        minPassRate = 0.90
    )
    @Pacing(maxRequestsPerMinute = 10)  // Very conservative: 1 request per 6 seconds
    @DisplayName("Conservative pacing (10 RPM)")
    void shouldReturnValidJsonWithConservativePacing(
            ShoppingUseCase useCase,
            TokenChargeRecorder tokenRecorder) {
        UseCaseResult result = useCase.searchProducts("noise cancelling earbuds");

        tokenRecorder.recordTokens(result.getInt("tokensUsed", 0));

        assertThat(result.getBoolean("isValidJson", false))
            .as("Response should be valid JSON")
            .isTrue();
    }
}
