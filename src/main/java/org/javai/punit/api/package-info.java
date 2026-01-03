/**
 * PUNIT API: Annotations and interfaces for probabilistic unit testing.
 * 
 * <h2>Overview</h2>
 * <p>This package contains the public API for the PUNIT (Probabilistic Unit Testing)
 * framework. PUNIT is a JUnit 5 extension for testing non-deterministic systems by
 * running tests multiple times and determining pass/fail based on statistical thresholds.
 * 
 * <h2>Core Annotations</h2>
 * <ul>
 *   <li>{@link org.javai.punit.api.ProbabilisticTest} - Marks a method as a probabilistic test</li>
 *   <li>{@link org.javai.punit.api.ProbabilisticTestBudget} - Defines shared budgets at class level</li>
 * </ul>
 * 
 * <h2>Quick Start</h2>
 * <pre>{@code
 * import org.javai.punit.api.ProbabilisticTest;
 * import static org.assertj.core.api.Assertions.assertThat;
 * 
 * class MyServiceTest {
 *     @ProbabilisticTest(samples = 100, minPassRate = 0.95)
 *     void serviceReturnsValidResponse() {
 *         Response response = myService.call();
 *         assertThat(response.isValid()).isTrue();
 *     }
 * }
 * }</pre>
 * 
 * <h2>Dynamic Token Tracking</h2>
 * <p>For tests that consume API tokens (e.g., LLM invocations), inject a
 * {@link org.javai.punit.api.TokenChargeRecorder} to track actual consumption:
 * 
 * <pre>{@code
 * @ProbabilisticTest(samples = 50, minPassRate = 0.90, tokenBudget = 50000)
 * void llmTest(TokenChargeRecorder tokenRecorder) {
 *     LlmResponse response = llmClient.complete("Generate code...");
 *     tokenRecorder.recordTokens(response.getUsage().getTotalTokens());
 *     assertThat(response.getContent()).isNotEmpty();
 * }
 * }</pre>
 * 
 * <h2>Configuration Precedence</h2>
 * <p>Parameter values are resolved in this order (highest to lowest priority):
 * <ol>
 *   <li>System property (e.g., {@code -Dpunit.samples=10})</li>
 *   <li>Environment variable (e.g., {@code PUNIT_SAMPLES=10})</li>
 *   <li>Annotation value</li>
 *   <li>Framework default</li>
 * </ol>
 * 
 * <h2>Enums</h2>
 * <ul>
 *   <li>{@link org.javai.punit.api.BudgetExhaustedBehavior} - What to do when budget runs out</li>
 *   <li>{@link org.javai.punit.api.ExceptionHandling} - How to handle non-assertion exceptions</li>
 * </ul>
 * 
 * @see org.javai.punit.api.ProbabilisticTest
 * @see org.javai.punit.api.TokenChargeRecorder
 */
package org.javai.punit.api;

