package org.javai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.javai.punit.controls.budget.ProbabilisticTestBudgetExtension;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Defines a shared budget for all {@link ProbabilisticTest} methods within a test class.
 * 
 * <p>When applied to a test class, all probabilistic tests within that class share
 * a common time and/or token budget. This allows limiting the total resource consumption
 * across multiple test methods.
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * @ProbabilisticTestBudget(timeBudgetMs = 60000, tokenBudget = 100000)
 * class LlmIntegrationTests {
 *     
 *     @ProbabilisticTest(samples = 20, minPassRate = 0.90)
 *     void testJsonGeneration(TokenChargeRecorder recorder) {
 *         // ...
 *     }
 *     
 *     @ProbabilisticTest(samples = 20, minPassRate = 0.85)
 *     void testCodeGeneration(TokenChargeRecorder recorder) {
 *         // ...
 *     }
 * }
 * }</pre>
 * 
 * <p>In this example, both test methods share a 60-second time budget and
 * 100,000 token budget. If the first test consumes 40 seconds, the second
 * test only has 20 seconds remaining.
 * 
 * <h2>Budget Scope Precedence</h2>
 * <p>Budgets are checked in order: suite → class → method. The first exhausted
 * budget triggers termination with an appropriate {@link org.javai.punit.model.TerminationReason}.
 * 
 * <h2>Thread Safety</h2>
 * <p>The shared budget is thread-safe, allowing parallel test execution within
 * the class while maintaining accurate budget tracking.
 * 
 * @see ProbabilisticTest
 * @see TokenChargeRecorder
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(ProbabilisticTestBudgetExtension.class)
public @interface ProbabilisticTestBudget {

    /**
     * Maximum wall-clock time budget in milliseconds shared across all
     * probabilistic tests in this class.
     * 0 = unlimited. Default: 0.
     *
     * @return the shared time budget in milliseconds
     */
    long timeBudgetMs() default 0;

    /**
     * Maximum total token budget shared across all probabilistic tests
     * in this class.
     * 0 = unlimited. Default: 0.
     *
     * @return the shared token budget
     */
    long tokenBudget() default 0;

    /**
     * Behavior when the class-level budget is exhausted.
     * Default: FAIL (test fails if budget exhausted).
     *
     * @return the budget exhaustion behavior
     */
    BudgetExhaustedBehavior onBudgetExhausted() default BudgetExhaustedBehavior.FAIL;
}

