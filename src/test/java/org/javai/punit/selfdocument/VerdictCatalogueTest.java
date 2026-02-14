package org.javai.punit.selfdocument;

import java.util.stream.Stream;
import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.InputSource;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.TestIntent;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.examples.usecases.ShoppingBasketUseCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Catalogue of every meaningful PUnit verdict archetype.
 *
 * <p>This test class exercises every significant verdict shape using
 * {@link ShoppingBasketUseCase} with the {@code MockChatLlm}. A dedicated
 * Log4j2 appender ({@link CatalogueMarkdownAppender}) captures each verdict
 * and writes it to a documentation-ready markdown file.
 *
 * <h2>Verdict Dimensions Covered</h2>
 * <ul>
 *   <li><b>Normal completion</b> — pass and fail, summary and transparent</li>
 *   <li><b>Early termination</b> — impossibility and success-guaranteed</li>
 *   <li><b>Budget exhaustion</b> — FAIL and EVALUATE_PARTIAL behaviours</li>
 *   <li><b>Threshold origin framing</b> — SLA, SLO, POLICY, EMPIRICAL hypothesis text</li>
 *   <li><b>Statistical caveats</b> — small sample, close-to-threshold, compliance undersized</li>
 *   <li><b>Covariate misalignment</b> — temporal mismatch between baseline and test run</li>
 * </ul>
 *
 * <h2>Running</h2>
 * <pre>{@code
 * # Generate VERBOSE catalogue (default)
 * ./gradlew test -Prun=VerdictCatalogueTest
 *
 * # Generate SUMMARY catalogue
 * ./gradlew test -Prun=VerdictCatalogueTest -Dpunit.stats.detailLevel=SUMMARY
 *
 * # Output: build/verdict-catalogue-{LEVEL}.md
 * }</pre>
 *
 * @see CatalogueMarkdownAppender
 */
@Disabled("Verdict catalogue — run manually to generate build/verdict-catalogue.md")
public class VerdictCatalogueTest {

    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    private static CatalogueMarkdownAppender appender;

    @BeforeAll
    static void installAppender() {
        appender = CatalogueMarkdownAppender.install();
    }

    @AfterAll
    static void removeAppender() {
        if (appender != null) {
            appender.remove();
        }
    }

    @BeforeEach
    void setUp() {
        provider.register(ShoppingBasketUseCase.class, ShoppingBasketUseCase::new);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INPUT SOURCES
    // ═══════════════════════════════════════════════════════════════════════════

    static Stream<String> standardInstructions() {
        return Stream.of(
                "Add 2 apples",
                "Remove the milk",
                "Add 1 loaf of bread",
                "Add 3 oranges and 2 bananas",
                "Clear the basket"
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 1. NORMAL COMPLETION
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Normal Completion")
    class NormalCompletion {

        static Stream<String> standardInstructions() {
            return VerdictCatalogueTest.standardInstructions();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 50,
                minPassRate = 0.50
        )
        @InputSource("standardInstructions")
        void servicePassesComfortably(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(0.0);
            useCase.translateInstruction(instruction).assertAll();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 50,
                minPassRate = 0.95,
                intent = TestIntent.SMOKE
        )
        @InputSource("standardInstructions")
        void serviceFailsNarrowly(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(0.5);
            useCase.translateInstruction(instruction).assertAll();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 50,
                minPassRate = 0.50,
                transparentStats = true
        )
        @InputSource("standardInstructions")
        void servicePassesComfortablyTransparent(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(0.0);
            useCase.translateInstruction(instruction).assertAll();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 50,
                minPassRate = 0.95,
                transparentStats = true,
                intent = TestIntent.SMOKE
        )
        @InputSource("standardInstructions")
        void serviceFailsNarrowlyTransparent(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(0.5);
            useCase.translateInstruction(instruction).assertAll();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 2. EARLY TERMINATION
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Early Termination")
    class EarlyTermination {

        static Stream<String> standardInstructions() {
            return VerdictCatalogueTest.standardInstructions();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 30,
                minPassRate = 0.95,
                intent = TestIntent.SMOKE
        )
        @InputSource("standardInstructions")
        void failsEarlyWhenThresholdUnreachable(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(1.0);
            useCase.translateInstruction(instruction).assertAll();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 100,
                minPassRate = 0.50
        )
        @InputSource("standardInstructions")
        void passesEarlyWhenThresholdAlreadyMet(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(0.0);
            useCase.translateInstruction(instruction).assertAll();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 30,
                minPassRate = 0.95,
                transparentStats = true,
                intent = TestIntent.SMOKE
        )
        @InputSource("standardInstructions")
        void failsEarlyWhenThresholdUnreachableTransparent(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(1.0);
            useCase.translateInstruction(instruction).assertAll();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 100,
                minPassRate = 0.50,
                transparentStats = true
        )
        @InputSource("standardInstructions")
        void passesEarlyWhenThresholdAlreadyMetTransparent(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(0.0);
            useCase.translateInstruction(instruction).assertAll();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 3. BUDGET EXHAUSTION
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Budget Exhaustion")
    class BudgetExhaustion {

        static Stream<String> standardInstructions() {
            return VerdictCatalogueTest.standardInstructions();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 50,
                minPassRate = 0.50,
                tokenCharge = 200,
                tokenBudget = 1000,
                onBudgetExhausted = BudgetExhaustedBehavior.FAIL
        )
        @InputSource("standardInstructions")
        void failsWhenBudgetRunsOut(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(0.0);
            useCase.translateInstruction(instruction).assertAll();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 50,
                minPassRate = 0.50,
                tokenCharge = 200,
                tokenBudget = 1000,
                onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL
        )
        @InputSource("standardInstructions")
        void evaluatesPartialResultsOnBudgetPass(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(0.0);
            useCase.translateInstruction(instruction).assertAll();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 50,
                minPassRate = 0.90,
                tokenCharge = 200,
                tokenBudget = 1000,
                onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL
        )
        @InputSource("standardInstructions")
        void evaluatesPartialResultsOnBudgetFail(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(1.0);
            useCase.translateInstruction(instruction).assertAll();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 50,
                minPassRate = 0.50,
                tokenCharge = 200,
                tokenBudget = 1000,
                onBudgetExhausted = BudgetExhaustedBehavior.FAIL,
                transparentStats = true
        )
        @InputSource("standardInstructions")
        void failsWhenBudgetRunsOutTransparent(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(0.0);
            useCase.translateInstruction(instruction).assertAll();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 50,
                minPassRate = 0.50,
                tokenCharge = 200,
                tokenBudget = 1000,
                onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL,
                transparentStats = true
        )
        @InputSource("standardInstructions")
        void evaluatesPartialResultsOnBudgetPassTransparent(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(0.0);
            useCase.translateInstruction(instruction).assertAll();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 50,
                minPassRate = 0.90,
                tokenCharge = 200,
                tokenBudget = 1000,
                onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL,
                transparentStats = true
        )
        @InputSource("standardInstructions")
        void evaluatesPartialResultsOnBudgetFailTransparent(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(1.0);
            useCase.translateInstruction(instruction).assertAll();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 4. THRESHOLD ORIGIN FRAMING (transparent only)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Threshold Origin Framing")
    class ThresholdOriginFraming {

        static Stream<String> standardInstructions() {
            return VerdictCatalogueTest.standardInstructions();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 50,
                minPassRate = 0.50,
                thresholdOrigin = ThresholdOrigin.SLA,
                contractRef = "Acme Payment SLA v3.2 §4.1",
                transparentStats = true
        )
        @InputSource("standardInstructions")
        void slaPassShowsComplianceHypothesis(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(0.0);
            useCase.translateInstruction(instruction).assertAll();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 50,
                minPassRate = 0.50,
                thresholdOrigin = ThresholdOrigin.SLO,
                transparentStats = true
        )
        @InputSource("standardInstructions")
        void sloPassShowsTargetHypothesis(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(0.0);
            useCase.translateInstruction(instruction).assertAll();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 50,
                minPassRate = 0.50,
                thresholdOrigin = ThresholdOrigin.POLICY,
                transparentStats = true
        )
        @InputSource("standardInstructions")
        void policyPassShowsPolicyHypothesis(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(0.0);
            useCase.translateInstruction(instruction).assertAll();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 50,
                minPassRate = 0.50,
                thresholdOrigin = ThresholdOrigin.EMPIRICAL,
                transparentStats = true
        )
        @InputSource("standardInstructions")
        void empiricalPassShowsBaselineHypothesis(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(0.0);
            useCase.translateInstruction(instruction).assertAll();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5. STATISTICAL CAVEATS (transparent only)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Statistical Caveats")
    class StatisticalCaveats {

        static Stream<String> standardInstructions() {
            return VerdictCatalogueTest.standardInstructions();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 20,
                minPassRate = 0.30,
                transparentStats = true
        )
        @InputSource("standardInstructions")
        void smallSampleInterpretWithCaution(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(0.0);
            useCase.translateInstruction(instruction).assertAll();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 100,
                minPassRate = 0.88,
                transparentStats = true
        )
        @InputSource("standardInstructions")
        void closeToThresholdMayFluctuate(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(0.5);
            useCase.translateInstruction(instruction).assertAll();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 50,
                minPassRate = 0.9999,
                thresholdOrigin = ThresholdOrigin.SLA,
                contractRef = "Acme Payment SLA v3.2 §4.1",
                transparentStats = true,
                intent = TestIntent.SMOKE
        )
        @InputSource("standardInstructions")
        void complianceUndersizedSmokeTestOnly(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(0.0);
            useCase.translateInstruction(instruction).assertAll();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 6. COVARIATE MISALIGNMENT (spec-driven)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Covariate Misalignment")
    class CovariateMisalignment {

        static Stream<String> standardInstructions() {
            return VerdictCatalogueTest.standardInstructions();
        }

        @BeforeEach
        void setUpCovariateMatchingFactory() {
            // Register factory that pre-sets CONFIGURATION covariates to match
            // the baseline (llm_model=mock-llm, temperature=0.3). This is needed
            // because covariate resolution happens before the test body runs.
            // TEMPORAL covariates (time_of_day) will naturally differ from the
            // baseline, producing the misalignment warning/caveat we want to show.
            provider.register(ShoppingBasketUseCase.class, () -> {
                ShoppingBasketUseCase uc = new ShoppingBasketUseCase();
                uc.setModel("mock-llm");
                return uc;
            });
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 50
        )
        @InputSource("standardInstructions")
        void temporalMismatchShowsWarning(ShoppingBasketUseCase useCase, String instruction) {
            useCase.translateInstruction(instruction).assertAll();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 50,
                transparentStats = true
        )
        @InputSource("standardInstructions")
        void temporalMismatchShowsCaveatTransparent(ShoppingBasketUseCase useCase, String instruction) {
            useCase.translateInstruction(instruction).assertAll();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 7. INTENT-GOVERNED VERDICTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Intent-Governed Verdicts")
    class IntentGovernedVerdicts {

        static Stream<String> standardInstructions() {
            return VerdictCatalogueTest.standardInstructions();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 50,
                minPassRate = 0.50,
                intent = TestIntent.VERIFICATION,
                thresholdOrigin = ThresholdOrigin.SLA,
                contractRef = "Acme Payment SLA v3.2 §4.1",
                transparentStats = true
        )
        @InputSource("standardInstructions")
        void verificationPassSized(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(0.0);
            useCase.translateInstruction(instruction).assertAll();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 30,
                minPassRate = 0.95,
                intent = TestIntent.SMOKE,
                thresholdOrigin = ThresholdOrigin.SLA,
                transparentStats = true
        )
        @InputSource("standardInstructions")
        void smokeUndersizedNormative(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(0.0);
            useCase.translateInstruction(instruction).assertAll();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 50,
                minPassRate = 0.50,
                intent = TestIntent.SMOKE,
                thresholdOrigin = ThresholdOrigin.SLA,
                transparentStats = true
        )
        @InputSource("standardInstructions")
        void smokeSizedNormativeHint(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(0.0);
            useCase.translateInstruction(instruction).assertAll();
        }

        @ProbabilisticTest(
                useCase = ShoppingBasketUseCase.class,
                samples = 50,
                minPassRate = 0.50,
                intent = TestIntent.SMOKE,
                thresholdOrigin = ThresholdOrigin.EMPIRICAL,
                transparentStats = true
        )
        @InputSource("standardInstructions")
        void smokeNonNormative(ShoppingBasketUseCase useCase, String instruction) {
            useCase.setTemperature(0.0);
            useCase.translateInstruction(instruction).assertAll();
        }
    }
}
