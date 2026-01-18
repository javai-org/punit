package org.javai.punit.examples2.tests;

import org.javai.punit.api.ExceptionHandling;
import org.javai.punit.api.Factor;
import org.javai.punit.api.FactorSource;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.examples2.usecases.ShoppingBasketUseCase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestTemplate;

/**
 * Demonstrates exception handling modes in probabilistic testing.
 *
 * <p>When individual samples throw exceptions, you can configure how PUnit
 * should respond:
 * <ul>
 *   <li><b>FAIL_SAMPLE</b> - Treat exception as failed sample, continue testing</li>
 *   <li><b>ABORT_TEST</b> - Immediately abort the test with the exception</li>
 * </ul>
 *
 * <h2>What This Demonstrates</h2>
 * <ul>
 *   <li>{@code onException = ExceptionHandling.FAIL_SAMPLE} - Treat as failure</li>
 *   <li>{@code onException = ExceptionHandling.ABORT_TEST} - Abort immediately</li>
 *   <li>{@code maxExampleFailures} - Limit captured failure examples</li>
 * </ul>
 *
 * <h2>When to Use Each Mode</h2>
 *
 * <h3>FAIL_SAMPLE (Default)</h3>
 * <p>Use when:
 * <ul>
 *   <li>Some failures are expected in normal operation</li>
 *   <li>You want to measure overall reliability including exception rate</li>
 *   <li>Exceptions represent business-level failures, not infrastructure issues</li>
 * </ul>
 *
 * <h3>ABORT_TEST</h3>
 * <p>Use when:
 * <ul>
 *   <li>Any exception indicates a serious problem</li>
 *   <li>You want fast feedback when things break</li>
 *   <li>Exceptions indicate infrastructure or configuration issues</li>
 * </ul>
 *
 * <h2>Running</h2>
 * <pre>{@code
 * ./gradlew test --tests "ShoppingBasketExceptionTest"
 * }</pre>
 *
 * @see ShoppingBasketUseCase
 */
@Disabled("Example test - run manually after generating baseline")
public class ShoppingBasketExceptionTest {

    /**
     * Test with FAIL_SAMPLE exception handling.
     *
     * <p>When a sample throws an exception:
     * <ul>
     *   <li>The exception is recorded as a failed sample</li>
     *   <li>Up to {@code maxExampleFailures} examples are captured</li>
     *   <li>Testing continues with remaining samples</li>
     *   <li>Final verdict is based on overall success rate</li>
     * </ul>
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     */
    @TestTemplate
    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 100,
            onException = ExceptionHandling.FAIL_SAMPLE,
            maxExampleFailures = 5
    )
    @FactorSource(value = "standardInstructions", factors = {"instruction"})
    void testFailSampleMode(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction
    ) {
        useCase.translateInstruction(instruction).assertAll();
    }

    /**
     * Test with ABORT_TEST exception handling.
     *
     * <p>When a sample throws an exception:
     * <ul>
     *   <li>The test immediately stops</li>
     *   <li>The exception is propagated to the test framework</li>
     *   <li>No partial results are evaluated</li>
     * </ul>
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     */
    @TestTemplate
    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 100,
            onException = ExceptionHandling.ABORT_TEST
    )
    @FactorSource(value = "standardInstructions", factors = {"instruction"})
    void testAbortTestMode(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction
    ) {
        useCase.translateInstruction(instruction).assertAll();
    }

    /**
     * Test with limited failure example capture.
     *
     * <p>The {@code maxExampleFailures} parameter limits how many failure
     * examples are captured for diagnostics. This prevents memory issues
     * when there are many failures.
     *
     * <p>Setting a low limit (like 3) is useful when:
     * <ul>
     *   <li>Failure messages are large</li>
     *   <li>You only need a few examples to understand the issue</li>
     *   <li>Memory is constrained</li>
     * </ul>
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     */
    @TestTemplate
    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 100,
            onException = ExceptionHandling.FAIL_SAMPLE,
            maxExampleFailures = 3  // Only capture first 3 failures
    )
    @FactorSource(value = "standardInstructions", factors = {"instruction"})
    void testWithLimitedFailureExamples(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction
    ) {
        useCase.translateInstruction(instruction).assertAll();
    }

    /**
     * Test with maximum failure example capture.
     *
     * <p>A higher limit (like 20) captures more examples, useful when:
     * <ul>
     *   <li>You need to analyze patterns across failures</li>
     *   <li>Failures are varied and you want comprehensive visibility</li>
     *   <li>Memory is not a concern</li>
     * </ul>
     *
     * @param useCase the use case instance
     * @param instruction the instruction to process
     */
    @TestTemplate
    @ProbabilisticTest(
            useCase = ShoppingBasketUseCase.class,
            samples = 100,
            onException = ExceptionHandling.FAIL_SAMPLE,
            maxExampleFailures = 20
    )
    @FactorSource(value = "standardInstructions", factors = {"instruction"})
    void testWithExtendedFailureExamples(
            ShoppingBasketUseCase useCase,
            @Factor("instruction") String instruction
    ) {
        useCase.translateInstruction(instruction).assertAll();
    }
}
