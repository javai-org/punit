package org.javai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.javai.punit.ptest.engine.ProbabilisticTestExtension;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks a method as a probabilistic test that will be executed multiple times
 * with pass/fail determined by whether the observed pass rate meets a minimum threshold.
 *
 * <p>Probabilistic tests are useful for testing non-deterministic systems where
 * individual invocations may occasionally fail, but the overall behavior should
 * meet a statistical threshold.
 *
 * <h2>Three Operational Approaches</h2>
 *
 * <p>PUnit supports three mutually exclusive approaches for configuring test thresholds.
 * At any given time, you control two of the three variables (sample size, confidence, threshold);
 * the third is determined by statistics.
 *
 * <h3>Approach 1: Sample-Size-First (Cost-Driven)</h3>
 * <p>You specify: {@code samples} + {@code thresholdConfidence}<br>
 * Framework computes: {@code minPassRate} (threshold)
 *
 * <pre>{@code
 * @ProbabilisticTest(
 *     spec = "json.generation:v1",
 *     samples = 100,
 *     thresholdConfidence = 0.95
 * )
 * void testWithCostConstraint() { ... }
 * }</pre>
 *
 * <h3>Approach 2: Confidence-First (Quality-Driven)</h3>
 * <p>You specify: {@code confidence} + {@code minDetectableEffect} + {@code power}<br>
 * Framework computes: {@code samples} (required sample size)
 *
 * <pre>{@code
 * @ProbabilisticTest(
 *     spec = "json.generation:v1",
 *     confidence = 0.99,
 *     minDetectableEffect = 0.05,
 *     power = 0.80
 * )
 * void testWithQualityConstraint() { ... }
 * }</pre>
 *
 * <h3>Approach 3: Threshold-First (Baseline-Anchored)</h3>
 * <p>You specify: {@code samples} + {@code minPassRate}<br>
 * Framework computes: implied confidence (with warnings if statistically unsound)
 *
 * <pre>{@code
 * @ProbabilisticTest(
 *     spec = "json.generation:v1",
 *     samples = 100,
 *     minPassRate = 0.951
 * )
 * void testWithExplicitThreshold() { ... }
 * }</pre>
 *
 * <h2>Legacy Mode (No Spec)</h2>
 * <p>When no spec is provided, the test runs in legacy mode with inline parameters:
 *
 * <pre>{@code
 * @ProbabilisticTest(samples = 100, minPassRate = 0.95)
 * void serviceReturnsValidResponse() {
 *     Response response = service.call();
 *     assertThat(response.isValid()).isTrue();
 * }
 * }</pre>
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

    // ═══════════════════════════════════════════════════════════════════════════
    // APPROACH 1: SAMPLE-SIZE-FIRST (Cost-Driven)
    // Specify: samples + thresholdConfidence → Framework computes: minPassRate
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Number of sample invocations to execute.
     * Must be ≥ 1.
     *
     * <p>Used in:
     * <ul>
     *   <li>Approach 1 (Sample-Size-First): combined with {@code thresholdConfidence}</li>
     *   <li>Approach 3 (Threshold-First): combined with {@code minPassRate}</li>
     *   <li>Legacy mode: when no spec is provided</li>
     * </ul>
     *
     * @return the number of samples to execute
     */
    int samples() default 100;

    /**
     * Confidence level for threshold derivation (0.0 to 1.0).
     *
     * <p>Used in Approach 1 (Sample-Size-First): combined with {@code samples}
     * to derive the minimum pass rate threshold from the baseline data.
     *
     * <p>Higher confidence means a lower threshold (more tolerant of variance),
     * which reduces false positives but may miss subtle degradations.
     *
     * <p>Default: {@code Double.NaN} (not set, uses legacy mode or other approach).
     *
     * @return the confidence level for threshold derivation
     */
    double thresholdConfidence() default Double.NaN;

    // ═══════════════════════════════════════════════════════════════════════════
    // APPROACH 2: CONFIDENCE-FIRST (Quality-Driven)
    // Specify: confidence + minDetectableEffect + power → Framework computes: samples
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Required confidence level for the test result (0.0 to 1.0).
     *
     * <p>Used in Approach 2 (Confidence-First): combined with
     * {@code minDetectableEffect} and {@code power} to compute the required sample size.
     *
     * <p>This is the probability that a "fail" verdict indicates real degradation
     * rather than random sampling variance. A confidence of 0.95 means there's only
     * a 5% chance of a false positive (failing when the system is actually fine).
     *
     * <p>Default: {@code Double.NaN} (not set, uses other approach).
     *
     * @return the required confidence level
     */
    double confidence() default Double.NaN;

    /**
     * Minimum degradation worth detecting (0.0 to 1.0).
     *
     * <p>Used in Approach 2 (Confidence-First): specifies the smallest drop
     * from the baseline rate that should be detected.
     *
     * <p>Example: {@code 0.05} means "detect a 5% drop from baseline".
     * If baseline is 95%, this would detect degradation to 90% or below.
     *
     * <p>Default: {@code Double.NaN} (required when using Confidence-First approach).
     *
     * @return the minimum detectable effect size
     */
    double minDetectableEffect() default Double.NaN;

    /**
     * Statistical power: probability of detecting a real degradation (0.0 to 1.0).
     *
     * <p>Used in Approach 2 (Confidence-First): combined with {@code confidence}
     * and {@code minDetectableEffect} to compute sample size.
     *
     * <p>Example: {@code 0.80} means "80% chance of catching a real degradation".
     * Higher power requires more samples.
     *
     * <p>Default: {@code Double.NaN} (required when using Confidence-First approach).
     *
     * @return the statistical power
     */
    double power() default Double.NaN;

    // ═══════════════════════════════════════════════════════════════════════════
    // APPROACH 3: THRESHOLD-FIRST (Baseline-Anchored)
    // Specify: samples + minPassRate → Framework computes: implied confidence
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Explicit minimum pass rate threshold (0.0 to 1.0).
     *
     * <p>Used in:
     * <ul>
     *   <li>Approach 3 (Threshold-First): when combined with {@code samples},
     *       the framework computes the implied confidence and warns if statistically unsound</li>
     *   <li>Legacy mode: when no spec is provided, this is the pass/fail threshold</li>
     * </ul>
     *
     * <p>The test passes if and only if:
     * {@code (successes / samplesExecuted) >= minPassRate}
     *
     * <p>Default: {@code Double.NaN} (derive from spec or use other approach).
     * Legacy default: {@code 1.0} (100% pass rate required).
     *
     * @return the minimum required pass rate (0.0 to 1.0)
     */
    double minPassRate() default Double.NaN;

    // ═══════════════════════════════════════════════════════════════════════════
    // BUDGET CONTROLS
    // ═══════════════════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════════════════
    // TRANSPARENT STATISTICS MODE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Enables transparent statistics mode for this test.
     *
     * <p>When enabled, the framework produces detailed statistical explanations
     * of the test verdict, including:
     * <ul>
     *   <li>Hypothesis statement (H₀ and H₁)</li>
     *   <li>Observed data summary</li>
     *   <li>Baseline reference and threshold derivation</li>
     *   <li>Confidence intervals and statistical inference</li>
     *   <li>Plain English verdict interpretation</li>
     * </ul>
     *
     * <p>This mode is designed for:
     * <ul>
     *   <li>Auditors requiring proof that testing is statistically sound</li>
     *   <li>Stakeholders who need evidence for reliability claims</li>
     *   <li>New team members learning how PUnit reaches verdicts</li>
     *   <li>Regulators requiring compliance documentation</li>
     * </ul>
     *
     * <h3>Configuration Precedence</h3>
     * <ol>
     *   <li>This annotation attribute (highest)</li>
     *   <li>{@code -Dpunit.stats.transparent=true} (system property)</li>
     *   <li>{@code PUNIT_STATS_TRANSPARENT=true} (environment variable)</li>
     *   <li>Default: {@code false}</li>
     * </ol>
     *
     * @return true to enable transparent stats, false to use global settings
     */
    boolean transparentStats() default false;

    // ═══════════════════════════════════════════════════════════════════════════
    // USE CASE AND SPEC-DRIVEN TEST SUPPORT
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * The use case class to test.
     *
     * <p>When specified, the framework:
     * <ol>
     *   <li>Resolves the use case ID from the class (via {@code @UseCase} or class name)</li>
     *   <li>Looks up the spec automatically: {@code punit/specs/{useCaseId}.yaml}</li>
     *   <li>Injects the use case instance via {@link UseCaseProvider}</li>
     *   <li>Uses the spec's {@code minPassRate} as the threshold</li>
     * </ol>
     *
     * <h3>Example</h3>
     * <pre>{@code
     * @RegisterExtension
     * UseCaseProvider provider = new UseCaseProvider();
     *
     * @BeforeEach
     * void setUp() {
     *     provider.register(ShoppingUseCase.class, () ->
     *         new ShoppingUseCase(new MockShoppingAssistant())
     *     );
     * }
     *
     * @ProbabilisticTest(useCase = ShoppingUseCase.class, samples = 30)
     * void testJsonValidity(ShoppingUseCase useCase) {
     *     UseCaseOutcome<SearchResult> outcome = useCase.searchProducts("query", context);
     *     assertThat(outcome.result().isValidJson()).isTrue();
     * }
     * }</pre>
     *
     * @return the use case class, or {@code Void.class} for legacy inline mode
     * @see UseCaseProvider
     * @see UseCase
     */
    Class<?> useCase() default Void.class;

    // ═══════════════════════════════════════════════════════════════════════════
    // THRESHOLD PROVENANCE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Documents the origin of the threshold (e.g., SLA, SLO, policy, empirical).
     *
     * <p>This metadata is surfaced in the verdict output for traceability and
     * affects how hypothesis tests and verdicts are framed:
     * <pre>
     * PUnit PASSED: testServiceResponds
     *   Observed pass rate: 97.0% (97/100) >= min pass rate: 95.0%
     *   Threshold origin: SLA
     *   Contract ref: Acme API SLA v3.2 §2.1
     * </pre>
     *
     * <p>The origin determines hypothesis framing:
     * <ul>
     *   <li>SLA: "System meets SLA requirement" / "System violates SLA"</li>
     *   <li>SLO: "System meets SLO target" / "System falls short of SLO"</li>
     *   <li>POLICY: "System meets policy" / "System violates policy"</li>
     *   <li>EMPIRICAL: "No degradation from baseline" / "Degradation detected"</li>
     * </ul>
     *
     * @return the threshold origin, or {@link ThresholdOrigin#UNSPECIFIED} if not documented
     * @see ThresholdOrigin
     * @see #contractRef()
     */
    ThresholdOrigin thresholdOrigin() default ThresholdOrigin.UNSPECIFIED;

    /**
     * A human-readable reference to the contract, policy, or document
     * from which the threshold was derived.
     *
     * <p>Examples:
     * <ul>
     *   <li>"Acme API SLA v3.2 §2.1"</li>
     *   <li>"Internal SLO: LLM-2024-Q3"</li>
     *   <li>"SOC 2 Availability Policy"</li>
     *   <li>"Baseline spec: shopping-assistant.yaml (2024-01-15)"</li>
     * </ul>
     *
     * <p>This value is surfaced in verdict output when set.
     *
     * @return the contract reference, or empty string if not specified
     * @see #thresholdOrigin()
     */
    String contractRef() default "";

}
