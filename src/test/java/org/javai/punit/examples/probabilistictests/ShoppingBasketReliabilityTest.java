package org.javai.punit.examples.probabilistictests;

import java.util.stream.Stream;
import org.javai.punit.api.InputSource;
import org.javai.punit.api.MeasureExperiment;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.examples.usecases.ShoppingBasketUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Unified reliability test for ShoppingBasketUseCase — both baseline measurement
 * and probabilistic verification live in a single class.
 *
 * <p>This class demonstrates the <b>unified test pattern</b>, where {@code @MeasureExperiment}
 * and {@code @ProbabilisticTest} methods coexist with shared setup and input sources.
 * Tag-based filtering ensures the correct methods run for each Gradle task:
 * <ul>
 *   <li>{@code ./gradlew exp} — runs only {@code @MeasureExperiment} methods
 *       (tagged {@code punit-experiment})</li>
 *   <li>{@code ./gradlew test} — runs only {@code @ProbabilisticTest} methods
 *       (experiment tag excluded)</li>
 * </ul>
 *
 * <h2>Workflow</h2>
 * <pre>{@code
 * # Phase 1: Establish baseline (run once / periodically)
 * ./gradlew exp -Prun=ShoppingBasketReliabilityTest.measureBaseline
 *
 * # Phase 2: Verify against baseline (run in CI)
 * ./gradlew test --tests "ShoppingBasketReliabilityTest.testInstructionTranslation"
 * }</pre>
 *
 * <h2>Benefits over separate classes</h2>
 * <ul>
 *   <li>Single {@code @BeforeEach} — shared setup, no duplication</li>
 *   <li>Shared input source — {@link #instructions()} used by both measure and test</li>
 *   <li>Co-located documentation — the relationship between measure and test is explicit</li>
 * </ul>
 *
 * @see ShoppingBasketUseCase
 */
@Disabled("Example - run with ./gradlew exp or ./gradlew test (see class Javadoc)")
public class ShoppingBasketReliabilityTest {

    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    @BeforeEach
    void setUp() {
        provider.register(ShoppingBasketUseCase.class, ShoppingBasketUseCase::new);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MEASURE — establish baseline (run once / periodically)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Establishes the production baseline for shopping basket instruction translation.
     *
     * <p>Cycles through representative instructions, measuring how reliably the LLM
     * translates each type. With 1000 samples and 10 instructions, each instruction
     * is tested exactly 100 times.
     *
     * @param useCase the use case instance (injected by PUnit)
     * @param instruction the instruction (cycles through variations)
     * @param captor records outcomes for aggregation
     */
    @MeasureExperiment(useCase = ShoppingBasketUseCase.class, experimentId = "baseline-v1")
    @InputSource("instructions")
    void measureBaseline(ShoppingBasketUseCase useCase, String instruction, OutcomeCaptor captor) {
        captor.record(useCase.translateInstruction(instruction));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST — verify against baseline (run in CI)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Tests shopping basket instruction translation with spec-derived threshold.
     *
     * <p>Runs 100 samples with varied instructions, using a threshold derived from
     * the baseline measurement. Passes if the observed success rate meets statistical
     * expectations.
     *
     * @param useCase the use case instance (injected by PUnit)
     * @param instruction the instruction to translate
     */
    @ProbabilisticTest(useCase = ShoppingBasketUseCase.class, samples = 100)
    @InputSource("instructions")
    void testInstructionTranslation(ShoppingBasketUseCase useCase, String instruction) {
        useCase.translateInstruction(instruction).assertAll();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SHARED INPUT SOURCE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Representative basket instructions covering the main operation types.
     *
     * <p>Shared by both the measure experiment and the probabilistic test, ensuring
     * the test exercises the same input distribution as the baseline.
     *
     * @return stream of instruction strings
     */
    static Stream<String> instructions() {
        return Stream.of(
                "Add 2 apples",
                "Remove the milk",
                "Add 1 loaf of bread",
                "Add 3 oranges and 2 bananas",
                "Add 5 tomatoes and remove the cheese",
                "Clear the basket",
                "Clear everything",
                "Remove 2 eggs from the basket",
                "Add a dozen eggs",
                "I'd like to remove all the vegetables"
        );
    }
}
