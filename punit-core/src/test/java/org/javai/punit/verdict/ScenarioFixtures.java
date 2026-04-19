package org.javai.punit.verdict;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.javai.punit.api.TestIntent;
import org.javai.punit.controls.budget.CostBudgetMonitor.TokenMode;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.model.UseCaseAttributes;
import org.javai.punit.verdict.ProbabilisticTestVerdict.BaselineSummary;
import org.javai.punit.verdict.ProbabilisticTestVerdict.CostSummary;
import org.javai.punit.verdict.ProbabilisticTestVerdict.CovariateStatus;
import org.javai.punit.verdict.ProbabilisticTestVerdict.ExecutionSummary;
import org.javai.punit.verdict.ProbabilisticTestVerdict.FunctionalDimension;
import org.javai.punit.verdict.ProbabilisticTestVerdict.LatencyDimension;
import org.javai.punit.verdict.ProbabilisticTestVerdict.Misalignment;
import org.javai.punit.verdict.ProbabilisticTestVerdict.PercentileAssertion;
import org.javai.punit.verdict.ProbabilisticTestVerdict.SpecProvenance;
import org.javai.punit.verdict.ProbabilisticTestVerdict.StatisticalAnalysis;
import org.javai.punit.verdict.ProbabilisticTestVerdict.Termination;
import org.javai.punit.verdict.ProbabilisticTestVerdict.TestIdentity;

/**
 * Shared scenario fixtures for pipeline validation tests.
 *
 * <p>Each factory method returns a {@link ProbabilisticTestVerdict} constructed
 * with the exact hand-computed values from the rendering validation plan (Scenarios A–G).
 * Verdicts are constructed directly (not via the builder) so that every field is
 * under explicit control — no derived computation happens here.
 *
 * <p>Reusable by rendering tests, HTML report tests, and XML roundtrip tests.
 */
final class ScenarioFixtures {

    private static final Instant BASELINE_GENERATED = Instant.parse("2026-01-15T00:00:00Z");

    private ScenarioFixtures() {}

    // ── Scenario A: Golden path PASS, baseline-driven ────────────────────

    static ProbabilisticTestVerdict scenarioA() {
        return new ProbabilisticTestVerdict(
                "v:scen-a",
                Instant.now(),
                new TestIdentity("PaymentGatewayTest", "shouldProcessPayment", Optional.empty()),
                new ExecutionSummary(100, 100, 96, 4, 0.9374, 0.9600, 150,
                        Optional.empty(), TestIntent.VERIFICATION, 0.95, UseCaseAttributes.DEFAULT),
                Optional.empty(),
                Optional.empty(),
                new StatisticalAnalysis(
                        0.95,
                        0.0196,    // SE(p̂) = √(0.96 × 0.04 / 100)
                        0.9016,    // Wilson CI lower
                        0.9843,    // Wilson CI upper
                        Optional.of(0.9331),   // Z = (0.96 - 0.9374) / SE₀
                        Optional.of(0.8246),   // p = Φ(0.9331)
                        Optional.of("Wilson"),
                        Optional.of(new BaselineSummary(
                                "PaymentGateway.yaml", BASELINE_GENERATED,
                                1000, 950, 0.9500, 0.9374)),
                        List.of()
                ),
                CovariateStatus.allAligned(),
                new CostSummary(0, 0, 0, TokenMode.NONE, Optional.empty(), Optional.empty()),
                Optional.empty(),
                Optional.of(new SpecProvenance(
                        "EMPIRICAL", "", "PaymentGateway.yaml",
                        Optional.empty(), Optional.of("(bundled)"))),
                new Termination(TerminationReason.COMPLETED, Optional.empty()),
                Map.of(),
                true,
                PunitVerdict.PASS,
                "0.9600 >= 0.9374"
        );
    }

    // ── Scenario B: Golden path FAIL, baseline-driven ────────────────────

    static ProbabilisticTestVerdict scenarioB() {
        return new ProbabilisticTestVerdict(
                "v:scen-b",
                Instant.now(),
                new TestIdentity("PaymentGatewayTest", "shouldProcessPayment", Optional.empty()),
                new ExecutionSummary(100, 100, 85, 15, 0.9374, 0.8500, 150,
                        Optional.empty(), TestIntent.VERIFICATION, 0.95, UseCaseAttributes.DEFAULT),
                Optional.empty(),
                Optional.empty(),
                new StatisticalAnalysis(
                        0.95,
                        0.0357,    // SE(p̂) = √(0.85 × 0.15 / 100)
                        0.7672,    // Wilson CI lower
                        0.9076,    // Wilson CI upper
                        Optional.of(-3.6080),  // Z
                        Optional.of(0.0002),   // p
                        Optional.of("Wilson"),
                        Optional.of(new BaselineSummary(
                                "PaymentGateway.yaml", BASELINE_GENERATED,
                                1000, 950, 0.9500, 0.9374)),
                        List.of()
                ),
                CovariateStatus.allAligned(),
                new CostSummary(0, 0, 0, TokenMode.NONE, Optional.empty(), Optional.empty()),
                Optional.empty(),
                Optional.of(new SpecProvenance(
                        "EMPIRICAL", "", "PaymentGateway.yaml",
                        Optional.empty(), Optional.of("(bundled)"))),
                new Termination(TerminationReason.COMPLETED, Optional.empty()),
                Map.of(),
                false,
                PunitVerdict.FAIL,
                "0.8500 < 0.9374"
        );
    }

    // ── Scenario C: Budget exhausted FAIL ────────────────────────────────

    static ProbabilisticTestVerdict scenarioC() {
        return new ProbabilisticTestVerdict(
                "v:scen-c",
                Instant.now(),
                new TestIdentity("LatencyTest", "shouldMeetSla", Optional.empty()),
                new ExecutionSummary(100, 50, 40, 10, 0.9000, 0.8000, 5000,
                        Optional.empty(), TestIntent.VERIFICATION, 0.95, UseCaseAttributes.DEFAULT),
                Optional.empty(),
                Optional.empty(),
                new StatisticalAnalysis(
                        0.95,
                        0.0566,    // SE(p̂) = √(0.80 × 0.20 / 50)
                        0.6696,    // Wilson CI lower
                        0.8911,    // Wilson CI upper
                        Optional.of(-2.3570),  // Z
                        Optional.of(0.0092),   // p
                        Optional.empty(),      // no threshold derivation (inline)
                        Optional.empty(),      // no baseline
                        List.of()
                ),
                CovariateStatus.allAligned(),
                new CostSummary(0, 5000, 0, TokenMode.NONE, Optional.empty(), Optional.empty()),
                Optional.empty(),
                Optional.empty(),              // no provenance (inline threshold)
                new Termination(TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED, Optional.empty()),
                Map.of(),
                false,
                PunitVerdict.FAIL,
                "budget exhausted"
        );
    }

    // ── Scenario D: Inline threshold PASS (no baseline) ──────────────────

    static ProbabilisticTestVerdict scenarioD() {
        return new ProbabilisticTestVerdict(
                "v:scen-d",
                Instant.now(),
                new TestIdentity("ClassifierTest", "shouldClassifyCorrectly", Optional.empty()),
                new ExecutionSummary(200, 200, 180, 20, 0.8500, 0.9000, 300,
                        Optional.empty(), TestIntent.VERIFICATION, 0.95, UseCaseAttributes.DEFAULT),
                Optional.empty(),
                Optional.empty(),
                new StatisticalAnalysis(
                        0.95,
                        0.0212,    // SE(p̂) = √(0.90 × 0.10 / 200)
                        0.8506,    // Wilson CI lower
                        0.9367,    // Wilson CI upper
                        Optional.of(1.9802),   // Z
                        Optional.of(0.9762),   // p
                        Optional.empty(),      // no threshold derivation
                        Optional.empty(),      // no baseline
                        List.of()
                ),
                CovariateStatus.allAligned(),
                new CostSummary(0, 0, 0, TokenMode.NONE, Optional.empty(), Optional.empty()),
                Optional.empty(),
                Optional.empty(),              // no provenance
                new Termination(TerminationReason.COMPLETED, Optional.empty()),
                Map.of(),
                true,
                PunitVerdict.PASS,
                "0.9000 >= 0.8500"
        );
    }

    // ── Scenario E: PASS with functional + latency dimensions ────────────

    static ProbabilisticTestVerdict scenarioE() {
        return new ProbabilisticTestVerdict(
                "v:scen-e",
                Instant.now(),
                new TestIdentity("ShoppingBasketTest", "shouldCompleteCheckout", Optional.empty()),
                new ExecutionSummary(100, 100, 95, 5, 0.9000, 0.9500, 200,
                        Optional.empty(), TestIntent.VERIFICATION, 0.95, UseCaseAttributes.DEFAULT),
                Optional.of(new FunctionalDimension(95, 5, 0.9500)),
                Optional.of(new LatencyDimension(
                        90, 100, false, Optional.empty(),
                        120, 340, 420, 810, 1250,
                        List.of(
                                new PercentileAssertion("p95", 420, 500, true, false, "from baseline"),
                                new PercentileAssertion("p99", 810, 700, false, false, "from baseline")
                        ),
                        List.of(),
                        90, 10
                )),
                new StatisticalAnalysis(
                        0.95,
                        0.0218,    // SE(p̂) = √(0.95 × 0.05 / 100)
                        0.8883,    // Wilson CI lower
                        0.9810,    // Wilson CI upper
                        Optional.of(1.6667),   // Z
                        Optional.of(0.9522),   // p
                        Optional.empty(),
                        Optional.empty(),
                        List.of()
                ),
                CovariateStatus.allAligned(),
                new CostSummary(0, 0, 0, TokenMode.NONE, Optional.empty(), Optional.empty()),
                Optional.empty(),
                Optional.of(new SpecProvenance(
                        "EMPIRICAL", "", "ShoppingBasket.yaml",
                        Optional.empty(), Optional.empty())),
                new Termination(TerminationReason.COMPLETED, Optional.empty()),
                Map.of(),
                true,
                PunitVerdict.PASS,
                "0.9500 >= 0.9000"
        );
    }

    // ── Scenario F: Inconclusive (covariate misalignment) ────────────────

    static ProbabilisticTestVerdict scenarioF() {
        return new ProbabilisticTestVerdict(
                "v:scen-f",
                Instant.now(),
                new TestIdentity("ModelTest", "shouldGenerateResponse", Optional.empty()),
                new ExecutionSummary(100, 100, 93, 7, 0.9000, 0.9300, 150,
                        Optional.empty(), TestIntent.VERIFICATION, 0.95, UseCaseAttributes.DEFAULT),
                Optional.empty(),
                Optional.empty(),
                new StatisticalAnalysis(
                        0.95,
                        0.0255,    // SE(p̂) = √(0.93 × 0.07 / 100)
                        0.8625,    // Wilson CI lower
                        0.9678,    // Wilson CI upper
                        Optional.of(1.0000),   // Z
                        Optional.of(0.8413),   // p
                        Optional.empty(),
                        Optional.empty(),
                        List.of()
                ),
                new CovariateStatus(false, List.of(
                        new Misalignment("model", "gpt-4", "gpt-4o")),
                        Map.of(), Map.of()),
                new CostSummary(0, 0, 0, TokenMode.NONE, Optional.empty(), Optional.empty()),
                Optional.empty(),
                Optional.empty(),
                new Termination(TerminationReason.COMPLETED, Optional.empty()),
                Map.of(),
                false,
                PunitVerdict.INCONCLUSIVE,
                "covariate misalignment"
        );
    }

    // ── Scenario G: Early termination (impossibility) ────────────────────

    /**
     * Scenario G: Early termination because the pass rate is mathematically unreachable.
     * 30/100 samples executed, 20 successes, 10 failures — even if all 70 remaining succeed,
     * maximum possible = 90 &lt; required 90 (ceiling).
     */
    static ProbabilisticTestVerdict scenarioG() {
        return new ProbabilisticTestVerdict(
                "v:scen-g",
                Instant.now(),
                new TestIdentity("ReliabilityTest", "shouldMeetTarget", Optional.empty()),
                new ExecutionSummary(100, 30, 20, 10, 0.9000, 0.6667, 45,
                        Optional.empty(), TestIntent.VERIFICATION, 0.95, UseCaseAttributes.DEFAULT),
                Optional.empty(),
                Optional.empty(),
                new StatisticalAnalysis(
                        0.95,
                        0.0861,    // SE(p̂) = √(0.6667 × 0.3333 / 30)
                        0.4880,    // Wilson CI lower
                        0.8118,    // Wilson CI upper
                        Optional.of(-4.2597),  // Z
                        Optional.of(0.0000),   // p ≈ 0.0000
                        Optional.empty(),
                        Optional.empty(),
                        List.of()
                ),
                CovariateStatus.allAligned(),
                new CostSummary(0, 0, 0, TokenMode.NONE, Optional.empty(), Optional.empty()),
                Optional.empty(),
                Optional.empty(),
                new Termination(TerminationReason.IMPOSSIBILITY, Optional.empty()),
                Map.of(),
                false,
                PunitVerdict.FAIL,
                "0.6667 < 0.9000"
        );
    }

    // ── Scenario H: Token budget exhausted ───────────────────────────────

    /**
     * Scenario H: Token budget was exhausted before enough samples could run.
     * 66/100 samples executed — the system may or may not be performing adequately,
     * there simply was not enough budget to tell.
     */
    static ProbabilisticTestVerdict scenarioH() {
        return new ProbabilisticTestVerdict(
                "v:scen-h",
                Instant.now(),
                new TestIdentity("LlmTest", "shouldGenerateValid", Optional.empty()),
                new ExecutionSummary(100, 66, 55, 11, 0.9000, 0.8333, 3200,
                        Optional.empty(), TestIntent.VERIFICATION, 0.95, UseCaseAttributes.DEFAULT),
                Optional.empty(),
                Optional.empty(),
                new StatisticalAnalysis(
                        0.95,
                        0.0459,    // SE(p̂) = √(0.8333 × 0.1667 / 66)
                        0.7264,    // Wilson CI lower
                        0.9078,    // Wilson CI upper
                        Optional.of(-1.7638),  // Z
                        Optional.of(0.0389),   // p
                        Optional.empty(),
                        Optional.empty(),
                        List.of()
                ),
                CovariateStatus.allAligned(),
                new CostSummary(12000, 0, 15000, TokenMode.DYNAMIC, Optional.empty(), Optional.empty()),
                Optional.empty(),
                Optional.empty(),
                new Termination(TerminationReason.METHOD_TOKEN_BUDGET_EXHAUSTED, Optional.empty()),
                Map.of(),
                false,
                PunitVerdict.FAIL,
                "budget exhausted"
        );
    }
}
