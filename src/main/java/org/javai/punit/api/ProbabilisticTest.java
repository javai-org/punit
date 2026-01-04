package org.javai.punit.api;

import org.javai.punit.engine.ProbabilisticTestExtension;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a probabilistic test that will be executed multiple times
 * with pass/fail determined by whether the observed pass rate meets a minimum threshold.
 *
 * <p>Probabilistic tests are useful for testing non-deterministic systems where
 * individual invocations may occasionally fail, but the overall behavior should
 * meet a statistical threshold.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * @ProbabilisticTest(samples = 100, minPassRate = 0.95)
 * void serviceReturnsValidResponse() {
 *     Response response = service.call();
 *     assertThat(response.isValid()).isTrue();
 * }
 * }</pre>
 *
 * <p>The test above will execute 100 times and pass if at least 95% of
 * invocations succeed.
 *
 * <h2>Budget Control</h2>
 * <p>You can set time and token budgets to control resource consumption:
 * <pre>{@code
 * @ProbabilisticTest(samples = 100, minPassRate = 0.90,
 *                    timeBudgetMs = 60000, // 1 minute max
 *                    tokenBudget = 50000)  // 50k tokens max
 * void llmRespondsWithValidJson(TokenChargeRecorder tokenRecorder) {
 *     LlmResponse response = llmClient.complete("Generate JSON");
 *     tokenRecorder.recordTokens(response.getUsage().getTotalTokens());
 *     assertThat(response.getContent()).isValidJson();
 * }
 * }</pre>
 *
 * @see ProbabilisticTestExtension
 * @see TokenChargeRecorder
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(ProbabilisticTestExtension.class)
public @interface ProbabilisticTest {

    /**
     * Number of sample invocations to execute.
     * Must be ≥ 1.
     *
     * @return the number of samples to execute
     */
    int samples() default 100;

    /**
     * Minimum pass rate required for overall test success.
     * Value must be in range [0.0, 1.0]. The default is 1.0 (100% pass rate).
     *
     * <p>The test passes if and only if:
     * {@code (successes / samplesExecuted) >= minPassRate}
     *
     * @return the minimum required pass rate (0.0 to 1.0)
     */
    double minPassRate() default 1.0;

    /**
     * Maximum wall-clock time budget in milliseconds for all samples.
     * 0 = unlimited. Default: 0.
     * 
     * <p>If budget is exhausted before all samples complete, behavior is 
     * controlled by {@link #onBudgetExhausted()}.
     *
     * @return the time budget in milliseconds, or 0 for unlimited
     */
    long timeBudgetMs() default 0;

    /**
     * Token charge per sample invocation (static mode).
     * Must be ≥ 0. Default: 0 (no static token charging).
     * 
     * <p>This value is accumulated after each sample execution. Used in
     * conjunction with {@link #tokenBudget()} to limit total token consumption.
     * 
     * <p><strong>Static vs Dynamic charging:</strong>
     * <ul>
     *   <li>If the test method has a {@link TokenChargeRecorder} parameter,
     *       dynamic mode is used and this value is ignored.</li>
     *   <li>Otherwise, this fixed charge is added after each sample.</li>
     * </ul>
     *
     * @return the token charge per sample, or 0 to disable static charging
     */
    int tokenCharge() default 0;

    /**
     * Maximum total token budget for all samples combined.
     * 0 = unlimited. Default: 0.
     * 
     * <p>In static mode: before each sample, if the next sample's tokenCharge
     * would exceed the remaining budget, termination is triggered.
     * 
     * <p>In dynamic mode: after each sample, if total tokens consumed exceeds
     * the budget, termination is triggered.
     * 
     * <p>Behavior when exhausted is controlled by {@link #onBudgetExhausted()}.
     *
     * @return the token budget, or 0 for unlimited
     */
    long tokenBudget() default 0;

    /**
     * Behavior when any budget (time or token) is exhausted before completing
     * all samples. Default: FAIL (test fails if budget exhausted).
     *
     * @return the budget exhaustion behavior
     */
    BudgetExhaustedBehavior onBudgetExhausted() default BudgetExhaustedBehavior.FAIL;

    /**
     * How to treat non-{@link AssertionError} exceptions thrown by the test method.
     * Default: FAIL_SAMPLE (count as a failed sample, continue execution).
     *
     * @return the exception handling behavior
     */
    ExceptionHandling onException() default ExceptionHandling.FAIL_SAMPLE;

    /**
     * Maximum number of example failures to capture for diagnostic reporting.
     * Set to 0 to disable capture.
     *
     * @return the maximum number of failures to capture
     */
    int maxExampleFailures() default 5;

    // ========== Spec-Driven Test Support (Phase 4) ==========

    /**
     * Reference to an execution specification.
     *
     * <p>Format: {@code "useCaseId:version"} (e.g., "usecase.json.generation:v3")
     *
     * <p>When provided:
     * <ul>
     *   <li>The specification controls samples, minPassRate, budgets, and context</li>
     *   <li>Inline parameter values are ignored (with a warning)</li>
     *   <li>Success criteria from the spec are used to evaluate each sample</li>
     * </ul>
     *
     * <p>When empty (default):
     * <ul>
     *   <li>Test behaves as before (inline parameters control execution)</li>
     *   <li>Success is determined by absence of AssertionError</li>
     * </ul>
     *
     * @return the specification ID, or empty string for inline mode
     */
    String spec() default "";

    /**
     * The use case ID to execute (alternative to spec).
     *
     * <p>When provided without spec, the use case is executed with inline parameters.
     * Success criteria must be defined via method assertions.
     *
     * @return the use case ID, or empty string if not using use case mode
     */
    String useCase() default "";
}
